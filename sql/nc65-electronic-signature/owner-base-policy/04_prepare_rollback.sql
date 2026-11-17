-- Stop the NC services and drain requests before returning to the 1.2.0 application.
-- Preserve owner policy rows, request objects and all logs. Verify the output, then commit explicitly.
DECLARE
  v_count NUMBER;
  v_now CHAR(19) := TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS');
BEGIN
  SAVEPOINT qd_owner_rollback;
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'QDNC'
     OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'QDNC'
     OR SYS_CONTEXT('USERENV','DB_UNIQUE_NAME') <> 'DB'
     OR LOWER(SYS_CONTEXT('USERENV','SERVER_HOST')) <> 'ncoa-db' THEN
    RAISE_APPLICATION_ERROR(-20230, 'Expected verified QDNC test application schema');
  END IF;
  LOCK TABLE qd_es_schema_version IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.0';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20231, 'Expected version 1.3.0'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.0-RB';
  IF v_count=1 THEN RETURN; END IF;
  LOCK TABLE qd_es_request IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_request WHERE business_state='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20232, 'Drain running requests first'); END IF;
  LOCK TABLE qd_es_archive IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM user_triggers t JOIN user_objects o ON o.object_name=t.trigger_name
   WHERE o.object_type='TRIGGER' AND o.status='VALID' AND t.status='ENABLED'
     AND ((t.trigger_name='QDES_OWNER_REQ_RB_GUARD' AND t.table_name='QD_ES_REQUEST')
       OR (t.trigger_name='QDES_OWNER_OBJ_RB_GUARD' AND t.table_name='QD_ES_OBJECT')
       OR (t.trigger_name='QDES_OWNER_SIGN_RB_GUARD' AND t.table_name='QD_ES_SIGNLOG')
       OR (t.trigger_name='QDES_OWNER_AUDIT_RB_GUARD' AND t.table_name='QD_ES_AUDIT'));
  IF v_count<>4 THEN RAISE_APPLICATION_ERROR(-20234, 'Rollback evidence guards must be valid and enabled'); END IF;
  UPDATE qd_es_request SET business_state='CANCELLED',
         signature_state=CASE WHEN signature_state='PENDING' THEN 'CANCELLED' ELSE signature_state END,
         finished_at=v_now, error_code='OWNER_POLICY_ROLLBACK'
   WHERE policy_mode='OWNER_V1' AND business_state IN ('PENDING','READY');
  UPDATE qd_es_locator l SET request_closed='Y'
   WHERE EXISTS (SELECT 1 FROM qd_es_request r WHERE r.request_id=l.request_id
                  AND r.error_code='OWNER_POLICY_ROLLBACK' AND r.business_state='CANCELLED');
  UPDATE qd_es_bd_control SET master_enabled='N', control_version=control_version+1, modified_at=v_now
   WHERE master_enabled='Y' AND control_version<9223372036854775807;
  SELECT COUNT(*) INTO v_count FROM qd_es_bd_control WHERE master_enabled='Y';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20233, 'A group control cannot advance its version'); END IF;
  -- Retire routes added for 1.3 without deleting references or owner-policy evidence.
  UPDATE qd_es_node SET validated='N'
   WHERE (scope_id IN ('QD-ES-B48245BDDB687314@10140MAO','QD-ES-2DAE0B30BE8A146B@10140MAO')
            AND node_code='10140MAO' AND button_code IN ('FiSave','PuSave'))
      OR (group_scope_id='QD-ES-755F2D0C43B0646E' AND button_code='Assign'
            AND adapter_class='nc.itf.uapbd.qd.esign.IMaterialAssignmentService');
  UPDATE qd_es_node SET operation_types='INSERT'
   WHERE scope_id='QD-ES-A7FBCA61C6991D48' AND group_scope_id=scope_id
     AND node_code='10140MAO' AND button_code='Save' AND operation_types='INSERT,UPDATE'
     AND adapter_class='nc.itf.uapbd.qd.esign.IMaterialOperationAdapter';
  INSERT INTO qd_es_schema_version(version_no,applied_at,description)
  VALUES ('1.3.0-RB',v_now,'Owner policy rollback prepared; group base controls disabled');
EXCEPTION WHEN OTHERS THEN ROLLBACK TO qd_owner_rollback; RAISE;
END;
/
SELECT pk_group,master_enabled,control_version FROM qd_es_bd_control ORDER BY pk_group;
SELECT COUNT(*) retained_organization_policies FROM qd_es_bd_org_policy;
