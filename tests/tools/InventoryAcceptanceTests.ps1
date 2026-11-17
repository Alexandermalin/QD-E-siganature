$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$tool=Join-Path $root 'tools\inventory-acceptance.ps1'
$folder=Join-Path $root ('build\inventory-acceptance-test-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $folder | Out-Null
$file=Join-Path $folder 'fixture.csv'
$log=Join-Path $folder 'test.log'
function Invoke-Tool([string]$mode, [int]$expected) {
    & pwsh -NoProfile -ExecutionPolicy Bypass -File $tool -Mode $mode -File $file *> $log
    if ($LASTEXITCODE -ne $expected) { throw "Expected exit $expected, got $LASTEXITCODE for $mode. See $log" }
}
function Save-Rows($data) { $data | Export-Csv -LiteralPath $file -NoTypeInformation -Encoding utf8BOM }
function Mark-Executed($row) {
    $row.status='PASS'; $row.pkGroup='TEST-GROUP'; $row.pkOrg='TEST-ORG'; $row.userCode='TEST-USER'
    $row.actualResult='fixture only'; $row.resultEvidence='fixture:result'; $row.businessEvidence='fixture:business'
    $row.executedAt='2026-11-12T09:00:00+08:00'
}
Invoke-Tool 'Prepare' 0
Invoke-Tool 'Prepare' 1
Invoke-Tool 'Check' 2
$rows=@(Import-Csv -LiteralPath $file -Encoding UTF8)
if ($rows.Count -ne 830) { throw 'Expected one unexecuted record for each of 83 scopes and 10 scenarios.' }
$first=$rows | Where-Object { $_.caseId -eq 'BOTH_OFF' } | Select-Object -First 1
Mark-Executed $first
Save-Rows $rows
Invoke-Tool 'Check' 1
$first.objectPks='FIXTURE-PK'; $first.billCodes='FIXTURE-BILL'
Save-Rows $rows
Invoke-Tool 'Check' 2
$rollback=$rows | Where-Object { $_.caseId -eq 'AUDIT_FAILURE' } | Select-Object -First 1
Mark-Executed $rollback
$rollback.requestIds='FIXTURE-REQUEST'; $rollback.signEvidence='fixture:sign'; $rollback.auditEvidence='fixture:audit-failure'
Save-Rows $rows
Invoke-Tool 'Check' 1
$rollback.stockEvidence='fixture:stock'; $rollback.generatedBillEvidence='fixture:no-generated-bill'
Save-Rows $rows
Invoke-Tool 'Check' 2
$batch=$rows | Where-Object { $_.caseId -eq 'BATCH_PARTIAL' } | Select-Object -First 1
Mark-Executed $batch
$batch.requestIds='FIXTURE-REQUEST'; $batch.signEvidence='fixture:one-sign'; $batch.auditEvidence='fixture:audit'
$batch.objectPks='FIXTURE-PK;FIXTURE-PK'
Save-Rows $rows
Invoke-Tool 'Check' 1
$batch.objectPks='FIXTURE-PK-1;FIXTURE-PK-2'
Save-Rows $rows
Invoke-Tool 'Check' 2
Save-Rows (@($rows)+@($rows[0]))
Invoke-Tool 'Check' 1
Write-Output 'PASS InventoryAcceptanceTests (coverage, safe overwrite, evidence and batch validation; no NC)'
