# 发货单维护（40060402）原生证据 · 逐按钮静态取证

> 取证性质：**只读**。未修改任何既有源码/文档/数据库，未运行构建，未启动 NC，未连接数据库、未做任何运行期联调。
> 证据全部来自 `E:\01-Home\NC65home\QuDuTest\nchome` 下的 **jar 字节码**（javap）、**客户端配置 XML**、**BMF 元数据**、以及本仓库 `config/qd-esign/site-20261107/` 的 **现场注册 CSV**。
> 原始 javap 输出已转存为 UTF-8（内容未改；javap 直出为系统代码页 GBK），见第 6 节索引。

## 0. 证据等级约定

| 等级 | 含义 |
| --- | --- |
| A（字节码） | 由 jar 内 class 的 `javap -p -c` 字节码直接可见：类、父类、方法调用顺序、常量、字符串资源 ID |
| B（配置/元数据） | 由 `delivery_config.xml`、`delivery.bmf`、现场 CSV 直接可见 |
| C（推断） | 由 A/B 组合推导，未经运行期验证；文中均显式标注「推断」 |

**UI 中文文案一律未核实**：对话框标题/正文只以资源 ID 形式存在（如 `4006002_0 / 04006002-0108`），NC65 的多语资源存放在数据库（`MD_*` 多语表）中，jar/配置里没有 properties 文件（已确认 `uiso_delivery.jar`、`pubso_delivery.jar` 无 `.properties`，`modules/so` 下 41 个 `.bmf` 中也没有语言资源）。因此本文只记录“哪个资源 ID + 哪种对话框”，不臆测中文原文。

## 1. 基本信息

| 项 | 值 | 证据 |
| --- | --- | --- |
| 功能节点 | `40060402` 发货单维护 | 现场 CSV；配置 `billType=4331` |
| 节点 ID | `QD-NODE-97231D8D3F118969` | `node-bindings.csv` |
| 注册功能主键 | `1002Z81000000000G83T`（注册名：发货单维护） | `node-bindings.csv` |
| 上级主键 / 模块 | `1002Z810000000000RS3` / `4006` | `node-bindings.csv` |
| 组织类型 | `TRAFFICORGTYPE000000`（物流组织） | `node-bindings.csv` |
| 客户端配置 | `nc/ui/so/m4331/billui/model/delivery_config.xml` | `node-bindings.csv` 的 `beanConfig` |
| 配置实体来源 | `uiso_delivery.jar!nc/ui/so/m4331/billui/model/delivery_config.xml` | 解压件 `build/native-configs-other-nodes/delivery_config.xml`（50,411 字节） |
| 单据类型 | 基本单据类型 `4331` | 配置行 713/728/763/779/805/821 `billType=4331` |
| 注册按钮数 / 节点映射状态 | 40 / `NODE_REGISTRATION_CONFIRMED_RPC_PENDING`，`validated=N` | `node-bindings.csv` |
| 服务接口（辅助功能） | `nc.itf.so.m4331.IDeliveryAssitFunc`（`modules/so/lib/pubso_delivery.jar`） | javap 接口清单 |
| 服务实现（辅助功能） | `nc.impl.so.m4331.DeliveryAssitFuncImpl`（`modules/so/META-INF/lib/so_delivery.jar`） | javap；委托给 `nc.impl.so.m4331.action.assist.{DeliveryBillCloseAction, DeliveryBillOpenAction, DeliveryRowCloseAction, DeliveryRowOpenAction}` |
| 服务接口（维护） | `nc.itf.so.m4331.IDeliveryMaintain`（`pubso_delivery.jar`），实现 `nc.impl.so.m4331.DeliveryMaintainImpl`（`so_delivery.jar`） | javap |
| VO | `nc.vo.so.m4331.entity.DeliveryVO / DeliveryHVO / DeliveryBVO / DeliveryUserObject`（`pubso_delivery.jar`） | javap |
| 物理表（表头/表体） | `so_delivery`（`DeliveryHVO`）/ `so_delivery_b`（`DeliveryBVO`） | `modules/so/METADATA/so_delivery/delivery.bmf` 中 `tableName` 属性（另含 `so_delivery_check`） |
| 主键列 | 表头 `cdeliveryid`（`isKey=true`，char20，typing UFID）；表体 `cdeliverybid`（`isKey=true`，char20） | BMF 属性定义 + `DeliveryHVO.CDELIVERYID`/`DeliveryBVO.CDELIVERYBID` 常量 |

### 1.1 两类执行通道（贯穿全节点）

本节点 10 个按钮分属**两条完全不同的执行通道**，这是适配时最容易出错的分界线：

1. **PF（流程平台）脚本动作**：`Save / Delete / Commit / UnCommit / Approve / UnApprove` 六个按钮。
   链路：`*ScriptAction.doAction` → `AbstractScriptExcAction.doAction` → `procFlow(PFlowContext)` → `nc.ui.pubapp.uif2app.model.PFlowProxy.runAction(PFlowContext)` → `nc.ui.pub.pf.PfUtilClient.runAction(container, actionName+userId, billType, billVO, userObj, strBeforeUIClass, checkVO, eParam)`，返回后 `PfUtilClient.isSuccess()` 决定 `processReturnObj` 走成功/失败分支。
   证据：`parent-AbstractScriptExcAction.javap.txt` 第 357–498 行；`bridge-PFlowProxy.javap.txt` 第 20–73 行；`bridge-PfUtilClient-api.txt`。
2. **直连 RPC（不走 PF）**：`BillClose / BillOpen / OutClose / OutOpen` 四个辅助功能。
   链路：`NCLocator.getInstance().lookup(IDeliveryAssitFunc.class)` → 直接调用 `closeDelivery(SOParameterVO)` / `openDelivery(SOParameterVO)` / `closeDeliveryRows(DeliveryVO,int[])` / `openDeiveryRows(SOParameterVO,int[])`。
   证据：`action-DeliveryCloseAction.javap.txt` 第 98–104 行（`getServcie()`）、`action-DeliveryRowCloseAction.javap.txt` 第 49–72 行、`action-DeliveryRowOpenAction.javap.txt` 第 60–102 行。

> 因此：签名/审计位置、事务边界、锁与 ATP/信用校验的触发点，在同一个节点内并不统一。PF 六键的“最终业务写入”由 PF 脚本（服务端）完成；辅助四键的“最终业务写入”由上述 RPC 实现直接完成，并在服务端写 `BusinessLogUtil` 业务日志（见 3.7）。

## 2. 现场注册证据（10 行）

来源：`config/qd-esign/site-20261107/operation-bindings.csv`（`nodeId=QD-NODE-97231D8D3F118969`），按 `sourceRow` 排序，共 10 行。

| 原表行 | scope_id | 按钮名 | ncActionCode | registeredButtonPk | entryButtonCode / 名称 | mappingState |
| --- | --- | --- | --- | --- | --- | --- |
| 229 | `QD-ES-04ECAF336F8932CA` | 保存 | `Save` | `1005Z810000000001GHS` | `Save` / 保存 | `REGISTERED_BUTTON_RPC_PENDING` |
| 230 | `QD-ES-FF81305763F3CF1F` | 删除 | `Delete` | `1005Z810000000001GH7` | `Delete` / 删除 | `REGISTERED_BUTTON_RPC_PENDING` |
| 231 | `QD-ES-D494A8A893DDBC0B` | 提交 | `Commit` | `1005Z810000000001GHD` | `Commit` / 提交 | `REGISTERED_BUTTON_RPC_PENDING` |
| 232 | `QD-ES-14232F4638F48C8C` | 收回 | `UnCommit` | `1005Z810000000001GHR` | `UnCommit` / 收回 | `REGISTERED_BUTTON_RPC_PENDING` |
| 233 | `QD-ES-257B45A6A4F80EF6` | 审批-确定 | （空） | `1005Z810000000001GH4` | `Approve` / 审批 | `ENTRY_ONLY_SUBPAGE_PENDING` |
| 234 | `QD-ES-7DA00D0A9032E168` | 取消审批 | `UnApprove` | `1005Z810000000001GH2` | `UnApprove` / 取消审批 | `REGISTERED_BUTTON_RPC_PENDING` |
| 235 | `QD-ES-52A72C09C2BE1F47` | 辅助功能-整单关闭 | `BillClose` | `1005Z810000000001GHM` | `BillClose` / 整单关闭 | `REGISTERED_BUTTON_RPC_PENDING` |
| 236 | `QD-ES-61C46C0C9ED0BC7D` | 辅助功能-整单打开 | `BillOpen` | `1005Z810000000001GHN` | `BillOpen` / 整单打开 | `REGISTERED_BUTTON_RPC_PENDING` |
| 237 | `QD-ES-AF1994D66608BEF1` | 辅助功能-出库关闭 | `OutClose` | `1005Z810000000001GGX` | `OutClose` / 出库关闭 | `REGISTERED_BUTTON_RPC_PENDING` |
| 238 | `QD-ES-5275CF042A861E3D` | 辅助功能-出库打开 | `OutOpen` | `1005Z810000000001GGZ` | `OutOpen` / 出库打开 | `REGISTERED_BUTTON_RPC_PENDING` |

10 行均为 `validated=N`、`enabled=N`，`remaining` 列统一为「最终RPC、服务包装、同步审计和NC事务待验证」；第 233 行为「仅定位打开入口；子页确认/保存及复合对象待专用适配」。

**注册层与原生层的一个差异（B 级）**：第 233 行现场登记为 `ENTRY_ONLY_SUBPAGE_PENDING`（`ncActionCode` 为空），而原生 `approveAction` 是完整 PF 动作（`actionName=APPROVE`，配置行 800–808）。即：现场把「审批」当作“打开子页入口”处理，原生类却是“直接走 PF APPROVE 并把返回 VO 回写”。适配时必须确认：是拦截“审批窗口确定”，还是拦截“打开审批入口”。

## 3. 逐按钮证据

### 3.0 汇总表

| 原表行 | scope_id | 按钮编码 | bean id | 原生动作类（父类） | 选择粒度 | 确认/弹窗 | 最终 PF 或 RPC | 返回/刷新 | 复合语义 | 证据等级 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 229 | `QD-ES-04ECAF336F8932CA` | Save | `saveAction` | `DeliverySaveAction`（`SaveScriptAction`→`ScriptPFlowAction`→`AbstractScriptExcAction`） | 当前单据；ADD/EDIT 取编辑态 VO，否则取当前选中 | 仅 EDIT 且“预留行数量/发货仓库”被改时 `showYesNoDlg(4006002_0/04006002-0113)` | **PF `WRITE`** | `PfUtilClient.isSuccess`→`directlyAdd`/`directlyUpdate`+状态复位；确认分支额外 `refreshAction.doAction` | 无（`saveandsendAction` 才是复合） | A+B |
| 230 | `QD-ES-FF81305763F3CF1F` | Delete | `deleteAction` | `DeliveryDeleteAction`（`DeleteScriptAction`） | 当前选中（多选走 `getSelectedOperaDatas`） | **先** `CommonConfirmDialogUtils.showConfirmDeleteDialog`，返回 4 才继续 | **PF `DELETE`** | `processReturnObj`→`model.directlyDelete` + 成功/失败提示 | 无 | A+B |
| 231 | `QD-ES-D494A8A893DDBC0B` | Commit | `sendApproveAction` | `DeliverySendApproveAction`（`CommitScriptAction`） | 当前单据/多选 | 无（父类无确认框） | **PF `SAVE`** | `directlyUpdate`+状态复位 | 无 | A+B |
| 232 | `QD-ES-14232F4638F48C8C` | UnCommit | `unsendApproveAction` | `DeliveryUnSendApproveAction`（`UnCommitScriptAction`） | 当前单据/多选 | 无 | **PF `UNSAVE`**（类默认 Code=`UnCommit`，配置覆盖为 `UNSAVE`） | 同上 | 无 | A+B |
| 233 | `QD-ES-257B45A6A4F80EF6` | （空）/入口 `Approve` | `approveAction` | `DeliveryApproveAction`（`ApproveScriptAction`） | 当前单据/多选 | 审批窗口由 **PF 客户端**在 `PfUtilClient` 内处理（含 `checkWorkitemWhenApprove`/`actionAboutApproveflow`） | **PF `APPROVE`** | `processReturnObj` 回写 | 审批意见/决议属 PF 框架，未核实 | A+B（窗口时序含 C） |
| 234 | `QD-ES-7DA00D0A9032E168` | UnApprove | `unApproveAction` | `DeliveryUnApproveAction`（`UNApproveScriptAction`） | 当前单据/多选 | 无 | **PF `UNAPPROVE`** | `processReturnObj` 回写 | 无 | A+B |
| 235 | `QD-ES-52A72C09C2BE1F47` | BillClose | `deliveryCloseAction` | `DeliveryCloseAction`（`NCAction`） | **整单**（`model.getSelectedData()` 单张，包成 `DeliveryVO[1]`） | 仅当服务抛业务校验异常：ATP `YesNo(04006002-0108)`；信用 `ICreditCheckMessageService.showMessage` | **RPC** `IDeliveryAssitFunc.closeDelivery(SOParameterVO)` | `ClientBillCombinServer.combine` + `model.directlyUpdate` | 无（循环重试协议见 3.7） | A+B |
| 236 | `QD-ES-61C46C0C9ED0BC7D` | BillOpen | `deliveryOpenAction` | `DeliveryOpenAction`（`NCAction`） | 整单（同上） | 4 类：ATP `04006002-0108`、信用、订单容差 `04006002-0109`、调拨容差 `04006002-0110` | **RPC** `openDelivery(SOParameterVO)` | `combine` + `directlyUpdate` | 无 | A+B |
| 237 | `QD-ES-AF1994D66608BEF1` | OutClose | `rowCloseAction` | `DeliveryRowCloseAction`（`NCAction`） | **卡片表选中行号** `UITable.getSelectedRows()` → `closeDeliveryRows(DeliveryVO,int[])` | **无**确认框；无业务校验例外分支 | **RPC** `closeDeliveryRows(DeliveryVO,int[])` | `combine` + `directlyUpdate` + `refreshAction.doAction` | 无 | A+B |
| 238 | `QD-ES-5275CF042A861E3D` | OutOpen | `rowOpenAction` | `DeliveryRowOpenAction`（`NCAction`） | 卡片表选中行号 → `openDeiveryRows(SOParameterVO,int[])`（`SOParameterVO.setVo` 单张） | 同整单打开 4 类 | **RPC** `openDeiveryRows(SOParameterVO,int[])`（原生拼写） | `combine` + `directlyUpdate`（**无** refreshAction，配置也未注入） | 无 | A+B |

### 3.1 保存（行 229 · Save · `saveAction`）

配置：`delivery_config.xml` 行 723–732

```xml
<bean id="saveAction" class="nc.ui.so.m4331.billui.action.DeliverySaveAction">
  <property name="model" ref="ManageAppModel" />
  <property name="editor" ref="billFormEditor" />
  <property name="filledUpInFlow" value="false" />
  <property name="actionName" value="WRITE" />
  <property name="billType" value="4331" />
  <property name="refreshAction" ref="cardRefreshAction" />
</bean>
```

- **是否经 PF：是（A 级）**。`DeliverySaveAction extends nc.ui.pubapp.uif2app.actions.pflow.SaveScriptAction`（`action-DeliverySaveAction.javap.txt` 第 2 行）；父类构造器 `ActionInitializer.initializeAction(this,"Save")` 并 `setActionName("WRITE")`（`parent-SaveScriptAction.javap.txt` 第 5–15 行）。最终写库经 `AbstractScriptExcAction.procFlow` → `PFlowProxy.runAction` → `PfUtilClient.runAction`（`parent-AbstractScriptExcAction.javap.txt` 357–498；`bridge-PFlowProxy.javap.txt` 20–73）。
- **校验器（B 级）**：`saveAction` **没有** `validationService` 属性；配置中维护权校验被注释掉（行 729–736）。带 `PowerValidateService` 的有 delete/commit/uncommit/approve/unapprove 五个（行 714、764、780、806、822）。所以「compositevalidateService 之类的组合校验」在本节点保存链路上**没有**出现；保存前的数据校验来自 `ScriptPFlowAction.getFlowData()` 内的 `BillForm.validateValue()`（ADD/EDIT 分支，`parent-ScriptPFlowAction.javap.txt` 553–657）。
- **新增/修改分支（A 级）**：`ScriptPFlowAction.getFlowData()` 三分支——
  - `uiState==ADD`：取 `editor.getValue()`，`processBefore` 后 `setNewVOStatus`（新增状态标记）；
  - `appUiState==EDIT`：取 `editor.getValue()`，`processBefore` 后 `produceLightVO`（轻量 VO 上送）；
  - 其它：取 `editor` 可见时的 `selectedData`，否则 `selectedOperaDatas`（多选），再 `produceLightVO`。
- **新增/修改分支之上的“二次确认”（A 级）**：`DeliverySaveAction.doAction` 仅在 `UIState.EDIT` 时，先用 `getModel().findBusinessData(editor.getValue())` 找到原 VO，调 `DeliverySaveReverseCheckRule.checkChangeItems(原VO, 编辑VO)`；返回非空字符串则弹 `MessageDialog.showYesNoDlg(parent, NCLangRes(4006002_0,04006002-0113), 该字符串)`（`action-DeliverySaveAction.javap.txt` 45–105、138–164）。
  该规则的判定（`rule-DeliverySaveReverseCheckRule.javap.txt`）：`initOldbvoInfos` 只把 **`nreqrsnum != 0`** 的旧表体行（按 `cdeliverybid`）放进 map；随后逐行比较新 VO 的 `nnum` 与 `csendstordocid`，有变化即返回 `4006002_0 / 04006002-0019` 的提示串。
  用户点“是”（返回码 4）→ 先执行 `SaveScriptAction.doAction`，再执行 `refreshAction.doAction`；否则**什么都不做、也不抛错**（静默取消）。
- **返回/刷新**：PF 成功后 `ScriptPFlowAction.processReturnObj` 在 ADD 场景 `ClientBillCombinServer.combine` + `directlyAdd`，其它场景 `directlyUpdate`，并 `setAppUiState(NOT_EDIT)`/`setOtherUiState(NOT_EDIT)`（`parent-ScriptPFlowAction.javap.txt` 872–1001）。注意：**PF 主路径不调用 `refreshAction`**，只有上面“二次确认→是”的私有 `save()` 分支额外刷新一次。

### 3.2 删除（行 230 · Delete · `deleteAction`）

配置：行 708–721（`actionName=DELETE`，`validationService=powerdeletevalidservice`，`PowerValidateService(actionCode=delete, permissionCode=4331)`）。

- **原生类**：`DeliveryDeleteAction extends DeleteScriptAction`，自身只重写 `isActionEnable()` 与 `isResume()`（`action-DeliveryDeleteAction.javap.txt`）。
- **可点条件（A 级）**：`uiState==NOT_EDIT` 且 `getSelectedData()!=null`；且（`getSelectedOperaDatas()` 多选人数 >1 **或** 表头 `fstatusflag` 为 `FREE` / `NOPASS`）——即已提交/已审批单据不能删。
- **确认框（A 级）**：父类 `DeleteScriptAction.doAction` 先 `nc.ui.uif2.components.CommonConfirmDialogUtils.showConfirmDeleteDialog(model.getContext().getEntranceUI())`，返回码 **等于 4** 才调 `ScriptPFlowAction.doAction`（`parent-DeleteScriptAction.javap.txt` 12–24）。
  该确认框实现：`MessageDialog.showYesNoDlg(container, NCLangRes("uif2","CommonConfirmDialogUtils-000002"), NCLangRes("uif2","CommonConfirmDialogUtils-000003"), 8)`（`bridge-CommonConfirmDialogUtils.javap.txt` 28–45）。**顺序结论：确认框在权限校验与 PF 之前**（`ScriptPFlowAction.doAction` 里才做 `checkDataPermission`）。
- **最终动作**：**PF `DELETE`**（配置行 711 + `PFlowProxy.runAction`）。
- **返回/刷新（A 级）**：`DeleteScriptAction.processReturnObj` 在成功时 `model.directlyDelete(getFullBills(...))`，并按 `MultiBillScriptRunner.isTaskSuccessful()` 显示成功/失败信息（`parent-DeleteScriptAction.javap.txt` 93–148）。配置无 `refreshAction` 属性。

### 3.3 提交（行 231 · Commit · `sendApproveAction`）

配置：行 758–771（`actionName=SAVE`、`filledUpInFlow=true`、`PowerValidateService(actionCode=commit)`）。

- `DeliverySendApproveAction extends CommitScriptAction`，仅重写 `isActionEnable`/`isResume`（`action-DeliverySendApproveAction.javap.txt`）。
- 可点条件（A 级）：编辑态（`EDIT`/`ADD`）直接可点；否则需 `uiState==NOT_EDIT` 且有选中。
- 父类构造器：`initializeAction(this,"Commit")` + `setActionName("SAVE")` + 标题 `pubapp_0/0pubapp-0116`；`doBeforAction` 依据 `appUiState==NOT_EDIT` 置 `setComposite(false/true)`（`parent-CommitScriptAction.javap.txt`）。`composite=true` 表示“编辑态下先保存再提交”的复合流程（PF 前置动作）。
- 无确认框；最终 **PF `SAVE`**；返回走 `ScriptPFlowAction.processReturnObj` 回写。

### 3.4 收回（行 232 · UnCommit · `unsendApproveAction`）

配置：行 773–786（`actionName=UNSAVE`、`PowerValidateService(actionCode=uncommit)`）。

- `DeliveryUnSendApproveAction extends UnCommitScriptAction`（`action-DeliveryUnSendApproveAction.javap.txt`）。
- 可点条件（A 级）：非编辑态；多选 >1 直接可点；单选时要求 `fstatusflag==AUDITING` **且** 表头 `approver` 为空。
- 父类构造器只 `putValue("Code","UnCommit")`、`Name=pubapp_0/0pubapp-0118`，**未** `setActionName`（`parent-UnCommitScriptAction.javap.txt` 5–20）；因此 PF 脚本名 `UNSAVE` 完全来自配置行 777。**适配时不要以类名/Code 推断 PF 动作名。**
- 最终 **PF `UNSAVE`**；无确认框。

### 3.5 审批-确定（行 233 · 入口 `Approve` · `approveAction`）

配置：行 800–814（`actionName=APPROVE`、`filledUpInFlow=true`、`PowerValidateService(actionCode=approve)`）。现场登记为 `ENTRY_ONLY_SUBPAGE_PENDING`、`ncActionCode` 空。

- `DeliveryApproveAction extends ApproveScriptAction`（`action-DeliveryApproveAction.javap.txt`）。
- 可点条件（A 级）：`getAppUiState()==NOT_EDIT` 且有选中；表头 `fstatusflag` ∈ {`FREE`, `AUDITING`}。
- 父类（A 级）：`ApproveScriptAction` 构造器 `initializeAction(this,"Approve")`、`setActionName("APPROVE")`、标题 `pubapp_0/0pubapp-0115`；`doBeforAction` 把 `LoginContext.pk_loginUser` 写入 `PFlowContext.setUserId`；`fillBusitypeAfterLight` 从完整旧 VO 回填 `pkorg`、`billmaker`（`parent-ApproveScriptAction.javap.txt`）。错误文案 `pubapp_0/0pubapp-0338`。
- **“审批窗口确定”的最终提交点**：
  - 静态可见的客户端最后一步是 `AbstractScriptExcAction.procFlow` → `PFlowProxy.runAction` → **`PfUtilClient.runAction(container, actionName+userId, billType, billVO, userObj, strBeforeUIClass, checkVO, eParam)`**，返回后 `PfUtilClient.isSuccess()` 决定 `processReturnObj`（`bridge-PFlowProxy.javap.txt` 20–73）。
  - `PfUtilClient` 的私有方法签名显示审批语义由 PF 客户端承担：`checkWorkitemWhenApprove(Container, String, String, Object, HashMap, int)`、`actionAboutApproveflow(...)`、`hasApproveflowDef(String, AggregatedValueObject)`、`BesideApprove(...)`（`bridge-PfUtilClient-api.txt` 21–49）。
  - 结论（A 级事实 + C 级时序推断）：**审批窗口不是本节点动作类弹出的**，而是 PF 客户端在 `runAction` 内按 `APPROVE` 脚本决定是否弹工作流审批窗口；“确定”即触发 PF 的服务端审批提交，返回后由 `ScriptPFlowAction.processReturnObj` 回写 UI。审批意见/决议的具体字段与写库路径在 PF 框架内，本节点动作类**未设置**任何意见字段（`DeliveryUserObject` 在本节点只被保存动作用于 `isclientsave=true`，见 `vo-DeliveryUserObject.javap.txt`、`action-DeliverySaveAction.javap.txt` 24–41）。
- 无本节点自定义确认框；最终 **PF `APPROVE`**。

### 3.6 取消审批（行 234 · UnApprove · `unApproveAction`）

配置：行 816–829（`actionName=UNAPPROVE`、`PowerValidateService(actionCode=unapprove)`）。

- `DeliveryUnApproveAction extends UNApproveScriptAction`（`action-DeliveryUnApproveAction.javap.txt`）。
- 可点条件（A 级）：非编辑态；多选 >1 直接可点；单选时 `fstatusflag` ∈ {`AUDIT`, `AUDITING`, `NOPASS`}。
- 父类构造器 `initializeAction(this,"UnApprove")` + `setActionName("UNAPPROVE")`（`parent-UNApproveScriptAction.javap.txt` 5–19）。
- 无确认框；最终 **PF `UNAPPROVE`**。

### 3.7 辅助功能四键（行 235–238）

四者均 `extends nc.ui.uif2.NCAction`，构造器调 `nc.ui.scmpub.action.SCMActionInitializer.initializeAction(this,"BillClose"/"BillOpen"/"OutClose"/"OutOpen")` 注册动作名/图标资源；**不继承任何 PF 动作**。

#### 3.7.1 整单关闭（行 235 · BillClose · `deliveryCloseAction`）

配置：行 939–942（只注入 `model`、`editor`，**无 refreshAction**）。

调用链（`action-DeliveryCloseAction.javap.txt` 25–93）：
1. `model.getSelectedData()` 取**当前一张** `DeliveryVO`，包成 `DeliveryVO[1]`；
2. `new SOParameterVO()`；`setVos(DeliveryVO[])`；`setBusinessCheckMap(businessCheckMap)`（本类字段，构造时 `new HashMap`）；
3. 循环体（`boolean retry=true`）：
   - `getServcie().closeDelivery(SOParameterVO)`（`NCLocator.lookup(IDeliveryAssitFunc.class)`，104 行）；
   - 成功：`new ClientBillCombinServer().combine(原 DeliveryVO[], 返回 DeliveryVO[])`，把服务端状态合并回客户端 VO；随后因 `retry=false` 退出循环；
   - 抛 `BusinessException`：`isBusiCheckException` 判定是否为 `CreditCheckException` / `AtpNotEnoughException` / `OrderToleranceException` / `M5xDeliToleranceException`；
     - 是 → `dealException`：`processATPCheck`（弹 `showYesNoDlg(4006002_0/04006002-0108, 异常 message)`；返回 4 → `businessCheckMap.put(BusinessCheck.ATPCheck.getCheckCode(), FALSE)` 并返回 true，否则返回 false）+ `processCreditCheck`（`NCUILocator.lookup(ICreditCheckMessageService, NCModule.CREDIT).showMessage(parent, (CreditCheckException)cause)`；确认 → `put(BusinessCheck.CreditCheck.getCheckCode(), FALSE)`，返回 true）。二者取“或”作为 `retry`；
     - 否 → `ExceptionUtils.wrappException`（中断）；
4. 循环结束后**无条件** `model.directlyUpdate(DeliveryVO[])`（即使因异常中断也已抛出，实际到不了这里）。
5. 可点条件：`getSelectedData()!=null`、`uiState∉{ADD,EDIT}`、表头 `fstatusflag==AUDIT`。

**SOParameterVO 构造要点（B 级）**：`nc.vo.so.pub.SOParameterVO`（`pubso_pub.jar`）字段为 `vo`/`vos`/`view`/`views`/`userobject`/`businessCheckMap`，getter/setter 全部存在（`vo-SOParameterVO.javap.txt`）。整单关闭只用了 `setVos` + `setBusinessCheckMap`，**没有** `setUserObject`、没有 `setView`。

**服务端把 businessCheckMap 当“跳过校验开关”消费（A 级）**：`DeliveryAssitFuncImpl.closeDelivery(SOParameterVO)` → `DeliveryBillCloseAction.closeBill(SOParameterVO)`：取 `sop.getVos()`；`new BussiCheckRule().setBusiCheckFlag(sop.getBusinessCheckMap())`；`AroundProcesser` 挂 `Action4331PlugInPoint.BillCloseActionByPara` 的 before/after 插件点 + `DeliveryVOATPBeforeRule`/`DeliveryVOATPAfterRule`；`BillTransferTool.getClientFullInfoBill()`（日志文案「补全前台VO、加锁、检查时间戳」）；`addRule` 里 `RewriteSrcRule`、`RenovateQualityStateRule`；`DeliveryStateMachine.setState(new BillCloseState(), ...)`；`setBusiLog()` 写业务日志（actiontype `billclose`、funcnode `40060402`、busiobjname `4006002_0/04006002-0130`）；返回 `BillTransferTool.getBillForToClient`（`server-DeliveryBillCloseAction.javap.txt`）。

#### 3.7.2 整单打开（行 236 · BillOpen · `deliveryOpenAction`）

配置：行 944–947（`model`、`editor`，无 refreshAction）。

与整单关闭结构相同，差异（`action-DeliveryOpenAction.javap.txt`）：
- RPC 方法为 `openDelivery(SOParameterVO)`；
- 可点条件为表头 `fstatusflag==CLOSED`；
- `dealException` 有 **4 个**处理器：`processATPCheck`（04006002-0108）、`processCreditCheck`、`processToleranceCheck`（`OrderToleranceException` → `04006002-0109`，确认后 `put(BusinessCheck.OrderToleranceCheck.getCheckCode(), FALSE)`）、`processTranOrderCheck`（`M5xDeliToleranceException` → `04006002-0110`，确认后 `put(BusinessCheck.TransDeliToleranceCheck.getCheckCode(), FALSE)`）；四者取“或”作为是否重试。
- 服务端 `DeliveryBillOpenAction.openBill(SOParameterVO)` 对应插件点 `Action4331PlugInPoint.BillOpenAction`，业务日志 actiontype `billopen`、funcnode `40060402`、busiobjname `4006002_0/04006002-0131`（`server-DeliveryBillOpenAction.javap.txt` 23–24、139–160）。

#### 3.7.3 出库关闭（行 237 · OutClose · `rowCloseAction`）

配置：行 949–953（`model`、`editor`、**`refreshAction=cardRefreshAction`**）。

调用链（`action-DeliveryRowCloseAction.javap.txt` 20–90）：
1. `editor.getBillCardPanel().getBillTable().getSelectedRow()` 为 -1 时，抛 `wrappBusinessException(NCLangRes(4006002_0, 04006002-0005))`（未选行提示）；
2. `int[] rows = getBillTable().getSelectedRows()` —— **这是卡片表格的 UI 行下标（0-based）**；
3. `DeliveryVO vo = model.getSelectedData()`（当前单据，含全部表体）；
4. `NCLocator.lookup(IDeliveryAssitFunc.class).closeDeliveryRows(vo, rows)`；
5. `new ClientBillCombinServer().combine(new DeliveryVO[]{vo}, 返回)`；
6. `model.directlyUpdate(new DeliveryVO[]{vo})`；`refreshAction.doAction(ae)`；
7. 捕获 `BusinessException` 仅 `ExceptionUtils.wrappException` —— **本键没有 ATP/信用/容差的“继续/取消”重试协议**（点击后若服务端 ATP 不足，按静态路径是直接报错中断）；
8. 可点条件（A 级）：非编辑、`getSelectedRow()!=-1`、表头 `fstatusflag==AUDIT`，且（选中行数 >1 **或** 单行时该行 `DeliveryBVO.boutendflag` 为假）。

**`int[]` 的真正语义（A 级，服务端证据）**：`DeliveryAssitFuncImpl.closeDeliveryRows(DeliveryVO,int[])` → 私有 `getRowSelectedBill(DeliveryVO, int[])`：`DeliveryBVO[] all = vo.getChildrenVO(); newVO.setChildrenVO(new DeliveryBVO[]{all[rows[i]] ...})`，逐个 `int` 直接作为 `childrenVO` 数组下标，**既不校验上界，也不按 `cdeliverybid`/`crowno` 定位**；新 VO 复用同一 `parentVO` 引用（`service-DeliveryAssitFuncImpl.javap.txt` 51–72、133–173）。随后 `DeliveryRowCloseAction.closeRow(DeliveryVO[])`：插件点 `Action4331PlugInPoint.RowCloseAction`、ATP before/after、`RewriteSrcRule`、`RenovateQualityStateRule`、`BillToViewConvertor(DeliveryViewVO)` → `RowCloseState`+`DeliveryStateMachine` → `ViewToBillConvertor`、业务日志（actiontype `outclose`、funcnode `40060402`、busiobjname `04006002-0132`，按行 `insertBusiLogForBody`）（`server-DeliveryRowCloseAction.javap.txt`）。

#### 3.7.4 出库打开（行 238 · OutOpen · `rowOpenAction`）

配置：行 955–958（`model`、`editor`，**无 refreshAction**）。

调用链（`action-DeliveryRowOpenAction.javap.txt` 25–116）：
1. 未选行 → 同样抛 `04006002-0005`；
2. `int[] rows = getBillTable().getSelectedRows()`；
3. `new SOParameterVO(); setVo(当前 DeliveryVO); setBusinessCheckMap(businessCheckMap)`（注意：**单数 `setVo`**，与关闭的 `setVos` 不同）；
4. 循环：`openDeiveryRows(sop, rows)`（原生拼写即 `openDeiveryRows`）→ `combine`；
5. 4 类业务校验处理器与整单打开完全相同（ATP/信用/订单容差/调拨容差，均为 `showYesNoDlg` + `put(code, FALSE)` 后重试）；
6. 非业务校验异常：`wrappBusinessException(e.getMessage())`；
7. 结束 `model.directlyUpdate(new DeliveryVO[]{vo})` —— **没有 refreshAction 调用**，配置也未注入（对比 237 行）。
8. 可点条件：非编辑、有选中行、表头 `fstatusflag ∈ {AUDIT, CLOSED}`，且（多选 **或** 单行时该行 `boutendflag==true`）；
9. 服务端 `openDeiveryRows(SOParameterVO,int[])` 先 `sop.getVo()` 转 `DeliveryVO`，再 `getRowSelectedBill(...)`，然后 `DeliveryRowOpenAction.openRow(DeliveryVO[], sop.getBusinessCheckMap())`（`service-DeliveryAssitFuncImpl.javap.txt` 74–107），插件点 `Action4331PlugInPoint.RowOpenAction`，业务日志 actiontype `outopen`（`server-DeliveryRowOpenAction.javap.txt`）。

### 3.8 复合入口：保存并提交（不在本次 10 行内，但同节点存在）

配置：行 751–755 `<bean id="saveandsendAction" class="nc.ui.so.m4331.billui.action.DeliverySaveCommitAction">`，构造参数 `saveAction` + `sendApproveAction`，并挂在**编辑态动作条** `editActions`（行 549–562，与 `saveAction` 并列），因此它是“编辑态下与保存并列的第 2 个按钮”。

- `DeliverySaveCommitAction extends nc.ui.pubapp.uif2app.actions.pflow.SaveAndCommitScriptAction`，构造器把两个子动作传给 `(saveAction, commitAction)`（`action-DeliverySaveCommitAction.javap.txt` 5–12）。
- 两段式语义（A 级，`parent-SaveAndCommitScriptAction.javap.txt` 68–92）：
  1. `doAction` 先 `saveAction.actionPerformed(event)`；
  2. **再判断** `model.getUiState()==NOT_EDIT`，成立才 `setExceptionHandler(commitAction.getExceptionHandler())` 并 `commitAction.actionPerformed(event)`；
  3. 即：保存未把 UI 状态从 EDIT/ADD 复位（失败或用户取消二次确认）→ **提交段不执行**。判断依据是 UI 状态而非返回值。
- 它可点条件（`isActionEnable`）为 `EDIT`/`ADD`/（`NOT_EDIT` 且有选中）——见同文件 13–71。
- 适配含义：同一物理点击在原生会产生 **两次 PF 调用（`WRITE` + `SAVE`）**，且两次都可能触发各自的签名点；若要“一次操作一张签名”，必须按此两段式切分或只拦截其中一段（待业务确认）。

## 4. 关键结论与风险

1. **保存确实走 PF（A 级）**：`saveAction` 是标准 PF 脚本动作（`SaveScriptAction` + `actionName=WRITE`），不是直接 RPC；写库与业务校验发生在 PF 脚本执行侧（服务端脚本内部本次未展开）。前端唯一“非 PF”的保存路径是“预留行数量/发货仓库被改动”时的二次确认分支，但它确认后仍调用同一个 `SaveScriptAction.doAction`（即仍是 PF）。
2. **“保存并提交”是复合动作（A 级）**：一次点击 = `WRITE`（保存）→ 成功且 UI 复位后才 `SAVE`（提交）。若签名点按“按钮”绑定，会漏掉第二段；按“PF 脚本”绑定，则会拆成两次可分别签名的写入（推断其业务影响，需确认）。
3. **删除确认在权限校验与 PF 之前（A 级）**：`DeleteScriptAction.doAction` 先弹 `showConfirmDeleteDialog`，返回 4 才继续；之后才 `checkDataPermission` → PF `DELETE` → `directlyDelete`。若适配层在“确认”之后插入签名，顺序为 确认 → 签名 → PF；若在动作入口拦截，则先签名再确认，语义不同。
4. **行关闭/行打开用“数组下标”而非行主键（A 级）**：
   - 客户端传 `UITable.getSelectedRows()`（卡片表 UI 行号）；服务端直接 `childrenVO[int]` 取值，**无上界校验、无主键匹配**。
   - 风险：①行被排序/过滤/插入新行后，UI 行号与 `childrenVO` 顺序不一致 → **关错行**（推断，需联调）；②越界 → `ArrayIndexOutOfBoundsException`（无防御，A 级可证）；③服务端只回传“选中行子集”构造的新 VO（复用 parentVO 引用），并非整单。
5. **ATP/信用/容差的“继续/取消”协议（A 级事实 + C 级风险）**：
   - 继续 = 对话框返回 4 → 把 `BusinessCheck.<枚举>.getCheckCode()` 以 `FALSE` 写入 `SOParameterVO.businessCheckMap` → 重试 RPC；服务端 `BussiCheckRule.setBusiCheckFlag(map)` 消费该 map（跳过对应校验）。
   - 涉及枚举：`ATPCheck`、`CreditCheck`、`OrderToleranceCheck`、`TransDeliToleranceCheck`（`ref-BusinessCheck.javap.txt`）。
   - **风险（推断，静态控制流）**：`dealException` 把各 `process*` 的返回值取“或”（整单关闭 2 个：ATP、信用；打开/行打开 4 个）。未命中的处理器默认返回 true，因此**用户点“取消”时（该处理器返回 false）整体仍为 true → 立即重试 → 再次弹窗**，操作并未中止。除非另一次成功后 map 生效，否则表现为“取消=再问一遍”。此点必须联调确认。
   - **整单关闭缺少容差处理器（推断）**：`DeliveryCloseAction` 只处理 ATP + 信用，却仍把 `OrderToleranceException`/`M5xDeliToleranceException` 判为“业务校验异常”；这两类异常进入 `dealException` 后无对话框、返回 true → 可能形成**无提示的无限重试**。打开/行打开有对应处理器，关闭/行关闭没有。
6. **行关闭没有业务校验重试（A 级）**：`DeliveryRowCloseAction` 捕获 `BusinessException` 后只 `wrappException`，不弹 ATP/信用框、不重试。与行打开不对称。
7. **刷新不对称（A 级）**：`rowCloseAction` 显式注入并调用 `cardRefreshAction`；`rowOpenAction`、`deliveryCloseAction`、`deliveryOpenAction` 均无 refreshAction；PF 六键中只有 `saveAction` 注入了 refreshAction，且只在“二次确认→是”分支调用。适配时不能假设“操作后必有整单刷新”。
8. **单选粒度不一致（A 级）**：整单关闭/打开用 `model.getSelectedData()`（当前一张，即使列表多选也只处理一张）；行关闭/打开作用于“当前单据 + 选中行集合”；PF 六键走 `getFlowData()`，在多选场景可批量（`selectedOperaDatas`）。现场按钮如果出现在列表工具条上，多选行为的差异会被放大。
9. **服务端会写原生业务日志（A 级）**：辅助四键在服务端 `BusinessLogUtil.insertBusiLog/insertBusiLogForBody` 写 `billclose/billopen/outclose/outopen` 类型日志，funcnode 固定 `40060402`。电子签名的审计记录若也写业务日志，需要与这类原生日志区分（事件类型/来源标识），避免审计口径混淆（推断其实现选择）。
10. **审批节点现场口径与原生类不一致（B 级）**：现场第 233 行只登记“入口”，`ncActionCode` 为空；原生 `approveAction` 是完整 `APPROVE` PF 动作且无本节点确认框。若按现场口径实现“打开审批子页”，会与原生“点一下就走 PF APPROVE”不同——**必须先定清楚拦截“审批窗口确定”还是“打开审批入口”**。
11. **`DeliveryMaintainImpl` 与本节点按钮的关系未静态闭合**：`IDeliveryMaintain`（`insertDelivery/updateDelivery/deleteDelivery/approveDelivery/unapproveDelivery`）确实存在实现，但 6 个 PF 按钮的最终服务端脚本不是本节点客户端类，静态字节码里看不到 `IDeliveryMaintain` 的调用点；PF 的 `WRITE/DELETE/SAVE/UNSAVE/APPROVE/UNAPPROVE` 脚本到服务端实现的绑定关系**未核实**（需要 PF 脚本注册表/数据库）。

## 5. 未核实清单

| # | 未核实项 | 原因 | 建议核实方式 |
| --- | --- | --- | --- |
| 1 | 各对话框的中文标题/正文（`04006002-0108/0109/0110/0113/0019/0005/0130/0131/0132/0133`、`uif2/CommonConfirmDialogUtils-000002/000003`） | NC 多语资源在数据库，jar/配置/BMF 内均无 properties | 查 `MD_*` 多语表或在 NC 中打开对话框 |
| 2 | 返回码 4 是否等于“是” | `MessageDialog` 只暴露 `OK_OPTION/YES_NO_OPTION/ID_YESTOALL…`，无 `ID_YES` 常量；代码直接与字面量 4 比较 | 断点/日志或查 `MessageDialog.showYesNoDlg` 实现 |
| 3 | PF 六个脚本（`WRITE/DELETE/SAVE/UNSAVE/APPROVE/UNAPPROVE`）在服务端的具体实现类，是否调用 `IDeliveryMaintain` | PF 脚本注册在平台/数据库中，jar 内无注册表 | 查 PF 脚本注册（`pubapp` 脚本表）或运行期日志 |
| 4 | 审批窗口内“意见/决议”字段、以及“确定”到工作流提交的精确时序 | PF 框架内部（`PfUtilClient.actionAboutApproveflow/checkWorkitemWhenApprove` 为 private），本节点类不参与 | NC 联调 + PF 源码/日志 |
| 5 | “取消”是否真的导致重复弹窗、整单关闭是否真会无限重试 | 仅静态控制流推断（见 4.5） | NC 联调构造 ATP/容差异常场景 |
| 6 | UI 表格行号与 `childrenVO` 顺序在排序/过滤/新增行后是否一致 | 需要运行期数据（`UITable` 模型与卡片表绑定关系） | 联调：排序后关闭中间行，核对 `crowno` |
| 7 | `DeliverySaveReverseCheckRule` 提示文案含义（`04006002-0019`）与业务后果 | 资源在库；且 `nreqrsnum`（预留/需求数量）字段语义未在元数据注释中给出 | 查多语表 + 业务确认 |
| 8 | 保存并提交的两次 PF 调用是否各自产生独立的签名/审计事件 | 取决于适配层设计，不是原生事实 | 需求确认 |
| 9 | `DeliveryUserObject.setIsclientsave(true)` 对 PF 服务端的具体作用 | 字段消费方在 PF/服务端脚本，未静态闭合 | 联调/查 `DeliveryUserObject` 使用点 |
| 10 | 现场 40 个注册按钮与本章 10 行的对应关系是否覆盖“保存并提交” | 本次只取第 229–238 行 | 复核 `operation-bindings.csv` 全量 40 行 |

## 6. 原始证据索引

### 6.1 javap 原始输出（`build/native-evidence-other-nodes/delivery/`）

| 文件 | 内容 / 证明什么 |
| --- | --- |
| `action-DeliverySaveAction.javap.txt` | 保存动作：`doAction` EDIT 分支、`DeliverySaveReverseCheckRule`、二次确认、`refreshAction`、`getFlowData` 返回 `DeliveryVO[]` |
| `action-DeliveryDeleteAction.javap.txt` | 删除动作：仅重写可点条件（FREE/NOPASS 或多选）与 `isResume` |
| `action-DeliverySendApproveAction.javap.txt` | 提交：`CommitScriptAction` 派生、可点条件 |
| `action-DeliveryUnSendApproveAction.javap.txt` | 收回：`AUDITING` 且 `approver` 为空 |
| `action-DeliveryApproveAction.javap.txt` | 审批：`ApproveScriptAction` 派生、FREE/AUDITING 可点 |
| `action-DeliveryUnApproveAction.javap.txt` | 取消审批：AUDIT/AUDITING/NOPASS 可点 |
| `action-DeliverySaveCommitAction.javap.txt` | 保存并提交：`SaveAndCommitScriptAction` 派生、构造参数 |
| `action-DeliveryCloseAction.javap.txt` | 整单关闭：`SOParameterVO.setVos/setBusinessCheckMap`、RPC 循环、ATP/信用弹窗、`directlyUpdate` |
| `action-DeliveryOpenAction.javap.txt` | 整单打开：同结构 + 4 类弹窗（0108/0109/0110） |
| `action-DeliveryRowCloseAction.javap.txt` | 行关闭：`getSelectedRows()` → `closeDeliveryRows`、无重试、`refreshAction` |
| `action-DeliveryRowOpenAction.javap.txt` | 行打开：`setVo` + `openDeiveryRows`、4 类弹窗、无 refreshAction |
| `parent-SaveScriptAction.javap.txt` / `parent-DeleteScriptAction.javap.txt` | PF 父类：`WRITE` 默认动作名；删除确认框 + `directlyDelete` |
| `parent-CommitScriptAction.javap.txt` / `parent-UnCommitScriptAction.javap.txt` / `parent-ApproveScriptAction.javap.txt` / `parent-UNApproveScriptAction.javap.txt` | PF 父类各自默认动作名（SAVE / Code=UnCommit / APPROVE / UNAPPROVE）与标题资源 |
| `parent-SaveAndCommitScriptAction.javap.txt` | 复合动作两段式：`saveAction.actionPerformed` → 判断 `uiState==NOT_EDIT` → `commitAction.actionPerformed` |
| `parent-ScriptPFlowAction.javap.txt` | `doAction`→`checkDataPermission`、`fillUpContext`、`getFlowData` 三分支、`processReturnObj` 回写 |
| `parent-AbstractScriptExcAction.javap.txt` | `procFlow`：`validate`（`IValidationService`）→ `PFlowProxy.runAction` → `processReturnObjs`；`isResume` 重试 |
| `bridge-PFlowProxy.javap.txt` | PF 桥：`PfUtilClient.runAction(...)` 参数构造、`isSuccess` |
| `bridge-PfUtilClient-api.txt` | PF 客户端 API：`runAction`、`actionAboutApproveflow`、`checkWorkitemWhenApprove`、`hasApproveflowDef` |
| `bridge-CommonConfirmDialogUtils.javap.txt` | 删除确认框：`showYesNoDlg(container, uif2/CommonConfirmDialogUtils-000002, uif2/CommonConfirmDialogUtils-000003, 8)`（第 4 个 int 为消息框选项参数） |
| `service-DeliveryAssitFuncImpl.javap.txt` | 服务实现：RPC 方法逐个委托；`getRowSelectedBill` 用 `childrenVO[int]` |
| `server-DeliveryBillCloseAction.javap.txt` / `server-DeliveryBillOpenAction.javap.txt` | 服务端整单关闭/打开：插件点、ATP 规则、`BussiCheckRule.setBusiCheckFlag`、状态机、业务日志、返回 VO |
| `server-DeliveryRowCloseAction.javap.txt` / `server-DeliveryRowOpenAction.javap.txt` | 服务端行关闭/打开：`DeliveryViewVO` 转换、行状态机、按行业务日志 |
| `service-DeliveryMaintainImpl.javap.txt` | `IDeliveryMaintain` 实现（本次未静态闭合到按钮链路） |
| `itf-IDeliveryAssitFunc.javap.txt` / `itf-IDeliveryMaintain.javap.txt` | 两套接口方法签名（含原生拼写 `openDeiveryRows`） |
| `vo-DeliveryVO.javap.txt` / `vo-DeliveryHVO*.javap.txt` / `vo-DeliveryBVO*.javap.txt` | VO 结构、主键/状态/来源/行号/出库关闭标志常量 |
| `vo-SOParameterVO.javap.txt` | 参数 VO 6 个字段与 getter/setter |
| `vo-DeliveryUserObject.javap.txt` | 用户对象（保存时 `setIsclientsave(true)` 使用） |
| `rule-DeliverySaveReverseCheckRule.javap.txt` | 保存前二次确认规则：`nreqrsnum!=0` 行、比较 `nnum`/`csendstordocid`、提示 `04006002-0019` |
| `ref-BusinessCheck.javap.txt` | `BusinessCheck` 枚举（`ATPCheck/CreditCheck/OrderToleranceCheck/TransDeliToleranceCheck`） |
| `ref-MessageDialog-constants.javap.txt` | `MessageDialog` 常量与返回码参考 |
| `metadata-delivery.bmf` | 发货单元数据：`so_delivery`/`so_delivery_b` 物理表名、`cdeliveryid`/`cdeliverybid` 主键、关键字段中文名 |

### 6.2 非 javap 证据

| 证据 | 路径 |
| --- | --- |
| 客户端配置（解压件） | `build/native-configs-other-nodes/delivery_config.xml`（源：`uiso_delivery.jar!nc/ui/so/m4331/billui/model/delivery_config.xml`） |
| 表单元数据（原件） | `E:\01-Home\NC65home\QuDuTest\nchome\modules\so\METADATA\so_delivery\delivery.bmf` |
| 现场注册（节点） | `config/qd-esign/site-20261107/node-bindings.csv`（`ncFunctionCode=40060402`） |
| 现场注册（10 行） | `config/qd-esign/site-20261107/operation-bindings.csv`（`nodeId=QD-NODE-97231D8D3F118969`） |
| 注册汇总 | `config/qd-esign/site-20261107/summary.json` |

### 6.3 复现命令（只读）

```powershell
$javap = 'E:\01-Home\NC65home\QuDuTest\nchome\ufjdk\bin\javap.exe'
$jar   = 'E:\01-Home\NC65home\QuDuTest\nchome\modules\so\client\lib\uiso_delivery.jar'
& $javap -p -c -classpath $jar nc.ui.so.m4331.billui.action.DeliverySaveAction

$soJar = 'E:\01-Home\NC65home\QuDuTest\nchome\modules\so\META-INF\lib\so_delivery.jar'
& $javap -p -c -classpath $soJar nc.impl.so.m4331.DeliveryAssitFuncImpl

$pubJar = 'E:\01-Home\NC65home\QuDuTest\nchome\modules\so\lib\pubso_delivery.jar'
& $javap -p -c -classpath $pubJar nc.vo.so.m4331.entity.DeliveryBVO
```

> javap 输出编码：本机为 zh-CN 代码页，javap 直出为 GBK；本次落盘后统一转 UTF-8，未改动任何字节码内容（仅重编码文本）。

---

**取证完成度：10/10 行静态核实**（229–238 全部定位到原生动作类 + 配置 bean + 调用链 + 最终 PF/RPC + 返回处理）。文中的「推断」项均已列入第 5 节未核实清单，需 NC 联调或查库确认，本报告不声称做过任何运行期验证。
