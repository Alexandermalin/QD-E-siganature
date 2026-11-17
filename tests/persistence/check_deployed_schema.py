"""Compare captured live Oracle columns with the approved DDL and monthly CTAS contract."""
import json
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "docs/nc65-electronic-signature/database-execution/20261107"
actual = defaultdict(list)
capture = json.loads((REPORT / "live_columns.json").read_text(encoding="utf-8"))
assert not capture["result"].get("isError"), "Live metadata query failed"
for row in capture["rows"]:
    actual[row["TABLE_NAME"].lower()].append(row)
assert len(actual) == 24 and sum(map(len, actual.values())) == 332
source = (ROOT / "sql/nc65-electronic-signature/001_schema.sql").read_text(encoding="utf-8")
source = re.sub(r"--[^\n]*", "", source)
base_tables = set()
for match in re.finditer(r"CREATE TABLE (\w+)\s*\((.*?)\n\);", source, re.S | re.I):
    table = match.group(1).lower()
    base_tables.add(table)
    fields = list(re.finditer(r"^\s*(\w+)\s+(VARCHAR2|CHAR|NUMBER|CLOB)\b(?:\((\d+)(?:\s+(CHAR|BYTE))?\))?([^\n]*)", match.group(2), re.M | re.I))
    assert [f.group(1).lower() for f in fields] == [r["COLUMN_NAME"].lower() for r in actual[table]], table
    for field, row in zip(fields, actual[table]):
        name, datatype, length, semantics, tail = field.groups()
        assert row["DATA_TYPE"] == datatype.upper(), (table, name, "type")
        if datatype.upper() in ("CHAR", "VARCHAR2"):
            if (semantics or "BYTE").upper() == "CHAR":
                assert row["CHAR_USED"] == "C" and int(row["CHAR_LENGTH"]) == int(length), (table, name, "CHAR length")
            else:
                assert row["CHAR_USED"] == "B" and int(row["DATA_LENGTH"]) == int(length), (table, name, "BYTE length")
        if "NOT NULL" in tail.upper():
            assert row["NULLABLE"] == "N", (table, name, "nullability")
assert len(base_tables) == 17
for table, fields in actual.items():
    if table in base_tables:
        continue
    assert table.endswith("_h202608"), table
    base = table[:-8]
    assert base in base_tables, table
    assert len(fields) == len(actual[base]), table
    for row, online in zip(fields, actual[base]):
        for key in ("COLUMN_ID", "COLUMN_NAME", "DATA_TYPE", "DATA_LENGTH", "CHAR_LENGTH", "CHAR_USED"):
            assert row[key] == online[key], (table, row["COLUMN_NAME"], key)
manifest = json.loads((ROOT / "sql/nc65-electronic-signature/approval-20261107/manifest.json").read_text(encoding="utf-8"))
assert manifest["executed"] is True and set(manifest["tables"]) == set(actual)
print("PASS live Oracle DDL contract: 24 tables / 332 columns, order/types/byte-char semantics/nullability and 7 monthly shapes; no transaction assertions")
