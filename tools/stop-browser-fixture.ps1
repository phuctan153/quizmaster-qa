$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$statePath = Join-Path $projectRoot 'backend\target\browser-fixture.json'
if (!(Test-Path -LiteralPath $statePath)) { Write-Output 'No browser fixture state exists.'; exit 0 }
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
if ($state.containerName -notmatch '^quizmaster-browser-[a-f0-9]{32}$') { throw 'Unexpected fixture container name.' }
$labelJson = docker inspect --format '{{json .Config.Labels}}' $state.containerName
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect fixture container.' }
$labels = $labelJson | ConvertFrom-Json
if ($labels.'quizmaster.qa.fixture' -ne 'true') { throw 'Container ownership check failed; inspect fixture manually.' }
$process = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$state.processId)"
if ($process) {
    $expectedJar = Join-Path $projectRoot 'backend\target\quizmaster-api-0.1.0-SNAPSHOT.jar'
    if ($process.Name -ne 'java.exe' -or !$process.CommandLine.Contains($expectedJar) -or !$process.CommandLine.Contains('--spring.profiles.active=bootstrap')) {
        throw 'Process ownership check failed; refusing to stop an unrelated process.'
    }
    Stop-Process -Id ([int]$state.processId)
}
docker stop $state.containerName | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Fixture container did not stop.' }
$resolvedState = (Resolve-Path -LiteralPath $statePath).Path
if ($resolvedState -ne (Join-Path $projectRoot 'backend\target\browser-fixture.json')) { throw 'Unexpected state path.' }
Remove-Item -LiteralPath $resolvedState
Write-Output 'Stopped the owned fixture process/container and removed its temporary credentials.'
