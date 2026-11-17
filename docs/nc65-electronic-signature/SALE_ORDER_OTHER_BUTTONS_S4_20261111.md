# 销售订单其他按钮 S4（审批动作：审批确定 / 取消审批）交付记录

- 批次：**S4 审批动作**（对应 `docs/nc65-electronic-signature/SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md` 第 50 行）
- 日期：2026-11-11
- 范围：`40060301` 审批（212，真实按钮 `Approve`）与取消审批（213，`UnApprove`）的受控链路；首轮限 990 单笔普通订单；保留既有 Save、S2 直接服务与 S3 PF 基础动作。
- 本批结论：
  - **审批（212）**：客户端覆盖层 `QdSaleOrderApproveAction` 在原生 `procFlow` 内先执行原生审批流程准备（`PfUtilClient.hintBeforeAction` + `actionAboutApproveflow`，即审批窗口）；**打开或取消审批窗口时不签名、不产生业务请求**。窗口「确定」后才执行一次签名（策略/理由/审计按真实 `Approve` 按钮），把带 `WorkflownoteVO`（审批意见、决议、工作项、指定审批人扩展）的 `SaleOrderPfActionCommand` 交给网关，由服务端调用原生 PF `APPROVE`，再以持久化表头与返回身份核对结果。
  - **取消审批（213）**：`QdSaleOrderUnApproveAction` 同样在原生流程准备后签名，经网关调用原生 PF `UNAPPROVE`；工作项与下游单据检查由原生 PF 保留。
  - **时序符合计划要求**：签名发生在"点击确定之后、真实写入之前"；打开/取消审批窗只走原生流程准备，不产生业务请求、不落成功审计。
  - **策略与权限使用真实按钮**：`Approve`/`UnApprove` 注册编码与 action code 不变，不虚构「确定」按钮；`SaleOrderPilotScope.isImplemented` 纳入两者（已接入 7 项：Save/Commit/UnCommit/Approve/UnApprove/Freeze/BillClose；仅 Delete 因确认顺序与 UnFreeze 因 ATP 恢复协议保持关闭）。
  - **指定审批人/加签分支**：`SaleOrderPfPreparation` 对审批类动作不再拒绝 `isAddApproverOrAppoint`，流程说明整体交给原生 PF；真实分支与通知时序待联调（U56）。
- 边界：未部署、未执行 SQL、未启停共享 NC、未外发。**真实 NC 审批窗口、工作项与通知未验证**。

## 1. 新增 / 修改文件

| 层 | 文件（主线） | 990 兼容包副本 | 说明 |
| --- | --- | --- | --- |
| private | `so/src/private/nc/impl/so/qd/esign/SaleOrderActionRoute.java` | 有，已同步 | 服务端就绪集合加入 Approve/UnApprove（现仅 UnFreeze 关闭） |
| private | `so/src/private/nc/impl/so/qd/esign/SaleOrderOperationAdapter.java` | 有，已同步 | PF 动作名映射补全 `APPROVE/UNAPPROVE`（此前仅 DELETE/SAVE/UNSAVE） |
| public | `uapbd/src/public/nc/vo/uapbd/qd/esign/SaleOrderPilotScope.java` | 有，已同步 | 已接入集合加入 Approve/UnApprove（7 项；Delete 明确排除） |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/SaleOrderPfPreparation.java` | 有，已同步 | 动作白名单扩为 `WRITE/DELETE/SAVE/UNSAVE/APPROVE/UNAPPROVE`；审批类动作调用原生审批窗口；流程参数白名单（notechecked/silently/nosendmessage/besideapprove，String/Boolean）；审批类允许加签/指定审批人 |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/QdSaleOrderApproveAction.java`（新） | 有，已同步 | 审批：原生审批窗口确认后签名，受控 PF APPROVE |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/QdSaleOrderUnApproveAction.java`（新） | 有，已同步 | 取消审批：签名后受控 PF UNAPPROVE |
| client | `uapbd/src/client/nc/ui/uapbd/qd/esign/QdSaleOrderConfig.xml` | 有，已同步 | 增加 `approveAction`/`unApproveAction` 覆盖；`deleteAction`/`unFreezeAction` 保持原生 |
| test | `SaleOrderPfActionAdapterTests`（扩至 48 断言） | 有，已同步 | 增加审批/取消审批原生动作名、参数与结果用例；启用边界更新 |
| test | `SaleOrderStateAdapterTests`(49)、`SaleOrderOtherButtonsTests`(162)、`SaleOrderPilotConfigTests`、`SaleOrderPilotPolicyServiceTests`、`AllTests` | 有/已同步 | 启用边界、配置 bean 断言与策略白名单更新 |

未改动：删除客户端覆盖层（删除仍关闭）、签名拦截器、handoff、保存试点、网关白名单、`MappingRules`、`OrderPhysicalSnapshots`、`qd_esign_saleorder.upm`、`package.ps1`。

## 2. 验证与构建记录（实际执行）

### 2.1 990 隔离销售构建（本批权威构建记录）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1 -Verify
```

结果（退出码 0，日志 `build/s4-build-990.log`）：

```text
PASS SaleOrderPfSaveAdapterTests assertions=26
PASS SaleOrderPilotConfigTests
PASS SaleOrderPilotPolicyServiceTests
PASS SaleOrderOtherButtonsTests assertions=162
PASS SaleOrderActionHandoffTests assertions=45
PASS SaleOrderStateAdapterTests assertions=49
PASS SaleOrderPfActionAdapterTests assertions=48
ISOLATED SALES BUILD PASSED. No deployment or database change.
Verified isolated build inputs and compiled classes.
```

- 构建记录：`build/sales-990-isolated/build-record.json`（`tests_passed=true`、128 个编译类、冻结输入 `2A1BF49F…`、`13:53:39Z`）；`-Verify` 退出码 0。

### 2.2 主工程整包构建（本轮未产出绿色记录）

- 日志 `build/s4-build-main.log`：全层编译通过，`ALL OFFLINE TESTS PASSED`（含本批 6 个销售测试类）；随后冻结校验报 `Inputs changed while building`——并行会话在构建期间持续写入（21:51–21:54 修改 `InventoryPhysicalSnapshots`、`OperationTransactionServiceImpl`、`NativeOrgBatchPage` 等）。
- 主工程整包记录待并行会话完成、写入停止后重跑（`UNRESOLVED_ISSUES.md` U43/U45/U51/U57）。

## 3. S4 验收项对照

| 验收要求 | 本批离线证据 | 状态 |
| --- | --- | --- |
| 打开/取消审批窗不产生业务请求 | 原生审批窗口在签名之前；窗口取消（`prepare` 未接受）直接返回，不签名、不派发 | 代码路径就绪；真实窗口待联调 |
| 点击「确定」后按策略签名一次 | 窗口接受后调用 `ElectronicSignatureActionInterceptor`，一次性授权后派发一次 | 代码路径就绪；真实签名交互待联调 |
| 通过/退回/非终审分支按原流程推进 | 审批决议/状态/工作项均在原生 `WorkflownoteVO` 中随命令传递，服务端调用原生 PF APPROVE | 离线通过（载体不丢失）；真实分支待联调 |
| 审计按真实结果记录 | 审批/弃审按 UPDATE 走销售聚合 BEFORE/AFTER 快照；原生失败不落成功审计 | 离线通过（快照识别）；真实差异待联调 |
| 原有通知不漏发、不重复 | 受控路径只调用一次原生 PF（不额外重复通知）；通知由原生 PF/工作流发出 | 代码层面成立；真实时序待联调 |
| 策略/权限用真实 Approve/UnApprove | 未使用虚构「确定」按钮；策略包装器按真实按钮范围管理 | 离线通过 |
| 指定审批人/加签 | 审批类动作不再拒绝 `isAddApproverOrAppoint`，流程说明整体交原生 PF | 待联调确认（U56） |

## 4. 已知限制与风险

1. 审批窗口、签名窗口、`WorkflownoteVO` 实际内容（意见/决议/工作项/指定审批人）、通知只发一次与非终审时序均未在真实 NC 验证；客户端在 `procFlow` 内临时安装/恢复签名拦截链的设计只在离线编译与 XML 解析层验证。
2. 「保存并提交」与审批通知的交互、审批后置插件时序需联调（S3 U54/S4 U56）。
3. 多选审批在受控策略下显式拒绝（单张），不回落原生写入；S5 解锁。
4. 主工程整包构建受并行会话持续写入影响，记录仅 990 隔离构建有效。

## 5. 下一步

- **S5 批量与现场分支**：多选语义、逐对象事务与回执；解锁删除/审批/提交等多选入口前先核实原生原子/逐单语义。
- **S6 候选包与回退**：隔离构建、逐条目哈希、映射/撤回脚本、`package.ps1` 适配当前 HOME 的 upm 派生逻辑。
- **联调优先**：冻结→审计链路、整单关闭原因/整单行、解冻 ATP 协议、提交/收回 PF 与「保存并提交」开关组合、审批窗口与通知时序、删除"确认后签名"设计。
