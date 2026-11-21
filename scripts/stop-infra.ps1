#Requires -Version 5.1
<#
.SYNOPSIS
  Stops the local Docker infrastructure started for development.

.DESCRIPTION
  `docker compose down` stops PostgreSQL, Redis and Kafka but keeps their named volumes, so the
  next start reuses the databases and the Kafka log. Use -RemoveVolumes for a clean slate
  (deletes all local MediFlow data, including Redis revocation markers).

.EXAMPLE
  .\scripts\stop-infra.ps1

.EXAMPLE
  .\scripts\stop-infra.ps1 -RemoveVolumes
#>
[CmdletBinding()]
param(
    [switch]$RemoveVolumes
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if ($RemoveVolumes) {
    Write-Host 'Stopping infrastructure and deleting volumes...' -ForegroundColor Yellow
    docker compose down -v
} else {
    Write-Host 'Stopping infrastructure (volumes are kept)...' -ForegroundColor Cyan
    docker compose down
}

if ($LASTEXITCODE -ne 0) { throw 'docker compose down failed.' }
Write-Host 'Infrastructure stopped.' -ForegroundColor Green
