# 编译 MC Chunk Protector NeoForge mod（开发验证用，绕过 Gradle）
# 依赖：dev-server 已安装的 NeoForge libraries（classpath.txt）
# 输出：dist/mods/mcchunkprotector-1.0.0.jar
param(
    [string]$ServerDir = "$PSScriptRoot\..\dev-server"
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$javaSrc = Join-Path $root 'mod\src\main\java'
$resSrc  = Join-Path $root 'mod\src\main\resources'
$out     = Join-Path $root 'mod\build\classes'
$jarOut  = Join-Path $root 'dist\mods'
$classpathFile = Join-Path $ServerDir 'classpath.txt'

if (-not (Test-Path $classpathFile)) {
    Write-Error "缺少 classpath.txt。请先在 dev-server 运行:
    \$jars = Get-ChildItem -Path '<dev-server>\libraries' -Recurse -Filter *.jar | Where-Object {\$_.Name -notmatch 'sources|javadoc'} | Select-Object -ExpandProperty FullName
    Set-Content -Path '<dev-server>\classpath.txt' -Value (\$jars -join ';')
"
}
$cp = Get-Content $classpathFile -Raw

# 清理旧产物
if (Test-Path $out)  { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null
New-Item -ItemType Directory -Force -Path $jarOut | Out-Null

# 收集 java 文件
$sources = @(Get-ChildItem $javaSrc -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
if ($sources.Count -eq 0) { Write-Error "无 java 源文件" }

Write-Host "javac 编译 $($sources.Count) 个文件..."
& javac -proc:none -encoding UTF-8 -source 21 -target 21 -nowarn -cp $cp -d $out $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# 复制资源（mods.toml / mixins.json），保留目录结构（META-INF/ 等）
Copy-Item -Path "$resSrc\*" -Destination $out -Recurse -Force -ErrorAction Stop

# 打包 jar
$jarPath = Join-Path $jarOut 'mcchunkprotector-1.0.0.jar'
& jar cf $jarPath -C $out .
Write-Host "已生成: $jarPath"
Get-Item $jarPath | Select-Object FullName,Length
