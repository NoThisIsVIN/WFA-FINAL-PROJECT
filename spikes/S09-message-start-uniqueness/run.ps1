# S9 - message start event uniqueness per correlation key. Usage: powershell -File run.ps1
. "$PSScriptRoot\lib.ps1"
$run = (Get-Date).ToString('HHmmss')
$K1 = "APP-1-$run"; $K2 = "APP-2-$run"          # suffix keeps re-runs independent of earlier instances
$MSG = 'spike-application-submitted'
Deploy-Bpmn "$PSScriptRoot\s9-message-start.bpmn" | Out-Null

function Publish($key, $ttl, $mid) {
  $r = C8 '/v2/messages/publication' @{ name = $MSG; correlationKey = $key; timeToLive = $ttl; messageId = $mid; variables = @{ appId = $key; mid = $mid } }
  Write-Host ("  publish key={0} ttl={1} messageId={2} -> HTTP {3} {4}" -f $key, $ttl, $mid, $r.Status, $r.Raw); return $r
}
function Correlate($key) {
  $r = C8 '/v2/messages/correlation' @{ name = $MSG; correlationKey = $key; variables = @{ appId = $key; mid = 'correlate' } }
  Write-Host ("  correlate key={0} -> HTTP {1} {2}" -f $key, $r.Status, $r.Raw); return $r
}
function Instances($key, [int]$expect = -1) {
  # instances that were started by a message carrying appId=$key (message variables land on the root scope)
  $f = @{ name = 'appId'; value = ('"' + $key + '"') }
  $u = if ($expect -ge 0) { [scriptblock]::Create("param(`$i) @(`$i | Sort-Object processInstanceKey -Unique).Count -ge $expect") } else { { param($i) $false } }
  $vars = Search-Until '/v2/variables/search' $f $u 12
  $out = @(); foreach ($v in @($vars | Sort-Object processInstanceKey -Unique)) { $out += [pscustomobject]@{ pi = [string]$v.processInstanceKey; state = (Get-InstanceState $v.processInstanceKey); mid = (Get-Vars $v.processInstanceKey)['mid'] } }
  Write-Host ("  instances for {0}: {1}" -f $key, $out.Count); $out | ForEach-Object { Write-Host ("    pi={0} state={1} mid={2}" -f $_.pi, $_.state, $_.mid) }
  return ,$out
}
function Complete-Hold($pi) {
  for ($i = 0; $i -lt 10; $i++) {
    $r = C8 '/v2/jobs/activation' @{ type = 'spike-s9-hold'; maxJobsToActivate = 20; timeout = 3000; worker = 's9' }
    $job = @($r.Json.jobs) | Where-Object { [string]$_.processInstanceKey -eq $pi } | Select-Object -First 1
    if ($job) { $c = C8 "/v2/jobs/$($job.jobKey)/completion" @{ variables = @{} }; Write-Host "  completed job of pi=$pi -> HTTP $($c.Status)"; return }
    Start-Sleep -Seconds 2
  }
  throw "no job for $pi"
}

Section "1. first publish $K1 (ttl 0) -> instance A"
Publish $K1 0 "m1-$run" | Out-Null
$a = Instances $K1 1; $A = $a[0].pi

Section "2. second publish same key while A active (ttl 0, new messageId)"
Publish $K1 0 "m2-$run" | Out-Null
Start-Sleep 4; Instances $K1 | Out-Null

Section "3. publish same key with ttl 60s (buffered?) and again with SAME messageId"
Publish $K1 60000 "m3-$run" | Out-Null
Publish $K1 60000 "m3-$run" | Out-Null
Start-Sleep 4; Instances $K1 | Out-Null

Section "4. /v2/messages/correlation with same key while A active"
Correlate $K1 | Out-Null

Section "5. different key $K2"
Publish $K2 0 "m4-$run" | Out-Null
Instances $K2 1 | Out-Null

Section "6. complete A -> does buffered m3 start a new instance automatically?"
Complete-Hold $A
Wait-InstanceState $A 'COMPLETED' | Out-Null
$after = Instances $K1 2

Section "7. complete everything for $K1, then publish again (ttl 0) and correlate"
foreach ($x in $after) { if ($x.state -eq 'ACTIVE') { Complete-Hold $x.pi; Wait-InstanceState $x.pi 'COMPLETED' | Out-Null } }
Publish $K1 0 "m5-$run" | Out-Null
$all = Instances $K1 ($after.Count + 1)
foreach ($x in $all) { if ($x.state -eq 'ACTIVE') { Complete-Hold $x.pi; Wait-InstanceState $x.pi 'COMPLETED' | Out-Null } }
Section "8. all $K1 instances completed -> /v2/messages/correlation creates a new instance?"
$c = Correlate $K1
Instances $K1 ($all.Count + 1) | Out-Null
if ($c.Json.processInstanceKey) { Complete-Hold ([string]$c.Json.processInstanceKey) }
$b = Instances $K2; foreach ($x in $b) { if ($x.state -eq 'ACTIVE') { Complete-Hold $x.pi } }   # cleanup
