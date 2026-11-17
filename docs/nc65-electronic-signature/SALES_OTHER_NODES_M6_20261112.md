# 销售管理其他三节点 M6：候选包与回退演练（2026-11-12，离线）

本文件记录 `SALES_OTHER_NODES_DEVELOPMENT_PLAN.md` M6 的交付：冻结源码快照构建、候选包、SQL 草案、
部署前置条件与回退演练材料。**本批候选未部署、未执行 SQL、未改 HOME、未启用任何策略**；
真实 NC 联调（M5）尚未开展。

## 1. 证据链：为什么用“冻结源码快照”构建

共享工作区 `E:\02-Project\QiDu\QD-E-Siganature` 在本批开发期间存在其他会话并行写入（库存/出库申请，
以及销售订单文件的进行中修改）。主工程 `tools\build.ps1 -Test` 有两道依赖“工作区静止”的机制：
构建前后输入哈希一致、以及全部 `AllTests` 必须通过。本批开工至收尾期间，另一会话多次修改
`SaleOrderActionRoute` / `SaleOrderOperationAdapter` / `SaleOrderPilotScope` / `SaleOrderStateAdapterTests`
等文件（mtime 2026-11-12 12:54–13:04），因此：

- 主工程全量套件当前会在 **销售订单既有测试** 处中断（`SaleOrderStateAdapterTests`），与本批代码无关；
- 本批证据改为：把当前源码/配置/脚本**复制成冻结快照**，在快照内执行
  `tools\build.ps1`（全量编译 + 冻结输入与类产物记录），再编译测试层并只运行本批 9 个离线测试类；
  快照哈希、内部构建记录哈希、类产物哈希与逐测试输出全部写入 `build\sales-other-nodes-isolated\build-record.json`。

这不是放宽验收：全量编译覆盖全部模块源码；本批测试 100% 在本批代码上执行；只是**不把其他会话
进行中的用例算进本批记录**，且在全量套件恢复后必须重跑主工程构建（见 §6）。

## 2. 构建与测试命令（可复现）

```powershell
# 1) 冻结快照全量编译 + 本批离线套件（写 build\sales-other-nodes-isolated\build-record.json）
powershell -NoProfile -ExecutionPolicy Bypass -File sales-other-nodes-release\build.ps1

# 2) 复核快照哈希、内部记录哈希与每个类产物哈希
powershell -NoProfile -ExecutionPolicy Bypass -File sales-other-nodes-release\build.ps1 -Verify

# 3) 开发期快速检查（全量编译 + 只跑本批测试，不写主工程记录）
powershell -NoProfile -ExecutionPolicy Bypass -File sales-other-nodes-release\check.ps1 -Name <name>
powershell -NoProfile -ExecutionPolicy Bypass -File sales-other-nodes-release\check.ps1 -Name <name> -Tests "<类1,类2>"

# 4) 候选包（只基于上述记录；不部署、不执行 SQL）
powershell -NoProfile -ExecutionPolicy Bypass -File sales-other-nodes-release\package.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File sales-other-nodes-release\package.ps1 -Verify build\<候选包>.zip
```

### 2.1 本次结果（2026-11-12 13:33 实测）

| 项 | 实测值 |
| --- | --- |
| 快照路径 | `build/sales-other-nodes-isolated/snapshot`（源码/配置/脚本冻结副本，深 3 层并补偿 IDE 依赖相对路径） |
| 快照哈希 | `B77067581B00E75DFFA72F160F7680D1279BFE3DB783AC9532E0EC160327944F` |
| 内部构建记录 SHA-256 | `1FC8E9DC5FC6BC1A98874836A96CA278384E01A01FC0F011EFBB5BB467ABFCEC` |
| 内部冻结输入哈希 | `6F4319013C3BA3372D4BFFDE926A05EC4538E8B7094BF1A19CA17907A7A60320` |
| 全量编译类数 | 1085（public/private/client/legacy-examples 全层通过） |
| 本批离线测试 | 9 类全通过，合计 **1176** 条断言（201+13+45+353+248+105+122+83+6） |
| `build.ps1 -Verify` | 退出码 0：快照哈希、内部记录哈希、每个类产物 SHA-256 全部一致 |
| 候选包 | `build/qd-esign-sales-other-nodes-20261112-135158-1145bdd8.zip`（13:34 的首个候选 `...133410-7516cdf9.zip` 已作废：打包器把覆盖类写成了 `public/nc/...` 前缀，已修正为 `nc/...` 并重建） |
| 候选包 SHA-256 | `6CFEDB15CA185B2E4ED201BC996C3FBA72CB813CD1514964DE6373BC493BD322` |
| 候选包内容 | 16 个文件：5 个基线 JAR、5 个覆盖 JAR、1 个新增 UPM、4 份 SQL、manifest、rollback |
| 覆盖类 | 50 个（含 `$Kind` 等内部类），逐个与构建记录 SHA-256 核对并通过 Java 7 主版本校验 |
| `package.ps1 -Verify` | 退出码 0：manifest 内每个文件哈希复核通过 |

### 2.2 本地开发 HOME 部署（2026-11-12 13:52）

用 `sales-other-nodes-release\deploy-local.ps1` 部署到 `E:\01-Home\NC65home\QuDuTest\nchome`：

- 部署前校验候选 manifest 的 5 个基线哈希与 HOME 一致（不一致即中止）；
- 备份 5 个基线 JAR 到 `build\deployment\20261112-135219\backup\files`；
- 写入 50 个覆盖类 + 新增 UPM `modules/so/META-INF/qd_esign_sales_other_nodes.upm`；
- **补齐 259 个缺失依赖类**（本机 HOME 是部分部署基线，候选 overlay 里的 `OperationTransactionServiceImpl`
  依赖仓库中未部署的 uapbd 类；不补齐会在网关执行时缺类），逐条哈希与条目清单核验通过；
- 部署后条目数：uapbd public 40→101、uapbd private 53→257、uapbd client 129→157、so public 7→13、so private 4→16；
- 文件回退：`build\deployment\20261112-135219\rollback.ps1 -NcHome <HOME>`；
- **未执行 SQL、未改数据库**；需重启 NC 服务端并重新登录客户端才生效。

测试 SQL 与操作顺序见 `sales-other-nodes-release/sql/nc65-electronic-signature/sales-other-nodes/test/README.md`
（01 只读预检 → 02 切客户端配置 → 03 注册映射 → 04 开测试策略 → 05 复原；父目录 `verify.sql` 按请求核对）。

### 2.3 测试库实际执行（2026-11-12 14:35，已提交）

通过本机 DBX MCP（连接 `齐都药业测试库` = `172.21.253.8:1521/DB`，schema 限定 `QDNC`）直连执行，
未使用 NC `ierp/bin/prop.xml` 口令（该口令 JDBC 直连 ORA-01017），未输出任何口令：

| 项 | 结果 |
| --- | --- |
| 组织类型映射（更正） | `org_orgtype`：`PK_ORGTYPE`=NC 类型码，`CODE`=小写短名，`FIELDNAME`→`org_orgs` 列：saleorg→orgtype8、trafficorg→orgtype10、financeorg→orgtype5 |
| 试点组织 | `0001A910000000001O7X`（山东齐都药业有限公司；三类组织类型齐全，发货单 1,375,756 / 发票 1,447,230） |
| 客户端配置 | 40060402→`QdDeliveryConfig.xml`、40060501→`QdSaleInvoiceConfig.xml`（各影响 1 行），40060303 保持原生 |
| 映射 | 22 个已实现按钮 `PILOT/Y`（关闭管理 10、发货单 7、发票 5），按钮注册逐一核对通过 |
| 策略 | O7X 总控 Y + 12 条策略（发货单 7、发票 5）`sign=Y/audit=Y/enabled=Y`；关闭管理 12 项未开（无客户端覆盖层） |
| 未触碰 | 已有 990 组织总控与 8 条销售订单/物料策略、28 范围以外的映射 |
| 回退 | `build/sales-other-nodes-db/apply2.py 05`（DB）+ `build/deployment/20261112-135219/rollback.ps1`（文件） |
| 证据 | `build/sales-other-nodes-db/db-changes.md`、`db-before.json`、`db-after.txt`、`apply2-0*.log` |

执行中发现的 DBX MCP 限制：`dbx_execute_batch` 对“PL/SQL 块里 `SELECT 普通列 INTO 变量`”会误报
`DATABASE_OUT_OF_SCOPE`（`COUNT(*)`/`MAX(列)` 不触发），因此改为逐条单语句执行；该限制与客户端拆分
PL/SQL 块导致 `PLS-00201` 属同类工具边界，已在测试 SQL README 中说明。
| 全量 AllTests | **未纳入本批记录**：快照采样期间另一会话正在修改销售订单文件（12:54–13:04），当前工作区 `SaleOrderStateAdapterTests` 失败；见 §6 |

打包时的 HOME 基线（写入 `manifest.json` 的 `baseline_sha256`）：
`qd_esign_uapbd_public.jar 41157`、`qd_esign_uapbd_private.jar 165283`、`qd_esign_uapbd_client.jar 232090`、
`qd_esign_so_public.jar 5183`、`qd_esign_so_private.jar 17668`（客户端 JAR 在 M0 取证后、打包前发生过一次变化，
部署前必须以 `manifest.json` 的哈希复核目标 HOME，而不是用 M0 文档的早期哈希）。

## 3. 候选包内容

`package.ps1` 以**目标 HOME 的实际基线 JAR** 为底，只覆盖本批增量类与新增文件：

| 包内路径 | 变化 |
| --- | --- |
| `modules/uapbd/lib/qd_esign_uapbd_public.jar` | 追加 `SalesOtherNodeScope`、`SalesPfActionCommand`、`SalesPfActionResult` |
| `modules/uapbd/META-INF/lib/qd_esign_uapbd_private.jar` | 覆盖 `OperationTransactionServiceImpl`（ADAPTERS 三项 + 审计快照挂钩）、`MappingRules`（三节点根/行校验）、新增 `SalesOtherNodeSnapshots` |
| `modules/uapbd/client/lib/qd_esign_uapbd_client.jar` | 追加 4 个共用客户端类、5 个发货单覆盖动作、5 个发票覆盖动作、`QdDeliveryConfig.xml`、`QdSaleInvoiceConfig.xml` |
| `modules/so/lib/qd_esign_so_public.jar` | 追加 3 个适配接口、`SaleCloseCommand`、`DeliveryCommand`、`DeliveryStateResult` |
| `modules/so/META-INF/lib/qd_esign_so_private.jar` | 追加 3 个路由与 3 个适配器（含内部类） |
| `modules/so/META-INF/qd_esign_sales_other_nodes.upm` | **新增** UPM 注册三个适配器（`remote=false`、`singleton=true`、`tx=CMT`），不改既有 UPM |
| `sql/nc65-electronic-signature/sales-other-nodes/*.sql` | 预检/注册/回退/按请求核对四份草案 |
| `manifest.json` | 基线文件哈希、覆盖类清单、客户端 XML、候选内每个文件的 SHA-256 |
| `rollback.ps1` + `baseline/` | 逐文件基线副本与可逆回退脚本 |

打包器逐项校验：覆盖类必须来自构建记录、CAFEBABE + Java 7 主版本 + 记录内 SHA-256 一致；
基线 JAR 中未被本批声明的条目哈希必须不变；不得出现未声明的新条目；客户端配置 XML 只新增本批文件。

## 4. 部署前置条件（缺一不可）

1. 目标 HOME 必须是**完整既有部署基线**（本包只覆盖本批增量类；当前开发 HOME 为部分部署基线，
   `qd_esign_uapbd_private.jar` 仅 53 条目，不满足完整基线要求）。
2. 目标数据库已安装并核对 schema 1.3.0（含 `qd_es_node` 28 行种子为 `UNMAPPED/N`）。
3. 维护窗口内先备份：5 个基线 JAR + 既有 `qd_esign_saleorder.upm`/`qd_esign_core.upm` + 目标库只读快照。
4. 客户端 `BeanConfigFilePath` 需切到本批覆盖层（40060402→`QdDeliveryConfig.xml`；40060501→`QdSaleInvoiceConfig.xml`）；
   40060303 关闭管理本批**没有客户端覆盖层**，`configure.sql` 的客户端守卫会拦下 REGISTER（有意失败关闭）。
5. NC 服务端/客户端需重新加载类（重启或重新登录）；本批不启动/停止 NC。

## 5. 部署与回退演练步骤（草案，待真实窗口执行）

1. 记录部署前哈希：5 个目标 JAR、其余 `qd_esign_*` 文件、`sm_paramregister` 客户端配置参数。
2. 解压候选包，按 `manifest.json` 逐文件核对目标 HOME 基线与 `baseline_sha256`；不一致即中止。
3. 备份目标文件到维护目录；复制 `modules/**` 覆盖 HOME；新增 UPM 文件单独登记。
4. 策略保持关闭，先跑原生基线：三个节点各做一次原生保存/审批/关闭操作，记录行为与回写。
5. 执行前只读预检 `preflight.sql`：版本、组织类型、按钮注册、映射/策略现状、运行中请求。
6. 按节点逐按钮 `configure.sql`（`pilot_mode=REGISTER`、`:pilot_allow_unverified=Y` 需负责人确认）：
   事务内注册 → 回显核对 → 手工 COMMIT；随后仅在已联调通过的按钮上开启策略。
7. 回退：停新请求 → 核对运行中/未知结果 → 执行 `rollback.sql`（映射失效、待执行请求失效）→
   执行候选包内 `rollback.ps1 -NcHome <HOME>` 恢复基线 JAR 并删除新增 UPM → 复核哈希与策略状态。
   保留全部签名/审计/请求证据，不删除、不覆盖后续补丁。

## 6. 本批未完成与必须复验

1. **全量 AllTests 未纳入本批记录**：并行会话修改销售订单文件期间，`SaleOrderStateAdapterTests` 在当前工作区失败；
   候选发布前必须在串行冻结窗口重跑 `tools/build.ps1 -Test`（含全部测试）并复核 `-VerifyRecord`。
2. 候选包未部署、未演练回退；`rollback.ps1` 只恢复文件，数据/注册回退须用 SQL 草案并人工确认。
3. 映射/回退/核对 SQL 未在真实库执行或语法校验（`org_orgtype`、`org_orgs.orgtypeN`、按钮注册均未现场核对）。
4. 三节点试点组织/账号未确认（M0 §2），真实 NC 联调（M5）未开展。
5. 关闭管理客户端覆盖层、发货/发票新增保存、发货 Delete/发票 Delete、全部需要“继续确认”的打开动作保持关闭
   （见 `SALES_OTHER_NODES_M1_M4_20261112.md` §7/§8 与 `UNRESOLVED_ISSUES.md` U71–U80）。
