# 库存试点只读检查

`preflight.sql` 先核对当前用户与表字段，再分别查看指定业务单元、库存组织、两个节点的客户端插件、按钮注册、节点映射和组织策略，并对比采购入库与委外加工入库的节点交易类型参数。交易类型参数仅用于后续建立经验证的服务端白名单，不能直接当作授权。绑定 `pilot_group`、`pilot_org`、`pilot_stockorg`；业务单元和库存组织之间的实际关系须在 NC 配置中确认。`ORG_STOCKORG` 及其字段来自本机 NC65 字典，`DR` 是 NC 通用约定，运行对应查询前先核对第一段现场字段结果。

`verify-request.sql` 按请求号、集团、业务单元和用户编码查看在线签名尝试、审计记录、库存表头及快照摘要。历史请求须使用项目既有的定位／历史查询服务；关闭签名和审计时没有请求记录属于可能的正常路径，不能把查询无结果当作业务成功或失败的证据。所有时间字段遵循表设计中的 UTC 约定。

首批单张删除开启审计时，若表体存在有效 `pk_batchcode`，候选实现将批次档案引用记录为 `snapshot_phase=BEFORE`、`entity_name=reference:scm_batchcode`；应结合删除前表体快照核对主键，不能将该引用解释为批次档案被删除。实际字段、集团共享规则和事务回滚尚需现场验证。

上述首批两个文件均为只读查询，未执行过现场数据库。表字段依据为本项目 `001_schema.sql`、组织策略建表文件、2026-11-07 现场只读列快照和本机 NC65 数据字典；两张货位表、`SCM_BATCHCODE` 及当前 `ORG_STOCKORG` 物理列还没有现场验证。上线注册、策略切换和 NC 事务回滚应在试点业务单元及客户端接入核实后另外处理。

`transin-preflight.sql` 和 `verify-transin-request.sql` 为调拨入库单张候选提供相同的只读检查。`IC_TRANSIN_H/B/L` 的字段依据本机 BMF，表间关联列以及 `DR`、`TS` 按 NC 结构约定处理，现场字段和组织语义尚未确认；先运行预检的字段清单，再判断后续查询是否适用。以上脚本尚未在 QDNC 执行。

`initial-in-preflight.sql` 和 `verify-initial-in-request.sql` 为期初余额单张候选提供只读检查。`IC_OPENBAL_H/B/L` 的字段来自本机 BMF，关联列和 `DR`、`TS` 仍须先在现场核对。导入直接调用 `IInitializtionInMaintain.saveForImport`，与批量保存、批量签字一起尚未完成接入及验证；不能因为单张候选存在就启用节点策略。这两个文件同样尚未在 QDNC 执行。

期初预检同时列出 `PUB_PLUGINITEM` 中的 `IC/m40/InsertAction` 扩展，核对新增后置规则、其他插件及顺序。NC 规则列表在服务进程内缓存，数据库存在记录也不能证明各进程已加载；本项目候选规则尚未注册。

`locadjust-preflight.sql` 和 `verify-locadjust-request.sql` 对应货位调整保存、删除的单张候选。先确认 `IC_LOCADJUST_H/B/L` 的实际列，再以受权单据主键绑定 `bill_pk`，把表体、货位行数与 NC 客户端逐行核对；孤儿数为零不能单独证明关联完整。表体外键 `cgeneralhid`、货位外键 `cgeneralbid` 及 `DR`、`TS` 为候选结构约定，并非现场已验证事实。脚本未在 QDNC 执行。

`generalin-preflight.sql` 和 `verify-generalin-request.sql` 对应其他入库普通单张候选。`IC_GENERALIN_H/B/L` 字段来自本机 BMF，表体／货位关联列及 `DR`、`TS` 仍是待现场验证的结构约定。脚本不覆盖来源／生成单据副作用或独立批量入口，尚未在 QDNC 执行。

`productin-preflight.sql` 和 `verify-productin-request.sql` 对应普通产成品入库单张候选。预检分别列出普通节点 `40080604` 和定制投产单节点 `40080604400` 的交易类型参数，以供现场核对，不能合并两者授权。`IC_FINPRODIN_H/B/L` 的关联列及 `DR`、`TS` 仍待核实，脚本未在 QDNC 执行。

`subcontin-preflight.sql` 和 `verify-subcontin-request.sql` 对应委托加工入库单张候选。预检同时列出委外加工入库节点 `40080602400` 的参数供比较；两者的节点、VO 和交易类型范围独立。`IC_SUBCONTIN_H/B/L` 的关联列及 `DR`、`TS` 尚待现场核实，独立批量入口与来源单据副作用未覆盖；脚本尚未执行。

`generalout-preflight.sql` 和 `verify-generalout-request.sql` 对应其他出库单张候选。`IC_GENERALOUT_H/B/L` 的关联列及 `DR`、`TS` 尚待现场核实，独立批量入口与关联单据副作用未覆盖；脚本尚未执行。

`materialout-preflight.sql` 和 `verify-materialout-request.sql` 对应材料出库单张候选。`IC_MATERIAL_H/B/L` 的关联列和 `DR`、`TS` 尚待现场核实；单据类型组件是 `ic_materialout`，不能由物理表前缀推定。现场插件、批量与关联单据副作用未覆盖；脚本尚未执行。

`transout-preflight.sql` 和 `verify-transout-request.sql` 对应调拨出库单张候选。前者分别查询 `4Y` 与 `4y` 的类型记录，须结合实际客户端传入 PF 的类型和集团共享语义核对；大小写不能仅凭元数据查询归一。表关联列、`DR`、`TS`、批量及关联单据仍待验证，脚本尚未执行。

`custom-in-preflight.sql` 和 `verify-custom-in-request.sql` 对应委外加工入库和投产单的独立候选。两者分别限定 `45-Cxx-08`、`46-Cxx-2`，须核对类型主键、集团、节点、组件及草稿填充时机；表字段和组织关系也须先确认。脚本只读且尚未执行，不覆盖批量或关联单据事务。

`sapply-preflight.sql` 和 `verify-sapply-request.sql` 对应出库申请 8 项动作的单张候选，并列出行操作的授权子对象。QDNC 只读核对发现 `IC_SAPPLY_E` 动态表通过表体主键关联，实际字段及关联仍须结合业务核对。预检列出节点按钮及 `m4455` 现有 BP 规则，不能仅凭数据库记录证明服务进程加载了规则。审批最终确认、行操作、复合入口与批量仍待完善或现场核验；两个脚本未在 QDNC 完整执行。2026-11-14 仅以不存在的请求号执行了新增的两条行操作核对查询，确认 Oracle 可解析并返回零行，不是业务验收。

行操作核对查询分别显示授权行当前状态和该行 `ISCLOSED` 的审计前后值。当前值可能被后续操作改变，查不到审计也可能是审计关闭；均不能单独证明先前事务已经提交或回滚。查询按请求、集团、组织及用户限定，并显示无法找到的单据或行；现场还需结合业务日志和关联数据核对。

`sapply-rule-preflight.sql` 在确认 `PUB_PLUGINITEM` 物理列后，只读核对九个 BP 的 18 个前后规则类：`MISSING` 为没有匹配登记，`DUPLICATE` 为匹配多条，`PRESENT_REVIEW` 仅表示有一条登记。`MATCHING_CANDIDATE_FIELDS` 另外统计符合当前候选扩展类型、模块、目标类及适用范围等字段的行；不为 1 时必须复核具体登记，即使为 1 仍需核对执行顺序、各进程实际加载及事务传播。本机加载器对组件键、事件类型和扩展类型的匹配区分大小写；预检会列出大小写不同的疑似登记，但不会把它计为符合候选字段。本机规则加载器的该查询未过滤 `DR`，本预检因此统计所有匹配记录；逻辑删除本身不能证明规则不会加载。该脚本未在 QDNC 执行，不会注册规则或启用策略。

各节点预检还查询本集团 `BD_BILLTYPE` 及节点交易类型参数，核对类型主键、编码、集团、节点、父类型和组件；当前候选不推断共享集团的授权。候选执行期间对类型记录使用 `FOR UPDATE NOWAIT`，还需核实数据库账户的锁权限；同类型并发可能冲突，须在现场测试并解决性能问题后才能启用。
