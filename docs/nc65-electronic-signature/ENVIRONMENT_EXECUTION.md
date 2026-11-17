# NC65测试环境实施记录与操作手册

日期：2026-11-07。源码工程为 `E:\02-Project\QiDu\QD-E-Siganature`，目标依赖HOME为 `E:\01-Home\NC65home\QuDuTest\nchome`。

## 当前实施边界

用户已确认这是共享测试环境，有其他用户或后台业务；试点用户为yonyou02，物料和销售订单使用新建测试样本。用户随后自行在IDEA中启动“QD-E-Siganature - 中间件”和“QD-E-Siganature - 客户端”。本次沿用这些进程，没有再次启动或停止真实NC，没有安装本次候选包或执行数据库写入。

本轮已完成构建和部署工具实现、隔离fixture验证、实际IBM JDK7完整构建及239项离线断言；真实认证、原业务CMT和243项业务验收尚未通过。不能把运行标签、主页HTTP200、工具fixture通过记为电子签名验收通过。

## 已交付工具

| 入口 | 实际行为 |
|---|---|
| `tools/build.ps1 -Test` | 只读引用HOME；清理工程内五个编译层的旧class，重新编译及运行离线测试。失败先使旧成功记录失效；成功冻结源码、构建脚本、UPM、config/sql、JDK、实际依赖及class指纹。 |
| `tools/build.ps1 -VerifyRecord` | 只读复核schema2构建凭据，拒绝源码、依赖、工具链、资源、class或HOME漂移。 |
| `tools/package.ps1 -SkipBuild` | 打包前后核验凭据；检查生产类命名段、major51及重复；生成含release-manifest.json和evidence/build-result.json的候选ZIP。不会安装。 |
| `tools/nc-runtime.ps1 -Mode Inspect` | 生成脱敏HOME/JDK/启动/端口/既有V1预检；不执行Java、不连库、不访问应用。无法观测的状态保留unknown。 |
| `tools/nc-runtime.ps1 -Mode Start` | 要求有来源文件及哈希的准入证据，拒绝活动共享环境、未知占用和不适用的调试地址。仅子进程指定JDK，调用既有入口；不编辑HOME。启动本身可能触发NC后台任务，不能脱离准入执行。 |
| `tools/nc-patch.ps1 -Action Plan/Backup` | 锁定包和目标HOME，输出21个模块文件的增改、原/新哈希及恢复动作；读取原文件和指定配置，在工程build目录生成私有ACL备份。 |
| `tools/nc-patch.ps1 -Action Deploy/Verify/Rollback` | 仅处理固定13个项目JAR和8个UPM；拒绝路径逃逸、非白名单文件、缺失备份和文件漂移。用journal恢复本次修改，不操作SQL、产品包、业务数据或审计日志。 |
| `tools/prepare-acceptance.py` | 从正式清单生成243条逻辑操作、312条物理入口及场景记录，全部NOT_RUN。不得填入密码或令牌。 |

备份工具是本次补丁受影响文件与指定配置的备份，**不是整套HOME/数据库的一致性快照**。在活动环境生成的读取备份，也不能代替维护窗口内的冻结与恢复验证。

## IDEA两端

服务端和客户端已有用户创建的NCCloud运行配置，项目SDK已由用户配置为QiDu-1.7。保留用户运行配置，不改写全局SDK，不重启正在运行的实例。

另提供普通Application配置“NC65 Client - local 18080”：独立NC65-Client-Bootstrap模块，只读引用UAP_Login_v6.jar及目标IBM7的plugin.jar/deploy.jar，入口为已核验的nc.starter.ui.NCLauncher；显式连接http://127.0.0.1:18080，使用工程build/client-run工作目录，不执行Make。该替代配置已做结构和入口依赖静态检查，未另起重复客户端。

插件右键重量端启动Action存在重启当前RunContent的分支，不能把它视为只启动客户端的可靠入口。使用已明确选定的客户端运行配置，避免误重启中间件。

## 本轮真实环境核验

- 只读连接“齐都药业测试库”，确认SESSION_USER/CURRENT_SCHEMA为QDNC、DB_NAME为DB、字符集ZHS16GBK，24张QD_ES_*表。
- 当前243条节点均未验证，总控和策略表均0行。未执行安装、升级、启用或清理。
- Oracle索引为79个NORMAL加6个LOB内部索引；24主键、12唯一约束。不能把85个总索引误报为新增了6个业务索引。
- 用户启动的服务端进程使用目标HOME的IBM7，并监听127.0.0.1:18080；启动初期主页超时，随后GET根路径返回HTTP200。客户端Yonyou NC窗口已由系统窗口枚举确认，登录和业务节点状态仍需实际验证。
- 本轮宿主只读查询确认Windows11 x64、约31.5GiB内存；这是当前运行事实，不能由历史UAP兼容表推定所有功能均受支持。

原始只读数据库结果见 `environment-execution/20261107/readonly-database.json`；构建、工具测试、预检及部署备份状态存工程build目录，不提交凭据或配置原文。

## 既有环境故障及冲突

1. 2026-11-07本次启动中，空的messages/offline/board.xml导致OfflineBoard解析EOF；该文件为历史0字节资源。已定位直接原因，未修改或移动该文件，也未推断它必然造成所有HTTP500。
2. 存在seeyon、SysHr2NC、监控组件缺类、cmp副本目录及重复组件等既有加载异常；SO客户端seeyon-ctp-core.jar确认为不可读归档。它不在本轮编译采用的有效依赖清单中。
3. 启动后实际后台调用出现ServiceDispatcherServlet HTTP500。旧web.xml入口过滤器SignIngressFilter映射了该服务，并要求ISignSourceBoundary；现存注册/类资源未找到对应服务，是需进一步确认的V1冲突线索，尚未取得500服务端完整根异常。
4. 既有V1配置及probe的DENY行为没有被本次工具关闭。不能为排障直接移除共享环境的旧签名控制。

先在可协调的维护窗口核对旧扩展来源和完整栈，再选择恢复同版本资源或隔离失效扩展；必须保留备份、明确影响范围并回归。不得通过覆盖新补丁来掩盖原环境故障。

## 操作顺序

下列命令均从源码根目录执行。当前活动共享环境只执行Inspect、Plan及读取备份；Deploy、Rollback和新起服务必须满足实际准入。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 -Test
powershell -NoProfile -ExecutionPolicy Bypass -File tools/package.ps1 -SkipBuild
powershell -NoProfile -ExecutionPolicy Bypass -File tools/nc-runtime.ps1 -Mode Inspect -OutputPath "E:\02-Project\QiDu\QD-E-Siganature\build\runtime-inspect.json"
powershell -NoProfile -ExecutionPolicy Bypass -File tools/nc-patch.ps1 -Action Plan -NcHome "E:\01-Home\NC65home\QuDuTest\nchome" -Package "<候选ZIP绝对路径>"
powershell -NoProfile -ExecutionPolicy Bypass -File tools/nc-patch.ps1 -Action Backup -DeploymentId "<Plan返回的ID>"
```

维护窗口内，环境负责人提供environment_isolation、database_identity、outbound_and_jobs_isolation、backup_and_rollback、jdk_baseline和v1_compatibility各项真实证据文件及哈希。共享环境还须有使用人排空、后台任务暂停、维护负责人及有效时间窗。准入不是自动生成的通过结论。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/nc-patch.ps1 -Action Deploy -DeploymentId "<ID>" -Admission "<工程内准入JSON绝对路径>"
powershell -NoProfile -ExecutionPolicy Bypass -File tools/nc-patch.ps1 -Action Verify -DeploymentId "<ID>"
# 先关闭本项目控制和新增任务、保全证据、停应用，再按原批准窗口撤回文件。
powershell -NoProfile -ExecutionPolicy Bypass -File tools/nc-patch.ps1 -Action Rollback -DeploymentId "<ID>" -Admission "<工程内准入JSON绝对路径>"
```

该工具只安装模块文件，不能代替节点/权限/最终RPC/同步审计hook注册。已有24表安装包不重跑；升级仍需明确增量脚本。回滚保留签名、审计、安全日志和历史数据，不整体恢复共享数据库，不自动重试外部副作用。

试点顺序为原物料和销售订单保存基线、真实身份和权限、单对象签名及审计、故障注入与重放、原功能回归；仅通过的动作可考虑启用。后续逐项补齐243操作，不以试点替代全量验收。
