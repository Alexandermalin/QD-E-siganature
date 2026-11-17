# 销售订单其他按钮 S2（直接服务：冻结 / 解冻 / 整单关闭）交付记录

- 批次：**S2 直接服务**（对应 `docs/nc65-electronic-signature/SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md` 第 48 行）
- 日期：2026-11-11
- 范围：`40060301` 三个直接服务动作的受控链路，首轮限 990 单笔普通订单；保留既有 Save 试点。
- 本批结论：
  - **冻结（214）、整单关闭（216）已接通受控链路**：客户端覆盖层 → 一次性签名授权 → `IOperationService` 类型化命令 → 服务端固定分支 → `ISaleOrderCloseManageMaintain` 原生方法 → 审计快照（`ISaleOrderOperationAdapter` + `UPDATE`，已在 990 构建中确认被现场已部署的 `OrderPhysicalSnapshots` 识别）。
  - **解冻（215）服务端分支已实现但保持关闭**：原生 ATP「继续」是客户端捕获 `IResumeException` 后二次调用并传 `ATPCheck=false` 的恢复协议；受控网关当前不保留可恢复异常类型，无法在离线阶段证明与原生等价。按计划"未能保留原生警告和恢复协议的分支不启用"，解冻保持关闭，且策略包装器**禁止**为其开启签名/审计策略，避免原生写入绕过签名。待联调确认恢复协议后再启用。
  - **整单关闭保留原生语义**：关闭原因输入框（原生 `MessageDialog.showInputDlg` + 原生资源 04006011-0233/0234）、取消不留写入、整单行缺失时用 `ISaleOrderMaintain.querySaleorder` 回查补齐、逐行写 `vclosereason`；签名事由与关闭原因分别传递，服务端校验原因非空、首尾无空白且与每行 `vclosereason` 一致。
- 边界：未部署、未执行 SQL、未启停共享 NC、未外发。**真实 NC 联调仍未进行**；客户端覆盖层未在真实窗口验证。

## 1. 新增 / 修改文件

| 层 | 文件（主线） | 990 兼容包副本 | 说明 |
| --- | --- | --- | --- |
| public | `uapbd/src/public/nc/vo/uapbd/qd/esign/SaleOrderPilotScope.java` | 有，已同步 | 增加"已接入受控执行"按钮集合（Save/Freeze/BillClose）、`isImplemented/implementedButtons/buttonForScope`，供策略包装器阻止未接入按钮开启策略 |
| private | `so/src/private/nc/impl/so/qd/esign/SaleOrderActionRoute.java` | 有，已同步 | 启用边界改为 Freeze、BillClose；UnFreeze 保持关闭；`Kind` 增加 `isState()`，语义仍为"服务端决定原生方法" |
| private | `so/src/private/nc/impl/so/qd/esign/SaleOrderOperationAdapter.java` | 有，已同步 | 新增直接服务固定分支：`SaleOrderStateCommand` 校验（单张订单、主键/组织与授权一致、关闭原因与整单行/`vclosereason` 一致、ATP 参数白名单）→ `freezeSaleOrder` / `unFreezeSaleOrder(map)` / `closeSaleOrder` → 返回身份核对；新增 `closeManage()` 可测试钩子 |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/QdSaleOrderFreezeAction.java`（新） | 有，已同步 | 继承原生 `FreezeAction`；保留原生选择、返回 VO 合并（`ClientBillCombinServer`）与 `directlyUpdate`；策略关闭时回落原生 `doAction` |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/QdSaleOrderCloseAction.java`（新） | 有，已同步 | 继承原生 `SaleOrderCloseAction`；保留关闭原因输入、取消无写入、整单行补齐、逐行 `vclosereason`；受控路径单张、多选显式拒绝（不回落原生写入）、混合组织拒绝 |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/QdSaleOrderConfig.xml` | 有，已同步 | 增加 `freezeAction`→`QdSaleOrderFreezeAction`、`closeAction`→`QdSaleOrderCloseAction` 覆盖；`unFreezeAction`、`deleteAction` 等保持原生 |
| policy | ——（仅 990 包） | `sales-order-990-release/uapbd/src/private/nc/impl/uapbd/qd/esign/SaleOrderPilotPolicyService.java` | 增加"未接入按钮禁止开启策略"门；已接入 Save/Freeze/BillClose 仍限 990 |
| test | `tests/java/nc/test/qd/esign/SaleOrderStateAdapterTests.java`（新） | 有，已同步 | 44 断言：动作启用边界、冻结/关闭成功路径、参数白名单（关闭原因/整单行/ATP 标记/跳过检查）、主键与组织一致性、原生拒绝不重试、结果身份不一致、审计识别 |
| test | `tests/java/nc/test/qd/esign/SaleOrderOtherButtonsTests.java` | 有，已同步 | 按 S2 启用边界更新（167 断言）：Freeze/BillClose 已启用，其余仍关闭 |
| test | `tests/java/nc/ui/uapbd/qd/esign/SaleOrderPilotConfigTests.java` | 有，已同步 | 增加 bean 覆盖断言：freeze/close 用签名覆盖类；unFreeze/delete 保持原生 |
| test | `tests/java/nc/test/qd/esign/AllTests.java` | ——（990 包用运行清单） | 注册 `SaleOrderStateAdapterTests` |
| build | `sales-order-990-release/build.ps1` | —— | 测试运行清单加入 `SaleOrderStateAdapterTests` |

未改动：`ElectronicSignatureActionInterceptor`、`SignatureLifecycleInstaller`、`SaleOrderActionHandoff`、保存试点类、网关白名单、`MappingRules`、`OrderPhysicalSnapshots`、`qd_esign_saleorder.upm`、`package.ps1`。

## 2. 验证与构建记录（实际执行）

### 2.1 990 隔离销售构建（本批权威构建记录）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1 -Verify
```

结果（退出码 0，220s；日志 `build/s2-build-990.log`）：

```text
PASS SaleOrderPfSaveAdapterTests assertions=26 (native PF, database, and transaction not invoked)
PASS SaleOrderPilotConfigTests (native XML and pinned PF client; no NC session)
PASS SaleOrderPilotPolicyServiceTests (pilot guard, implemented-scope gate and delegation)
PASS SaleOrderOtherButtonsTests assertions=167 (whitelist, route, typed commands and save delegation; no NC or database invoked)
PASS SaleOrderActionHandoffTests assertions=45 (offline handoff contract; no NC client or service invoked)
PASS SaleOrderStateAdapterTests assertions=44 (direct-service branches, parameter whitelist and identity checks; no NC or database invoked)
ISOLATED SALES BUILD PASSED. No deployment or database change.
Verified isolated build inputs and compiled classes.
```

- 构建记录：`build/sales-990-isolated/build-record.json`（`schema_version=1`、`tests_passed=true`、100 个编译类、冻结输入 `9E56070A…`）；`-Verify` 退出码 0。
- 该构建使用 HOME 已部署的 `qd_esign_uapbd_private.jar` 中的 `OrderPhysicalSnapshots`，确认**现场已部署的审计识别支持 `ISaleOrderOperationAdapter` + `UPDATE/DELETE/INSERT`**，因此 8 项直接服务动作的审计差异路线在部署基线上成立（删除关联明细补充仍属 S3/S5 待办）。

### 2.2 主工程整包构建（通过）

命令：`powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1 -Test`。

- 第一次尝试（日志 `build/s2-build-main.log`）：public 114 / private 153 / client 114 / legacy-examples 6 编译通过；测试层失败于并行会话正在编辑的 `PartnerBatchChunksTests.java:32-34`（引用尚不存在的 `QdPartnerBatchDispatcher.dispatchFailure`），与本批文件无关。
- 并行会话暂停后重跑（日志 `build/s2-build-main2.log`）：**退出码 0**，427s；全部离线测试 `ALL OFFLINE TESTS PASSED`（含本批 `SaleOrderStateAdapterTests` 44 断言、`SaleOrderOtherButtonsTests` 167、`SaleOrderActionHandoffTests` 45、`SaleOrderPfSaveAdapterTests` 26），`BUILD PASSED`。
- 构建记录：`build/build-result.json`（`schema_version=2`、`tests_passed=true`、1023 类、`12:23:59Z`、`class_artifacts_sha256=3789B5DE…`）；构建当次 `-VerifyRecord` 退出码 0。
- **记录随后漂移（并行会话写入，非本批改动）**：20:25–20:26 并行会话又修改了 `InventoryPfPreparation.java`、`QdPurchaseInConfig.java`、`QdPurchaseInSaveAction.java`，并**把保存试点的 `SaleOrderPfPreparation.java` 中 `field/method` 由 private 放宽为包内可见**（887→6883→6867 字节，功能不变，疑为其 inventory PF 复用），因此现在 `-VerifyRecord` 报输入不一致。候选发布前必须串行化写入窗口后重跑主工程与 990 构建；本批权威记录仍以 990 隔离构建为准（其输入不含这些文件，`-Verify` 通过）。
- 并行会话对 `SaleOrderPfPreparation` 的可见性改动使主线与 990 副本出现差异（990 副本仍为 `53AF0687…`），已登记 `UNRESOLVED_ISSUES.md` U50：候选打包前必须决定同步方向（建议把放宽可见性同步进 990 副本后重建 990 记录）。

## 3. S2 验收项对照（离线 vs 待联调）

| 验收要求 | 本批离线证据 | 状态 |
| --- | --- | --- |
| 每项有成功用例 | 冻结/关闭成功路径各 1 组（原生服务调用一次、结果身份核对、`describeResult` 回填） | 离线通过 |
| 每项有原生拒绝用例 | 原生服务抛 `BusinessException` → 原样传播、调用一次不重试、无成功审计路线 | 离线通过 |
| 每项有取消用例 | 关闭原因输入取消 → 不签名不写入；签名取消 → 不派发；解冻关闭状态 → 无原生调用 | 离线通过 |
| 每项有审计故障用例 | 离线无法注入网关审计提交故障；仅确认审计识别路线与结果核对 | **待联调注入** |
| 未能保留原生警告/恢复协议的分支不启用 | 解冻 ATP 恢复协议无法离线证明 → 保持关闭且禁止开启策略 | 按计划执行 |
| 关闭原因与整单行 | 原因非空/首尾空格/与每行 `vclosereason` 一致校验；缺整单行拒绝 | 离线通过；真实输入框与补齐待联调 |
| 只执行一次成功写入 | 适配器每次调用原生服务一次；网关受控路径不重复调原生 | 离线通过（真实事务边界待联调） |

## 4. 已知限制与风险

1. **客户端覆盖层未在真实 NC 窗口验证**：Spring bean 覆盖、拦截器安装顺序、EDT 行为、`MessageDialog` 输入框与 `processReturnObjs` 刷新均只在离线编译/配置解析层面检查。
2. **整单关闭多选显式拒绝**：受控路径单选；多选抛出"多选尚未适配，请单选"而不回落原生写入，符合首轮单笔范围；多选留待 S5。
3. **解冻关闭**：策略包装器禁止为其开启签名/审计，避免绕过签名；启用前需联调确认 ATP「继续」的受控恢复协议（可能需要在网关增加"用户已确认"参数白名单与一次性授权复用规则，单列设计）。
4. **关闭原因长度/编码**：本批只做非空与首尾空白校验，不在代码中臆造长度上限；原生服务与字段约束继续生效。
5. **主工程整包构建受并行会话阻断**（见 §2.2），候选发布前需串行化写入窗口。

## 5. 下一步

1. **S3 PF 基础动作**：删除（保留原生二次确认与余额校验）、提交/收回（覆盖「保存并提交」提交段），把 PF 五项链中删除/提交/收回接入受控分支，并补删除前关联明细快照。
2. **S4 审批动作**：审批窗口「确定」与取消审批，保留 `WorkflownoteVO` 参数与通知时序。
3. **S5 多选**：核实原生原子批次/逐单语义，解锁关闭等多选入口。
4. **联调前**：与项目负责人约定测试窗口，优先验证冻结→审计链路，再验证整单关闭原因/整单行与解冻 ATP 协议。
