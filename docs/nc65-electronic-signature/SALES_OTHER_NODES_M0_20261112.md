# 销售管理其他三节点 M0 取证汇总（2026-11-12）

本文件汇总 `SALES_OTHER_NODES_DEVELOPMENT_PLAN.md` 要求的 M0 交付：逐按钮证据索引、三节点试点组织判断、
现有销售订单补丁基线、拟改文件与未核实清单。**本批为离线开发与只读取证**：未部署、未执行 SQL、未启动 NC、
未连接数据库、未触发任何外部平台调用。所有“静态核实”均指本机 JAR/XML/字节码与 2026-11-07 现场快照派生核对，
不等于真实 NC 会话验证。

## 1. 逐行证据索引（28 项）

三个节点的逐行证据由独立取证任务产出，均为只读来源（本机 NC65 `E:\01-Home\NC65home\QuDuTest\nchome`
的 `modules/so/**` JAR、`modules/so/METADATA/**` BMF、`config/qd-esign/site-20261107/*.csv`）：

| 节点 | 原表行 | 项数 | 证据文件 | 原始 javap |
| --- | ---: | ---: | --- | --- |
| 销售订单关闭管理 `40060303` | 217–228 | 12 | [close-manage-evidence.md](sales-other-nodes-evidence/close-manage-evidence.md) | `build/native-evidence-other-nodes/close-manage/`（41 份） |
| 发货单维护 `40060402` | 229–238 | 10 | [delivery-evidence.md](sales-other-nodes-evidence/delivery-evidence.md) | `build/native-evidence-other-nodes/delivery/`（43 份） |
| 销售发票维护 `40060501` | 239–244 | 6 | [invoice-evidence.md](sales-other-nodes-evidence/invoice-evidence.md) | `build/native-evidence-other-nodes/invoice/`（55 份） |

28 行全部完成“客户端配置 bean → 原生动作类 → 调用链 → 最终 PF/RPC → 返回/刷新”的静态定位；
**真实 NC 会话核实 0 条**（本环境无 NC 客户端连接与数据库会话）。

### 1.1 关键静态结论（按节点）

**销售订单关闭管理（12 项，`ISaleOrderCloseManageMaintain`）**

- 12 个 bean 与现场注册按钮逐一对应；`rowCloseAction` 类名为 `M30CloseRowCloseAction` 但按钮编码是 `LineClose`，
  `rowOpenAction` → `LineOpen`，覆盖层不得改 bean id 或按钮编码。
- 选择粒度：217/218 为整单（按表头去重后回读该单全部行）；其余 10 项为选中行（`SaleOrderViewVO`，行主键 `csaleorderbid`），
  发货/出库/开票/结算在 service 内 `changeToSaleOrderVO()` 转单行整单后调用接口。
- `openSaleOrder(vos, boolean)` 布尔 = `SOATPprocess.abandonATPCheck()`（true 跳过 ATP 检查），客户端恒传 false；
  `openSaleOrderOut(vos, boolean)` 的实现体未引用该参数（死参数）。
- 12 项均先弹“关闭/打开原因”输入（取消即不写库，空原因被原生阻断），原因逐行写入 `SaleOrderBVO.setVclosereason`。
- `SOParameterVO[]` 无成功标志/错误码，不能当逐单回执；服务端异常表现为整体或部分 null。
- 10 个行级按钮 `isActionEnable` 在多选时直接放行（不校验行状态）；受控路径必须单笔并由服务端复核行范围。
- 原生 Service 经 `SCMResumeExceptionInvoker` 代理，遇 `IResumeException` 会自动重发；217/218 的 ATP 分支为 while 循环，
  且用户答“否”也会继续重发（原生缺陷）。受控路径**不得照抄**：单次调用、异常上抛、不自动重试。
- 218/224 需要 ATP“继续确认”恢复协议，本批保持关闭。

**发货单维护（10 项）**

- 保存 = PF `WRITE`（`DeliverySaveAction extends SaveScriptAction`，基本类型 `4331`）；删除 = 确认框 → 数据权限 → PF `DELETE`；
  提交/收回/审批/取消审批分别为 PF `SAVE/UNSAVE/APPROVE/UNAPPROVE`。
- `closeDelivery(SOParameterVO)`：参数 = `setVos(DeliveryVO[1])` + `setBusinessCheckMap`，不 setVo/setView/setUserObject。
- 行动作 `closeDeliveryRows(DeliveryVO, int[])` / `openDeiveryRows(SOParameterVO, int[])` 的 `int[]` 是**表体行下标**，
  服务端 `DeliveryAssitFuncImpl.getRowSelectedBill` 直接 `childrenVO[int]`，无越界与主键比对 → 受控适配器必须按持久化单据重核下标与行主键。
- 物理表：`so_delivery` / `so_delivery_b`，主键 `cdeliveryid` / `cdeliverybid`（BMF 静态核实）。
- `BillOpen`（ATP/信用/容差继续）、`OutOpen`（4 类检查继续）本批关闭；`Delete` 的确认框在 PF 之前，签名时序未核实，本批关闭。
- 保存并提交 `saveandsendAction` 为两段式（WRITE 后条件 SAVE），不在本批 10 行内，未接入。

**销售发票维护（6 项）**

- 6 项链路一致：`AbstractScriptExcAction.doAction → procFlow → PfUtilClient.runAction → IplatFormEntry.processAction`；
  动作名分别 WRITE/DELETE/SAVE/UNSAVE/APPROVE/UNAPPROVE。
- 保存配置含 `compositevalidateService`，`fillUpContext` 注入 `SaleinvoiceUserObject`（isclientsave=true），含 bcombin 组合分支。
- 删除：`Delete/1001Z810000000008DPR` 为发票删除；`fs_delete/0001Z810000000017PB3` 属附件面板按钮组（同组 fs_browse/download/rename/upload），
  不作为删除落点；删除确认在基类 `DeleteScriptAction.doAction`，取消返回码 ≠4 即中止。
- 6 项自身字节码无税控/数电/HTTP 调用；但审批后/取消审批后有跨模块回写（应收 m35、结算 m33、会计平台凭证删除消息），
  事务边界未实测。金税相关是节点内独立按钮，不在本批 6 项内。
- `Approve` 现场为 `ENTRY_ONLY_SUBPAGE_PENDING`，按钮编码 `Approve` 已由快照确认；审批窗口“确定”后的最终提交点是
  `IplatFormEntry.processAction`，取消窗口不发业务请求。
- `Delete` 本批关闭（入口歧义 + 确认时序未核实）。

## 2. 三个节点各自的试点组织判断（M0 要求）

**结论：三节点均未取得可确认的试点组织；本批不写死组织，受控入口默认关闭，激活由策略行与预检共同约束。**

依据：
1. 现场导出中只有集团业务单元清单（`site-evidence/2026-11-07/group_business_units.json`），**没有业务单元与组织类型的关联数据**；
   `org_orgs` 的组织类型是 `orgtype1..50` 标志列，代码到列名的映射在 `org_orgtype.fieldname`（平台 `OrgTypeVO` 静态线索），
   本机无法取得 `org_orgtype` 数据行。
2. 关闭管理注册组织类型 `SALEORGTYPE000000000`；既有 990 销售组织 `0001A9100000003UDS6R` 已被销售订单保存试点使用，
   但“同一 pk_org 或同节点注册类型”不足以证明其满足关闭管理的业务数据条件（需有可关闭/可打开的已审核订单与行）。
3. 发货单注册组织类型 `TRAFFICORGTYPE000000`、销售发票注册组织类型 `FINANCEORGTYPE000000`，均与 990 销售组织类型不同，
   **不得**用 990 销售组织直接充当发货/发票试点组织。
4. 因此 M0 交付的是“候选组织取得方法”，而非猜测值：
   - 只读预检 `sales-other-nodes-release/sql/.../preflight.sql` §2 取得三节点组织类型在 `org_orgtype` 中的 `fieldname`；
   - §4 列出集团内全部启用业务单元；
   - `configure.sql` 在执行时用 `EXECUTE IMMEDIATE` 读取该 `fieldname`，校验候选业务单元确实带有所需组织类型标志；
   - 现场还需确认：该业务单元下存在本节点可操作的真实单据（已审核订单/未关闭行、可关闭发货单、可审批发票）、
     账号具备节点/按钮/组织/数据权限，且客户端 `BeanConfigFilePath` 已切到本批覆盖层 XML。

**需用户/负责人确认的事项（未确认前不激活任何按钮）：**
| 节点 | 需确认内容 |
| --- | --- |
| 40060303 | 关闭管理试点业务单元（建议先验证 990 是否为有效销售组织且有可关闭订单）；账号按钮权限 |
| 40060402 | 一个启用中、带 TRAFFICORGTYPE 标志、已有发货单数据的业务单元；账号权限 |
| 40060501 | 一个启用中、带 FINANCEORGTYPE 标志、已有销售发票数据的财务组织；账号权限 |

## 3. 现有销售订单补丁基线（2026-11-12 11:05 快照）

本批以“当前 HOME 已安装销售订单补丁”为部署基线；开工只读记录全部 `qd_esign_*` 文件哈希（未改动 HOME）：

| 文件 | 字节 | SHA-256（前 16 位） |
| --- | ---: | --- |
| `modules/uapbd/lib/qd_esign_uapbd_public.jar` | 41157 | `02161CA0B63E8805` |
| `modules/uapbd/META-INF/lib/qd_esign_uapbd_private.jar` | 165283 | `683FB0D009C4E04E` |
| `modules/uapbd/client/lib/qd_esign_uapbd_client.jar` | 232243 | `0E59BA41210E10A9` |
| `modules/so/lib/qd_esign_so_public.jar` | 5183 | `283398CD25859C5C` |
| `modules/so/META-INF/lib/qd_esign_so_private.jar` | 17668 | `100066166B43052B` |
| `modules/so/META-INF/qd_esign_saleorder.upm` | 665 | `E195AC9CBFEB8A49` |
| `modules/uapbd/META-INF/qd_esign_core.upm` | 2013 | `0D3C4018451B48D2` |
| `modules/baseapp/**`、`modules/ic/**`、`modules/pu/**`、`modules/purp/**` | 见 `build/deployment/20261112-104711-c9f0fccb/` | — |

- HOME 中 `qd_esign_uapbd_private.jar` 仅 53 个条目（部分部署基线），**不等于**当前工作区全部源码；
  因此本批候选包只包含本批类，不得把“在本机 HOME 上编译通过”当作完整部署基线通过。
- 打包时（13:34）复核发现 `qd_esign_uapbd_client.jar` 已由 232243 字节变为 232090 字节（期间有其他会话/批次
  改动了 HOME），本批候选包以**打包时 Home 实际文件**为基线并写入 `manifest.json` 的 `baseline_sha256`；
  上表哈希仅作 11:05 开工快照，部署前必须以 manifest 复核目标 HOME。
- 当前工作区存在并行会话（库存/出库申请）持续写入，主工程 `tools/build.ps1 -Test` 的冻结校验可能报
  `Inputs changed while building`；本批正式证据以**冻结源码快照**上的构建记录为准（见 M6 文档）。
- 本批未覆盖/未修改：`QdSaleOrderConfig.xml`、销售订单保存试点、209–216 映射、`ISaleOrderOperationAdapter` 既有分支、
  `qd_esign_saleorder.upm`、`qd_esign_core.upm` 的既有组件。

## 4. 拟改文件表（按节点；均为本批新增或本批必要的共享修改）

| 范围 | 文件 | 类型 |
| --- | --- | --- |
| 共用白名单 | `uapbd/src/public/nc/vo/uapbd/qd/esign/SalesOtherNodeScope.java` | 新增 |
| 共用 PF 命令/结果 | `uapbd/src/public/nc/vo/uapbd/qd/esign/SalesPfActionCommand.java`、`SalesPfActionResult.java` | 新增 |
| 网关与映射（共享文件） | `uapbd/src/private/nc/impl/uapbd/qd/esign/OperationTransactionServiceImpl.java`（ADAPTERS 三项 + 快照挂钩）<br>`uapbd/src/private/nc/bs/uapbd/qd/esign/core/MappingRules.java`（三节点根/行对象校验） | 修改（最小增量） |
| 审计快照 | `uapbd/src/private/nc/bs/uapbd/qd/esign/audit/SalesOtherNodeSnapshots.java` | 新增 |
| 关闭管理 | `so/src/public/nc/vo/so/qd/esign/SaleCloseCommand.java`<br>`so/src/private/nc/impl/so/qd/esign/SaleCloseActionRoute.java`、`SaleCloseOperationAdapter.java` | 新增 |
| 发货单 | `so/src/public/nc/vo/so/qd/esign/DeliveryCommand.java`、`DeliveryStateResult.java`<br>`so/src/private/nc/impl/so/qd/esign/DeliveryActionRoute.java`、`DeliveryOperationAdapter.java`<br>`uapbd/src/client/.../QdDelivery*Action.java`（5 个）、`QdDeliveryConfig.xml` | 新增 |
| 销售发票 | `so/src/private/nc/impl/so/qd/esign/SaleInvoiceActionRoute.java`、`SaleInvoiceOperationAdapter.java`<br>`uapbd/src/client/.../QdSaleInvoice*Action.java`（5 个）、`QdSaleInvoiceConfig.xml` | 新增 |
| 适配接口 | `so/src/public/nc/itf/so/qd/esign/ISaleCloseOperationAdapter.java`、`IDeliveryOperationAdapter.java`、`ISaleInvoiceOperationAdapter.java` | 新增 |
| 客户端共用 | `uapbd/src/client/.../SalesNodeActionHandoff.java`、`SalesNodeSelection.java`、`SalesPfPreparation.java`、`SalesPfActionDispatch.java` | 新增 |
| 测试 | `tests/java/nc/test/qd/esign/SalesOtherNodeScopeTests.java`、`SalesOtherNodeSnapshotsTests.java`、`SaleCloseAdapterTests.java`、`DeliveryAdapterTests.java`、`SaleInvoiceAdapterTests.java`<br>`tests/java/nc/ui/uapbd/qd/esign/SalesNodeHandoffTests.java`、`DeliveryClientConfigTests.java`、`SaleInvoiceClientConfigTests.java` | 新增 |
| 注册与测试清单 | `tests/java/nc/test/qd/esign/AllTests.java`（登记新测试） | 修改（最小增量） |
| 交付物 | `sales-other-nodes-release/`（隔离构建检查器、候选打包脚本、SQL 草案、README） | 新增 |

## 5. 未核实清单（按计划必须保持关闭或待联调）

1. 三个节点的真实 NC 客户端入口、按钮可见性/权限、Spring bean 覆盖加载顺序、EDT 与弹窗行为。
2. 三节点试点组织与账号（含组织类型标志、数据权限、可操作样本单据）。
3. 关闭管理 12 项：原因弹窗中文文案（`4006011_0` 资源包本机 0 命中）、ATP 继续/取消分支、`IResumeException` 恢复协议、
   原生多选绕过状态校验的实际表现、`openSaleOrderOut` 死参数的现场影响。
4. 发货单：保存 EDIT 态二次确认（04006002-0113）、删除确认后签名顺序、ATP/信用/容差继续、行下标在排序/过滤后的错行风险、
   保存并提交两段式、审批通知次数。
5. 销售发票：`fs_delete` 附件语义的实际行为、审批窗口意见/决议/非终审/加签、取消审批的下游与凭证/结算回写、
   跨模块（m35/m33/会计平台）事务边界、外部平台是否存在。
6. 审计快照：`so_delivery`/`so_delivery_b` 物理表已由 BMF 静态核实；`so_saleinvoice`/`so_saleinvoice_b` 为命名规则推断，
   必须现场只读核对；三表真实字段白名单、`pk_group/pk_org` 取值与聚合明细完整性。
7. 同事务回滚与故障注入：原生写入后审计失败时业务/回写/审计是否共同回滚；外发/独立事务是否存在。
8. 批量与跨组织：本批只支持单笔；多选在受控路径必须拒绝，真实原生批量语义未验证。
9. 映射/回退 SQL 未在真实库执行或做语法校验；`org_orgtype.fieldname` 与 `org_orgs.orgtypeN` 的实际取值未核对。
10. 候选包未部署；部署前置条件（目标 HOME 是否为完整基线、客户端 `BeanConfigFilePath` 切换、UPM 注册）未演练。

## 6. 批次状态

| 批次 | 状态 |
| --- | --- |
| M0 基线与逐项取证 | **部分完成**：28/28 静态核实，真实会话 0；试点组织待现场确认 |
| M1 公共协议与隔离构建 | **已完成并通过离线测试**：范围表/路由/DTO/接口/一次性交接/选择判定/快照识别 + 冻结快照全量编译记录（1085 类、9 类测试 1176 断言）；未在 NC65 环境验证 |
| M2 关闭管理 12 项 | **部分完成**：服务端 12 项固定映射与适配器完成（10 启用、BillOpen/OutOpen 关闭）；客户端覆盖层未交付；详见 `SALES_OTHER_NODES_M1_M4_20261112.md` |
| M3 发货单 10 项 | **部分完成**：服务端 10 项完成（Save/Commit/UnCommit/Approve/UnApprove/BillClose/OutClose 启用；Delete/BillOpen/OutOpen 关闭）；5 个 PF 客户端覆盖层与配置交付；详见同上 |
| M4 销售发票 6 项 | **部分完成**：服务端 6 项完成（5 启用、Delete 关闭）；5 个 PF 客户端覆盖层与配置交付；详见同上 |
| M5 批量与真实 NC 联调 | 尚未开展（无 NC 会话/数据库授权） |
| M6 候选包与回退演练 | **已交付离线候选与回退材料**：候选包 `build/qd-esign-sales-other-nodes-20261112-133410-7516cdf9.zip`（SHA-256 `18BE91836366AD89A11B5FF23DAC8A574F5B73B8316759F2A0699CE0026E0732`）、4 份 SQL 草案、`rollback.ps1`；未部署、未演练，详见 `SALES_OTHER_NODES_M6_20261112.md` |
