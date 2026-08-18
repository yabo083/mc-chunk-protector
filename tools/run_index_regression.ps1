$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$serverDir = Join-Path $root 'dev-server'
$classes = Join-Path $root 'mod\build\classes'
$testClasses = Join-Path $PSScriptRoot 'test-classes'
$cp = (Get-Content (Join-Path $serverDir 'classpath.txt') -Raw).Trim()

if (Test-Path $testClasses) { Remove-Item $testClasses -Recurse -Force }
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
javac -encoding UTF-8 -source 21 -target 21 -cp "$classes;$cp" -d $testClasses (Join-Path $PSScriptRoot 'FrozenRegionManagerRegression.java')
if ($LASTEXITCODE -ne 0) { throw 'index regression compile failed' }
java -cp "$testClasses;$classes;$cp" FrozenRegionManagerRegression
if ($LASTEXITCODE -ne 0) { throw 'index regression failed' }
