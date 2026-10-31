# Shared helpers for the Phase-0 spikes (Camunda 8.10 Orchestration Cluster REST API v2).
# Windows PowerShell 5.1 compatible. Dot-source from run.ps1:  . "$PSScriptRoot\common.ps1"
$ErrorActionPreference = 'Stop'
$Global:C8Base = 'http://localhost:8080/v2'
$Global:C8Auth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('demo:demo')) }

function Invoke-C8 {
  param([string]$Method = 'POST', [string]$Path, $Body = $null, [switch]$AllowError)
  $uri = "$Global:C8Base$Path"
  $params = @{ Method = $Method; Uri = $uri; Headers = $Global:C8Auth; ContentType = 'application/json; charset=utf-8' }
  if ($null -ne $Body) { $params.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 30 -Compress)) }
  try { return Invoke-RestMethod @params }
  catch {
    $msg = $_.Exception.Message
    $resp = $_.Exception.Response
    if ($resp) { try { $sr = New-Object IO.StreamReader($resp.GetResponseStream()); $msg = "$([int]$resp.StatusCode) $($sr.ReadToEnd())" } catch {} }
    if ($AllowError) { return [pscustomobject]@{ error = $msg } }
    throw "C8 $Method $Path failed: $msg"
  }
}

function Deploy-Bpmn {
  param([string[]]$Files)
  $args = @('-s', '-u', 'demo:demo', '-X', 'POST', "$Global:C8Base/deployments")
  foreach ($f in $Files) { $args += @('-F', "resources=@$f") }
  $out = & curl.exe @args
  $o = $out | ConvertFrom-Json
  if (-not $o.deployments) { throw "Deployment failed: $out" }
  foreach ($d in $o.deployments) { if ($d.processDefinition) { Write-Host ("  deployed {0} v{1} key={2}" -f $d.processDefinition.processDefinitionId, $d.processDefinition.processDefinitionVersion, $d.processDefinition.processDefinitionKey) } }
  return $o
}

function Start-Instance {
  param([string]$ProcessId, [hashtable]$Variables = @{})
  $r = Invoke-C8 -Path '/process-instances' -Body @{ processDefinitionId = $ProcessId; variables = $Variables }
  Write-Host "  started $ProcessId pik=$($r.processInstanceKey)"
  return $r.processInstanceKey
}

# Activate jobs of a type, retrying until at least $Min are returned (or timeout). Optional $Pik filter (client side).
function Get-Jobs {
  param([string]$Type, [int]$Min = 1, [int]$Max = 20, [string]$Pik = $null, [int]$TimeoutSec = 20)
  $deadline = (Get-Date).AddSeconds($TimeoutSec); $all = @()
  while ((Get-Date) -lt $deadline) {
    $r = Invoke-C8 -Path '/jobs/activation' -Body @{ type = $Type; timeout = 60000; maxJobsToActivate = $Max; worker = 'spike'; requestTimeout = 1000 }
    $jobs = @($r.jobs)
    if ($Pik) {
      # hand jobs that belong to other instances straight back (fail with unchanged retries, no backoff)
      foreach ($j in @($jobs | Where-Object { $_.processInstanceKey -ne $Pik })) {
        Invoke-C8 -Path "/jobs/$($j.jobKey)/failure" -Body @{ retries = [int]$j.retries; retryBackOff = 0; errorMessage = 'released by spike' } -AllowError | Out-Null }
      $jobs = @($jobs | Where-Object { $_.processInstanceKey -eq $Pik }) }
    $all += $jobs
    if ($all.Count -ge $Min) { return $all }
    Start-Sleep -Milliseconds 500
  }
  return $all
}

function Complete-Job { param([string]$Key, [hashtable]$Variables = @{})
  Invoke-C8 -Path "/jobs/$Key/completion" -Body @{ variables = $Variables } | Out-Null }

function Throw-JobError { param([string]$Key, [string]$Code, [string]$Message = 'spike', [hashtable]$Variables = @{})
  Invoke-C8 -Path "/jobs/$Key/error" -Body @{ errorCode = $Code; errorMessage = $Message; variables = $Variables } | Out-Null }

function Set-Vars { param([string]$ScopeKey, [hashtable]$Variables, [bool]$Local = $false)
  Invoke-C8 -Method PUT -Path "/element-instances/$ScopeKey/variables" -Body @{ variables = $Variables; local = $Local } }

function Publish-Message { param([string]$Name, [string]$CorrelationKey, [hashtable]$Variables = @{})
  Invoke-C8 -Path '/messages/publication' -Body @{ name = $Name; correlationKey = $CorrelationKey; timeToLive = 0; variables = $Variables } }

function Search-All { param([string]$Path, [hashtable]$Filter, [int]$Limit = 200)
  $r = Invoke-C8 -Path $Path -Body @{ filter = $Filter; page = @{ limit = $Limit } }
  return @($r.items) }

function Get-ElementInstances { param([string]$Pik) Search-All '/element-instances/search' @{ processInstanceKey = $Pik } }
function Get-Variables { param([string]$Pik) Search-All '/variables/search' @{ processInstanceKey = $Pik } }
function Get-Incidents { param([string]$Pik) Search-All '/incidents/search' @{ processInstanceKey = $Pik } }
function Get-PI { param([string]$Pik) @(Search-All '/process-instances/search' @{ processInstanceKey = $Pik })[0] }

# Poll a scriptblock until it returns $true (secondary storage lags). Returns $true/$false.
function Wait-Until { param([scriptblock]$Cond, [int]$TimeoutSec = 30, [string]$What = 'condition')
  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) { try { if (& $Cond) { return $true } } catch {} ; Start-Sleep -Seconds 2 }
  Write-Host "  TIMEOUT waiting for: $What" -ForegroundColor Yellow; return $false }

# Element instances that match elementId (optionally state)
function Find-EI { param([string]$Pik, [string]$ElementId, [string]$State = $null)
  $e = Get-ElementInstances $Pik | Where-Object { $_.elementId -eq $ElementId }
  if ($State) { $e = $e | Where-Object { $_.state -eq $State } }
  return @($e) }

function Show-EI { param([string]$Pik, [string]$Label = '')
  Write-Host "  --- element instances $Label (pik=$Pik)"
  Get-ElementInstances $Pik | Sort-Object { [int64]$_.elementInstanceKey } | ForEach-Object { Write-Host ("    {0,-32} {1,-26} {2}" -f $_.elementId, $_.type, $_.state) } }

function Show-Vars { param([string]$Pik)
  Write-Host "  --- variables (pik=$Pik)"
  Get-Variables $Pik | ForEach-Object { Write-Host ("    scope={0} {1} = {2}" -f $_.scopeKey, $_.name, $_.value) } }

function Check { param([string]$Name, [bool]$Ok, [string]$Detail = '')
  if ($Ok) { Write-Host "  [PASS] $Name $Detail" -ForegroundColor Green } else { Write-Host "  [FAIL] $Name $Detail" -ForegroundColor Red }
  $Global:SpikeResults += ,([pscustomobject]@{ check = $Name; ok = $Ok; detail = $Detail }) }
$Global:SpikeResults = @()
