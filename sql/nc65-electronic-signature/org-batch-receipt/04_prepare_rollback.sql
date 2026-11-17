-- Candidate DML. First disable the organization-tab route and drain its requests; stop NC and archive services.
-- Replace these identity literals with the same isolated target verified for 02_schema.sql.
-- This does not drop receipt tables or erase their online/history evidence.
DECLARE
  c_schema CONSTANT VARCHAR2(128) := '<REPLACE_ISOLATED_SCHEMA>';
  c_database CONSTANT VARCHAR2(128) := '<REPLACE_ISOLATED_DB_UNIQUE_NAME>';
  c_host CONSTANT VARCHAR2(128) := '<REPLACE_ISOLATED_SERVER_HOST>';
  v_count NUMBER;
  v_now CHAR(19) := TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS');
BEGIN
  SAVEPOINT qd_batch_receipt_rollback;
  IF c_schema IS NULL OR c_database IS NULL OR c_host IS NULL
     OR SUBSTR(c_schema,1,1)='<' OR SUBSTR(c_database,1,1)='<' OR SUBSTR(c_host,1,1)='<'
     OR NVL(SYS_CONTEXT('USERENV','SESSION_USER'),'<NULL>')<>UPPER(c_schema)
     OR NVL(SYS_CONTEXT('USERENV','CURRENT_SCHEMA'),'<NULL>')<>UPPER(c_schema)
     OR NVL(SYS_CONTEXT('USERENV','DB_UNIQUE_NAME'),'<NULL>')<>UPPER(c_database)
     OR LOWER(NVL(SYS_CONTEXT('USERENV','SERVER_HOST'),'<NULL>'))<>LOWER(c_host) THEN
    RAISE_APPLICATION_ERROR(-20341,'Replace and verify the isolated target identity before rollback');
  END IF;
  LOCK TABLE qd_es_schema_version IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_request IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_archive IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.1';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20342,'Expected receipt schema version 1.3.1'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.1-SEL';
  IF v_count>1 THEN RAISE_APPLICATION_ERROR(-20348,'Duplicate selection capability marker'); END IF;
  IF v_count=1 THEN
    SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.1-SEL-RB';
    IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20349,'Prepare selection rollback before receipt rollback'); END IF;
  END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_request WHERE business_state='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20343,'Drain running requests first'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_archive WHERE status='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20344,'Stop and reconcile the running archive first'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_request r WHERE r.business_state IN ('PENDING','READY','UNKNOWN') AND
    (EXISTS (SELECT 1 FROM qd_es_object o WHERE o.request_id=r.request_id AND o.client_object_id LIKE 'orgbulk:%')
     OR EXISTS (SELECT 1 FROM qd_es_batch_page p WHERE p.request_id=r.request_id));
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20345,'Drain or reconcile organization batch requests before rollback'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_triggers t JOIN user_objects o ON o.object_name=t.trigger_name
   WHERE o.object_type='TRIGGER' AND o.status='VALID' AND t.status='ENABLED'
     AND ((t.trigger_name='QDES_BPAGE_OBJ_GUARD' AND t.table_name='QD_ES_OBJECT')
       OR (t.trigger_name='QDES_BPAGE_REQ_GUARD' AND t.table_name='QD_ES_REQUEST'));
  IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20346,'Keep receipt archive guards enabled during rollback'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.1-RB';
  IF v_count=1 THEN RETURN; END IF;
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20347,'Duplicate receipt rollback marker'); END IF;
  INSERT INTO qd_es_schema_version(version_no,applied_at,description)
  VALUES ('1.3.1-RB',v_now,'Receipt rollback marker recorded; writer must honor marker; evidence retained');
EXCEPTION WHEN OTHERS THEN ROLLBACK TO qd_batch_receipt_rollback; RAISE;
END;
/
-- Review before explicitly committing; no COMMIT is issued by this script.
SELECT version_no,applied_at,description FROM qd_es_schema_version WHERE version_no IN ('1.3.1','1.3.1-RB');
