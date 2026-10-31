# Starts the local stack: c8run 8.10 (Zeebe, Operate, Tasklist, Admin, connectors) and Mailpit.
# Usage: powershell -File scripts\start-all.ps1 [-SkipMailpit]
param(
    [string]$CamundaHome = 'C:\Users\vines\camunda',
    [switch]$SkipMailpit
)
$ErrorActionPreference = 'Stop'

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
    if (-not (Get-Process mailpit -ErrorAction SilentlyContinue)) {
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
