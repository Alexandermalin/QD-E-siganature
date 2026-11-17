# 销售订单其他按钮 S3（PF 基础动作：删除 / 提交 / 收回）交付记录

- 批次：**S3 PF 基础动作**（对应 `docs/nc65-electronic-signature/SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md` 第 49 行）
- 日期：2026-11-11
- 范围：`40060301` 删除（209）、提交（210）、收回（211）的受控链路；首轮限 990 单笔普通订单；保留既有 Save 试点与 S2 直接服务。
- 本批结论：
  - **提交、收回已接通受控链路**：客户端覆盖层（`QdSaleOrderSendApproveAction` / `QdSaleOrderUnSendApproveAction`）保留原生流程准备（`fillUpContext`/`PfUserObject`/交易类型/前置校验）与结果刷新，只在最终 PF 写入前用一次性授权把 `SaleOrderPfActionCommand` 交给网关；服务端固定分支调用原生 `IplatFormEntry.processAction("SAVE"/"UNSAVE", …)`，并按持久化表头与返回身份核对业务事实。**「保存并提交」复合动作复用同一个 `sendApproveAction` bean，其提交段同样受控**，保存段仍是既有 Save 试点，两段各自签名、互不复用授权。
  - **删除服务端分支已实现并通过离线用例，但客户端覆盖层与策略开放保持关闭**：原生删除的二次确认（余额提示 + `MessageDialog.showYesNoDlg`）在 `SaleOrderDeleteAction.doAction` 内、PF 写入之前；若按现有包装方式先签名再执行原生 `doAction`，用户在确认框点"取消"时签名已被消费并会留下失败审计记录。按计划"未能保留原生确认/取消路径的分支不启用"，本批不创建删除客户端覆盖类，`deleteAction` 保持原生，`SaleOrderPilotScope.isImplemented("Delete")=false` 使策略包装器**禁止**开启删除签名/审计（避免原生删除绕过签名）。删除的服务端固定分支、参数白名单、`dr=1/不存在` 事实核对与审计删除路线已实现并有 43 项断言中的删除用例覆盖，待联调确认"确认后签名"顺序后启用。
  - **PF 参数白名单**：服务端只接受 `notechecked/silently/nosendmessage/besideapprove` 四个键的 String/Boolean 值；拒绝任意脚本名、编译器类名、`businessCheckMap` 跳过标记；交易类型必须与单据一致。
  - **版本限定桥接**：提交/收回沿用保存试点的 `SaleOrderPfPreparation` 版本闸门（`PfUtilClient` 字节码 SHA-256 固定），动作白名单扩为 `WRITE/DELETE/SAVE/UNSAVE`；审批类动作仍拒绝（S4）。
- 边界：未部署、未执行 SQL、未启停共享 NC、未外发。真实 NC 联调未进行；删除客户端未实现。

## 1. 新增 / 修改文件

| 层 | 文件（主线） | 990 兼容包副本 | 说明 |
| --- | --- | --- | --- |
| public | `so/src/public/nc/vo/so/qd/esign/SaleOrderPfActionCommand.java` | 有，已同步 | 增加交易类型字段（5 参构造，保留 4 参兼容） |
| public | `so/src/public/nc/vo/so/qd/esign/SaleOrderPfActionResult.java`（新） | 有，已同步 | 原生返回对象 + 真实主键/单号 + 删除事实 |
| public | `uapbd/src/public/nc/vo/uapbd/qd/esign/SaleOrderPilotScope.java` | 有，已同步 | 已接入集合增加 Commit/UnCommit（Delete 明确不加入） |
| private | `so/src/private/nc/impl/so/qd/esign/SaleOrderActionRoute.java` | 有，已同步 | 服务端就绪集合：Delete/Commit/UnCommit/Freeze/BillClose；Approve/UnApprove/UnFreeze 仍关闭 |
| private | `so/src/private/nc/impl/so/qd/esign/SaleOrderOperationAdapter.java` | 有，已同步 | 新增 PF 固定分支：命令校验（单张、主键/组织/交易类型/原生用户对象/参数白名单）→ 原生 `processAction` → 持久化事实与返回身份核对；新增 `platformEntry()/loadHeader()` 测试钩子 |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/SaleOrderPfPreparation.java` | 有，已同步 | 允许 `WRITE/DELETE/SAVE/UNSAVE`，仍拒绝审批/启动/信号动作；版本闸门不变 |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/SaleOrderPfActionDispatch.java`（新） | 有，已同步 | 受控 PF 分发：核对流程上下文/动作名/前置后置动作/校验器 → `fillUpContext` → `SaleOrderPfPreparation` → 类型化命令 → 网关 → `prep.completed()` + 原生结果刷新 |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/QdSaleOrderSendApproveAction.java`（新） | 有，已同步 | 提交：包装签名拦截链，`procFlow` 受控分发 |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/QdSaleOrderUnSendApproveAction.java`（新） | 有，已同步 | 收回：同上，使用原生 UNSAVE 流程上下文 |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/QdSaleOrderConfig.xml` | 有，已同步 | 增加 `sendApproveAction`/`unsendApproveAction` 覆盖；`deleteAction`、`unFreezeAction` 保持原生 |
| policy | ——（仅 990 包） | `sales-order-990-release/.../SaleOrderPilotPolicyService.java` | 未改代码，`isImplemented` 门自动把 Commit/UnCommit 纳入、Delete 排除 |
| test | `tests/java/nc/test/qd/esign/SaleOrderPfActionAdapterTests.java`（新） | 有，已同步 | 43 断言：PF SAVE/UNSAVE/DELETE 动作名与参数、删除事实、返回身份、参数白名单、原生拒绝不重试、启用边界、审计识别 |
| test | `SaleOrderOtherButtonsTests` / `SaleOrderStateAdapterTests` / `SaleOrderPilotConfigTests` / `AllTests` / 990 `build.ps1` 测试清单 | 有/已同步 | 按 S3 启用边界更新；配置断言覆盖 send/unsend bean；注册新测试 |

未改动：删除客户端覆盖层（见结论）、签名拦截器、handoff、保存试点、网关白名单、`MappingRules`、`OrderPhysicalSnapshots`、`qd_esign_saleorder.upm`、`package.ps1`。

## 2. 验证与构建记录（实际执行）

### 2.1 990 隔离销售构建（本批权威构建记录）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1 -Verify
```

结果（退出码 0，日志 `build/s3-build-990.log`）：

```text
PASS SaleOrderPfSaveAdapterTests assertions=26
PASS SaleOrderPilotConfigTests
PASS SaleOrderPilotPolicyServiceTests
PASS SaleOrderOtherButtonsTests assertions=164
PASS SaleOrderActionHandoffTests assertions=45
PASS SaleOrderStateAdapterTests assertions=47
PASS SaleOrderPfActionAdapterTests assertions=43
ISOLATED SALES BUILD PASSED. No deployment or database change.
Verified isolated build inputs and compiled classes.
```

- 构建记录：`build/sales-990-isolated/build-record.json`（`tests_passed=true`、127 个编译类、冻结输入 `0306A825…`、`13:02:14Z`）；`-Verify` 退出码 0。

### 2.2 主工程整包构建（本轮未产出绿色记录，原因均为并行会话）

- 第一次（日志 `build/s3-build-main.log`）：public/private/client/legacy 层编译通过，`ALL OFFLINE TESTS PASSED`（包含本批 6 个销售测试类），随后冻结校验报 `Inputs changed while building`——并行会话在构建期间持续写入。
- 第二次（日志 `build/s3-build-main2.log`，等待空闲后重跑）：离线测试失败于并行会话正在编辑的 `PartnerBatchChunksTests`（`Unsent chunk did not report prior and remaining work accurately`，`AllTests` 第 96 行），与本批文件无关。
- 因此本批以 990 隔离销售构建为主记录；主工程整包记录待并行会话完成、写入停止后重跑补齐（`UNRESOLVED_ISSUES.md` U43/U45/U50/U51）。

## 3. S3 验收项对照

| 验收要求 | 本批离线证据 | 状态 |
| --- | --- | --- |
| 每次确认只执行对应 PF 写入 | 提交/收回各调用原生 PF 一次；动作名 SAVE/UNSAVE 与交易类型、流程说明、用户对象、参数逐一核对 | 离线通过 |
| 取消、保存未成功或权限失败不写 | 流程准备未接受 → 不派发；签名取消 → 不进入 procFlow 受控分支；原生失败 → 原样传播不重试；多选在受控策略下被 handoff 拒绝 | 离线通过 |
| 删除快照完整 | 审计识别 DELETE → 销售聚合 BEFORE 快照；删除后以 `dr=1/不存在` 核对失效；**关联明细快照仍待补** | 部分完成（关联明细待 S5/联调） |
| 提交/收回真实流程结果可核对 | 持久化表头存在/状态字段、原生返回身份、真实单号回填 | 离线通过；真实工作项待联调 |
| 「保存并提交」两段策略 | 复合动作复用同一 `sendApproveAction` bean，提交段受控；保存段仍为 Save 试点，两段各授权一次 | 代码路径就绪；真实开关组合待联调 |
| 删除的确认/取消顺序 | 发现原生确认在 PF 之前，当前包装会在确认前签名 → **删除保持关闭** | 需"确认后签名"设计或联调验证 |

## 4. 已知限制与风险

1. 删除客户端未实现：需在确认之后发起签名（例如覆盖 `doAction` 并只复用原生确认逻辑，或在 `procFlow` 内完成签名/授权），并证明取消确认不消费签名、不落失败审计；确认前删除策略禁止开启。
2. 提交/收回客户端 `procFlow` 桥接依赖固定版本 `PfUtilClient`，只在离线编译与配置解析层验证；真实窗口的流程准备、审批弹窗与结果刷新需联调。
3. 「保存并提交」的保存成功后才进入提交段、提交取消/失败时保留保存结果等复合语义，需真实会话验证（本批只保证代码路径与离线契约）。
4. 多选 PF 动作在受控策略下会被 handoff（单对象）拒绝，不会回落原生写入；S5 解锁。
5. 主工程整包构建受并行会话持续写入影响，记录仅 990 隔离构建有效。

## 5. 下一步

- **S4 审批动作**：审批窗口「确定」与取消审批，保留 `WorkflownoteVO` 参数与通知时序。
- **删除确认顺序设计/联调**：确认后签名或 procFlow 内签名，满足"取消确认不删、不落成功审计"。
- **S5 多选**、**S6 候选包与回退脚本**；真实 NC 联调窗口优先验证提交/收回 PF 链路与「保存并提交」开关组合。
