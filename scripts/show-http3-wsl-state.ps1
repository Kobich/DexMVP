$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$wsl = Get-Command wsl -ErrorAction SilentlyContinue
if ($wsl -eq $null) {
    throw "wsl command not found. Install WSL or provide WSL IP manually."
}

$wslIp = (& wsl.exe sh -lc "hostname -I | awk '{print `$1}'").Trim()
if (-not $wslIp) {
    throw "Cannot detect WSL IP."
}

$nginxUpstream = (& wsl.exe sh -lc "grep -R 'proxy_pass http://' /etc/nginx/conf.d/dexmvp-http3.conf 2>/dev/null | head -n 1 | sed -E 's/.*proxy_pass http:\/\/([^;]+);.*/\1/'").Trim()

$baseUrl = "https://${wslIp}:8443"

Write-Host "WSL IP: $wslIp"
if ($nginxUpstream) {
    Write-Host "NGINX upstream: http://$nginxUpstream"
}
Write-Host ""
Write-Host "Use this in Android app:"
Write-Host $baseUrl
Write-Host ""
Write-Host "Start Ktor server with:"
Write-Host ".\scripts\run-server.ps1 -BaseUrl `"$baseUrl`""
Write-Host ""
Write-Host "WSL checks:"
Write-Host "curl -k https://127.0.0.1:8443/health"
Write-Host "curl -k https://127.0.0.1:8443/api/v1/modules/active"
