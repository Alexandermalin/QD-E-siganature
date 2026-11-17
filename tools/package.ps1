<# Default rebuilds with tests. SkipBuild still verifies every frozen input and compiled artifact. #>
param([switch]$SkipBuild,[string]$NcHome='E:\01-Home\NC65home\QuDuTest\nchome')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
if(!$SkipBuild){& (Join-Path $PSScriptRoot 'build.ps1') -NcHome $NcHome -Test}
$record=& (Join-Path $PSScriptRoot 'build.ps1') -NcHome $NcHome -VerifyRecord
$recordPath=Join-Path $projectRoot 'build\build-result.json'
$recordHash=(Get-FileHash -LiteralPath $recordPath -Algorithm SHA256).Hash
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$buildRoot = Join-Path $projectRoot 'build'
$modules = @('baseapp','uapbd','pu','so','ic','purp')
$clientResources = @{}
foreach ($resource in $record.frozen_inputs.client_resources) {
    $module = $resource.module
    if ($module -cnotin $modules -or $resource.entry -cnotmatch "^nc/ui/$module/qd/esign/([A-Za-z0-9_-]+/)*[A-Za-z0-9_.-]+\.xml$" -or
        $resource.path -cne "$module/src/client/$($resource.entry)") { throw 'Invalid client XML in the build record.' }
    if (!$clientResources.ContainsKey($module)) { $clientResources[$module] = @{} }
    if ($clientResources[$module].ContainsKey($resource.entry)) { throw "Duplicate client XML: $($resource.entry)" }
    $clientResources[$module][$resource.entry] = $resource
}
<# 每次使用独立暂存目录，避免旧候选包残留混入本次内容。 #>
$stage = Join-Path $buildRoot ('package-staging\' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stage -Force | Out-Null
foreach ($module in $modules) {
    foreach ($kind in @('public','private','client')) {
        $sourceRoot = Join-Path $projectRoot "$module\src\$kind"
        if (!(Test-Path -LiteralPath $sourceRoot)) { continue }
        $classes = Join-Path $buildRoot "classes\$kind"
        $jarFiles = New-Object 'System.Collections.Generic.List[string]'
        foreach ($source in Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' -File) {
            <# 排除占位 Application 和包说明文件，只按真实源码包路径收集主类及其内部类。 #>
            if ($source.Name -match '^Application[0-9]+\.java$') { continue }
            if ($source.Name -eq 'package-info.java') { continue }
            $content = Get-Content -LiteralPath $source.FullName -Raw -Encoding UTF8
            $match = [regex]::Match($content,'(?m)^\s*package\s+([\w.]+)\s*;')
            if (!$match.Success) { throw "Packaged Java source has no package: $($source.FullName)" }
            $relative = $match.Groups[1].Value.Replace('.','\')
            $compiledDir = Join-Path $classes $relative
            $name = [IO.Path]::GetFileNameWithoutExtension($source.Name)
            $found = @(Get-ChildItem -LiteralPath $compiledDir -Filter "$name*.class" | Where-Object { $_.Name -match ('^' + [regex]::Escape($name) + '(\$.*)?\.class$') })
            if ($found.Count -eq 0) { throw "Missing compiled class: $($source.FullName)" }
            foreach ($class in $found) { $jarFiles.Add($class.FullName) }
        }
        $resources = @()
        if ($kind -eq 'client' -and $clientResources.ContainsKey($module)) {
            $resources = @($clientResources[$module].Values | Sort-Object entry)
        }
        if ($jarFiles.Count -eq 0 -and $resources.Count -eq 0) { continue }
        <# 公共类放模块 lib，私有类放 META-INF/lib，客户端类放 client/lib，不覆盖原产品包。 #>
        $jarDirectory = switch ($kind) {
            'public' { "modules\$module\lib" }
            'private' { "modules\$module\META-INF\lib" }
            'client' { "modules\$module\client\lib" }
        }
        $destination = Join-Path $stage $jarDirectory
        New-Item -ItemType Directory -Path $destination -Force | Out-Null
        $jar = Join-Path $destination "qd_esign_${module}_${kind}.jar"
        <# 以新文件方式创建附加 JAR，写入完成后始终释放 ZIP 和文件流资源。 #>
        $stream = [IO.File]::Open($jar,[IO.FileMode]::CreateNew)
        try {
            $archive = New-Object IO.Compression.ZipArchive($stream,[IO.Compression.ZipArchiveMode]::Create,$true)
            try {
                foreach ($class in $jarFiles) {
                    $entry = $class.Substring($classes.Length+1).Replace('\','/')
                    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$class,$entry) | Out-Null
                }
                foreach ($resource in $resources) {
                    $source = Join-Path $projectRoot $resource.path
                    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$source,$resource.entry) | Out-Null
                }
            } finally { $archive.Dispose() }
        } finally { $stream.Dispose() }
    }
    <# 复制本项目附加 UPM 与离线接线示例，示例仍不是活动节点注册。 #>
    $upmRoot = Join-Path $projectRoot "$module\META-INF"
    if (Test-Path -LiteralPath $upmRoot) {
        $dest = Join-Path $stage "modules\$module\META-INF"
        New-Item -ItemType Directory -Path $dest -Force | Out-Null
        Get-ChildItem -LiteralPath $upmRoot -Filter 'qd_esign*.upm' -File | Copy-Item -Destination $dest
    }
    $examples = Join-Path $projectRoot "$module\config\examples"
    if (Test-Path -LiteralPath $examples) {
        $exampleDestination = Join-Path $stage "examples\$module\config"
        New-Item -ItemType Directory -Path $exampleDestination -Force | Out-Null
        Copy-Item -LiteralPath $examples -Destination $exampleDestination -Recurse
    }
}
<# 加入 SQL、正式范围配置和交付文档，跳过 Python 缓存；只是复制文件，不执行 SQL。 #>
foreach ($folder in @('sql','config','docs\nc65-electronic-signature')) {
    $source = Join-Path $projectRoot $folder
    $dest = Join-Path $stage $folder
    foreach ($file in Get-ChildItem -LiteralPath $source -Recurse -File) {
        if ($file.Extension -eq '.pyc' -or $file.FullName -match '[\\/]__pycache__[\\/]') { continue }
        $target = Join-Path $dest $file.FullName.Substring($source.Length+1)
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $target
    }
}
Copy-Item -LiteralPath (Join-Path $projectRoot 'README.md') -Destination $stage
$bridgeDoc = Join-Path $projectRoot 'baseapp\AUTH_BRIDGE.md'
Copy-Item -LiteralPath $bridgeDoc -Destination (Join-Path $stage 'docs\nc65-electronic-signature\AUTH_BRIDGE.md')
<# 以下候选包说明属于实际输出文本，保持原样；不能把离线编译通过描述为生产验收通过。 #>
$readme = @'
NC65 electronic signature OFFLINE CANDIDATE package.
This archive is not a verified production release. No deployment has been performed.
Review docs/nc65-electronic-signature/DEPLOYMENT_GUIDE.md and UNRESOLVED_ISSUES.md.
Do not overwrite existing NC product JARs, UPMs, configuration or keys.
SQL must be reviewed and approved before execution. All node mappings are disabled.
Client examples are NOT active node registrations. Missing transaction/snapshot hooks prohibit enabling audit.
'@
[IO.File]::WriteAllText((Join-Path $stage 'READ_BEFORE_DEPLOYMENT.txt'),$readme,(New-Object Text.UTF8Encoding($false)))

# Freeze a portable, exhaustive manifest. Nothing is installed or executed here.
$utf8=New-Object Text.UTF8Encoding($false)
$artifactIndex=@{}
foreach($artifact in $record.class_artifacts){$artifactIndex[$artifact.path]=$artifact}
$jarEntries=@();$allRuntimePaths=New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
$sha=[Security.Cryptography.SHA256]::Create()
try {
    foreach($jar in Get-ChildItem -LiteralPath (Join-Path $stage 'modules') -Recurse -File -Filter '*.jar' | Sort-Object FullName) {
        $relative=$jar.FullName.Substring($stage.Length+1).Replace('\','/')
        if($relative -notmatch '^modules/([^/]+)/.+_(public|private|client)\.jar$'){throw "Unrecognized JAR layer: $relative"}
        $jarModule=$Matches[1];$jarLayer=$Matches[2]
        $expectedResources=@{}
        if($jarLayer -eq 'client' -and $clientResources.ContainsKey($jarModule)){$expectedResources=$clientResources[$jarModule]}
        $entries=@();$resourceEntries=@()
        $archive=[IO.Compression.ZipFile]::OpenRead($jar.FullName)
        try {
            foreach($entry in $archive.Entries | Sort-Object FullName) {
                $path=$entry.FullName
                $resource=$expectedResources[$path]
                if(!$resource -and ($path -notmatch '^nc/(bs|impl|itf|ui|vo)/(baseapp|uapbd|pu|so|ic|purp)/qd/esign/[A-Za-z0-9_/$]+\.class$' -or $path -match '(^|/)(Application[0-9]*|[^/]*(Test|Tests|Fixture))([\$./]|$)')) { throw "Unexpected runtime entry in candidate JAR: $relative :: $path" }
                if(!$allRuntimePaths.Add($path)) { throw "Duplicate runtime entry in candidate: $path" }
                $input=$entry.Open();$memory=New-Object IO.MemoryStream
                try{$input.CopyTo($memory);$bytes=$memory.ToArray()}finally{$input.Dispose();$memory.Dispose()}
                $entryHash=[BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-','')
                if($resource){
                    if($resource.entry -cne $path -or $resource.sha256 -ne $entryHash -or $resource.size -ne $entry.Length){throw "Packaged client XML differs from tested source: $path"}
                    $resourceEntries += [ordered]@{path=$path;size=[long]$entry.Length;sha256=$entryHash}
                    continue
                }
                if($bytes.Length -lt 8 -or [BitConverter]::ToString($bytes,0,4) -ne 'CA-FE-BA-BE') { throw "Invalid class: $path" }
                $major=[int]$bytes[6]*256+[int]$bytes[7]
                if($major -ne 51) { throw "Non-Java7 class: $path" }
                $artifact=$artifactIndex['classes/'+$jarLayer+'/'+$path]
                if(!$artifact -or $artifact.sha256 -ne $entryHash -or $artifact.size -ne $entry.Length){throw "Candidate class differs from recorded build artifact: $path"}
                $entries += [ordered]@{path=$path;size=[long]$entry.Length;sha256=$entryHash;major=$major}
            }
        } finally{$archive.Dispose()}
        if($resourceEntries.Count -ne $expectedResources.Count){throw "Missing tested client XML in $relative"}
        if($entries.Count -eq 0 -and $resourceEntries.Count -eq 0){throw "Empty runtime JAR: $relative"}
        $inventory=[ordered]@{path=$relative;entries=$entries}
        if($resourceEntries.Count){$inventory['resources']=$resourceEntries}
        $jarEntries += $inventory
    }
} finally{$sha.Dispose()}
$evidence=Join-Path $stage 'evidence'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
Copy-Item -LiteralPath $recordPath -Destination (Join-Path $evidence 'build-result.json')
if((Get-FileHash -LiteralPath (Join-Path $evidence 'build-result.json') -Algorithm SHA256).Hash -ne $recordHash){throw 'Copied build evidence differs from the verified record.'}
$sourceRecords=@($record.frozen_inputs.sources)+@($record.frozen_inputs.client_resources)
& (Join-Path $PSScriptRoot 'copy-tested-sources.ps1') -ProjectRoot $projectRoot -StageRoot $stage -SourceRecords $sourceRecords
$files=@()
foreach($file in Get-ChildItem -LiteralPath $stage -Recurse -File | Sort-Object FullName) {
    $path=$file.FullName.Substring($stage.Length+1).Replace('\','/')
    if($path -match '(^/|\\|:|(^|/)\.\.?(/|$))'){throw "Unsafe candidate path: $path"}
    $category='documentation';$module=$null;$layer=$null
    if($path.StartsWith('modules/')){
        if($path -match '^modules/([^/]+)/(.+)$'){$module=$Matches[1]}
        if($module -notin $modules){throw "Unexpected runtime module: $path"}
        if($path -match '_((public|private|client))\.jar$'){$category='module_jar';$layer=$Matches[1]}
        elseif($path -match '/META-INF/qd_esign[^/]*\.upm$'){$category='module_registration'}
        else{throw "Unexpected runtime module resource: $path"}
    } elseif($path.StartsWith('config/')){$category='config'}
    elseif($path.StartsWith('sql/')){$category='sql'}
    elseif($path.StartsWith('examples/')){$category='example_config'}
    elseif($path.StartsWith('evidence/')){$category='build_evidence'}
    elseif($path.StartsWith('sources/')){$category='tested_source'}
    $item=[ordered]@{path=$path;size=[long]$file.Length;sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash;category=$category}
    if($module){$item['module']=$module};if($layer){$item['layer']=$layer};$files+=$item
}
$releaseId='qd-esign-candidate-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$manifest=[ordered]@{schema_version=1;release_id=$releaseId;created_at_utc=[DateTime]::UtcNow.ToString('o');nc_home=$record.nc_home;jdk=[ordered]@{java_version=$record.frozen_inputs.toolchain.java_version;javac_version=$record.frozen_inputs.toolchain.javac_version;java_sha256=$record.frozen_inputs.toolchain.java.sha256;javac_sha256=$record.frozen_inputs.toolchain.javac.sha256};build_record_sha256=$recordHash;modules=$modules;files=$files;jar_entries=$jarEntries}
$manifestFile=Join-Path $stage 'release-manifest.json'
[IO.File]::WriteAllText($manifestFile,($manifest | ConvertTo-Json -Depth 100),$utf8)
# Repeat verification after copying so concurrent edits cannot create a mixed candidate.
$null=& (Join-Path $PSScriptRoot 'build.ps1') -NcHome $NcHome -VerifyRecord
if((Get-FileHash -LiteralPath $recordPath -Algorithm SHA256).Hash -ne $recordHash){throw 'Build record changed during packaging.'}
$pending=Join-Path $buildRoot ('package-pending-'+[Guid]::NewGuid().ToString('N')+'.zip')
$output=Join-Path $buildRoot ($releaseId+'.zip')
try {
    $destinationStream=[IO.File]::Open($pending,[IO.FileMode]::CreateNew)
    try {
        $destinationArchive=New-Object IO.Compression.ZipArchive($destinationStream,[IO.Compression.ZipArchiveMode]::Create,$true)
        try {
            foreach($file in Get-ChildItem -LiteralPath $stage -Recurse -File | Sort-Object FullName){
                $entryPath=$file.FullName.Substring($stage.Length+1).Replace('\','/')
                [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($destinationArchive,$file.FullName,$entryPath) | Out-Null
            }
        } finally{$destinationArchive.Dispose()}
    } finally{$destinationStream.Dispose()}
    $zip=[IO.Compression.ZipFile]::OpenRead($pending)
    $sha=[Security.Cryptography.SHA256]::Create()
    try {
        $expected=@{};foreach($item in $files){if($expected.ContainsKey($item.path)){throw "Duplicate manifest path: $($item.path)"};$expected[$item.path]=$item.sha256}
        $expected['release-manifest.json']=(Get-FileHash -LiteralPath $manifestFile -Algorithm SHA256).Hash
        $seen=New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
        foreach($entry in $zip.Entries){
            $path=$entry.FullName.Replace('\','/')
            if(!$seen.Add($path) -or !$expected.ContainsKey($path)){throw "Unexpected or duplicate ZIP path: $path"}
            $input=$entry.Open();try{$hash=[BitConverter]::ToString($sha.ComputeHash($input)).Replace('-','')}finally{$input.Dispose()}
            if($hash -ne $expected[$path]){throw "ZIP content differs from manifest: $path"}
        }
        if($seen.Count -ne $expected.Count){throw 'ZIP manifest inventory does not match its contents.'}
    } finally{$sha.Dispose();$zip.Dispose()}
    Move-Item -LiteralPath $pending -Destination $output
} finally{if(Test-Path -LiteralPath $pending){Remove-Item -LiteralPath $pending -Force}}
Get-FileHash -LiteralPath $output -Algorithm SHA256
Write-Output "Candidate archive: $output. Release manifest included. No NC Home or database changes performed."
