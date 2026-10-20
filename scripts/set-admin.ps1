param([Parameter(Mandatory = $true)][ValidateRange(1, 9223372036854775807)][long]$UserId)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
# 10/20：只能由具备数据库操作权限的部署者指定现有账号，不提供公开提权接口。
$sql = "UPDATE ``user`` SET role=1 WHERE id=$UserId AND status=1; SELECT id,nickname,role,status FROM ``user`` WHERE id=$UserId;"
$sql | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"'
if ($LASTEXITCODE -ne 0) { throw '管理员设置失败，请检查数据库连接。' }
Write-Output '请核对查询结果：仅 role=1 且 status=1 的账号可管理；空结果表示用户不存在。'
