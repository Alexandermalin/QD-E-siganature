<# Compile selected sources against a tested build and run selected Java tests. #>
param(
    [Parameter(Mandatory = $true)]
    [string[]]$Source,
    [string[]]$TestClass = @(),
    [ValidateSet('Platform', 'SapplyClient', 'Custom')]
    [string]$Profile = 'Platform',
    [string]$ClasspathFile,
    [string]$BaselineBuild,
    [string]$NcHome
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$buildRoot = Join-Path $projectRoot 'build'
$utf8 = New-Object Text.UTF8Encoding($false)
if ($ClasspathFile -and $Profile -ne 'Custom') {
    throw '-ClasspathFile requires -Profile Custom.'
}

function Get-BuildCandidate([string]$directory) {
    $recordPath = Join-Path $directory 'build-result.json'
    if (!(Test-Path -LiteralPath $recordPath -PathType Leaf)) { return }
    try {
        $record = Get-Content -LiteralPath $recordPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($record.schema_version -ne 2 -or $record.tests_passed -isnot [bool] -or !$record.tests_passed) { return }
        $completed = [DateTimeOffset]$record.completed_at_utc
        foreach ($layer in @('public', 'private', 'client', 'test')) {
            if (!(Test-Path -LiteralPath (Join-Path $directory "classes/$layer") -PathType Container)) { return }
        }
        [pscustomobject]@{ Directory = (Resolve-Path -LiteralPath $directory).Path; Completed = $completed; Record = $record }
    } catch { return }
}

if ($BaselineBuild) {
    $candidate = Get-BuildCandidate $BaselineBuild
    if (!$candidate) { throw "Baseline lacks a successful full test record or class layers: $BaselineBuild" }
} else {
    $candidates = @(Get-BuildCandidate $buildRoot)
    if (Test-Path -LiteralPath $buildRoot -PathType Container) {
        $candidates += @(Get-ChildItem -LiteralPath $buildRoot -Directory | ForEach-Object {
            Get-BuildCandidate (Join-Path $_.FullName 'build')
        })
    }
    $candidate = $candidates | Sort-Object Completed -Descending | Select-Object -First 1
    if (!$candidate) { throw 'No successful full test build was found. Run tools/build.ps1 -Test or supply -BaselineBuild.' }
}

$baseline = $candidate.Directory
$record = $candidate.Record
$baselineRecordPath = Join-Path $baseline 'build-result.json'
$baselineRecordHash = (Get-FileHash -LiteralPath $baselineRecordPath -Algorithm SHA256).Hash
if (!$NcHome) { $NcHome = [string]$record.nc_home }
$ncRoot = (Resolve-Path -LiteralPath $NcHome).Path.TrimEnd('\', '/')
$baselineNcRoot = [IO.Path]::GetFullPath([string]$record.nc_home).TrimEnd('\', '/')
if (!$ncRoot.Equals($baselineNcRoot, [StringComparison]::OrdinalIgnoreCase)) {
    Write-Warning 'The selected NC Home differs from the full-build baseline. Verify that the platform versions match.'
}
$javac = Join-Path $ncRoot 'ufjdk/bin/javac.exe'
$java = Join-Path $ncRoot 'ufjdk/bin/java.exe'
foreach ($tool in @($javac, $java)) {
    if (!(Test-Path -LiteralPath $tool -PathType Leaf)) { throw "Missing NC Java tool: $tool" }
}

$sourcePaths = New-Object 'System.Collections.Generic.List[string]'
foreach ($path in $Source) {
    $resolved = (Resolve-Path -LiteralPath $path).Path
    if (!(Test-Path -LiteralPath $resolved -PathType Leaf) -or !$resolved.EndsWith('.java', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Expected a Java source file: $path"
    }
    if (!$sourcePaths.Contains($resolved)) { $sourcePaths.Add($resolved) }
}
foreach ($name in $TestClass) {
    if ($name -notmatch '^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$') {
        throw "Invalid Java test class: $name"
    }
    $relative = $name.Replace('.', '/') + '.java'
    foreach ($root in @('tests/java', 'tests/persistence', 'baseapp/src/test')) {
        $testSource = Join-Path $projectRoot "$root/$relative"
        if (Test-Path -LiteralPath $testSource -PathType Leaf) {
            $resolved = (Resolve-Path -LiteralPath $testSource).Path
            if (!$sourcePaths.Contains($resolved)) { $sourcePaths.Add($resolved) }
            break
        }
    }
}
$sourceHashes = @{}
foreach ($path in $sourcePaths) { $sourceHashes[$path] = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }
$baselineChanges = New-Object 'System.Collections.Generic.List[string]'
foreach ($entry in $record.frozen_inputs.sources) {
    $path = [IO.Path]::GetFullPath((Join-Path $projectRoot ([string]$entry.path)))
    if ($sourcePaths.Contains($path)) { continue }
    if (!(Test-Path -LiteralPath $path -PathType Leaf) -or
            (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.sha256) {
        $baselineChanges.Add([string]$entry.path)
    }
}
if ($baselineChanges.Count -gt 0) {
    Write-Warning ("{0} unselected sources differ from the baseline. Include affected sources: {1}" -f
        $baselineChanges.Count, (($baselineChanges | Select-Object -First 8) -join ', '))
}

$classpath = New-Object 'System.Collections.Generic.List[string]'
foreach ($layer in @('public', 'private', 'client', 'test')) {
    $classpath.Add((Resolve-Path -LiteralPath (Join-Path $baseline "classes/$layer")).Path)
}
$platformPaths = if ($Profile -eq 'SapplyClient' -or $Profile -eq 'Custom') {
    $listFile = if ($Profile -eq 'SapplyClient') {
        Join-Path $PSScriptRoot 'targeted-classpaths/sapply-client.txt'
    } else {
        if (!$ClasspathFile) { throw 'Custom profile requires -ClasspathFile.' }
        $ClasspathFile
    }
    @(Get-Content -LiteralPath $listFile -Encoding UTF8 |
        Where-Object { $_.Trim() -and !$_.Trim().StartsWith('#') } | ForEach-Object {
            $path = $_.Trim()
            if ([IO.Path]::IsPathRooted($path)) { $path } else { Join-Path $ncRoot $path }
        })
} else {
    @($record.frozen_inputs.classpath | ForEach-Object {
        $path = [IO.Path]::GetFullPath([string]$_)
        if ($path.Equals($baselineNcRoot, [StringComparison]::OrdinalIgnoreCase) -or
                $path.StartsWith($baselineNcRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
            $ncRoot + $path.Substring($baselineNcRoot.Length)
        } else { $path }
    })
}
foreach ($path in $platformPaths) {
    if (!(Test-Path -LiteralPath $path)) { throw "Missing classpath entry: $path" }
    if (!$classpath.Contains($path)) { $classpath.Add($path) }
}

$runId = (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$runDir = Join-Path $buildRoot "targeted/$runId"
$classesDir = Join-Path $runDir 'classes'
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

# Test classes may expose the project's standard run() entry point or a regular main().
$runnerSource = Join-Path $runDir 'QdTargetedTestRunner.java'
$runner = @'
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class QdTargetedTestRunner {
    public static void main(String[] args) throws Exception {
        for (String name : args) {
            Class<?> test = Class.forName(name);
            Method method;
            Object[] arguments;
            try {
                method = test.getMethod("run");
                arguments = new Object[0];
            } catch (NoSuchMethodException missingRun) {
                method = test.getMethod("main", String[].class);
                arguments = new Object[] { new String[0] };
            }
            if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != Void.TYPE) {
                throw new IllegalArgumentException(name + " must provide static void run() or main(String[])");
            }
            try {
                method.invoke(null, arguments);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw failure;
            }
            System.out.println("PASS targeted " + name);
        }
    }
}
'@
[IO.File]::WriteAllText($runnerSource, $runner, $utf8)
function Quote-JavacArgument([string]$value) { '"' + $value.Replace('\', '/') + '"' }
$argsFile = Join-Path $runDir 'javac.args'
$arguments = @('-encoding', 'UTF-8', '-source', '1.7', '-target', '1.7', '-g', '-proc:none',
    '-sourcepath', '""', '-classpath', (Quote-JavacArgument ($classpath -join ';')),
    '-d', (Quote-JavacArgument $classesDir))
$arguments += @($sourcePaths | ForEach-Object { Quote-JavacArgument $_ })
$arguments += Quote-JavacArgument $runnerSource
[IO.File]::WriteAllLines($argsFile, [string[]]$arguments, $utf8)

Write-Output "Baseline: $baseline ($($candidate.Completed.ToString('o')))"
Write-Output "Profile: $Profile; classpath entries: $($classpath.Count); selected Java sources: $($sourcePaths.Count)"
Write-Output "Output: $runDir"
$timer = [Diagnostics.Stopwatch]::StartNew()
$compileLog = Join-Path $runDir 'compile.log'
$previousErrorAction = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    & $javac ('@' + $argsFile) *> $compileLog
    $compileExitCode = $LASTEXITCODE
} finally { $ErrorActionPreference = $previousErrorAction }
if ($compileExitCode -ne 0) {
    Get-Content -LiteralPath $compileLog -Tail 30
    throw "Targeted compilation failed with exit code $compileExitCode. See $compileLog"
}
$compileSeconds = [math]::Round($timer.Elapsed.TotalSeconds, 2)
Write-Output ("Targeted compilation passed in {0:N2} seconds." -f $compileSeconds)
$compilerOutput = @(Get-Content -LiteralPath $compileLog -TotalCount 15)
if ($compilerOutput.Count -gt 0) {
    $compilerOutput | Write-Output
    Write-Output "Full compiler output: $compileLog"
}

$testSeconds = $null
if ($TestClass.Count -gt 0) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.IO.Compression
    $runtimePaths = @($classesDir) + @($classpath.ToArray())
    foreach ($module in @('baseapp', 'uapbd', 'pu', 'so', 'ic', 'purp')) {
        $resource = Join-Path $projectRoot "$module/src/client"
        if (Test-Path -LiteralPath $resource -PathType Container) { $runtimePaths += $resource }
    }
    $uris = foreach ($path in $runtimePaths) {
        $full = [IO.Path]::GetFullPath($path)
        if (Test-Path -LiteralPath $full -PathType Container) { $full = $full.TrimEnd('\', '/') + '\' }
        ([Uri]$full).AbsoluteUri
    }
    $line = 'Class-Path: ' + ($uris -join ' ')
    $manifest = "Manifest-Version: 1.0`r`n"
    while ($line.Length -gt 70) { $manifest += $line.Substring(0, 70) + "`r`n"; $line = ' ' + $line.Substring(70) }
    $manifest += $line + "`r`n`r`n"
    $manifestJar = Join-Path $runDir 'runtime-classpath.jar'
    $archive = [IO.Compression.ZipFile]::Open($manifestJar, [IO.Compression.ZipArchiveMode]::Create)
    try {
        $writer = New-Object IO.StreamWriter($archive.CreateEntry('META-INF/MANIFEST.MF').Open(), [Text.Encoding]::ASCII)
        try { $writer.Write($manifest) } finally { $writer.Dispose() }
    } finally { $archive.Dispose() }
    $timer.Restart()
    $testLog = Join-Path $runDir 'test.log'
    $previousErrorAction = $ErrorActionPreference
    Push-Location $runDir
    try {
        $ErrorActionPreference = 'Continue'
        & $java -ea '-Djava.awt.headless=true' "-Djava.io.tmpdir=$runDir" "-Duser.home=$runDir" -classpath $manifestJar QdTargetedTestRunner @TestClass *> $testLog
        $testExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
        Pop-Location
    }
    if ($testExitCode -ne 0) {
        Get-Content -LiteralPath $testLog -Tail 30
        throw "Targeted tests failed with exit code $testExitCode. See $testLog"
    }
    $passes = @(Select-String -LiteralPath $testLog -Pattern '^PASS ' | Select-Object -First 30)
    foreach ($pass in $passes) { Write-Output $pass.Line }
    $testSeconds = [math]::Round($timer.Elapsed.TotalSeconds, 2)
    Write-Output ("Targeted tests passed in {0:N2} seconds." -f $testSeconds)
    Write-Output "Full test output: $testLog"
} else {
    Write-Output 'Compilation only; no test classes were requested.'
}
foreach ($path in $sourcePaths) {
    if (!(Test-Path -LiteralPath $path -PathType Leaf) -or
            (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $sourceHashes[$path]) {
        throw "Source changed during targeted verification: $path"
    }
}
if (!(Test-Path -LiteralPath $baselineRecordPath -PathType Leaf) -or
        (Get-FileHash -LiteralPath $baselineRecordPath -Algorithm SHA256).Hash -ne $baselineRecordHash) {
    throw 'The full-build baseline changed during targeted verification.'
}
$result = [ordered]@{
    completed_at_utc = [DateTime]::UtcNow.ToString('o')
    baseline_build = $baseline
    baseline_record_sha256 = $baselineRecordHash
    nc_home = $ncRoot
    profile = $Profile
    classpath_entries = $classpath.Count
    sources = @($sourcePaths.ToArray() | ForEach-Object { [ordered]@{ path = $_; sha256 = $sourceHashes[$_] } })
    unselected_baseline_changes = @($baselineChanges.ToArray())
    test_classes = @($TestClass)
    tests_passed = if ($TestClass.Count -gt 0) { $true } else { $null }
    compile_seconds = $compileSeconds
    test_seconds = $testSeconds
}
[IO.File]::WriteAllText((Join-Path $runDir 'result.json'), ($result | ConvertTo-Json -Depth 5), $utf8)
Write-Output 'Targeted verification complete; the full build record is unchanged.'
