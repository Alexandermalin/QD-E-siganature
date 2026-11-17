# 开发任务台账

2026-11-09 组织策略批次：组织参照、按钮明细批量维护、组织总控及 1.1.0 升级脚本已开发但尚未在 NC65 环境验证。QDNC 仍为 1.0.0，现场部署及用例见 [组织策略验证](ORGANIZATION_POLICY.md)。下面保留早期批次的现场结构与离线测试记录。

更新：2026-11-07。已完成首轮离线实现及全量编译，实际批次结果见文末及TEST_REPORT。全部243项业务操作尚未完成接入和验收，不把源码存在或候选配置视为已启用。

允许的状态仅为：已完成并通过实际测试；已开发但尚未在 NC65 环境验证；部分完成；尚未开发；因环境或资料不足而阻塞。

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
