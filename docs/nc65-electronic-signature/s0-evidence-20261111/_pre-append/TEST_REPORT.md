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
