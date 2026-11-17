-- Apply after the DDL. NC must be stopped and there must be no RUNNING requests.
-- The formal base-data scope IDs below come from operation-scope.csv, category 基础数据.
-- Disable autocommit. Verify results and commit explicitly after review.
DECLARE
  v_count NUMBER;
  v_now CHAR(19) := TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS');
BEGIN
  SAVEPOINT qd_group_policy_upgrade;
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'QDNC'
      OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'QDNC' THEN
    RAISE_APPLICATION_ERROR(-20120, 'Expected the verified QDNC test application schema');
  END IF;
  LOCK TABLE qd_es_schema_version IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.1.0';
  IF v_count <> 1 THEN
    RAISE_APPLICATION_ERROR(-20121, 'Expected applied upgrade 1.1.0');
  END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no IN ('1.1.0-RB','1.2.0-RB');
  IF v_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20122, 'Reconcile the previous rollback before upgrading');
  END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.2.0';
  IF v_count = 1 THEN
    RETURN;
  END IF;

  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_org_control IN SHARE MODE NOWAIT;
  LOCK TABLE qd_es_policy IN SHARE MODE NOWAIT;
  LOCK TABLE qd_es_request IN SHARE MODE NOWAIT;
  LOCK TABLE qd_es_bd_control IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_bd_policy IN SHARE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_request WHERE business_state='RUNNING';
  IF v_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20123, 'Reconcile running requests before upgrading');
  END IF;
  SELECT (SELECT COUNT(*) FROM qd_es_bd_control) + (SELECT COUNT(*) FROM qd_es_bd_policy)
      + (SELECT COUNT(*) FROM qd_es_node WHERE group_scope_id IS NOT NULL)
    INTO v_count FROM dual;
  IF v_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20124, 'Expected empty group policy tables and unclassified mappings');
  END IF;

  UPDATE qd_es_node SET group_scope_id=scope_id WHERE scope_id IN (
    'QD-ES-B9C29A5B958696F4', 'QD-ES-0DA86324DBBF4DE6', 'QD-ES-E332E41B042F9540', 'QD-ES-9F902C76B60FBE04',
    'QD-ES-E576276F26109C16', 'QD-ES-35B5B1E09B5A4A80', 'QD-ES-E9B352C5EACB0820', 'QD-ES-0245CC5E8FA59478',
    'QD-ES-56EC7D5EE24A0804', 'QD-ES-A9C10437DFDE8814', 'QD-ES-8555A9BFE51EA438', 'QD-ES-7B87FD76F6ADBDC0',
    'QD-ES-B856D13140D5457B', 'QD-ES-E153DD629D00947F', 'QD-ES-70EC3A5C19555BD7', 'QD-ES-7493FA0F20E75AF0',
    'QD-ES-DDC46B3D9F641DFF', 'QD-ES-36702F5EC874AF77', 'QD-ES-6FCD3C3549E70950', 'QD-ES-56F5E6C99AD9B4B7',
    'QD-ES-D1DA60E564CAF142', 'QD-ES-A46C77B59E5AD49E', 'QD-ES-AA8986FF31D0B647', 'QD-ES-2D9AA713970C7E86',
    'QD-ES-0BB4B107C5D6B51F', 'QD-ES-D9F13F397DF1DF0B', 'QD-ES-93CFA69510E56403', 'QD-ES-E8C69ACAF76ECABF',
    'QD-ES-83A95678666FC134', 'QD-ES-F6D1E9BB792F6075', 'QD-ES-D9EF39B9A666E88D', 'QD-ES-122DB04892CFDA8B',
    'QD-ES-FD8282A38B9C4E89', 'QD-ES-309E9ED628D45CDF', 'QD-ES-1EFAFB0888F5A7CA', 'QD-ES-E2F3A16029CD1BC1',
    'QD-ES-E70DEE0145D79FDE', 'QD-ES-00DF272BF31F0AA2', 'QD-ES-24C25407F1C36AF6', 'QD-ES-B3A292BB1D2F3E84',
    'QD-ES-C9ED571D3AB7DD28', 'QD-ES-643C1BCD6537DD6B', 'QD-ES-8D2A39DEDA2F588D', 'QD-ES-F136737476331BC2',
    'QD-ES-5D9816B2EE6203BC', 'QD-ES-689A1D26EF57426B', 'QD-ES-8C9F0ACFE6E32A2B', 'QD-ES-42DE49AC3986C6B1',
    'QD-ES-A7FBCA61C6991D48', 'QD-ES-2F2608B07F6B73DD', 'QD-ES-4D029F8FC9693DD9', 'QD-ES-93014EDACA48E4D1',
    'QD-ES-4CE7157ECDB4A443', 'QD-ES-755F2D0C43B0646E', 'QD-ES-CE419A6EAAED8307', 'QD-ES-B48245BDDB687314',
    'QD-ES-FA635534B542BA71', 'QD-ES-2DAE0B30BE8A146B', 'QD-ES-2B20B2A6925C5B1A', 'QD-ES-E5FA2B4E1183BF51',
    'QD-ES-4DA3476F6CC93426', 'QD-ES-92F433355082640B', 'QD-ES-2148AD881831CEFA', 'QD-ES-B54294B82124C93D',
    'QD-ES-AF7F85BA60C742D9', 'QD-ES-765869462A8F9898', 'QD-ES-DB52192A0DE19F91', 'QD-ES-98DD9EFAD6E1C54E',
    'QD-ES-E8808CF0B60EED9C', 'QD-ES-00D8C4530BE5A4C4', 'QD-ES-25C42CF52E33610D', 'QD-ES-22C93F5AF42F721D',
    'QD-ES-B9B8E830DE3C5B6D', 'QD-ES-5BD5E8E063ECF621', 'QD-ES-62A408A680D6A829', 'QD-ES-69884D289EB5AFCC',
    'QD-ES-8B947039D517EEF6'
  );
  IF SQL%ROWCOUNT <> 77 THEN
    RAISE_APPLICATION_ERROR(-20125, 'Expected all 77 formal base-data scopes');
  END IF;

  -- Fresh group versions must never collide with a pre-upgrade authorization, even on the same node.
  INSERT INTO qd_es_bd_control (pk_group,master_enabled,control_version,modified_at)
  SELECT pk_group,'N',MAX(epoch)+1,v_now
    FROM (
      SELECT pk_group,control_version epoch FROM qd_es_request
      UNION ALL SELECT pk_group,control_version FROM qd_es_org_control
      UNION ALL SELECT pk_group,0 FROM qd_es_policy
    )
   GROUP BY pk_group;

  INSERT INTO qd_es_schema_version (version_no,applied_at,description)
  VALUES ('1.2.0',v_now,'Group-scoped base-data policies, initially disabled');
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_group_policy_upgrade;
    RAISE;
END;
/

SELECT COUNT(*) formal_base_data_actions FROM qd_es_node WHERE group_scope_id=scope_id;
SELECT pk_group,master_enabled,control_version FROM qd_es_bd_control ORDER BY pk_group;
