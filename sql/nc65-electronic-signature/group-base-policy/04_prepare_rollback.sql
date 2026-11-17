-- Prepare an application rollback to 1.1.0 in a stopped-service maintenance window.
-- Preserve the new tables, mappings, all logs and snapshots. Disable only base-data policies.
-- Disable autocommit. Verify results before restoring the previous application and committing.
DECLARE
  v_count NUMBER;
  v_now CHAR(19) := TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS');
BEGIN
  SAVEPOINT qd_group_policy_rollback;
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'QDNC'
      OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'QDNC' THEN
    RAISE_APPLICATION_ERROR(-20120, 'Expected the verified QDNC test application schema');
  END IF;
  LOCK TABLE qd_es_schema_version IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.2.0';
  IF v_count <> 1 THEN
    RAISE_APPLICATION_ERROR(-20126, 'Expected applied upgrade 1.2.0');
  END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.2.0-RB';
  IF v_count = 1 THEN
    RETURN;
  END IF;

  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_policy IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_request IN SHARE MODE NOWAIT;
  LOCK TABLE qd_es_bd_control IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_request WHERE business_state='RUNNING';
  IF v_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20123, 'Reconcile running requests before rollback');
  END IF;

  UPDATE qd_es_bd_control SET master_enabled='N',control_version=control_version+1,modified_at=v_now
   WHERE master_enabled='Y';
  UPDATE qd_es_policy p SET enabled='N',policy_version=policy_version+1,modified_at=v_now
   WHERE p.enabled='Y' AND EXISTS (
      SELECT 1 FROM qd_es_node n WHERE n.group_scope_id IS NOT NULL
        AND n.node_code=p.node_code AND n.button_code=p.button_code
   );
  -- Version 1.1 does not distinguish group aliases from its own strategy catalog.
  UPDATE qd_es_node SET validated='N'
   WHERE group_scope_id IS NOT NULL AND group_scope_id<>scope_id AND validated='Y';
  SELECT COUNT(*) INTO v_count FROM qd_es_policy WHERE policy_version>9223372036854775807;
  IF v_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20127, 'Policy version exceeds the Java long range');
  END IF;
  INSERT INTO qd_es_schema_version (version_no,applied_at,description)
  VALUES ('1.2.0-RB',v_now,'Base-data policies disabled before application rollback');
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_group_policy_rollback;
    RAISE;
END;
/

SELECT COUNT(*) active_group_controls FROM qd_es_bd_control WHERE master_enabled='Y';
