# 销售订单其他按钮 S5（批量与现场分支）交付记录

- 批次：**S5 批量和现场分支**（对应 `docs/nc65-electronic-signature/SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md` 第 51 行）
- 日期：2026-11-12（承接 2026-11-11 的 S1–S4）
- 范围：`40060301` 八项按钮的多选/批量语义取证与受控防线；首轮仍限 990 单笔业务验收。
- 本批结论：
  - **静态取证确定各按钮原生选择能力**（见 §2 矩阵）：删除、提交、收回、审批、取消审批、整单关闭原生均存在 `getSelectedOperaDatas()` 多选入口（列表工具栏均可多选）；冻结、解冻只使用 `getSelectedData()`，原生仅单笔。
  - **批量受控路径保持关闭**：原生批量语义（`MultiBillScriptRunner`/`runBatchNew`/`processBatch` 的提交边界、部分成功与逐对象回执）无法在离线阶段核实；按计划"无可靠逐对象结果时保持该入口的批量受控路径关闭"，本批**不实现**批量写入，只保证多选在启用按钮策略后不能回落原生写入。
  - **新增"选择范围判定"并失败关闭**：`SaleOrderSelection` 把选择分为 `CONTROLLED_SINGLE / CONTROLLED_MULTI / NATIVE / REJECT_MIXED / REJECT_UNKNOWN`。提交、收回、审批、取消审批的覆盖层改为：**混合组织或无法判定归属一律拒绝**（此前会回落原生写入，存在绕过签名风险）；全部首轮组织多选时进入受控路径，由单对象交接/适配器约束拒绝并提示"多选尚未适配"；全部非首轮组织保持原生行为。
  - 整单关闭此前已显式拒绝多选与混合组织；冻结/解冻原生仅单笔。
- 边界：未部署、未执行 SQL、未启停 NC、未外发。**两张以上订单的真实多选联调未进行**，S5 的"全成功/原生部分成功/逐对象回执"验收项未通过，批量写入仍关闭。

## 1. 新增 / 修改文件

| 层 | 文件 | 990 兼容包副本 | 说明 |
| --- | --- | --- | --- |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/SaleOrderSelection.java`（新） | 有，已同步 | 选择范围判定与拒绝提示；混合组织/归属不明必须拒绝 |
| client | `QdSaleOrderSendApproveAction.java` / `QdSaleOrderUnSendApproveAction.java` | 有，已同步 | 用统一判定替换"任一非首轮即原生"逻辑，混合组织失败关闭 |
| client | `QdSaleOrderApproveAction.java` / `QdSaleOrderUnApproveAction.java` | 有，已同步 | 同上；受控多选在 `procFlow` 显式拒绝并提示单选 |
| test | `tests/java/nc/ui/uapbd/qd/esign/SaleOrderSelectionTests.java`（新） | 有，已同步 | 15 断言：单笔/多选/全部非试点/混合/缺失/空组织/集团缺失与拒绝提示 |
| build | `tests/java/nc/test/qd/esign/AllTests.java` | ——（990 用运行清单） | 注册选择判定测试 |
| build | `sales-order-990-release/build.ps1` | —— | 测试运行清单加入 `SaleOrderSelectionTests` |

未改动：删除客户端（仍关闭）、各动作服务端分支、签名拦截器、handoff、保存试点、网关白名单、`OrderPhysicalSnapshots`、`qd_esign_saleorder.upm`、`package.ps1`。

## 2. 原生多选能力取证矩阵（静态）

依据：`s0-evidence/javap/nc.ui.so.m30.billui.action.*.txt` 中 `getSelectedOperaDatas()`/`getSelectedData()` 的引用计数；`uiso_salesorder.jar!nc/ui/so/m30/billui/model/saleorder_config.xml` 的 `actionsOfList`/`actionsOfCard` 绑定（deleteAction、sendActionGroup、approveActionGroup、browAstActionGroup 均在列表和卡片工具栏）。

| 按钮 | 原生选择 API 证据 | 原生多选 | 受控路径当前行为 |
| --- | --- | --- | --- |
| Delete | `getSelectedOperaDatas`=1 / `getSelectedData`=2 | 是 | 客户端与策略关闭（确认顺序未解决），多选走原生但**策略不可开启** |
| Commit | 1 / 2 | 是 | 策略开启时多选被单对象交接拒绝（不回落原生）；策略关闭时原生多选 |
| UnCommit | 1 / 2 | 是 | 同上 |
| Approve | 1 / 1 | 是 | 受控路径**显式拒绝多选**（单选提示），不回落原生 |
| UnApprove | 1 / 1 | 是 | 同上 |
| Freeze | 0 / 3 | 否（仅选中单张） | 原生单笔；受控单笔 |
| UnFreeze | 0 / 3 | 否 | 原生单笔；策略关闭未启用 |
| BillClose | 3 / 2 | 是 | 受控路径显式拒绝多选；混合组织拒绝 |
| Save | 单笔新增 | 否 | 既有试点单笔 |

## 3. 验证与构建记录（实际执行）

### 3.1 990 隔离销售构建（本批权威构建记录）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1
```

结果（退出码 0，日志 `build/s5-build-990.log`）：

```text
PASS SaleOrderPfSaveAdapterTests assertions=26
PASS SaleOrderPilotConfigTests
PASS SaleOrderPilotPolicyServiceTests
PASS SaleOrderOtherButtonsTests assertions=162
PASS SaleOrderActionHandoffTests assertions=45
PASS SaleOrderSelectionTests assertions=15
PASS SaleOrderStateAdapterTests assertions=49
PASS SaleOrderPfActionAdapterTests assertions=48
ISOLATED SALES BUILD PASSED. No deployment or database change.
```

- 构建记录：`build/sales-990-isolated/build-record.json`（`tests_passed=true`、131 个编译类、冻结输入 `E7F51322…`、`09:33:03Z`）。

### 3.2 主工程整包构建（本轮未产出绿色记录）

- 日志 `build/s5-build-main.log`：全层编译通过，`ALL OFFLINE TESTS PASSED`（含本批 `SaleOrderSelectionTests` 15 断言及其余销售测试）；随后冻结校验报 `Inputs changed while building`。
- 原因：另一并行会话在 2026-11-12 09:36–09:41 仍在修改 `SignatureServiceImpl`、`NativeOrgBatch*`、`Sapply*`、`InventoryIntegrationMatrix`、`AllTests` 等文件，属共享工程并发写入。
- 主工程整包记录待并行会话停止写入后重跑（`UNRESOLVED_ISSUES.md` U51/U57/U61）。

## 4. S5 验收项对照

| 验收要求 | 本批证据 | 状态 |
| --- | --- | --- |
| 核实原生原子批次或逐单处理语义 | 仅静态确认 `MultiBillScriptRunner`/`runBatchNew`/`processBatch` 路径存在；未核实提交边界、部分成功与逐对象结果 | **未通过（需联调）** |
| 一次认证绑定固定对象集、逐对象记录签名与真实结果 | 未实现批量受控命令与逐对象回执；当前多选在策略开启时被拒绝 | 未开发（批量写入关闭） |
| 两张以上订单验证全成功/部分成功 | 未执行（无 NC） | 未通过 |
| 无可靠逐对象结果时批量入口关闭 | 多选在受控路径被拒绝；混合组织/归属不明一律拒绝 | **已落实** |
| 防止多选绕回原生写入 | `SaleOrderSelection` + 四个覆盖层失败关闭；删除策略禁止开启；关闭/审批多选显式拒绝 | **已落实（离线契约）** |

## 5. 已知限制与下一步

1. 批量受控写入未实现，多选在策略开启时被拒绝；现场若需多选必须先完成原生批量语义核实与逐对象事务/回执设计。
2. 混合组织失败关闭改变了"此前回落原生"的行为，属有意的安全收窄；需现场确认无合法混合组织批量操作需求。
3. 另需注意：并行会话在 2026-11-12 上午持续修改同工程（含 `AllTests`），主工程记录无法冻结；990 隔离构建不受影响。
4. **下一步建议**：S6 候选包与回退演练（隔离构建、逐条目哈希、映射/撤回脚本草案、`package.ps1` 适配当前 HOME 的 upm 派生逻辑），或先安排真实 NC 联调（冻结→审计、关闭原因/整单行、解冻 ATP、提交/收回与保存并提交、审批窗口与通知、多选）。
