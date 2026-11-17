# Oracle 部署脚本

已连接QDNC完成结构/注册核对，并按用户确认安装24张本项目表及243条关闭目录。使用 NC SQL 技能的 Oracle 11g 约束，并以自建表设计为字段依据；原 NC 注册表仍须现场核实并确认脚本后修改。

本次固定包见`approval-20261107/install.sql`和`manifest.json`，已安装17核心表、7张202608历史表、243条未映射种子，不可盲目重跑。`010_test_database_smoke.sql`设计为仅写自建表并回滚合成数据，但被MCP范围校验拒绝，尚未通过。实际回执及已核验结构见项目docs/nc65-electronic-signature/database-execution/20261107；旧版QD_ESIGN_*表和节点保持原样。

`011_readonly_sql_checks.sql`包含10条从实际仓储SQL提取的只读检查，已在空QDNC自建表上执行通过；它不写数据，不证明事务、实际日志结果或数据规模性能。

## 执行顺序

1. 备份应用与数据库，核对目标测试库、用户、字符集、表空间和已有同名表。Oracle DDL 隐式提交，不能依靠 `ROLLBACK` 撤销建表。
2. 审批后执行 `001_schema.sql`、`002_indexes.sql`、`003_init.sql`。脚本没有自动 COMMIT；所有控制和节点策略默认关闭。
3. `004_platform_precheck.sql` 仅查询现场结构及现有规则。先检查第一段字段结果，再按已确认字段运行其余查询。不自动替换 `bd_billtype.checkclassname` 或原事件监听器。`008_scope_seed.sql` 是从正式243行清单生成的目录初始化，仅增加未映射、未验证条目，不覆盖现有映射。
4. 用 `generate_history.py --from-month YYYYMM --to-month YYYYMM --output <新文件.sql>` 离线生成明确月份的历史表脚本，再审批执行。脚本拒绝覆盖，最多一次生成120个月。不在运行时动态建表。
5. 核对初始化节点、功能权限、客户端扩展与服务端规则注册，逐项通过 NC 回滚及原功能回归后才允许启用策略。

`006_upgrade_from_1_0_0.sql` 是当前版本的只读升级前检；没有虚构后续升级。`007_disable_rollback.sql` 按指定集团关闭功能，不删除证据或原业务数据，不自动提交。将来每次结构升级使用新的不可变版本脚本。

## 数据约定

- 自建主键使用 UUID32；集团和组织保存 NC 原20位主键。所有时间列为 UTC `CHAR(19)`，格式 `yyyy-MM-dd HH:mm:ss`；`archive_month` 按 `Asia/Shanghai` 自然月计算，不能直接取 UTC 时间前6位。
- 中文人员、节点、按钮和字段显示名使用明确的 `VARCHAR2(200 CHAR)`；操作事由为 `VARCHAR2(4000 BYTE)`，服务器同时限制2000字符和4000 UTF-8字节，避免Oracle11g字节上限与中文长度不一致。确认所用数据库字符集能表示输入；不把事由改成会破坏日志集合查询的CLOB。
- 节点映射必须显式登记 `operation_types`（允许操作集合）与 `object_type`，防止用保存按钮授权执行删除；清单初始化不推测这些值。请求同时保存节点策略 `policy_version` 与总控 `control_version`，总控关闭再开启后旧授权不能恢复使用。
- `request_id` 贯通所有操作，签名及业务状态分别保存。`finished_at` 非空才允许归档相关记录。`qd_es_locator` 保留在线，关联跨月的请求与明细；不能用时间窗口猜配请求。
- 日志关系使用逻辑外键：在线与历史可跨月分布，不能建立会阻止搬移的物理跨表外键。完整性由同事务写入、唯一约束、核验及关联查询保证。
- 签名及审计查询字段冗余保存操作时名称、编码、组织和对象信息，不能因档案后续改名而失去当时语义。新增编码回写只发生在真实业务成功的事务内。
- `qd_es_security_key` 为在线幂等目录，防止原生安全事件归档后重复导入；不保存原认证详情或密码。
- `qd_es_sync_cursor` 与安全事件在同一 CMT 内更新。`NC_OPERATELOG` 的 `ts + pk_operatelog` 增量游标用于及时性；独立 `NC_OPERATELOG_RECONCILE` 游标反复按完整PK顺序重扫，周期到尾后从起点重置，补获同秒较晚提交或回填旧时间戳的事实，不修改原生日志。`cycle_started_at/cycle_completed_at` 记录完整重扫周期的UTC时间，便于监控是否赶上原日志清理期限。

## 归档运行契约

`MonthlyArchiveStore.archiveMonth(month, now, batchSize)` 必须在 NC CMT 中调用，`batchSize` 为1至900；每次返回每表移动量、`remaining` 可归档行数和 `pending_open` 未结束请求数。不自行提交事务。相同月份由目录行 `FOR UPDATE NOWAIT` 互斥。

每批先确认全部历史表及列存在，再复制、逐字段核验（CLOB 使用 `DBMS_LOB.COMPARE`）并删除同批在线副本。任何异常向外抛出，使调用者事务整体回滚。存在未结束请求时保留在线、目录标记 `PARTIAL`；应先按真实状态核对/结束请求，不能自动伪造业务失败。调度器须重查 `PARTIAL` 月份，而非只运行一次。在线保留当月及前两个月；活跃或待核对请求为保护证据暂留在线，并作为运维未完成事项显示。

脚本预建的月表在管理员批准后才可使用。缺表或结构不一致直接失败，不删除在线数据。历史不会自动清理或截断。

## 查询、导出与后台任务

- 日志远程服务分别注册 `QD_ES_SIGNLOG`、`QD_ES_AUDITLOG`、`QD_ES_SECURITY` 功能节点的 `QUERY` 和 `EXPORT` 按钮权限。客户端不能传组织白名单。只有明确授权的 `GROUP_SECURITY` 才可查询无业务单元的集团安全事件；不提供“未注册即放行”回退。
- 全部筛选导出使用一次 Oracle SELECT 的读一致性，不用循环 OFFSET 拼接变化中的数据；超过100000行或64MiB明确失败。UI的已加载页导出调用 `exportLoadedCsv`，提交至多1000个已加载事件ID，服务器在同一SELECT中再次核对ID集合、原筛选及组织权限；缺失或无权限时明确失败，不能静默少导记录。导出字段取服务器当前值，不能声称保留先前加载时的旧值。旧兼容接口 `exportCsv(...,true)` 仍按页重新查询，UI不使用它导出已加载页。
- 审计明细先对主记录进行组织授权，再从服务器实际请求号经 `qd_es_locator` 定位所有相关月份，分开查询差异和快照，避免对CLOB使用Oracle不支持的集合运算。每种明细分页最多1000行，单CLOB展示最多100000字符，并返回 `_truncated` 与 `_chars`；页面预览累计最多16Mi字符，超限要求缩小页数，不伪称完整展示。
- `MonthlyArchiveTask` 注册为每月1日北京时间01:00，并配置每日补跑/失败重试；`ExpiredRequestTask` 可每5分钟运行，只结束未执行的过期请求，保留既有签名成功事实，不推测RUNNING或外部调用结果。
- `NativeSecurityLogSyncTask` 可每分钟运行，需核对源日志时区（明确默认 `Asia/Shanghai`）。它只导入原生 `OperateLogVO` 中真实登录结果和实际写入的注销时间；原表没有账号编码或终端名时保留未知，不伪造字段。其他安全日志源的管理员锁定/解锁等事件尚未接入，不能把本适配器称为全部安全事件覆盖。
- `NativeSecurityReconciliationTask` 每日执行全PK重扫，复用同一安全事件幂等目录。重扫的下一周期才能捕获本轮已经越过的小PK较晚提交记录；这不等同即时无漏，也无法挽回在捕获前已被原系统清理的日志。现场必须验证原日志保留期大于完整对账周期，持续监控本轮完成时间；该条件未证实时不得宣称安全事件完整性已验收。
- 原生查询接口为 `nc.bs.pub.operatelog.itf.IOperatelogQueryService.queryByPKs(String[])`；源PK增量选择的表和字段来自实际VO及字节码，还必须由现场预检核对。存在无 `ts` 记录或不完整事件时间时明确报错、保留游标，不静默丢弃。
- 后台任务调用 `remote=false, tx=CMT` 的维护服务。任务引擎外层事务、服务调用嵌套和故障状态持久化必须在NC环境核实。任何失败向NC任务引擎抛出；不能把同事务内写入的错误状态当成必定独立提交的故障日志。当前没有使用未经验证的独立提交API。

## 目前验证范围

数据层 Java 已使用本地 NC IBM JDK 7 和真实 BaseDAO/SQLParameter 依赖编译验证；SQL 仅做结构一致性检查。尚未进行 Oracle 执行、NC 事务回滚、权限、查询性能及月度归档实测，不能标记这些功能已验收。
