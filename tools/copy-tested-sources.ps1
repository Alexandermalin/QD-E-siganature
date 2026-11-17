param(
    [Parameter(Mandatory=$true)][string]$ProjectRoot,
    [Parameter(Mandatory=$true)][string]$StageRoot,
    [Parameter(Mandatory=$true)][object[]]$SourceRecords
)
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
$stage = [IO.Path]::GetFullPath($StageRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
$sourceRoot = Join-Path $stage 'sources'
if (!(Test-Path -LiteralPath $project -PathType Container) -or !(Test-Path -LiteralPath $stage -PathType Container) -or $SourceRecords.Count -eq 0) {
    throw 'Tested source packaging requires existing project and staging directories and a nonempty record.'
}
$seen = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
$verified = New-Object 'System.Collections.Generic.List[object]'
foreach ($record in $SourceRecords) {
    $relative = [string]$record.path
    $hash = [string]$record.sha256
    $javaSource = $relative -cmatch '^(?:(?:baseapp|uapbd|pu|so|ic|purp)/src/(?:client|public|private|test)/|tests/(?:java|persistence)/)[A-Za-z0-9_./$-]+\.java$'
    $clientXml = $relative -cmatch '^(?<module>baseapp|uapbd|pu|so|ic|purp)/src/client/nc/ui/\k<module>/qd/esign/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_.-]+\.xml$'
    if ((!$javaSource -and !$clientXml) -or
            $relative -match '(^|/)\.{1,2}(/|$)' -or !$seen.Add($relative) -or $hash -notmatch '^[A-Fa-f0-9]{64}$') {
        throw 'Invalid or duplicate tested source record.'
    }
    $inputFile = [IO.Path]::GetFullPath((Join-Path $project $relative))
    $outputFile = [IO.Path]::GetFullPath((Join-Path $sourceRoot $relative))
    if (!$inputFile.StartsWith($project + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
            !$outputFile.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
            !(Test-Path -LiteralPath $inputFile -PathType Leaf) -or (Get-FileHash -LiteralPath $inputFile -Algorithm SHA256).Hash -ne $hash) {
        throw "Tested source differs from build record: $relative"
    }
    $verified.Add([PSCustomObject]@{source=$inputFile;destination=$outputFile;sha256=$hash})
}
foreach ($item in $verified) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $item.destination) -Force | Out-Null
    Copy-Item -LiteralPath $item.source -Destination $item.destination
    if ((Get-FileHash -LiteralPath $item.destination -Algorithm SHA256).Hash -ne $item.sha256) {
        throw 'Copied source differs from the verified build record.'
    }
}
