# S5 - parallel multi-instance embedded SP: completionCondition over outputCollection, early exit, error end in one instance
. "$PSScriptRoot\common.ps1"
Start-Transcript -Path "$PSScriptRoot\run-output.txt" -Force | Out-Null
Write-Host '== Deploy'
Deploy-Bpmn @("$PSScriptRoot\s5-mi-completion.bpmn", "$PSScriptRoot\s5-mi-counters.bpmn") | Out-Null
$P = 'spike-s5-mi-completion'
$devices = @('SN-1', 'SN-2', 'SN-3', 'SN-4')

function Start-MI { param([string]$Tag, [string]$Proc = $P)
  Write-Host "== $Tag"
  $pik = Start-Instance $Proc @{ devices = $devices }
  $jobs = @(Get-Jobs 'spike-s5-test' -Pik $pik -Min 4)
  $byDev = @{}; foreach ($j in $jobs) { $byDev[$j.variables.device] = $j }
  Write-Host "  test jobs: $($jobs.Count) devices=$(($byDev.Keys | Sort-Object) -join ',')"
  return @{ pik = $pik; jobs = $byDev } }

function Done { param([hashtable]$Ctx, [string]$Serial, [string]$Status)
  $j = $Ctx.jobs[$Serial]
  $r = Invoke-C8 -Path "/jobs/$($j.jobKey)/completion" -Body @{ variables = @{ result = @{ serial = $Serial; status = $Status } } } -AllowError
  if ($r -and $r.error) { Write-Host "  complete $Serial -> $($r.error)"; return $false }
  Write-Host "  completed $Serial status=$Status"; return $true }

function MI-State { param([string]$Pik)
  $ei = Get-ElementInstances $Pik
  $body = @($ei | Where-Object { $_.elementId -eq 'MI_SP' -and $_.type -eq 'MULTI_INSTANCE_BODY' })
  $inner = @($ei | Where-Object { $_.elementId -eq 'MI_SP' -and $_.type -eq 'SUB_PROCESS' })
  $tasks = @($ei | Where-Object { $_.elementId -eq 'TestDevice' })
  return [pscustomobject]@{ body = ($body | ForEach-Object state) -join ','; inner = ($inner | Group-Object state | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ' '; tasks = ($tasks | Group-Object state | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ' ' } }

# --- A: 2 of 4 fail first -> expect early exit, siblings terminated
$c = Start-MI 'A: SN-1 FAILED, SN-2 FAILED first (SN-3, SN-4 still running)'
$first = $c.jobs['SN-1'].variables
Write-Host "  job vars seen by SN-1: $(ConvertTo-Json -InputObject $first -Compress -Depth 5)"
Done $c 'SN-1' 'FAILED' | Out-Null
Start-Sleep -Seconds 2
Done $c 'SN-2' 'FAILED' | Out-Null
$after = @(Get-Jobs 'spike-s5-after' -Pik $c.pik -TimeoutSec 15)
Check 'A1 completion condition met after 2nd FAILED -> AfterMI reached early' ($after.Count -eq 1)
if ($after.Count) { Write-Host "  AfterMI sees results = $(ConvertTo-Json -InputObject $after[0].variables.results -Compress -Depth 5)" }
$late3 = Done $c 'SN-3' 'OK'
Check 'A2 sibling SN-3 job no longer completable (instance terminated)' (-not $late3)
Wait-Until { (MI-State $c.pik).tasks -match 'TERMINATED=2' } -What 'siblings terminated' | Out-Null
$st = MI-State $c.pik
Check 'A3 2 siblings TERMINATED, 2 COMPLETED' ($st.tasks -match 'COMPLETED=2' -and $st.tasks -match 'TERMINATED=2') "body=$($st.body) inner=[$($st.inner)] tasks=[$($st.tasks)]"
$res = $after[0].variables.results
Check 'A4 results has 4 slots, unfinished entries are null' (@($res).Count -eq 4 -and $null -eq $res[2] -and $null -eq $res[3] -and $res[0].status -eq 'FAILED') ("results=" + (ConvertTo-Json -InputObject $res -Compress -Depth 5))
foreach ($a in $after) { Complete-Job $a.jobKey }
Check 'A5 PI COMPLETED via End' (Wait-Until { (Get-PI $c.pik).state -eq 'COMPLETED' } -What 'A completed')
Show-EI $c.pik 'A final'

# --- B: only 1 fails -> no early exit, all 4 complete
$c = Start-MI 'B: 1 FAILED + 3 OK (no early exit)'
Done $c 'SN-1' 'FAILED' | Out-Null; Start-Sleep -Seconds 1
Done $c 'SN-2' 'OK' | Out-Null; Start-Sleep -Seconds 1
$early = @(Get-Jobs 'spike-s5-after' -Pik $c.pik -TimeoutSec 4)
Check 'B1 no early exit with 1 FAILED + 1 OK' ($early.Count -eq 0)
Done $c 'SN-3' 'OK' | Out-Null; Done $c 'SN-4' 'OK' | Out-Null
$after = @(Get-Jobs 'spike-s5-after' -Pik $c.pik -TimeoutSec 15)
$res = $after[0].variables.results
Check 'B2 all 4 complete, results in device order (by index, not completion time)' ($after.Count -eq 1 -and @($res).Count -eq 4 -and $res[0].serial -eq 'SN-1' -and $res[3].serial -eq 'SN-4') ("results=" + (ConvertTo-Json -InputObject $res -Compress -Depth 5))
foreach ($a in $after) { Complete-Job $a.jobKey }
Wait-Until { (Get-PI $c.pik).state -eq 'COMPLETED' } -What 'B completed' | Out-Null

# --- B2: completion order differs from index order (SN-4 then SN-3 FAIL) -> slot placement
$c = Start-MI 'C: SN-4 FAILED then SN-3 FAILED (reverse order)'
Done $c 'SN-4' 'FAILED' | Out-Null; Start-Sleep -Seconds 1
Done $c 'SN-3' 'FAILED' | Out-Null
$after = @(Get-Jobs 'spike-s5-after' -Pik $c.pik -TimeoutSec 15)
$res = $after[0].variables.results
Check 'C1 early exit, results keep index slots [null,null,SN-3,SN-4]' ($after.Count -eq 1 -and $null -eq $res[0] -and $res[2].serial -eq 'SN-3' -and $res[3].serial -eq 'SN-4') ("results=" + (ConvertTo-Json -InputObject $res -Compress -Depth 5))
foreach ($a in $after) { Complete-Job $a.jobKey }
Wait-Until { (Get-PI $c.pik).state -eq 'COMPLETED' } -What 'C completed' | Out-Null

# --- D: error end event inside one instance
$c = Start-MI 'D: SN-2 FATAL -> error end event in one MI instance'
Done $c 'SN-1' 'OK' | Out-Null; Start-Sleep -Seconds 1
Done $c 'SN-2' 'FATAL' | Out-Null
$fatal = @(Get-Jobs 'spike-s5-fatal' -Pik $c.pik -TimeoutSec 15)
Check 'D1 error caught by boundary on the MI sub-process' ($fatal.Count -eq 1)
if ($fatal.Count) { Write-Host "  HandleFatal sees results = $(ConvertTo-Json -InputObject $fatal[0].variables.results -Compress -Depth 5) ; all vars: $(ConvertTo-Json -InputObject $fatal[0].variables -Compress -Depth 5)" }
$late = Done $c 'SN-3' 'OK'
Wait-Until { (MI-State $c.pik).body -eq 'TERMINATED' } -What 'MI body terminated' | Out-Null
$st = MI-State $c.pik
Check 'D2 siblings terminated, MI body TERMINATED, sibling job gone' ((-not $late) -and $st.body -eq 'TERMINATED' -and $st.tasks -match 'TERMINATED=2') "body=$($st.body) inner=[$($st.inner)] tasks=[$($st.tasks)]"
foreach ($f in $fatal) { Complete-Job $f.jobKey }
$ok = Wait-Until { (Get-PI $c.pik).state -eq 'COMPLETED' } -What 'D completed'
Check 'D3 PI COMPLETED via End_Fatal, AfterMI not reached' ($ok -and @(Find-EI $c.pik 'End_Fatal' 'COMPLETED').Count -eq 1 -and @(Find-EI $c.pik 'AfterMI').Count -eq 0 -and @(Get-Incidents $c.pik).Count -eq 0)
Show-EI $c.pik 'D final'

# --- E: completion condition on built-in counters (are numberOf* visible in completionCondition?)
$c = Start-MI 'E: counters variant =numberOfCompletedInstances >= 2 and numberOfInstances = 4 and numberOfActiveInstances = 2' 'spike-s5-mi-counters'
Done $c 'SN-1' 'OK' | Out-Null; Start-Sleep -Seconds 1
$early = @(Get-Jobs 'spike-s5-after' -Pik $c.pik -TimeoutSec 4)
Done $c 'SN-2' 'OK' | Out-Null
$after = @(Get-Jobs 'spike-s5-after' -Pik $c.pik -TimeoutSec 15)
$inc = @(Get-Incidents $c.pik)
Check 'E1 numberOfCompletedInstances/numberOfInstances/numberOfActiveInstances usable in completionCondition (exit after 2 of 4)' ($early.Count -eq 0 -and $after.Count -eq 1 -and $inc.Count -eq 0) ("after=$($after.Count) incidents=" + (($inc | ForEach-Object { $_.errorMessage }) -join ' | '))
foreach ($a in $after) { Complete-Job $a.jobKey }
Wait-Until { (Get-PI $c.pik).state -eq 'COMPLETED' } -What 'E completed' | Out-Null

Write-Host '== Summary'
$Global:SpikeResults | Format-Table -AutoSize -Wrap | Out-String -Width 250 | Write-Host
Stop-Transcript | Out-Null
