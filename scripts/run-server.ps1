param(
    [string]$BaseUrl = "http://10.0.2.2:8080",
    [string]$ArtifactPath = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$defaultJbr = "C:\Program Files\Android\Android Studio\jbr"
if (((-not $env:JAVA_HOME) -or (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) -or (-not (Test-Path "$env:JAVA_HOME\lib\jvm.cfg"))) -and (Test-Path $defaultJbr)) {
    $env:JAVA_HOME = $defaultJbr
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$env:DEX_SERVER_BASE_URL = $BaseUrl
if ($ArtifactPath -ne "") {
    $env:DEX_REMOTE_ARTIFACT = $ArtifactPath
}

Write-Host "Building remote module..."
& .\gradlew.bat :remote-module:assembleDebug
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Starting server..."
Write-Host "Local PC URL: http://localhost:8080"
Write-Host "Manifest artifactUrl base: $BaseUrl"
Write-Host "Stop server with Ctrl+C."
& .\gradlew.bat :server:run
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
