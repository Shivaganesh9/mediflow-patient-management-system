#Requires -Version 5.1
<#
.SYNOPSIS
  Runs the lightweight checks that are practical on an 8 GB Windows machine.

.DESCRIPTION
  Frontend type checking and production build, the mocked-API Playwright suite (38 + 5 demo
  tests), and the static release-readiness checks. No Java services, no Docker and no
  Testcontainers are required.

  This is NOT a substitute for `mvn -B -ntp verify` or the cross-service E2E suite; both run in
  GitHub Actions. See .github/workflows/ci.yml for the full matrix.

.EXAMPLE
  .\scripts\test-light.ps1

.EXAMPLE
  .\scripts\test-light.ps1 -SkipE2E -SkipInstall
#>
[CmdletBinding()]
param(
    [switch]$SkipInstall,
    [switch]$SkipE2E
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$ui = Join-Path $repoRoot 'frontend\mediflow-ui'

function Invoke-Step {
    param([string]$Label, [scriptblock]$Action)
    Write-Host "==> $Label" -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0) { throw "$Label failed (exit $LASTEXITCODE)." }
}

Push-Location $ui
try {
    if (-not $SkipInstall) {
        Invoke-Step 'Frontend dependencies (npm install)' { npm install --no-audit --no-fund }
    }
    Invoke-Step 'Frontend type checking and production build (npm run build)' { npm run build }
    Invoke-Step 'Playwright typecheck (npm run typecheck:e2e)' { npm run typecheck:e2e }
    if (-not $SkipE2E) {
        Invoke-Step 'Playwright browser suite (npm run test:e2e)' { npm run test:e2e }
    }
} finally {
    Pop-Location
}

Push-Location $repoRoot
try {
    Invoke-Step 'Static release-readiness checks (node testing/release/check-release-readiness.mjs)' {
        node testing/release/check-release-readiness.mjs
    }
} finally {
    Pop-Location
}

Write-Host 'Lightweight checks passed.' -ForegroundColor Green
