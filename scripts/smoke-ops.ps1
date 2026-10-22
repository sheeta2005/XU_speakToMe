#requires -Version 7.4
param([Parameter(Mandatory = $true)][string]$Image)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$runId = [guid]::NewGuid().ToString('N').Substring(0, 10)
$workspace = Join-Path $repo "tmp/ops-smoke-$runId"
New-Item -ItemType Directory -Path $workspace -Force | Out-Null
$ops = Join-Path $PSScriptRoot 'ops.ps1'
$source = "ops-$runId-source"
$restore = "ops-$runId-restore"
$legacy = "ops-$runId-legacy"
$prod = "ops-$runId-prod"
$oldImage = "speaktome-ops:old-$runId"
$newImage = "speaktome-ops:new-$runId"
$badImage = "speaktome-ops:bad-$runId"
$composeFile = Join-Path $workspace 'compose.yaml'
$envFile = Join-Path $workspace '.env'
$prodFile = Join-Path $workspace 'deploy/compose.prod.yaml'
$prodEnv = Join-Path $workspace 'deploy/prod.env'
$originalImage = $env:APP_IMAGE
Remove-Item Env:APP_IMAGE -ErrorAction SilentlyContinue

# 10/22：只操作随机生成的独立项目；演练结束只删除这些测试项目的数据卷。
function Invoke-Docker([string[]]$Arguments) {
    $result = & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw '运维演练 Docker 命令失败。' }
    return $result
}
function C([string]$Project, [string[]]$Arguments) {
    $file = if ($Project -eq $prod) { $prodFile } else { $composeFile }
    $envPath = if ($Project -eq $prod) { $prodEnv } else { $envFile }
    Invoke-Docker (@('compose', '-p', $Project, '--env-file', $envPath, '-f', $file) + $Arguments)
}
function Sql([string]$Project, [string]$Query) {
    $file = if ($Project -eq $prod) { $prodFile } else { $composeFile }
    $envPath = if ($Project -eq $prod) { $prodEnv } else { $envFile }
    $result = $Query | & docker compose -p $Project --env-file $envPath -f $file exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" --batch --skip-column-names --default-character-set=utf8mb4 "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) { throw '运维演练 SQL 失败。' }
    return $result
}
function Require($Condition, [string]$Message) { if (!$Condition) { throw $Message } }
function Must-Fail([scriptblock]$Operation, [string]$Message) {
    $failed = $false
    try { & $Operation } catch { $failed = $true; Write-Host "预期拒绝：$Message" }
    Require $failed $Message
}
function Secret { [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)) }
function Common-Options([string]$Project) {
    return @{ ComposeFile = $composeFile; EnvFile = $envFile; Project = $Project }
}
try {
    Copy-Item -LiteralPath (Join-Path $repo 'compose.yaml') -Destination $composeFile
    $composeText = [IO.File]::ReadAllText($composeFile).Replace('${APP_PORT:-8080}', '0')
    $composeText = $composeText.Replace('${MYSQL_HOST_PORT:-13306}', '0').Replace('${REDIS_HOST_PORT:-16379}', '0')
    [IO.File]::WriteAllText($composeFile, $composeText)
    @("APP_PROFILE=demo", "APP_IMAGE=$oldImage", "AES_KEY=$(Secret)", "MYSQL_ROOT_PASSWORD=$(Secret)",
        "MYSQL_PASSWORD=$(Secret)", "REDIS_PASSWORD=$(Secret)") | Set-Content -LiteralPath $envFile -Encoding utf8NoBOM
    Invoke-Docker @('tag', $Image, $oldImage) | Out-Null
    # 10/22：用独立镜像 ID 验证切换机制，应用包相同；不据此宣称任意历史业务版本都兼容。
    "FROM $Image`nLABEL speaktome.ops.release=new" | Set-Content -LiteralPath (Join-Path $workspace 'Dockerfile.new') -Encoding utf8NoBOM
    "FROM $Image`nENTRYPOINT [""/bin/false""]" | Set-Content -LiteralPath (Join-Path $workspace 'Dockerfile.bad') -Encoding utf8NoBOM
    Invoke-Docker @('build', '-q', '-t', $newImage, '-f', (Join-Path $workspace 'Dockerfile.new'), $workspace) | Out-Null
    Invoke-Docker @('build', '-q', '-t', $badImage, '-f', (Join-Path $workspace 'Dockerfile.bad'), $workspace) | Out-Null
    C $source @('up', '-d', '--no-build', '--wait', '--wait-timeout', '300') | Out-Host
    $sourceOptions = Common-Options $source
    & $ops -Action Check @sourceOptions

    # 10/22：按第五批原始 SQL 建立无版本库，实际经过备份、结构核验、接管和 V2 升级。
    C $legacy @('up', '-d', '--no-build', '--wait', '--wait-timeout', '180', 'mysql', 'redis') | Out-Null
    Sql $legacy (Get-Content -LiteralPath (Join-Path $repo 'sql/init/01-schema.sql') -Raw -Encoding utf8) | Out-Null
    Sql $legacy "INSERT INTO campus(id,name) VALUES(1,'保留的旧校区');" | Out-Null
    $legacyOptions = Common-Options $legacy
    $legacyBackup = Join-Path $workspace 'before-baseline'
    & $ops -Action Backup -BackupDirectory $legacyBackup @legacyOptions
    & $ops -Action Baseline -BackupDirectory $legacyBackup -ConfirmLegacySchema @legacyOptions
    C $legacy @('up', '-d', '--no-build', '--wait', '--wait-timeout', '180', 'app') | Out-Null
    Require ((Sql $legacy 'SELECT name FROM campus WHERE id=1;') -eq '保留的旧校区') '接管升级覆盖了存量业务数据。'
    Require ([int](Sql $legacy "SELECT COUNT(*) FROM flyway_schema_history WHERE version='2' AND success=1;") -eq 1) '接管后没有升级到 V2。'
    C $legacy @('stop', 'app') | Out-Null
    $address = C $source @('port', 'app', '8080')
    $base = "http://$address/api/v1"
    $login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body '{"code":"demo-user"}'
    Require ($login.code -eq 0) '演示登录失败。'
    $session = $login.data.token
    Sql $source "UPDATE campus SET name='备份恢复验收校区' WHERE id=1; INSERT INTO audit_log(review_id,user_id,content,check_type,result,create_at) VALUES(NULL,1,'需要归档的历史审核内容',1,0,DATE_SUB(NOW(), INTERVAL 90 DAY));" | Out-Null
    & $ops -Action Archive @sourceOptions
    Require ([int](Sql $source 'SELECT COUNT(*) FROM audit_log;') -eq 0) '历史日志未归档清理。'
    $backupPath = Join-Path $workspace 'backup'
    & $ops -Action Backup -BackupDirectory $backupPath @sourceOptions
    Require ((Get-ChildItem -LiteralPath (Join-Path $backupPath 'archive') -Filter '*.jsonl').Count -eq 1) '备份缺少归档文件。'
    C $restore @('up', '-d', '--no-build', '--wait', '--wait-timeout', '180', 'mysql', 'redis') | Out-Null
    $restoreOptions = Common-Options $restore
    C $restore @('exec', '-T', 'redis', 'sh', '-c', 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli -n 1 SET restore-smoke-preserve keep') | Out-Null
    Must-Fail { & $ops -Action Restore -BackupDirectory $backupPath -ConfirmRestore @restoreOptions } 'Redis 非默认库非空时必须拒绝恢复。'
    C $restore @('exec', '-T', 'redis', 'sh', '-c', 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli -n 1 DEL restore-smoke-preserve') | Out-Null
    & $ops -Action Restore -BackupDirectory $backupPath -ConfirmRestore @restoreOptions
    Require ((Sql $restore 'SELECT name FROM campus WHERE id=1;') -eq '备份恢复验收校区') '数据库中文内容恢复不一致。'
    C $restore @('up', '-d', '--no-build', '--wait', '--wait-timeout', '180', 'app') | Out-Null
    $restoredAddress = C $restore @('port', 'app', '8080')
    $me = Invoke-RestMethod -Uri "http://$restoredAddress/api/v1/auth/me" -Headers @{ Authorization = "Bearer $session" }
    Require ($me.code -eq 0) 'Redis 会话恢复失败。'
    C $restore @('stop', 'app') | Out-Null
    Must-Fail { & $ops -Action Restore -BackupDirectory $backupPath -ConfirmRestore @restoreOptions } '非空目标必须拒绝恢复。'
    $wrongEnv = Join-Path $workspace 'wrong.env'
    ([IO.File]::ReadAllText($envFile) -replace '(?m)^AES_KEY=.*$', "AES_KEY=$(Secret)") | Set-Content -LiteralPath $wrongEnv -Encoding utf8NoBOM
    Must-Fail { & $ops -Action Restore -BackupDirectory $backupPath -ConfirmRestore -ComposeFile $composeFile -EnvFile $wrongEnv -Project $restore } '不配套的 AES 密钥必须拒绝恢复。'
    Add-Content -LiteralPath (Join-Path $backupPath 'mysql.sql') -Value '-- 破坏校验和' -Encoding utf8NoBOM
    Must-Fail { & $ops -Action Restore -BackupDirectory $backupPath -ConfirmRestore @restoreOptions } '损坏备份必须拒绝恢复。'
    & $ops -Action Deploy -Image $newImage -BackupDirectory (Join-Path $workspace 'before-deploy') @sourceOptions
    & $ops -Action Rollback -BackupDirectory (Join-Path $workspace 'before-rollback') @sourceOptions
    $container = C $source @('ps', '-q', 'app')
    Require ((Invoke-Docker @('inspect', '--format', '{{.Config.Image}}', $container)) -eq $oldImage) '回滚未恢复原镜像。'
    Must-Fail { & $ops -Action Deploy -Image $badImage -BackupDirectory (Join-Path $workspace 'before-failed-deploy') @sourceOptions } '坏版本必须失败并恢复原应用。'
    & $ops -Action Check @sourceOptions

    # 10/22：生产模板使用自签证书和虚构微信配置，仅验证 HTTPS、端口隔离、故障探针。
    New-Item -ItemType Directory -Path (Join-Path $workspace 'deploy/certs') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $repo 'deploy/compose.prod.yaml') -Destination $prodFile
    Copy-Item -LiteralPath (Join-Path $repo 'deploy/nginx.prod.conf') -Destination (Join-Path $workspace 'deploy/nginx.prod.conf')
    $prodText = [IO.File]::ReadAllText($prodFile).Replace('${HTTP_PORT:-80}', '0').Replace('${HTTPS_PORT:-443}', '0')
    [IO.File]::WriteAllText($prodFile, $prodText)
    $prodConfig = [IO.File]::ReadAllText($envFile).Replace("APP_IMAGE=$oldImage", "APP_IMAGE=$Image")
    $prodConfig += "`nWX_APPID=wx0000000000000000`nWX_SECRET=isolated-test-placeholder`nHTTP_BIND=127.0.0.1`n"
    [IO.File]::WriteAllText($prodEnv, $prodConfig)
    $certs = (Resolve-Path -LiteralPath (Join-Path $workspace 'deploy/certs')).Path
    Invoke-Docker @('run', '--rm', '--mount', "type=bind,source=$certs,target=/certs", '--entrypoint', 'openssl',
        'eclipse-temurin:21-jdk-jammy', 'req', '-x509', '-nodes', '-newkey', 'rsa:2048', '-days', '1',
        '-keyout', '/certs/privkey.pem', '-out', '/certs/fullchain.pem', '-subj', '/CN=localhost',
        '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1') | Out-Null
    C $prod @('up', '-d', '--no-build', '--wait', '--wait-timeout', '300') | Out-Host
    $prodOptions = @{ ComposeFile = $prodFile; EnvFile = $prodEnv; Project = $prod }
    & $ops -Action Check @prodOptions
    Require ([int](Sql $prod 'SELECT COUNT(*) FROM campus;') -eq 0) '生产环境误加载演示数据。'
    $httpsAddress = C $prod @('port', 'nginx', '443')
    $response = Invoke-RestMethod -Uri "https://$httpsAddress/api/v1/campuses" -SkipCertificateCheck
    Require ($response.code -eq 0) 'HTTPS 业务请求失败。'
    $management = Invoke-WebRequest -Uri "https://$httpsAddress/actuator/health" -SkipCertificateCheck -SkipHttpErrorCheck
    Require ($management.StatusCode -eq 404) '管理端点不应公开。'
    # 10/22：生产发布和回滚同时验证反代重建后仍能访问新的应用容器。
    & $ops -Action Deploy -Image $newImage -BackupDirectory (Join-Path $workspace 'before-prod-deploy') @prodOptions
    & $ops -Action Rollback -BackupDirectory (Join-Path $workspace 'before-prod-rollback') @prodOptions
    $httpsAddress = C $prod @('port', 'nginx', '443')
    Require ((Invoke-RestMethod -Uri "https://$httpsAddress/api/v1/campuses" -SkipCertificateCheck).code -eq 0) '生产回滚后 HTTPS 不可用。'
    foreach ($service in @('mysql', 'redis', 'app')) {
        $id = C $prod @('ps', '-q', $service)
        $bindings = Invoke-Docker @('inspect', '--format', '{{json .HostConfig.PortBindings}}', $id)
        Require ($bindings -in @('{}', 'null')) "$service 不应暴露宿主端口。"
    }
    foreach ($dependency in @('redis', 'mysql')) {
        C $prod @('stop', $dependency) | Out-Null
        Must-Fail { & $ops -Action Check @prodOptions } "$dependency 停止时就绪探针必须失败。"
        C $prod @('exec', '-T', 'app', 'curl', '--fail', '--silent', 'http://127.0.0.1:9001/actuator/health/liveness') | Out-Null
        C $prod @('up', '-d', '--no-build', '--wait', '--wait-timeout', '180', $dependency) | Out-Null
        & $ops -Action Check @prodOptions
    }
    Write-Host '运维演练通过：存量接管升级、归档、中文数据/会话/归档恢复、非空/损坏/错密钥拒绝、镜像回滚、失败发布恢复、生产 HTTPS、端口隔离和依赖故障探针。'
} finally {
    foreach ($projectName in @($source, $restore, $legacy, $prod)) {
        if ($projectName -notmatch '^ops-[a-f0-9]{10}-(source|restore|legacy|prod)$') { throw '清理目标校验失败。' }
        $file = if ($projectName -eq $prod) { $prodFile } else { $composeFile }
        $environment = if ($projectName -eq $prod) { $prodEnv } else { $envFile }
        if ((Test-Path -LiteralPath $file) -and (Test-Path -LiteralPath $environment)) {
            & docker compose -p $projectName --env-file $environment -f $file down -v --remove-orphans
        }
    }
    $env:APP_IMAGE = $originalImage
}
