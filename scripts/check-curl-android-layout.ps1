param(
    [string]$CurlRootDir = ".\third_party\curl-android",
    [string[]]$RequiredAbis = @("arm64-v8a", "x86_64")
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not (Test-Path -LiteralPath $CurlRootDir)) {
    throw "curl root directory not found: $CurlRootDir"
}

$resolvedCurlRoot = Resolve-Path -LiteralPath $CurlRootDir
$curlHeader = Join-Path $resolvedCurlRoot "include\curl\curl.h"

Write-Host "Checking curl Android bundle..."
Write-Host "curlRootDir=$resolvedCurlRoot"

if (-not (Test-Path -LiteralPath $curlHeader)) {
    throw "Missing curl header: $curlHeader"
}

foreach ($abi in $RequiredAbis) {
    $sharedLibrary = Join-Path $resolvedCurlRoot "libs\$abi\libcurl.so"
    $staticLibrary = Join-Path $resolvedCurlRoot "libs\$abi\libcurl.a"
    if ((-not (Test-Path -LiteralPath $sharedLibrary)) -and (-not (Test-Path -LiteralPath $staticLibrary))) {
        throw "Missing libcurl for ABI '$abi'. Expected one of: $sharedLibrary or $staticLibrary"
    }
}

Write-Host ""
Write-Host "OK: curl Android bundle layout is valid."
