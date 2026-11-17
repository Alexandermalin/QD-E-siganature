<# Offline fixtures only. Never runs Java, connects to Oracle or starts NC. #>
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $projectRoot 'tools\nc-runtime.ps1') -LoadFunctionsOnly
$fixtureRoot=Join-Path $projectRoot ('build\runtime-tests\'+[Guid]::NewGuid().ToString('N'))
$fixtureHome=Join-Path $fixtureRoot 'home'
$fixtureJdk=Join-Path $fixtureHome 'ufjdk'
$utf8=New-Object Text.UTF8Encoding($false)
$script:testCount=0
$initialLaunches=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build\runtime') -Directory -Filter 'launch-*' -ErrorAction SilentlyContinue).Count
function Assert-Test([bool]$Condition,[string]$Message) {
    if (!$Condition) { throw ('FAILED: '+$Message) }; $script:testCount++
}
function Assert-Rejected([scriptblock]$Action,[string]$Pattern) {
    $caught=$false
    try { & $Action | Out-Null } catch { $caught=$true; if ($_.Exception.Message -notmatch $Pattern) { throw ('Unexpected rejection: '+$_.Exception.Message) } }
    Assert-Test $caught ('Expected rejection: '+$Pattern)
}
function Write-Fixture([string]$Relative,[string]$Content) {
    $file=Join-Path $fixtureHome $Relative; $directory=Split-Path -Parent $file
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    [IO.File]::WriteAllText($file,$Content,$utf8)
}
$script:occupancyFixture='clear'; $script:addressesFixture=@('127.0.0.1','192.0.2.10')
function Get-RuntimeOccupancy([string]$TargetHome,[int[]]$Ports) {
    if ($script:occupancyFixture -eq 'unknown') { return [pscustomobject]@{process_state='unknown';port_state='unknown';target_processes=@();occupied_ports=@();reason_codes=@('FIXTURE_UNKNOWN')} }
    if ($script:occupancyFixture -eq 'occupied') { return [pscustomobject]@{process_state='known';port_state='known';target_processes=@();occupied_ports=@(@{port=18080;pid=123});reason_codes=@()} }
    return [pscustomobject]@{process_state='known';port_state='known';target_processes=@();occupied_ports=@();reason_codes=@()}
}
function Get-RuntimeLocalAddresses { return [pscustomobject]@{state='known';addresses=$script:addressesFixture} }

foreach ($file in @('starter.jar','middleware\mw.jar','middleware\tomcat.jar','bin\uapSetupCmdLine.bat','ufjdk\bin\java.exe','ufjdk\bin\javac.exe','ufjdk\jre\bin\j9vm\jvm.dll')) { Write-Fixture $file 'fixture, never execute' }
Write-Fixture 'startServer.bat' '@echo fixture, never execute'
Write-Fixture 'startup.bat' 'set JAVA_HOME=D:\nchome\ufjdk'
Write-Fixture 'ufjdk\release' 'JAVA_VERSION="1.7.0"'
Write-Fixture 'ierp\bin\prop.xml' @'
<?xml version="1.0" encoding="UTF-8"?>
<runtime><server><servicePort>8005</servicePort><port>18080</port><jvmArgs>-Xmx4096m -Dfile.encoding=GBK -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=192.0.2.10:8423 -Dcredential=NEVER_OUTPUT_ARG</jvmArgs></server><dataSource><dataSourceName>QDNC</dataSourceName><databaseType>ORACLE11G</databaseType><databaseUrl>jdbc:oracle:thin:@192.0.2.8:1521/NEVER_OUTPUT_SERVICE</databaseUrl><user>QDNC</user><password>NEVER_OUTPUT_PASSWORD_OR_CIPHERTEXT</password></dataSource></runtime>
'@
Write-Fixture 'ierp\bin\qd-esign-v1.properties' 'keyFile=C:\NEVER_OUTPUT_SECRET_KEYFILE'
Write-Fixture 'nclogs\server\nc-log.log' 'scheduler WMS http://NEVER_OUTPUT_HOST ORA-02049 private business payload NEVER_OUTPUT_LOG'
New-Item -ItemType Directory -Path (Join-Path $fixtureHome 'modules\baseapp\lib') -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath=Join-Path $fixtureHome 'modules\baseapp\lib\old.jar'
$jar=[IO.Compression.ZipFile]::Open($jarPath,[IO.Compression.ZipArchiveMode]::Create)
try { $entry=$jar.CreateEntry('nc/itf/baseapp/qd/esign/v1/Legacy.class'); $stream=$entry.Open(); try { $bytes=[byte[]]@(1,2,3);$stream.Write($bytes,0,$bytes.Length) } finally {$stream.Dispose()} } finally {$jar.Dispose()}
$before=@{}; Get-ChildItem -LiteralPath $fixtureHome -File -Recurse | ForEach-Object {$before[$_.FullName]=Get-RuntimeFileHash $_.FullName}
$inspection=Get-NcRuntimeInspection $fixtureHome $fixtureJdk
$json=$inspection | ConvertTo-Json -Depth 12
Assert-Test ($json -notmatch 'NEVER_OUTPUT') 'No password/ciphertext/URL/JVM arguments/keyfile/log content escapes inspection'
Assert-Test ($inspection.datasources.Count -eq 1 -and $inspection.datasources[0].schema -eq 'QDNC') 'Schema name is visible'
Assert-Test ($inspection.datasources[0].target_sha256 -match '^[0-9a-f]{64}$') 'Datasource endpoint is represented only by SHA256'
Assert-Test ($inspection.home_fingerprint -match '^[0-9a-f]{64}$') 'HOME fingerprint exists'
Assert-Test ($inspection.ports -contains 18080 -and $inspection.ports -contains 8005 -and $inspection.ports -contains 8423) 'All configured runtime ports are checked'
Assert-Test ($inspection.jdk.ibm_java7_confirmed) 'IBM Java7 fixture recognized without executing Java'
Assert-Test ($inspection.legacy.v1_class_count -eq 1 -and $inspection.legacy.v1_config_present) 'Existing V1 classes and configuration detected'
Assert-Test ($inspection.historical_activity.scheduled_task_activity -and $inspection.historical_activity.external_integration_activity) 'Historical scheduler/external activity reported as flags'
Assert-Test ($inspection.startup.legacy_startup_has_hardcoded_java_home) 'Legacy hardcoded JAVA_HOME detected'
Assert-Rejected { Get-NcRuntimeInspection '.\home' $fixtureJdk } 'absolute'
Assert-Rejected { Get-NcRuntimeInspection (Join-Path $fixtureRoot 'missing') $fixtureJdk } 'does not exist'
Assert-Rejected { Assert-RuntimeWorkspacePath (Join-Path $fixtureHome 'report.json') $fixtureHome } 'outside HOME'
Assert-Rejected { Invoke-NcRuntime 'Start' $fixtureHome $fixtureJdk '' $true } 'AdmissionPath'
Assert-Rejected { Invoke-NcRuntime 'Start' $fixtureHome '' '' $true } 'explicit'
$wrongJdk=Join-Path $fixtureRoot 'non-ibm-java8'
New-Item -ItemType Directory -Path (Join-Path $wrongJdk 'bin') -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $wrongJdk 'bin\java.exe'),'fixture, never execute',$utf8)
[IO.File]::WriteAllText((Join-Path $wrongJdk 'release'),'JAVA_VERSION="1.8.0"',$utf8)
Assert-Rejected { Invoke-NcRuntime 'Start' $fixtureHome $wrongJdk '' $true } 'IBM Java 7'
$evidencePath=Join-Path $fixtureRoot 'owner-evidence.txt'; [IO.File]::WriteAllText($evidencePath,'Offline fixture evidence. Never use for a real environment.',$utf8)
$admissionPath=Join-Path $fixtureRoot 'admission.json'
$checks=[ordered]@{}
foreach ($gate in @('environment_isolation','database_identity','outbound_and_jobs_isolation','backup_and_rollback','jdk_baseline','v1_compatibility')) {
    $kinds=@('general')
    if($gate -eq 'environment_isolation'){$kinds=@('environment','stop_check','users_drained','maintenance_owner')}
    if($gate -eq 'outbound_and_jobs_isolation'){$kinds=@('isolation','tasks_paused')}
    $references=@($kinds | ForEach-Object {@{path=$evidencePath;sha256=(Get-RuntimeFileHash $evidencePath);kind=$_}})
    $checks[$gate]=@{status='passed';description='Offline fixture only';evidence_refs=$references}
}
$admission=[ordered]@{schema_version=1;home_path=$inspection.home_path;home_fingerprint=$inspection.home_fingerprint;java_home=$fixtureJdk;environment_classification='shared_test_maintenance';valid_until_utc=[DateTime]::UtcNow.AddHours(1).ToString('o');checks=$checks}
function Write-Admission { [IO.File]::WriteAllText($admissionPath,($admission | ConvertTo-Json -Depth 12),$utf8) }
Write-Admission
$dryRunResult=Invoke-NcRuntime 'Start' $fixtureHome $fixtureJdk $admissionPath $true
Assert-Test ($dryRunResult.status -eq 'validated_not_started' -and $dryRunResult.dry_run) 'A valid dry run never launches NC'
$originalJavaHome=$env:JAVA_HOME
Assert-Test ($env:JAVA_HOME -eq $originalJavaHome) 'Dry run leaves process JAVA_HOME unchanged'
$admission.environment_classification='shared_test_active'; Write-Admission
Assert-Rejected { Invoke-NcRuntime 'Start' $fixtureHome $fixtureJdk $admissionPath $true } 'maintenance'
$admission.environment_classification='shared_test_maintenance';$admission.home_fingerprint='0'*64;Write-Admission
Assert-Rejected { Invoke-NcRuntime 'Start' $fixtureHome $fixtureJdk $admissionPath $true } 'fingerprint'
$admission.home_fingerprint=$inspection.home_fingerprint;$admission.valid_until_utc=[DateTime]::UtcNow.AddMinutes(-1).ToString('o');Write-Admission
Assert-Rejected { Invoke-NcRuntime 'Start' $fixtureHome $fixtureJdk $admissionPath $true } 'expired'
$admission.valid_until_utc=[DateTime]::UtcNow.AddHours(1).ToString('o');$admission.checks.v1_compatibility.status='pending';Write-Admission
Assert-Rejected { Invoke-NcRuntime 'Start' $fixtureHome $fixtureJdk $admissionPath $true } 'v1_compatibility'
$admission.checks.v1_compatibility.status='passed';$admission.checks.database_identity.evidence_refs[0].sha256='0'*64;Write-Admission
Assert-Rejected { Invoke-NcRuntime 'Start' $fixtureHome $fixtureJdk $admissionPath $true } 'checksum'
$admission.checks.database_identity.evidence_refs[0].sha256=Get-RuntimeFileHash $evidencePath;Write-Admission
$admission.checks.environment_isolation.evidence_refs[2].kind='unconfirmed';Write-Admission
Assert-Rejected { Invoke-NcRuntime 'Start' $fixtureHome $fixtureJdk $admissionPath $true } 'users_drained'
$admission.checks.environment_isolation.evidence_refs[2].kind='users_drained';Write-Admission
$script:occupancyFixture='unknown'
Assert-Rejected { Invoke-NcRuntime 'Start' $fixtureHome $fixtureJdk $admissionPath $true } 'visibility is unknown'
$script:occupancyFixture='occupied'
Assert-Rejected { Invoke-NcRuntime 'Start' $fixtureHome $fixtureJdk $admissionPath $true } 'occupied'
$script:occupancyFixture='clear';$script:addressesFixture=@('127.0.0.1')
Assert-Rejected { Invoke-NcRuntime 'Start' $fixtureHome $fixtureJdk $admissionPath $true } 'nonlocal'
$script:addressesFixture=@('127.0.0.1','192.0.2.10')
$changed=@(Get-ChildItem -LiteralPath $fixtureHome -File -Recurse | Where-Object {$before[$_.FullName] -ne (Get-RuntimeFileHash $_.FullName)})
Assert-Test ($changed.Count -eq 0) 'Inspection and all rejection paths preserve every HOME fixture file'
$finalLaunches=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build\runtime') -Directory -Filter 'launch-*' -ErrorAction SilentlyContinue).Count
Assert-Test ($finalLaunches -eq $initialLaunches) 'Dry run creates no launch directory'
Write-Output ('PASS: '+$script:testCount+' runtime fixture checks; no service was started. Fixtures: '+$fixtureRoot)
