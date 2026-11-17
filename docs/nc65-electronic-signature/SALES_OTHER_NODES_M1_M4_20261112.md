# 销售管理其他三节点 M1–M4 实现记录（2026-11-12）

本文件记录 `SALES_OTHER_NODES_DEVELOPMENT_PLAN.md` 的 M1（公共协议与隔离构建）、M2（关闭管理）、
M3（发货单）、M4（销售发票）实现。**全部为离线开发与离线测试**：未部署、未执行 SQL、未启动 NC、
未连接数据库、未触发任何外部平台调用。真实 NC 联调（M5）尚未开展，任何入口不得据此在目标组织启用。

## 1. M1 公共协议（共用设计）

| 交付项 | 文件 | 说明 |
| --- | --- | --- |
| 三节点固定范围白名单 | `uapbd/src/public/nc/vo/uapbd/qd/esign/SalesOtherNodeScope.java` | 28 个正式范围 ID、节点/按钮/动作域/对象类型/行对象类型/可执行开关/注册组织类型；同名按钮分节点登记，不共享授权 |
| 通用 PF 命令 | `uapbd/src/public/nc/vo/uapbd/qd/esign/SalesPfActionCommand.java` | 交易类型、单据、原生用户对象、审批说明、参数白名单载体（防御性复制） |
| 通用 PF 结果 | `uapbd/src/public/nc/vo/uapbd/qd/esign/SalesPfActionResult.java` | 原生返回 + 真实主键/单号 + 删除事实；返回 VO 不自动视为成功 |
| 三个固定委托接口 | `so/src/public/nc/itf/so/qd/esign/ISaleCloseOperationAdapter.java`、`IDeliveryOperationAdapter.java`、`ISaleInvoiceOperationAdapter.java` | 均 extends `BusinessOperationAdapter`（validate/execute/describeResult） |
| 适配器白名单 | `uapbd/src/private/nc/impl/uapbd/qd/esign/OperationTransactionServiceImpl.java` | ADAPTERS 增加三项；未列入一律拒绝，不允许客户端传服务名 |
| 映射对象域校验 | `uapbd/src/private/nc/bs/uapbd/qd/esign/core/MappingRules.java` | 新增三节点分支：根对象必须等于映射 object_type，行对象只允许已核实行类型且必须挂在根下 |
| 审计快照识别 | `uapbd/src/private/nc/bs/uapbd/qd/esign/audit/SalesOtherNodeSnapshots.java` | 三节点各自头/体表与主键；根对象采集头+全部行，行对象单独采集；网关 before/after 挂钩 |
| 客户端一次性交接 | `uapbd/src/client/nc/ui/uapbd/qd/esign/SalesNodeActionHandoff.java` | 单节点绑定；签名后按钮/组织/根对象变化、重复绑定/派发一律拒绝；行集合以子对象形式随授权持久化 |
| 客户端选择判定 | `uapbd/src/client/nc/ui/uapbd/qd/esign/SalesNodeSelection.java` | 单笔/多笔/混合组织/归属不明；混合与不明拒绝，不回落原生写入 |
| 客户端 PF 桥 | `uapbd/src/client/nc/ui/uapbd/qd/esign/SalesPfPreparation.java`、`SalesPfActionDispatch.java` | 版本钉死的原生 PF 准备（`PfUtilClient` 字节码哈希校验）+ 类型化命令派发 + 结果核对 + 原生结果回填 |
| 服务注册 | `so/META-INF/qd_esign_sales_other_nodes.upm` | 新增 UPM 文件注册三个适配器（remote=false, CMT），不改既有 `qd_esign_saleorder.upm` / `qd_esign_core.upm` |
| 隔离构建记录 | `sales-other-nodes-release/build.ps1` | 冻结源码快照内运行全量 `tools/build.ps1 -Test`，产出可复核记录；避免共享工作区并行写入导致冻结校验失败 |
| 候选打包 | `sales-other-nodes-release/package.ps1` | 基于基线 JAR 增量覆盖本批类 + 新增 UPM + SQL 草案 + manifest + rollback.ps1；不部署、不执行 SQL |

**组织与激活边界**：本批不写死试点组织。受控动作只在 `qd_es_policy` 对「组织 + 节点 + 按钮」开启时生效；
`SalesOtherNodeScope.supports` 只校验集团与业务组织非空、无首尾空白，组织类型与数据条件由只读预检、
`configure.sql` 的 `org_orgtype.fieldname` 动态校验和现场确认共同保证。

## 2. M2 销售订单关闭管理（40060303，217–228，12 项）

交付：
- `so/src/public/nc/vo/so/qd/esign/SaleCloseCommand.java`：整单 `SaleOrderVO[]` / 行 `SaleOrderViewVO[]` + `closeReason`；
  防御性复制；不携带方法名、SQL 或服务名。
- `so/src/private/nc/impl/so/qd/esign/SaleCloseActionRoute.java`：12 按钮固定映射到 `ISaleOrderCloseManageMaintain`
  的 7 个方法；区分整单/行粒度与返回类型；按钮、动作域、单根对象、授权行（子对象）逐项校验；未启用动作执行前拒绝。
- `so/src/private/nc/impl/so/qd/esign/SaleCloseOperationAdapter.java`：原因必填且与受影响行 `vclosereason` 一致；
  整单动作要求整单行完整、拒绝子对象；行动作只接受单行且必须属于授权订单；单次原生调用、异常原样上抛不重试；
  返回数组按授权主键逐一核对（空/多余/重复/身份不符拒绝）；`describeResult` 覆盖根对象与授权行。
- `tests/java/nc/test/qd/esign/SaleCloseAdapterTests.java`：353 条离线断言。

逐项状态：
| 行 | 按钮 | 服务端分支 | 可执行 | 说明 |
| ---: | --- | --- | --- | --- |
| 217 | BillClose | 已实现（`closeSaleOrder`） | 是 | 整单+全部行；原因与行原因一致 |
| 218 | BillOpen | 已实现（`openSaleOrder(vos,false)`） | **否** | 需 ATP「继续确认」恢复协议（`IResumeException` 重试 + `ATPCheck=false`），受控单次 RPC 无法表达，保持关闭 |
| 219 | LineClose | 已实现（`closeSaleOrderRow`） | 是 | 单行授权 |
| 220 | LineOpen | 已实现（`openSaleOrderRow`） | 是 | 单行授权 |
| 221 | SendClose | 已实现（行→VO 后 `closeSaleOrderSend`） | 是 | 与原 service 相同的 `changeToSaleOrderVO()` |
| 222 | SendOpen | 已实现（`openSaleOrderSend`） | 是 | 原生前置业务校验由原生服务保留 |
| 223 | OutClose | 已实现（`closeSaleOrderOut`） | 是 | 单行授权 |
| 224 | OutOpen | 已实现（`openSaleOrderOut(vos,false)`） | **否** | 需原生继续确认；布尔参数在原实现中未使用，不作为开关 |
| 225 | InvoiceClose | 已实现（`closeSaleOrderInvoice`） | 是 | 单行授权 |
| 226 | InvoiceOpen | 已实现（`openSaleOrderInvoice`） | 是 | 单行授权 |
| 227 | SettleClose | 已实现（`closeSaleOrderSettle`） | 是 | 单行授权 |
| 228 | SettleOpen | 已实现（`openSaleOrderSettle`） | 是 | 单行授权 |

**客户端覆盖层本批未交付**：12 个原生动作是 `NCAction` 子类，内部先弹「关闭/打开原因」输入、经
`SCMResumeExceptionInvoker` 组合代理与 while 重试调用服务；正确的签名点应在原因确认之后、原生写入之前，
在无真实 NC 窗口的情形下无法静态证明安装/恢复顺序与取消语义。按计划「未核实分支保持关闭」，
本批只交付服务端固定映射与审计识别，客户端入口留待 M5 联调确认后再交付。

## 3. M3 发货单维护（40060402，229–238，10 项）

交付：
- `so/src/public/nc/vo/so/qd/esign/DeliveryCommand.java`、`DeliveryStateResult.java`：整单 `DeliveryVO[]`、
  行下标 `int[]`（防御复制）、`SOParameterVO[]`；直接服务结果携带真实主键/单号。
- `so/src/private/nc/impl/so/qd/esign/DeliveryActionRoute.java`：10 按钮 → PF（WRITE/DELETE/SAVE/UNSAVE/APPROVE/UNAPPROVE）
  与 `IDeliveryAssitFunc`（closeDelivery/openDelivery/closeDeliveryRows/openDeiveryRows）。
- `so/src/private/nc/impl/so/qd/esign/DeliveryOperationAdapter.java`：
  - PF 动作使用 `SalesPfActionCommand`，核对单据类型 `DeliveryVO`、主键、集团/组织、交易类型 `4331`、
    原生用户对象、参数白名单；执行后以 `loadHeader` 核对持久化事实；删除以 `dr=1`/不存在确认。
  - 直接服务动作使用 `DeliveryCommand`：整单关闭只接受 `vos`=1 张同主键、`businessCheckMap` 为空、
    `vo/view/views/userObject` 为空；行关闭**先读取持久化单据**核对 `int[]` 下标与授权行主键一致后才调用原生，
    防止排序/过滤/新增行导致错行或越界。
- 客户端覆盖层（5 个）：`QdDeliverySaveAction`、`QdDeliverySendApproveAction`、`QdDeliveryUnSendApproveAction`、
  `QdDeliveryApproveAction`、`QdDeliveryUnApproveAction` + `QdDeliveryConfig.xml`（import 原生 `delivery_config.xml`
  并覆盖同名 5 个 bean，不覆盖删除与 4 个直接服务动作）。
- `tests/java/nc/test/qd/esign/DeliveryAdapterTests.java`（248 断言）、
  `tests/java/nc/ui/uapbd/qd/esign/DeliveryClientConfigTests.java`。

逐项状态：
| 行 | 按钮 | 落点 | 可执行 | 说明 |
| ---: | --- | --- | --- | --- |
| 229 | Save | PF WRITE | 是 | 新增/修改；EDIT 态原生二次确认（04006002-0113）在签名之后仍可能出现，列入未核实分支 |
| 230 | Delete | PF DELETE | **否** | 确认框在 PF 之前；「确认后签名」顺序未核实，保持关闭 |
| 231 | Commit | PF SAVE | 是 | 单独提交；保存并提交复合入口未接入 |
| 232 | UnCommit | PF UNSAVE | 是 | |
| 233 | Approve | PF APPROVE | 是 | 审批窗口「确定」后写入；通知/意见/非终审待联调 |
| 234 | UnApprove | PF UNAPPROVE | 是 | |
| 235 | BillClose | `closeDelivery(SOParameterVO)` | 是 | 参数白名单严格；ATP/信用检查由原生规则处理 |
| 236 | BillOpen | `openDelivery(SOParameterVO)` | **否** | ATP/信用/容差继续协议未核实 |
| 237 | OutClose | `closeDeliveryRows(DeliveryVO,int[])` | 是 | 服务端重核下标与行主键 |
| 238 | OutOpen | `openDeiveryRows(SOParameterVO,int[])` | **否** | 4 类检查继续协议未核实 |

## 4. M4 销售发票维护（40060501，239–244，6 项）

交付：
- `so/src/private/nc/impl/so/qd/esign/SaleInvoiceActionRoute.java`：6 按钮 → 固定 PF 动作。
- `so/src/private/nc/impl/so/qd/esign/SaleInvoiceOperationAdapter.java`：`SalesPfActionCommand` 校验（单据类型
  `SaleInvoiceVO`、主键/组织、交易类型 `32`、原生 `SaleinvoiceUserObject`、参数白名单、审批类允许流程说明）；
  单次 `processAction`、持久化事实核对、`describeResult` 覆盖真实主键/单号。
- 客户端覆盖层（5 个）：`QdSaleInvoiceSaveAction`、`QdSaleInvoiceCommitAction`、`QdSaleInvoiceUnCommitAction`、
  `QdSaleInvoiceApproveAction`、`QdSaleInvoiceUnApproveAction` + `QdSaleInvoiceConfig.xml`（import 原生
  `saleinvoice_config.xml`，覆盖 5 个 bean，不覆盖 deleteAction）。
- `tests/java/nc/test/qd/esign/SaleInvoiceAdapterTests.java`、
  `tests/java/nc/ui/uapbd/qd/esign/SaleInvoiceClientConfigTests.java`。

逐项状态：
| 行 | 按钮 | 落点 | 可执行 | 说明 |
| ---: | --- | --- | --- | --- |
| 239 | Save | PF WRITE | 是 | 保留组合校验器与 `SaleinvoiceUserObject`；参照/组合交易类型待现场逐一核对 |
| 240 | Delete | PF DELETE | **否** | 删除入口（Delete vs `fs_delete`）与「确认后签名」顺序未核实，保持关闭 |
| 241 | Commit | PF SAVE | 是 | 保存并提交复合入口未接入 |
| 242 | UnCommit | PF UNSAVE | 是 | |
| 243 | Approve | PF APPROVE | 是 | 审批窗口「确定」后写入；意见/决议/非终审/加签待联调 |
| 244 | UnApprove | PF UNAPPROVE | 是 | 已核销/下游出库等原生限制保留 |

**外部副作用**：6 项自身字节码未发现税控/数电/HTTP 调用；但审批后/取消审批后有跨模块回写（应收 m35、
结算 m33、会计平台凭证删除消息），是否属独立事务或外发未实测。本批不新增独立开票/红冲按钮，不改造既有自动接口。

## 5. 离线测试与构建

| 测试类 | 断言数 | 覆盖 |
| --- | ---: | --- |
| `SalesOtherNodeScopeTests` | 201 | 28 行范围 ID/按钮/动作域/对象类型/启用开关/组织口径/适配器识别 |
| `SalesOtherNodeSnapshotsTests` | 13 | 适配接口→快照范围映射；订单/发货/发票互不借用 |
| `SalesNodeHandoffTests` | 45 | 一次性交接绑定/派发/释放/变化拒绝；选择判定与混合组织拒绝 |
| `SaleCloseAdapterTests` | 353 | 12 项固定映射、启用边界、原因与行一致性、单笔约束、原生单次调用与返回核对 |
| `DeliveryAdapterTests` | 248 | 10 项映射、PF 参数白名单、行下标重核、原生调用次数与返回核对 |
| `DeliveryClientConfigTests` | 105 | 配置 XML bean 覆盖范围、原生 property、未覆盖动作、10 按钮启用边界 |
| `SaleInvoiceAdapterTests` | 122 | 6 项映射、PF 参数/用户对象、删除关闭、原生调用次数与返回核对 |
| `SaleInvoiceClientConfigTests` | 83 | 配置 XML 覆盖范围、继承关系、启用边界 |
| `SalesPfBridgeTests` | 6 | 覆盖 procFlow 后原生校验器复刻：无校验器空操作、批量校验器恰好一次、失败原样传播 |
| 合计 | **1176** | 均为离线替身；不调用 NC 服务、数据库或事务 |

运行器：`sales-other-nodes-release\check.ps1`（全量源码按 public→private→client→legacy-examples→test 编译，
只运行本批测试，使用独立输出目录，不写 `build/build-result.json`）。
正式证据：`sales-other-nodes-release\build.ps1` 在冻结源码快照内执行全量 `tools/build.ps1 -Test`（含 AllTests），
结果与哈希见 `SALES_OTHER_NODES_M6_20261112.md`。

## 6. 与既有销售的交叉与未改动项

- 未修改 `ISaleOrderOperationAdapter`/`SaleOrderOperationAdapter` 既有分支、销售订单保存试点、
  `SaleOrderActionRoute`、`QdSaleOrderConfig.xml`、`qd_esign_saleorder.upm`、`qd_esign_core.upm` 既有组件。
- 共享文件仅两处最小增量：网关 ADAPTERS 三项与快照挂钩、`MappingRules` 三节点分支；均不影响既有
  `ISaleOrderOperationAdapter` 等路径的判定结果（新增分支只在适配接口命中时生效）。
- 审计识别新增独立枚举，不修改 `OrderPhysicalSnapshots`，销售订单与采购订单快照路径保持不变。

## 7. 未完成 / 未验证（不得视为已完成）

1. M5 真实 NC 联调全部未开展：客户端入口（关闭管理 12 项、发货/发票直接服务动作）、权限/工作项、
   原生弹窗与确认顺序、ATP/信用/容差继续、通知次数、同事务回滚与审计注入、批量与部分成功。
2. 三节点试点组织与账号未确认（见 M0 文档 §2）。
3. `so_saleinvoice`/`so_saleinvoice_b` 表名为命名规则推断，须现场只读核对；三表字段白名单与聚合完整性未实测。
4. 发货单保存 EDIT 态二次确认、保存并提交两段式、审批 ENTRY_ONLY 入口的真实按钮行为未验证。
5. 销售发票跨模块回写（m35/m33/会计平台）事务边界与外部影响未实测。
6. 映射/回退 SQL 未在真实库执行或语法校验；客户端 `BeanConfigFilePath` 切换脚本未提供（须人工受控执行）。
7. 关闭管理客户端覆盖层未交付，因此 `configure.sql` 对 40060303 的客户端配置守卫会拦下 REGISTER——这是有意的失败关闭。

## 8. 明确的能力边界（避免误认为已覆盖）

1. **新增保存不在受控范围**：发货单 229 / 销售发票 239 的受控路径只接受已持久化单笔（签名上下文必须携带真实主键）。
   新单据在签名时尚无主键，计划要求的“绑定集团/组织/交易类型/临时对象并在保存后回填”方案本批未实现；
   新增保存仍走原生、不产生签名/审计。M3/M4 验收中“覆盖新增”一项**未达成**，须单独立项。
2. **保存动作域登记为 INSERT**：与销售订单保存试点一致。对已持久化单据的“修改”也落在 INSERT 域，
   现有网关规则对 INSERT 跳过 BEFORE 快照，因此修改类审计缺少 before 值/字段差异。是否改为 UPDATE 域
   需在联调时结合既有保存试点一并决策，本批不改网关规则。
3. **多选与批量一律拒绝**：受控路径只接受单笔；多选在客户端选择判定或服务端单对象约束处拒绝，不回落原生写入。
   原生批量语义（逐单事务、部分成功、逐对象回执）未验证，批量写入保持关闭。
4. **混合组织拒绝**：客户端 `SalesNodeSelection` 对混合/不明组织直接拒绝，不回落原生写入（有意的安全收窄），
   现场若确有合法的跨组织批量需求，须另行设计逐组织分流与各自签名授权。
5. **恢复/继续协议未接入**：关闭管理 BillOpen/OutOpen、发货单 BillOpen/OutOpen 因需要原生
   `IResumeException`+`businessCheckMap` 继续确认，受控单次 RPC 无法等价表达，全部保持关闭；
   发货单 Delete、销售发票 Delete 因删除确认位于 PF 之前、签名时序未核实，同样保持关闭。

