-- Run after 02_schema.sql, with the NC services stopped and automatic commit disabled.
-- Check the reported affected requests before explicitly committing.
DECLARE
  v_count NUMBER;
  v_now CHAR(19) := TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS');
BEGIN
  SAVEPOINT qd_owner_upgrade;
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'QDNC'
     OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'QDNC'
     OR SYS_CONTEXT('USERENV','DB_UNIQUE_NAME') <> 'DB'
     OR LOWER(SYS_CONTEXT('USERENV','SERVER_HOST')) <> 'ncoa-db' THEN
    RAISE_APPLICATION_ERROR(-20220, 'Expected verified QDNC test application schema');
  END IF;
  LOCK TABLE qd_es_schema_version IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.2.0';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20221, 'Expected version 1.2.0'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.2.0-RB';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20226, 'Version 1.2.0 is rolled back'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.0-RB';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20225, 'Review previous 1.3.0 rollback before upgrading'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.0';
  IF v_count=1 THEN RETURN; END IF;
  LOCK TABLE qd_es_request IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_request WHERE business_state='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20222, 'Drain running requests first'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE group_scope_id=scope_id;
  IF v_count<>77 THEN RAISE_APPLICATION_ERROR(-20223, 'Expected 77 formal base-data actions'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_bd_org_policy;
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20224, 'Review existing organization base policies before upgrade'); END IF;
  SELECT COUNT(*) INTO v_count FROM user_triggers t JOIN user_objects o ON o.object_name=t.trigger_name
   WHERE o.object_type='TRIGGER' AND o.status='VALID' AND t.status='ENABLED'
     AND ((t.trigger_name='QDES_OWNER_REQ_RB_GUARD' AND t.table_name='QD_ES_REQUEST')
       OR (t.trigger_name='QDES_OWNER_OBJ_RB_GUARD' AND t.table_name='QD_ES_OBJECT')
       OR (t.trigger_name='QDES_OWNER_SIGN_RB_GUARD' AND t.table_name='QD_ES_SIGNLOG')
       OR (t.trigger_name='QDES_OWNER_AUDIT_RB_GUARD' AND t.table_name='QD_ES_AUDIT'));
  IF v_count<>4 THEN RAISE_APPLICATION_ERROR(-20227, 'Missing valid enabled rollback evidence guards'); END IF;
  MERGE INTO qd_es_bd_owner_guard d
  USING (SELECT pk_group,pk_org AS pk_owner_org FROM org_orgs
          WHERE pk_group IS NOT NULL AND pk_org IS NOT NULL AND isbusinessunit='Y'
            AND islastversion='Y' AND NVL(dr,0)=0
         UNION SELECT pk_group,pk_group FROM org_group WHERE pk_group IS NOT NULL AND NVL(dr,0)=0) s
     ON (d.pk_group=s.pk_group AND d.pk_owner_org=s.pk_owner_org)
   WHEN NOT MATCHED THEN INSERT (pk_group,pk_owner_org) VALUES (s.pk_group,s.pk_owner_org);
  UPDATE qd_es_request r
     SET business_state='CANCELLED',
         signature_state=CASE WHEN signature_state='PENDING' THEN 'CANCELLED' ELSE signature_state END,
         finished_at=v_now, error_code='OWNER_POLICY_UPGRADE'
   WHERE r.business_state IN ('PENDING','READY')
     AND EXISTS (SELECT 1 FROM qd_es_node n WHERE n.node_code=r.node_code
                  AND n.button_code=r.button_code AND n.group_scope_id IS NOT NULL);
  UPDATE qd_es_locator l SET request_closed='Y'
   WHERE EXISTS (SELECT 1 FROM qd_es_request r WHERE r.request_id=l.request_id
                  AND r.error_code='OWNER_POLICY_UPGRADE' AND r.business_state='CANCELLED');
  INSERT INTO qd_es_schema_version(version_no,applied_at,description)
  VALUES ('1.3.0',v_now,'Base-data policy follows each archive owner; organization defaults off');
EXCEPTION WHEN OTHERS THEN ROLLBACK TO qd_owner_upgrade; RAISE;
END;
/
SELECT COUNT(*) organization_base_policy_count FROM qd_es_bd_org_policy;
SELECT pk_group,master_enabled,control_version FROM qd_es_bd_control ORDER BY pk_group;
SELECT COUNT(*) cancelled_base_requests FROM qd_es_request WHERE error_code='OWNER_POLICY_UPGRADE';
