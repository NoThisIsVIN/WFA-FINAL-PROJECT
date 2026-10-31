# S14 - timer start events: 6-field cron, 5-field cron (rejected), ISO R/PT1M; then stop via new version
Set-Location $PSScriptRoot
$B = 'http://localhost:8080/v2'
foreach ($f in 'cron6.bpmn','cron5.bpmn','iso.bpmn') { Write-Host "deploy $f"; curl.exe -s -u demo:demo -X POST "$B/deployments" -F "resources=@$f"; Write-Host '' }
Write-Host 'waiting 150 s for timer-started instances...'; Start-Sleep 150
curl.exe -s -u demo:demo -H 'Content-Type: application/json' -X POST "$B/process-instances/search" -d '{\"filter\":{\"processDefinitionId\":{\"$like\":\"spike-s14-*\"}},\"page\":{\"limit\":50}}'
Write-Host ''; Write-Host 'STOP: deploying none-start versions (removes timer subscriptions)'
foreach ($f in 'stop-cron6.bpmn','stop-iso.bpmn') { curl.exe -s -u demo:demo -X POST "$B/deployments" -F "resources=@$f"; Write-Host '' }
