// Offline only: exercise the read-only rule preflight against an in-memory SQLite fixture.
// Run with Node 22: node --experimental-sqlite tests/sql/sapply-rule-preflight.test.js
// SQLite is not an Oracle compatibility or NC runtime test.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');

const db = new DatabaseSync(':memory:');
db.function('nvl', (value, fallback) => value === null ? fallback : value);
db.exec(`CREATE TABLE dual (dummy TEXT);
  INSERT INTO dual VALUES ('X');
  CREATE TABLE pub_pluginitem (
    pk_pluginitem TEXT, dr INTEGER, vmodulename TEXT, vcomponentname TEXT,
    vextendpointname TEXT, veventtype TEXT, vextendtype TEXT, vruleclass TEXT,
    vtargetclass TEXT, vextendmodule TEXT, localtype TEXT, industrytype TEXT
  );`);

const sql = fs.readFileSync(path.join(__dirname, '../../sql/nc65-electronic-signature/inventory-pilot/sapply-rule-preflight.sql'), 'utf8');
const query = db.prepare(sql);
function result(point, phase) {
  const rows = query.all();
  assert.equal(rows.length, 18);
  return rows.find(row => row.bp_name === point && row.event_name === phase);
}
function seed(id, dr, type, module) {
  db.prepare(`INSERT INTO pub_pluginitem VALUES (?, ?, 'IC', 'm4455',
      'nc.bs.ic.m4455.maintain.InsertBP', 'before', ?,
      'nc.impl.ic.qd.esign.SapplyBpRules$BeforeInsert', NULL, ?, '~', '~')`)
    .run(id, dr, type, module);
}

let row = result('InsertBP', 'before');
assert.equal(row.registration_check, 'MISSING');
assert.equal(row.matching_rows, 0);
assert.equal(row.matching_candidate_fields, 0);

seed('one', 0, 'addBefore', 'ic');
row = result('InsertBP', 'before');
assert.equal(row.registration_check, 'PRESENT_REVIEW');
assert.equal(row.matching_rows, 1);
assert.equal(row.matching_candidate_fields, 1);

db.exec("UPDATE pub_pluginitem SET dr=1 WHERE pk_pluginitem='one'");
row = result('InsertBP', 'before');
assert.equal(row.matching_rows, 1, 'a logically deleted row still appears in loader-oriented counts');
assert.equal(row.matching_candidate_fields, 0, 'a logically deleted row is not a clean candidate');

db.exec("UPDATE pub_pluginitem SET dr=0,vextendtype='addAfter' WHERE pk_pluginitem='one'");
assert.equal(result('InsertBP', 'before').matching_candidate_fields, 0);
db.exec("UPDATE pub_pluginitem SET vextendtype='ADDBEFORE' WHERE pk_pluginitem='one'");
assert.equal(result('InsertBP', 'before').matching_candidate_fields, 0, 'NC rule type matching is case-sensitive');
db.exec("UPDATE pub_pluginitem SET vextendtype='addBefore',vextendmodule='other' WHERE pk_pluginitem='one'");
assert.equal(result('InsertBP', 'before').matching_candidate_fields, 0);
db.exec("UPDATE pub_pluginitem SET vextendmodule='ic',veventtype='BEFORE' WHERE pk_pluginitem='one'");
row = result('InsertBP', 'before');
assert.equal(row.matching_rows, 1, 'case differences remain visible for operator review');
assert.equal(row.matching_candidate_fields, 0, 'NC event type matching is case-sensitive');
db.exec("UPDATE pub_pluginitem SET veventtype='before',vcomponentname='M4455' WHERE pk_pluginitem='one'");
row = result('InsertBP', 'before');
assert.equal(row.matching_rows, 1);
assert.equal(row.matching_candidate_fields, 0, 'NC component lookup uses its exact key');

seed('two', 0, 'addBefore', 'ic');
row = result('InsertBP', 'before');
assert.equal(row.registration_check, 'DUPLICATE');
assert.equal(row.matching_rows, 2);
assert.equal(row.matching_candidate_fields, 1);
assert.equal(result('UnapproveBP', 'after').registration_check, 'MISSING');
db.close();
console.log('PASS sapply-rule-preflight: missing, valid, deleted, mismatched and duplicate rule records');
