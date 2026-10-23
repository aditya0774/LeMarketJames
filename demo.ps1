# LEAP demo commands for Windows PowerShell (native stack, no Docker).
#
# Load once in each PowerShell window, from the folder this file is in:
#     Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#     . .\demo.ps1
# (the dot and the space matter: they load the commands into this window). Then type demo_help.
#
# The repo is expected at $HOME\LeMarketJames. If it's elsewhere, first run:
#     $env:LMJ_REPO = 'C:\path\to\LeMarketJames'

$Repo  = if ($env:LMJ_REPO) { $env:LMJ_REPO } else { Join-Path $HOME 'LeMarketJames' }
$ACC_A = 49                                     # Client A, Lebron@lemarket.com
$DbOwnerPassword = 'changeme'                   # setup-db.ps1 default for the lemarket owner
$LmjHost = if ($env:LMJ_HOST) { $env:LMJ_HOST } else { 'localhost' }
# curl.exe, never PowerShell's curl alias; plain curl only when testing off Windows.
$CurlExe = if ($IsLinux -or $IsMacOS) { 'curl' } else { 'curl.exe' }
$NullDev = if ($IsLinux -or $IsMacOS) { '/dev/null' } else { 'NUL' }

if (-not (Test-Path (Join-Path $Repo 'scripts\windows\start-all.ps1'))) {
    Write-Warning "Repo not found at $Repo. Run: `$env:LMJ_REPO = 'C:\path\to\LeMarketJames'  then load demo.ps1 again."
}

# ---- Demo settings: every service window started from here inherits them -------------
$env:SIM_RESPECT_MARKET_HOURS = 'false'              # trading-service: prices move at any hour
$env:LMJ_EXECUTION_RESPECT_MARKET_HOURS = 'false'    # orders-service: orders fill at any hour

# Secrets live in the repo's git-ignored .env. The first load creates it with random values.
$EnvFile = Join-Path $Repo '.env'
if ((Test-Path $Repo) -and -not (Test-Path $EnvFile)) {
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $jwt = New-Object byte[] 48; $rng.GetBytes($jwt)
    $tok = New-Object byte[] 32; $rng.GetBytes($tok)
    $hex = -join ($tok | ForEach-Object { $_.ToString('x2') })
    Set-Content -Path $EnvFile -Encoding ASCII -Value @("JWT_SECRET=$([Convert]::ToBase64String($jwt))", "INTERNAL_API_TOKEN=$hex")
    Write-Host "Created $EnvFile with new secrets (git ignores it)."
}
if (Test-Path $EnvFile) {
    foreach ($line in Get-Content $EnvFile) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { Set-Item -Path "Env:$($Matches[1])" -Value $Matches[2] }
    }
}
$InternalToken = if ($env:INTERNAL_API_TOKEN) { $env:INTERNAL_API_TOKEN } else { 'change-this-development-only-internal-token' }

# ---- Helpers ------------------------------------------------------------------------------
function Find-PgTool([string]$Name) {
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\$Name.exe" -ErrorAction SilentlyContinue |
        Sort-Object { [int]$_.Directory.Parent.Name } -Descending | Select-Object -First 1 -ExpandProperty FullName
}
$Psql = Find-PgTool 'psql'

function sqlo([string]$Sql) {
    if (-not $Psql) { Write-Warning 'psql not found: install PostgreSQL or add its bin folder to PATH.'; return }
    $Sql = ($Sql -replace '\s+', ' ').Trim()    # one line: Windows passes it to psql intact
    $env:PGPASSWORD = $DbOwnerPassword; $env:PGCLIENTENCODING = 'UTF8'
    try { & $Psql -h $LmjHost -U lemarket -d lemarket -c $Sql } finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
}

# Internal test switches: PowerShell's own HTTP client, so JSON bodies aren't mangled.
function internal([string]$Method, [string]$Url, [string]$Body) {
    $p = @{ Method = $Method; Uri = $Url; Headers = @{ 'X-Internal-Token' = $InternalToken }; ContentType = 'application/json' }
    if ($Body) { $p.Body = $Body }
    try { Invoke-RestMethod @p | ConvertTo-Json -Compress }
    catch { Write-Warning "$Method $Url failed: $($_.Exception.Message)" }
}

# HTTP status of a request made with a given session token (goes straight to trading-service).
function http_code([string]$Token) {
    & $CurlExe -s -o $NullDev -w '%{http_code}' -H "Cookie: jwt=$Token" "http://${LmjHost}:8084/api/v1/holdings"
}

function sign_in([string]$Email) {
    $body = @{ username = $Email; password = 'Pass123!' } | ConvertTo-Json -Compress
    try { $r = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://${LmjHost}:8082/api/auth/login" -Body $body -ContentType 'application/json' }
    catch { throw "Sign-in as $Email failed: $($_.Exception.Message)" }
    $cookie = ($r.Headers['Set-Cookie'] | Out-String)
    if ($cookie -match 'jwt=([^;,\s]+)') { return $Matches[1] }
    throw "No session cookie returned for $Email"
}

function is_up([int]$Port) {
    try { return (Invoke-RestMethod -TimeoutSec 3 "http://${LmjHost}:$Port/actuator/health").status -eq 'UP' } catch { return $false }
}
function wait_up([int]$Port = 8085, [int]$TimeoutSec = 240) {
    $start = Get-Date
    while (-not (is_up $Port)) {
        if (((Get-Date) - $start).TotalSeconds -gt $TimeoutSec) { Write-Warning "Port $Port not UP after $TimeoutSec s: check its window."; return }
        Start-Sleep -Seconds 2
    }
    Write-Host ("UP ({0}) after {1:N0} s" -f $Port, ((Get-Date) - $start).TotalSeconds)
}

# One service window, titled exactly as start-all.ps1 titles it, so stop-all.ps1 still finds it.
function Start-ServiceWindow([string]$Title, [string]$Command) {
    $script = "`$Host.UI.RawUI.WindowTitle = '$Title'; $Command"
    Start-Process powershell -WorkingDirectory $Repo -ArgumentList '-NoExit', '-Command', $script | Out-Null
    Write-Host "Started $Title"
}
# Stops a service window started by start-all.ps1 or by this file, with the Java process inside it.
function Stop-ServiceWindow([string]$Title, [int]$Port) {
    Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe'" |
        Where-Object { $_.CommandLine -like "*WindowTitle = '$Title'*" } |
        ForEach-Object { taskkill /F /T /PID $_.ProcessId 2>&1 | Out-Null }
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Sort-Object OwningProcess -Unique | ForEach-Object { taskkill /F /T /PID $_.OwningProcess 2>&1 | Out-Null }
    Write-Host "Stopped $Title"
}
$OrdersTitle = 'orders-service :8085'
$OrdersCmd   = '$env:LMJ_EXECUTION_CONTROL_ENABLED = ''true''; mvn -B -pl services/orders-service spring-boot:run'
$StaffTitle  = 'staff-ops-service :8086'
$StaffCmd    = 'mvn -B -pl services/staff-ops-service spring-boot:run'
$DemoDir     = Join-Path $HOME 'lmj-demo'
New-Item -ItemType Directory -Force -Path $DemoDir | Out-Null

# ---- Stack --------------------------------------------------------------------------------
function start_stack {   # builds once (~1-2 min), opens one window per service, waits for all four
    & (Join-Path $Repo 'scripts\windows\start-all.ps1') -SessionTimeout PT5M @args
    foreach ($p in 8082, 8084, 8085, 8086) { wait_up $p 420 }
}
function stop_stack { & (Join-Path $Repo 'scripts\windows\stop-all.ps1') }
function health { foreach ($p in 8082, 8084, 8085, 8086) { "{0}  {1}" -f $p, $(if (is_up $p) { 'UP' } else { 'DOWN' }) } }

# ---- Evidence queries ---------------------------------------------------------------------
function totals { sqlo @"
SELECT a.account_id, a.cash_balance,
  (SELECT count(*) FROM orders o WHERE o.account_id = a.account_id AND o.order_status = 'FILLED') AS filled_orders,
  (SELECT count(*) FROM orders o WHERE o.account_id = a.account_id) AS all_orders,
  (SELECT string_agg(i.ticker || ' ' || h.quantity, ', ' ORDER BY i.ticker)
     FROM holdings h JOIN instruments i USING (instrument_id)
     WHERE h.account_id = a.account_id AND h.quantity > 0) AS holdings
FROM accounts a WHERE a.account_id = $ACC_A;
"@ }
function last_order([int]$N = 1) { sqlo "SELECT order_id, order_type, quantity, order_status, rejection_reason, submitted_at, filled_at FROM orders WHERE account_id = $ACC_A ORDER BY order_id DESC LIMIT $N;" }
function trace([int]$OrderId) {
    if (-not $OrderId) { Write-Host 'usage: trace ORDER_ID'; return }
    sqlo "SELECT order_id, order_type, quantity, price_per_unit, order_status, rejection_reason, quote_source, quote_time, submitted_at, accepted_at, filled_at FROM orders WHERE order_id = $OrderId;"
    sqlo "SELECT created_at, action, details FROM audit_log WHERE order_id = $OrderId ORDER BY created_at, audit_id;"
}
function try_change([int]$OrderId) {   # act 3.4: both refused, even for the database owner
    if (-not $OrderId) { Write-Host 'usage: try_change ORDER_ID'; return }
    sqlo "UPDATE orders SET quantity = quantity + 1 WHERE order_id = $OrderId;"
    sqlo "DELETE FROM audit_log WHERE order_id = $OrderId;"
}
function reports {      # act 5.3: the nightly summary
    sqlo 'SELECT time_zone, summarized_through, refreshed_at FROM reporting_summary_state;'
    sqlo 'SELECT trade_date, symbol, side, trade_count, gross_amount FROM reporting_daily_trades ORDER BY trade_date DESC, gross_amount DESC LIMIT 8;'
}
function backup_now { & (Join-Path $Repo 'scripts\windows\backup-db.ps1') }
function backups { Get-ChildItem (Join-Path $Repo 'backups') -Filter 'lemarket-*.dump' -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object Name, Length, LastWriteTime }
function listening { Get-NetTCPConnection -State Listen -LocalPort 5432, 8082, 8084, 8085, 8086, 4200, 4201 -ErrorAction SilentlyContinue | Sort-Object LocalPort | Select-Object LocalAddress, LocalPort, OwningProcess }

# ---- Act 4 failure tests --------------------------------------------------------------------
function exec_pause  { internal PUT "http://${LmjHost}:8085/internal/execution/control" '{"enabled": false}' }
function exec_resume { internal PUT "http://${LmjHost}:8085/internal/execution/control" '{"enabled": true}' }
function restart_orders {   # closes the orders-service window and opens a fresh one
    Stop-ServiceWindow $OrdersTitle 8085
    Start-ServiceWindow $OrdersTitle $OrdersCmd
    wait_up 8085
}
function bad_deploy {       # a release that can't start: the wrong database password
    Stop-ServiceWindow $OrdersTitle 8085
    $log = Join-Path $DemoDir 'bad-deploy.log'
    Remove-Item $log -ErrorAction SilentlyContinue
    Start-ServiceWindow $OrdersTitle ('$env:SPRING_DATASOURCE_PASSWORD = ''wrong-password''; ' + $OrdersCmd + " 2>&1 | Tee-Object -FilePath '$log'")
}
function deploy_status {
    "orders-service on 8085: $(if (is_up 8085) { 'UP' } else { 'NOT RUNNING' })"
    $log = Join-Path $DemoDir 'bad-deploy.log'
    if (Test-Path $log) { Select-String -Path $log -Pattern 'password authentication failed[^"]*"[a-z_]+"' | Select-Object -First 1 | ForEach-Object { $_.Matches[0].Value } }
}
function rollback {
    Stop-ServiceWindow $OrdersTitle 8085
    Start-ServiceWindow $OrdersTitle $OrdersCmd
    wait_up 8085
}

function capture_old {      # act 0.1: a token that will have expired by act 4
    try { $script:OldToken = sign_in 'seed_active@seed.lemarket.com' } catch { Write-Warning $_; return }
    $script:OldTime = Get-Date -Format 'HH:mm:ss'
    Set-Content -Path (Join-Path $DemoDir 'old.token') -Value "$script:OldTime $script:OldToken"
    "captured at ${script:OldTime}: $(http_code $script:OldToken) (valid now)"
}
function tokens {           # act 4.3
    $saved = Join-Path $DemoDir 'old.token'
    if (Test-Path $saved) {
        $time, $old = (Get-Content $saved -Raw).Trim() -split ' ', 2
        "expired  (captured $time, unused since):       $(http_code $old)"
    }
    try { $new = sign_in 'seed_trader@seed.lemarket.com' } catch { Write-Warning $_; return }
    $c = if ($new.Substring(30, 1) -eq 'A') { 'B' } else { 'A' }
    $bad = $new.Substring(0, 30) + $c + $new.Substring(31)
    "valid    (fresh sign-in):                       $(http_code $new)"
    "tampered (one character changed in the middle): $(http_code $bad)"
    & $CurlExe -s -o $NullDev -X POST -H "Cookie: jwt=$new" "http://${LmjHost}:8082/api/auth/logout"
    "revoked  (same valid token after signing out):  $(http_code $new)"
}

function fault_on    { internal PUT    "http://${LmjHost}:8084/internal/holdings/control/settlement-faults/$ACC_A" }
function fault_check { internal GET    "http://${LmjHost}:8084/internal/holdings/control/settlement-faults/$ACC_A" }
function fault_off   { internal DELETE "http://${LmjHost}:8084/internal/holdings/control/settlement-faults/$ACC_A" }

function feed_down  { internal PUT  "http://${LmjHost}:8084/internal/market/control/feed" '{"mode": "UNAVAILABLE"}' }
function feed_stale { internal PUT  "http://${LmjHost}:8084/internal/market/control/feed" '{"mode": "STALE"}' }
function feed_reset { internal POST "http://${LmjHost}:8084/internal/market/control/reset" }

function all_clear { feed_reset; exec_resume; fault_off; health }   # every test switch back to normal

# ---- Prep only ------------------------------------------------------------------------------
function restore_demo_db([string]$Dump = (Join-Path $PSScriptRoot 'lemarket-demo.dump')) {
    # Replaces the lemarket database with the demo copy. Run setup-db.ps1 once before, so the
    # lemarket and lemarket_app logins exist (a dump doesn't carry logins).
    if (-not (Test-Path $Dump)) { Write-Warning "Dump not found: $Dump"; return }
    $pgRestore = Find-PgTool 'pg_restore'
    if (-not $Psql -or -not $pgRestore) { Write-Warning 'psql / pg_restore not found: install PostgreSQL first.'; return }
    $secure = Read-Host 'Password for the PostgreSQL "postgres" superuser' -AsSecureString
    $super = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
    try {
        $env:PGPASSWORD = $super
        & $Psql -h $LmjHost -U postgres -d postgres -v ON_ERROR_STOP=1 -c 'DROP DATABASE IF EXISTS lemarket WITH (FORCE);' -c 'CREATE DATABASE lemarket OWNER lemarket;'
        if ($LASTEXITCODE -ne 0) { throw 'Could not recreate the lemarket database.' }
        $env:PGPASSWORD = $DbOwnerPassword
        & $pgRestore -h $LmjHost -U lemarket -d lemarket --exit-on-error $Dump
        if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed.' }
        Write-Host 'Demo database restored.'
        totals
    } finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
}

function run_overnight_now {   # only if the overnight report shows no Ready run: tonight's job, now
    # The service never re-runs a night it already recorded, so clear yesterday's "did not run" first.
    sqlo "DELETE FROM report_runs WHERE run_date = (now() AT TIME ZONE 'America/New_York')::date - 1 AND status = 'FAILED' AND started_at IS NULL;"
    $ny = [System.TimeZoneInfo]::ConvertTimeBySystemTimeZoneId((Get-Date).AddMinutes(-10), 'Eastern Standard Time').ToString('HH:mm')
    Stop-ServiceWindow $StaffTitle 8086
    Start-ServiceWindow $StaffTitle ("`$env:LMJ_REPORTS_OVERNIGHTRUNTIME = '$ny'; `$env:LMJ_REPORTS_READYBY = '23:59'; " + $StaffCmd)
    wait_up 8086
    Start-Sleep -Seconds 15
    sqlo 'SELECT report_name, run_date, status, ready_at, failure_reason FROM report_runs ORDER BY run_date DESC LIMIT 3;'
    Stop-ServiceWindow $StaffTitle 8086       # back to the normal 2 a.m. schedule
    Start-ServiceWindow $StaffTitle $StaffCmd
    wait_up 8086
}

function demo_help { @'
Stack:     start_stack  stop_stack  health  listening
Evidence:  totals  last_order [N]  trace ID  try_change ID  reports  backups  backup_now
Act 0:     capture_old
Act 4.1:   exec_pause  (buy 1 share)  last_order  restart_orders
Act 4.2:   bad_deploy ... deploy_status  rollback
Act 4.3:   tokens
Act 4.4:   fault_on  (buy 2 shares)  totals  fault_check  fault_off
Act 4.5:   feed_down  (try a buy)  feed_reset
Rescue:    all_clear
Prep:      restore_demo_db  run_overnight_now
'@ }
Write-Host "LEAP demo commands loaded (repo: $Repo, Client A = account $ACC_A). Type demo_help for the list."
