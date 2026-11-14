$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    # Native tools may emit harmless JVM warnings to stderr on Windows PowerShell.
    $ErrorActionPreference = 'Continue'
    & mvn -Punit-tests test
    $runExitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($runExitCode -ne 0) { throw "Unit test run failed (exit $runExitCode). See target/roms-unit-reports." }
} finally { Pop-Location }
