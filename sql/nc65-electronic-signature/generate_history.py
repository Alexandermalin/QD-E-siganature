"""Emit reviewable Oracle 11g monthly history DDL; never connect or execute SQL."""
# 离线生成 Oracle 月度历史表脚本，不连接或执行数据库；模块文档同时用于 --help，保留其原文以保持命令行输出。
import argparse
from datetime import datetime
from pathlib import Path

# 固定允许归档的七类表：每项记录主键和可选业务唯一列，决定历史表约束；不接受外部任意表名。
TABLES = {
    "qd_es_request": ("request_id", None),
    "qd_es_object": ("object_id", "request_id,client_object_id"),
    "qd_es_signlog": ("event_id", "request_id,attempt_id,object_id"),
    "qd_es_audit": ("audit_id", "request_id,object_id,operation_type"),
    "qd_es_change": ("change_id", None),
    "qd_es_snapshot": ("snapshot_id", None),
    "qd_es_security": ("event_id", "source_system,source_event_id,source_version"),
}


# 逐月产生包含起止月份的 YYYYMM 序列；严格校验格式和先后顺序，单次最多 120 个月。
def months(start, end):
    first = datetime.strptime(start, "%Y%m")
    last = datetime.strptime(end, "%Y%m")
    if first.strftime("%Y%m") != start or last.strftime("%Y%m") != end or first > last:
        raise ValueError("Invalid month range")
    value = first
    count = 0
    while value <= last:
        count += 1
        if count > 120:
            raise ValueError("At most 120 months per approval script")
        yield value.strftime("%Y%m")
        # 十二月显式推进到下一年一月，避免直接把月份加一造成非法日期。
        value = value.replace(year=value.year + 1, month=1) if value.month == 12 else value.replace(month=value.month + 1)


# 返回完整待审批 SQL 文本；按月份为固定表生成结构、约束和查询索引，不覆盖已存在历史表。
def generate(start, end, include_org_batch_receipt=False):
    # 以下头部文本属于生成器输出字符串，保持原样；脚本只说明审批及隐式提交约束，不自行提交或执行。
    output = ["-- Generated monthly history DDL. REVIEW AND APPROVE BEFORE EXECUTION.",
              "-- Oracle DDL commits implicitly. Run only after 001/002/003.",
              "-- Existing monthly tables are not dropped or overwritten. No COMMIT command.",
              "-- CTAS uses existing approved QD columns; all data remains in the same schema."]
    if include_org_batch_receipt:
        output.append("-- Receipt history additionally requires the reviewed 1.3.1 receipt schema.")
    for month in months(start, end):
        for index, (base, (primary, unique)) in enumerate(TABLES.items(), 1):
            target = f"{base}_h{month}"
            prefix = f"qdh{month}_{index}"
            output.append(f"CREATE TABLE {target} AS SELECT * FROM {base} WHERE 1=0;")
            output.append(f"ALTER TABLE {target} ADD CONSTRAINT {prefix}_pk PRIMARY KEY({primary});")
            if unique:
                output.append(f"ALTER TABLE {target} ADD CONSTRAINT {prefix}_uq UNIQUE({unique});")
            # 请求和对象以创建时间查询，其余日志以事件时间查询，历史索引沿用对应时间列。
            stamp = "created_at" if base in ("qd_es_request", "qd_es_object") else "event_time"
            output.append(f"CREATE INDEX {prefix}_scope_ix ON {target}(pk_group,pk_org,{stamp});")
            # 按实际表关联补齐请求、审计和单据查询索引，不能为没有对应列的表创建同样索引。
            if base not in ("qd_es_request", "qd_es_security"):
                output.append(f"CREATE INDEX {prefix}_req_ix ON {target}(request_id);")
            if base in ("qd_es_change", "qd_es_snapshot"):
                output.append(f"CREATE INDEX {prefix}_audit_ix ON {target}(audit_id);")
            if base in ("qd_es_object", "qd_es_signlog", "qd_es_audit"):
                output.append(f"CREATE INDEX {prefix}_bill_ix ON {target}(pk_group,pk_org,bill_code);")
        if include_org_batch_receipt:
            target = f"qd_es_batch_page_h{month}"
            prefix = f"qdbp{month}"
            output.append(f"CREATE TABLE {target} AS SELECT * FROM qd_es_batch_page WHERE 1=0;")
            output.append(f"ALTER TABLE {target} ADD CONSTRAINT {prefix}_pk PRIMARY KEY(record_id);")
            output.append(f"ALTER TABLE {target} ADD CONSTRAINT {prefix}_uq UNIQUE(request_id,object_id,row_kind,item_key);")
            output.append(f"CREATE INDEX {prefix}_scope_ix ON {target}(pk_group,pk_org,event_time);")
        # 历史表定义完成后登记 PREPARED 月份，运行时归档仍需验证目标结构及目录。
        output.append(f"INSERT INTO qd_es_archive(archive_month,status) VALUES ('{month}','PREPARED');")
    return "\n".join(output) + "\n"


# 命令行必须显式给出起止月份和新输出文件，目标存在就拒绝，避免覆盖已审批脚本。
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from-month", required=True)
    parser.add_argument("--to-month", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--include-org-batch-receipt", action="store_true",
                        help="include receipt history only after the 1.3.1 receipt table is prepared")
    args = parser.parse_args()
    target = Path(args.output)
    if target.exists():
        raise SystemExit("Refusing to overwrite an existing approval script")
    target.write_text(generate(args.from_month, args.to_month, args.include_org_batch_receipt), encoding="utf-8")
