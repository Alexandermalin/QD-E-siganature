# 销售发票维护 `40060501` 第 239–244 行（6 项）逐按钮原生证据

- 交付批次：销售/发货/发票「其它节点」逐按钮取证（对应 `docs/nc65-electronic-signature/SALES_OTHER_NODES_DEVELOPMENT_PLAN.md` 第三节 L63–L78）
- 取证日期：2026-11-12
- 项目根（只读）：`E:\02-Project\QiDu\QD-E-Siganature`
- NC65 开发 HOME（只读）：`E:\01-Home\NC65home\QuDuTest\nchome`；自带 `ufjdk` 为 IBM J9 / `1.7.0-internal`（`javap.exe` 同版本）
- 交付性质：**离线只读证据**。未启动 / 未连接 NC，未执行任何 SQL，未运行构建，未修改任何既有源码 / 文档 / 数据库。**真实 NC 会话核实 0 条**；本文所有「核实」仅指读到 JAR 字节码、JAR 内 XML 原文或项目已存的 2026-11-07 现场只读导出。
- 证据等级记号：`[静态]` = 本次直接读自 JAR 内字节码 / XML；`[库快照]` = 来自项目已存的 2026-11-07 QDNC 只读导出；`[未核实]` = 本机只读条件无法确认，已给出观察位置。

---

## 0. 结论速览

1. **6 项全部在客户端配置里有唯一 bean，且与现场 `btncode` 一一对齐** `[静态]`+`[库快照]`：`saveAction`/`deleteAction`/`commitAction`/`unCommitAction`/`approveAction`/`unApproveAction`，动作类名与需求清单完全一致（§2）。
2. **6 项的最终出口都是同一个 PF（流程平台）入口** `[静态]`：`AbstractScriptExcAction.doAction → doBeforAction → procFlow → PFlowProxy.runAction → PfUtilClient.runAction → IplatFormEntry.processAction(actionName, billType, worknote, vo, userObj, eParam)`。真实动作名分别为 `WRITE / DELETE / SAVE / UNSAVE / APPROVE / UNAPPROVE`；`SaveScriptAction`、`CommitScriptAction`、`ApproveScriptAction`、`UNApproveScriptAction` 四个基类在**构造器里硬编码**同名动作，`Delete`/`UnCommit` 两个基类不写死、依赖 config（§2）。
3. **240 的删除入口歧义已可静态定性** `[静态]`：`Delete / 1001Z810000000008DPR` 是发票删除；`fs_delete / 0001Z810000000017PB3` 属**附件面板按钮组**（与 `fs_browse/download/rename/upload` 同前缀 `0001Z810000000017PB1–PB5`、`ISBUTTONPOWER=Y`），在本节点配置中 0 引用、在客户端 JAR 的 154 个条目名中 0 命中。→ 落地应取 `Delete`（§3.2）。`fs_delete` 的真实行为仍标 `[未核实]`。
4. **保存带组合校验器与组合单（bcombin）分支** `[静态]`：`saveAction.validationService=compositevalidateService`（`CompositeValidation`，成员是 `SaleInvoiceValidationService` 非空校验；`powerwritevalidservice` 被注释掉，即**不校验「保存维护权限」**）。`SaleInvoiceSaveAction` 另覆盖了 `doAction/doBeforAction/fillUpContext/processReturnObj/produceLightVO` 并把 `SaleinvoiceUserObject` 塞进 `PfUserObject`，覆盖保存时不能按「无额外校验器」的订单假设处理（§3.1）。
5. **审批窗口在 `processAction` 之前弹出，取消不产生业务请求** `[静态]`：`PfUtilClient.runAction` 内先按动作名分流到 `actionAboutApproveflow` / `actionAboutWorkflow` 生成 `WorkflownoteVO`，失败或用户取消（`m_isSuccess=false`）时**直接 `return null`**，未到达 `IplatFormEntry.processAction`（§3.3）。
6. **6 项自身没有税控 / 数电 / 开票平台调用** `[静态]`：对 6 个客户端动作类 + 7 个服务端动作类的字节码做过外部调用扫描（`HttpClient / HttpURL / java.net / Socket / WebService / 金税 / 税控 / 发票` 关键字）**0 命中**；这 6 个类里也没有 `IplatFormEntry / PfUtilClient / PFlowProxy` 字样（PF 入口全在框架基类）。金税是同一节点上的**独立按钮**：`ExportGoldTax / 传金税`（`1001Z810000000008DPI` → `nc.ui.scmpub.goldtax.TransGoldTaxDlg` → `ISaleInvoiceMaintain.updateWhenExportGoldTax`），另有 `ImportTaxCode / 导入税票号`、`VatSubTotal / 税额小计`（§3.4）。
7. **但 6 项会触达其它 NC 模块与会计平台，不能假设随本地事务回滚** `[静态]`：审批后回写应收并推销售结算（`IArsubToSaleInvoice.writeNoriginvoicemny`、`ISquare33For32.pushSquareSrv`）；取消审批后反写应收、**删会计平台实时凭证**、撤销结算推送；删除后回写来源单与应收。明细见 §3.4、§3.6。
8. **`SaleInvoiceSaveCommitAction` 是严格两段式** `[静态]`：先执行 `saveAction.actionPerformed`，只有 UI 状态变回 `NOT_EDIT`（即保存成功并退出编辑态）才继续 `commitAction.actionPerformed`；`doAction` 内无 try/catch（§3.5）。

---

## 1. 基本信息

### 1.1 节点、组织与注册

| 项 | 值 | 证据 |
| --- | --- | --- |
| 功能节点 | `40060501` 销售发票维护 | `[库快照]` `node-bindings.csv:L42` |
| `nodeId` | `QD-NODE-BB454518920E0F76` | `[库快照]` `node-bindings.csv:L42`、`operation-bindings.csv:L308–L313` |
| 注册功能主键 `registeredFunctionPk` | `1002Z810000000000MDO` | `[库快照]` `node-bindings.csv:L42` |
| 组织类型 `orgTypeCode` | **`FINANCEORGTYPE000000`**（财务组织） | `[库快照]` `node-bindings.csv:L42` |
| 模块 | `4006`，父节点 `1002Z810000000002IA1` | `[库快照]` `node-bindings.csv:L42` |
| 按钮注册总数 | **45**（本次实测 45 行，`parent_id` 全部为 `1002Z810000000000MDO`） | `[库快照]` `buttons_1.json` 过滤 `FUNCODE=40060501`；清单副本 `build/native-evidence-other-nodes/invoice/site-buttons-40060501.tsv` |
| 节点映射状态 | `NODE_REGISTRATION_CONFIRMED_RPC_PENDING` | `[库快照]` `node-bindings.csv:L42` |
| 页签（`PAGECODE`）级注册 | **未核实**。`buttons_1.json` 的 SQL 只 JOIN `sm_funcregister`+`sm_butnregister`，无 `PK_PAGE/PAGECODE` | `[未核实]` 观察位置：把同一三表 JOIN 扩到 `sm_pageregister` |

### 1.2 客户端配置与单据类型

| 项 | 值 | 证据 |
| --- | --- | --- |
| 客户端 Bean 配置 | `nc/ui/so/m32/billui/model/saleinvoice_config.xml` | `[库快照]` `node-bindings.csv:L42`；`[静态]` 该路径存在于 `uiso_saleinvoice.jar` |
| 本地解压副本 | `build/native-configs-other-nodes/saleinvoice_config.xml`，1193 行 / 46310 字节，SHA-256 `4568BD3B8F7393FECC206FEF51BA74EF91FEA40CB2392097BFBE99ACEEC8D996` | 本次只读提取 |
| 基本单据类型 `billType` | **`32`** | `[静态]` `saleinvoice_config.xml:441/459/469/480/660/669` 等 |
| 另存配置 | `nc/ui/so/m32/billui/model/saleinvoice_unApprove_config.xml`（5002 字节，138 行） | `[静态]` 同 JAR；本次提取副本见 §5。**它不是审批子页**：其模型是 `nc.ui.so.m32.closingcheck.IA2012Model` + `ClosingCheckModelService`、列表 `IA201BatchTable`，属「关账检查 / 批量」列表壳，`saleinvoice_config.xml` 内 0 引用 |
| 元数据全名 | `so.saleinvoice`（表头）、`so.saleinvoice_b`（表体），各出现 3 次 | `[静态]` `saleinvoice_config.xml:992/997`、`1011/1016`、`1043/1046` |
| 物理表名 | **推断** `so_saleinvoice` / `so_saleinvoice_b`（按 NC 元数据名 → 表名下划线规则）；未连库、未读 ORM/BMF 资源 | `[未核实]` 见 §4.6 |

### 1.3 服务端接口与 UPM 绑定

| 接口 | 实现 | 证据 |
| --- | --- | --- |
| `nc.itf.so.m32.ISaleInvoiceMaintain` | `nc.impl.so.m32.SaleInvoiceMaintainImpl` | `[静态]` `modules\so\META-INF\SCM_SO_SALEINVOICE_EJB60.upm`（`<interface>`/`<implementation>`）；JAR `pubso_saleinvoice.jar` / `so_saleinvoice.jar` |
| `nc.itf.so.m32.ISaleInvoiceMaintainApp` | `nc.impl.so.m32.SaleInvoiceMaintainAppImpl` | 同上 |
| `nc.itf.so.m32.ISaleInvoiceScriptMaintain` | `nc.impl.so.m32.SaleInvoiceScriptMaintainImpl`（业务日志；AOP `BusilogAspect`） | 同上 + `modules\so\META-INF\SCM_SO_SALEINVOICE_bill.aop` |
| PF 动作脚本实现（按「动作名+单据类型」约定装配） | `nc.impl.so.m32.action.{Insert,Update,Delete,Commit,UnCommit,Approve,UnApprove}SaleInvoiceAction` | `[静态]` `so_saleinvoice.jar` 条目；本次全部反编译留档（§5） |
| 业务处理类 | `nc.bs.so.m32.maintain.{Insert,Update,Delete}SaleInvoiceBP`、`CommitSaleInvoiceBP`、`CreateVoucherSaleInvoiceBP` | 同上 |
| 状态检查 | `nc.impl.so.m32.pf.SaleInvoiceCheckStatus` | 同上 |

`ISaleInvoiceMaintain` 的税控相关方法（**不在 6 项链路上**）：`executeVOChangeTogtax(String[])`、`updateGoldTaxCode(SaleInvoiceHVO[])`、`updateWhenExportGoldTax(SaleInvoiceHVO[])` —— 见 §3.4。

### 1.4 VO 与关键字段

| 类 | 声明 | 关键字段（常量字面值） | 证据 |
| --- | --- | --- | --- |
| `nc.vo.so.m32.entity.SaleInvoiceVO` | `extends nc.vo.pubapp.pattern.model.entity.bill.AbstractBill`；仅 `getParentVO()/getChildrenVO()/getMetaData()` | 聚合根；行为由 `AbstractBill` 提供 | `[静态]` `vo-SaleInvoiceVO.javap.txt` |
| `nc.vo.so.m32.entity.SaleInvoiceHVO` | `extends nc.vo.pub.SuperVO`；**73** 个字段常量 | `csaleinvoiceid`（主键）、`vbillcode`、`pk_org`、`pk_org_v`、`approver`、`billmaker`、`fstatusflag`、`btogoldtaxflag`、`tgoldtaxtime`、`vgoldtaxcode`、`vvatcode`、`vcustvatcode`、`cinvoicecustid`、`ctaxcountryid` | `[静态]` `vo-SaleInvoiceHVO.javap.txt` / `.constants.txt` |
| `nc.vo.so.m32.entity.SaleInvoiceBVO` | `extends nc.vo.pub.SuperVO`；**146** 个字段常量 | `csaleinvoicebid`（主键）、`csaleinvoiceid`（外键）、`crowno`、`ntaxrate`、`ntaxmny`、`ntaxprice`、`ctaxcodeid`、`ftaxtypeflag`、`cmffileid`、`vrownote`、`vsrcrowno` | `[静态]` `vo-SaleInvoiceBVO.javap.txt` / `.constants.txt` |
| `nc.vo.so.m32.entity.SaleinvoiceUserObject` | `implements Serializable` | `OffsetTempVO tempvo`、`boolean isclientsave` —— 保存时由 `SaleInvoiceSaveAction.setUserObj()` 装入 `PfUserObject` | `[静态]` `vo-SaleinvoiceUserObject.javap.txt`；`SaleInvoiceSaveAction.javap.txt:522–558` |
| `nc.vo.so.m32.entity.SaleInvoiceViewVO` | `extends AbstractDataView` | `getHead()/getItem()/changeToBill()` | `[静态]` `vo-SaleInvoiceViewVO.javap.txt` |
| `nc.vo.so.m32.entity.SaleInvoiceViewMeta` | `extends DataViewMeta` | 仅构造器 | `[静态]` `vo-SaleInvoiceViewMeta.javap.txt` |

> 字段前缀约定与 NC 一致：`c*` 主键/引用，`v*` 字符，`n*` 数值，`f*` 标志，`t*` 时间。`btogoldtaxflag`/`tgoldtaxtime`/`vgoldtaxcode` 说明**传金税状态落在表头**，但写入者是独立的「传金税」按钮与 `updateWhenExportGoldTax`，见 §3.4。

### 1.5 证据来源（JAR 与框架）

| 用途 | 文件 | SHA-256 |
| --- | --- | --- |
| 6 个客户端动作类 + 主配置 + 税控按钮 | `modules\so\client\lib\uiso_saleinvoice.jar`（154 条目 / 119 class） | `E1DE1AA8D4B91AAA99CE2EC911B5CF44A5A7082760C9E685F4D8B180C5C8BC0A` |
| 服务端 PF 动作类、规则、BP | `modules\so\META-INF\lib\so_saleinvoice.jar` | `288310ACA56D4C6972680013A567FA00AF167EA350E6283552E248D2B44B2ADB` |
| VO、`ISaleInvoiceMaintain`、`RemoteFormSOUtil` | `modules\so\lib\pubso_saleinvoice.jar` | 见 §5 逐文件清单 |
| PF 动作基类、`PFlowProxy` | `modules\pubapp\client\lib\uipubapp_platformextLevel-1.jar` | 见 §5 |
| `PfUtilClient`（PF 客户端入口） | `modules\riart\client\lib\uiriart_riartplatformLevel-1.jar` | 见 §5 |
| 附件管理动作 `SOManageDocumentAction` | `modules\so\client\lib\uiso_pub.jar` | 见 §5 |
| 现场按钮注册 | `docs/nc65-electronic-signature/site-evidence/2026-11-07/buttons_1.json`（`read_only=true`，schema `QDNC`） | 45 行 `40060501` |
| 现场节点/按钮映射 | `config/qd-esign/site-20261107/{node-bindings,operation-bindings}.csv` | — |

---

## 2. 六项逐行证据表

> 配置行号 = `saleinvoice_config.xml`（JAR 条目原文行号）。`PF` 列给的是 `PFlowContext.getActionName()` 最终值。所有 bean 均在 `actionsOfList`（`:359–381`）与 `actionsOfCard`（`:384–420`）出现；`saveAction`/`saveandcommitAction` 额外在 `actionsOfCard.editActions`（`:407–419`）。

| 原表行 | scope_id / 现场注册 | 按钮编码 · bean id | 原生动作类（父类） | 选择粒度 / 启用条件 | 确认 · 弹窗 | 最终 PF / RPC | 返回 · 刷新 | 证据等级 |
| ---: | --- | --- | --- | --- | --- | --- | --- | --- |
| **239** | `QD-ES-176DC431DD4F5803`；pk `1001Z810000000008DQ9`；`Save`；`REGISTERED_BUTTON_RPC_PENDING` | `Save` · **`saveAction`**（`:664–672`）：`validationService=compositevalidateService`、`actionName=WRITE`、`billType=32` | `nc.ui.so.m32.billui.action.SaleInvoiceSaveAction`（`extends SaveScriptAction`） | 卡片编辑态当前单据；组合单取 `getSelectedData()` 的 `editvo`。`isActionEnable` 继承基类：`UIState=EDIT/ADD` | **类内 0 个弹窗**；无「保存确认」。校验来自 `CompositeValidation`（`:674–681` → `SaleInvoiceValidationService`，`:683–686`） | **`WRITE`**（基类构造器 `setActionName("WRITE")` + 配置同名） | `processReturnObj`：组合分支 `updateTSAndVOState → splitEditSaleInvoice → ClientBillCombinServer.combine → getCombinNewVO → editor.setValue → setFullOldVOs` 后回调 `super.processReturnObj`；`doAction` 组合分支异常时**回滚 `AppUiState` 与 `editor.setValue(editvo)` 再抛出** | **客户端静态 + 服务端静态**（`Insert/UpdateSaleInvoiceAction`） |
| **240** | `QD-ES-8F8846F2D6E1ECD0`；pk/entry **空**；`NO_UNIQUE_REGISTERED_BUTTON` | `Delete` · **`deleteAction`**（`:656–662`）：`actionName=DELETE`、`billType=32`、`validationService=powerdeletevalidservice` | `nc.ui.so.m32.billui.action.SaleInvoiceDeleteAction`（`extends DeleteScriptAction`） | `NOT_EDIT` + 有选中；状态 `FREE` 或 `NOPASS`；`getSelectedOperaDatas().length>1` 时直接放行（`:67–95`） | **删除前二次确认在基类**：`DeleteScriptAction.doAction` 先 `CommonConfirmDialogUtils.showConfirmDeleteDialog(entranceUI)`，返回值 `!= 4` 即 `return`，`== 4` 才继续 `super.doAction`（`parent-DeleteScriptAction.javap.txt:12–24`）。常量 `4` 的语义（该工具类的返回码定义）`[未核实]` | **`DELETE`**（`DeleteScriptAction` 构造器只 `initializeAction("Delete")`，**不** `setActionName`，动作名依赖配置） | 基类：`PfUtilClient.isSuccess()` → `getFullBills` → `model.directlyDelete(objs)`；`MultiBillScriptRunner.isTaskSuccessful()` 决定 `showSuccessInfo/showFailedInfo`；子类组合分支先 `updateCombinRela` | **客户端静态 + 服务端静态**（`DeleteSaleInvoiceAction` → `DeleteSaleInvoiceBP`） |
| **241** | `QD-ES-56499B75D3E91C33`；pk `1001Z810000000008DQ0`；`Commit`；`REGISTERED_BUTTON_RPC_PENDING` | `Commit` · **`commitAction`**（`:435–443`）：`actionName=SAVE`、`billType=32`、`validationService=powercommitvalidservice`、`filledUpInFlow=true` | `nc.ui.so.m32.billui.action.SaleInvoiceCommitAction`（`extends CommitScriptAction`） | `EDIT/ADD/TRANSFERBILL_ADD` → 可用；`NOT_EDIT` 有选中时：状态 `FREE` 或 `NOPASS`（单条），多选直接放行（`:22–95`） | **类内 0 个弹窗**；基类 `doBeforAction` 仅按 `NOT_EDIT` 切换 `setComposite`。若该交易类型存在审批流定义，框架可能弹「送审」窗（§3.3） | **`SAVE`**（基类构造器硬编码 + 配置同值） | `processReturnObj` 组合分支：`getOldDetailVOs → getNewCombinUIVOS → setFullOldVOs(oldcombinvo) → super.processReturnObj` | **客户端静态 + 服务端静态**（`CommitSaleInvoiceAction`） |
| **242** | `QD-ES-29895E169F92EB91`；pk `1001Z810000000008DQA`；`UnCommit`；`REGISTERED_BUTTON_RPC_PENDING` | `UnCommit` · **`unCommitAction`**（`:453–461`）：`actionName=UNSAVE`、`billType=32`、`validationService=poweruncommitvalidservice`、`filledUpInFlow=true` | `nc.ui.so.m32.billui.action.SaleInvoiceUnCommitAction`（`extends UnCommitScriptAction`） | `NOT_EDIT` + 有选中；`approver` 为空且状态 `AUDITING`（单条）；多选 `length>1` 直接放行（`:22–88`）。**未重写 `doAction`** | **类内 0 个弹窗**（基类也无） | **`UNSAVE`**（`UnCommitScriptAction` 构造器只 `putValue("Code","UnCommit")`，**不** `setActionName` → 完全依赖配置的 `UNSAVE`） | `processReturnObj` 组合分支同上（`getNewCombinUIVOS`）；非组合直接 `super` | **客户端静态 + 服务端静态**（`UnCommitSaleInvoiceAction`，服务端极薄） |
| **243** | `QD-ES-9AE255EF674BD8E2`；pk `1001Z810000000008DPP`；`entryButtonCode=Approve`、`ncActionCode` 空；`ENTRY_ONLY_SUBPAGE_PENDING` | `Approve`（现场 `btnname=审批`，清单名「审批-确定」）· **`approveAction`**（`:474–482`）：`actionName=APPROVE`、`billType=32`、`validationService=powerapprovevalidservice`、`filledUpInFlow=true` | `nc.ui.so.m32.billui.action.SaleInvoiceApproveAction`（`extends ApproveScriptAction`） | `NOT_EDIT` + 有选中；状态 `FREE` 或 `AUDITING`（单条）；多选放行（`:22–73`）。**未重写 `doAction`** | **审批窗口**由框架弹出：`PfUtilClient.runAction → actionAboutApproveflow(...)` 生成 `WorkflownoteVO`；取消 → `return null`，**不调 `processAction`**（§3.3）。`isResume` 走 `ResumeExceptionUIProcessUtils` | **`APPROVE`**（基类构造器硬编码 + 配置同值）→ `IplatFormEntry.processAction(...)` | 基类 `processReturnObj`（`getFullOldVOs`/`directlyUpdate`）+ 组合分支 `getNewCombinUIVOS`；基类 `doBeforAction` 末尾 `PFlowContext.setUserId(登录用户)` | **客户端静态 + 框架静态 + 服务端静态**；弹窗内参数落库 `[未核实]` |
| **244** | `QD-ES-5EBFFEF0FFE24CAA`；pk `1001Z810000000008DPN`；`UnApprove`；`REGISTERED_BUTTON_RPC_PENDING` | `UnApprove` · **`unApproveAction`**（`:463–471`）：`actionName=UNAPPROVE`、`billType=32`、`validationService=powerunapprovevalidservice`、`filledUpInFlow=true` | `nc.ui.so.m32.billui.action.SaleInvoiceUnApproveAction`（`extends UNApproveScriptAction`） | `NOT_EDIT` + 有选中；`AUDIT`／`AUDITING`／`NOPASS` 且 `approver` 非空（单条）；多选放行（`:29–107`） | **类内 0 个弹窗**；`beforeCheck` 仅 `super`。服务端会抛业务异常（下游/核销），见 §3.6 | **`UNAPPROVE`**（基类构造器硬编码 + 配置同值） | `processReturnObj` 组合分支同 241/242（`oldcombinvo` + `getNewCombinUIVOS`） | **客户端静态 + 服务端静态**（规则链完整核实，§3.6） |
| — | （范围外，仅对照） | `saveandcommitAction`（`:446–450`）= `SaleInvoiceSaveCommitAction(saveAction, commitAction)` | 复合：`extends SaveAndCommitScriptAction` | `EDIT/ADD/TRANSFERBILL_ADD`，或 `NOT_EDIT` 有选中（`:13–82`） | 无独立弹窗；两段各自的弹窗由被组合的动作决定 | 先 `WRITE` 后 `SAVE`（两段） | 见 §3.5 | **静态** |

> 一致性旁证 `[静态]`：6 个 bean 的 `class` 全名与需求清单给出的类名逐一相同；`billType=32` 全一致；`filledUpInFlow=true` 只出现在 241–244（保存/删除无此属性，故单笔直传）。

---

## 3. 关键结论与风险

### 3.1 「组合保存」贯穿 6 项，且保存链最重（239）

`SaleInvoiceSaveAction` 是 6 个类里唯一覆盖 `doAction/doBeforAction/fillUpContext/produceLightVO` 的实现，全部围绕 `SaleInvoiceManageModel.getCombinCacheVO().getBcombinflag()` 分支（`SaleInvoiceSaveAction.javap.txt`）：

| 方法 | 组合单（`bcombinflag=true`）时的行为 | 证据偏移 |
| --- | --- | --- |
| `doAction` | 先存 `AppUiState` 快照 → `super.doAction`；`catch(Exception)`：恢复 `setAppUiState(旧)` + `editor.setValue(editvo)` 后**重新抛出** | `:20–56` |
| `doBeforAction` | `processCombinShow()`（缓存 `editvo`，把 `status==3` 的表体行主键收进 `setDelbids`，`PFlowContext.setBillVo`）；**组合态不调 `super.doBeforAction`**；最后 `setUserObj()` | `:58–76`、`:455–520` |
| `fillUpContext` | 组合态仍调 `super`，异常时 `editor.setValue(editvo)` | `:78–107` |
| `processReturnObj` | 删行过滤 `setDelbids` → `ClientBillCombinServer.combine(新建, 返回)` → `updateEditCombinRela` → `getCombinNewVO`（`SaleInvoiceCombin.combinSaleInvoices`）→ `editor.setValue` → `setFullOldVOs` → `super.processReturnObj(新VO)` | `:117–267` |
| `produceLightVO` | 仅组合态且 `isLightBillUsed()` 才走 `ClientBillToServer.construct` 拆分，否则透传 | `:321–453` |
| `updateTSAndVOState` | 用返回 VO 覆盖本地行属性（按 `combinRela` 键）并把行 `status` 置 0 | `:560–669` |

`240/241/242/243/244` 的 `processReturnObj` 都带组合分支（`updateCombinRela` / `getNewCombinUIVOS` / `oldcombinvo`），`processCombinShow` 是这 5 个类共有的私有方法。

**风险**：①覆盖保存时若只保留「单笔」路径，会破坏「编辑组合单再保存」场景（含删行、行状态重置、TS 处理）；②`doAction` 的失败回滚只回滚**客户端 UI 状态**，不涉及服务端；③`setUserObj()` 每次都新建 `SaleinvoiceUserObject` 并覆盖 `PfUserObject.userObject` —— 若覆盖层需要向服务端传自定义上下文，必须与它共享同一个 `PfUserObject`，否则会被本次保存替换。

### 3.2 删除入口歧义：`Delete` 是发票删除，`fs_delete` 属附件面板

`[库快照]` 本节点 45 枚按钮中，名为「删除」的有两枚；`[静态]` 本节点配置里 `fs_delete` 出现 **0** 次，`fs_browse/download/rename/upload` 同样 0 次。

| 候选 | `BTNCODE` / `BTNNAME` | `PK_BTN` | `ISBUTTONPOWER` | 静态判定 |
| --- | --- | --- | --- | --- |
| A | `Delete` / 删除 | `1001Z810000000008DPR` | `N` | **发票删除入口**。与业务按钮同前缀（`1001Z810000000008D*`），绑定 `deleteAction`（`actionName=DELETE`），PF `DELETE` 直达 `DeleteSaleInvoiceAction` |
| B | `fs_delete` / 删除 | `0001Z810000000017PB3` | **`Y`** | **附件面板按钮**。与 `fs_browse(PB4)`/`fs_download(PB2)`/`fs_rename(PB5)`/`fs_upload(PB1)` 构成「查看/下载/上传/重命名/删除」完整文件操作集，同前缀连号 |

判定依据（4 条独立线索）：
1. **同组完整性**：`fs_*` 五枚是文件面板的标准操作集，不构成发票业务动作集；
2. **权限位不同**：本节点 45 枚中 `ISBUTTONPOWER=Y` 的只有 `fs_*` 五枚与 `VatSubTotal`（`0001Z81000000000ZK4I`），业务动作全为 `N`；
3. **配置 0 命中**：`saleinvoice_config.xml` 内 `fs_delete`（及其同伴 `fs_browse/download/rename/upload`）**0 次**；客户端 JAR 的 154 个条目名中 `fs_`/`FileManager` 关键字 **0 命中**（JAR 内配置只有 `docmngAction` 这一个附件相关 bean，见线索 4）；
4. **另有独立附件入口**：`File / 附件管理`（`1002Z81000000000KSGH`），配置中对应 `docmngAction = nc.ui.so.pub.actions.SOManageDocumentAction`（`saleinvoice_config.xml:759–761`），挂在浏览态「辅助功能」菜单 `browAstFuncActionGroup`（`:694–715`，第 `:712` 行引用）。该类 `extends nc.ui.pubapp.uif2app.actions.FileDocManageAction`（`attachment-SOManageDocumentAction.javap.txt:2`），即由平台附件管理面板承载文件操作。

**结论**：240 的实现落点应取 `Delete / 1001Z810000000008DPR`。`fs_delete` **不进入**本批范围。
**`[未核实]`**：`fs_delete` 点击后到底删什么（附件 vs 发票）—— 观察位置：`FileManager` 面板真实点击、`sm_butnregister` 的组归属、以及附件服务端接口。即使核实为删附件，也不应作为发票删除的签名落点（对象与事务都不同）。

### 3.3 审批窗口时序：窗口在 `processAction` 之前，取消不产生请求（243）

`[静态]` 完整链路（同一次 `doAction` 内）：

```
AbstractScriptExcAction.doAction
  → doBeforAction()                       (ApproveScriptAction: super + PFlowContext.setUserId(登录用户))
  → procFlow(PFlowContext)
      → PFlowProxy.runAction(ctx)          (单笔)  / runActionBatch(ctx) (批量)
          → PfUtilClient.runAction(parent, actionName+userId, billType|trantype, vo, userObj, strBeforeUIClass, checkVo, eParam)
```

`PfUtilClient.runAction` 内（`parent-PfUtilClient.javap.txt:2284–2450`）：

1. `initEParam` → `hintBeforeAction(...)`：返回 false → `m_isSuccess=false`、`return null`（**不发业务请求**）；
2. 无 `silently`/`nosendmessage` 时，若 `PfUtilBaseTools.isSaveAction(actionName,billType)` **或** `isApproveAction(...)` → `actionAboutApproveflow(...)` 生成 `WorkflownoteVO`（审批/送审窗口）；`m_isSuccess==false` → `return null`；
3. 否则若 `isStartAction`/`isSignalAction` → `actionAboutWorkflow(...)`，同样 `return null`；
4. 若 `isAddApproverOrAppoint(worknote)` 且携带 `besideapprove` 上下文 → 只弹提示 `MessageDialog.showHintDlg`，**`return` 且不调 `processAction`**；
5. `worknote==null` → `eParam.put("notechecked","notechecked")`；
6. 最终 `NCLocator.lookup(nc.itf.uap.pf.IplatFormEntry)` → `processAction(actionName, billType, worknote, vo, userObj, eParam)`（偏移 `333–344`）。

**结论**：243 的「确定」最终提交点 = 上述第 6 步 `IplatFormEntry.processAction`；窗口打开、取消、只加签批人三种情况都**不产生业务写入**。客户端可插入的最后一个点是 `procFlow`/`PFlowProxy.runAction` 之前（动作 `doAction` 或 `PFlowContext` 装配处）。
**`[未核实]`**：`PfUtilBaseTools.isSaveAction/isApproveAction/isSignalAction` 的判定实现——该类（`nc.vo.pf.change.PfUtilBaseTools`）在本机 HOME 的 `client\lib`、`nchome\lib`、各模块 `client\classes` 中均未定位到类体；仅核实了调用点。观察位置：`E:\01-Home\NC65home\QuDuTest\nchome` 全盘 `jar` 内检索该类，或运行时断点。

> 另注：241 的 `actionName` 是 `SAVE`，与 `isSaveAction` 同名规则，因此**提交也可能弹「送审/审批」窗口**（取决于该交易类型是否配了审批流定义 `hasApproveflowDef`）。这是原生行为，覆盖时必须保留。

### 3.4 外部开票 / 税控 / 跨模块副作用

**（a）6 项自身无税控调用** `[静态]`。分两轮扫描 6 个客户端动作类与 7 个服务端动作类字节码（关键字 `HttpClient / HttpURL / java.net / Socket / WebService / jinshui / goldtax / GoldTaxVO / fapiao / Toolkit / Runtime.getRuntime`），**0 命中**；这 6 个类里也**没有** `IplatFormEntry / PfUtilClient / PFlowProxy` 字样——PF 入口完全在框架基类中（§3.3）。命中的唯一文件是范围外的 `tax-ExportGoldTaxAction.javap.txt`（见下）。

**（b）金税走独立按钮，不在 6 项内** `[静态]`：

| 按钮 | `PK_BTN` | bean / 类 | 行为 |
| --- | --- | --- | --- |
| `ExportGoldTax` / 传金税 | `1001Z810000000008DPI` | `exptGoldTaxAction` = `nc.ui.so.m32.billui.action.ast.ExportGoldTaxAction`（`saleinvoice_config.xml:708`、`:749–751`） | `new TransGoldTaxDlg(workbench)` → `setGoldTaxVOs(...)`、`setPkOrg(表头 pk_org)` → `showModal()`；确认后 `NCLocator.lookup(ISaleInvoiceMaintain).updateWhenExportGoldTax(HVO[])` → `model.directlyUpdate(...)` |
| `ImportTaxCode` / 导入税票号 | `1001Z810000000008DQ8` | `imptTaxCodeAction` = `ast.ImportTaxCodeAction`（`:707`、`:746–748`） | 识别税票号 |
| `VatSubTotal` / 税额小计 | `0001Z81000000000ZK4I` | `vatSubTotalAction` = `ast.VatSubTotalAction`（`:710`、`:754–757`） | 税额小计（`ISBUTTONPOWER=Y`） |

表头字段 `btogoldtaxflag`/`tgoldtaxtime`/`vgoldtaxcode` 由这些按钮写入。→ **本批 6 项不触发金税/数电/开票平台**；若验收要求「已传金税不得取消审批/删除」这类业务约束，需要单独核实（见 §4.5）。

**（c）但 6 项会跨模块写** `[静态]`（这是「副作用」风险的真实来源，均经 `NCLocator` 调 NC 内部接口，不是 HTTP）：

| 动作 | 服务端规则 / 调用 | 目标 | 证据 |
| --- | --- | --- | --- |
| 审批后 | `ReWriteArsubAppRule` | `nc.pubitf.so.m35.so.m32.IArsubToSaleInvoice.writeNoriginvoicemny(String[], boolean)`（应收 m35） | `rule-ReWriteArsubAppRule.javap.txt`、`server\RemoteFormSOUtil.javap.txt:236–247` |
| 审批后 | `PushSquareRule` | `nc.pubitf.so.m33.so.m32.ISquare33For32.pushSquareSrv(SaleInvoiceVO[])`（销售结算 m33） | `rule-PushSquareRule.javap.txt` |
| 取消审批后 | `ReWriteArsubUnAppRule` | 同上 `writeNoriginvoicemny(ids, false)` | `rule-ReWriteArsubUnAppRule.javap.txt` |
| 取消审批后 | `DeleteVoucherRule` | `SysInitGroupQuery.isFIPEnabled()` 为真时 → `createBillsForFIP` + `sendMessage`，向**会计平台（FIP）**发实时凭证删除消息 | `rule-DeleteVoucherRule.javap.txt` |
| 取消审批后 | `CancleSquareRule` | `ISquare33For32.cancelSquareSrv(...)` | `rule-CancleSquareRule.javap.txt` |
| 审批/取消审批 | `BusiLog` | `ISaleInvoiceScriptMaintain`（业务日志，AOP `BusilogAspect`） | `rule-*.javap.txt`、`SCM_SO_SALEINVOICE_bill.aop` |
| 删除后 | `RewriteARSubDeleteRule` / `RewriteBillDeleteRule` / `ReturnBillCodeRule` / `UpdateOppFlagDeleteRule` | 应收回写、来源单回写、单据号退回、对冲标记更新 | `server\DeleteSaleInvoiceBP.javap.txt:57–97` |

**结论**：6 项的「本地业务」和「跨模块回写/平台消息」是否同事务，**必须实测**；`RemoteFormSOUtil` 的每个方法都在 `try{BusinessException}` 后 `ExceptionUtils.wrappException`（即**吞掉异常后抛出包装异常**），对「外部已成功、本地回滚」的判定没有保护作用。

### 3.5 「保存并提交」两段式语义（`saveandcommitAction`）

`[静态]` `parent-SaveAndCommitScriptAction.javap.txt:68–91`：

```
doAction(e):
  e.setSource("SaveAndCommitScriptAction")
  saveAction.actionPerformed(e)                      // 第 1 段：WRITE，自带校验/回滚
  if (model.getUiState() == UIState.NOT_EDIT) {      // 只有保存成功并退出编辑态
     this.setExceptionHandler(commitAction.getExceptionHandler())
     commitAction.actionPerformed(e)                 // 第 2 段：SAVE（提交）
  }
```

- 构造器强制注入两个 `NCAction`（配置 `:446–450` 的 `constructor-arg` 正是 `saveAction` + `commitAction`），`ActionInitializer.initializeAction(this,"SaveCommit")`。
- **无 try/catch**：第 1 段异常直接冒泡，第 2 段不执行；
- 判定条件是 `UIState==NOT_EDIT`，不是「返回值/标志位」——即**保存失败停留在 EDIT 就不会提交**；
- 第 2 段失败时第 1 段已落库：语义为「已保存、未提交」，与订单同类按钮一致。
- `SaleInvoiceSaveCommitAction` 只重写 `isActionEnable`（`EDIT/ADD/TRANSFERBILL_ADD` 或 `NOT_EDIT` 有选中且状态 `FREE`）。

**风险**：若为 6 项做签名包装，`saveandcommitAction` 是**一个** bean 触发**两个** PF 动作，签名/审计需按两段分别落（保存成功但提交取消时，保存结果必须保留）。

### 3.6 取消审批的下游/核销/凭证限制（244，服务端已核实）

`UnApproveSaleInvoiceAction` 规则链（`server\UnApproveSaleInvoiceAction.javap.txt`）：

**before**
1. `CheckUnAppEnableRule`，按表头逐单四条检查（`rule-CheckUnAppEnableRule.javap.txt:9–134`）：
   - 状态必须 `AUDIT`/`AUDITING`/`NOPASS`，否则抛 `4006008_0 / 04006008-0033`（`:38–79`）；
   - `SaleInvoiceHVO.getFopposeflag() == OpposeFlag.FINSH` → 抛 `4006008_0 / 04006008-0034`（对冲已完成，`:82–104`）；
   - **审批权限**：`BillPowerChecker.hasApproverPermission(IBill, SOBillType.Invoice.getCode())` 为 false → 消息 `40060501 / 1400605010035`（`:107–131`）；
   - 若 `SysInitGroupQuery.isICEnabled()`：`NCLocator.lookup(I4CQueryPubService).existBill(ids, true, SOBillType.Invoice.getCode())` 任一为 true → 抛 `4006008_0 / 04006008-0035`。**即「已被下游出库单引用」禁止取消审批**（`:154–283`）。
2. `CheckConsumeRule`：逐行检查 `SaleInvoiceBVO.getCsumid()`，非空 → 抛 `4006008_0 / 04006008-0032`。**即「行已被消耗汇总（核销）」禁止取消审批**（`rule-CheckConsumeRule.javap.txt:9–63`）。
3. `BusiLog`（业务日志）。

**after**
4. `ReWriteArsubUnAppRule`（应收反写）；
5. `DeleteVoucherRule`（`isFIPEnabled` 时删会计平台实时凭证）；
6. `CancleSquareRule`（撤销销售结算推送）。

对照：**审批** before = `CheckAppEnableRule`（状态 `FREE`/`AUDITING`；单据日期早于业务日期 → `4006008_0 / 04006008-0029`）+ `BusiLog`；after = 应收回写 + 结算推送。**提交** before = `CheckCommitEnableRule`（`FREE`/`NOPASS` + 交易类型/组织）+ `SetCommitStatusRule`。**删除** before = `CheckEnableDeleteRule`（状态/审批人）；after = 4 条回写规则。**收回**（`UnCommitSaleInvoiceAction.unSend(vos, oldvos)`）**只有 `BillUpdate.update`，无 before/after 规则、无插件点**——注意 `Action32PlugInPoint` 虽定义了 `UnSendAppAction`，该实现类**未使用**它。

---

## 4. 未核实清单

| # | 事项 | 观察位置 / 说明 |
| ---: | --- | --- |
| 4.1 | `fs_delete` 的真实行为（删附件 vs 删发票） | 真实会话点击 `File/附件管理` 面板；`sm_butnregister` 组归属；附件服务端接口 |
| 4.2 | 现场是否存在挂在 `Action32PlugInPoint`（`so/m32`）的插件 | `pub_pluginitem` 按 `module=so`、`point` 过滤；本次未查（`Action32PlugInPoint` 7 个点：`Insert/Update/Delete/SendApp/UnSendApp/Approve/UnApprove`） |
| 4.3 | 审批窗口内 `WorkflownoteVO`（意见/决议/指定审批人/工作项）的落库 | 真实审批会话；`PFlowContext.eParam` 键 `worknote/silently/nosendmessage/besideapprove/notechecked` |
| 4.4 | `PfUtilBaseTools.isSaveAction/isApproveAction/isStartAction/isSignalAction` 的判定实现 | 类体 `nc/vo/pf/change/PfUtilBaseTools` 在本机 HOME 的 `client\lib`、`nchome\lib`、模块 `client\classes`、`classes` 中均未定位；只能核实调用点 |
| 4.5 | 已传金税（`btogoldtaxflag=true`）的发票能否取消审批/删除 | 本次在 `CheckUnAppEnableRule`/`CheckEnableDeleteRule`/`CheckConsumeRule` 中**未见** `btogoldtaxflag` 判定 → 客户端/服务端规则层无此限制；但下游 `ExportGoldTax` 之后是否有其它拦截未核实 |
| 4.6 | 物理表名与真实列 | 元数据名 `so.saleinvoice`/`so.saleinvoice_b` 已核实；物理表 `so_saleinvoice`/`so_saleinvoice_b` 为命名规则**推断**；未连库、未读 ORM/BMF（`uiso_saleinvoice.jar`/`pubso_saleinvoice.jar`/`so_saleinvoice.jar` 内无 `.bmf/.upm` ORM 资源） |
| 4.7 | 具体交易类型值 | 配置只有 `billType=32`；`trantype` 由运行时 `PFlowContext.getTrantype()` 提供 |
| 4.8 | 各 `power*validservice` 是否含数据权限 | `powerdeletevalidservice`/`powercommitvalidservice`/`poweruncommitvalidservice`/`powerapprovevalidservice`/`powerunapprovevalidservice` 的类体与配置 |
| 4.9 | 服务端事务边界（本地 + 应收/结算/FIP 是否同事务） | 需在测试库用真实事务观测；`RemoteFormSOUtil` 各方法 `catch{BusinessException → wrappException}`，无法从字节码判定 |
| 4.10 | 页签（`PAGECODE`）级按钮注册 | `sm_pageregister` 三表 JOIN |
| 4.11 | `saleinvoice_unApprove_config.xml` 实际属于哪个功能节点 | 该配置的 `IA201Model`/`ClosingCheckModelService` 属关账检查列表；本节点 `saleinvoice_config.xml` 0 引用 |

---

## 5. 原始证据索引

目录：`build/native-evidence-other-nodes/invoice/`（全部为 `javap -p [-c|-constants]` 原始输出与只读提取副本；本次生成，未覆盖任何既有文件）

### 5.1 客户端动作类（`uiso_saleinvoice.jar`，`javap -p -c`）

| 文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| `SaleInvoiceSaveAction.javap.txt` | 36459 | `EA245AB4A1453883AB39CCDB6CD54A128311A0C30A05FD585BA01795739D8396` |
| `SaleInvoiceDeleteAction.javap.txt` | 12265 | `EDB7365D00C547A04DA04DB5AE43744D3812973D011E037BC83462395F5116FE` |
| `SaleInvoiceCommitAction.javap.txt` | 11996 | `5467ED8B92D1F03F37A106925A58C187DCC290477223B3855CBBEF21F8E361CE` |
| `SaleInvoiceUnCommitAction.javap.txt` | 10918 | `CE771AE15D63E7B4D40BE338238D0099B1733D2B403B8EF318B07D4BEB6C8DBE` |
| `SaleInvoiceApproveAction.javap.txt` | 10782 | `2D6378A9419F46CE1D412A2AE13F53FF97DE5AC6B4E1250F81A8E9A82D75200D` |
| `SaleInvoiceUnApproveAction.javap.txt` | 12163 | `766E98C680983624291A9C391465C17AEDE687CB998C8D4E0093EDE10BF8D534` |
| `SaleInvoiceSaveCommitAction.javap.txt` | 5075 | `3180996BEE2C0747AFB6E3A1E99D141498FCC7CF48448DAD878831CA150387E2` |
| `tax-ExportGoldTaxAction.javap.txt`（对照，非本批） | 22888 | `D2E9EB47E43E28B226679AFE4FAD0EB46DFD5488C34C08E7CCBD8169B3531BFE` |
| `attachment-SOManageDocumentAction.javap.txt`（`uiso_pub.jar`） | 1547 | `25EB71FEB66CA34ABC070002420845FBCA32464F8FAC5E5F7B64289289BF8FA6` |

### 5.2 PF 框架基类（`uipubapp_platformextLevel-1.jar` / `uiriart_riartplatformLevel-1.jar`）

| 文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| `parent-AbstractScriptExcAction.javap.txt` | 39326 | `2BCB9DD4C1A782A7511A80D07E5832FB1C91CA99119424E4177AA9B0E37E9B69` |
| `parent-ScriptPFlowAction.javap.txt` | 67769 | `7BF962DCF558219A0BBBB12567442F5F489A0A587F4701B2B29111BAB6341168` |
| `parent-DeleteScriptAction.javap.txt` | 7947 | `DBF4DE3510E2C297D3F1E4FB1C7F06C59D21C349ABF5525BA320C8F89730E194` |
| `parent-SaveScriptAction.javap.txt` | 2170 | `D091B884A23A9F8AD6A782DE934C5BA577E65D3CE4AEC34DC4DA4C906C628AE1` |
| `parent-SaveAndCommitScriptAction.javap.txt` | 5955 | `FFB5830CED6C6151022E92260816C426DF552525A79C72CC9819049AE0AF464F` |
| `parent-CommitScriptAction.javap.txt` | 4733 | `A2588C68C1CB936DA318ACFB4E9AC98D4BCD53DBC3D1230273AC1F0895BA3E24` |
| `parent-UnCommitScriptAction.javap.txt` | 4441 | `601D17B573A4E1CD2DFD825173356D01E1437D4DD243EEAD62E13B17F556C180` |
| `parent-ApproveScriptAction.javap.txt` | 9747 | `F67DC8956E5932298BD32E5BC474CF8D65A55FB9B49963F864B52B8701BB9105` |
| `parent-UNApproveScriptAction.javap.txt` | 4942 | `0099FE8BC2E93787E1DA1F535E31E715EB18D5301D9BED8E9E4A7EB894BEBAC8` |
| `parent-PFlowProxy.javap.txt` | 8723 | `4999C0FCE557F53B065AC3C7C929970A7C9F3C5BD9B87F3812A9520DF49671AA` |
| `parent-PfUtilClient.javap.txt` | 236929 | `B9F7A2596B4D4449257D5DD521EE65D888B16A44CCB9FD592ED7C6B63C4EB3B7` |

### 5.3 VO（`pubso_saleinvoice.jar`）

`vo-SaleInvoiceVO.javap.txt`、`vo-SaleInvoiceHVO.javap.txt` + `.constants.txt` + `.code.javap.txt`、`vo-SaleInvoiceBVO.javap.txt` + `.constants.txt` + `.code.javap.txt`、`vo-SaleinvoiceUserObject.javap.txt`、`vo-SaleInvoiceViewVO.javap.txt`、`vo-SaleInvoiceViewMeta.javap.txt`（逐个 SHA-256 见 `build/native-evidence-other-nodes/invoice/` 目录实测值；`.constants.txt` 为 `javap -p -constants` 输出，含字段字面值）。

### 5.4 服务端（`so_saleinvoice.jar` / `pubso_saleinvoice.jar`，均在 `server/` 子目录）

`InsertSaleInvoiceAction`、`UpdateSaleInvoiceAction`、`DeleteSaleInvoiceAction`、`CommitSaleInvoiceAction`、`UnCommitSaleInvoiceAction`、`ApproveSaleInvoiceAction`、`UnApproveSaleInvoiceAction`、`DeleteSaleInvoiceBP`、`SaleInvoiceCheckStatus`、`Action32PlugInPoint`、`ISaleInvoiceMaintain`、`RemoteFormSOUtil`，以及规则类 `rule-CheckAppEnableRule`、`rule-ReWriteArsubAppRule`、`rule-PushSquareRule`、`rule-CheckCommitEnableRule`、`rule-SetCommitStatusRule`、`rule-CheckUnAppEnableRule`、`rule-CheckConsumeRule`、`rule-DeleteVoucherRule`、`rule-ReWriteArsubUnAppRule`、`rule-CancleSquareRule`、`CheckEnableDeleteRule`。

### 5.5 配置与现场快照

| 文件 | 说明 |
| --- | --- |
| `saleinvoice_unApprove_config.xml` | 从 JAR 只读提取的第二个配置（5002 字节），供 §1.2、§4.11 判断使用 |
| `site-buttons-40060501.tsv` | `buttons_1.json` 中 45 行 `40060501` 按钮的规范化导出（`BTNCODE/BTNNAME/PK_BTN/ISBUTTONPOWER/BTNOWNERTYPE/ISENABLE/ISKEYFUNC`） |
| `build/native-configs-other-nodes/saleinvoice_config.xml` | 既有解压副本（本次未改动），SHA-256 `4568BD3B…C8D996` |

### 5.6 复现命令（只读）

```
E:\01-Home\NC65home\QuDuTest\nchome\ufjdk\bin\javap.exe -p -c ^
  -classpath E:\01-Home\NC65home\QuDuTest\nchome\modules\so\client\lib\uiso_saleinvoice.jar ^
  nc.ui.so.m32.billui.action.SaleInvoiceSaveAction
```

基类把 `-classpath` 换成 `modules\pubapp\client\lib\uipubapp_platformextLevel-1.jar`；`PfUtilClient` 换成 `modules\riart\client\lib\uiriart_riartplatformLevel-1.jar`；服务端换成 `modules\so\META-INF\lib\so_saleinvoice.jar`；VO/接口换成 `modules\so\lib\pubso_saleinvoice.jar`。

---

## 6. 本批交付边界

- 本文只做**只读取证**：未部署、未注册、未写库、未启动或停止 NC、未外发、未运行构建。
- 「静态核实」不等于联调通过。**只有通过真实 NC 会话验证的入口才可标记「已完成并通过实际测试」**；本文的 6 项目前均为静态核实。
- §4 的 11 项未核实事项在进入实现前必须先有结论，尤其是 4.1（`fs_delete` 语义）、4.2（现场插件）、4.6（物理表）、4.9（事务边界）。
