<#
.SYNOPSIS
    Runs the whole app natively on Windows: every backend service plus both Angular dev servers.

.DESCRIPTION
    Windows counterpart of `docker compose up`, for machines that can't run Linux containers.
    Builds the backend once, then opens one PowerShell window per process so each one's log
    stays readable and closing a window stops that process. Every service's defaults already
    point at localhost (database on 5432, services on 8081-8086, the trading gateway on 8089 and
    the staff gateway on 8090), so no configuration is needed.

    Run .\scripts\windows\setup-db.ps1 once first so the lemarket database exists.

    Kafka is not started: this script only runs what needs no container. Without -Kafka,
    buy-sell-service logs its order events instead of publishing them (its stub publisher), so
    nothing here needs a broker. See "Kafka (order events)" in the README.

.EXAMPLE
    .\scripts\windows\start-all.ps1
    .\scripts\windows\start-all.ps1 -SkipBuild
    .\scripts\windows\start-all.ps1 -Kafka
#>
param(
    # Skip `mvn install` when nothing under libs/ or services/ changed since the last run.
    [switch]$SkipBuild,
    # Publish order events to a Kafka broker you already have running on localhost:9092.
    [switch]$Kafka
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
# The same setting Docker Compose gives it; the broker address defaults to localhost:9092.
$buySell = 'mvn -B -pl services/buy-sell-service spring-boot:run'
if ($Kafka) { $buySell = '$env:LMJ_EVENTS_PUBLISHER = ''kafka''; ' + $buySell }
Start-Window 'buy-sell-service :8085' $repoRoot $buySell
Start-Window 'holdings-service :8084' $repoRoot 'mvn -B -pl services/holdings-service spring-boot:run'
# Not behind the trading gateway below: only the staff gateway routes to it.
Start-Window 'reporting-service :8086' $repoRoot 'mvn -B -pl services/reporting-service spring-boot:run'
# 8089 matches the port Docker Compose publishes the gateway on, which proxy.conf.json targets.
Start-Window 'gateway-service :8089'  $repoRoot "mvn -B -pl services/gateway-service spring-boot:run '-Dspring-boot.run.arguments=--server.port=8089'"
# The staff gateway is the same module with the "staff" profile, which sets port 8090 and the
# staff routes. The staff app's proxy.conf.json targets it.
Start-Window 'staff-gateway-service :8090' $repoRoot "mvn -B -pl services/gateway-service spring-boot:run '-Dspring-boot.run.profiles=staff'"

$frontend = Join-Path $repoRoot 'apps\frontend'
$npm = if (Test-Path (Join-Path $frontend 'node_modules')) { 'npm start' } else { 'npm install; npm start' }
Start-Window 'frontend :4200' $frontend $npm
# The staff app is a second project in the same Angular workspace. It shares node_modules with
# the trading frontend, so it waits for the install above instead of running a second one.
$staff = 'while (-not (Test-Path node_modules\.package-lock.json)) { Start-Sleep 2 }; npm run start:staff'
Start-Window 'staff-frontend :4201' $frontend $staff

Write-Host ''
Write-Host 'Services take ~30s to start. Then open http://localhost:4200 (trading app) or http://localhost:4201 (staff app)'
Write-Host 'Health check: curl http://localhost:8089/actuator/health (trading gateway), http://localhost:8090/actuator/health (staff gateway)'
