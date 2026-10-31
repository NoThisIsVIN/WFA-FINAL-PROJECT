# S7 - Email connector -> Mailpit, error expression + boundary, retries. Usage: powershell -File run.ps1
. "$PSScriptRoot\lib.ps1"
$connLog = 'C:\Users\vines\camunda\c8run-8.10.7\log\connectors.log'
$run = (Get-Date).ToString('HHmmss')
Deploy-Bpmn "$PSScriptRoot\s7-email.bpmn" | Out-Null
Deploy-Bpmn "$PSScriptRoot\s7-email-noexpr.bpmn" | Out-Null

function Vars($app, $port) { @{ applicationId = $app; applicantName = 'Ada Lovelace'; applicantEmail = 'ada@example.test'; capacityKw = 42; smtpPort = $port } }
function Attempts($pi) { @(Select-String -Path $connLog -Pattern "ERROR processInstanceKey=$pi," -SimpleMatch:$false | ForEach-Object { $_.Line.Substring(11, 8) } | Sort-Object -Unique) }
function Settle($pi, [int]$max = 60) {
  $deadline = (Get-Date).AddSeconds($max)
  do { $st = Get-InstanceState $pi; if ($st -eq 'COMPLETED') { return $st }
       $inc = C8 '/v2/incidents/search' @{ filter = @{ processInstanceKey = $pi } }; if ($inc.Json.items) { return 'INCIDENT' }
       Start-Sleep 2 } while ((Get-Date) -lt $deadline)
  return $st
}

Section '1. happy path: 127.0.0.1:1025, noAuth, NONE encryption, FEEL subject/body'
$app = "APP-S7-$run"
$t0 = Get-Date; $pi = Start-Instance 'spike-s7-email' (Vars $app 1025); $st = Settle $pi
Write-Host ("  pi={0} -> {1} after {2:n1}s" -f $pi, $st, ((Get-Date) - $t0).TotalSeconds)
Show-Elements (Get-Elements $pi { param($i) @($i | Where-Object { $_.type -eq 'END_EVENT' }).Count -gt 0 } 10)
Write-Host ('  emailResult = ' + (Get-Vars $pi)['emailResult'])
$q = [uri]::EscapeDataString("subject:`"$app`"")
$m = $null
for ($i = 0; $i -lt 10 -and -not $m; $i++) {
  $r = Invoke-Http -Method GET -Url "http://localhost:8025/api/v1/search?query=$q" -NoAuth
  $m = @($r.Json.messages) | Select-Object -First 1; if (-not $m) { Start-Sleep 1 }
}
if ($m) {
  Write-Host ("  Mailpit: ID={0} MessageID={1} From={2} To={3} Subject='{4}'" -f $m.ID, $m.MessageID, $m.From.Address, $m.To[0].Address, $m.Subject)
  $full = Invoke-Http -Method GET -Url "http://localhost:8025/api/v1/message/$($m.ID)" -NoAuth
  Write-Host ('  Text: ' + ($full.Json.Text -replace "`r?`n", ' | '))
} else { Write-Host '  Mailpit: NOT FOUND' -ForegroundColor Red }

Section '2. failure: port 1026 (nothing listening), retries=1, errorExpression -> bpmnError("EMAIL_FAILED", ...)'
$t0 = Get-Date; $pi = Start-Instance 'spike-s7-email' (Vars "APP-S7F-$run" 1026); $st = Settle $pi
Write-Host ("  pi={0} -> {1} after {2:n1}s" -f $pi, $st, ((Get-Date) - $t0).TotalSeconds)
Show-Elements (Get-Elements $pi { param($i) @($i | Where-Object { $_.type -eq 'END_EVENT' }).Count -gt 0 } 10)
Write-Host ('  emailError = ' + (Get-Vars $pi)['emailError'])
Start-Sleep 1; Write-Host ('  failed attempts in connectors.log: ' + ((Attempts $pi) -join ', '))

Section '3. failure without errorExpression, default retries=3, retryBackoff=PT1S'
$t0 = Get-Date; $pi = Start-Instance 'spike-s7-email-noexpr' (Vars "APP-S7N-$run" 1026); $st = Settle $pi 90
Write-Host ("  pi={0} -> {1} after {2:n1}s" -f $pi, $st, ((Get-Date) - $t0).TotalSeconds)
Start-Sleep 1; Write-Host ('  failed attempts in connectors.log: ' + ((Attempts $pi) -join ', '))
$inc = C8 '/v2/incidents/search' @{ filter = @{ processInstanceKey = $pi } }
foreach ($i in @($inc.Json.items)) { Write-Host ("  INCIDENT {0}: {1}" -f $i.errorType, $i.errorMessage); C8 "/v2/process-instances/$pi/cancellation" @{} | Out-Null }
