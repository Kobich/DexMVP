param(
    [ValidateSet("x86_64", "arm64-v8a")]
    [string]$Abi = "x86_64",

    [ValidateSet("Debug", "Release")]
    [string]$BuildType = "Debug",

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
    & .\scripts\build-http3-apk.ps1 -Abi $Abi -BuildType $BuildType
}

$outputBuildType = $BuildType.ToLowerInvariant()
$apkName = if ($BuildType -eq "Debug") { "app-debug.apk" } else { "app-release-unsigned.apk" }
$apk = Join-Path $repoRoot "app\build\outputs\apk\$outputBuildType\$apkName"
if (-not (Test-Path $apk)) {
    throw "APK not found: $apk. Run .\scripts\build-http3-apk.ps1 -BuildType $BuildType first."
}

if ($BuildType -eq "Release" -and $apkName.EndsWith("-unsigned.apk")) {
    throw "Release APK is unsigned and cannot be installed directly: $apk. Add release signing config or install a signed artifact."
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
