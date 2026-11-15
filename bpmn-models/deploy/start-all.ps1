# Starts the local stack: c8run 8.10 (Zeebe, Operate, Tasklist, Admin, connectors) and Mailpit.
# Usage: powershell -ExecutionPolicy Bypass -File bpmn-models\deploy\start-all.ps1 [-CamundaHome <dir>] [-SkipMailpit]
# CamundaHome (default: $env:CAMUNDA_HOME, else $HOME\camunda) must contain a c8run-8.10* folder and,
# unless -SkipMailpit is given, mailpit\mailpit.exe.
param(
    [string]$CamundaHome = $(if ($env:CAMUNDA_HOME) { $env:CAMUNDA_HOME } else { Join-Path $HOME 'camunda' }),
    [switch]$SkipMailpit
)
$ErrorActionPreference = 'Stop'

# Connector secrets must be env vars prefixed SECRET_ (e-sign webhook HMAC check, mock API key).
# The defaults below are DEMO-ONLY values that match the mock-services settings; they protect nothing real.
if (-not $env:SECRET_ESIGN_HMAC_SECRET) { $env:SECRET_ESIGN_HMAC_SECRET = 'demo-esign-secret' }
if (-not $env:SECRET_MOCKS_API_KEY) { $env:SECRET_MOCKS_API_KEY = 'demo-mocks-key' }

function Wait-Http([string]$Url, [int]$TimeoutSec = 240) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 | Out-Null
            return $true
        } catch { Start-Sleep -Seconds 3 }
    }
    return $false
}

# Mailpit: SMTP on 1025, web UI on 8025 (localhost only)
if (-not $SkipMailpit) {
    $mailpit = Join-Path $CamundaHome 'mailpit\mailpit.exe'
    if (-not (Test-Path $mailpit)) {
        Write-Warning "Mailpit not found at $mailpit (install it there or pass -SkipMailpit)"
    } elseif (-not (Get-Process mailpit -ErrorAction SilentlyContinue)) {
        Start-Process -FilePath $mailpit -ArgumentList '--smtp', '127.0.0.1:1025', '--listen', '127.0.0.1:8025' -WindowStyle Hidden
    }
    if (Wait-Http 'http://localhost:8025/api/v1/info' 30) { Write-Host 'Mailpit up: http://localhost:8025' }
    else { Write-Warning 'Mailpit did not answer on :8025' }
}

# c8run: newest c8run-8.10* folder under CamundaHome
$c8 = Get-ChildItem $CamundaHome -Directory -Filter 'c8run-8.10*' | Sort-Object Name -Descending | Select-Object -First 1
if (-not $c8) { throw "No c8run-8.10* folder under $CamundaHome" }
if (Wait-Http 'http://localhost:8080/v2/topology' 3) {
    Write-Host 'c8run already running'
} else {
    Push-Location $c8.FullName
    try { & .\c8run.exe start } finally { Pop-Location }
}

if (Wait-Http 'http://localhost:8080/v2/topology') { Write-Host 'Camunda up: http://localhost:8080 (demo/demo)' }
else { throw 'Camunda REST API did not answer on :8080' }
if (Wait-Http 'http://localhost:8086/actuator/health' 120) { Write-Host 'Connectors up: http://localhost:8086' }
else { Write-Warning 'Connectors runtime did not answer on :8086' }
