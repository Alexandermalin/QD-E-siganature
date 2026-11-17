// Read-only reconciliation queries exercised against in-memory fixtures, not NC or Oracle.
// Run with Node 22: node --experimental-sqlite tests/sql/verify-sapply-line.test.js
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');

const file = path.join(__dirname, '../../sql/nc65-electronic-signature/inventory-pilot/verify-sapply-request.sql');
const source = fs.readFileSync(file, 'utf8');
function statement(name) {
  const start = `-- BEGIN ${name}`;
  const end = `-- END ${name}`;
  const first = source.indexOf(start), last = source.indexOf(end);
  assert.ok(first >= 0 && last > first, `Missing ${name} reconciliation query`);
  return source.slice(first + start.length, last).trim().replace(/;\s*$/, '')
    .replace(/DBMS_LOB\.SUBSTR/gi, 'lob_substr');
}

const db = new DatabaseSync(':memory:');
db.function('nvl', (value, fallback) => value === null ? fallback : value);
db.function('rpad', (value, width, pad) => value === null ? null : String(value).padEnd(Number(width), pad).slice(0, Number(width)));
db.function('lob_substr', (value, length, start) => value === null ? null : String(value).slice(Number(start) - 1, Number(start) - 1 + Number(length)));
db.exec(`CREATE TABLE qd_es_request (request_id TEXT, pk_group TEXT, pk_org TEXT, user_code TEXT,
         node_code TEXT, button_code TEXT, signature_state TEXT, business_state TEXT);
  CREATE TABLE qd_es_object (object_id TEXT, request_id TEXT, object_type TEXT, object_pk TEXT, parent_object_id TEXT);
  CREATE TABLE qd_es_change (request_id TEXT, object_id TEXT, entity_name TEXT, row_id TEXT, parent_row_id TEXT,
         field_code TEXT, change_type TEXT, before_value TEXT, after_value TEXT);
  CREATE TABLE ic_sapply_h (cgeneralhid TEXT, pk_group TEXT, pk_org TEXT, fbillflag INTEGER, dr INTEGER);
  CREATE TABLE ic_sapply_b (cgeneralbid TEXT, cgeneralhid TEXT, crowno TEXT, isclosed TEXT, dr INTEGER);
  INSERT INTO qd_es_request VALUES ('R','G','O','U','40080801','LineClose','SUCCESS','UNKNOWN');
  INSERT INTO qd_es_request VALUES ('R2','G','O','U','40080801','LineClose','SUCCESS','SUCCESS');
  INSERT INTO qd_es_object VALUES ('H','R','IC_SAPPLY','BILL',NULL);
  INSERT INTO qd_es_object VALUES ('L1','R','IC_SAPPLY_LINE','ROW1','H');
  INSERT INTO qd_es_object VALUES ('L2','R','IC_SAPPLY_LINE','ROW2','H');
  INSERT INTO qd_es_object VALUES ('H2','R2','IC_SAPPLY','OTHER',NULL);
  INSERT INTO qd_es_object VALUES ('L3','R2','IC_SAPPLY_LINE','ROW3','H2');
  INSERT INTO qd_es_change VALUES ('R','L1','ic_sapply_b','ROW1','BILL','isclosed','UPDATE','N','Y');
  INSERT INTO qd_es_change VALUES ('R','L1','ic_sapply_b','OTHER','BILL','isclosed','UPDATE','N','Y');
  INSERT INTO qd_es_change VALUES ('R2','L3','ic_sapply_b','ROW3','OTHER','isclosed','UPDATE','N','Y');`);
db.prepare('INSERT INTO ic_sapply_h VALUES (?,?,?,?,?)').run('BILL'.padEnd(20), 'G', 'O'.padEnd(20), 4, 0);
db.prepare('INSERT INTO ic_sapply_b VALUES (?,?,?,?,?)').run('ROW1'.padEnd(20), 'BILL'.padEnd(20), '001', 'N', 0);

const params = { request_id: 'R', pilot_group: 'G', pilot_org: 'O', pilot_user_code: 'U' };
const current = db.prepare(statement('sapply-line-current'));
const changes = db.prepare(statement('sapply-line-audit'));
const rows = current.all(params);
assert.equal(rows.length, 2);
assert.deepEqual(rows.map(row => [row.line_pk, row.current_lookup, row.isclosed]),
  [['ROW1', 'PRESENT', 'N'], ['ROW2', 'LINE_NOT_FOUND', null]]);
const audited = changes.all(params);
assert.equal(audited.length, 1);
assert.deepEqual([audited[0].line_pk, audited[0].before_value, audited[0].after_value], ['ROW1', 'N', 'Y']);
assert.equal(current.all({ ...params, pilot_user_code: 'OTHER' }).length, 0);
assert.equal(changes.all({ ...params, pilot_group: 'OTHER' }).length, 0);
assert.equal(current.all({ ...params, pilot_org: 'OTHER' }).length, 0);
db.exec("UPDATE ic_sapply_b SET isclosed=NULL,dr=NULL WHERE crowno='001'");
assert.equal(current.all(params)[0].current_lookup, 'PRESENT', 'a null state differs from a missing row');
assert.equal(current.all(params)[0].isclosed, null);
db.exec("DELETE FROM qd_es_change WHERE request_id='R' AND object_id='L1' AND row_id='ROW1'");
assert.equal(changes.all(params).length, 0, 'a missing audit must not be invented from the current state');
db.prepare('UPDATE ic_sapply_h SET pk_org=? WHERE cgeneralhid=?').run('OTHER'.padEnd(20), 'BILL'.padEnd(20));
assert.ok(current.all(params).every(row => row.current_lookup === 'BILL_NOT_FOUND' && row.isclosed === null),
  'a bill now owned by a different organization must not expose its current row data');
db.exec("UPDATE qd_es_request SET button_code='Save' WHERE request_id='R'");
assert.equal(current.all(params).length, 0);
assert.equal(changes.all(params).length, 0);
db.close();
console.log('PASS verify-sapply-line: current rows, audit differences, missing rows and scope isolation');
