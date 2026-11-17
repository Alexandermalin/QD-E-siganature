"""Prepare unexecuted NC acceptance records; never connect to NC or a database."""
import argparse
import csv
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path


def read_csv(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def write_csv(path, rows):
    with path.open("x", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--test-user", default="yonyou02")
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    scope_path = root / "config/qd-esign/operation-scope.csv"
    bindings_path = root / "config/qd-esign/site-20261107/operation-bindings.csv"
    scopes, bindings = read_csv(scope_path), read_csv(bindings_path)
    ids = {row["scopeId"] for row in scopes}
    if len(scopes) != 243 or len(ids) != 243 or len({r["nodeId"] for r in scopes}) != 35:
        raise ValueError("Expected the approved 35-node/243-operation baseline")
    if len(bindings) != 312 or len({r["bindingId"] for r in bindings}) != 312:
        raise ValueError("Expected 312 unique physical bindings")
    if {r["scopeId"] for r in bindings} != ids:
        raise ValueError("Physical bindings do not cover exactly the approved scope")
    if any(r["enabled"] != "N" or r["validated"] != "N" for r in bindings):
        raise ValueError("Review changed binding enablement before preparing a new baseline")
    output = args.output or root / "build/acceptance" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    output = output.resolve()
    if not output.is_relative_to(root / "build"):
        raise ValueError("Acceptance output must stay inside this project's build directory")
    if output.exists():
        raise FileExistsError("Refusing to overwrite an existing acceptance run")
    output.mkdir(parents=True)
    common = dict(testUser=args.test_user, businessUnit="", role="", buildVersion="",
                  environment="shared_test", originalBehavior="", testSamplePk="",
                  requestId="", batchId="", actualResult="", evidencePath="",
                  executedAt="", status="NOT_RUN")
    logical = []
    for row in scopes:
        logical.append(dict(scopeId=row["scopeId"], sourceRow=row["sourceRow"],
                            nodeName=row["nodeName"], buttonName=row["buttonName"],
                            adapterFamily=row["adapterFamily"], **common))
    physical = []
    for row in bindings:
        physical.append(dict(bindingId=row["bindingId"], scopeId=row["scopeId"],
                             nodeCode=row["ncFunctionCode"], buttonCode=row["ncActionCode"],
                             mappingState=row["mappingState"], blocker=row["remaining"],
                             **common))
    scenarios = [
        ("AUTH", "Current identity; correct/wrong password; cancel; native lock/unlock"),
        ("PERMISSION", "Button and BU authorization, cross-user/object tampering rejected"),
        ("POLICY", "Four signature/audit combinations; default off; stale authorization rejected"),
        ("BUSINESS", "Original rules, exceptions, PK/code and no-change save behavior"),
        ("TRANSACTION", "Audit failure rolls back business; independent signature remains"),
        ("REPLAY", "Duplicate and failed requests cannot execute business again"),
        ("BATCH", "One confirmation; real outcome and evidence for each object"),
        ("SNAPSHOT", "Authorized fields, header/body/relations, GBK/CLOB round trip"),
        ("QUERY_EXPORT", "Permissioned trace links; Chinese CSV; failed export preserves files"),
        ("ARCHIVE", "Data-bearing cross-month copy/verify/recovery; no runtime DDL"),
        ("V1", "Existing V1/probe and original interceptor chain do not double-intercept"),
        ("PATCH_ROLLBACK", "Verified backup; install/withdraw; original business restored"),
    ]
    write_csv(output / "logical-243.csv", logical)
    write_csv(output / "physical-312.csv", physical)
    write_csv(output / "scenarios.csv", [dict(scenarioId=i, expected=e, testUser=args.test_user,
              applicableScopeIds="", actualResult="", evidencePath="", status="NOT_RUN") for i, e in scenarios])
    manifest = dict(created_at_utc=datetime.now(timezone.utc).isoformat(), test_user=args.test_user,
                    logical_count=len(logical), physical_count=len(physical),
                    all_statuses="NOT_RUN", pilot="new material save and new sales order save",
                    sources={str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                             for p in (scope_path, bindings_path)},
                    outputs={p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in output.glob("*.csv")},
                    limitations=["Templates only; no NC operation has been executed.",
                                 "Actual BU/role and shared test maintenance/isolation are pending.",
                                 "Do not put passwords, tokens or keys in these records."])
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(dict(output=str(output), logical=243, physical=312, status="NOT_RUN")))


if __name__ == "__main__":
    main()
