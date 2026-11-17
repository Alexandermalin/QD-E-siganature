-- QDNC only. Run 01_precheck.sql in the target session before starting the transaction.
-- Execute all statements as one rollbackable batch and stop on any error.
-- The first and last INSERTs evaluate their assertions as part of a required primary key.
-- A failed assertion raises ORA-01400 and the entire batch must be rolled back.
LOCK TABLE QDNC.sm_funcregister IN SHARE ROW EXCLUSIVE MODE NOWAIT;
LOCK TABLE QDNC.sm_menuitemreg IN SHARE ROW EXCLUSIVE MODE NOWAIT;
LOCK TABLE QDNC.sm_responsibility IN SHARE ROW EXCLUSIVE MODE NOWAIT;
LOCK TABLE QDNC.sm_role IN SHARE ROW EXCLUSIVE MODE NOWAIT;

INSERT INTO QDNC.sm_funcregister
    (cfunid, funcode, fun_name, class_name, own_module, parent_id, orgtypecode,
     funtype, fun_property, isfunctype, isbuttonpower, isenable, iscauserusable)
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
SELECT CASE WHEN COUNT(*) = 0 THEN '1001A9QDESMF00000000' END AS cfunid,
       'QD_ES_MGMT', UNISTR('\7535\5B50\7B7E\540D\7BA1\7406\FF08\65B0\7248\FF09'), CAST(NULL AS VARCHAR2(200)), '1014', '~', CAST(NULL AS VARCHAR2(50)), CAST(NULL AS NUMBER), CAST(NULL AS NUMBER), 'Y', 'N', 'N', CAST(NULL AS CHAR(1))
  FROM checks
 WHERE observed_count <> expected_count
UNION ALL
SELECT '1001A9QDESMF00000001', 'QD_ES_POLICY', UNISTR('\7535\5B50\7B7E\540D\7B56\7565'), 'nc.ui.uapbd.qd.esign.QdPolicyFunclet', '1014', '1001A9QDESMF00000000', 'BUSINESSUNIT00000000', 0, 0, 'N', 'Y', 'Y', 'N' FROM dual
UNION ALL
SELECT '1001A9QDESMF00000002', 'QD_ES_SIGNLOG', UNISTR('\7535\5B50\7B7E\540D\65E5\5FD7'), 'nc.ui.uapbd.qd.esign.QdSignatureLogFunclet', '1014', '1001A9QDESMF00000000', 'BUSINESSUNIT00000000', 0, 0, 'N', 'Y', 'Y', 'N' FROM dual
UNION ALL
SELECT '1001A9QDESMF00000003', 'QD_ES_AUDITLOG', UNISTR('\4E1A\52A1\5BA1\8BA1\65E5\5FD7'), 'nc.ui.uapbd.qd.esign.QdAuditLogFunclet', '1014', '1001A9QDESMF00000000', 'BUSINESSUNIT00000000', 0, 0, 'N', 'Y', 'Y', 'N' FROM dual
UNION ALL
SELECT '1001A9QDESMF00000004', 'QD_ES_SECURITY', UNISTR('\767B\5F55\4E0E\5B89\5168\4E8B\4EF6\65E5\5FD7'), 'nc.ui.uapbd.qd.esign.QdSecurityLogFunclet', '1014', '1001A9QDESMF00000000', 'BUSINESSUNIT00000000', 0, 0, 'N', 'Y', 'Y', 'N' FROM dual;

INSERT INTO QDNC.sm_menuitemreg
    (pk_menuitem, pk_menu, menuitemcode, menuitemname, funcode, ismenutype, resid)
SELECT '1001A9QDESMM00000000', '1004ZZ10000000000FFL', '1098', UNISTR('\7535\5B50\7B7E\540D\7BA1\7406\FF08\65B0\7248\FF09'), '~', 'Y', 'D1098' FROM dual
UNION ALL
SELECT '1001A9QDESMM00000001', '1004ZZ10000000000FFL', '109801', UNISTR('\7535\5B50\7B7E\540D\7B56\7565'), 'QD_ES_POLICY', 'N', 'D109801' FROM dual
UNION ALL
SELECT '1001A9QDESMM00000002', '1004ZZ10000000000FFL', '109802', UNISTR('\7535\5B50\7B7E\540D\65E5\5FD7'), 'QD_ES_SIGNLOG', 'N', 'D109802' FROM dual
UNION ALL
SELECT '1001A9QDESMM00000003', '1004ZZ10000000000FFL', '109803', UNISTR('\4E1A\52A1\5BA1\8BA1\65E5\5FD7'), 'QD_ES_AUDITLOG', 'N', 'D109803' FROM dual
UNION ALL
SELECT '1001A9QDESMM00000004', '1004ZZ10000000000FFL', '109804', UNISTR('\767B\5F55\4E0E\5B89\5168\4E8B\4EF6\65E5\5FD7'), 'QD_ES_SECURITY', 'N', 'D109804' FROM dual;

INSERT INTO QDNC.sm_busiactive_perm
    (pk_busiactive_perm, pk_subject, subjectcode, displayname, subjecttype, parentid, own_module,
     pk_group, isbuttonpower, resid, creationtime)
SELECT '1001A9QDESMC00000001', '1001A9QDESMF00000001', 'QD_ES_POLICY', 'QD_ES_POLICY ' || UNISTR('\7535\5B50\7B7E\540D\7B56\7565'), 0, '1001A9QDESMF00000000', '1014', '0001A9100000000005MH', 'Y', 'QD_ES_POLICY', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual
UNION ALL
SELECT '1001A9QDESMC00000002', '1001A9QDESMF00000002', 'QD_ES_SIGNLOG', 'QD_ES_SIGNLOG ' || UNISTR('\7535\5B50\7B7E\540D\65E5\5FD7'), 0, '1001A9QDESMF00000000', '1014', '0001A9100000000005MH', 'Y', 'QD_ES_SIGNLOG', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual
UNION ALL
SELECT '1001A9QDESMC00000003', '1001A9QDESMF00000003', 'QD_ES_AUDITLOG', 'QD_ES_AUDITLOG ' || UNISTR('\4E1A\52A1\5BA1\8BA1\65E5\5FD7'), 0, '1001A9QDESMF00000000', '1014', '0001A9100000000005MH', 'Y', 'QD_ES_AUDITLOG', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual
UNION ALL
SELECT '1001A9QDESMC00000004', '1001A9QDESMF00000004', 'QD_ES_SECURITY', 'QD_ES_SECURITY ' || UNISTR('\767B\5F55\4E0E\5B89\5168\4E8B\4EF6\65E5\5FD7'), 0, '1001A9QDESMF00000000', '1014', '0001A9100000000005MH', 'Y', 'QD_ES_SECURITY', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual;

INSERT INTO QDNC.sm_butnregister
    (pk_btn, parent_id, btncode, btnname, btnownertype, isenable, isbuttonpower,
     iskeyfunc, resid)
SELECT '1001A9QDESMB00000001', '1001A9QDESMF00000001', 'QUERY', UNISTR('\67E5\8BE2'), 0, 'Y', 'Y', 'N', 'btn_QD_ES_POLICY_QUERY' FROM dual
UNION ALL
SELECT '1001A9QDESMB00000002', '1001A9QDESMF00000001', 'SAVE', UNISTR('\4FDD\5B58\7B56\7565'), 0, 'Y', 'Y', 'N', 'btn_QD_ES_POLICY_SAVE' FROM dual
UNION ALL
SELECT '1001A9QDESMB00000003', '1001A9QDESMF00000001', 'MASTER', UNISTR('\7EF4\62A4\96C6\56E2\603B\63A7'), 0, 'Y', 'Y', 'N', 'btn_QD_ES_POLICY_MASTER' FROM dual
UNION ALL
SELECT '1001A9QDESMB00000004', '1001A9QDESMF00000002', 'QUERY', UNISTR('\67E5\8BE2'), 0, 'Y', 'Y', 'N', 'btn_QD_ES_SIGNLOG_QUERY' FROM dual
UNION ALL
SELECT '1001A9QDESMB00000005', '1001A9QDESMF00000002', 'EXPORT', UNISTR('\5BFC\51FA'), 0, 'Y', 'Y', 'N', 'btn_QD_ES_SIGNLOG_EXPORT' FROM dual
UNION ALL
SELECT '1001A9QDESMB00000006', '1001A9QDESMF00000003', 'QUERY', UNISTR('\67E5\8BE2'), 0, 'Y', 'Y', 'N', 'btn_QD_ES_AUDITLOG_QUERY' FROM dual
UNION ALL
SELECT '1001A9QDESMB00000007', '1001A9QDESMF00000003', 'EXPORT', UNISTR('\5BFC\51FA'), 0, 'Y', 'Y', 'N', 'btn_QD_ES_AUDITLOG_EXPORT' FROM dual
UNION ALL
SELECT '1001A9QDESMB00000008', '1001A9QDESMF00000004', 'QUERY', UNISTR('\67E5\8BE2'), 0, 'Y', 'Y', 'N', 'btn_QD_ES_SECURITY_QUERY' FROM dual
UNION ALL
SELECT '1001A9QDESMB00000009', '1001A9QDESMF00000004', 'EXPORT', UNISTR('\5BFC\51FA'), 0, 'Y', 'Y', 'N', 'btn_QD_ES_SECURITY_EXPORT' FROM dual
UNION ALL
SELECT '1001A9QDESMB00000010', '1001A9QDESMF00000004', 'GROUP_SECURITY', UNISTR('\67E5\770B\96C6\56E2\7EA7\5B89\5168\4E8B\4EF6'), 0, 'Y', 'Y', 'N', 'btn_QD_ES_SECURITY_GROUP_SECURITY' FROM dual;

INSERT INTO QDNC.sm_busiactivereg
    (pk_busiactive, parent_id, code, name, busi_pk, ownertype, resid)
SELECT '1001A9QDESMA00000001', '1001A9QDESMF00000001', 'QUERY', UNISTR('\67E5\8BE2'), 'QD_ES_POLICY_QUERY', 0, 'QD_ES_POLICY_QUERY' FROM dual
UNION ALL
SELECT '1001A9QDESMA00000002', '1001A9QDESMF00000001', 'SAVE', UNISTR('\4FDD\5B58\7B56\7565'), 'QD_ES_POLICY_SAVE', 0, 'QD_ES_POLICY_SAVE' FROM dual
UNION ALL
SELECT '1001A9QDESMA00000003', '1001A9QDESMF00000001', 'MASTER', UNISTR('\7EF4\62A4\96C6\56E2\603B\63A7'), 'QD_ES_POLICY_MASTER', 0, 'QD_ES_POLICY_MASTER' FROM dual
UNION ALL
SELECT '1001A9QDESMA00000004', '1001A9QDESMF00000002', 'QUERY', UNISTR('\67E5\8BE2'), 'QD_ES_SIGNLOG_QUERY', 0, 'QD_ES_SIGNLOG_QUERY' FROM dual
UNION ALL
SELECT '1001A9QDESMA00000005', '1001A9QDESMF00000002', 'EXPORT', UNISTR('\5BFC\51FA'), 'QD_ES_SIGNLOG_EXPORT', 0, 'QD_ES_SIGNLOG_EXPORT' FROM dual
UNION ALL
SELECT '1001A9QDESMA00000006', '1001A9QDESMF00000003', 'QUERY', UNISTR('\67E5\8BE2'), 'QD_ES_AUDITLOG_QUERY', 0, 'QD_ES_AUDITLOG_QUERY' FROM dual
UNION ALL
SELECT '1001A9QDESMA00000007', '1001A9QDESMF00000003', 'EXPORT', UNISTR('\5BFC\51FA'), 'QD_ES_AUDITLOG_EXPORT', 0, 'QD_ES_AUDITLOG_EXPORT' FROM dual
UNION ALL
SELECT '1001A9QDESMA00000008', '1001A9QDESMF00000004', 'QUERY', UNISTR('\67E5\8BE2'), 'QD_ES_SECURITY_QUERY', 0, 'QD_ES_SECURITY_QUERY' FROM dual
UNION ALL
SELECT '1001A9QDESMA00000009', '1001A9QDESMF00000004', 'EXPORT', UNISTR('\5BFC\51FA'), 'QD_ES_SECURITY_EXPORT', 0, 'QD_ES_SECURITY_EXPORT' FROM dual
UNION ALL
SELECT '1001A9QDESMA00000010', '1001A9QDESMF00000004', 'GROUP_SECURITY', UNISTR('\67E5\770B\96C6\56E2\7EA7\5B89\5168\4E8B\4EF6'), 'QD_ES_SECURITY_GROUP_SECURITY', 0, 'QD_ES_SECURITY_GROUP_SECURITY' FROM dual;

INSERT INTO QDNC.sm_busiactive_btn
    (pk_relation, pk_busiactive, pk_butn)
SELECT '1001A9QDESML00000001', '1001A9QDESMA00000001', '1001A9QDESMB00000001' FROM dual
UNION ALL
SELECT '1001A9QDESML00000002', '1001A9QDESMA00000002', '1001A9QDESMB00000002' FROM dual
UNION ALL
SELECT '1001A9QDESML00000003', '1001A9QDESMA00000003', '1001A9QDESMB00000003' FROM dual
UNION ALL
SELECT '1001A9QDESML00000004', '1001A9QDESMA00000004', '1001A9QDESMB00000004' FROM dual
UNION ALL
SELECT '1001A9QDESML00000005', '1001A9QDESMA00000005', '1001A9QDESMB00000005' FROM dual
UNION ALL
SELECT '1001A9QDESML00000006', '1001A9QDESMA00000006', '1001A9QDESMB00000006' FROM dual
UNION ALL
SELECT '1001A9QDESML00000007', '1001A9QDESMA00000007', '1001A9QDESMB00000007' FROM dual
UNION ALL
SELECT '1001A9QDESML00000008', '1001A9QDESMA00000008', '1001A9QDESMB00000008' FROM dual
UNION ALL
SELECT '1001A9QDESML00000009', '1001A9QDESMA00000009', '1001A9QDESMB00000009' FROM dual
UNION ALL
SELECT '1001A9QDESML00000010', '1001A9QDESMA00000010', '1001A9QDESMB00000010' FROM dual;

INSERT INTO QDNC.sm_responsibility
    (pk_responsibility, code, name, pk_group, pk_org, type, creationtime,
     modifiedtime)
SELECT '1001A9QDESMD00000001', 'QD_ES_QUERY_990', UNISTR('\7535\5B50\7B7E\540D\67E5\8BE2\FF08990\FF09'), '0001A9100000000005MH', '0001A9100000000005MH', 0, TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS'), TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual;

INSERT INTO QDNC.sm_resp_func
    (pk_resp_func, pk_responsibility, busi_pk, type, systype, pk_group, creationtime)
SELECT '1001A9QDESMI00000001', '1001A9QDESMD00000001', 'QD_ES_POLICY', 0, 'RIA', '0001A9100000000005MH', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual
UNION ALL
SELECT '1001A9QDESMI00000002', '1001A9QDESMD00000001', 'QD_ES_POLICY_QUERY', 2, 'RIA', '0001A9100000000005MH', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual
UNION ALL
SELECT '1001A9QDESMI00000003', '1001A9QDESMD00000001', 'QD_ES_SIGNLOG', 0, 'RIA', '0001A9100000000005MH', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual
UNION ALL
SELECT '1001A9QDESMI00000004', '1001A9QDESMD00000001', 'QD_ES_SIGNLOG_QUERY', 2, 'RIA', '0001A9100000000005MH', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual
UNION ALL
SELECT '1001A9QDESMI00000005', '1001A9QDESMD00000001', 'QD_ES_AUDITLOG', 0, 'RIA', '0001A9100000000005MH', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual
UNION ALL
SELECT '1001A9QDESMI00000006', '1001A9QDESMD00000001', 'QD_ES_AUDITLOG_QUERY', 2, 'RIA', '0001A9100000000005MH', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual
UNION ALL
SELECT '1001A9QDESMI00000007', '1001A9QDESMD00000001', 'QD_ES_SECURITY', 0, 'RIA', '0001A9100000000005MH', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual
UNION ALL
SELECT '1001A9QDESMI00000008', '1001A9QDESMD00000001', 'QD_ES_SECURITY_QUERY', 2, 'RIA', '0001A9100000000005MH', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual;

INSERT INTO QDNC.sm_role
    (pk_role, role_code, role_name, role_type, role_class, isorgrole, role_group_id,
     pk_group, pk_org, creationtime, modifiedtime)
SELECT '1001A9QDESMR00000001', 'QD_ES_QUERY_990', UNISTR('\7535\5B50\7B7E\540D\67E5\8BE2\FF08990\FF09'), 1, 0, 'N', '1001A9100000009E4341', '0001A9100000000005MH', '0001A9100000003UDS6R', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS'), TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual;

INSERT INTO QDNC.sm_perm_func
    (pk_perm_config, subjectid, resourceid, ruleid, pk_group, creationtime)
SELECT '1001A9QDESMP00000001', '1001A9QDESMR00000001', 'funcperm000000000000', '1001A9QDESMD00000001', '0001A9100000000005MH', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual;

INSERT INTO QDNC.sm_subject_org
    (pk_subject_org, subjectid, pk_org, pk_group, isrule, creationtime)
SELECT '1001A9QDESMO00000001', '1001A9QDESMR00000001', '0001A9100000003UDS6R', '0001A9100000000005MH', '~', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual;

-- Activate the pilot assignment after validating the other 56 rows and their relationships.
INSERT INTO QDNC.sm_user_role
    (pk_user_role, cuserid, pk_role, pk_org, enabledate, creationtime)
WITH counts (table_name, observed_count, expected_count) AS (
    SELECT 'sm_funcregister', COUNT(*), 5 FROM QDNC.sm_funcregister
     WHERE NVL(dr, 0) = 0 AND (cfunid LIKE '1001A9QDESM%' OR funcode = 'QD_ES_MGMT' OR REGEXP_LIKE(funcode, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)'))
    UNION ALL
    SELECT 'sm_menuitemreg', COUNT(*), 5 FROM QDNC.sm_menuitemreg
     WHERE NVL(dr, 0) = 0 AND (pk_menuitem LIKE '1001A9QDESM%' OR REGEXP_LIKE(funcode, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)')
     OR (pk_menu = '1004ZZ10000000000FFL' AND menuitemcode LIKE '1098%'))
    UNION ALL
    SELECT 'sm_butnregister', COUNT(*), 10 FROM QDNC.sm_butnregister
     WHERE NVL(dr, 0) = 0 AND (pk_btn LIKE '1001A9QDESM%' OR parent_id LIKE '1001A9QDESM%')
    UNION ALL
    SELECT 'sm_busiactivereg', COUNT(*), 10 FROM QDNC.sm_busiactivereg
     WHERE NVL(dr, 0) = 0 AND (pk_busiactive LIKE '1001A9QDESM%' OR parent_id LIKE '1001A9QDESM%' OR REGEXP_LIKE(busi_pk, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)'))
    UNION ALL
    SELECT 'sm_busiactive_btn', COUNT(*), 10 FROM QDNC.sm_busiactive_btn
     WHERE NVL(dr, 0) = 0 AND (pk_relation LIKE '1001A9QDESM%' OR pk_butn LIKE '1001A9QDESM%' OR pk_busiactive LIKE '1001A9QDESM%')
    UNION ALL
    SELECT 'sm_busiactive_perm', COUNT(*), 4 FROM QDNC.sm_busiactive_perm
     WHERE NVL(dr, 0) = 0 AND (pk_busiactive_perm LIKE '1001A9QDESM%' OR pk_subject LIKE '1001A9QDESM%' OR REGEXP_LIKE(subjectcode, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)'))
    UNION ALL
    SELECT 'sm_responsibility', COUNT(*), 1 FROM QDNC.sm_responsibility
     WHERE NVL(dr, 0) = 0 AND (pk_responsibility = '1001A9QDESMD00000001' OR (pk_group = '0001A9100000000005MH' AND code = 'QD_ES_QUERY_990'))
    UNION ALL
    SELECT 'sm_resp_func', COUNT(*), 8 FROM QDNC.sm_resp_func
     WHERE NVL(dr, 0) = 0 AND (pk_responsibility = '1001A9QDESMD00000001' OR REGEXP_LIKE(busi_pk, '^QD_ES_(POLICY|SIGNLOG|AUDITLOG|SECURITY)(_|$)'))
    UNION ALL
    SELECT 'sm_role', COUNT(*), 1 FROM QDNC.sm_role
     WHERE NVL(dr, 0) = 0 AND (pk_role = '1001A9QDESMR00000001' OR (pk_group = '0001A9100000000005MH' AND role_code = 'QD_ES_QUERY_990'))
    UNION ALL
    SELECT 'sm_perm_func', COUNT(*), 1 FROM QDNC.sm_perm_func
     WHERE NVL(dr, 0) = 0 AND (subjectid = '1001A9QDESMR00000001' OR ruleid = '1001A9QDESMD00000001')
    UNION ALL
    SELECT 'sm_subject_org', COUNT(*), 1 FROM QDNC.sm_subject_org
     WHERE NVL(dr, 0) = 0 AND (subjectid = '1001A9QDESMR00000001')
    UNION ALL
    SELECT 'sm_user_role', COUNT(*), 0 FROM QDNC.sm_user_role
     WHERE NVL(dr, 0) = 0 AND (pk_role = '1001A9QDESMR00000001')
)
SELECT CASE WHEN NOT EXISTS (SELECT 1 FROM counts WHERE observed_count <> expected_count)
                 AND (SELECT COUNT(*) FROM (
                        SELECT f.funcode, f.class_name, i.menuitemcode, b.btncode, a.busi_pk,
                               CASE WHEN q.pk_resp_func IS NULL THEN 'N' ELSE 'Y' END AS query_role_grant,
                               CASE WHEN
                                   f.class_name = CASE f.funcode
                                   WHEN 'QD_ES_POLICY' THEN 'nc.ui.uapbd.qd.esign.QdPolicyFunclet'
                                   WHEN 'QD_ES_SIGNLOG' THEN 'nc.ui.uapbd.qd.esign.QdSignatureLogFunclet'
                                   WHEN 'QD_ES_AUDITLOG' THEN 'nc.ui.uapbd.qd.esign.QdAuditLogFunclet'
                                   WHEN 'QD_ES_SECURITY' THEN 'nc.ui.uapbd.qd.esign.QdSecurityLogFunclet'
                                   END
                                   AND f.own_module = '1014' AND f.parent_id = '1001A9QDESMF00000000'
                                   AND f.orgtypecode = 'BUSINESSUNIT00000000'
                                   AND f.funtype = 0 AND f.isfunctype = 'N' AND f.isenable = 'Y' AND f.isbuttonpower = 'Y'
                                   AND p.menuitemcode = SUBSTR(i.menuitemcode, 1, LENGTH(i.menuitemcode) - 2)
                                   AND LENGTH(i.menuitemcode) = 6 AND i.menuitemname = f.fun_name AND i.ismenutype = 'N'
                                   AND p.ismenutype = 'Y'
                                   AND b.btnownertype = 0 AND b.isenable = 'Y' AND b.isbuttonpower = 'Y'
                                   AND a.parent_id = f.cfunid AND a.ownertype = 0 AND a.code = b.btncode
                                   AND a.busi_pk = f.funcode || '_' || b.btncode
                                   AND c.subjectcode = f.funcode AND c.subjecttype = 0 AND c.isbuttonpower = 'Y'
                                   AND c.parentid = '1001A9QDESMF00000000' AND c.own_module = '1014' AND c.pk_org = '~'
                                   AND (   (b.btncode = 'QUERY' AND q.pk_resp_func IS NOT NULL)
                                        OR (b.btncode <> 'QUERY' AND q.pk_resp_func IS NULL))
                                   AND RTRIM(b.pk_btn) || ':' || f.funcode || ':' || b.btncode IN (
                                '1001A9QDESMB00000001:QD_ES_POLICY:QUERY',
                                '1001A9QDESMB00000002:QD_ES_POLICY:SAVE',
                                '1001A9QDESMB00000003:QD_ES_POLICY:MASTER',
                                '1001A9QDESMB00000004:QD_ES_SIGNLOG:QUERY',
                                '1001A9QDESMB00000005:QD_ES_SIGNLOG:EXPORT',
                                '1001A9QDESMB00000006:QD_ES_AUDITLOG:QUERY',
                                '1001A9QDESMB00000007:QD_ES_AUDITLOG:EXPORT',
                                '1001A9QDESMB00000008:QD_ES_SECURITY:QUERY',
                                '1001A9QDESMB00000009:QD_ES_SECURITY:EXPORT',
                                '1001A9QDESMB00000010:QD_ES_SECURITY:GROUP_SECURITY'
                                   )
                                   THEN 'OK' ELSE 'STOP' END AS status
                          FROM QDNC.sm_funcregister f
                          JOIN QDNC.sm_butnregister b ON b.parent_id = RTRIM(f.cfunid) AND NVL(b.dr, 0) = 0
                          LEFT JOIN QDNC.sm_busiactive_btn x ON x.pk_butn = RTRIM(b.pk_btn) AND NVL(x.dr, 0) = 0
                          LEFT JOIN QDNC.sm_busiactivereg a ON RTRIM(a.pk_busiactive) = x.pk_busiactive AND NVL(a.dr, 0) = 0
                          LEFT JOIN QDNC.sm_menuitemreg i ON i.funcode = f.funcode AND i.pk_menu = '1004ZZ10000000000FFL' AND NVL(i.dr, 0) = 0
                          LEFT JOIN QDNC.sm_menuitemreg p ON p.pk_menu = i.pk_menu AND p.pk_menuitem = '1001A9QDESMM00000000'
                               AND p.menuitemcode = '1098' AND NVL(p.dr, 0) = 0
                          LEFT JOIN QDNC.sm_busiactive_perm c ON c.pk_subject = RTRIM(f.cfunid) AND c.pk_group = '0001A9100000000005MH' AND NVL(c.dr, 0) = 0
                          LEFT JOIN QDNC.sm_resp_func q ON q.busi_pk = a.busi_pk AND q.pk_responsibility = '1001A9QDESMD00000001'
                               AND q.type = 2 AND q.systype = 'RIA' AND q.pk_group = '0001A9100000000005MH' AND NVL(q.dr, 0) = 0
                         WHERE NVL(f.dr, 0) = 0
                           AND f.funcode IN ('QD_ES_POLICY', 'QD_ES_SIGNLOG', 'QD_ES_AUDITLOG', 'QD_ES_SECURITY')
                      ) WHERE status = 'OK') = 10
                 AND (SELECT COUNT(*) FROM (
                        SELECT u.user_code, o.code AS unit_code, r.role_code, d.code AS duty_code,
                               g.busi_pk, g.type AS grant_type, g.systype,
                               CASE WHEN
                                     u.cuserid = '1001A910000000016OHR' AND u.user_code = 'yonyou02' AND u.user_type = 1 AND u.pk_group = r.pk_group AND u.enablestate = 2
                                 AND r.role_code = 'QD_ES_QUERY_990' AND r.role_type = 1 AND r.role_class = 0
                                 AND r.isorgrole = 'N' AND r.role_group_id = '1001A9100000009E4341'
                                 AND r.pk_group = '0001A9100000000005MH' AND r.pk_org = '0001A9100000003UDS6R'
                                 AND d.pk_responsibility = '1001A9QDESMD00000001' AND d.code = r.role_code
                                 AND d.pk_group = r.pk_group AND d.pk_org = r.pk_group AND d.type = 0
                                 AND p.resourceid = 'funcperm000000000000' AND p.operationcode IS NULL AND p.pk_group = r.pk_group
                                 AND s.pk_org = r.pk_org AND s.pk_group = r.pk_group AND s.isrule = '~' AND s.rulepar IS NULL
                                 AND o.code = '990' AND o.pk_group = r.pk_group AND o.enablestate = 2
                                 AND o.isbusinessunit = 'Y' AND o.islastversion = 'Y'
                                 AND g.systype = 'RIA' AND g.pk_group = r.pk_group
                                 AND (
                                     (g.type = 0 AND g.busi_pk IN ('QD_ES_POLICY', 'QD_ES_SIGNLOG', 'QD_ES_AUDITLOG', 'QD_ES_SECURITY'))
                                     OR (g.type = 2 AND g.busi_pk IN (
                                         'QD_ES_POLICY_QUERY', 'QD_ES_SIGNLOG_QUERY', 'QD_ES_AUDITLOG_QUERY', 'QD_ES_SECURITY_QUERY'
                                     ))
                                 )
                                 THEN 'OK' ELSE 'STOP' END AS status
                          FROM QDNC.sm_role r
                          JOIN QDNC.sm_user u ON u.cuserid = '1001A910000000016OHR' AND NVL(u.dr, 0) = 0
                          JOIN QDNC.sm_subject_org s ON s.subjectid = RTRIM(r.pk_role) AND NVL(s.dr, 0) = 0
                          JOIN QDNC.org_orgs o ON RTRIM(o.pk_org) = s.pk_org AND NVL(o.dr, 0) = 0
                          JOIN QDNC.sm_perm_func p ON p.subjectid = RTRIM(r.pk_role) AND NVL(p.dr, 0) = 0
                          JOIN QDNC.sm_responsibility d ON RTRIM(d.pk_responsibility) = p.ruleid AND NVL(d.dr, 0) = 0
                          JOIN QDNC.sm_resp_func g ON g.pk_responsibility = RTRIM(d.pk_responsibility) AND NVL(g.dr, 0) = 0
                         WHERE r.pk_role = '1001A9QDESMR00000001' AND NVL(r.dr, 0) = 0
                      ) WHERE status = 'OK') = 8
            THEN '1001A9QDESMU00000001'
       END AS pk_user_role,
       '1001A910000000016OHR', '1001A9QDESMR00000001', '~', TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS'), TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS')
  FROM dual;
