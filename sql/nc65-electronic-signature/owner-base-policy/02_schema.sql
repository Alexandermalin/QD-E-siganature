-- Oracle DDL commits implicitly. Run only after precheck and stopping the NC services.
-- Existing columns are validated before reuse, so a partially completed run can be resumed.
DECLARE
  v_count NUMBER;
  v_default VARCHAR2(4000);
  PROCEDURE ensure_column(p_table VARCHAR2, p_column VARCHAR2, p_type VARCHAR2,
                          p_length NUMBER, p_definition VARCHAR2) IS
    v_type user_tab_columns.data_type%TYPE;
    v_length user_tab_columns.data_length%TYPE;
    v_precision user_tab_columns.data_precision%TYPE;
    v_nullable user_tab_columns.nullable%TYPE;
  BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns
     WHERE table_name=p_table AND column_name=p_column;
    IF v_count=0 THEN
      EXECUTE IMMEDIATE 'ALTER TABLE ' || DBMS_ASSERT.SIMPLE_SQL_NAME(p_table)
          || ' ADD (' || DBMS_ASSERT.SIMPLE_SQL_NAME(p_column) || ' ' || p_definition || ')';
    END IF;
    SELECT data_type, data_length, data_precision, nullable INTO v_type, v_length, v_precision, v_nullable
      FROM user_tab_columns WHERE table_name=p_table AND column_name=p_column;
    IF v_type<>p_type OR (p_type='NUMBER' AND NVL(v_precision,0)<>p_length)
       OR (p_type<>'NUMBER' AND v_length<>p_length)
       OR (INSTR(p_definition,'NOT NULL')>0 AND v_nullable<>'N') THEN
      RAISE_APPLICATION_ERROR(-20210, 'Unexpected column: ' || p_table || '.' || p_column);
    END IF;
  END;
  -- 旧版为 NUMBER(4)，快速批改允许多于 9999 个对象；仅允许无损扩容已核实的旧结构。
  PROCEDURE widen_owner_object_count(p_table VARCHAR2) IS
    v_present NUMBER;
    v_type user_tab_columns.data_type%TYPE;
    v_precision user_tab_columns.data_precision%TYPE;
    v_scale user_tab_columns.data_scale%TYPE;
    v_nullable user_tab_columns.nullable%TYPE;
  BEGIN
    SELECT COUNT(*) INTO v_present FROM user_tab_columns
     WHERE table_name=p_table AND column_name='OWNER_OBJECT_COUNT';
    IF v_present=0 THEN RETURN; END IF;
    SELECT data_type,data_precision,data_scale,nullable INTO v_type,v_precision,v_scale,v_nullable
      FROM user_tab_columns WHERE table_name=p_table AND column_name='OWNER_OBJECT_COUNT';
    IF v_type<>'NUMBER' OR NVL(v_scale,-1)<>0 OR v_nullable<>'Y' OR NVL(v_precision,0) NOT IN (4,6) THEN
      RAISE_APPLICATION_ERROR(-20240, 'Unexpected owner object count: ' || p_table);
    END IF;
    IF v_precision=4 THEN
      EXECUTE IMMEDIATE 'ALTER TABLE ' || DBMS_ASSERT.SIMPLE_SQL_NAME(p_table)
          || ' MODIFY (OWNER_OBJECT_COUNT NUMBER(6))';
    END IF;
  END;
  -- 1.2 归档只复制旧字段。回退标记生效后，阻止旧归档删除含 1.3 归属证据的在线行。
  PROCEDURE ensure_rollback_guard(p_name VARCHAR2, p_table VARCHAR2, p_predicate VARCHAR2) IS
    v_existing NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_existing FROM user_triggers
     WHERE trigger_name=p_name AND table_name<>p_table;
    IF v_existing<>0 THEN RAISE_APPLICATION_ERROR(-20229, 'Rollback guard name is already in use'); END IF;
    EXECUTE IMMEDIATE 'CREATE OR REPLACE TRIGGER ' || DBMS_ASSERT.SIMPLE_SQL_NAME(p_name)
      || ' BEFORE DELETE ON ' || DBMS_ASSERT.SIMPLE_SQL_NAME(p_table) || ' FOR EACH ROW '
      || 'DECLARE n NUMBER; BEGIN IF ' || p_predicate || ' THEN '
      || 'SELECT COUNT(*) INTO n FROM qd_es_schema_version WHERE version_no=''1.3.0-RB''; '
      || 'IF n>0 THEN RAISE_APPLICATION_ERROR(-20239,''Owner-policy evidence cannot be archived by 1.2''); '
      || 'END IF; END IF; END;';
  END;
BEGIN
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'QDNC'
     OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'QDNC'
     OR SYS_CONTEXT('USERENV','DB_UNIQUE_NAME') <> 'DB'
     OR LOWER(SYS_CONTEXT('USERENV','SERVER_HOST')) <> 'ncoa-db' THEN
    RAISE_APPLICATION_ERROR(-20211, 'Expected verified QDNC test application schema');
  END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.2.0';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20212, 'Expected version 1.2.0'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.2.0-RB';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20217, 'Version 1.2.0 is rolled back'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.0-RB';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20241, 'Review previous 1.3.0 rollback before upgrading'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_request WHERE business_state='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20213, 'Drain running requests first'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name='QD_ES_BD_ORG_POLICY';
  IF v_count=0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLE qd_es_bd_org_policy (' ||
      'pk_group VARCHAR2(20 BYTE) NOT NULL, pk_org VARCHAR2(20 BYTE) NOT NULL, scope_id VARCHAR2(64 BYTE) NOT NULL,' ||
      'sign_enabled CHAR(1 BYTE) DEFAULT ''N'' NOT NULL, reason_required CHAR(1 BYTE) DEFAULT ''N'' NOT NULL,' ||
      'audit_enabled CHAR(1 BYTE) DEFAULT ''N'' NOT NULL, policy_version NUMBER(19) NOT NULL, modified_at CHAR(19 BYTE) NOT NULL,' ||
      'CONSTRAINT qdes_bd_org_policy_pk PRIMARY KEY (pk_group,pk_org,scope_id),' ||
      'CONSTRAINT qdes_bd_org_policy_scope_fk FOREIGN KEY (scope_id) REFERENCES qd_es_node(scope_id),' ||
      'CONSTRAINT qdes_bd_org_policy_flags_ck CHECK (sign_enabled IN (''Y'',''N'') AND reason_required IN (''Y'',''N'')' ||
      ' AND audit_enabled IN (''Y'',''N'') AND policy_version BETWEEN 1 AND 9223372036854775807))';
  END IF;
  SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='QD_ES_BD_ORG_POLICY'
   AND ((column_name IN ('PK_GROUP','PK_ORG') AND data_type='VARCHAR2' AND data_length=20 AND nullable='N')
     OR (column_name='SCOPE_ID' AND data_type='VARCHAR2' AND data_length=64 AND nullable='N')
     OR (column_name IN ('SIGN_ENABLED','REASON_REQUIRED','AUDIT_ENABLED') AND data_type='CHAR' AND data_length=1 AND nullable='N')
     OR (column_name='POLICY_VERSION' AND data_type='NUMBER' AND data_precision=19 AND nullable='N')
     OR (column_name='MODIFIED_AT' AND data_type='CHAR' AND data_length=19 AND nullable='N'));
  IF v_count<>8 THEN RAISE_APPLICATION_ERROR(-20214, 'Unexpected organization base-policy structure'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='QD_ES_BD_ORG_POLICY';
  IF v_count<>8 THEN RAISE_APPLICATION_ERROR(-20218, 'Unexpected extra organization policy columns'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_cons_columns c JOIN user_constraints k ON k.constraint_name=c.constraint_name
   WHERE k.table_name='QD_ES_BD_ORG_POLICY' AND k.constraint_type='P' AND k.status='ENABLED'
     AND ((c.position=1 AND c.column_name='PK_GROUP') OR (c.position=2 AND c.column_name='PK_ORG')
       OR (c.position=3 AND c.column_name='SCOPE_ID'));
  IF v_count<>3 THEN RAISE_APPLICATION_ERROR(-20215, 'Unexpected organization base-policy primary key'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_constraints k
   JOIN user_constraints r ON r.constraint_name=k.r_constraint_name
   JOIN user_cons_columns c ON c.constraint_name=k.constraint_name
   JOIN user_cons_columns rc ON rc.constraint_name=r.constraint_name AND rc.position=c.position
   WHERE k.table_name='QD_ES_BD_ORG_POLICY' AND k.constraint_name='QDES_BD_ORG_POLICY_SCOPE_FK'
     AND k.constraint_type='R' AND k.status='ENABLED' AND k.validated='VALIDATED'
     AND c.column_name='SCOPE_ID' AND rc.table_name='QD_ES_NODE' AND rc.column_name='SCOPE_ID';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20219, 'Unexpected organization policy scope reference'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_constraints
   WHERE table_name='QD_ES_BD_ORG_POLICY' AND constraint_name='QDES_BD_ORG_POLICY_FLAGS_CK'
     AND constraint_type='C' AND status='ENABLED' AND validated='VALIDATED';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20226, 'Missing organization policy flag constraint'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name='QD_ES_BD_OWNER_GUARD';
  IF v_count=0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLE qd_es_bd_owner_guard (' ||
      'pk_group VARCHAR2(20 BYTE) NOT NULL, pk_owner_org VARCHAR2(20 BYTE) NOT NULL,' ||
      'CONSTRAINT qdes_bd_owner_guard_pk PRIMARY KEY (pk_group,pk_owner_org))';
  END IF;
  SELECT COUNT(*) INTO v_count FROM user_cons_columns c JOIN user_constraints k ON k.constraint_name=c.constraint_name
   WHERE k.table_name='QD_ES_BD_OWNER_GUARD' AND k.constraint_type='P' AND k.status='ENABLED'
     AND ((c.position=1 AND c.column_name='PK_GROUP') OR (c.position=2 AND c.column_name='PK_OWNER_ORG'));
  IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20216, 'Unexpected owner guard primary key'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='QD_ES_BD_OWNER_GUARD'
   AND column_name IN ('PK_GROUP','PK_OWNER_ORG') AND data_type='VARCHAR2' AND data_length=20 AND nullable='N';
  IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20227, 'Unexpected owner guard columns'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='QD_ES_BD_OWNER_GUARD';
  IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20228, 'Unexpected extra owner guard columns'); END IF;
  FOR t IN (SELECT table_name FROM user_tables
             WHERE table_name IN ('QD_ES_REQUEST','QD_ES_OBJECT','QD_ES_SIGNLOG','QD_ES_AUDIT')
                OR REGEXP_LIKE(table_name,'^QD_ES_(REQUEST|OBJECT|SIGNLOG|AUDIT)_H[0-9]{6}$')) LOOP
    IF REGEXP_LIKE(t.table_name,'^QD_ES_REQUEST(_H[0-9]{6})?$') THEN
      ensure_column(t.table_name,'POLICY_MODE','VARCHAR2',20,'VARCHAR2(20 BYTE) DEFAULT ''LEGACY'' NOT NULL');
      widen_owner_object_count(t.table_name);
      ensure_column(t.table_name,'OWNER_OBJECT_COUNT','NUMBER',6,'NUMBER(6)');
      IF t.table_name='QD_ES_REQUEST' THEN
        SELECT data_default INTO v_default FROM user_tab_columns
         WHERE table_name=t.table_name AND column_name='POLICY_MODE';
        IF v_default IS NULL OR REGEXP_REPLACE(v_default,'[[:space:]]','')<>'''LEGACY''' THEN
          RAISE_APPLICATION_ERROR(-20236, 'Unexpected online request policy-mode default');
        END IF;
      END IF;
    ELSE
      ensure_column(t.table_name,'POLICY_OWNER_ORG','VARCHAR2',20,'VARCHAR2(20 BYTE)');
      ensure_column(t.table_name,'POLICY_OWNER_TYPE','CHAR',1,'CHAR(1 BYTE)');
      ensure_column(t.table_name,'POLICY_SCOPE_ID','VARCHAR2',64,'VARCHAR2(64 BYTE)');
      ensure_column(t.table_name,'TARGET_ORG','VARCHAR2',20,'VARCHAR2(20 BYTE)');
      IF REGEXP_LIKE(t.table_name,'^QD_ES_OBJECT(_H[0-9]{6})?$') THEN
        ensure_column(t.table_name,'POLICY_OWNER_ROOT','VARCHAR2',100,'VARCHAR2(100 BYTE)');
        ensure_column(t.table_name,'DESIRED_OWNER_ORG','VARCHAR2',20,'VARCHAR2(20 BYTE)');
        ensure_column(t.table_name,'OWNER_POLICY_VERSION','NUMBER',19,'NUMBER(19)');
        ensure_column(t.table_name,'OWNER_CONTROL_VERSION','NUMBER',19,'NUMBER(19)');
        ensure_column(t.table_name,'OWNER_SIGN_REQUIRED','CHAR',1,'CHAR(1 BYTE)');
        ensure_column(t.table_name,'OWNER_REASON_REQUIRED','CHAR',1,'CHAR(1 BYTE)');
        ensure_column(t.table_name,'OWNER_AUDIT_ENABLED','CHAR',1,'CHAR(1 BYTE)');
        ensure_column(t.table_name,'UNIT_STATE','VARCHAR2',20,'VARCHAR2(20 BYTE)');
        ensure_column(t.table_name,'UNIT_ERROR_CODE','VARCHAR2',40,'VARCHAR2(40 BYTE)');
        ensure_column(t.table_name,'UNIT_FINISHED_AT','CHAR',19,'CHAR(19 BYTE)');
      END IF;
    END IF;
  END LOOP;
  ensure_rollback_guard('QDES_OWNER_REQ_RB_GUARD','QD_ES_REQUEST',':OLD.policy_mode=''OWNER_V1''');
  ensure_rollback_guard('QDES_OWNER_OBJ_RB_GUARD','QD_ES_OBJECT',':OLD.policy_owner_org IS NOT NULL');
  ensure_rollback_guard('QDES_OWNER_SIGN_RB_GUARD','QD_ES_SIGNLOG',':OLD.policy_owner_org IS NOT NULL');
  ensure_rollback_guard('QDES_OWNER_AUDIT_RB_GUARD','QD_ES_AUDIT',':OLD.policy_owner_org IS NOT NULL');
END;
/
