<#
.SYNOPSIS
    Runs the whole app natively on Windows: every backend service plus the Angular dev server.

.DESCRIPTION
    Windows counterpart of `docker compose up`, for machines that can't run Linux containers.
    Builds the backend once, then opens one PowerShell window per process so each one's log
    stays readable and closing a window stops that process. Every service's defaults already
    point at localhost (database on 5432, services on 8081-8085), so no configuration is needed.

    Run .\scripts\windows\setup-db.ps1 once first so the lemarket database exists.

.EXAMPLE
    .\scripts\windows\start-all.ps1
    .\scripts\windows\start-all.ps1 -SkipBuild
#>
param(
    # Skip `mvn install` when nothing under libs/ or services/ changed since the last run.
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

if (-not $SkipBuild) {
    Write-Host 'Building backend (mvn install -DskipTests)...'
    Push-Location $repoRoot
    try {
        mvn -B -q -DskipTests install
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    } finally {
        Pop-Location
    }
}

function Start-Window([string]$Title, [string]$WorkingDir, [string]$Command) {
    $script = "`$Host.UI.RawUI.WindowTitle = '$Title'; $Command"
    Start-Process powershell -WorkingDirectory $WorkingDir -ArgumentList '-NoExit', '-Command', $script | Out-Null
    Write-Host "Started $Title"
}

# market-service first: core and holdings fetch prices from it. Their clients are lazy, so the
# rest can start in any order.
# A local stack is a test environment, so the quote-feed test controls are on (contracts/C4-quote-feed.md).
Start-Window 'market-service :8083'   $repoRoot '$env:SIM_CONTROL_ENABLED = ''true''; mvn -B -pl services/market-service spring-boot:run'
Start-Window 'auth-service :8082'     $repoRoot 'mvn -B -pl services/auth-service spring-boot:run'
Start-Window 'core-service :8081'     $repoRoot 'mvn -B -pl services/core-service spring-boot:run'
Start-Window 'buy-sell-service :8085' $repoRoot 'mvn -B -pl services/buy-sell-service spring-boot:run'
Start-Window 'holdings-service :8084' $repoRoot 'mvn -B -pl services/holdings-service spring-boot:run'
# 8089 matches the port Docker Compose publishes the gateway on, which proxy.conf.json targets.
Start-Window 'gateway-service :8089'  $repoRoot "mvn -B -pl services/gateway-service spring-boot:run '-Dspring-boot.run.arguments=--server.port=8089'"

$frontend = Join-Path $repoRoot 'apps\frontend'
$npm = if (Test-Path (Join-Path $frontend 'node_modules')) { 'npm start' } else { 'npm install; npm start' }
Start-Window 'frontend :4200' $frontend $npm

Write-Host ''
Write-Host 'Services take ~30s to start. Then open http://localhost:4200'
Write-Host 'Health check: curl http://localhost:8089/actuator/health'
