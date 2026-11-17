# 销售订单其他按钮 S6（候选发布与回退演练）交付记录

- 批次：**S6 候选发布和回退演练**（对应 `docs/nc65-electronic-signature/SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md` 第 52 行）
- 日期：2026-11-12
- 范围：在既有 QDNC 1.3.0 + 990 保存补丁基线上，为本批（保存试点 + 209–216 其他按钮）生成**隔离候选包**、逐条目哈希与回退点，交付映射/预检/撤回 SQL 草案与启用清单。
- **重要前提**：本批全部入口**尚未通过真实 NC 联调**。按计划"仅允许通过实际 NC 验证的入口在目标组织启用"，本候选包与脚本**只作离线候选与演练材料**，不得据此在现场开启任何按钮策略或注册映射。

## 1. 候选包（已生成并校验）

| 项 | 值 |
| --- | --- |
| 候选 ZIP | `build/qd-esign-sale-990-20261112-103155-3dfd554c.zip` |
| SHA-256 | `917D494B5C5AAB7D5CF321CF8841655D45784508BC81532A0AE823DF9F484A4E` |
| 大小 / 条目 | 487,843 B / 29 条目（28 个文件 + 目录条目） |
| release id | `qd-esign-sale-990-20261112-103155-3dfd554c` |
| manifest | 包内 `release-manifest.json`：`files=28`、`baseline_sha256` 21 个模块文件、`build_record_sha256=7617B032…` |
| 990 构建记录 | `build/sales-990-isolated/build-record.json`：`tests_passed=true`、131 个编译类、冻结输入 `439F571D…` |
| 基线一致性 | 21 个模块目标文件均从当前 HOME 复制并逐文件哈希复核；非本批 JAR 条目经打包器逐条目核对未变化 |

**包内内容**：
- 6 个模块的 `qd_esign_*_public/private.jar`（含本批新类）、`uapbd/client/lib/qd_esign_uapbd_client.jar`（含 11 个客户端覆盖类与 `QdSaleOrderConfig.xml`）、8 份 UPM、`evidence/build-result.json`；
- 覆盖类已按前缀解析内部类：`SaleOrderActionRoute$Kind`、`SaleOrderSelection$Kind`、`SaleOrderPfActionDispatch$ContextFiller`、`QdSaleOrderSendApproveAction$1` 等均在包内；
- `sql/sale-order-save-pilot/{configure,verify}.sql` 与 `sql/sale-order-other-buttons/{preflight,configure,rollback,verify}.sql` 共 6 份脚本；
- `modules/uapbd/META-INF/qd_esign_core.upm` **保留当前 HOME 已绑定的 `SaleOrderPilotPolicyService`**（打包器输出 "already bound … preserved"）；`so/META-INF/qd_esign_saleorder.upm` 维持 HOME 单组件基线（本批决策复用 `ISaleOrderOperationAdapter`，不新增第二组件）。

## 2. 打包器（`package.ps1`）本批修正

| # | 修正 | 原因 |
| --- | --- | --- |
| 1 | 覆盖项改为"类前缀 + 从构建记录解析内部类" | 原脚本只列 `.class` 主类，会漏掉 `$Kind`/`$ContextFiller`/匿名类，导致部署后 `NoClassDefFoundError`；现在前缀缺失即中止 |
| 2 | UPM 派生支持双基线 | 旧脚本假设 HOME 仍为 `PolicyServiceImpl`，当前 HOME 已绑定 `SaleOrderPilotPolicyService` → 会 throw；现在"已绑定则保留、未绑定则替换一次、其他状态中止" |
| 3 | SQL 复制兼容两个目录 | 原脚本只复制 `sale-order-save-pilot`；新增 `sale-order-other-buttons` 4 份脚本 |
| 4 | 文件改为 UTF-8 BOM | Windows PowerShell 5.1 以 ANSI 读取无 BOM 的 `.ps1`；新增中文注释后出现解析错位，BOM 后稳定（打包器已实际跑通两次） |

## 3. 映射与回退脚本草案（`sales-order-990-release/sql/nc65-electronic-signature/sale-order-other-buttons/`）

| 脚本 | 性质 | 作用与关键守卫 |
| --- | --- | --- |
| `preflight.sql` | **只读** | 核对 QDNC/schema 1.3.0（无 1.3.0-RB）、990 为启用业务单元+销售组织、8 个按钮现场注册（pk_btn/btncode）、8 个范围当前映射、保存试点映射快照、8 按钮策略须全关、运行中/待执行请求须为 0、`BeanConfigFilePath`、组织总控 |
| `configure.sql` | 事务性，**不自动提交** | `SAVEPOINT` + 全表锁（NOWAIT）；校验环境/组织/客户端/策略关闭/无运行中请求；**REGISTER 必须 `:pilot_allow_unverified=Y`**；仅注册 7 个已实现按钮（Delete 确认顺序完成前不注册）；每行按钮注册逐一比对；`SQL%ROWCOUNT` 必须 1；不改保存试点、策略、总控、插件参数；异常 `ROLLBACK TO SAVEPOINT` |
| `rollback.sql` | 事务性，**不自动提交** | 8 个范围恢复 `UNMAPPED/N`（node/button/adapter/operation/object 置空），本批 `PENDING/READY` 请求置 `EXPIRED`，校验保存试点映射未变；不改策略/总控/签名与审计记录 |
| `verify.sql` | **只读** | 按 `:request_id/:pilot_group/:pilot_user_code/:button_code` 核对请求终态、签名/审计计数、订单是否仍有效、快照表头/表体/删除关联、审计记录、当前映射与策略 |

> 全部脚本未执行。现场执行要求：QDNC 登录、关闭自动提交、维护窗口、执行人核对回显后手工 `COMMIT`/`ROLLBACK`。

## 4. 启用清单（按入口；本批全部"未通过真实 NC 验证"，一律不启用）

| 原表行 | 按钮 | 离线实现 | 真实 NC 验证 | 策略允许 | 映射草案 | 结论 |
| ---: | --- | --- | --- | --- | --- | --- |
| 208 | Save | 既有试点 + 本批回归 | 未做（本批） | 既有试点范围 | 不动 | 保留现状 |
| 209 | Delete | 服务端就绪；**客户端未实现**（确认后签名未解决） | 未做 | **禁止开启** | 不注册 | 保持关闭 |
| 210 | Commit | 客户端+服务端就绪 | 未做 | 可开启（990） | 草案含 | 待联调 |
| 211 | UnCommit | 客户端+服务端就绪 | 未做 | 可开启（990） | 草案含 | 待联调 |
| 212 | Approve | 客户端+服务端就绪（审批窗口后签名） | 未做 | 可开启（990） | 草案含 | 待联调 |
| 213 | UnApprove | 客户端+服务端就绪 | 未做 | 可开启（990） | 草案含 | 待联调 |
| 214 | Freeze | 客户端+服务端就绪 | 未做 | 可开启（990） | 草案含 | 待联调 |
| 215 | UnFreeze | 服务端就绪；**ATP 恢复协议未证** | 未做 | **禁止开启** | 草案含（仅登记不启用） | 保持关闭 |
| 216 | BillClose | 客户端+服务端就绪 | 未做 | 可开启（990） | 草案含 | 待联调 |
| 多选 | 全部 | 选择判定+失败关闭；批量写入未实现 | 未做 | —— | —— | 批量保持关闭 |

## 5. 回退点（演练用）

| 回退点 | 位置 / 值 |
| --- | --- |
| 候选包 | `build/qd-esign-sale-990-20261112-103155-3dfd554c.zip`（SHA-256 见 §1；早期 10:25 候选已删除，避免误用） |
| 目标 HOME 文件基线 | manifest `baseline_sha256` 中 21 个 `modules/...` 文件哈希（部署前另按现场流程备份） |
| 构建记录 | `build/sales-990-isolated/build-record.json`（`input_sha256=439F571D…`；`-Verify` 通过） |
| 策略/总控 | 本批不修改；如现场开启策略，按 `qd_es_policy` 原值回退并保留签名/审计记录 |
| 映射 | `rollback.sql`（8 范围恢复目录种子；不触碰保存试点与其他范围） |
| UPM | 候选包保留 HOME 已绑定 `SaleOrderPilotPolicyService`；若目标环境为 `PolicyServiceImpl` 基线，打包器会替换一次并校验可逆 |
| 客户端配置 | 候选包不改 `BeanConfigFilePath`/插件参数；现场切换与还原按部署手册 |

## 6. 验证与未通过项

**已执行（离线）**：
- `sales-order-990-release/build.ps1` 退出码 0，8 个测试类全通过（26+15+45+48+49+162 断言 + 配置/策略），记录重新冻结；
- `package.ps1` 退出码 0：`-Verify` 两次（打包前后）、21 个基线文件逐条哈希、覆盖类与内部类注入、非本批 JAR 条目不变、UPM 保留、ZIP 条目与哈希复核；
- 候选包内容抽查：3 个关键 JAR 的新类与内部类齐全、6 份 SQL 齐全、2 份 UPM 状态正确。

**未通过 / 未执行**：
- 全部 8 项的**真实 NC 联调**（原生流程、事务回滚、权限/工作项、弹窗与通知、审计差异、多选）；
- 主工程整包构建记录仍受并行会话写入影响（本批未依赖它）；
- 映射/撤回 SQL 未执行、未在真实库语法校验（草案，含守卫但需现场 review）；
- 候选包**未部署**，不构成现场可用结论。

## 7. 现场建议执行顺序（待联调窗口）

1. 冻结候选包与目标 HOME 文件、按 manifest 备份；保持所有新按钮策略关闭，先验证原生基线。
2. 按部署手册安装候选包，核对实际加载类与 UPM（本批不改数据库）。
3. 保持策略关闭，用隔离样本逐入口验证原生行为与签名覆盖（先保存、冻结→审计链路，再关闭、提交/收回、审批/取消审批）。
4. 仅对通过项：`preflight.sql` → `configure.sql`（`:pilot_allow_unverified=Y`，无自动提交，核对回显后提交）→ 单笔开关组合测试 → 逐按钮开启 990 策略。
5. 任一入口出现异常：先停策略、核对运行中/结果未知请求，再执行 `rollback.sql` 恢复映射，最后按备份回退文件。
