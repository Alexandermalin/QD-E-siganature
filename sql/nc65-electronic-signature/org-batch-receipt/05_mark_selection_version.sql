-- Candidate DML only. Stop every NC and archive process and disable automatic commit first.
-- Replace the identity placeholders with the same isolated target verified for 02_schema.sql.
-- All readers and writers must understand W/V prepared-selection records before this marker is set.
DECLARE
  c_schema CONSTANT VARCHAR2(128) := '<REPLACE_ISOLATED_SCHEMA>';
  c_database CONSTANT VARCHAR2(128) := '<REPLACE_ISOLATED_DB_UNIQUE_NAME>';
  c_host CONSTANT VARCHAR2(128) := '<REPLACE_ISOLATED_SERVER_HOST>';
  v_count NUMBER;
  v_applied NUMBER;
  v_now CHAR(19) := TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS');
BEGIN
  SAVEPOINT qd_batch_selection_upgrade;
  IF c_schema IS NULL OR c_database IS NULL OR c_host IS NULL
     OR SUBSTR(c_schema,1,1)='<' OR SUBSTR(c_database,1,1)='<' OR SUBSTR(c_host,1,1)='<'
     OR NVL(SYS_CONTEXT('USERENV','SESSION_USER'),'<NULL>')<>UPPER(c_schema)
     OR NVL(SYS_CONTEXT('USERENV','CURRENT_SCHEMA'),'<NULL>')<>UPPER(c_schema)
     OR NVL(SYS_CONTEXT('USERENV','DB_UNIQUE_NAME'),'<NULL>')<>UPPER(c_database)
     OR LOWER(NVL(SYS_CONTEXT('USERENV','SERVER_HOST'),'<NULL>'))<>LOWER(c_host) THEN
    RAISE_APPLICATION_ERROR(-20351,'Replace and verify the isolated target identity before marking selection');
  END IF;
  LOCK TABLE qd_es_schema_version IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_request IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_archive IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.0';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20352,'Expected owner-policy version 1.3.0'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.1';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20353,'Expected receipt version 1.3.1'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version
    WHERE version_no IN ('1.3.0-RB','1.3.1-RB','1.3.1-SEL-RB');
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20354,'Review rollback markers before selection upgrade'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_request WHERE business_state='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20355,'Drain running requests first'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_request r WHERE r.business_state IN ('PENDING','READY','UNKNOWN') AND
    (EXISTS (SELECT 1 FROM qd_es_object o WHERE o.request_id=r.request_id AND o.client_object_id LIKE 'orgbulk:%')
     OR EXISTS (SELECT 1 FROM qd_es_batch_page p WHERE p.request_id=r.request_id));
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20356,'Drain or reconcile organization batch requests first'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_archive WHERE status='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20357,'Stop and reconcile the running archive first'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name='QD_ES_BATCH_PAGE';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20358,'Receipt table is missing'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_archive a WHERE NOT EXISTS
    (SELECT 1 FROM user_tables t WHERE t.table_name='QD_ES_BATCH_PAGE_H'||TRIM(a.archive_month));
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20359,'A registered receipt history table is missing'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_tables t WHERE REGEXP_LIKE(t.table_name,'^QD_ES_BATCH_PAGE_H[0-9]{6}$')
    AND NOT EXISTS (SELECT 1 FROM qd_es_archive a WHERE TRIM(a.archive_month)=SUBSTR(t.table_name,-6));
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20360,'Review unregistered receipt history'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_triggers t JOIN user_objects o ON o.object_name=t.trigger_name
   WHERE o.object_type='TRIGGER' AND o.status='VALID' AND t.status='ENABLED'
     AND ((t.trigger_name='QDES_BPAGE_OBJ_GUARD' AND t.table_name='QD_ES_OBJECT')
       OR (t.trigger_name='QDES_BPAGE_REQ_GUARD' AND t.table_name='QD_ES_REQUEST'));
  IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20364,'Receipt archive guards are missing or invalid'); END IF;
  SELECT COUNT(*) INTO v_applied FROM qd_es_schema_version WHERE version_no='1.3.1-SEL';
  IF v_applied>1 THEN RAISE_APPLICATION_ERROR(-20361,'Duplicate selection capability marker'); END IF;
  IF v_applied=1 THEN RETURN; END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_batch_page WHERE row_kind IN ('W','V');
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20362,'Review existing prepared-selection records'); END IF;
  FOR item IN (SELECT table_name FROM user_tables WHERE REGEXP_LIKE(table_name,'^QD_ES_BATCH_PAGE_H[0-9]{6}$')) LOOP
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM '||DBMS_ASSERT.SIMPLE_SQL_NAME(item.table_name)
      ||' WHERE ROW_KIND IN (''W'',''V'')' INTO v_count;
    IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20363,'Review existing prepared-selection history'); END IF;
  END LOOP;
  INSERT INTO qd_es_schema_version(version_no,applied_at,description)
  VALUES ('1.3.1-SEL',v_now,'Prepared page-selection records supported; organization action remains disabled');
EXCEPTION WHEN OTHERS THEN ROLLBACK TO qd_batch_selection_upgrade; RAISE;
END;
/
-- Review before explicitly committing; no COMMIT is issued by this script.
SELECT version_no,applied_at,description FROM qd_es_schema_version
 WHERE version_no IN ('1.3.1-SEL','1.3.1-SEL-RB');
