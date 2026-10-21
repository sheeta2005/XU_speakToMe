param([Parameter(Mandatory = $true)][string]$DevToolsPath)
$ErrorActionPreference = 'Stop'
# 10/21：仅使用本机已安装的微信编译器，不登录、不上传；产物放入被忽略的 tmp。
$repoPath = Split-Path $PSScriptRoot -Parent
$compilerPath = Join-Path $DevToolsPath 'code/package.nw/node_modules/wcc-exec'
$wccPath = Join-Path $compilerPath 'wcc.exe'
$wcscPath = Join-Path $compilerPath 'wcsc.exe'
if (!(Test-Path -LiteralPath $wccPath) -or !(Test-Path -LiteralPath $wcscPath)) {
    throw '未找到微信编译器，请指定包含 cli.bat 的开发者工具安装目录。'
}
$outputPath = Join-Path $repoPath 'tmp/wechat-check'
New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
$miniPath = Join-Path $repoPath 'miniprogram'
Push-Location $miniPath
try {
    # 10/21：编译器使用正斜杠解析相对导入，避免 Windows 路径影响 common.wxss。
    $templates = @(Get-ChildItem -LiteralPath (Join-Path $miniPath 'pages') -Filter '*.wxml' -Recurse |
        ForEach-Object { $_.FullName.Substring($miniPath.Length + 1).Replace('\', '/') })
    $styles = @(Get-ChildItem -LiteralPath $miniPath -Filter '*.wxss' -Recurse |
        ForEach-Object { $_.FullName.Substring($miniPath.Length + 1).Replace('\', '/') })
    & $wccPath -o (Join-Path $outputPath 'wxml.js') @templates
    if ($LASTEXITCODE -ne 0) { throw 'WXML 编译失败。' }
    & $wcscPath -o (Join-Path $outputPath 'wxss.js') @styles
    if ($LASTEXITCODE -ne 0) { throw 'WXSS 编译失败。' }
    Write-Output "微信编译检查通过：$($templates.Count) 个模板，$($styles.Count) 个样式文件。仍需 IDE 页面运行和真机验收。"
} finally {
    Pop-Location
}
