# 开发任务台账

2026-11-09 组织策略批次：组织参照、按钮明细批量维护、组织总控及 1.1.0 升级脚本已开发但尚未在 NC65 环境验证。QDNC 仍为 1.0.0，现场部署及用例见 [组织策略验证](ORGANIZATION_POLICY.md)。下面保留早期批次的现场结构与离线测试记录。

更新：2026-11-07。已完成首轮离线实现及全量编译，实际批次结果见文末及TEST_REPORT。全部243项业务操作尚未完成接入和验收，不把源码存在或候选配置视为已启用。

允许的状态仅为：已完成并通过实际测试；已开发但尚未在 NC65 环境验证；部分完成；尚未开发；因环境或资料不足而阻塞。

2026-11-14 原生审批短路修正：本机 `PfUtilClient.runAction` 对 `isAddApproverOrAppoint` 返回的结果直接返回原单，不再派发 PF 审批。出库申请候选现在按相同分支刷新原单，不创建审批签名请求或派发审批；服务端单张审批守卫也拒绝将这类工作项当审批命令处理，并校验本机判断辅助类的版本。`build/targeted/20261114-122101-109-23989203/test.log` 六组相关定向测试通过，覆盖加签／指定审批人、三种受控开关组合、服务端拒绝及空工作项的惰性读取。该修正仅避免误派发，不为加签／指定审批人补签或生成其审计，不能回滚弹窗中已经执行的动作；下述签名前副作用缺口仍未修复，受控单张审批候选暂不可启用。未执行真实 NC 操作或更新旧交付包。

补充：`build/targeted/20261114-122322-023-ed6fe7a9/test.log` 两组定向测试在普通及轻量 VO 模式下调用了本机 NC 单张结果刷新代码，核对原单刷新并确认不派发审批；使用客户端模型替身，不代表现场 NC 会话已验证。

2026-11-14 单张审批确认的高优先级缺口：本机 `PfUtilClient.checkWorkitemWhenApprove` 在非旁批路径也会打开 `ApproveWorkitemAcceptDlg`；该对话框的 `onAddAssign` 在关闭前调用 `IApplicationRuntimeAdjust.adjust`，本机 `AddAssignAdjustImpl.adjust` 随后调用 `IWorkflowDefine.saveProcessAfterAdjust`，而 `beforeButtonOperate` 会调用附件上传。当前 `SapplyApprovalCoordinator` 在 `preparation.prepare` 返回后才验签，不能确保这些附加效果在签名及审计后发生。此结论来自本机 NC 类字节码，真实现场版本、功能权限和流程尚未验证；现有受控单张审批候选不得据离线结果启用，需修复最终确认及事务边界并现场验证。批量窗口与取消审批的实际路径需分别核对，不能由此推断相同影响。

2026-11-14 预算流程扩展只读核查：QDNC `BD_BILLTYPE2` 基础类型 `4455` 的 `CLASSTYPE=9` 有两条记录，分别为 `nc.ui.ic.m4455.pf.BudgetPfClientBizProcess`、`nc.ui.ic.m4455.pf.BudgetPfProjectBudgetCheckProcess`；两条 `DR` 均为空，按常用查询约定作未删除候选，实际加载仍待核对。本机 NC 的 `ExtendedClassEnum.PROC_CLIENT` 值为 9，缓存匹配实际基础单据类型以及共享 `XX`；`PfUtilClient` 在流程说明包含应用参数时可能在签名前调用这些客户端扩展。本机扩展代码分别调用 `IbudgetExe4OutApply.checkProjectBudget`、`IProjectBudgetQuery.isBudgetReleaseByPkProjects`；是否在该业务单据触发、实际服务副作用和事务均未验证，也不能将这两项直接等同于行关闭／打开的 `BUDGET_ALERT`。`sapply-preflight.sql` 已加入基础类型、试点类型、可能的父类型及共享 `XX` 的只读候选查询（包含逻辑删除记录），以 `4455-Cxx-1` 代入在 QDNC 返回上述两行。未修改或执行数据库配置。

2026-11-14 出库申请批量结果补充：QDNC 连接重新确认，当前用户及模式均为 `QDNC`，未执行数据库写入。对 NC 原生批次异常集合，即使异常消息为空也显示批次异常提示；无提交回执的单据仍标记为“结果待核对”，不据异常推断回滚。轻量单据部分成功的离线测试实际调用本机 NC 客户端合并方法，验证返回顺序与选择顺序不一致时按主键合并、未成功单据不变，以及重复或未知主键时拒绝刷新。`build/targeted/20261114-115504-574-daded513/test.log` 四组相关定向测试通过；真实 NC 客户端及数据库事务未测试，本次未完整构建或更新此前候选包。

2026-11-14 签名审计表现场结构只读核对：QDNC 的请求、对象、签名记录、审计、差异和快照六表，与 `001_schema.sql` 中的 118 个基础字段相比，未发现缺列、类型、最低字符长度、显式字符长度语义、数值精度／小数位或非空要求不兼容；另外 24 个升级字段按当前升级声明核对，只有 `QD_ES_REQUEST.OWNER_OBJECT_COUNT` 为旧版 `NUMBER(4)`，当前升级脚本期望 `NUMBER(6)`。六表九项主键／唯一约束以及审计结果、快照阶段两项检查约束均为启用且已验证。出库申请候选限制至 1000 对象，`NUMBER(4)` 容量足够；其他超过 9999 对象的业务须按已有升级说明处理。只读预检已增加该列的状态提示，并在 QDNC Oracle 核对现状、缺列、异常类型及模拟新版四种结果；未执行 DDL，字段默认值、完整约束集及 NC 事务仍须现场验证。

2026-11-14 出库申请审计字段现场只读核对：QDNC 的 `IC_SAPPLY_H`、`IC_SAPPLY_B`、`IC_SAPPLY_E`、`SCM_BATCHCODE` 当前分别有 54、96、6、45 个列名，均在 `InventoryAuditFields` 对应清单内；当前列名未命中 `SnapshotSupport.sensitive` 的排除规则。`IC_SAPPLY_E.CGENERALBID` 是启用的单列主键，与现有表体扩展关联假设一致；1000 条有效表体样本均能通过补齐主键关联到扩展行，样本主键均为 20 位。抽样 10000 条有效出库申请表体中未发现有效批次引用，不能据此认定全表没有或证明批次采集。以上仅核对模式、名称及样本，不证明短主键、NC JDBC 采集、字段值完整、关联数据或事务回滚。

2026-11-14 行关闭／打开空值处理：QDNC 的 `IC_SAPPLY_B.ISCLOSED` 允许为空，抽样 10000 条有效表体为 5996 条 `N`、4004 条 `Y`，该样本未发现空值，不能推断全部数据。本机原生动作仅对选中行读取关闭状态；候选适配器及 BP／物理快照比较现允许未选中行保持空值，并区分空值与 `N`，仍拒绝选中行状态为空、未选中行状态变化及重复行主键。`build/targeted/20261114-112146-701-ef09a63a/test.log` 六组相关定向测试通过，包含 BP 前后规则对未选空值及异常状态变化的验证；真实 NC VO、规则及事务仍需现场验证。

2026-11-14 批量取消审批补充：本机 NC 的 `PFBusiAction.prepareWorkflownotesForBatch` 对非工作流动作创建新的空逐单说明；QDNC 只读查询到 `4455%` 范围内 26 条有效 `UNAPPROVE` 动作记录的 `ACTION_TYPE` 均为空，但数据库记录不能证明服务端缓存及实际运行类型。批量签名时若原始确认说明为空，候选现在只接受空值或与原生新建对象一致的逐单说明；未经确认的意见、审批结果、代理人、工作项和其他说明字段会被拒绝。`build/targeted/20261114-105245-418-9d2631e6/test.log` 十组相关定向测试通过，包括标准合并后的取消审批、说明被替换及原生执行前修改。不同动作配置可能生成非空逐单说明，需在现场核实，当前会拒绝；未执行实际 NC 批量流程或数据库写入。

2026-11-14 出库申请只读预检增加基础类型和试点类型的 `PUB_BILLACTION` 检查。将新增查询的 `:pilot_bill_type` 代入 `4455-Cxx-1` 后，在 QDNC Oracle 返回基础及试点类型的审批／取消审批共四行；仅验证该查询和当时的数据，不证明服务端缓存或工作流执行。

2026-11-14 批量审批原生转换候选：离线复现标准合并补回数据库字段后，直接比较客户端签名摘要会误拒。现只对已核对版本的库存标准回调，使用其服务端原单和已签客户端单据重新执行本机 NC 的单据合并、克隆与货位恢复，比较实际逐单内容；签名输入、原单、审批意见和逐单参数在进入原生业务前再次校验。`build/targeted/20261114-103702-080-46003827/test.log` 中十组相关定向测试通过，包含有数据库专有表头／表体字段、货位和基类数组的模拟，以及输入、原单、转换结果、货位、意见和原生参数被改动时的拒绝。使用本机 NC 合并代码及测试元数据，未运行实际 NC 回调或现场事务；现场真实单据、VO 子类、锁和审批流程仍待验证。本次未完整构建或更新历史交付包。

2026-11-14 QDNC 恢复连接后的只读核查：八个出库申请 `scope_id` 均为未映射种子、`group_scope_id` 均为空；节点 `40080801` 无策略和未完成请求，`PUB_PLUGINITEM` 无 `IC/m4455` 登记。`QD_ES_SCHEMA_VERSION` 已登记至 `1.3.0`；对照 `QdTableMetadata` 核查出库申请相关的十张表，173 个声明字段均存在，仅核对字段名，不证明完整结构或事务行为。在线 `QD_ES_REQUEST.OWNER_OBJECT_COUNT` 为 `NUMBER(4)`，当前受控批量客户端候选最多选择 1000 张，未验证如此大批次的现场表现。策略读取使用的双左连接在 Oracle 上分别核对了不存在的组织、总控开启但出库申请策略缺失的组织，以及其他节点的一条已启用策略；读取结果符合相应记录。这不代表 NC 的 JDBC 参数或策略业务测试通过。未执行配置 SQL。

2026-11-14 再次连接 QDNC 只读复核：八个出库申请范围均为 `UNMAPPED`、`VALIDATED=N` 且 `NODE_CODE` 为空；按节点 `40080801` 查询策略为零，按 `IC/m4455` 查询规则登记为零。未写入数据库或安装组件。

2026-11-14 行操作补充证据：`PUB_EVENTTYPE` 中 `40080013` 至 `40080016` 对应的来源为出库申请单，按实际四个事件主键查询 `PUB_EVENTLISTENER` 无登记，包含已停用和逻辑删除记录。`USER_TRIGGERS` 显示 `IC_SAPPLY_H` 有启用的更新触发器；其源码中未检索到自治事务声明或 COMMIT，部分状态变更会写入关联表。本机行关闭 BP 的后置规则还可能调用采购回写和项目物资服务。上述静态结果不能证明现场全部调用参加同一事务或首次异常已完整回滚，预算提示续做仍待安全处理和现场验证。DBX 拒绝直接执行带 CTE 的规则预检脚本并返回 `DATABASE_OUT_OF_SCOPE`；上述登记结论来自限定 `IC/m4455` 的独立只读查询，不能记为脚本已执行。

2026-11-14 批量预校验提示补充：全部所选单据均未通过时，在停止提交前展示前五张单据的原生原因及其余数量，单条原因和单号长度受限。`build/targeted/20261114-081218-289-d1837711/test.log` 的审批客户端与批量事务两组定向测试通过；补充长原因和超过五张的边界后，`build/targeted/20261114-081325-157-f03414f0/test.log` 的审批客户端定向测试通过。该轮未执行完整构建、重新打包或现场测试。

2026-11-14 行操作异常提示补充：已派发请求发生业务／运行时异常或返回结果无法核实时，客户端提示重新查询并显示请求号，保留原始异常；服务端已返回、客户端刷新失败时也显示请求号。预算提示仍不会自动续做，以上提示不把异常视为回滚凭据。`build/targeted/20261114-082219-982-72f50f03/test.log` 中行操作协调及授权交接两组定向测试通过，覆盖四种开关、派发异常、无效结果和刷新失败；真实 NC 客户端仍待验证。

2026-11-14 行操作预算异常提示：针对被 NC 或传输层包装的 `BUDGET_ALERT`，客户端从有限且防循环的异常链中提取原生预算原因，同时保留请求号、原始异常和结果未确认提示；不自动重试或假定首次业务回滚。`build/targeted/20261114-110421-445-775bcb00/test.log` 两组相关定向测试通过，覆盖嵌套预算异常、其他可续做异常及循环异常链。预算续做本身和真实 NC 异常传递仍待验证与开发。

2026-11-14 行操作现场预检补充事件与触发器只读查询：事件按 `PUB_EVENTTYPE.PK_EVENTTYPE` 关联监听器，包含停用及逻辑删除记录，并显示事件来源。两条新增查询在 QDNC Oracle 实际运行：四个出库申请事件未返回监听器，`IC_SAPPLY_H_TRG` 为启用的更新触发器；仅验证查询和当前登记，不证明进程内扩展、外部系统或回滚结果。

2026-11-14 行操作核对 SQL 补充：`verify-sapply-request.sql` 可列出授权行的当前关闭状态及对应审计前后值，保留已删除或查不到的行；当前值不能证明先前事务的提交或回滚。`tests/sql/verify-sapply-line.test.js` 在内存 SQLite 样例中检查行缺失、审计差异和用户／组织隔离；两条查询以不存在的请求号在 QDNC Oracle 只读执行并返回零行，仅验证语法和当前列，不代表真实业务结果验收。

2026-11-14 批量现场配置核对发现明确缺口：QDNC 查询到的 34 条 `DR=0` 出库申请交易类型记录（包含集团、全局及基础类型）均配置 `nc.bs.ic.pf.PfBeforeAndAfterActionForIC`，该查询未核验每条是否启用。此前候选会拒绝实现 `IPfBeforeAction`／`IPfAfterAction` 的所有处理器，因此使用本机标准版本时五项受控批量均无法进入原生批次。本机回调在批次前使用 `BillTransferTool` 取得原单并加动态锁、重建单据及补充原单参数，批次后重新查询并组装返回值；数据库记录不能证明各服务进程的实际类版本或锁传播。

2026-11-14 标准库存批量回调兼容候选：仅放行与本机三层标准回调类 SHA-256 完全一致的实现；未知实现、子类和其他版本仍拒绝。对标准回调附带的原单数组核对授权主键、顺序、集团和组织，在签名参数核对中排除该服务端衍生参数，同时原样保留给 NC；逐单执行前再次核对原单及请求上下文。通过本批范围检查后，原单参数或授权检查在进入原生逐单流程前拒绝的单据记为未执行；已进入原生流程的异常仍待核对。`build/targeted/20261114-091452-537-36f2a4db/test.log` 中八组相关定向测试通过，包含原单范围、顺序、组织篡改、未知回调、审批授权和预校验后只剩一张的派发边界。测试使用 NC 类和事务替身，尚未在真实回调、动态锁、单据转换或逐单事务中运行；审批若原生转换改变已签字段仍会被拒绝，须现场核对，不能据此认定批量已可用。

2026-11-13 出库申请优先批次已有八项单张候选及 BP 前后规则，状态为**部分完成**。保存并提交按原生顺序执行两步策略。普通删除、提交、收回、审批和取消审批的受控批量候选已连接一次验密、原生逐单事务和回执；原生预校验部分失败时跳过未通过的对象。行操作预算续做及部分复杂审批参数待开发。QDNC 已恢复只读连通性测试；规则加载、事务回滚、现场按钮和八项验收仍待验证。盘点及库存调整 25 项暂停。 [库存接入矩阵说明](INVENTORY_INTEGRATION_20261111.md)

2026-11-13 批量审批候选：原生确认后绑定整批审批内容，服务端按已签对象逐单核验实际工作项；单张与批量动作均保留原生用户后缀。`build/targeted/20261113-213247-605-e0fa8580/test.log` 八组相关定向测试通过。对应完整构建、打包和现场结果以各自实际记录为准。

2026-11-13 预校验部分失败候选：服务端在本次授权范围内只提交通过预校验的单据，并记录未提交对象；审批签名绑定排除范围。`build/targeted/20261113-223512-623-d5ddc55a/test.log` 八组定向测试通过，覆盖只剩一张可提交、范围篡改和重复逐单调用；随后 `build/targeted/20261113-224331-446-0a83cc23/test.log` 五组相关测试通过，补充覆盖四种开关组合、排除范围变更和结果提示。未进行完整构建、重新打包或现场测试。QDNC 只读查询确认八个出库申请范围未映射、无 `IC/m4455` 有效 BP 规则、无该节点策略及未完成请求；测试映射 SQL 仅已静态核对，尚未执行。

2026-11-13 增加 BP 规则登记候选 SQL：核对本机 `BPPlugInPoint`、`RuleCollection` 和现场表字段，提供 18 条前后规则登记，主键须由现场 NC 生成。本机加载器查询 `PUB_PLUGINITEM` 未过滤 `DR`；QDNC 按全部 `DR` 状态查询 `IC/m4455` 登记仍为零。`tests/sql/sapply-rule-preflight.test.js` 在内存 SQLite 中验证了缺失、正常、逻辑删除、字段不匹配与重复登记的只读预检结果；该替身不验证 Oracle 或 NC 行为。两份配置 SQL 均未执行，规则加载和回退仍须由现场人员验证。

2026-11-13 无受控作用域的出库申请 BP 策略读取调整为校验 NC 上下文集团及数据源，不再要求交互用户档案；策略直接按组织和按钮读取，即使映射缺失也不会忽略有效策略。BP 提供新旧 VO 时同时检查新旧组织。策略开启仍拒绝未经授权的 BP，缺失上下文仍拒绝。`build/targeted/20261113-233556-880-b9c792bc/test.log` 中相关七组联合定向测试通过；实际新旧 VO、自动任务、身份与事务待现场验证。

2026-11-13 批量回执在写入前先核对全部拒绝／跳过清单，拒绝重复、越权及把已执行单据标记为未提交。`build/targeted/20261113-232548-106-c70b42f1/test.log` 对本轮批量、审批、BP 与组织策略共七组测试联合编译和测试通过；此前完整构建记录与历史交付包均未更新。

2026-11-13 21:41，QDNC 只读表结构检查返回 `TABLE_DESCRIPTION_ERROR`／`i/o timeout`，未执行现场查询。另据本机 `UAPTransaction.rollback` 字节码，底层 `realRollback` 抛出异常时仍可能先设置回滚状态，再抛出 `SystemException`；仅凭事务状态或回滚标记不足以确认实际回滚完成。行操作预算续做仍需可核实的首次事务结果，不得直接重试。

2026-11-13 后续核对：本机 `ICEventType` 的行关闭／打开后置事件编码分别为 `40080013`、`40080015`，后置事件可能在 BP 更新之后触发。查询现场事件类型结构及监听器时数据库连接再次超时，不能由此推断实际监听器或其事务；事件编码也不能未经核对就当成 `PUB_EVENTLISTENER.PK_EVENTTYPE`。行操作续做继续保持关闭，待现场确认受影响监听器与首次业务事务的实际回滚。

2026-11-13 批量回执修正：仅将明确在原生逐单动作前被拒绝的单据标为失败；已进入原生动作但缺少已提交回执的单据标为待核对。`build/targeted/20261113-173354-365-52bfd2df/test.log` 中五组定向测试通过；对应完整构建和打包以实际构建记录为准。

2026-11-13 20:37 隔离快照 `build/sapply-delivery-snapshot-20261113-173526` 完整编译和离线测试通过，随后基于同一构建打包；候选包 1144 个清单文件及必要源码、JAR、UPM 和 SQL 校验通过。新包和 SHA-256 见 `deliveries/README.md`。未部署或执行 NC 现场验收。

## 已执行的基础任务

| ID | 文件/工作项 | 结果与证据 | 状态 |
|---|---|---|---|
| B00-01 | 开发目录、Git、模块、AGENTS和构建扫描 | 原目录为空壳；用户已建main及六模块；Home只读 | 已完成并通过实际测试 |
| B00-02 | PDF全部19页、正式XLSX读取 | 需求规则、35节点243操作已提取，哈希见需求基线 | 已完成并通过实际测试 |
| B00-03 | IBM JDK7基础编译/运行 | Application5975编译至临时目录，运行输出Hello world；仅示例 | 已完成并通过实际测试 |
| B01-01 | config/qd-esign/operation-scope.csv | 243数据行、稳定自定义ID、保留源行号；真实NC编码空，非已适配注册 | 已完成并通过实际测试 |
| B01-02 | config/qd-esign/node-scope.csv | 35节点，类别计数与操作目录一致 | 已完成并通过实际测试 |
| B01-03 | docs/nc65-electronic-signature/全部开发文档 | 基线文档已建立；需随实际批次持续更新 | 部分完成 |
| ENV-01 | QDNC测试库连接 | 已恢复并完成24表安装及只读核验，见实际执行回执 | 已完成并通过实际测试 |

基础资料任务的完成不代表任何电子签名/审计业务功能完成。

## 代码及部署工作项

下列路径以对应模块 `src/client|public|private` 为根。类名是实施契约，新增后需按最终源码更新路径；不得以本表代替真实文件检查。

| ID/批次 | 新增或修改对象 | 功能/接口依赖 | 数据结构 | 验证与当前状态 |
|---|---|---|---|---|
| B01-04/01 | 隔离构建脚本、依赖清单、SQL/升级/回滚 | NC Home现有JDK7和已核验依赖；不写入Home | 全部qd_es_*表、索引、版本 | 24表安装及结构/只读SQL已核验；010回滚和NC事务未通过，部分完成 |
| B02-01/02 | OperationContext、BusinessObjectRef及服务端上下文 | 显式请求/批次/身份/BU/节点/对象 | request/object/locator | 源码已新增；跨RPC/并发NC待验证；已开发但尚未在 NC65 环境验证 |
| B02-02/02 | PolicyDecision、IPolicyService/PolicyServiceImpl | 总控/策略独立版本、BU/节点/按钮 | control/node/policy | 解析/保存已开发；缓存及NC配置联调待核对；部分完成 |
| B02-03/02 | IOperationService/IOperationTransactionService及实现 | NONE外层、独立认领CMT、业务CMT、失败CMT | request/object/fault | 源码已开发；真实代理/提交/回滚待NC；已开发但尚未在 NC65 环境验证 |
| B03-01/03 | ElectronicSignatureActionInterceptor等客户端桥 | ActionInterceptor、上下文provider/consumer | node/policy/request | 公共源码存在；243节点末次RPC/实际XML尚未绑定；部分完成 |
| B04-01/04 | ElectronicSignatureDialog及窗口辅助类 | UIDialog、当前用户/事由 | signlog/request | 源码已新增；真实NC显示/取消/交互待验证；已开发但尚未在 NC65 环境验证 |
| B04-02/04 | baseapp IdentityConfirmationServiceImpl及ISignatureService | 原认证、错误计数/锁定、当前会话 | signlog/security/fault | 桥与DTO契约已开发；真实认证/锁定/会话待NC；已开发但尚未在 NC65 环境验证 |
| B05-01/05 | SignaturePreparation/SignatureResult、SignatureServiceImpl及编码回写 | 独立签名RPC、typed真实返回对象 | signlog/object/request | 源码已新增；实际独立提交/回滚/批量待NC；已开发但尚未在 NC65 环境验证 |
| B06-01/06 | AuditCaptureScope、SnapshotSupport、AuditRecorder、MaterialAuditListener | 白名单/稳定行、真实同步hook | audit/change/snapshot | 核心及物料候选已有；完整删除关系/其他hook未注册，缺hook阻断；部分完成 |
| B07-01/07 | so SaleOrderCommand/Adapter及独立UPM | 有限typed销售桥 | request/audit及关联表 | 有限源码已开发；正式37操作末次RPC/事件注册未完成；部分完成 |
| B07-02/07 | pu PurchaseOrderOperationCommand/Adapter及独立UPM | 原IOrderMaintain INSERT/UPDATE/DELETE及OrderContext | request/audit/fault | 有限typed桥已开发及离线测；审批/数电/其余采购未开发，正式44未绑定；部分完成 |
| B07-03/07 | purp SupplierPriceOperationCommand/Adapter及独立UPM | 原batchSave，分离新增/修改/删除 | object/signlog/audit | 有限桥已开发；禁止混合批次/全部多对象，实际2按钮未绑定；部分完成 |
| B07-04/07 | uapbd MaterialCommand/Adapter及候选listener | 有限主物料typed桥 | 对象/快照及日志 | 其他主档案/子页/分配/批改未适配，正式77未完成；部分完成 |
| B07-05/07 | ic InventoryOperationCommand/Adapter及独立UPM | 采购入库/销售出库INSERT/UPDATE/DELETE/SIGN/CANCEL_SIGN | 源/生成对象关系及快照 | 有限桥已开发；复合/其他库存未开发，正式83未绑定；部分完成 |
| B08-01/08 | QdLogRepository与查询窗口 | 原权限、显式授权组织、月目录 | 在线/历史/locator | 空表Oracle联合/分页/计数检查通过；窗口、权限、实际日志结果与规模待NC验证，部分完成 |
| B08-02/08 | public log包CsvLogWriter与导出入口 | 同查询筛选及权限 | 查询结果 | CSV实现已落盘，页面入口/NC规模未验证；部分完成 |
| B09-01/09 | MonthlyArchiveStore及后台任务 | IBackgroundWorkPlugin、预建历史表 | archive/job及所有归档表 | store已落盘，任务触发/Oracle回滚与跨月NC未验证；部分完成 |
| B09-02/09 | NativeSecurityLogImporter/SyncTask | 原生LOGIN及已写LOGOUT、ts+pk游标 | security/security_key/sync_cursor | 同步源码已开发；源时区/其他安全事件覆盖待NC；部分完成 |
| B10-01/10 | 测试环境部署及243操作验收 | 已确认SQL、NC客户端/服务端、真实权限 | 全部 | 24表安装与空表SQL已核验；匿名回滚受MCP限制，NC全链路未部署/验证，部分完成 |

## 每批追加模板

### 数据持久化批次：已落盘，待环境验证

- 新增 `nc.bs.uapbd.qd.esign.persistence` 下 QdEsignStore、QdLogRepository、MonthlyArchiveStore、QdTableMetadata、QdUtcClock，以及 public log 包 CsvLogWriter/LogQuery/LogKind；均为本项目新增文件。
- 新增版本化SQL、离线月表DDL生成器及SQL README；没有在数据库执行。
- 数据层实现人员报告使用真实NC依赖及IBM JDK7编译验证，完整命令/独立测试结果由主任务在最终集成报告补充；不能据此标NC联调通过。
- QdLogRepository要求serverGroup与非空allowedOrgs；MonthlyArchiveStore不自行commit/DDL，缺表抛出；未结束请求保留PARTIAL并复查。
- 状态：已开发但尚未在 NC65 环境验证。对应查询窗口、后台调度、权限、业务事务集成仍不因此完成。

```
批次/时间：
实际新增文件/类：
实际修改文件（说明是否原有用户文件）：
数据库/注册脚本版本（是否执行）：
JDK/构建命令/退出码：
实际测试（输入/期望/观察）：
未执行验证及原因：
异常、事务、并发与兼容性检查：
状态（五种允许值之一）：
下一任务/阻塞：
```

### Typed适配与权限修复批次

- pu/ic/purp各新增public typed DTO、SPI子接口、private实现和local-only CMT UPM，不覆盖产品类、不修改业务表、不主动写审计。
- 限定采购订单INSERT/UPDATE/DELETE，采购入库/销售出库另SIGN/CANCEL_SIGN，价目表单种新增/修改/删除批次。明确拒绝审批PF、复合库存、混合/多对象价目表和其他多对象新增，不编造compiler或批量返回顺序。
- 校验权威对象集合、VO集团/BU，并只读核对存量真实归属；原NC服务保留业务校验。结果按PK关联，void删除沿用权威对象，价目表不回填未核验客户端单号。
- IBM JDK7编译通过；TypedBusinessAdapterTests离线35断言通过，使用显式typed getter和最小元数据fixture，仅测授权/关联，不执行NC业务/真实DAO。
- EsignPermission和LogAccess改用真实profile逐subject按钮+BU交集；原getButtonPermissionOrgs为null stub。PermissionRulesTests离线16断言通过，不执行NC profile服务或orgType查询。
- 243实际节点末次RPC、BP/事件注册、完整删除关系、真实CMT及业务验收未完成，所有节点仍默认禁用。

### 集成复核与候选交付批次

- `tools/build.ps1 -Test`最后稳定构建退出0：public/private/client源码全量编译，8组203项离线断言通过；SQL结构17表16映射通过。真实NC/Oracle测试没有通过记录。
- 核心新增MappingRules绑定服务器动作/对象域；control_version撤销总控变化前的旧授权；ObjectRelations统一首次/重放父子关系；ReasonRules统一UTF8字节限制；快照拒绝空数据及敏感密钥。
- OperationServiceImpl为NONE入口，NativeTransactionBoundaryProbe只读验证无ambient事务；IOperationTransactionService分别承担claim/业务/失败CMT。RUNNING认领不随业务回滚释放，不自动重试未知结果。实际NC提交边界仍为启用门槛。
- 客户端新增SignatureLifecycleInterceptor/Installer，在整条原拦截链外围无条件清理；没有正确安装或链语义未知时拒绝受控操作，BYPASS沿用原行为。实际节点安装顺序仍未验证。
- 查询、审计明细、精确已加载ID导出、归档、请求过期、原生安全日志增量与周期全PK对账均已有代码；权限/Oracle/任务调度尚未NC验证。
- `tools/package.ps1`仅生成已测试源码对应的候选产物，不部署、不执行SQL、不修改Home。所有SQL等待确认，所有节点初始UNMAPPED/N。
- 本批状态：构建及离线测试工具为已完成并通过实际测试；运行功能为已开发但尚未在NC65环境验证或部分完成，详见IMPLEMENTATION_STATUS。

### 物料主档删除与图片提交后清理批次（2026-11-11，由 ZCode 并行会话开发）

> 下方“实际新增”至“异常、事务、并发与兼容性检查”为原始候选的历史记录，部分结论已被审核否定，不代表当前功能状态。当前状态及只读取证见本节末尾与 `MATERIAL_DELETE_REVIEW_FIX_20261111.md`。

批次/时间：2026-11-11 上午；ChatGPT 主会话同期推进往来单位批量，两线无文件交叠。

实际新增文件/类：
- public：`IMaterialMainDeleteAdapter`、`IMaterialImageCleanupService`、`MaterialMainDeleteResult`。
- private：`MaterialMainDeleteAdapter`（受控删除：锁+全表快照+目录身份+同事务登记清理任务+原生删除+逐表核对）、`MaterialImageCleanupStore`（qd_es_img_cleanup 表访问）、`MaterialImageCleanupServiceImpl`（决策+退避）、`MaterialImageCleanupTask`（IBackgroundWorkPlugin）、`MaterialImageFolder`（目录约定常量与身份守卫）。
- tests：`MaterialImageFolderTests`、`MaterialMainDeleteAdapterTests`、`MaterialImageCleanupServiceTests`。

实际修改文件（均为本项目既有文件，非用户原有文件）：
- `OperationTransactionServiceImpl`：ADAPTERS 白名单加入新 SPI；新增 ownerMode 预锁钩子（requireBusiness+requireArchiveData("material","delete")+BDPKLockUtil 预锁）。
- `qd_esign_material.upm`：注册删除适配器（本地 CMT）与清理维护服务（本地 NONE）。
- `AllTests`：注册三套新离线测试。
- 文档：BASE_ARCHIVE_ACTION_MATRIX 物料删除行、BASE_ARCHIVE_WORK §33/34、本台账、CHANGELOG。

数据库/注册脚本版本（是否执行）：`sql/nc65-electronic-signature/material-image-cleanup/`（01 建表、02 回退、03 路由 seed、04 只读预检）全部未执行；qd_es_node 未登记、后台清理调度未配置。

JDK/构建命令/退出码：`powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 -Test`，NC Home 自带 IBM JDK7，退出码 0，BUILD PASSED；证据 `build/base-archives-check/material-delete-image-cleanup-20261111-build.log`。

实际测试（输入/期望/观察）：离线 91 套 PASS 含新增 3 套——目录身份捕获拒绝路径漂移与空主键、同节点守卫拒绝重建目录；路由仅接受 10140MAG/10140MAO+Delete+DELETE，扩展载荷/主键不一致/类型不符/缺所属组织在执行前拒绝；清理决策对物料行恢复、目录缺失、身份变化、可删除四分支与退避上限（10 次、末次 9 小时）断言通过。

未执行验证及原因：QDNC 自 2026-11-11 02:50 起直连超时；真实 NC 客户端删除入口、Oracle 事务回滚、IImageSystemService/IFileSystemService 实际行为、审计失败后图片未删、清理任务并发与进程中断均未现场验证。

异常、事务、并发与兼容性检查：受控路径不进入原生图片删除与扩展持久化分支，审计失败整体回滚时清理任务行一并回滚；清理只删登记目录且先核身份与物料行不存在，幂等、退避、上限转待核对；网关对 DELETE 仍要求 BEFORE 快照，删除无需 AFTER。

状态（更新于 2026-11-11）：审核修复进行中；批量删除、权威主档、版本快照、文件节点证据、原子认领和回退脚本已有源码候选。自动文件级清理未完成，任务只转 NEEDS_REVIEW；真实客户端、事务、存储和并发尚未验收，禁止标为完成或启用。详见 `MATERIAL_DELETE_REVIEW_FIX_20261111.md`。

下一任务/阻塞：QDNC 的 04 只读预检已执行，Delete 按钮存在、清理表尚未安装。需先在可控存储环境实现并核实文件级条件删除、并发保护与物理结果核验，再准备数据库升级、候选路由、真实客户端及事务验收；目前不能执行安装或启用步骤。

### 销售订单其他按钮 S0 取证批次（2026-11-11，离线只读）

批次/时间：2026-11-11 下午；对象 `40060301` 第 209–216 行 8 项（删除/提交/收回/审批-确定/取消审批/冻结/解冻/整单关闭）。基线日期 2026-11-11。

实际新增文件（均为 `docs/nc65-electronic-signature/s0-evidence-20261111/`）：
- `S0_EVIDENCE_209-216.md`：逐按钮证据表（原表行→注册→页面/bean→动作类→最终 PF/RPC→刷新/异常/外发）、当前 990 保存基线、拟改文件清单（44 项，主线/兼容包双列）、未核实清单（17 项）、批次映射。
- `site-evidence.md`：现场库按钮/范围/节点参数证据（446 行，含 68 条本节点按钮与 18 条 40060303 按钮原文行）。
- `native-mapping.md`：原生落点与实现链（237 行，`uiso_salesorder.jar` XML 与 javap 证据，含 PF 五项链、冻结/解冻/关闭服务签名、审批「确定」提交点）。
- `baseline-990.md`：当前 990 保存基线证据（537 行，11 对源文件哈希、构建/发布证据、HOME 实际状态、网关/事务/审计识别面、拟改清单证据基础）。

实际修改文件（均为本项目既有文档，非用户原有文件）：本台账、`TEST_REPORT.md`、`UNRESOLVED_ISSUES.md` 追加本批记录。未改任何源码、配置、SQL 或 HOME 文件。

数据库/注册脚本版本（是否执行）：未执行任何 SQL；未部署；未启动或停止 NC。`sale-order-save-pilot/configure.sql` 仅只读解析（3 条 UPDATE、SAVEPOINT/ROLLBACK TO、无 COMMIT）。

JDK/构建命令/退出码：未运行构建。本批仅用 HOME 自带 IBM JDK7 `javap` 做只读反编译，未编译任何代码。

实际测试（输入/期望/观察）：**无 NC 测试、无离线单元测试运行**。完成的核实类型为：①JAR 内 XML/字节码静态核实；②现场库 2026-11-07/11-11 只读导出的派生核对；③文件 SHA-256 逐字节冻结核对（990 当前快照 21/21 一致；保存实现 11 对文件中 10 对一致）。

未执行验证及原因：真实 NC 会话（未连接、未登录、未点击）、Oracle 事务与回滚、权限/工作项验证、多选行为、审批弹窗外发时序均未验证；禁止执行 SQL 与部署。`build-record.json`/热修构建记录/upm 部署缺口三项仅做静态判定，未运行 `build.ps1 -Verify`（会写项目 build 目录）。

异常、事务、并发与兼容性检查：发现三个基线缺口（见 `S0_EVIDENCE_209-216.md` §4.4）：`build\sales-990-isolated\build-record.json` 冻结旧源码（`SaleOrderPfSaveAdapter.class` 8555B/`DC6A6A84…` vs 当前源码 `3008DE55…`/HOME 10021B/`1E130A22…`）→ `-Verify` 必然失败；热修 "26 assertions" 测试源码未入库；HOME `qd_esign_saleorder.upm` 665B 单组件，缺 `ISaleOrderPfSaveAdapter` 注册（主线源码 976B 双组件），而 `sale-order-save-pilot/configure.sql:9` 正按该接口映射。以上不影响既有保存代码本身，但会卡住 990 包改造与联调起点。

状态：**部分完成**。S0 证据表、990 基线与拟改文件清单已交付；但按计划第 46 行，证据须标注"静态核实/真实会话核实/未核实"，本批**真实会话核实为 0 条**，因此不满足"已完成并通过实际测试"，整批不能标记完成。

下一任务/阻塞：①需项目写权限（当前会话 workspace-write 沙箱在项目目录外，源码改动无法落盘）；②需在授权写目录重建 990 构建证据并处置 upm 部署缺口（缺口 A/C）；③需真实 NC 会话逐按钮取证以升级静态结论。S1 起按"动作白名单/组织策略门/类型化命令/一次性授权交接/结果校验/审计"推进，首轮仍限单笔 990 普通订单。

### 销售订单其他按钮 S1 单笔公共链路批次（2026-11-11）

批次/时间：2026-11-11 下午；对象 `40060301` 第 209–216 行八项按钮的公共接入链路，首轮限 990 单笔普通订单，保留既有 Save 试点。负责人已确认：缺口 C **复用现有 `ISaleOrderOperationAdapter`**（不新增 upm 组件、不经 NCLocator 查找第二接口）。

实际新增文件/类（主线，均有 990 兼容包同名副本）：
- public：`SaleOrderStateCommand`（冻结/解冻/关闭类型化命令，含关闭原因与 ATP 检查标记）、`SaleOrderPfActionCommand`（PF 动作类型化命令，含 `PfUserObject`/`WorkflownoteVO`/PF 参数）。
- private：`SaleOrderActionRoute`（8 按钮固定动作白名单与原生落点类型，未联调动作执行前拒绝）。
- client：`SaleOrderActionHandoff`（一次性授权绑定/派发/释放，签名后按钮、组织、对象变化拒绝）。
- tests：`SaleOrderOtherButtonsTests`（169 断言）、`SaleOrderActionHandoffTests`（45 断言）。

实际修改文件（均为本项目既有文件）：
- `SaleOrderPilotScope`：扩为 9 个已核实范围 ID 白名单与动作域归类（Delete→DELETE、Save→INSERT、其余→UPDATE）。
- `SaleOrderOperationAdapter`：`validate`/`execute` 先经固定路由；未启用动作拒绝且**不回落低层脚本**；Save 分支保持。
- `sales-order-990-release` 内 `SaleOrderPilotPolicyService`：策略白名单扩为 9 个范围，非 990 开启拒绝、关闭放行。
- `AllTests` 注册两个新测试；`sales-order-990-release\build.ps1` 测试清单加入两个新测试。
- 未改动：`QdSaleOrderConfig.xml`、保存试点类、网关白名单、`MappingRules`、`OrderPhysicalSnapshots`、`qd_esign_saleorder.upm`、`package.ps1`。

数据库/注册脚本版本（是否执行）：未执行任何 SQL；未部署；未启动或停止 NC。

JDK/构建命令/退出码：
- `powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1` → **退出码 0**，237s，日志 `build/s1-build-990.log`；5 个测试类全通过（26+169+45 断言及配置/策略测试）；构建记录 `build/sales-990-isolated/build-record.json`（`tests_passed=true`，47 类，冻结输入 `0C33112E…`）；`-Verify` 退出码 0。**由此解决 S0 缺口 A**（旧记录冻结的是热修前源码）。
- `powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1 -Test` → **退出码 0**，249s，日志 `build/s1-build-main3.log`：public111/private150/client111/legacy-examples6/test120 编译通过，全部离线测试 `ALL OFFLINE TESTS PASSED`，记录 `build/build-result.json`（`schema_version=2`、`tests_passed=true`、958 类、`class_artifacts_sha256=A42E081D…`），`-VerifyRecord` 退出码 0。此前三次失败（`build/s1-build-main.log`、`-retry.log`、`-main2.log`）为环境/并行会话写入导致，与本批代码无关；并行会话仍共用该工程目录，候选发布前需串行化写入窗口后重跑（`UNRESOLVED_ISSUES.md` U43）。

实际测试（输入/期望/观察）：离线断言证明——8 按钮固定路由与动作域正确；错节点/未登记按钮/错组织/错动作/多选/子对象/缺主键全部执行前拒绝；未启用动作 `validate` 与 `execute` 均拒绝且**原生调用次数为 0**；既有 Save 仍每次委托一次固定 PF 保存适配器；客户端一次性交接的绑定/派发/释放与变化拒绝；990 策略包装器覆盖 9 个范围。均为离线测试，不调用 NC 服务。

未执行验证及原因：真实 NC 会话、原生 PF 写入、冻结/解冻/关闭实际业务、ATP 继续/取消、关闭原因落库、同事务回滚、权限与工作项、多选均未验证（需联调窗口）。

异常、事务、并发与兼容性检查：本批只登记白名单并保持执行关闭，未打开任何新按钮；未改网关/映射/快照/upm，不影响既有保存与其他模块。`package.ps1` 的 upm 派生逻辑仍假设 HOME 为 `PolicyServiceImpl`（当前 HOME 已是 `SaleOrderPilotPolicyService`，`count≠1` 会 throw），本批未改，S6 处理。

状态：**已开发但尚未在 NC65 环境验证**（990 隔离构建与离线测试通过；真实 NC 联调未做，不标"已完成并通过实际测试"）。

下一任务/阻塞：S2 接入冻结→解冻→整单关闭（`SaleOrderStateCommand` 固定分支 + 逐动作启用 + 审计差异）；主工程（958 类）与 990 隔离（70 类）构建记录均已产出并通过复核；真实 NC 联调仍待测试窗口。

### 销售订单其他按钮 S2 直接服务批次（2026-11-11）

批次/时间：2026-11-11 晚间；对象 `40060301` 冻结（214）/解冻（215）/整单关闭（216）三条直接服务链路；首轮限 990 单笔普通订单，保留既有 Save 试点。负责人已确认沿用 S1 决策：复用 `ISaleOrderOperationAdapter`，不新增 upm 组件。

实际新增文件/类（主线 + 990 同名副本）：
- client：`QdSaleOrderFreezeAction`（继承原生 `FreezeAction`，保留选择、`ClientBillCombinServer` 合并与 `directlyUpdate`）、`QdSaleOrderCloseAction`（继承原生 `SaleOrderCloseAction`，保留关闭原因输入、取消无写入、`querySaleorder` 补齐整单行、逐行 `vclosereason`；多选显式拒绝、混合组织拒绝）。
- tests：`SaleOrderStateAdapterTests`（44 断言）。

实际修改文件（均为本项目既有文件）：
- `SaleOrderPilotScope`：新增已接入按钮集合（Save/Freeze/BillClose）与 `isImplemented/buttonForScope`。
- `SaleOrderActionRoute`：启用 Freeze、BillClose；UnFreeze 保持关闭；`Kind.isState()`。
- `SaleOrderOperationAdapter`：直接服务固定分支 + `SaleOrderStateCommand` 参数白名单（关闭原因/整单行一致/ATP 跳过标记）+ 返回身份核对 + `closeManage()` 测试钩子。
- `QdSaleOrderConfig.xml`：新增 `freezeAction`/`closeAction` 覆盖；`unFreezeAction`、`deleteAction` 等保持原生。
- `SaleOrderPilotPolicyService`（990）：未接入受控执行的按钮禁止开启策略（避免原生写入绕过签名）。
- `SaleOrderOtherButtonsTests`（167 断言，按 S2 启用边界更新）、`SaleOrderPilotConfigTests`（新增 bean 覆盖断言）、`AllTests`、`sales-order-990-release\build.ps1`（测试清单加入 S2 测试）。
- 未改动：签名拦截器、handoff、保存试点、网关白名单、`MappingRules`、`OrderPhysicalSnapshots`、`qd_esign_saleorder.upm`、`package.ps1`。

数据库/注册脚本版本（是否执行）：未执行任何 SQL；未部署；未启动或停止 NC。

JDK/构建命令/退出码：
- `sales-order-990-release\build.ps1` → **退出码 0**，220s，日志 `build/s2-build-990.log`；6 个测试类全通过（26+44+45+167 断言及配置/策略）；记录 `build/sales-990-isolated/build-record.json`（`tests_passed=true`、100 类、冻结输入 `9E56070A…`）；`-Verify` 退出码 0。
- `tools\build.ps1 -Test` → 第一次尝试（`build/s2-build-main.log`）公共层编译通过，测试层被并行会话未完成的 `PartnerBatchChunksTests` 阻断；并行会话暂停后重跑（`build/s2-build-main2.log`）**退出码 0**，427s，`ALL OFFLINE TESTS PASSED`，`BUILD PASSED`，记录 `build/build-result.json`（`schema_version=2`、`tests_passed=true`、1023 类、`class_artifacts_sha256=3789B5DE…`），构建当次 `-VerifyRecord` 退出码 0。20:25–20:26 并行会话又修改 `InventoryPfPreparation`/`QdPurchaseIn*` 及保存试点 `SaleOrderPfPreparation` 可见性，使记录再次漂移（U43/U50），候选发布前需串行化写入窗口后重跑。

实际测试（输入/期望/观察）：离线 44 断言覆盖——冻结/关闭成功各 1 组（原生服务仅调用一次、结果身份与授权一致、`describeResult` 回填）；关闭原因缺失/首尾空白/与行 `vclosereason` 不一致/缺整单行全部拒绝；冻结不接受关闭原因或检查参数；解冻只接受 ATP 继续标记（false）且当前关闭；主键、组织、动作域不一致拒绝；原生拒绝原样传播且不重试；返回身份不一致拒绝；`OrderPhysicalSnapshots` 对 `ISaleOrderOperationAdapter` + UPDATE/DELETE/INSERT 的识别在 990 构建（使用 HOME 已部署类）中通过。

未执行验证及原因：真实 NC 窗口、Spring bean 覆盖实际加载、EDT/弹窗/刷新行为、原生关闭原因输入与整单行补齐、解冻 ATP 恢复协议、网关审计提交故障注入、同事务回滚、权限与工作项均未验证（需联调窗口）。

异常、事务、并发与兼容性检查：解冻因无法在线下证明原生 ATP「继续」恢复协议等价而保持关闭，且策略包装器禁止开启其签名/审计策略；整单关闭多选显式拒绝（不回落原生写入）；独立服务动作服务端复核单张订单、主键、集团与销售组织，返回结果以实际主键核对；关闭原因与签名事由分离传递。已知并行会话占用同一工程目录，主工程构建记录存在漂移风险。

状态：**已开发但尚未在 NC65 环境验证**（990 隔离构建与离线测试通过；真实 NC 联调未做，不标"已完成并通过实际测试"）。

下一任务/阻塞：S3 接入删除/提交/收回 PF 路径；解冻需联调确认 ATP 恢复协议后再启用；真实联调窗口与主工程整包重建受并行会话影响。

### 销售订单其他按钮 S3 PF 基础动作批次（2026-11-11）

批次/时间：2026-11-11 夜间；对象 `40060301` 删除（209）/提交（210）/收回（211）；首轮限 990 单笔普通订单，保留既有 Save 与 S2 直接服务。

实际新增文件/类（主线 + 990 同名副本）：
- public：`SaleOrderPfActionResult`（原生返回 + 真实主键/单号 + 删除事实）；`SaleOrderPfActionCommand` 增加交易类型字段。
- client：`SaleOrderPfActionDispatch`（受控 PF 分发与流程核对）、`QdSaleOrderSendApproveAction`（提交）、`QdSaleOrderUnSendApproveAction`（收回）。
- tests：`SaleOrderPfActionAdapterTests`（43 断言）。

实际修改文件（均为本项目既有文件）：
- `SaleOrderActionRoute`：服务端就绪集合加入 Delete/Commit/UnCommit。
- `SaleOrderOperationAdapter`：PF 固定分支 + `SaleOrderPfActionCommand` 校验（单张/主键/组织/交易类型/原生用户对象/参数白名单）+ 原生 `IplatFormEntry.processAction` + 持久化事实与返回身份核对 + `platformEntry()/loadHeader()` 测试钩子。
- `SaleOrderPilotScope`：已接入集合加入 Commit/UnCommit（Delete 明确排除）。
- `SaleOrderPfPreparation`：动作白名单扩为 WRITE/DELETE/SAVE/UNSAVE（审批仍拒绝），版本闸门不变。
- `QdSaleOrderConfig.xml`：增加 sendApproveAction/unsendApproveAction 覆盖；deleteAction、unFreezeAction 保持原生。
- 测试与构建：`SaleOrderOtherButtonsTests`(164)、`SaleOrderStateAdapterTests`(47)、`SaleOrderPilotConfigTests`、`AllTests`、990 `build.ps1` 测试清单、990 策略测试。
- 未改动：删除客户端覆盖层（见下）、签名拦截器、handoff、保存试点、网关白名单、`MappingRules`、`OrderPhysicalSnapshots`、`qd_esign_saleorder.upm`、`package.ps1`。

数据库/注册脚本版本（是否执行）：未执行任何 SQL；未部署；未启动或停止 NC。

JDK/构建命令/退出码：
- `sales-order-990-release\build.ps1` → **退出码 0**，日志 `build/s3-build-990.log`；7 个测试类全通过（26+43+45+47+164 断言及配置/策略）；记录 `build/sales-990-isolated/build-record.json`（`tests_passed=true`、127 类、冻结输入 `0306A825…`）；`-Verify` 退出码 0。
- `tools\build.ps1 -Test` → 第一次（`build/s3-build-main.log`）全层编译通过、`ALL OFFLINE TESTS PASSED`（含本批全部销售测试），但冻结校验报 `Inputs changed while building`（并行会话持续写入）；空闲后第二次（`build/s3-build-main2.log`）离线测试失败于并行会话正在编辑的 `PartnerBatchChunksTests`（`AllTests` 第 96 行），与本批无关。主工程整包记录待并行会话停止写入后重跑。

实际测试（输入/期望/观察）：43 断言覆盖——提交调用原生 PF `SAVE` 一次且交易类型/流程说明/用户对象/参数原样传递；收回调用 `UNSAVE` 一次；删除调用 `DELETE` 一次并以 `dr=1`/不存在确认失效（无变化时拒绝）；原生返回身份不一致拒绝；参数白名单（只允许 notechecked/silently/nosendmessage/besideapprove，且只接受 String/Boolean）与任意键拒绝；交易类型不匹配/主键不匹配/用户对象缺失/跳过检查参数拒绝；原生拒绝不重试；启用边界（Delete/Commit/UnCommit/Freeze/BillClose 服务端就绪，Approve/UnApprove/UnFreeze 关闭；策略层 Delete 仍不可开启）；审计识别 DELETE/UPDATE 销售聚合。

未执行验证及原因：真实 NC 窗口的流程准备弹窗、`PfUserClient` 版本实测、工作项与通知时序、「保存并提交」真实开关组合、删除确认后签名顺序、网关审计故障注入、同事务回滚、多选均未验证（需联调窗口）。

异常、事务、并发与兼容性检查：删除因原生确认顺序问题**客户端与策略均保持关闭**（服务端分支已就绪待启用）；提交/收回沿用固定版本 `PfUtilClient` 桥接，动作白名单扩至 WRITE/DELETE/SAVE/UNSAVE；多选在受控策略下由 handoff 单对象约束拒绝，不回落原生写入；并行会话持续修改同工程目录，主工程记录暂不可冻结。

状态：**已开发但尚未在 NC65 环境验证**（990 隔离构建与离线测试通过；真实 NC 联调未做；删除客户端未实现，不标"已完成并通过实际测试"）。

下一任务/阻塞：S4 审批动作（审批「确定」与取消审批）；删除需先完成"确认后签名"设计并证明取消确认不消费签名；真实联调窗口与主工程整包重建受并行会话影响。

### 销售订单其他按钮 S4 审批动作批次（2026-11-11）

批次/时间：2026-11-11 夜间；对象 `40060301` 审批（212，真实 `Approve`）/取消审批（213，`UnApprove`）；首轮限 990 单笔普通订单，保留既有 Save 与 S2/S3 已接入动作。

实际新增文件/类（主线 + 990 同名副本）：`QdSaleOrderApproveAction`、`QdSaleOrderUnApproveAction`。
实际修改文件（均为本项目既有文件）：
- `SaleOrderActionRoute`：服务端就绪集合加入 Approve/UnApprove（现仅 UnFreeze 关闭）。
- `SaleOrderOperationAdapter`：PF 动作名映射补全 `APPROVE/UNAPPROVE`。
- `SaleOrderPilotScope`：已接入集合加入 Approve/UnApprove（共 7 项，Delete 排除）。
- `SaleOrderPfPreparation`：动作白名单扩为 WRITE/DELETE/SAVE/UNSAVE/APPROVE/UNAPPROVE；审批类调用原生审批窗口；参数白名单统一；审批类允许加签/指定审批人。
- `QdSaleOrderConfig.xml`：增加 approveAction/unApproveAction 覆盖；deleteAction、unFreezeAction 保持原生。
- 测试与构建：`SaleOrderPfActionAdapterTests`(48)、`SaleOrderStateAdapterTests`(49)、`SaleOrderOtherButtonsTests`(162)、`SaleOrderPilotConfigTests`、`SaleOrderPilotPolicyServiceTests`、`AllTests`。
- 未改动：删除客户端、签名拦截器、handoff、保存试点、网关白名单、`MappingRules`、`OrderPhysicalSnapshots`、`qd_esign_saleorder.upm`、`package.ps1`。

数据库/注册脚本版本（是否执行）：未执行任何 SQL；未部署；未启动或停止 NC。

JDK/构建命令/退出码：
- `sales-order-990-release\build.ps1` → **退出码 0**，日志 `build/s4-build-990.log`；7 个测试类全通过（26+48+45+49+162 断言及配置/策略）；记录 `build/sales-990-isolated/build-record.json`（`tests_passed=true`、128 类、冻结输入 `2A1BF49F…`、`13:53:39Z`）；`-Verify` 退出码 0。
- `tools\build.ps1 -Test` → 全层编译通过、`ALL OFFLINE TESTS PASSED`（含本批全部销售测试），但冻结校验报 `Inputs changed while building`（并行会话 21:51–21:54 持续修改 `InventoryPhysicalSnapshots`/`OperationTransactionServiceImpl`/`NativeOrgBatchPage` 等）；主工程记录待并行会话停止写入后重跑。

实际测试（输入/期望/观察）：48 断言覆盖——审批/取消审批各调用原生 PF `APPROVE`/`UNAPPROVE` 一次，交易类型/流程说明/用户对象/参数原样传递；原生返回身份不一致拒绝；参数白名单与任意键拒绝；原生拒绝不重试；启用边界（Delete/Commit/UnCommit/Approve/UnApprove/Freeze/BillClose 服务端就绪，仅 UnFreeze 关闭；策略层 7 项已接入、Delete 不可开启）；审批审计按 UPDATE 销售聚合识别。另 49 项直接服务断言、162 项 S1 断言、45 项交接断言、26 项保存 PF 断言同批通过。

未执行验证及原因：真实 NC 审批窗口与签名时序、WorkflownoteVO 实际内容（意见/决议/工作项/指定审批人）、非终审与退回分支、通知只发一次、插件时序、审计故障注入、同事务回滚、多选均未验证（需联调窗口）。

异常、事务、并发与兼容性检查：审批签名在原生审批窗口确认之后、真实写入之前；窗口取消不签名不派发；审批类允许加签/指定审批人但真实分支待联调；多选审批在受控策略下显式拒绝；并行会话持续写入使主工程记录无法冻结。

状态：**已开发但尚未在 NC65 环境验证**（990 隔离构建与离线测试通过；真实 NC 审批/通知/事务未做）。

下一任务/阻塞：S5 批量与现场分支（多选/逐对象回执）；S6 候选包与回退；真实联调优先验证审批窗口时序与通知；删除客户端"确认后签名"设计仍待处理。

### 2026-11-11 库存首批候选与范围矩阵

- 当时 `config/qd-esign/inventory-integration-matrix.csv` 覆盖 18 节点、83 个唯一范围 ID；普通出入库、期初、货位调整、两个定制入库节点及出库申请单保存／删除／提交／收回共 54 项已有单张客户端／PF 服务端候选。此后出库申请另增加 4 项单张候选，盘点及库存调整 25 项按用户要求暂停。矩阵中的测试状态均未冒充 NC 验收结果。
- 库存验收证据台账为每个范围准备四种开关及取消、错密、越权、重复、批量和回滚用例，共 830 条，当前均未执行；配套脚本只校验覆盖和证据字段，不替代现场核验。见 [库存联调证据台账](INVENTORY_ACCEPTANCE.md)。
- 调拨入库复用库存单据及表头公共基类和单张库存适配，限定节点 `40080618`、对象 `IC_TRANS_IN`、原生类型 `4E`；字段白名单来自本机 BMF。PF 带后缀类型候选依据锁定的 `bd_billtype` 按集团、节点、组件和父类型核验，锁竞争及共享集团规则尚未在现场验证；旧直连适配器仍只接受基本类型。单据 `pk_org` 参照库存组织，与策略业务单元之间的主键关系、字段、插件、跨单据副作用及事务均待现场核实。
- 期初余额使用 `InitializtionInVO`／`InitializtionInHeadVO` 公共基类与本机 `40` 类型的普通单张候选；导入经 `OpenBalImportablePanel.setValue` 直接调用 `IInitializtionInMaintain.saveForImport`，不经过普通保存候选。该入口与独立批量保存和批量签字尚未接入，未完成这些路径前不得启用。
- 为期初导入增加未注册的原生新增后置捕获候选，按单次 `IMPORT` 作用域校验同线程、同事务和返回单据身份；该动作域、规则注册、批量一次验密、逐单事务与审计均未接入，普通保存授权不能直接用于导入。
- 货位调整保存、删除增加单张候选：使用 `LocAdjustVO`／`LocAdjustHeadVO` 的库存公共基类，删除继承原生 `LocAdjDeleteAction`；节点路由不接受签字／取消签字。表头、表体和货位字段与本机 BMF 核对，关联列、库存组织、现场插件、库存／货位副作用和事务均待现场验证。只读预检尚未执行。
- 其他入库单张候选复用 `GeneralInVO`／`GeneralInHeadVO` 基类，签字继承专用 `GeneralInSignAction`，保存保留原生异常处理器；来源／生成单据副作用、独立批量保存和签字、`m4alims.xml` 插件及现场事务仍待接入或验证，不得启用。
- 产成品入库单张候选使用 `FinProdInVO`／`FinProdInHeadVO`，限定普通节点 `40080604`；定制投产单 `40080604400` 及 `46-Cxx-2` 类型不得借用该授权。两类节点的现场元数据、插件和跨单据事务仍待核对。
- 委托加工入库单张候选使用 `SubcontInVO`／`SubcontInHeadVO`，限定节点 `40080606` 和类型 `47`；定制委外加工入库 `40080602400`／`45-Cxx-08` 不能借用该授权。独立批量保存与签字、来源单据及其库存副作用仍待接入或验证。
- 其他出库单张候选使用 `GeneralOutVO`／`GeneralOutHeadVO`，保存保留原生专用异常处理器；独立批量、来源／生成单据及库存副作用尚待接入或验证。
- 材料出库单张候选使用 `MaterialOutVO`／`MaterialOutHeadVO`，取消签字保留原生专用子类；组件 `ic_materialout` 与物理表前缀 `ic_material` 不同。现场 WMS／导出插件、来源单据、批量和事务仍待核实。
- 调拨出库单张候选保留原生保存／签字的恢复分支及签字流程标志；当前只接受 `4Y`，本机配置的 `4y` 不做归一并会失败关闭。现场 PF 实际传值及共享集团类型元数据可能阻断业务，必须核实并解决后才能启用。
- 委外加工入库与投产单已增加各自单张候选，分别限定节点／对象和 `45-Cxx-08`、`46-Cxx-2` 类型；未取得可核验的类型主键即失败关闭。类型配置、草稿赋值时机、批量及关联单据仍待现场验证。
- 十三个库存删除候选已将原生确认框和首次数据权限检查移到签名前，并保留 `doSuperAction` 的原生权限复核；确认取消或首次拒绝不创建签名请求。复核拒绝时受控请求登记业务失败，缺失或重复复核时失败关闭。离线测试覆盖四种策略组合、权限变化、原拦截器顺序和重入；桥接固定本机删除及 `ScriptPFlowAction` 字节码，两次权限检查的现场表现、其他提示和插件仍须联调，相关候选未启用。
- 出库申请单 `40080801` 的保存、删除、普通单张提交及收回新增候选，保留原生通用动作、模型与校验服务，并复用 `SapplyBillVO`／`SapplyBillHeadVO` 的库存公共基类；删除前另采集引用的批次档案。此前 QDNC 只读核查还发现表体扩展表 `IC_SAPPLY_E`，已按表体主键纳入候选快照；现场实际关联、类型 `4455` 和其他关联表仍需验证。审批最终确认、取消审批及行关闭／打开后来补充了单张候选，保存并提交复合入口尚未验收。候选未注册或启用。
- 出库申请九个本机 BP 均已定位前／后扩展点，规则候选将受控请求与同线程活动事务、单据主键及组织绑定，并在 BP 前捕获审计快照；规则缺失或异常使受控事务失败。规则会拦截相应策略开启时来自其他入口的未授权 BP 调用，必须先核对来源单据、后台任务、规则缓存及事务传播；本机单元测试不证明现场事务或生效顺序。
- 2026-11-12 追加库存单数据源提交保护：NC 事务类替身测试已覆盖单数据源通过及零／多数据源标记回滚。当时 QDNC 只读连接网络超时；多单一次验密与部分成功、复合入口及预算续做仍未完成。
- 2026-11-13 明确出库申请本批范围为单据电子签名和审计；现有立体库接口及触发器不属于本批专项开发或验收前置条件。原生业务回归、事务回滚和按钮挂载仍需现场联调。
- 2026-11-13 出库申请三个服务端单据读取入口统一处理已核实的 `CHAR(20)` 主键及表体关联键；6 组相关适配和 DAO 替身测试通过，真实 NC DAO／Oracle 查询仍待现场验证。此前候选包不含此后新增的读取修正。
- 2026-11-13 参考同事库存工作流对复合入口的策略核对，出库申请“保存并提交”在原生保存前核对两项策略，任一受控时提示分步操作，查询失败时停止。用户确认现场不存在仅有复合按钮权限而没有对应单独按钮权限的情况。已用本机原生复合动作及客户端替身测试事件标记、顺序和控制组合；这不是受控复合提交的实现，现场插件和策略变更行为仍待核实。
- 2026-11-13 本机客户端将非空 `besideapprove` 视为旁批；受控审批现在在客户端原生准备前后和服务端均拒绝该尚未接入的参数，准备结果中的空值在签名前移除，其他动作保持原有参数规则，策略关闭保留原生流程。客户端和服务端替身测试通过；共享／委托审批及真实待办语义仍需现场核对，不能照搬其他单据的任务判断。
- 2026-11-13 新增 `sapply-rule-preflight.sql`，按九个扩展点及前后规则只读检查登记是否缺失或重复；脚本中的点位与本机 NC 枚举及 18 个编译规则类已核对。SQL 本身尚未在 QDNC 执行，登记匹配也不证明规则加载顺序或事务传播；现有候选包不包含该补充文件。
- 2026-11-12 04:05 的独立快照完成 IBM JDK 7 全层编译、完整离线套件及 `-VerifyRecord`，对应当时的 50 项候选；记录为 `build/inventory-source-snapshot-20261112-040519-permission/build/build-result.json`，不包含此后新增的出库申请候选，也不是部署凭据或现场验收。
- 2026-11-12 02:54 独立源码快照通过 IBM JDK 7 全层编译、完整离线测试及 `-VerifyRecord`，包含删除权限修复；其他入库在该快照后新增，其类型化 PF、客户端绑定和共享快照已通过单独离线替身测试，尚需新的全量构建。快照不代表现场验收。
- 2026-11-12 03:07 的隔离快照也通过全层编译、完整离线测试及 `-VerifyRecord`，包含其他入库初版；其后更改了部分模型获取方式并新增三个单张节点候选，这些后续变化仅通过针对性离线测试，需新的全量构建。快照不代表当前工作区正式打包或现场验收。
- 2026-11-12 03:35 的隔离快照通过全层编译、完整离线测试及 `-VerifyRecord`，包含修正后的其他入库、产成品入库、委托加工入库和其他出库；材料出库在该快照后新增，目前仅通过针对性离线测试。快照不代表当前工作区正式打包或现场验收。
- 2026-11-12 03:47 的隔离快照通过全层编译、完整离线测试及 `-VerifyRecord`，含材料出库；调拨出库和两个定制入库在此后新增，仅通过针对性离线测试。快照不代表当前工作区正式打包或现场验收。
- 库存删除结果的单号改为原生调用前读取的服务端持久化单号，缺失时阻断；客户端单号不一致和缺失场景已通过离线替身测试。
- 2026-11-12 02:28 隔离快照完成 IBM JDK 7 全层编译、完整离线测试和 `-VerifyRecord`，69 个库存相关 Java 源码／测试及本次矩阵和预检脚本在核对时与工作区一致；结果不等同于当前工作区正式部署凭据或 NC 验收。记录位于 `build/inventory-source-snapshot-20261112-022800-current/build/build-result.json`。
- 2026-11-12 01:35 的独立源码快照通过 IBM JDK 7 全量编译、完整离线测试及 `-VerifyRecord`；58 个库存 Java 源码／测试与当时工作区一致。包含交易类型主键和锁定查询、原生 VO 原地修改、导入后置规则调用及异常回滚标记测试；矩阵随后补充了货位调整静态线索，现场未执行。
- 2026-11-12 00:42 的独立源码快照通过 IBM JDK 7 全层编译、完整离线测试与 `-VerifyRecord`，53 个库存 Java 源码及测试文件与当时工作区一致；随后更新了矩阵和预检说明中的导入缺口，未执行现场测试。
- 库存保存、删除、签字和取消签字使用原生动作子类及 `ICBillVO` 公共基类，服务端核对节点、按钮、对象、组织和实际结果；审计候选包含表头、表体、货位字段白名单与前后快照。多选受控操作尚未接入，明确阻断。
- 2026-11-12 00:26 的独立源码快照通过 IBM JDK 7 全层编译、完整离线套件与 `-VerifyRecord`，45 个库存 Java 源文件／测试和当时工作区一致。后续修改的只读预检 SQL 及新增验收台账工具分别进行静态和离线验证，不属于该快照；所有现场验收仍未执行。
- 本机字节码显示原生多选包含 `beforeBatch`／`afterBatch` 和逐单 `RequiresNew` 工作流。未保持原生语义的逐单分发实验已移出正式源码；服务端在创建受控签名请求前拒绝首批多选，两个开关均关闭的原生 BYPASS 保持可用。原生批量、一次验密和逐单审计事务仍待完成。
- 原生批量的逐单 PF 约束可见部分调用参数，但尚未证明授权可跨进程传至每张单据且缺少约束时能在提交前阻断；前置批量处理还可能改变对象顺序。异常索引和返回数组不能单独证明提交结果，不能据此记逐单成功。
- 库存快照补充实体、表头主键和缺失结果检查。2026-11-11 23:24 独立源码快照通过 IBM JDK 7 全层编译、完整离线套件和 `-VerifyRecord`，包含数量保护、快照、客户端交接与单笔 PF 替身测试。相关库存源码核对时与工作区一致；矩阵随后更新调拨入库线索，隔离记录不是当前工作区打包凭据或 NC 验收结果。
- 2026-11-11 22:28 隔离源码快照完成 IBM JDK 7 全量编译和离线测试，输入复核通过，含状态异常、保存目标组织和模块／网关路由一致性检查。之后共享工作区的其他模块源码继续变化，隔离记录不能作为当前工作区的正式打包或 NC 验收凭据。
- 单据表体中的批次主键、批次号及自定义项已纳入字段白名单。首批单张删除候选增加独立共享批次档案的删除前引用快照，按集团及表体主键核对，引用证据与单据删除差异分开；字段、共享范围及同事务回滚均待现场验证。
- 2026-11-11 23:48 独立源码快照完成 IBM JDK 7 全层编译、完整离线套件和 `-VerifyRecord`，覆盖批次引用去重、集团隔离、分段参数化查询及审计异常传播。库存相关源码核对时与工作区一致；范围矩阵随后补充复杂动作线索，此快照并非当前工作区正式打包凭据或现场验收证据。
- `sql/nc65-electronic-signature/inventory-pilot/` 的预检及请求核对脚本只读，尚未执行。当前 DBX 连接返回 `DATABASE_OUT_OF_SCOPE`，未能核实试点库存业务单元、当前物理字段、原生批量结果或 NC 事务回滚。详见 [库存接入记录](INVENTORY_INTEGRATION_20261111.md)。
- 2026-11-11 23:57 复查 DBX 连接列表两次均为 `Transport closed`，未能确认授权范围是否变化；未绕过限制或执行现场查询。
- 状态：部分完成。下一步需要可访问的 QDNC 环境及已启用库存业务的试点组织，完成首批单张、四种开关、失败回滚和批量部分成功后再逐批扩展。

### 销售订单其他按钮 S5 批量与现场分支批次（2026-11-12）

批次/时间：2026-11-12 上午；对象 `40060301` 八项按钮的多选/批量语义与受控防线；首轮仍限 990 单笔业务验收。

实际新增文件/类（主线 + 990 同名副本）：
- client：`SaleOrderSelection`（选择范围判定：受控单笔/受控多选/原生/混合拒绝/归属不明拒绝）。
- tests：`SaleOrderSelectionTests`（15 断言）。

实际修改文件（均为本项目既有文件）：
- `QdSaleOrderSendApproveAction`、`QdSaleOrderUnSendApproveAction`、`QdSaleOrderApproveAction`、`QdSaleOrderUnApproveAction`：统一改用 `SaleOrderSelection` 判定；**混合组织或无法判定归属一律拒绝**（此前会回落原生写入），全部首轮组织多选进入受控路径并由单对象约束拒绝。
- `AllTests` 注册选择判定测试；`sales-order-990-release\build.ps1` 测试清单加入该测试。
- 未改动：删除客户端（仍关闭）、各动作服务端分支、签名拦截器、handoff、保存试点、网关白名单、`OrderPhysicalSnapshots`、`qd_esign_saleorder.upm`、`package.ps1`。

数据库/注册脚本版本（是否执行）：未执行任何 SQL；未部署；未启动或停止 NC。

JDK/构建命令/退出码：
- `sales-order-990-release\build.ps1` → **退出码 0**，日志 `build/s5-build-990.log`；8 个测试类全通过（26+15+45+48+49+162 断言及配置/策略）；记录 `build/sales-990-isolated/build-record.json`（`tests_passed=true`、131 类、冻结输入 `E7F51322…`、`09:33:03Z`）。
- `tools\build.ps1 -Test` → 全层编译通过、`ALL OFFLINE TESTS PASSED`（含本批 15 项选择判定断言），但冻结校验报 `Inputs changed while building`；并行会话 09:36–09:41 仍在修改 `SignatureServiceImpl`、`NativeOrgBatch*`、`Sapply*`、`InventoryIntegrationMatrix`、`AllTests` 等，主工程记录待其停止写入后重跑。

实际测试（输入/期望/观察）：15 断言覆盖——单笔首轮组织→受控单笔；多张首轮组织→受控多选；全部非首轮→原生；首轮与非首轮混合（任意顺序）→拒绝；缺失选择/空选择/集团缺失/组织为空→拒绝；受控判定只对受控单笔/多选为真；拒绝提示分别说明"混合组织"与"归属不明"。

未执行验证及原因：两张以上订单的真实多选、原生原子/逐单语义、部分成功与逐对象回执、批量事务边界均未验证（需 NC 环境）；批量受控写入按计划保持关闭。

异常、事务、并发与兼容性检查：混合组织由"回落原生"改为"拒绝"，属有意安全收窄，需现场确认无合法混合组织批量需求；多选在策略开启时由单对象交接/适配器拒绝，不回落原生写入；删除策略不可开启；整单关闭/审批多选显式拒绝；并行会话持续写入使主工程记录无法冻结（990 隔离构建不受影响）。

状态：**已开发但尚未在 NC65 环境验证**（选择判定与失败关闭离线通过；批量写入未实现，S5 批量验收未通过）。

下一任务/阻塞：S6 候选包与回退演练，或先安排真实 NC 联调；批量受控写入需在核实原生批量语义与逐对象事务后再设计。

### 销售订单其他按钮 S6 候选发布与回退演练批次（2026-11-12）

批次/时间：2026-11-12 上午；候选打包基于当前 QDNC 1.3.0 + 990 保存补丁 HOME 基线；本批全部入口**尚未通过真实 NC 联调**，候选包与脚本仅作离线候选/演练材料，未部署、未执行 SQL。

实际新增文件：
- `sales-order-990-release/sql/nc65-electronic-signature/sale-order-other-buttons/preflight.sql`（只读预检）
- `.../configure.sql`（事务性注册，不自动提交；REGISTER 需 `:pilot_allow_unverified=Y`；仅 7 个已实现按钮，Delete 不注册）
- `.../rollback.sql`（8 范围恢复 UNMAPPED/N、待执行请求失效，不触碰保存试点）
- `.../verify.sql`（按请求只读核对签名/审计/快照/映射/策略）

实际修改文件（均为本项目既有文件）：
- `sales-order-990-release/package.ps1`：①覆盖项改"类前缀 + 从构建记录解析内部类"；②UPM 派生支持当前 HOME 已绑定 `SaleOrderPilotPolicyService`（已绑定则保留、未绑定替换一次、其他中止）；③SQL 复制兼容 `sale-order-other-buttons`；④文件改为 UTF-8 BOM（Windows PowerShell 5.1 中文注释解析稳定）。未改保存试点 SQL、网关白名单、`build.ps1`。
- `docs/nc65-electronic-signature/SALE_ORDER_OTHER_BUTTONS_S6_20261112.md`（本批交付记录）。

数据库/注册脚本版本（是否执行）：未执行任何 SQL；未部署；未启动或停止 NC。

JDK/构建命令/退出码：
- `sales-order-990-release\build.ps1` → **退出码 0**，8 个测试类全通过，记录重新冻结（131 类、`input_sha256=439F571D…`）。
- `sales-order-990-release\package.ps1` → **退出码 0**，109s；输出 `SALES_990_CANDIDATE=build/qd-esign-sale-990-20261112-103155-3dfd554c.zip`、`SHA256=917D494B5C5AAB7D5CF321CF8841655D45784508BC81532A0AE823DF9F484A4E`；打包前后各执行一次 `-Verify`；UPM 输出 "already bound to SaleOrderPilotPolicyService; preserved"。
- 候选 manifest：`files=28`、`baseline_sha256` 21 个模块文件、`build_record_sha256=7617B032…`；ZIP 29 条目、487,843 B。

实际测试（输入/期望/观察）：打包器逐条核对——21 个 HOME 基线文件复制前后哈希一致；覆盖类与内部类（`SaleOrderActionRoute$Kind`、`SaleOrderSelection$Kind`、`SaleOrderPfActionDispatch$ContextFiller`、`QdSaleOrder*Action$1`）均注入；非本批 JAR 条目未变化；UPM 保持已绑定销售策略包装器；`so/META-INF/qd_esign_saleorder.upm` 维持 HOME 单组件基线；包内 6 份 SQL、2 份 UPM、evidence 构建记录齐全；ZIP 条目大小与哈希复核通过。早期 10:25 不完整候选（缺 SQL）已删除，避免误用。

未执行验证及原因：全部 8 项真实 NC 联调（原生流程/事务回滚/权限/工作项/弹窗与通知/审计差异/多选）；映射与撤回 SQL 未在真实库执行或语法校验；候选包未部署。主工程整包记录仍受并行会话持续写入影响（本批未依赖）。

异常、事务、并发与兼容性检查：候选包不修改数据库、不开启策略、不改客户端/插件参数；SQL 草案均为事务性且无隐式提交，带环境/组织/按钮/运行中请求/DELETE 排除等守卫；UPM 已绑定当前 HOME 状态，若目标环境为旧基线则打包器会替换一次并可逆；并行会话仍在同一工程目录写入，候选包冻结的是 990 子集，不受主线其他模块影响。

状态：**已开发但尚未在 NC65 环境验证**（离线候选包与回退材料已交付；未部署、未执行 SQL、未联调）。按计划，没有真实 NC 验证前**不得在目标组织启用任何入口**。

下一任务/阻塞：真实 NC 联调窗口（优先冻结→审计、整单关闭原因/整单行、解冻 ATP、提交/收回与保存并提交、审批窗口与通知、删除确认后签名、多选）；联调通过后按 S6 §7 顺序逐入口注册映射并开启 990 策略；主工程整包记录待并行会话停止写入后重跑。

### 销售订单其他按钮 开发 HOME 文件部署记录（2026-11-12）

执行时间：2026-11-12 10:47–10:52；目标 HOME：`E:\01-Home\NC65home\QuDuTest\nchome`。项目负责人直接授权文件级补丁，未安排额外维护窗口；本会话未启动/停止 NC、未执行任何 SQL。

实际执行：
- `tools\nc-patch.ps1 -Action Plan` → deployment_id `20261112-104711-c9f0fccb`，21 个模块文件，`home_changed=false`。
- `tools\nc-patch.ps1 -Action Backup` → 21 个模块文件 + 44 份 `ierp/bin` 配置备份（私有 ACL），`verified=true`。
- 文件应用：因 `Deploy` 的 admission 门要求环境/数据库/外发隔离证据（本会话无法独立取证），经负责人直接授权后以原子替换手动应用候选包 21 个模块文件（每文件校验 old→new 哈希，写与工具同格式 `journal.json`）。
- `tools\nc-patch.ps1 -Action Verify` → `verified=true`（21/21 新哈希）。

校验与边界：
- 候选包 `build/qd-esign-sale-990-20261112-103155-3dfd554c.zip`，SHA-256 `917D494B…84A4E`；release `qd-esign-sale-990-20261112-103155-3dfd554c`。
- 部署后 `qd_esign_core.upm` 保留 `SaleOrderPilotPolicyService`；`qd_esign_saleorder.upm` 哈希未变（`E195AC9C…`）；44 份配置哈希 0 变化；新类已在 HOME 各 JAR 中确认存在。
- 未变更：数据库/注册数据、组织总控、按钮策略（全部保持关闭）、`BeanConfigFilePath` 与插件参数、签名/审计记录。
- 生效条件：需 NC 服务端/客户端重新加载类（重启服务端或客户端重新登录/刷新缓存）；本机当前未观察到从该 HOME 启动的 java 进程。是否使用签名覆盖层仍取决于数据库功能参数 `BeanConfigFilePath` 是否已指向 `QdUiSaleOrderConfig.xml`（本批未修改数据库，未核实）。
- 回退：`build\deployment\20261112-104711-c9f0fccb\rollback.ps1`（逐文件核对 old/new 哈希后从 `backup/files` 还原并复核）；`deployment-note.md` 记录完整过程；自带的 `nc-patch.ps1 Rollback` 需要 admission JSON，故提供手动回退脚本。

状态：**文件已安装，未联调**；策略仍关闭，未做任何启用动作。

## 2026-11-12 销售订单其他按钮数据库只读预检（实际执行）

执行通道：DBX MCP（连接"齐都药业测试库"，`172.21.253.8:1521/DB`，schema QDNC）。本批脚本未执行任何写操作。

| 项 | 实测值 |
|---|---|
| 会话/版本 | `QDNC/QDNC`；schema 版本含 1.0.0/1.1.0/1.2.0/1.3.0，无 1.3.0-RB |
| 8 个按钮注册 | Delete/Commit/UnCommit/Approve/UnApprove/Freeze/UnFreeze/BillClose 均存在且 PK 与 site-evidence 一致，`ISENABLE=Y` |
| 8 个范围映射（209–216） | **全部 `UNMAPPED/N`**（本批未注册） |
| 保存映射（208） | **`PILOT/Y`**，`ISaleOrderOperationAdapter` / `INSERT` / `SO_SALE_ORDER` |
| 策略 | **Save 策略 `ENABLED=Y`（990 组织）**；其他按钮无启用策略 |
| `BeanConfigFilePath` | **已指向 `nc/ui/uapbd/qd/esign/QdSaleOrderConfig.xml`**（客户端覆盖层已切换） |
| 运行中/待执行请求 | 0 |
| 组织总控 | 990 组织 `master_enabled=Y`，`control_version=1` |

写操作尝试与结论：尝试以事务性 PL/SQL 注册 7 个按钮映射，被 DBX MCP 拒绝（`DATABASE_OUT_OF_SCOPE`，该连接不允许写库）；复核显示数据库**零变更**（8 个范围仍 UNMAPPED、策略仍只有 Save 启用、保存映射未变）。已交付供负责人手工执行的独立脚本 `sales-order-990-release/sql/nc65-electronic-signature/sale-order-other-buttons/operator-run.sql`（含执行前只读核对、7 项注册块、回显核对、手工 COMMIT 与回退块）。

注意：实测状态与 S0/S6 文档中的旧假设不同（旧文写"保存映射 UNMAPPED、策略未启用、BeanConfig 未切"是 2026-11-10/11 早期状态）；后续以本次实测为准。

## 2026-11-12 映射注册执行回执（负责人手工执行 + 独立只读复核）

执行方式：项目负责人按 `operator-run.sql` 在 QDNC 手工执行事务块并 COMMIT；随后本会话通过 DBX MCP **只读复核**，结果一致。

| 项 | 执行后实测值 |
|---|---|
| 209–216 映射 | 7 个按钮已注册：`Commit/UnCommit/Approve/UnApprove/Freeze/UnFreeze/BillClose` → `node_code=40060301`，`adapter_class=nc.itf.so.qd.esign.ISaleOrderOperationAdapter`，`operation_types=UPDATE`，`object_type=SO_SALE_ORDER`，`mapping_state=PILOT`，`validated=Y` |
| Delete（209） | 保持 `UNMAPPED/N`（客户端确认后签名未完成，本批不注册） |
| 保存映射（208） | 未变：`PILOT/Y` + `ISaleOrderOperationAdapter/INSERT/SO_SALE_ORDER` |
| 策略 | 未变：仅 Save 一条 `ENABLED=Y`；8 项其他按钮均未启用策略（映射注册不等于启用签名） |
| 运行中请求 | 0 |

结论：映射注册已生效且未触碰保存试点与策略；这些按钮的实际签名行为仍取决于 990 策略开关，当前保持原生行为。后续需：①重启/重登 NC 使新客户端类加载；②先做保存链路回归（保存策略已开启）；③修复关闭/审批在策略关闭时的二次弹窗风险（U70）；④联调通过后逐按钮在策略页开启 990 策略。

### 销售管理其他三节点 M0–M4/M6 批次（2026-11-12，离线开发 + 只读取证）

批次/时间：2026-11-12 上午至下午；对象 `40060303`（217–228，12 项）、`40060402`（229–238，10 项）、`40060501`（239–244，6 项），共 28 项。
本批**未部署、未执行任何 SQL、未启动/停止 NC、未连接数据库、未触发外部平台**；真实 NC 会话核实 0 条。

实际新增文件/类：
- public：`SalesOtherNodeScope`（28 行范围白名单）、`SalesPfActionCommand`、`SalesPfActionResult`；
  `ISaleCloseOperationAdapter`、`IDeliveryOperationAdapter`、`ISaleInvoiceOperationAdapter`、
  `SaleCloseCommand`、`DeliveryCommand`、`DeliveryStateResult`。
- private：`SaleCloseActionRoute`、`SaleCloseOperationAdapter`、`DeliveryActionRoute`、`DeliveryOperationAdapter`、
  `SaleInvoiceActionRoute`、`SaleInvoiceOperationAdapter`、`SalesOtherNodeSnapshots`（审计快照识别）。
- client：`SalesNodeActionHandoff`、`SalesNodeSelection`、`SalesPfPreparation`、`SalesPfActionDispatch`；
  `QdDelivery*Action`（5）、`QdDeliveryConfig.xml`；`QdSaleInvoice*Action`（5）、`QdSaleInvoiceConfig.xml`。
- 注册：`so/META-INF/qd_esign_sales_other_nodes.upm`（三个适配器，独立 UPM，不改既有 UPM）。
- 交付：`sales-other-nodes-release/`（`check.ps1` 快速检查器、`build.ps1` 冻结快照构建、`package.ps1` 候选打包、
  `tests.txt`、`sql/.../sales-other-nodes/{preflight,configure,rollback,verify}.sql`）。
- 文档：`SALES_OTHER_NODES_M0_20261112.md`、`SALES_OTHER_NODES_M1_M4_20261112.md`、`SALES_OTHER_NODES_M6_20261112.md`、
  `sales-other-nodes-evidence/{close-manage,delivery,invoice}-evidence.md`。

实际修改文件（均为本项目既有文件，最小增量）：
- `OperationTransactionServiceImpl`：ADAPTERS 增加三个接口；审计前后挂钩新增 `SalesOtherNodeSnapshots`（不影响既有订单/库存路径）。
- `MappingRules`：新增三节点根/行对象校验分支（不影响既有适配器判定）。
- `AllTests`：登记本批 9 个离线测试类。

数据库/注册脚本版本（是否执行）：`sales-other-nodes-release/sql/.../sales-other-nodes/` 四份草案（预检/注册/回退/核对）**未执行**；
预检含 `org_orgtype.fieldname` 动态组织类型校验、按钮注册核对、映射/策略/运行中请求守卫；`configure.sql` 无隐式提交。

JDK/构建命令/退出码：以 `sales-other-nodes-release\build.ps1` 在冻结源码快照中执行全量 `tools/build.ps1 -Test`（IBM JDK7），
结果与哈希见 `SALES_OTHER_NODES_M6_20261112.md`；开发期用 `sales-other-nodes-release\check.ps1` 全量编译+本批测试，
不写主工程 `build/build-result.json`。

实际测试：本批 9 个离线测试类通过（范围表 201、快照识别 13、客户端交接 45、关闭管理适配器 353、发货单适配器 248、
发货单客户端配置 105、销售发票适配器 122、销售发票客户端配置 83、PF 客户端桥 6，合计 1176 条断言）；均为离线替身，
不调用 NC 服务/数据库/事务。逐项覆盖见 `SALES_OTHER_NODES_M1_M4_20261112.md`。

未执行验证及原因：三节点真实 NC 入口/权限/工作项、原生弹窗与确认顺序、ATP/信用/容差继续协议、
同事务回滚与审计故障注入、跨模块回写（应收/结算/凭证）、通知外发、批量语义均未验证（无 NC 会话与数据库授权）；
三节点试点组织与账号未确认。

异常、事务、并发与兼容性检查：未修改销售订单保存试点、209–216 映射、既有 `ISaleOrderOperationAdapter` 分支与既有 UPM；
关闭管理 BillOpen/OutOpen、发货单 Delete/BillOpen/OutOpen、销售发票 Delete 保持关闭（原生调用前拒绝，测试断言 0 次）；
混合组织与多选在客户端/服务端拒绝且不回落原生写入；删除/修改/新增边界与不支持的恢复协议已在 M1–M4 文档 §8 明确列为能力边界。

状态：**已开发但尚未在 NC65 环境验证**（离线构建与测试通过；未部署、未执行 SQL、未联调，不得据此在目标组织启用任何入口）。

下一任务/阻塞：①现场确认三节点试点组织与账号；②真实 NC 联调（优先：发货/发票审批窗口与通知、保存与行下标、
关闭管理原因与行范围、发货删除确认顺序、ATP/信用继续协议）；③映射/回退 SQL 在真实库只读预检→事务注册→回显→提交；
④候选包部署演练与回退（见 M6 文档 §5/§7）。- 冻结快照构建记录：快照 `B77067581B00E75DFFA72F160F7680D1279BFE3DB783AC9532E0EC160327944F`、内部记录 `1FC8E9DC5FC6BC1A98874836A96CA278384E01A01FC0F011EFBB5BB467ABFCEC`、1085 类、9 测试类 1176 断言；候选包 `build/qd-esign-sales-other-nodes-20261112-133410-7516cdf9.zip`（SHA-256 `18BE91836366AD89A11B5FF23DAC8A574F5B73B8316759F2A0699CE0026E0732`、16 文件、50 覆盖类）。全量 AllTests 因并行会话改销售订单文件未纳入，候选发布前须串行窗口重跑主构建。
