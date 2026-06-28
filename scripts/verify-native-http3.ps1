$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$defaultJbr = "C:\Program Files\Android\Android Studio\jbr"
if (((-not $env:JAVA_HOME) -or (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) -or (-not (Test-Path "$env:JAVA_HOME\lib\jvm.cfg"))) -and (Test-Path $defaultJbr)) {
    $env:JAVA_HOME = $defaultJbr
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

Write-Host "Building native-http3 with CMake enabled..."
& .\gradlew.bat :native-http3:assembleDebug "-PnativeHttp3.enableCmake=true"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "OK: native-http3 CMake build finished."
