-- Candidate Oracle 11g DDL. DDL commits implicitly; stop NC and archival services first.
-- Replace all three identity literals only after checking 01_precheck.sql in an isolated environment.
-- The placeholders deliberately make this file refuse execution until the target has been reviewed.
DECLARE
  c_schema CONSTANT VARCHAR2(128) := '<REPLACE_ISOLATED_SCHEMA>';
  c_database CONSTANT VARCHAR2(128) := '<REPLACE_ISOLATED_DB_UNIQUE_NAME>';
  c_host CONSTANT VARCHAR2(128) := '<REPLACE_ISOLATED_SERVER_HOST>';
  v_count NUMBER;

  PROCEDURE check_column(p_table VARCHAR2,p_column VARCHAR2,p_type VARCHAR2,p_length NUMBER,p_nullable CHAR) IS
    v_type user_tab_columns.data_type%TYPE;
    v_length user_tab_columns.data_length%TYPE;
    v_precision user_tab_columns.data_precision%TYPE;
    v_scale user_tab_columns.data_scale%TYPE;
    v_nullable user_tab_columns.nullable%TYPE;
    v_semantics user_tab_columns.char_used%TYPE;
  BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name=p_table AND column_name=p_column;
    IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20301,'Missing receipt column: '||p_table||'.'||p_column); END IF;
    SELECT data_type,data_length,data_precision,data_scale,nullable,char_used
      INTO v_type,v_length,v_precision,v_scale,v_nullable,v_semantics
      FROM user_tab_columns WHERE table_name=p_table AND column_name=p_column;
    IF v_type<>p_type OR (p_type='NUMBER' AND (NVL(v_precision,0)<>p_length OR NVL(v_scale,-1)<>0))
       OR (p_type<>'NUMBER' AND (v_length<>p_length OR NVL(v_semantics,'?')<>'B'))
       OR (p_nullable IS NOT NULL AND v_nullable<>p_nullable) THEN
      RAISE_APPLICATION_ERROR(-20302,'Unexpected receipt column: '||p_table||'.'||p_column);
    END IF;
  END;

  PROCEDURE check_shape(p_table VARCHAR2,p_online BOOLEAN) IS
    v_required CHAR(1);
  BEGIN
    IF p_online THEN v_required:='N'; END IF;
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name=p_table;
    IF v_count<>15 THEN RAISE_APPLICATION_ERROR(-20303,'Unexpected receipt column count: '||p_table); END IF;
    check_column(p_table,'RECORD_ID','VARCHAR2',32,v_required);
    check_column(p_table,'REQUEST_ID','VARCHAR2',32,v_required);
    check_column(p_table,'OBJECT_ID','VARCHAR2',32,v_required);
    check_column(p_table,'ROW_KIND','VARCHAR2',1,v_required);
    check_column(p_table,'ITEM_KEY','VARCHAR2',20,v_required);
    check_column(p_table,'TARGET_ORG','VARCHAR2',20,'Y');
    check_column(p_table,'RESULT_STATE','VARCHAR2',20,'Y');
    check_column(p_table,'FAILURE_STAGE','VARCHAR2',20,'Y');
    check_column(p_table,'PAGE_COUNT','NUMBER',10,'Y');
    check_column(p_table,'EVIDENCE_HASH','CHAR',64,'Y');
    check_column(p_table,'TAB_CODE','VARCHAR2',40,v_required);
    check_column(p_table,'PK_GROUP','VARCHAR2',20,v_required);
    check_column(p_table,'PK_ORG','VARCHAR2',20,v_required);
    check_column(p_table,'EVENT_TIME','CHAR',19,v_required);
    check_column(p_table,'ARCHIVE_MONTH','CHAR',6,v_required);
  END;

  PROCEDURE ensure_key(p_table VARCHAR2,p_name VARCHAR2,p_kind CHAR,p_columns VARCHAR2) IS
    v_columns VARCHAR2(4000);
  BEGIN
    SELECT COUNT(*) INTO v_count FROM user_constraints WHERE constraint_name=p_name;
    IF v_count=0 THEN
      EXECUTE IMMEDIATE 'ALTER TABLE '||DBMS_ASSERT.SIMPLE_SQL_NAME(p_table)||' ADD CONSTRAINT '
        ||DBMS_ASSERT.SIMPLE_SQL_NAME(p_name)||CASE p_kind WHEN 'P' THEN ' PRIMARY KEY (' ELSE ' UNIQUE (' END||p_columns||')';
    END IF;
    SELECT COUNT(*) INTO v_count FROM user_constraints WHERE constraint_name=p_name AND table_name=p_table
      AND constraint_type=p_kind AND status='ENABLED' AND validated='VALIDATED';
    IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20304,'Unexpected receipt constraint: '||p_name); END IF;
    SELECT LISTAGG(column_name,',') WITHIN GROUP (ORDER BY position) INTO v_columns
      FROM user_cons_columns WHERE table_name=p_table AND constraint_name=p_name;
    IF v_columns IS NULL OR v_columns<>p_columns THEN RAISE_APPLICATION_ERROR(-20305,'Unexpected receipt key columns: '||p_name); END IF;
  END;

  PROCEDURE ensure_index(p_table VARCHAR2,p_name VARCHAR2,p_columns VARCHAR2) IS
    v_columns VARCHAR2(4000);
  BEGIN
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name=p_name;
    IF v_count=0 THEN
      EXECUTE IMMEDIATE 'CREATE INDEX '||DBMS_ASSERT.SIMPLE_SQL_NAME(p_name)||' ON '
        ||DBMS_ASSERT.SIMPLE_SQL_NAME(p_table)||' ('||p_columns||')';
    END IF;
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name=p_name AND table_name=p_table
      AND status='VALID' AND uniqueness='NONUNIQUE';
    IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20306,'Unexpected receipt index: '||p_name); END IF;
    SELECT LISTAGG(column_name,',') WITHIN GROUP (ORDER BY column_position) INTO v_columns
      FROM user_ind_columns WHERE table_name=p_table AND index_name=p_name;
    IF v_columns IS NULL OR v_columns<>p_columns THEN RAISE_APPLICATION_ERROR(-20307,'Unexpected receipt index columns: '||p_name); END IF;
  END;

  PROCEDURE ensure_history(p_month VARCHAR2) IS
    v_table VARCHAR2(30) := 'QD_ES_BATCH_PAGE_H'||p_month;
    v_prefix VARCHAR2(20) := 'QDBP'||p_month;
  BEGIN
    IF p_month IS NULL OR NOT REGEXP_LIKE(p_month,'^[0-9]{4}(0[1-9]|1[0-2])$') OR SUBSTR(p_month,1,4)='0000' THEN
      RAISE_APPLICATION_ERROR(-20308,'Invalid approved archive month');
    END IF;
    SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name=v_table;
    IF v_count=0 THEN
      EXECUTE IMMEDIATE 'CREATE TABLE '||DBMS_ASSERT.SIMPLE_SQL_NAME(v_table)||' AS SELECT * FROM QD_ES_BATCH_PAGE WHERE 1=0';
    END IF;
    check_shape(v_table,FALSE);
    ensure_key(v_table,v_prefix||'_PK','P','RECORD_ID');
    ensure_key(v_table,v_prefix||'_UQ','U','REQUEST_ID,OBJECT_ID,ROW_KIND,ITEM_KEY');
    ensure_index(v_table,v_prefix||'_SCOPE_IX','PK_GROUP,PK_ORG,EVENT_TIME');
  END;

  PROCEDURE ensure_guard(p_name VARCHAR2,p_table VARCHAR2,p_body VARCHAR2) IS
    v_body VARCHAR2(32767);
  BEGIN
    SELECT COUNT(*) INTO v_count FROM user_triggers WHERE trigger_name=p_name;
    IF v_count=0 THEN EXECUTE IMMEDIATE 'CREATE TRIGGER '||DBMS_ASSERT.SIMPLE_SQL_NAME(p_name)
      ||' BEFORE DELETE ON '||DBMS_ASSERT.SIMPLE_SQL_NAME(p_table)||' FOR EACH ROW '||p_body; END IF;
    SELECT COUNT(*) INTO v_count FROM user_triggers t JOIN user_objects o ON o.object_name=t.trigger_name
     WHERE t.trigger_name=p_name AND t.table_name=p_table AND t.status='ENABLED' AND o.object_type='TRIGGER' AND o.status='VALID'
       AND t.triggering_event='DELETE' AND t.trigger_type='BEFORE EACH ROW';
    IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20309,'Missing valid receipt archive guard: '||p_name); END IF;
    SELECT trigger_body INTO v_body FROM user_triggers WHERE trigger_name=p_name AND table_name=p_table;
    IF v_body IS NULL OR REGEXP_REPLACE(UPPER(v_body),'[[:space:]]','')
       <>REGEXP_REPLACE(UPPER(p_body),'[[:space:]]','') THEN
      RAISE_APPLICATION_ERROR(-20310,'Unexpected receipt archive guard body: '||p_name);
    END IF;
  END;
BEGIN
  IF c_schema IS NULL OR c_database IS NULL OR c_host IS NULL
     OR SUBSTR(c_schema,1,1)='<' OR SUBSTR(c_database,1,1)='<' OR SUBSTR(c_host,1,1)='<'
     OR NVL(SYS_CONTEXT('USERENV','SESSION_USER'),'<NULL>')<>UPPER(c_schema)
     OR NVL(SYS_CONTEXT('USERENV','CURRENT_SCHEMA'),'<NULL>')<>UPPER(c_schema)
     OR NVL(SYS_CONTEXT('USERENV','DB_UNIQUE_NAME'),'<NULL>')<>UPPER(c_database)
     OR LOWER(NVL(SYS_CONTEXT('USERENV','SERVER_HOST'),'<NULL>'))<>LOWER(c_host) THEN
    RAISE_APPLICATION_ERROR(-20311,'Replace and verify the isolated target identity before running this DDL');
  END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.0';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20312,'Expected owner-policy version 1.3.0'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no IN ('1.3.0-RB','1.3.1-RB');
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20313,'Review rollback markers before receipt upgrade'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_request WHERE business_state='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20314,'Drain running requests and stop NC first'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_archive WHERE status='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20315,'Stop and reconcile the running archive first'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_tables t WHERE REGEXP_LIKE(t.table_name,'^QD_ES_REQUEST_H[0-9]{6}$')
    AND NOT EXISTS (SELECT 1 FROM qd_es_archive a WHERE TRIM(a.archive_month)=SUBSTR(t.table_name,-6));
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20316,'Review unregistered precreated request history first'); END IF;

  SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name='QD_ES_BATCH_PAGE';
  IF v_count=0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLE QD_ES_BATCH_PAGE ('||
      'RECORD_ID VARCHAR2(32 BYTE) NOT NULL,REQUEST_ID VARCHAR2(32 BYTE) NOT NULL,'||
      'OBJECT_ID VARCHAR2(32 BYTE) NOT NULL,ROW_KIND VARCHAR2(1 BYTE) NOT NULL,ITEM_KEY VARCHAR2(20 BYTE) NOT NULL,'||
      'TARGET_ORG VARCHAR2(20 BYTE),RESULT_STATE VARCHAR2(20 BYTE),FAILURE_STAGE VARCHAR2(20 BYTE),'||
      'PAGE_COUNT NUMBER(10),EVIDENCE_HASH CHAR(64 BYTE),TAB_CODE VARCHAR2(40 BYTE) NOT NULL,'||
      'PK_GROUP VARCHAR2(20 BYTE) NOT NULL,PK_ORG VARCHAR2(20 BYTE) NOT NULL,'||
      'EVENT_TIME CHAR(19 BYTE) NOT NULL,ARCHIVE_MONTH CHAR(6 BYTE) NOT NULL)';
  END IF;
  check_shape('QD_ES_BATCH_PAGE',TRUE);
  ensure_key('QD_ES_BATCH_PAGE','QDES_BPAGE_PK','P','RECORD_ID');
  ensure_key('QD_ES_BATCH_PAGE','QDES_BPAGE_UQ','U','REQUEST_ID,OBJECT_ID,ROW_KIND,ITEM_KEY');
  ensure_index('QD_ES_BATCH_PAGE','QDES_BPAGE_MONTH_IX','ARCHIVE_MONTH,RECORD_ID');
  ensure_index('QD_ES_BATCH_PAGE','QDES_BPAGE_SCOPE_IX','PK_GROUP,PK_ORG,EVENT_TIME');
  FOR item IN (SELECT TRIM(archive_month) archive_key FROM qd_es_archive ORDER BY archive_month) LOOP
    ensure_history(item.archive_key);
  END LOOP;

  ensure_guard('QDES_BPAGE_OBJ_GUARD','QD_ES_OBJECT',
    'DECLARE n NUMBER; BEGIN IF :OLD.CLIENT_OBJECT_ID LIKE ''orgbulk:%'' THEN '
    ||'SELECT COUNT(*) INTO n FROM QD_ES_BATCH_PAGE WHERE REQUEST_ID=:OLD.REQUEST_ID AND OBJECT_ID=:OLD.OBJECT_ID; '
    ||'IF n>0 THEN RAISE_APPLICATION_ERROR(-20317,''Online organization receipt must be archived first''); END IF; END IF; END;');
  ensure_guard('QDES_BPAGE_REQ_GUARD','QD_ES_REQUEST',
    'DECLARE n NUMBER; BEGIN IF :OLD.POLICY_MODE=''OWNER_V1'' THEN '
    ||'SELECT COUNT(*) INTO n FROM QD_ES_BATCH_PAGE WHERE REQUEST_ID=:OLD.REQUEST_ID; '
    ||'IF n>0 THEN RAISE_APPLICATION_ERROR(-20318,''Online organization receipt must be archived first''); END IF; END IF; END;');
END;
/
