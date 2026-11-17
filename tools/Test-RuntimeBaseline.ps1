param(
    [Parameter(Mandatory = $true)]
    [string]$NcHome
)

$ErrorActionPreference = 'Stop'
$ncRoot = (Resolve-Path -LiteralPath $NcHome).Path
$moduleRoot = Join-Path $ncRoot 'modules'
$manifestPath = Join-Path $PSScriptRoot '../config/runtime-baseline.json'
$manifest = Get-Content -Raw -Encoding utf8 -LiteralPath $manifestPath | ConvertFrom-Json
if ($manifest.SchemaVersion -ne 1 -or @($manifest.Files).Count -eq 0) {
    throw '运行基线清单缺失或格式不正确。'
}

$expected = @{}
$differences = New-Object 'System.Collections.Generic.List[string]'
foreach ($entry in $manifest.Files) {
    $relative = [string]$entry.Path
    if ($relative -notmatch '^modules/(?:[A-Za-z0-9_-]+/)+qd_esign[A-Za-z0-9_-]*\.(jar|upm)$' -or
        $entry.Sha256 -notmatch '^[A-Fa-f0-9]{64}$' -or $expected.ContainsKey($relative)) {
        throw "运行基线清单含重复或无效条目 $relative"
    }
    $expected[$relative] = $entry
    $path = Join-Path $ncRoot $relative
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) {
        $differences.Add("缺少 $relative")
        continue
    }
    $file = Get-Item -LiteralPath $path
    if ($file.Length -ne $entry.Size -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.Sha256) {
        $differences.Add("版本不符 $relative")
    }
}

$rootPrefixLength = $ncRoot.TrimEnd('\','/').Length + 1
foreach ($file in Get-ChildItem -LiteralPath $moduleRoot -Recurse -File -Filter 'qd_esign*') {
    if ($file.Extension -notin @('.jar', '.upm')) {
        continue
    }
    $relative = $file.FullName.Substring($rootPrefixLength).Replace('\','/')
    if (!$expected.ContainsKey($relative)) {
        $differences.Add("存在清单外构件 $relative")
    }
}

if ($differences.Count) {
    throw ("运行基线 {0} 核对失败`n{1}" -f $manifest.Baseline, ($differences -join "`n"))
}
Write-Output "PASS $($manifest.Baseline)，$($expected.Count) 个运行构件的路径和 SHA-256 一致。"
