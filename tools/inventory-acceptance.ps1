<# Prepare and check inventory acceptance evidence; no NC or database connection. #>
param(
    [ValidateSet('Prepare','Check')][string]$Mode = 'Check',
    [string]$File = (Join-Path (Split-Path -Parent $PSScriptRoot) 'docs\nc65-electronic-signature\inventory-acceptance-evidence.csv'),
    [string]$Matrix = (Join-Path (Split-Path -Parent $PSScriptRoot) 'config\qd-esign\inventory-integration-matrix.csv')
)
$ErrorActionPreference = 'Stop'

# These are evidence categories, not assertions about native NC behavior.
$cases = [ordered]@{
    BOTH_OFF = '签名关闭、审计关闭；核对原生业务结果。'
    AUDIT_ONLY = '签名关闭、审计开启；核对业务与审计。'
    SIGN_ONLY = '签名开启、审计关闭；核对签名与业务。'
    BOTH_ON = '签名开启、审计开启；核对签名、业务与审计关联。'
    CANCEL = '取消签名；核对单据与库存未发生受控业务变更。'
    BAD_PASSWORD = '密码错误；核对业务未执行及认证结果。'
    NO_PERMISSION = '按钮或组织越权；核对拒绝及业务未执行。'
    DUPLICATE = '重复提交；核对业务不重复执行及请求结果。'
    BATCH_PARTIAL = '支持批量时一次验密，逐单核对成功、失败及对应审计。'
    AUDIT_FAILURE = '注入审计写入失败；核对单据、库存、生成单回滚及独立签名保留。'
}

function Stop-Invalid([string]$Message) { throw $Message }
function Has-Text($Value) { return $null -ne $Value -and -not [string]::IsNullOrWhiteSpace([string]$Value) }

$scopes = @(Import-Csv -LiteralPath $Matrix -Encoding UTF8)
$scopeMap = @{}
foreach ($scope in $scopes) {
    if (-not (Has-Text $scope.scopeId) -or -not (Has-Text $scope.nodeCode) -or $scopeMap.ContainsKey($scope.scopeId)) {
        Stop-Invalid '范围矩阵存在缺失或重复的 scopeId / 节点编码。'
    }
    $scopeMap[$scope.scopeId] = $scope
}
if ($scopes.Count -ne 83 -or @($scopes.nodeCode | Sort-Object -Unique).Count -ne 18) {
    Stop-Invalid '范围矩阵必须包含 18 个节点和 83 个唯一 scopeId。'
}

if ($Mode -eq 'Prepare') {
    if (Test-Path -LiteralPath $File) { Stop-Invalid '证据文件已存在，拒绝覆盖。' }
    $rows = foreach ($scope in $scopes) {
        foreach ($caseId in $cases.Keys) {
            [pscustomobject][ordered]@{
                scopeId=$scope.scopeId; nodeCode=$scope.nodeCode; nodeName=$scope.nodeName
                buttonName=$scope.buttonName; caseId=$caseId; expectedResult=$cases[$caseId]
                status='NOT_RUN'; pkGroup=''; pkOrg=''; userCode=''; requestIds=''
                objectPks=''; billCodes=''; signEvidence=''; auditEvidence=''
                businessEvidence=''; stockEvidence=''; generatedBillEvidence=''
                resultEvidence=''; actualResult=''; skipReason=''; executedAt=''
            }
        }
    }
    $parent = Split-Path -Parent ([IO.Path]::GetFullPath($File))
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) { Stop-Invalid '证据文件的父目录不存在。' }
    $rows | Export-Csv -LiteralPath $File -NoTypeInformation -Encoding utf8BOM -NoClobber
    Write-Output "已建立 $($rows.Count) 条未执行记录；没有连接 NC 或数据库：$File"
    exit 0
}

$rows = @(Import-Csv -LiteralPath $File -Encoding UTF8)
$columns = @('scopeId','nodeCode','nodeName','buttonName','caseId','expectedResult','status','pkGroup','pkOrg',
    'userCode','requestIds','objectPks','billCodes','signEvidence','auditEvidence','businessEvidence','stockEvidence',
    'generatedBillEvidence','resultEvidence','actualResult','skipReason','executedAt')
if ($rows.Count -eq 0) { Stop-Invalid '证据文件为空。' }
foreach ($column in $columns) {
    if (-not ($rows[0].PSObject.Properties.Name -contains $column)) { Stop-Invalid "证据文件缺少列：$column" }
}
$seen = @{}
$errors = New-Object 'System.Collections.Generic.List[string]'
$counts = @{NOT_RUN=0; PASS=0; FAIL=0; NOT_APPLICABLE=0}
foreach ($row in $rows) {
    $key = "$($row.scopeId)|$($row.caseId)"
    if (-not (Has-Text $row.scopeId) -or -not (Has-Text $row.caseId) -or
            -not $scopeMap.ContainsKey($row.scopeId) -or -not $cases.Contains($row.caseId)) {
        $errors.Add("未知范围或用例：$key"); continue
    }
    if ($seen.ContainsKey($key)) { $errors.Add("重复记录：$key"); continue }
    $seen[$key] = $true
    $scope = $scopeMap[$row.scopeId]
    if ($row.nodeCode -cne $scope.nodeCode -or $row.nodeName -cne $scope.nodeName -or $row.buttonName -cne $scope.buttonName) {
        $errors.Add("范围名称或节点编码不一致：$key")
    }
    if ($row.expectedResult -cne $cases[$row.caseId]) { $errors.Add("预期结果被修改：$key") }
    if (-not $counts.ContainsKey($row.status)) { $errors.Add("未知状态：$key"); continue }
    $counts[$row.status]++
    if ($row.status -eq 'NOT_RUN') { continue }
    if ($row.status -eq 'NOT_APPLICABLE') {
        if ($row.caseId -ne 'BATCH_PARTIAL' -or -not (Has-Text $row.skipReason) -or -not (Has-Text $row.resultEvidence)) {
            $errors.Add("仅不支持批量的动作可标记不适用，须提供原因和证据：$key")
        }
        continue
    }
    foreach ($field in @('pkGroup','pkOrg','userCode','actualResult','resultEvidence','executedAt')) {
        if (-not (Has-Text $row.$field)) { $errors.Add("已执行记录缺少 $field：$key") }
    }
    $parsedTime = [DateTimeOffset]::MinValue
    if ($row.executedAt -notmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(Z|[+-]\d{2}:\d{2})$' -or
            -not [DateTimeOffset]::TryParse($row.executedAt, [ref]$parsedTime)) {
        $errors.Add("执行时间须包含时区，例如 2026-11-12T09:00:00+08:00：$key")
    }
    if ($row.status -ne 'PASS') { continue }
    $required = @('businessEvidence')
    if ($row.caseId -in @('BOTH_OFF','AUDIT_ONLY','SIGN_ONLY','BOTH_ON')) { $required += 'objectPks','billCodes' }
    if ($row.caseId -in @('AUDIT_ONLY','SIGN_ONLY','BOTH_ON','DUPLICATE','BATCH_PARTIAL','AUDIT_FAILURE')) { $required += 'requestIds' }
    if ($row.caseId -in @('SIGN_ONLY','BOTH_ON','BATCH_PARTIAL','AUDIT_FAILURE')) { $required += 'signEvidence' }
    if ($row.caseId -in @('AUDIT_ONLY','BOTH_ON','BATCH_PARTIAL','AUDIT_FAILURE')) { $required += 'auditEvidence' }
    if ($row.caseId -eq 'AUDIT_FAILURE') { $required += 'stockEvidence','generatedBillEvidence' }
    foreach ($field in $required) {
        if (-not (Has-Text $row.$field)) { $errors.Add("通过记录缺少 $field：$key") }
    }
    if ($row.caseId -eq 'BATCH_PARTIAL') {
        $pks = @($row.objectPks -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Sort-Object -Unique)
        if ($pks.Count -lt 2) { $errors.Add("批量部分成功须以分号记录至少两个不同对象主键：$key") }
    }
}
foreach ($scopeId in $scopeMap.Keys) {
    foreach ($caseId in $cases.Keys) {
        if (-not $seen.ContainsKey("$scopeId|$caseId")) { $errors.Add("缺少记录：$scopeId|$caseId") }
    }
}
if ($errors.Count -gt 0) {
    $errors | Select-Object -First 30 | ForEach-Object { Write-Error -ErrorAction Continue $_ }
    Write-Output "证据记录有 $($errors.Count) 处结构或必填项错误。"
    exit 1
}
Write-Output "用例记录：未执行=$($counts.NOT_RUN)，通过=$($counts.PASS)，失败=$($counts.FAIL)，不适用=$($counts.NOT_APPLICABLE)。"
if ($counts.NOT_RUN -gt 0 -or $counts.FAIL -gt 0) { Write-Output '尚不能进行完整验收。'; exit 2 }
Write-Output '记录字段已填写；仍须人工核验现场证据和业务验收，不等于自动验收通过。'
exit 0
