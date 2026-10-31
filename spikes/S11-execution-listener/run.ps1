# S11 - execution listeners (audit-log) on intermediate throw event + embedded sub-process
$ErrorActionPreference = 'Stop'; Set-Location $PSScriptRoot
$B = 'http://localhost:8080/v2'; $H = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('demo:demo')) }
function Post($p, $b) { Invoke-RestMethod -Method Post -Uri "$B$p" -Headers $H -ContentType 'application/json' -Body ($b | ConvertTo-Json -Depth 10) }
function Activate { (Post '/jobs/activation' @{ type='audit-log'; timeout=60000; maxJobsToActivate=10; worker='spike-s11' }).jobs }
foreach ($f in 's11-listeners.bpmn','s11-gateway-start.bpmn','s11-gateway-flow.bpmn') { Write-Host "deploy $f"; curl.exe -s -u demo:demo -X POST "$B/deployments" -F "resources=@$f"; Write-Host '' }  # gateway-flow is EXPECTED to be rejected
$pi = (Post '/process-instances' @{ processDefinitionId='spike-s11-listeners'; variables=@{ applicationId='APP-1' } }).processInstanceKey
Start-Sleep 3; $j = Activate; $j | Select-Object elementId, kind, listenerEventType, retries, jobKey | Format-Table
Start-Sleep 3; (Post '/element-instances/search' @{ filter=@{ processInstanceKey=$pi } }).items | Select-Object elementId, state | Format-Table  # Milestone ACTIVE while START listener open
Post "/jobs/$($j[0].jobKey)/completion" @{ variables=@{ auditStartMilestone='logged' } } | Out-Null
Start-Sleep 2; $j = Activate; Post "/jobs/$($j[0].jobKey)/failure" @{ retries=0; errorMessage='audit store unavailable (spike)' } | Out-Null
Start-Sleep 5; $inc = (Post '/incidents/search' @{ filter=@{ processInstanceKey=$pi } }).items; $inc | Select-Object errorType, elementId, jobKey | Format-Table
Invoke-RestMethod -Method Patch -Uri "$B/jobs/$($inc[0].jobKey)" -Headers $H -ContentType 'application/json' -Body '{"changeset":{"retries":1}}' | Out-Null
Post "/incidents/$($inc[0].incidentKey)/resolution" @{} | Out-Null
for ($i = 0; $i -lt 4; $i++) { Start-Sleep 2; foreach ($x in Activate) { Write-Host "$($x.elementId) $($x.listenerEventType)"; Post "/jobs/$($x.jobKey)/completion" @{} | Out-Null } }
Start-Sleep 4; (Invoke-RestMethod -Uri "$B/process-instances/$pi" -Headers $H).state
