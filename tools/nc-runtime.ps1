<#
NC65 runtime inspection and gated launch. Inspect does not execute Java, query a
database or contact the application. Start changes JAVA_HOME only in its child
process and never edits HOME. Output and launch metadata stay in this project.
No admission file is generated automatically: its evidence must be supplied by
the environment owner after the approved isolation and recovery work.
#>
[CmdletBinding()]
param(
    [ValidateSet('Inspect','Start')][string]$Mode = 'Inspect',
    [string]$NcHome = 'E:\01-Home\NC65home\QuDuTest\nchome',
    [string]$JavaHome,
    [string]$AdmissionPath,
    [string]$OutputPath,
    [switch]$DryRun,
    [switch]$LoadFunctionsOnly
)
$ErrorActionPreference = 'Stop'
$script:RuntimeProjectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot)).TrimEnd('\','/')

function Get-NormalizedRuntimePath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path) -or ![IO.Path]::IsPathRooted($Path) -or
        $Path -match '^[A-Za-z]:[^\\/]' -or $Path -match '[\x00-\x1f&|<>^%!]') {
        throw 'An absolute local path without shell expansion characters is required.'
    }
    if ($Path.StartsWith('\\') -or $Path.StartsWith('//')) { throw 'UNC/device paths are not allowed.' }
    $full = [IO.Path]::GetFullPath($Path).TrimEnd('\','/')
    if ($full -eq [IO.Path]::GetPathRoot($full).TrimEnd('\','/')) { throw 'A drive root is not an NC HOME.' }
    # Junctions/symlinks could redirect an apparently safe path into HOME or another workspace.
    $cursor = $full
    while ($cursor) {
        if ((Test-Path -LiteralPath $cursor) -and ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'Reparse points are not allowed in runtime or evidence paths.'
        }
        $parent = Split-Path -Parent $cursor
        if ($parent -eq $cursor) { break }; $cursor = $parent
    }
    return $full
}

function Test-RuntimePathWithin([string]$Path,[string]$Root) {
    return $Path.Equals($Root,[StringComparison]::OrdinalIgnoreCase) -or
        $Path.StartsWith($Root + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)
}

function Assert-RuntimeWorkspacePath([string]$Path,[string]$TargetHome) {
    $full = Get-NormalizedRuntimePath $Path
    if (!(Test-RuntimePathWithin $full $script:RuntimeProjectRoot) -or (Test-RuntimePathWithin $full $TargetHome)) {
        throw 'Evidence, reports and launch metadata must be inside the project and outside HOME.'
    }
    return $full
}

function Get-RuntimeTextHash([string]$Value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
}

function Get-RuntimeFileHash([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Read-RuntimeXml([string]$Path) {
    $settings = New-Object Xml.XmlReaderSettings
    $settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $reader = [Xml.XmlReader]::Create($Path,$settings)
    try { $xml = New-Object Xml.XmlDocument; $xml.XmlResolver = $null; $xml.Load($reader); return ,$xml }
    finally { $reader.Dispose() }
}

function Get-RuntimeJdk([string]$Path) {
    $result = [ordered]@{ home=$Path; java_present=$false; compiler_present=$false; version='unknown'; vendor='unknown'; ibm_java7_confirmed=$false; java_sha256=$null; release_sha256=$null; jvm_sha256=$null }
    $java = Join-Path $Path 'bin\java.exe'; $release = Join-Path $Path 'release'
    $result.java_present = Test-Path -LiteralPath $java -PathType Leaf
    $result.compiler_present = Test-Path -LiteralPath (Join-Path $Path 'bin\javac.exe') -PathType Leaf
    if ($result.java_present) { $result.java_sha256 = Get-RuntimeFileHash $java }
    $releaseText = ''; if (Test-Path -LiteralPath $release -PathType Leaf) { $releaseText = [IO.File]::ReadAllText($release); $result.release_sha256=Get-RuntimeFileHash $release }
    if ($releaseText -match '(?m)^JAVA_VERSION="([^"]+)"') { $result.version=$matches[1] }
    if ($releaseText -match '(?m)^(?:IMPLEMENTOR|JAVA_VENDOR)="([^"]+)"') {
        if ($matches[1] -match '(?i)IBM') { $result.vendor='IBM' }
        elseif ($matches[1] -match '(?i)Oracle') { $result.vendor='Oracle' }
        else { $result.vendor='other' }
    }
    $j9 = @(Get-ChildItem -LiteralPath (Join-Path $Path 'jre\bin') -Filter 'j9vm*' -Directory -ErrorAction SilentlyContinue)
    if ($j9.Count -gt 0 -and (Test-Path -LiteralPath (Join-Path $Path 'jre\bin\j9vm\jvm.dll') -PathType Leaf)) { $result.vendor='IBM'; $result.jvm_sha256=Get-RuntimeFileHash (Join-Path $Path 'jre\bin\j9vm\jvm.dll') }
    $result.ibm_java7_confirmed = $result.java_present -and $result.version -match '^1\.7(?:\.|$)' -and $result.vendor -eq 'IBM'
    return [pscustomobject]$result
}

function Get-RuntimeOccupancy([string]$TargetHome,[int[]]$Ports) {
    $result = [ordered]@{ process_state='unknown'; port_state='unknown'; target_processes=@(); occupied_ports=@(); reason_codes=@() }
    try {
        # Never return command lines: they can contain database or integration secrets.
        $processes = @(Get-CimInstance Win32_Process -ErrorAction Stop)
        if ($processes.Count -eq 0 -or !($processes | Where-Object { $_.ProcessId -eq 4 })) { throw 'Incomplete host process inventory.' }
        $unknownJava = $false
        foreach ($process in $processes) {
            if ($process.Name -notmatch '^(java|javaw|ncstarter)(\.exe)?$') { continue }
            if ([string]::IsNullOrWhiteSpace($process.CommandLine)) { $unknownJava=$true; continue }
            if ($process.CommandLine.IndexOf($TargetHome,[StringComparison]::OrdinalIgnoreCase) -ge 0) {
                $result.target_processes += [pscustomobject]@{ pid=[int]$process.ProcessId; name=[string]$process.Name }
            }
        }
        if ($unknownJava) { $result.reason_codes += 'JAVA_PROCESS_COMMANDLINE_UNAVAILABLE' }
        else { $result.process_state='known' }
    } catch { $result.reason_codes += 'HOST_PROCESS_INVENTORY_UNAVAILABLE' }
    try {
        $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction Stop)
        if ($result.process_state -ne 'known') { throw 'Host inventory is not trustworthy.' }
        foreach ($listener in $listeners) {
            if ($Ports -contains [int]$listener.LocalPort) {
                $result.occupied_ports += [pscustomobject]@{ port=[int]$listener.LocalPort; pid=[int]$listener.OwningProcess }
            }
        }
        $result.port_state='known'
    } catch { $result.reason_codes += 'HOST_LISTENER_INVENTORY_UNAVAILABLE' }
    return [pscustomobject]$result
}

function Get-RuntimeLocalAddresses {
    try {
        $addresses = @(Get-NetIPAddress -ErrorAction Stop | ForEach-Object { [string]$_.IPAddress })
        if ($addresses.Count -eq 0) { throw 'No host address inventory.' }
        return [pscustomobject]@{ state='known'; addresses=$addresses }
    } catch { return [pscustomobject]@{ state='unknown'; addresses=@() } }
}

function Get-RuntimeLegacyInventory([string]$TargetHome) {
    $files = New-Object 'System.Collections.Generic.List[string]'
    $errors = New-Object 'System.Collections.Generic.List[string]'
    $v1Entries = New-Object 'System.Collections.Generic.List[string]'
    foreach ($relative in @('ierp\bin\qd-esign-v1.properties','ierp\bin\qd-esign-probe.properties')) {
        if (Test-Path -LiteralPath (Join-Path $TargetHome $relative) -PathType Leaf) { $files.Add($relative) }
    }
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    foreach ($module in @('baseapp','uapbd','pu','so','ic','purp')) {
        $moduleRoot=Join-Path $TargetHome ('modules\'+$module)
        if (!(Test-Path -LiteralPath $moduleRoot -PathType Container)) { continue }
        foreach ($entry in Get-ChildItem -LiteralPath $moduleRoot -File -Recurse -ErrorAction Stop) {
            if ($entry.Extension -eq '.class' -and $entry.FullName -match '(?i)[\\/]qd[\\/]esign[\\/]v1[\\/]') {
                $relative=$entry.FullName.Substring($TargetHome.Length+1); $v1Entries.Add($relative); $files.Add($relative)
            }
            if ($entry.Extension -ne '.jar') { continue }
            $relative=$entry.FullName.Substring($TargetHome.Length+1)
            # All six module jars form the deployment identity, not only jars named esign.
            $files.Add($relative)
            $archive=$null
            try {
                $archive=[IO.Compression.ZipFile]::OpenRead($entry.FullName)
                foreach ($jarEntry in $archive.Entries) {
                    if ($jarEntry.FullName -match '(?i)(^|/)qd/esign/v1/') { $v1Entries.Add($relative+'!/'+$jarEntry.FullName) }
                }
            } catch { $errors.Add($relative) }
            finally { if ($archive) { $archive.Dispose() } }
        }
    }
    return [pscustomobject]@{
        v1_config_present=(Test-Path -LiteralPath (Join-Path $TargetHome 'ierp\bin\qd-esign-v1.properties') -PathType Leaf)
        probe_config_present=(Test-Path -LiteralPath (Join-Path $TargetHome 'ierp\bin\qd-esign-probe.properties') -PathType Leaf)
        v1_class_count=$v1Entries.Count; v1_class_samples=@($v1Entries | Select-Object -First 12)
        archive_scan_state=$(if($errors.Count){'partial'}else{'complete'}); unreadable_archives=@($errors)
        fingerprint_files=@($files | Sort-Object -Unique)
    }
}

function Get-NcRuntimeInspection([string]$TargetHome,[string]$Jdk) {
    $homePath=Get-NormalizedRuntimePath $TargetHome
    if (!(Test-Path -LiteralPath $homePath -PathType Container)) { throw 'NC HOME does not exist.' }
    $required=@('starter.jar','startServer.bat','bin\uapSetupCmdLine.bat','ierp\bin\prop.xml','middleware\mw.jar','middleware\tomcat.jar','modules')
    foreach ($relative in $required) {
        if (!(Test-Path -LiteralPath (Join-Path $homePath $relative))) { throw ('HOME identity check failed: missing '+$relative) }
    }
    $jdkPath=Get-NormalizedRuntimePath $(if($Jdk){$Jdk}else{Join-Path $homePath 'ufjdk'})
    $jdkInfo=Get-RuntimeJdk $jdkPath
    $prop=Read-RuntimeXml (Join-Path $homePath 'ierp\bin\prop.xml')
    $datasources=@()
    foreach ($node in $prop.SelectNodes('//*[local-name()="dataSource"]')) {
        $schema=[string]$node.user
        if ($schema -notmatch '^[A-Za-z][A-Za-z0-9_$#]{0,127}$') { $schema='unknown' }
        $datasources += [pscustomobject]@{ name=[string]$node.dataSourceName; type=[string]$node.databaseType; schema=$schema; target_sha256=(Get-RuntimeTextHash ([string]$node.databaseUrl)) }
    }
    $portValues=New-Object 'System.Collections.Generic.List[int]'
    foreach ($node in $prop.SelectNodes('//*[local-name()="servicePort" or local-name()="port"]')) {
        $portValue=0
        if (![int]::TryParse($node.InnerText,[ref]$portValue) -or $portValue -lt 1 -or $portValue -gt 65535) { throw 'HOME contains an invalid runtime port.' }
        $portValues.Add($portValue)
    }
    if ($portValues.Count -eq 0) { throw 'No configured runtime ports were found.' }
    $debugBindings=@()
    foreach ($node in $prop.SelectNodes('//*[local-name()="jvmArgs"]')) {
        $jvm=[string]$node.InnerText
        if ($jvm -match '(?:-agentlib:jdwp=|-Xrunjdwp:)([^\s]+)') {
            $jdwp=$matches[1]; $address='unknown'; $port=0; $server=$false
            if ($jdwp -match '(?:^|,)server=y(?:,|$)') { $server=$true }
            if ($jdwp -match '(?:^|,)address=([^,\s]+)') {
                $addressValue=$matches[1]
                if ($addressValue -match '^(.*):(\d+)$') { $address=$matches[1].Trim('[',']'); $port=[int]$matches[2] }
                elseif ($addressValue -match '^\d+$') { $address='*'; $port=[int]$addressValue }
            }
            if ($server -and ($port -lt 1 -or $port -gt 65535)) { throw 'HOME contains an unrecognized JDWP server address.' }
            if ($server) { $portValues.Add($port) }
            $debugBindings += [pscustomobject]@{ server=$server; address=$address; port=$port }
        }
    }
    $ports=@($portValues | Sort-Object -Unique)
    $legacy=Get-RuntimeLegacyInventory $homePath
    $fingerprintFiles=@($required | Where-Object { Test-Path -LiteralPath (Join-Path $homePath $_) -PathType Leaf })+
        @('startup.bat','stopServer.bat','ierp\bin\qd-esign-v1.properties','ierp\bin\qd-esign-probe.properties')+$legacy.fingerprint_files
    $fingerprintRecords=@()
    foreach ($relative in @($fingerprintFiles | Sort-Object -Unique)) {
        $filePath=Join-Path $homePath $relative
        if (Test-Path -LiteralPath $filePath -PathType Leaf) { $fingerprintRecords += [pscustomobject]@{ path=$relative.Replace('\','/'); sha256=(Get-RuntimeFileHash $filePath) } }
    }
    $fingerprint=Get-RuntimeTextHash ($homePath.ToLowerInvariant()+"`n"+$jdkPath.ToLowerInvariant()+"`n"+($jdkInfo | ConvertTo-Json -Compress)+"`n"+($fingerprintRecords | ConvertTo-Json -Compress -Depth 5))
    $startupHardcoded=$false
    if (Test-Path -LiteralPath (Join-Path $homePath 'startup.bat')) {
        $startupText=[IO.File]::ReadAllText((Join-Path $homePath 'startup.bat'))
        $startupHardcoded=$startupText -match '(?im)^\s*set\s+JAVA_HOME\s*=\s*[A-Za-z]:\\'
    }
    $history=[ordered]@{ state='not_found'; latest_log_utc=$null; scheduled_task_activity=$false; external_integration_activity=$false; existing_error_markers=@() }
    $log=Join-Path $homePath 'nclogs\server\nc-log.log'
    if (Test-Path -LiteralPath $log -PathType Leaf) {
        $history.state='historical_log_only'; $history.latest_log_utc=(Get-Item -LiteralPath $log).LastWriteTimeUtc.ToString('o')
        # Only return flags/codes, never business data, SQL, command lines or raw log text.
        $history.scheduled_task_activity=[bool](Select-String -LiteralPath $log -Pattern 'scheduler|scheduleengine|PreAlertEngine' -Quiet)
        $history.external_integration_activity=[bool](Select-String -LiteralPath $log -Pattern 'WMS|http[s]?://|smtp|webservice' -Quiet)
        foreach ($code in @('ORA-02049','ConnectException','BindException')) {
            if (Select-String -LiteralPath $log -Pattern ([regex]::Escape($code)) -Quiet) { $history.existing_error_markers += $code }
        }
    }
    $occupancy=Get-RuntimeOccupancy $homePath $ports
    $local=Get-RuntimeLocalAddresses
    $bindingState='known'; $invalidDebug=@()
    foreach ($binding in $debugBindings | Where-Object { $_.server }) {
        if ($binding.address -in @('*','0.0.0.0','::')) { continue }
        if ($local.state -ne 'known') { $bindingState='unknown'; continue }
        if ($local.addresses -notcontains $binding.address) { $invalidDebug += $binding.address }
    }
    $legacyPublic=[ordered]@{}
    foreach ($property in $legacy.PSObject.Properties) { if ($property.Name -ne 'fingerprint_files') { $legacyPublic[$property.Name]=$property.Value } }
    return [pscustomobject][ordered]@{
        schema_version=1; inspected_at_utc=[DateTime]::UtcNow.ToString('o'); mode='Inspect'; home_path=$homePath
        home_fingerprint=$fingerprint; fingerprint_files=$fingerprintRecords; jdk=$jdkInfo
        datasource_state=$(if($datasources.Count){'configured_not_connected'}else{'not_found'}); datasources=$datasources
        ports=$ports; debug_bindings=$debugBindings; debug_local_binding_state=$bindingState; nonlocal_debug_addresses=$invalidDebug
        startup=[pscustomobject]@{ selected_entry='startServer.bat'; legacy_startup_has_hardcoded_java_home=$startupHardcoded; child_java_home_explicit=$jdkPath; service_health='not_checked' }
        license=[pscustomobject]@{ root_file_present=(Test-Path -LiteralPath (Join-Path $homePath 'license.resp') -PathType Leaf); bin_file_present=(Test-Path -LiteralPath (Join-Path $homePath 'bin\license.resp') -PathType Leaf); validity='unknown' }
        legacy=[pscustomobject]$legacyPublic; historical_activity=[pscustomobject]$history; occupancy=$occupancy
        admission_required=@('environment_isolation','database_identity','outbound_and_jobs_isolation','backup_and_rollback','jdk_baseline','v1_compatibility_when_present')
    }
}

function Assert-RuntimeAdmission($Inspection,[string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { throw 'Start requires -AdmissionPath with owner-supplied evidence.' }
    $admissionFile=Assert-RuntimeWorkspacePath $Path $Inspection.home_path
    if (!(Test-Path -LiteralPath $admissionFile -PathType Leaf)) { throw 'Admission evidence JSON is missing.' }
    $admission=Get-Content -LiteralPath $admissionFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($admission.schema_version -ne 1) { throw 'Unsupported admission schema_version.' }
    if ((Get-NormalizedRuntimePath ([string]$admission.home_path)) -ine $Inspection.home_path -or
        [string]$admission.home_fingerprint -cne $Inspection.home_fingerprint -or
        (Get-NormalizedRuntimePath ([string]$admission.java_home)) -ine $Inspection.jdk.home) { throw 'Admission HOME/JDK fingerprint does not match the current inspection.' }
    if ($admission.environment_classification -notin @('dedicated_test','shared_test_maintenance')) { throw 'Start requires a dedicated test environment or an approved shared-test maintenance window.' }
    $validUntil=[DateTimeOffset]::MinValue
    $validDate=$false
    # PowerShell 7 may deserialize ISO dates into DateTime; PowerShell 5 keeps strings.
    if ($admission.valid_until_utc -is [DateTime]) { $validUntil=[DateTimeOffset]$admission.valid_until_utc; $validDate=$true }
    else { $validDate=[DateTimeOffset]::TryParse([string]$admission.valid_until_utc,[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::AssumeUniversal,[ref]$validUntil) }
    if (!$validDate -or $validUntil -le [DateTimeOffset]::UtcNow) { throw 'Admission validity window is missing or expired.' }
    if ($validUntil -gt [DateTimeOffset]::UtcNow.AddHours(48)) { throw 'Admission validity must end within 48 hours.' }
    $gates=@('environment_isolation','database_identity','outbound_and_jobs_isolation','backup_and_rollback','jdk_baseline')
    if ($Inspection.legacy.v1_config_present -or $Inspection.legacy.v1_class_count -gt 0) { $gates += 'v1_compatibility' }
    foreach ($gate in $gates) {
        $check=$admission.checks.$gate
        if (!$check -or $check.status -cne 'passed' -or [string]::IsNullOrWhiteSpace([string]$check.description) -or @($check.evidence_refs).Count -eq 0) { throw ('Admission gate lacks concrete evidence: '+$gate) }
        $requiredKinds=@()
        if ($gate -eq 'environment_isolation') {
            $requiredKinds=@('environment','stop_check')
            if ($admission.environment_classification -eq 'shared_test_maintenance') { $requiredKinds += @('users_drained','maintenance_owner') }
        }
        if ($gate -eq 'outbound_and_jobs_isolation') {
            $requiredKinds=@('isolation')
            if ($admission.environment_classification -eq 'shared_test_maintenance') { $requiredKinds += 'tasks_paused' }
        }
        $kinds=@($check.evidence_refs | ForEach-Object { [string]$_.kind })
        foreach ($kind in $requiredKinds) { if ($kinds -notcontains $kind) { throw ('Admission evidence kind is missing: '+$gate+'/'+$kind) } }
        foreach ($evidence in $check.evidence_refs) {
            $evidencePath=Assert-RuntimeWorkspacePath ([string]$evidence.path) $Inspection.home_path
            if ($evidencePath -ieq $admissionFile -or !(Test-Path -LiteralPath $evidencePath -PathType Leaf) -or (Get-Item -LiteralPath $evidencePath).Length -eq 0) { throw ('Admission evidence file is invalid: '+$gate) }
            if ([string]$evidence.sha256 -notmatch '^[A-Fa-f0-9]{64}$' -or (Get-RuntimeFileHash $evidencePath) -ine [string]$evidence.sha256) { throw ('Admission evidence checksum mismatch: '+$gate) }
        }
    }
    return [pscustomobject]@{ path=$admissionFile; sha256=(Get-RuntimeFileHash $admissionFile); environment_classification=$admission.environment_classification }
}

function Invoke-NcRuntime([string]$RunMode,[string]$TargetHome,[string]$Jdk,[string]$Admission,[bool]$NoLaunch) {
    $inspection=Get-NcRuntimeInspection $TargetHome $Jdk
    if ($RunMode -eq 'Inspect') { return $inspection }
    if ([string]::IsNullOrWhiteSpace($Jdk) -or !$inspection.jdk.ibm_java7_confirmed) { throw 'Start requires an explicit -JavaHome pointing to a verified IBM Java 7 installation.' }
    $admissionRecord=Assert-RuntimeAdmission $inspection $Admission
    if ($inspection.legacy.archive_scan_state -ne 'complete') { throw 'Module archive inspection is incomplete; resolve unreadable archives first.' }
    if ($inspection.occupancy.process_state -ne 'known' -or $inspection.occupancy.port_state -ne 'known') { throw 'Host process/listener visibility is unknown; run preflight with host inventory access before starting.' }
    if (@($inspection.occupancy.target_processes).Count -or @($inspection.occupancy.occupied_ports).Count) { throw 'Target HOME has a running Java process or a configured port is occupied. Stop through the approved maintenance procedure first.' }
    if ($inspection.debug_local_binding_state -ne 'known') { throw 'The configured JDWP binding cannot be checked against local addresses.' }
    if (@($inspection.nonlocal_debug_addresses).Count) { throw 'prop.xml JDWP is bound to a nonlocal address. Obtain approval to correct that configuration and regenerate admission; no automatic edit was made.' }
    $result=[ordered]@{ schema_version=1; mode='Start'; dry_run=$NoLaunch; home_path=$inspection.home_path; home_fingerprint=$inspection.home_fingerprint; java_home=$inspection.jdk.home; admission_sha256=$admissionRecord.sha256; status='validated_not_started'; service_health='not_checked' }
    if ($NoLaunch) { return [pscustomobject]$result }
    # Recheck immediately before launching, after evidence validation and hashing.
    $lastOccupancy=Get-RuntimeOccupancy $inspection.home_path $inspection.ports
    if ($lastOccupancy.process_state -ne 'known' -or $lastOccupancy.port_state -ne 'known' -or
        @($lastOccupancy.target_processes).Count -or @($lastOccupancy.occupied_ports).Count) { throw 'Host occupancy changed or became unknown; repeat preflight.' }
    $logDirectory=Assert-RuntimeWorkspacePath (Join-Path $script:RuntimeProjectRoot ('build\runtime\launch-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8))) $inspection.home_path
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    # Output is deliberately discarded: NC console output can contain SQL and secrets.
    # HOME's own application logs remain the authorized source for service diagnostics.
    $processInfo=New-Object Diagnostics.ProcessStartInfo
    $processInfo.FileName=$env:ComSpec
    $processInfo.Arguments='/d /s /c ""'+(Join-Path $inspection.home_path 'startServer.bat')+'" 1>nul 2>nul"'
    $processInfo.WorkingDirectory=$logDirectory
    $processInfo.UseShellExecute=$false; $processInfo.CreateNoWindow=$true
    $processInfo.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden
    $processInfo.EnvironmentVariables['JAVA_HOME']=$inspection.jdk.home
    $process=New-Object Diagnostics.Process
    $process.StartInfo=$processInfo
    try {
        if (!$process.Start()) { throw 'NC launcher could not be created.' }
        $result.launcher_pid=$process.Id; $result.log_directory=$logDirectory
        if ($process.WaitForExit(1500)) {
            $result.launcher_exit_code=$process.ExitCode
            $result.status=$(if($process.ExitCode -eq 0){'launcher_exited_service_unverified'}else{'launcher_failed'})
        } else { $result.status='launcher_running_service_unverified' }
        [IO.File]::WriteAllText((Join-Path $logDirectory 'launch.json'),([pscustomobject]$result | ConvertTo-Json -Depth 8),(New-Object Text.UTF8Encoding($false)))
    } finally { $process.Dispose() }
    if ($result.status -eq 'launcher_failed') { throw 'NC launcher exited unsuccessfully. Inspect the sanitized launch record and approved NC application logs.' }
    return [pscustomobject]$result
}

if ($LoadFunctionsOnly) { return }
try {
    # Reject an unsafe report destination before any possible launch side effect.
    $reportPath=$null
    if ($OutputPath) { $reportPath=Assert-RuntimeWorkspacePath $OutputPath (Get-NormalizedRuntimePath $NcHome) }
    $result=Invoke-NcRuntime $Mode $NcHome $JavaHome $AdmissionPath ([bool]$DryRun)
    $json=$result | ConvertTo-Json -Depth 12
    if ($OutputPath) {
        $reportParent=Split-Path -Parent $reportPath
        if (!(Test-Path -LiteralPath $reportParent)) { New-Item -ItemType Directory -Path $reportParent -Force | Out-Null }
        [IO.File]::WriteAllText($reportPath,$json,(New-Object Text.UTF8Encoding($false)))
    }
    Write-Output $json
} catch {
    # Do not echo XML/JSON parse errors or OS command details containing raw content.
    $message=$_.Exception.Message
    if ($message -match 'Exception calling|Cannot convert|ConvertFrom-Json|xml|JSON|UnauthorizedAccess|Access.*denied' -and $message -notmatch '^(Admission|Start|prop.xml|Unsupported)') { $message='Runtime inspection failed to parse or access a required file. No launch was performed.' }
    Write-Error ('NC runtime request rejected: '+$message)
    exit 1
}
