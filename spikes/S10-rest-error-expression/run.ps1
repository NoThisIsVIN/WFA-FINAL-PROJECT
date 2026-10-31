# S10 - REST connector error shape, error expression routing, retries/backoff. Usage: powershell -File run.ps1
. "$PSScriptRoot\lib.ps1"
$base = 'http://127.0.0.1:18091'
$cases = [ordered]@{ ok = "$base/ok"; notfound = "$base/notfound"; fail = "$base/fail"; refused = 'http://127.0.0.1:18099/nothing'; slow = "$base/slow" }
$log = "$PSScriptRoot\requests.log"; if (Test-Path $log) { Remove-Item $log }
$srv = Start-Process node -ArgumentList "`"$PSScriptRoot\server.js`"", '18091' -PassThru -WindowStyle Hidden
Start-Sleep 2
try {
  Deploy-Bpmn "$PSScriptRoot\s10-error-shape.bpmn" | Out-Null
  Deploy-Bpmn "$PSScriptRoot\s10-error-routing.bpmn" | Out-Null

  function Settle($pi, [int]$max = 40) {
    # wait until the instance is COMPLETED or has an incident
    $deadline = (Get-Date).AddSeconds($max)
    do {
      $st = Get-InstanceState $pi
      if ($st -eq 'COMPLETED' -or $st -eq 'TERMINATED') { return $st }
      $inc = C8 '/v2/incidents/search' @{ filter = @{ processInstanceKey = $pi } }
      if ($inc.Json.items) { return 'INCIDENT' }
      Start-Sleep 2
    } while ((Get-Date) -lt $deadline)
    return $st
  }

  Section 'A. error shape probe (errorExpression returns bpmnError("PROBE", error.message, {probeError: error, ...}))'
  foreach ($c in $cases.Keys) {
    $t0 = Get-Date
    $pi = Start-Instance 'spike-s10-error-shape' @{ url = $cases[$c] }
    $st = Settle $pi
    Write-Host ("--- {0} ({1}) pi={2} -> {3} after {4:n1}s" -f $c, $cases[$c], $pi, $st, ((Get-Date) - $t0).TotalSeconds)
    Show-Elements (Get-Elements $pi { param($i) @($i | Where-Object { $_.type -eq 'END_EVENT' }).Count -gt 0 } 6)
    $v = Get-Vars $pi
    foreach ($n in 'okStatus', 'okBody', 'okHeaders', 'probeCode', 'probeType', 'probeError') { if ($v.Contains($n)) { Write-Host ("    {0} = {1}" -f $n, $v[$n]) } }
    $inc = C8 '/v2/incidents/search' @{ filter = @{ processInstanceKey = $pi } }
    foreach ($i in @($inc.Json.items)) { Write-Host ("    INCIDENT {0}: {1}" -f $i.errorType, $i.errorMessage); C8 "/v2/process-instances/$pi/cancellation" @{} | Out-Null }
  }

  Section 'B. routing expression (404 -> NOT_FOUND, 500 -> SERVER_ERROR, else null), retries=2, retryBackoff=PT2S'
  foreach ($c in $cases.Keys) {
    $before = if (Test-Path $log) { @(Get-Content $log).Count } else { 0 }
    $t0 = Get-Date
    $pi = Start-Instance 'spike-s10-error-routing' @{ url = $cases[$c] }
    $st = Settle $pi 60
    $secs = ((Get-Date) - $t0).TotalSeconds
    Start-Sleep 1
    $hits = if (Test-Path $log) { @(Get-Content $log | Select-Object -Skip $before) } else { @() }
    Write-Host ("--- {0} pi={1} -> {2} after {3:n1}s; server saw {4} request(s): {5}" -f $c, $pi, $st, $secs, $hits.Count, (($hits | ForEach-Object { ($_ | ConvertFrom-Json).t.Substring(11, 12) }) -join ', '))
    Show-Elements (Get-Elements $pi { param($i) @($i | Where-Object { $_.type -eq 'END_EVENT' }).Count -gt 0 } 6)
    $v = Get-Vars $pi
    foreach ($n in 'httpStatus', 'appStatus', 'httpBody') { if ($v.Contains($n)) { Write-Host ("    {0} = {1}" -f $n, $v[$n]) } }
    $inc = C8 '/v2/incidents/search' @{ filter = @{ processInstanceKey = $pi } }
    foreach ($i in @($inc.Json.items)) {
      Write-Host ("    INCIDENT {0} created {1}: {2}" -f $i.errorType, $i.creationTime, $i.errorMessage)
      C8 "/v2/process-instances/$pi/cancellation" @{} | Out-Null
    }
  }
} finally {
  Stop-Process -Id $srv.Id -Force -ErrorAction SilentlyContinue
  Write-Host "`nstopped test server pid $($srv.Id)"
}
