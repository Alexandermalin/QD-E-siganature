# 销售订单维护 `40060301` 第 209–216 行 S0 证据表、990 保存基线与拟改文件清单

- 交付批次：**S0 冻结基线与取证**（对应 `docs/nc65-electronic-signature/SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md` 第 46 行）
- 基线日期：2026-11-11
- 项目根（只读）：`E:\02-Project\QiDu\QD-E-Siganature`
- NC65 开发 HOME（只读）：`E:\01-Home\NC65home\QuDuTest\nchome`（由 `modules\so\client\lib\uiso_salesorder.jar` 实测存在确认；HOME 自带 `ufjdk` 为 IBM JDK 7 / `1.7.0-internal`）
- 交付性质：**离线只读证据**。未部署、未执行任何 SQL、未启动/停止 NC、未修改项目目录或 HOME、未外发。**真实 NC 会话核实为 0 条**，本文件所有"静态核实"只表示"读到 JAR 字节码/XML 原文或现场库只读导出"，不等于联调通过。
- 详细附录（同批取证，均为只读产出）：
  - `s0-evidence/site-evidence.md`（446 行，现场库按钮/范围/节点参数证据，SHA-256 `730FA18B02656B1300FD652317FA76EBA6E5C03657E4B8A76D7566438C4CBCB0`）
  - `s0-evidence/native-mapping.md`（237 行，原生落点与实现链，SHA-256 `5926C17415CE2FC98332E514D29527271D1F9D4DF3CEE25D0ECE0B3A8E5E4114`）
  - `s0-evidence/baseline-990.md`（537 行，990 保存基线与拟改清单证据基础，SHA-256 `7F5FC77B966D43D8F8BC91B2AC26B7D962D2F56753405CCB125BF63F77EF5520`）
- 证据等级记号：`[静态]` = 直接读自 JAR 内 XML/字节码；`[库快照]` = 来自项目已存的 2026-11-07 QDNC 只读导出；`[未核实]` = 本机只读条件无法确认，已给观察位置。

---

## 0. 结论速览（先读这一段）

1. **本批 8 项 = 正式清单源行 209–216**：删除、提交、收回、审批-确定、取消审批、冻结、解冻、整单关闭。`nodeId=QD-NODE-642F7D874EA13550`，`adapterFamily=SO_PFLOW`，8 个范围 ID 见 §1.3。第 208 行 `Save/INSERT` 是既有保存试点基线，本批保留不扩，208 不在本批 8 项内。
2. **9 个按钮 ID 中只有 8 个是订单动作**：`fs_delete / 1002Z81000000000QCAZ` 与 `fs_browse/fs_download/fs_rename/fs_upload` 同组（PK 前缀 `1002`），属附件/文件面板按钮组，`uiso_salesorder.jar` 内 0 命中、无对应 bean，**不是订单删除入口**，不应作为 209 的实现落点（§3.1）。
3. **8 项客户端绑定的 action code 与现场 `btncode` 逐一相等** `[静态]`：Delete/Commit/UnCommit/Approve/UnApprove/Freeze/UnFreeze/BillClose。覆盖层必须保留 bean id 与 action code，只能换 class/属性，否则模板找不到按钮（§2、§4.1）。
4. **五项链归属已静态贯通** `[静态]`：删除/提交/收回/审批/取消审批 保留完整 PF 路径（`DELETE/SAVE/UNSAVE/APPROVE/UNAPPROVE`），冻结/解冻/整单关闭 走 `ISaleOrderCloseManageMaintain` 直接服务（不是 PF）。212 的「确定」不是注册按钮，策略与权限用真实 `Approve`（§3.2）。
5. **当前 990 保存基线 = `build\deployment\20261111-155019-9df7af95\plan.json`**（release `display-catalog-only-20261111-154957-0763568d`），21 个目标文件**21/21 与当前 HOME 逐字节一致**（mismatch=0）。10:08:53 的销售补丁与 14:07 空收款热修都**不是**当前快照（§5）。
6. **三个必须先处置的基线缺口**（都不是本批代码问题，但会卡住 S1 及联调）：
   - **缺口 A**：`build\sales-990-isolated\build-record.json` 冻结的是**旧源码**（其 `SaleOrderPfSaveAdapter.class` 为 8555B/`DC6A6A84…`，含仓库已删字符串、无 `isEmptyBalance`），与当前源码（`3008DE55…`）和 HOME 实际类（10021B/`1E130A22…`）都不一致 → `sales-order-990-release\build.ps1 -Verify` 必然失败，**当前 990 热修没有有效构建记录背书**。
   - **缺口 B**：空收款热修自称的 "26 assertions" 测试源码未入库（仓库测试只有 `$1…$14`，热修 classes 有 `$1…$24`，无构建记录）。
   - **缺口 C**：HOME `modules\so\META-INF\qd_esign_saleorder.upm` 为 665B、**只有 1 个 component**；主线源码 976B 含第 2 个 `ISaleOrderPfSaveAdapter` 注册 → **未部署**。而 `sql\...\sale-order-save-pilot\configure.sql:9` 正把 `adapter_class` 写成 `ISaleOrderPfSaveAdapter`，网关 `OperationTransactionServiceImpl:277` 用 `NCLocator.lookup` 取它 → 若 DB 里确为该接口，保存会失败；今天能跑通是因为 `SaleOrderOperationAdapter:45` 内部 `new SaleOrderPfSaveAdapter()` 绕过了 NCLocator。**本批要么先补部署该 upm，要么映射统一改走 `ISaleOrderOperationAdapter`。**
7. **网关层把 8 项归 `DELETE`/`UPDATE` 可行，阻碍全在适配器层**：`OperationTransactionServiceImpl` ADAPTERS 白名单（28–51 行）已有两个销售接口；`MappingRules.requireDomain` 通用分支放行；`OrderPhysicalSnapshots.forAdapter`（24–31 行）已识别两个销售适配器且支持 INSERT/UPDATE/DELETE，删除还有"表里还能读到就报错"的反向断言。真正要改的是 `SaleOrderPfSaveAdapter:30–32`（硬限 Save+INSERT）、`SaleOrderOperationAdapter.isPilotSave`（只认 Save，非 INSERT 走低层脚本不是 PF）、`SaleOrderPfPreparation:67`（硬限 `WRITE`）。
8. **建议归类**：209 = `DELETE`；210–216 = `UPDATE`（复用现有快照分支，日志用真实按钮编码/名称区分业务动作）。若验收要求独立动作类型，单列改动，不用通用 UPDATE 兜底。
9. **外发与插件**：`PluginBeanConfigFilePath`（`saleorder_esign_probe.xml`）**注册存在但文件在本机 HOME 及其 JAR 中 0 命中（悬空，不生效）**；`PluginBeanConfigFilePath_Industry0_tempsave` 目标文件存在（仅暂存扩展，不拦截 8 项）；2026-11-07 `pub_pluginitem` 快照**无审批后置 OA 插件**，so/m30 只有 1 行"删除后→PMS"规则（`nc.pubimpl.so.m30.pms.rule.M30_Del2PmsRule`，实现类在本机 3711 个 JAR 中未找到）。**"现场是否新增 OA/APS 审批后置或独立事务"仍属未核实，不能据此开启带外发的入口。**
10. **本批交付边界**：源码、测试、候选脚本与回退说明；真实部署、注册/数据库写入、启停共享 NC、实际外发由项目负责人安排测试窗口执行。**只有通过真实 NC 联调的入口才可标记"已完成并通过实际测试"。**

---

## 1. 范围、注册与正式清单

### 1.1 节点与组织

| 项 | 值 | 证据 |
| --- | --- | --- |
| 功能节点 | `40060301` 销售订单维护，`CFUNID=1001Z810000000000UV6` | `[库快照]` `function_candidates.json`（FUNCODE=40060301 行）；`buttons_1.json` 8 行 `PARENT_ID` 一致 |
| 客户端配置 | `BeanConfigFilePath=nc/ui/so/m30/billui/model/saleorder_config.xml`（PK_PARAM `1001Z810000000000UV7`） | `[库快照]` `node_ui_params.json:L44-L48` |
| 其他两个节点参数 | `PluginBeanConfigFilePath=nc/ui/so/qd/esign/config/saleorder_esign_probe.xml`（`1001A9100000009NMGNC`）；`PluginBeanConfigFilePath_Industry0_tempsave=nc/ui/so/m30/billui/model/saleorder_tempsave_config.xml`（`1002Z810000000Z0C4QC`） | `[库快照]` `node_ui_params.json:L51-L62` |
| 首轮集团 / 销售组织 | `0001A9100000000005MH` / `0001A9100000003UDS6R`（990 销售组织） | 计划第 9 行；组织侧仍需联调确认 |
| 按钮注册总数 | 68（与 `node-bindings.csv:L39 registeredButtonCount=68` 吻合） | `[库快照]` `buttons_1.json` 按 FUNCODE 过滤 |
| 页签按钮注册 | **未核实**。`buttons_1.json` 只 JOIN `sm_funcregister`+`sm_butnregister`，无 `PAGECODE/PK_PAGE`；2026-11-11 页签导出白名单仅 6 个 `10140*` 功能，本节点命中 0 行（属查询范围设定，不是"查过无结果"） | `[未核实]` 观察位置：同款三表 JOIN 把 funcode 换为 `40060301/40060303` |

### 1.2 八个按钮现场注册行

全部为 `[库快照]` `buttons_1.json`；8 行均 `BTNOWNERTYPE=0`（功能直属按钮形态）、`ISENABLE=Y`、`ISBUTTONPOWER=N`（不纳入按钮权限体系）、`ISKEYFUNC=N`、`PARENT_ID=1001Z810000000000UV6`（=功能 CFUNID）。

| 原表行 | 操作（正式清单） | `btncode` | `btnname` | `pk_btn` | 证据行 |
| ---: | --- | --- | --- | --- | --- |
| 209 | 删除 | `Delete` | 删除 | `1001Z810000000004TO3` | `buttons_1.json:L203` |
| 210 | 提交 | `Commit` | 提交 | `1001Z810000000004TOA` | `L131` |
| 211 | 收回 | `UnCommit` | 收回 | `1001Z810000000004TOG` | `L683` |
| 212 | 审批-确定 | `Approve` | 审批 | `1001Z810000000004TO2` | `L35` |
| 213 | 取消审批 | `UnApprove` | 取消审批 | `1001Z810000000004TOO` | `L671` |
| 214 | 辅助功能-冻结 | `Freeze` | 冻结 | `1001Z810000000004TP5` | `L251` |
| 215 | 辅助功能-解冻 | `UnFreeze` | 解冻 | `1001Z810000000004TOE` | `L695` |
| 216 | 辅助功能-整单关闭 | `BillClose` | 整单关闭 | `1001Z810000000004TP1` | `L83` |
| — | 附件组删除 | `fs_delete` | 删除 | `1002Z81000000000QCAZ` | `L755`（**本批范围外**，见 §3.1） |

### 1.3 正式清单映射（`config/qd-esign/operation-scope.csv`）

8 行的 `ncFunctionCode`/`ncActionCode` **全空**，`mappingStatus=SOURCE_CANDIDATE_ONLY`，`notes=仅需求目录；未登记真实NC功能/按钮编码…` → **正式清单本身不含按钮编码**，1:1 对应只能靠派生快照 `config/qd-esign/site-20261107/operation-bindings.csv`。

| 源行 | `scopeId` | 派生 `ncFunctionCode` | 派生 `entryButtonCode` | `registeredButtonPk` | 派生 `mappingState` |
| ---: | --- | --- | --- | --- | --- |
| 209 | `QD-ES-FEFBC5B292C330AD` | 40060301 | （空） | （空） | `NO_UNIQUE_REGISTERED_BUTTON`（Delete 与 fs_delete 同名） |
| 210 | `QD-ES-4671CC2E70A34B97` | 40060301 | `Commit` | `1001Z810000000004TOA` | `REGISTERED_BUTTON_RPC_PENDING` |
| 211 | `QD-ES-4F23D38FB22BA45F` | 40060301 | `UnCommit` | `1001Z810000000004TOG` | `REGISTERED_BUTTON_RPC_PENDING` |
| 212 | `QD-ES-9C0B035FFEB2110D` | 40060301 | `Approve` | `1001Z810000000004TO2` | `ENTRY_ONLY_SUBPAGE_PENDING` |
| 213 | `QD-ES-51BB76212FFCD156` | 40060301 | `UnApprove` | `1001Z810000000004TOO` | `REGISTERED_BUTTON_RPC_PENDING` |
| 214 | `QD-ES-C7E51B3ED69F8B05` | 40060301 | `Freeze` | `1001Z810000000004TP5` | `REGISTERED_BUTTON_RPC_PENDING` |
| 215 | `QD-ES-BF1F44B944C037B3` | 40060301 | `UnFreeze` | `1001Z810000000004TOE` | `REGISTERED_BUTTON_RPC_PENDING` |
| 216 | `QD-ES-2A439AB1FE3628B0` | 40060301 | `BillClose` | `1001Z810000000004TP1` | `REGISTERED_BUTTON_RPC_PENDING` |

- 208 保存行：`scopeId=QD-ES-EAF88AC5CAF75442`，与 `SaleOrderPilotScope.SCOPE_ID`、`sale-order-save-pilot/configure.sql:8` **三处逐字符一致** `[静态]`。
- 取证陷阱：`operation-bindings.csv` 的 `pageName` 列**不是** `sm_pageregister.PAGECODE`（值等于 buttonName），不能用于填 pagecode。

### 1.4 第 217–228 行（`40060303` 销售订单关闭管理）独立性

`[库快照]` 两节点 `PK_BTN` **零交集**；`BTNCODE` 大小写敏感仅 4 个同名（`BillClose`/`BillLinkQuery`/`Query`/`Refresh`，各自独立注册：如 `BillClose` 40060301=`…TP1`、40060303=`…DAZ`）；忽略大小写另加 `Preview↔preView`、`Print↔print/printGroup`。`40060303` 无 `Delete/Commit/Approve/fs_*`。→ **支持"第 217–228 行为独立批次"**，客户端入口、按钮权限与策略须按不同 `pk_btn`/`cfunid` 分别核对。

---

## 2. 逐按钮证据表（主表）

`[静态]` 下列 bean 与类均读自 `uiso_salesorder.jar!nc/ui/so/m30/billui/model/saleorder_config.xml`（提取副本 SHA-256 `12F85390…75B3`，66981 字节 / 1586 行；行号=JAR 条目原文行号）与 HOME 各 JAR 字节码；按钮侧为 `[库快照]`。

| # | 操作 / 按钮 | 页面 bean（id = class，关键属性） | 动作类与可核实行为 | 最终写入调用 | PF / RPC | 刷新 / 异常 / 外发 | 核实 |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 209 | 删除 `Delete`<br>`1001Z810000000004TO3` | `deleteAction` = `nc.ui.so.m30.billui.action.SaleOrderDeleteAction`；`actionName=DELETE`、`billType=30`、`validationService=powerdeletevalidservice`（`saleorder_config.xml:800-811`） | `extends DeleteScriptAction`，无自定义 PF 逻辑。`doAction`：取选中→`ISOBalanceQuery.querySoBalanceVOBySaleOrderIDs` 余额校验→`MessageDialog.showYesNoDlg` 二次确认→`super.doAction`；`isActionEnable` 要求 `BillStatus.FREE`。基类先 `CommonConfirmDialogUtils.showConfirmDeleteDialog` 再 `ScriptPFlowAction.doAction`；`processReturnObj` 用 `PfUtilClient.isSuccess()` 决定 `directlyDelete`/`showFailedInfo` | `AbstractScriptExcAction.doAction → procFlow → PFlowProxy.runAction → PfUtilClient.runAction → IplatFormEntry.processAction("DELETE", 30/交易类型, worknote, vo, userObj, eParam)` | **PF `DELETE`** | 原生二次确认与失败提示保留；外发未核实（见 §3.6） | 客户端静态；服务端 PF 实现未核实 |
| 210 | 提交 `Commit`<br>`1001Z810000000004TOA` | `sendApproveAction` = `SaleOrderSendApproveAction`；`filledUpInFlow=true`、`actionName=SAVE`、`billType=30`、`validationService=powersendappvalidservice`、`tpaProgressUtil`（`:856-879`） | `extends CommitScriptAction`，**未重写 doAction**，只重写 `isActionEnable`（EDIT/ADD/COPY_ADD 或 NOT_EDIT 且 FREE/多选）。基类构造器 `setActionName("SAVE")` | 同上 PF 链；批量走 `MultiBillScriptRunner.runTask → PfUtilClient.runBatchNew` | **PF `SAVE`** | **同一个 bean 被 `saveandsendApproveAction` 复用**，覆盖一次即同时管住「保存并提交」的提交段；提交取消/失败时已成功的保存结果保留（§4.6） | 静态；真实交易类型/指定审批人分支未核实 |
| 211 | 收回 `UnCommit`<br>`1001Z810000000004TOG` | `unsendApproveAction` = `SaleOrderUnSendApproveAction`；`filledUpInFlow=true`、**`actionName=UNSAVE`（来自配置，类内不写死）**、`validationService=powerunsendappvalidservice`（`:881-895`） | `extends UnCommitScriptAction`，只重写 `isActionEnable`（`approver` 为空且状态 AUDITING）。基类构造器只 `putValue("Code","UnCommit")`，**不调用 setActionName** | 同上 PF 链 | **PF `UNSAVE`**（覆盖时必须显式保留 `actionName` 属性） | 原生流程上下文与工作项由 PF 处理；不与取消审批共用授权 | 静态 |
| 212 | 审批 `Approve`<br>`1001Z810000000004TO2` | `approveAction` = `SaleOrderApproveAction`；`filledUpInFlow=true`、`actionName=APPROVE`、`validationService=powerapprovevalidservice`、`tpaProgressUtil`（`:897-911`）；组装 `approveActionGroup`（含 `queryAuditFlowAction`，`:929-937`） | `extends ApproveScriptAction`，只重写 `isActionEnable`（NOT_EDIT 且 FREE/AUDITING）。基类 `setActionName("APPROVE")`；`doBeforAction` 末尾 `flowContext.setUserId(登录用户)` | 同上 PF 链 → `actionAboutApproveflow → checkWorkitemWhenApprove`（审批窗口）→ `executeBusinessPlugin` → `processAction(APPROVE, …)` | **PF `APPROVE`** | 审批窗口为 `nc.ui.pf.workitem.WFStartDispatchDialog / ApproveWorkitemAcceptDlg / ApproveFlowDispatchDialog / BatchApproveWorkitemAcceptDlg`（`WorkflownoteVO` + `showModal`）；**打开/取消窗口不产生业务请求**，点击「确定」后才走 `processAction` | 静态（弹窗内参数落库未核实） |
| 213 | 取消审批 `UnApprove`<br>`1001Z810000000004TOO` | `unApproveAction` = `SaleOrderUnApproveAction`；`filledUpInFlow=true`、`actionName=UNAPPROVE`、`validationService=powerunapprovevalidservice`（`:913-927`） | `extends UNApproveScriptAction`，只重写 `isActionEnable`（AUDIT/AUDITING/NOPASS 且 `approver` 非空）。基类 `setActionName("UNAPPROVE")` | 同上 PF 链 | **PF `UNAPPROVE`** | 原生权限、工作项与下游单据检查保留 | 静态 |
| 214 | 冻结 `Freeze`<br>`1001Z810000000004TP5` | `freezeAction` = `nc.ui.so.m30.billui.action.assist.FreezeAction`；仅 `model`（`:1016-1019`）；挂在辅助功能菜单 `browAstActionGroup`（`:1553-1581`，`:1565`） | `extends NCAction`（非 PF）。`doAction`：`new SaleOrderVO[]{选中}` → `NCLocator.lookup(ISaleOrderCloseManageMaintain).freezeSaleOrder(vos)` → `ClientBillCombinServer.combine(原VO, 返回VO)` → `AbstractAppModel.directlyUpdate`；捕获 `nc.vo.pub.BusinessException` → `ExceptionUtils.wrappException`；`isActionEnable` 要求状态 AUDIT | **直接服务 RPC**：`ISaleOrderCloseManageMaintain.freezeSaleOrder(SaleOrderVO[])` | 无 PF | 原生选择、返回 VO 合并、界面刷新必须保留 | **两侧均静态核实**（实现 `SaleOrderCloseManageMaintainImpl` → `BillFreezeState` → `SaleOrderStateMachine.setState`） |
| 215 | 解冻 `UnFreeze`<br>`1001Z810000000004TOE` | `unFreezeAction` = `assist.UnFreezeAction`；仅 `model`（`:1020-1023`）；辅助功能菜单（`:1566`） | `extends NCAction`。`doAction` 为 **`while(continueFlag)` 重试循环**：每次新建 `businessCheckMap`；`unFreezeSaleOrder(vos, map)`；异常若为 `IResumeException` 且 `getBusiExceptionType()==BusinessCheck.ATPCheck.getCheckCode()` → `MessageDialog.showYesNoDlg`（ATP 不足）→ 用户选继续后 `map.put(ATPCheck.getCheckCode(), Boolean.FALSE)` **再调一次**；`isActionEnable` 要求 FREEZE | **直接服务 RPC**：`unFreezeSaleOrder(SaleOrderVO[], Map<String,Boolean>)` | 无 PF | **必须原样保留 ATP 继续/取消分支**，不得默认传入跳过检查参数；取消无业务变更 | **两侧均静态核实**（实现：`examATPCheck → SaleOrderVOATPBeforeRule → BillAuditState → setState → SaleOrderVOATPAfterRule`） |
| 216 | 整单关闭 `BillClose`<br>`1001Z810000000004TP1` | `closeAction` = `assist.SaleOrderCloseAction`；`singleBillService`=`nc.ui.so.m30.closemanage.model.M30CloseBillCloseService`（`:1024-1030`）；辅助功能菜单（`:1568`） | `extends NCAction implements IMultiReturnObjProcessor, ProgressActionInterface`。`doAction`：`MessageDialog.showInputDlg` 取**关闭原因** → 取选中 → `getChildrenVO()==null` 时 `ISaleOrderMaintain.querySaleorder(ids)` 回查补齐**整单行** → `setCloseOpenReason`（逐 `SaleOrderBVO.setVclosereason(原因)`）→ 多张 `MultiBillTaskRunner.runTask` / 单张 `singleBillService.operateBill(vo)` → `processReturnObjs`（`directlyUpdate`） | `M30CloseBillCloseService.operateBill(SaleOrderVO)` → `closeSaleOrder(new SaleOrderVO[]{vo})` | 无 PF | **关闭原因与签名事由必须分别传递**；实现 `closeSaleOrder`：`checkOrderDownBillAllApprove → updateCloseOpenReason（VOUpdate.update(children,["vclosereason"])）→ BillCloseState → setState`；`setState` 用 `BillTransferTool.getOriginBills()`（以库内原单为准）再回查 View 返回 | **两侧均静态核实** |

> 「保存并提交」`saveandsendApproveAction` = `SaleOrderSaveAndSendAction`，构造注入 `saveAction` + `sendApproveAction`（`:869-873`，挂在 `actionsOfCard.editActions` `:1520`）。→ 覆盖 `sendApproveAction` 会同时作用于该复合入口的提交段；保存段仍是既有 `saveAction` 试点。

---

## 3. 专项核实

### 3.1 `fs_delete` 归属（209 的同名歧义）

`[库快照]` 同 parent（`1001Z810000000000UV6`）下 5 枚 `fs_*`：`fs_browse`/查看 `1002…QCB0`、`fs_delete`/删除 `1002…QCAZ`、`fs_download`/下载 `1002…QCAY`、`fs_rename`/重命名 `1002…QCB1`、`fs_upload`/上传 `1002…QCAX`，全 `ISBUTTONPOWER=Y`；同 parent 另有 `File`/附件管理 `1001Z810000000004TOU`。

判定：**`fs_delete` 属附件管理按钮组，不是删订单**。依据：①5 枚构成"附件面板完整操作集"，不构成订单业务动作集；②`fs_*` 全 `isbuttonpower=Y`，而订单业务按钮几乎全 `N`；③同 parent 有独立 `File/附件管理` 入口；④订单删除已有独立 `Delete/…TO3`。
`[静态]` 旁证：`uiso_salesorder.jar` 全 418 个条目中 `fs_delete/fs_upload/fs_browse` 字符串命中 0，`saleorder_config.xml` 无对应 bean；订单页附件入口为 `docManageAction`=`nc.ui.so.pub.actions.SOManageDocumentAction`（`:1182-1185`）。
**`[未核实]` `fs_delete` 的客户端动作绑定是否只删附件**：观察位置 = `SOManageDocumentAction` 打开的 FileManager 面板与真实会话点击；`sm_butnregister` 的按钮组归属。→ 在核实前**留在本批范围外**；即使核实为删订单，也须单独核对其入口、权限与映射。

### 3.2 212 审批「确定」的最终提交点与参数载体

- **注册层面**：`[库快照]` `Approve` 的 `btnname=审批`，**没有**"上级按钮"标记（`BTNOWNERTYPE=0`，父节点是功能 `CFUNID` 而非按钮）；68 条注册中无名称含"确定"的按钮。"上级按钮/入口"只是文档口径（`SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md:21`、`UNMATCHED_BUTTONS_20261110.md:85,95`）。`Approve` 之下是否有子页/子按钮注册 = `[未核实]`（观察位置：`sm_butnregister WHERE parent_id='1001Z810000000004TO2'`）。
- **最终提交点** `[静态]`：`approveAction.doAction → AbstractScriptExcAction.doAction → procFlow → PFlowProxy.runAction → PfUtilClient.runAction → actionAboutApproveflow（bytecode 偏移 127）→ checkWorkitemWhenApprove（偏移 172 调用点）→ 偏移 344 IplatFormEntry.processAction("APPROVE", 30/交易类型, worknote, SaleOrderVO, userObj, eParam)`。客户端可插入的最后一个点就是 `PfUtilClient.runAction` 之前（`PFlowProxy.runAction` / 动作 `doAction`）。
- **参数载体** `[静态]` `WorkflownoteVO`：审批意见 `checknote`；决议 `approveresult`（String）+ `approvestatus`（Integer）+ `ischeck`；工作项 `pk_wf_task`；审批流 `pk_checkflow`；审批人 `checkman/checkname`；消息 `messagenote`；指定审批人扩展 `extApprovers`；消息上下文 `msgContext`。业务参数在 `PFlowContext.eParam`（键含 `worknote/silently/nosendmessage/besideapprove/notechecked`），用户对象 `PfUserObject{businessCheckMap,userObject}`。
- **策略口径**：策略与权限用**真实按钮 `Approve` / `1001Z810000000004TO2`**，不虚构「确定」注册；「确定」只是审批窗口的模态返回。

### 3.3 PF 五项链与 `PFlowContext` 清空

`[静态]` 公共链：`AbstractScriptExcAction.doAction`（循环 `fillUpContext/procFlow`，`IResumeException` 重试）→ `procFlow`（单张 `PFlowProxy.runAction`；批量 `MultiBillScriptRunner.runTask`）→ `PFlowProxy.runAction` 校验 `actionName`/`billType` 后调 `PfUtilClient.runAction(parent, actionName+userId, trantype|billType, billVO, userObj, strBeforeUIClass, checkVO, new HashMap(eParam))` → `PfUtilClient` 执行 `executeBusinessPlugin`、`actionAboutApproveflow`、`actionAboutWorkflow` → `IplatFormEntry.processAction`。批量：`eParam.put("worknote", worknote)` 后 `runBatchNew → processBatch`。

- `PFlowContext` 会被清空 → **不能只在 `beforeDoAction` 塞参数**（与计划一致）。
- 服务端 PF 最终实现类 `riart_riartplatformLevel-1.jar!nc/bs/pub/pf/pfframe/PlatFormEntryImpl.class` 已定位但方法体未展开 → `[未核实]`：五个 PF action 的服务端 action driver 与业务规则。

### 3.4 冻结 / 解冻 / 整单关闭原生签名（`[静态]` 双向）

`pubso_salesorder.jar!nc/itf/so/m30/closemanage/ISaleOrderCloseManageMaintain.class`（JAR SHA-256 `B3FCAC67…0B89`）：

```java
SaleOrderVO[] closeSaleOrder(SaleOrderVO[]) throws BusinessException;
SaleOrderVO[] freezeSaleOrder(SaleOrderVO[]) throws BusinessException;
SaleOrderVO[] unFreezeSaleOrder(SaleOrderVO[], Map<String,Boolean>) throws BusinessException;
```

实现 `so_salesorder.jar!nc/impl/so/m30/closemanage/SaleOrderCloseManageMaintainImpl.class`：`setState` 用 `BillTransferTool.getOriginBills()` 丢弃客户端 VO、以库内原单为准 → `SaleOrderStateMachine.setState` → 按 `csaleorderbid` 回查 View。`updateCloseOpenReason` 以 `VOUpdate.update(children, ["vclosereason"])` 持久化关闭原因（即 `so_sale_order_b.vclosereason`）。

### 3.5 「保存并提交」复合动作

同一个 `sendApproveAction` bean 被 `saveandsendApproveAction` 复用：`Save/WRITE` 保存成功并返回真实单据后才进入 `Commit/SAVE`，两者按各自按钮策略分别授权一次、**不复用保存授权**；提交取消或失败时已成功的保存结果及其审计保留，界面能说明订单仍未提交；保存取消/失败/结果未知时不得触发提交。注意本机 `SaleOrderSaveAndSendAction` 的复合判断只检查退出编辑状态（计划第 36 行）→ 需防误提交旧选中订单。

### 3.6 插件 / PF 版本 / 外发

| 项 | 结论 | 证据 |
| --- | --- | --- |
| `PfUtilClient` 版本闸门 | **一致**：HOME `modules\riart\client\lib\uiriart_riartplatformLevel-1.jar!nc/ui/pub/pf/PfUtilClient.class`（49119B）现算 SHA-256 `1448DB24…6689` = 试点常量 `SUPPORTED_PF_CLIENT`；全量 3711 JAR 索引确认仅此一份 | `[静态]` |
| `saleorder_esign_probe.xml`（V1 `PluginBeanConfigFilePath`） | 注册存在，但**目标文件在本机 HOME 及其 JAR 中 0 命中 → 本机悬空/不生效**；远程客户端缓存或其他加载路径仍 `[未核实]`。**不要覆盖或删除旧版配置来排障** | `[静态]` + `SALE_ORDER_SAVE_PILOT.md:13` |
| `…_Industry0_tempsave` | 目标文件存在（`uiso_salesorder.jar!…saleorder_tempsave_config.xml`，3502B），仅暂存扩展，不拦截 8 项；**覆盖层换 BeanConfigFilePath 时不能丢该参数** | `[静态]` |
| 审批后置 / OA 外发 | 2026-11-07 `pub_pluginitem` 快照 so/m30 只有 1 行（Delete after → `nc.pubimpl.so.m30.pms.rule.M30_Del2PmsRule`，opcnc/PMS，**非审批、非 OA**）；无 `%ActionPlugInPoint.Approve%` 行。平台层存在致远 OA（`nchome\lib\seeyon-ctp-core.jar`），审批通知主体是 PF/工作流（`WorkflownoteVO.msgContext`），不在销售 bean | `[库快照]`；`[未核实]` 现场是否新增审批后置/APS/独立事务 |
| `M30_Del2PmsRule` 实现类 | 在本机 3711 JAR 中未找到，`opcnc` 模块无 `pms` 条目 → 该注册在本机可能无法加载 | `[未核实]` |

---

## 4. 当前 990 保存基线（第 208 行试点）

### 4.1 冻结坐标（本次取证最关键结论）

| 项 | 值 |
| --- | --- |
| 当前目标 HOME 快照计划 | `build\deployment\20261111-155019-9df7af95\plan.json` |
| release id | `display-catalog-only-20261111-154957-0763568d`，`created_at_utc=2026-11-11T07:50:20Z` |
| 核对结果 | 计划内 **21 个文件 21/21 与当前 HOME 逐字节一致**（match=21, mismatch=0） |
| 早期证据的关系 | 10:08:53 销售补丁（`deployment\20261111-100853-135cd831`）的 21 项中已有 **13 项被后续覆盖**；14:07 空收款热修只是其中一环 → **都不是当前整体快照**，不能当当前 HOME 的完整基线 |
| 注意 | `sales-file-only-journal.json` 本身**不含任何哈希**，只有 applied/restored 状态；哈希证据在同目录 `plan.json`。不要把 journal 当哈希证据 |

### 4.2 保存实现文件：主线 ↔ 990 兼容包（11 对）

| 文件 | 一致性 | SHA-256 |
| --- | --- | --- |
| `QdSaleOrderConfig.xml` | 一致 | `998AFEBA…` |
| `QdSaleOrderSaveAction.java` | 一致 | `63020D6F…` |
| `SaleOrderSaveHandoff.java` | 一致 | `86283A14…` |
| `SaleOrderPfPreparation.java` | 一致 | `53AF0687…` |
| `SaleOrderOperationAdapter.java` | 一致 | `8C13BB9F…` |
| `SaleOrderPfSaveAdapter.java` | 一致 | `3008DE55…` |
| `SaleOrderPilotScope.java` | 一致 | `6BDFE012…` |
| `ISaleOrderPfSaveAdapter.java` | 一致 | `DC111F2A…` |
| `SaleOrderPfSaveCommand.java` | 一致 | `93060A24…` |
| `SaleOrderPfSaveResult.java` | 一致 | `FE3851BB…` |
| `SaleOrderPilotPolicyService.java` | **只在 990 包**（主线不存在） | `06E6A87A…`（3752B） |

- `SaleOrderPilotPolicyService` 由 `sales-order-990-release\package.ps1:40` 打进 overlay、`:90-97` 在打包时把 upm 的 `PolicyServiceImpl` 替换成它（派生，不入主线源码树）。
- 反向非对称：`so\src\public\nc\vo\so\qd\esign\SaleOrderCommand.java` **只在主线**，990 包靠 HOME 已装 `qd_esign_so_public.jar` 里的 class 编译 → **改它会打破 990 包"其他条目不变"假设**。

### 4.3 空收款容器热修的证据链（`publish.json`）

`[静态]` `build\sales-990-balance-fix\stage-20261111-140357-5be4f8b7\publish.json` 四段一致：`new_sha256=7D0BEC07A9EC45B2D25B597765BEF62C678437E9FFBEDBB7AE641F4529108390`（8391B）= 当前 HOME `modules\so\META-INF\lib\qd_esign_so_private.jar`；`before.jar=A29957E7…`=`old_sha256`=990 包 staged 版本；JAR 内 `SaleOrderPfSaveAdapter.class=1E130A22…`=`class_sha256`。
→ 热修**本身**的"当前 HOME 一致性"成立；但见缺口 A/B。

### 4.4 三个基线缺口（S1 开工前必须处置或明确绕行）

| 缺口 | 事实 | 影响 | 观察位置 |
| --- | --- | --- | --- |
| A. `build-record.json` 过期 | `build\sales-990-isolated\build-record.json` 的 `SaleOrderPfSaveAdapter.class` 是 8555B/`DC6A6A84…`，常量池含仓库已删字符串、**无 `isEmptyBalance`**；当前源码 `3008DE55…`、HOME 实际 10021B/`1E130A22…` | `build.ps1 -Verify`（第 55–58 行）必然抛 `Isolated build inputs no longer match the tested record` → **改 990 包前必须重建构建证据** | 禁止运行会写项目 build 的脚本；需在隔离副本或授权写目录中执行 |
| B. 热修无构建记录 | 热修自称 "26 assertions"，但仓库测试只有 `$1…$14`（10230B），`build\sales-990-balance-fix\classes` 有 `$1…$24`（12277B）；无源码、无 build-record | 该版本不能作为"已测试"候选 | `build\sales-990-balance-fix\` |
| C. HOME 少一个 upm component | HOME `modules\so\META-INF\qd_esign_saleorder.upm` 665B（1 个 component，仅 `ISaleOrderOperationAdapter`）；主线源码 976B（2 个 component，含 `ISaleOrderPfSaveAdapter`）→ **未部署**。而 `sale-order-save-pilot/configure.sql:9` 把 `adapter_class` 写成 `ISaleOrderPfSaveAdapter`，网关 `OperationTransactionServiceImpl:277` 用 `NCLocator.lookup` 取它 | 若 DB 确为该接口 → 保存失败；今天能跑通是因为 `SaleOrderOperationAdapter:45` 内部 `new SaleOrderPfSaveAdapter()` 绕过 NCLocator | 先查 `qd_es_node.adapter_class`（只读）；再二选一：补部署该 upm，或映射统一改走 `ISaleOrderOperationAdapter` |

### 4.5 HOME 当前实际状态（只读实测）

| 对象 | 状态 |
| --- | --- |
| `modules\uapbd\META-INF\qd_esign_core.upm` | 2013B / `0D3C4018…`；第 **27 行** `<implementation>nc.impl.uapbd.qd.esign.SaleOrderPilotPolicyService</implementation>`（已部署包装器）；主线源码 2003B / `A205A9DB…` 仍指 `PolicyServiceImpl` |
| `modules\so\META-INF\qd_esign_saleorder.upm` | 665B / `E195AC9C…`，**单组件**（见缺口 C） |
| `modules\so\META-INF\lib\qd_esign_so_private.jar` | `7D0BEC07…`（8391B） |
| `modules\so\lib\qd_esign_so_public.jar` | `70528FC6…`（3174B） |
| `modules\uapbd\client\lib\qd_esign_uapbd_client.jar` | `78D724A8…`（191052B，含 `QdSaleOrderConfig.xml`） |
| `modules\uapbd\lib\qd_esign_uapbd_public.jar` | `0A87651F…` |
| `modules\uapbd\META-INF\lib\qd_esign_uapbd_private.jar` | `7745B793…` |
| 销售订单客户端 JAR（**实际位置**） | `modules\so\client\lib\uiso_salesorder.jar`（904074B / `359F4DD2…`，418 条目）；`so\lib` 下没有 `u*so*`；另有 `modules\so\META-INF\lib\so_salesorder.jar`（758011B / `4A61AFAE…`）与 `modules\so\lib\pubso_salesorder.jar`（333127B / `B3FCAC67…`） |
| 试点 10 个类在 HOME 各 JAR 中 | 实测均存在；9 个类哈希与 build-record MATCH，1 个 DIFF（即热修 `SaleOrderPfSaveAdapter`） |

### 4.6 990 保存试点 SQL 要点（只读解析，**未执行**）

- `sql\nc65-electronic-signature\sale-order-save-pilot\configure.sql` 有 **3 条 UPDATE**（`qd_es_node` ×2、`qd_es_request` ×1），第 13 行 `SAVEPOINT`、第 70–71 行 `ROLLBACK TO`，**无 COMMIT**（事务性；需在支持事务的会话中人工提交）。
- `:pilot_mode=DISABLE` 即回退，但只置 `validated='N'`，**不还原 `mapping_state`** → 完整回退需独立脚本。
- `verify.sql` 纯只读。
- 与 `SaleOrderPilotScope.SCOPE_ID=QD-ES-EAF88AC5CAF75442`、`operation-scope.csv` 第 208 行三处一致。
- **本批 8 项 = 第 209–216 行**，绝不能替换 208 保存映射、插件参数或总控。

### 4.7 网关 / 事务 / 审计对 8 项的支撑面

| 层 | 现状 | 8 项可用性 |
| --- | --- | --- |
| `OperationTransactionServiceImpl` ADAPTERS 白名单（28–51 行，25 项） | 销售仅 2 项：`ISaleOrderOperationAdapter`、`ISaleOrderPfSaveAdapter` | 若复用现有接口 → **不用改白名单**；若新增接口 → 必须加白名单 + `qd_esign_saleorder.upm` |
| `OperationServiceImpl` 入口路由 | 远程入口，NONE 事务，转本地 `IOperationTransactionService` | 不用改 |
| `MappingRules.requireDomain`（13–16 行 + 64 行通用分支） | 只要 operation_types 登记 `UPDATE`/`DELETE`、object_type=`SO_SALE_ORDER` 即放行；销售无专项分支 | **不用改** |
| `OrderPhysicalSnapshots.forAdapter`（24–31 行） | 已识别两个销售适配器，支持 INSERT/UPDATE/DELETE；`after()`（48–60 行）删除后有"表里还能读到就报错"反向断言；`aggregate()`（62–79 行）校验 `pk_group/pk_org` | 删除/更新快照能力**已就绪**；缺口 = 删除不采关联明细、审批不采工作项/状态证据（G.5#26–28） |
| 网关审计完整性断言（334–343 行） | INSERT 免 BEFORE、DELETE 免 AFTER | 不用改 |
| 执行期策略判定 | `OperationTransactionServiceImpl:269–270` **静态调用** `PolicyServiceImpl`，不经 `.upm` | 若按按钮细分执行期策略，改点在 `PolicyServiceImpl`；包装器 `SaleOrderPilotPolicyService` 只管"保存配置"白名单 |
| 真正阻碍 | `SaleOrderPfSaveAdapter:30–32` 硬限 Save+INSERT；`SaleOrderOperationAdapter.isPilotSave` 只认 Save，非 INSERT 走 `ISaleOrderScriptMaintain`（**不是 PF**）；`SaleOrderPfPreparation:67` 硬限 `"WRITE"` | 需按 §6 改造 |

---

## 5. 拟改文件清单（主线 / 990 兼容包双列）

> 标"建议名"的类**当前不存在**，需设计评审定名后落地；命名族依据是既存 `QdSaleOrderSaveAction extends SaleOrderSaveAction`（`QdSaleOrderSaveAction.java:32`）。**§4.2 中一致的文件改一侧必须同步另一侧**，否则 990 包 `-Verify` 输入冻结与 overlay 清单漂移。共 44 项，分 7 组。

### 5.1 客户端覆盖层（→ `modules\uapbd\client\lib\qd_esign_uapbd_client.jar`）

| # | 文件 | 990 副本 | 动作 | 依据 |
| ---: | --- | --- | --- | --- |
| 1 | `uapbd\src\client\nc\ui\uapbd\qd\esign\QdSaleOrderConfig.xml` | 有 | **改** | 现仅覆盖 `saveAction`（6–12 行）。需为原生 9 个 bean 加覆盖（保存 1 + 其他 8）：`deleteAction`(原生 `:800`)、`sendApproveAction`(`:856`)、`saveandsendApproveAction`(`:869`，复合)、`unsendApproveAction`(`:881`)、`approveAction`(`:897`)、`unApproveAction`(`:913`)、`freezeAction`(`:1016`)、`unFreezeAction`(`:1020`)、`closeAction`(`:1024`)；对应类全部实测在 `uiso_salesorder.jar` |
| 2–9 | `QdSaleOrderDeleteAction` / `QdSaleOrderSendApproveAction` / `QdSaleOrderUnSendApproveAction` / `QdSaleOrderApproveAction` / `QdSaleOrderUnApproveAction` / `QdSaleOrderFreezeAction` / `QdSaleOrderUnFreezeAction` / `QdSaleOrderCloseAction`（**均建议名**） | 同名副本 | **新增** | 各自 extends 原生类；保留 action code、`actionName`、`billType`、`validationService`、确认/弹窗/刷新 |
| 10 | `SaleOrderSaveHandoff.java` | 有 | **改** | 现 `supports()`（13–21 行）硬限 `40060301/Save/INSERT` 且单对象 → 按按钮泛化，或每动作独立 handoff |
| 11 | `SaleOrderPfPreparation.java` | 有 | **改/按动作新增** | `SUPPORTED_PF_CLIENT`（25 行）+ `prepare()` 硬限 `"WRITE"`（67 行）、83–87 行显式拒绝 approve/start/signal → `SAVE/UNSAVE/APPROVE/UNAPPROVE` 需逐动作桥接与版本校验 |
| 12 | `ElectronicSignatureActionInterceptor` / `SignatureLifecycleInstaller` / `SignatureLifecycleInterceptor` / `OperationContextProvider` / `AuthorizedRequestConsumer` | 990 包未含（靠 HOME 已装 JAR） | **改（小幅复用）** | 复用签名窗口与请求交接契约；若改动必须进 990 overlay 清单（`package.ps1:38-47`） |

### 5.2 公共链路（`public`）

| # | 文件 | 990 副本 | 动作 | 依据 |
| ---: | --- | --- | --- | --- |
| 13 | `uapbd\src\public\nc\vo\uapbd\qd\esign\SaleOrderPilotScope.java` | 有 | **改** | 现只有 GROUP/ORG/SCOPE_ID + `supports(group,org)`，唯一维度是组织，不足以区分 208 与 209–216 → 需按按钮/动作白名单收窄 |
| 14 | `so\src\public\nc\vo\so\qd\esign\SaleOrderCommand.java` | **无副本**（靠 HOME 已装 JAR） | **改/增 DTO** | 冻结/解冻/整单关闭需要新的类型化 command；新增 DTO 进 990 包必须扩 `package.ps1:43-44` |
| 15 | `ISaleOrderOperationAdapter.java` | 靠已装 JAR | 不改（建议） | 仅 extends `BusinessOperationAdapter`，无方法集 |
| 16 | `ISaleOrderPfSaveAdapter.java` | 有 | 不改 / 或新增独立接口 | 新增独立接口会连带改 ADAPTERS 白名单 |
| 17 | `SaleOrderPfSaveCommand/Result.java` | 有 | 仅保存路径变化时改 | 保存回归须保持 |
| 18 | `uapbd\src\public\nc\vo\uapbd\qd\esign\OperationContext.java` | — | **改（可能）** | 若需携带流程上下文（工作项、决议、关闭原因）需扩字段；本项 `[未核实]`（未读完整字段集） |

### 5.3 销售服务适配器（`so` `private`）

| # | 文件 | 990 副本 | 动作 | 依据 |
| ---: | --- | --- | --- | --- |
| 19 | `SaleOrderOperationAdapter.java` | 有 | **改** | `isPilotSave()`（46–48）只认 Save；非 INSERT 走 `ISaleOrderScriptMaintain.saleOrderDelete/Update`（31–34 行）**不是 PF**；`validate` 21 行 INSERT 限单笔 → 按 8 按钮加固定分支，流程类改走 PF |
| 20 | `SaleOrderPfSaveAdapter.java` | 有 | **改** | `validate` 30–32 行硬限 Save+INSERT；46 行 `status==2`；48–50 行只允许 `30…` 交易类型。**改它前必须先解决缺口 A（无有效构建记录）** |
| 21 | `SaleOrderProcessAdapter.java`（**建议名**，提交/收回/审批/取消审批 PF 分支） | 同名副本 | **新增** | 或直接扩 #19；新增接口须改白名单 + upm |
| 22 | `SaleOrderStateAdapter.java`（**建议名**，冻结/解冻/整单关闭的 `ISaleOrderCloseManageMaintain` 分支） | 同名副本 | **新增** | 与 PF 路径不同源 |

### 5.4 策略服务

| # | 文件 | 990 副本 | 动作 | 依据 |
| ---: | --- | --- | --- | --- |
| 23 | `sales-order-990-release\uapbd\src\private\...\SaleOrderPilotPolicyService.java` | **990 包唯一副本（主线无）** | **改** | `save()` 第 50 行只白名单 `40060301+Save`；`checkChanges` 第 58 行只认一个 `SCOPE_ID` → 扩为「208–216 共 9 个 scope × 按钮」白名单 |
| 24 | `PolicyServiceImpl.java` | 无副本 | 核对（尽量不改） | 执行期策略判定在此（静态调用，不经 upm） |
| 25 | `uapbd\META-INF\qd_esign_core.upm` | 990 包由 `package.ps1:90-97` 派生 | **改（主线）/ 改派生逻辑（990）** | 主线仍指 `PolicyServiceImpl`、HOME 已指 `SaleOrderPilotPolicyService`；`package.ps1:90-97` 在 `count≠1` 时 throw → **当前 HOME 下必然失败**，须改为"已正确绑定则保留 UPM，未知状态中止" |

### 5.5 审计快照

| # | 文件 | 990 副本 | 动作 | 依据 |
| ---: | --- | --- | --- | --- |
| 26 | `OrderPhysicalSnapshots.java` | 靠已装 JAR | **改** | `SALE` 只读 `so_saleorder`+`so_saleorder_b`；删除补"受影响的关联明细"、审批补工作项/状态证据（`forAdapter` 24–31 行已支持 UPDATE/DELETE，不用改） |
| 27 | `PhysicalSnapshotReader.java` | 靠已装 JAR | 改（按需） | 新增关联明细查询能力 |
| 28 | `AuditRecorder.java` / `SnapshotFieldLabels.java` | 靠已装 JAR | 改（按需） | 新按钮证据字段标签/分组 |
| 29 | `so\META-INF\qd_esign_saleorder.upm` | 990 无副本 | **改（主线）→ 需部署** | 补齐缺口 C 或改映射走 `ISaleOrderOperationAdapter` |

### 5.6 配置与脚本

| # | 文件 | 990 副本 | 动作 | 依据 |
| ---: | --- | --- | --- | --- |
| 30 | `sql\…\sale-order-other-buttons\preflight.sql`（**建议名**） | 新增副本 | **新增** | 只读预检：版本、990 业务单元/销售组织、8 组 pk_btn、`qd_es_node` 旧行、`qd_es_policy`、无 `RUNNING` 请求 |
| 31 | `…\configure.sql`（**建议名**） | 新增副本 | **新增** | SAVEPOINT + 不自提交 + `%ROWCOUNT=1` 断言，逐按钮 9 条映射；**不替换 208 保存映射** |
| 32 | `…\rollback.sql`（**建议名**） | 新增副本 | **新增** | DISABLE 不还原 `mapping_state`，需独立恢复 |
| 33 | `config\qd-esign\operation-scope.csv` | 无副本 | **改** | 209–216 的 `mappingStatus` 升级时更新，填 `ncFunctionCode/ncActionCode` |
| 34 | `sales-order-990-release\package.ps1` | 唯一打包器 | **改 3 处** | 38–47 行 overlay 清单、90–97 行 upm 派生逻辑、106–108 行 SQL 复制清单 |
| 35 | `sales-order-990-release\build.ps1` | — | **改** | 113–118 行测试类数组；8–12 行基线 JAR 清单（若新增接口 JAR） |
| 36 | `tools\build.ps1` | — | **不改** | 无 SaleOrder 硬编码，靠 `AllTests` |
| 37 | 四个开发文档（DEVELOPMENT_PLAN/TASK_TRACKER/TEST_REPORT/UNRESOLVED_ISSUES） | 无副本 | **改** | S0 证据落账 |

### 5.7 测试

| # | 文件 | 990 副本 | 动作 | 依据 |
| ---: | --- | --- | --- | --- |
| 38 | `tests\java\nc\test\qd\esign\AllTests.java` | 无（990 不用 AllTests） | **改** | 注册新测试（现有注册位 21/118 行） |
| 39 | `SaleOrderPfSaveAdapterTests.java` | 有（两处哈希相同 `67CAD28A…`） | **改** | 改一侧必须同步 |
| 40 | `SaleOrderPilotConfigTests.java` | 有（`994FA313…`） | **改** | 新增 bean 覆盖需扩 Spring XML 断言 |
| 41 | `SaleOrderPilotPolicyServiceTests.java` | **仅 990 包有** | **改（990）/ 新增（主线）** | 扩白名单必须扩此测试 |
| 42 | `SaleOrderOtherButtonsTests.java`（**建议名**） | 同名副本 | **新增** | 动作白名单、DELETE/UPDATE 归类、取消与未知结果、原生调用次数断言（S1 通过条件） |
| 43 | `OrderPhysicalSnapshotsTests.java`（**建议名**） | 同名副本 | **新增** | DELETE 反向断言、UPDATE BEFORE/AFTER、删除关联明细 |
| 44 | `SaleOrderOperationAdapterTests.java`（**建议名**） | 同名副本 | **新增** | 现无 so 私有适配器单测 |

### 5.8 打包器必改点（否则打包失败/产物错误）

1. `package.ps1:38-47` overlay 清单：新增客户端类、新 upm、新 public DTO 必须列入。
2. `package.ps1:90-97` upm 派生假设：当前 HOME 已是 `SaleOrderPilotPolicyService`，脚本要求 finding `PolicyServiceImpl` 恰好 1 次 → **必然 throw**；须改为"已正确绑定则保留、未知状态中止"。
3. `package.ps1:106-108` SQL 复制清单：新增 8 项脚本路径。

---

## 6. 实现方案落点（S1–S4 设计要点，供本批编码依据）

1. **客户端覆盖层**：复制 `saleorder_config.xml` 结构到 `QdSaleOrderConfig.xml`，**bean id 与 action code 不变**，只替换 class 为 `QdSaleOrder*Action`。每个新动作类：保留原生选择/确认/弹窗/进度/刷新；在即将发起真实写入前复用 `ElectronicSignatureActionInterceptor` 的签名窗口与一次性请求交接；签名取消即 `return`（不写），通过才 `super.doAction(...)`（PF 五项）或调用原服务方法（冻结/解冻/关闭三项）。签名动作与授权对象在窗口关闭后重新核对。
2. **服务端固定分支**：为已核实的 节点+按钮+对象类型+操作类型 定义固定 DTO 与分支，服务端决定原生方法；客户端不传可执行类名/SQL/脚本名。归类：209=`DELETE`，210–216=`UPDATE`，日志用真实按钮编码/名称区分业务动作，复用现有快照分支。
3. **PF 动作**：删除/提交/收回/审批/取消审批保留完整 PF 路径（原交易类型、前置提示/校验、`PfUserObject`、流程参数、真正写入 RPC、结果处理、后置插件）。`PFlowContext` 会清空 → 不能只塞 `beforeDoAction`；优先核实可注入的最终调用点；版本限定桥接必须逐动作验证客户端契约、类来源及哈希，未识别版本拒绝受控执行。**不得直接用 `saleOrderDelete` 或审批底层接口代替未证实等价的 PF。**
4. **冻结/解冻/关闭**：客户端保留原生选择、返回 VO 合并（`ClientBillCombinServer.combine` + `directlyUpdate`）、ATP 继续/取消（第二次调用才带 `ATPCheck=false`）、关闭原因输入与整单行补齐；服务端用固定 DTO 调 `freezeSaleOrder` / `unFreezeSaleOrder(vos, map)` / `M30CloseBillCloseService.operateBill`，**关闭原因与签名事由分开传**，`vclosereason` 由原生 `updateCloseOpenReason` 落库。
5. **策略**：扩 `SaleOrderPilotPolicyService` 白名单（208–216 共 9 个 scope × 按钮），同时在销售服务端再次限制集团/组织/动作；保留物料归属策略、日志/认证与已装网关。执行期策略在 `PolicyServiceImpl` 侧另核。
6. **审计**：复用 `OrderPhysicalSnapshots` + 审计链；删除补关联明细、审批补工作项/状态证据；用服务端实际落库值按稳定主键比较；业务、成功审计、请求终态同一业务事务；原生失败或审计写失败不留成功审计；重复提交只查已知结果，超时/回包丢失先查请求及原单状态。
7. **保存并提交**：`Save/WRITE` 成功后进入 `Commit/SAVE`，各自按钮策略、各自授权一次；提交取消/失败时保存结果与审计保留且界面说明未提交；保存取消/失败/未知时不得触发提交；测试原生复合判断只检查退出编辑状态的误提交风险。
8. **多选**：单笔联调只用隔离样本并明确拒绝未适配的受控多选；启用按钮策略后多选不能绕回原生写入；共享环境需单笔+多选时先过 S5 再开该按钮。

---

## 7. 未核实清单（合并三份证据，去重；每条给观察位置）

| # | 未核实项 | 影响 | 观察位置 |
| ---: | --- | --- | --- |
| 1 | **数据库实际状态**：`qd_es_node` 是否已登记 208 映射（`mapping_state/validated/adapter_class`）、`qd_es_policy` 是否有启用的销售保存策略、`qd_es_org_control` 总控、`sm_paramregister.BeanConfigFilePath` 是否已切到 `QdSaleOrderConfig.xml` | 决定 S1 起点与缺口 C 是否致命；`SALE_ORDER_SAVE_PILOT.md:5`（11-10）"UNMAPPED/N"早于 11-11 部署，不能当当前状态 | 只读会话执行 `verify.sql` 等价 SELECT；`configure.sql:75-79` 回显 |
| 2 | 8 个按钮的 `pagecode`/`pk_page`；本节点是否有页签级按钮 | 无法填页签列；显示/权限入口可能不止 68 条 | 三表 JOIN，funcode 换 `('40060301','40060303')` |
| 3 | `fs_delete` 的客户端动作绑定（只删附件？） | 决定 209 范围归属 | `SOManageDocumentAction` 打开的 FileManager；真实会话点击 |
| 4 | `Approve` 之下是否有子页/子按钮注册；「确定」后的参数落库值（`checknote/approveresult/pk_wf_task`） | 212 只能算"定位到入口"，不能算最终确认 | `sm_butnregister WHERE parent_id='…TO2'`；真实会话抓 `processAction` 入参 / `wf_*` 表 |
| 5 | 8 枚按钮的按钮级 `sm_paramregister` | 权限/动作参数白名单无法定稿 | `sm_paramregister WHERE parentid IN (8 个 pk_btn)` |
| 6 | 角色/用户按钮授权与组织范围（990 之外可见性） | 不能验证"有权限/无权限"分支 | 功能权限/角色分配表；真实会话 |
| 7 | 真实 NC 会话加载的类、按钮显隐、原生调用链 | 0 条真实会话证据；所有"静态核实"不能升级 | 客户端 `BeanConfigFilePath` 生效后看实际动作类；`nclogs` 组件加载日志；逐按钮点击留证 |
| 8 | 五个 PF 动作的服务端最终实现（`PlatFormEntryImpl` 内 action driver 与业务规则） | 210/211/212/213 的服务端等价性未证 | `riart_riartplatformLevel-1.jar!nc/bs/pub/pf/pfframe/PlatFormEntryImpl.class`（javap -c）；真实会话 PF 日志 |
| 9 | `build-record.json` 的 `-Verify` 实际失败信息 | 禁止运行会写项目 build 的脚本；目前为常量池层面确定性推断 | 在授权写的隔离副本上跑 `build.ps1 -Verify` |
| 10 | 热修版 `SaleOrderPfSaveAdapter` 是否有通过的构建记录 | 该版本不能标"已测试" | `build\sales-990-balance-fix\` |
| 11 | `saleorder_esign_probe.xml` 是否被远程客户端缓存/其他加载路径使用 | V1 兼容性不能断言"不生效" | 现场客户端缓存目录与资源解析日志 |
| 12 | 现场是否新增审批后置/外发插件（OA/APS）或独立事务 | 无法证明不漏发/不重复/回滚后不误发 → 对应入口保持关闭 | `pub_pluginitem`（`vextendpointname like '%ActionPlugInPoint.Approve%'`、after）；`billtype2` 业务插件配置；工作流消息表/OA 接口日志 |
| 13 | `M30_Del2PmsRule` 实现类缺失的运行期行为 | 删除后 PMS 外发可能异常 | 服务端插件加载日志；opcnc 服务端 JAR 清单 |
| 14 | 8 项按钮是否支持多选（批量） | S5 依赖；启用策略后多选不能绕回原生 | 原生 `actionsOfList/actionsOfCard`（`:1464/1491`）绑定；实测 UI |
| 15 | `OperationContext` 是否需扩流程上下文字段 | 影响 DTO 设计 | `uapbd\src\public\nc\vo\uapbd\qd\esign\OperationContext.java` |
| 16 | `ElectronicSignatureActionInterceptor` 对非保存动作的复用度 | 影响客户端改造量 | 该类全文（HOME `qd_esign_uapbd_client.jar` 内 13077B） |
| 17 | 两个节点 "上一级/上级按钮" 口径 | 仅文档口径，非库字段；避免按错误口径设计 | `sm_butnregister` 无父按钮层级；如需层级查 `parent_id` |

---

## 8. 下一步（批次映射与本轮边界）

| 批次 | 状态 | 本轮结论 |
| --- | --- | --- |
| S0 冻结基线与取证 | **本轮交付** | 证据表 + 990 基线 + 拟改清单 + 未核实清单（本文件） |
| S1 单笔公共链路 | 待写码 | 动作白名单、组织策略门、类型化命令、一次性授权交接、结果校验与共用审计路线；保留保存分支；补配置加载与四种开关组合断言。**前置**：缺口 A/C 处置；项目写权限 |
| S2 直接服务（冻结→解冻→整单关闭） | 待写码 | 先冻结验链路，再 ATP 继续/取消、整单行、关闭原因；未能保留原生警告/恢复协议的分支不启用 |
| S3 PF 基础动作（删除/提交/收回） | 待写码 | 列表/卡片、原生确认与权限；覆盖「保存并提交」全部开关组合；每次确认只执行对应 PF 写入 |
| S4 审批动作（审批确定/取消审批） | 待写码 | 真实任务/签名/审计一致；开窗/取消不记成功；审批通知不漏发不重复 |
| S5 批量与现场分支 | 待写码 | 多选语义、逐对象事务与回执；无可靠逐对象结果时保持批量受控路径关闭 |
| S6 候选发布与回退演练 | 待写码 | 隔离构建、逐条目哈希、映射/撤回脚本草案；只允许通过实际 NC 验证的入口在目标组织启用 |

**本轮未执行（按授权边界）**：任何部署、注册/数据库写入、启停共享 NC、实际外发、会写项目 `build` 目录的构建。候选编译与测试需在能写项目（或隔离副本）的条件下执行，并以真实命令/退出码回填 TEST_REPORT。
