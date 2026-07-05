param(
    [ValidateSet("x86_64", "arm64-v8a")]
    [string]$Abi = "x86_64",

    [ValidateSet("Debug", "Release")]
    [string]$BuildType = "Debug",

    [string]$CurlRootDir = ".\third_party\curl-android"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

& .\scripts\check-curl-android-layout.ps1 -CurlRootDir $CurlRootDir -RequiredAbis $Abi

$resolvedCurlRoot = Resolve-Path -LiteralPath $CurlRootDir

$defaultJbr = "C:\Program Files\Android\Android Studio\jbr"
if (((-not $env:JAVA_HOME) -or (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) -or (-not (Test-Path "$env:JAVA_HOME\lib\jvm.cfg"))) -and (Test-Path $defaultJbr)) {
    $env:JAVA_HOME = $defaultJbr
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$gradleTask = ":app:assemble$BuildType"
$outputBuildType = $BuildType.ToLowerInvariant()

Write-Host "Building app $BuildType APK with native HTTP/3 enabled..."
Write-Host "abi=$Abi"
Write-Host "curlRootDir=$resolvedCurlRoot"

& .\gradlew.bat $gradleTask `
    "-PnativeHttp3.enableCmake=true" `
    "-PnativeHttp3.enableCurl=true" `
    "-PnativeHttp3.curlRootDir=$resolvedCurlRoot" `
    "-PnativeHttp3.abis=$Abi"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "OK: HTTP/3 APK built."
Write-Host "APK directory: app\build\outputs\apk\$outputBuildType"
