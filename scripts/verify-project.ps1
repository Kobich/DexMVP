$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$defaultJbr = "C:\Program Files\Android\Android Studio\jbr"
if (((-not $env:JAVA_HOME) -or (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) -or (-not (Test-Path "$env:JAVA_HOME\lib\jvm.cfg"))) -and (Test-Path $defaultJbr)) {
    $env:JAVA_HOME = $defaultJbr
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

Write-Host "Building app, remote module, and server..."
& .\gradlew.bat :app:assembleDebug :remote-module:assembleDebug :server:build
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "OK: project build finished."
