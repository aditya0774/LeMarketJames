<#
.SYNOPSIS
    Creates the lemarket database on a locally installed PostgreSQL and applies database/schema.

.DESCRIPTION
    Windows counterpart of the db container in docker-compose.yml, for machines that can't run
    Linux containers. Creates the `lemarket` login and database (the same names and default
    password every service expects), then runs every database/schema/*.sql file in numeric order,
    exactly like docker-entrypoint-initdb.d does on first start.

    Also creates `lemarket_app`, the restricted account of the audit lockdown
    (database/schema/016_audit_lockdown.sql). `lemarket` owns the database but is no superuser
    here, so it cannot create that role itself. This part runs even when the database already
    exists, so an existing database only needs 016 applied afterwards.

    Pass -AuditLogging to make the server log name the account behind every statement it records,
    the way the Compose db does. That log is the record of refused attempts to change audit
    records (contracts/C2-audit.md). It is opt-in because the log format is a setting of the
    whole PostgreSQL install, not just of the lemarket database.

    Schema files are only applied to a freshly created database, because they are not safe to
    re-run. Pass -Reset to drop and recreate the database (wipes all data).

.EXAMPLE
    .\scripts\windows\setup-db.ps1
    .\scripts\windows\setup-db.ps1 -Reset
    .\scripts\windows\setup-db.ps1 -AuditLogging
#>
param(
    # Must match SPRING_DATASOURCE_PASSWORD / DB_PASSWORD (services default to 'changeme').
    [string]$DbPassword = 'changeme',
    # Password of lemarket_app; must match APP_DB_PASSWORD (016 defaults to 'changeme_app').
    [string]$AppDbPassword = 'changeme_app',
    [string]$PgHost = 'localhost',
    [int]$PgPort = 5432,
    [switch]$Reset,
    # Changes the log line format of the whole PostgreSQL install; see the description.
    [switch]$AuditLogging
)

$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$schemaDir = Join-Path $repoRoot 'database\schema'

# Use psql from PATH, else the newest PostgreSQL install under Program Files.
$psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    $psql = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
        Sort-Object { [int]$_.Directory.Parent.Name } -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $psql) { throw 'psql not found. Install PostgreSQL (https://www.postgresql.org/download/windows/) first.' }

function Invoke-Psql([string]$User, [string]$Password, [string]$Database, [string[]]$Arguments) {
    $env:PGPASSWORD = $Password
    # Windows psql otherwise reads the UTF-8 schema files in the console's code page.
    $env:PGCLIENTENCODING = 'UTF8'
    # Hide NOTICEs such as the schema's "DROP TABLE IF EXISTS ... skipping"; warnings and errors still show.
    $env:PGOPTIONS = '-c client_min_messages=warning'
    try {
        & $psql -h $PgHost -p $PgPort -U $User -d $Database -v ON_ERROR_STOP=1 -At @Arguments
        if ($LASTEXITCODE -ne 0) { throw "psql failed (exit $LASTEXITCODE)" }
    } finally {
        Remove-Item Env:PGPASSWORD, Env:PGOPTIONS -ErrorAction SilentlyContinue
    }
}

# The superuser password is only needed to create the role and database; it is never stored.
$secure = Read-Host 'Password for the PostgreSQL "postgres" superuser' -AsSecureString
$superPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

# Create each login, or reset its password so it matches what the services and scripts use.
# lemarket owns the database; lemarket_app gets its (restricted) rights from 016.
foreach ($login in @(@{ Name = 'lemarket'; Password = $DbPassword }, @{ Name = 'lemarket_app'; Password = $AppDbPassword })) {
    $name = $login.Name
    $escaped = $login.Password.Replace("'", "''")
    Invoke-Psql 'postgres' $superPassword 'postgres' @('-c', @"
DO `$`$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$name') THEN
    CREATE ROLE $name LOGIN PASSWORD '$escaped';
  ELSE
    ALTER ROLE $name WITH LOGIN PASSWORD '$escaped';
  END IF;
END `$`$;
"@)
}

# Mirrors the db command in docker-compose.yml. The prefix puts the account, database, client
# program and address on every log line. DDL logging is set on the lemarket database only, so the
# password statements above, which run in the postgres database, never reach the log.
function Enable-AuditLogging {
    if (-not $AuditLogging) { return }
    Invoke-Psql 'postgres' $superPassword 'postgres' @(
        '-c', "ALTER SYSTEM SET log_line_prefix = '%m [%p] %quser=%u db=%d app=%a client=%h '",
        '-c', "ALTER DATABASE lemarket SET log_statement = 'ddl'",
        '-c', 'SELECT pg_reload_conf()') | Out-Null
    Write-Host 'Server log now records the account behind each statement, and DDL on lemarket.'
}

$exists = Invoke-Psql 'postgres' $superPassword 'postgres' @('-c', "SELECT 1 FROM pg_database WHERE datname = 'lemarket'")
if ($exists -and $Reset) {
    Write-Host 'Dropping existing lemarket database (-Reset)...'
    Invoke-Psql 'postgres' $superPassword 'postgres' @('-c', 'DROP DATABASE lemarket WITH (FORCE)')
    $exists = $null
}
if ($exists) {
    Enable-AuditLogging
    Write-Host 'Database lemarket already exists; schema not re-applied. Use -Reset to recreate it, or apply new files with psql (see database/README.md).'
    return
}

# The owner can create tables in public (PostgreSQL 15+ no longer lets every role do so).
Invoke-Psql 'postgres' $superPassword 'postgres' @('-c', 'CREATE DATABASE lemarket OWNER lemarket')
Enable-AuditLogging

# Apply as lemarket so it owns the tables: migrations and resets run as the owner, and
# lemarket_app is only given what 016 grants it.
Get-ChildItem $schemaDir -Filter '*.sql' | Sort-Object Name | ForEach-Object {
    Write-Host "Applying $($_.Name)"
    Invoke-Psql 'lemarket' $DbPassword 'lemarket' @('-q', '-f', $_.FullName) | Out-Null
}
Write-Host 'Database lemarket is ready.'
