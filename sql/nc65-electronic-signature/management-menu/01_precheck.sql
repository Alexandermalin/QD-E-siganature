-- QDNC registration preflight. All rows should have status OK.
WITH checks (check_name, observed_count, expected_count) AS (
    SELECT 'target_session', CASE WHEN SYS_CONTEXT('USERENV', 'SESSION_USER') = 'QDNC'
      AND SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') = 'QDNC'
      AND SYS_CONTEXT('USERENV', 'DB_UNIQUE_NAME') = 'DB'
      AND SYS_CONTEXT('USERENV', 'SERVICE_NAME') = 'DB'
      AND LOWER(SYS_CONTEXT('USERENV', 'SERVER_HOST')) = 'ncoa-db' THEN 1 ELSE 0 END, 1 FROM dual
    UNION ALL
    SELECT 'default_menu', COUNT(*), 1
      FROM QDNC.sm_menuregister
     WHERE pk_menu = '1004ZZ10000000000FFL' AND menucode = 'defaultMenu' AND isenable = 'Y' AND isdefault = 'Y' AND NVL(dr, 0) = 0
    UNION ALL
    SELECT 'enterprise_menu', COUNT(*), 1
      FROM QDNC.sm_menuitemreg
     WHERE pk_menu = '1004ZZ10000000000FFL' AND menuitemcode = '10' AND ismenutype = 'Y'
         AND menuitemname = UNISTR('\4F01\4E1A\5EFA\6A21\5E73\53F0') AND NVL(dr, 0) = 0
    UNION ALL
    SELECT 'module_reference', COUNT(*), 1
      FROM QDNC.sm_funcregister
     WHERE cfunid = '0001Z010000000046FYT' AND funcode = '101414' AND own_module = '1014' AND isfunctype = 'Y' AND NVL(dr, 0) = 0
    UNION ALL
    SELECT 'v1_reference', COUNT(*), 1
      FROM QDNC.sm_funcregister
     WHERE cfunid = '1001A9100000009NU6OE' AND funcode = '13050QDES' AND NVL(dr, 0) = 0
    UNION ALL
    SELECT 'pilot_group', COUNT(*), 1
      FROM QDNC.org_group
     WHERE pk_group = '0001A9100000000005MH' AND enablestate = 2 AND NVL(dr, 0) = 0
    UNION ALL
    SELECT 'pilot_unit', COUNT(*), 1
      FROM QDNC.org_orgs
     WHERE pk_org = '0001A9100000003UDS6R' AND code = '990' AND pk_group = '0001A9100000000005MH'
         AND enablestate = 2 AND isbusinessunit = 'Y' AND islastversion = 'Y' AND NVL(dr, 0) = 0
    UNION ALL
    SELECT 'pilot_user', COUNT(*), 1
      FROM QDNC.sm_user
     WHERE cuserid = '1001A910000000016OHR' AND user_code = 'yonyou02' AND user_type = 1 AND pk_group = '0001A9100000000005MH' AND enablestate = 2 AND NVL(dr, 0) = 0
    UNION ALL
    SELECT 'pilot_role_group', COUNT(*), 1
      FROM QDNC.sm_role_group
     WHERE pk_role_group = '1001A9100000009E4341' AND group_code = '990' AND pk_org = '0001A9100000003UDS6R'
         AND pk_group = '0001A9100000000005MH' AND group_type = 1 AND NVL(dr, 0) = 0
    UNION ALL
    SELECT 'group_master_on', COUNT(*), 0
      FROM QDNC.qd_es_control
     WHERE pk_group = '0001A9100000000005MH' AND master_enabled = 'Y'
    UNION ALL
    SELECT 'function_codes', COUNT(*), 0
      FROM QDNC.sm_funcregister
     WHERE cfunid LIKE '1001A9QDESM%' OR parent_id LIKE '1001A9QDESM%' OR funcode IN ('QD_ES_MGMT', 'QD_ES_POLICY', 'QD_ES_SIGNLOG', 'QD_ES_AUDITLOG', 'QD_ES_SECURITY')
    UNION ALL
    SELECT 'menu_codes', COUNT(*), 0
      FROM QDNC.sm_menuitemreg
     WHERE pk_menuitem LIKE '1001A9QDESM%' OR funcode IN ('QD_ES_POLICY', 'QD_ES_SIGNLOG', 'QD_ES_AUDITLOG', 'QD_ES_SECURITY')
         OR (pk_menu = '1004ZZ10000000000FFL' AND (menuitemcode LIKE '1098%' OR menuitemname = UNISTR('\7535\5B50\7B7E\540D\7BA1\7406\FF08\65B0\7248\FF09')))
    UNION ALL
    SELECT 'button_ids', COUNT(*), 0
      FROM QDNC.sm_butnregister
     WHERE pk_btn LIKE '1001A9QDESM%' OR parent_id LIKE '1001A9QDESM%'
    UNION ALL
    SELECT 'activity_ids', COUNT(*), 0
      FROM QDNC.sm_busiactivereg
     WHERE pk_busiactive LIKE '1001A9QDESM%' OR parent_id LIKE '1001A9QDESM%'
         OR REGEXP_LIKE(busi_pk, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)')
    UNION ALL
    SELECT 'activity_button_ids', COUNT(*), 0
      FROM QDNC.sm_busiactive_btn
     WHERE pk_relation LIKE '1001A9QDESM%' OR pk_butn LIKE '1001A9QDESM%' OR pk_busiactive LIKE '1001A9QDESM%'
    UNION ALL
    SELECT 'button_control_ids', COUNT(*), 0
      FROM QDNC.sm_busiactive_perm
     WHERE pk_busiactive_perm LIKE '1001A9QDESM%' OR pk_subject LIKE '1001A9QDESM%'
         OR parentid LIKE '1001A9QDESM%' OR subjectcode IN ('QD_ES_POLICY', 'QD_ES_SIGNLOG', 'QD_ES_AUDITLOG', 'QD_ES_SECURITY')
    UNION ALL
    SELECT 'duty_ids', COUNT(*), 0
      FROM QDNC.sm_responsibility
     WHERE pk_responsibility LIKE '1001A9QDESM%' OR (pk_group = '0001A9100000000005MH' AND code = 'QD_ES_QUERY_990')
    UNION ALL
    SELECT 'duty_grants', COUNT(*), 0
      FROM QDNC.sm_resp_func
     WHERE pk_resp_func LIKE '1001A9QDESM%' OR pk_responsibility LIKE '1001A9QDESM%'
         OR REGEXP_LIKE(busi_pk, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)')
    UNION ALL
    SELECT 'role_ids', COUNT(*), 0
      FROM QDNC.sm_role
     WHERE pk_role LIKE '1001A9QDESM%' OR (pk_group = '0001A9100000000005MH' AND role_code = 'QD_ES_QUERY_990')
    UNION ALL
    SELECT 'role_duty_links', COUNT(*), 0
      FROM QDNC.sm_perm_func
     WHERE pk_perm_config LIKE '1001A9QDESM%' OR subjectid LIKE '1001A9QDESM%' OR ruleid LIKE '1001A9QDESM%'
    UNION ALL
    SELECT 'role_org_links', COUNT(*), 0
      FROM QDNC.sm_subject_org
     WHERE pk_subject_org LIKE '1001A9QDESM%' OR subjectid LIKE '1001A9QDESM%'
    UNION ALL
    SELECT 'user_role_links', COUNT(*), 0
      FROM QDNC.sm_user_role
     WHERE pk_user_role LIKE '1001A9QDESM%' OR pk_role LIKE '1001A9QDESM%'
    UNION ALL
    SELECT 'func_params', COUNT(*), 0
      FROM QDNC.sm_paramregister
     WHERE parentid LIKE '1001A9QDESM%'
)
SELECT check_name, observed_count, expected_count,
       CASE WHEN observed_count = expected_count THEN 'OK' ELSE 'STOP' END AS status
  FROM checks
 ORDER BY CASE WHEN observed_count = expected_count THEN 1 ELSE 0 END, check_name;
