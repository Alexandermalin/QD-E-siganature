"""Check the offline organization-batch receipt schema contract; never connect to Oracle."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
folder = ROOT / "sql/nc65-electronic-signature/org-batch-receipt"
codec = (ROOT / "uapbd/src/private/nc/bs/uapbd/qd/esign/persistence/NativeOrgBatchReceiptRecords.java").read_text(encoding="utf-8")
target_codec = (ROOT / "uapbd/src/private/nc/bs/uapbd/qd/esign/persistence/NativeOrgBatchTargetRecords.java").read_text(encoding="utf-8")
prepared_codec = (ROOT / "uapbd/src/private/nc/bs/uapbd/qd/esign/persistence/NativeOrgBatchPreparedRecords.java").read_text(encoding="utf-8")
ddl = (folder / "02_schema.sql").read_text(encoding="utf-8")
marker = (folder / "03_mark_version.sql").read_text(encoding="utf-8")
rollback = (folder / "04_prepare_rollback.sql").read_text(encoding="utf-8")
selection_marker = (folder / "05_mark_selection_version.sql").read_text(encoding="utf-8")
selection_rollback = (folder / "06_prepare_selection_rollback.sql").read_text(encoding="utf-8")
precheck = (folder / "01_precheck.sql").read_text(encoding="utf-8")
store = (ROOT / "uapbd/src/private/nc/bs/uapbd/qd/esign/persistence/QdEsignStore.java").read_text(encoding="utf-8")

block = re.search(r"EXECUTE IMMEDIATE 'CREATE TABLE QD_ES_BATCH_PAGE \('(.*?);\s*END IF;", ddl, re.S)
assert block, "Missing fixed online table definition"
literals = ["CREATE TABLE QD_ES_BATCH_PAGE ("] + re.findall(r"'((?:''|[^'])*)'", block.group(1))
statement = "".join(value.replace("''", "'") for value in literals)
ddl_columns = re.findall(r"\b([A-Z_]+)\s+(?:VARCHAR2|CHAR|NUMBER)\b", statement, re.I)
columns_block = re.search(r"COLUMNS=Collections\.unmodifiableList\(Arrays\.asList\((.*?)\)\);", codec, re.S)
assert columns_block, "Missing fixed Java receipt column list"
java_columns = re.findall(r'"([a-z_]+)"', columns_block.group(1))
assert [name.lower() for name in ddl_columns] == java_columns, (ddl_columns, java_columns)
assert len(java_columns) == 15 and java_columns[0] == "record_id"
assert 'TARGETS="@TARGETS",HEADER="@"' in codec
assert '"S"' in target_codec and '"U"' in target_codec and '"T"' in codec and '"H"' in codec and '"P"' in codec
assert 'HEADER="@SELECTED"' in prepared_codec and '"W"' in prepared_codec and '"V"' in prepared_codec
metadata = (ROOT / "uapbd/src/private/nc/bs/uapbd/qd/esign/persistence/QdTableMetadata.java").read_text(encoding="utf-8")
mapping = re.search(r'define\("qd_es_batch_page", "([^"]+)"\)', metadata)
assert mapping and mapping.group(1).split() == java_columns

checks = re.findall(r"check_column\(p_table,'([A-Z_]+)','(VARCHAR2|CHAR|NUMBER)',(\d+),", ddl)
assert [item[0].lower() for item in checks] == java_columns, checks
assert ("PAGE_COUNT", "NUMBER", "10") in checks and ("EVIDENCE_HASH", "CHAR", "64") in checks
assert ("ROW_KIND", "VARCHAR2", "1") in checks and ("ITEM_KEY", "VARCHAR2", "20") in checks
assert ("OBJECT_ID", "VARCHAR2", "32") in checks and len("@SELECTED") <= 20 and len("@TARGETS") <= 32
assert "REQUEST_ID,OBJECT_ID,ROW_KIND,ITEM_KEY" in ddl and "ARCHIVE_MONTH,RECORD_ID" in ddl
assert "QD_ES_BATCH_PAGE_H'||p_month" in ddl and "FROM qd_es_archive ORDER BY archive_month" in ddl
assert "p_month IS NULL" in ddl and "DBMS_ASSERT.SIMPLE_SQL_NAME(v_table)" in ddl
assert "QDES_BPAGE_OBJ_GUARD" in ddl and "QDES_BPAGE_REQ_GUARD" in ddl
assert "OBJECT_ID=:OLD.OBJECT_ID" in ddl and "REQUEST_ID=:OLD.REQUEST_ID" in ddl
assert "REGEXP_REPLACE(UPPER(v_body)" in ddl, "Existing guards must be checked rather than silently replaced"

for script in (ddl, marker, rollback, selection_marker, selection_rollback):
    for name in ("<REPLACE_ISOLATED_SCHEMA>", "<REPLACE_ISOLATED_DB_UNIQUE_NAME>", "<REPLACE_ISOLATED_SERVER_HOST>"):
        assert name in script, name
    assert "NVL(SYS_CONTEXT('USERENV','SESSION_USER'),'<NULL>')" in script
    assert "NVL(SYS_CONTEXT('USERENV','DB_UNIQUE_NAME'),'<NULL>')" in script
    assert "NVL(SYS_CONTEXT('USERENV','SERVER_HOST'),'<NULL>')" in script
    executable = re.sub(r"--[^\n]*", "", script)
    assert not re.search(r"\b(?:DROP|TRUNCATE|COMMIT)\b", executable, re.I)
assert "1.3.0-RB" in ddl and "1.3.1-RB" in ddl
assert "QDES_BPAGE_OBJ_GUARD" in marker and "QDES_BPAGE_REQ_GUARD" in marker
assert "QD_ES_BATCH_PAGE_H[0-9]{6}" in marker and "QD_ES_OBJECT_H[0-9]{6}" in marker
assert "business_state IN ('PENDING','READY','UNKNOWN')" in rollback and "orgbulk:%" in rollback
assert "1.3.1-RB" in rollback and "ROLLBACK TO qd_batch_receipt_rollback" in rollback
assert "1.3.1-SEL-RB" in rollback and "selection_count" in store
assert "1.3.1-SEL" in store and "1.3.1-SEL-RB" in store
for script in (selection_marker, selection_rollback):
    assert "1.3.1-SEL" in script and "1.3.1-SEL-RB" in script
    assert "business_state IN ('PENDING','READY','UNKNOWN')" in script and "orgbulk:%" in script
    assert "LOCK TABLE qd_es_schema_version" in script and "LOCK TABLE qd_es_request" in script
assert "ROW_KIND IN (''W'',''V'')" in selection_marker and "QD_ES_BATCH_PAGE_H[0-9]{6}" in selection_marker
assert "QDES_BPAGE_OBJ_GUARD" in selection_marker and "QDES_BPAGE_REQ_GUARD" in selection_marker
queries = re.sub(r"--[^\n]*", "", precheck).strip()
assert all(item.strip().upper().startswith("SELECT ") for item in queries.split(";") if item.strip())
print("PASS organization batch receipt DDL/codec contract (offline; no Oracle execution)")
