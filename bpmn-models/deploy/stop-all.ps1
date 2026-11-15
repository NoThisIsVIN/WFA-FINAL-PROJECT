# Stops c8run and Mailpit.
# Usage: powershell -ExecutionPolicy Bypass -File bpmn-models\deploy\stop-all.ps1 [-CamundaHome <dir>]
param(
    [string]$CamundaHome = $(if ($env:CAMUNDA_HOME) { $env:CAMUNDA_HOME } else { Join-Path $HOME 'camunda' })
)

$c8 = Get-ChildItem $CamundaHome -Directory -Filter 'c8run-8.10*' | Sort-Object Name -Descending | Select-Object -First 1
if ($c8) {
    Push-Location $c8.FullName
    try { & .\c8run.exe stop } finally { Pop-Location }
}
Get-Process mailpit -ErrorAction SilentlyContinue | Stop-Process -Confirm:$false
