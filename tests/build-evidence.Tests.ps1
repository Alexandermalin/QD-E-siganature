<# Isolated evidence/packaging tests. No real NC_HOME, compiler, NC service or database is used. #>
param([string]$ScriptsRoot=(Join-Path (Split-Path -Parent $PSScriptRoot) 'tools'))
$ErrorActionPreference='Stop'
$testUtf8=New-Object Text.UTF8Encoding($false)
$testBase=Join-Path (Split-Path -Parent $ScriptsRoot) ('build\evidence-tests-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testBase -Force|Out-Null
$assertions=0
function Check([bool]$ok,[string]$message){if(!$ok){throw "Assertion failed: $message"};$script:assertions++}
function Reject([scriptblock]$action,[string]$message){
    $failed=$false;try{& $action|Out-Null}catch{$failed=$true}
    Check $failed $message
}
function Put([string]$path,[string]$text){
    New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force|Out-Null
    [IO.File]::WriteAllText($path,$text,$testUtf8)
}
function Fixture([string]$root){
    foreach($name in @('baseapp','uapbd','pu','so','ic','purp')){
        foreach($layer in @('public','private','client')){New-Item -ItemType Directory -Path (Join-Path $root "$name\src\$layer") -Force|Out-Null}
    }
    Put (Join-Path $root 'tests\java\Marker.java') 'class Marker {}'
    Put (Join-Path $root 'config\qd-esign\scope.csv') 'disabled'
    Put (Join-Path $root 'sql\install.sql') '-- fixture only'
    Put (Join-Path $root 'docs\nc65-electronic-signature\GUIDE.md') 'Fixture documentation.'
    Put (Join-Path $root 'baseapp\AUTH_BRIDGE.md') 'Fixture authentication contract.'
    Put (Join-Path $root 'README.md') 'Fixture candidate.'
    Put (Join-Path $root 'uapbd\META-INF\qd_esign_core.upm') '<module/>'
    Put (Join-Path $root 'uapbd\src\public\Receipt.java') "package nc.vo.uapbd.qd.esign; public class Receipt {}"
    Put (Join-Path $root 'uapbd\src\client\nc\ui\uapbd\qd\esign\FixtureConfig.xml') '<beans/>'
    New-Item -ItemType Directory -Path (Join-Path $root 'tools') -Force|Out-Null
    Copy-Item -LiteralPath (Join-Path $ScriptsRoot 'build.ps1') -Destination (Join-Path $root 'tools\build.ps1')
    Copy-Item -LiteralPath (Join-Path $ScriptsRoot 'package.ps1') -Destination (Join-Path $root 'tools\package.ps1')
}
$fixtureRoot=Join-Path $testBase 'evidence'
Fixture $fixtureRoot
$fakeHome=Join-Path $testBase 'home'
Put (Join-Path $fakeHome 'ufjdk\bin\java.exe') 'fixture java bytes'
Put (Join-Path $fakeHome 'ufjdk\bin\javac.exe') 'fixture javac bytes'
Put (Join-Path $fakeHome 'ufjdk\jre\lib\rt.jar') 'fixture boot classpath fingerprint'
Put (Join-Path $fakeHome 'platform\classes\Platform.class') 'fixture directory dependency'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$emptyZip=[IO.Compression.ZipFile]::Open((Join-Path $fakeHome 'platform.jar'),[IO.Compression.ZipArchiveMode]::Create);$emptyZip.Dispose()
$homeXml=[Security.SecurityElement]::Escape($fakeHome)
$dirUrl=[Security.SecurityElement]::Escape('file://'+(Join-Path $fakeHome 'platform\classes').Replace('\','/'))
$jarUrl=[Security.SecurityElement]::Escape('jar://'+(Join-Path $fakeHome 'platform.jar').Replace('\','/')+'!/')
Put (Join-Path $fixtureRoot '.idea\NCHomeConfigSetting.xml') ('<project><option name="homePath" value="'+$homeXml+'"/></project>')
Put (Join-Path $fixtureRoot '.idea\libraries\Platform.xml') ('<component><library><CLASSES><root url="'+$jarUrl+'"/><root url="'+$dirUrl+'"/></CLASSES></library></component>')
$projectRoot=$fixtureRoot;$NcHome=$fakeHome;$buildRoot=Join-Path $fixtureRoot 'build';$recordPath=Join-Path $buildRoot 'build-result.json'
$modules=@('baseapp','uapbd','pu','so','ic','purp');$layers=@('public','private','client','legacy-examples','test');$utf8=$testUtf8
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $ScriptsRoot 'build.ps1'),[ref]$tokens,[ref]$errors)
Check (@($errors).Count -eq 0) 'build script parses'
$functions=@($ast.EndBlock.Statements|Where-Object{$_ -is [Management.Automation.Language.FunctionDefinitionAst]}|ForEach-Object{$_.Extent.Text}) -join [Environment]::NewLine
. ([scriptblock]::Create($functions))
# Only tool-version queries are substituted: snapshot/file/hash logic is the production code.
function Version([string]$path){return 'fixture Java7 version'}
$class=Join-Path $buildRoot 'classes\public\nc\vo\uapbd\qd\esign\Receipt.class'
New-Item -ItemType Directory -Path (Split-Path -Parent $class) -Force|Out-Null
[IO.File]::WriteAllBytes($class,[byte[]]@(202,254,186,190,0,0,0,51))
$snap=Snapshot;$artifacts=ClassArtifacts
Check ((JsonHash $snap) -eq (JsonHash (Snapshot))) 'snapshot is deterministic'
$record=[ordered]@{schema_version=2;nc_home=$fakeHome;tests_passed=$true;frozen_inputs=$snap;frozen_inputs_sha256=(JsonHash $snap);class_artifacts=$artifacts;class_artifacts_sha256=(JsonHash $artifacts)}
[IO.File]::WriteAllText($recordPath,($record|ConvertTo-Json -Depth 100),$testUtf8)
$null=VerifiedRecord;Check $true 'matching evidence accepted'
function Changed([string]$path,[string]$label){
    $before=[IO.File]::ReadAllBytes($path)
    try{[IO.File]::AppendAllText($path,'changed',$testUtf8);Reject {VerifiedRecord} $label}
    finally{[IO.File]::WriteAllBytes($path,$before)}
}
Changed (Join-Path $fixtureRoot 'uapbd\src\public\Receipt.java') 'source drift rejected'
Changed (Join-Path $fixtureRoot 'tests\java\Marker.java') 'test source drift rejected'
Changed (Join-Path $fixtureRoot 'tools\build.ps1') 'build script drift rejected'
Changed (Join-Path $fixtureRoot 'tools\package.ps1') 'package script drift rejected'
Changed (Join-Path $fixtureRoot 'uapbd\META-INF\qd_esign_core.upm') 'UPM drift rejected'
Changed (Join-Path $fixtureRoot 'uapbd\src\client\nc\ui\uapbd\qd\esign\FixtureConfig.xml') 'client XML drift rejected'
Changed (Join-Path $fixtureRoot 'config\qd-esign\scope.csv') 'configuration drift rejected'
Changed (Join-Path $fixtureRoot 'sql\install.sql') 'SQL drift rejected'
Changed (Join-Path $fakeHome 'platform\classes\Platform.class') 'classpath directory content drift rejected'
Changed (Join-Path $fakeHome 'ufjdk\bin\javac.exe') 'compiler tool drift rejected'
Changed (Join-Path $fakeHome 'ufjdk\jre\lib\rt.jar') 'boot classpath drift rejected'
Changed $class 'class artifact bytes drift rejected'
$extra=Join-Path $fakeHome 'platform\classes\Added.class'
Put $extra 'added'
try{Reject {VerifiedRecord} 'classpath directory addition rejected'}finally{Remove-Item -LiteralPath $extra}
$beforeJar=[IO.File]::ReadAllBytes((Join-Path $fakeHome 'platform.jar'))
$zip=[IO.Compression.ZipFile]::Open((Join-Path $fakeHome 'platform.jar'),[IO.Compression.ZipArchiveMode]::Update)
$entry=$zip.CreateEntry('extra.txt');$zip.Dispose()
try{Reject {VerifiedRecord} 'platform JAR drift rejected'}finally{[IO.File]::WriteAllBytes((Join-Path $fakeHome 'platform.jar'),$beforeJar)}
$originalHome=$NcHome;$NcHome=Join-Path $testBase 'another-home'
try{Reject {VerifiedRecord} 'cross-HOME reuse rejected'}finally{$NcHome=$originalHome}
$record.tests_passed=$false
[IO.File]::WriteAllText($recordPath,($record|ConvertTo-Json -Depth 100),$testUtf8)
Reject {VerifiedRecord} 'untested build rejected'
$record.tests_passed=$true;$record.schema_version=1
[IO.File]::WriteAllText($recordPath,($record|ConvertTo-Json -Depth 100),$testUtf8)
Reject {VerifiedRecord} 'legacy source-only build record rejected'
$record.schema_version=2
[IO.File]::WriteAllText($recordPath,($record|ConvertTo-Json -Depth 100),$testUtf8)
$classpath=@('D:\fixture-a.jar','D:\fixture-b.jar');$compiled=New-Object 'System.Collections.Generic.List[string]'
$compiled.Add((Join-Path $buildRoot 'classes\public'))
function FixtureCompiler { param([Parameter(ValueFromRemainingArguments=$true)][string[]]$args) $script:LASTEXITCODE=0 }
$javac='FixtureCompiler'
$null=Compile-Sources 'private' @((Join-Path $fixtureRoot 'uapbd\src\public\Receipt.java'))
$javacArgs=Get-Content -LiteralPath (Join-Path $buildRoot 'private-javac.args')
Check ($javacArgs[8] -eq ('"D:/fixture-a.jar;D:/fixture-b.jar;'+(Join-Path $buildRoot 'classes\public').Replace('\','/')+'"')) 'compiler classpath preserves ordered entries'
Check ($javacArgs[2] -eq '-source' -and $javacArgs[3] -eq '1.7' -and $javacArgs[4] -eq '-target' -and $javacArgs[5] -eq '1.7') 'compiler uses Java7 source and target'
$originalClass=[IO.File]::ReadAllBytes($class);$wrongClass=$originalClass.Clone();$wrongClass[7]=52
[IO.File]::WriteAllBytes($class,$wrongClass)
try{Reject {VerifiedRecord} 'non-Java7 class rejected'}finally{[IO.File]::WriteAllBytes($class,$originalClass)}
# A failed real build invocation must invalidate prior success and remove stale layer classes.
$failureRoot=Join-Path $testBase 'failure';Fixture $failureRoot
Put (Join-Path $failureRoot 'build\build-result.json') '{"schema_version":2,"tests_passed":true}'
foreach($layer in $layers){Put (Join-Path $failureRoot "build\classes\$layer\Stale.class") 'stale'}
Reject {& (Join-Path $failureRoot 'tools\build.ps1') -NcHome (Join-Path $testBase 'missing-home')} 'missing compiler fails build'
Check (!(Test-Path -LiteralPath (Join-Path $failureRoot 'build\build-result.json'))) 'failed build leaves no old successful record'
Check (@(Get-ChildItem -LiteralPath (Join-Path $failureRoot 'build\classes') -Recurse -File -Filter '*.class').Count -eq 0) 'known output layers are cleaned before compiling'
# Package boundaries use a verifier stub, keeping this fixture independent of Java/NC.
$packageRoot=Join-Path $testBase 'package';Fixture $packageRoot
$stub=@'
param([string]$NcHome,[switch]$Test,[switch]$VerifyRecord)
if(!$VerifyRecord){throw 'Fixture does not compile.'}
Get-Content -LiteralPath (Join-Path (Split-Path -Parent $PSScriptRoot) 'build\build-result.json') -Raw|ConvertFrom-Json
'@
Put (Join-Path $packageRoot 'tools\build.ps1') $stub
$stubRecord=[ordered]@{schema_version=2;nc_home=$fakeHome;tests_passed=$true;class_artifacts=$artifacts;frozen_inputs=[ordered]@{client_resources=$snap.client_resources;toolchain=[ordered]@{java_version='fixture JDK7';javac_version='fixture javac7';java=[ordered]@{sha256='fixture'};javac=[ordered]@{sha256='fixture'}}}}
Put (Join-Path $packageRoot 'build\build-result.json') ($stubRecord|ConvertTo-Json -Depth 10)
$packageClass=Join-Path $packageRoot 'build\classes\public\nc\vo\uapbd\qd\esign\Receipt.class'
New-Item -ItemType Directory -Path (Split-Path -Parent $packageClass) -Force|Out-Null
[IO.File]::WriteAllBytes($packageClass,$originalClass)
$null=& (Join-Path $packageRoot 'tools\package.ps1') -SkipBuild -NcHome $fakeHome
$candidate=Get-ChildItem -LiteralPath (Join-Path $packageRoot 'build') -File -Filter 'qd-esign-candidate-*.zip'|Select-Object -First 1
Check ($null -ne $candidate) 'candidate created after successful checks'
$zip=[IO.Compression.ZipFile]::OpenRead($candidate.FullName)
try{
    $manifestEntry=$zip.GetEntry('release-manifest.json');Check ($null -ne $manifestEntry) 'release manifest included'
    $reader=New-Object IO.StreamReader($manifestEntry.Open())
    try{$manifest=$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()}
    Check ($manifest.schema_version -eq 1 -and [bool]$manifest.release_id) 'manifest interface version and release ID'
    Check (@($manifest.files).Count -eq $zip.Entries.Count-1) 'manifest enumerates every payload file'
    $hash=[Security.Cryptography.SHA256]::Create()
    try{
        foreach($file in $manifest.files){
            $entry=$zip.GetEntry($file.path);Check ($null -ne $entry -and $entry.Length -eq $file.size) ('manifest size '+$file.path)
            $stream=$entry.Open();try{$actual=[BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-','')}finally{$stream.Dispose()}
            Check ($actual -eq $file.sha256) ('manifest hash '+$file.path)
        }
    }finally{$hash.Dispose()}
    $publicJar=$manifest.jar_entries|Where-Object path -Like '*/qd_esign_uapbd_public.jar'
    $clientJar=$manifest.jar_entries|Where-Object path -Like '*/qd_esign_uapbd_client.jar'
    Check ($publicJar.entries[0].major -eq 51) 'manifest records runtime class hashes and Java7 version'
    Check ($clientJar.resources[0].path -eq $snap.client_resources[0].entry -and
        $clientJar.resources[0].sha256 -eq $snap.client_resources[0].sha256) 'manifest records the tested client XML at its classpath location'
    Check ($null -ne $zip.GetEntry('evidence/build-result.json')) 'full build evidence included'
}finally{$zip.Dispose()}
[IO.File]::WriteAllBytes($packageClass,$wrongClass)
try{Reject {& (Join-Path $packageRoot 'tools\package.ps1') -SkipBuild -NcHome $fakeHome} 'package rejects non-Java7 class'}finally{[IO.File]::WriteAllBytes($packageClass,$originalClass)}
[IO.File]::WriteAllBytes($packageClass,([byte[]]$originalClass+[byte]1))
try{Reject {& (Join-Path $packageRoot 'tools\package.ps1') -SkipBuild -NcHome $fakeHome} 'package binds each included class to build artifact hash'}finally{[IO.File]::WriteAllBytes($packageClass,$originalClass)}
$packageXml=Join-Path $packageRoot $snap.client_resources[0].path
[IO.File]::AppendAllText($packageXml,'changed',$testUtf8)
Reject {& (Join-Path $packageRoot 'tools\package.ps1') -SkipBuild -NcHome $fakeHome} 'package binds client XML bytes to tested source'
Put (Join-Path $packageRoot 'uapbd\src\public\LeakTests.java') 'package nc.vo.uapbd.qd.esign; public class LeakTests {}'
$leak=Join-Path (Split-Path -Parent $packageClass) 'LeakTests.class';[IO.File]::WriteAllBytes($leak,$originalClass)
try{Reject {& (Join-Path $packageRoot 'tools\package.ps1') -SkipBuild -NcHome $fakeHome} 'package rejects test class'}
finally{Remove-Item -LiteralPath $leak;Remove-Item -LiteralPath (Join-Path $packageRoot 'uapbd\src\public\LeakTests.java')}
Put (Join-Path $packageRoot 'so\src\public\Receipt.java') 'package nc.vo.uapbd.qd.esign; public class Receipt {}'
Reject {& (Join-Path $packageRoot 'tools\package.ps1') -SkipBuild -NcHome $fakeHome} 'package rejects duplicate class across modules'
Write-Output "BUILD EVIDENCE FIXTURES PASSED: $assertions assertions. Fixture files: $testBase"

