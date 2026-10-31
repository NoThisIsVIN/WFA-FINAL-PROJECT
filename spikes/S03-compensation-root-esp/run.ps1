# S3 - compensation thrown from a ROOT interrupting message ESP (spike-withdraw, correlation =caseId)
. "$PSScriptRoot\common.ps1"
Start-Transcript -Path "$PSScriptRoot\run-output.txt" -Force | Out-Null
Write-Host '== Deploy'
Deploy-Bpmn @("$PSScriptRoot\s3-comp-root-esp.bpmn", "$PSScriptRoot\s3-comp-root-esp-nonint.bpmn", "$PSScriptRoot\s3-nested-withdraw.bpmn") | Out-Null
$handlerTypes = 'spike-s3-undo-survey', 'spike-s3-undo-design', 'spike-s3-undo-reserve', 'spike-s3-undo-await'

function Collect-Handlers { param([string]$Pik)
  $found = @{}
  foreach ($t in $handlerTypes) { $found[$t] = @(Get-Jobs $t -Pik $Pik -TimeoutSec 6) }
  Write-Host ('  handler jobs: ' + (($handlerTypes | ForEach-Object { "$_=$($found[$_].Count)" }) -join ' '))
  return $found }

function Finish { param([string]$Pik, $Found, [string]$Tag)
  Start-Sleep -Seconds 2
  foreach ($t in $handlerTypes) { foreach ($j in $Found[$t]) { Complete-Job $j.jobKey } }
  $ok = Wait-Until { (Get-PI $Pik).state -in 'COMPLETED','TERMINATED' } -What "$Tag completed"
  $inc = @(Get-Incidents $Pik)
  Check "$Tag-end PI ended ($((Get-PI $Pik).state)) via Withdraw_End, no incidents" ($ok -and @(Find-EI $Pik 'Withdraw_End' 'COMPLETED').Count -eq 1 -and $inc.Count -eq 0) "incidents=$($inc.Count)"
  Show-EI $Pik "$Tag final"
  if (-not $ok) { Invoke-C8 -Path "/process-instances/$Pik/cancellation" -Body @{} -AllowError | Out-Null; Write-Host "  (still $((Get-PI $Pik).state): cancelled $Pik)" } }

function Run-All { param([string]$P, [string]$V)
Write-Host "== [$V] A: SP1 COMPLETED, root Reserve COMPLETED, withdraw while Await (compensable) is in flight"
$case = "S3-$V-A-$(Get-Random)"
$pik = Start-Instance $P @{ caseId = $case }
foreach ($t in 'spike-s3-survey', 'spike-s3-design', 'spike-s3-reserve') { $j = @(Get-Jobs $t -Pik $pik)[0]; Complete-Job $j.jobKey @{ "done_$($t -replace '-','_')" = $true } }
$await = @(Get-Jobs 'spike-s3-await' -Pik $pik)
Check "[$V] A0 Await job active" ($await.Count -eq 1)
$r = Publish-Message 'spike-withdraw' $case
Write-Host "  publish response: $($r | ConvertTo-Json -Compress)"
$f = Collect-Handlers $pik
Check "[$V] A1 UndoSurvey runs (inside COMPLETED embedded SP)" ($f['spike-s3-undo-survey'].Count -eq 1)
Check "[$V] A2 UndoDesign runs (inside COMPLETED embedded SP)" ($f['spike-s3-undo-design'].Count -eq 1)
Check "[$V] A3 UndoReserve runs (completed root task)" ($f['spike-s3-undo-reserve'].Count -eq 1)
Check "[$V] A4 UndoAwait does NOT run (in-flight task interrupted)" ($f['spike-s3-undo-await'].Count -eq 0)
Check "[$V] A5 CompThrowAll waits while handlers pending" (@(Find-EI $pik 'CompThrowAll' 'ACTIVE').Count -eq 1)
$aw = @(Find-EI $pik 'Await')[0].state
if ($V -eq 'INT') { Check "[$V] A6 Await TERMINATED by interrupting ESP before compensation" ($aw -eq 'TERMINATED') "Await=$aw" }
else { Check "[$V] A6 Await still ACTIVE during compensation (non-interrupting)" ($aw -eq 'ACTIVE') "Await=$aw" }
Finish $pik $f "[$V] A"

Write-Host "== [$V] B: withdraw while SP1 still ACTIVE (Survey COMPLETED, Design in flight)"
$case = "S3-$V-B-$(Get-Random)"
$pik = Start-Instance $P @{ caseId = $case }
$j = @(Get-Jobs 'spike-s3-survey' -Pik $pik)[0]; Complete-Job $j.jobKey
$design = @(Get-Jobs 'spike-s3-design' -Pik $pik)
Publish-Message 'spike-withdraw' $case | Out-Null
$f = Collect-Handlers $pik
Check "[$V] B1 (info) UndoSurvey runs for completed task inside an ACTIVE (then terminated) SP" ($f['spike-s3-undo-survey'].Count -eq 1) "jobs=$($f['spike-s3-undo-survey'].Count)"
Check "[$V] B2 UndoDesign does NOT run (in flight)" ($f['spike-s3-undo-design'].Count -eq 0)
Check "[$V] B3 UndoReserve does NOT run (never reached)" ($f['spike-s3-undo-reserve'].Count -eq 0)
Finish $pik $f "[$V] B"

Write-Host "== [$V] C: withdraw immediately (nothing completed yet)"
$case = "S3-$V-C-$(Get-Random)"
$pik = Start-Instance $P @{ caseId = $case }
$s = @(Get-Jobs 'spike-s3-survey' -Pik $pik)
Publish-Message 'spike-withdraw' $case | Out-Null
$f = Collect-Handlers $pik
Check "[$V] C1 no handler runs; throw completes immediately" ((($handlerTypes | ForEach-Object { $f[$_].Count }) | Measure-Object -Sum).Sum -eq 0)
Finish $pik $f "[$V] C"

}

Run-All 'spike-s3-comp-root-esp' 'INT'
Run-All 'spike-s3-comp-root-esp-nonint' 'NONINT'

Write-Host '== [NESTED] B: withdraw ESP inside SP1 + root withdraw ESP; withdraw while Design in flight'
$X = 'spike-s3-nested-withdraw'
$case = "S3-NEST-B-$(Get-Random)"
$pik = Start-Instance $X @{ caseId = $case; withdrawn = $false }
$j = @(Get-Jobs 'spike-s3-survey' -Pik $pik)[0]; Complete-Job $j.jobKey
$design = @(Get-Jobs 'spike-s3-design' -Pik $pik)
Publish-Message 'spike-withdraw' $case | Out-Null
$f = Collect-Handlers $pik
Start-Sleep -Seconds 2
$inner = @(Find-EI $pik 'InnerWithdrawESP').Count; $root = @(Find-EI $pik 'WithdrawESP').Count
Check '[NESTED] B1 UndoSurvey runs via SP1-local withdraw ESP' ($f['spike-s3-undo-survey'].Count -eq 1)
Check '[NESTED] B2 one message -> only ONE ESP triggered (inner wins)' ($inner -eq 1 -and $root -eq 0) "InnerWithdrawESP=$inner WithdrawESP=$root"
foreach ($t in $handlerTypes) { foreach ($jj in $f[$t]) { Complete-Job $jj.jobKey } }
$ok = Wait-Until { (Get-PI $pik).state -eq 'COMPLETED' } -What 'nested B completed'
Check '[NESTED] B3 SP1 completes, XOR routes to End_Withdrawn' ($ok -and @(Find-EI $pik 'End_Withdrawn' 'COMPLETED').Count -eq 1 -and @(Find-EI $pik 'Reserve').Count -eq 0)
Show-EI $pik '[NESTED] B final'

Write-Host '== [NESTED] A: withdraw after SP1 completed (root ESP handles it)'
$case = "S3-NEST-A-$(Get-Random)"
$pik = Start-Instance $X @{ caseId = $case; withdrawn = $false }
foreach ($t in 'spike-s3-survey', 'spike-s3-design', 'spike-s3-reserve') { $j = @(Get-Jobs $t -Pik $pik)[0]; Complete-Job $j.jobKey }
$await = @(Get-Jobs 'spike-s3-await' -Pik $pik)
Publish-Message 'spike-withdraw' $case | Out-Null
$f = Collect-Handlers $pik
Check '[NESTED] A1 root ESP compensates Survey+Design (completed SP1) + Reserve, not Await' ($f['spike-s3-undo-survey'].Count -eq 1 -and $f['spike-s3-undo-design'].Count -eq 1 -and $f['spike-s3-undo-reserve'].Count -eq 1 -and $f['spike-s3-undo-await'].Count -eq 0)
Finish $pik $f '[NESTED] A'

Write-Host '== Summary'
$Global:SpikeResults | Format-Table -AutoSize -Wrap | Out-String -Width 250 | Write-Host
Stop-Transcript | Out-Null
