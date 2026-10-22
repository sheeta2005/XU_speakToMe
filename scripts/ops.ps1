#requires -Version 7.4
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Backup', 'Restore', 'Baseline', 'Archive', 'Deploy', 'Rollback', 'Check')][string]$Action,
    [string]$ComposeFile = 'deploy/compose.prod.yaml',
    [string]$EnvFile = 'deploy/prod.env',
    [ValidatePattern('^[a-z0-9][a-z0-9_-]+$')][string]$Project = 'speaktome-prod',
    [string]$BackupDirectory = '',
    [string]$Image = '',
    [switch]$ConfirmRestore,
    [switch]$ConfirmLegacySchema
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$composePath = (Resolve-Path -LiteralPath $ComposeFile).Path
$envPath = (Resolve-Path -LiteralPath $EnvFile).Path
$compose = @('compose', '--project-name', $Project, '--env-file', $envPath, '--file', $composePath)
$stateDirectory = Join-Path $repo "var/ops/$Project"
$stateFile = Join-Path $stateDirectory 'deployment.json'
$savedImage = $env:APP_IMAGE
$savedOutputEncoding = $OutputEncoding
$OutputEncoding = [Text.UTF8Encoding]::new($false)

# 10/22：所有操作统一使用显式 Compose 项目和配置；不调用 down -v、FLUSHDB 或覆盖恢复。
function Invoke-Docker([string[]]$Arguments) {
    $output = & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker 操作失败（退出码 $LASTEXITCODE），未继续后续步骤。" }
    return $output
}
function Compose([string[]]$Arguments) { Invoke-Docker ($compose + $Arguments) }
function Container([string]$Service) {
    $id = @(Compose @('ps', '--all', '--quiet', $Service))
    if ($id.Count -ne 1 -or !$id[0]) { throw "找不到唯一的 $Service 容器。" }
    return $id[0]
}
function Running([string]$Service) {
    return @((Compose @('ps', '--status', 'running', '--services'))).Contains($Service)
}
function Query-Sql([string]$Sql) {
    $result = $Sql | & docker @compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) { throw '数据库操作失败。' }
    return $result
}
function Redis([string[]]$Arguments) {
    Compose (@('exec', '-T', 'redis', 'sh', '-c', 'REDISCLI_AUTH="$REDIS_PASSWORD" exec redis-cli "$@"', 'sh') + $Arguments)
}
function Key-Fingerprint {
    # 10/22：清单只记录 AES 指纹，不导出密钥；恢复必须另行提供配套的原始密钥。
    $line = @(Get-Content -LiteralPath $envPath | Where-Object { $_ -match '^AES_KEY=' })
    if ($line.Count -ne 1) { throw '配置必须包含唯一 AES_KEY=原始Base64 值。' }
    $key = $line[0].Substring(8).Trim()
    if ([Convert]::FromBase64String($key).Length -ne 32) { throw 'AES_KEY 必须为 32 字节 Base64。' }
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($key))).ToLower()
}
function Save-State($Value) {
    New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
    $Value | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$stateFile.tmp" -Encoding utf8NoBOM
    Move-Item -LiteralPath "$stateFile.tmp" -Destination $stateFile -Force
}
function Verify-Backup([string]$Directory) {
    $backupPath = (Resolve-Path -LiteralPath $Directory).Path
    $manifest = Get-Content -LiteralPath (Join-Path $backupPath 'manifest.json') -Raw | ConvertFrom-Json
    if ($manifest.format -ne 1 -or $manifest.database -ne 'speak_to_me') { throw '不支持的备份格式或数据库。' }
    if ($manifest.aesFingerprint -ne (Key-Fingerprint)) { throw 'AES_KEY 与备份不配套，恢复已拒绝。' }
    if (@($manifest.files).Count -lt 2) { throw '备份清单不完整。' }
    foreach ($entry in $manifest.files) {
        if ([IO.Path]::IsPathRooted($entry.path) -or $entry.path -match '(^|[\\/])\.\.([\\/]|$)') { throw '备份路径非法。' }
        $file = Get-Item -LiteralPath (Join-Path $backupPath $entry.path)
        if ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw '备份不能包含链接。' }
        if ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLower() -ne $entry.sha256) { throw "备份校验失败：$($entry.path)" }
    }
    foreach ($required in @('mysql.sql', 'redis.rdb')) {
        if ($required -notin @($manifest.files.path)) { throw "备份缺少 $required。" }
    }
    return @{ directory = $backupPath; manifest = $manifest }
}
function New-Backup {
    $destination = if ($BackupDirectory) { [IO.Path]::GetFullPath($BackupDirectory) }
        else { Join-Path $repo ("backups/$Project/" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)) }
    if (Test-Path -LiteralPath $destination) { throw '备份目录已存在，拒绝覆盖。' }
    $fingerprint = Key-Fingerprint
    $resume = Running app
    if ($resume) { Compose @('stop', 'app') | Out-Null }
    $dump = '/tmp/speaktome-' + [guid]::NewGuid().ToString('N') + '.sql'
    try {
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        if (!$IsWindows) { & chmod 700 $destination; if ($LASTEXITCODE -ne 0) { throw '备份目录权限设置失败。' } }
        Compose @('exec', '-T', 'mysql', 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --events --triggers --hex-blob --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 "$MYSQL_DATABASE" > "$1"', 'sh', $dump) | Out-Null
        Invoke-Docker @('cp', "$(Container mysql):$dump", (Join-Path $destination 'mysql.sql')) | Out-Null
        if ((Redis @('SAVE')) -ne 'OK') { throw 'Redis 快照失败。' }
        Invoke-Docker @('cp', "$(Container redis):/data/dump.rdb", (Join-Path $destination 'redis.rdb')) | Out-Null
        $appId = @(Compose @('ps', '--all', '--quiet', 'app'))
        $sourceImage = $null
        if ($appId.Count -eq 1 -and $appId[0]) {
            New-Item -ItemType Directory -Path (Join-Path $destination 'archive') | Out-Null
            $mounts = (Invoke-Docker @('inspect', '--format', '{{json .Mounts}}', $appId[0])) | ConvertFrom-Json
            # 10/22：第五批镜像尚无归档卷；已有归档卷时复制失败必须使整个备份失败。
            if ('/app/archive' -in @($mounts.Destination)) {
                Invoke-Docker @('cp', "$($appId[0]):/app/archive/.", (Join-Path $destination 'archive')) | Out-Null
            }
            $sourceImage = Invoke-Docker @('inspect', '--format', '{{.Config.Image}}', $appId[0])
        }
        $files = @(Get-ChildItem -LiteralPath $destination -File -Recurse | ForEach-Object {
            @{ path = [IO.Path]::GetRelativePath($destination, $_.FullName).Replace('\', '/'); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower() }
        })
        @{ format = 1; database = 'speak_to_me'; sourceProject = $Project; sourceImage = $sourceImage;
            createdAt = [DateTimeOffset]::Now.ToString('o'); aesFingerprint = $fingerprint; files = $files
        } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $destination 'manifest.json') -Encoding utf8NoBOM
        Verify-Backup $destination | Out-Null
        Write-Host "备份完成：$destination（密钥需单独保管，数据库与归档含敏感内容）。"
        return $destination
    } finally {
        Compose @('exec', '-T', 'mysql', 'rm', '-f', $dump) | Out-Null
        if ($resume) { Compose @('start', 'app') | Out-Null }
    }
}
function Invoke-DatabaseCommand([string]$Command, [switch]$Root) {
    $extra = @()
    if ($Root) {
        # 10/22：结构核验需要创建临时参考库，管理员密码仅经进程环境传入一次性容器。
        $rootLine = @(Get-Content -LiteralPath $envPath | Where-Object { $_ -match '^MYSQL_ROOT_PASSWORD=' })
        if ($rootLine.Count -ne 1) { throw '缺少唯一 MYSQL_ROOT_PASSWORD。' }
        $savedPassword = $env:MYSQL_PASSWORD
        $env:MYSQL_PASSWORD = $rootLine[0].Substring('MYSQL_ROOT_PASSWORD='.Length).Trim()
        $extra = @('-e', 'MYSQL_USER=root', '-e', 'MYSQL_PASSWORD')
    }
    try {
        Compose (@('run', '--rm', '--no-deps') + $extra + @('--entrypoint', 'java', 'app',
            '-Dloader.main=com.xiyou.speakToMe.config.DatabaseCommand', '-cp', '/app/app.jar',
            'org.springframework.boot.loader.launch.PropertiesLauncher', $Command)) | Out-Host
    } finally {
        if ($Root) { $env:MYSQL_PASSWORD = $savedPassword }
    }
}
function Set-Image([string]$Value) {
    if (!$Value -or $Value -match '(^|:)latest$' -or ($Value -notmatch '@sha256:[a-f0-9]{64}$' -and $Value -notmatch ':[^/]+$')) {
        throw '必须指定固定版本镜像标签或 sha256 摘要，禁止 latest。'
    }
    Invoke-Docker @('image', 'inspect', $Value, '--format', '{{.Id}}') | Out-Null
    $env:APP_IMAGE = $Value
}
function Start-Release([string]$Value) {
    Set-Image $Value
    Compose @('up', '-d', '--no-build', '--wait', '--wait-timeout', '300', 'mysql', 'redis', 'app') | Out-Host
    # 10/22：应用重建后地址可能变化，重新创建反代以重新解析上游，避免仍转发到旧容器。
    if ('nginx' -in @(Compose @('config', '--services'))) {
        Compose @('up', '-d', '--no-build', '--force-recreate', '--wait', '--wait-timeout', '180', 'nginx') | Out-Host
    }
}
try {
    switch ($Action) {
        'Backup' { New-Backup | Out-Null }
        'Check' {
            # 10/22：独立内部端口检查依赖与积压，返回非零供计划任务、监控系统报警。
            Compose @('exec', '-T', 'app', 'curl', '--fail', '--silent', '--max-time', '10', 'http://127.0.0.1:9001/actuator/health/readiness') | Out-Host
            Compose @('exec', '-T', 'app', 'curl', '--fail', '--silent', '--max-time', '10', 'http://127.0.0.1:9001/actuator/health/liveness') | Out-Host
        }
        'Archive' { Invoke-DatabaseCommand archive }
        'Baseline' {
            if (!$ConfirmLegacySchema -or !$BackupDirectory) { throw '存量接管需提供已验证的 BackupDirectory 和 ConfirmLegacySchema。' }
            if (Running app) { throw '存量接管前必须停止应用和其他写入者。' }
            $backup = Verify-Backup $BackupDirectory
            if ($backup.manifest.sourceProject -ne $Project) { throw '备份不属于当前项目，接管已拒绝。' }
            Invoke-DatabaseCommand baseline -Root
        }
        'Restore' {
            if (!$ConfirmRestore -or !$BackupDirectory) { throw '恢复需提供 BackupDirectory 和 ConfirmRestore，仅接受空目标。' }
            $backup = Verify-Backup $BackupDirectory
            if (Running app) { throw '恢复前必须停止目标应用。' }
            if ([int](Query-Sql 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();') -ne 0) { throw '目标数据库非空，拒绝覆盖恢复。' }
            # 10/22：RDB 包含全部逻辑库，不能只检查默认的 0 号库是否为空。
            if (((Redis @('INFO', 'keyspace')) -join "`n") -match '(?m)^db\d+:keys=[1-9]') { throw '目标 Redis 非空，拒绝覆盖恢复。' }
            Compose @('create', '--no-build', 'app') | Out-Null
            Compose @('run', '--rm', '--no-deps', '--entrypoint', 'sh', 'app', '-c', 'test -z "$(ls -A /app/archive)"') | Out-Null
            $restoreDump = '/tmp/restore-' + [guid]::NewGuid().ToString('N') + '.sql'
            Invoke-Docker @('cp', (Join-Path $backup.directory 'mysql.sql'), "$(Container mysql):$restoreDump") | Out-Null
            try {
                Compose @('exec', '-T', 'mysql', 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 "$MYSQL_DATABASE" < "$1"', 'sh', $restoreDump) | Out-Null
            } finally { Compose @('exec', '-T', 'mysql', 'rm', '-f', $restoreDump) | Out-Null }
            if (Test-Path -LiteralPath (Join-Path $backup.directory 'archive')) {
                Invoke-Docker @('cp', ((Join-Path $backup.directory 'archive') + '/.'), "$(Container app):/app/archive") | Out-Null
                Compose @('run', '--rm', '--no-deps', '--user', '0', '--entrypoint', 'sh', 'app', '-c', 'chown -R app:app /app/archive') | Out-Null
            }
            # 10/22：先以 RDB 加载，再生成新的 AOF；不复用空实例已有的 AOF 以免忽略快照。
            Compose @('stop', 'redis') | Out-Null
            Invoke-Docker @('cp', (Join-Path $backup.directory 'redis.rdb'), "$(Container redis):/data/dump.rdb") | Out-Null
            Compose @('run', '--rm', '--no-deps', '--user', '0', '--entrypoint', 'sh', 'redis', '-c', 'chown redis:redis /data/dump.rdb') | Out-Null
            $helper = "$Project-restore-" + [guid]::NewGuid().ToString('N').Substring(0, 8)
            try {
                Compose @('run', '-d', '--no-deps', '--name', $helper, 'redis', 'sh', '-c', 'exec redis-server --appendonly no --requirepass "$REDIS_PASSWORD"') | Out-Null
                $ready = $false
                for ($attempt = 0; $attempt -lt 30; $attempt++) {
                    $ping = & docker exec $helper sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping' 2>$null
                    if ($LASTEXITCODE -eq 0 -and $ping -eq 'PONG') { $ready = $true; break }
                    Start-Sleep -Seconds 1
                }
                if (!$ready) { throw 'Redis 恢复实例未就绪。' }
                Invoke-Docker @('exec', $helper, 'sh', '-c', 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli CONFIG SET appendonly yes') | Out-Null
                $aofReady = $false
                for ($attempt = 0; $attempt -lt 30; $attempt++) {
                    $info = Invoke-Docker @('exec', $helper, 'sh', '-c', 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli INFO persistence')
                    if (($info -join "`n") -match 'aof_rewrite_in_progress:0' -and ($info -join "`n") -match 'aof_last_bgrewrite_status:ok') { $aofReady = $true; break }
                    Start-Sleep -Seconds 1
                }
                if (!$aofReady) { throw 'Redis AOF 重建未完成。' }
            } finally {
                Invoke-Docker @('stop', $helper) | Out-Null
                Invoke-Docker @('rm', $helper) | Out-Null
            }
            Compose @('start', 'redis') | Out-Null
            Write-Host '恢复完成。应用保持停止，请先检查迁移版本、历史数据、归档和会话，再部署配套镜像。'
        }
        'Deploy' {
            Set-Image $Image
            $previous = $null
            $existing = @(Compose @('ps', '--all', '--quiet', 'app'))
            if ($existing.Count -eq 1 -and $existing[0]) {
                $previous = Invoke-Docker @('inspect', '--format', '{{.Config.Image}}', $existing[0])
                New-Backup | Out-Null
            }
            try {
                Start-Release $Image
                Save-State @{ active = $Image; previous = $previous; deployedAt = [DateTimeOffset]::Now.ToString('o') }
            } catch {
                $deploymentFailure = $_
                if ($previous) { Write-Host '新版本失败，尝试恢复原应用镜像；不会倒退数据库迁移。'; Start-Release $previous }
                throw $deploymentFailure
            }
        }
        'Rollback' {
            $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
            if (!$state.previous) { throw '没有上一版镜像记录；数据库恢复请使用独立空环境。' }
            New-Backup | Out-Null
            Start-Release $state.previous
            Save-State @{ active = $state.previous; previous = $state.active; deployedAt = [DateTimeOffset]::Now.ToString('o') }
            Write-Host '应用镜像已回滚；数据库只允许兼容性向前迁移，禁止自动执行破坏性降级 SQL。'
        }
    }
} finally {
    $env:APP_IMAGE = $savedImage
    $OutputEncoding = $savedOutputEncoding
}
