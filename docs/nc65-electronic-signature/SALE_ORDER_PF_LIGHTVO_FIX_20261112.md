# 销售订单流程动作差异 VO 修复：20261112

## 1. 现象

990 组织销售订单列表态点「提交」，签名弹窗确认后报错：

> 流程动作的订单、组织或主键与签名授权不一致。

现场调试（断点 `SaleOrderPfActionDispatch.dispatch` 第 46 行）取到流程上下文里的 `SaleOrderVO`：表头仅 `csaleorderid`、`ts`、`fstatusflag=1`、`fpfstatusflag=-1`、`cbiztypeid`、`dr=0`、`pseudocolumn` 有值，`pk_group`/`pk_org`/`vtrantypecode` 等全部 `null[NOT SETTING]`。

## 2. 根因

原生 `ScriptPFlowAction.getFlowData()`（uipubapp platformext，2026-11-12 核对源码）对**已存单据**的流程动作统一执行 `produceLightVO()`：经 `ClientBillToServer.construct` 产出**差异 VO**（light VO），表头只保留主键、并发时间戳、状态标志与流程业务接口字段（`fillBusitypeAfterLight` 仅按 IFlowBizItf 键回填 busitype/审批人等，不回填 pk_group/pk_org）。列表态与卡片态（`editor.isComponentVisible()`）均如此；`fullOldVOs` 保留完整单据但仅存于动作实例。

电子签受控分支此前直接对流程上下文 bill 做组织/集团核对与派发参数传递，差异 VO 的空值导致：

| 位置 | 原逻辑 | 后果 |
| --- | --- | --- |
| `SaleOrderPfActionDispatch.dispatch`（提交/收回提交共用） | 授权 group/org 与 bill 表头比对 | 必然误判，抛出本现象错误 |
| 同上 `handoff.dispatch` 调用 | 传 bill 的 pk_group/pk_org（null） | 即使放行也会报「按钮、组织、对象已变化」 |
| `QdSaleOrderApproveAction` / `QdSaleOrderUnApproveAction.dispatch` | 同类比对 | 审批/弃审联调将误报「授权与当前订单不一致」 |
| `QdSaleOrderDeleteAction.procFlow` | `policyControlled(bill.pk_org)`（null） | **空组织被策略服务判为不受控 → 静默回落原生删除，绕过签名（安全缺陷）** |
| 服务端 `SaleOrderOperationAdapter.validatePf` | 对命令内 bill 表头核对 group/org/vtrantypecode | 客户端放行后服务端将报「订单组织与授权不一致」 |

冻结/解冻/整单关闭不受影响（动作直接取 model 完整 VO）。保存试点（INSERT/UPDATE）走编辑器完整 VO，不受影响。

## 3. 修复

原则：与已联调的发货单/发票 `SalesPfActionDispatch`（仅核对主键）及 `SaleOrderCommand` 路径（服务端按库内核对组织）保持同一模式——主键核对保留在差异 VO 上；集团/组织/交易类型一律改用完整事实源。

1. `SaleOrderPfActionDispatch`：新增包内静态 `fullBillOf(action, bill)`——经 `ScriptPFlowAction.getFullOldVOs()`（fillUpContext 后必已填充）按主键取完整单据，取不到或主键不符即拒绝；`dispatch` 的 group/org 比对改用完整单据；`handoff.dispatch` 改传授权中的 group/org。
2. `QdSaleOrderApproveAction` / `QdSaleOrderUnApproveAction`：`dispatch` 同步改用 `fullBillOf` + 授权值。
3. `QdSaleOrderDeleteAction`：`procFlow` 的 group/org 改从 `fullBillOf` 结果取，`policyControlled` 不再收到 null 组织。
4. 服务端 `SaleOrderOperationAdapter.validatePf`：组织与交易类型改按 `loadHeader(pk)` 持久化表头核对（loadHeader 已含所需列），不再信任命令 VO 表头字段；主键与路由对象核对、userObj/PF 参数白名单不变。

主工程与 `sales-order-990-release` 隔离发布树逐字同步（5 个文件 diff 仅本次修复）。

## 4. 验证

- 主工程 `tools\build.ps1 -Test`：BUILD PASSED，ALL OFFLINE TESTS PASSED（含 `SaleOrderPfActionAdapterTests` 49 断言，其 Stub 的 `loadHeader` 返回授权组织/集团/`vtrantypecode="30"`，与新校验兼容）。输出中 `SalesPfBridgeTests` 的 NPE 堆栈为离线实例化原生动作的既有噪音（该测试 PASS，断言 6）。
- 990 隔离管线 `sales-order-990-release\build.ps1`：8 个测试类全部通过。构建收尾的冻结输入复核曾连续失败，定位为运行中的 NC 进程持续写入 `sales-order-990-release\nclogs\nc-log.log`（该文件在冻结输入集内）；前后快照对比确认唯一变化文件即此日志。**NC 停止后重跑通过后再作为部署证据**。
- 真实 NC 联调：待部署后在 QuDuTest 本地 HOME 执行（提交/收回/审批/弃审/删除逐按钮 + 卡片编辑态提交应按既有设计报「流程动作与签名授权不一致，请重新操作」）。

## 5. 部署（已执行）

- 部署会话：`build/deployment/20261112-143754-07fb0ae8`（第三次：流程动作差异 VO 修复）
- 候选包：`build/qd-esign-sale-990-20261112-143652-24d1b76e.zip`（SHA-256 `59D09608…B70BADB3`）
- 目标：`E:\01-Home\NC65home\QuDuTest\nchome`，21 个模块文件原子替换（File.Replace，逐文件包内哈希复核）
- 校验：`nc-patch.ps1 -Action Verify` verified=true（21/21 新哈希）；44 份 ierp/bin 配置 0 变化；core.upm 仍绑 SaleOrderPilotPolicyService；部署时无 HOME Java 进程
- 生效：重启 NC 中间件 + 客户端重新登录（服务端 so private 与客户端 uapbd client 均有类变更）
- 回退：`build/deployment/20261112-143754-07fb0ae8/rollback.ps1`
- 注意：990 隔离构建必须从工程根目录调用 `powershell -File sales-order-990-release\build.ps1`——测试 JVM 按工作目录写 `nclogs/nc-log.log`，若在 990 树内调用会污染冻结输入复核（本次已删除误生成的 `sales-order-990-release/nclogs/`）

## 6. 遗留提示

- 卡片编辑态（EDIT/ADD/COPY_ADD）点提交走 composite（preActionNames 含 SAVE），受控 dispatch 按设计拒绝（报「销售订单流程动作与签名授权不一致，请重新操作」），未放开；如需支持需另立方案，不得静默回落原生。
- 平台结论备查：任何基于 `procFlow` 流程上下文 bill 的核对，不得读其表头业务字段（差异 VO 无组织/集团/交易类型）；主键之外的核对用 `getFullOldVOs()` 或持久化事实。
