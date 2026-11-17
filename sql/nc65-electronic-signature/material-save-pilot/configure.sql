-- QDNC isolated pilot only. Bind :pilot_group, :pilot_org and :pilot_enabled (M/Y/N).
-- Use a dedicated transaction-capable Oracle session with auto-commit disabled.
-- M registers only the verified mapping for 990. Set policy and control in the NC page.
-- M and Y require verified class loading, native BYPASS and an approved isolated BU.
-- Inspect the result before committing. This block never commits.
DECLARE
  v_group qd_es_org_control.pk_group%TYPE := :pilot_group;
  v_org qd_es_policy.pk_org%TYPE := :pilot_org;
  v_enable qd_es_policy.enabled%TYPE := :pilot_enabled;
  v_scope CONSTANT qd_es_node.scope_id%TYPE := 'QD-ES-A7FBCA61C6991D48';
  v_node CONSTANT qd_es_node.node_code%TYPE := '10140MAO';
  v_button CONSTANT qd_es_node.button_code%TYPE := 'Save';
  v_service CONSTANT qd_es_node.adapter_class%TYPE := 'nc.itf.uapbd.qd.esign.IMaterialOperationAdapter';
  v_now qd_es_policy.modified_at%TYPE := TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS');
  v_count NUMBER;
BEGIN
  SAVEPOINT qd_material_pilot;
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'QDNC'
      OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'QDNC' THEN
    RAISE_APPLICATION_ERROR(-20061, 'Expected QDNC test application schema');
  END IF;
  IF v_group IS NULL OR v_org IS NULL OR v_enable IS NULL OR v_enable NOT IN ('M','Y','N') THEN
    RAISE_APPLICATION_ERROR(-20062, 'Bind an approved test group, BU and M/Y/N mode');
  END IF;

  LOCK TABLE qd_es_schema_version IN SHARE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version
   WHERE version_no='1.2.0'
     AND NOT EXISTS (SELECT 1 FROM qd_es_schema_version WHERE version_no='1.2.0-RB');
  IF v_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20069, 'Use the 1.2 group policy pilot; the legacy BU configuration is retired');
  END IF;

  LOCK TABLE qd_es_org_control IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_policy IN SHARE ROW EXCLUSIVE MODE NOWAIT;

  IF v_enable IN ('M','Y') THEN
    -- The site baseline already identified this BU as feeding the WMS queue.
    IF v_org = '0001A910000000001O7X' THEN
      RAISE_APPLICATION_ERROR(-20063, 'The default WMS-connected BU is excluded from this pilot');
    END IF;
    SELECT COUNT(*) INTO v_count FROM org_orgs
      WHERE pk_group = v_group AND pk_org = v_org AND NVL(dr,0) = 0
        AND isbusinessunit = 'Y' AND islastversion = 'Y' AND enablestate = 2;
    IF v_count <> 1 THEN
      RAISE_APPLICATION_ERROR(-20064, 'Expected exactly one active business unit in the selected group');
    END IF;
    IF v_enable = 'M' THEN
      IF v_group <> '0001A9100000000005MH' OR v_org <> '0001A9100000003UDS6R' THEN
        RAISE_APPLICATION_ERROR(-20067, 'Mapping-only registration is limited to QDNC business unit 990');
      END IF;
      SELECT COUNT(*) INTO v_count FROM qd_es_org_control
        WHERE pk_group = v_group AND pk_org = v_org AND master_enabled = 'Y';
      IF v_count <> 0 THEN
        RAISE_APPLICATION_ERROR(-20068, 'Close the pilot organization control before registering the mapping');
      END IF;
    END IF;
    SELECT COUNT(*) INTO v_count FROM qd_es_policy
      WHERE enabled = 'Y' AND (sign_enabled = 'Y' OR audit_enabled = 'Y')
        AND ((pk_group = v_group AND pk_org = v_org AND (v_enable = 'M' OR node_code <> v_node OR button_code <> v_button))
             OR (node_code = v_node AND button_code = v_button AND (pk_group <> v_group OR pk_org <> v_org)));
    IF v_count <> 0 THEN
      RAISE_APPLICATION_ERROR(-20065, 'Another policy could become active or share the pilot mapping');
    END IF;

    UPDATE qd_es_node
       SET node_code = v_node, button_code = v_button, adapter_class = v_service,
           mapping_state = 'PILOT', operation_types = 'INSERT', object_type = 'BD_MATERIAL', validated = 'Y'
     WHERE scope_id = v_scope
       AND ((mapping_state = 'UNMAPPED' AND validated = 'N' AND node_code IS NULL AND button_code IS NULL
             AND adapter_class IS NULL AND operation_types IS NULL AND object_type IS NULL)
            OR (mapping_state = 'PILOT' AND node_code = v_node AND button_code = v_button
                AND adapter_class = v_service AND operation_types = 'INSERT' AND object_type = 'BD_MATERIAL'));
    IF SQL%ROWCOUNT <> 1 THEN
      RAISE_APPLICATION_ERROR(-20066, 'Pilot scope changed or is not present');
    END IF;
    IF v_enable = 'M' THEN
      RETURN;
    END IF;

    UPDATE qd_es_policy SET sign_enabled = 'Y', reason_required = 'Y', audit_enabled = 'Y',
       enabled = 'Y', validated = 'Y', policy_version = policy_version + 1, modified_at = v_now
     WHERE pk_group = v_group AND pk_org = v_org AND node_code = v_node AND button_code = v_button;
    IF SQL%ROWCOUNT = 0 THEN
      INSERT INTO qd_es_policy (policy_id,pk_group,pk_org,node_code,button_code,sign_enabled,
          reason_required,audit_enabled,enabled,validated,policy_version,modified_at)
      VALUES (LOWER(RAWTOHEX(SYS_GUID())),v_group,v_org,v_node,v_button,'Y','Y','Y','Y','Y',1,v_now);
    END IF;
    UPDATE qd_es_org_control SET master_enabled = 'Y', control_version = control_version + 1, modified_at = v_now
     WHERE pk_group = v_group AND pk_org = v_org AND master_enabled = 'N';
    IF SQL%ROWCOUNT = 0 THEN
      INSERT INTO qd_es_org_control (pk_group,pk_org,master_enabled,control_version,modified_at)
      SELECT v_group,v_org,'Y',1,v_now FROM dual
       WHERE NOT EXISTS (SELECT 1 FROM qd_es_org_control WHERE pk_group = v_group AND pk_org = v_org);
    END IF;
  ELSE
    -- Disabling the scoped policy invalidates its pending requests without affecting other policies.
    UPDATE qd_es_policy SET enabled = 'N', validated = 'N', policy_version = policy_version + 1, modified_at = v_now
     WHERE pk_group = v_group AND pk_org = v_org AND node_code = v_node AND button_code = v_button
       AND enabled = 'Y' AND EXISTS (SELECT 1 FROM qd_es_node WHERE scope_id = v_scope AND mapping_state = 'PILOT'
         AND node_code = v_node AND button_code = v_button AND adapter_class = v_service);
    UPDATE qd_es_node SET validated = 'N'
     WHERE scope_id = v_scope AND mapping_state = 'PILOT' AND node_code = v_node AND button_code = v_button
       AND adapter_class = v_service AND EXISTS (SELECT 1 FROM qd_es_policy
         WHERE pk_group = v_group AND pk_org = v_org AND node_code = v_node AND button_code = v_button)
       AND NOT EXISTS (SELECT 1 FROM qd_es_policy WHERE node_code = v_node AND button_code = v_button AND enabled = 'Y');
  END IF;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_material_pilot;
    RAISE;
END;
/

SELECT scope_id,node_code,button_code,operation_types,object_type,mapping_state,validated
  FROM qd_es_node WHERE scope_id = 'QD-ES-A7FBCA61C6991D48';
SELECT pk_org,master_enabled,control_version FROM qd_es_org_control
 WHERE pk_group = :pilot_group AND pk_org = :pilot_org;
SELECT pk_org,sign_enabled,reason_required,audit_enabled,enabled,validated,policy_version FROM qd_es_policy
 WHERE pk_group = :pilot_group AND pk_org = :pilot_org AND node_code = '10140MAO' AND button_code = 'Save';
