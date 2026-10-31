# S13 - task listeners: assigning (sod-assign, corrections) + completing (sod-check, deny then approve)
$ErrorActionPreference = 'Stop'; Set-Location $PSScriptRoot
$B = 'http://localhost:8080/v2'; $H = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('demo:demo')) }
function Post($p, $b) { Invoke-RestMethod -Method Post -Uri "$B$p" -Headers $H -ContentType 'application/json' -Body ($b | ConvertTo-Json -Depth 10) }
function Activate($t) { Start-Sleep 3; (Post '/jobs/activation' @{ type=$t; timeout=60000; maxJobsToActivate=5; worker='spike-s13' }).jobs[0] }
# user-task commands BLOCK until listeners finish -> fire them as background curl jobs
function Fire($p, $json) { Start-Job { param($u, $j) $j | curl.exe -s -u demo:demo -H 'Content-Type: application/json' -X POST $u -d '@-' -w ' HTTP %{http_code}' } -ArgumentList "$B$p", $json }
foreach ($f in 's13-sod.bpmn','s13-legacy-names.bpmn') { curl.exe -s -u demo:demo -X POST "$B/deployments" -F "resources=@$f"; Write-Host '' }
$pi = (Post '/process-instances' @{ processDefinitionId='spike-s13-sod'; variables=@{ submittedBy='alice' } }).processInstanceKey
do { Start-Sleep 2; $ut = (Post '/user-tasks/search' @{ filter=@{ processInstanceKey=$pi } }).items } until ($ut); $u = $ut[0].userTaskKey
$a = Fire "/user-tasks/$u/assignment" '{"assignee":"alice"}'; $j = Activate 'sod-assign'
Post "/jobs/$($j.jobKey)/completion" @{ result=@{ type='userTask'; corrections=@{ assignee='demo'; priority=80 } } } | Out-Null
Receive-Job $a -Wait; Start-Sleep 3; Invoke-RestMethod -Uri "$B/user-tasks/$u" -Headers $H | Select-Object state, assignee, priority   # demo / 80
$c = Fire "/user-tasks/$u/completion" '{"variables":{"decision":"approve"}}'; $j = Activate 'sod-check'
Post "/jobs/$($j.jobKey)/completion" @{ result=@{ type='userTask'; denied=$true; deniedReason='SoD violation: approver must differ from submitter' } } | Out-Null
Receive-Job $c -Wait; Start-Sleep 3; Invoke-RestMethod -Uri "$B/user-tasks/$u" -Headers $H | Select-Object state, assignee      # 409 above; CREATED / demo
$c = Fire "/user-tasks/$u/completion" '{"variables":{"decision":"approve"}}'; $j = Activate 'sod-check'
Post "/jobs/$($j.jobKey)/completion" @{ result=@{ type='userTask'; denied=$false } } | Out-Null
Receive-Job $c -Wait; Start-Sleep 4; (Invoke-RestMethod -Uri "$B/process-instances/$pi" -Headers $H).state                      # COMPLETED
