"""Generate OFF/unmapped scope seed SQL from the approved CSV; never execute it."""
# 从正式 CSV 离线生成目录种子，不执行 SQL；原模块文档用于 CLI 帮助，保持原文并在此补充中文说明。
import argparse
import csv
from pathlib import Path


# 将文本转为 SQL 字符串字面量，只转义单引号；字段来源为已批准清单，不用于拼接 SQL 标识符。
def literal(value):
    return "'" + value.replace("'", "''") + "'"


# 读取含 BOM 或不含 BOM 的 UTF-8 清单，要求恰好 243 项且范围 ID 唯一，返回仅补缺的目录初始化文本。
def generate(source):
    with Path(source).open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    assert len(rows) == 243, "Approved scope must contain 243 operations"
    assert len({row["scopeId"] for row in rows}) == len(rows), "Duplicate scope ID"
    # 保留生成 SQL 的头部字符串及来源行说明，避免改变生成器输出；新条目始终未映射、未验证。
    statements = ["-- Generated from approved operation-scope.csv: 243 catalogue entries only.",
                  "-- Review and approve before execution. No NC function/button code is invented.",
                  "-- Existing rows are preserved; all new entries remain UNMAPPED and validated=N.",
                  "-- No COMMIT; no existing policy is enabled or modified."]
    # 逐行保留正式范围标识、节点名、按钮名和原表行号；不根据名称推测真实功能或按钮编码。
    for row in rows:
        scope = literal(row["scopeId"])
        node = literal(row["nodeName"])
        button = literal(row["buttonName"])
        statements.append(f"-- {row['sourceSheet']} row {int(row['sourceRow'])}: {row['category']}")
        # 只插入缺少的范围目录，不覆盖既有映射或修改策略启用状态。
        statements.append(f"MERGE INTO qd_es_node d USING (SELECT {scope} scope_id FROM dual) s "
                          "ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN "
                          "INSERT(scope_id,node_name,button_name,mapping_state,validated) "
                          f"VALUES(s.scope_id,{node},{button},'UNMAPPED','N');")
    return "\n".join(statements) + "\n"


# 显式指定清单与新输出文件，存在目标时拒绝覆盖；写入 UTF-8 候选脚本，后续执行需按部署流程审批。
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output)
    if output.exists():
        raise SystemExit("Refusing to overwrite existing approval script")
    output.write_text(generate(args.source), encoding="utf-8")
