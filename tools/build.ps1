<# NcHome is read-only. VerifyRecord checks evidence without compiling or writing. #>
param([string]$NcHome='E:\01-Home\NC65home\QuDuTest\nchome',[switch]$Test,[switch]$VerifyRecord)
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$buildRoot=Join-Path $projectRoot 'build'
$recordPath=Join-Path $buildRoot 'build-result.json'
$pendingPath=Join-Path $buildRoot 'build-result.pending.json'
$modules=@('baseapp','uapbd','pu','so','ic','purp')
$layers=@('public','private','client','legacy-examples','test')
$utf8=New-Object Text.UTF8Encoding($false)
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

function Relative([string]$base,[string]$path) {
    $prefix=[IO.Path]::GetFullPath($base).TrimEnd('\','/')+'\'
    $full=[IO.Path]::GetFullPath($path)
    if(!$full.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)){throw "Path escapes expected root: $full"}
    return $full.Substring($prefix.Length).Replace('\','/')
}
function Fingerprint([string]$path,[string]$base='') {
    $file=Get-Item -LiteralPath $path
    if($file.PSIsContainer -or ($file.Attributes -band [IO.FileAttributes]::ReparsePoint)){throw "Expected regular file: $path"}
    $name=$file.FullName; if($base){$name=Relative $base $name}
    return [ordered]@{path=$name;size=[long]$file.Length;sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash}
}
function JsonHash($value) {
    $hash=[Security.Cryptography.SHA256]::Create()
    try{return [BitConverter]::ToString($hash.ComputeHash($utf8.GetBytes(($value|ConvertTo-Json -Depth 100 -Compress)))).Replace('-','')}
    finally{$hash.Dispose()}
}
function Version([string]$path) {
    $info=New-Object Diagnostics.ProcessStartInfo
    $info.FileName=$path; $info.Arguments='-version'; $info.UseShellExecute=$false
    $info.CreateNoWindow=$true; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $process=New-Object Diagnostics.Process; $process.StartInfo=$info
    try{
        if(!$process.Start()){throw "Cannot start tool: $path"}
        $out=$process.StandardOutput.ReadToEnd();$err=$process.StandardError.ReadToEnd();$process.WaitForExit()
        if($process.ExitCode -ne 0){throw "Tool version query failed: $path"}
        return ($out+$err).Trim().Replace([string][char]13,'')
    }finally{$process.Dispose()}
}
function Snapshot {
    $resolvedHome=[IO.Path]::GetFullPath($NcHome).TrimEnd('\','/')
    $jdk=Join-Path $resolvedHome 'ufjdk'
    $java=Fingerprint (Join-Path $jdk 'bin\java.exe');$javac=Fingerprint (Join-Path $jdk 'bin\javac.exe')
    $sourceFiles=@()
    foreach($module in $modules){
        $root=Join-Path $projectRoot "$module\src"
        if(Test-Path -LiteralPath $root){$sourceFiles+=@(Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.java')}
    }
    $sourceFiles+=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tests') -Recurse -File -Filter '*.java')
    $sources=@($sourceFiles|Sort-Object FullName|ForEach-Object{Fingerprint $_.FullName $projectRoot})
    $files=@()
    $clientResources=@()
    foreach($relative in @('tools\build.ps1','tools\package.ps1','tools\copy-tested-sources.ps1','.idea\NCHomeConfigSetting.xml')){
        $files+=Get-Item -LiteralPath (Join-Path $projectRoot $relative)
    }
    $libraries=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot '.idea\libraries') -File -Filter '*.xml'|Sort-Object Name)
    $files+=$libraries
    foreach($folder in @('config','sql')){
        $files+=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot $folder) -Recurse -File|Where-Object{$_.Extension -ne '.pyc' -and $_.FullName -notmatch '[\\/]__pycache__[\\/]'})
    }
    foreach($module in $modules){
        $metadata=Join-Path $projectRoot "$module\META-INF"
        if(Test-Path -LiteralPath $metadata){$files+=@(Get-ChildItem -LiteralPath $metadata -Recurse -File -Filter '*.upm')}
        $configuration=Join-Path $projectRoot "$module\config"
        if(Test-Path -LiteralPath $configuration){$files+=@(Get-ChildItem -LiteralPath $configuration -Recurse -File)}
        $clientRoot=Join-Path $projectRoot "$module\src\client"
        $resourceRoot=Join-Path $clientRoot "nc\ui\$module\qd\esign"
        if(Test-Path -LiteralPath $resourceRoot){
            foreach($file in Get-ChildItem -LiteralPath $resourceRoot -Recurse -File -Filter '*.xml'|Sort-Object FullName){
                $resource=Fingerprint $file.FullName $projectRoot
                $resource['module']=$module
                $resource['entry']=Relative $clientRoot $file.FullName
                $clientResources+=$resource
            }
        }
    }
    $inputs=@($files|Sort-Object FullName -Unique|ForEach-Object{Fingerprint $_.FullName $projectRoot})
    [xml]$settings=Get-Content -LiteralPath (Join-Path $projectRoot '.idea\NCHomeConfigSetting.xml') -Raw -Encoding UTF8
    $configuredHome=[IO.Path]::GetFullPath($settings.SelectSingleNode('//option[@name="homePath"]').value).TrimEnd('\','/')
    $classpath=New-Object 'System.Collections.Generic.List[string]'
    foreach($file in $libraries){
        [xml]$library=Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
        foreach($entry in $library.SelectNodes('//CLASSES/root')){
            $path=$entry.url -replace '^(jar|file)://','' -replace '!/.*$',''
            $path=[IO.Path]::GetFullPath($path.Replace('$PROJECT_DIR$',$projectRoot))
            if($path.Equals($configuredHome,[StringComparison]::OrdinalIgnoreCase) -or $path.StartsWith($configuredHome+'\',[StringComparison]::OrdinalIgnoreCase)){
                $relativePath=$path.Substring($configuredHome.Length)
                $path=$resolvedHome+$relativePath
                if($relativePath -eq '\modules\riart\client\lib\uiriart_riasfbaseLevel-1.jar' -and !(Test-Path -LiteralPath $path)){
                    # The test deployment carries the same dependency in GL.
                    $deployedPath=Join-Path $resolvedHome 'modules\gl\lib\uiriart_riasfbaseLevel-1.jar'
                    if((Test-Path -LiteralPath $deployedPath -PathType Leaf) -and
                        (Get-FileHash -LiteralPath $deployedPath -Algorithm SHA256).Hash -eq '1038CD095F73E7C49E90562C0613A38C0E2DDE7BC81E64236B2BF92919E413E4'){
                        $path=$deployedPath
                    }
                }
            }
            if(!(Test-Path -LiteralPath $path)){throw "Missing platform dependency: $path"}
            if(!$classpath.Contains($path)){$classpath.Add($path)}
        }
    }
    $dependencies=@()
    foreach($path in $classpath){
        $item=Get-Item -LiteralPath $path
        if($item.Attributes -band [IO.FileAttributes]::ReparsePoint){throw "Dependency cannot be a link: $path"}
        if($item.PSIsContainer){
            $entries=@(Get-ChildItem -LiteralPath $path -Recurse -File|Sort-Object FullName|ForEach-Object{Fingerprint $_.FullName $path})
            $dependencies+=[ordered]@{path=$item.FullName;kind='directory';files=$entries}
        }else{
            if($path.EndsWith('.jar',[StringComparison]::OrdinalIgnoreCase)){$zip=[IO.Compression.ZipFile]::OpenRead($path);$zip.Dispose()}
            $dependencies+=[ordered]@{path=$item.FullName;kind='file';file=(Fingerprint $path)}
        }
    }
    $jdkFiles=@(Get-ChildItem -LiteralPath $jdk -Recurse -File|Where-Object{$_.Extension -in @('.exe','.dll','.jar','.properties','.cfg','.dat')}|Sort-Object FullName|ForEach-Object{Fingerprint $_.FullName $jdk})
    return [ordered]@{nc_home=$resolvedHome;toolchain=[ordered]@{java=$java;javac=$javac;java_version=(Version $java.path);javac_version=(Version $javac.path);jdk_files=$jdkFiles};sources=$sources;inputs=$inputs;client_resources=$clientResources;classpath=@($classpath.ToArray());dependencies=$dependencies}
}
function ClassArtifacts {
    $root=Join-Path $buildRoot 'classes'
    if(!(Test-Path -LiteralPath $root)){throw 'Compiled class output is missing.'}
    $result=@()
    foreach($file in Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.class'|Sort-Object FullName){
        $relative=Relative $root $file.FullName
        if($relative.Split('/')[0] -notin $layers){throw "Unrecognized class output layer: $relative"}
        $bytes=[IO.File]::ReadAllBytes($file.FullName)
        if($bytes.Length -lt 8 -or [BitConverter]::ToString($bytes,0,4) -ne 'CA-FE-BA-BE'){throw "Invalid class: $relative"}
        $major=[int]$bytes[6]*256+[int]$bytes[7]
        if($major -ne 51){throw "Expected Java 7 class: $relative"}
        $entry=Fingerprint $file.FullName $buildRoot;$entry['major']=$major;$result+=$entry
    }
    if($result.Count -eq 0){throw 'Compiled class inventory is empty.'}
    return ,$result
}
function VerifiedRecord {
    if(!(Test-Path -LiteralPath $recordPath)){throw 'A successful evidence-based build is required.'}
    $record=Get-Content -LiteralPath $recordPath -Raw -Encoding UTF8|ConvertFrom-Json
    if($record.schema_version -ne 2){throw 'Legacy build evidence is insufficient; run build.ps1 -Test again.'}
    if(!$record.tests_passed){throw 'Packaging requires a build with -Test.'}
    if(![IO.Path]::GetFullPath($NcHome).TrimEnd('\','/').Equals([string]$record.nc_home,[StringComparison]::OrdinalIgnoreCase)){throw 'Build NC_HOME differs from requested NC_HOME.'}
    if((JsonHash (Snapshot)) -ne $record.frozen_inputs_sha256){throw 'Source, scripts, configuration, JDK or platform dependencies differ from the tested build.'}
    if((JsonHash (ClassArtifacts)) -ne $record.class_artifacts_sha256){throw 'Compiled class artifacts differ from the tested build.'}
    return $record
}
if($VerifyRecord){VerifiedRecord;return}

# Invalidate previous success before any input/JDK/compilation check can fail.
foreach($directory in @($buildRoot,(Join-Path $buildRoot 'classes'))){
    if((Test-Path -LiteralPath $directory) -and ((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw "Build output cannot be a link: $directory"}
}
New-Item -ItemType Directory -Path $buildRoot -Force|Out-Null
foreach($path in @($recordPath,$pendingPath)){if(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path -Force}}
# Only delete .class files under the five explicitly checked build/classes layers.
$classRoot=[IO.Path]::GetFullPath((Join-Path $buildRoot 'classes'))
foreach($layer in $layers){
    $destination=[IO.Path]::GetFullPath((Join-Path $classRoot $layer))
    if(!$destination.StartsWith($classRoot+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe class cleanup path.'}
    if(Test-Path -LiteralPath $destination){
        foreach($directory in @(Get-Item -LiteralPath $destination)+@(Get-ChildItem -LiteralPath $destination -Directory -Recurse)){
            if($directory.Attributes -band [IO.FileAttributes]::ReparsePoint){throw "Class output contains a link: $($directory.FullName)"}
        }
        Get-ChildItem -LiteralPath $destination -Recurse -File -Filter '*.class'|ForEach-Object{Remove-Item -LiteralPath $_.FullName -Force}
    }
}
Write-Output 'Freezing source, configuration, toolchain and platform dependencies...'
$snapshot=Snapshot;$inputHash=JsonHash $snapshot
$java=$snapshot.toolchain.java.path;$javac=$snapshot.toolchain.javac.path;$classpath=@($snapshot.classpath)
[IO.File]::WriteAllLines((Join-Path $buildRoot 'dependency-limitations.txt'),[string[]]@(),$utf8)
[IO.File]::WriteAllLines((Join-Path $buildRoot 'platform-classpath.txt'),[string[]]$classpath,$utf8)
Write-Output $snapshot.toolchain.java_version
$compiled=New-Object 'System.Collections.Generic.List[string]'
function Compile-Sources([string]$kind,[string[]]$sources,[string]$encoding='UTF-8'){
    if($sources.Count -eq 0){return}
    $dest=Join-Path $buildRoot "classes\$kind";New-Item -ItemType Directory -Path $dest -Force|Out-Null
    $cp=(@($classpath)+@($compiled.ToArray()) -join ';').Replace('\','/')
    $arguments=@('-encoding',$encoding,'-source','1.7','-target','1.7','-g','-classpath',('"'+$cp+'"'),'-d',('"'+$dest.Replace('\','/')+'"'))
    $arguments+=$sources|ForEach-Object{'"'+$_.Replace('\','/')+'"'}
    $argumentFile=Join-Path $buildRoot "$kind-javac.args";[IO.File]::WriteAllLines($argumentFile,[string[]]$arguments,$utf8)
    Write-Output "Compiling $kind ($($sources.Count) source files)"
    & $javac "@$argumentFile"
    if($LASTEXITCODE -ne 0){throw "Compilation failed: $kind"}
    $compiled.Add($dest)
}
foreach($kind in @('public','private','client')){
    $sources=@($modules|ForEach-Object{$root=Join-Path $projectRoot "$_\src\$kind";if(Test-Path -LiteralPath $root){Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.java'|Sort-Object FullName|ForEach-Object FullName}})
    $legacy=@($sources|Where-Object{$_ -match '\\src\\client\\Application[0-9]+\.java$'})
    Compile-Sources $kind @($sources|Where-Object{$_ -notmatch '\\src\\client\\Application[0-9]+\.java$'})
    if($legacy.Count -gt 0){Compile-Sources 'legacy-examples' $legacy 'GBK'}
}
if($Test){
    $tests=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tests\java') -Recurse -File -Filter '*.java'|Sort-Object FullName|ForEach-Object FullName)
    foreach($additional in @('baseapp\src\test','tests\persistence')){$extra=Join-Path $projectRoot $additional;if(Test-Path -LiteralPath $extra){$tests+=@(Get-ChildItem -LiteralPath $extra -Recurse -File -Filter '*.java'|Sort-Object FullName|ForEach-Object FullName)}}
    Compile-Sources 'test' $tests
    $manifestPath=Join-Path $buildRoot 'runtime-classpath.jar'
    $paths=@($compiled.ToArray())+@($classpath)
    $urls=@($paths|ForEach-Object{$p=$_;if(Test-Path -LiteralPath $p -PathType Container){$p+=[IO.Path]::DirectorySeparatorChar};([Uri]$p).AbsoluteUri})
    $newline=[string][char]13+[char]10
    $manifest='Manifest-Version: 1.0'+$newline;$line='Class-Path: '+($urls -join ' ')
    while($line.Length -gt 70){$manifest+=$line.Substring(0,70)+$newline;$line=' '+$line.Substring(70)}
    $manifest+=$line+$newline+$newline
    $stream=[IO.File]::Open($manifestPath,[IO.FileMode]::Create)
    try{
        $archive=New-Object IO.Compression.ZipArchive($stream,[IO.Compression.ZipArchiveMode]::Create,$true)
        try{$entry=$archive.CreateEntry('META-INF/MANIFEST.MF');$writer=New-Object IO.StreamWriter($entry.Open(),$utf8);try{$writer.Write($manifest)}finally{$writer.Dispose()}}
        finally{$archive.Dispose()}
    }finally{$stream.Dispose()}
    $testTemp=Join-Path $buildRoot 'test-temp';New-Item -ItemType Directory -Path $testTemp -Force|Out-Null
    # The test JVM loads client XML from the same source roots that IDEA uses.
    $testClasspath=@($manifestPath)
    foreach($module in $modules){
        $clientRoot=Join-Path $projectRoot "$module\src\client"
        if(Test-Path -LiteralPath $clientRoot -PathType Container){$testClasspath+=$clientRoot}
    }
    & $java -ea '-Djava.awt.headless=true' "-Djava.io.tmpdir=$testTemp" -cp ($testClasspath -join ';') nc.test.qd.esign.AllTests
    if($LASTEXITCODE -ne 0){throw 'Offline tests failed.'}
}
Write-Output 'Checking frozen inputs after compilation and tests...'
if((JsonHash (Snapshot)) -ne $inputHash){throw 'Inputs changed while building; no successful record was published.'}
$artifacts=ClassArtifacts;$sourceHashes=[ordered]@{}
foreach($source in $snapshot.sources){$sourceHashes[(Join-Path $projectRoot $source.path.Replace('/','\'))]=$source.sha256}
$result=[ordered]@{schema_version=2;nc_home=$snapshot.nc_home;tests_passed=[bool]$Test;completed_at_utc=[DateTime]::UtcNow.ToString('o');compiler=$javac;dependency_exclusions=0;source_hashes=$sourceHashes;frozen_inputs=$snapshot;frozen_inputs_sha256=$inputHash;class_artifacts=$artifacts;class_artifacts_sha256=(JsonHash $artifacts)}
[IO.File]::WriteAllText($pendingPath,($result|ConvertTo-Json -Depth 100),$utf8)
Move-Item -LiteralPath $pendingPath -Destination $recordPath -Force
Write-Output 'BUILD PASSED. Frozen input and class evidence recorded. No deployment performed.'

