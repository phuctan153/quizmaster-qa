param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectRoot 'backend\target'
$jar = Join-Path $outputDirectory 'quizmaster-api-0.1.0-SNAPSHOT.jar'
$statePath = Join-Path $outputDirectory 'browser-fixture.json'
if (!(Test-Path -LiteralPath $jar)) { throw 'Build backend with Maven verify first.' }
if (Test-Path -LiteralPath $statePath) { throw 'A fixture state file already exists; inspect and stop that fixture first.' }
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "Port $Port is already in use." }
$containerName = 'quizmaster-browser-' + [Guid]::NewGuid().ToString('N')
$env:POSTGRES_PASSWORD = [Guid]::NewGuid().ToString()
$env:POSTGRES_USER = 'quizmaster'
$env:POSTGRES_DB = 'quizmaster_browser_test'
$backendProcess = $null
try {
    docker run --detach --rm --name $containerName --label quizmaster.qa.fixture=true -e POSTGRES_PASSWORD -e POSTGRES_USER -e POSTGRES_DB -p '127.0.0.1::5432' postgres:18 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Disposable PostgreSQL startup failed.' }
    $mapping = docker port $containerName 5432/tcp
    $databasePort = ($mapping -split ':')[-1].Trim()
    $env:DB_URL = "jdbc:postgresql://localhost:$databasePort/quizmaster_browser_test"
    $env:DB_USERNAME = $env:POSTGRES_USER
    $env:DB_PASSWORD = $env:POSTGRES_PASSWORD
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    $random.GetBytes($bytes)
    $random.Dispose()
    $env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes)
    $env:BOOTSTRAP_ADMIN_EMAIL = 'browser-admin@example.test'
    $env:BOOTSTRAP_ADMIN_PASSWORD = [Guid]::NewGuid().ToString()
    $backendProcess = Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar, '--spring.profiles.active=bootstrap', "--server.port=$Port") -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $outputDirectory 'browser-backend.log') -RedirectStandardError (Join-Path $outputDirectory 'browser-backend-error.log')
    @{
        containerName = $containerName
        processId = $backendProcess.Id
        port = $Port
        email = $env:BOOTSTRAP_ADMIN_EMAIL
        password = $env:BOOTSTRAP_ADMIN_PASSWORD
    } | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
    Write-Output 'Started disposable browser fixture. Credentials/state are in ignored backend/target/browser-fixture.json.'
} catch {
    if ($backendProcess) { Stop-Process -Id $backendProcess.Id -ErrorAction SilentlyContinue }
    docker stop $containerName 2>$null | Out-Null
    throw
}
