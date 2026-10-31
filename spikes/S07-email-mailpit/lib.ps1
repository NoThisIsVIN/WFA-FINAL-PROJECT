# Shared helpers for the Phase-0 spikes (Windows PowerShell 5.1 compatible).
$ErrorActionPreference = 'Stop'
$Global:C8 = 'http://localhost:8080'
$Global:C8Auth = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('demo:demo'))

function Invoke-Http {
  # Returns @{ Status = <int>; Raw = <string>; Json = <object or $null> } and never throws on HTTP errors.
  param([string]$Method = 'POST', [string]$Url, $Body = $null, [hashtable]$Headers = @{}, [switch]$NoAuth, [int]$TimeoutSec = 60)
  $h = @{}
  foreach ($k in $Headers.Keys) { $h[$k] = $Headers[$k] }
  if (-not $NoAuth) { $h['Authorization'] = $Global:C8Auth }
  $bytes = $null
  if ($null -ne $Body) {
    if ($Body -is [byte[]]) { $bytes = $Body }
    elseif ($Body -is [string]) { $bytes = [Text.Encoding]::UTF8.GetBytes($Body) }
    else { $bytes = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 20 -Compress)) }
  }
  $status = 0; $raw = ''
  try {
    $p = @{ Method = $Method; Uri = $Url; Headers = $h; UseBasicParsing = $true; TimeoutSec = $TimeoutSec }
    if ($null -ne $bytes) { $p['Body'] = $bytes; $p['ContentType'] = 'application/json' }
    $r = Invoke-WebRequest @p
    $status = [int]$r.StatusCode
    $raw = if ($r.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($r.Content) } else { [string]$r.Content }
  } catch [System.Net.WebException] {
    $resp = $_.Exception.Response
    if ($null -eq $resp) { return @{ Status = -1; Raw = $_.Exception.Message; Json = $null } }
    $status = [int]$resp.StatusCode
    $raw = if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $_.ErrorDetails.Message } else {
      try { (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd() } catch { '' } }
  }
  $json = $null
  if ($raw) { try { $json = $raw | ConvertFrom-Json } catch { } }
  return @{ Status = $status; Raw = $raw; Json = $json }
}

function C8 { param([string]$Path, $Body = $null, [string]$Method = 'POST') Invoke-Http -Method $Method -Url ($Global:C8 + $Path) -Body $Body }

function Deploy-Bpmn {
  param([string]$File)
  $out = & curl.exe -s -u demo:demo -F ("resources=@" + $File) "$Global:C8/v2/deployments"
  $j = $out | ConvertFrom-Json
  if (-not $j.deployments) { throw "Deployment failed: $out" }
  $pd = $j.deployments[0].processDefinition
  Write-Host ("Deployed {0} v{1} key={2}" -f $pd.processDefinitionId, $pd.processDefinitionVersion, $pd.processDefinitionKey)
  return $pd
}

function Start-Instance {
  param([string]$ProcessId, [hashtable]$Variables = @{})
  $r = C8 '/v2/process-instances' @{ processDefinitionId = $ProcessId; variables = $Variables }
  if ($r.Status -ne 200) { throw "Start failed: $($r.Status) $($r.Raw)" }
  return [string]$r.Json.processInstanceKey
}

function Search-Until {
  # Polls a v2 search endpoint until $Until (scriptblock over items) returns $true or ~$MaxSec passes. Returns items.
  param([string]$Path, $Filter, [scriptblock]$Until = { param($items) $items.Count -gt 0 }, [int]$MaxSec = 30)
  $deadline = (Get-Date).AddSeconds($MaxSec); $items = @()
  do {
    $r = C8 $Path @{ filter = $Filter; page = @{ limit = 100 } }
    $items = @(); if ($r.Json -and $r.Json.items) { $items = @($r.Json.items) }
    if (& $Until $items) { return ,$items }
    Start-Sleep -Seconds 2
  } while ((Get-Date) -lt $deadline)
  return ,$items
}

function Get-Elements { param([string]$Pi, [scriptblock]$Until = { param($i) $i.Count -gt 0 }, [int]$MaxSec = 30)
  Search-Until '/v2/element-instances/search' @{ processInstanceKey = $Pi } $Until $MaxSec }

function Show-Elements { param($items) $items | ForEach-Object { '    {0,-28} {1,-22} {2}' -f $_.elementId, $_.type, $_.state } | Write-Host }

function Get-Vars { param([string]$Pi) $r = C8 '/v2/variables/search' @{ filter = @{ processInstanceKey = $Pi }; page = @{ limit = 100 } }
  $h = [ordered]@{}; foreach ($v in @($r.Json.items)) { $h[$v.name] = $v.value }; return $h }

function Get-Incidents { param([string]$Pi, [int]$MaxSec = 30)
  Search-Until '/v2/incidents/search' @{ processInstanceKey = $Pi } { param($i) $i.Count -gt 0 } $MaxSec }

function Get-InstanceState { param([string]$Pi)
  $r = C8 '/v2/process-instances/search' @{ filter = @{ processInstanceKey = $Pi } }
  if ($r.Json.items) { return $r.Json.items[0].state } return '?' }

function Wait-InstanceState { param([string]$Pi, [string]$State = 'COMPLETED', [int]$MaxSec = 40)
  $items = Search-Until '/v2/process-instances/search' @{ processInstanceKey = $Pi } { param($i) $i.Count -gt 0 -and $i[0].state -eq $State } $MaxSec
  if ($items.Count) { return $items[0].state } return '?' }

function Section { param([string]$t) Write-Host ''; Write-Host ('=== ' + $t) -ForegroundColor Cyan }
