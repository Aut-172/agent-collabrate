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

docker compose exec -T postgres sh -c 'printf "%s\n%s\n" "$POSTGRES_PASSWORD" "$POSTGRES_PASSWORD" | psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 --command="\password $POSTGRES_USER"'
if ($LASTEXITCODE -ne 0) {
    throw "Failed to synchronize the PostgreSQL role password. Ensure the postgres service is running."
}

docker compose up -d backend frontend --wait --wait-timeout 90
if ($LASTEXITCODE -ne 0) {
    throw "The password was synchronized, but the application stack did not become healthy. Check docker compose logs backend."
}

Write-Host "PostgreSQL password synchronized; backend and frontend are healthy."
