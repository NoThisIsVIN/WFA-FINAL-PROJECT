# S4 - inclusive fork (3 conditional branches + default) joined by an inclusive gateway; C branch has an XOR
. "$PSScriptRoot\common.ps1"
Start-Transcript -Path "$PSScriptRoot\run-output.txt" -Force | Out-Null
Write-Host '== Deploy'
Deploy-Bpmn @("$PSScriptRoot\s4-inclusive-join.bpmn") | Out-Null
$P = 'spike-s4-inclusive-join'
$types = @{ A = 'spike-s4-a'; B = 'spike-s4-b'; C = 'spike-s4-c'; D = 'spike-s4-default' }

# $Order: branch letters in completion order. Before each completion except the last, assert the join has NOT fired.
function Run-Case { param([string]$Tag, [hashtable]$Vars, [string[]]$Expect, [string[]]$Order, [switch]$WaitFirst)
  Write-Host "== $Tag : vars=$($Vars | ConvertTo-Json -Compress) expect=$($Expect -join ',')"
  $pik = Start-Instance $P $Vars
  $jobs = @{}
  foreach ($b in $Expect) { $jobs[$b] = @(Get-Jobs $types[$b] -Pik $pik)[0] }
  $unexpected = @($types.Keys | Where-Object { $Expect -notcontains $_ } | ForEach-Object { @(Get-Jobs $types[$_] -Pik $pik -TimeoutSec 3) } )
  Check "$Tag-1 exactly branches [$($Expect -join ',')] activated" ((@($Expect | Where-Object { $null -eq $jobs[$_] }).Count -eq 0) -and $unexpected.Count -eq 0) "unexpectedJobs=$($unexpected.Count)"
  for ($i = 0; $i -lt $Order.Count; $i++) {
    $b = $Order[$i]
    if ($i -gt 0 -or $WaitFirst) {
      $early = @(Get-Jobs 'spike-s4-after' -Pik $pik -TimeoutSec 4)
      Check "$Tag-2.$i join waits (no After job) with $($Order.Count - $i) branch(es) pending" ($early.Count -eq 0)
      foreach ($e in $early) { Complete-Job $e.jobKey } }
    Complete-Job $jobs[$b].jobKey
  }
  $after = @(Get-Jobs 'spike-s4-after' -Pik $pik -TimeoutSec 15)
  Check "$Tag-3 join fires exactly once after the taken branches" ($after.Count -eq 1) "afterJobs=$($after.Count)"
  foreach ($a in $after) { Complete-Job $a.jobKey }
  $extra = @(Get-Jobs 'spike-s4-after' -Pik $pik -TimeoutSec 4)
  $ok = Wait-Until { (Get-PI $pik).state -eq 'COMPLETED' } -What "$Tag completed"
  Check "$Tag-4 PI COMPLETED, no second After, no incidents" ($ok -and $extra.Count -eq 0 -and @(Get-Incidents $pik).Count -eq 0) "joinInstances=$(@(Find-EI $pik 'Join').Count)"
  Show-EI $pik "$Tag final"
}

$base = @{ needA = $false; needB = $false; needC = $false; fastC = $false }
function V { param([hashtable]$o) $h = $base.Clone(); foreach ($k in $o.Keys) { $h[$k] = $o[$k] }; return $h }

Run-Case 'ONE'      (V @{ needA = $true })                               @('A')      @('A')
Run-Case 'TWO'      (V @{ needA = $true; needB = $true })                @('A', 'B') @('A', 'B')
Run-Case 'NONE'     (V @{})                                              @('D')      @('D')
Run-Case 'C-FULL'   (V @{ needB = $true; needC = $true })                @('B', 'C') @('C', 'B')
# C fast-track: XOR goes straight to the join; A still pending -> join must keep waiting for A
Run-Case 'C-FAST'   (V @{ needA = $true; needC = $true; fastC = $true }) @('A')      @('A') -WaitFirst
Run-Case 'ALL3'     (V @{ needA = $true; needB = $true; needC = $true }) @('A', 'B', 'C') @('B', 'C', 'A')

Write-Host '== MISSING variable (needB absent) - does the fork raise an incident?'
$pik = Start-Instance $P @{ needA = $true; needC = $false; fastC = $false }
Start-Sleep -Seconds 4
$inc = @(Get-Incidents $pik)
Check 'MISS-1 (info) absent condition variable' $true ("incidents=$($inc.Count) " + (($inc | ForEach-Object { "$($_.elementId) $($_.errorType): $($_.errorMessage)" }) -join ' | '))
$ja = @(Get-Jobs 'spike-s4-a' -Pik $pik -TimeoutSec 4); foreach ($j in $ja) { Complete-Job $j.jobKey }
$af = @(Get-Jobs 'spike-s4-after' -Pik $pik -TimeoutSec 6); foreach ($j in $af) { Complete-Job $j.jobKey }
Write-Host "  A jobs=$($ja.Count) After jobs=$($af.Count)"
$okM = Wait-Until { (Get-PI $pik).state -eq 'COMPLETED' } -What 'MISS completed'
Check 'MISS-2 (info) absent var treated as not-true: only A taken, PI completes' ($okM -and $ja.Count -eq 1 -and $af.Count -eq 1)
if (-not $okM) { Invoke-C8 -Path "/process-instances/$pik/cancellation" -Body @{} -AllowError | Out-Null; Write-Host "  cancelled $pik" }

Write-Host '== Summary'
$Global:SpikeResults | Format-Table -AutoSize -Wrap | Out-String -Width 250 | Write-Host
Stop-Transcript | Out-Null
