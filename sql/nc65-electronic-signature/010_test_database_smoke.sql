-- QDNC TEST ONLY. Review/approve before execution, after the 24-table install.
-- Native NC business/registration tables are never modified.
-- Every synthetic write is rolled back inside this single PL/SQL block.
-- This proves Oracle SQL/constraints/LOB behavior, not NC authentication or CMT.
DECLARE
  r VARCHAR2(32) := RAWTOHEX(SYS_GUID());
  o VARCHAR2(32) := RAWTOHEX(SYS_GUID());
  s VARCHAR2(32) := RAWTOHEX(SYS_GUID());
  a VARCHAR2(32) := RAWTOHEX(SYS_GUID());
  k VARCHAR2(32) := RAWTOHEX(SYS_GUID());
  p VARCHAR2(32) := RAWTOHEX(SYS_GUID());
  n NUMBER;
  tests NUMBER := 0;
  txt VARCHAR2(32767);
  PROCEDURE ok(condition BOOLEAN, message VARCHAR2) IS
  BEGIN
    IF condition IS NULL OR NOT condition THEN RAISE_APPLICATION_ERROR(-20100,message); END IF;
    tests := tests + 1;
  END;
BEGIN
  SAVEPOINT qdes_test_start;
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'QDNC' OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'QDNC' THEN
    RAISE_APPLICATION_ERROR(-20101,'QDNC test session required');
  END IF;
  SELECT COUNT(*) INTO n FROM qd_es_node;
  ok(n=243,'Fresh install must have 243 formal scope entries');
  SELECT COUNT(*) INTO n FROM qd_es_node WHERE validated<>'N' OR mapping_state<>'UNMAPPED'
    OR node_code IS NOT NULL OR button_code IS NOT NULL OR adapter_class IS NOT NULL;
  ok(n=0,'All seeded entries must remain disabled and unmapped');
  SELECT COUNT(*) INTO n FROM user_tab_columns WHERE table_name='QD_ES_REQUEST' AND column_name='REASON'
    AND data_type='VARCHAR2' AND data_length=4000 AND char_used='B';
  ok(n=1,'Reason must use 4000 BYTE');
  SELECT COUNT(*) INTO n FROM user_tab_columns WHERE table_name='QD_ES_REQUEST' AND column_name='USER_NAME'
    AND char_length=200 AND char_used='C';
  ok(n=1,'Names must use explicit CHAR semantics');
  SELECT COUNT(*) INTO n FROM qd_es_archive WHERE archive_month='202608' AND status='PREPARED';
  ok(n=1,'August history directory must be prepared');
  SELECT COUNT(*) INTO n FROM user_tables WHERE table_name IN ('QD_ES_REQUEST_H202608','QD_ES_OBJECT_H202608',
    'QD_ES_SIGNLOG_H202608','QD_ES_AUDIT_H202608','QD_ES_CHANGE_H202608','QD_ES_SNAPSHOT_H202608','QD_ES_SECURITY_H202608');
  ok(n=7,'Seven history tables required');

  INSERT INTO qd_es_control(pk_group,modified_at) VALUES ('QD_ES_TEST_GROUP','2026-08-01 00:00:00');
  SELECT COUNT(*) INTO n FROM qd_es_control WHERE pk_group='QD_ES_TEST_GROUP' AND master_enabled='N' AND policy_version=1;
  ok(n=1,'Control defaults must be off/version 1');
  INSERT INTO qd_es_policy(policy_id,pk_group,pk_org,node_code,button_code,modified_at)
    VALUES(p,'QD_ES_TEST_GROUP','QD_ES_TEST_ORG','QD_ES_TEST_NODE','Save','2026-08-01 00:00:00');
  SELECT COUNT(*) INTO n FROM qd_es_policy WHERE policy_id=p AND sign_enabled='N' AND audit_enabled='N'
    AND enabled='N' AND validated='N' AND reason_required='N';
  ok(n=1,'Policy defaults must be off');
  UPDATE qd_es_policy SET policy_version=2 WHERE policy_id=p AND policy_version=1;
  ok(SQL%ROWCOUNT=1,'First optimistic update must succeed');
  UPDATE qd_es_policy SET policy_version=3 WHERE policy_id=p AND policy_version=1;
  ok(SQL%ROWCOUNT=0,'Stale optimistic update must affect zero rows');

  INSERT INTO qd_es_request(request_id,batch_id,user_id,pk_group,pk_org,node_code,button_code,operation_type,
    reason,signature_state,business_state,policy_version,control_version,created_at,expires_at,finished_at,archive_month)
    VALUES(r,r,'QD_ES_TEST_USER','QD_ES_TEST_GROUP','QD_ES_TEST_ORG','QD_ES_TEST_NODE','Save','SAVE',
      '电子签名测试','SUCCESS','READY',1,1,'2026-08-01 00:00:00','2026-08-01 00:10:00','2026-08-01 00:01:00','202608');
  SELECT reason INTO txt FROM qd_es_request WHERE request_id=r;
  ok(txt='电子签名测试' AND LENGTHB(txt)=12,'Chinese GBK text must round trip');
  txt := RPAD('X',4001,'X');
  BEGIN
    UPDATE qd_es_request SET reason=txt WHERE request_id=r;
    RAISE_APPLICATION_ERROR(-20102,'Oversized reason accepted');
  EXCEPTION WHEN OTHERS THEN IF SQLCODE<>-12899 THEN RAISE; END IF; END;
  tests:=tests+1;
  UPDATE qd_es_request SET business_state='RUNNING',execution_token=k WHERE request_id=r AND business_state='READY';
  ok(SQL%ROWCOUNT=1,'Claim must change one ready request');
  UPDATE qd_es_request SET business_state='RUNNING' WHERE request_id=r AND business_state='READY';
  ok(SQL%ROWCOUNT=0,'Second claim must not execute again');
  INSERT INTO qd_es_object(object_id,request_id,client_object_id,object_type,pk_group,pk_org,created_at,archive_month)
    VALUES(o,r,'temporary-1','QD_ES_TEST_OBJECT','QD_ES_TEST_GROUP','QD_ES_TEST_ORG','2026-08-01 00:00:00','202608');
  BEGIN
    INSERT INTO qd_es_object(object_id,request_id,client_object_id,object_type,pk_group,pk_org,created_at,archive_month)
      VALUES(RAWTOHEX(SYS_GUID()),r,'temporary-1','QD_ES_TEST_OBJECT','QD_ES_TEST_GROUP','QD_ES_TEST_ORG','2026-08-01 00:00:00','202608');
    RAISE_APPLICATION_ERROR(-20103,'Duplicate client object accepted');
  EXCEPTION WHEN DUP_VAL_ON_INDEX THEN tests:=tests+1; END;
  INSERT INTO qd_es_signlog(event_id,request_id,attempt_id,object_id,user_id,pk_group,pk_org,node_code,button_code,result_code,event_time,archive_month)
    VALUES(s,r,k,o,'QD_ES_TEST_USER','QD_ES_TEST_GROUP','QD_ES_TEST_ORG','QD_ES_TEST_NODE','Save','SUCCESS','2026-08-01 00:00:01','202608');
  BEGIN
    INSERT INTO qd_es_signlog(event_id,request_id,attempt_id,object_id,user_id,pk_group,pk_org,node_code,button_code,result_code,event_time,archive_month)
      SELECT RAWTOHEX(SYS_GUID()),request_id,attempt_id,object_id,user_id,pk_group,pk_org,node_code,button_code,result_code,event_time,archive_month
      FROM qd_es_signlog WHERE event_id=s;
    RAISE_APPLICATION_ERROR(-20104,'Duplicate signature event accepted');
  EXCEPTION WHEN DUP_VAL_ON_INDEX THEN tests:=tests+1; END;
  UPDATE qd_es_object SET object_pk='QD_ES_TEST_PK',bill_code='QD_ES_TEST_BILL' WHERE object_id=o;
  UPDATE qd_es_signlog SET object_pk='QD_ES_TEST_PK',bill_code='QD_ES_TEST_BILL' WHERE request_id=r AND object_id=o;
  SELECT COUNT(*) INTO n FROM qd_es_object ob JOIN qd_es_signlog sl ON sl.object_id=ob.object_id AND sl.request_id=ob.request_id
    WHERE ob.object_id=o AND sl.object_pk=ob.object_pk AND sl.bill_code=ob.bill_code;
  ok(n=1,'Backfilled identity must match signature object');
  INSERT INTO qd_es_audit(audit_id,request_id,object_id,user_id,pk_group,pk_org,node_code,button_code,operation_type,result_code,event_time,archive_month)
    VALUES(a,r,o,'QD_ES_TEST_USER','QD_ES_TEST_GROUP','QD_ES_TEST_ORG','QD_ES_TEST_NODE','Save','SAVE','SUCCESS','2026-08-01 00:00:02','202608');
  BEGIN
    UPDATE qd_es_audit SET result_code='FAILED' WHERE audit_id=a;
    RAISE_APPLICATION_ERROR(-20105,'Failed business recorded as success audit');
  EXCEPTION WHEN OTHERS THEN IF SQLCODE<>-2290 THEN RAISE; END IF; END;
  tests:=tests+1;
  BEGIN
    INSERT INTO qd_es_audit(audit_id,request_id,object_id,user_id,pk_group,pk_org,node_code,button_code,operation_type,result_code,event_time,archive_month)
      SELECT RAWTOHEX(SYS_GUID()),request_id,object_id,user_id,pk_group,pk_org,node_code,button_code,operation_type,result_code,event_time,archive_month
      FROM qd_es_audit WHERE audit_id=a;
    RAISE_APPLICATION_ERROR(-20106,'Duplicate business audit accepted');
  EXCEPTION WHEN DUP_VAL_ON_INDEX THEN tests:=tests+1; END;
  SAVEPOINT qdes_atomic_probe;
  BEGIN
    UPDATE qd_es_object SET bill_code='SHOULD_ROLL_BACK' WHERE object_id=o;
    UPDATE qd_es_audit SET result_code='FAILED' WHERE audit_id=a;
    RAISE_APPLICATION_ERROR(-20107,'Expected injected audit constraint failure');
  EXCEPTION WHEN OTHERS THEN
    IF SQLCODE<>-2290 THEN RAISE; END IF;
    ROLLBACK TO qdes_atomic_probe;
  END;
  SELECT COUNT(*) INTO n FROM qd_es_object WHERE object_id=o AND bill_code='QD_ES_TEST_BILL';
  ok(n=1,'Oracle same-transaction write must roll back after injected audit failure');

  INSERT INTO qd_es_change(change_id,audit_id,request_id,object_id,entity_name,row_id,field_code,change_type,before_value,after_value,pk_group,pk_org,event_time,archive_month)
    VALUES(RAWTOHEX(SYS_GUID()),a,r,o,'test_body','row-1','name','UPDATE',TO_CLOB('修改前'),TO_CLOB('修改后'),
      'QD_ES_TEST_GROUP','QD_ES_TEST_ORG','2026-08-01 00:00:02','202608');
  INSERT INTO qd_es_snapshot(snapshot_id,audit_id,request_id,object_id,snapshot_phase,entity_name,row_id,payload,payload_hash,pk_group,pk_org,event_time,archive_month)
    VALUES(RAWTOHEX(SYS_GUID()),a,r,o,'BEFORE','test_body','row-1',TO_CLOB(RPAD('A',4000,'A'))||RPAD('B',4000,'B'),RPAD('a',64,'a'),
      'QD_ES_TEST_GROUP','QD_ES_TEST_ORG','2026-08-01 00:00:02','202608');
  SELECT DBMS_LOB.GETLENGTH(payload) INTO n FROM qd_es_snapshot WHERE request_id=r;
  ok(n=8000,'Snapshot must store CLOB larger than 4000 bytes');
  INSERT INTO qd_es_locator(request_id,archive_month,request_closed) VALUES(r,'202608','Y');
  INSERT INTO qd_es_locator(request_id,archive_month,request_closed) VALUES(r,'202609','Y');
  SELECT COUNT(*) INTO n FROM qd_es_locator WHERE request_id=r;
  ok(n=2,'Request may span month locator entries');
  INSERT INTO qd_es_security(event_id,source_system,source_event_id,source_version,pk_group,event_type,result_code,event_time,archive_month)
    VALUES(k,'QD_ES_TEST',r,'v1','QD_ES_TEST_GROUP','LOGIN','SUCCESS','2026-08-01 00:00:00','202608');
  INSERT INTO qd_es_security_key(event_id,source_system,source_event_id,source_version) VALUES(k,'QD_ES_TEST',r,'v1');
  BEGIN
    INSERT INTO qd_es_security_key(event_id,source_system,source_event_id,source_version) VALUES(RAWTOHEX(SYS_GUID()),'QD_ES_TEST',r,'v1');
    RAISE_APPLICATION_ERROR(-20108,'Duplicate native source version accepted');
  EXCEPTION WHEN DUP_VAL_ON_INDEX THEN tests:=tests+1; END;

  INSERT INTO qd_es_request_h202608 SELECT * FROM qd_es_request WHERE request_id=r;
  INSERT INTO qd_es_object_h202608 SELECT * FROM qd_es_object WHERE request_id=r;
  INSERT INTO qd_es_signlog_h202608 SELECT * FROM qd_es_signlog WHERE request_id=r;
  INSERT INTO qd_es_audit_h202608 SELECT * FROM qd_es_audit WHERE request_id=r;
  INSERT INTO qd_es_change_h202608 SELECT * FROM qd_es_change WHERE request_id=r;
  INSERT INTO qd_es_snapshot_h202608 SELECT * FROM qd_es_snapshot WHERE request_id=r;
  INSERT INTO qd_es_security_h202608 SELECT * FROM qd_es_security WHERE event_id=k;
  SELECT COUNT(*) INTO n FROM qd_es_snapshot q JOIN qd_es_snapshot_h202608 h ON h.snapshot_id=q.snapshot_id
    WHERE q.request_id=r AND DBMS_LOB.COMPARE(q.payload,h.payload)=0 AND q.payload_hash=h.payload_hash;
  ok(n=1,'Archived snapshot CLOB and hash must match');
  DELETE FROM qd_es_snapshot WHERE request_id=r;
  DELETE FROM qd_es_change WHERE request_id=r;
  DELETE FROM qd_es_audit WHERE request_id=r;
  DELETE FROM qd_es_signlog WHERE request_id=r;
  DELETE FROM qd_es_object WHERE request_id=r;
  DELETE FROM qd_es_request WHERE request_id=r;
  DELETE FROM qd_es_security WHERE event_id=k;
  SELECT COUNT(*) INTO n FROM (SELECT request_id FROM qd_es_request WHERE request_id=r
    UNION ALL SELECT request_id FROM qd_es_request_h202608 WHERE request_id=r);
  ok(n=1,'Online/history request query must find one archived request');
  SELECT COUNT(*) INTO n FROM qd_es_locator WHERE request_id=r;
  ok(n=2,'Locators must remain after online cleanup');
  SELECT COUNT(*) INTO n FROM qd_es_security_key WHERE source_system='QD_ES_TEST' AND source_event_id=r;
  ok(n=1,'Security idempotence key must remain after archive');
  ROLLBACK TO qdes_test_start;
  SELECT COUNT(*) INTO n FROM qd_es_request_h202608 WHERE request_id=r;
  ok(n=0,'All history test data must roll back');
  SELECT COUNT(*) INTO n FROM qd_es_security_key WHERE source_event_id=r AND source_system='QD_ES_TEST';
  ok(n=0,'Test idempotence key must roll back');
  SELECT COUNT(*) INTO n FROM qd_es_policy WHERE policy_id=p;
  ok(n=0,'Test policy must roll back');
  SELECT COUNT(*) INTO n FROM qd_es_control WHERE pk_group='QD_ES_TEST_GROUP';
  ok(n=0,'Test control must roll back');
  DBMS_OUTPUT.PUT_LINE('PASS QD SQL smoke assertions='||tests||'; all synthetic data rolled back');
EXCEPTION WHEN OTHERS THEN
  ROLLBACK TO qdes_test_start;
  RAISE;
END;
/
-- Only reached when the batch stops on error and the block succeeds.
SELECT 'SQL_ASSERTIONS_PASSED_AND_SYNTHETIC_DATA_ROLLED_BACK' AS result FROM dual;
