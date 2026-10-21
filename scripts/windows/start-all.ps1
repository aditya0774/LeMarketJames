<#
.SYNOPSIS
    Runs the whole app natively on Windows: every backend service plus both Angular dev servers.

.DESCRIPTION
    Windows counterpart of `docker compose up`, for machines that can't run Linux containers.
    Builds the backend once, then opens one PowerShell window per process so each one's log
    stays readable and closing a window stops that process. Every service's defaults already
    point at localhost (database on 5432, auth on 8082, trading on 8084, orders on 8085 and
    staff-ops on 8086), and each app's dev server forwards /api straight to them, so no
    configuration is needed.

    Run .\scripts\windows\setup-db.ps1 once first so the lemarket database exists.

    Kafka is not started: this script only runs what needs no container. Without -Kafka,
    orders-service logs its order events instead of publishing them (its stub publisher) and
    the consumers in trading-service and staff-ops-service start no listener, so nothing here
    needs a broker; their endpoints still answer, with nothing new arriving. See "Kafka (order events)" in the README.

.EXAMPLE
    .\scripts\windows\start-all.ps1
    .\scripts\windows\start-all.ps1 -SkipBuild
    .\scripts\windows\start-all.ps1 -Kafka
#>
param(
    # Skip `mvn install` when nothing under libs/ or services/ changed since the last run.
    [switch]$SkipBuild,
    # Publish order events to a Kafka broker you already have running on localhost:9092, and have
    # the consumers in trading-service and staff-ops-service read them from it.
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

# trading-service first: orders-service fetches prices from it. Their clients are lazy, so the
# rest can start in any order.
# A local stack is a test environment, so the quote-feed test controls (contracts/C4-quote-feed.md)
# and the settlement test control (contracts/C5-config.md) are on, as in Docker Compose. With
# -Kafka its notification and market-activity listeners start too.
$trading = '$env:SIM_CONTROL_ENABLED = ''true''; $env:LMJ_SETTLEMENT_CONTROL_ENABLED = ''true''; ' +
    'mvn -B -pl services/trading-service spring-boot:run'
if ($Kafka) { $trading = '$env:LMJ_EVENTS_CONSUMER = ''kafka''; ' + $trading }
Start-Window 'trading-service :8084' $repoRoot $trading
# The web apps below run over HTTPS (BR-02), so the session cookie is marked Secure, as in Docker Compose.
Start-Window 'auth-service :8082'     $repoRoot ('$env:AUTH_COOKIE_SECURE = ''true''; ' +
    'mvn -B -pl services/auth-service spring-boot:run')
# The same setting Docker Compose gives it; the broker address defaults to localhost:9092.
$orders = '$env:LMJ_EXECUTION_CONTROL_ENABLED = ''true''; ' +
    'mvn -B -pl services/orders-service spring-boot:run'
if ($Kafka) { $orders = '$env:LMJ_EVENTS_PUBLISHER = ''kafka''; ' + $orders }
Start-Window 'orders-service :8085' $repoRoot $orders
# Reports and surveillance alerts. Only the staff app routes to it; with -Kafka its surveillance
# listeners start too.
$staffOps = 'mvn -B -pl services/staff-ops-service spring-boot:run'
if ($Kafka) { $staffOps = '$env:LMJ_EVENTS_CONSUMER = ''kafka''; ' + $staffOps }
Start-Window 'staff-ops-service :8086' $repoRoot $staffOps

$frontend = Join-Path $repoRoot 'apps\frontend'
# --ssl: the dev server makes its own self-signed certificate, so both apps are HTTPS as in Docker (BR-02).
$npm = if (Test-Path (Join-Path $frontend 'node_modules')) { 'npm start -- --ssl' } else { 'npm install; npm start -- --ssl' }
Start-Window 'frontend :4200' $frontend $npm
# The staff app is a second project in the same Angular workspace. It shares node_modules with
# the trading frontend, so it waits for the install above instead of running a second one.
$staff = 'while (-not (Test-Path node_modules\.package-lock.json)) { Start-Sleep 2 }; npm run start:staff -- --ssl'
Start-Window 'staff-frontend :4201' $frontend $staff

Write-Host ''
Write-Host 'Services take ~30s to start. Then open https://localhost:4200 (trading app) or https://localhost:4201 (staff app); accept the browser''s certificate warning once'
Write-Host 'Health check: curl http://localhost:<port>/actuator/health for 8082 (auth), 8084 (trading), 8085 (orders), 8086 (staff-ops)'
