<#
.SYNOPSIS
Creates the June-September 2026 historical demo. Default is a rollback-only preview.
.DESCRIPTION
Apply database/schema/021_historical_segment_demo_clients.sql first. Uses DB_PASSWORD
and optional DEMO_DATABASE_URL (JDBC) / DEMO_DATABASE_USER. No services need stopping.
The Java fixture writer uses AuditRecorder in the same transaction as orders and settlement.
.EXAMPLE
.\scripts\windows\seed-historical-segment-report.ps1 -Apply
#>
param([switch]$Apply)
$ErrorActionPreference = 'Continue' # Native Java warnings use stderr; exit codes below decide failure.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $repoRoot
try {
    & mvn -B -pl services/orders-service -am install '-DskipTests'
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
    $seedBuild = Join-Path $repoRoot 'services\orders-service\target\historical-seed'
    New-Item -ItemType Directory -Force -Path $seedBuild | Out-Null
    $classpathFile = Join-Path $seedBuild 'classpath.txt'
    & mvn -B -pl services/orders-service dependency:build-classpath "-Dmdep.outputFile=$classpathFile" '-Dmdep.includeScope=runtime'
    if ($LASTEXITCODE -ne 0) { throw 'Dependency classpath failed' }
    $seedClasspath = (Join-Path $repoRoot 'services\orders-service\target\classes') + ';' + (Get-Content -Raw $classpathFile).Trim()
    & javac -encoding UTF-8 -cp $seedClasspath -d $seedBuild scripts/seed/HistoricalSegmentSeed.java
    if ($LASTEXITCODE -ne 0) { throw 'Seed compilation failed' }
    $seedArguments = @()
    if ($Apply) { $seedArguments += '--apply' }
    & java -cp "$seedBuild;$seedClasspath" com.lemarketjames.common.audit.HistoricalSegmentSeed @seedArguments
    if ($LASTEXITCODE -ne 0) { throw 'Historical seed failed; its transaction was rolled back' }
} finally { Pop-Location }

