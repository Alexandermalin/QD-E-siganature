<# Standalone Windows PowerShell 5.1 integration tests. Only generated fixture HOMEs are mutated. #>
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version 2.0
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$tool = Join-Path $root 'tools\nc-patch.ps1'
$runRoot = Join-Path $root ('build\deployment-fixtures\'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
$utf8 = New-Object Text.UTF8Encoding($false)
$shell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$assertions=0
$calls=0
function Assert($Condition,[string]$Message) { if (!$Condition) { throw "ASSERTION FAILED: $Message" }; $script:assertions++ }
function Put([string]$Path,[string]$Text) { New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null;[IO.File]::WriteAllText($Path,$Text,$utf8) }
function Digest([byte[]]$Bytes) { $sha=[Security.Cryptography.SHA256]::Create(); try{return [BitConverter]::ToString($sha.ComputeHash($Bytes)).Replace('-','')}finally{$sha.Dispose()} }
$paths = @(
 'modules/baseapp/lib/qd_esign_baseapp_public.jar','modules/baseapp/META-INF/lib/qd_esign_baseapp_private.jar',
 'modules/uapbd/lib/qd_esign_uapbd_public.jar','modules/uapbd/META-INF/lib/qd_esign_uapbd_private.jar',
 'modules/pu/lib/qd_esign_pu_public.jar','modules/pu/META-INF/lib/qd_esign_pu_private.jar',
 'modules/so/lib/qd_esign_so_public.jar','modules/so/META-INF/lib/qd_esign_so_private.jar',
 'modules/ic/lib/qd_esign_ic_public.jar','modules/ic/META-INF/lib/qd_esign_ic_private.jar',
 'modules/purp/lib/qd_esign_purp_public.jar','modules/purp/META-INF/lib/qd_esign_purp_private.jar',
 'modules/uapbd/client/lib/qd_esign_uapbd_client.jar',
 'modules/baseapp/META-INF/qd_esign_auth.upm','modules/uapbd/META-INF/qd_esign_core.upm',
 'modules/uapbd/META-INF/qd_esign_logs.upm','modules/uapbd/META-INF/qd_esign_material.upm',
 'modules/pu/META-INF/qd_esign_purchase_adapter.upm','modules/so/META-INF/qd_esign_saleorder.upm',
 'modules/ic/META-INF/qd_esign_inventory_adapter.upm','modules/purp/META-INF/qd_esign_supplier_price_adapter.upm'
)
function Zip-Entry($Zip,[string]$Name,[byte[]]$Bytes) { $entry=$Zip.CreateEntry($Name);$s=$entry.Open();try{$s.Write($Bytes,0,$Bytes.Length)}finally{$s.Dispose()} }
function New-Package([string]$Path,[string]$Extra='',[switch]$BadManifest,[switch]$MissingModule) {
    $zip=[IO.Compression.ZipFile]::Open($Path,[IO.Compression.ZipArchiveMode]::Create)
    try {
        $files=@()
        foreach($name in $paths) {
            if($MissingModule -and $name -eq $paths[0]){continue}
            $bytes=$utf8.GetBytes('new fixture payload: '+$name)
            Zip-Entry $zip $name $bytes
            $files += [pscustomobject]@{path=$name;sha256=(Digest $bytes);size=$bytes.Length}
        }
        if($BadManifest){$files[0].sha256='0'*64}
        $manifest=[pscustomobject]@{schema_version=1;release_id='fixture-release';files=$files}
        Zip-Entry $zip 'release-manifest.json' ($utf8.GetBytes(($manifest|ConvertTo-Json -Depth 6)))
        Zip-Entry $zip 'README.md' ($utf8.GetBytes('fixture documentation, not installed'))
        Zip-Entry $zip 'docs/notes.txt' ($utf8.GetBytes('ignored'))
        if($Extra){Zip-Entry $zip $Extra ($utf8.GetBytes('unapproved'))}
    }finally{$zip.Dispose()}
}
function Fixture([string]$Name) {
    $base=Join-Path $runRoot $Name
    $targetHome=Join-Path $base 'home'
    New-Item -ItemType Directory -Path $targetHome -Force|Out-Null
    Put (Join-Path $targetHome $paths[0]) 'original module'
    Put (Join-Path $targetHome 'modules\uapbd\lib\product.jar') 'product retained'
    Put (Join-Path $targetHome 'ierp\bin\prop.xml') '<config><password>fixture-secret</password></config>'
    Put (Join-Path $targetHome 'ierp\bin\config\nested\options.properties') 'secret=fixture-value'
    Put (Join-Path $targetHome 'nclogs\prior.log') 'retain audit evidence'
    $package=Join-Path $base 'candidate.zip'
    New-Package $package
    return [pscustomobject]@{base=$base;home=$targetHome;package=$package}
}
function Call([string[]]$Arguments,[switch]$Fail) {
    $script:calls++
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try{$out=@(& $shell -NoProfile -ExecutionPolicy Bypass -File $tool @Arguments 2>&1);$code=$LASTEXITCODE}finally{$ErrorActionPreference=$old}
    $text=($out|ForEach-Object{$_.ToString()})-join "`n"
    if($Fail){Assert ($code-ne0) ('command should reject '+($Arguments-join ' '));return $text}
    if($code-ne0){throw ('Child tool failed: '+$text)}
    Assert (!$text.Contains('fixture-secret') -and !$text.Contains('fixture-value')) 'secret configuration values must not be printed'
    return ($text|ConvertFrom-Json)
}
function Plan($F) { return (Call @('-NcHome',$F.home,'-Package',$F.package)) }
function Admit($F,$P,[string]$Type='dedicated_test') {
    $file=Join-Path $F.base 'admission.json'
    $evidenceFile=Join-Path $F.base 'fixture-evidence.txt'
    Put $evidenceFile 'This generated fixture has no NC server, users, database or external integration; lifecycle writes are restricted to fixture module files.'
    $hash=(Get-FileHash -LiteralPath $evidenceFile -Algorithm SHA256).Hash
    $references=@('environment','stop_check','isolation'|ForEach-Object{[pscustomobject]@{path=$evidenceFile;sha256=$hash;kind=$_}})
    $checks=[ordered]@{}
    foreach($name in @('environment_isolation','database_identity','outbound_and_jobs_isolation','backup_and_rollback','jdk_baseline','v1_compatibility')){$checks[$name]=[pscustomobject]@{status='passed';description='Generated offline fixture only; no real runtime or business data.';evidence_refs=$references}}
    $body=[pscustomobject]@{schema_version=1;environment_classification=$Type;home_path=$F.home;package_sha256=(Get-FileHash -LiteralPath $F.package -Algorithm SHA256).Hash;nc_server_stopped=$true;valid_until_utc=[DateTime]::UtcNow.AddHours(1).ToString('o');checks=$checks;confirmed_by='fixture test';confirmed_at_utc=[DateTime]::UtcNow.ToString('o')}
    Put $file ($body|ConvertTo-Json -Depth 8)
    return $file
}

# Complete lifecycle: one existing file is replaced, twenty new files are later removed.
$f=Fixture 'lifecycle';$p=Plan $f
Assert ($p.module_files-eq21) 'fixed 21-file payload inventory'
$state=$p.state_directory;$id=$p.deployment_id
$plan=Get-Content -LiteralPath (Join-Path $state 'plan.json') -Raw|ConvertFrom-Json
Assert (@($plan.files|Where-Object change -eq 'update').Count-eq1) 'existing project module recorded for restoration'
Assert (@($plan.files|Where-Object change -eq 'add').Count-eq20) 'new module files recorded for removal'
Assert ((Get-Content -LiteralPath (Join-Path $f.home $paths[0]) -Raw)-eq'original module') 'Plan never modifies HOME'
Assert ((Get-Acl -LiteralPath $state).AreAccessRulesProtected) 'private deployment-state ACL'
$null=Call @('-Action','Backup','-DeploymentId',$id)
Assert (Test-Path -LiteralPath (Join-Path $state 'backup\files\ierp\bin\prop.xml')) 'sensitive configuration backup exists privately'
Assert (Test-Path -LiteralPath (Join-Path $state 'backup\files\ierp\bin\config\nested\options.properties')) 'recursive config backup'
$a=Admit $f $p
$null=Call @('-Action','Deploy','-DeploymentId',$id,'-Admission',$a)
$null=Call @('-Action','Verify','-DeploymentId',$id)
Assert (!(Test-Path -LiteralPath (Join-Path $f.home 'README.md'))) 'documentation is not extracted into HOME'
Assert ((Get-Content -LiteralPath (Join-Path $f.home 'modules\uapbd\lib\product.jar') -Raw)-eq'product retained') 'product files untouched'
$null=Call @('-Action','Deploy','-DeploymentId',$id,'-Admission',$a) -Fail
$drift=Join-Path $f.home $paths[3];$saved=[IO.File]::ReadAllBytes($drift);[IO.File]::WriteAllText($drift,'later edit')
$null=Call @('-Action','Rollback','-DeploymentId',$id,'-Admission',$a) -Fail
Assert ((Get-Content -LiteralPath $drift -Raw)-eq'later edit') 'rollback never overwrites a later modification'
Assert (Test-Path -LiteralPath (Join-Path $f.home $paths[-1])) 'rollback validates all paths before changing any module'
[IO.File]::WriteAllBytes($drift,$saved)
$null=Call @('-Action','Rollback','-DeploymentId',$id,'-Admission',$a)
Assert ((Get-Content -LiteralPath (Join-Path $f.home $paths[0]) -Raw)-eq'original module') 'prior project JAR restored'
Assert (@($paths|Select-Object -Skip 1|Where-Object{Test-Path -LiteralPath (Join-Path $f.home $_)}).Count-eq0) 'only newly installed files removed'
Assert ((Get-Content -LiteralPath (Join-Path $f.home 'nclogs\prior.log') -Raw)-eq'retain audit evidence') 'existing logs retained'
Assert ((Get-Content -LiteralPath (Join-Path $f.home 'ierp\bin\prop.xml') -Raw).Contains('fixture-secret')) 'configuration unchanged by deployment and rollback'
$null=Call @('-Action','Rollback','-DeploymentId',$id,'-Admission',$a) -Fail

# Recover a persisted interrupted-operation journal: three published files, one intent-only row.
# The fixture models termination between atomic replacement and journal completion.
$f=Fixture 'interrupted-journal';$p=Plan $f;$id=$p.deployment_id
$null=Call @('-Action','Backup','-DeploymentId',$id)
$a=Admit $f $p
$savedPlan=Get-Content -LiteralPath (Join-Path $p.state_directory 'plan.json') -Raw|ConvertFrom-Json
$rows=@();$index=0
foreach($record in $savedPlan.files){
    $status='pending'
    if($index-lt3){Put (Join-Path $f.home $record.path) ('new fixture payload: '+$record.path);$status='applied'}
    elseif($index-eq3){$status='applying'}
    $rows+=[pscustomobject]@{path=$record.path;status=$status};$index++
}
$journal=[pscustomobject]@{schema_version=1;deployment_id=$id;plan_sha256=(Get-FileHash -LiteralPath (Join-Path $p.state_directory 'plan.json') -Algorithm SHA256).Hash;status='deploying';files=$rows}
Put (Join-Path $p.state_directory 'journal.json') ($journal|ConvertTo-Json -Depth 5)
$null=Call @('-Action','Rollback','-DeploymentId',$id,'-Admission',$a)
Assert ((Get-Content -LiteralPath (Join-Path $f.home $paths[0]) -Raw)-eq'original module') 'interrupted update restores prior file'
Assert (!(Test-Path -LiteralPath (Join-Path $f.home $savedPlan.files[1].path))) 'interrupted added file is removed'
Assert ((Get-Content -LiteralPath (Join-Path $p.state_directory 'journal.json') -Raw|ConvertFrom-Json).status-eq'rolled-back') 'interrupted journal reaches verified terminal state'

# Manifest drift and before-deploy filesystem drift fail before any HOME write.
$f=Fixture 'preflight';$p=Plan $f;$id=$p.deployment_id
$null=Call @('-Action','Backup','-DeploymentId',$id)
$a=Admit $f $p
Put (Join-Path $f.home $paths[0]) 'concurrent replacement'
$null=Call @('-Action','Deploy','-DeploymentId',$id,'-Admission',$a) -Fail
Assert (!(Test-Path -LiteralPath (Join-Path $f.home $paths[1]))) 'pre-deploy drift leaves new module absent'

# Shared active environments and empty maintenance evidence cannot pass admission.
$f=Fixture 'admission';$p=Plan $f;$id=$p.deployment_id
$null=Call @('-Action','Backup','-DeploymentId',$id)
$a=Admit $f $p
Put (Join-Path $f.base 'fixture-evidence.txt') 'altered after approval'
$null=Call @('-Action','Deploy','-DeploymentId',$id,'-Admission',$a) -Fail
$a=Admit $f $p 'shared-test'
$null=Call @('-Action','Deploy','-DeploymentId',$id,'-Admission',$a) -Fail
$a=Admit $f $p 'shared_test_maintenance'
$null=Call @('-Action','Deploy','-DeploymentId',$id,'-Admission',$a) -Fail
Assert (!(Test-Path -LiteralPath (Join-Path $f.home $paths[1]))) 'shared environment rejection leaves HOME untouched'

# Malicious ZIP names, duplicate aliases, product JARs, incomplete payload, bad hashes.
$badNames=@('../escape.txt','modules/uapbd/lib/product.jar','modules/uapbd/lib/qd_esign_uapbd_private.jar','modules/uapbd/lib/..\escape.jar','DOCS/NOTES.TXT','unapproved.exe','docs/CON.txt','docs/file.txt:stream')
$i=0
foreach($name in $badNames){
    $i++;$bad=Join-Path $runRoot ("bad-$i.zip");New-Package $bad $name
    $null=Call @('-NcHome',$f.home,'-Package',$bad) -Fail
}
$bad=Join-Path $runRoot 'bad-hash.zip';New-Package $bad -BadManifest
$null=Call @('-NcHome',$f.home,'-Package',$bad) -Fail
$bad=Join-Path $runRoot 'missing-module.zip';New-Package $bad -MissingModule
$null=Call @('-NcHome',$f.home,'-Package',$bad) -Fail

# A junction in the HOME path is rejected before reading/writing its target.
$junction=Join-Path $runRoot 'home-link';$junctionCoverage='generated-junction'
try { New-Item -ItemType Junction -Path $junction -Target $f.home -ErrorAction Stop|Out-Null }
catch {
    $junction=Join-Path $env:USERPROFILE 'My Documents'
    if ((Get-Item -LiteralPath $junction -Force -ErrorAction SilentlyContinue).Attributes -band [IO.FileAttributes]::ReparsePoint) { $junctionCoverage='existing-Windows-junction-read-only' }
    else { throw 'No reparse fixture can be created or inspected; junction coverage cannot be claimed.' }
}
$null=Call @('-NcHome',$junction,'-Package',$f.package) -Fail
Assert (!(Test-Path -LiteralPath (Join-Path $runRoot 'escape.txt'))) 'ZIP traversal never creates an escaped file'

$result=[pscustomobject]@{passed=$true;assertions=$assertions;tool_invocations=$calls;junction_coverage=$junctionCoverage;fixture_root=$runRoot;real_home_modified=$false}
Put (Join-Path $runRoot 'result.json') ($result|ConvertTo-Json)
$result|ConvertTo-Json
