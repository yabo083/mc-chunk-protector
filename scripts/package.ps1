# 打包 MC Chunk Protector 部署包到 dist/deploy/
# 用法：pwsh scripts/package.ps1
param(
    [switch]$SkipRebuild = $false
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$deploy = Join-Path $root 'dist\deploy'
$jar = Join-Path $root 'dist\mods\mcchunkprotector-1.0.0.jar'

if (-not $SkipRebuild) {
    & (Join-Path $root 'mod\build.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'mod build failed' }
}
if (-not (Test-Path $jar)) {
    Write-Error "未找到 mod: $jar"
}

# 清空旧的部署目录
if (Test-Path $deploy) { Remove-Item $deploy -Recurse -Force }
New-Item -ItemType Directory -Force -Path $deploy | Out-Null

# 1) 服务端 mod
New-Item -ItemType Directory -Force -Path (Join-Path $deploy 'mods') | Out-Null
Copy-Item $jar (Join-Path $deploy 'mods\')

# 2) 配置模板 + schema
New-Item -ItemType Directory -Force -Path (Join-Path $deploy 'config-schema') | Out-Null
Copy-Item (Join-Path $root 'config-schema\regions.schema.json') (Join-Path $deploy 'config-schema\')
Copy-Item (Join-Path $root 'config-schema\regions.example.json') (Join-Path $deploy 'config-schema\regions.json.example')

Write-Host "已生成部署包: $deploy"
Get-ChildItem $deploy -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($deploy.Length + 1)
    "{0,8:N0} B  {1}" -f $_.Length, $rel
}
