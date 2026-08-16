# 打包 MC Chunk Protector 部署包到 dist/deploy/
# 用法：pwsh scripts/package.ps1
param(
    [switch]$SkipRebuild = $false
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$deploy = Join-Path $root 'dist\deploy'
$exe = Join-Path $root 'dist\gui\McChunkProtector.Gui.exe'

if (-not (Test-Path $exe)) {
    Write-Error "未找到发布 exe: $exe。请先运行: dotnet publish ...  或加 -SkipRebuild 跳过检查。"
}

# 清空旧的部署目录
if (Test-Path $deploy) { Remove-Item $deploy -Recurse -Force }
New-Item -ItemType Directory -Force -Path $deploy | Out-Null

# 1) GUI 可执行文件
New-Item -ItemType Directory -Force -Path (Join-Path $deploy 'gui') | Out-Null
Copy-Item $exe (Join-Path $deploy 'gui\')

# 2) KubeJS 服务端脚本（甲方复制到 <server>/kubejs/server_scripts/）
New-Item -ItemType Directory -Force -Path (Join-Path $deploy 'kubejs\server_scripts') | Out-Null
Copy-Item (Join-Path $root 'kubejs-scripts\ChunkProtector.js') (Join-Path $deploy 'kubejs\server_scripts\')

# 3) 配置模板 + schema
New-Item -ItemType Directory -Force -Path (Join-Path $deploy 'config-schema') | Out-Null
Copy-Item (Join-Path $root 'config-schema\regions.schema.json') (Join-Path $deploy 'config-schema\')
Copy-Item (Join-Path $root 'config-schema\regions.example.json') (Join-Path $deploy 'config-schema\regions.json.example')

Write-Host "已生成部署包: $deploy"
Get-ChildItem $deploy -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($deploy.Length + 1)
    "{0,8:N0} B  {1}" -f $_.Length, $rel
}
