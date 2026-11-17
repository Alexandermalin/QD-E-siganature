-- Prepare for a software rollback in the QDNC maintenance window.
-- Preserve organization and button settings. Legacy group switches start OFF at a later version.
-- Use manual commit and verify the resulting rows before restoring the old JARs.
DECLARE
  v_count NUMBER;
  v_now CHAR(19) := TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP), 'YYYY-MM-DD HH24:MI:SS');
BEGIN
  SAVEPOINT qd_org_control_rollback;
  IF NVL(SYS_CONTEXT('USERENV','SESSION_USER'),'~') <> 'QDNC'
      OR NVL(SYS_CONTEXT('USERENV','CURRENT_SCHEMA'),'~') <> 'QDNC'
      OR NVL(SYS_CONTEXT('USERENV','DB_UNIQUE_NAME'),'~') <> 'DB'
      OR NVL(SYS_CONTEXT('USERENV','SERVICE_NAME'),'~') <> 'DB'
      OR NVL(LOWER(SYS_CONTEXT('USERENV','SERVER_HOST')),'~') <> 'ncoa-db' THEN
    RAISE_APPLICATION_ERROR(-20091, 'Expected the verified QDNC test database');
  END IF;
  LOCK TABLE QDNC.qd_es_schema_version IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM QDNC.qd_es_schema_version WHERE version_no = '1.1.0';
  IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20097, 'Expected applied upgrade 1.1.0'); END IF;
  SELECT COUNT(*) INTO v_count FROM QDNC.qd_es_schema_version WHERE version_no = '1.1.0-RB';
  IF v_count = 1 THEN RETURN; END IF;

  LOCK TABLE QDNC.qd_es_control IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE QDNC.qd_es_request IN SHARE MODE NOWAIT;
  LOCK TABLE QDNC.qd_es_org_control IN SHARE MODE NOWAIT;

  SELECT COUNT(*) INTO v_count FROM QDNC.qd_es_request WHERE business_state = 'RUNNING';
  IF v_count <> 0 THEN RAISE_APPLICATION_ERROR(-20094, 'Reconcile running requests before rollback'); END IF;

  MERGE INTO QDNC.qd_es_control c
  USING (
    SELECT pk_group, MAX(epoch) + 1 AS next_version
      FROM (
        SELECT pk_group, policy_version AS epoch FROM QDNC.qd_es_control
        UNION ALL
        SELECT pk_group, control_version FROM QDNC.qd_es_org_control
        UNION ALL
        SELECT pk_group, control_version FROM QDNC.qd_es_request
      )
     GROUP BY pk_group
  ) v ON (c.pk_group = v.pk_group)
  WHEN MATCHED THEN UPDATE SET c.master_enabled = 'N', c.policy_version = v.next_version, c.modified_at = v_now
  WHEN NOT MATCHED THEN INSERT (pk_group, master_enabled, policy_version, modified_at)
      VALUES (v.pk_group, 'N', v.next_version, v_now);

  SELECT COUNT(*) INTO v_count FROM QDNC.qd_es_control WHERE policy_version > 9223372036854775807;
  IF v_count <> 0 THEN RAISE_APPLICATION_ERROR(-20098, 'Legacy control version exceeds the Java long range'); END IF;

  INSERT INTO QDNC.qd_es_schema_version (version_no, applied_at, description)
  VALUES ('1.1.0-RB', v_now, 'Legacy group controls disabled before software rollback');
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_org_control_rollback;
    RAISE;
END;
/

SELECT pk_group, master_enabled, policy_version FROM QDNC.qd_es_control ORDER BY pk_group;
SELECT version_no, applied_at FROM QDNC.qd_es_schema_version WHERE version_no = '1.1.0-RB';
