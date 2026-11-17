# 销售订单其他按钮 S1（单笔公共链路）交付记录

- 批次：**S1 单笔公共链路**（对应 `docs/nc65-electronic-signature/SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md` 第 47 行）
- 日期：2026-11-11
- 范围：`40060301` 第 209–216 行八项按钮的**公共接入链路**；首轮仍限 990 单笔普通订单；**保留既有 Save 试点**。
- 处置决策（本批已按项目负责人确认执行）：
  - 缺口 C 采用**复用现有 `ISaleOrderOperationAdapter`** 方案：不新增 upm 组件、不依赖 `NCLocator` 查找第二接口；`qd_esign_saleorder.upm` 维持 HOME 现状。
  - 本批**不启用任何新按钮**：只登记服务端固定白名单并关闭执行；冻结/解冻/关闭（S2）、删除/提交/收回（S3）、审批/取消审批（S4）逐动作接入后再打开。
- 边界：未部署、未执行 SQL、未启停共享 NC、未外发。真实 NC 联调未进行。

## 1. 新增 / 修改文件

| 层 | 文件（主线） | 990 兼容包副本 | 说明 |
| --- | --- | --- | --- |
| public | `uapbd/src/public/nc/vo/uapbd/qd/esign/SaleOrderPilotScope.java` | 有，已同步 | 扩为 9 个已核实范围 ID 白名单（208–216）；新增 `NODE_CODE/SAVE_BUTTON/buttonScopes/scopeIdFor/operationTypeFor/supportsOperation/supportsAction/buttonName`；删除归 `DELETE`、保存归 `INSERT`、其余归 `UPDATE` |
| public | `so/src/public/nc/vo/so/qd/esign/SaleOrderStateCommand.java` | **新增**，已同步 | 冻结/解冻/整单关闭的类型化命令：单据数组、关闭原因、ATP 业务检查标记；数组与 Map 副本隔离 |
| public | `so/src/public/nc/vo/so/qd/esign/SaleOrderPfActionCommand.java` | **新增**，已同步 | PF 动作（删除/提交/收回/审批/取消审批）类型化命令：单据、`PfUserObject`、`WorkflownoteVO`、PF 参数；副本隔离 |
| private | `so/src/private/nc/impl/so/qd/esign/SaleOrderActionRoute.java` | **新增**，已同步 | 服务端固定动作白名单：8 按钮 → `PF_DELETE/PF_SAVE/PF_UNSAVE/PF_APPROVE/PF_UNAPPROVE/STATE_FREEZE/STATE_UNFREEZE/STATE_CLOSE`；拒绝错节点、未登记按钮、错组织、错动作域、多选、子对象；`requireEnabled()` 对未联调动作在执行前拒绝 |
| private | `so/src/private/nc/impl/so/qd/esign/SaleOrderOperationAdapter.java` | 已同步 | `validate`/`execute` 先经固定路由；未启用动作直接拒绝，**不回落** `ISaleOrderScriptMaintain` 低层脚本；Save 分支保持原有 PF 保存委托 |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/SaleOrderActionHandoff.java` | **新增**，已同步 | 一次性授权交接：绑定一次、按真实按钮/组织/对象主键派发一次、结束释放；签名后换单/切组织/换按钮均拒绝 |
| policy | —（仅 990 包） | `sales-order-990-release/uapbd/src/private/nc/impl/uapbd/qd/esign/SaleOrderPilotPolicyService.java` | 白名单由 `40060301+Save` 扩为 `SaleOrderPilotScope` 的 9 个范围；非 990 开启任一销售订单范围拒绝；关闭操作不限制；其他节点/其他模块策略原样委托 |
| test | `tests/java/nc/test/qd/esign/SaleOrderOtherButtonsTests.java` | 已同步 | 169 项离线断言：范围白名单、动作域归类、固定路由、错组织/错对象/错动作/未知按钮拒绝、适配器零原生调用、Save 仍委托一次、类型化命令副本隔离 |
| test | `tests/java/nc/ui/uapbd/qd/esign/SaleOrderActionHandoffTests.java` | 已同步 | 45 项离线断言：8 按钮交接、Save 不接管、错组织/错动作/多选/子对象拒绝、一次性绑定与派发、按钮/组织/对象变化拒绝、释放语义 |
| test | `tests/java/nc/test/qd/esign/AllTests.java` | —（990 包不使用 AllTests） | 注册上述两个测试 |
| build | — | `sales-order-990-release/build.ps1` | 测试运行清单加入 `SaleOrderOtherButtonsTests`、`SaleOrderActionHandoffTests` |

> 未改动：`QdSaleOrderConfig.xml`（本批不新增覆盖 bean，S2–S4 逐动作接入时再改）、保存试点相关类、网关白名单、`MappingRules`、`OrderPhysicalSnapshots`、`qd_esign_saleorder.upm`。

## 2. 验证与构建记录（实际执行）

### 2.1 990 隔离销售构建（本批构建记录）

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1 -Verify
```

结果（退出码 0，237s；日志 `build/s1-build-990.log`）：

```text
PASS SaleOrderPfSaveAdapterTests assertions=26 (native PF, database, and transaction not invoked)
PASS SaleOrderPilotConfigTests (native XML and pinned PF client; no NC session)
PASS SaleOrderPilotPolicyServiceTests (pilot guard, button scopes and installed-service delegation)
PASS SaleOrderOtherButtonsTests assertions=169 (whitelist, route, typed commands and save delegation; no NC or database invoked)
PASS SaleOrderActionHandoffTests assertions=45 (offline handoff contract; no NC client or service invoked)
ISOLATED SALES BUILD PASSED. No deployment or database change.
Verified isolated build inputs and compiled classes.
```

- 构建记录：`build/sales-990-isolated/build-record.json`（`schema_version=1`、`tests_passed=true`、47 个编译类、冻结输入哈希 `0C33112E…`）。
- `-Verify` 通过 → 本批源码、资源、JDK 与依赖已冻结，且编译产物与记录一致。
- 该构建同时**解决了 S0 缺口 A**：旧 `build-record.json` 冻结的是空收款热修前的旧源码；本次已按当前源码重建并验证。
- 说明：`SaleOrderPfSaveAdapterTests assertions=26` 与 2026-11-11 空收款热修自称的 "26 assertions" 一致，说明 990 包测试副本本就覆盖空收款容器兼容修复；主线副本为较早版本，两者按原方案保持各自内容。

### 2.2 主工程整包构建（通过）

命令：`powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1 -Test`。

结果（退出码 0，297s；成功日志 `build/s1-build-main2.log`）：

```text
Compiling public (111 source files)
Compiling private (150 source files)
Compiling client (111 source files)
Compiling legacy-examples (6 source files)
Compiling test (120 source files)
PASS SaleOrderPilotConfigTests (native XML and pinned PF client; no NC session)
PASS SaleOrderActionHandoffTests assertions=45 (offline handoff contract; no NC client or service invoked)
PASS SaleOrderPfSaveAdapterTests assertions=26 (native PF, database, and transaction not invoked)
PASS SaleOrderOtherButtonsTests assertions=169 (whitelist, route, typed commands and save delegation; no NC or database invoked)
ALL OFFLINE TESTS PASSED (not NC/Oracle integration verification)
BUILD PASSED. Frozen input and class evidence recorded. No deployment performed.
```

- 构建记录：`build/build-result.json`（`schema_version=2`、`tests_passed=true`、958 个编译类、`completed_at_utc=2026-11-11T11:05:03Z`、`class_artifacts_sha256=A42E081D…`）。
- `tools\build.ps1 -VerifyRecord` 复核退出码 0 → 记录与当前源码/配置/依赖一致。
- 过程记录（如实保留）：首次执行（`build/s1-build-main.log`）编译全部通过，但在既有 `ArchiveTreeDeleteAdapterTests` 因 `CustClassVO.getMetaData()` 经 `NCLocator` 报 `invalid url: null` 中止（本机 NC 客户端元数据缓存缺失，环境相关）；第二次（`build/s1-build-main-retry.log`）全部 113 个 PASS 与 `ALL OFFLINE TESTS PASSED`，但构建末尾冻结校验报 `Inputs changed while building`（并行会话同期写入）；第三次 `build/s1-build-main2.log` 通过但随后并行会话在 17:28 修改 `config/qd-esign/inventory-integration-matrix.csv`、`tests/java/nc/bs/uapbd/qd/esign/audit/InventoryPhysicalSnapshotsTests.java`（17:33 又见 `QdPartnerBatchChunks.java`），导致记录与工作区漂移；**第四次（2026-11-11 19:05，日志 `build/s1-build-main3.log`）在并行写入停止后重跑通过**，记录已重新冻结并通过 `-VerifyRecord`。上述过程失败均与本批代码无关。
- 残余风险：本工程目录有多个会话并行使用；任何会话在构建后修改源码/配置都会使 `-VerifyRecord` 漂移，候选发布前必须串行化写入窗口并重跑构建。

## 3. 本批证明与未证明

**已证明（离线）**：
- 8 个按钮的服务端固定路由与动作域归类正确；错节点/未登记按钮/错组织/错动作/多选/子对象/缺主键全部在执行前拒绝。
- 未通过联调的动作在 `validate` 与 `execute` 均拒绝，**不产生任何原生调用**（测试桩断言调用次数为 0）。
- 既有 Save 试点仍委托固定 PF 保存适配器，且每次调用一次（回归）。
- 客户端一次性授权交接的绑定/派发/释放与变化拒绝语义。
- 990 策略包装器把 9 个销售范围纳入保护，非 990 开启拒绝、关闭放行、其他节点不受影响。

**未证明（需 S2–S4 与真实联调）**：
- 冻结/解冻/整单关闭、PF 五个动作的实际原生写入与返回结果、ATP 继续/取消、关闭原因落库、审计差异与同事务回滚。
- 审批窗口「确定」后的流程参数与通知时序。
- 真实 NC 会话加载的类、按钮显隐、组织与权限校验、多选。
- `package.ps1` 的 upm 派生逻辑仍假设 HOME 为 `PolicyServiceImpl`（当前 HOME 已是 `SaleOrderPilotPolicyService`，`count≠1` 会 throw）；本批未改打包器，留待 S6 按"已正确绑定则保留、未知状态中止"修正。

## 4. 下一步（S2）

1. 在 `SaleOrderActionRoute` 内为 `STATE_FREEZE/STATE_UNFREEZE/STATE_CLOSE` 增加固定分支：`SaleOrderStateCommand` → `ISaleOrderCloseManageMaintain.freezeSaleOrder/unFreezeSaleOrder(Map)/closeSaleOrder`，保留客户端 ATP 决策与关闭原因/整单行。
2. 逐动作把 `ENABLED` 打开（只打开已实现并通过离线用例的动作），并补审计差异（删除关联明细、关闭前后表头表体与 `vclosereason`）。
3. 每项补成功/取消/原生拒绝/审计故障用例后，才允许进入真实 NC 单笔联调。
