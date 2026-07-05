param(
    [Parameter(Mandatory = $true)]
    [string]$IpAddress,

    [string]$CaddyCertDir = "E:\caddy\certs",

    [string]$AndroidCaTarget = ".\app\src\main\res\raw\dexmvp_root_ca.crt",

    [string]$OpenSslExe = "C:\Program Files\Git\usr\bin\openssl.exe",

    [string]$RootCommonName = "DexMVP Local Root CA"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not (Test-Path -LiteralPath $OpenSslExe)) {
    throw "OpenSSL not found: $OpenSslExe"
}

$resolvedCaddyCertDir = New-Item -ItemType Directory -Force -Path $CaddyCertDir
$androidCaDir = Split-Path -Parent $AndroidCaTarget
New-Item -ItemType Directory -Force -Path $androidCaDir | Out-Null

$rootKey = Join-Path $resolvedCaddyCertDir.FullName "dexmvp-root-ca.key"
$rootCert = Join-Path $resolvedCaddyCertDir.FullName "dexmvp-root-ca.crt"
$serverKey = Join-Path $resolvedCaddyCertDir.FullName "dexmvp-ip.key"
$serverCsr = Join-Path $resolvedCaddyCertDir.FullName "dexmvp-ip.csr"
$serverCert = Join-Path $resolvedCaddyCertDir.FullName "dexmvp-ip.crt"
$serverConfig = Join-Path $resolvedCaddyCertDir.FullName "dexmvp-ip-openssl.cnf"
$serialFile = Join-Path $resolvedCaddyCertDir.FullName "dexmvp-root-ca.srl"

@"
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
req_extensions = v3_req

[dn]
CN = $IpAddress

[v3_req]
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
IP.1 = $IpAddress
"@ | Set-Content -Path $serverConfig -Encoding ascii

& $OpenSslExe genrsa -out $rootKey 4096
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $OpenSslExe req -x509 -new -nodes `
    -key $rootKey `
    -sha256 `
    -days 3650 `
    -out $rootCert `
    -subj "/CN=$RootCommonName"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $OpenSslExe genrsa -out $serverKey 2048
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $OpenSslExe req -new `
    -key $serverKey `
    -out $serverCsr `
    -config $serverConfig
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (Test-Path -LiteralPath $serialFile) {
    Remove-Item -Force -LiteralPath $serialFile
}

& $OpenSslExe x509 -req `
    -in $serverCsr `
    -CA $rootCert `
    -CAkey $rootKey `
    -CAcreateserial `
    -out $serverCert `
    -days 365 `
    -sha256 `
    -extensions v3_req `
    -extfile $serverConfig
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Copy-Item -Force -LiteralPath $rootCert -Destination $AndroidCaTarget

Write-Host "OK: generated Caddy IP certificate chain."
Write-Host "IP SAN: $IpAddress"
Write-Host "Root CA public cert: $rootCert"
Write-Host "Root CA private key: $rootKey"
Write-Host "Caddy server cert: $serverCert"
Write-Host "Caddy server key: $serverKey"
Write-Host "Android CA resource: $AndroidCaTarget"
Write-Host ""
Write-Host "Do not commit or ship .key files. APK contains only the public root CA certificate."
