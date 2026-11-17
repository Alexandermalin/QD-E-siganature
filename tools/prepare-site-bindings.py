"""Reconcile the approved scope with saved QDNC read-only evidence; no DB writes."""
import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "docs/nc65-electronic-signature/site-evidence/2026-11-07"
SCOPE = ROOT / "config/qd-esign"

# 现场启用节点；集团/业务单元变体分别保留，不把全局停用节点当本次接入对象。
NODE_CODES = {
    "客户基本分类": ["10140CCLG", "10140CCLO"], "客户": ["10140CUG", "10140CUO"],
    "供应商基本分类": ["10140SCLG", "10140SCLO"], "供应商": ["10140SUG", "10140SUO"],
    "物料基本分类": ["10140MCLG", "10140MCLO"], "物料": ["10140MAG", "10140MAO"],
    "仓库": ["10140WARH"], "货位": ["10140RACK"], "物资需求申请单": ["40040000"],
    "请购单维护": ["40040200"], "采购订单维护": ["40040400"], "采购发票维护": ["40041000"],
    "供应商价目表维护": ["40050200"], "期初余额": ["40080402"], "采购入库": ["40080602"],
    "投产单": ["40080604400"], "调拨入库": ["40080618"], "委外加工入库": ["40080602400"],
    "产成品入库": ["40080604"], "其他入库": ["40080608"], "委托加工入库": ["40080606"],
    "出库申请单": ["40080801"], "销售出库": ["40080802"], "调拨出库": ["40080820"],
    "材料出库": ["40080804"], "其他出库": ["40080808"], "盘点": ["40081016"], "转库": ["40081002"],
    "货位调整": ["40081014"], "形态转换": ["40081008"], "库存状态调整": ["40081009"],
    "销售订单维护": ["40060301"], "销售订单关闭管理": ["40060303"], "发货单维护": ["40060402"], "销售发票维护": ["40060501"],
}


def evidence(name):
    value = json.loads((EVIDENCE / (name + ".json")).read_text(encoding="utf-8-sig"))
    assert value["read_only"] is True and value["schema"] == "QDNC"
    return value["rows"]


def csv_rows(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def write_csv(path, rows):
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def write_client_catalog(path, bindings):
    root = ET.Element("operation-codes")
    for binding in bindings:
        attributes = {"scope": binding["scopeId"], "node": binding["ncFunctionCode"]}
        if binding["ncActionCode"]:
            attributes["button"] = binding["ncActionCode"]
        elif binding["entryButtonCode"]:
            attributes["entry"] = binding["entryButtonCode"]
        ET.SubElement(root, "binding", attributes)
    ET.indent(root, space="  ")
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)


def button_mapping(row, registered):
    label = row["buttonName"]
    # 菜单组不改变其子动作名；这里只确认注册身份，不声明末次 RPC 已接管。
    direct = label.split("-", 1)[1] if label.startswith(("辅助功能-", "关联功能-")) else label
    terminal = "-" not in direct and "&" not in direct
    entry_label = direct if terminal else label.split("-", 1)[0]
    matches = [b for b in registered if b["BTNNAME"] == entry_label and b["ISENABLE"] == "Y"]
    if len(matches) != 1:
        return {"ncActionCode": "", "registeredButtonPk": "", "entryButtonCode": "",
                "entryButtonName": "", "mappingState": "NO_UNIQUE_REGISTERED_BUTTON",
                "remaining": "核对实际子页、定制按钮或同名注册；未确认最终RPC和事务"}
    match = matches[0]
    return {"ncActionCode": match["BTNCODE"] if terminal else "", "registeredButtonPk": match["PK_BTN"],
            "entryButtonCode": match["BTNCODE"], "entryButtonName": match["BTNNAME"],
            "mappingState": "REGISTERED_BUTTON_RPC_PENDING" if terminal else "ENTRY_ONLY_SUBPAGE_PENDING",
            "remaining": "最终RPC、服务包装、同步审计和NC事务待验证" if terminal else "仅定位打开入口；子页确认/保存及复合对象待专用适配"}


def prepare(output):
    nodes, operations = csv_rows(SCOPE / "node-scope.csv"), csv_rows(SCOPE / "operation-scope.csv")
    assert len(nodes) == 35 and len(operations) == 243
    assert len({r["scopeId"] for r in operations}) == 243
    assert set(NODE_CODES) == {r["nodeName"] for r in nodes}
    functions = defaultdict(list)
    for f in evidence("function_candidates") + evidence("extra_functions"):
        if all(old["CFUNID"] != f["CFUNID"] for old in functions[f["FUNCODE"]]):
            functions[f["FUNCODE"]].append(f)
    params = defaultdict(dict)
    duplicate_params = []
    for p in evidence("all_node_ui_params"):
        old = params[p["FUNCODE"]].get(p["PARAMNAME"])
        assert old is None or old == p["PARAMVALUE"], (p["FUNCODE"], p["PARAMNAME"], "Conflicting native parameter values")
        if old is not None:
            duplicate_params.append({"node": p["FUNCODE"], "parameter": p["PARAMNAME"], "value": old})
        params[p["FUNCODE"]][p["PARAMNAME"]] = p["PARAMVALUE"]
    registered = defaultdict(list)
    for b in evidence("buttons_0") + evidence("buttons_1"):
        registered[b["FUNCODE"]].append(b)
    bills = defaultdict(list)
    for b in evidence("original_bill_rules"):
        if b["PK_GROUP"] in ("~", "0001A9100000000005MH"):
            bills[b["NODECODE"]].append(b)
    physical = []
    for node in nodes:
        for code in NODE_CODES[node["nodeName"]]:
            assert len(functions[code]) == 1, (code, functions[code])
            f, p = functions[code][0], params[code]
            assert f["ISENABLE"] == "Y", code
            physical.append({"nodeId": node["nodeId"], "nodeName": node["nodeName"], "ncFunctionCode": code,
                "registeredFunctionPk": f["CFUNID"], "registeredName": f["FUN_NAME"], "orgTypeCode": f["ORGTYPECODE"],
                "module": f["OWN_MODULE"], "parentPk": f["PARENT_ID"], "beanConfig": p.get("BeanConfigFilePath", ""),
                "transactionType": p.get("transtype", ""), "transactionTypePk": p.get("pk_transtype", ""),
                "pluginConfigs": json.dumps({k: v for k, v in p.items() if k.startswith("PluginBeanConfigFilePath")}, ensure_ascii=False),
                "registeredButtonCount": len(registered[code]), "mappingState": "NODE_REGISTRATION_CONFIRMED_RPC_PENDING", "validated": "N"})
    bindings = []
    for row in operations:
        for code in NODE_CODES[row["nodeName"]]:
            binding = {"bindingId": row["scopeId"] + "@" + code, "scopeId": row["scopeId"], "sourceRow": row["sourceRow"],
                "nodeId": row["nodeId"], "nodeName": row["nodeName"], "buttonName": row["buttonName"],
                "pageName": row["buttonName"].replace("生成信息-", "生产信息-"), "adapterFamily": row["adapterFamily"], "ncFunctionCode": code}
            if row["adapterFamily"] == "UNRESOLVED_NODE":
                binding["adapterFamily"] = "IC_PURCHASE_TRANSACTION" if code == "40080602400" else "IC_PRODUCT_TRANSACTION"
            binding.update(button_mapping(row, registered[code]))
            binding["validated"] = "N"
            binding["enabled"] = "N"
            bindings.append(binding)
    assert len(physical) == 41 and len(bindings) == 312
    assert len({b["bindingId"] for b in bindings}) == 312
    direct = [(b["ncFunctionCode"], b["ncActionCode"]) for b in bindings if b["ncActionCode"]]
    assert len(direct) == len(set(direct)), "Distinct formal operations must not share one terminal mapping"
    output.mkdir(parents=True, exist_ok=True)
    targets = [output / name for name in ("node-bindings.csv", "operation-bindings.csv", "summary.json", "operation-codes.xml")]
    if any(p.exists() for p in targets):
        raise ValueError("Refusing to replace a previously generated evidence snapshot")
    write_csv(targets[0], physical)
    write_csv(targets[1], bindings)
    write_client_catalog(targets[3], bindings)
    summary = {"source_date": "2026-11-07", "schema": "QDNC", "formal_nodes": 35, "formal_operations": 243,
        "physical_nodes": len(physical), "physical_operation_bindings": len(bindings), "registered_buttons": sum(map(len, registered.values())),
        "states": dict(Counter(b["mappingState"] for b in bindings)), "validated_or_enabled": 0,
        "existing_duplicate_parameters": duplicate_params,
        "notes": ["注册映射不等于适配完成；所有行均禁用且未通过NC验证", "子页打开入口不作为保存/确认动作", "两个原缺失节点依据现场参数独立映射"],
        "file_sha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in targets[:2]},
        "evidence_sha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(EVIDENCE.glob("*.json"))},
    }
    targets[2].write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if not k.endswith("sha256")}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    prepare(parser.parse_args().output.resolve())
