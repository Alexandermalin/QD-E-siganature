-- For an existing installation of the earlier material-image-cleanup schema only.
-- Stop the scheduler, drain requests and back up the schema and evidence first.
-- Oracle DDL commits implicitly, including inside PL/SQL; this cannot be rolled back as one transaction.
DECLARE
  v_count NUMBER;
  PROCEDURE ensure_column(p_name VARCHAR2,p_type VARCHAR2,p_length NUMBER,p_ddl VARCHAR2) IS
    v_exists NUMBER;v_valid NUMBER;
  BEGIN
    SELECT COUNT(*),COUNT(CASE WHEN data_type=p_type AND data_length=p_length THEN 1 END)
      INTO v_exists,v_valid FROM user_tab_columns WHERE table_name='QD_ES_IMG_CLEANUP' AND column_name=p_name;
    IF v_exists=0 THEN EXECUTE IMMEDIATE 'ALTER TABLE qd_es_img_cleanup ADD ('||p_ddl||')';
    ELSIF v_valid<>1 THEN RAISE_APPLICATION_ERROR(-20277,'Incompatible cleanup column '||p_name); END IF;
  END;
BEGIN
  SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name='QD_ES_IMG_CLEANUP';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20278,'Use 01_schema.sql for a new installation'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_img_cleanup WHERE status NOT IN ('PENDING','RUNNING','CLEANED','NEEDS_REVIEW');
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20279,'Unexpected cleanup status; inspect before upgrade'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_img_cleanup WHERE status='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20281,'Cleanup is still running; drain it before upgrade'); END IF;
  SELECT COUNT(*) INTO v_count FROM (SELECT request_id,object_pk FROM qd_es_img_cleanup
    GROUP BY request_id,object_pk HAVING COUNT(*)>1);
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20280,'Duplicate request/material cleanup tasks'); END IF;
  -- Old route backups did not capture node_code. Populated backups cannot be reconstructed
  -- safely; reconcile them with the pre-install backup before running this upgrade.
  SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name='QD_ES_MATDEL_ROUTE_BAK';
  IF v_count=1 THEN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='QD_ES_MATDEL_ROUTE_BAK'
      AND column_name='NODE_CODE';
    IF v_count=0 THEN
      EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM qd_es_matdel_route_bak' INTO v_count;
      IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20284,'Existing route backup lacks node_code; reconcile original values first'); END IF;
      EXECUTE IMMEDIATE 'ALTER TABLE qd_es_matdel_route_bak ADD (node_code VARCHAR2(100))';
    ELSE
      SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='QD_ES_MATDEL_ROUTE_BAK'
        AND column_name='NODE_CODE' AND data_type='VARCHAR2' AND data_length>=100;
      IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20285,'Incompatible route backup node_code'); END IF;
    END IF;
  END IF;
  ensure_column('LEASE_TOKEN','VARCHAR2',32,'lease_token VARCHAR2(32)');
  ensure_column('LEASE_UNTIL','CHAR',19,'lease_until CHAR(19)');
  SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='QD_ES_IMG_CLEANUP'
    AND column_name='FOLDER_PK' AND nullable='N';
  IF v_count=1 THEN EXECUTE IMMEDIATE 'ALTER TABLE qd_es_img_cleanup MODIFY (folder_pk NULL)'; END IF;
  SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='QD_ES_IMG_CLEANUP'
    AND column_name='FOLDER_VERSION' AND nullable='N';
  IF v_count=1 THEN EXECUTE IMMEDIATE 'ALTER TABLE qd_es_img_cleanup MODIFY (folder_version NULL)'; END IF;
  SELECT COUNT(*) INTO v_count FROM user_constraints
    WHERE table_name='QD_ES_IMG_CLEANUP' AND constraint_name='QDES_IMGCLEANUP_STATUS_CK';
  IF v_count=1 THEN EXECUTE IMMEDIATE 'ALTER TABLE qd_es_img_cleanup DROP CONSTRAINT qdes_imgcleanup_status_ck'; END IF;
  EXECUTE IMMEDIATE 'ALTER TABLE qd_es_img_cleanup ADD CONSTRAINT qdes_imgcleanup_status_ck '
    ||'CHECK (status IN (''PENDING'',''RUNNING'',''CLEANED'',''NEEDS_REVIEW''))';
  SELECT COUNT(*) INTO v_count FROM user_constraints
    WHERE table_name='QD_ES_IMG_CLEANUP' AND constraint_name='QDES_IMGCLEANUP_IDENT_CK';
  IF v_count=0 THEN EXECUTE IMMEDIATE 'ALTER TABLE qd_es_img_cleanup ADD CONSTRAINT qdes_imgcleanup_ident_ck '
    ||'CHECK (status=''NEEDS_REVIEW'' OR (folder_pk IS NOT NULL AND folder_version IS NOT NULL))'; END IF;
  SELECT COUNT(*) INTO v_count FROM user_constraints
    WHERE table_name='QD_ES_IMG_CLEANUP' AND constraint_name='QDES_IMGCLEANUP_LEASE_CK';
  IF v_count=0 THEN EXECUTE IMMEDIATE 'ALTER TABLE qd_es_img_cleanup ADD CONSTRAINT qdes_imgcleanup_lease_ck '
    ||'CHECK ((status=''RUNNING'' AND lease_token IS NOT NULL AND lease_until IS NOT NULL) OR '
    ||'(status<>''RUNNING'' AND lease_token IS NULL AND lease_until IS NULL))'; END IF;
  SELECT COUNT(*) INTO v_count FROM user_constraints
    WHERE table_name='QD_ES_IMG_CLEANUP' AND constraint_name='QDES_IMGCLEANUP_OBJ_UQ';
  IF v_count=1 THEN EXECUTE IMMEDIATE 'ALTER TABLE qd_es_img_cleanup DROP CONSTRAINT qdes_imgcleanup_obj_uq'; END IF;
  EXECUTE IMMEDIATE 'ALTER TABLE qd_es_img_cleanup ADD CONSTRAINT qdes_imgcleanup_obj_uq UNIQUE(request_id,object_pk)';
  SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name='QD_ES_MATDEL_ROUTE_BAK';
  IF v_count=0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLE qd_es_matdel_route_bak ('
      ||'scope_id VARCHAR2(64) NOT NULL,existed CHAR(1) NOT NULL,node_code VARCHAR2(100),button_code VARCHAR2(100),'
      ||'button_name VARCHAR2(200 CHAR),adapter_class VARCHAR2(300),operation_types VARCHAR2(200),'
      ||'object_type VARCHAR2(100),validated CHAR(1),mapping_state VARCHAR2(30),'
      ||'CONSTRAINT qdes_matdel_route_bak_pk PRIMARY KEY(scope_id),'
      ||'CONSTRAINT qdes_matdel_route_bak_ck CHECK(existed IN (''Y'',''N'')))';
  END IF;
  SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name='QD_ES_IMG_CLEANUP_ITEM';
  IF v_count=0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLE qd_es_img_cleanup_item ('
      ||'cleanup_id VARCHAR2(32) NOT NULL,file_pk VARCHAR2(100) NOT NULL,file_path VARCHAR2(2000) NOT NULL,'
      ||'is_folder VARCHAR2(10),is_doc VARCHAR2(10),storage_pk VARCHAR2(2000),modify_time VARCHAR2(100),'
      ||'last_time VARCHAR2(100),file_length VARCHAR2(100),'
      ||'CONSTRAINT qdes_imgcleanup_item_pk PRIMARY KEY(cleanup_id,file_pk),'
      ||'CONSTRAINT qdes_imgcleanup_item_fk FOREIGN KEY(cleanup_id) REFERENCES qd_es_img_cleanup(cleanup_id))';
  ELSE
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='QD_ES_IMG_CLEANUP_ITEM'
      AND column_name IN ('FILE_PATH','STORAGE_PK') AND data_type='VARCHAR2';
    IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20282,'Incompatible cleanup item columns'); END IF;
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='QD_ES_IMG_CLEANUP_ITEM'
      AND column_name='FILE_PATH' AND data_length<2000;
    IF v_count=1 THEN EXECUTE IMMEDIATE 'ALTER TABLE qd_es_img_cleanup_item MODIFY (file_path VARCHAR2(2000))'; END IF;
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name='QD_ES_IMG_CLEANUP_ITEM'
      AND column_name='STORAGE_PK' AND data_length<2000;
    IF v_count=1 THEN EXECUTE IMMEDIATE 'ALTER TABLE qd_es_img_cleanup_item MODIFY (storage_pk VARCHAR2(2000))'; END IF;
  END IF;
END;
/
