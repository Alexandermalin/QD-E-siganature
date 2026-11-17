"""Check code/schema/binding contracts against saved QDNC facts, without connecting."""
import csv
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "docs/nc65-electronic-signature/site-evidence/2026-11-07"
SITE = ROOT / "config/qd-esign/site-20261107"
PAGE_EVIDENCE = ROOT / "docs/nc65-electronic-signature/site-evidence/2026-11-11/page_button_registrations.json"


def rows(name):
    doc = json.loads((EVIDENCE / (name + ".json")).read_text(encoding="utf-8-sig"))
    assert doc["schema"] == "QDNC" and doc["read_only"] is True
    assert doc["sql"].lstrip().upper().startswith("SELECT ")
    return doc["rows"]


def read_csv(name):
    with (SITE / name).open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


nodes, operations = read_csv("node-bindings.csv"), read_csv("operation-bindings.csv")
assert len(nodes) == 41 and len({n["nodeId"] for n in nodes}) == 35
assert len(operations) == 312 and len({o["scopeId"] for o in operations}) == 243
assert all(o["validated"] == "N" and o["enabled"] == "N" for o in operations)
assert all(n["validated"] == "N" for n in nodes)
registered = {(b["FUNCODE"], b["BTNCODE"], b["PK_BTN"]) for b in rows("buttons_0") + rows("buttons_1")}
for o in operations:
    if o["ncActionCode"]:
        assert (o["ncFunctionCode"], o["ncActionCode"], o["registeredButtonPk"]) in registered
    if o["mappingState"] != "REGISTERED_BUTTON_RPC_PENDING":
        assert not o["ncActionCode"], "A popup entry must not impersonate a terminal save/confirm"
direct = [(o["ncFunctionCode"], o["ncActionCode"]) for o in operations if o["ncActionCode"]]
assert len(direct) == len(set(direct)) == 204

# 页签按钮属于 SM_PAGEREGISTER，而不是直接属于 SM_FUNCREGISTER。
# 旧快照保持原样；仅把已留存的页签注册事实叠加到客户端显示目录。
page_doc = json.loads(PAGE_EVIDENCE.read_text(encoding="utf-8-sig"))
assert page_doc["schema"] == "QDNC" and page_doc["read_only"] is True
assert page_doc["sql"].lstrip().upper().startswith("SELECT ")
page_rows = page_doc["rows"]
assert len(page_rows) == 48
page_index = {}
for page in page_rows:
    assert page["CFUNID"] == page["PAGE_PARENT_ID"]
    assert page["PK_PAGE"] == page["BUTTON_PARENT_ID"]
    assert page["ISENABLE"] == "Y" and str(page["BTNOWNERTYPE"]) == "1"
    assert page["BTNNAME"] in ("保存", "删除")
    key = (page["FUNCODE"], page["PAGECODE"], page["BTNNAME"])
    assert key not in page_index, "页签下的保存或删除注册必须唯一"
    page_index[key] = page
assert len({page["PK_BTN"] for page in page_rows}) == 48

page_codes = {
    ("客户", "财务信息"): "finance_info", ("客户", "销售信息"): "sale_info",
    ("客户", "信用控制信息"): "credit_info", ("供应商", "财务信息"): "finance_info",
    ("供应商", "采购信息"): "stock_info", ("物料", "财务信息"): "fi_info",
    ("物料", "采购信息"): "pu_info", ("物料", "销售信息"): "sale_info",
    ("物料", "库存信息"): "stock_info", ("物料", "计划信息"): "plan_info",
    # 用户确认原表“生成信息”对应注册中的生产信息页签。
    ("物料", "生成信息"): "prod_info", ("物料", "成本信息"): "cost_info",
}
expected_catalog = []
used_pages = set()
for operation in operations:
    button, entry = operation["ncActionCode"], operation["entryButtonCode"]
    if operation["adapterFamily"] == "BD_SUBPAGE" and operation["mappingState"] == "NO_UNIQUE_REGISTERED_BUTTON":
        page_name, action_name = operation["buttonName"].rsplit("-", 1)
        page_code = page_codes[(operation["nodeName"], page_name)]
        key = (operation["ncFunctionCode"], page_code, action_name)
        assert key in page_index and key not in used_pages
        button, entry = page_index[key]["BTNCODE"], ""
        used_pages.add(key)
    expected_catalog.append((operation["scopeId"], operation["ncFunctionCode"], button,
                             "" if button else entry))
assert used_pages == set(page_index), "所有新增显示编码均须有可追溯的页签注册"
terminal_codes = [(node, button) for _, node, button, _ in expected_catalog if button]
assert len(terminal_codes) == len(set(terminal_codes)) == 252

catalog = ROOT / "uapbd/src/client/nc/ui/uapbd/qd/esign/operation-codes.xml"
assert [
    (entry.get("scope"), entry.get("node"), entry.get("button", ""), entry.get("entry", ""))
    for entry in ET.parse(catalog).getroot().findall("binding")
] == expected_catalog, "Client display catalog must reflect both the original snapshot and verified page registrations"
for name, code, trade in (("投产单", "40080604400", "46-Cxx-2"), ("委外加工入库", "40080602400", "45-Cxx-08")):
    n = next(n for n in nodes if n["nodeName"] == name)
    assert n["ncFunctionCode"] == code and n["transactionType"] == trade
    assert sum(o["nodeName"] == name for o in operations) == 4
    assert any(b["NODECODE"] == code and b["PK_BILLTYPECODE"] == trade and b["PK_BILLTYPEID"] == n["transactionTypePk"]
               for b in rows("native_alias_types"))
assert next(n for n in nodes if n["nodeName"] == "委托加工入库")["ncFunctionCode"] == "40080606"

columns = defaultdict(set)
for c in rows("audit_core_columns") + rows("audit_inventory_columns"):
    columns[c["TABLE_NAME"].lower()].add(c["COLUMN_NAME"].lower())
assert len(columns) == 10
material = (ROOT / "uapbd/src/private/nc/bs/uapbd/qd/esign/audit/MaterialAuditListener.java").read_text(encoding="utf-8")
fields = re.search(r'new String\[\]\{([^}]+)\}', material).group(1)
assert set(re.findall(r'"([a-z_]+)"', fields)) <= columns["bd_material"]
for table in ("so_saleorder", "po_order", "ic_purchasein_h", "ic_saleout_h"):
    assert {"pk_group", "pk_org"} <= columns[table]
assert "csaleorderid" in columns["so_saleorder"]
assert {"cgeneralhid", "cgeneralbid"} <= columns["ic_purchasein_b"]
assert {"cgeneralhid", "cgeneralbid"} <= columns["ic_saleout_b"]

directory = ROOT / "sql/nc65-electronic-signature/approval-20261107"
manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
install = (directory / "install.sql").read_bytes()
smoke = ROOT / "sql/nc65-electronic-signature/010_test_database_smoke.sql"
assert hashlib.sha256(install).hexdigest() == manifest["install_sha256"]
assert hashlib.sha256(smoke.read_bytes()).hexdigest() == manifest["validation_sha256"]
assert len(manifest["tables"]) == 24 and all(t.startswith("qd_es_") for t in manifest["tables"])
legacy = {r["TABLE_NAME"].lower() for r in rows("existing_esign_tables")}
assert len(legacy) == 10 and legacy.isdisjoint(manifest["tables"])
assert re.search(r"RAISE_APPLICATION_ERROR\(-20001", install.decode("utf-8"))
assert "ROLLBACK TO qdes_test_start" in smoke.read_text(encoding="utf-8")
summary = json.loads((SITE / "summary.json").read_text(encoding="utf-8"))
for name, digest in summary["file_sha256"].items():
    assert hashlib.sha256((SITE / name).read_bytes()).hexdigest() == digest
print("PASS saved site contracts: 35/243 scope, 41/312 physical bindings, 48 verified page registrations, 10 entity schemas, 24-table approval hashes; no DB execution")
