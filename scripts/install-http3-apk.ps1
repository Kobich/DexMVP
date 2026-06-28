param(
    [ValidateSet("x86_64", "arm64-v8a")]
    [string]$Abi = "x86_64",

    [switch]$Build
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$platformTools = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools"
if ((Get-Command adb -ErrorAction SilentlyContinue) -eq $null -and (Test-Path $platformTools)) {
    $env:Path = "$env:Path;$platformTools"
}

if ($Build) {
    & .\scripts\build-http3-apk.ps1 -Abi $Abi
}

$apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) {
    throw "APK not found: $apk. Run .\scripts\build-http3-apk.ps1 first."
}

Write-Host "Installing APK..."
& adb install -r $apk
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Launching app..."
& adb shell monkey -p com.engboost.dexmvp 1
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "OK: app installed and launched."
