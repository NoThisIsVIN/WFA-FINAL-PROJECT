# Deploys every BPMN, DMN and form under bpmn-models/ in ONE deployment (POST /v2/deployments),
# so processes, decisions and forms that use bindingType="deployment" always ship together.
# Usage: powershell -File bpmn-models\deploy\deploy.ps1 [-Only dmn] [-BaseUrl http://localhost:8080]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$User = 'demo',
    [string]$Password = 'demo',
    [ValidateSet('bpmn', 'dmn', 'forms')]
    [string[]]$Only = @('bpmn', 'dmn', 'forms')
)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$files = foreach ($folder in $Only) {
    $sub = @{ bpmn = 'processes'; dmn = 'decisions'; forms = 'forms' }[$folder]
    $dir = Join-Path $root $sub
    if (Test-Path $dir) {
        Get-ChildItem $dir -File -Recurse | Where-Object { $_.Extension -in '.bpmn', '.dmn', '.form' }
    }
}
if (-not $files) { throw "Nothing to deploy under bpmn-models\ ($($Only -join ', '))" }

$curlArgs = @('-s', '-S', '-u', "${User}:${Password}", '-w', '\n%{http_code}')
foreach ($file in $files) { $curlArgs += @('-F', "resources=@$($file.FullName)") }
$output = & curl.exe @curlArgs "$BaseUrl/v2/deployments"
if ($LASTEXITCODE -ne 0) { throw "curl failed with exit code $LASTEXITCODE" }

$lines = @($output)
$status = $lines[-1]
$body = ($lines[0..($lines.Count - 2)]) -join "`n"
if ($status -ne '200') { throw "Deployment failed (HTTP $status): $body" }

$result = $body | ConvertFrom-Json
Write-Host "Deployment $($result.deploymentKey): $($files.Count) file(s)"
foreach ($d in $result.deployments) {
    if ($d.processDefinition) {
        Write-Host ("  process   {0} v{1}" -f $d.processDefinition.processDefinitionId, $d.processDefinition.processDefinitionVersion)
    }
    if ($d.decisionDefinition) {
        Write-Host ("  decision  {0} v{1}" -f $d.decisionDefinition.decisionDefinitionId, $d.decisionDefinition.version)
    }
    if ($d.decisionRequirements) {
        Write-Host ("  drd       {0} v{1}" -f $d.decisionRequirements.decisionRequirementsId, $d.decisionRequirements.version)
    }
    if ($d.form) {
        Write-Host ("  form      {0} v{1}" -f $d.form.formId, $d.form.version)
    }
}
