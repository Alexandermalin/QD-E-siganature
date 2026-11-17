# S0 取证：当前 990 保存基线 + 本批拟改文件清单证据基础

- 取证时间：2026-11-11（Asia/Shanghai）
- 项目根目录（只读）：`E:\02-Project\QiDu\QD-E-Siganature`
- NC65 HOME（只读）：`E:\01-Home\NC65home\QuDuTest\nchome`（存在）
- 本文件所有路径若无特别说明，均相对项目根目录；HOME 路径相对 `nchome\`
- 约束遵守情况：全程只读；未运行 `build.ps1` / `package.ps1` / 任何部署脚本；未执行 SQL；未启动 NC；未修改项目或 HOME。仅计算哈希与读取 ZIP 条目（在内存中解压）。
- 结论速记：**当前 990 保存基线 = 文件部署 `20261111-155019-9df7af95`（release `display-catalog-only-20261111-154957-0763568d`）的 21 个文件全部与 HOME 逐字节一致**；其中销售服务 JAR 的 `SaleOrderPfSaveAdapter` 来自 14:07 的空收款容器热修。同时发现 **3 个必须先行处置的基线缺口**（见 ②.5、③.4、⑧）。

---

## ① A. 保存试点实现现状：源码（主线）vs 990 兼容包副本

比对对象：主线 `/` 与 `sales-order-990-release/` 同名文件。SHA-256 为本文件实测（`Get-FileHash -Algorithm SHA256`）。

| # | 文件 | 主线路径 | 990 包路径 | 主线 SHA-256 | 990 包 SHA-256 | 大小(主线/990) | 一致 |
|---|---|---|---|---|---|---|---|
| 1 | `QdSaleOrderConfig.xml` | `uapbd\src\client\nc\ui\uapbd\qd\esign\QdSaleOrderConfig.xml` | `sales-order-990-release\uapbd\src\client\nc\ui\uapbd\qd\esign\QdSaleOrderConfig.xml` | `998AFEBAFBAE43D7879DA8493B68E5D2889589979AB0BF200719B873D7DBAD1D` | 同左 | 753 / 753 | ✅ 一致 |
| 2 | `QdSaleOrderSaveAction.java` | `uapbd\src\client\nc\ui\uapbd\qd\esign\QdSaleOrderSaveAction.java` | `sales-order-990-release\uapbd\src\client\nc\ui\uapbd\qd\esign\QdSaleOrderSaveAction.java` | `63020D6F375116DE9509492E51D4C8A8E6C46C21C49A506A20A4C28FE02B5C03` | 同左 | 10381 / 10381 | ✅ 一致 |
| 3 | `SaleOrderSaveHandoff.java` | `uapbd\src\client\nc\ui\uapbd\qd\esign\SaleOrderSaveHandoff.java` | `sales-order-990-release\uapbd\src\client\nc\ui\uapbd\qd\esign\SaleOrderSaveHandoff.java` | `86283A140065E42DA25699AF574E687EB6631AB799CA3B9D639C39FE87498940` | 同左 | 2146 / 2146 | ✅ 一致 |
| 4 | `SaleOrderPfPreparation.java` | `uapbd\src\client\nc\ui\uapbd\qd\esign\SaleOrderPfPreparation.java` | `sales-order-990-release\uapbd\src\client\nc\ui\uapbd\qd\esign\SaleOrderPfPreparation.java` | `53AF06874F5EE8DB313F8B7FA22C3E612C43C314207CCCBF8FFB8117D8A82A03` | 同左 | 6883 / 6883 | ✅ 一致 |
| 5 | `SaleOrderOperationAdapter.java` | `so\src\private\nc\impl\so\qd\esign\SaleOrderOperationAdapter.java` | `sales-order-990-release\so\src\private\nc\impl\so\qd\esign\SaleOrderOperationAdapter.java` | `8C13BB9F764B5D1C92E7AB0443C5E531BA447BE83066C8B36162EBA72314EDE9` | 同左 | 5409 / 5409 | ✅ 一致 |
| 6 | `SaleOrderPfSaveAdapter.java` | `so\src\private\nc\impl\so\qd\esign\SaleOrderPfSaveAdapter.java` | `sales-order-990-release\so\src\private\nc\impl\so\qd\esign\SaleOrderPfSaveAdapter.java` | `3008DE5509601C2FCA9259316C25DE13F21F948BB21B37D7757614748630253A` | 同左 | 8988 / 8988 | ✅ 一致 |
| 7 | `SaleOrderPilotScope.java` | `uapbd\src\public\nc\vo\uapbd\qd\esign\SaleOrderPilotScope.java` | `sales-order-990-release\uapbd\src\public\nc\vo\uapbd\qd\esign\SaleOrderPilotScope.java` | `6BDFE0127062D350718F1EDC47FEDE13AD8AA21607926EB3213569493286A685` | 同左 | 524 / 524 | ✅ 一致 |
| 8 | `ISaleOrderPfSaveAdapter.java` | `so\src\public\nc\itf\so\qd\esign\ISaleOrderPfSaveAdapter.java` | `sales-order-990-release\so\src\public\nc\itf\so\qd\esign\ISaleOrderPfSaveAdapter.java` | `DC111F2A6964B83419FB5631CC9A7A750B478CA97FDC2F4C1EE7F174D95846F3` | 同左 | 204 / 204 | ✅ 一致 |
| 9 | `SaleOrderPfSaveCommand.java` | `so\src\public\nc\vo\so\qd\esign\SaleOrderPfSaveCommand.java` | `sales-order-990-release\so\src\public\nc\vo\so\qd\esign\SaleOrderPfSaveCommand.java` | `93060A2471DC7A1CC479F3839EB42AAC1A9159F5F30DFABFFEA7C4BF34FAACAA` | 同左 | 1431 / 1431 | ✅ 一致 |
| 10 | `SaleOrderPfSaveResult.java` | `so\src\public\nc\vo\so\qd\esign\SaleOrderPfSaveResult.java` | `sales-order-990-release\so\src\public\nc\vo\so\qd\esign\SaleOrderPfSaveResult.java` | `FE3851BB0FF40359BBF277B33AE413A7A32A65F40C33DB3E37064EB349B1A202` | 同左 | 772 / 772 | ✅ 一致 |
| 11 | `SaleOrderPilotPolicyService.java` | **不存在** | `sales-order-990-release\uapbd\src\private\nc\impl\uapbd\qd\esign\SaleOrderPilotPolicyService.java` | —（MISSING） | `06E6A87ABEBE0FF97726103647143F78DC3DFC93E1238746C57140E9F3244BC9` | 0 / 3752 | ❌ **仅 990 包存在** |

### A 的结论与必须注意的非对称项

1. **11 对里 10 对逐字节一致**，第 11 对（`SaleOrderPilotPolicyService.java`）在主线**根本不存在**。即：990 包不是"主线的子集副本"，而是"主线 + 一个只在 990 生效的策略包装器"。该包装器由 `sales-order-990-release/package.ps1:40` 作为 overlay 打进 `qd_esign_uapbd_private.jar`，并由 `package.ps1:90–97` 在**打包时**把 HOME 的 `qd_esign_core.upm` 里 `PolicyServiceImpl` 替换为 `SaleOrderPilotPolicyService`（派生，不入源码树）。→ **主线的 `uapbd\META-INF\qd_esign_core.upm` 永远指向 `PolicyServiceImpl`**（见 ③）。
2. **反方向还有一处非对称**：`so\src\public\nc\vo\so\qd\esign\SaleOrderCommand.java`（1633 B）**只在主线存在**，990 包目录里没有。990 包能编译 `SaleOrderOperationAdapter.java`（其第 10 行 `import nc.vo.so.qd.esign.SaleOrderCommand`），是因为 `sales-order-990-release/build.ps1:8–12` 把 **HOME 已安装的 `qd_esign_so_public.jar` 放进 classpath**，该 JAR 内含 `nc/vo/so/qd/esign/SaleOrderCommand.class [1322 B]`。→ 结论：**`SaleOrderCommand` 是"源码在主线、字节码由已装 JAR 提供"的隐式依赖**；本批若改动它，990 包的基线假设（"其他 JAR 条目逐项不变"）会被打破，必须重新设计 overlay 清单。
3. 990 包**不含任何 `.upm`**；`so\META-INF\qd_esign_saleorder.upm` 的"两组件版本"（976 B，含 `ISaleOrderPfSaveAdapter` 注册）**只在主线**，未随包发布（见 ③.4 缺口）。

---

## ② B. 构建 / 发布证据与哈希一致性

### B.1 三个被引用文件的真实存在性

| 引用 | 存在 | 大小 | 结论 |
|---|---|---|---|
| `build\sales-990-isolated\build-record.json` | ✅ | 9159 B | 真实存在 |
| `build\deployment\20261111-100853-135cd831\sales-file-only-journal.json` | ✅ | 1322 B | 真实存在 |
| `build\sales-990-balance-fix\stage-20261111-140357-5be4f8b7\publish.json` | ✅ | 501 B | 真实存在 |
| 其引用的 `build\platform-classpath.txt` | ✅ | 406824 B | 真实存在 |
| 其声称的 `nc_home` | ✅ | — | `E:\01-Home\NC65home\QuDuTest\nchome` 存在 |

### B.2 `build\sales-990-isolated\build-record.json` 关键字段（实读）

| 字段 | 值 |
|---|---|
| `schema_version` | `1` |
| `completed_at_utc` | `2026-11-11T02:06:05.2864454Z`（本地 10:06:05） |
| `nc_home` | `E:\01-Home\NC65home\QuDuTest\nchome` |
| `input_sha256` | `43AC79E13D09330DA4A9EF02432936092E900029556E7150161771A86757366E` |
| `tests_passed` | `true` |
| `classes`（条目数） | **31** |

31 个条目 = 3 client + 4 private + 4 public + 20 test（`SaleOrderPfSaveAdapterTests$*` 等）。10 个非测试类的 `sha256` 见下（C 会逐条与 HOME 对账）。

### B.3 `sales-file-only-journal.json`（10:08:53 部署）

`status=published`、`database_changed=false`、`action=file-only-local-stopped-home`、`plan_sha256=2B96B3D786B276F2EEF5D2B1B2C622254CB33E3F6471B8E5857D43A6E61FE74F`，6 个文件状态 `applied`：`uapbd_public.jar`、`uapbd_private.jar`、`so_public.jar`、`so_private.jar`、`uapbd_client.jar`、`uapbd\core.upm`。

**注意（重要）**：该 journal 本身**不记录任何哈希**，只有 `applied/restored/pending` 状态。真正的哈希在同目录 `plan.json`（21 个文件）。把它单独当"哈希证据"是误读 —— 它是**状态证据**，哈希证据在 `plan.json`。

同目录 `plan.json` 的 21 项与**当前 HOME 对比**：13 项已 `CHANGED-SINCE`（被 10:11 / 12:55 / 14:07 / 15:50 的后续动作覆盖或改写），仅 8 项仍 `SAME-AS-DEPLOYED`。已变化的包括 `so_private.jar`（`A29957E7…` → `7D0BEC07…`）、`uapbd_client.jar`（`74F7FA0B…` → `78D724A8…`）、`uapbd_public/private`、`baseapp_public/private`、`auth.upm`。**因此 10:08:53 不是当前基线。**

### B.4 `publish.json`（空收款容器热修）—— 与当前 HOME 完全一致 ✅

`build\sales-990-balance-fix\stage-20261111-140357-5be4f8b7\publish.json`：

| 字段 | 值 |
|---|---|
| `target` | `E:\01-Home\NC65home\QuDuTest\nchome\modules\so\META-INF\lib\qd_esign_so_private.jar` |
| `old_sha256` | `A29957E757DA448950F219EC3C3133F660FEBBEF556E03830A6F480C5DB16855` |
| `new_sha256` | `7D0BEC07A9EC45B2D25B597765BEF62C678437E9FFBEDBB7AE641F4529108390` |
| `class_sha256` | `1E130A227057DBB90B7BB51CDF7FCEBBED1772CA355D5D8726806182EFDED2BB` |
| `timestamp_utc` | `2026-11-11T06:07:03.3678144Z`（本地 14:07:03） |
| `database_changed` | `false` |

**三级哈希一致链（全部实测，无一处不符）：**

| 环节 | 实测值 | 与 publish.json |
|---|---|---|
| `stage-…/before.jar` | `A29957E757DA448950F219EC3C3133F660FEBBEF556E03830A6F480C5DB16855`（7626 B） | = `old_sha256` ✅ |
| 990 包 staged `modules\so\META-INF\lib\qd_esign_so_private.jar` | `A29957E7…`（7626 B） | = `old_sha256` ✅（说明热修的 before 就是 10:07 包发布的版本） |
| `stage-…/after.jar` | `7D0BEC07A9EC45B2D25B597765BEF62C678437E9FFBEDBB7AE641F4529108390`（8391 B） | = `new_sha256` ✅ |
| **当前 HOME** `modules\so\META-INF\lib\qd_esign_so_private.jar` | **`7D0BEC07A9EC45B2D25B597765BEF62C678437E9FFBEDBB7AE641F4529108390`（8391 B）** | **= `new_sha256` ✅ 一致** |
| JAR 内条目 `nc/impl/so/qd/esign/SaleOrderPfSaveAdapter.class` | `1E130A227057DBB90B7BB51CDF7FCEBBED1772CA355D5D8726806182EFDED2BB`（10021 B） | = `class_sha256` ✅ |

**结论：`publish.json` 的 `new_sha256` 与当前 HOME 销售服务 JAR 的 SHA-256 完全一致，且类条目级哈希也一致。这一条引用是真的、可复核的。**

### B.5 ⚠️ 基线缺口 1：`build-record.json` 已过期，不能作为当前保存基线的构建证据

用"类文件字符串表"比对可**证明**（无需运行构建）：

- `build-record.json` 中 `private/nc/impl/so/qd/esign/SaleOrderPfSaveAdapter.class`：`size=8555`、`sha256=DC6A6A8455184B2CB1CE7EE0C414561AE3B3AE7C16DE83731AFF97CBF55FDC29`。
- 当前 HOME 中同名条目：`size=10021`、`sha256=1E130A22…`（即热修版本）。
- 常量池差异（实测）：
  - **只在 8555 B 旧类**中出现：`Composite sales-order operations are not included in the pilot`
  - **只在 10021 B 新类**中出现：`isEmptyBalance`、`Lnc/vo/so/m30/sobalance/entity/SoBalanceVO;`、`getListenerflag`、`getChildren` 等空收款容器检查符号
- 当前仓库源码 `so\src\private\…\SaleOrderPfSaveAdapter.java`（`3008DE55…`，8988 B）**含** `isEmptyBalance`（第 127–140 行）、**不含** `"Composite sales-order operations…"`。旧字符串仅存在于快照副本 `build\signature-retry-isolated-d91bd651\so\src\private\nc\impl\so\qd\esign\SaleOrderPfSaveAdapter.java`（第 63 行，`5D1536EA…`，7718 B）。

→ **`build-record.json` 冻结的是 10:06 之前的旧适配器源码；`input_sha256=43AC79…` 不可能等于当前输入。`sales-order-990-release\build.ps1 -Verify`（第 55–58 行会校验 `Hash-Inputs -ne $record.input_sha256`）在当前工作区必然抛 `Isolated build inputs no longer match the tested record`。**
（未实测该失败：任务禁止运行 `build.ps1`；此处为源码/字节码常量池层面的确定性推断，标注为"推断"。）

同时 `build-record.json` 也不含 14:07 热修后的类——即**当前 HOME 的 `SaleOrderPfSaveAdapter` 没有任何"通过的隔离构建记录"背书**，它只由 `build\sales-990-balance-fix\classes` 这个手工 staging 目录支撑。

### B.6 ⚠️ 基线缺口 2：热修自称的"26 assertions"测试源码未入库

- `publish.json` / `plan.json` 记 `tests: "SaleOrderPfSaveAdapterTests 26 assertions; offline only"`。
- 但 `build\sales-990-balance-fix\classes\nc\test\qd\esign\SaleOrderPfSaveAdapterTests*.class` 有 `$1…$24`（主类 12277 B）；而当前仓库测试源码编译产出的 build-record 版本只有 `$1…$14`（主类 10230 B）。
- 全项目搜索 `SaleOrderPfSaveAdapterTests.java` 只有 14398 B 一份（`67CAD28A…`，主线/990 包/git-onboarding 完全相同），其 `check(` 调用 9 处。
→ **产生"26 断言"的那份测试源码不在仓库内，"26 assertions"无法离线复现。**（`build\sales-990-balance-fix\classes` 是从仓库外/已删改状态编译出来的手工产物。）

### B.7 990 隔离构建的输入冻结口径（便于复核）

`sales-order-990-release\build.ps1:28–54` 的 `Hash-Inputs` 把 **`sales-order-990-release` 目录下全部文件 + 5 个基线 JAR + `build\platform-classpath.txt` 全部条目 + HOME 的 `ufjdk\bin\java.exe`/`javac.exe`/`ufjdk\jre\bin\j9vm\jvm.dll` + 8 个 `.upm` + 13 个目标 JAR** 一起冻结。→ 任何一个 HOME 文件变动（包括后续 display-catalog 部署改过的 `uapbd_client.jar`、`baseapp*.jar`）都会让 `-Verify` 失败。这也是 B.5 之外的第二重"记录已失效"原因。

---

## ③ C. HOME 当前实际状态（只读实测）

### C.1 `modules\so\META-INF\qd_esign_saleorder.upm`

- 大小 665 B，SHA-256 = `E195AC9CBFEB8A495E8D3BC1A28F5953D79A8C54406AB6A22FD92E82C6EBFF84`
- 实际内容（4 行有效体，**只有 1 个 component**）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- 附加服务注册：interface 为服务定位契约，implementation 为对应实现；注册存在不代表节点已验证或策略已启用。 -->
<module name="so"><public>
    <!-- ISaleOrderOperationAdapter：仅本地调用，禁止作为远程用户服务暴露；… -->
    <component remote="false" singleton="true" tx="CMT">
<interface>nc.itf.so.qd.esign.ISaleOrderOperationAdapter</interface>
<implementation>nc.impl.so.qd.esign.SaleOrderOperationAdapter</implementation>
</component></public></module>
```

**⚠️ 基线缺口 3：HOME 未注册 `ISaleOrderPfSaveAdapter`。** 主线源码 `so\META-INF\qd_esign_saleorder.upm` 是 **976 B / `BD82AB35FE6B8149D6DA0AE51F86F210EFAD205B26E096E25C9E114ECB64EB41`**，含**第二个** component：

```
    <!-- 单笔新增试点：保留 NC PF WRITE 调用；注册本身不启用节点策略。 -->
    <component remote="false" singleton="true" tx="CMT">
<interface>nc.itf.so.qd.esign.ISaleOrderPfSaveAdapter</interface>
<implementation>nc.impl.so.qd.esign.SaleOrderPfSaveAdapter</implementation>
</component>
```
即：**该注册尚未部署到 HOME**。
影响（与 D 直接相关）：`sql\…\sale-order-save-pilot\configure.sql:9` 把 `v_adapter` 设为 `nc.itf.so.qd.esign.ISaleOrderPfSaveAdapter` 并写入 `qd_es_node.adapter_class`；而 `OperationTransactionServiceImpl.java:277` 用 `NCLocator.getInstance().lookup(service)` 按该字符串取服务。在只有 `ISaleOrderOperationAdapter` 注册的情况下，查找取不到 → 第 277 行 `EsignValues.require(located instanceof BusinessOperationAdapter,"Invalid adapter service contract")` 失败。
（当前试点实际能跑通的原因：`SaleOrderOperationAdapter.java:45,17,30` 内部用 `new SaleOrderPfSaveAdapter()` 直接实例化，**绕过了 NCLocator**。所以"保存试点能用"与"configure.sql 的映射能生效"是两件事，后者依赖尚未部署的 upm 注册，或改用 `ISaleOrderOperationAdapter` 作 adapter_class。）

### C.2 `modules\uapbd\META-INF\qd_esign_core.upm`

- 大小 2013 B，SHA-256 = `0D3C4018451B48D27569B7A9A9293A0D748E4679B9C8A188BD78EEB41088DAEE`
- 4 个 component：`IOperationService→OperationServiceImpl(remote=true,tx=NONE)`、`IOperationTransactionService→OperationTransactionServiceImpl(remote=false,CMT)`、`ISignatureService→SignatureServiceImpl(remote=true,CMT)`、**`IPolicyService→` 指向 `nc.impl.uapbd.qd.esign.SaleOrderPilotPolicyService`（第 27 行）**。
- **policy 实现指向 `SaleOrderPilotPolicyService`，不是 `PolicyServiceImpl`。**（这正是 `package.ps1:90–97` 的派生结果，已实际落到 HOME。）
- 对照：主线源码 `uapbd\META-INF\qd_esign_core.upm` = 2003 B / `A205A9DB6F78D415209476B7CA92619178E0A32A810823AA82EB7E72294F0CBE`，第 27 行仍是 `PolicyServiceImpl`。→ **源码树里没有任何 `.upm` 声明 `SaleOrderPilotPolicyService`（全项目非 build 目录搜索为空）**。`SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md:72` 已指出该事实（"旧打包脚本假设它仍指向 `PolicyServiceImpl`"）。**若本批直接复用 `sales-order-990-release\package.ps1`，其第 94 行 `if count -ne 1 throw` 会因 HOME 已是包装器而失败。**
- 另：`OperationTransactionServiceImpl.java:269–270` 的策略判定**不走** `.upm` 注册的 `IPolicyService`，而是**静态调用 `PolicyServiceImpl.validateOwnerRequest` / `PolicyServiceImpl.resolveFor`**。→ 包装器 `SaleOrderPilotPolicyService` 只影响"策略维护/查询"（远程 `IPolicyService` 入口），不影响服务端执行期的策略解析。这对本批"扩展策略白名单"的设计落点很关键。

### C.3 5 个试点 JAR 的 SHA-256 + 是否含本试点类（实际条目名）

| JAR（相对 `nchome\`） | SHA-256 | 大小 | 条目数 | 含本试点类 |
|---|---|---|---|---|
| `modules\so\META-INF\lib\qd_esign_so_private.jar` | `7D0BEC07A9EC45B2D25B597765BEF62C678437E9FFBEDBB7AE641F4529108390` | 8391 | 2（全是） | `nc/impl/so/qd/esign/SaleOrderOperationAdapter.class`[6981]、`nc/impl/so/qd/esign/SaleOrderPfSaveAdapter.class`[10021] |
| `modules\so\lib\qd_esign_so_public.jar` | `70528FC67FD9D3926FDF3E63CADC4C7B9084CCC55FD45E1EBE10D6B9A31C18A5` | 3174 | 5（全是） | `nc/itf/so/qd/esign/ISaleOrderOperationAdapter.class`[210]、`nc/itf/so/qd/esign/ISaleOrderPfSaveAdapter.class`[204]、`nc/vo/so/qd/esign/SaleOrderCommand.class`[1322]、`nc/vo/so/qd/esign/SaleOrderPfSaveCommand.class`[2308]、`nc/vo/so/qd/esign/SaleOrderPfSaveResult.class`[961] |
| `modules\uapbd\client\lib\qd_esign_uapbd_client.jar` | `78D724A88B5894C43777721763722FB727FA1310AB60839FDE3D77FC725858AE` | 191052 | 117（4 个） | `nc/ui/uapbd/qd/esign/QdSaleOrderConfig.xml`[753]、`…/QdSaleOrderSaveAction.class`[13105]、`…/SaleOrderPfPreparation.class`[7719]、`…/SaleOrderSaveHandoff.class`[2612] |
| `modules\uapbd\lib\qd_esign_uapbd_public.jar` | `0A87651F06968ADA1BBC18F56337CDCD15AFF8B7C2F4A0143C0EA0923463A499` | 39308 | 40（1 个） | `nc/vo/uapbd/qd/esign/SaleOrderPilotScope.class`[769] |
| `modules\uapbd\META-INF\lib\qd_esign_uapbd_private.jar` | `7745B793600BA0AC82CDCA3D55749C6158433D17F3A1CF57DD9F334470FA4700` | 164969 | 53（1 个） | `nc/impl/uapbd/qd/esign/SaleOrderPilotPolicyService.class`[4772] |

**部署一致性交叉验证（10 个类逐条比对 build-record 的 `sha256`）：9 条 MATCH，1 条 DIFF —— 唯一 DIFF 就是 `SaleOrderPfSaveAdapter.class`（`DC6A6A84…` vs HOME `1E130A22…`），与 B.5 的结论互为印证。**
另：HOME `qd_esign_uapbd_client.jar` 内 `nc/ui/uapbd/qd/esign/QdSaleOrderConfig.xml` 条目哈希 = `998AFEBAFBAE43D7879DA8493B68E5D2889589979AB0BF200719B873D7DBAD1D`，与主线/990 包源码**完全一致**（✅ 客户端覆盖层已按源码部署）。

### C.4 销售订单服务 JAR（"`uiso_salesorder.jar` 或实际名"）

任务里写的 `modules\so\lib\uiso_salesorder.jar` **不存在**；实际有三个不同文件，**都在 `client` 或 `pub` 位置，`so\lib` 下没有 `u*so*`**：

| 实际路径 | 大小 | SHA-256 | 说明 |
|---|---|---|---|
| `modules\so\client\lib\uiso_salesorder.jar` | 904074 | `359F4DD209F48B63966C1D9853EAA9FA3ABD6D97F785197CC0520718E5150D07` | **客户端 UI JAR（494 条目）**，含 8 项按钮的原生 Action 类、`nc/ui/so/m30/billui/model/saleorder_config.xml`(66981 B)、`saleorder_config.class`(102822 B) |
| `modules\so\META-INF\lib\so_salesorder.jar` | 758011 | `4A61AFAE9CD8E5E21B00023F9EBBC8FCD260F716596A52DA2F873B7981082544` | 服务端销售订单 JAR |
| `modules\so\lib\pubso_salesorder.jar` | 333127 | `B3FCAC679519B48C485F34CC3F8AA6F1653AE8EF95C4FFAC936DF13DBAAD0B89` | 公共接口 JAR |

**8 项按钮对应的原生类在 `uiso_salesorder.jar` 中全部存在**（实测条目）：
`nc/ui/so/m30/billui/action/SaleOrderDeleteAction.class`[4978]、`SaleOrderSendApproveAction.class`[2117]、`SaleOrderUnSendApproveAction.class`[1790]、`SaleOrderApproveAction.class`[2094]、`SaleOrderUnApproveAction.class`[2273]、`SaleOrderSaveAndSendAction.class`[2117]、`assist/FreezeAction.class`[3263]、`assist/UnFreezeAction.class`[5095]、`assist/SaleOrderCloseAction.class`[8038]。

### C.5 当前 990 基线的准确坐标（本次取证的最关键结论）

`build\deployment\20261111-155019-9df7af95\plan.json`（`release_id=display-catalog-only-20261111-154957-0763568d`，`created_at_utc=2026-11-11T07:50:20.3033826Z`，本地 15:50:20）列出 **21 个文件**，`new_sha256` **21/21 与当前 HOME 逐字节相等（实测：match=21, mismatch=0）**；其中除 `uapbd_client.jar`（`old=8DA14869…` → `new=78D724A8…`）外，其余 20 项 `old_sha256 == new_sha256`（= 一次"全量基线重述"式发布）。

对应 journal：`build\deployment\20261111-155019-9df7af95\display-catalog-file-only-journal.json` → `status=published`、`database_changed=false`、仅 1 个文件 `modules/uapbd/client/lib/qd_esign_uapbd_client.jar` 状态 `applied`。

**→ 结论：钉死"当前 990 保存基线"= `20261111-155019-9df7af95` 的 21 项哈希集合。**（10:08 的销售补丁包与 14:07 的热修是它的历史来源，但**都不是**当前快照；只有 `publish.json` 的那一个 JAR 仍与当前一致。）

990 相关部署时间线（`build\deployment\` 实测目录）：

| 本地时间 | deployment_id | 与当前 HOME 的关系 |
|---|---|---|
| 11-10 17:53 | `20261110-175339-7622596e` | 早期 |
| **11-11 10:08:53** | `20261111-100853-135cd831` | 销售 990 补丁包首发（21 文件 plan）；现仅 8 项仍一致 |
| 10:11:00 / 10:11:12 / 10:11:52 / 10:12:04 | `…2ec7891c` / `…b1e9c094` / `…dc7aec79` / `…d26d18d2` | 10:12:04 一份 `status=rolled-back`（`so_private/client/core.upm` 记为 `pending`） |
| **14:07:03** | 无 deployment_id，`build\sales-990-balance-fix\stage-20261111-140357-5be4f8b7` | 空收款热修；**当前 `so_private.jar` 即此版本** |
| 12:55:04 | `20261111-125504-3071ba1a` | display-catalog 中期 |
| **15:50:19** | `20261111-155019-9df7af95` | **当前基线（21/21 匹配）** |

---

## ④ D. 990 保存试点 SQL 要点（只读解析，未执行）

文件：`sql\nc65-electronic-signature\sale-order-save-pilot\configure.sql`（79 行）、`verify.sql`（16 行）。990 包内副本位于 `sales-order-990-release\sql\nc65-electronic-signature\sale-order-save-pilot\`（`package.ps1:106–108` 原样复制）。

### D.1 会不会写库？

**会。** `configure.sql` 含 3 条 `UPDATE`：

| 行 | 语句 | 条件 |
|---|---|---|
| 42–44 | `UPDATE qd_es_node SET validated='N'` | `DISABLE` 模式 |
| 46–47 | `UPDATE qd_es_request SET business_state='EXPIRED', error_code='SALE_SAVE_PILOT_DISABLED'` | `DISABLE` 模式，把 `PENDING/READY` 请求置过期 |
| 62–68 | `UPDATE qd_es_node SET node_code/button_code/adapter_class/mapping_state/operation_types/object_type/validated` | `REGISTER` 模式 |

脚本**不自提交**：第 13 行 `SAVEPOINT qd_sale_save_pilot`，第 70–71 行 `WHEN OTHERS THEN ROLLBACK TO qd_sale_save_pilot; RAISE;`，末尾没有 `COMMIT`（文件头第 1–3 行明确"Stop new requests and disable autocommit"）。**当前禁止执行，本报告未执行。**

`verify.sql` 是**纯只读**（3 条 `SELECT`，第 1 行注释 "Read only"）。

### D.2 涉及的表与行（逐条）

| 表 | 读/写 | 行号 | 作用 |
|---|---|---|---|
| 会话上下文 `SYS_CONTEXT('USERENV',…)` | 读 | 14–15 | 强制 `SESSION_USER='QDNC'` 且 `CURRENT_SCHEMA='QDNC'` |
| `qd_es_schema_version` | 读 + `LOCK … SHARE` | 21, 25–28 | 必须存在 `1.2.0` 且**不存在** `1.2.0-RB`（回滚标记） |
| `qd_es_request` | `LOCK … SHARE ROW EXCLUSIVE` + 读 + 写 | 22, 34–36, 46–47 | 不得有 `RUNNING` 的 `40060301/Save`；DISABLE 时把 `PENDING/READY` 置 `EXPIRED` |
| `qd_es_node` | `LOCK … SHARE ROW EXCLUSIVE` + 写 | 23, 42–45, 62–69 | 唯一映射行（`scope_id='QD-ES-EAF88AC5CAF75442'` 且 `group_scope_id IS NULL`）；REGISTER 要求 `SQL%ROWCOUNT=1` |
| `qd_es_policy` | `LOCK … SHARE` + 读 | 24, 37–39, 78–79 | 不得存在已启用（`enabled='Y'`）的 `40060301/Save` 策略 |
| `org_orgs` ⨝ `org_salesorg` | 读 | 29–33 | 990 必须是启用业务单元 + 启用销售组织 |
| `sm_funcregister` ⨝ `sm_paramregister` | 读 | 51–56 | 要求 `funcode='40060301'` 的 `BeanConfigFilePath = nc/ui/uapbd/qd/esign/QdSaleOrderConfig.xml`（否则 `-20216`） |
| `sm_funcregister` ⨝ `sm_butnregister` | 读 | 57–61 | 要求 `btncode='Save'` 且 `pk_btn='1001Z810000000004TP6'`（否则 `-20217`） |
| `qd_es_org_control` | 读 | 77 | 回显组织总控 |
| `qd_es_signlog` / `qd_es_object` / `qd_es_snapshot` / `qd_es_audit` / `so_saleorder` | 读（verify.sql） | verify:2–16 | 按 `request_id` 核对签名次数、审计数、是否真的落库 |
| `SO_SALE_ORDER` 物理表 | 读（verify.sql:7–8） | 7–8 | `so_saleorder` 上按 `csaleorderid`+`vbillcode`+`pk_group`+`pk_org`+`dr=0` 核对 |

### D.3 映射的适配器与操作类型

- 适配器：`v_adapter CONSTANT := 'nc.itf.so.qd.esign.ISaleOrderPfSaveAdapter'`（第 9 行）→ 写入 `qd_es_node.adapter_class`（第 62 行）
- 操作类型：`operation_types='INSERT'`（第 63 行，**单值，无 UPDATE/DELETE**）
- 对象类型：`object_type='SO_SALE_ORDER'`（第 63 行）
- 节点/按钮：`node_code='40060301'`、`button_code='Save'`（第 62 行）
- 范围：`scope_id='QD-ES-EAF88AC5CAF75442'`（第 8 行），且 `group_scope_id IS NULL`
- 其他硬编码：集团 `0001A9100000000005MH`、990 组织 `0001A9100000003UDS6R`（第 16–17 行）

### D.4 是否可回退

**可回退，且设计了两种回退：**

1. **脚本内 `DISABLE` 模式**（第 41–49 行）：`UPDATE qd_es_node SET validated='N'`（把映射置为未验证=停用）+ 把在途 `PENDING/READY` 请求置 `EXPIRED`；要求 `SQL%ROWCOUNT=1`（否则 `-20215`，即拒绝"映射已漂移"时盲改）。
2. **`SAVEPOINT` + `ROLLBACK TO`**（第 13、70–71 行）：任何异常整体回滚到保存点。
3. `verify.sql` 只读，可反复核对，无回退需求。

限制：脚本**不恢复**此前 `qd_es_node` 的字段原值（不像 `package.ps1` 那样有 before/after 对称校验）；`DISABLE` 只把 `validated='N'`，不把 `mapping_state` 还原为 `UNMAPPED`。若要"完全回到未映射"，需另行写恢复语句或依赖备份。

### D.5 `config\qd-esign\operation-scope.csv` 第 208 行核对 ✅

| 列 | 第 208 行实际值 |
|---|---|
| `sourceFile` | `C:\Users\Freud\Desktop\NC电子签名表.xlsx` |
| `sourceSheet` | `Sheet2` |
| `sourceRow` | `208` |
| `scopeId` | `QD-ES-EAF88AC5CAF75442` |
| `nodeId` | `QD-NODE-642F7D874EA13550` |
| `category` | `销售管理` |
| `nodeName` | `销售订单维护` |
| `buttonName` | `保存` |
| `adapterFamily` | `SO_PFLOW` |
| `mappingStatus` | `SOURCE_CANDIDATE_ONLY`（`ncFunctionCode`/`ncActionCode` 均为空） |
| `notes` | `仅需求目录；未登记真实NC功能/按钮编码，未实现或验证本操作适配。` |

**范围 ID 与代码严格一致**：`SaleOrderPilotScope.SCOPE_ID = "QD-ES-EAF88AC5CAF75442"`（第 7 行）、`configure.sql:8`、第 208 行三处相同；`SaleOrderPilotScope.GROUP_ID/ORG_ID` 与 `configure.sql:16–17` 相同（逐字符核对）。
CSV 共 244 行（含表头）；`node-scope.csv` 第 33 行：`QD-NODE-642F7D874EA13550 / 销售订单维护 / operationCount=9 / adapterFamilies=SO_PFLOW / mappingStatus=PENDING_RUNTIME_NODE_REGISTRATION`，`sourceFirstRow=208, sourceLastRow=216`。
**→ 本批 8 项 = 第 209–216 行**（保存 208 已完成）：

| CSV 行 | scopeId | buttonName | 对应注册 btncode / pk_btn（来自 `site-evidence/2026-11-07/buttons_1.json`，均已核对存在） |
|---|---|---|---|
| 209 | `QD-ES-FEFBC5B292C330AD` | 删除 | `Delete` / `1001Z810000000004TO3`；并存 `fs_delete` / `1002Z81000000000QCAZ`（附件组） |
| 210 | `QD-ES-4671CC2E70A34B97` | 提交 | `Commit` / `1001Z810000000004TOA` |
| 211 | `QD-ES-4F23D38FB22BA45F` | 收回 | `UnCommit` / `1001Z810000000004TOG` |
| 212 | `QD-ES-9C0B035FFEB2110D` | 审批-确定 | `Approve` / `1001Z810000000004TO2` |
| 213 | `QD-ES-51BB76212FFCD156` | 取消审批 | `UnApprove` / `1001Z810000000004TOO` |
| 214 | `QD-ES-C7E51B3ED69F8B05` | 辅助功能-冻结 | `Freeze` / `1001Z810000000004TP5` |
| 215 | `QD-ES-BF1F44B944C037B3` | 辅助功能-解冻 | `UnFreeze` / `1001Z810000000004TOE` |
| 216 | `QD-ES-2A439AB1FE3628B0` | 辅助功能-整单关闭 | `BillClose` / `1001Z810000000004TP1` |

（`Save` = `1001Z810000000004TP6`，与 `configure.sql:60` 一致。）

---

## ⑤ E. 网关 / 事务 / 审计：8 项按钮归 DELETE / UPDATE 的可行性

### E.1 `OperationTransactionServiceImpl.java` 的 ADAPTERS 白名单（第 27–51 行）

`private static final Set<String> ADAPTERS`（`Collections.unmodifiableSet`，`HashSet`）共 **25 项**，销售相关的只有 2 项（第 29 行）：

```
"nc.itf.so.qd.esign.ISaleOrderOperationAdapter","nc.itf.so.qd.esign.ISaleOrderPfSaveAdapter",
```
其余为 `IMaterialOperationAdapter`(30)、`IMaterialOrgInfoOperationAdapter`(31)、`IMaterialProdSaveAdapter`(32)、`IMaterialMainDeleteAdapter`(33)、`ICustomerFreezeAdapter`(34)、`ISupplierFreezeAdapter`(35)、`IPartnerStateAdapter`(36)、`IPartnerMainSaveAdapter`(37)、`IPartnerBatchAdapter`(38)、`IMaterialStateAdapter`(39)、`IWarehouseStateAdapter`(40)、`IBankAccountStateAdapter`(41)、`IBankAccountSaveAdapter`(42)、`IBankAccountDeleteAdapter`(43)、`IArchiveTreeDisableAdapter`(44)、`IArchiveTreeEnableAdapter`(45)、`IArchiveTreeSaveAdapter`(46)、`IArchiveTreeDeleteAdapter`(47)、`IPartnerOrgInfoOperationAdapter`(48)、`IWarehouseSaveAdapter`(49)、`IPurchaseOrderOperationAdapter`(50)、`IInventoryOperationAdapter`(50)、`ISupplierPriceOperationAdapter`(51)。

**8 项按钮若复用 `ISaleOrderOperationAdapter`，无需改白名单**；若按 `configure.sql` 走 `ISaleOrderPfSaveAdapter`，也在白名单内。**只有新增独立适配器接口时才需改第 28–51 行。**

### E.2 `OperationServiceImpl` 的入口路由（第 27–40 行）

`execute(String requestId, Serializable command)`：
1. 第 29 行 `boundary.assertNoTransaction()` — 外层不得有活动事务；
2. 第 30 行 `NCLocator.lookup(IOperationTransactionService.class)`；
3. 第 32 行 `claim_RequiresNew(requestId)` — 独立 CMT 认领并提交；
4. 第 34 行 `executeClaimed_RequiresNew(requestId,token,command)` — 业务 CMT；
5. 第 35–39 行失败处理：`recordFailure`（`failClaimed_RequiresNew`）另起事务记录，`nativePartnerSaveFailure` 仅对 `PartnerMainSaveCommand` 解包。
**该入口对"哪个按钮"完全无感知**——按 `request_id` 从库里取 `node_code/button_code/operation_type`，再 `store.loadNodeByAction(node,button)` 取 `adapter_class`（`OperationTransactionServiceImpl.java:121–124`）。→ **扩展 8 项按钮不需要改 `OperationServiceImpl`。**

### E.3 执行期动作域名校验 `MappingRules.requireDomain`（`uapbd\src\private\nc\bs\uapbd\qd\esign\core\MappingRules.java`）

- 第 13–16 行：取 `mapping.operation_types`（逗号分隔）与 `mapping.object_type`；要求 `actions.contains(operation)`。
- 第 64 行（销售订单走的通用分支）：`for(BusinessObjectRef object:objects) require(type.equals(object.getObjectType()),"Object type is outside the registered button domain")`。
- 销售订单**没有**像银行账户(17–29)/客户供应商保存(30–45)/客户供应商删除(46–63)那样的专项分支。
**→ 只要 `qd_es_node.operation_types` 登记 `UPDATE`/`DELETE` 且 `object_type='SO_SALE_ORDER'`，`MappingRules` 层面即放行；无需改该类。**

### E.4 审计识别：`OrderPhysicalSnapshots`（`uapbd\src\private\nc\bs\uapbd\qd\esign\audit\OrderPhysicalSnapshots.java`）

- 第 10–12 行 `enum`：`SALE("so_saleorder","csaleorderid","csaleorderbid")`、`PURCHASE("po_order","pk_order","pk_order_b")`。
- 第 24–27 行 `forAdapter(adapter, operation)`：**只接受 `INSERT` / `UPDATE` / `DELETE`**，其他返回 `null`。
- 第 28–31 行：`ISaleOrderOperationAdapter` **或** `ISaleOrderPfSaveAdapter` → `SALE`。
- 第 32–34 行：`IPurchaseOrderOperationAdapter` → `PURCHASE`；其余 `null`。
- 第 38–46 行 `before()`：`INSERT` 直接 return（新增无需 BEFORE）；其他动作读锁 + 全量聚合头/行。
- 第 48–60 行 `after()`：`DELETE` 时**反向校验**——若 `so_saleorder` 仍能读到该 `csaleorderid` 则抛 `"Native order deletion left an active authorized order"`；否则采 AFTER 聚合。
- 第 62–79 行 `aggregate()`：要求 `rows.size()==1` 且 `pk_group`/`pk_org` 与授权上下文相等（否则 `"Persisted order is outside the authorized group or organization"`）；未加锁时还要求 `vbillcode` 与 `ref.getBillCode()` 一致；随后拼 `so_saleorder_b` 行（`detailKey=csaleorderbid`）。
- 第 81–85 行 `details()` 只读**订单头和表体两张表**（PURCHASE 才多加 `po_order_payment`）。

**→ DELETE / UPDATE 的物理快照能力已经具备且已在代码里识别销售适配器**：删除有"真删掉了"的反向断言，更新走到头+行 BEFORE/AFTER 差异。缺口在：删除**不读受影响的关联明细**（下游单据、发货/发票/收款等），只看 `so_saleorder` 主表是否消失。

### E.5 `AuditCaptureScope` 与网关的审计完整性断言

- `AuditCaptureScope`（`uapbd\src\private\nc\bs\uapbd\qd\esign\audit\AuditCaptureScope.java`）**不区分适配器/操作类型**：第 10 行 ThreadLocal 栈式作用域，第 28–39 行两种构造（`(context,boolean enabled)` / `(context,Set<String> enabledObjectIds)`），第 52 行 `isEnabledFor`，第 72–80 行 `capture()` 拒绝重复/关闭后写入/越范围快照。
- 网关侧（`OperationTransactionServiceImpl.java`）：
  - 第 287 行 `AuditCaptureScope scope = ownerMode ? new AuditCaptureScope(c,auditIds) : new AuditCaptureScope(c,p.isAuditEnabled());`
  - 第 289–291 行 `OrderPhysicalSnapshots orders = p.isAuditEnabled() ? OrderPhysicalSnapshots.forAdapter(service,c.getOperationType()) : null; if(orders!=null) orders.before(scope,c);`
  - 第 332 行 `if(orders!=null) orders.after(scope,c,saved);`
  - 第 334–343 行：`if(p.isAuditEnabled())` 逐对象断言 —— 第 339 行 `require("INSERT".equals(op)||scope.hasBefore(key), "Locked BEFORE snapshot was not captured; business must roll back")`；第 340 行 `require("DELETE".equals(op)||scope.hasAfter(key), "Persisted AFTER snapshot was not captured; …")`；第 341 行 `new AuditRecorder().record(r,o,scope.getBefore(key),scope.getAfter(key))`。

**→ 八项按钮归类结论（可行性判定）：**

| 按钮 | 建议 operation_type | 网关可行性 | 阻碍点（须改代码，非配置） |
|---|---|---|---|
| 209 删除 | `DELETE` | ✅ 可行：`forAdapter` 支持，`after()` 有删除后反向断言，审计只要求 BEFORE | `SaleOrderOperationAdapter` 现走 `ISaleOrderScriptMaintain.saleOrderDelete`（低层脚本服务，第 34 行），**不是 PF 路径**；`validate` 对非 INSERT 要求 `SaleOrderCommand` 且已有主键（第 24–26 行）；删除前关联明细未采 |
| 210 提交 | `UPDATE` | ✅ 可行：BEFORE/AFTER 全采 | 需 `SAVE` 的 PF 流程上下文；`SaleOrderPfPreparation` 第 67 行硬限 `"WRITE".equals(action)`，不能复用到 `SAVE` |
| 211 收回 | `UPDATE` | ✅ 可行 | 同上，需 `UNSAVE` 流程上下文 |
| 212 审批确定 | `UPDATE` | ✅ 可行 | 需 `APPROVE` + 真实 `Approve` 按钮编码；审计未采工作项/决议 |
| 213 取消审批 | `UPDATE` | ✅ 可行 | 需 `UNAPPROVE` |
| 214 冻结 | `UPDATE` | ✅ 可行 | 原生落点是 `ISaleOrderCloseManageMaintain.freezeSaleOrder(SaleOrderVO[])`（**非 PF**），需新的服务端分支 |
| 215 解冻 | `UPDATE` | ✅ 可行，但有 ATP 用户确认分支需保留 | `unFreezeSaleOrder(SaleOrderVO[], Map)` 的参数不允许默认"跳过 ATP 检查" |
| 216 整单关闭 | `UPDATE` | ✅ 可行 | 走 `M30CloseBillCloseService.operateBill` → `closeSaleOrder`；需保留关闭原因（`vclosereason`）与签名事由分离 |

**明确不可行/不得超过的既有硬限制（第 2 条同类）**：`SaleOrderPfSaveAdapter.java:30–32` 硬断言 `40060301 + Save + INSERT`，第 46 行 `status==2`，第 48–50 行只允许交易类型 `30…`；`SaleOrderOperationAdapter.java:46–48` 的 `isPilotSave` 只认 `Save`；第 21 行 `if("INSERT".equals(op))require(bills.length==1,…)` 只对 INSERT 限单笔。**这些都必须显式改造，不能靠改配置绕过。**

---

## ⑥ F. 现有测试入口

### F.1 主工程 `tools\build.ps1`（217 行）

- 第 2 行：`param([string]$NcHome='E:\01-Home\NC65home\QuDuTest\nchome',[switch]$Test,[switch]$VerifyRecord)`
- `-Test` 段：**第 182–208 行**。第 183 行收集 `tests\java\**\*.java`；第 184 行并追加 `baseapp\src\test`、`tests\persistence`；第 185 行 `Compile-Sources 'test' $tests`；第 186–198 行生成 `build\runtime-classpath.jar`（manifest Class-Path）；第 199–205 行把各模块 `src\client` 也加进 test classpath（因为测试要加载客户端 XML）；**第 206 行唯一的测试入口**：
  `& $java -ea '-Djava.awt.headless=true' "-Djava.io.tmpdir=$testTemp" -cp ($testClasspath -join ';') nc.test.qd.esign.AllTests`
- 第 207 行失败即 `throw 'Offline tests failed.'`；第 213 行把 `tests_passed=[bool]$Test` 写进 `build\build-result.json`（`schema_version=2`）。

**`tools\build.ps1` 里没有任何 "SaleOrder" 字样**（实测 grep 为空）——销售相关测试是**通过 `AllTests` 注册**进来的，不是脚本直接列举。

### F.2 `AllTests` 注册位置

`tests\java\nc\test\qd\esign\AllTests.java`（121 行，`main` 从 5 行起）：
- **第 21 行**：`nc.ui.uapbd.qd.esign.SaleOrderPilotConfigTests.run();`
- **第 118 行**：`SaleOrderPfSaveAdapterTests.run();`
- 第 119 行：`System.out.println("ALL OFFLINE TESTS PASSED (not NC/Oracle integration verification)");`
- 另第 8 行 `IdentityConfirmationContractTest`、第 115–117 行 `ResultRecoveryTests`/`PermissionRulesTests`/`TypedBusinessAdapterTests`（带 assertions 计数）。
**注意：`SaleOrderPilotPolicyServiceTests` 不在 `AllTests` 里**（该测试只在 990 包目录，见 F.3）。

### F.3 `sales-order-990-release\build.ps1` 三个测试类的注册位置

- 第 98 行：从 `sales-order-990-release\tests\java` 收集测试源；第 99 行 `Compile 'test' $tests`
- **第 113–118 行**（即注册处）：
  ```powershell
  $temp=Join-Path $out 'test-temp';New-Item -ItemType Directory -Path $temp -Force | Out-Null
  foreach($test in @('nc.test.qd.esign.SaleOrderPfSaveAdapterTests','nc.ui.uapbd.qd.esign.SaleOrderPilotConfigTests',
          'nc.impl.uapbd.qd.esign.SaleOrderPilotPolicyServiceTests')) {
      & (Join-Path $jdk 'java.exe') -ea '-Djava.awt.headless=true' "-Djava.io.tmpdir=$temp" -cp $runtime $test
      if($LASTEXITCODE -ne 0){throw "Isolated test failed: $test"}
  }
  ```
  三个类分别在第 **114 行**（`SaleOrderPfSaveAdapterTests`、`SaleOrderPilotConfigTests`）和第 **115 行**（`SaleOrderPilotPolicyServiceTests`）。
- 990 包测试目录与主线测试目录的关系（实测 SHA-256）：
  - `nc\test\qd\esign\SaleOrderPfSaveAdapterTests.java`：990 包 = 主线 = `67CAD28A0442CB3D176F7620CCC7EA7E82C7C931EA1EFA535440F492857B3F21`（14398 B）✅ 一致
  - `nc\ui\uapbd\qd\esign\SaleOrderPilotConfigTests.java`：990 包 = 主线 = `994FA31392BA3F4A4D1E335743F9CB7A014ABD1B1869427243809E6030CB67CB`（4646 B）✅ 一致
  - `nc\impl\uapbd\qd\esign\SaleOrderPilotPolicyServiceTests.java`：990 包 `3BA2E331C042F2568CFD53F576EE54DFFD8DF378906FFB0F29ECC1663B7A56F2`（2475 B）；**主线不存在** ❌
  → **本批新增测试必须同时挂到 `AllTests`（主线）和 990 包 `build.ps1:114–115` 的数组（兼容包），且 990 包目录需要自己的副本。**

### F.4 离线测试覆盖了什么

| 测试 | 覆盖 | 证据位置 |
|---|---|---|
| `SaleOrderPfSaveAdapterTests`（14398 B，`check(` 9 处 + `expectFailure`） | 适配器契约：原生 PF **只调用一次**（`adapter.calls==1`）、返回持久化主键/单号、`describeResult` 回填主键、`PFReturnObject`/数组包装拆解、组织不匹配、交易类型不匹配、PF 参数白名单（仅 `notechecked`）、非单笔、预分配主键已存在、收款/抵消/赠品/物料特征/本次收款等复合场景拒绝、`SoBalanceVO` 空容器判定（`FIXTURE_META`/`CHILD_FIXTURE_META` 桩） | `tests\java\nc\test\qd\esign\SaleOrderPfSaveAdapterTests.java:66–208` |
| `SaleOrderPilotConfigTests`（4646 B） | `SaleOrderPfPreparation.verifyNativeContract()`（PfUtilClient 字节码 SHA-256 固定版本 + 反射字段/方法存在）、`QdSaleOrderConfig.xml` 被 Spring `XmlBeanFactory` 真实解析、`saveAction` bean 类 = `QdSaleOrderSaveAction`、`saveandsendApproveAction` 仍是原生 `SaleOrderSaveAndSendAction`、`cardRefreshAction` 存在、无同名 Java 配置类遮蔽 XML、`isAvailable` 只对 `40060301/Save/INSERT/990` 为真、`SaleOrderSaveHandoff` 一次性绑定/派发/释放语义 | `tests\java\nc\ui\uapbd\qd\esign\SaleOrderPilotConfigTests.java:15–58` |
| `SaleOrderPilotPolicyServiceTests`（2475 B，仅 990 包） | 用 JDK 动态代理伪造 `IPolicyService` delegate：非 990 组织开启销售策略被拒且**不落到 delegate**（`calls==0`）；旧 `save(org,"40060301","Save",…)` 同样被拒；990 放行；其余 5 个调用原样透传且计数正确 | `sales-order-990-release\tests\java\nc\impl\uapbd\qd\esign\SaleOrderPilotPolicyServiceTests.java:14–44` |

### F.5 离线测试没覆盖什么（必须写进验收缺口）

1. **真实 NC 会话/PF**：无 `IplatFormEntry`、无 `PfUtilClient` 真调用、无原生事务；`AllTests:119` 自己声明 "not NC/Oracle integration verification"。
2. **Oracle 事务与回滚**：审计写入故障、业务回滚、CMT 边界完全未测（`publish.json` 也标 "offline only"）。
3. **客户端真实加载**：只验证 Spring XML 能解析 + 类名对得上；**没有验证 NC 客户端实际加载的是 `QdSaleOrderSaveAction`**（需 `BeanConfigFilePath` 生效 + 重新登录）。
4. **多选/批量**：`SaleOrderOperationAdapter:21` 只对 INSERT 限单笔，非 INSERT 的批量相关性未测。
5. **8 项按钮**：**当前 0 覆盖**——`AllTests`/990 包里没有任何 delete/commit/approve/freeze/close 的测试。
6. **旧 probe 拦截链**：`saleorder_esign_probe.xml` / `PluginBeanConfigFilePath` 是否生效未测（`SALE_ORDER_SAVE_PILOT.md:13` 已标注"未找到该 XML，不能证明未生效"）。
7. **`fs_delete` 归属**（附件删除 vs 订单删除）未测、未定。

---

## ⑦ G. 拟改文件清单草案（主线 / 990 兼容包双列）

> 说明：**标"建议名"的类当前不存在**，需设计评审定名后再落地；命名族依据是既存的 `QdSaleOrderSaveAction extends SaleOrderSaveAction`（`QdSaleOrderSaveAction.java:32`）。标"改"的均为已存在文件。凡是 ①-A 中"一致"的 10 个文件，**改一侧必须同步另一侧**，否则 `sales-order-990-release\build.ps1 -Verify` 的输入冻结与 990 包 overlay 清单会漂移。

### G.1 客户端覆盖层（打进 `modules\uapbd\client\lib\qd_esign_uapbd_client.jar`）

| # | 文件 | 990 兼容包副本 | 新增/改 | 依据 |
|---|---|---|---|---|
| 1 | `uapbd\src\client\nc\ui\uapbd\qd\esign\QdSaleOrderConfig.xml` | `sales-order-990-release\uapbd\src\client\nc\ui\uapbd\qd\esign\QdSaleOrderConfig.xml` | **改** | A#1；现仅覆盖 `saveAction`（第 6–12 行）。需为 8 个原生 bean 增加覆盖：`deleteAction`(原生 `saleorder_config.xml`:800 → `SaleOrderDeleteAction`)、`sendApproveAction`(856 → `SaleOrderSendApproveAction`)、`saveandsendApproveAction`(869 → `SaleOrderSaveAndSendAction`，复合动作)、`unsendApproveAction`(881)、`approveAction`(897)、`unApproveAction`(913)、`freezeAction`(1017 → `assist.FreezeAction`)、`unFreezeAction`(1021)、`closeAction`(1025 → `assist.SaleOrderCloseAction`)；这些类全部实测存在于 `uiso_salesorder.jar` |
| 2 | `uapbd\src\client\nc\ui\uapbd\qd\esign\QdSaleOrderDeleteAction.java`（**建议名**） | 同名副本 | **新增** | 覆盖层要求新 bean 类；父类 `nc.ui.so.m30.billui.action.SaleOrderDeleteAction`（`uiso_salesorder.jar` 实测存在） |
| 3 | `…\QdSaleOrderSendApproveAction.java`（**建议名**） | 同名副本 | **新增** | 父类 `SaleOrderSendApproveAction`；须同时接住 `saveandsendApproveAction` 同一提交动作 |
| 4 | `…\QdSaleOrderUnSendApproveAction.java`（**建议名**） | 同名副本 | **新增** | 父类 `SaleOrderUnSendApproveAction` |
| 5 | `…\QdSaleOrderApproveAction.java`（**建议名**） | 同名副本 | **新增** | 父类 `SaleOrderApproveAction`；须定位审批弹窗"确定"后的最终提交点 |
| 6 | `…\QdSaleOrderUnApproveAction.java`（**建议名**） | 同名副本 | **新增** | 父类 `SaleOrderUnApproveAction` |
| 7 | `…\QdSaleOrderFreezeAction.java`（**建议名**） | 同名副本 | **新增** | 父类 `nc.ui.so.m30.billui.action.assist.FreezeAction` |
| 8 | `…\QdSaleOrderUnFreezeAction.java`（**建议名**） | 同名副本 | **新增** | 父类 `assist.UnFreezeAction`；须保留 ATP 用户决策 |
| 9 | `…\QdSaleOrderCloseAction.java`（**建议名**） | 同名副本 | **新增** | 父类 `assist.SaleOrderCloseAction`；关闭原因与签名事由分离 |
| 10 | `uapbd\src\client\nc\ui\uapbd\qd\esign\SaleOrderSaveHandoff.java` | `sales-order-990-release\uapbd\src\client\…\SaleOrderSaveHandoff.java` | **改** | A#3；现 `supports()`（第 13–21 行）硬限 `40060301/Save/INSERT` 且单对象。要么按按钮泛化，要么按动作各建一个 handoff |
| 11 | `uapbd\src\client\nc\ui\uapbd\qd\esign\SaleOrderPfPreparation.java` | `sales-order-990-release\uapbd\src\client\…\SaleOrderPfPreparation.java` | **改**（或按动作新增） | A#4；`SUPPORTED_PF_CLIENT` 固定哈希（第 25 行）+ `prepare()` 硬限 `"WRITE".equals(action)`（第 67 行），第 83–87 行显式拒绝 approve/start/signal 动作。`SAVE/UNSAVE/APPROVE/UNAPPROVE` 需逐动作桥接与版本校验 |
| 12 | `ElectronicSignatureActionInterceptor.java` / `SignatureLifecycleInstaller.java` / `SignatureLifecycleInterceptor.java` / `OperationContextProvider.java` / `AuthorizedRequestConsumer.java`（均 `uapbd\src\client\nc\ui\uapbd\qd\esign\`） | 同名副本（990 包未含，靠 HOME 已装 JAR 提供） | **改（小幅复用）** | 皆已在 `qd_esign_uapbd_client.jar`；8 项按钮复用签名窗口/请求交接契约，预计只需对外暴露可复用钩子；若改动则必须同步进 990 overlay 清单（`package.ps1:38–47`） |

### G.2 公共链路类（`public` 层）

| # | 文件 | 990 兼容包副本 | 新增/改 | 依据 |
|---|---|---|---|---|
| 13 | `uapbd\src\public\nc\vo\uapbd\qd\esign\SaleOrderPilotScope.java` | `sales-order-990-release\uapbd\src\public\nc\vo\uapbd\qd\esign\SaleOrderPilotScope.java` | **改** | A#7；现只有 `GROUP_ID/ORG_ID/SCOPE_ID` + `supports(group,org)`。8 项需按按钮/动作收窄（当前唯一可表达维度是组织，不足以区分 208 与 209–216） |
| 14 | `so\src\public\nc\vo\so\qd\esign\SaleOrderCommand.java` | **无副本**（990 包靠 HOME 已装 `qd_esign_so_public.jar` 提供 `SaleOrderCommand.class`） | **改 / 增 DTO** | A 结论 2：这是"源码在主线、字节码来自已装 JAR"的隐式依赖。非 PF 动作（冻结/解冻/整单关闭）需要新的类型化 command；若新增 DTO 进 990 包，必须同时扩 `package.ps1:43–44` 的 overlay 清单 |
| 15 | `so\src\public\nc\itf\so\qd\esign\ISaleOrderOperationAdapter.java` | `sales-order-990-release\so\src\public\nc\itf\so\qd\esign\`（990 包目录无此文件，靠已装 JAR） | 不改（建议） | 第 1–3 行仅 extends `BusinessOperationAdapter`，无方法集，够用 |
| 16 | `so\src\public\nc\itf\so\qd\esign\ISaleOrderPfSaveAdapter.java` | 有副本（A#8） | 不改 / 或按需新增独立接口 | 第 1–3 行同上；新增独立接口会连带改 `OperationTransactionServiceImpl` ADAPTERS（E.1） |
| 17 | `so\src\public\nc\vo\so\qd\esign\SaleOrderPfSaveCommand.java` / `SaleOrderPfSaveResult.java` | 有副本（A#9/#10） | 改（仅在 PF 保存路径变化时） | 保存回归须保持；`SaleOrderPfPreparation`/`QdSaleOrderSaveAction` 依赖其字段 |
| 18 | `uapbd\src\public\nc\vo\uapbd\qd\esign\OperationContext.java` | — | 改（可能） | `OperationContextProvider`/`AuditCaptureScope`/`MappingRules` 都用它携带 `nodeCode/buttonCode/operationType`；若需携带流程上下文（工作项、决议、关闭原因）则要扩字段 |

### G.3 销售服务适配器（`so` 模块 `private` 层）

| # | 文件 | 990 兼容包副本 | 新增/改 | 依据 |
|---|---|---|---|---|
| 19 | `so\src\private\nc\impl\so\qd\esign\SaleOrderOperationAdapter.java` | `sales-order-990-release\so\src\private\nc\impl\so\qd\esign\SaleOrderOperationAdapter.java` | **改** | A#5；`isPilotSave()`（46–48）只认 `Save`；非 INSERT 走 `ISaleOrderScriptMaintain.saleOrderDelete/Update`（31–34 行）**不是 PF**；`validate` 第 21 行 INSERT 限单笔。需按 8 个按钮加固定分支（服务端决定原生方法），并把流程类动作改走 PF |
| 20 | `so\src\private\nc\impl\so\qd\esign\SaleOrderPfSaveAdapter.java` | `sales-order-990-release\so\src\private\nc\impl\so\qd\esign\SaleOrderPfSaveAdapter.java` | **改** | A#6；`validate` 30–32 行硬限 `Save+INSERT`，46 行 `status==2`，48–50 行只允许交易类型 `30…`。另 B.5：当前 HOME 的类（10021 B）只由手工 staging 支撑，**没有有效 build-record**，改它必须先重建构建证据 |
| 21 | `so\src\private\nc\impl\so\qd\esign\SaleOrderProcessAdapter.java`（**建议名**，承载提交/收回/审批/取消审批的 PF 分支） | 同名副本 | **新增** | 依据 G.3#19 的改造量；或直接扩 `SaleOrderOperationAdapter`。若新增接口，须改 `OperationTransactionServiceImpl:28–51` 白名单 + `so\META-INF\qd_esign_saleorder.upm` |
| 22 | `so\src\private\nc\impl\so\qd\esign\SaleOrderStateAdapter.java`（**建议名**，承载冻结/解冻/整单关闭的 `ISaleOrderCloseManageMaintain` 分支） | 同名副本 | **新增** | 214/215/216 原生落点分别是 `freezeSaleOrder` / `unFreezeSaleOrder(SaleOrderVO[],Map)` / `M30CloseBillCloseService.operateBill→closeSaleOrder`（见 E.3 分析与开发计划第 23–25 行），与 PF 路径不同源 |

### G.4 策略服务

| # | 文件 | 990 兼容包副本 | 新增/改 | 依据 |
|---|---|---|---|---|
| 23 | `sales-order-990-release\uapbd\src\private\nc\impl\uapbd\qd\esign\SaleOrderPilotPolicyService.java` | **990 包唯一副本（主线无）** | **改** | A#11；`save()` 第 50 行只白名单 `"40060301".equals(node) && "Save".equals(button)`；`checkChanges` 第 58 行只认 `SCOPE_ID` 一个范围。8 项需扩为「208–216 共 9 个 scope_id × 按钮」白名单 |
| 24 | `uapbd\src\private\nc\impl\uapbd\qd\esign\PolicyServiceImpl.java` | 无副本（靠 HOME 已装 JAR） | 核对（尽量不改） | E.2/③.2：执行期策略判定是 `OperationTransactionServiceImpl:269–270` **静态调用** `PolicyServiceImpl`，不经 `.upm`。若要按按钮细分执行期策略，改点在这里而不是包装器 |
| 25 | `uapbd\META-INF\qd_esign_core.upm` | **990 包无副本**（由 `package.ps1:90–97` 运行时派生） | **改（主线）/ 派生（990）** | ③.2 + 开发计划第 72 行：主线仍指 `PolicyServiceImpl`，HOME 已指 `SaleOrderPilotPolicyService`。**必须改 `package.ps1:90–97` 的假设**（它 `count -ne 1` 就 throw，当前 HOME 下必然失败），或改为"已正确绑定则保留、未知状态中止" |

### G.5 审计快照

| # | 文件 | 990 兼容包副本 | 新增/改 | 依据 |
|---|---|---|---|---|
| 26 | `uapbd\src\private\nc\bs\uapbd\qd\esign\audit\OrderPhysicalSnapshots.java` | 无副本（靠 HOME 已装 `qd_esign_uapbd_private.jar`） | **改** | E.4；`SALE` enum 只读 `so_saleorder` + `so_saleorder_b`（第 11、74 行）。删除需补"受影响的关联明细"，审批需补工作项/状态证据；`forAdapter` 已支持 `UPDATE/DELETE`（24–31 行）——**这部分不用改** |
| 27 | `uapbd\src\private\nc\bs\uapbd\qd\esign\audit\PhysicalSnapshotReader.java` | 无副本 | 改（按需） | 现 `readLocked/readByPk` 只按单表+主键读；新增关联明细需要新查询能力 |
| 28 | `uapbd\src\private\nc\bs\uapbd\qd\esign\audit\AuditRecorder.java` / `SnapshotFieldLabels.java`（`uapbd\src\public\…\audit\`） | 无副本 | 改（按需） | 新增按钮的证据字段标签/记录分组 |
| 29 | `so\META-INF\qd_esign_saleorder.upm` | **990 包无副本** | **改（主线）→ 需部署** | ③.1：主线 976 B 已含两个 component；**HOME 只有 665 B 单组件**。这是"主线已改、未部署"的既存差距，本批必须先补齐或改映射走 `ISaleOrderOperationAdapter` |

### G.6 配置与脚本

| # | 文件 | 990 兼容包副本 | 新增/改 | 依据 |
|---|---|---|---|---|
| 30 | `sql\nc65-electronic-signature\sale-order-other-buttons\preflight.sql`（**建议名**） | `sales-order-990-release\sql\…\sale-order-other-buttons\` | **新增** | D.2/D.4 的只读预检模板：版本 `1.2.0`、不存在 `1.2.0-RB`、990 业务单元+销售组织、`sm_funcregister/sm_btnregister` 8 组 pk_btn、`qd_es_node` 旧行、`qd_es_policy` 启用状态、无 `RUNNING` 请求 |
| 31 | `sql\…\sale-order-other-buttons\configure.sql`（**建议名**） | 同上 | **新增** | 参照 `configure.sql` 结构（SAVEPOINT + 不自提交 + `%ROWCOUNT=1` 断言），逐按钮 9 条映射；**不得替换 208 保存映射** |
| 32 | `sql\…\sale-order-other-buttons\rollback.sql`（**建议名**） | 同上 | **新增** | D.4 的 `DISABLE` 模式不足以完全回退（`mapping_state` 不回 `UNMAPPED`），需独立恢复脚本 |
| 33 | `config\qd-esign\operation-scope.csv` | 无副本 | **改** | 第 209–216 行 `mappingStatus` 从 `SOURCE_CANDIDATE_ONLY` 升级时更新；`ncFunctionCode='40060301'`、`ncActionCode` 填按钮编码 |
| 34 | `sales-order-990-release\package.ps1` | —（唯一的 990 打包器） | **改** | 必须改 3 处：第 38–47 行 overlay 清单（新增类/新 upm）、第 90–97 行 `.upm` 派生逻辑（见 G.4#25）、第 106–108 行 SQL 复制清单 |
| 35 | `sales-order-990-release\build.ps1` | — | **改** | 第 114–115 行测试类数组需加新测试；第 8–12 行基线 JAR 清单若新增接口 JAR 也要改 |
| 36 | `tools\build.ps1` | — | **不改** | F.1：无 SaleOrder 硬编码，靠 `AllTests` |
| 37 | `docs\nc65-electronic-signature\SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md` / `TASK_TRACKER.md` / `TEST_REPORT.md` / `UNRESOLVED_ISSUES.md` | 无副本 | **改** | S0 证据落账（开发计划第 67 行明确要求维护这四个文档） |

### G.7 测试

| # | 文件 | 990 兼容包副本 | 新增/改 | 依据 |
|---|---|---|---|---|
| 38 | `tests\java\nc\test\qd\esign\AllTests.java` | 无副本（990 包不用 AllTests） | **改** | F.2：新测试需在第 21/118 行附近注册 |
| 39 | `tests\java\nc\test\qd\esign\SaleOrderPfSaveAdapterTests.java` | `sales-order-990-release\tests\java\nc\test\qd\esign\SaleOrderPfSaveAdapterTests.java` | **改** | A/F.3：两处当前哈希相同（`67CAD28A…`），改一侧必须同步 |
| 40 | `tests\java\nc\ui\uapbd\qd\esign\SaleOrderPilotConfigTests.java` | `sales-order-990-release\tests\java\nc\ui\uapbd\qd\esign\SaleOrderPilotConfigTests.java` | **改** | 同上（`994FA313…`）；新增 bean 覆盖需扩 Spring XML 断言 |
| 41 | `tests\java\nc\impl\uapbd\qd\esign\SaleOrderPilotPolicyServiceTests.java` | `sales-order-990-release\tests\java\nc\impl\uapbd\qd\esign\SaleOrderPilotPolicyServiceTests.java`（**仅 990 包有**） | **改（990 包）/ 新增（主线）** | A#11 + F.3：主线缺该类；本批扩白名单必须扩此测试 |
| 42 | `tests\java\nc\test\qd\esign\SaleOrderOtherButtonsTests.java`（**建议名**） | 同名副本 | **新增** | 8 项按钮的动作白名单/DELETE-UPDATE 归类/取消与未知结果处理/N 次原生调用断言（对应开发计划 S1 通过条件） |
| 43 | `tests\java\nc\bs\uapbd\qd\esign\audit\OrderPhysicalSnapshotsTests.java`（**建议名**） | 同名副本 | **新增** | E.4：DELETE 后反向断言、UPDATE BEFORE/AFTER、删除关联明细（当前 `audit` 目录下无该类测试） |
| 44 | `tests\java\nc\impl\so\qd\esign\SaleOrderOperationAdapterTests.java`（**建议名**） | 同名副本 | **新增** | 现无 `so` 模块私有适配器的直接单测（`tests\java\nc\impl\` 下只有 `uapbd` 包） |

---

## ⑧ 未核实项（含明确原因与建议观察位置）

| # | 未核实项 | 原因 | 建议观察位置 |
|---|---|---|---|
| 1 | **数据库实际状态**：`qd_es_node` 是否已登记 208 映射（`mapping_state/validated/adapter_class`）、`qd_es_policy` 是否有启用的销售保存策略、`qd_es_org_control` 总控、`sm_paramregister.BeanConfigFilePath` 是否已切到 `QdSaleOrderConfig.xml` | 任务禁止执行 SQL；`SALE_ORDER_SAVE_PILOT.md:5`（2026-11-10）称"映射仍为 `UNMAPPED/N`、无销售保存策略"，但该文早于 11-11 的部署，**不能当作当前状态** | 只读会话执行 `verify.sql` 的等价 SELECT；或 `configure.sql:75–79` 的 3 条回显 |
| 2 | **`qd_esign_saleorder.upm` 少一个 component 是否已导致保存失败** | 只能证明"未注册 `ISaleOrderPfSaveAdapter`"（③.1）；实际是否报 `Invalid adapter service contract` 取决于 DB 里 `adapter_class` 到底存的是哪个接口 | `qd_es_node.adapter_class`（若=`ISaleOrderPfSaveAdapter`）→ 必然失败；若=`ISaleOrderOperationAdapter` → 可绕过。另查 `nclogs` |
| 3 | **真实 NC 会话加载的类与 PF 调用链** | 需启动 NC + 登录 + 联调，本任务禁止 | 客户端 `BeanConfigFilePath` 生效后看实际 `saveAction` 类；`nclogs` 中的组件加载日志 |
| 4 | **`build-record.json` 的 `-Verify` 实际失败信息** | 禁止运行 `build.ps1`；仅为常量池层面的确定性推断（B.5） | 在允许写的隔离副本上跑 `sales-order-990-release\build.ps1 -Verify` |
| 5 | **`fs_delete`（`1002Z81000000000QCAZ`）是附件删除还是订单删除** | `buttons_1.json` 只有 `btnname=删除`，与 `fs_upload/download/rename/browse` 同父节点；需看 `sm_butnregister` 的按钮组归属或实际 UI | `sm_butnregister` 中该 `pk_btn` 的所属面板/组；`uiso_salesorder.jar` 内附件管理 `SOManageDocumentAction` 绑定 |
| 6 | **审批弹窗"确定"的最终提交点** | 静态只能定位到 `approveAction→SaleOrderApproveAction→PF APPROVE`；弹窗内部最终调用点未反编译确认 | `SaleOrderApproveAction.class` 的反编译/`javap -p -c`（另一队友在 `s0-evidence\javap`） |
| 7 | **`SaleOrderPilotPolicyService` 在 990 包外是否还有第二份（如现场已装 JAR 内）** | 只能确认源码树只有一份、HOME `qd_esign_uapbd_private.jar` 有该类条目 | `qd_esign_uapbd_private.jar` 条目已核（C.3）；现场 QDNC 1.3.0 原始包未核 |
| 8 | **热修版 `SaleOrderPfSaveAdapter` 是否有通过的构建记录** | B.5/B.6：build-record 是旧版；"26 assertions"测试源码未入库 | `build\sales-990-balance-fix\`（只有 `classes/` 与两个手工 ps1，无源码、无 build-record） |
| 9 | **`saleorder_esign_probe.xml` / `PluginBeanConfigFilePath` 是否实际生效** | `SALE_ORDER_SAVE_PILOT.md:13` 自述"在已配置依赖 JAR 和客户端 classes 中未找到该 XML" | 现场客户端缓存；`sm_paramregister` 的 `PluginBeanConfigFilePath` 参数值 |
| 10 | **8 项按钮是否支持多选（批量）** | 未在源码或证据中找到每按钮的选择模式定义 | 原生 `saleorder_config.xml` 的 `actionsOfList`/`actionsOfCard`（第 1464/1491 行）绑定；实测 UI |
| 11 | **`OperationContext.java` 是否需扩流程上下文字段**（工作项、决议、关闭原因） | 未读该文件的完整字段集（仅从使用点反推） | `uapbd\src\public\nc\vo\uapbd\qd\esign\OperationContext.java` |
| 12 | **`ElectronicSignatureActionInterceptor` 对非保存动作的复用度** | 本批未逐行评审该类的按钮/节点假设 | `uapbd\src\client\nc\ui\uapbd\qd\esign\ElectronicSignatureActionInterceptor.java`（`qd_esign_uapbd_client.jar` 内 13077 B） |

---

### 附：本次取证用到的关键实测命令口径（便于复核，均为只读）

- 文件哈希：`Get-FileHash -LiteralPath <f> -Algorithm SHA256`
- JAR 条目与条目哈希：`[IO.Compression.ZipFile]::OpenRead(<jar>)` → `Entries` / `entry.Open()` + `SHA256`
- 类常量池字符串：读 `.class` 字节，取可打印 ASCII 连续串（`>=32 && <127`，长度≥6）后做 `Compare-Object`
- JSON：`Get-Content -Raw -Encoding UTF8 | ConvertFrom-Json`
- CSV：`Get-Content -Encoding UTF8` 按行号取（`operation-scope.csv` 共 244 行，含表头）
