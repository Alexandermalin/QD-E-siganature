# 销售订单维护 40060301 — 八项原生落点与实现链（S0 证据）

- 取证日期：2026-11-11
- 项目根（只读）：`E:\02-Project\QiDu\QD-E-Siganature`
- NC65 HOME（只读）：`E:\01-Home\NC65home\QuDuTest\nchome`（由 `modules\so\client\lib\uiso_salesorder.jar` 实际存在确认）
- 工作区（唯一可写）：`E:\AI-WorkSpace\DeepSeekHarness\QD-P-WMS\s0-evidence\`
- 工具：HOME 自带 `ufjdk\bin\javap.exe`（`1.7.0-internal`）；JAR 以 `[IO.Compression.ZipFile]::OpenRead` 只读打开，条目复制到工作区后分析；未安装任何工具，未部署、未执行 SQL、未启动 NC、未改项目或 HOME。

## 证据等级

| 标记 | 含义 |
| --- | --- |
| **静态核实** | 直接读自 JAR 内 XML 原文或 `javap -p -c` 字节码；本文给出 `JAR!条目` 或「文件:行号」 |
| **DB 快照** | 来自项目既有只读取证 `docs/nc65-electronic-signature/site-evidence/2026-11-07/`（2026-11-07 采集的 QDNC 只读查询结果），非本次新查库 |
| **未核实** | 本机只读条件无法确认；给出下一步具体观察位置 |

## 0. 口径说明：8 项 vs 任务列出的 9 个按钮 ID

正式清单第 209–216 行共 **8 项操作**；任务给出的 9 个 ID 中，删除一行同时登记了两个按钮：

| 原表行 | 操作 | 注册编码 / PK_BTN |
| --- | --- | --- |
| 209 | 删除 | `Delete` / `1001Z810000000004TO3`；另有 `fs_delete` / `1002Z81000000000QCAZ` |
| 210 | 提交 | `Commit` / `1001Z810000000004TOA` |
| 211 | 收回 | `UnCommit` / `1001Z810000000004TOG` |
| 212 | 审批 | `Approve` / `1001Z810000000004TO2` |
| 213 | 取消审批 | `UnApprove` / `1001Z810000000004TOO` |
| 214 | 冻结 | `Freeze` / `1001Z810000000004TP5` |
| 215 | 解冻 | `UnFreeze` / `1001Z810000000004TOE` |
| 216 | 整单关闭 | `BillClose` / `1001Z810000000004TP1` |

`fs_delete` 的结论见 §1.2：它属于附件/文件面板按钮组，**不是**订单删除入口，不应作为 209 的实现落点。

## 1. 八项映射表

### 1.1 按钮 → bean → 类 → 最终调用 → PF/RPC

按钮注册：**DB 快照** `docs/nc65-electronic-signature/site-evidence/2026-11-07/buttons_1.json`（查询 `sm_funcregister` ⋈ `sm_butnregister`，`funcode=40060301`，68 行）。
bean 注册与属性原文：**静态核实** `uiso_salesorder.jar!nc/ui/so/m30/billui/model/saleorder_config.xml`（下称 `saleorder_config.xml`，行号与 JAR 条目 1:1，提取副本 SHA-256 = `12F85390729D4652B76752CC405D2BDDA062799ABD44BE304EB1541E0D1475B3`）。

**按钮与动作的绑定机制（静态核实）**：NC65 客户端工具栏按钮按 `NCAction` 的 **action code** 绑定。8 个动作类在构造器里初始化的 code 与 DB `BTNCODE` 完全一致：
`DeleteScriptAction` → `ActionInitializer(this,"Delete")`；`CommitScriptAction` → `"Commit"`；`UnCommitScriptAction` → `putValue("Code","UnCommit")`；`ApproveScriptAction` → `"Approve"`；`UNApproveScriptAction` → `"UnApprove"`；`FreezeAction` → `"Freeze"`；`UnFreezeAction` → `"UnFreeze"`；`SaleOrderCloseAction` → `SCMActionInitializer(this,"BillClose")`。
证据：`uipubapp_platformextLevel-1.jar` 内各自的 `javap -c` 构造器（工作区 `s0-evidence/javap/nc.ui.pubapp.uif2app.actions.pflow.*.txt`、`...assist.*.txt`）。
→ **实现覆盖层必须保留 action code 不变**，只能替换 bean 的 class 或属性；改 code 会导致模板找不到按钮。

| # | 操作（按钮） | 页面 bean（id / class / 关键属性原文） | 动作类与类的可核实行为 | 最终调用 | PF / RPC action | 核实 |
| --- | --- | --- | --- | --- | --- | --- |
| 209 | 删除 `Delete`<br>`1001Z810000000004TO3` | `deleteAction` = `nc.ui.so.m30.billui.action.SaleOrderDeleteAction`；`model=ManageAppModel`、`editor=billFormEditor`、`actionName=DELETE`、`billType=30`、`validationService=powerdeletevalidservice`（`saleorder_config.xml:800-806`）。权限服务 `powerdeletevalidservice`：`actionCode=delete`、`billCodeFiledName=vbillcode`、`permissionCode=30`（同文件 807-811） | `extends nc.ui.pubapp.uif2app.actions.pflow.DeleteScriptAction`（无自定义 PF 逻辑）。`doAction`：取 `getSelectedOperaDatas` → 组 `SaleOrderHVO.csaleorderid` 列表 → `ISOBalanceQuery.querySoBalanceVOBySaleOrderIDs` 余额校验 → `MessageDialog.showYesNoDlg` 二次确认（资源 4006011_0/04006011-0283…0285）→ `super.doAction`；`isActionEnable` 要求 `BillStatus.FREE`。基类 `DeleteScriptAction.doAction` 先 `CommonConfirmDialogUtils.showConfirmDeleteDialog` 再 `ScriptPFlowAction.doAction`；`processReturnObj` 用 `PfUtilClient.isSuccess()` 决定 `directlyDelete`/`showFailedInfo` | `AbstractScriptExcAction.doAction → procFlow(PFlowContext) → PFlowProxy.runAction → PfUtilClient.runAction(...) → IplatFormEntry.processAction("DELETE", "30"/交易类型, worknote, vo, userObj, eParam)` | **PF `DELETE`** | 静态核实 |
| — | `fs_delete`<br>`1002Z81000000000QCAZ` | 在 `saleorder_config.xml` 中**无**对应 bean；`uiso_salesorder.jar` 全部 418 个条目中 `fs_delete`/`fs_upload`/`fs_browse` 字符串命中 **0** | 与 `fs_browse`/`fs_download`/`fs_rename`/`fs_upload` 同组（同一 `sm_butnregister` 批次，PK 前缀 `1002…` 而非订单按钮的 `1001…`）。销售订单页的附件入口为 `docManageAction` = `nc.ui.so.pub.actions.SOManageDocumentAction`（`saleorder_config.xml:1182-1185`） | 附件/文件管理面板（平台文件组件），非订单删除 | 非 PF 订单动作 | 未核实（强线索：附件面板；见 §4-2） |
| 210 | 提交 `Commit`<br>`1001Z810000000004TOA` | `sendApproveAction` = `nc.ui.so.m30.billui.action.SaleOrderSendApproveAction`；`model=ManageAppModel`、`editor=billFormEditor`、`filledUpInFlow=true`、`actionName=SAVE`、`billType=30`、`validationService=powersendappvalidservice`（`commit`）、`tpaProgressUtil=tpaprogressutil`（`saleorder_config.xml:856-864`；权限 875-879） | `extends CommitScriptAction`，**未重写 `doAction`**（只重写 `isActionEnable`：EDIT/ADD/COPY_ADD 或 NOT_EDIT 且状态 FREE/多选）。基类构造器 `setActionName("SAVE")`，标题 `pubapp_0/0pubapp-0116` | 同上 PF 链；批量时走 `MultiBillScriptRunner.runTask → PfUtilClient.runBatchNew` | **PF `SAVE`** | 静态核实 |
| 211 | 收回 `UnCommit`<br>`1001Z810000000004TOG` | `unsendApproveAction` = `nc.ui.so.m30.billui.action.SaleOrderUnSendApproveAction`；`model`、`editor`、`filledUpInFlow=true`、`actionName=UNSAVE`、`billType=30`、`validationService=powerunsendappvalidservice`（`uncommit`）、`tpaProgressUtil`（`saleorder_config.xml:881-895`） | `extends UnCommitScriptAction`，只重写 `isActionEnable`（要求 `approver` 为空且状态 AUDITING）。**注意**：基类构造器只 `putValue("Code","UnCommit")`，**不调用 `setActionName`**；PF 动作名来自 bean 属性 `actionName=UNSAVE`（经 `AbstractScriptExcAction.setActionName → PFlowContext.setActionName`） | 同上 PF 链 | **PF `UNSAVE`** | 静态核实 |
| 212 | 审批 `Approve`<br>`1001Z810000000004TO2` | `approveAction` = `nc.ui.so.m30.billui.action.SaleOrderApproveAction`；`model`、`editor`、`filledUpInFlow=true`、`actionName=APPROVE`、`billType=30`、`validationService=powerapprovevalidservice`（`approve`）、`tpaProgressUtil`（`saleorder_config.xml:897-911`）；组装 `approveActionGroup`（含 `queryAuditFlowAction`，929-937） | `extends ApproveScriptAction`，只重写 `isActionEnable`（NOT_EDIT 且状态 FREE/AUDITING）。基类构造器 `setActionName("APPROVE")`；`doBeforAction` 先调基类再 `flowContext.setUserId(登录用户)` | 同上 PF 链 → `actionAboutApproveflow → checkWorkitemWhenApprove`（审批窗口）→ `executeBusinessPlugin` → `IplatFormEntry.processAction(APPROVE,…)` | **PF `APPROVE`** | 静态核实（弹窗类见 §1.3） |
| 213 | 取消审批 `UnApprove`<br>`1001Z810000000004TOO` | `unApproveAction` = `nc.ui.so.m30.billui.action.SaleOrderUnApproveAction`；`model`、`editor`、`filledUpInFlow=true`、`actionName=UNAPPROVE`、`billType=30`、`validationService=powerunapprovevalidservice`（`unapprove`）、`tpaProgressUtil`（`saleorder_config.xml:913-927`） | `extends UNApproveScriptAction`，只重写 `isActionEnable`（AUDIT/AUDITING/NOPASS 且 `approver` 非空）。基类构造器 `setActionName("UNAPPROVE")` | 同上 PF 链 | **PF `UNAPPROVE`** | 静态核实 |
| 214 | 冻结 `Freeze`<br>`1001Z810000000004TP5` | `freezeAction` = `nc.ui.so.m30.billui.action.assist.FreezeAction`；仅 `model=ManageAppModel`（`saleorder_config.xml:1016-1019`）；挂在 `browAstActionGroup`（辅助功能菜单，1553-1581，见 1565） | `extends nc.ui.uif2.NCAction`，非 PF 动作。`doAction`：`new SaleOrderVO[]{选中数据}` → `NCLocator.lookup(ISaleOrderCloseManageMaintain).freezeSaleOrder(vos)` → `ClientBillCombinServer.combine(原VO, 返回VO)` → `AbstractAppModel.directlyUpdate`；捕获 `nc.vo.pub.BusinessException` 后 `ExceptionUtils.wrappException`。`isActionEnable` 要求状态 `AUDIT` | 直接服务 RPC：`ISaleOrderCloseManageMaintain.freezeSaleOrder(SaleOrderVO[])`（NCLocator 查找，非 `IplatFormEntry`） | 无 PF action；服务 RPC `freezeSaleOrder` | 静态核实 |
| 215 | 解冻 `UnFreeze`<br>`1001Z810000000004TOE` | `unFreezeAction` = `nc.ui.so.m30.billui.action.assist.UnFreezeAction`；仅 `model`（`saleorder_config.xml:1020-1023`）；同在辅助功能菜单（1566） | `extends NCAction`。`doAction` 为 **`while(continueFlag)` 重试循环**：每次 `businessCheckMap=new HashMap()`；`getServcie().unFreezeSaleOrder(vos, businessCheckMap)`；异常时 `ExceptionUtils.unmarsh`，若为 `IResumeException` 且 `getBusiExceptionType()==BusinessCheck.ATPCheck.getCheckCode()` → `MessageDialog.showYesNoDlg`（资源 4006011_0/04006011-0244，title=ATP 不足信息）→ 用户选继续后 `businessCheckMap.put(ATPCheck.getCheckCode(), Boolean.FALSE)` 再调一次（即**第二次调用带“跳过 ATP 检查”标记**）。`isActionEnable` 要求状态 `FREEZE` | 直接服务 RPC：`ISaleOrderCloseManageMaintain.unFreezeSaleOrder(SaleOrderVO[], Map<String,Boolean>)` | 无 PF action；服务 RPC `unFreezeSaleOrder` | 静态核实（ATP 分支保留要求见 §2） |
| 216 | 整单关闭 `BillClose`<br>`1001Z810000000004TP1` | `closeAction` = `nc.ui.so.m30.billui.action.assist.SaleOrderCloseAction`；`model`、`singleBillService=<bean class="nc.ui.so.m30.closemanage.model.M30CloseBillCloseService"/>`（`saleorder_config.xml:1024-1030`）；同在辅助功能菜单（1568） | `extends NCAction implements IMultiReturnObjProcessor, ProgressActionInterface`。`doAction`：`MessageDialog.showInputDlg`（资源 04006011-0233/0234）取**关闭原因** → `getSelectedOperaDatas`；对 `getChildrenVO()==null` 的单据用 `ISaleOrderMaintain.querySaleorder(ids)` 回查并 `setChildrenVO`（**客户端的整单行补齐**）→ `setCloseOpenReason`：逐 `SaleOrderBVO.setVclosereason(关闭原因)` → 多张走 `MultiBillTaskRunner.runTask`，单张走 `singleBillService.operateBill(vo)` → `processReturnObjs`（`directlyUpdate`） | `M30CloseBillCloseService.operateBill(SaleOrderVO)` → `ISaleOrderCloseManageMaintain.closeSaleOrder(new SaleOrderVO[]{vo})` | 无 PF action；服务 RPC `closeSaleOrder` | 静态核实 |

> 「保存并提交」`saveandsendApproveAction` = `SaleOrderSaveAndSendAction`，构造注入 `saveAction` + `sendApproveAction`（`saleorder_config.xml:869-873`，挂在 `actionsOfCard.editActions`，1520）。覆盖 `sendApproveAction` 会同时作用于该复合入口的提交段。

### 1.2 客户端到 PF 的公共链（静态核实）

`SaleOrder*Action` 的父类链（`uipubapp_platformextLevel-1.jar`，SHA-256 `EF77D162…CF4BC15E2`）：

1. `AbstractScriptExcAction.doAction` → 循环 `fillUpContext(flowContext)` / `procFlow(flowContext)`；`isResume(IResumeException)` 为真则重试。
2. `procFlow`：单张 → `PFlowProxy.runAction(PFlowContext)`；批量 → `PFlowProxy.runActionBatch` 或 `MultiBillScriptRunner.runTask()`。
3. `PFlowProxy.runAction`：校验 `actionName` 与 `billType|trantype` 非空后调用
   `PfUtilClient.runAction(parent, actionName+userId, (trantype!=null?trantype:billType), billVo, userObj, strBeforeUIClass, checkVo, new HashMap(eParam))`。
4. `PfUtilClient.runAction`（`uiriart_riartplatformLevel-1.jar!nc/ui/pub/pf/PfUtilClient.class`）：
   - offset 64/127/163：`executeBusinessPlugin(...)`、`actionAboutApproveflow(...)`、`actionAboutWorkflow(...)`（审批弹窗与流程检查）；
   - offset 344：`IplatFormEntry.processAction(String actionName, String billType, WorkflownoteVO, AggregatedValueObject, Object userObj, HashMap eParam)`。
5. 批量：`MultiBillScriptRunner` 先 `eParam.put("worknote", worknote)`，再
   `PfUtilClient.runBatchNew(parent, actionName+userId, trantype|billType, vos, userObjs, strBeforeUIClass, eParam)` → `IplatFormEntry.processBatch(...)`。
6. `WorkflownoteVO` 由 `eParam["worknote"]` 携带；`PFlowContext` 会被清空（`clear()`），因此**不能只在 `beforeDoAction` 里塞参数**（与开发计划 §公共接入 4 一致）。
7. 服务端 PF 入口实现类：`riart_riartplatformLevel-1.jar!nc/bs/pub/pf/pfframe/PlatFormEntryImpl.class`（未展开方法体 → 未核实，见 §4-1）。

### 1.3 审批「确定」的最终提交点（D 项）

- **点击链**：`approveAction.doAction`（`ScriptPFlowAction.doAction`）→ `AbstractScriptExcAction.doAction` → `procFlow` → `PFlowProxy.runAction` → `PfUtilClient.runAction` → `actionAboutApproveflow`（`PfUtilClient` 方法，bytecode 偏移 127）→ `checkWorkitemWhenApprove(Container,String,String,Object,HashMap,int)`（`PfUtilClient` 私有静态方法，偏移 172 调用点）。
- **审批窗口类（`javap` 常量池可证，`PfUtilClient` 内引用）**：`nc.ui.pf.workitem.WFStartDispatchDialog`、`nc.ui.pf.workitem.ApproveWorkitemAcceptDlg`、`nc.ui.pf.workitem.ApproveFlowDispatchDialog`、`nc.ui.pf.workitem.BatchApproveWorkitemAcceptDlg`（批量）、`WorkflowWorkitemAcceptDlg`；均以 `WorkflownoteVO` 构造并以 `showModal()` 返回结果。
- **最终提交点**：窗口「确定」返回后，`PfUtilClient.runAction` 在偏移 344 调 `IplatFormEntry.processAction("APPROVE", 30/交易类型, worknote, SaleOrderVO, userObj, eParam)`。客户端可插入的最后一个点就是 `PfUtilClient.runAction` 之前（即 `PFlowProxy.runAction` / 动作 `doAction`）。
- **审批意见 / 决议 / 工作项参数载体（静态核实，`pubriart_riartplatformLevel-1.jar!nc/vo/pub/workflownote/WorkflownoteVO.class`）**：
  - 审批意见：`WorkflownoteVO.checknote`
  - 决议/审批结果：`WorkflownoteVO.approveresult`（String）、`approvestatus`（Integer）、`ischeck`
  - 工作项：`WorkflownoteVO.pk_wf_task`；审批流：`pk_checkflow`
  - 审批人：`checkman` / `checkname`；消息：`messagenote`；指定审批人扩展：`extApprovers`；消息上下文：`msgContext`（`nc.vo.wfengine.pub.WorkitemMsgContext`）
  - 业务参数：`PFlowContext.eParam`（`HashMap`，键含 `worknote`/`silently`/`nosendmessage`/`besideapprove`/`notechecked`）；用户对象：`nc.vo.pubapp.pflow.PfUserObject{businessCheckMap, userObject}`
- **策略口径**：策略与权限应绑定**真实按钮编码 `Approve`**（PK `1001Z810000000004TO2`、btncode `Approve`、action code `Approve`），**不要虚构「确定」按钮**；「确定」只是审批窗口的模态返回，不是注册按钮。

## 2. F 项：实现落点建议

原则（与 `SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md` 一致）：**客户端覆盖层只做“签名/审计前置拦截 + 原生调用复用”**；不新建 PF 调用、不改 `actionName`/`actionCode`/`billType`，签名取消即 `return`，签名通过才 `super.doAction(...)`（PF 五项）或调用原服务方法（冻结/解冻/关闭三项）。覆盖方式是复制 `saleorder_config.xml` 为 `QdSaleOrderConfig.xml`（HOME 已部署同名文件于 `qd_esign_uapbd_client.jar`），仅替换相应 bean 的 `class`，bean id 与 action code 保持不变。

| # | 客户端覆盖层改哪个 bean/动作 | 服务端最终原生方法 | 核实 |
| --- | --- | --- | --- |
| 209 Delete | 覆盖 `deleteAction`（`SaleOrderDeleteAction`）；保留 `actionName=DELETE`、`billType=30`、`validationService`，保留余额提示→确认→`super.doAction` | PF `DELETE` → `PlatFormEntryImpl.processAction` →（服务端动作驱动，见 §4-1） | 客户端静态核实；服务端未核实 |
| 210 Commit | 覆盖 `sendApproveAction`（`SaleOrderSendApproveAction`，`actionName=SAVE`）；**同一 bean 被 `saveandsendApproveAction` 复用**，一次覆盖同时管住「保存并提交」的提交段 | PF `SAVE` → `PlatFormEntryImpl.processAction` | 客户端静态核实；服务端未核实 |
| 211 UnCommit | 覆盖 `unsendApproveAction`（`actionName=UNSAVE` 来自配置，类内不写死；覆盖 class 时必须显式保留该属性） | PF `UNSAVE` → `PlatFormEntryImpl.processAction` | 客户端静态核实；服务端未核实 |
| 212 Approve | 覆盖 `approveAction`（`SaleOrderApproveAction`，`actionName=APPROVE`）；拦截点只能在其 `doAction` 之前，审批窗口与 `processAction(APPROVE)` 由框架完成 | PF `APPROVE` → `PlatFormEntryImpl.processAction`；审批窗口 `nc.ui.pf.workitem.*` | 客户端静态核实；服务端未核实 |
| 213 UnApprove | 覆盖 `unApproveAction`（`actionName=UNAPPROVE`） | PF `UNAPPROVE` → `PlatFormEntryImpl.processAction` | 客户端静态核实；服务端未核实 |
| 214 Freeze | 覆盖 `freezeAction`（`nc.ui.so.m30.billui.action.assist.FreezeAction`）；保留 `ClientBillCombinServer.combine` + `directlyUpdate` 才能刷新界面 | `ISaleOrderCloseManageMaintain.freezeSaleOrder(SaleOrderVO[]) → SaleOrderVO[]` → `SaleOrderCloseManageMaintainImpl.freezeSaleOrder` → `BillFreezeState` → `SaleOrderStateMachine.setState`（写库并回查 View） | **两侧均静态核实** |
| 215 UnFreeze | 覆盖 `unFreezeAction`；**必须原样保留 `businessCheckMap` + ATP 继续/取消分支**（第二次调用 `map.put(ATPCheck.getCheckCode(), Boolean.FALSE)` 才会跳过 ATP 检查），不得默认传入跳过参数 | `unFreezeSaleOrder(SaleOrderVO[], Map<String,Boolean>) → SaleOrderVO[]` → `examATPCheck(map)`（IC 启用时）`SaleOrderVOATPBeforeRule.process` → `BillAuditState` → `SaleOrderStateMachine.setState` → `SaleOrderVOATPAfterRule.process` | **两侧均静态核实** |
| 216 BillClose | 覆盖 `closeAction`；保留关闭原因输入框、`ISaleOrderMaintain.querySaleorder` 补行、`setClosereason` 逐行写 `vclosereason`、多单 `MultiBillTaskRunner`、单张 `M30CloseBillCloseService.operateBill`。**签名事由与关闭原因必须分开传** | `closeSaleOrder(SaleOrderVO[]) → SaleOrderVO[]` → `AggVOUtil.getDistinctItemFieldArray(csaleorderbid/csaleorderid)` → `checkOrderDownBillAllApprove(csaleorderid[],csaleorderbid[])` → `updateCloseOpenReason(vos)`（`VOUpdate.update(children,["vclosereason"])` 持久化关闭原因）→ `BillCloseState` → `SaleOrderStateMachine.setState` | **两侧均静态核实** |

### 2.1 三项直接服务的签名（静态核实）

接口 `pubso_salesorder.jar!nc/itf/so/m30/closemanage/ISaleOrderCloseManageMaintain.class`（SHA-256 `B3FCAC67…DBAAD0B89`）：

```java
public abstract SaleOrderVO[] closeSaleOrder(SaleOrderVO[]) throws BusinessException;
public abstract SaleOrderVO[] freezeSaleOrder(SaleOrderVO[]) throws BusinessException;
public abstract SaleOrderVO[] unFreezeSaleOrder(SaleOrderVO[], Map<String,Boolean>) throws BusinessException;
public abstract SOParameterVO[] openSaleOrder(SaleOrderVO[], boolean) throws BusinessException;
// 另有 closeSaleOrderInvoice/Out/Row/Send/Settle、openSaleOrder*、querySaleOrderViewVO
```

实现 `so_salesorder.jar!nc/impl/so/m30/closemanage/SaleOrderCloseManageMaintainImpl.class`（jar SHA-256 `4A61AFAE…1082544`，1724 行 javap）：

- `closeSaleOrder`：`AggVOUtil.getDistinctItemFieldArray(vo[],"csaleorderbid",String.class)` 与 `"csaleorderid"` → `checkOrderDownBillAllApprove(String[],String[])` → `updateCloseOpenReason(SaleOrderVO[])` → `new BillCloseState()` → `setState(IState,SaleOrderVO[])`。
- `freezeSaleOrder`：`new BillFreezeState()` → `setState(...)`。
- `unFreezeSaleOrder`：`examATPCheck(Map)` → `SysInitGroupQuery.isICEnabled()` 为真则 `new SaleOrderVOATPBeforeRule().process(vos)` → `new BillAuditState()` → `setState(...)` → `new SaleOrderVOATPAfterRule().process(vos)`。
- `setState(IState,SaleOrderVO[])`：`BillTransferTool.getOriginBills()`（丢弃客户端 VO，改用库里原单）→ `TimeLog` → `new SaleOrderStateMachine().setState(state, originBills)` → 按 `csaleorderbid` 回查 `ViewQuery(SaleOrderViewVO).query` → `ViewToBillConvertor.convert` 返回。
- `updateCloseOpenReason(SaleOrderVO[])`：收集全部 `getChildrenVO()`，`VOUpdate.update(children, ["vclosereason"])` —— 即关闭原因最终以 `so_sale_order_b.vclosereason` 持久化（字段名取自字节码常量 `vclosereason`）。
- `M30CloseBillCloseService.operateBill(SaleOrderVO)`：`new SCMResumeExceptionInvoker().getService(ISaleOrderCloseManageMaintain)` → `closeSaleOrder(new SaleOrderVO[]{vo})` → `filterReturnbvo(原VO, 返回VO)`（行数不等时按 `csaleorderbid` 用 `AggVOUtil.createItemMap` 重建 children 再 `setChildrenVO`）；`operateBill(Object)` 为桥接重载。
- 客户端 `SaleOrderCloseAction` 与服务端共同构成「整单行补齐」：客户端 `childrenVO==null` 时用 `ISaleOrderMaintain.querySaleorder(String[])` 补，服务端 `setState` 再次以库内原单为准。

## 3. PfUtilClient / 插件 / 外发核对结果（E 项）

### 3.1 PfUtilClient 版本哈希：**一致**

- 位置（HOME 内唯一命中，全量 3711 个 JAR 索引确认无第二份）：`modules\riart\client\lib\uiriart_riartplatformLevel-1.jar!nc/ui/pub/pf/PfUtilClient.class`（条目 49119 字节）
- 现算 SHA-256：`1448DB245D5179F80268369D754CAD0336FE4028CEA52EF53319CFB859826689`
- 试点常量（`SaleOrderPfPreparation.SUPPORTED_PF_CLIENT`）：`1448DB245D5179F80268369D754CAD0336FE4028CEA52EF53319CFB859826689`
- **结论：完全一致，试点 `verifyNativeContract()` 在本机 HOME 可通过版本闸门。** 外层 JAR SHA-256：`32268D4974F80B0B49C6242B6459E72F02B4F800C0198C3F5161B0C16F573EE1`。
- 注意：一致性只证明「本机客户端该类的字节码版本匹配」，不证明现场客户端加载的是同一份（远程客户端缓存/远程类加载仍需会话核实，见 §4-4）。

### 3.2 40060301 节点参数注册（DB 快照 2026-11-07，`site-evidence/2026-11-07/node_ui_params.json` / `all_node_ui_params.json`）

| PK_PARAM | PARAMNAME | PARAMVALUE |
| --- | --- | --- |
| `1001Z810000000000UV7` | `BeanConfigFilePath` | `nc/ui/so/m30/billui/model/saleorder_config.xml` |
| `1001A9100000009NMGNC` | `PluginBeanConfigFilePath` | `nc/ui/so/qd/esign/config/saleorder_esign_probe.xml` |
| `1002Z810000000Z0C4QC` | `PluginBeanConfigFilePath_Industry0_tempsave` | `nc/ui/so/m30/billui/model/saleorder_tempsave_config.xml` |

判定：

1. **`PluginBeanConfigFilePath`（V1 probe）注册存在，但目标文件在本机 HOME 不存在 → 本机不生效（悬空参数）。**
   - 3711 个 JAR 条目中 `saleorder_esign_probe` 命中 **0**；
   - HOME `nchome` 全盘文件名扫描 `saleorder_esign_probe.xml` 命中 **0**（32 s）；
   - 项目工作区（`E:\02-Project\...`）中亦无该文件。
   - → 若客户端仍登记该参数，加载时会因找不到资源而跳过（具体异常/降级行为未核实，见 §4-4）。这与 `SALE_ORDER_SAVE_PILOT.md:13` 的早期结论一致。
2. **`PluginBeanConfigFilePath_Industry0_tempsave` 注册且目标文件存在 → 可加载。**
   - `uiso_salesorder.jar!nc/ui/so/m30/billui/model/saleorder_tempsave_config.xml`（3502 字节，SHA-256 `CA1687057286B4A2AEC80F06215DB0B8C314C88D0D145596571AFF6C535D097F`）。
   - 内容仅为**暂存**按钮扩展：`addTempSaveAction`(`InsertActionInfo`→`saveTempAction`)、`SaveTemporaryAction`、`SaveTempActionInterceptor`、`CardToolBarPnlMediator`、`SaleOrderTempDataLoader`、`SaveInterceptor4TempSave` 等，**不拦截**上述 8 项动作。
   - → 该参数与八项签名无关，但覆盖层必须保留它（换 `BeanConfigFilePath` 时不能丢 `PluginBeanConfigFilePath_Industry0_tempsave`）。

### 3.3 是否存在「向 OA 发消息的审批后置插件」

结论：**在 2026-11-07 快照中未发现针对 m30 审批动作的后置插件注册；也没有证据表明存在经 `pub_pluginitem` 向 OA 发消息的审批后置插件。** 依据与观察位置如下：

1. `site-evidence/2026-11-07/plugins.json`（只读查询 `pub_pluginitem`，`vmodulename in ('uapbd','pu','so','ic','purp')`）共 **4 行**，`so` 模块 4 行中与 m30 相关的只有 1 行：
   | PK_PLUGINITEM | 组件 | 扩展点 | 事件 | 规则类 | 扩展模块 |
   | --- | --- | --- | --- | --- | --- |
   | `1001Z81000000000PMS1` | so/m30 | `nc.vo.so.m30.plugin.BPPlugInPoint.Delete` | after / addAfter | `nc.pubimpl.so.m30.pms.rule.M30_Del2PmsRule` | `opcnc` |
   这是**删除后**发 PMS 消息的规则，不是审批后置，也不是 OA。
2. 服务端扩展点类的真实形态（静态核实，`so_salesorder.jar!nc/bs/so/m30/plugin/`）：
   - `BP30PlugInPoint` 枚举常量（`<clinit>` 字符串）含 `DeleteBP→nc.vo.so.m30.plugin.BPPlugInPoint.Delete`、`InsertBP`、`UpdateBP`、`SendBP→…Send`、`UnSendBP→…UnSend`、`Revise*BP` 等 → 上述 PMS 注册的扩展点字符串**是有效的**；
   - `Action30PlugInPoint` 枚举常量含 `SaveAction/InsertAction/DeleteAction/UpdateAction/SendApproveAction/ApproveAction/UnApproveAction…`，对应点字符串如 `nc.vo.so.m30.plugin.ActionPlugInPoint.Approve`、`…SendApprove`、`…UnApprove` → **审批类后置（若现场配置）应落在 `pub_pluginitem` 中 `vextendpointname like '%ActionPlugInPoint.Approve%'` 且 `veventtype='after'` 的行**，快照中无此类行。
   - 另：该 PMS 规则的实现类 `nc.pubimpl.so.m30.pms.rule.M30_Del2PmsRule` 在本机 3711 个 JAR 中**未找到**（`opcnc` 模块 JAR 内也无任何 `pms` 条目）→ 该注册在本机可能无法加载（未核实，见 §4-5）。
3. OA 在平台层确实存在：`nchome\lib\seeyon-ctp-core.jar`（致远 CTP 客户端 SDK，另有 `modules\so\client\lib\seeyon-ctp-core.jar`）；PF 侧消息能力通过 `WorkflownoteVO.msgContext`（`nc.vo.wfengine.pub.WorkitemMsgContext`）、`nc.message.vo.AttachmentVO`（`PfUtilClient.updateAttachment2DocServer`）等承载。**审批通知的发送主体是工作流/PF 引擎，不在销售订单的 bean 或插件里**；若现场的 OA 通知由 PF 业务插件（`nc.ui.pub.pf.IPFClientBizProcess` / 服务端 `IPFClientBizProcessBS` 实现）发出，其类由 `billtype2` 配置决定，而非 `pub_pluginitem`（观察位置见 §4-6）。

## 4. 未核实项与观察位置

| # | 未核实项 | 现有证据 | 下一步观察位置 |
| --- | --- | --- | --- |
| 1 | 五个 PF 动作在服务端的最终实现类/方法（DELETE/SAVE/UNSAVE/APPROVE/UNAPPROVE 的 action driver 与业务规则） | 客户端链已到 `IplatFormEntry.processAction`；服务端入口类 `nc/bs/pub/pf/pfframe/PlatFormEntryImpl.class` 已定位但未展开 | `riart_riartplatformLevel-1.jar!nc/bs/pub/pf/pfframe/PlatFormEntryImpl.class`（javap -c）；真实会话 PF 日志；`so_salesorder.jar` 内 `nc/bs/so/m30/plugin/Action30PlugInPoint` 对应动作实现 |
| 2 | `fs_delete` 的真实归属 | 与 `fs_browse/fs_download/fs_rename/fs_upload` 同组、PK 前缀 `1002…`；`uiso_salesorder.jar` 无 `fs_*` 字符串与 bean；订单页附件入口为 `docManageAction→SOManageDocumentAction` | 客户端「附件管理」面板实际按钮（`SOManageDocumentAction` 打开的 FileManager）；`sm_butnregister` 中同组按钮的 `parent_id` 所属子功能；真实会话点击验证 |
| 3 | 按钮 btncode 与模板实际绑定的运行时确认 | action code 与 btncode 在 8 个类构造器中逐一相等（静态）；模板不在 JAR 内 | 客户端单据模板（DB 模板表，如 `pub_billtemplet*`）中按钮的 action/bean 映射；真实会话工具栏加载日志；`actionsOfList`/`actionsOfCard`（`saleorder_config.xml:1464-1531`）实际注入的 bean |
| 4 | `saleorder_esign_probe.xml` 是否被远程客户端缓存或其它加载路径使用 | 本机 HOME 与其 JAR 均无该文件（0 命中） | 现场客户端缓存目录与 `PluginBeanConfigFilePath` 资源解析日志；`nc.ui.pubapp.uif2app` 插件加载异常日志 |
| 5 | `M30_Del2PmsRule` 实现类缺失导致的运行期行为 | 在 HOME 3711 个 JAR 未找到该类；`opcnc` 模块无 `pms` 条目 | 服务端插件加载日志；`pub_pluginitem` 运行期命中情况；opcnc 服务端 JAR 清单复核 |
| 6 | 审批后置/外发插件（OA/APS）是否在现场被新增 | 2026-11-07 `pub_pluginitem` 快照无 `%ActionPlugInPoint.Approve%`、无 OA 规则 | `pub_pluginitem`（`vcomponentname='m30'`、`vextendpointname like '%ActionPlugInPoint.Approve%'`）；`billtype2` 业务插件配置；工作流消息表与 OA 接口日志 |
| 7 | 审批窗口「确定」后的真实参数落库（`checknote/approveresult/pk_wf_task` 的最终值） | 载体字段已静态核实 | 真实会话抓取 `IplatFormEntry.processAction` 入参（PF 日志/调试代理）；`wf_*` 工作流表 |
| 8 | 项目源码 `uapbd/src/client/nc/uapbd/qd/esign/SaleOrderPfPreparation.java` 的正文 | 取证前期该文件在目录枚举中可见，`Select-String` 曾命中（`SUPPORTED_PF_CLIENT` 常量、`PfUtilClient.isSuccess()`、`verifyNativeContract()`）；取证后期该目录读取报「路径不存在」，同目录其它文件亦然（疑被其它进程/同步重建） | 项目 git 工作区或 IDE 索引复核；`build/git-onboarding-864b1051`、`build/signature-retry-isolated-d91bd651` 两份副本；`sales-order-990-release/uapbd/src/...` |

## 5. 关键命令与哈希

### 5.1 命令（均为只读；PowerShell 7）

```powershell
# 1) JAR 只读列条目
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z=[IO.Compression.ZipFile]::OpenRead('E:\01-Home\NC65home\QuDuTest\nchome\modules\so\client\lib\uiso_salesorder.jar')
$z.Entries | Where-Object { $_.FullName -match '\.xml$' } | Select-Object FullName,Length
$z.Dispose()

# 2) 条目复制到工作区（不改 JAR）
[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$dest,$true)

# 3) 反编译（HOME 自带 IBM JDK）
& 'E:\01-Home\NC65home\QuDuTest\nchome\ufjdk\bin\javap.exe' -p -c -classpath <extract-root> <class>

# 4) 全量 JAR 索引（3711 个 JAR / 549298 条目，27 s）
#    输出：s0-evidence\jar-index.tsv（条目名<TAB>JAR 路径），用于定位类与资源

# 5) 哈希
Get-FileHash <file> -Algorithm SHA256
```

### 5.2 JAR 哈希（SHA-256）

| JAR | SHA-256 | 字节 |
| --- | --- | --- |
| `modules\so\client\lib\uiso_salesorder.jar` | `359F4DD209F48B63966C1D9853EAA9FA3ABD6D97F785197CC0520718E5150D07` | 904074 |
| `modules\pubapp\client\lib\uipubapp_platformextLevel-1.jar` | `EF77D162738513F9D028DF5E814711C0851220F70DE091996567BC3CF4BC15E2` | 1173460 |
| `modules\riart\client\lib\uiriart_riartplatformLevel-1.jar` | `32268D4974F80B0B49C6242B6459E72F02B4F800C0198C3F5161B0C16F573EE1` | 2614522 |
| `modules\so\lib\pubso_salesorder.jar` | `B3FCAC679519B48C485F34CC3F8AA6F1653AE8EF95C4FFAC936DF13DBAAD0B89` | 333127 |
| `modules\so\META-INF\lib\so_salesorder.jar` | `4A61AFAE9CD8E5E21B00023F9EBBC8FCD260F716596A52DA2F873B7981082544` | 758011 |
| `modules\uapbd\client\lib\qd_esign_uapbd_client.jar`（含 `QdSaleOrderConfig.xml`） | `78D724A88B5894C43777721763722FB727FA1310AB60839FDE3D77FC725858AE` | 191052 |
| `modules\so\lib\qd_esign_so_public.jar` | `70528FC67FD9D3926FDF3E63CADC4C7B9084CCC55FD45E1EBE10D6B9A31C18A5` | 3174 |
| `modules\so\META-INF\lib\qd_esign_so_private.jar` | `7D0BEC07A9EC45B2D25B597765BEF62C678437E9FFBEDBB7AE641F4529108390` | 8391 |

### 5.3 关键类/资源哈希

| 对象 | SHA-256 |
| --- | --- |
| `uiriart_riartplatformLevel-1.jar!nc/ui/pub/pf/PfUtilClient.class` | `1448DB245D5179F80268369D754CAD0336FE4028CEA52EF53319CFB859826689`（与试点常量一致） |
| `uiso_salesorder.jar!nc/ui/so/m30/billui/model/saleorder_config.xml` | `12F85390729D4652B76752CC405D2BDDA062799ABD44BE304EB1541E0D1475B3`（66981 字节 / 1586 行） |
| `uiso_salesorder.jar!nc/ui/so/m30/billui/model/saleorder_tempsave_config.xml` | `CA1687057286B4A2AEC80F06215DB0B8C314C88D0D145596571AFF6C535D097F`（3502 字节） |
| `uiso_salesorder.jar!nc/ui/so/m30/closemanage/model/m30close_config.xml` | `191BDFA1EB23973B2478AC1067594BBDF0EF46F23A4519530EEB507F793F536D`（16386 字节） |

### 5.4 本目录内可复用的证据文件

- `s0-evidence/jar-extract/uiso_salesorder/`：`uiso_salesorder.jar` 全量只读提取副本（418 文件，含 `saleorder_config.xml`、`m30close_config.xml`、全部动作类 `.class`）
- `s0-evidence/server-extract/`：`pubso_salesorder`（接口）、`so_salesorder`（关闭实现、m30 扩展点）、`platform`（pflow 动作）、`riart`（`PfUtilClient`）、`pubapp`（`PfUserObject`）
- `s0-evidence/javap/`：上述类的 `javap -p -c` / `javap -p` 输出（UTF-8）
- `s0-evidence/jar-index.tsv`：HOME 全量 JAR→条目索引（3711 JAR / 549298 行 / 69.5 MB），定位类与资源用

> 复核提示：`saleorder_config.xml` 的行号即 JAR 条目原文行号（提取为逐字节副本，未重排）。
