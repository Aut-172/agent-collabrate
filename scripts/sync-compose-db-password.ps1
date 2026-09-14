[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath ".env")) {
    throw "Missing .env. Create it from .env.example before synchronizing the database password."
}

docker compose up -d --no-deps postgres
if ($LASTEXITCODE -ne 0) {
    throw "Failed to start the postgres service with the current .env configuration."
}

$databaseReady = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    docker compose exec -T postgres sh -c 'pg_isready --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"' *> $null
    if ($LASTEXITCODE -eq 0) {
        $databaseReady = $true
        break
    }
    Start-Sleep -Seconds 1
}

if (-not $databaseReady) {
    throw "PostgreSQL did not become ready within 30 seconds. Check docker compose logs postgres."
}

$psqlCommand = @'
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=ON_ERROR_STOP=1 \
  --set=role="$POSTGRES_USER" \
  --set=new_password="$POSTGRES_PASSWORD"
'@
$alterRoleSql = @'
ALTER ROLE :"role" PASSWORD :'new_password';
'@
$alterRoleSql | docker compose exec -T postgres sh -c $psqlCommand
if ($LASTEXITCODE -ne 0) {
    throw "Failed to synchronize the PostgreSQL role password. Ensure the postgres service is running."
}

$null = docker compose up -d backend frontend
if ($LASTEXITCODE -ne 0) {
    throw "The password was synchronized, but the application stack failed to start. Check docker compose logs backend."
}

$stackHealthy = $false
for ($attempt = 1; $attempt -le 60; $attempt++) {
    $backendId = (docker compose ps -q backend).Trim()
    $frontendId = (docker compose ps -q frontend).Trim()
    if ($backendId -and $frontendId) {
        $backendState = docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' $backendId
        $frontendState = docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' $frontendId
        if ($backendState -match '^exited\|' -or $frontendState -match '^exited\|') {
            throw "The password was synchronized, but an application container exited. Check docker compose logs backend frontend."
        }
        if ($backendState -match '^running\|healthy$' -and $frontendState -match '^running\|healthy$') {
            $stackHealthy = $true
            break
        }
    }
    Start-Sleep -Seconds 2
}

if (-not $stackHealthy) {
    throw "The password was synchronized, but the application stack did not become healthy within 120 seconds. Check docker compose logs backend frontend."
}

Write-Host "PostgreSQL password synchronized; backend and frontend are healthy."
