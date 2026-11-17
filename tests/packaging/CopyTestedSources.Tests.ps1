$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$helper = Join-Path $project 'tools/copy-tested-sources.ps1'
if (!(Test-Path -LiteralPath $helper -PathType Leaf)) { throw 'Tested source packaging helper is missing.' }

$testRoot = Join-Path $project ('build/sapply-package-test/' + [Guid]::NewGuid().ToString('N'))
$inputRoot = Join-Path $testRoot 'input'
$stageRoot = Join-Path $testRoot 'stage'
$relative = 'ic/src/public/nc/vo/ic/qd/esign/Example.java'
$inputFile = Join-Path $inputRoot $relative
New-Item -ItemType Directory -Path (Split-Path -Parent $inputFile),$stageRoot -Force | Out-Null
[IO.File]::WriteAllText($inputFile, 'class Example {}', (New-Object Text.UTF8Encoding($false)))
$hash = (Get-FileHash -LiteralPath $inputFile -Algorithm SHA256).Hash
$record = [PSCustomObject]@{path=$relative;sha256=$hash}

& $helper -ProjectRoot $inputRoot -StageRoot $stageRoot -SourceRecords @($record)
$copied = Join-Path $stageRoot ('sources/' + $relative)
if (!(Test-Path -LiteralPath $copied -PathType Leaf) -or (Get-FileHash -LiteralPath $copied -Algorithm SHA256).Hash -ne $hash) {
    throw 'Recorded source was not copied exactly.'
}

$xmlRelative = 'ic/src/client/nc/ui/ic/qd/esign/Example.xml'
$xmlInput = Join-Path $inputRoot $xmlRelative
New-Item -ItemType Directory -Path (Split-Path -Parent $xmlInput) -Force | Out-Null
[IO.File]::WriteAllText($xmlInput, '<beans/>', (New-Object Text.UTF8Encoding($false)))
$xmlHash = (Get-FileHash -LiteralPath $xmlInput -Algorithm SHA256).Hash
$xmlRecord = [PSCustomObject]@{path=$xmlRelative;sha256=$xmlHash}
& $helper -ProjectRoot $inputRoot -StageRoot $stageRoot -SourceRecords @($xmlRecord)
$xmlCopy = Join-Path $stageRoot ('sources/' + $xmlRelative)
if (!(Test-Path -LiteralPath $xmlCopy -PathType Leaf) -or (Get-FileHash -LiteralPath $xmlCopy -Algorithm SHA256).Hash -ne $xmlHash) {
    throw 'Recorded client XML was not copied exactly.'
}

$badHash = [PSCustomObject]@{path=$relative;sha256=('0' * 64)}
try { & $helper -ProjectRoot $inputRoot -StageRoot $stageRoot -SourceRecords @($badHash); throw 'Changed source was accepted.' }
catch { if ($_.Exception.Message -eq 'Changed source was accepted.') { throw } }
$badPath = [PSCustomObject]@{path='../outside.java';sha256=$hash}
try { & $helper -ProjectRoot $inputRoot -StageRoot $stageRoot -SourceRecords @($badPath); throw 'Traversal source was accepted.' }
catch { if ($_.Exception.Message -eq 'Traversal source was accepted.') { throw } }
try { & $helper -ProjectRoot $inputRoot -StageRoot $stageRoot -SourceRecords @($record,$record); throw 'Duplicate source was accepted.' }
catch { if ($_.Exception.Message -eq 'Duplicate source was accepted.') { throw } }
Write-Output 'PASS CopyTestedSources (recorded source, changed hash, traversal and duplicate rejection)'
