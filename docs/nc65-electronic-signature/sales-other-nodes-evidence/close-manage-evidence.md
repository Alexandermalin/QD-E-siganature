# 销售订单关闭管理 `40060303` 第 217–228 行逐按钮原生证据

- 批次：其他节点原生取证（关闭管理 12 项）
- 取证日期：2026-11-12
- 项目根（只读）：`E:\02-Project\QiDu\QD-E-Siganature`
- NC65 开发 HOME（只读）：`E:\01-Home\NC65home\QuDuTest\nchome`
- 取证性质：**离线只读取证**。全部结论来自 JAR 字节码（`javap -p -c`）、JAR 内 XML 原文与项目已存的现场注册快照（`config/qd-esign/site-20261107/*.csv`）。
- **未做**：未部署、未运行构建、未启动/停止 NC、未连接数据库、未执行任何 SQL、未修改任何既有文件。**真实 NC 会话核实条数 = 0**。
- 本文件所有「静态核实」只表示"从字节码/XML 原文读到"，**不等于联调通过**；推断一律显式标注「推断」。

---

## 1. 基本信息

| 项 | 值 | 证据出处 |
| --- | --- | --- |
| 功能节点 | `40060303` 销售订单关闭管理 | `node-bindings.csv:L40` |
| 节点内部 ID | `QD-NODE-2EB54F3AAEBD27DB` | `node-bindings.csv:L40` |
| 注册功能主键 | `registeredFunctionPk=1001Z810000000001CMN` | `node-bindings.csv:L40`（列 `registeredFunctionPk`） |
| 组织类型 | `orgTypeCode=SALEORGTYPE000000000` | `node-bindings.csv:L40` |
| 模块 / 父节点 | `module=4006`；`parentPk=1001Z810000000000YQG` | `node-bindings.csv:L40` |
| 节点注册态 | `NODE_REGISTRATION_CONFIRMED_RPC_PENDING`、`validated=N` | `node-bindings.csv:L40` |
| 按钮注册总数 | `registeredButtonCount=18`（本批 12 项之外还有查询/刷新/联查/打印组等，其余 6 项的归属未逐一核对） | `node-bindings.csv:L40`；`m30close_config.xml:65-84` |
| 客户端配置（bean 装配） | `BeanConfigFilePath=nc/ui/so/m30/closemanage/model/m30close_config.xml` | `node-bindings.csv:L40`（列 `beanConfig`） |
| 客户端配置实体 | `build/native-configs-other-nodes/m30close_config.xml`，16386 B，SHA-256 `191BDFA1EB23973B2478AC1067594BBDF0EF46F23A4519530EEB507F793F536D` | 取自 `uiso_salesorder.jar!nc/ui/so/m30/closemanage/model/m30close_config.xml` |
| 客户端 JAR | `modules\so\client\lib\uiso_salesorder.jar`，904074 B，SHA-256 `359F4DD209F48B63966C1D9853EAA9FA3ABD6D97F785197CC0520718E5150D07` | 本机实测 |
| 服务接口 JAR | `modules\so\lib\pubso_salesorder.jar`，333127 B，SHA-256 `B3FCAC679519B48C485F34CC3F8AA6F1653AE8EF95C4FFAC936DF13DBAAD0B89` | 本机实测 |
| 服务实现 JAR | `modules\so\META-INF\lib\so_salesorder.jar`，758011 B，SHA-256 `4A61AFAE9CD8E5E21B00023F9EBBC8FCD260F716596A52DA2F873B7981082544` | 本机实测 |
| 服务接口 | `nc.itf.so.m30.closemanage.ISaleOrderCloseManageMaintain`（15 个方法，含 `querySaleOrderViewVO`；本批 12 项各用其一） | `iface-ISaleOrderCloseManageMaintain.javap.txt` |
| 服务实现 | `nc.impl.so.m30.closemanage.SaleOrderCloseManageMaintainImpl` | `impl-SaleOrderCloseManageMaintainImpl.javap.txt` |
| 客户端服务定位方式 | 全部 12 个 `M30Close*Service` 均 `new SCMResumeExceptionInvoker().getService(ISaleOrderCloseManageMaintain.class)` — 返回 **动态代理**（`Proxy.newProxyInstance`），不是直连 | `infra-SCMResumeExceptionInvoker.javap.txt:getService` |
| 列表控件 | `nc.ui.so.m30.closemanage.view.SaleOrderCloseManageListView`，绑定 `BatchBillTableModel`；配置 `bodyMultiSelectEnable=true`、`isBodyAutoAddLine=false`、`voClassName=nc.vo.so.msaleorder.entity.SaleOrderViewVO` | `m30close_config.xml:47-54` |
| 数据模型管理器 | `nc.ui.so.m30.closemanage.model.CloseManageModelDataManager extends ModelDataManager` | `infra-CloseManageModelDataManager.javap.txt` |
| 查询服务 | `SaleOrderCloseManageService implements IQueryService` → `ISaleOrderCloseManageMaintain.querySaleOrderViewVO(IQueryScheme)` | `infra-SaleOrderCloseManageService.javap.txt` |
| VO 类型 | 表头/整单 `nc.vo.so.m30.entity.SaleOrderVO`（`extends AbstractBill`）；行视图 `nc.vo.so.m30.entity.SaleOrderViewVO`（`extends AbstractDataView`，`getHead()/getBody()/changeToSaleOrderVO()`）；参数容器 `nc.vo.so.pub.SOParameterVO`（`vo/vos/view/views/businessCheckMap`） | `vo-*.javap.txt` |
| 按钮组装配 | 6 个 `MenuAction` 组：`billCloseGroup`(整单关闭) / `rowCloseGroup`(行关闭) / `sendCloseGroup`(发货关闭) / `outCloseGroup`(出库关闭) / `invoiceCloseGroup`(开票关闭) / `settleCloseGroup`(结算关闭)，每组=关闭+打开 2 项 | `m30close_config.xml:105-320` |

### 1.1 按钮编码与 action code 交叉核对（关键一致性）

每个 Action 构造器都调用 `SCMActionInitializer.initializeAction(this, "<code>")`，该 code 即按钮动作编码：

| bean id | Action 类 | 构造器 code | 现场 `btncode` | 一致 |
| --- | --- | --- | --- | --- |
| `billCloseAction` | `M30CloseBillCloseAction` | `BillClose` | `BillClose` | ✅ |
| `billOpenAction` | `M30CloseBillOpenAction` | `BillOpen` | `BillOpen` | ✅ |
| `rowCloseAction` | `M30CloseRowCloseAction` | **`LineClose`** | `LineClose` | ✅（类名 Row、编码 Line，易误判） |
| `rowOpenAction` | `M30CloseRowOpenAction` | **`LineOpen`** | `LineOpen` | ✅ |
| `sendCloseAction` | `M30CloseSendCloseAction` | `SendClose` | `SendClose` | ✅ |
| `sendOpenAction` | `M30CloseSendOpenAction` | `SendOpen` | `SendOpen` | ✅ |
| `outCloseAction` | `M30CloseOutCloseAction` | `OutClose` | `OutClose` | ✅ |
| `outOpenAction` | `M30CloseOutOpenAction` | `OutOpen` | `OutOpen` | ✅ |
| `invoiceCloseAction` | `M30CloseInvoiceCloseAction` | `InvoiceClose` | `InvoiceClose` | ✅ |
| `invoiceOpenAction` | `M30CloseInvoiceOpenAction` | `InvoiceOpen` | `InvoiceOpen` | ✅ |
| `settleCloseAction` | `M30CloseSettleCloseAction` | `SettleClose` | `SettleClose` | ✅ |
| `settleOpenAction` | `M30CloseSettleOpenAction` | `SettleOpen` | `SettleOpen` | ✅ |

→ 12/12 一致，**该节点注册的 12 枚业务按钮就是本表这 12 个 bean**（覆盖层必须保留 bean id 与构造器 code，只换 class）。

---

## 2. 12 项逐行证据表（主表）

> 列说明：**选择粒度** = 从模型取什么样的数据集送服务；**确认/弹窗** 中的 `04006011-xxxx` 是 `4006011_0` 资源号（本机 JAR 内无该资源包，中文原文未核实，见 §4）。
> 全部 12 行共同点（不再逐行重复）：① 进入 `doAction` 第一件事就是 `MessageDialog.showInputDlg(容器, 04006011-0233, 04006011-0234, null, 120)` 采集**关闭/打开原因**，返回 `null`（取消）**立即 return 不写库**；空串则 `ExceptionUtils.wrappBusinessException(04006011-0544)` 阻断；② 原因通过 `SaleOrderBVO.setVclosereason(String)` 逐行写入，服务端 `updateCloseOpenReason` 落库（`so_sale_order_b.vclosereason` 列名未核实，§4）；③ 服务调用统一经 `SCMResumeExceptionInvoker` 代理 → 组合远程调用（先 `IResumeResultService.setResumeResult(Map)`，再业务方法）+ 可重入重试（§3.4）。

| 原表行 | scope_id | 按钮编码 | bean id | 原生动作类 | 选择粒度 | 确认/弹窗 | 最终原生服务方法（完整签名） | 返回类型 | 刷新 / 结果处理 | 批量语义 | 证据等级 |
| ---: | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 217 | `QD-ES-6DA27F33C45CD3B0` | `BillClose` | `billCloseAction` | `M30CloseBillCloseAction`<br>`extends NCAction`<br>`implements IMultiReturnObjProcessor, ProgressActionInterface` | **整单**：先按选中行 `head.getCsaleorderid()` 去重成 Set，再遍历 `model.getRows()` 把**该单全部行**捞回，`billComposite()` 组头+全部行 | 有：原因输入 `04006011-0233/0234`；空原因 `04006011-0544`；多单进度框标题 `04006011-0286` | `SaleOrderVO[] closeSaleOrder(SaleOrderVO[]) throws BusinessException`（经 `M30CloseBillCloseService.operateBill`） | `SaleOrderVO[]` | `processReturnObjs`：`BillToViewConvertor`→`SaleOrderViewVO[]`→`BatchOperateVO.setUpdObjs`→`model.directSave`→`loadLoadRelationItemValue`；末尾**无条件** `refreshAction.doAction(event)` | 已知：>1 张→`MultiBillTaskRunner.runTask()`（逐单一次 RPC）；=1 张→`singleBillService.operateBill` | **静态核实**（客户端+服务端双向） |
| 218 | `QD-ES-4A1493B793E22F6D` | `BillOpen` | `billOpenAction` | `M30CloseBillOpenAction`<br>`extends NCAction`<br>同上两接口 | **整单**（同上，`billComposite` 展开全部行） | 有：原因输入 `0233/0234/0544`；多单标题 `04006011-0377`；**ATP 不足**：捕获 `AtpNotEnoughException`→`showYesNoDlg(workbench.getParent(), 04006011-0244, e.getMessage())` | `SOParameterVO[] openSaleOrder(SaleOrderVO[], boolean)`（`boolean` 由 `M30CloseBillOpenService` 硬编码 `iconst_0`=false） | `SOParameterVO[]` | `processReturnObjs(SOParameterVO[])`：用每个 `paraVO.getVo()` 拆 head+body 生成 ViewVO 行→`directSave`→`loadLoadRelationItemValue`；末尾 `refreshAction.doAction(event)` | >1 个 `paraVO`→`MultiBillTaskRunner`；=1→`operateBill`；**ATP 重试为 `while` 循环**（§3.3） | **静态核实** |
| 219 | `QD-ES-F598A0B4A16EFE84` | `LineClose` | `rowCloseAction` | `M30CloseRowCloseAction`<br>`extends NCAction`<br>同上两接口 | **选中行**：`getSelectedOperaDatas()` 逐个转型为 `SaleOrderViewVO`，不展开整单 | 有：原因输入 `0233/0234/0544`；多行标题 `04006011-0290` | `SaleOrderViewVO[] closeSaleOrderRow(SaleOrderViewVO[]) throws BusinessException` | `SaleOrderViewVO[]` | `processReturnObjs`：`BatchOperateVO.setUpdObjs`（直接用返回的 ViewVO）→`directSave`→`loadLoadRelationItemValue`→**`refreshAction.getDataManager().refresh()`**（刷新点在 processReturnObjs 内，doAction 末尾无 refresh） | 多行→`MultiBillTaskRunner`（逐行一次 RPC）；单行→`operateBill` | **静态核实** |
| 220 | `QD-ES-FF713F12789B45A1` | `LineOpen` | `rowOpenAction` | `M30CloseRowOpenAction`<br>`extends NCAction`<br>同上两接口 | **选中行**（同 219） | 有：原因输入 `0233/0234/0544`；多行标题 `04006011-0383` | `SaleOrderViewVO[] openSaleOrderRow(SaleOrderViewVO[]) throws BusinessException` | `SaleOrderViewVO[]` | `operateBill` 之后**额外回查**：把 `model.getRows()` **全部行**的 `body.getCsaleorderbid()` 收集成 Set → `NCLocator.lookup(ISaleOrderForPub).querySaleOrderViewVOs(ids)` → `processReturnObjs(回查结果)`→`loadLoadRelationItemValue`；末尾 `refreshAction.doAction(event)` | 多行→`MultiBillTaskRunner`；**回查范围=当前列表全部行**（不止选中行，见 §3.5） | **静态核实** |
| 221 | `QD-ES-887831FDC8C23F76` | `SendClose` | `sendCloseAction` | `M30CloseSendCloseAction`<br>`extends NCAction`<br>同上两接口 | **选中行**（发货关闭按行） | 有：原因输入 `0233/0234/0544`；多行标题 `04006011-0291` | `SaleOrderViewVO[] closeSaleOrderSend(SaleOrderVO[])`；service 内先 `SaleOrderViewVO.changeToSaleOrderVO()` 转整单 VO 再传 | `SaleOrderViewVO[]` | `processReturnObjs`（同 219 的 ViewVO 直存）→`directSave`→`loadLoadRelationItemValue`；末尾 `refreshAction.doAction(event)` | 多行→`MultiBillTaskRunner`（每行单独一次 `changeToSaleOrderVO`+RPC） | **静态核实** |
| 222 | `QD-ES-8A7BC4FA7E1631FB` | `SendOpen` | `sendOpenAction` | `M30CloseSendOpenAction`<br>`extends NCAction`<br>同上两接口 | **选中行** | 有：原因输入 `0233/0234/0544`；多行标题 `04006011-0384`；**前置业务校验 `checkBusiSendOpen`**：单据业务类型不含 `SOBillType.Delivery` 时弹 `showErrorDlg(null, 04006011-0010, 04006011-0381+单号列表)` 并**整体放弃**（不发 RPC，仅 refresh） | `SaleOrderViewVO[] openSaleOrderSend(SaleOrderVO[])` | `SaleOrderViewVO[]` | 同 221：`processReturnObjs`→`directSave`→`loadLoadRelationItemValue`（校验不通过则不执行此段）；末尾 `refreshAction.doAction(event)` | 多行→`MultiBillTaskRunner`；**校验是整批否决式（全通过才进批次）** | **静态核实** |
| 223 | `QD-ES-32542641C9DC8398` | `OutClose` | `outCloseAction` | `M30CloseOutCloseAction`<br>`extends NCAction`<br>同上两接口 | **选中行**（出库关闭按行） | 有：原因输入 `0233/0234/0544`；多行标题 `04006011-0288` | `SaleOrderViewVO[] closeSaleOrderOut(SaleOrderVO[])`（service 内 `changeToSaleOrderVO()`） | `SaleOrderViewVO[]` | 同 219；末尾 `refreshAction.doAction(event)` | 多行→`MultiBillTaskRunner` | **静态核实** |
| 224 | `QD-ES-64B839C61D768D19` | `OutOpen` | `outOpenAction` | `M30CloseOutOpenAction`<br>`extends NCAction`<br>同上两接口 | **选中行**，但参数容器是 `SOParameterVO`：逐行 `new SOParameterVO().setView(viewVO)` | 有：原因输入 `0233/0234/0544`；多行标题 `04006011-0379`；**前置校验 `checkBusiOutOpen`**：业务类型不含 `ICBillType.SaleOut` 时 `showErrorDlg`（`04006011-0380/0284/0381`，标题 `04006011-0010`）并整体放弃 | `SOParameterVO[] openSaleOrderOut(SaleOrderVO[], boolean)`（`boolean` 硬编码 `iconst_0`=false，且**实现体未引用该参数**，§3.2） | `SOParameterVO[]` | `processReturnObjs(SOParameterVO[])`：取 `paraVO.getView()` 转型 ViewVO→`directSave`→`loadLoadRelationItemValue`；末尾 `refreshAction.doAction(event)` | 多行→`MultiBillTaskRunner` | **静态核实** |
| 225 | `QD-ES-9985A340EB7EFFC4` | `InvoiceClose` | `invoiceCloseAction` | `M30CloseInvoiceCloseAction`<br>`extends NCAction`<br>同上两接口 | **选中行**（开票关闭按行） | 有：原因输入 `0233/0234/0544`；多行标题 `04006011-0287` | `SaleOrderViewVO[] closeSaleOrderInvoice(SaleOrderVO[])`（service 内 `changeToSaleOrderVO()`） | `SaleOrderViewVO[]` | 同 219；末尾 `refreshAction.doAction(event)` | 多行→`MultiBillTaskRunner` | **静态核实** |
| 226 | `QD-ES-C6E139BBEF4B8BE6` | `InvoiceOpen` | `invoiceOpenAction` | `M30CloseInvoiceOpenAction`<br>`extends NCAction`<br>同上两接口 | **选中行** | 有：原因输入 `0233/0234/0544`；多行标题 `04006011-0378` | `SaleOrderViewVO[] openSaleOrderInvoice(SaleOrderVO[])` | `SaleOrderViewVO[]` | 同 219；末尾 `refreshAction.doAction(event)` | 多行→`MultiBillTaskRunner` | **静态核实** |
| 227 | `QD-ES-BA71E137623CEC43` | `SettleClose` | `settleCloseAction` | `M30CloseSettleCloseAction`<br>`extends NCAction`<br>同上两接口 | **选中行**（结算关闭按行） | 有：原因输入 `0233/0234/0544`；多行标题 `04006011-0292` | `SaleOrderViewVO[] closeSaleOrderSettle(SaleOrderVO[])`（service 内 `changeToSaleOrderVO()`） | `SaleOrderViewVO[]` | 同 219；末尾 `refreshAction.doAction(event)` | 多行→`MultiBillTaskRunner` | **静态核实** |
| 228 | `QD-ES-42EE6E523F56E9D0` | `SettleOpen` | `settleOpenAction` | `M30CloseSettleOpenAction`<br>`extends NCAction`<br>同上两接口 | **选中行** | 有：原因输入 `0233/0234/0544`；多行标题 `04006011-0386` | `SaleOrderViewVO[] openSaleOrderSettle(SaleOrderVO[])` | `SaleOrderViewVO[]` | 同 219；末尾 `refreshAction.doAction(event)` | 多行→`MultiBillTaskRunner` | **静态核实** |

### 2.1 按钮启用条件（`isActionEnable`，静态核实）

| 行 | 启用判定（字节码控制流） |
| ---: | --- |
| 217 BillClose | `getSelectedData()!=null` **且** `BillStatus.AUDIT.equalsValue(head.getFstatusflag())` |
| 218 BillOpen | `getSelectedData()!=null` **且** `BillStatus.CLOSED.equalsValue(head.getFstatusflag())` |
| 219 LineClose | 无选中→false；**`getSelectedOperaRows().length > 1` 直接 return true（跳过状态校验）**；单选→`head=AUDIT` 且 `body.getFrowstatus()!=CLOSED` |
| 220 LineOpen | 同上结构，单选分支→`head∈{AUDIT,CLOSED}` 且 `body.getFrowstatus()==CLOSED` |
| 221 SendClose | 同 219 结构；单选分支：`head=AUDIT` 且 `!body.getBbsendendflag()` 且 `rowstatus!=CLOSED` |
| 222 SendOpen | 同 219 结构；单选分支：`head∈{AUDIT,CLOSED}` 且 `!blaborflag/!bdiscountflag` 且 `!body.getBbsendendflag()` |
| 223 OutClose | 同 219 结构；单选分支：`head=AUDIT` 且 `!body.getBboutendflag()` 且 `rowstatus!=CLOSED` |
| 224 OutOpen | 同 219 结构；单选分支：`head∈{AUDIT,CLOSED}` 且 `!blaborflag/!bdiscountflag` 且 `!body.getBboutendflag()` |
| 225 InvoiceClose | 同 219 结构；单选分支：`head=AUDIT` 且 **`!body.getBbinvoicendflag()`** 且 `rowstatus!=CLOSED` |
| 226 InvoiceOpen | 同 219 结构；单选分支：`head∈{AUDIT,CLOSED}` 且 `!body.getBbinvoicendflag()` |
| 227 SettleClose | 同 219 结构；单选分支：`head=AUDIT` 且 `!body.getBbcostsettleflag() && !body.getBbarsettleflag()` 且 `rowstatus!=CLOSED` |
| 228 SettleOpen | 同 219 结构；单选分支：`head∈{AUDIT,CLOSED}` 且 `!body.getBbcostsettleflag() && !body.getBbarsettleflag()` |

> **风险 R1**：10 个行级按钮的「多选即放行」是**原生行为**——`getSelectedOperaRows().length > 1` 时直接返回 `true`，完全不看被选中行的状态。因此混合选择（已关闭 + 未关闭、不同业务类型）在**客户端不拦截**，只在服务端状态机/校验抛错。签名层若照抄此逻辑，等于让签名窗口在多选时对"根本不可执行"的行也收一次授权。

---

## 3. 关键结论与风险

### 3.1 整单 vs 行动作的分界（静态核实）

| 分类 | 行 | 判定依据（字节码） |
| --- | --- | --- |
| **整单**（2 项） | 217 BillClose、218 BillOpen | 独有 `private billComposite(SaleOrderViewVO[])`：按 `head.getCsaleorderid()` 去重 + 再遍历 `model.getRows()` 补齐该单全部行 → 组装 `SaleOrderVO`（头+全部体） |
| **行动作**（10 项） | 219–228 | 无 `billComposite`；直接把 `getSelectedOperaDatas()` 得到的 `SaleOrderViewVO`（单行视图）作为操作对象；服务端接口签名也以 `SaleOrderViewVO[]` 为入参（219/220）或由 service 内 `changeToSaleOrderVO()` 转单行整单（221/223/225/227） |

- 重要的语义差别：行级"发货/出库/开票/结算"关闭/打开，**只操作选中那一行**（`csaleorderbid` 粒度），不会牵动同单其它行；整单关闭/打开则一次性作用于整张单的全部行。
- 「行」在数据结构上是 `SaleOrderViewVO`（head+body 成对），主键线索 `SaleOrderBVO.CSALEORDERBID`；「单」是 `SaleOrderVO`（`AbstractBill`，主键线索 `SaleOrderHVO.CSALEORDERID`）。

### 3.2 `openSaleOrder` / `openSaleOrderOut` 的布尔参数含义（静态核实 + 标注推断）

- **客户端实参恒为 `false`**：`M30CloseBillOpenService.operateBill` 偏移 42 `iconst_0`、`M30CloseOutOpenService.operateBill` 偏移 45 `iconst_0`。**原生界面两条"打开"链路都不会跳过 ATP 检查**。
- **`openSaleOrder(vos, boolean)` 中该布尔 = "放弃/跳过 ATP 检查"**（静态核实）：实现体第一句即
  ```
  0: iload_2
  1: ifeq 7
  4: invokestatic nc/itf/so/pub/ref/ic/m4c/SOATPprocess.abandonATPCheck:()V
  ```
  → `true` 时调用 `SOATPprocess.abandonATPCheck()` 后才进入 `updateCloseOpenReason → BillOpenState.setState → InvoiceOpenState(行) → 组装 SOParameterVO[]`。
- **`openSaleOrderOut(vos, boolean)` 的同一布尔在本版实现中未被引用**（静态核实）：方法体（`impl` 473–557 行）内**没有任何 `iload_2`**，无 `abandonATPCheck` 调用，直接 `convert → updateCloseOpenReason → OutOpenState → 组装 SOParameterVO[]`。→ **推断**：该参数为历史遗留/预留开关，当前版本传 `false` 还是 `true` 对出库打开行为无差异；**不要**据此设计"关闭 ATP"开关。
- **推断**：`abandonATPCheck` 是服务端线程级开关（方法无参、静态），因此它只对"本次调用会话"生效，不能当作持久化配置。

### 3.3 ATP 不足时的原生重试契约（静态核实，必须原样保留）

`M30CloseBillOpenAction.doAction` 的实质结构（偏移 328–569）：

```java
boolean continueFlag = true;
while (continueFlag) {
  try {
    if (singleBillService != null) {
      if (paraVOs.length > 1)  multiBillTaskRunner...runTask();
      else if (paraVOs.length == 1) { r = singleBillService.operateBill(paraVOs[0]); processReturnObjs(new Object[]{r}); }
    }
    continueFlag = false;
  } catch (Exception e) {
    Throwable t = ExceptionUtils.unmarsh(e);
    if (t instanceof AtpNotEnoughException) {
      int answer = MessageDialog.showYesNoDlg(workbench.getParent(), resId 04006011-0244, ((AtpNotEnoughException)t).getMessage());
      if (answer == 4) {                      // 4 = MessageDialog 的"是"常量（常量值未逐一核实，见 §4）
        for (int i = 0; i < paraVOs.length; i++)
          paraVOs[i].getBusinessCheckMap().put(BusinessCheck.ATPCheck.getCheckCode(), Boolean.FALSE);
      }
      continueFlag = true;                     // ← 无论"是/否"都继续循环
    } else { continueFlag = false; ExceptionUtils.wrappException(e); }
  }
}
refreshAction.doAction(event);
```

两点必须注意：
1. **回答"否"也会 `continueFlag=true`** → 同一批单据用**相同参数**再次发起 RPC，通常再次抛 ATP 异常并再次弹窗，直到用户点"是"或发生其它异常。→ **推断**：这是原生缺陷（"否"未作为取消分支），签名层不得照抄；覆盖实现必须让"否"真正终止。
2. **`businessCheckMap` 在这条链路上不生效**（静态核实）：`M30CloseBillOpenService.operateBill` 只读取 `SOParameterVO.getVo()`，从不读取 `getBusinessCheckMap()`，也没有把它转交给服务端。→ **推断**：ATP "继续"实际是靠 `SCMResumeExceptionInvoker` 的 resume 机制完成的（见 §3.4），客户端塞的 `businessCheckMap` 是**死写入**。因此签名层**不能**用 `businessCheckMap` 表达"用户已同意继续"。

### 3.4 服务调用外壳：resume 组合远程调用 + 自动重试（静态核实）

`nc.ui.scmpub.model.SCMResumeExceptionInvoker`（`uiscmpub_scmpub.jar`）：

- `getService(Class)` 用 `Proxy.newProxyInstance(..., RemoteInterfaceInvokeHanlder)` 生成代理 → **12 个 Service 的每一次服务调用都经过它**。
- `doInvokeWithResumeProcess(Method, Object[])`：
  ```
  while (continueFlag) {
    try {
      resumeInfo = RemoteCallInfo(IResumeResultService.setResumeResult(Map))   // 第一次远程调用
      bizInfo    = RemoteCallInfo(业务方法, 实参)                              // 第二次远程调用
      results = NCLocator.lookup(IRemoteCallCombinatorService).doRemoteCall([resumeInfo, bizInfo]);
      return results.get(1).getResult();
    } catch (Exception e) {
      if (t instanceof IResumeException && isResume((IResumeException) t)) continueFlag = true;   // 自动重发
      else if (!(t instanceof IResumeException)) ExceptionUtils.wrappException(e);
      else continueFlag = false;
    }
  }
  ```
- `isResume` 委托 `nc.ui.scmpub.util.ResumeExceptionUIProcessUtils.isResume(exception, resumeResultContainer)`。

**结论/风险 R2**：一次"关闭/打开"点击在原生实现里可能产生**两次 RPC**（resume 标记 + 业务方法），业务方法还可能被**自动重发若干次**；`MultiBillTaskRunner` 又是逐单/逐行一次 RPC；再叠加 217/218 的 `while` ATP 循环。签名层必须按"一次点击 = 可能多次真实写入尝试"设计幂等与审计，**不可假定 1 次受控执行 = 1 次服务调用**。

### 3.5 返回 `SOParameterVO[]` 不能当逐单成功回执（静态核实）

- `SOParameterVO` 结构只有 `vo / vos / view / views / userobject / businessCheckMap`，**没有任何成功标志、错误码或逐单消息字段**（`vo-SOParameterVO.javap.txt`）。
- 服务端 `openSaleOrder` / `openSaleOrderOut` 在异常时是 `catch(Exception){ ExceptionUtils.marsh(e); return null; }` → 失败表现为**整体 null 或部分元素为 null**，不携带"哪一张失败/为何失败"。
- 客户端 `M30CloseBillOpenService.operateBill` 对 `null` 只做 `ifnull` 跳过 `setVo`，仍返回一个 `SOParameterVO` 包装；`processReturnObjs` 再用 `paraVO.getVo()/getView()` 重建 ViewVO 行并 `directSave` → **界面显示的是"客户端回填后的行"，不是服务端确认结果**。
- 批量路径的"逐单成败"只存在于客户端进度框架：`MultiBillTaskRunner$1.doInBackground` 对每个对象调用 `ISingleBillService.operateBill` 并构造 `ProgressInfo(index, success, throwable)`，收集成失败条目后在 `doFinished` 里拼消息。
- → **签名/审计口径**：这 12 项**不能**用返回值判断逐单成功；必须以"服务端落库后的重查值 + 请求终态 + 业务事务"三方一致为准（与项目既有 `OrderPhysicalSnapshots` 反向断言思路一致）。

### 3.6 其他原生行为差异（静态核实，易被覆盖层破坏）

| # | 事实 | 影响 |
| ---: | --- | --- |
| 1 | 219 LineClose 的刷新在 `processReturnObjs` 内用 `refreshAction.getDataManager().refresh()`；其余 11 项在 `doAction` 末尾用 `refreshAction.doAction(event)` | 覆盖 219 时若只保留 `super.doAction` 而不放行 `processReturnObjs`，列表不会刷新 |
| 2 | 220 LineOpen 在 `operateBill` 之后额外 `ISaleOrderForPub.querySaleOrderViewVOs(全部行 body id)` 回查并 `processReturnObjs(回查结果)` | 该回查**覆盖当前列表全部行**（不是仅选中行）；覆盖时漏掉会丢失"同单其它行也随之变化"的显示 |
| 3 | 222 SendOpen / 224 OutOpen 有**整批否决式前置校验**（业务类型不含发货/出库时 `showErrorDlg` 后整体放弃，仅刷新） | 覆盖层若绕过校验直接调服务，会让本应被拦下的单据进入服务端 |
| 4 | 217/218 会**无条件**（即使 `singleBillService==null`）执行末尾 `refreshAction.doAction` | 覆盖层早退（例如签名取消）时必须**不**刷新，否则会误丢弃用户当前选择状态（原生只在真正执行后刷新） |
| 5 | 原因输入框 `bipush 120` 为第 5 个参数（`MessageDialog.showInputDlg(Container,String,String,Object,int)`） | 覆盖实现签名必须用 5 参重载；参数语义（最大长度/宽度）本机未核实 |
| 6 | 行级服务把 `SaleOrderViewVO` 转整单时才调用 `changeToSaleOrderVO()`（222/223/225/227/228 在 service 内） | 覆盖层若在 action 侧就转整单，会改变"行"语义 |

### 3.7 与 `SALEORGTYPE000000000` 及现场注册的关系

- 节点 `orgTypeCode=SALEORGTYPE000000000`（销售组织），`registeredButtonCount=18`，`validated=N`。
- 12 行的 `mappingState` **全部**为 `REGISTERED_BUTTON_RPC_PENDING`、`validated=N`、`enabled=N`、`remaining="最终RPC、服务包装、同步审计和NC事务待验证"`（`operation-bindings.csv:L286-297`）。
- → 现场注册层面**只到"注册存在、唯一命中"，未验证过 RPC/服务包装/审计/事务**；本文件提供的正是这 4 项缺失的静态基础，但**不能替代**真实会话验证。

---

## 4. 未核实清单（需真实 NC 会话 / 数据库 / 元数据才能确认）

| # | 未核实项 | 为什么重要 | 观察位置 |
| ---: | --- | --- | --- |
| 1 | **弹窗中文文案**：`4006011_0` 资源包（`04006011-0233/0234/0544/0244/0286/0287/0288/0290/0291/0292/0377/0378/0379/0380/0381/0383/0384/0386/0284/0010`）在本机 `nchome` 全盘与 `modules\{so,riart,pubapp,uap}\**\*.jar` 内**均 0 命中** | 无法给出按钮弹窗的准确中文；文案错会直接影响用户对"关闭原因/ATP"的理解 | 远程客户端资源目录 / `NCLangRes` 实际解析路径；或真实会话截屏 |
| 2 | `MessageDialog.showYesNoDlg` 返回常量 `4` 的语义（是/否） | 决定 §3.3 里"是"分支的判定；若 `4` 实为"否"，原生逻辑方向相反 | `nc/ui/pub/beans/MessageDialog` 常量池值（`public static final int` 未在 `javap -p` 中显示值，需 `-constants`） |
| 3 | `SOATPprocess.abandonATPCheck()` 的副作用范围（线程级？会话级？是否影响后续调用） | 若为会话级持久开关，误用会**长期关闭全系统 ATP 检查** | `pubso_pub.jar!nc/itf/so/pub/ref/ic/m4c/SOATPprocess` 实现（本机仅有接口侧） |
| 4 | `ResumeExceptionUIProcessUtils.isResume` 的判定条件与 resume 容器的键 | 决定自动重试次数与触发条件（§3.4） | `uiscmpub_scmpub.jar!nc/ui/scmpub/util/ResumeExceptionUIProcessUtils` |
| 5 | 物理表名与列名：`SaleOrderHVO/BVO` **未重写 `getTableName()`**，表名来自 BMF 元数据 | 审计快照/字段标签需要真实的表列名 | `*-*-bmf.xml` 元数据或数据库字典；`SaleOrderHVO.CSALEORDERID/PK_ORG/PK_ORG_V/PK_GROUP/FSTATUSFLAG/VBILLCODE/CBIZTYPEID/DBILLDATE`、`SaleOrderBVO.CSALEORDERBID/CSALEORDERID/VCLOSEREASON/FROWSTATUS/PK_ORG/NNUM/NASTNUM` 为字段名常量（已静态核实） |
| 6 | `vclosereason` 的落库列与长度（`updateCloseOpenReason` 走 `VOUpdate.update`，仅见字段名） | 签名事由与关闭原因分列存放的可行性 | 服务端 `VOUpdate` 目标表；数据库反查 |
| 7 | 多选混合状态下服务端是否逐行校验并部分成功 | 决定"部分成功"审计与 UI 提示口径（客户端多选不校验，见 R1） | 真实会话：选 2 行（一已关闭一未关闭）执行行关闭，观察进度框与库值 |
| 8 | 12 项各自的服务端状态机规则（`BillCloseState/BillOpenState/SendCloseState/...`）与前置校验 | 决定失败原因与可重入性 | `so_salesorder.jar!nc/bs/so/m30/state/**`（本次未展开） |
| 9 | `ISaleOrderForPub.querySaleOrderViewVOs` 的查询条件（220 回查范围） | 影响 220 覆盖后的显示集合 | `pubso_pub.jar`/`so_saleorder.jar` 内实现 |
| 10 | 行级按钮的 `pagecode`/`pk_page` 与页签级注册 | 显示与按钮权限入口可能不止 18 条 | `sm_funcregister`+`sm_butnregister`（JOIN `sm_pageregister`）按 funcode=40060303 |
| 11 | 12 个 `pk_btn` 的按钮级 `sm_paramregister` | 权限/参数白名单无法定稿 | `sm_paramregister WHERE parentid IN (12 个 pk_btn)` |
| 12 | 真实会话加载的类、按钮显隐、`SCMActionInitializer` 是否被运行时替换 | 0 条真实会话证据；静态核实不能升级 | 客户端 `BeanConfigFilePath` 生效后的实际动作类；`nclogs` 组件加载日志 |
| 13 | 12 项是否还有 PF/审批流/外发插件旁路 | 关闭管理节点为直接服务调用，未查 `pub_pluginitem` | `pub_pluginitem`（`vextendpointname like '%CloseManage%'`、`%ActionPlugInPoint%`）；服务端插件日志 |
| 14 | `Open` 方向也要求填写"原因"是否符合业务预期 | 打开写 `vclosereason` 会覆盖上次关闭原因 | 真实会话 + 库反查（本项属产品行为确认，非代码问题） |

---

## 5. 原始证据索引（`build/native-evidence-other-nodes/close-manage/`）

全部为 `javap -p -c`（或 `-p`）原样输出，未做删改；`-c` 表示含字节码。

### 5.1 客户端动作类（`uiso_salesorder.jar`）

| 文件 | 类 | 对应行 |
| --- | --- | ---: |
| `action-M30CloseBillCloseAction.javap.txt` | `nc.ui.so.m30.closemanage.action.M30CloseBillCloseAction` | 217 |
| `action-M30CloseBillOpenAction.javap.txt` | `...M30CloseBillOpenAction` | 218 |
| `action-M30CloseRowCloseAction.javap.txt` | `...M30CloseRowCloseAction` | 219 |
| `action-M30CloseRowOpenAction.javap.txt` | `...M30CloseRowOpenAction` | 220 |
| `action-M30CloseSendCloseAction.javap.txt` | `...M30CloseSendCloseAction` | 221 |
| `action-M30CloseSendOpenAction.javap.txt` | `...M30CloseSendOpenAction` | 222 |
| `action-M30CloseOutCloseAction.javap.txt` | `...M30CloseOutCloseAction` | 223 |
| `action-M30CloseOutOpenAction.javap.txt` | `...M30CloseOutOpenAction` | 224 |
| `action-M30CloseInvoiceCloseAction.javap.txt` | `...M30CloseInvoiceCloseAction` | 225 |
| `action-M30CloseInvoiceOpenAction.javap.txt` | `...M30CloseInvoiceOpenAction` | 226 |
| `action-M30CloseSettleCloseAction.javap.txt` | `...M30CloseSettleCloseAction` | 227 |
| `action-M30CloseSettleOpenAction.javap.txt` | `...M30CloseSettleOpenAction` | 228 |

### 5.2 客户端服务类（`uiso_salesorder.jar`）

`service-M30CloseBillCloseService` / `BillOpenService` / `RowCloseService` / `RowOpenService` / `SendCloseService` / `SendOpenService` / `OutCloseService` / `OutOpenService` / `InvoiceCloseService` / `InvoiceOpenService` / `SettleCloseService` / `SettleOpenService`（均 `.javap.txt`）。

### 5.3 服务端接口与实现

| 文件 | 类 | 来源 JAR |
| --- | --- | --- |
| `iface-ISaleOrderCloseManageMaintain.javap.txt` | `nc.itf.so.m30.closemanage.ISaleOrderCloseManageMaintain` | `pubso_salesorder.jar` |
| `impl-SaleOrderCloseManageMaintainImpl.javap.txt` | `nc.impl.so.m30.closemanage.SaleOrderCloseManageMaintainImpl` | `so_salesorder.jar` |

### 5.4 VO

`vo-SaleOrderVO.javap.txt`、`vo-SaleOrderViewVO.javap.txt`（`pubso_salesorder.jar`）、`vo-SaleOrderHVO.javap.txt`、`vo-SaleOrderBVO.javap.txt`（字段名常量）、`vo-SOParameterVO.javap.txt`（`pubso_pub.jar`）。

### 5.5 框架/基础设施

| 文件 | 类 | 来源 |
| --- | --- | --- |
| `infra-SaleOrderCloseManageService.javap.txt` | `...model.SaleOrderCloseManageService` | `uiso_salesorder.jar` |
| `infra-CloseManageModelDataManager.javap.txt` | `...model.CloseManageModelDataManager` | 同上 |
| `infra-SaleOrderCloseManageListView.javap.txt` | `...view.SaleOrderCloseManageListView` | 同上 |
| `infra-M30CloseBillCardPanelValueAdapter.javap.txt` | `...model.M30CloseBillCardPanelValueAdapter` | 同上 |
| `infra-MultiBillTaskRunner.javap.txt`、`infra-MultiBillTaskRunner-1.javap.txt`、`infra-MultiBillTaskRunner-2.javap.txt` | `nc.ui.pubapp.pub.task.MultiBillTaskRunner` 及其内部 SwingWorker | `uipubapp_platformextLevel-1.jar` |
| `infra-ISingleBillService.javap.txt` | `ISingleBillService<E>`（仅 `E operateBill(E)`） | 同上 |
| `infra-MessageDialog.javap.txt` | `nc.ui.pub.beans.MessageDialog` | `uiriart_ncbeanLevel-1.jar` |
| `infra-SCMResumeExceptionInvoker.javap.txt` | `nc.ui.scmpub.model.SCMResumeExceptionInvoker` | `uiscmpub_scmpub.jar` |

---

## 6. 一句话结论

12 项全部为「填写关闭/打开原因 → 取选中（整单 2 项按 `csaleorderid` 展开整单全部行，行级 10 项按选中行）→ 单笔直调 / 多笔 `MultiBillTaskRunner` 逐单 RPC → 用返回 VO 回填并刷新」的同构结构，服务端为 `ISaleOrderCloseManageMaintain` 12 个方法；`openSaleOrder/openSaleOrderOut` 的布尔参数是「跳过 ATP 检查」且客户端恒传 `false`、`openSaleOrderOut` 的实现根本没用到它；`SOParameterVO[]` 无任何逐单成败字段，不能当回执；行级按钮多选时原生**跳过状态校验**，且服务调用外壳带 resume 组合调用与自动重试——这四点必须成为签名覆盖层设计的硬约束。
