<#
.SYNOPSIS
    Stops everything start-all.ps1 started: every backend service plus both Angular dev servers.

.DESCRIPTION
    Counterpart of start-all.ps1, and the Windows equivalent of `docker compose down`.
    First closes each window start-all.ps1 opened, together with the Java/Node processes inside
    it. Then stops anything still listening on the stack's ports, which covers services started
    by hand in their own terminals. PostgreSQL is left running: it's a Windows service that
    start-all.ps1 never starts.

.EXAMPLE
    .\scripts\windows\stop-all.ps1
#>

$ErrorActionPreference = 'Stop'

# Must match the window titles and ports in start-all.ps1.
$titles = @(
    'market-service :8083', 'auth-service :8082', 'core-service :8081',
    'buy-sell-service :8085', 'holdings-service :8084', 'reporting-service :8086',
    'gateway-service :8089', 'frontend :4200', 'staff-frontend :4201'
)
$ports = 8083, 8082, 8081, 8084, 8085, 8086, 8089, 4200, 4201

# Kills a process and all its descendants. Stop-Process alone would leave the JVM or Node
# child of a window running and still holding its port.
function Stop-Tree([int]$ProcessId, [string]$Label) {
    taskkill /F /T /PID $ProcessId 2>&1 | Out-Null
    Write-Host "Stopped $Label"
}

# Find the windows by the title start-all.ps1 puts on their command line. That works whichever
# terminal hosts them, whereas the visible window title is empty under Windows Terminal.
$windows = Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe'"
foreach ($title in $titles) {
    $window = $windows | Where-Object { $_.CommandLine -like "*WindowTitle = '$title'*" }
    foreach ($process in $window) { Stop-Tree $process.ProcessId $title }
}

# Anything still listening was started some other way, for example by hand from a terminal.
$listeners = Get-NetTCPConnection -LocalPort $ports -State Listen -ErrorAction SilentlyContinue
foreach ($listener in $listeners | Sort-Object OwningProcess -Unique) {
    Stop-Tree $listener.OwningProcess "process on port $($listener.LocalPort)"
}

Write-Host 'LeMarketJames stack stopped.'
