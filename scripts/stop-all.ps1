# Stops c8run and Mailpit.
param([string]$CamundaHome = 'C:\Users\vines\camunda')

$c8 = Get-ChildItem $CamundaHome -Directory -Filter 'c8run-8.10*' | Sort-Object Name -Descending | Select-Object -First 1
if ($c8) {
    Push-Location $c8.FullName
    try { & .\c8run.exe stop } finally { Pop-Location }
}
Get-Process mailpit -ErrorAction SilentlyContinue | Stop-Process -Confirm:$false
