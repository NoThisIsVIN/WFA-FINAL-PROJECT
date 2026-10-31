# S1 - conditional event sub-process inside an embedded sub-process + root conditional ESP + conditional gate
. "$PSScriptRoot\common.ps1"
Start-Transcript -Path "$PSScriptRoot\run-output.txt" -Force | Out-Null
Write-Host '== Deploy'
Deploy-Bpmn @("$PSScriptRoot\s1-conditional-esp.bpmn", "$PSScriptRoot\s1-root-esp-and-gate.bpmn") | Out-Null
$P = 'spike-s1-conditional-esp'

Write-Host '== A: update load at PROCESS (root) scope while SP waits on service task'
$pik = Start-Instance $P @{ load = 0; threshold = 100; upgradeRequested = $false }
$wait = @(Get-Jobs 'spike-s1-wait' -Pik $pik)[0]
Check 'A0 SP waiting on Wait job' ($null -ne $wait)
Set-Vars $pik @{ load = 150 } | Out-Null
$okFire = Wait-Until { @(Find-EI $pik 'MarkUpgrade' 'COMPLETED').Count -ge 1 } -What 'ESP fired'
$up = @(Get-Jobs 'spike-s1-upgrade' -Pik $pik)
Check 'A1 ESP fired on root-scope update load=150' ($okFire -and $up.Count -eq 1) "upgradeJobs=$($up.Count)"
$v = Get-Variables $pik | Where-Object { $_.name -eq 'upgradeRequested' }
Check 'A1b upgradeRequested set true by script task' (@($v | Where-Object { $_.value -eq 'true' }).Count -ge 1) (($v | ForEach-Object { "scope=$($_.scopeKey) val=$($_.value)" }) -join '; ')
Set-Vars $pik @{ load = 200 } | Out-Null
Start-Sleep -Seconds 6
$extra = @(Get-Jobs 'spike-s1-upgrade' -Pik $pik -TimeoutSec 4)
$espCount = @(Find-EI $pik 'ESP_Load').Count
Check 'A2 guard: second update (load=200) does NOT re-fire' ($espCount -eq 1 -and $extra.Count -eq 0) "ESP_Load instances=$espCount extraJobs=$($extra.Count)"
Complete-Job $wait.jobKey
Start-Sleep -Seconds 6
$sp = @(Find-EI $pik 'SP')[0]; $spEnd = @(Find-EI $pik 'SP_End')
Show-EI $pik 'after Wait completed, ESP still running'
Check 'A3 SP stays ACTIVE until ESP finishes (SP_End reached)' ($sp.state -eq 'ACTIVE' -and $spEnd.Count -eq 1) "SP=$($sp.state) SP_End=$($spEnd.state)"
Complete-Job $up[0].jobKey
$done = Wait-Until { (Get-PI $pik).state -eq 'COMPLETED' } -What 'A completed'
Check 'A4 SP + PI complete after ESP ends' $done
Show-EI $pik 'final A'

Write-Host '== B: update load LOCAL on the SP element-instance scope'
$pikB = Start-Instance $P @{ load = 0; threshold = 100; upgradeRequested = $false }
$waitB = @(Get-Jobs 'spike-s1-wait' -Pik $pikB)[0]
Wait-Until { @(Find-EI $pikB 'SP').Count -ge 1 } -What 'SP visible' | Out-Null
$spKey = @(Find-EI $pikB 'SP')[0].elementInstanceKey
Set-Vars $spKey @{ load = 150 } $true | Out-Null
$okB = Wait-Until { @(Find-EI $pikB 'MarkUpgrade' 'COMPLETED').Count -ge 1 } -What 'ESP fired B'
Check 'B1 ESP fires on SP-local variable update' $okB
$upB = @(Get-Jobs 'spike-s1-upgrade' -Pik $pikB); Complete-Job $upB[0].jobKey; Complete-Job $waitB.jobKey
Check 'B2 PI completed' (Wait-Until { (Get-PI $pikB).state -eq 'COMPLETED' } -What 'B completed')

Write-Host '== C: condition already true when SP is entered (load=150 at start)'
$pikC = Start-Instance $P @{ load = 150; threshold = 100; upgradeRequested = $false }
$waitC = @(Get-Jobs 'spike-s1-wait' -Pik $pikC)[0]
Start-Sleep -Seconds 6
$firedC = @(Find-EI $pikC 'MarkUpgrade').Count
Check 'C1 (info) ESP fires on scope activation when condition already true' ($firedC -ge 1) "MarkUpgrade instances=$firedC"
$upC = @(Get-Jobs 'spike-s1-upgrade' -Pik $pikC -TimeoutSec 4); foreach ($j in $upC) { Complete-Job $j.jobKey }; Complete-Job $waitC.jobKey
Wait-Until { (Get-PI $pikC).state -eq 'COMPLETED' } -What 'C completed' | Out-Null

Write-Host '== D: ROOT-level conditional ESP + conditional intermediate catch gate'
$G = 'spike-s1-root-esp-and-gate'
$pikD = Start-Instance $G @{ gridEmergency = $true; flag = $false }
$gateWaiting = Wait-Until { @(Find-EI $pikD 'EmergencyGate' 'ACTIVE').Count -eq 1 } -What 'gate active'
Check 'D0 gate waits while gridEmergency=true' $gateWaiting
Set-Vars $pikD @{ flag = $true } | Out-Null
$rj = @(Get-Jobs 'spike-s1-rootesp' -Pik $pikD)
Check 'D1 root-level non-interrupting conditional ESP fires' ($rj.Count -eq 1)
Set-Vars $pikD @{ gridEmergency = $false } | Out-Null
$w2 = @(Get-Jobs 'spike-s1-wait2' -Pik $pikD)
Check 'D2 conditional catch passes on gridEmergency=false update' ($w2.Count -eq 1)
foreach ($j in $rj + $w2) { Complete-Job $j.jobKey }
Check 'D3 PI completed' (Wait-Until { (Get-PI $pikD).state -eq 'COMPLETED' } -What 'D completed')
Show-EI $pikD 'final D'

Write-Host '== E: gate when gridEmergency already false on arrival'
$pikE = Start-Instance $G @{ gridEmergency = $false; flag = $false }
$w2E = @(Get-Jobs 'spike-s1-wait2' -Pik $pikE -TimeoutSec 10)
Check 'E1 gate passes immediately if condition already true' ($w2E.Count -eq 1)
foreach ($j in $w2E) { Complete-Job $j.jobKey }

Write-Host '== F: gate when gridEmergency variable is absent, then created'
$pikF = Start-Instance $G @{ flag = $false }
Start-Sleep -Seconds 4
$incF = @(Get-Incidents $pikF)
Check 'F0 absent variable -> gate waits, no incident' (@(Find-EI $pikF 'EmergencyGate' 'ACTIVE').Count -eq 1 -and $incF.Count -eq 0) "incidents=$($incF.Count) $(($incF | ForEach-Object { $_.errorMessage }) -join ' | ')"
Set-Vars $pikF @{ gridEmergency = $false } | Out-Null
$w2F = @(Get-Jobs 'spike-s1-wait2' -Pik $pikF)
Check 'F1 gate passes on variable CREATE' ($w2F.Count -eq 1)
foreach ($j in $w2F) { Complete-Job $j.jobKey }

Write-Host '== G: CONTROL - same ESP without the upgradeRequested guard (condition =load > threshold)'
Deploy-Bpmn @("$PSScriptRoot\s1-noguard-control.bpmn") | Out-Null
$pikG = Start-Instance 'spike-s1-noguard-control' @{ load = 0; threshold = 100 }
$waitG = @(Get-Jobs 'spike-s1-noguard-wait' -Pik $pikG)[0]
Set-Vars $pikG @{ load = 150 } | Out-Null
$g1 = @(Get-Jobs 'spike-s1-noguard-upgrade' -Pik $pikG)
Set-Vars $pikG @{ load = 200 } | Out-Null
$g2 = @(Get-Jobs 'spike-s1-noguard-upgrade' -Pik $pikG -TimeoutSec 8)
Set-Vars $pikG @{ load = 50 } | Out-Null; Start-Sleep -Seconds 1
Set-Vars $pikG @{ load = 300 } | Out-Null
$g3 = @(Get-Jobs 'spike-s1-noguard-upgrade' -Pik $pikG -TimeoutSec 8)
Check 'G1 (info) unguarded ESP: fires on 0->150' ($g1.Count -eq 1)
Check 'G2 (info) unguarded ESP re-fires on 150->200 (true->true update)' ($g2.Count -ge 1) "extra jobs=$($g2.Count)"
Check 'G3 (info) unguarded ESP re-fires on 50->300 (false->true)' ($g3.Count -ge 1) "extra jobs=$($g3.Count)"
foreach ($j in $g1 + $g2 + $g3) { Complete-Job $j.jobKey }; Complete-Job $waitG.jobKey
Wait-Until { (Get-PI $pikG).state -eq 'COMPLETED' } -What 'G completed' | Out-Null
Show-EI $pikG 'final G'

Write-Host '== H: load set by a JOB COMPLETION inside the SP (not via the variables API)'
Deploy-Bpmn @("$PSScriptRoot\s1-job-trigger.bpmn") | Out-Null
$pikH = Start-Instance 'spike-s1-job-trigger' @{ load = 0; threshold = 100; upgradeRequested = $false }
$calc = @(Get-Jobs 'spike-s1-jt-calc' -Pik $pikH)[0]
Complete-Job $calc.jobKey @{ load = 150 }
$hold = @(Get-Jobs 'spike-s1-jt-hold' -Pik $pikH)[0]
$h1 = @(Get-Jobs 'spike-s1-jt-upgrade' -Pik $pikH -TimeoutSec 10)
Check 'H1 ESP fires when load arrives via job completion variables' ($h1.Count -eq 1) "upgradeJobs=$($h1.Count)"
foreach ($j in $h1) { Complete-Job $j.jobKey }; Complete-Job $hold.jobKey
Wait-Until { (Get-PI $pikH).state -eq 'COMPLETED' } -What 'H completed' | Out-Null
Show-EI $pikH 'final H'

Write-Host '== Summary'
$Global:SpikeResults | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
Stop-Transcript | Out-Null
