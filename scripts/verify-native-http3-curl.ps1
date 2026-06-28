param(
    [string]$CurlRootDir = ".\third_party\curl-android",
    [string[]]$Abis = @("arm64-v8a", "x86_64")
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

& .\scripts\check-curl-android-layout.ps1 -CurlRootDir $CurlRootDir -RequiredAbis $Abis

$resolvedCurlRoot = Resolve-Path -LiteralPath $CurlRootDir

$defaultJbr = "C:\Program Files\Android\Android Studio\jbr"
if (((-not $env:JAVA_HOME) -or (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) -or (-not (Test-Path "$env:JAVA_HOME\lib\jvm.cfg"))) -and (Test-Path $defaultJbr)) {
    $env:JAVA_HOME = $defaultJbr
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

Write-Host "Building native-http3 with CMake and libcurl enabled..."
Write-Host "curlRootDir=$resolvedCurlRoot"
& .\gradlew.bat :native-http3:assembleDebug `
    "-PnativeHttp3.enableCmake=true" `
    "-PnativeHttp3.enableCurl=true" `
    "-PnativeHttp3.curlRootDir=$resolvedCurlRoot" `
    "-PnativeHttp3.abis=$($Abis -join ',')"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "OK: native-http3 libcurl build finished."
