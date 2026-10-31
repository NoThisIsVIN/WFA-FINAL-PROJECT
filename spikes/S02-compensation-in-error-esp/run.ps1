# S2 - compensation throw inside an error event sub-process inside an embedded sub-process (+ fallbacks)
. "$PSScriptRoot\common.ps1"
Start-Transcript -Path "$PSScriptRoot\run-output.txt" -Force | Out-Null
Write-Host '== Deploy'
Deploy-Bpmn @('s2-comp-all', 's2-comp-activityref', 's2-root-esp-catch', 's2-flag-route', 's2-boundary-then-comp' | ForEach-Object { "$PSScriptRoot\$_.bpmn" }) | Out-Null

function Run-S2 { param([string]$ProcessId, [string]$Tag, [bool]$HasNotify, [bool]$ExpectRetract,
                        [string]$HandleType, [string]$EndId, [string]$ThrowId = 'CompThrow', [bool]$ExpectIncident = $false)
  Write-Host "== $Tag : $ProcessId"
  $pik = Start-Instance $ProcessId @{ caseId = "S2-$Tag"; spFailed = $false }
  $j = @(Get-Jobs 'spike-s2-reserve' -Pik $pik)[0]; Complete-Job $j.jobKey @{ reservationId = 'R-42' }
  if ($HasNotify) { $j = @(Get-Jobs 'spike-s2-notify' -Pik $pik)[0]; Complete-Job $j.jobKey @{ noticeId = 'N-7' } }
  $j = @(Get-Jobs 'spike-s2-validate' -Pik $pik)[0]
  Throw-JobError $j.jobKey 'VALIDATION_FAILED' 'validation failed in spike'
  $cancel  = @(Get-Jobs 'spike-s2-cancel-reserve' -Pik $pik -TimeoutSec 15)
  $retract = @(); if ($HasNotify) { $retract = @(Get-Jobs 'spike-s2-retract-notice' -Pik $pik -TimeoutSec 8) }
  Check "$Tag-1 CancelReserve handler job created" ($cancel.Count -eq 1) "jobs=$($cancel.Count) vars=$(($cancel | ForEach-Object { $_.variables | ConvertTo-Json -Compress }) -join ',')"
  if ($HasNotify) {
    if ($ExpectRetract) { Check "$Tag-2 RetractNotice handler job created (compensate all)" ($retract.Count -eq 1) "jobs=$($retract.Count)" }
    else { Check "$Tag-2 RetractNotice NOT created (activityRef=Reserve only)" ($retract.Count -eq 0) "jobs=$($retract.Count)" } }
  Start-Sleep -Seconds 4
  $ct = @(Find-EI $pik $ThrowId)[0]
  Check "$Tag-3 $ThrowId waits for handlers (waitForCompletion)" ($ct.state -eq 'ACTIVE') "$ThrowId=$($ct.state)"
  foreach ($h in $cancel + $retract) { Complete-Job $h.jobKey }
  $handle = @(Get-Jobs $HandleType -Pik $pik -TimeoutSec 15)
  if ($ExpectIncident) {
    Start-Sleep -Seconds 3
    $inc = @(Get-Incidents $pik)
    Check "$Tag-4 (finding) SP_FAILED thrown from inner error ESP is NOT caught (SP boundary / root error ESP) -> incident" ($handle.Count -eq 0 -and $inc.Count -eq 1) ("incident: " + (($inc | ForEach-Object { "$($_.elementId) $($_.errorType): $($_.errorMessage)" }) -join ' | '))
    Show-EI $pik "$Tag final (stuck)"
    Invoke-C8 -Path "/process-instances/$pik/cancellation" -Body @{} | Out-Null; Write-Host "  cancelled $pik"
    return }
  Check "$Tag-4 failure path reached -> $HandleType job" ($handle.Count -eq 1)
  foreach ($h in $handle) { Complete-Job $h.jobKey }
  $ok = Wait-Until { (Get-PI $pik).state -eq 'COMPLETED' } -What "$Tag completed"
  $endF = @(Find-EI $pik $EndId 'COMPLETED').Count; $endOk = @(Find-EI $pik 'End_OK').Count; $inc = @(Get-Incidents $pik)
  Check "$Tag-5 PI COMPLETED via $EndId, End_OK not reached, no incidents" ($ok -and $endF -eq 1 -and $endOk -eq 0 -and $inc.Count -eq 0) "$EndId=$endF End_OK=$endOk incidents=$($inc.Count)"
  Show-EI $pik "$Tag final"
}

Run-S2 'spike-s2-comp-all'           'ALL'  $true  $true  'spike-s2-handle'      'End_Failed' -ExpectIncident $true
Run-S2 'spike-s2-comp-activityref'   'REF'  $true  $false 'spike-s2-handle'      'End_Failed' -ExpectIncident $true
Run-S2 'spike-s2-root-esp-catch'     'ROOTESP' $true $true 'spike-s2-root-handle' 'RootErrESP_End' -ExpectIncident $true
Run-S2 'spike-s2-flag-route'         'FLAG' $false $false 'spike-s2-handle'      'End_Failed'
# BND: expected-to-FAIL checks 1 and 3 are the finding (no compensation of a terminated SP's completed tasks)
Run-S2 'spike-s2-boundary-then-comp' 'BND'  $false $false 'spike-s2-handle'      'End_Failed' -ThrowId 'CompThrowRoot'

Write-Host '== CTL : spike-s2-rethrow-control (error ESP rethrows SP_FAILED immediately, NO compensation step)'
Deploy-Bpmn @("$PSScriptRoot\s2-rethrow-control.bpmn") | Out-Null
$pik = Start-Instance 'spike-s2-rethrow-control' @{ caseId = 'S2-CTL' }
$j = @(Get-Jobs 'spike-s2-reserve' -Pik $pik)[0]; Complete-Job $j.jobKey
$j = @(Get-Jobs 'spike-s2-notify' -Pik $pik)[0]; Complete-Job $j.jobKey
$j = @(Get-Jobs 'spike-s2-validate' -Pik $pik)[0]; Throw-JobError $j.jobKey 'VALIDATION_FAILED' 'ctl'
$handle = @(Get-Jobs 'spike-s2-handle' -Pik $pik -TimeoutSec 15)
Start-Sleep -Seconds 3; $inc = @(Get-Incidents $pik)
Check 'CTL-1 (info) rethrow from error ESP WITHOUT compensation caught by SP boundary' ($handle.Count -eq 1) ("handleJobs=$($handle.Count) incidents: " + (($inc | ForEach-Object { "$($_.elementId) $($_.errorType): $($_.errorMessage)" }) -join ' | '))
foreach ($h in $handle) { Complete-Job $h.jobKey }
if ($handle.Count -eq 0) { Invoke-C8 -Path "/process-instances/$pik/cancellation" -Body @{} | Out-Null; Write-Host "  cancelled $pik" }
else { Wait-Until { (Get-PI $pik).state -eq 'COMPLETED' } -What 'CTL completed' | Out-Null }
Show-EI $pik 'CTL final'

Write-Host '== Summary'
$Global:SpikeResults | Format-Table -AutoSize -Wrap | Out-String -Width 250 | Write-Host
Stop-Transcript | Out-Null
