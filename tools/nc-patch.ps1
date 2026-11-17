<#
NC65 additive patch deployment. No SQL, registration, task, or product configuration is changed.
Plan -NcHome <dedicated home> -Package <candidate.zip>
Backup|Verify -DeploymentId <id>
Deploy|Rollback -DeploymentId <id> -Admission <reviewed admission.json>
State and confidential configuration backups stay under project build/deployment with a private ACL.
#>
[CmdletBinding()]
param(
    [ValidateSet('Plan','Backup','Deploy','Verify','Rollback')][string]$Action = 'Plan',
    [string]$NcHome,
    [string]$Package,
    [string]$DeploymentId,
    [string]$Admission
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$projectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$stateRoot = Join-Path $projectRoot 'build\deployment'
$utf8 = New-Object Text.UTF8Encoding($false)
$modules = @('baseapp','uapbd','pu','so','ic','purp')
$allowed = @()
foreach ($module in $modules) {
    $allowed += "modules/$module/lib/qd_esign_${module}_public.jar"
    $allowed += "modules/$module/META-INF/lib/qd_esign_${module}_private.jar"
}
$allowed += 'modules/uapbd/client/lib/qd_esign_uapbd_client.jar'
$allowed += @('baseapp/META-INF/qd_esign_auth.upm','ic/META-INF/qd_esign_inventory_adapter.upm',
    'pu/META-INF/qd_esign_purchase_adapter.upm','purp/META-INF/qd_esign_supplier_price_adapter.upm',
    'so/META-INF/qd_esign_saleorder.upm','uapbd/META-INF/qd_esign_core.upm',
    'uapbd/META-INF/qd_esign_logs.upm','uapbd/META-INF/qd_esign_material.upm') | ForEach-Object { 'modules/' + $_ }

function Assert-NoReparse([string]$Path) {
    $cursor = [IO.Path]::GetFullPath($Path)
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            $item = Get-Item -LiteralPath $cursor -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse-point path refused: $cursor" }
        }
        $parent = Split-Path -Parent $cursor
        if (!$parent -or $parent -eq $cursor) { break }
        $cursor = $parent
    }
}
function Canonical([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path) -or ![IO.Path]::IsPathRooted($Path)) { throw 'An absolute local path is required.' }
    $full = [IO.Path]::GetFullPath($Path).TrimEnd('\','/')
    if ($full.StartsWith('\\') -or $full -match '^\\[?.]') { throw 'Network/device paths are not supported.' }
    Assert-NoReparse $full
    return $full
}
function Under([string]$Root,[string]$Relative) {
    if ([IO.Path]::IsPathRooted($Relative) -or $Relative.Contains(':')) { throw 'Unsafe relative path.' }
    $full = [IO.Path]::GetFullPath((Join-Path $Root $Relative))
    if (!$full.StartsWith($Root.TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Path escapes its approved root.' }
    Assert-NoReparse $full
    return $full
}
function Hash([string]$Path) { if (Test-Path -LiteralPath $Path -PathType Leaf) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }; return $null }
function StreamHash($Stream) { return (Get-FileHash -InputStream $Stream -Algorithm SHA256).Hash }
function Private-Directory([string]$Path) {
    Assert-NoReparse $Path
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true,$false)
    $inherit = [Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit'
    foreach ($identity in @($sid,(New-Object Security.Principal.SecurityIdentifier('S-1-5-18')))) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule($identity,'FullControl',$inherit,'None','Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $Path -AclObject $acl
}
function Save-Json([string]$Path,$Value) {
    Assert-NoReparse $Path
    $temp = $Path + '.new'
    Assert-NoReparse $temp
    [IO.File]::WriteAllText($temp,($Value | ConvertTo-Json -Depth 14),$utf8)
    if (Test-Path -LiteralPath $Path) { [IO.File]::Replace($temp,$Path,[NullString]::Value) } else { [IO.File]::Move($temp,$Path) }
}
function Read-Json([string]$Path) { Assert-NoReparse $Path; return (Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json) }
function Assert-ZipPath([string]$Name) {
    if (!$Name -or $Name.Contains('\') -or $Name.Contains(':') -or $Name.StartsWith('/') -or $Name -match '[\x00-\x1f]') { throw 'Unsafe ZIP path.' }
    $parts = $Name.TrimEnd('/').Split('/')
    foreach ($part in $parts) {
        if (!$part -or $part -eq '.' -or $part -eq '..' -or $part -match '[. ]$' -or $part -match '^(?i:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)') { throw 'Unsafe ZIP component.' }
    }
}
function Inspect-Package([string]$Path) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $seen = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
        $payload = @{}
        $total = [long]0
        $manifest = $null
        foreach ($entry in $zip.Entries) {
            $name = $entry.FullName
            Assert-ZipPath $name
            if (!$seen.Add($name.TrimEnd('/'))) { throw 'Duplicate ZIP path.' }
            if (($entry.ExternalAttributes -band 0x400) -ne 0 -or (($entry.ExternalAttributes -shr 16) -band 0xF000) -eq 0xA000) { throw 'ZIP links/reparse entries refused.' }
            if ($name.EndsWith('/')) { continue }
            if ($entry.Length -gt 134217728) { throw 'Oversized ZIP entry.' }
            $total += $entry.Length
            if ($total -gt 536870912) { throw 'Oversized ZIP archive.' }
            if ($name -eq 'release-manifest.json') {
                if ($entry.Length -gt 8388608) { throw 'Oversized release manifest.' }
                $reader = New-Object IO.StreamReader($entry.Open(),[Text.Encoding]::UTF8)
                try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
            } elseif ($name.StartsWith('modules/',[StringComparison]::OrdinalIgnoreCase)) {
                if ($allowed -cnotcontains $name) { throw "Module file is not in the fixed deployment whitelist: $name" }
                $stream = $entry.Open()
                try { $payload[$name] = [pscustomobject]@{ path=$name; sha256=(StreamHash $stream); size=$entry.Length } } finally { $stream.Dispose() }
            } elseif ($name -notmatch '^(docs/|config/|sql/|examples/)' -and $name -cnotin @('README.md','READ_BEFORE_DEPLOYMENT.txt','evidence/build-result.json')) {
                throw "Archive member outside the documentation whitelist: $name"
            }
        }
        if (!$manifest -or $manifest.schema_version -ne 1 -or [string]::IsNullOrWhiteSpace($manifest.release_id)) { throw 'A version-1 release manifest is required.' }
        if ($payload.Count -ne $allowed.Count) { throw 'Candidate must contain all 21 approved module files.' }
        $registered = @{}
        foreach ($file in $manifest.files) {
            Assert-ZipPath $file.path
            $key = $file.path.ToLowerInvariant()
            if ($registered.ContainsKey($key)) { throw 'Duplicate manifest path.' }
            $registered[$key] = $file
        }
        foreach ($name in $allowed) {
            $file = $registered[$name.ToLowerInvariant()]
            if (!$file -or $file.sha256 -notmatch '^[0-9A-Fa-f]{64}$' -or $file.sha256 -ne $payload[$name].sha256 -or [long]$file.size -ne $payload[$name].size) { throw "Release manifest does not match payload: $name" }
        }
        return [pscustomobject]@{ release_id=$manifest.release_id; files=@($allowed | ForEach-Object { $payload[$_] }) }
    } finally { $zip.Dispose() }
}
function Assert-Plan($Plan) {
    if ($Plan.schema_version -ne 1 -or $Plan.deployment_id -ne $DeploymentId -or @($Plan.files).Count -ne $allowed.Count) { throw 'Invalid deployment plan.' }
    $targetHome = Canonical $Plan.nc_home
    if (!(Test-Path -LiteralPath $targetHome -PathType Container) -or $targetHome -eq [IO.Path]::GetPathRoot($targetHome).TrimEnd('\')) { throw 'Invalid HOME root.' }
    if ($targetHome -eq $stateRoot -or $targetHome.StartsWith($stateRoot + '\',[StringComparison]::OrdinalIgnoreCase) -or $stateRoot.StartsWith($targetHome + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'HOME and deployment storage must be separate.' }
    $names = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
    foreach ($file in $Plan.files) {
        if ($allowed -cnotcontains $file.path -or !$names.Add($file.path) -or $file.new_sha256 -notmatch '^[0-9A-Fa-f]{64}$') { throw 'Invalid module inventory.' }
        if ($file.old_sha256 -and $file.old_sha256 -notmatch '^[0-9A-Fa-f]{64}$') { throw 'Invalid previous hash.' }
        $null = Under $targetHome $file.path
    }
    return $targetHome
}
function Assert-OldState($Plan,[string]$TargetHome) {
    foreach ($file in $Plan.files) {
        $target = Under $TargetHome $file.path
        if (Test-Path -LiteralPath $target -PathType Container) { throw 'Module target is a directory.' }
        if ((Hash $target) -ne $file.old_sha256) { throw "HOME drift detected before deployment: $($file.path)" }
    }
}
function Check-Admission([string]$Path,$Plan,[string]$TargetHome) {
    if (!$Path) { throw 'A reviewed admission JSON is required for HOME mutation.' }
    $a = Read-Json (Canonical $Path)
    if ($a.schema_version -ne 1 -or $a.environment_classification -notin @('dedicated_test','shared_test_maintenance') -or (Canonical $a.home_path) -ne $TargetHome -or $a.package_sha256 -ne $Plan.package_sha256) { throw 'Admission does not identify an approved isolated test HOME/package.' }
    if ($a.nc_server_stopped -isnot [bool] -or !$a.nc_server_stopped) { throw 'A stopped server is required.' }
    $until=[DateTimeOffset]::Parse($a.valid_until_utc)
    if ($until -le [DateTimeOffset]::UtcNow -or $until -gt [DateTimeOffset]::UtcNow.AddHours(48)) { throw 'Admission must be current and expire within 48 hours.' }
    $requiredKinds=@('environment','stop_check','isolation')
    if ($a.environment_classification -eq 'shared_test_maintenance') { $requiredKinds+=@('users_drained','tasks_paused','maintenance_owner') }
    $kinds=New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
    foreach ($checkName in @('environment_isolation','database_identity','outbound_and_jobs_isolation','backup_and_rollback','jdk_baseline','v1_compatibility')) {
        $check=$a.checks.$checkName
        if ($check.status -ne 'passed' -or [string]::IsNullOrWhiteSpace($check.description) -or @($check.evidence_refs).Count -eq 0) { throw "Admission check is incomplete: $checkName" }
        foreach ($reference in $check.evidence_refs) {
            $evidencePath=Canonical $reference.path
            if (!$evidencePath.StartsWith($projectRoot+'\',[StringComparison]::OrdinalIgnoreCase) -or $evidencePath.StartsWith($TargetHome+'\',[StringComparison]::OrdinalIgnoreCase) -or $evidencePath -eq (Canonical $Path)) { throw 'Evidence must be a separate project file outside HOME.' }
            if (!(Test-Path -LiteralPath $evidencePath -PathType Leaf) -or (Get-Item -LiteralPath $evidencePath).Length -eq 0 -or $reference.sha256 -notmatch '^[0-9A-Fa-f]{64}$' -or (Hash $evidencePath) -ne $reference.sha256) { throw 'Admission evidence is missing, empty or changed.' }
            if ($reference.PSObject.Properties.Name -contains 'kind') { $null=$kinds.Add($reference.kind) }
        }
    }
    foreach($kind in $requiredKinds){if(!$kinds.Contains($kind)){throw "Admission lacks hashed evidence kind: $kind"}}
    if ([string]::IsNullOrWhiteSpace($a.confirmed_by)) { throw 'Admission must identify the confirming operator.' }
    $when = [DateTimeOffset]::Parse($a.confirmed_at_utc)
    if ($when -gt [DateTimeOffset]::UtcNow.AddMinutes(5) -or $when -lt [DateTimeOffset]::UtcNow.AddHours(-24)) { throw 'Admission confirmation must be within the last 24 hours.' }
    if ($a.environment_classification -eq 'shared_test_maintenance') {
        if ($a.no_active_users -isnot [bool] -or !$a.no_active_users -or $a.background_tasks_paused -isnot [bool] -or !$a.background_tasks_paused) { throw 'Shared test HOME requires drained users and paused background tasks.' }
        $start = [DateTimeOffset]::Parse($a.maintenance_window.start_utc)
        $end = [DateTimeOffset]::Parse($a.maintenance_window.end_utc)
        if ($end -le $start -or $start -gt [DateTimeOffset]::UtcNow -or $end -lt [DateTimeOffset]::UtcNow -or ($end-$start).TotalHours -gt 24) { throw 'Shared test deployment must occur within an explicit maintenance window of at most 24 hours.' }
    }
    # This supplementary local check does not replace the operator's service/system-JDK stop evidence.
    # Process enumeration avoids WMI/CIM permissions and never prints command lines.
    foreach ($process in Get-Process -Name java,javaw -ErrorAction SilentlyContinue) {
        $executable = $process.Path
        if (!$executable) { throw 'Cannot inspect an active Java process; obtain a verifiable stopped-server baseline.' }
        if ($executable.StartsWith($TargetHome + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'A Java process executing from this HOME is still running.' }
    }
}
function Backup-Path([string]$Relative) { return Under $session ('backup\files\' + $Relative.Replace('/','\')) }
function Check-Backup($Plan) {
    $record = Read-Json (Under $session 'backup.json')
    if ($record.schema_version -ne 1 -or $record.plan_sha256 -ne (Hash (Under $session 'plan.json'))) { throw 'Backup does not match this plan.' }
    foreach ($file in $Plan.files | Where-Object { $_.old_sha256 }) {
        if ((Hash (Backup-Path $file.path)) -ne $file.old_sha256) { throw 'Required original-file backup is missing or altered.' }
    }
    foreach ($file in $record.configurations) {
        Assert-ZipPath $file.path
        if ($file.path -notmatch '^ierp/bin/' -or $file.sha256 -notmatch '^[0-9A-Fa-f]{64}$' -or (Hash (Backup-Path $file.path)) -ne $file.sha256) { throw 'Configuration backup is missing or altered.' }
    }
    return $record
}
function Atomic-Publish([string]$Target,[byte[]]$Bytes,[string]$TargetHome) {
    $null = Under $TargetHome $Target.Substring($TargetHome.Length+1)
    $parent = Split-Path -Parent $Target
    Assert-NoReparse $parent
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    $temp = $Target + '.qd-stage-' + $DeploymentId
    $null = Under $TargetHome $temp.Substring($TargetHome.Length+1)
    if (Test-Path -LiteralPath $temp) { throw 'A previous deployment staging file remains; investigate before continuing.' }
    try {
        [IO.File]::WriteAllBytes($temp,$Bytes)
        $null = Under $TargetHome $Target.Substring($TargetHome.Length+1)
        if (Test-Path -LiteralPath $Target) { [IO.File]::Replace($temp,$Target,[NullString]::Value) } else { [IO.File]::Move($temp,$Target) }
    } finally {
        if (Test-Path -LiteralPath $temp) { $null = Under $TargetHome $temp.Substring($TargetHome.Length+1); Remove-Item -LiteralPath $temp -Force }
    }
}

Assert-NoReparse $stateRoot
if ($Action -eq 'Plan') {
    $targetHome = Canonical $NcHome
    if (!(Test-Path -LiteralPath $targetHome -PathType Container)) { throw 'HOME directory does not exist.' }
    $packagePath = Canonical $Package
    $packageHash = Hash $packagePath
    $candidate = Inspect-Package $packagePath
    $DeploymentId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
    $session = Under $stateRoot $DeploymentId
    $files = @($candidate.files | ForEach-Object {
        $target = Under $targetHome $_.path
        if (Test-Path -LiteralPath $target -PathType Container) { throw 'Module target is a directory.' }
        $old = Hash $target
        [pscustomobject]@{ path=$_.path; new_sha256=$_.sha256; size=$_.size; old_sha256=$old; change=$(if($old){'update'}else{'add'}); backup_relative_path=('backup/files/'+$_.path); restore_action=$(if($old){'restore-original'}else{'remove-added-file'}) }
    })
    $plan = [pscustomobject]@{ schema_version=1; deployment_id=$DeploymentId; created_at_utc=[DateTime]::UtcNow.ToString('o'); release_id=$candidate.release_id; nc_home=$targetHome; package=$packagePath; package_sha256=$packageHash; files=$files }
    $null = Assert-Plan $plan
    if ((Hash $packagePath) -ne $packageHash) { throw 'Candidate changed during planning.' }
    Private-Directory $session
    Save-Json (Under $session 'plan.json') $plan
    [pscustomobject]@{action='Plan';deployment_id=$DeploymentId;state_directory=$session;module_files=$files.Count;home_changed=$false} | ConvertTo-Json
    exit
}
if ($DeploymentId -notmatch '^\d{8}-\d{6}-[a-f0-9]{8}$') { throw 'A valid deployment id is required.' }
$session = Under $stateRoot $DeploymentId
$plan = Read-Json (Under $session 'plan.json')
$targetHome = Assert-Plan $plan
$journalPath = Under $session 'journal.json'

if ($Action -eq 'Backup') {
    if ((Test-Path -LiteralPath (Under $session 'backup.json')) -or (Test-Path -LiteralPath $journalPath)) { throw 'Backup already exists or deployment has started; use a new plan.' }
    Assert-OldState $plan $targetHome
    Private-Directory (Under $session 'backup')
    foreach ($file in $plan.files | Where-Object { $_.old_sha256 }) {
        $source = Under $targetHome $file.path
        $target = Backup-Path $file.path
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        [IO.File]::Copy($source,$target,$false)
        if ((Hash $target) -ne $file.old_sha256 -or (Hash $source) -ne $file.old_sha256) { throw 'Original module changed while backing up.' }
    }
    $configFiles = @()
    $bin = Under $targetHome 'ierp\bin'
    if (Test-Path -LiteralPath $bin) {
        $configFiles += @(Get-ChildItem -LiteralPath $bin -File -Force | Where-Object { $_.Extension -in @('.xml','.properties','.prop','.conf','.ini','.bat','.sh') })
        foreach ($folder in @('prop','config')) {
            $path = Under $targetHome ('ierp\bin\'+$folder)
            if (Test-Path -LiteralPath $path -PathType Container) {
                # Walk explicitly: do not follow a junction before checking its attributes.
                $queue = New-Object 'System.Collections.Generic.Queue[string]'; $queue.Enqueue($path)
                while ($queue.Count -gt 0) {
                    $current = $queue.Dequeue(); Assert-NoReparse $current
                    foreach ($entry in Get-ChildItem -LiteralPath $current -Force) {
                        Assert-NoReparse $entry.FullName
                        if ($entry.PSIsContainer) { $queue.Enqueue($entry.FullName) } else { $configFiles += $entry }
                    }
                }
            }
        }
    }
    $configs = @()
    foreach ($file in $configFiles | Sort-Object FullName -Unique) {
        $relative = $file.FullName.Substring($targetHome.Length+1).Replace('\','/')
        $source = Under $targetHome $relative
        $expected = Hash $source
        $target = Backup-Path $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        [IO.File]::Copy($source,$target,$false)
        if ((Hash $target) -ne $expected -or (Hash $source) -ne $expected) { throw 'Configuration changed while backing up.' }
        $configs += [pscustomobject]@{path=$relative;sha256=$expected}
    }
    Assert-OldState $plan $targetHome
    Save-Json (Under $session 'backup.json') ([pscustomobject]@{schema_version=1;created_at_utc=[DateTime]::UtcNow.ToString('o');plan_sha256=(Hash (Under $session 'plan.json'));configurations=$configs})
    $null = Check-Backup $plan
    [pscustomobject]@{action='Backup';deployment_id=$DeploymentId;verified=$true;configuration_files=$configs.Count;home_changed=$false} | ConvertTo-Json
    exit
}
if ($Action -eq 'Verify') {
    $problems = @($plan.files | Where-Object { (Hash (Under $targetHome $_.path)) -ne $_.new_sha256 } | ForEach-Object { $_.path })
    if ($problems.Count) { throw ('Deployment verification failed for '+$problems.Count+' module files.') }
    [pscustomobject]@{action='Verify';deployment_id=$DeploymentId;verified=$true;module_files=$plan.files.Count;home_changed=$false} | ConvertTo-Json
    exit
}
Check-Admission (Canonical $Admission) $plan $targetHome
$backup = Check-Backup $plan
if ($Action -eq 'Deploy') {
    if (Test-Path -LiteralPath $journalPath) { throw 'Deployment already started; use Verify or Rollback.' }
    Assert-OldState $plan $targetHome
    if ((Hash $plan.package) -ne $plan.package_sha256) { throw 'Candidate ZIP changed after planning.' }
    $candidate = Inspect-Package $plan.package
    foreach ($file in $plan.files) { if (($candidate.files | Where-Object path -CEQ $file.path).sha256 -ne $file.new_sha256) { throw 'Candidate and plan differ.' } }
    foreach ($configuration in $backup.configurations) {
        if ((Hash (Under $targetHome $configuration.path)) -ne $configuration.sha256) { throw 'HOME configuration changed after backup; create a new plan.' }
    }
    $journal = [pscustomobject]@{schema_version=1;deployment_id=$DeploymentId;plan_sha256=(Hash (Under $session 'plan.json'));status='deploying';files=@($plan.files | ForEach-Object { [pscustomobject]@{path=$_.path;status='pending'} })}
    Save-Json $journalPath $journal
    $zip = [IO.Compression.ZipFile]::OpenRead($plan.package)
    try {
        foreach ($file in $plan.files) {
            $target = Under $targetHome $file.path
            if ((Hash $target) -ne $file.old_sha256) { throw 'HOME drift detected during deployment.' }
            $entry = $zip.GetEntry($file.path)
            $stream = $entry.Open(); $memory = New-Object IO.MemoryStream
            try {
                $stream.CopyTo($memory); $memory.Position=0
                if ((StreamHash $memory) -ne $file.new_sha256) { throw 'Candidate entry changed after preflight.' }
                $bytes=$memory.ToArray()
            } finally { $stream.Dispose();$memory.Dispose() }
            $row = $journal.files | Where-Object path -CEQ $file.path
            $row.status='applying'; Save-Json $journalPath $journal
            Atomic-Publish $target $bytes $targetHome
            if ((Hash $target) -ne $file.new_sha256) { throw 'Published module hash mismatch.' }
            $row.status='applied'; Save-Json $journalPath $journal
        }
        $journal.status='deployed'; Save-Json $journalPath $journal
    } catch {
        $journal.status='failed'; Save-Json $journalPath $journal
        throw ('Deployment stopped. Preserve journal and use Rollback after review. '+$_.Exception.Message)
    } finally { $zip.Dispose() }
    [pscustomobject]@{action='Deploy';deployment_id=$DeploymentId;verified=$true;module_files=$plan.files.Count} | ConvertTo-Json
    exit
}
if ($Action -eq 'Rollback') {
    $journal = Read-Json $journalPath
    if ($journal.schema_version -ne 1 -or $journal.deployment_id -ne $DeploymentId -or $journal.plan_sha256 -ne (Hash (Under $session 'plan.json'))) { throw 'Journal does not match this deployment.' }
    if ($journal.status -eq 'rolled-back') { throw 'This deployment is already rolled back.' }
    if (@($journal.files).Count -ne $plan.files.Count) { throw 'Invalid journal inventory.' }
    $seen = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
    foreach ($row in $journal.files) {
        if ($allowed -cnotcontains $row.path -or !$seen.Add($row.path) -or $row.status -notin @('pending','applying','applied','restoring','restored')) { throw 'Invalid journal entry.' }
        $file = $plan.files | Where-Object path -CEQ $row.path
        $currentTarget = Under $targetHome $row.path
        if (Test-Path -LiteralPath $currentTarget -PathType Container) { throw 'Rollback refused: a module target became a directory.' }
        $actual = Hash $currentTarget
        if ($row.status -in @('pending','restored')) {
            if ($actual -ne $file.old_sha256) { throw "Rollback refused: later modification to $($row.path)" }
        } elseif ($actual -ne $file.old_sha256 -and $actual -ne $file.new_sha256) { throw "Rollback refused: later modification to $($row.path)" }
    }
    $journal.status='rolling-back'; Save-Json $journalPath $journal
    $rows = @($journal.files); [array]::Reverse($rows)
    foreach ($row in $rows) {
        if ($row.status -in @('pending','restored')) { continue }
        $file = $plan.files | Where-Object path -CEQ $row.path
        $target = Under $targetHome $row.path
        if (Test-Path -LiteralPath $target -PathType Container) { throw 'Rollback refused: a module target became a directory.' }
        $actual = Hash $target
        if ($actual -ne $file.old_sha256 -and $actual -ne $file.new_sha256) { throw 'HOME drift detected during rollback.' }
        $row.status='restoring'; Save-Json $journalPath $journal
        if ($actual -ne $file.old_sha256) {
            if ($file.old_sha256) { Atomic-Publish $target ([IO.File]::ReadAllBytes((Backup-Path $file.path))) $targetHome }
            else { $null = Under $targetHome $file.path; Remove-Item -LiteralPath $target -Force }
        }
        if ((Hash $target) -ne $file.old_sha256) { throw 'Original-file restoration hash mismatch.' }
        $row.status='restored'; Save-Json $journalPath $journal
    }
    $journal.status='rolled-back'; Save-Json $journalPath $journal
    [pscustomobject]@{action='Rollback';deployment_id=$DeploymentId;verified=$true;preserved_database_and_logs=$true} | ConvertTo-Json
}
