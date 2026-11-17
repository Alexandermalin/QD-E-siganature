-- QDNC only. Use ROLLBACK in the original session for an uncommitted registration.
-- For a committed registration, run 03_verify.sql first and review the new transaction before committing.
DECLARE
  c_prefix CONSTANT VARCHAR2(11) := '1001A9QDESM';
  c_menu CONSTANT VARCHAR2(20) := '1004ZZ10000000000FFL';
  c_group CONSTANT VARCHAR2(20) := '0001A9100000000005MH';
  c_org CONSTANT VARCHAR2(20) := '0001A9100000003UDS6R';
  c_user CONSTANT VARCHAR2(20) := '1001A910000000016OHR';
  c_role_group CONSTANT VARCHAR2(20) := '1001A9100000009E4341';
  c_role CONSTANT VARCHAR2(20) := '1001A9QDESMR00000001';
  c_duty CONSTANT VARCHAR2(20) := '1001A9QDESMD00000001';
  c_func_folder CONSTANT VARCHAR2(20) := '1001A9QDESMF00000000';
  c_role_code CONSTANT VARCHAR2(30) := 'QD_ES_QUERY_990';
  c_folder_name CONSTANT VARCHAR2(100) := UNISTR('\7535\5B50\7B7E\540D\7BA1\7406\FF08\65B0\7248\FF09');
  c_pilot_name CONSTANT VARCHAR2(100) := UNISTR('\7535\5B50\7B7E\540D\67E5\8BE2\FF08990\FF09');
  l_func VARCHAR2(20);
  l_menu_item VARCHAR2(20);
  l_control VARCHAR2(20);
  l_item_code VARCHAR2(6);
  l_activity VARCHAR2(20);
  l_button VARCHAR2(20);
  l_relation VARCHAR2(20);
  l_duty_item VARCHAR2(20);
  l_busi_pk VARCHAR2(50);

  FUNCTION key_for(p_kind CHAR, p_number PLS_INTEGER) RETURN VARCHAR2 IS
  BEGIN
    RETURN c_prefix || p_kind || TO_CHAR(p_number, 'FM00000000');
  END key_for;

  PROCEDURE expect_deleted(p_count PLS_INTEGER, p_target VARCHAR2) IS
  BEGIN
    IF p_count <> 1 THEN
      RAISE_APPLICATION_ERROR(-20084, 'Rollback data differs from registration: ' || p_target);
    END IF;
  END expect_deleted;
BEGIN
  SAVEPOINT qd_es_menu_rollback;
  IF NVL(SYS_CONTEXT('USERENV', 'SESSION_USER'), '~') <> 'QDNC'
     OR NVL(SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA'), '~') <> 'QDNC'
     OR NVL(SYS_CONTEXT('USERENV', 'DB_UNIQUE_NAME'), '~') <> 'DB'
     OR NVL(SYS_CONTEXT('USERENV', 'SERVICE_NAME'), '~') <> 'DB'
     OR NVL(LOWER(SYS_CONTEXT('USERENV', 'SERVER_HOST')), '~') <> 'ncoa-db' THEN
    RAISE_APPLICATION_ERROR(-20081, 'Expected the verified QDNC test session');
  END IF;
  LOCK TABLE QDNC.sm_funcregister IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE QDNC.sm_menuitemreg IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE QDNC.sm_responsibility IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE QDNC.sm_role IN SHARE ROW EXCLUSIVE MODE NOWAIT;

  -- Stop if later administration attached other data to this installation.
  FOR item IN (
    WITH dependencies (dependency) AS (
      SELECT 'function_child' FROM QDNC.sm_funcregister
       WHERE parent_id LIKE c_prefix || '%' AND cfunid NOT IN (
         '1001A9QDESMF00000000', '1001A9QDESMF00000001', '1001A9QDESMF00000002',
         '1001A9QDESMF00000003', '1001A9QDESMF00000004'
       )
      UNION ALL
      SELECT 'menu_item' FROM QDNC.sm_menuitemreg
       WHERE (REGEXP_LIKE(funcode, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)') OR (pk_menu = c_menu AND menuitemcode LIKE '1098%'))
         AND pk_menuitem NOT IN (
         '1001A9QDESMM00000000', '1001A9QDESMM00000001', '1001A9QDESMM00000002',
         '1001A9QDESMM00000003', '1001A9QDESMM00000004'
       )
      UNION ALL
      SELECT 'button' FROM QDNC.sm_butnregister
       WHERE parent_id LIKE c_prefix || '%' AND pk_btn NOT IN (
         '1001A9QDESMB00000001', '1001A9QDESMB00000002', '1001A9QDESMB00000003',
         '1001A9QDESMB00000004', '1001A9QDESMB00000005', '1001A9QDESMB00000006',
         '1001A9QDESMB00000007', '1001A9QDESMB00000008', '1001A9QDESMB00000009',
         '1001A9QDESMB00000010'
       )
      UNION ALL
      SELECT 'activity' FROM QDNC.sm_busiactivereg
       WHERE (parent_id LIKE c_prefix || '%' OR REGEXP_LIKE(busi_pk, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)'))
         AND pk_busiactive NOT IN (
         '1001A9QDESMA00000001', '1001A9QDESMA00000002', '1001A9QDESMA00000003',
         '1001A9QDESMA00000004', '1001A9QDESMA00000005', '1001A9QDESMA00000006',
         '1001A9QDESMA00000007', '1001A9QDESMA00000008', '1001A9QDESMA00000009',
         '1001A9QDESMA00000010'
       )
      UNION ALL
      SELECT 'button_activity_link' FROM QDNC.sm_busiactive_btn
       WHERE (pk_butn LIKE c_prefix || '%' OR pk_busiactive LIKE c_prefix || '%')
         AND pk_relation NOT IN (
         '1001A9QDESML00000001', '1001A9QDESML00000002', '1001A9QDESML00000003',
         '1001A9QDESML00000004', '1001A9QDESML00000005', '1001A9QDESML00000006',
         '1001A9QDESML00000007', '1001A9QDESML00000008', '1001A9QDESML00000009',
         '1001A9QDESML00000010'
       )
      UNION ALL
      SELECT 'button_control' FROM QDNC.sm_busiactive_perm
       WHERE (pk_subject LIKE c_prefix || '%' OR parentid LIKE c_prefix || '%' OR REGEXP_LIKE(subjectcode, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)'))
         AND pk_busiactive_perm NOT IN (
         '1001A9QDESMC00000001', '1001A9QDESMC00000002', '1001A9QDESMC00000003',
         '1001A9QDESMC00000004'
       )
      UNION ALL
      SELECT 'activity_grant' FROM QDNC.sm_resp_func
       WHERE (pk_responsibility = c_duty OR REGEXP_LIKE(busi_pk, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)'))
         AND pk_resp_func NOT IN (
         '1001A9QDESMI00000001', '1001A9QDESMI00000002', '1001A9QDESMI00000003',
         '1001A9QDESMI00000004', '1001A9QDESMI00000005', '1001A9QDESMI00000006',
         '1001A9QDESMI00000007', '1001A9QDESMI00000008'
       )
      UNION ALL
      SELECT 'role_duty' FROM QDNC.sm_perm_func
       WHERE (subjectid = c_role OR ruleid = c_duty) AND pk_perm_config <> '1001A9QDESMP00000001'
      UNION ALL
      SELECT 'role_org' FROM QDNC.sm_subject_org
       WHERE subjectid = c_role AND pk_subject_org <> '1001A9QDESMO00000001'
      UNION ALL
      SELECT 'role_user' FROM QDNC.sm_user_role
       WHERE pk_role = c_role AND pk_user_role <> '1001A9QDESMU00000001'
      UNION ALL
      SELECT 'function_parameter' FROM QDNC.sm_paramregister
       WHERE parentid LIKE c_prefix || '%'
      UNION ALL
      SELECT 'admin_function' FROM QDNC.sm_admin_func
       WHERE func_id LIKE c_prefix || '%' OR REGEXP_LIKE(func_id, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)') OR admin_role_id = c_role
      UNION ALL
      SELECT 'module_function' FROM QDNC.sm_gm_funcreg
       WHERE REGEXP_LIKE(funcode, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)')
      UNION ALL
      SELECT 'role_inheritance' FROM QDNC.sm_role_inherit
       WHERE role_id = c_role OR inherited_role_id = c_role
      UNION ALL
      SELECT 'role_exclusion' FROM QDNC.sm_role_exclude
       WHERE excluding_role = c_role OR excluded_role = c_role
      UNION ALL
      SELECT 'role_data_permissions' FROM QDNC.sm_perm_data
       WHERE subjectid = c_role OR ruleid = c_duty
      UNION ALL
      SELECT 'role_data_rule' FROM QDNC.sm_permission_data
       WHERE roleid = c_role
      UNION ALL
      SELECT 'role_data_reference' FROM QDNC.sm_permission_ref
       WHERE roleid = c_role
      UNION ALL
      SELECT 'role_permission_document' FROM QDNC.sm_roleperm_pf
       WHERE pk_role = c_role
      UNION ALL
      SELECT 'user_permission_role' FROM QDNC.sm_uppf_rolesub
       WHERE pk_role = c_role
      UNION ALL
      SELECT 'user_permission_duty' FROM QDNC.sm_uppf_respsub
       WHERE pk_responsibility = c_duty
      UNION ALL
      SELECT 'role_permission_duty' FROM QDNC.sm_rpsub_resppf
       WHERE pk_responsibility = c_duty
    )
    SELECT dependency FROM dependencies WHERE ROWNUM = 1
  ) LOOP
    RAISE_APPLICATION_ERROR(-20083, 'Rollback blocked by an additional reference: ' || item.dependency);
  END LOOP;

  DELETE FROM QDNC.sm_user_role
   WHERE pk_user_role = '1001A9QDESMU00000001' AND pk_role = c_role AND cuserid = c_user
     AND pk_org = '~' AND disabledate IS NULL AND NVL(dr, 0) = 0;
  expect_deleted(SQL%ROWCOUNT, 'pilot user assignment');

  DELETE FROM QDNC.sm_subject_org
   WHERE pk_subject_org = '1001A9QDESMO00000001' AND subjectid = c_role AND pk_org = c_org
     AND pk_group = c_group AND isrule = '~' AND rulepar IS NULL AND NVL(dr, 0) = 0;
  expect_deleted(SQL%ROWCOUNT, 'pilot organization scope');

  DELETE FROM QDNC.sm_perm_func
   WHERE pk_perm_config = '1001A9QDESMP00000001' AND subjectid = c_role AND ruleid = c_duty
     AND pk_group = c_group AND resourceid = 'funcperm000000000000' AND operationcode IS NULL AND NVL(dr, 0) = 0;
  expect_deleted(SQL%ROWCOUNT, 'pilot role responsibility');

  FOR action IN (
    WITH action_defs (action_no, node_no, node_code, action_code, action_name) AS (
      SELECT 1, 1, 'QD_ES_POLICY', 'QUERY', UNISTR('\67E5\8BE2') FROM dual
      UNION ALL
      SELECT 2, 1, 'QD_ES_POLICY', 'SAVE', UNISTR('\4FDD\5B58\7B56\7565') FROM dual
      UNION ALL
      SELECT 3, 1, 'QD_ES_POLICY', 'MASTER', UNISTR('\7EF4\62A4\96C6\56E2\603B\63A7') FROM dual
      UNION ALL
      SELECT 4, 2, 'QD_ES_SIGNLOG', 'QUERY', UNISTR('\67E5\8BE2') FROM dual
      UNION ALL
      SELECT 5, 2, 'QD_ES_SIGNLOG', 'EXPORT', UNISTR('\5BFC\51FA') FROM dual
      UNION ALL
      SELECT 6, 3, 'QD_ES_AUDITLOG', 'QUERY', UNISTR('\67E5\8BE2') FROM dual
      UNION ALL
      SELECT 7, 3, 'QD_ES_AUDITLOG', 'EXPORT', UNISTR('\5BFC\51FA') FROM dual
      UNION ALL
      SELECT 8, 4, 'QD_ES_SECURITY', 'QUERY', UNISTR('\67E5\8BE2') FROM dual
      UNION ALL
      SELECT 9, 4, 'QD_ES_SECURITY', 'EXPORT', UNISTR('\5BFC\51FA') FROM dual
      UNION ALL
      SELECT 10, 4, 'QD_ES_SECURITY', 'GROUP_SECURITY', UNISTR('\67E5\770B\96C6\56E2\7EA7\5B89\5168\4E8B\4EF6') FROM dual
    )
    SELECT action_no, node_no, node_code, action_code, action_name FROM action_defs ORDER BY action_no
  ) LOOP
    l_func := key_for('F', action.node_no);
    l_activity := key_for('A', action.action_no);
    l_button := key_for('B', action.action_no);
    l_relation := key_for('L', action.action_no);
    l_busi_pk := action.node_code || '_' || action.action_code;

    IF action.action_code = 'QUERY' THEN
      l_duty_item := key_for('I', 2 * action.node_no);
      DELETE FROM QDNC.sm_resp_func
       WHERE pk_resp_func = l_duty_item AND pk_responsibility = c_duty AND pk_group = c_group
         AND busi_pk = l_busi_pk AND type = 2 AND systype = 'RIA' AND NVL(dr, 0) = 0;
      expect_deleted(SQL%ROWCOUNT, l_busi_pk || ' grant');
    END IF;

    DELETE FROM QDNC.sm_busiactive_btn
     WHERE pk_relation = l_relation AND pk_busiactive = l_activity AND pk_butn = l_button AND NVL(dr, 0) = 0;
    expect_deleted(SQL%ROWCOUNT, l_busi_pk || ' activity button');

    DELETE FROM QDNC.sm_busiactivereg
     WHERE pk_busiactive = l_activity AND parent_id = l_func AND busi_pk = l_busi_pk
       AND code = action.action_code AND name = action.action_name AND ownertype = 0 AND NVL(dr, 0) = 0;
    expect_deleted(SQL%ROWCOUNT, l_busi_pk || ' activity');

    DELETE FROM QDNC.sm_butnregister
     WHERE pk_btn = l_button AND parent_id = l_func AND btncode = action.action_code
       AND btnname = action.action_name AND btnownertype = 0
       AND isenable = 'Y' AND isbuttonpower = 'Y' AND iskeyfunc = 'N' AND NVL(dr, 0) = 0;
    expect_deleted(SQL%ROWCOUNT, l_busi_pk || ' button');
  END LOOP;

  FOR node IN (
    WITH node_defs (node_no, node_code, node_name, class_name) AS (
      SELECT 1, 'QD_ES_POLICY', UNISTR('\7535\5B50\7B7E\540D\7B56\7565'),
             'nc.ui.uapbd.qd.esign.QdPolicyFunclet' FROM dual
      UNION ALL
      SELECT 2, 'QD_ES_SIGNLOG', UNISTR('\7535\5B50\7B7E\540D\65E5\5FD7'),
             'nc.ui.uapbd.qd.esign.QdSignatureLogFunclet' FROM dual
      UNION ALL
      SELECT 3, 'QD_ES_AUDITLOG', UNISTR('\4E1A\52A1\5BA1\8BA1\65E5\5FD7'),
             'nc.ui.uapbd.qd.esign.QdAuditLogFunclet' FROM dual
      UNION ALL
      SELECT 4, 'QD_ES_SECURITY', UNISTR('\767B\5F55\4E0E\5B89\5168\4E8B\4EF6\65E5\5FD7'),
             'nc.ui.uapbd.qd.esign.QdSecurityLogFunclet' FROM dual
    )
    SELECT node_no, node_code, node_name, class_name FROM node_defs ORDER BY node_no
  ) LOOP
    l_func := key_for('F', node.node_no);
    l_menu_item := key_for('M', node.node_no);
    l_control := key_for('C', node.node_no);
    l_duty_item := key_for('I', 2 * node.node_no - 1);
    l_item_code := '1098' || TO_CHAR(node.node_no, 'FM00');

    DELETE FROM QDNC.sm_resp_func
     WHERE pk_resp_func = l_duty_item AND pk_responsibility = c_duty AND pk_group = c_group
       AND busi_pk = node.node_code AND type = 0 AND systype = 'RIA' AND NVL(dr, 0) = 0;
    expect_deleted(SQL%ROWCOUNT, node.node_code || ' function grant');

    DELETE FROM QDNC.sm_busiactive_perm
     WHERE pk_busiactive_perm = l_control AND pk_subject = l_func AND subjectcode = node.node_code
       AND subjecttype = 0 AND parentid = c_func_folder AND own_module = '1014'
       AND pk_group = c_group AND pk_org = '~' AND isbuttonpower = 'Y' AND NVL(dr, 0) = 0;
    expect_deleted(SQL%ROWCOUNT, node.node_code || ' button control');

    DELETE FROM QDNC.sm_menuitemreg
     WHERE pk_menuitem = l_menu_item AND pk_menu = c_menu AND menuitemcode = l_item_code
       AND funcode = node.node_code AND menuitemname = node.node_name AND ismenutype = 'N' AND NVL(dr, 0) = 0;
    expect_deleted(SQL%ROWCOUNT, node.node_code || ' menu');

    DELETE FROM QDNC.sm_funcregister
     WHERE cfunid = l_func AND parent_id = c_func_folder AND funcode = node.node_code
       AND class_name = node.class_name AND fun_name = node.node_name AND own_module = '1014'
       AND orgtypecode = 'BUSINESSUNIT00000000' AND funtype = 0 AND fun_property = 0
       AND isfunctype = 'N' AND isenable = 'Y' AND isbuttonpower = 'Y' AND NVL(dr, 0) = 0;
    expect_deleted(SQL%ROWCOUNT, node.node_code || ' function');
  END LOOP;

  DELETE FROM QDNC.sm_menuitemreg
   WHERE pk_menuitem = '1001A9QDESMM00000000' AND pk_menu = c_menu AND menuitemcode = '1098'
     AND funcode = '~' AND menuitemname = c_folder_name AND ismenutype = 'Y' AND NVL(dr, 0) = 0;
  expect_deleted(SQL%ROWCOUNT, 'menu folder');

  DELETE FROM QDNC.sm_funcregister
   WHERE cfunid = c_func_folder AND parent_id = '~' AND funcode = 'QD_ES_MGMT'
     AND fun_name = c_folder_name AND own_module = '1014' AND isfunctype = 'Y'
     AND isenable = 'N' AND isbuttonpower = 'N' AND NVL(dr, 0) = 0;
  expect_deleted(SQL%ROWCOUNT, 'function category');

  DELETE FROM QDNC.sm_role
   WHERE pk_role = c_role AND role_code = c_role_code AND role_name = c_pilot_name AND role_group_id = c_role_group
     AND pk_group = c_group AND pk_org = c_org AND role_type = 1 AND role_class = 0
     AND isorgrole = 'N' AND NVL(dr, 0) = 0;
  expect_deleted(SQL%ROWCOUNT, 'pilot role');

  DELETE FROM QDNC.sm_responsibility
   WHERE pk_responsibility = c_duty AND code = c_role_code AND name = c_pilot_name
     AND pk_group = c_group AND pk_org = c_group AND type = 0 AND NVL(dr, 0) = 0;
  expect_deleted(SQL%ROWCOUNT, 'pilot responsibility');
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_es_menu_rollback;
    RAISE;
END;
/
