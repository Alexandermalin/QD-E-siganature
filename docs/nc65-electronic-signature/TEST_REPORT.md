# 实际测试与验证报告

## 2026-11-09 组织策略离线检查与 QDNC 只读预检

使用 IBM JDK 7 编译 37 份公共源码、42 份服务端源码、26 份客户端源码、6 份原示例及 14 份测试源码。`OrganizationPolicyTests` 和 `OrganizationPolicyClientTests` 与现有离线测试全部通过。另核对数据库结构契约和迁移前后的版本计算。

QDNC 只读核对确认组织 `990` 有效，目录查询返回 243 条待映射记录，零条已配置策略。升级执行、组织参照交互、实际权限及事务回滚的现场验收待维护窗口。用例及证据范围见 [组织策略验证](ORGANIZATION_POLICY.md)。

## 2026-11-07：24 表安装与 239 项离线断言

2026-11-07，用户在收到固定脚本后授权继续。QDNC实际安装24表、79索引、24主键/12唯一约束及243条UNMAPPED/N目录，另一查询会话确认提交结果；332列与月表实际结构通过check_deployed_schema.py核验。011_readonly_sql_checks.sql的10条生产查询在真实Oracle上执行通过，空表结果为6项0行、4项计数0。全部原始回执见database-execution/20261107，汇总见RESULT.md。

010匿名回滚块被MCP的DATABASE_OUT_OF_SCOPE拒绝，没有计为通过；独立COMMIT也不受MCP支持，实际依其默认逐句自动提交并经另一会话核实。在线/历史日志抽查为空，没有将工具拒绝计作SQL事务、CLOB数据往返或NC业务回滚成功。

本批修复日志对象类型被请求NULL覆盖，以及归档后旧请求结果丢失。新增CompletedRequestReader和ResultRecoveryTests，历史仅恢复终态结果，SUCCESS重放只读、不再次执行业务；副本冲突、缺表、错组织和对象证据不完整均拒绝。

完整IBM JDK7构建退出0：34public/42private/18client、6原有示例及10测试源码通过；Core51、Dispatcher5、Identity15、SignatureClient51、Management16、Persistence21、ResultRecovery29、Permission16、TypedAdapter35，共239项离线断言。仅离线检查，不调用实际NC认证、原业务CMT或界面。下文203/210项和连接超时为早期批次历史记录。

报告基线：2026-11-07。只列实际发生的验证；计划用例见 TEST_CASES。NC业务验收尚未进行，不能依据离线检查宣称通过。

| ID | 实际命令/动作 | 观察结果 | 结论及边界 |
|---|---|---|---|
| V01 | 检查开发目录与Git；读取.iml/模块配置 | 原为uapbd骨架；用户后建main、六模块和18个源码根 | 工程现状已核实；尚无提交，不覆盖用户文件 |
| V02 | NC_HOME/ufjdk/bin/java.exe -version；javac.exe -version | IBM Java 1.7.0/J9，javac 1.7.0-internal | 真实可用目标工具链；未升级依赖 |
| V03 | IBM javac -encoding GBK -source 1.7 -target 1.7 -d 临时目录 uapbd/src/client/Application5975.java；随后IBM java -cp 临时目录 Application5975 | 编译成功，运行输出Hello world! | 仅基础示例实际测试通过，不证明NC功能 |
| V04 | pypdf读取PDF全部页并检查页数 | 19页需求文本读取完成；哈希已记录 | 需求已读取，不是业务测试 |
| V05 | bundled Python/openpyxl只读读取Sheet2并生成CSV | 243操作/35节点；各类77/44/2/83/37；scopeId唯一 | 范围提取通过；非243操作适配通过 |
| V06 | 读取本地Java/XML及对真实NC JAR执行javap | 证实拦截器/服务/认证/事务/任务契约及关键陷阱 | 静态接口存在性验证，不代表NC运行测试 |
| V07 | 主任务对授权QDNC测试库尝试网络连接 | 连接超时；未执行SQL | 因环境或资料不足而阻塞；不能声明Oracle/事务/部署测试通过 |

## 尚未执行的验证

- 新增代码整体构建和离线测试已执行，结果见下方集成批次；不代替以下NC/Oracle验证。
- Oracle建表/索引/初始化/升级/回滚/历史月表及真实SQL执行。
- NC客户端签名窗口、服务端认证/锁定、明确上下文跨RPC、业务同事务回滚、编码回写、批量及全部243操作。
- 实际权限与BU隔离、登录注销/IP、安全事件、CSV真实规模、月归档故障恢复及历史统一查询。
- 既有核心业务保存/审批/删除/状态动作回归、数电接口测试环境结果。

## 后续结果记录格式

每条实际测试应注明日期、目标环境（脱敏）、构建版本、命令或人工步骤、输入、预期、实际观察、退出码/日志位置、影响scopeId、结论和未验证边界。依赖缺失/原有损坏JAR单独列为环境问题；新增源码编译错误单独列为本次问题。模拟对象验证只能称离线单元测试，不能称NC真实测试。

## 2026-11-07 集成批次实际结果

命令：`powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 -Test`。最后一次稳定源码构建退出码 **0**，全部离线测试通过。输出见工程 `build/build-final.log`，源码SHA256及成功标记见 `build/build-result.json`；两者为构建产物，不提交Git。

编译使用NC Home自带IBM JDK7，34个public、40个private、18个client源码，以及6个原有GBK示例；9个测试源码编译。新代码UTF-8，原示例不修改。编译器出现旧API提示；没有新增依赖升级。已检出的编译字节码均为major 51。

| 测试组 | 实际通过断言 | 验证内容和边界 |
|---|---:|---|
| CoreTests | 44 | 开关组合、请求状态、选择防篡改、父子关系、字节长度/月界、字段差异、敏感字段、空快照拒绝、线程隔离；纯算法 |
| DispatcherTests | 5 | 认领先于业务、异常原样传播、独立失败回调、持久化失败附加异常、边界失败不执行；使用明确测试替身，不证明CMT提交 |
| IdentityConfirmationContractTest | 15 | 密码数组拷贝/清理、序列化及输入契约；不调用真实认证/锁定 |
| SignatureClientTests | 51 | 取消/失败/重复操作、外层生命周期及真实pubapp CompositeActionInterceptor调用顺序；无NC窗口或业务服务 |
| ManagementClientTests | 16 | 北京时间转换、CSV文件发布与已加载ID集合；本地文件/格式测试 |
| PersistenceTests | 21 | CSV/BOM/中文/引号/上限、月份/表名/保留规则、全PK扫描边界；不连接Oracle |
| PermissionRulesTests | 16 | 同subject按钮与组织交集、跨角色串权拒绝、管理显式授权和集团权限；不调用真实RBAC服务 |
| TypedBusinessAdapterTests | 35 | typed getter/元数据fixture下的对象/BU/结果关联及不支持动作拒绝；无真实DAO或NC原业务调用 |
| **合计** | **203** | **仅离线断言，不是203项NC业务验收** |

结构验证：`tests/persistence/check_schema_contract.py`实际通过17张表、16个Java字段映射及历史DDL范围/标识检查。8份本项目UPM XML可解析，13个接口注册无重复；业务外层为NONE，事务服务为local-only CMT。操作目录仍为35节点/243项，不代表这些节点已接入。

首轮离线批次两次连接QDNC均在连接阶段超时，工具报告operationOutcome=not_started；该批未执行SQL。随后恢复的只读核对见后续批次；NC原生认证、JNDI事务状态、独立认领提交、原业务回滚、全部实际节点、权限注册和原功能回归仍未验证。

### 过程中真实失败及修正

- 首次统一UTF-8编译无法读取原有GBK示例注释；构建改为分组编码，不改原示例。
- 管理窗口文件测试初次使用sandbox默认临时目录遇到访问限制；构建固定使用workspace的build/test-temp。
- 并行修改价目表多对象拒绝规则时，一轮测试仍使用旧编译类，拒绝断言失败；冻结源码后重新完整编译通过。构建现比较前后源码哈希，源文件变化会失败而不发布成功结果。
- 真实VO setter在离线环境触发平台元数据缓存且缺少远程配置，未通过；后续适配单元测试使用明确fixture，只验证委托边界，未将其称为NC业务通过。
- IBM JVM输出Windows Preferences注册表权限警告；最终测试进程仍退出0。本轮未修改注册表权限或NC配置。

## 候选包检查

`powershell -NoProfile -ExecutionPolicy Bypass -File tools/package.ps1 -SkipBuild`实际执行成功。包内13个本项目JAR、125个生产class、8份UPM；ZIP和内嵌JAR完整性检查通过，无重复class条目，class均位于本项目qd/esign包段且major51，没有测试类、示例Application、Git数据或Python缓存混入生产JAR。

打包前核对107个Java源文件的SHA256与已通过测试的构建记录一致；有新增/删除/修改即拒绝跳过构建。初次打包进程未加载Compression枚举程序集而失败，已显式加载System.IO.Compression后重新执行成功。该修正只影响构建工具，不涉及NC业务或配置。

产物是离线候选包，未安装到NC Home。菜单/节点示例放在examples区域，不作为已启用配置。所有实际部署与数据库脚本执行仍需按部署手册逐项确认和验证。

### Typed适配与权限离线验证（2026-11-07）

| ID | 实际命令和结果 | 范围与限制 |
|---|---|---|
| V08 | IBM javac @build/typed-adapter-check.args退出0；IBM java -ea -cp build/typed-adapter-runtime.jar nc.test.qd.esign.TypedBusinessAdapterTests | 35断言通过：对象/BU伪造、存量归属替身、新旧配对、PK乱序关联、未支持动作/混合批次拒绝、真实返回编号；仅typed VO fixture，不执行NC业务/DAO |
| V09 | IBM javac @build/permission-check.args退出0；IBM java -ea -cp build/typed-adapter-runtime.jar nc.bs.uapbd.qd.esign.security.PermissionRulesTests | 16断言通过：同subject按钮+BU、角色间不串权、无按钮管控、严格管理按钮、规则组织；未执行NC profile服务/orgType查询 |
| V10 | 实现人员执行PersistenceUtilityTest与tests/persistence/check_schema_contract.py | PersistenceTests21断言、17表/16映射及历史DDL契约通过；无Oracle执行/NC事务测试 |

V08首次直接使用原生VO.setter/聚合构造元数据时因未配置NC远程URL而失败。随后测试明确使用typed getter和最小IBillMeta fixture隔离平台；生产适配未绕过元数据，fixture结果不算真实NC测试通过。

## 2026-11-07 测试库恢复后的实际结果

| ID | 已实际执行 | 结果与边界 |
|---|---|---|
| V11 | QDNC固定会话SELECT：环境、结构、注册、插件、监听器及日志形态样本 | 成功：41节点/1385按钮/72参数、10业务表1208列及32个BU；原始SQL/结果见site-evidence；无数据写入 |
| V12 | prepare-site-bindings.py；check_site_evidence.py | 35/243正式范围与41/312物理映射、真实按钮、物料白名单列、两个交易类型、24表SQL指纹及旧表隔离检查通过；全部绑定禁用 |
| V13 | prepare-test-database.py；check_schema_contract.py | 固定24表安装包已生成；17核心表/16Java映射及历史DDL范围/标识检查通过；尚未执行DDL或合成数据测试 |
| V14 | IBM JDK7完整编译，tools/build.ps1 -Test退出0 | 34public、41private、18client、6原有示例及9测试源码通过；Core51，其余组为5/15/51/16/21/16/35，共210离线断言通过；新增7条GBK保护检查，不调用NC服务或Oracle写入 |

首次交易类型核对误用了非现场列名，Oracle返回ORA-00904；随后依据user_tab_columns改为PK_BILLTYPECODE/PK_BILLTYPEID，成功取得真实定义。一次重复启动构建导致platform-classpath文件占用，该重复进程退出1；保留原构建继续，最后完整编译/测试退出0。中间失败未计为验证成功。

原生VO的getLogdate/getLogoffdate已再次用javap核实为UFDate，time为UFTime；现场日期列长19并不表示VO会拼出双时间。50条样本不能代替全部源日志质量与保留期验证。

本批已申请用户确认固定install.sql及010_test_database_smoke.sql，收到确认前保持只读。真实Oracle写入回滚、NC事务/认证、业务回归和243项验收仍未通过。

## 2026-11-11 销售订单 209–216 S0 只读取证（非测试批次）

本批**未运行构建、未运行离线测试、无 NC 测试**。仅做只读取证与文件哈希冻结，记录如下，避免与其他批次的"通过"混淆。

| 项 | 实际动作 | 观察结果 | 结论与边界 |
|---|---|---|---|
| S0-A | 只读解析 `site-evidence/2026-11-07/buttons_1.json`、`node_ui_params.json`、`operation-bindings.csv`、`operation-scope.csv` | 8 按钮注册行齐全（`Delete/Commit/UnCommit/Approve/UnApprove/Freeze/UnFreeze/BillClose`，`isenable=Y`、`btnownertype=0`）；`fs_delete` 与 `fs_browse/fs_download/fs_rename/fs_upload` 同组，判定附件按钮组；`4TO2` 无"上级按钮"标记 | 静态核实；pagecode/pk_page、按钮级参数、角色授权、真实点击链路**未核实** |
| S0-B | 只读打开 `modules\so\client\lib\uiso_salesorder.jar`（904074B/`359F4DD2…`）与 HOME 平台 JAR，`javap` 反编译 | 8 项 bean/action code、PF 五项链（DELETE/SAVE/UNSAVE/APPROVE/UNAPPROVE）、冻结/解冻/关闭三项 `ISaleOrderCloseManageMaintain` 签名、审批窗口与 `WorkflownoteVO` 载体、`PfUtilClient` 哈希 `1448DB24…6689` 与本机一致 | 静态核实；服务端 `PlatFormEntryImpl` 方法体、弹窗落库值**未核实** |
| S0-C | 逐文件 SHA-256 冻结；只读比对 `build\deployment\20261111-155019-9df7af95\plan.json` 与 HOME | 当前 990 快照 21/21 逐字节一致；保存实现 11 对源文件 10 对一致（`SaleOrderPilotPolicyService` 仅 990 包）；`publish.json` 四段链一致（`7D0BEC07…`/`1E130A22…`） | 静态核实；数据库实际状态**未核实**（未执行 SQL） |
| S0-D | 只读核对 `build\sales-990-isolated\build-record.json`、`build\sales-990-balance-fix\stage-…\publish.json`、HOME 两份 upm | `build-record.json` 冻结旧源码（`DC6A6A84…`/8555B）与当前源码（`3008DE55…`）及 HOME（`1E130A22…`/10021B）均不一致 → `-Verify` 必然失败；HOME `qd_esign_saleorder.upm` 665B 单组件，缺 `ISaleOrderPfSaveAdapter` | 缺口 A/B/C，详见 `s0-evidence-20261111/S0_EVIDENCE_209-216.md` §4.4；未运行 `-Verify`（会写项目 build） |

**未执行**：`tools/build.ps1 -Test`、`sales-order-990-release/build.ps1`、`package.ps1`、任何 SQL、任何部署、任何 NC 启动/登录。因此本批**没有任何可计入"实际测试通过"的结果**。

**待补**：需要在能写项目目录或隔离副本的条件下重建 990 构建记录并跑离线测试；真实 NC 会话逐按钮取证后才能升级"静态核实"。

## 2026-11-11 销售订单其他按钮 S1 单笔公共链路（离线测试与 990 隔离构建）

本批**实际运行了构建与离线测试**，结论与边界如下。

| 项 | 实际命令 | 观察结果 | 结论及边界 |
|---|---|---|---|
| S1-B1 | `powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1` | 退出码 0，237s；5 个测试类全通过：`SaleOrderPfSaveAdapterTests`(26)、`SaleOrderPilotConfigTests`、`SaleOrderPilotPolicyServiceTests`、`SaleOrderOtherButtonsTests`(169)、`SaleOrderActionHandoffTests`(45)；输出 `ISOLATED SALES BUILD PASSED`；日志 `build/s1-build-990.log` | 990 兼容包隔离构建通过；记录 `build/sales-990-isolated/build-record.json`（`tests_passed=true`、47 类、冻结输入 `0C33112E…`） |
| S1-B2 | `... build.ps1 -Verify` | `Verified isolated build inputs and compiled classes.`，退出码 0 | 源码/资源/JDK/依赖与编译产物已冻结一致；**S0 缺口 A（旧构建记录冻结热修前源码）解除** |
| S1-T1 | `powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1 -Test` | 最终退出码 0（249s，日志 `build/s1-build-main3.log`）：编译 public111/private150/client111/legacy-examples6/test120 全部通过；`ALL OFFLINE TESTS PASSED`；`BUILD PASSED`。构建记录 `build/build-result.json`（`schema_version=2`、`tests_passed=true`、958 类、`11:05:03Z`、`class_artifacts_sha256=A42E081D…`）；`-VerifyRecord` 退出码 0 | 主工程整包构建通过且记录与当前输入一致。此前三次失败均为环境/并行会话写入导致（`build/s1-build-main.log`、`-retry.log`、`-main2.log` 保留），与本批代码无关；并行会话仍使用同一工程目录，候选发布前需串行化写入窗口重跑 |
| S1-T2 | 上述构建中的 `SaleOrderOtherButtonsTests` | 169 断言通过：8 按钮固定路由、动作域归类、错节点/未登记按钮/错组织/错动作/多选/子对象/缺主键拒绝、未启用动作零原生调用、Save 单次委托、类型化命令副本隔离 | 仅离线契约检查，不调用 NC/Oracle |
| S1-T3 | 上述构建中的 `SaleOrderActionHandoffTests` | 45 断言通过：8 按钮交接、Save 不接管、错组织/错动作/多选/子对象拒绝、一次性绑定与派发、按钮/组织/对象变化拒绝、释放语义 | 仅离线交接契约，不打开界面、不调用服务 |
| S1-T4 | 上述构建中的 `SaleOrderPilotPolicyServiceTests` | 通过：9 个销售范围在非 990 开启被拒、关闭放行、其他节点原样委托 | 包装器逻辑离线验证；真实策略表未读写 |

**未执行**：真实 NC 联调、原生 PF/RPC 写入、冻结/解冻/整单关闭实际业务、审批窗口「确定」、ATP 继续/取消、关闭原因落库、同事务回滚、权限/工作项、多选，以及任何 SQL、部署、NC 启停。

**构建记录交付**：以 `build/sales-990-isolated/build-record.json` 为准（`-Verify` 通过）；主工程整包记录待客户端元数据缓存环境恢复后重跑 `tools/build.ps1 -Test` 补齐。

## 2026-11-11 销售订单其他按钮 S2 直接服务（离线测试与 990 隔离构建）

| 项 | 实际命令 | 观察结果 | 结论及边界 |
|---|---|---|---|
| S2-B1 | `powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1` | 退出码 0，220s；6 个测试类全通过：`SaleOrderPfSaveAdapterTests`(26)、`SaleOrderPilotConfigTests`、`SaleOrderPilotPolicyServiceTests`、`SaleOrderOtherButtonsTests`(167)、`SaleOrderActionHandoffTests`(45)、`SaleOrderStateAdapterTests`(44)；`ISOLATED SALES BUILD PASSED`；日志 `build/s2-build-990.log` | 990 兼容包隔离构建通过；记录 `build/sales-990-isolated/build-record.json`（`tests_passed=true`、100 类、冻结输入 `9E56070A…`） |
| S2-B2 | `sales-order-990-release\build.ps1 -Verify` | `Verified isolated build inputs and compiled classes.`，退出码 0 | 本批源码/资源/JDK/依赖与编译产物一致 |
| S2-T1 | `powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1 -Test` | 第一次尝试（日志 `build/s2-build-main.log`）：public114/private153/client114/legacy6 编译通过；测试层失败于并行会话正在编辑的 `PartnerBatchChunksTests.java:32-34`（引用尚不存在的 `QdPartnerBatchDispatcher.dispatchFailure`）。并行会话暂停后重跑（日志 `build/s2-build-main2.log`）：**退出码 0**，427s，`ALL OFFLINE TESTS PASSED`，`BUILD PASSED`；记录 `build/build-result.json`（`schema_version=2`、`tests_passed=true`、1023 类、`12:23:59Z`、`class_artifacts_sha256=3789B5DE…`）；构建当次 `-VerifyRecord` 退出码 0 | 主工程整包构建通过。**记录随后漂移**：20:25–20:26 并行会话修改 `InventoryPfPreparation.java`、`QdPurchaseInConfig.java`、`QdPurchaseInSaveAction.java` 及保存试点 `SaleOrderPfPreparation.java`（放宽 `field/method` 可见性），故当前 `-VerifyRecord` 报输入不一致——属共享工程并发写入，非本批代码；本批权威记录为 990 隔离构建（`-Verify` 通过） |
| S2-T2 | 上述 990 构建中的 `SaleOrderStateAdapterTests` | 44 断言通过：启用边界；冻结/关闭成功（原生服务一次、结果身份一致、结果回填）；关闭原因缺失/首尾空白/与行 `vclosereason` 不一致/缺整单行拒绝；冻结拒绝关闭原因与检查参数；解冻仅接受 ATP 继续标记且当前关闭；主键/组织/动作域不一致拒绝；原生拒绝不重试；返回身份不一致拒绝；`OrderPhysicalSnapshots`（HOME 已部署类）识别 `ISaleOrderOperationAdapter`+UPDATE/DELETE/INSERT | 仅离线契约；真实 NC、事务、弹窗与审计故障注入未验证 |
| S2-T3 | 上述 990 构建中的 `SaleOrderPilotConfigTests` | 通过：`freezeAction`/`closeAction` 使用签名覆盖类；`unFreezeAction`/`deleteAction` 保持原生；保存覆盖与原生卡片刷新仍在 | XML 解析层通过；真实客户端加载顺序待联调 |
| S2-T4 | 上述 990 构建中的 `SaleOrderPilotPolicyServiceTests` | 通过：未接入受控执行的按钮（UnFreeze/UnApprove/Delete 等）禁止开启策略；已接入范围仍限 990；关闭与其他节点原样委托 | 包装器逻辑离线通过；真实策略表未读写 |

**未执行**：真实 NC 窗口、客户端 bean 覆盖实际加载、关闭原因输入与整单行补齐、解冻 ATP 恢复协议、网关审计提交故障注入、同事务回滚、权限/工作项、任何 SQL、部署、NC 启停。

**交付构建记录**：以 `build/sales-990-isolated/build-record.json` 为准（`-Verify` 通过）；主工程整包记录待并行会话停止写入后重跑补齐。

## 2026-11-11 销售订单其他按钮 S3 PF 基础动作（离线测试与 990 隔离构建）

| 项 | 实际命令 | 观察结果 | 结论及边界 |
|---|---|---|---|
| S3-B1 | `powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1` | 退出码 0；7 个测试类全通过：`SaleOrderPfSaveAdapterTests`(26)、`SaleOrderPilotConfigTests`、`SaleOrderPilotPolicyServiceTests`、`SaleOrderOtherButtonsTests`(164)、`SaleOrderActionHandoffTests`(45)、`SaleOrderStateAdapterTests`(47)、`SaleOrderPfActionAdapterTests`(43)；`ISOLATED SALES BUILD PASSED`；日志 `build/s3-build-990.log` | 990 兼容包隔离构建通过；记录 `build/sales-990-isolated/build-record.json`（`tests_passed=true`、127 类、冻结输入 `0306A825…`） |
| S3-B2 | `sales-order-990-release\build.ps1 -Verify` | `Verified isolated build inputs and compiled classes.`，退出码 0 | 本批源码/资源/JDK/依赖与编译产物一致 |
| S3-T1 | `powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1 -Test`（第一次） | 全层编译通过；`ALL OFFLINE TESTS PASSED`（含本批 6 个销售测试类）；随后冻结校验报 `Inputs changed while building` | 未产出主工程记录；并行会话在构建期间持续写入 |
| S3-T2 | 同上（等待空闲后第二次） | 离线测试失败于并行会话正在编辑的 `PartnerBatchChunksTests`（`Unsent chunk did not report prior and remaining work accurately`，`AllTests` 第 96 行） | 与本批文件无关；主工程记录待串行化写入窗口后重跑 |
| S3-T3 | 上述 990 构建中的 `SaleOrderPfActionAdapterTests` | 43 断言通过：提交/收回/删除各调用对应原生 PF 一次；交易类型/流程说明/用户对象/参数白名单核对；删除以 `dr=1`/不存在确认事实；返回身份不一致拒绝；原生拒绝不重试；Delete/Commit/UnCommit/Freeze/BillClose 服务端就绪、Approve/UnApprove/UnFreeze 关闭、策略层 Delete 不可开启；DELETE/UPDATE 审计识别 | 仅离线契约；真实 NC 流程准备、弹窗、工作项与事务未验证 |
| S3-T4 | 上述 990 构建中的 `SaleOrderPilotConfigTests` | 通过：`sendApproveAction`/`unsendApproveAction` 使用签名覆盖类；`deleteAction`/`unFreezeAction` 保持原生；保存与冻结/关闭覆盖仍在 | XML 解析层通过；真实客户端加载顺序待联调 |

**未执行**：真实 NC 窗口、`PfUtilClient` 版本实测、流程弹窗与工作项、通知时序、「保存并提交」真实开关组合、删除确认后签名、审计故障注入、同事务回滚、多选、任何 SQL、部署、NC 启停。

**交付构建记录**：以 `build/sales-990-isolated/build-record.json` 为准（`-Verify` 通过）；主工程整包记录待并行会话停止写入后重跑补齐。

## 2026-11-11 销售订单其他按钮 S4 审批动作（离线测试与 990 隔离构建）

| 项 | 实际命令 | 观察结果 | 结论及边界 |
|---|---|---|---|
| S4-B1 | `powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1` | 退出码 0；7 个测试类全通过：`SaleOrderPfSaveAdapterTests`(26)、`SaleOrderPilotConfigTests`、`SaleOrderPilotPolicyServiceTests`、`SaleOrderOtherButtonsTests`(162)、`SaleOrderActionHandoffTests`(45)、`SaleOrderStateAdapterTests`(49)、`SaleOrderPfActionAdapterTests`(48)；`ISOLATED SALES BUILD PASSED`；日志 `build/s4-build-990.log` | 990 兼容包隔离构建通过；记录 `build/sales-990-isolated/build-record.json`（`tests_passed=true`、128 类、冻结输入 `2A1BF49F…`） |
| S4-B2 | `sales-order-990-release\build.ps1 -Verify` | `Verified isolated build inputs and compiled classes.`，退出码 0 | 本批源码/资源/JDK/依赖与编译产物一致 |
| S4-T1 | `powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1 -Test` | 全层编译通过；`ALL OFFLINE TESTS PASSED`（含本批 6 个销售测试类）；随后报 `Inputs changed while building` | 未产出主工程记录；并行会话在构建期间（21:51–21:54）持续修改其他模块文件 |
| S4-T2 | 上述 990 构建中的 `SaleOrderPfActionAdapterTests` | 48 断言通过：审批调用原生 PF `APPROVE` 一次、取消审批调用 `UNAPPROVE` 一次；交易类型/流程说明/用户对象/参数原样传递；返回身份不一致拒绝；参数白名单；原生拒绝不重试；启用边界（仅 UnFreeze 关闭；策略层 7 项已接入、Delete 不可开启）；审批按 UPDATE 销售聚合审计 | 仅离线契约；真实审批窗口/通知/工作项未验证 |
| S4-T3 | 上述 990 构建中的 `SaleOrderPilotConfigTests` | 通过：approveAction/unApproveAction 使用签名覆盖类；deleteAction/unFreezeAction 保持原生；save/send/unsend/freeze/close 覆盖仍在 | XML 解析层通过；真实客户端加载顺序待联调 |

**未执行**：真实 NC 审批窗口与签名时序、`WorkflownoteVO` 实际内容（意见/决议/工作项/指定审批人）、非终审与退回分支、通知只发一次、插件时序、审计故障注入、同事务回滚、多选、任何 SQL、部署、NC 启停。

**交付构建记录**：以 `build/sales-990-isolated/build-record.json` 为准（`-Verify` 通过）；主工程整包记录待并行会话停止写入后重跑补齐。

## 2026-11-12 销售订单其他按钮 S5 批量与现场分支（离线测试与 990 隔离构建）

| 项 | 实际命令 | 观察结果 | 结论及边界 |
|---|---|---|---|
| S5-B1 | `powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1` | 退出码 0；8 个测试类全通过：`SaleOrderPfSaveAdapterTests`(26)、`SaleOrderPilotConfigTests`、`SaleOrderPilotPolicyServiceTests`、`SaleOrderOtherButtonsTests`(162)、`SaleOrderActionHandoffTests`(45)、`SaleOrderSelectionTests`(15)、`SaleOrderStateAdapterTests`(49)、`SaleOrderPfActionAdapterTests`(48)；`ISOLATED SALES BUILD PASSED`；日志 `build/s5-build-990.log` | 990 兼容包隔离构建通过；记录 `build/sales-990-isolated/build-record.json`（`tests_passed=true`、131 类、冻结输入 `E7F51322…`） |
| S5-T1 | `powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1 -Test` | 全层编译通过；`ALL OFFLINE TESTS PASSED`（含本批 15 项选择判定断言）；随后报 `Inputs changed while building` | 未产出主工程记录；并行会话 09:36–09:41 持续修改其他模块文件 |
| S5-T2 | 上述 990 构建中的 `SaleOrderSelectionTests` | 15 断言通过：单笔/多选首轮组织→受控；全部非首轮→原生；混合（任意顺序）→拒绝；缺失/空/集团缺失/空组织→拒绝；拒绝提示区分混合组织与归属不明 | 仅离线判定；真实多选与批量语义未验证 |
| S5-T3 | 静态取证（javap + 原生 XML） | 删除/提交/收回/审批/取消审批/整单关闭原生均有多选入口（`getSelectedOperaDatas`）；冻结/解冻仅 `getSelectedData` 单笔；八项均在列表与卡片工具栏绑定 | 静态核实；原生原子/逐单语义未核实 |

**未执行**：两张以上订单真实多选、原生原子批次或逐单提交语义、部分成功与逐对象回执、批量事务边界、任何 SQL、部署、NC 启停。

**结论**：批量受控写入按计划保持关闭；多选在策略开启时被拒绝且混合组织失败关闭，不会回落原生写入。S5 批量验收项未通过（需联调）。

## 2026-11-12 销售订单其他按钮 S6 候选发布与回退演练（离线打包）

| 项 | 实际命令 | 观察结果 | 结论及边界 |
|---|---|---|---|
| S6-B1 | `powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\build.ps1` | 退出码 0；8 个测试类全通过（26+15+45+48+49+162 断言 + 配置/策略）；记录 `tests_passed=true`、131 类、`input_sha256=439F571D…` | 990 隔离构建通过（含新增 SQL/打包器后的重新冻结） |
| S6-B2 | `powershell -NoProfile -ExecutionPolicy Bypass -File sales-order-990-release\package.ps1` | 退出码 0，109s；`SALES_990_CANDIDATE=build/qd-esign-sale-990-20261112-103155-3dfd554c.zip`；`SHA256=917D494B…84A4E`；打包前后两次 `-Verify` 通过；UPM 输出 "already bound to SaleOrderPilotPolicyService; preserved" | 隔离候选包生成成功；未部署 |
| S6-T1 | 候选包/manifest 核对 | `files=28`、`baseline_sha256` 21 个模块文件、`build_record_sha256=7617B032…`；ZIP 29 条目、487,843 B；6 份 SQL、2 份 UPM、evidence 构建记录齐全 | 候选内容完整；早期 10:25 缺 SQL 候选已删除 |
| S6-T2 | 关键 JAR 内部类抽查 | `qd_esign_so_public.jar`/`qd_esign_so_private.jar`/`qd_esign_uapbd_client.jar` 含本批 DTO、路由、选择判定、6 个客户端覆盖类及其内部类（`$Kind`/`$ContextFiller`/`$1`） | 覆盖类完整，避免部署后 `NoClassDefFoundError` |
| S6-T3 | `qd_esign_core.upm` / `qd_esign_saleorder.upm` 内容 | core 保留 `SaleOrderPilotPolicyService`；saleorder 维持 HOME 单组件基线 | UPM 与当前 HOME 已绑定状态一致；不新增第二组件（复用 `ISaleOrderOperationAdapter` 决策） |

**未执行**：映射/预检测试 SQL、真实 NC 联调、候选包部署、任何数据库写入、NC 启停。

**结论**：离线候选与回退材料已交付；按计划无真实 NC 验证前不得在目标组织启用任何入口。

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
## 2026-11-12 销售管理其他三节点（40060303 / 40060402 / 40060501，28 项）离线批次

批次/时间：2026-11-12；对象：销售订单关闭管理 217–228（12）、发货单维护 229–238（10）、销售发票维护 239–244（6）。
本批未部署、未执行任何 SQL、未启动/停止 NC、未连接数据库、未触发外部平台；真实 NC 会话核实 0 条。

构建与测试证据（冻结源码快照，因共享工作区存在并行写入）：
- `powershell -NoProfile -ExecutionPolicy Bypass -File sales-other-nodes-release\build.ps1` → 退出码 0；
  快照哈希 `B77067581B00E75DFFA72F160F7680D1279BFE3DB783AC9532E0EC160327944F`、
  内部记录 `1FC8E9DC5FC6BC1A98874836A96CA278384E01A01FC0F011EFBB5BB467ABFCEC`、
  内部冻结输入 `6F4319013C3BA3372D4BFFDE926A05EC4538E8B7094BF1A19CA17907A7A60320`、全量编译 1085 类。
- `sales-other-nodes-release\build.ps1 -Verify` → 退出码 0（快照、内部记录、每个类产物哈希一致）。
- 本批 9 个离线测试类全部通过，合计 1176 断言：
  SalesOtherNodeScopeTests 201、SalesOtherNodeSnapshotsTests 13、SalesNodeHandoffTests 45、
  SaleCloseAdapterTests 353、DeliveryAdapterTests 248、DeliveryClientConfigTests 105、
  SaleInvoiceAdapterTests 122、SaleInvoiceClientConfigTests 83、SalesPfBridgeTests 6。
- 正式候选包 `build/qd-esign-sales-other-nodes-20261112-133410-7516cdf9.zip`，
  SHA-256 `18BE91836366AD89A11B5FF23DAC8A574F5B73B8316759F2A0699CE0026E0732`，
  16 个文件、5 个基线 JAR、50 个覆盖类；`package.ps1 -Verify` 退出码 0。

全量 AllTests 未纳入本批记录：快照采样期间另一会话仍在修改销售订单文件（`SaleOrderActionRoute`/
`SaleOrderOperationAdapter`/`SaleOrderPilotScope`/`SaleOrderStateAdapterTests`，mtime 12:54–13:04），
当前工作区 `SaleOrderStateAdapterTests` 失败（与本批代码无关）。候选发布前须在串行冻结窗口重跑
`tools\build.ps1 -Test` 并复核 `-VerifyRecord`。

未执行验证及原因：三节点真实 NC 入口/权限/工作项、弹窗与确认顺序、ATP/信用/容差继续、
同事务回滚与审计故障注入、跨模块回写（应收/结算/凭证）、通知外发、批量语义均未验证；
三节点试点组织与账号未确认（M0 §2）；映射/回退 SQL 未在真实库执行；候选包未部署、回退未演练。

状态：**已开发但尚未在 NC65 环境验证**（离线构建与测试通过；不得据此在目标组织启用任何入口）。
详见 `SALES_OTHER_NODES_M0_20261112.md`、`SALES_OTHER_NODES_M1_M4_20261112.md`、`SALES_OTHER_NODES_M6_20261112.md`。
