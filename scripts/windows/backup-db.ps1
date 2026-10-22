<#
.SYNOPSIS
    Takes one backup of the lemarket database on a locally installed PostgreSQL, keeping the newest 7.

.DESCRIPTION
    Windows counterpart of scripts/backup-db.sh (BR-14), for the native setup of setup-db.ps1.
    Runs pg_dump as the owner, lemarket, in pg_dump's custom format (compressed; pg_restore can
    restore all of it or one table), into backups\lemarket-<UTC time>.dump at the repo root. The dump
    is written under a temporary name and renamed when complete, so a failed run never counts as a
    backup and never causes a good one to be pruned.

    For a nightly backup, schedule this script with Windows Task Scheduler. Restore steps are in
    database/README.md ("Backups and restore").

.EXAMPLE
    .\scripts\windows\backup-db.ps1
    .\scripts\windows\backup-db.ps1 -BackupDir D:\lemarket-backups -Keep 14
#>
param(
    # Must match SPRING_DATASOURCE_PASSWORD / DB_PASSWORD (services default to 'changeme').
    [string]$DbPassword = 'changeme',
    [string]$PgHost = 'localhost',
    [int]$PgPort = 5432,
    [string]$BackupDir = '',
    # How many backups to keep; the same default as scripts/backup-db.sh (BACKUP_KEEP).
    [ValidateRange(1, 1000)]
    [int]$Keep = 7
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
if (-not $BackupDir) { $BackupDir = Join-Path $repoRoot 'backups' }

# Use pg_dump from PATH, else the newest PostgreSQL install under Program Files (as setup-db.ps1 finds psql).
$pgDump = (Get-Command pg_dump -ErrorAction SilentlyContinue).Source
if (-not $pgDump) {
    $pgDump = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\pg_dump.exe' -ErrorAction SilentlyContinue |
        Sort-Object { [int]$_.Directory.Parent.Name } -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $pgDump) { throw 'pg_dump not found. Install PostgreSQL (https://www.postgresql.org/download/windows/) first.' }

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
# UTC, so names sort in time order and mean the same on every machine.
$file = Join-Path $BackupDir ('lemarket-{0}.dump' -f (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss'))
$partial = "$file.partial"

$env:PGPASSWORD = $DbPassword
try {
    & $pgDump --format=custom -h $PgHost -p $PgPort --username=lemarket --dbname=lemarket --file=$partial
    if ($LASTEXITCODE -ne 0) { throw "pg_dump failed (exit $LASTEXITCODE)" }
    Move-Item -LiteralPath $partial -Destination $file
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $partial -ErrorAction SilentlyContinue
}

# Keep the newest $Keep backups. The names sort by time.
Get-ChildItem -Path $BackupDir -Filter 'lemarket-*.dump' |
    Sort-Object Name -Descending |
    Select-Object -Skip $Keep |
    ForEach-Object {
        Remove-Item -LiteralPath $_.FullName
        Write-Host "Removed old backup $($_.FullName)"
    }

$size = '{0:N1} MB' -f ((Get-Item -LiteralPath $file).Length / 1MB)
Write-Host "Backed up lemarket to $file ($size); keeping the newest $Keep."
