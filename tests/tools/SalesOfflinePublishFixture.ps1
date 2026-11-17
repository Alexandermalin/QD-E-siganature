<# Exercise the stopped-local-home publisher only against disposable copies under build. #>
param([Parameter(Mandatory=$true)][string]$Candidate,[Parameter(Mandatory=$true)][string]$ActualPlan)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..')).TrimEnd('\')
$realHome='E:\01-Home\NC65home\QuDuTest\nchome'
$sourcePlan=Get-Content -LiteralPath $ActualPlan -Raw -Encoding UTF8 | ConvertFrom-Json
$id=[Guid]::NewGuid().ToString('N')
$fixtureRoot=Join-Path $project ('build\sales-publish-fixtures\'+$id)
New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null
$publisher=Get-Content -LiteralPath (Join-Path $project 'tools\sales-990-offline-publish.ps1') -Raw -Encoding UTF8
$parserErrors=$null;$tokens=$null
$ast=[System.Management.Automation.Language.Parser]::ParseInput($publisher,[ref]$tokens,[ref]$parserErrors)
if($parserErrors){throw 'Publisher source does not parse'}
$stopped=$ast.Find({param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Assert-HomeStopped'},$true)
if(!$stopped){throw 'Stopped-home guard not found'}
$publisher=$publisher.Substring(0,$stopped.Extent.StartOffset)+'function Assert-HomeStopped { }'+$publisher.Substring($stopped.Extent.EndOffset)
function Prepare-Fixture([string]$name,[bool]$injectFailure){
    $fixtureHome=Join-Path $fixtureRoot ($name+'\home')
    New-Item -ItemType Directory -Path $fixtureHome -Force | Out-Null
    foreach($file in $sourcePlan.files){
        $relative=[string]$file.path
        $input=Join-Path $realHome $relative.Replace('/','\')
        $output=Join-Path $fixtureHome $relative.Replace('/','\')
        New-Item -ItemType Directory -Path (Split-Path -Parent $output) -Force | Out-Null
        Copy-Item -LiteralPath $input -Destination $output
        if((Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash -ne $file.old_sha256){throw "Fixture baseline drifted: $relative"}
    }
    $planText=& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $project 'tools\nc-patch.ps1') -Action Plan -NcHome $fixtureHome -Package $Candidate
    if($LASTEXITCODE -ne 0){throw 'Fixture planning failed'}
    $planOutput=$planText | ConvertFrom-Json
    $null=& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $project 'tools\nc-patch.ps1') -Action Backup -DeploymentId $planOutput.deployment_id
    if($LASTEXITCODE -ne 0){throw 'Fixture backup failed'}
    $planPath=Join-Path $project ('build\deployment\'+$planOutput.deployment_id+'\plan.json')
    $fixturePlan=Get-Content -LiteralPath $planPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $src=$publisher -replace '(?m)^\$project=.*$',('$project='''+$project.Replace("'","''")+'''')
    $src=$src -replace '(?m)^\$homePath=.*$',('$homePath='''+$fixtureHome.Replace("'","''")+'''')
    if($injectFailure){
        $marker='$row.status=''applied'';Save-Journal $journal'
        if(!$src.Contains($marker)){throw 'Injection marker was not found'}
        $src=$src.Replace($marker,($marker+"`r`n"+'            if($file.path -ceq ''modules/so/lib/qd_esign_so_public.jar''){throw ''FIXTURE_INJECTED_FAILURE''}'))
    }
    $fixturePublisher=Join-Path $fixtureRoot ($name+'\publisher.ps1')
    [IO.File]::WriteAllText($fixturePublisher,$src,(New-Object Text.UTF8Encoding($false)))
    $preflight=& powershell -NoProfile -ExecutionPolicy Bypass -File $fixturePublisher -DeploymentId $planOutput.deployment_id -PreflightOnly
    if($LASTEXITCODE -ne 0 -or !($preflight | ConvertFrom-Json).stopped_home){throw 'Fixture preflight failed'}
    $publishLog=Join-Path $fixtureRoot ($name+'\publish.log')
    $previousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        & powershell -NoProfile -ExecutionPolicy Bypass -File $fixturePublisher -DeploymentId $planOutput.deployment_id *> $publishLog
        $publishExit=$LASTEXITCODE
    } finally { $ErrorActionPreference=$previousPreference }
    if((!$injectFailure -and $publishExit -ne 0) -or ($injectFailure -and $publishExit -eq 0)){throw 'Fixture returned the wrong publication result'}
    foreach($file in $fixturePlan.files){
        $actual=(Get-FileHash -LiteralPath (Join-Path $fixtureHome $file.path.Replace('/','\')) -Algorithm SHA256).Hash
        $wanted=if($injectFailure){$file.old_sha256}else{$file.new_sha256}
        if($actual -ne $wanted){throw "Fixture files differ after publication or rollback: $($file.path)"}
    }
    $journal=Get-Content -LiteralPath (Join-Path $project ('build\deployment\'+$planOutput.deployment_id+'\sales-file-only-journal.json')) -Raw -Encoding UTF8 | ConvertFrom-Json
    if((!$injectFailure -and $journal.status -ne 'published') -or ($injectFailure -and $journal.status -ne 'rolled-back')){
        throw "Unexpected fixture journal: $($journal.status)"
    }
}
Prepare-Fixture 'success' $false
Prepare-Fixture 'rollback' $true
Write-Output 'PASS offline publisher fixture: six-file publication and injected-failure rollback; actual NC HOME unchanged.'
