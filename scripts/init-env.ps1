param()
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
# 10/20：首次初始化独立随机凭证，拒绝覆盖已有密钥，避免历史数据无法解密。
if (Test-Path -LiteralPath '.env') { throw '.env 已存在，请自行检查；初始化脚本不会覆盖现有密钥。' }
function New-Secret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}
$content = Get-Content -LiteralPath '.env.example' -Raw -Encoding UTF8
foreach ($name in @('AES_KEY', 'MYSQL_ROOT_PASSWORD', 'MYSQL_PASSWORD', 'REDIS_PASSWORD')) {
    $content = [regex]::Replace($content, "(?m)^$name=.*$", "$name=$(New-Secret)")
}
[IO.File]::WriteAllText((Join-Path (Get-Location) '.env'), $content, (New-Object Text.UTF8Encoding $false))
Write-Output '已生成 .env。请保留并备份，接下来运行 docker compose up -d --build --wait。'
