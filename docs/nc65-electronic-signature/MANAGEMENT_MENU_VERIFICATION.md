# NC65 电子签名管理菜单验证

本清单用于齐都测试环境 QDNC。先确认客户端 JAR、菜单和试点角色齐备，再验证四个管理入口。业务单元选 `990 测试业务单元1`，本轮按只读权限验收。

后台注册可使用已按 QDNC 核对的脚本。请先审阅注册、授权范围和事务步骤。 [QDNC 菜单注册脚本](../../sql/nc65-electronic-signature/management-menu/README.md)

## 一、核对部署和注册值

1. 由维护人员在测试窗口备份并更新 `modules/uapbd/client/lib/qd_esign_uapbd_client.jar`。核对目标文件和交付的 SHA256 一致。重新登录时确认客户端连接的是 QDNC 对应的测试实例。现有策略和日志服务应能通过 NC 正常调用。 [环境与部署记录](ENVIRONMENT_EXECUTION.md)

   ```powershell
   Get-FileHash -Algorithm SHA256 -LiteralPath 'E:\01-Home\NC65home\QuDuTest\nchome\modules\uapbd\client\lib\qd_esign_uapbd_client.jar'
   ```
2. 打开「企业建模平台 → 开发配置 → 功能管理 → 功能注册」核对下表。四条均为普通功能，所属模块 `1014`，组织类型为「业务单元」。功能和按钮均启用按钮权限控制，`BeanConfigFilePath` 留空。

| 功能名称 | 功能编码 | 功能类名 | 按钮编码 |
|---|---|---|---|
| 电子签名策略 | `QD_ES_POLICY` | `nc.ui.uapbd.qd.esign.QdPolicyFunclet` | `QUERY`、`SAVE`、`MASTER` |
| 电子签名日志 | `QD_ES_SIGNLOG` | `nc.ui.uapbd.qd.esign.QdSignatureLogFunclet` | `QUERY`、`EXPORT` |
| 业务审计日志 | `QD_ES_AUDITLOG` | `nc.ui.uapbd.qd.esign.QdAuditLogFunclet` | `QUERY`、`EXPORT` |
| 登录与安全事件日志 | `QD_ES_SECURITY` | `nc.ui.uapbd.qd.esign.QdSecurityLogFunclet` | `QUERY`、`EXPORT`、`GROUP_SECURITY` |

3. 在「菜单注册」核对 `defaultMenu → 企业建模平台 → 电子签名管理（新版）」下四个叶子的关联功能编码。四个类名和编码要逐行对应。测试库现有 `13050QDES` 是 V1 的入口。
4. 在「功能注册 → 业务活动」核对每个新功能的 `QUERY` 活动。在「权限管理 → 职责管理 → 业务活动权限」核对新功能的控制状态。通过「职责-集团」「业务类角色」「用户权限分配」给试点账号分配四个查询活动。业务单元范围限定为 `990`，按钮与组织范围须在同一授权主体下生效。用「用户功能权限查询」确认该账号在新版节点具备 `QUERY`，没有 `SAVE`、`MASTER`、`EXPORT` 或 `GROUP_SECURITY`。

## 二、验证四个功能页签

1. 用试点账号重新登录 NC。点击「电子签名策略」，核对业务单元参照、查询、组织总控和结果表直接显示在页签内。
2. 查询后切换到其他页签再返回，核对原组织和查询结果。关闭策略页签后从菜单再次进入，核对可重新选择组织查询。
3. 依次进入电子签名日志、业务审计日志、登录与安全事件日志。核对筛选项、查询结果、分页和导出直接显示在对应页签内。每个日志页签首次显示时自动查询最近 24 小时。
4. 在日志页签之间切换，核对各自的筛选和当前页。选择有结果的记录，按类别核对记录摘要、关联日志和审计明细入口。
5. 在业务审计页确认记录列表在上、可拖动的详情区在下；选择记录时下方自动加载「审计事件」「字段差异」「数据快照」。审计事件和字段差异表头显示中文，快照只显示字段名和字段值，多快照通过上方选择器切换。记录摘要及明细内容预览均应显示内容。
6. 在电子签名日志页核对 NC 日期时间、业务单元和用户参照，以及结果类型枚举；列表首列为业务单元名称，操作人、签名处理编号等表头为中文。签名页保留「关联审计」，不显示「关联签名」和「审计明细」；业务审计页保留关联审计入口。
7. 在策略页输入功能节点名称或节点编码进行模糊筛选；修改两条策略后隐藏其中一条，核对待保存及隐藏数量，清空筛选后两条修改仍在，保存时提交全部待保存修改。

## 三、验证查询和组织隔离

1. 在「电子签名策略」用业务单元参照选择 `990 测试业务单元1`，点「查询」。应显示组织总控和全部 243 项按钮策略。未映射或未验证的行只读。当前仅有 `QUERY` 权限的试点账号应无法保存组织总控或策略。记录实际状态，管理菜单验收期间保持组织总控关闭。
2. 对照同账号的 NC 标准业务单元参照，核对树、搜索、编码、名称和单选。选择参照可见但缺少策略权限的业务单元，确认查询被拒绝且旧结果已清除。由测试维护人员核对服务端拒绝越权保存策略和保存总控。逐行保存、并发和授权版本验收见 [组织策略验证](ORGANIZATION_POLICY.md#qdnc-现场验收)。
3. 在三个日志页签分别查询。筛选时间输入北京时间，结果表的时间列注明 UTC。核对类别和查询状态。有记录时核对业务单元范围，空结果时记录查询成功和零条。仅授予 `QUERY` 时，集团级无业务单元安全事件仍受 `GROUP_SECURITY` 控制。
4. 选择任意一个日志页签，完成一次查询后点「导出筛选结果」，在测试目录选择一个新的 CSV 文件名。只读账号预期收到服务端的 `EXPORT` 权限拒绝，测试目录没有生成 CSV。用「导出筛选结果」可以在查询结果为零条时验证导出授权。
5. 在业务审计页用 NC 日期时间、业务单元和操作用户参照组合查询；无组织选择时按授权范围查询。用 `990`、`yonyou02` 和 `T-2026-11-09-02` 核对审计事件、十条字段差异及保存后快照。选择未授权业务单元时应拒绝并清空旧结果。分页与两种导出沿用已查询的组织和用户条件，越权导出应被拒绝。
6. 在签名日志页以 `990` 和 `yonyou02` 筛选，分别选择「签名成功」和「已取消」：核对 `T-2026-11-09-02`、`T-2026-11-09-03`；记录业务单元名称、请求号及签名处理编号。核对未授权业务单元被拒绝，分页和两种导出沿用同一筛选。签名成功不代表业务保存成功。

## 四、后台注册后的只读核对

在 QDNC 执行下面两条查询。第一条应返回四个不同的功能编码和十条按钮明细。功能和按钮启用、按钮权限列均应为 `Y`，所属模块为 `1014`，组织类型为 `BUSINESSUNIT00000000`。第二条应返回四个菜单叶子，父菜单名称为「电子签名管理（新版）」。逐行对照第一节的编码和类名。

```sql
SELECT f.funcode, f.class_name, f.own_module, f.orgtypecode,
       f.isenable AS function_enabled, f.isbuttonpower AS function_button_power,
       b.btncode, b.isenable AS button_enabled, b.isbuttonpower
  FROM QDNC.sm_funcregister f
  LEFT JOIN QDNC.sm_butnregister b
    ON b.parent_id = f.cfunid AND NVL(b.dr, 0) = 0
 WHERE NVL(f.dr, 0) = 0
   AND f.funcode IN ('QD_ES_POLICY', 'QD_ES_SIGNLOG', 'QD_ES_AUDITLOG', 'QD_ES_SECURITY')
 ORDER BY f.funcode, b.btncode;

SELECT m.menucode, p.menuitemname AS parent_name,
       i.menuitemcode, i.menuitemname, i.funcode
  FROM QDNC.sm_menuitemreg i
  JOIN QDNC.sm_menuregister m ON m.pk_menu = i.pk_menu AND NVL(m.dr, 0) = 0
  LEFT JOIN QDNC.sm_menuitemreg p
    ON p.pk_menu = i.pk_menu
   AND p.menuitemcode = SUBSTR(i.menuitemcode, 1, LENGTH(i.menuitemcode) - 2)
   AND NVL(p.dr, 0) = 0 AND p.ismenutype = 'Y'
 WHERE NVL(i.dr, 0) = 0 AND i.ismenutype = 'N' AND m.menucode = 'defaultMenu'
   AND i.funcode IN ('QD_ES_POLICY', 'QD_ES_SIGNLOG', 'QD_ES_AUDITLOG', 'QD_ES_SECURITY')
 ORDER BY i.menuitemcode;
```

## 五、定位失败并记录结果

| 现象 | 核对项 |
|---|---|
| 找不到新版菜单 | 核对 `defaultMenu` 的叶子关联、功能启用状态及测试账号的职责。授权后重新登录。 |
| 点击提示类找不到 | 核对已发布 JAR 的 SHA256、NC 加载的客户端版本以及完整功能类名。 |
| 提示功能编码不匹配 | 对照四行注册值修正功能编码与功能类名的对应关系。 |
| 菜单能打开，查询报权限不足 | 核对 `QUERY` 按钮注册、业务活动及同一角色下的 `990` 授权。 |
| 服务调用返回 HTTP 500 | 记录节点编码、操作时间、客户端报错和对应服务端异常栈，服务恢复后重试该项。 |

按四个功能分别记录菜单可见、首次弹出、手工重开和查询结果。另记未授权业务单元读取及导出拒绝。离线构建结果和 NC 现场结果分别登记，业务保存弹窗另按试点步骤验收。 [物料新增保存试点](MATERIAL_SAVE_PILOT.md)
