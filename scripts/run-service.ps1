#Requires -Version 5.1
<#
.SYNOPSIS
  Runs one MediFlow service (with its Maven reactor dependencies) against local infrastructure.

.DESCRIPTION
  Loads the repository .env into the current process, starts PostgreSQL/Redis/Kafka with
  Docker Compose (unless -SkipInfra), then runs `mvn -pl backend/<service> -am spring-boot:run`.

  This is the low-resource workflow: on an 8 GB machine run two or three services at a time
  instead of the full nine-service platform. Service-to-service calls default to localhost,
  so appointment-service expects doctor-service on http://localhost:8085.

.EXAMPLE
  .\scripts\run-service.ps1 -Service patient-service

.EXAMPLE
  .\scripts\run-service.ps1 -Service appointment-service -SkipInfra

.EXAMPLE
  # Load the deterministic demo accounts (admin/doctor/patient/... from DevSeedData)
  .\scripts\run-service.ps1 -Service auth-service -Profiles dev,seed
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Service,

    [switch]$SkipInfra,

    # Overrides SPRING_PROFILES_ACTIVE from .env. Use "dev,seed" to enable the dev seeders.
    [string]$Profiles = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# Runnable services and their ports (lab-service and pharmacy-service are scaffolds).
$ports = [ordered]@{
    'api-gateway'            = 8080
    'auth-service'           = 8081
    'patient-service'        = 8083
    'appointment-service'    = 8084
    'doctor-service'         = 8085
    'billing-service'        = 8087
    'analytics-service'      = 8088
    'notification-service'   = 8089
    'medical-record-service' = 8091
}

if (-not $ports.Contains($Service)) {
    throw "Unknown service '$Service'. Runnable services: $(($ports.Keys) -join ', ')"
}

$envFile = Join-Path $repoRoot '.env'
if (-not (Test-Path $envFile)) {
    throw 'Missing .env. Copy .env.example to .env and set JWT_SECRET and DB_PASSWORD.'
}

# Load .env only for variables that are not already set, so an explicit shell value wins.
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    $index = $line.IndexOf('=')
    if ($index -lt 1) { return }
    $name = $line.Substring(0, $index).Trim()
    $value = $line.Substring($index + 1).Trim().Trim('"')
    if ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name))) {
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

if ([string]::IsNullOrEmpty($env:JWT_SECRET)) {
    throw 'JWT_SECRET is not set. Add it to .env (see .env.example); services have no default.'
}
if ([string]::IsNullOrEmpty($env:DB_PASSWORD)) {
    throw 'DB_PASSWORD is not set. Add it to .env (see .env.example); services have no default.'
}

if ($Profiles) {
    $env:SPRING_PROFILES_ACTIVE = $Profiles
    Write-Host "Spring profiles: $Profiles" -ForegroundColor Cyan
}

if (-not $SkipInfra) {
    Write-Host 'Starting PostgreSQL, Redis and Kafka (docker compose)...' -ForegroundColor Cyan
    docker compose up -d --wait postgres redis kafka
    if ($LASTEXITCODE -ne 0) { throw 'docker compose failed. Is Docker Desktop running?' }
}

Write-Host "Starting $Service on http://localhost:$($ports[$Service])" -ForegroundColor Cyan
Write-Host 'Press Ctrl+C to stop. Infrastructure keeps running; use scripts\stop-infra.ps1 for that.' -ForegroundColor DarkGray

mvn -pl "backend/$Service" -am spring-boot:run
