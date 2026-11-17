"""Offline DDL/Java column contract checks; not a substitute for Oracle execution."""
# 离线核对自建表 DDL 与 Java 固定元数据，不连接 Oracle；原文档字符串保留，测试含义在此说明。
import csv
import importlib.util
import re
import sys
from pathlib import Path

# 导入 SQL 生成器时不留下字节码缓存，使契约检查只读取源码。
sys.dont_write_bytecode = True

# 从本文件位置定位工程，确保测试不依赖启动时工作目录。
ROOT = Path(__file__).resolve().parents[2]
schema = (ROOT / "sql/nc65-electronic-signature/001_schema.sql").read_text(encoding="utf-8")
schema += "\n" + (ROOT / "sql/nc65-electronic-signature/organization-policy/02_create_org_control.sql").read_text(encoding="utf-8")
group_policy = ROOT / "sql/nc65-electronic-signature/group-base-policy"
group_ddl = (group_policy / "02_create_group_policy.sql").read_text(encoding="utf-8")
schema += "\n" + group_ddl
metadata = (ROOT / "uapbd/src/private/nc/bs/uapbd/qd/esign/persistence/QdTableMetadata.java").read_text(encoding="utf-8")
# 直接以正则扫描原文，提取表和列清单；新增注释不能复制完整建表或映射声明，避免被误识别为代码。
tables = {}
for match in re.finditer(r"CREATE TABLE (\w+)\s*\((.*?)\n\);", schema, re.S | re.I):
    tables[match.group(1).lower()] = re.findall(r"\b([A-Za-z_]\w*)\s+(?:VARCHAR2|CHAR|NUMBER|CLOB)\b", match.group(2), re.I)
for table, column in re.findall(r"ALTER TABLE (\w+) ADD \((\w+)\s+VARCHAR2\(64\)\);", group_ddl, re.I):
    tables[table.lower()].append(column.lower())
# 1.3 的幂等 Oracle 升级使用动态 DDL。直接解析其建表字符串和 ensure_column 调用，
# 验证增量脚本与生产 Java 元数据，而非另造一份可能漂移的手写字段清单。
owner_ddl = (ROOT / "sql/nc65-electronic-signature/owner-base-policy/02_schema.sql").read_text(encoding="utf-8")
for match in re.finditer(r"EXECUTE IMMEDIATE\s+'CREATE TABLE\s+(\w+)(.*?);\s*END IF;", owner_ddl, re.S | re.I):
    literals = re.findall(r"'((?:''|[^'])*)'", "'CREATE TABLE " + match.group(1) + match.group(2))
    ddl = "".join(value.replace("''", "'") for value in literals)
    columns = re.findall(r"\b([A-Za-z_]\w*)\s+(?:VARCHAR2|CHAR|NUMBER|CLOB)\b", ddl, re.I)
    tables[match.group(1).lower()] = [column.lower() for column in columns]
calls = re.findall(r"ensure_column\(t\.table_name,'(\w+)'", owner_ddl, re.I)
assert calls == ["POLICY_MODE", "OWNER_OBJECT_COUNT", "POLICY_OWNER_ORG", "POLICY_OWNER_TYPE", "POLICY_SCOPE_ID", "TARGET_ORG",
                 "POLICY_OWNER_ROOT", "DESIRED_OWNER_ORG", "OWNER_POLICY_VERSION", "OWNER_CONTROL_VERSION",
                 "OWNER_SIGN_REQUIRED", "OWNER_REASON_REQUIRED", "OWNER_AUDIT_ENABLED",
                 "UNIT_STATE", "UNIT_ERROR_CODE", "UNIT_FINISHED_AT"], calls
# A quick-batch request persists its complete selected-object count, including counts above 9999.
batch_source = (ROOT / "uapbd/src/public/nc/vo/uapbd/qd/esign/PartnerBatchDraft.java").read_text(encoding="utf-8")
batch_maximum = int(re.search(r"MAX_SINGLE_TRANSACTION_OBJECTS\s*=\s*(\d+)", batch_source).group(1))
precision = int(re.search(r"ensure_column\(t\.table_name,'OWNER_OBJECT_COUNT','NUMBER',(\d+),'NUMBER\(\d+\)'\)", owner_ddl).group(1))
assert batch_maximum > 9999 and batch_maximum <= 10**precision - 1, (batch_maximum, precision)
assert "widen_owner_object_count(t.table_name)" in owner_ddl
assert "NVL(v_precision,0) NOT IN (4,6)" in owner_ddl and "NVL(v_scale,-1)<>0" in owner_ddl
assert "MODIFY (OWNER_OBJECT_COUNT NUMBER(6))" in owner_ddl and "^QD_ES_(REQUEST|OBJECT|SIGNLOG|AUDIT)_H[0-9]{6}$" in owner_ddl
tables["qd_es_request"].extend(column.lower() for column in calls[:2])
for table in ("qd_es_object", "qd_es_signlog", "qd_es_audit"):
    tables[table].extend(column.lower() for column in calls[2:6])
tables["qd_es_object"].extend(column.lower() for column in calls[6:])
receipt_ddl = (ROOT / "sql/nc65-electronic-signature/org-batch-receipt/02_schema.sql").read_text(encoding="utf-8")
receipt_match = re.search(r"EXECUTE IMMEDIATE 'CREATE TABLE QD_ES_BATCH_PAGE \('(.*?);\s*END IF;", receipt_ddl, re.S)
assert receipt_match
receipt_literals = ["CREATE TABLE QD_ES_BATCH_PAGE ("] + re.findall(r"'((?:''|[^'])*)'", receipt_match.group(1))
receipt_statement = "".join(value.replace("''", "'") for value in receipt_literals)
tables["qd_es_batch_page"] = [column.lower() for column in re.findall(
    r"\b([A-Za-z_]\w*)\s+(?:VARCHAR2|CHAR|NUMBER|CLOB)\b", receipt_statement, re.I)]
assert "^QD_ES_(REQUEST|OBJECT|SIGNLOG|AUDIT)_H[0-9]{6}$" in owner_ddl
assert "qdes_bd_org_policy_scope_fk" in owner_ddl and "qdes_bd_org_policy_flags_ck" in owner_ddl
rollback_guards = {
    "QDES_OWNER_REQ_RB_GUARD": "QD_ES_REQUEST", "QDES_OWNER_OBJ_RB_GUARD": "QD_ES_OBJECT",
    "QDES_OWNER_SIGN_RB_GUARD": "QD_ES_SIGNLOG", "QDES_OWNER_AUDIT_RB_GUARD": "QD_ES_AUDIT",
}
for guard, table in rollback_guards.items():
    assert len(guard) <= 30 and f"ensure_rollback_guard('{guard}','{table}'" in owner_ddl
    for script in ("03_migrate.sql", "04_prepare_rollback.sql"):
        text = (ROOT / "sql/nc65-electronic-signature/owner-base-policy" / script).read_text(encoding="utf-8")
        assert f"t.trigger_name='{guard}' AND t.table_name='{table}'" in text
assert "version_no=''1.3.0-RB''" in owner_ddl and "BEFORE DELETE ON " in owner_ddl
# 断言固定表数量，再按列顺序比较 Java 映射；版本表只用于部署，因此不要求 Java 映射。
assert len(tables) == 23, (len(tables), tables.keys())
contracts = dict(re.findall(r'define\("(\w+)", "([^"]+)"\)', metadata))
for table, fields in contracts.items():
    assert fields.split() == tables[table], (table, fields.split(), tables[table])
assert set(tables) - set(contracts) == {"qd_es_schema_version"}
# 核对 Oracle 11g 标识符长度和大小写不敏感的唯一性，避免部署时出现索引或约束命名冲突。
identifiers = re.findall(r"(?:CONSTRAINT|CREATE INDEX)\s+(\w+)", schema + owner_ddl + (ROOT / "sql/nc65-electronic-signature/002_indexes.sql").read_text(encoding="utf-8"), re.I)
assert all(len(x) <= 30 for x in identifiers), [x for x in identifiers if len(x) > 30]
assert len(identifiers) == len(set(x.lower() for x in identifiers)), "Duplicate Oracle identifiers"
with (ROOT / "config/qd-esign/operation-scope.csv").open(encoding="utf-8-sig", newline="") as stream:
    group_scopes = [row["scopeId"] for row in csv.DictReader(stream) if row["category"] == "基础数据"]
assert len(group_scopes) == 77
for script in ("01_precheck.sql", "03_migrate_group_policy.sql"):
    scope_ids = re.findall(r"'(QD-ES-[0-9A-F]{16})'", (group_policy / script).read_text(encoding="utf-8"))
    assert scope_ids == group_scopes, (script, "base-data scopes differ from the approved catalog")
# 通过文件路径载入真实生成器，后续检查直接作用于生产生成逻辑，不另造测试实现。
spec = importlib.util.spec_from_file_location("generate_history", ROOT / "sql/nc65-electronic-signature/generate_history.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
# 跨年两个月应产生十四张历史表和两个目录条目，且不得包含删除或截断历史数据的操作。
generated = module.generate("202612", "202701")
assert generated.count("CREATE TABLE ") == 14
assert generated.count("INSERT INTO qd_es_archive") == 2
assert "qd_es_snapshot_h202701" in generated
assert "DROP " not in generated and "TRUNCATE " not in generated
with_receipts = module.generate("202612", "202701", True)
assert with_receipts.count("CREATE TABLE ") == 16
assert "qd_es_batch_page_h202612" in with_receipts and "qdbp202612_pk" in with_receipts
assert "qdbp202701_uq" in with_receipts and "qdbp202701_scope_ix" in with_receipts
# 非法月份、倒置范围及非六位格式必须失败；正常返回反而说明生成器校验失效。
for invalid in [("202613", "202701"), ("202701", "202612"), ("20261", "202612")]:
    try:
        module.generate(*invalid)
        raise AssertionError(f"Invalid month accepted: {invalid}")
    except ValueError:
        pass
print(f"PASS schema contracts: {len(tables)} tables, {len(contracts)} Java mappings; history range/identifier checks")
