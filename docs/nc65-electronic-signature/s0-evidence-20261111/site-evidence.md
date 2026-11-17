# 销售订单维护 `40060301` 第 209–216 行 现场库证据（S0）

> 取证对象：NC65「销售订单维护」`40060301`，正式清单 `operation-scope.csv` 源行 209–216 共 8 项操作。
> 取证性质：**纯只读取证**。未连接 NC、未执行任何 SQL、未启动/停止 NC、未修改项目目录 `E:\02-Project\QiDu\QD-E-Siganature` 下任何文件、未部署。
> 现场库标识：连接名 `齐都药业测试库`，schema `QDNC`（见各 JSON 的 `connection`/`schema` 键，如 `site-evidence/2026-11-07/buttons_1.json` 键 `connection`、`schema`）。
> 证据时点：按钮/参数导出为 2026-11-07（`client_date`），页签按钮导出为 2026-11-11（`client_date`）。
> 行号约定：本文 `buttons_1.json:Lxx` 指该按钮 **`"PK_BTN"` 字段所在行**（每个按钮记录块共 12 行，自 `{` 起算，PK_BTN 为块内第 4 行）。CSV 行号指文件物理行号（含表头为第 1 行）。
> 本文件是 S0 证据表素材，不是验收结论；本文出现的「静态核实」仅表示“现场库只读导出中存在该字段值”。

## 0. 结论速览

1. 8 项操作在 `40060301` 上都能在**功能节点直属按钮**层找到唯一注册行（`Delete`/`Commit`/`UnCommit`/`Approve`/`UnApprove`/`Freeze`/`UnFreeze`/`BillClose`），全部 `ISENABLE=Y`、`BTNOWNERTYPE=0`、`PARENT_ID=1001Z810000000000UV6`（=该功能 `CFUNID`），`ISBUTTONPOWER` 均为 `N`。
2. `pagecode` / `pk_page` **未核实**：`buttons_1.json` 的 SQL 只 JOIN `sm_funcregister`+`sm_butnregister`，没有 `sm_pageregister` 字段；2026-11-11 的页签按钮查询只覆盖 6 个 `10140*` 客商/物料功能，**不含 `40060301`/`40060303`**（该文件 0 行命中）。
3. `Delete`/`1001Z810000000004TO3` 与 `fs_delete`/`1002Z81000000000QCAZ` 同名不同注册。`fs_delete` 与同 parent 下的 `fs_upload`/`fs_download`/`fs_rename`/`fs_browse` 构成 5 枚 `fs_*` 按钮组，且同 parent 另有 `File`/附件管理 入口 —— 判定 `fs_delete` 属**附件管理按钮组**，不是删订单；其客户端动作绑定（只删附件）仍属未核实。
4. 正式清单 `operation-scope.csv` 第 209–216 行**不含任何 NC 按钮编码**（`ncFunctionCode`/`ncActionCode` 全空，`mappingStatus=SOURCE_CANDIDATE_ONLY`），因此“与现场 btncode 一一对应”不能由正式清单本身完成；对应关系只存在于派生的 `site-20261107/operation-bindings.csv`（7 项 1:1 一致，第 209 行「删除」因两个同名注册而无唯一项）。
5. `40060303` 销售订单关闭管理与 `40060301` **不共用按钮注册主键**：两节点 `PK_BTN` 无交集；按钮编码（大小写敏感）仅 4 个同名 `BillClose`/`BillLinkQuery`/`Query`/`Refresh`，另有 `Preview`↔`preView`、`Print`↔`print`/`printGroup` 仅大小写相近。`40060303` 没有 `Delete`/`Commit`/`Approve` 等本批按钮。第 217–228 行可作为独立批次。
6. 212「审批-确定」在现场注册中**未被标记为上级按钮**：`BTNOWNERTYPE=0`、`PARENT_ID=CFUNID`。文档中的“上级按钮/上级入口”是任务语义描述（`SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md:21`、`UNMATCHED_BUTTONS_20261110.md:85,95`），不是 `sm_butnregister` 字段值。`Approve` 之下是否另有子页/子按钮注册 **未核实**（导出 SQL 未取 `parent_id=pk_btn` 的行）。

---

## 1. 数据源与完整性（S0 冻结用）

| 文件（项目相对路径，只读） | 字节 | SHA256 |
|---|---:|---|
| `docs/nc65-electronic-signature/site-evidence/2026-11-07/buttons_1.json` | 411546 | `16113DED352B771FD1A8BFCB6C20246C2D7BEB97A8FA01E6EAC7050E37F65309` |
| `docs/nc65-electronic-signature/site-evidence/2026-11-11/page_button_registrations.json` | 21598 | `2DB93EEA0A0D8F690960E8C4404A838028A2AC8B6E60EDC8BF1D53A34B5C59D7` |
| `docs/nc65-electronic-signature/site-evidence/2026-11-07/node_ui_params.json` | 9192 | `A55348E04CF4368ED20A57085722F2C910247A56A11AEA9E7A21254BEAA6096D` |
| `docs/nc65-electronic-signature/site-evidence/2026-11-07/function_candidates.json` | 117882 | `E03BCEB5C12A90C28FDFA253B592F714F7BE629F4717824B4F27E3E9ED241D16` |
| `config/qd-esign/operation-scope.csv` | 68180 | `2071A300DD56FB65C5DA1279E81E30C17B59B02454F5456A73EF5A3395F82FB4` |
| `config/qd-esign/node-scope.csv` | 8478 | `B1B9C4CDCE7DF52A68F4B0C07FC6F7B92A0F3B1BDD324B38F9F25C9EE41B4177` |
| `config/qd-esign/site-20261107/operation-bindings.csv` | 88050 | `177A2FDED4371164117665911DDEAC3DDB3D27EA80289D73ACEC4BC3CC8698AB` |
| `config/qd-esign/site-20261107/node-bindings.csv` | 11490 | `8683723F603614232081C69E53415DA7ED704B0F863F644F793282AB7388BF07` |

`buttons_1.json` 的查询原文（键 `sql`，行 6）：`SELECT f.funcode,f.fun_name,b.pk_btn,b.btncode,b.btnname,b.btnownertype,b.parent_id,b.isenable,b.isbuttonpower,b.iskeyfunc FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode) WHERE NVL(f.dr,0)=0 AND NVL(b.dr,0)=0 AND f.funcode IN (...'40060301','40060303'...) ORDER BY f.funcode,b.btncode`。
⇒ **该导出只含“功能节点直属按钮”（`parent_id` = 功能 `cfunid` 或 `funcode`），不含 `parent_id` = 页签 `pk_page` 或 = 其它按钮 `pk_btn` 的行。** 这是本文多处「未核实」的结构性原因。

`buttons_1.json` 中 `BTNOWNERTYPE` 全库取值分布：**0 → 928/928 行**；而 2026-11-11 页签查询 48 行的 `BTNOWNERTYPE` 全为 `1`。结合 `PAGE_REGISTRATION_20261110.md:48`（“按钮 `BTNOWNERTYPE=1` 为本次查得的页签按钮记录形态”），本批证据内的形态对应为 **0=功能直属按钮，1=页签按钮**；数据库字典无该字段说明（`platform_columns.json` 仅给出 `SM_BUTNREGISTER.BTNOWNERTYPE NUMBER(22) NULLABLE=Y`，行 `L2920-L2925`，无注释），故这是**形态对应，不是官方枚举定义**。

节点级交叉核对：`node-bindings.csv:L39` 记 `40060301` 的 `registeredFunctionPk=1001Z810000000000UV6`、`registeredButtonCount=68`；与 `buttons_1.json` 中 `FUNCODE=40060301` 的 **68 行**完全吻合。`node-bindings.csv:L40` 记 `40060303` 为 `1001Z810000000001CMN`、**18 行**，与 `buttons_1.json` 中 `FUNCODE=40060303` 的 18 行吻合。

---

## 2. ① 8 项按钮现场注册表（逐字段代码值）

功能节点本身（`function_candidates.json` 中 `FUNCODE=40060301` 行，`L1883` 起）：
`CFUNID=1001Z810000000000UV6`、`FUNCODE=40060301`、`FUN_NAME=销售订单维护`、`CLASS_NAME=nc.ui.pubapp.uif2app.ToftPanelAdaptorEx`、`ORGTYPECODE=SALEORGTYPE000000000`、`PK_GROUP=~`、`ISENABLE=Y`、`ISBUTTONPOWER=Y`、`ISFUNCTYPE=N`、`OWN_MODULE=4006`、`PARENT_ID=1001Z810000000000YQG`（父节点 `PARENT_CODE=400603` 「销售订单」）、`MDID=b4bde646-3f4d-4b95-919f-8ebe03c5f849`。

### 2.1 八个按钮的注册行

| 原表行 | 操作（正式清单 buttonName） | `btncode` | `btnname` | `pk_btn` | `parent_id` | `btnownertype` | `isenable` | `isbuttonpower` | `iskeyfunc` | 证据 |
|---:|---|---|---|---|---|---|---|---|---|---|
| 209 | 删除 | `Delete` | 删除 | `1001Z810000000004TO3` | `1001Z810000000000UV6` | `0` | `Y` | `N` | `N` | `buttons_1.json:L203`（块 L200–L211） |
| 210 | 提交 | `Commit` | 提交 | `1001Z810000000004TOA` | `1001Z810000000000UV6` | `0` | `Y` | `N` | `N` | `buttons_1.json:L131`（块 L128–L139） |
| 211 | 收回 | `UnCommit` | 收回 | `1001Z810000000004TOG` | `1001Z810000000000UV6` | `0` | `Y` | `N` | `N` | `buttons_1.json:L683`（块 L680–L691） |
| 212 | 审批-确定 | `Approve` | 审批 | `1001Z810000000004TO2` | `1001Z810000000000UV6` | `0` | `Y` | `N` | `N` | `buttons_1.json:L35`（块 L32–L43） |
| 213 | 取消审批 | `UnApprove` | 取消审批 | `1001Z810000000004TOO` | `1001Z810000000000UV6` | `0` | `Y` | `N` | `N` | `buttons_1.json:L671`（块 L668–L679） |
| 214 | 辅助功能-冻结 | `Freeze` | 冻结 | `1001Z810000000004TP5` | `1001Z810000000000UV6` | `0` | `Y` | `N` | `N` | `buttons_1.json:L251`（块 L248–L259） |
| 215 | 辅助功能-解冻 | `UnFreeze` | 解冻 | `1001Z810000000004TOE` | `1001Z810000000000UV6` | `0` | `Y` | `N` | `N` | `buttons_1.json:L695`（块 L692–L703） |
| 216 | 辅助功能-整单关闭 | `BillClose` | 整单关闭 | `1001Z810000000004TP1` | `1001Z810000000000UV6` | `0` | `Y` | `N` | `N` | `buttons_1.json:L83`（块 L80–L91） |

字段来源：`buttons_1.json` 各行 `rows[*]` 的 `FUNCODE/FUN_NAME/PK_BTN/BTNCODE/BTNNAME/BTNOWNERTYPE/PARENT_ID/ISENABLE/ISBUTTONPOWER/ISKEYFUNC`。

### 2.2 `pagecode` / `pk_page` / `cfunid`

- `cfunid` = **`1001Z810000000000UV6`**（静态核实）。三重一致：`function_candidates.json` 的 `CFUNID`、八个按钮的 `PARENT_ID`、`node-bindings.csv:L39` 的 `registeredFunctionPk`。
- `pagecode` = **未核实**；`pk_page` = **未核实**。
  - 原因 1：`buttons_1.json` 是 `sm_funcregister`+`sm_butnregister` 两表 JOIN，导出字段中没有 `PAGECODE`/`PK_PAGE`。
  - 原因 2：唯一的页签级导出 `site-evidence/2026-11-11/page_button_registrations.json` 的 `funcode` 白名单为 `10140CUG,10140CUO,10140SUG,10140SUO,10140MAG,10140MAO`（`sql` 行 `L6`；实测 `rows[*].FUNCODE` 去重结果即这 6 个），`40060301`/`40060303` **命中 0 行**。因此**不能**用该文件回答本节点页签，也不能反驳“本节点存在页签按钮”。
  - 观察位置：对 QDNC 只读执行与 `page_button_registrations.json` 相同的三表 JOIN，把 `f.funcode IN (...)` 换成 `('40060301','40060303')`；重点看 `b.parent_id = p.pk_page` 且 `b.btnownertype=1` 的行。该查询**未在本会话执行**。

### 2.3 212「审批-确定」专项

| 检查点 | 结论 | 证据 |
|---|---|---|
| `Approve` 的 `btnname` | **审批**（不是“审批-确定”） | `buttons_1.json:L37` |
| 是否被标为“上级按钮” | **否（注册层面无标记）**。`BTNOWNERTYPE=0`，与其余 67 枚本节点按钮同形态；`PARENT_ID=1001Z810000000000UV6` 即功能 `CFUNID`，不是另一个按钮 | `buttons_1.json:L38-L39` |
| 68 枚本节点注册中是否有名称含“确定”的按钮 | **没有**（`BTNNAME` 全量 68 条无“确定”二字） | 见附录 A |
| 是否存在挂在 `Approve` 其下的子页/子按钮注册 | **未核实**。导出 SQL 只取 `parent_id=cfunid/funcode`，未取 `parent_id='1001Z810000000004TO2'` 的行 | `buttons_1.json` 键 `sql`（行 6）；观察位置：`sm_butnregister WHERE NVL(dr,0)=0 AND parent_id='1001Z810000000004TO2'` |
| “上级按钮/上级入口”说法的出处 | 文档口径，不是库字段：`SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md:21` 写作「`Approve` / `1001Z810000000004TO2`（上级按钮）」；`UNMATCHED_BUTTONS_20261110.md:85` 将 212 归入“已找到的上级入口 `Approve`，最终动作仍需确认”，`:95` 列出销售订单 212 | 仅作对照 |

⇒ 对该行的取证结论：现场注册能证明“`40060301` 上存在启用的 `Approve`/审批按钮注册”，**不能**证明“审批弹窗里的『确定』是一个独立注册按钮”，也**不能**证明其最终提交点；后者属开发侧类级/会话级核实（见 `s0-evidence/javap/` 中同行产出的 `SaleOrderApproveAction` 反编译线索，非现场库证据）。

---

## 3. ② `Delete` 与 `fs_delete` 的差异与归属判定

### 3.1 两个同名“删除”的注册行对照

| 项 | `Delete`（订单删除候选） | `fs_delete`（附件删除候选） |
|---|---|---|
| `btncode` | `Delete` | `fs_delete` |
| `btnname` | 删除 | 删除 |
| `pk_btn` | `1001Z810000000004TO3` | `1002Z81000000000QCAZ` |
| `pk_btn` 号段 | `1001Z810…`（与业务按钮同段） | `1002Z810…`（与 `fs_upload/fs_download/fs_rename/fs_browse` 同段） |
| `btnownertype` | `0` | `0` |
| `parent_id` | `1001Z810000000000UV6` | `1001Z810000000000UV6` |
| `isenable` | `Y` | `Y` |
| `isbuttonpower` | **`N`** | **`Y`** |
| `iskeyfunc` | `N` | `N` |
| 证据 | `buttons_1.json:L203`（块 L200–L211） | `buttons_1.json:L755`（块 L752–L763） |

### 3.2 同一 parent（`1001Z810000000000UV6`）下 `fs_*` 按钮清单原文行

`parent_id` 全部为 `1001Z810000000000UV6`（= `40060301` 的 `CFUNID`）。以下 5 行为 `buttons_1.json` 原文（`PK_BTN` 行号标注）：

| `btncode` | `btnname` | `pk_btn` | `btnownertype` | `isenable` | `isbuttonpower` | `iskeyfunc` | 证据 |
|---|---|---|---|---|---|---|---|
| `fs_browse` | 查看 | `1002Z81000000000QCB0` | `0` | `Y` | `Y` | `N` | `L743`（块 L740–L751） |
| `fs_delete` | 删除 | `1002Z81000000000QCAZ` | `0` | `Y` | `Y` | `N` | `L755`（块 L752–L763） |
| `fs_download` | 下载 | `1002Z81000000000QCAY` | `0` | `Y` | `Y` | `N` | `L767`（块 L764–L775） |
| `fs_rename` | 重命名 | `1002Z81000000000QCB1` | `0` | `Y` | `Y` | `N` | `L779`（块 L776–L787） |
| `fs_upload` | 上传 | `1002Z81000000000QCAX` | `0` | `Y` | `Y` | `N` | `L791`（块 L788–L799） |

同一 parent 下另有附件入口按钮（原文行）：`File` / 附件管理 / `1001Z810000000004TOU` / `btnownertype=0` / `isenable=Y` / `isbuttonpower=N` / `iskeyfunc=N`，`buttons_1.json:L239`（块 L236–L247）。

同 parent 下另有 61 枚非 `fs_` 的订单业务按钮（含 `Delete`、`Save`、`Edit`、`Approve`、`Commit`、`BillClose` 等），完整 68 条见 **附录 A**。

### 3.3 判定

**判定：`fs_delete` 属于「附件管理」按钮组，不是删订单按钮。** 依据（均为现场注册证据）：

1. 同 parent 下恰好存在一个 5 枚一组的 `fs_*` 按钮族：查看/删除/下载/重命名/上传——这 5 个动作只构成“附件面板”的完整操作集，不构成订单业务动作集；
2. `fs_*` 5 枚全部 `ISBUTTONPOWER=Y`，而订单业务按钮（含 `Delete`）几乎全为 `N`（本节点 68 枚中仅 `QueryCustCredit`、`RefECC1`、`SaveTemporary`、`ShowAll`、5 枚 `fs_*` 为 `Y`）；
3. 同 parent 同时存在独立的 `File`/附件管理 入口按钮，附件面板内的 5 个 `fs_*` 动作与之呼应；
4. 本节点“删订单”已有独立的 `Delete`/`1001Z810000000004TO3` 注册，一行操作名“删除”对应到两个注册，属同名歧义而非同一按钮；
5. 文档侧同口径记录：`UNMATCHED_BUTTONS_20261110.md:53`（“以下每个节点都同时注册了启用的 `Delete` 和 `fs_delete`，显示名都叫‘删除’”）、`:61`（本节点 209 行）；`SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md:18`（“`fs_delete` 同组还有上传、下载、重命名，优先核对是否是附件删除。若属附件，留在本批范围外”）。

**未核实（必须保留）**：`fs_delete` 的**客户端动作绑定**是否只删除附件、是否可能删除订单，不能由 `sm_butnregister` 行证明。观察位置：客户端 XML（`node_ui_params.json:L44-L48` 指向 `nc/ui/so/m30/billui/model/saleorder_config.xml`）中 `fs_delete` 的 action 定义；本机 JAR 提取件 `s0-evidence/jar-extract/uiso_salesorder/nc/ui/so/m30/billui/model/saleorder_config.xml` 内**未出现** `fs_delete`/`fs_` 字符串（旁证，非现场库），与“`fs_*` 由附件面板框架提供、不在单据 UI XML 中定义”相符。建议在 S0 由开发侧在真实会话中观察附件面板点击“删除”后是否只动附件表。

---

## 4. ③ 正式清单 `operation-scope.csv` 第 209–216 行字段

### 4.1 表头（物理第 1 行）

`sourceFile,sourceSheet,sourceRow,scopeId,nodeId,category,nodeName,buttonName,adapterFamily,mappingStatus,ncFunctionCode,ncActionCode,notes`

### 4.2 逐行字段值（物理行 = sourceRow，此段恰好对齐）

公共列：`sourceFile=C:\Users\Freud\Desktop\NC电子签名表.xlsx`、`sourceSheet=Sheet2`、`nodeId=QD-NODE-642F7D874EA13550`、`category=销售管理`、`nodeName=销售订单维护`、`adapterFamily=SO_PFLOW`、`mappingStatus=SOURCE_CANDIDATE_ONLY`、`ncFunctionCode=`（空）、`ncActionCode=`（空）、`notes=仅需求目录；未登记真实NC功能/按钮编码，未实现或验证本操作适配。`

| 物理行 | `sourceRow` | `scopeId` | `buttonName` |
|---:|---:|---|---|
| L209 | 209 | `QD-ES-FEFBC5B292C330AD` | 删除 |
| L210 | 210 | `QD-ES-4671CC2E70A34B97` | 提交 |
| L211 | 211 | `QD-ES-4F23D38FB22BA45F` | 收回 |
| L212 | 212 | `QD-ES-9C0B035FFEB2110D` | 审批-确定 |
| L213 | 213 | `QD-ES-51BB76212FFCD156` | 取消审批 |
| L214 | 214 | `QD-ES-C7E51B3ED69F8B05` | 辅助功能-冻结 |
| L215 | 215 | `QD-ES-BF1F44B944C037B3` | 辅助功能-解冻 |
| L216 | 216 | `QD-ES-2A439AB1FE3628B0` | 辅助功能-整单关闭 |

原文行（逐字，含中文标点）：

```text
L209: C:\Users\Freud\Desktop\NC电子签名表.xlsx,Sheet2,209,QD-ES-FEFBC5B292C330AD,QD-NODE-642F7D874EA13550,销售管理,销售订单维护,删除,SO_PFLOW,SOURCE_CANDIDATE_ONLY,,,仅需求目录；未登记真实NC功能/按钮编码，未实现或验证本操作适配。
L210: C:\Users\Freud\Desktop\NC电子签名表.xlsx,Sheet2,210,QD-ES-4671CC2E70A34B97,QD-NODE-642F7D874EA13550,销售管理,销售订单维护,提交,SO_PFLOW,SOURCE_CANDIDATE_ONLY,,,仅需求目录；未登记真实NC功能/按钮编码，未实现或验证本操作适配。
L211: C:\Users\Freud\Desktop\NC电子签名表.xlsx,Sheet2,211,QD-ES-4F23D38FB22BA45F,QD-NODE-642F7D874EA13550,销售管理,销售订单维护,收回,SO_PFLOW,SOURCE_CANDIDATE_ONLY,,,仅需求目录；未登记真实NC功能/按钮编码，未实现或验证本操作适配。
L212: C:\Users\Freud\Desktop\NC电子签名表.xlsx,Sheet2,212,QD-ES-9C0B035FFEB2110D,QD-NODE-642F7D874EA13550,销售管理,销售订单维护,审批-确定,SO_PFLOW,SOURCE_CANDIDATE_ONLY,,,仅需求目录；未登记真实NC功能/按钮编码，未实现或验证本操作适配。
L213: C:\Users\Freud\Desktop\NC电子签名表.xlsx,Sheet2,213,QD-ES-51BB76212FFCD156,QD-NODE-642F7D874EA13550,销售管理,销售订单维护,取消审批,SO_PFLOW,SOURCE_CANDIDATE_ONLY,,,仅需求目录；未登记真实NC功能/按钮编码，未实现或验证本操作适配。
L214: C:\Users\Freud\Desktop\NC电子签名表.xlsx,Sheet2,214,QD-ES-C7E51B3ED69F8B05,QD-NODE-642F7D874EA13550,销售管理,销售订单维护,辅助功能-冻结,SO_PFLOW,SOURCE_CANDIDATE_ONLY,,,仅需求目录；未登记真实NC功能/按钮编码，未实现或验证本操作适配。
L215: C:\Users\Freud\Desktop\NC电子签名表.xlsx,Sheet2,215,QD-ES-BF1F44B944C037B3,QD-NODE-642F7D874EA13550,销售管理,销售订单维护,辅助功能-解冻,SO_PFLOW,SOURCE_CANDIDATE_ONLY,,,仅需求目录；未登记真实NC功能/按钮编码，未实现或验证本操作适配。
L216: C:\Users\Freud\Desktop\NC电子签名表.xlsx,Sheet2,216,QD-ES-2A439AB1FE3628B0,QD-NODE-642F7D874EA13550,销售管理,销售订单维护,辅助功能-整单关闭,SO_PFLOW,SOURCE_CANDIDATE_ONLY,,,仅需求目录；未登记真实NC功能/按钮编码，未实现或验证本操作适配。
```

节点表对应：`node-scope.csv:L33` → `sourceFirstRow=208, sourceLastRow=216, nodeId=QD-NODE-642F7D874EA13550, nodeName=销售订单维护, operationCount=9, mappingStatus=PENDING_RUNTIME_NODE_REGISTRATION`。即该节点共 9 项（208 保存 + 209–216 八项）；**208 不在本批**（208 是已有保存试点）。

### 4.3 与现场 `btncode` 的一一对应核对

**关键事实：正式清单本身不含按钮编码，无法单独完成“正式清单 ↔ 现场 btncode”映射。** 该 CSV 的 `ncFunctionCode`/`ncActionCode` 八行全空，`mappingStatus` 八行均为 `SOURCE_CANDIDATE_ONLY`，`notes` 八行均声明“未登记真实NC功能/按钮编码”。

对应关系只存在于**派生快照** `config/qd-esign/site-20261107/operation-bindings.csv`（其表头第 1 行含 `registeredButtonPk,entryButtonCode,entryButtonName,mappingState`）：

| 源行 | scopeId | `ncFunctionCode` | `entryButtonCode` | `registeredButtonPk` | `mappingState` | 证据 | 与 `buttons_1.json` 是否一致 |
|---:|---|---|---|---|---|---|---|
| 209 | `QD-ES-FEFBC5B292C330AD` | 40060301 | （空） | （空） | `NO_UNIQUE_REGISTERED_BUTTON` | `operation-bindings.csv:L278` | **无唯一项**：现场同名注册 2 条（`Delete`/`fs_delete`），见 §3 |
| 210 | `QD-ES-4671CC2E70A34B97` | 40060301 | `Commit` | `1001Z810000000004TOA` | `REGISTERED_BUTTON_RPC_PENDING` | `:L279` | 一致 |
| 211 | `QD-ES-4F23D38FB22BA45F` | 40060301 | `UnCommit` | `1001Z810000000004TOG` | `REGISTERED_BUTTON_RPC_PENDING` | `:L280` | 一致 |
| 212 | `QD-ES-9C0B035FFEB2110D` | 40060301 | `Approve` | `1001Z810000000004TO2` | `ENTRY_ONLY_SUBPAGE_PENDING` | `:L281` | 一致（`ncActionCode` 为空，仅入口） |
| 213 | `QD-ES-51BB76212FFCD156` | 40060301 | `UnApprove` | `1001Z810000000004TOO` | `REGISTERED_BUTTON_RPC_PENDING` | `:L282` | 一致 |
| 214 | `QD-ES-C7E51B3ED69F8B05` | 40060301 | `Freeze` | `1001Z810000000004TP5` | `REGISTERED_BUTTON_RPC_PENDING` | `:L283` | 一致 |
| 215 | `QD-ES-BF1F44B944C037B3` | 40060301 | `UnFreeze` | `1001Z810000000004TOE` | `REGISTERED_BUTTON_RPC_PENDING` | `:L284` | 一致 |
| 216 | `QD-ES-2A439AB1FE3628B0` | 40060301 | `BillClose` | `1001Z810000000004TP1` | `REGISTERED_BUTTON_RPC_PENDING` | `:L285` | 一致 |

（`operation-bindings.csv` 中 `bindingId` 形如 `QD-ES-XXX@40060301`，`scopeId` 列保留无后缀原值。）

**编码不一致项**：仅第 209 行。现场存在两个 `BTNNAME=删除` 的启用注册（`Delete`/`1001Z810000000004TO3` 与 `fs_delete`/`1002Z81000000000QCAZ`），派生映射按“同名唯一”规则无法收敛，故记 `NO_UNIQUE_REGISTERED_BUTTON`；其余 7 行 `entryButtonCode`、`registeredButtonPk` 与 `buttons_1.json` 逐字段一致（按钮编码大小写、主键均相同）。

**取证陷阱（务必避免）**：`operation-bindings.csv` 的 `pageName` 列**不是** `sm_pageregister.PAGECODE`；第 277–285 行该列值等于 `buttonName`（如“保存,保存”“删除,删除”“审批-确定,审批-确定”）。不能据此填 S0 的 `pagecode`。

---

## 5. ④ `40060303` 与 `40060301` 的按钮重叠核对

`40060303`「销售订单关闭管理」功能节点：`CFUNID=1001Z810000000001CMN`（`node-bindings.csv:L40`，另 `function_candidates.json` 同 `funcode` 行；`registeredButtonCount=18`），客户端配置 `nc/ui/so/m30/closemanage/model/m30close_config.xml`。其 18 条注册（全部 `parent_id=1001Z810000000001CMN`、`btnownertype=0`、`isenable=Y`、`isbuttonpower=N`）见附录 B。

### 5.1 按钮编码重叠

| 比较口径 | 重叠项 | 说明 |
|---|---|---|
| `PK_BTN`（注册主键） | **无**（0 项） | 两节点主键号段不同：`40060301` 为 `1001Z810000000004T??`/`1002Z81000000000Q?`，`40060303` 为 `1001Z810000000003DA?`–`1001Z810000000003DB?`；全库 `buttons_1.json` 无重复 `PK_BTN` |
| `BTNCODE`（大小写敏感） | **4 项**：`BillClose`、`BillLinkQuery`、`Query`、`Refresh` | 同名但各自独立注册，主键不同 |
| `BTNCODE`（忽略大小写） | 6 项：上述 4 项 + `Preview`↔`preView`、`Print`↔`print`/`printGroup` | 仅大小写/命名风格相近，不应视为同一编码 |

逐项对照（重叠 4 项）：

| `btncode` | `40060301` `pk_btn` / `isenable` / `isbuttonpower` | `40060303` `pk_btn` / `isenable` / `isbuttonpower` |
|---|---|---|
| `BillClose` | `1001Z810000000004TP1` / `Y` / `N`（`buttons_1.json:L83`） | `1001Z810000000003DAZ` / `Y` / `N`（`buttons_1.json:L827`） |
| `BillLinkQuery` | `1001Z810000000004TOR` / `Y` / `N`（`L95`） | `1001Z810000000003DAV` / `Y` / `N`（`L839`） |
| `Query` | `1001Z810000000004TOK` / `Y` / `N`（`L407`） | `1001Z810000000003DAT` / `Y` / `N`（`L935`） |
| `Refresh` | `1001Z810000000004TOV` / `Y` / `N`（`L575`） | `1001Z810000000003DAU` / `Y` / `N`（`L947`） |

### 5.2 本批 8 项按钮在 `40060303` 的匹配情况

`40060303` 的 18 个 `BTNCODE` 为：`BillClose, BillLinkQuery, BillOpen, InvoiceClose, InvoiceOpen, LineClose, LineOpen, OutClose, OutOpen, Query, Refresh, SendClose, SendOpen, SettleClose, SettleOpen, preView, print, printGroup`。
本批 8 项中：**只有 `BillClose`（216 辅助功能-整单关闭）在 `40060303` 有同名编码**，但为独立注册（不同 `pk_btn`、不同 parent）；其余 7 项（`Delete`/`Commit`/`UnCommit`/`Approve`/`UnApprove`/`Freeze`/`UnFreeze`）在 `40060303` **完全没有**同名注册，`40060303` 也没有 `fs_*` 附件按钮。

### 5.3 结论（供“第 217–228 行为独立批次”判断）

- 两节点**不共用任何按钮注册主键**，`pk_btn` 无交集；
- 同名 `BillClose` 是两份独立注册：216 行对应 `40060301` 的 `1001Z810000000004TP1`，217 行对应 `40060303` 的 `1001Z810000000003DAZ`（`operation-bindings.csv:L285` 与 `:L286` 亦分别指向这两个主键）；
- `40060303` 的 12 行正式操作（217–228）映射到其 12 枚开关类按钮（`BillClose/BillOpen/LineClose/LineOpen/SendClose/SendOpen/OutClose/OutOpen/InvoiceClose/InvoiceOpen/SettleClose/SettleOpen`，见 `operation-bindings.csv:L286-L297`），另有 6 枚非本批操作按钮（`Query`/`Refresh`/`BillLinkQuery`/`preView`/`print`/`printGroup`）；
- 因此**支持**“217–228 为独立批次”的判断：客户端入口、按钮权限与策略需按不同 `pk_btn`/`cfunid` 分别核对，不能因共用“整单关闭”服务而合并。

---

## 6. ⑤ 权限与参数可用性（逐按钮）

### 6.1 按钮注册层（静态核实）

| 原表行 | `btncode` | `isenable` | `isbuttonpower`（是否受按钮权限控制） | `iskeyfunc` | 判定 |
|---:|---|---|---|---|---|
| 209 | `Delete` | `Y` | `N` | `N` | 静态核实；该按钮注册为“不参与按钮权限控制” |
| 210 | `Commit` | `Y` | `N` | `N` | 静态核实 |
| 211 | `UnCommit` | `Y` | `N` | `N` | 静态核实 |
| 212 | `Approve` | `Y` | `N` | `N` | 静态核实 |
| 213 | `UnApprove` | `Y` | `N` | `N` | 静态核实 |
| 214 | `Freeze` | `Y` | `N` | `N` | 静态核实 |
| 215 | `UnFreeze` | `Y` | `N` | `N` | 静态核实 |
| 216 | `BillClose` | `Y` | `N` | `N` | 静态核实 |

说明：`isbuttonpower` 是 `sm_butnregister` 上的**注册属性**（该按钮是否纳入按钮权限体系），**不等于**“某角色/某用户已被授予该按钮”。本证据集中**没有**角色-按钮授权数据（`sm_role`/`sm_power`/功能权限分配表均未查询），故“谁能点”= **未核实**。注意功能节点本身 `ISBUTTONPOWER=Y`（`function_candidates.json:L1883` 起，节点受按钮权限控制），但 8 个按钮自身 `ISBUTTONPOWER=N`，两者层级不同、不可混用。

### 6.2 节点参数层（静态核实）

`node_ui_params.json`（`sql` 行 6：`SELECT f.funcode,f.fun_name,p.pk_param,p.paramname,p.paramvalue FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid ...`）中 `40060301` 共 3 条参数：

| `pk_param` | `paramname` | `paramvalue` | 证据 |
|---|---|---|---|
| `1001Z810000000000UV7` | `BeanConfigFilePath` | `nc/ui/so/m30/billui/model/saleorder_config.xml` | `node_ui_params.json:L44-L48` |
| `1001A9100000009NMGNC` | `PluginBeanConfigFilePath` | `nc/ui/so/qd/esign/config/saleorder_esign_probe.xml` | `node_ui_params.json:L51-L55` |
| `1002Z810000000Z0C4QC` | `PluginBeanConfigFilePath_Industry0_tempsave` | `nc/ui/so/m30/billui/model/saleorder_tempsave_config.xml` | `node_ui_params.json:L58-L62` |

（同一结果亦见 `all_node_ui_params.json` 的 `40060301` 三行；`node-bindings.csv:L39` 的 `beanConfig`/`pluginConfigs` 与之一致。`existing_esign_console_params.json` 为 0 行，说明 `13050QDES` 电子签名管理节点当前无参数，与本批按钮无直接绑定。）

### 6.3 状态标注

| 项 | 状态 | 说明/观察位置 |
|---|---|---|
| 8 枚按钮注册存在、启用、类型、权限标志 | **静态核实** | `buttons_1.json` 对应行 |
| 节点参数（3 条） | **静态核实** | `node_ui_params.json:L44-L62` |
| 按钮级参数（`sm_paramregister.parentid = pk_btn`） | **未核实** | 现有两次参数查询都只 JOIN `p.parentid=f.cfunid`；需按 8 个 `pk_btn` 查 `sm_paramregister WHERE NVL(dr,0)=0 AND parentid IN (...)` |
| 角色/用户按钮授权（谁能看到/能点） | **未核实** | 需查功能权限/角色分配相关表（本证据集未含） |
| 客户端实际加载的 action、按钮显隐、原生调用链 | **未核实** | 需真实 NC 会话或类级核实；本会话无 NC 连接、无截图、无会话日志 |
| 真实会话核实（登录 NC 点击验证） | **无一条** | 本批取证全部为库/文件静态证据 |

---

## 7. ⑥ 未核实项清单与观察位置

| # | 未核实项 | 影响 | 观察位置（精确到文件/键/建议查询） |
|---:|---|---|---|
| U1 | 8 个按钮的 `pagecode` / `pk_page` | 无法填 S0 的页签列；无法判断其是否同时以页签按钮（`BTNOWNERTYPE=1`）存在 | 对 QDNC 只读跑 `page_button_registrations.json` 同款 SQL（`sql` 行 `L6`），`funcode` 换为 `('40060301','40060303')` |
| U2 | `40060301`/`40060303` 是否存在页签级按钮注册 | 若有，显示/权限入口可能不止本表 68/18 条 | 同上；并看 `sm_butnregister.parent_id = sm_pageregister.pk_page` 且 `btnownertype=1` |
| U3 | `fs_delete` 的客户端动作绑定（只删附件？） | 决定 209 行是“删订单”还是“附件删除”，影响范围归属 | `nc/ui/so/m30/billui/model/saleorder_config.xml` 中 `fs_delete` 的 action 定义；真实会话中打开附件管理点删除；本机 JAR 提取件 `s0-evidence/jar-extract/.../saleorder_config.xml` 中未见 `fs_`（旁证） |
| U4 | `Approve` 之下是否有子页/子按钮注册；“审批弹窗-确定”的最终动作 | 212 行只能算“定位到入口”，不能算最终确认 | `sm_butnregister WHERE NVL(dr,0)=0 AND parent_id='1001Z810000000004TO2'`；`SaleOrderApproveAction` 类级/会话级核实 |
| U5 | 8 枚按钮的按钮级参数 | 权限/动作参数白名单无法定稿 | `sm_paramregister WHERE NVL(dr,0)=0 AND parentid IN ('1001Z810000000004TO3','…TOA','…TOG','…TO2','…TOO','…TP5','…TOE','…TP1')` |
| U6 | 角色-按钮授权、组织范围（990 之外的可见性） | 不能验证“有权限/无权限”分支 | 功能权限/角色分配表（本证据集未含）；`SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md:9` 给出的首轮组织 `0001A9100000000005MH`、`0001A9100000003UDS6R` 仍需现场确认 |
| U7 | 真实 NC 会话中按钮实际显隐、点击后的原生调用链 | 决定“静态核实”能否升级为“真实会话核实” | 在 990 组织真实登录 NC，逐按钮点击并留证；本会话未连 NC |
| U8 | `operation-bindings.csv` 的 `pageName` 语义 | 防止把操作名误当 `pagecode` | 该文件第 1 行表头与 L277–L285；已确认该列值 = `buttonName` |
| U9 | 2026-11-11 页签查询为何未覆盖 4006 节点 | 影响“本节点无页签按钮”的判断方向 | `page_button_registrations.json` 的 `sql`（`L6`）白名单仅 6 个 `10140*`；属查询范围设定，不是“查过无结果” |
| U10 | `btnownertype` 的官方枚举含义 | 本文“0=功能直属/1=页签”为形态归纳，不是字典定义 | `platform_columns.json:L2920-L2925`（字段元数据，无注释）；如需权威定义应查 NC 数据字典文档 |

---

## 8. 取证方法与可复现命令（实际执行）

全部为只读操作；项目目录仅被 `Get-ChildItem`/`Get-FileHash`/`[IO.File]::ReadAllText` 读取，未写入。工作区仅新增本文件（`s0-evidence/` 下 `jar-extract`、`javap` 为同批其他成员产出，本会话未改动）。

```powershell
# 统一编码，避免中文乱码
$OutputEncoding=[Console]::OutputEncoding=[Text.Encoding]::UTF8

# 1) 正式清单：表头、行数、209-216 原文（sourceRow 与物理行对齐）
$p='E:\02-Project\QiDu\QD-E-Siganature\config\qd-esign\operation-scope.csv'
$t=[IO.File]::ReadAllText($p,[Text.Encoding]::UTF8); $lines=$t -split "`r?`n"
$lines[0]; $lines[208..215]

# 2) 现场按钮导出：结构 + 按 funcode 过滤（不整文件打印）
$p='E:\02-Project\QiDu\QD-E-Siganature\docs\nc65-electronic-signature\site-evidence\2026-11-07\buttons_1.json'
$j=[IO.File]::ReadAllText($p,[Text.Encoding]::UTF8)|ConvertFrom-Json
$j.PSObject.Properties.Name; $j.sql
$j.rows | Where-Object {$_.FUNCODE -eq '40060301'} | ConvertTo-Json -Compress   # 68 行
$j.rows | Where-Object {$_.FUNCODE -eq '40060303'} | ConvertTo-Json -Compress   # 18 行

# 3) 用 PK_BTN 文本回查物理行号（仅匹配短行，避开 raw 的整表字符串）
$lines=[IO.File]::ReadAllLines($p,[Text.Encoding]::UTF8)
for($i=0;$i -lt 4400;$i++){ if($lines[$i].Length -lt 300 -and $lines[$i] -match '1001Z810000000004TO2'){ "$($i+1): $($lines[$i].Trim())" } }

# 4) 页签按钮导出：确认覆盖范围（结论：无 40060301/40060303）
$p2='E:\02-Project\QiDu\QD-E-Siganature\docs\nc65-electronic-signature\site-evidence\2026-11-11\page_button_registrations.json'
$j2=[IO.File]::ReadAllText($p2,[Text.Encoding]::UTF8)|ConvertFrom-Json
($j2.rows.FUNCODE | Sort-Object -Unique) -join ', '
($j2.rows | Where-Object {$_.FUNCODE -in @('40060301','40060303')}).Count   # 0

# 5) 功能节点、节点参数、派生绑定
$j3=[IO.File]::ReadAllText('E:\02-Project\QiDu\QD-E-Siganature\docs\nc65-electronic-signature\site-evidence\2026-11-07\function_candidates.json',[Text.Encoding]::UTF8)|ConvertFrom-Json
$j3.rows | Where-Object {$_.FUNCODE -eq '40060301'} | ConvertTo-Json -Compress
$j4=[IO.File]::ReadAllText('E:\02-Project\QiDu\QD-E-Siganature\docs\nc65-electronic-signature\site-evidence\2026-11-07\node_ui_params.json',[Text.Encoding]::UTF8)|ConvertFrom-Json
$j4.rows | Where-Object {$_.FUNCODE -eq '40060301'} | ConvertTo-Json -Compress   # 3 条参数

# 6) 文件指纹（S0 冻结）
Get-FileHash -LiteralPath $p -Algorithm SHA256; Get-Item -LiteralPath $p | Select Length
```

取证过程中**未执行**：任何 `INSERT/UPDATE/DELETE/MERGE/DROP`、任何 NC 客户端/服务端启动停止、任何部署或发布、任何项目目录写入。

---

## 附录 A：`40060301` 同 parent 按钮全量 68 条（`parent_id=1001Z810000000000UV6`）

格式：`BTNCODE | BTNNAME | PK_BTN | buttons_1.json 中 PK_BTN 行号`（`btnownertype` 全为 `0`，`isenable` 全为 `Y`；`isbuttonpower` 仅 `QueryCustCredit`/`RefECC1`/`SaveTemporary`/`ShowAll`/5 枚 `fs_*` 为 `Y`）。

```text
21|参照协同采购订单新增|1001Z810000000004TON|L11
AddLine|增行|1001Z810000000004Y8P|L23
Approve|审批|1001Z810000000004TO2|L35
ApproveInfo|查看审批流|1001Z810000000004TO5|L47
AskPrice|询价|1001Z810000000004TOZ|L59
BHArrange|补货安排|1001Z810000000004TNX|L71
BillClose|整单关闭|1001Z810000000004TP1|L83
BillLinkQuery|联查单据|1001Z810000000004TOR|L95
Cancel|取消|1001Z810000000004TOQ|L107
CashProcess|现销处理|1001Z810000000004TO9|L119
Commit|提交|1001Z810000000004TOA|L131
Copy|复制|1001Z810000000004TOM|L143
CopyLine|复制行|1001Z810000000004Y8S|L155
CreditQuery|信用查询|1001Z810000000004TOL|L167
CrossProfitRpt|毛利预估|1001Z810000000004TP0|L179
DelLine|删行|1001Z810000000004Y8R|L191
Delete|删除|1001Z810000000004TO3|L203
Edit|修改|1001Z810000000004TOS|L215
Exchange|换货|1001Z810000000004TOP|L227
File|附件管理|1001Z810000000004TOU|L239
Freeze|冻结|1001Z810000000004TP5|L251
InsLine|插入行|1001Z810000000004Y8Q|L263
LargessApportion|赠品价格分摊|1001Z810000000004TNZ|L275
Offset|费用冲抵|1001Z810000000004TNU|L287
OnhandInfo|存量显示/隐藏|1001Z810000000004TO0|L299
OrderPay|订单收款|1001Z810000000004TP2|L311
OrderQueryExec|订单执行情况|1001Z810000000004TOB|L323
PasteLine|粘贴行|1001Z810000000004Y8T|L335
PasteLineToTail|粘贴行到表尾|1001Z810000000004Y8U|L347
PayVerify|收款核销|1001Z810000000004TOI|L359
Preview|预览|1001Z810000000004TOT|L371
PriceForm|价格组成|1001Z810000000004TOX|L383
Print|打印|1001Z810000000004TOC|L395
Query|查询|1001Z810000000004TOK|L407
QueryCustCredit|查看客户信用|1002Z81000000000HXU2|L419
QueryCustInfo|客户信息|1001Z810000000004TOF|L431
QueryReserve|预留查询|1001Z8100000000015O2|L443
REARRANGEROWNO|重排行号|1001Z810000000004Y8V|L455
ReceivePlan|收款计划|1002Z81000000000DCTY|L467
Ref30|参照销售订单退货|1001Z810000000004TOD|L479
Ref38|预订单|1001Z810000000004TNY|L491
Ref4310|销售报价单|1001Z810000000004TO7|L503
Ref4C|参照销售出库单退货|1001Z810000000004TOW|L515
Ref4H|库存借出单|1001Z810000000004TOY|L527
RefECC1|订单中心预订单|1001Z81000000000E0MR|L539
RefMANUAL|自制|1001Z810000000004TP4|L551
RefZ3|销售合同|1001Z810000000004TOH|L563
Refresh|刷新|1001Z810000000004TOV|L575
Reserve|预留|1001Z810000000004TO8|L587
Save|保存|1001Z810000000004TP6|L599
SaveTemporary|暂存|1002Z81000000000E1EK|L611
SendArrange|发货安排|1001Z810000000004TP3|L623
ShowAll|查看全部|1002Z81000000000HXU3|L635
To21|生成协同采购订单|1001Z810000000004TNW|L647
TransInfo|内部交易信息|1001Z810000000004TO4|L659
UnApprove|取消审批|1001Z810000000004TOO|L671
UnCommit|收回|1001Z810000000004TOG|L683
UnFreeze|解冻|1001Z810000000004TOE|L695
UnOffset|取消费用冲抵|1001Z810000000004TNV|L707
UndoLargessApportion|取消赠品价格分摊|1001Z810000000004TO1|L719
ZYArrange|直运安排|1001Z810000000004TO6|L731
fs_browse|查看|1002Z81000000000QCB0|L743
fs_delete|删除|1002Z81000000000QCAZ|L755
fs_download|下载|1002Z81000000000QCAY|L767
fs_rename|重命名|1002Z81000000000QCB1|L779
fs_upload|上传|1002Z81000000000QCAX|L791
output|输出...|1002Z81000000000DCTZ|L803
splitprint|分单打印|1001Z810000000004TOJ|L815
```

## 附录 B：`40060303` 销售订单关闭管理 18 条按钮注册（`parent_id=1001Z810000000001CMN`）

格式：`BTNCODE | BTNNAME | PK_BTN | buttons_1.json 行号`（`btnownertype` 全为 `0`，`isenable` 全为 `Y`，`isbuttonpower` 全为 `N`）。

```text
BillClose|整单关闭|1001Z810000000003DAZ|L827
BillLinkQuery|联查单据|1001Z810000000003DAV|L839
BillOpen|整单打开|1001Z810000000003DB0|L851
InvoiceClose|开票关闭|1001Z810000000003DB1|L863
InvoiceOpen|开票打开|1001Z810000000003DB2|L875
LineClose|行关闭|1001Z810000000003DB5|L887
LineOpen|行打开|1001Z810000000003DB6|L899
OutClose|出库关闭|1001Z810000000003DB3|L911
OutOpen|出库打开|1001Z810000000003DB4|L923
Query|查询|1001Z810000000003DAT|L935
Refresh|刷新|1001Z810000000003DAU|L947
SendClose|发货关闭|1001Z810000000003DB7|L959
SendOpen|发货打开|1001Z810000000003DB8|L971
SettleClose|结算关闭|1001Z810000000003DB9|L983
SettleOpen|结算打开|1001Z810000000003DBA|L995
preView|预览|1001Z810000000003DAY|L1007
print|打印|1001Z810000000003DAX|L1019
printGroup|打印|1001Z810000000003DAW|L1031
```

说明：`40060303` 的 `preView`（小写 v）与 `print`/`printGroup`（小写 p）与 `40060301` 的 `Preview`/`Print`（大写）编码不同，不能直接合并；上表行号由 `PK_BTN` 值回查得到。

---

## 附：与本文结论相关的文档对照（仅作对照，不作为事实权威）

| 文档 | 位置 | 内容 | 与本文关系 |
|---|---|---|---|
| `docs/nc65-electronic-signature/SALE_ORDER_OTHER_BUTTONS_DEVELOPMENT_PLAN.md` | `:3`、`:8`、`:14`–`:25` | 明确本批范围为 209–216 八项；217–228 独立；表格已列 8 个「注册编码/主键」 | 与本文 §2/§4 一致 |
| 同上 | `:21` | 212 写作 `Approve`（上级按钮） | 语义描述，非注册字段；见 §2.3 |
| 同上 | `:18` | `fs_delete` 同组还有上传下载重命名，优先核对是否附件删除 | 与本文 §3.3 判定方向一致 |
| `docs/nc65-electronic-signature/UNMATCHED_BUTTONS_20261110.md` | `:53`、`:61` | 列出 6 个节点同时注册 `Delete` 与 `fs_delete`，本节点 209 行 | 与本文 §3 一致 |
| 同上 | `:85`、`:95` | 212 归入“已找到的上级入口 `Approve`，最终动作待确认” | 与本文 §2.3 “入口而非最终动作”一致 |
| `docs/nc65-electronic-signature/PAGE_REGISTRATION_20261110.md` | `:9`、`:48`、`:52` | 旧快照只关联功能直属按钮；`BTNOWNERTYPE=1` 为页签按钮形态；旧 `NO_UNIQUE_REGISTERED_BUTTON` 只反映旧查询方式 | 支持本文 §1 的形态对应与 §2.2 的未核实判断 |
| `docs/nc65-electronic-signature/OPERATION_COVERAGE.md` | `:5`、`:7`、`:35` | 243 行正式清单、原目录是需求追溯资料、编码空值保留 | 支持本文 §4.3 “正式清单不含编码” |
| `config/qd-esign/node-scope.csv` | `:33`、`:34` | 208–216 共 9 项；217–228 共 12 项 | 支持“本批 8 项 + 217–228 独立批次” |
