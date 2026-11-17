"""Build a reviewable, offline QDNC install bundle; never connect to a database."""
import argparse
import hashlib
import importlib.util
import json
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
SQL = ROOT / "sql/nc65-electronic-signature"


def prepare(output):
    spec = importlib.util.spec_from_file_location("history", SQL / "generate_history.py")
    history = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(history)
    sources = [SQL / name for name in ("001_schema.sql", "002_indexes.sql", "003_init.sql", "008_scope_seed.sql")]
    texts = [path.read_text(encoding="utf-8-sig") for path in sources]
    monthly = history.generate("202608", "202608")
    tables = re.findall(r"CREATE TABLE\s+(\w+)", texts[0] + "\n" + monthly, re.I)
    assert len(tables) == 24 and len(set(tables)) == 24
    assert len(re.findall(r"MERGE INTO qd_es_node", texts[3], re.I)) == 243
    indexes = re.findall(r"CREATE INDEX\s+(\w+)", texts[1] + "\n" + monthly, re.I)
    names = tables + indexes
    literals = ",".join("'" + name.upper() + "'" for name in names)
    preflight = f"""-- QDNC TEST ONLY. Approval required before execution.
-- Execute as a stop-on-first-error Oracle batch. DDL commits implicitly.
-- Adds 17 isolated QD tables + 7 August history tables; seeds 243 disabled entries.
-- Does not register, enable or replace any native NC node, plugin, listener or rule.
DECLARE
  n NUMBER;
  c VARCHAR2(100);
BEGIN
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'QDNC'
     OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'QDNC' THEN
    RAISE_APPLICATION_ERROR(-20001,'Expected QDNC session user and current schema');
  END IF;
  SELECT value INTO c FROM nls_database_parameters WHERE parameter='NLS_CHARACTERSET';
  IF c <> 'ZHS16GBK' THEN RAISE_APPLICATION_ERROR(-20002,'Site charset changed; re-review'); END IF;
  SELECT COUNT(*) INTO n FROM user_objects WHERE object_name IN ({literals});
  IF n <> 0 THEN RAISE_APPLICATION_ERROR(-20003,'Target object already exists; inspect partial install'); END IF;
END;
/
"""
    sections = [preflight]
    for path, text in zip(sources, texts):
        sections.extend(["-- BEGIN SOURCE " + path.name, text, "-- END SOURCE " + path.name])
    sections.extend(["-- BEGIN GENERATED HISTORY 202608", monthly, "-- END GENERATED HISTORY 202608"])
    # DBX commits each successful statement. Also supports clients with manual DML transactions.
    sections.append("-- Explicitly approved initialization only; all controls remain off.\nCOMMIT;\n")
    content = "\n".join(sections)
    output.mkdir(parents=True, exist_ok=True)
    target = output / "install.sql"
    if target.exists():
        raise ValueError("Refusing to replace an existing approval bundle")
    target.write_text(content, encoding="utf-8", newline="\n")
    manifest = {
        "schema": "QDNC", "environment": "test", "prepared_on": "2026-11-07",
        "executed": False, "charset": "ZHS16GBK", "oracle_version_observed": "11.2.0.4.0",
        "table_count": len(tables), "tables": tables, "explicit_indexes": indexes,
        "scope_seed_count": 243, "new_history_months": ["202608"],
        "native_nc_changes": [], "master_switch_default": "OFF", "validated_default": "N",
        "install_sha256": hashlib.sha256(content.encode("utf-8")).hexdigest(),
        "source_sha256": {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
        "validation_script": "../010_test_database_smoke.sql",
        "validation_sha256": hashlib.sha256((SQL / "010_test_database_smoke.sql").read_bytes()).hexdigest(),
        "validation_writes": "Synthetic QD_ES_TEST_* rows in QD-owned tables only; rolls back all test rows",
        "execution": "Stop on first error; Oracle DDL implicitly commits; do not retry a partial installation blindly",
    }
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"install": str(target), "tables": len(tables), "sha256": manifest["install_sha256"]}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    prepare(parser.parse_args().output.resolve())
