# 库存电子签名接入记录

## 逐项矩阵

`config/qd-esign/inventory-integration-matrix.csv` 以正式清单和 2026-11-07 现场映射快照生成，包含 83 个唯一 scopeId。73 项已有按钮注册，10 项已有上级入口。最终动作、业务服务、事务和审计采集栏按证据填写；待验证项继续保持待核对。测试栏目前均为未执行。

## 首批客户端证据

- 本机 `uiic_inbound.jar` 的 `nc/ui/ic/m45/PurchaseInAction.xml` 将保存动作绑定到 `PurchaseInSaveAction`。本机 `javap` 显示该类继承 `GeneralSaveAction`。
- 本机 `uiic_outbound.jar` 的 `nc/ui/ic/m4c/SaleOut_Org.xml` 将保存动作绑定到 `SaleOutSaveAction`。该类继承 `GeneralSaveAction`，并覆盖 `doBeforAction`、`processBefore`、`processReturnObj` 和恢复异常处理。字节码显示返回处理会调用 `updateCloseOrd`。库存保存接入需保留这些原生流程。
- 本机 `GeneralSaveAction.fillUpContext` 为新增和修改设置 `ICPFParameter.billaction`，并将其写回 `PFlowContext.userObj`。采购入库和销售出库已有的维护服务适配是候选服务，实际客户端末次调用和完整流程仍需核实。
- 本机 `javap` 确认 `PurchaseInVO` 和 `SaleOutVO` 继承 `ICBillVO`，两个单据的货位 VO 继承 `ICLocationVO`。本机元数据将货位映射到 `ic_purchasein_l` 和 `ic_saleout_l`。
- 采购入库保存动作配置了 `validateService`。本机 `AbstractScriptExcAction.procFlow` 字节码显示，原生流程在调用 `PFlowProxy.runAction` 前执行私有 `validate()`，包含完整单据校验和 `beforeCheck`。试点接入必须执行这些校验，并保留可恢复异常的处理与重试语义。
- 本机 `UIF2BeanFactory.loadBeanConfig` 优先把配置路径转换为同名 Java 类加载。采购入库现场路径对应的 `PurchaseIn_Org` 继承 `PurchaseInAction`，原 `getSaveAction()` 构造并缓存 `PurchaseInSaveAction`，从 UI 工厂获取 model、editor model、card 和 validateService。现场还配置了 `ext_PurchaseIn_Org.xml` 与 `m45limis.xml` 两个插件；这些配置及其加载顺序需在客户端验证。

## 库存快照候选实现

库存类型化适配器调用现已在业务事务内采集采购入库和销售出库的持久化表头、表体、货位及单品行。更新、删除和签字前读取并锁定旧数据，新增和更新等操作后读取真实结果；删除后核对表头的有效状态。采集核对集团、业务单元、单号以及表体和货位的父子关系。新增使用实际返回的主键。

IBM JDK 7 隔离构建已编译 public、private、client、示例及测试层。新增库存快照测试覆盖两种单据的新增、修改、删除、权限归属、单号及货位关系。测试使用数据替身，Oracle 表结构、物理锁、事务回滚和真实客户端入口仍需现场验证。标准构建的离线测试曾全部显示通过，但最终输入复核检测到变化，未生成有效构建凭据。共享 `build/classes` 在检查期间被清空；隔离目录测试独立完成。

后续一份源码隔离快照中的库存离线测试通过，完整套件曾在 `SaleOrderPfActionAdapterTests.approveSuccess` 失败，提示销售订单审批未按测试预期调用原生 PF。2026-11-11 22:14 的另一份隔离源码快照随后通过完整离线编译、测试及快照输入复核，记录位于 `build/inventory-source-snapshot-20261111-221425/build/build-result.json`。该隔离记录对应快照内的源码和调整为绝对路径的库配置，不是当前工作区的正式打包凭据。其后库存状态定义读取增加了错误处理，保存接入增加了目标组织字段校验；这些结果均不构成 NC 联调证据。

2026-11-11 22:28 新的隔离源码快照也通过完整离线编译、测试及输入复核，记录位于 `build/inventory-source-snapshot-20261111-222854/build/build-result.json`，包含上述库存加固和模块／网关路由一致性测试。之后共享工作区的其他模块源码继续变化，不能将该快照凭据用于当前工作区打包；预检脚本后来增加了共享批次档案的字段检查，仍未执行。

库存快照使用六张表各自的字段白名单，来源为本机 NC 单据元数据及 2026-11-07 QDNC 四张表的只读字段快照。读取器继续排除凭据类字段，遇到未列入白名单的非敏感物理列会终止采集，避免遗漏新增字段。采购入库与销售出库的表头、表体字段已与历史数据库快照逐列比对；两张货位表的现场物理列尚待数据库核对。

快照采集还核对表头实体及主键、表体和货位实体及行主键、父子关系；查询结果集缺失或删除后核验结果缺失时失败关闭。聚合时复制读取结果，避免依赖读取器返回可修改列表。上述异常和只读列表场景已用离线替身覆盖，不能替代现场数据库核对。

历史数据库快照中有 `BC_03`、`BC_04` 和销售出库表体的 `NORIGTAX`，本次按现场已有物理列纳入候选白名单，字段业务含义及扩展归属仍需核对。`InventoryAuditFieldsTests` 已离线验证身份字段、敏感字段排除和未知列拦截。

本机 BMF 和 NC65 字典显示两类单据表体的 `pk_batchcode` 引用共享批次档案 `scm_batchcode`。单张删除且审计开启时，候选实现按表体中的有效批次主键去重、按授权集团筛选并在删除前锁定采集档案；引用缺失、集团不符或未审核的物理字段会使操作失败关闭。空值和 NC BMF 中的默认占位符 `~` 不表示有效批次引用；被引用档案若已逻辑删除，仍保留其实际状态作为事实。批次字段及自定义项按本机元数据白名单采集，`dr`、`ts` 属 NC 通用约定，现场物理列和共享范围仍待确认。

批次档案以 `reference:scm_batchcode` 的 `BEFORE` 引用快照单独保存在同一审计对象下，单据表体的 `pk_batchcode` 保留其关联；引用快照不参与单据行的新增／删除差异，也不表示共享档案随单删除。审计界面将其显示为“删除前引用 / 批次档案”。单张新增、修改、签字和取消签字仍以单据表体中的批次字段记录差异，本批未声称采集这些动作对共享批次档案的前后变化。

IBM JDK 7 离线测试覆盖同批次多行去重、集团隔离、缺失引用与未知字段拒绝、按 900 个主键分段的参数化查询、审计写入异常传播及引用快照展示。2026-11-11 23:48 独立源码快照通过全层编译、完整离线套件、输入冻结及 `-VerifyRecord`，记录位于 `build/inventory-source-snapshot-20261111-234859/build/build-result.json`；库存引用相关源码核对时与工作区一致，范围矩阵随后补充了复杂动作的本机入口。该凭据属于独立快照，不能作为当前工作区正式打包凭据。测试使用替身，没有连接 Oracle 或 NC；审计写入异常是否连同单据、库存及生成单一起回滚仍须现场故障注入验证。

## PF 保存候选实现

新增本地 `IInventoryPfSaveAdapter`，通过原生 `IplatFormEntry.processAction` 的 `WRITE` 入口处理采购入库及销售出库单笔新增和修改。适配器使用 `ICBillVO` 和 `ICBillHeadVO` 的共同字段，校验节点、授权对象、组织、交易类型及原生 `ICPFParameter` 的新增或修改标记；业务后核对真实返回值及持久化单号。服务端网关和库存快照已识别该候选适配器。

`InventoryPfSaveAdapterTests` 在 IBM JDK 7 下使用原生 VO 子类和 PF 服务替身通过，覆盖两种单据、PF 原生参数传递、返回包装、越权及结果不一致。测试未连接 NC 或 Oracle。客户端最终调用、可恢复异常重试、现场策略注册与事务回滚仍需实际环境验证，节点策略尚未通过本批启用。

## 客户端保存候选

采购入库候选 `QdPurchaseInConfig` 继承原 `PurchaseIn_Org`，销售出库候选 `QdSaleOutConfig` 继承原 `SaleOut_Org`。两者分别创建原生保存动作的子类，共用 `InventorySaveCoordinator`，保留各自的校验、流程上下文、可恢复异常处理及结果处理。配置路径分别为 `nc/ui/uapbd/qd/esign/QdPurchaseInConfig.xml` 和 `nc/ui/uapbd/qd/esign/QdSaleOutConfig.xml`；NC 的 `UIF2BeanFactory` 会按路径优先加载对应 JavaBeanDefinition 类。

库存节点的业务单元策略执行已加入活动事务检查及显式回滚标记。服务端只有在回滚标记确认且失败结果成功记录后才向客户端恢复原生可继续异常；客户端保留原生处理，并为后续尝试准备新的签名请求。无法确认事务结果时保留 `UNKNOWN` 状态。上述逻辑通过离线替身测试，仍需 NC 事务与客户端实测。

两种保存候选当前要求单据卡片的一次 PF `WRITE`。带前后置复合动作的配置、原生流程返回加签或指定审批人的分支，需要完成专门的流程接入与验证后覆盖。现场插件、货位表字段、业务字段含义和四种策略组合仍待验证，候选配置及映射尚未在本批部署或启用。

## 删除和库存签字候选

采购入库、销售出库各增加删除、签字、取消签字的原生动作子类，并由同一节点候选配置保留原动作的模型、卡片、列表和校验绑定。单张动作使用同一次性授权交接与原生 PF `DELETE`、`SIGN`、`CANCELSIGN` 入口，服务端核对节点、按钮、对象类型、集团、业务单元和单据主键，业务后核对删除标志或 NC 原生签字状态，并在审计开启时采集表头、表体及货位前后快照。原生可恢复异常在服务端确认回滚标记并保存失败结果后，客户端为重试准备新的授权。

目前这些动作只覆盖单张单据。多选受控操作会明确拒绝，不会伪称批量成功；原生批量部分成功、一次验密及逐单事务/审计关联尚待实现和实测。`InventoryPfStateAdapterTests`、`InventoryStateHandoffTests`、`InventoryStateClientConfigTests` 和 `InventoryRetryAuthorizationTests` 已在隔离目录使用替身通过；状态值、真实 PF 返回形态、原生授权异常、按钮挂载和事务边界仍须在 QDNC 验证。所有映射仍未部署或启用。

删除候选在调用原生 PF 前固定服务端读取的持久化单号，并用该单号回填结果；客户端传入的单号不会覆盖该事实。持久化单号缺失时在原生删除之前失败关闭。该检查及客户端单号不一致场景已用离线替身验证，现场并发和事务仍需验证。

2026-11-12 重新检查本机删除调用顺序时发现：原候选在 `NCAction.actionPerformed` 的前置拦截阶段签名，而 `DeleteScriptAction.doAction` 随后才显示删除确认框；取消确认会被误报为 `DISPATCH_NOT_OBSERVED`，且可能已留下签名记录。十三个库存删除候选现均保留原拦截器的前置及结果回调，调用同一个原生删除确认框，确认后先执行原生数据权限检查，通过后才准备签名，再经原生 `doSuperAction` 复核数据权限并进入业务流程。确认取消或首次权限拒绝均不创建签名请求，也不调用删除流程；一次事件只允许一次确认，重入及直接绕过动作入口均被拒绝。

该桥接依赖本机 `DeleteScriptAction`、`ScriptPFlowAction`、`GeneralDeleteAction` 及相应销售／货位删除子类的固定字节码，版本不同将失败关闭，开关关闭时也需要匹配该本机版本。桥接会拒绝已知拦截链中重复安装的签名拦截器。签名期间权限被撤销时，原生复核显示原有拒绝提示；受控请求按业务失败结束，不作为未观察到派发的适配错误。无法观察到复核或出现重复复核时失败关闭，受控派发前也要求复核通过。离线测试覆盖确认取消、真实签名生命周期的四种开关组合、首次拒绝及复核拒绝、复核缺失、签名取消、原拦截器短路、原生异常、重复触发和回调顺序；权限、服务及派发均使用替身，未运行真实客户端。两次权限检查的现场语义、性能、实际拒绝提示和 PF 其他确认及插件仍须在 QDNC 核实，未验收前相关删除候选不得启用。

2026-11-12 02:28 的隔离源码快照通过 IBM JDK 7 全层编译、完整离线测试及 `-VerifyRecord`；测试包含货位调整、删除确认时序与服务端单号校验。核对时 69 个库存相关 Java 源码／测试与当前工作区一致，货位调整预检和范围矩阵也一致；构建记录为 `build/inventory-source-snapshot-20261112-022800-current/build/build-result.json`。该记录属于隔离快照，不代表当前工作区的正式打包或 NC 验收。

2026-11-12 02:54 的隔离快照通过 IBM JDK 7 全层编译、完整离线测试及 `-VerifyRecord`，包含首次权限拒绝和签名后权限复核的候选修复；记录为 `build/inventory-source-snapshot-20261112-025449-permission/build/build-result.json`。其他入库候选是在该快照之后新增的，不能用该记录证明其完整构建。

2026-11-12 03:07 的隔离快照通过 IBM JDK 7 全层编译、完整离线测试及 `-VerifyRecord`，含其他入库最初单张候选，记录为 `build/inventory-source-snapshot-20261112-030753-permission/build/build-result.json`。该快照后其他入库调整了部分原生模型获取方式，并新增产成品入库、委托加工入库和其他出库候选；这些后续变化已做针对性离线测试，不属于该全量构建记录。

2026-11-12 03:35 的隔离快照通过 IBM JDK 7 全层编译、完整离线测试及 `-VerifyRecord`，包含修正后的其他入库和产成品入库、委托加工入库、其他出库的单张候选，记录为 `build/inventory-source-snapshot-20261112-033529-permission/build/build-result.json`。材料出库候选、其预检 SQL 和后续矩阵修改发生在该快照之后，不能用该记录证明这些后续输入的全量构建。

2026-11-12 03:47 的隔离快照通过 IBM JDK 7 全层编译、完整离线测试及 `-VerifyRecord`，包含材料出库单张候选，记录为 `build/inventory-source-snapshot-20261112-034734-permission/build/build-result.json`。调拨出库和两个定制入库候选及其预检、矩阵更新发生在该快照之后，仅通过后续针对性离线测试，不属于该全量构建记录。

## 原生批量边界与服务端保护

- 本机 NC 字节码显示，多选经 `MultiBillScriptRunner` 和 `PfUtilClient.runBatchNew` 进入 `IplatFormEntry.processBatch`，并非简单重复单据 `processAction`。平台批量入口调用 `beforeBatch`、`afterBatch` 等专有处理；`PFBusiAction.processBatch` 按单调用名称带 `RequiresNew` 的工作流入口并收集异常。现场事务行为仍需验证。
- 因此，不能用循环调用现有单笔适配器替代原生批量，也不能假定在批量入口外围追加审计就能与每张单据的库存变动同事务回滚。此前逐单分发实验未能保持原生批量语义，已移出正式源码，未注册服务。其离线状态模型与替身测试只能作为研究，不能作为原生批量实现或 NC 验收。
- 在 `SignatureServiceImpl.prepare` 增加服务器端检查：现有十三个库存候选节点的受控单据动作仅接受一个对象，多选在创建请求和验密之前失败关闭；签名和审计均不生效的原生 BYPASS 不受此数量限制。客户端单张交接和业务适配器也继续执行各自的对象数量检查。
- 本机库存签字、取消签字及删除模板包含前后业务事件规则；`EventDispatcher.fireEvent` 的本机字节码直接调用已匹配监听器。来源 ID 从单据元数据取得。事件只能作为进一步研究的候选：实际节点是否命中事件、监听器注册和顺序、跨工作流调用的请求身份传递、单据事务与审计同事务、缺失事件时如何在提交前阻断，均需在 QDNC 证明。尤其批量后置处理的影响及局部提交后的回执恢复不能凭离线结果推断。
- 本机批量实现为每个单据复制扩展参数，并在 `RequiresNew` 路径的 `PfParameterVO` 中运行配置的前后 PF 约束；后约束不直接获得原生业务返回对象。现有函数表达式的常规参数尚未证明可安全读取签名请求并跨服务进程可靠传递；事件监听器缺失也可能静默跳过。`beforeBatch` 可改变对象顺序或数量，批量异常索引与返回数组不能单独证明每笔事务已提交，因此不能把它们直接当作逐单审计成功回执。以上仅是字节码线索，不能证明现场事务传播或约束配置。

本机 `ICEventType` 为删除、签字、取消签字分别定义前／后事件 `1005`／`1006`、`40080007`／`40080001`、`40080008`／`40080002`。本机 BMF 中采购入库与销售出库的表头实体 ID 分别为 `108cea13-88ce-430c-a772-1b9ed397d401` 和 `38a23a56-0686-4959-b094-16519b5bcb13`。这些仅是现场核对时的候选值；实际运行时的事件来源、订阅和回滚行为尚未验证，也未注册监听器。

2026-11-11 23:24 的独立源码快照使用 IBM JDK 7 完成全层编译、完整离线套件及构建输入冻结复核，随后 `-VerifyRecord` 通过；记录位于 `build/inventory-source-snapshot-20261111-232428/build/build-result.json`。测试包括本批受控数量保护、库存快照、客户端交接和单笔 PF 适配器。上述库存保护和测试源码在核对时与工作区一致；范围矩阵随后补充了调拨入库本机入口线索。该记录属于独立快照及其调整后的库路径，不是当前工作区的正式打包凭据，也不是 NC 验收结果。

## 后续标准单据的本机元数据线索

下列聚合 VO 均继承 `ICBillVO`。本机 NC 安装包中的维护接口具有类型化新增、修改、删除、签字及取消签字方法；这只证明接口存在，不能证明现场按钮最终调用该接口。表名前缀来自本机 BMF 表头、表体、货位映射，现场物理列及扩展字段仍需核实。

| 节点 | 聚合 VO | 候选维护接口 | 本机表前缀 |
|---|---|---|---|
| 期初余额 | `InitializtionInVO` | `IInitializtionInMaintain` | `ic_openbal` |
| 调拨入库 | `TransInVO` | `ITransInMaintain` | `ic_transin` |
| 产成品入库、投产单 | `FinProdInVO` | `IProductInMaitain` | `ic_finprodin` |
| 其他入库 | `GeneralInVO` | `IGeneralInMaintain` | `ic_generalin` |
| 委托加工入库 | `SubcontInVO` | `ISubcontInMaintain` | `ic_subcontin` |
| 调拨出库 | `TransOutVO` | `ITransOutMaintain` | `ic_transout` |
| 材料出库 | `MaterialOutVO` | `IMaterialOutMaintain` | `ic_material` |
| 其他出库 | `GeneralOutVO` | `IGeneralOutMaintain` | `ic_generalout` |

## 调拨入库单张候选

本机 `uiic_inbound.jar` 中 `TransIn_Org` 继承 `GeneralCommon` 的配置链；`GeneralButtonAction` 分别提供 `GeneralSaveAction`、`GeneralDeleteAction`、`GeneralSignAction` 和 `GeneralCancelSignAction`。候选配置 `QdTransInConfig` 继承原配置并替换四个动作，分别沿用这些原生动作的模型、表单、列表及校验绑定；节点挂载和现场插件加载顺序仍待验证。

单张保存、删除、签字和取消签字复用现有类型化 PF 适配器与客户端交接。调拨入库采用 `TransInVO`／`TransInHeadVO`，复用库存单据及表头公共基类；固定节点 `40080618`、对象类型 `IC_TRANS_IN` 和本机原生单据类型 `4E`，并检查单据集团、组织、交易类型、主键和实际结果。服务端在原生调用前后核对持久化交易类型；只有尚未持久化的新单据可暂时缺少类型。受控多选、复合前后置流程和指定审批人分支仍不开放；不受控策略保留原生路径。

PF 候选现在可校验带后缀的交易类型：必须从表头取得类型主键，按主键查询并锁定 `bd_billtype`，严格核对编码、集团、节点、父类型、组件和删除状态，再在原生调用后核对返回及持久化表头。调用前固定类型主键，避免原生代码原地修改 VO 后掩盖身份变化；不同主键即使编码相同也不能替代。基本类型若有类型主键，也核对该记录；旧直连适配器仍只接受基本类型。现场的共享集团、节点和组件表示尚未确认，未满足精确匹配会阻断，不能视为支持所有后缀。`FOR UPDATE NOWAIT` 会使同一类型的并发受控业务发生锁冲突并立即失败，须在现场测量并解决吞吐量和配置维护影响后才能启用。

本机 BMF 中表头 `pk_org` 的参照是库存组织（`stockorg`），它与策略中的业务单元是否相同或如何对应尚未在现场确认。候选实现目前要求两者主键一致，不满足即阻断，不应在核实组织关系前启用。调拨入库可能涉及源单及库存副作用，目前快照只覆盖本单及其批次引用，源单关联与业务事务边界仍待实测。

`ic_transin_h/b/l` 的字段白名单来自本机 BMF，分别覆盖 58、208、58 个元数据字段；`dr`、`ts` 及表体／货位的连接字段按 NC 结构约定加入，未审核的现场物理列会阻断审计。删除候选同样采集表体关联的批次档案引用。现场字段、跨单据副作用、事务回滚和四种开关组合尚未验证，候选配置和映射未部署或启用。

2026-11-12 00:26 的独立源码快照通过 IBM JDK 7 全层编译、完整离线测试及 `-VerifyRecord`；45 个相关库存 Java 源文件／测试与当时工作区哈希一致，记录为 `build/inventory-source-snapshot-20261112-002607/build/build-result.json`。本次构建之后只读 SQL 增加了交易类型参数查询，验收证据工具和台账也独立增加；它们分别通过静态检查和工具离线测试，不属于该快照的构建输入。隔离记录不是当前工作区正式部署凭据或 NC 验收结果。

## 期初余额单张候选

本机 `initializtionin_org` 继承库存通用配置链；`InitializtionInVO` 和 `InitializtionInHeadVO` 分别继承库存聚合单据和表头基类，原生单据类型为 `40`。候选 `QdInitialInConfig` 及四个动作限定节点 `40080402`、对象 `IC_INITIAL_IN`，复用单张 PF 委托和实际主键回填；`ic_openbal_h/b/l` 字段白名单来自本机 BMF，分别覆盖 50、155、58 个元数据字段，连接列及 `dr`、`ts` 仍属待现场核实的结构约定。

该节点还单独配置 `OpenbalImportSaveAction`、`BatchSaveAction` 和 `BatchSignAction`。本机字节码中导入保存动作的 `doAction` 不直接写入；`OpenbalImportAction` 异步启动 `DataImporter`，其调用 `OpenBalImportablePanel.setValue`，后者转换单据后直接调用 `IInitializtionInMaintain.saveForImport`。这一导入写入入口未接签名和审计，批量动作也使用独立流程。当前候选仅覆盖普通单张入口，这些其他入口会绕过或可能绕过候选动作，必须在启用前接入并验证，不得将本节认定为节点级覆盖。库存组织、单据状态、类型字段与事务同样需要现场验证。

本机服务端 `saveForImport` 返回 `void`；其 `ImportAction` 检查并填充默认值后调用 `InsertAction.insert`，随后丢弃返回数组。因此不能靠该接口的返回值回填真实主键，需在原生事务和导入过程内确定可靠的持久化身份、失败处理及批量关联方式，再接入审计。

本机字节码显示 `InsertActionTemplate` 的 `m40` `InsertAction` 后置规则收到实际新增返回数组，且 `DataImporter` 在每条数据的 `setValue` 阶段就写入，随后才调用无实际保存动作的 `save`。已新增一个**未注册、未接入网关**的后置捕获候选：仅在同线程、同事务且明确标记为 `IMPORT` 的受控作用域内接受一张原生返回单据；缺失、重复、跨集团或事务变化时拒绝，普通原生操作不受该类影响。捕获失败或未完成关闭会尝试将当前事务标记回滚，标记失败作为附加异常保留；已关闭或跨线程的旧作用域不会标记其他线程的事务。后续还需在签名前绑定导入批次及各对象、为逐单结果建立不可重放状态、验证事务内真实持久化身份并写入审计；`IMPORT` 当前并未加入服务映射，不能用于实际导入。

NC 的规则加载器从 `pub_pluginitem` 读取扩展点并在服务进程内缓存；只读预检已列出 `IC/m40/InsertAction` 的配置。必须核对各服务进程实际加载的规则、扩展顺序、线程与 CMT 事务传播，以及规则缺失或不同事务时不会造成独立提交。捕获规则不等于审计完成，不能据此启用期初节点。

将来接入服务端时，调用方须先验证签名请求与导入单元，再以 `try-with-resources` 管理捕获作用域，调用 `finish` 后按捕获主键重读并核对持久化单据及类型，最后在同一业务事务写审计。未完成或已污染的作用域关闭时会抛错并尝试标记回滚；调用方不得吞掉该异常。该约束不能替代现场注入失败、事务传播和批量部分成功测试。

2026-11-12 01:35 的独立源码快照通过 IBM JDK 7 全层编译、完整离线测试及 `-VerifyRecord`；58 个相关库存 Java 源码及测试文件与当时工作区一致。记录为 `build/inventory-source-snapshot-20261112-013536/build/build-result.json`。此后矩阵只补充货位调整的本机入口线索；该隔离记录不代表当前工作区正式打包或现场验收。

2026-11-12 00:42 的独立源码快照通过 IBM JDK 7 全层编译、完整离线测试与 `-VerifyRecord`；53 个相关库存 Java 源码及测试文件与当时工作区一致。记录为 `build/inventory-source-snapshot-20261112-004258/build/build-result.json`。矩阵和只读预检说明后来补充了导入路径缺口，隔离记录不是当前工作区正式打包凭据或 NC 验收结果。

## 货位调整单张候选

货位调整 `40081014` 的保存、删除增加候选客户端配置及原生动作子类，复用 `ICBillVO`／`ICBillHeadVO` 公共基类和单张 PF 适配器；对象类型为 `IC_LOC_ADJUST`，本机基础单据类型为 `4Q`。保存沿用原生 `GeneralSaveAction` 的模型、编辑器和 `WRITE` 绑定，不附加原配置没有设置的校验服务；删除继承 `LocAdjDeleteAction`，保留其专门的可用性判断。网关和库存适配器仅接受本节点清单中的保存、删除，不放开签字或取消签字。

候选快照包含 `ic_locadjust_h/b/l` 的表头、表体和货位行；删除前采集实际引用的共享批次档案。三张表的白名单已与本机 BMF 的 38、110、56 个物理字段逐列核对，另加入 `dr`、`ts` 和候选关联列。表体的 `cgeneralhid` 与货位的 `cgeneralbid` 不在本机 BMF 物理属性中，仍须在现场数据库和实际单据中核实；未审核的新增物理列会阻断审计。本机 `LocAdjustBodyVO.getLocationVOs()` 返回货位明细，但这不能证明现场数据库外键及数据完整性。

本机 `getImportExportActionFor4Q` 菜单仅列出 Excel 和 XML 两个导出动作；现场插件和其他写入入口仍需核对。`sql/nc65-electronic-signature/inventory-pilot/locadjust-preflight.sql` 提供只读列检查及按指定在用单据核对关联行数的查询，后者须先确认物理列并与客户端逐行对照。库存组织与策略业务单元的关系、原生 PF 返回形态、库存／货位及序列号副作用、删除回滚、四种开关和受控批量均未现场验证；候选配置未注册、未部署或启用。

## 其他入库单张候选

2026-11-07 现场节点快照记录 `40080608` 使用 `nc/ui/ic/m4a/GeneralIn_Org.xml` 及 `m4alims.xml` 插件。本机 `GeneralInVO`、`GeneralInHeadVO` 继承库存聚合单据和表头基类，货位 VO 继承 `ICLocationVO`，本机基础类型为 `4A`。候选 `QdGeneralInConfig` 及四个动作限定节点 `40080608` 和对象 `IC_GENERAL_IN`，复用单张 PF 委托、身份核对和结果回填；`ic_generalin_h/b/l` 白名单分别覆盖本机 BMF 的 54、191、58 个物理字段，另加 `dr`、`ts` 及候选表体／货位关联列，现场必须先核对。

本机 `GeneralIn_Org` 的保存动作使用专用 `DefaultExceptionHanlerFor4A`；签字动作是 `GeneralInSignAction`，其可恢复异常分支可能提示并修改 `isCheckOutFor4M`。候选保留该异常处理器及签字子类，但提示、用户选择、重试与事务回滚的完整行为仍需 QDNC 测试。节点还配置独立 `BatchSaveAction`、`BatchSignAction` 并供来源单据的出入库代理调用；这些路径未接入，不能将普通单张候选视为节点完整覆盖。来源单据、可能关联的其他出库单及其库存副作用尚未建立同事务审计与主键关联，现场插件及单据生成行为也未验证；未完成前不得启用。

`GeneralInPfAdapterTests`、`GeneralInClientConfigTests` 及共享快照／路由测试已在 IBM JDK 7 使用本机类与替身通过；没有连接 NC 或 Oracle。`generalin-preflight.sql`、`verify-generalin-request.sql` 是待执行的只读预检和请求核对脚本，不能作为实际字段或事务证据。

## 产成品入库单张候选

本机 `ProductIn_org` 继承库存通用配置链，`FinProdInVO`／`FinProdInHeadVO` 继承库存单据公共基类，货位 VO 继承 `ICLocationVO`。候选 `QdProductInConfig` 及四个动作限定普通产成品入库节点 `40080604`、对象 `IC_PRODUCT_IN` 和本机基础类型 `46`，复用单张 PF 委托与服务端主键、交易类型核对。`ic_finprodin_h/b/l` 白名单覆盖本机 BMF 的 58、224、57 个字段，并按结构约定补入 `dr`、`ts` 及候选关联列，现场物理字段和关系仍须核验。

投产单 `40080604400` 与本节点共用 `FinProdInVO` 及 `ic_finprodin` 表，但现场历史类型为 `46-Cxx-2`，使用单独的客户端候选和授权对象，不能借用普通产成品入库授权。受控子类型必须以锁定的 `bd_billtype` 主键核对集团、节点、父类型及组件；实际元数据和共享集团语义仍需现场核验。2026-11-07 快照显示普通节点配置了 `ext_delivery_config.xml` 插件；插件的动作覆盖、其他写入入口、源单／生成单和库存副作用尚未核实，多选受控路径未实现，未完成前不得启用。

`ProductInPfAdapterTests`、`ProductInClientConfigTests` 和共享路由／快照测试已在 IBM JDK 7 使用本机类和替身通过，未连接 NC 或 Oracle。`productin-preflight.sql` 同时列出普通节点与投产单节点的交易类型参数供现场比较；该脚本和请求核对脚本均未执行。

## 委托加工入库单张候选

本机 `subcontin_org` 继承库存通用配置链；`SubcontInVO`／`SubcontInHeadVO` 使用库存单据公共基类，货位 VO 继承 `ICLocationVO`。候选 `QdSubcontInConfig` 及四个动作限定节点 `40080606`、对象 `IC_SUBCONT_IN`、本机基础类型 `47`，复用单张 PF 与实际身份核对。`ic_subcontin_h/b/l` 字段白名单覆盖本机 BMF 的 56、206、58 个字段，另按结构约定补入 `dr`、`ts` 和候选关联列；现场列及关联关系尚未验证。

本机节点为来源单据路径配置了独立 `BatchSaveAction`、`BatchSignAction`，这些入口以及多选一次验密、逐单审计尚未实现。该节点与定制委外加工入库 `40080602400`、类型 `45-Cxx-08` 是不同节点和类型，离线测试阻止跨节点／跨类型借用授权。来源单据及其库存副作用、其他写入入口、现场客户端和事务回滚均待核实，候选未注册或启用。

`SubcontInPfAdapterTests`、`SubcontInClientConfigTests` 及共享快照／路由测试在 IBM JDK 7 使用本机类与替身通过，未连接 NC 或 Oracle。`subcontin-preflight.sql` 和请求核对脚本为只读候选，尚未执行。

## 其他出库单张候选

本机 `GeneralOut_Org` 继承库存通用配置链；`GeneralOutVO`／`GeneralOutHeadVO` 使用库存单据公共基类，货位 VO 继承 `ICLocationVO`。候选 `QdGeneralOutConfig` 及四个动作限定节点 `40080808`、对象 `IC_GENERAL_OUT`、本机基础类型 `4I`。保存动作保留原生 `DefaultExceptionHanlerFor4A` 和原模型获取方式；其他状态动作保留通用配置的模型、卡片与列表绑定。`ic_generalout_h/b/l` 白名单覆盖本机 BMF 的 58、202、58 个字段，另按结构约定补入 `dr`、`ts` 和候选关联列，现场列及关联关系尚未验证。

本机节点供来源单据和特殊出入库代理调用，另配置 `BatchSaveAction`、`BatchSignAction`；独立批量入口、多选一次验密及逐单审计尚未实现。来源／生成单据及库存副作用、其他写入入口和现场客户端、事务回滚仍待核实，候选未注册或启用。`GeneralOutPfAdapterTests`、`GeneralOutClientConfigTests` 和共享路由／快照测试在 IBM JDK 7 使用本机类与替身通过；`generalout-preflight.sql` 和请求核对脚本为只读候选，均未在 QDNC 执行。

## 材料出库单张候选

本机 `MaterialOut_Org` 继承库存通用配置链；`MaterialOutVO`／`MaterialOutHeadVO` 使用库存单据公共基类，货位 VO 继承 `ICLocationVO`。候选 `QdMaterialOutConfig` 及四个动作限定节点 `40080804`、对象 `IC_MATERIAL_OUT`、本机基础类型 `4D`。取消签字继承原生 `nc.ui.ic.m4d.action.CancelSignAction` 并保留该节点专用的模型绑定。聚合 VO 的 `getMetaData()` 返回具体 `MaterialOutMeta`，离线类型化适配测试使用不连接元数据服务的显式替身，不能替代真实 NC 元数据测试。

`ic_material_h/b/l` 白名单覆盖本机 BMF 的 63、250、58 个字段，另按结构约定补入 `dr`、`ts` 和候选关联列；类型元数据组件为 `ic_materialout`，与物理表前缀不同。现场列、关联关系及交易类型仍须核实。2026-11-07 快照显示节点有 WMS 和导出插件；其加载顺序、其他写入入口、批量、多单关联及库存事务尚未验证，候选未注册或启用。

`MaterialOutPfAdapterTests`、`MaterialOutClientConfigTests` 和共享路由／快照测试在 IBM JDK 7 使用本机类与替身通过；`materialout-preflight.sql` 和请求核对脚本均为只读候选，未在 QDNC 执行。

## 调拨出库单张候选

本机 `AllocationOut_org` 使用专用 `TransOutSaveAction`、`TransOutSignAction`；候选子类保留原生容差／ATP 可恢复异常处理，签字保留 `filledUpInFlow=false` 和原有卡片、列表绑定。`TransOutVO`／`TransOutHeadVO` 继承库存单据公共基类，候选限定节点 `40080820` 和对象 `IC_TRANS_OUT`。`ic_transout_h/b/l` 字段白名单覆盖本机 BMF 的 63、232、58 个字段，另按结构约定补入 `dr`、`ts` 和候选关联列，现场列及关系尚未验证。

本机节点 XML 和 Java 配置使用基础类型字符串 `4y`，库存类型枚举和 2026-11-07 现场单据类型快照使用 `4Y`；原生 `ScriptPFlowAction` 又可从单据读取交易类型填入流程。当前候选严格接受 `4Y` 及经核验的 `4Y-...` 子类型，未验证的 `4y` 会失败关闭，不进行大小写归一。现场历史快照中基础类型的集团为 `~`，子类型同时存在全局和本集团记录；当前候选要求类型集团与授权集团一致，选择共享类型记录时可能阻断实际业务，需核实授权语义并解决后才能启用。实际 PF 参数、原生恢复分支、共享集团、批量和关联单据／库存事务均未现场验证。

`TransOutPfAdapterTests`、`TransOutClientConfigTests` 和共享路由／快照测试在 IBM JDK 7 使用本机类与替身通过；`transout-preflight.sql` 可分别列出 `4Y` 和 `4y` 元数据，脚本尚未在 QDNC 执行。

## 定制入库单张候选

委外加工入库 `40080602400` 使用 `PurchaseInVO` 及采购入库原生动作子类，授权对象单独定义为 `IC_OUTSOURCE_IN`，只接受历史现场类型 `45-Cxx-08`；投产单 `40080604400` 使用 `FinProdInVO` 和产成品入库原生动作子类，授权对象单独定义为 `IC_PRODUCTION_ORDER`，只接受 `46-Cxx-2`。两者均不得使用基础类型或其他子类型，且必须按单据类型主键锁定 `bd_billtype` 并核对集团、节点、编码、父类型、组件及有效状态。新增草稿若没有可核验的类型主键会失败关闭，实际草稿赋值时机须在现场测试。

两个节点分别复用各自单据的表头、表体、货位及删除前批次引用快照；共用物理表不意味着共用授权。`CustomInventoryPfAdapterTests`、节点客户端绑定和共享快照／路由测试已使用本机 VO 与替身通过，覆盖新增结果回填、修改、删除、签字、取消签字及跨节点、类型、集团拒绝。历史类型及集团可能变化，字段与组织关系、现场插件、源单／生成单、批量及事务回滚均未现场验证，候选未注册或启用。`custom-in-preflight.sql` 和请求核对脚本只读，尚未执行。

## 后续复杂动作的本机入口线索

### 出库申请保存、删除单张候选

出库申请单 `40080801` 的 `SapplyBillVO` 与 `SapplyBillHeadVO` 继承库存单据公共基类，本机基础类型为 `4455`。`QdSapplyConfig` 保留原生 `SapplyBill_Org` 配置、通用保存／删除动作、模型、编辑器及保存校验服务；单张请求沿用库存 PF、身份回填和删除确认后验签的候选路径。BMF 的三个表体动态字段映射到 `ic_sapply_e`，按表体 `cgeneralbid` 关联；候选快照包含表头、表体、对应动态表以及删除前实际引用的共享批次档案。

QDNC 只读元数据显示出库申请表头主键及表体的单据关联键为 `CHAR(20)`；保存、状态操作及行操作的服务端读取现统一校验主键并为短值右补空格，表体查询使用参数绑定。DAO 替身测试覆盖短主键、20 位主键、非法输入和参数绑定；真实 DAO、Oracle 比较及不同站点字段类型仍需现场核实。

客户端候选路径为 `nc/ui/uapbd/qd/esign/QdSapplyConfig.xml`。本机 `UIF2BeanFactory.loadBeanConfig` 离线加载该路径后，工厂实际登记 `QdSapplyConfig` Java 配置类；这是本机版本的类加载证据，并不证明现场节点已挂载或各插件兼容。部署前须备份原节点配置和插件顺序，现场确认后再试用。

2026-11-12 对 QDNC 做了只读结构核对：`ic_sapply_h/b/e` 分别有 54、96、6 个物理列，均在当前白名单中；动态表 `cgeneralbid` 是主键，但未发现数据库外键。未指定排序的前 1,000 条有效表体样本均有对应有效动态行，不能由此推断所有单据均完整。交易类型存在集团和全局配置，类型共享、业务单元、实际插件和事务语义尚未验证。只读查询不等于 NC 业务联调。

出库申请卡片的 `savecommitAction` 组合原生保存与提交。两步各自执行策略和业务调用。保存成功并返回单据后，提交签名范围和最终 PF 入参均核对集团、组织及主键。保存取消时停止。提交取消或失败时保留已保存结果。专项离线测试覆盖配置绑定、原生结果包装、取消、错误选中和当前单据回退。现场需验证实际插件和事务行为。单张提交和收回使用 PF `SAVE`／`UNSAVE`。提交前要求自由态，提交后允许审批中或原生直接审批完成，收回要求审批中转自由态。`sapply-preflight.sql` 和 `verify-sapply-request.sql` 待现场执行，早期结构核对使用等价的部分只读查询。

受控普通删除、提交、收回、审批和取消审批的批量候选已连接客户端和服务端。审批在原生确认后一次授权绑定整批选择、预校验排除范围、意见和流转选择；单张及批量审批保留原生 PF 动作附带的当前用户，服务端核对为签名用户。未通过原生预校验的单据不进入 PF，其他单据发起一次 `IplatFormEntry.processBatch`；只剩一张时仍沿用原生批量入口。两处切面核对提交对象及外层和逐单事务键，逐单沿用 BP 前后规则，在原生调用成功后写入审计与单据回执。结束时区分预校验未提交、原生逐单动作前拒绝、已提交和待核对。测试使用本机 NC AOP 及事务替身，NC 客户端提示、实际权限和事务仍待现场验证；切面和本地服务的登记示例待现场装载。

2026-11-14 只读核对 QDNC 发现，34 条 `DR=0` 的出库申请交易类型记录均配置 `nc.bs.ic.pf.PfBeforeAndAfterActionForIC`；这些记录包含集团、全局及基础类型，未逐条核验启用状态。此前受控批量保护会拒绝该标准回调；当前候选只允许与本机字节码一致的三层标准类，并核对回调生成的原单范围、顺序与组织，将原单参数保留给 NC。八组相关离线测试通过，但未执行实际回调；单据重建、动态锁、审批签名内容及逐单事务均需现场验证，不能据此认定批量已可用。

2026-11-14 批量审批补充：离线测试使用本机 `ServerBillCombinClient` 和 `ICLocationUtil` 模拟数据库原单与客户端单据合并，覆盖原单表头、表体补充字段及货位恢复；原始输入、原单与转换后单据均在逐单原生调用前受校验。`build/targeted/20261114-103702-080-46003827/test.log` 十组相关测试通过，测试元数据和事务均为替身；仍须现场核对真实 VO、回调、动态锁、审批流及失败回滚。

现场验证需覆盖实际 NC 代理的事务顺序和该交易类型配置的 `beforeBatch`、工作流准备及 `afterBatch`。这些回调位于逐单事务之外，且 `beforeBatch` 可以替换待处理单据。三单验证需核对中间单在审计写入失败后回滚，前后两单正常提交，独立签名记录保留。还需核对批次后处理报错后，返回的单据结果与实际提交一致。

### 出库申请审批和行操作单张候选（2026-11-12）

`QdSapplyApproveAction`／`QdSapplyUnApproveAction` 保留本机原生动作继承链，受控的单张路径先执行 NC 审批准备与确认，再发起电子签名并调用 PF。审批授权摘要覆盖单据表头与表体的已使用字段、审批意见、用户对象及流程参数；摘要使用稳定的字段顺序，而不是依赖 NC VO、`HashMap` 的重序列化字节。签名前后以及服务端执行前都校验摘要，内容或选择发生变化即拒绝。无法识别的复杂参数、多个表体、静默审批、委托用户和旁批上下文暂不放行；真实审批任务参数、元数据、审批流及跨 RPC 前后字段是否等价仍需现场验证。审批弹窗最终确认的现场按钮映射尚未取得，不能以源码候选替代现场确认。

本机 `PfUtilClient` 以 `besideapprove` 值非空判断是否进入旁批；因此受控审批客户端在填充原生流程前后及准备结果中检查该参数，准备结果中的空值会在构造签名命令前移除，服务端对审批和取消审批拒绝该未接入参数，包括布尔值。提交等其他动作沿用原有参数规则，策略关闭时继续走原生审批。旁批和共享／委托审批的现场参数及工作流身份还未核实，不能视为已支持。

`QdSapplyRowCloseAction`／`QdSapplyRowOpenAction` 使用本机原生选择方法确定单张单据和所选行；受控业务在服务端重新加载表头及全部表体，仅提交授权行至 `ISapplyBillMaintain.rowClose/rowOpen`。原生 BP 比较规则和持久化重读分别核对授权行，服务端还检查未选中的行状态及表体集合未被意外改变。未选中行的空状态可原样保留，空值与未关闭状态分别比较；选中行状态为空时仍拒绝。独立签名与审计四种开关、取消、重复触发、选择变化、未选中行变化和签名绑定在离线替身测试中通过；多单批量、预算提示的续做、现场空状态及物理关联仍待完成或核验。

2026-11-14 QDNC 只读查询确认，行关闭／打开前后四个事件的数据库主键均无监听器登记；这不排除其他调用途径或运行时扩展。`IC_SAPPLY_H` 存在启用的更新触发器，源码包含状态变更写入关联表的分支，未检索到自治事务声明或 COMMIT。本机行关闭 BP 的后置规则还包含采购回写和条件调用项目物资服务。首次业务异常后的实际回滚、其他调用的事务传播和预算提示续做仍需验证，不能仅凭上述查询自动重试。触发器及既有接口不在本批改造范围。

上述候选继承 `ICBillVO`、`ICBillHeadVO` 与 NC 原生出库申请子类，并对本机用于选择和审批桥接的原生类字节做版本核对。不同 NC 补丁或站点插件可能使受控路径失败关闭；行操作版本检查目前也可能影响策略关闭路径，必须先在测试环境核实。正常动作、来源单据、后台任务和不同用户组织下的策略查询及可恢复异常都不能仅凭离线结果认定兼容。

权限方面，网关检查登录用户对节点、按钮和业务单元的授权，服务端还核对单据集团和组织。本机新建的原生保存、删除、提交、收回、审批及取消审批动作的 `resourceCode`、`mdOperateCode`、`operateCode` 默认均为空；站点配置或插件是否补充逐单数据权限尚未核实。行操作候选额外尝试 `ic_sapply`／`edit` 元数据权限，此资源和动作编码也未在现场确认。须核对现场单据级权限及各入口的服务端执行结果，不能仅以当前单元测试声称逐单越权已覆盖。

### 出库申请 BP 规则候选

本机 `ic_outbound.jar` 的 `BPPlugInPoint` 为以下动作提供规则扩展。`SapplyBpRules` 提供对应的前置／后置规则，均使用库存聚合单据基类，并在实例层校验出库申请 VO 子类。规则通过当前受控请求的事务作用域核对操作、主键、集团和组织；前置规则采集审计前快照，原生 BP 返回后由网关核对规则对及实际主键，再在同一业务事务采集最终快照并写审计。事务变化、规则缺失／乱序、其他单据或规则异常会阻断受控路径并请求回滚；签名记录沿用独立事务。

库存受控网关在业务返回后核对原事务及已登记的数据源：数据源为空、多个或无法识别时标记回滚；该行为已用 NC 事务类替身测试，真实数据源及数据库回滚仍待现场验证。安装规则时须按下表分别注册前置与后置类，并核对每个服务进程的实际加载和顺序；包本身不会自动注册规则。本机 `DbBPExtendConfigLoader` 按模块查询 `PUB_PLUGINITEM` 的语句未过滤 `DR`，因此回退时不能仅凭逻辑删除标记认定规则不会加载；现场版本和缓存行为还须核实。

| BP 扩展点后缀（模块 `IC`、组件 `m4455`） | 规则类后缀（均在 `nc.impl.ic.qd.esign.SapplyBpRules` 内） |
|---|---|
| `nc.bs.ic.m4455.maintain.InsertBP` | `$BeforeInsert` / `$AfterInsert` |
| `nc.bs.ic.m4455.maintain.UpdateBP` | `$BeforeUpdate` / `$AfterUpdate` |
| `nc.bs.ic.m4455.maintain.DeleteBP` | `$BeforeDelete` / `$AfterDelete` |
| `nc.bs.ic.m4455.maintain.CommitBP` | `$BeforeCommit` / `$AfterCommit` |
| `nc.bs.ic.m4455.maintain.UnCommitBP` | `$BeforeUnCommit` / `$AfterUnCommit` |
| `nc.bs.ic.m4455.maintain.ApproveBP` | `$BeforeApprove` / `$AfterApprove` |
| `nc.bs.ic.m4455.maintain.UnapproveBP` | `$BeforeUnApprove` / `$AfterUnApprove` |
| `nc.bs.ic.m4455.maintain.RowCloseBP` | `$BeforeRowClose` / `$AfterRowClose` |
| `nc.bs.ic.m4455.maintain.RowOpenBP` | `$BeforeRowOpen` / `$AfterRowOpen` |

更新、审批和行操作使用比较型规则；新增、删除、提交及收回使用普通规则。受控请求要求同一线程、同一活动事务及完整规则对；直接后台调用在相应按钮策略开启时会被前置规则阻止，策略关闭时沿用原生路径。该控制适用于命中这些 BP 的其他来源调用，可能影响来源单据或自动任务，必须逐条核对接入方式、调用身份和组织后再启用；后台线程或跨进程调用不能依靠线程局部变量传递授权。审批中间环节是否调用 `ApproveBP`、新旧 VO 是否完整、规则实例化／顺序、所有服务进程缓存及 CMT 实际传播仍须实测。若某种调用不经过目标 BP、规则缺失或原生 RPC 使用独立事务，不能认为本方案已经闭环。不得只凭候选规则注册或离线测试启用节点。

不带受控请求的 BP 入口按单据集团、组织与按钮直接读取组织策略，不依赖节点映射是否存在，并要求 NC 服务端上下文的集团一致且数据源非空；该策略查询不再额外加载交互用户档案。对于原生 BP 提供新旧 VO 的动作，同时核对新旧组织的策略，相同组织只查一次。策略开启仍阻断未授权的直接 BP 调用，即使节点映射缺失也不会把有效策略当作关闭；缺少集团／数据源上下文或策略读取失败时仍拒绝。新旧 VO 是否为现场完整权威数据、后台上下文与策略关闭时的兼容性仅做离线测试，需在现场用实际自动任务验证。

- 出库申请审批最终确认与行操作已有单张候选，仍须按上节验证现场窗口、原生可恢复异常及批量。
- 盘点 `AdjustAction` 根据仓库类型分支：一种打开材料出库节点，另一种打开 `ClientUIInAndOut`；不能假设所有盘点调整都经过同一个弹窗。转库直接转出、分步转出和转入使用 `TransClientUIInAndOut`，形态转换生成出入库单使用 `ClientUIInAndOut`。
- 上述通用出入库弹窗的“保存并签字”由 `SaveAndSignAction` 发起，批量对应 `BatchSaveAndSignAction`。本机出／入库代理分别先调用保存动作，再调用签字动作；不能仅凭弹窗按钮名称认定两步处在同一事务。现场最终动作、不同分支和源单／生成单回滚需要逐项验证。
- 库存状态调整的 `StateAdjustAction` 先进入编辑；本机 `StateAdjustSaveAction` 完成校验后调用 `IStateAdjustMaintain.stateAdjust(StateAdjustVO[])`。现场确认按钮挂载、库存对象主键和事务边界仍待验证。

现场历史元数据将投产单交易类型记录为 `46-Cxx-2`，委外加工入库记录为 `45-Cxx-08`。候选分别以各自节点和交易类型限定，仍须现场核对，不能仅根据共同的 VO 或表前缀合并授权范围。

## 环境核对

本次只读查询齐都药业测试库时，DBX 返回 `DATABASE_OUT_OF_SCOPE`，提示连接的数据库 `DB` 不在当前允许范围。故本批未取得当前 QDNC 组织、权限或业务数据，也未执行 NC 业务测试。

2026-11-11 23:57 再次读取 DBX 连接列表，两次均返回 `Transport closed`；因此也无法确认测试连接的授权范围是否已变化。本次没有改用其他通道绕过该限制。

`sql/nc65-electronic-signature/inventory-pilot/` 已准备只读预检和按请求号核对脚本，静态检查确认均为 SELECT，并覆盖首批 8 项、调拨入库 4 项和期初余额 4 项正式 scopeId。货位表和库存组织表的当前现场列仍需先通过脚本第一段核对；脚本尚未在现场执行。
