#Requires -Version 5.1
<#
.SYNOPSIS
  Runs the frontend-only demo mode (no Java services, no Docker).

.DESCRIPTION
  Starts `vite --mode demo` on port 3000. The SPA answers its own /api/v1 calls with synthetic
  data from the same double the Playwright suite uses. Use the demo banner to sign in as
  Patient, Doctor or Admin and to reset the seeded data.

.EXAMPLE
  .\scripts\start-demo.ps1
#>
[CmdletBinding()]
param(
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$ui = Join-Path $repoRoot 'frontend\mediflow-ui'

Push-Location $ui
try {
    if (-not (Test-Path (Join-Path $ui 'node_modules'))) {
        if ($SkipInstall) { throw 'frontend/mediflow-ui/node_modules is missing; run npm install first.' }
        Write-Host 'Installing frontend dependencies...' -ForegroundColor Cyan
        npm install
        if ($LASTEXITCODE -ne 0) { throw 'npm install failed.' }
    }

    Write-Host 'Starting MediFlow demo on http://localhost:3000' -ForegroundColor Cyan
    Write-Host 'Demo data is simulated; the banner signs you in and resets the data.' -ForegroundColor DarkGray
    npm run demo
} finally {
    Pop-Location
}
