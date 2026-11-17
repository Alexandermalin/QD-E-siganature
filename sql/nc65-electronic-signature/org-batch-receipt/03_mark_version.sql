-- Candidate DML. Stop NC and archival services; disable automatic commit.
-- Replace these identity literals with the same isolated target verified for 02_schema.sql.
DECLARE
  c_schema CONSTANT VARCHAR2(128) := '<REPLACE_ISOLATED_SCHEMA>';
  c_database CONSTANT VARCHAR2(128) := '<REPLACE_ISOLATED_DB_UNIQUE_NAME>';
  c_host CONSTANT VARCHAR2(128) := '<REPLACE_ISOLATED_SERVER_HOST>';
  v_count NUMBER;
  v_applied NUMBER;
  v_now CHAR(19) := TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS');
BEGIN
  SAVEPOINT qd_batch_receipt_upgrade;
  IF c_schema IS NULL OR c_database IS NULL OR c_host IS NULL
     OR SUBSTR(c_schema,1,1)='<' OR SUBSTR(c_database,1,1)='<' OR SUBSTR(c_host,1,1)='<'
     OR NVL(SYS_CONTEXT('USERENV','SESSION_USER'),'<NULL>')<>UPPER(c_schema)
     OR NVL(SYS_CONTEXT('USERENV','CURRENT_SCHEMA'),'<NULL>')<>UPPER(c_schema)
     OR NVL(SYS_CONTEXT('USERENV','DB_UNIQUE_NAME'),'<NULL>')<>UPPER(c_database)
     OR LOWER(NVL(SYS_CONTEXT('USERENV','SERVER_HOST'),'<NULL>'))<>LOWER(c_host) THEN
    RAISE_APPLICATION_ERROR(-20321,'Replace and verify the isolated target identity before marking the version');
  END IF;
  LOCK TABLE qd_es_schema_version IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_request IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_archive IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.0';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20322,'Expected owner-policy version 1.3.0'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no IN ('1.3.0-RB','1.3.1-RB');
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20323,'Review rollback markers before receipt upgrade'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_request WHERE business_state='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20324,'Drain running requests first'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_archive WHERE status='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20325,'Stop and reconcile the running archive first'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name='QD_ES_BATCH_PAGE';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20326,'Receipt table is missing'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_archive a WHERE NOT EXISTS
    (SELECT 1 FROM user_tables t WHERE t.table_name='QD_ES_BATCH_PAGE_H'||TRIM(a.archive_month));
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20327,'A prepared archive month is missing its receipt table'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_tables t WHERE REGEXP_LIKE(t.table_name,'^QD_ES_BATCH_PAGE_H[0-9]{6}$')
    AND NOT EXISTS (SELECT 1 FROM qd_es_archive a WHERE TRIM(a.archive_month)=SUBSTR(t.table_name,-6));
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20332,'Review unregistered receipt history before marking the version'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_triggers t JOIN user_objects o ON o.object_name=t.trigger_name
   WHERE o.object_type='TRIGGER' AND o.status='VALID' AND t.status='ENABLED'
     AND ((t.trigger_name='QDES_BPAGE_OBJ_GUARD' AND t.table_name='QD_ES_OBJECT')
       OR (t.trigger_name='QDES_BPAGE_REQ_GUARD' AND t.table_name='QD_ES_REQUEST'));
  IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20328,'Receipt archive guards are missing or invalid'); END IF;
  SELECT COUNT(*) INTO v_applied FROM qd_es_schema_version WHERE version_no='1.3.1';
  IF v_applied>1 THEN RAISE_APPLICATION_ERROR(-20329,'Duplicate receipt schema version'); END IF;
  IF v_applied=1 THEN RETURN; END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_batch_page;
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20330,'Review existing online receipt records before initial marking'); END IF;
  FOR item IN (SELECT table_name FROM user_tables WHERE REGEXP_LIKE(table_name,'^QD_ES_BATCH_PAGE_H[0-9]{6}$')) LOOP
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM '||DBMS_ASSERT.SIMPLE_SQL_NAME(item.table_name) INTO v_count;
    IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20333,'Review existing receipt history before initial marking'); END IF;
  END LOOP;
  SELECT COUNT(*) INTO v_count FROM qd_es_object WHERE client_object_id LIKE 'orgbulk:%';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20331,'Review existing organization batch requests before initial marking'); END IF;
  FOR item IN (SELECT table_name FROM user_tables WHERE REGEXP_LIKE(table_name,'^QD_ES_OBJECT_H[0-9]{6}$')) LOOP
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM '||DBMS_ASSERT.SIMPLE_SQL_NAME(item.table_name)
      ||' WHERE CLIENT_OBJECT_ID LIKE ''orgbulk:%''' INTO v_count;
    IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20334,'Review existing organization batch history before initial marking'); END IF;
  END LOOP;
  INSERT INTO qd_es_schema_version(version_no,applied_at,description)
  VALUES ('1.3.1',v_now,'Organization batch receipt storage prepared; action remains disabled');
EXCEPTION WHEN OTHERS THEN ROLLBACK TO qd_batch_receipt_upgrade; RAISE;
END;
/
-- Review before explicitly committing; no COMMIT is issued by this script.
SELECT version_no,applied_at,description FROM qd_es_schema_version WHERE version_no='1.3.1';
