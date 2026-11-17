<#
File-only publication of the sales 990 candidate to the explicitly named local
NC HOME. It uses nc-patch.ps1's plan and backup, refuses a running HOME process,
and never connects to Oracle, changes NC registrations, starts services, or
touches files outside the six sales-overlay targets. This is not authorization
to enable a policy or start an application against a shared database.
#>
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$DeploymentId,[switch]$PreflightOnly)
$ErrorActionPreference='Stop'
Set-StrictMode -Version 2.0
Add-Type -AssemblyName System.IO.Compression.FileSystem
$project=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot)).TrimEnd('\')
$homePath=[IO.Path]::GetFullPath('E:\01-Home\NC65home\QuDuTest\nchome').TrimEnd('\')
if($DeploymentId -notmatch '^\d{8}-\d{6}-[a-f0-9]{8}$'){throw 'Invalid deployment id'}
$session=Join-Path $project ('build\deployment\'+$DeploymentId)
$planPath=Join-Path $session 'plan.json';$backupPath=Join-Path $session 'backup.json'
$journalPath=Join-Path $session 'sales-file-only-journal.json'
$utf8=New-Object Text.UTF8Encoding($false)
$expected=@(
    'modules/uapbd/lib/qd_esign_uapbd_public.jar',
    'modules/uapbd/META-INF/lib/qd_esign_uapbd_private.jar',
    'modules/uapbd/client/lib/qd_esign_uapbd_client.jar',
    'modules/so/lib/qd_esign_so_public.jar',
    'modules/so/META-INF/lib/qd_esign_so_private.jar',
    'modules/uapbd/META-INF/qd_esign_core.upm')
function Hash([string]$path){
    if(!(Test-Path -LiteralPath $path -PathType Leaf)){throw "Required file is missing: $path"}
    return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
}
function Assert-NoReparse([string]$path){
    $current=[IO.Path]::GetFullPath($path)
    while($current){
        if(Test-Path -LiteralPath $current){
            $item=Get-Item -LiteralPath $current -Force
            if($item.Attributes -band [IO.FileAttributes]::ReparsePoint){throw "Reparse path refused: $current"}
        }
        $parent=Split-Path -Parent $current
        if(!$parent -or $parent -eq $current){break};$current=$parent
    }
}
function Home-File([string]$relative){
    if($expected -cnotcontains $relative){throw "Unexpected target: $relative"}
    $path=[IO.Path]::GetFullPath((Join-Path $homePath $relative.Replace('/','\')))
    if(!$path.StartsWith($homePath+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Target escapes NC HOME'}
    Assert-NoReparse $path
    return $path
}
function Backup-File([string]$relative){
    if($expected -cnotcontains $relative){throw 'Unexpected backup target'}
    $root=[IO.Path]::GetFullPath((Join-Path $session 'backup\files')).TrimEnd('\')
    $path=[IO.Path]::GetFullPath((Join-Path $root $relative.Replace('/','\')))
    if(!$path.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Backup path escapes session'}
    Assert-NoReparse $path
    return $path
}
function Assert-HomeStopped {
    # Full host inventory; never print command lines because they can contain secrets.
    $processes=@(Get-CimInstance Win32_Process -ErrorAction Stop)
    if(!($processes | Where-Object { $_.ProcessId -eq 4 })){throw 'Incomplete process inventory'}
    foreach($p in $processes){
        if($p.Name -notmatch '^(java|javaw|ncstarter)(\.exe)?$'){continue}
        if(!$p.ExecutablePath -or !$p.CommandLine){throw "Cannot inspect Java process $($p.ProcessId)"}
        if($p.ExecutablePath.StartsWith($homePath+'\',[StringComparison]::OrdinalIgnoreCase) -or
                $p.CommandLine.IndexOf($homePath,[StringComparison]::OrdinalIgnoreCase) -ge 0){
            throw "Target NC HOME has an active process: $($p.ProcessId)"
        }
    }
    $listeners=@(Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object { $_.LocalPort -in @(80,5566,8005,18080) })
    if($listeners.Count -gt 0){throw 'A relevant local NC port is listening; identify it before file publication'}
}
function Save-Journal($data){
    $temp=$journalPath+'.new';Assert-NoReparse $temp
    [IO.File]::WriteAllText($temp,($data | ConvertTo-Json -Depth 8),$utf8)
    if(Test-Path -LiteralPath $journalPath){[IO.File]::Replace($temp,$journalPath,[NullString]::Value)}
    else{[IO.File]::Move($temp,$journalPath)}
}
function Replace-Atomically([string]$target,[byte[]]$bytes,[string]$want){
    $temp=$target+'.qd-sales-'+$DeploymentId
    Assert-NoReparse $temp
    if(Test-Path -LiteralPath $temp){throw "A previous staging file remains: $temp"}
    try{
        [IO.File]::WriteAllBytes($temp,$bytes)
        if((Hash $temp) -ne $want){throw 'Staged file hash mismatch'}
        [IO.File]::Replace($temp,$target,[NullString]::Value)
    }finally{if(Test-Path -LiteralPath $temp){Remove-Item -LiteralPath $temp -Force}}
    if((Hash $target) -ne $want){throw 'Published file hash mismatch'}
}
Assert-NoReparse $planPath;Assert-NoReparse $backupPath;Assert-NoReparse $homePath
$plan=Get-Content -LiteralPath $planPath -Raw -Encoding UTF8 | ConvertFrom-Json
$backup=Get-Content -LiteralPath $backupPath -Raw -Encoding UTF8 | ConvertFrom-Json
if($plan.schema_version -ne 1 -or $plan.deployment_id -ne $DeploymentId -or
        [IO.Path]::GetFullPath($plan.nc_home).TrimEnd('\') -ine $homePath -or @($plan.files).Count -ne 21){
    throw 'Plan does not identify the expected NC HOME and full module inventory'
}
if($backup.schema_version -ne 1 -or $backup.plan_sha256 -ne (Hash $planPath)){throw 'Backup does not match plan'}
$package=[IO.Path]::GetFullPath($plan.package)
if(!$package.StartsWith((Join-Path $project 'build')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Candidate must be in the project build directory'}
Assert-NoReparse $package
if((Hash $package) -ne $plan.package_sha256){throw 'Candidate changed after planning'}
$changed=@($plan.files | Where-Object { $_.old_sha256 -ne $_.new_sha256 })
if($changed.Count -ne $expected.Count){throw 'Candidate changed a different number of module files'}
$seen=New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
foreach($file in $changed){
    if($expected -cnotcontains $file.path -or !$seen.Add($file.path) -or
            !$file.old_sha256 -or $file.old_sha256 -notmatch '^[0-9A-Fa-f]{64}$' -or
            $file.new_sha256 -notmatch '^[0-9A-Fa-f]{64}$'){throw 'Unexpected or invalid changed target'}
    if((Hash (Home-File $file.path)) -ne $file.old_sha256 -or
            (Hash (Backup-File $file.path)) -ne $file.old_sha256){throw "Target or backup drifted: $($file.path)"}
}
foreach($name in $expected){if(!$seen.Contains($name)){throw "Expected sales target missing: $name"}}
$zip=[IO.Compression.ZipFile]::OpenRead($package)
try{
    foreach($file in $changed){
        $entry=$zip.GetEntry($file.path)
        if(!$entry -or $entry.Length -ne $file.size){throw "Candidate entry missing or wrong size: $($file.path)"}
        $stream=$entry.Open();try{$actual=(Get-FileHash -InputStream $stream -Algorithm SHA256).Hash}finally{$stream.Dispose()}
        if($actual -ne $file.new_sha256){throw "Candidate entry hash mismatch: $($file.path)"}
    }
}finally{$zip.Dispose()}
Assert-HomeStopped
if($PreflightOnly){
    [pscustomobject]@{action='SalesFileOnlyPreflight';deployment_id=$DeploymentId;targets=$changed.Count;
        stopped_home=$true;database_changed=$false;home_changed=$false} | ConvertTo-Json
    return
}
if(Test-Path -LiteralPath $journalPath){throw 'A publication journal already exists'}
$journal=[pscustomobject]@{schema_version=1;deployment_id=$DeploymentId;plan_sha256=(Hash $planPath);
    action='file-only-local-stopped-home';status='publishing';database_changed=$false;
    files=@($changed | ForEach-Object {[pscustomobject]@{path=$_.path;status='pending'}})}
Save-Journal $journal
try{
    $zip=[IO.Compression.ZipFile]::OpenRead($package)
    try{
        foreach($file in $changed){
            Assert-HomeStopped
            if((Hash $package) -ne $plan.package_sha256 -or (Hash (Home-File $file.path)) -ne $file.old_sha256){throw 'Pre-publication drift detected'}
            $row=$journal.files | Where-Object path -CEQ $file.path
            $row.status='applying';Save-Journal $journal
            $stream=$zip.GetEntry($file.path).Open();$memory=New-Object IO.MemoryStream
            try{$stream.CopyTo($memory);$bytes=$memory.ToArray()}finally{$stream.Dispose();$memory.Dispose()}
            Replace-Atomically (Home-File $file.path) $bytes $file.new_sha256
            $row.status='applied';Save-Journal $journal
        }
    }finally{$zip.Dispose()}
    Assert-HomeStopped
    foreach($file in $changed){if((Hash (Home-File $file.path)) -ne $file.new_sha256){throw 'Final target hash mismatch'}}
    $journal.status='published';Save-Journal $journal
}catch{
    $original=$_.Exception.Message;$rollbackErrors=New-Object 'System.Collections.Generic.List[string]'
    $reverse=@($changed);[array]::Reverse($reverse)
    foreach($file in $reverse){
        $row=$journal.files | Where-Object path -CEQ $file.path
        if($row.status -notin @('applying','applied')){continue}
        try{
            Assert-HomeStopped
            $target=Home-File $file.path;$current=Hash $target
            if($current -eq $file.old_sha256){$row.status='restored';Save-Journal $journal;continue}
            if($current -ne $file.new_sha256){throw 'Target was modified by another process'}
            $backupFile=Backup-File $file.path
            if((Hash $backupFile) -ne $file.old_sha256){throw 'Backup changed'}
            Replace-Atomically $target ([IO.File]::ReadAllBytes($backupFile)) $file.old_sha256
            $row.status='restored';Save-Journal $journal
        }catch{$rollbackErrors.Add($file.path+': '+$_.Exception.Message)}
    }
    $journal.status=if($rollbackErrors.Count -eq 0){'rolled-back'}else{'rollback-incomplete'}
    Save-Journal $journal
    throw ("Publication failed: $original; rollback=$($journal.status); errors=$($rollbackErrors -join ', ')")
}
[pscustomobject]@{action='SalesFileOnlyPublish';deployment_id=$DeploymentId;targets=$changed.Count;
    verified=$true;database_changed=$false;nc_started=$false} | ConvertTo-Json
