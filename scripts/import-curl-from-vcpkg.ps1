param(
    [Parameter(Mandatory = $true)]
    [string]$VcpkgRoot,

    [string]$CurlRootDir = ".\third_party\curl-android",

    [string[]]$Triplets = @("arm64-android", "x64-android")
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$resolvedVcpkgRoot = Resolve-Path -LiteralPath $VcpkgRoot
$resolvedCurlRoot = New-Item -ItemType Directory -Force -Path $CurlRootDir

$tripletToAbi = @{
    "arm64-android" = "arm64-v8a"
    "x64-android" = "x86_64"
    "arm-android" = "armeabi-v7a"
    "arm-neon-android" = "armeabi-v7a"
    "x86-android" = "x86"
}

$headersCopied = $false

foreach ($triplet in $Triplets) {
    if (-not $tripletToAbi.ContainsKey($triplet)) {
        throw "Unsupported triplet '$triplet'. Supported: $($tripletToAbi.Keys -join ', ')"
    }

    $abi = $tripletToAbi[$triplet]
    $installedDir = Join-Path $resolvedVcpkgRoot "installed\$triplet"
    $header = Join-Path $installedDir "include\curl\curl.h"
    $releaseLibDir = Join-Path $installedDir "lib"
    $debugLibDir = Join-Path $installedDir "debug\lib"
    $sharedLibcurl = Join-Path $releaseLibDir "libcurl.so"
    $staticLibcurl = Join-Path $releaseLibDir "libcurl.a"

    if (-not (Test-Path -LiteralPath $header)) {
        throw "curl.h not found for $triplet. Expected: $header"
    }

    if ((-not (Test-Path -LiteralPath $sharedLibcurl)) -and (-not (Test-Path -LiteralPath $staticLibcurl))) {
        throw "libcurl not found for $triplet. Expected one of: $sharedLibcurl or $staticLibcurl"
    }

    if (-not $headersCopied) {
        $targetInclude = Join-Path $resolvedCurlRoot "include"
        New-Item -ItemType Directory -Force -Path $targetInclude | Out-Null
        Copy-Item -Recurse -Force -LiteralPath (Join-Path $installedDir "include\curl") -Destination $targetInclude
        $headersCopied = $true
    }

    $targetLibDir = Join-Path $resolvedCurlRoot "libs\$abi"
    New-Item -ItemType Directory -Force -Path $targetLibDir | Out-Null

    Get-ChildItem -LiteralPath $releaseLibDir -Filter "*.so" -File -ErrorAction SilentlyContinue |
        Copy-Item -Destination $targetLibDir -Force

    Get-ChildItem -LiteralPath $releaseLibDir -Filter "*.a" -File -ErrorAction SilentlyContinue |
        Copy-Item -Destination $targetLibDir -Force

    if (Test-Path -LiteralPath $debugLibDir) {
        Get-ChildItem -LiteralPath $debugLibDir -Filter "*.so" -File -ErrorAction SilentlyContinue |
            Where-Object { -not (Test-Path -LiteralPath (Join-Path $targetLibDir $_.Name)) } |
            Copy-Item -Destination $targetLibDir -Force

        Get-ChildItem -LiteralPath $debugLibDir -Filter "*.a" -File -ErrorAction SilentlyContinue |
            Where-Object { -not (Test-Path -LiteralPath (Join-Path $targetLibDir $_.Name)) } |
            Copy-Item -Destination $targetLibDir -Force
    }

    Write-Host "Imported $triplet -> $abi"
}

Write-Host ""
Write-Host "OK: imported curl bundle to $resolvedCurlRoot"
Write-Host "Next: .\scripts\check-curl-android-layout.ps1"
