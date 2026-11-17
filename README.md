# 齐都 NC65 电子签名与审计追溯

这是基于现场 NC65 私有依赖、IBM JDK 7 的二次开发工程，包含 `baseapp/uapbd/pu/so/ic/purp` 六个模块。源码使用 `public/private/client` 分层，保留原有示例和工程配置。

## 当前交付状态

已写入公共请求/签名/策略服务、原生认证桥接、NC客户端窗口、权限裁剪、日志查询/明细/CSV、归档与安全日志维护，以及有限的typed业务服务适配。所有节点种子默认未映射、未验证，不会自动启用业务控制。

**尚未达到35节点、243项操作的全量验收条件。** 2026-11-07 的原始快照核对了41现场节点及其1385条直属按钮，生成312条初始禁用的物理入口记录；QDNC当时安装了24张本项目表及243条关闭目录，之后另有物料试点。两个缺失节点已独立定位，日志身份与归档结果恢复缺陷已修复，完整JDK7编译和239项离线断言通过。回滚测试块被MCP范围校验拒绝，NC事务、客户端及全量动作联调仍未完成，见[核对报告](docs/nc65-electronic-signature/SITE_VALIDATION.md)。

2026-11-10 补充核对了功能节点下的页签按钮，找到了旧快照中48条未唯一匹配入口的注册；用户已确认原表“生成信息”指“生产信息”页签。完整编码和注册主键见[功能注册页签按钮核对](docs/nc65-electronic-signature/PAGE_REGISTRATION_20261110.md)。注册存在不等于电子签名已接入或验收通过。

## 本地构建与测试

日常开发按改动范围编译并测试。下面以出库申请客户端为例，先列出本次修改的源码，再指定相关测试：

```powershell
& .\tools\test-targeted.ps1 `
  -Source @(
    'uapbd/src/client/nc/ui/uapbd/qd/esign/SapplySaveCommitHandoff.java'
  ) `
  -TestClass @(
    'nc.ui.uapbd.qd.esign.SapplySaveCommitHandoffTests'
    'nc.ui.uapbd.qd.esign.SapplyClientConfigTests'
  ) `
  -Profile SapplyClient
```

脚本自动编译能在项目测试目录定位到的测试源码，运行其 `public static void run()` 或 `main(String[])`。按实际改动补充源码、直接受影响的调用方和测试；不传 `-TestClass` 时只编译。脚本默认选择最近一次带测试通过记录及各层编译产物的构建基线，也可通过 `-BaselineBuild` 指定 build 目录。其他未选源码来自基线，脚本会提示自基线以来发生变化的已记录源码，需根据改动判断是否加入验证。

`SapplyClient` 依赖清单经过出库申请客户端用例验证。其他模块可以使用默认的 `Platform` 配置，也可以通过 `-Profile Custom -ClasspathFile <清单路径>` 使用并验证各自的清单。清单每行一个依赖，支持 NC Home 相对路径或绝对路径，`#` 开头的行视为注释。脚本使用 NC Home 的 IBM JDK 7，关闭注解处理并将结果与日志写入 `build/targeted`，不覆盖完整构建记录。可通过 `-NcHome` 指定依赖目录，须与构建基线的 NC 版本匹配。

批次稳定并完成针对性检查后，执行完整构建和离线测试：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 -Test
```

默认使用项目配置中的NC65 Home；需要其他只读依赖目录时传 `-NcHome`。新代码以UTF-8编译，原有GBK示例单独编译；输出在 `build`，不写入NC Home。

离线断言覆盖纯算法、DTO、控制器和委托适配契约，**不证明原生认证、Oracle执行、CMT事务或NC窗口测试通过**。

构建通过后，使用该次产物生成候选 ZIP：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/package.ps1 -SkipBuild
```

打包仍会核验构建输入和编译产物摘要。需要直接重新构建并打包时，可省略 `-SkipBuild`。部署前阅读 `docs/nc65-electronic-signature/DEPLOYMENT_GUIDE.md` 与 `UNRESOLVED_ISSUES.md`。

## 需求和进度

物料-业务单元普通新增保存的试点部署、单业务单元配置、日志核对及撤回步骤见 [物料保存试点](docs/nc65-electronic-signature/MATERIAL_SAVE_PILOT.md)。

集团策略 1.2.0 的升级、权限和验收见 [基础档案集团策略](docs/nc65-electronic-signature/GROUP_BASE_POLICY_ACCEPTANCE.md)。物料-集团普通新增及修改的节点配置和复测步骤见 [物料集团保存试点](docs/nc65-electronic-signature/MATERIAL_GROUP_SAVE_PILOT.md)。

销售订单手工新增保存的电子签名候选接入及 990 试点准备见 [销售订单保存试点](docs/nc65-electronic-signature/SALE_ORDER_SAVE_PILOT.md)。现场流程、旧版 probe 和同事务回滚仍需实测，未据此宣称验收通过。

物料主档删除的受控适配与图片提交后清理机制（源码候选，未登记未部署）见 [基础档案工作记录](docs/nc65-electronic-signature/BASE_ARCHIVE_WORK_20261110.md) 与 `sql/nc65-electronic-signature/material-image-cleanup/`。

共享测试环境的启动预检、IDEA客户端替代配置、构建指纹、补丁清单与备份/撤回工具见 [环境实施记录](docs/nc65-electronic-signature/ENVIRONMENT_EXECUTION.md)。工具fixture验证使用隔离目录，不代表真实HOME已部署或NC业务验收通过。

- 正式操作目录：`config/qd-esign/operation-scope.csv`，243项，保留原Excel行号。
- 实施台账：`docs/nc65-electronic-signature/TASK_TRACKER.md`。
- 实际测试：`docs/nc65-electronic-signature/TEST_REPORT.md`。
- 数据库脚本：`sql/nc65-electronic-signature/`，固定24表包已安装；升级/注册变更仍须单独确认，010回滚用例尚未通过。

不包含生产凭证、不覆盖产品类、不修改NC Home、不提交或推送Git。
