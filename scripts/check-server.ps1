param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

$healthUrl = "$BaseUrl/health"
$manifestUrl = "$BaseUrl/api/v1/modules/active"
$artifactUrl = "$BaseUrl/api/v1/modules/hello/1/artifact"

Write-Host "Checking $healthUrl"
$health = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing
Write-Host "health: $($health.Content)"

Write-Host ""
Write-Host "Checking $manifestUrl"
$manifest = Invoke-WebRequest -Uri $manifestUrl -UseBasicParsing
Write-Host "manifest status: $($manifest.StatusCode)"
Write-Host $manifest.Content

Write-Host ""
Write-Host "Checking $artifactUrl"
$artifact = Invoke-WebRequest -Uri $artifactUrl -UseBasicParsing
Write-Host "artifact status: $($artifact.StatusCode)"
Write-Host "artifact bytes: $($artifact.RawContentLength)"
