-- Run the three SELECT statements in the manual-commit session after registration.
-- Expected results are 57 active rows across 12 tables, ten OK button rows and eight pilot grants.
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
    SELECT 'sm_user_role', COUNT(*), 1 FROM QDNC.sm_user_role
     WHERE NVL(dr, 0) = 0 AND (pk_role = '1001A9QDESMR00000001')
)
SELECT table_name, observed_count, expected_count,
       CASE WHEN observed_count = expected_count THEN 'OK' ELSE 'STOP' END AS status
  FROM counts
 ORDER BY table_name;

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
 ORDER BY f.funcode, b.btncode;

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
         AND ur.pk_org = '~' AND ur.enabledate <= TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS')
         AND (ur.disabledate IS NULL OR ur.disabledate > TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS'))
         AND g.systype = 'RIA' AND g.pk_group = r.pk_group
         AND (
             (g.type = 0 AND g.busi_pk IN ('QD_ES_POLICY', 'QD_ES_SIGNLOG', 'QD_ES_AUDITLOG', 'QD_ES_SECURITY'))
             OR (g.type = 2 AND g.busi_pk IN (
                 'QD_ES_POLICY_QUERY', 'QD_ES_SIGNLOG_QUERY', 'QD_ES_AUDITLOG_QUERY', 'QD_ES_SECURITY_QUERY'
             ))
         )
         THEN 'OK' ELSE 'STOP' END AS status
  FROM QDNC.sm_role r
  JOIN QDNC.sm_user_role ur ON ur.pk_role = RTRIM(r.pk_role) AND NVL(ur.dr, 0) = 0
  JOIN QDNC.sm_user u ON RTRIM(u.cuserid) = ur.cuserid AND NVL(u.dr, 0) = 0
  JOIN QDNC.sm_subject_org s ON s.subjectid = RTRIM(r.pk_role) AND NVL(s.dr, 0) = 0
  JOIN QDNC.org_orgs o ON RTRIM(o.pk_org) = s.pk_org AND NVL(o.dr, 0) = 0
  JOIN QDNC.sm_perm_func p ON p.subjectid = RTRIM(r.pk_role) AND NVL(p.dr, 0) = 0
  JOIN QDNC.sm_responsibility d ON RTRIM(d.pk_responsibility) = p.ruleid AND NVL(d.dr, 0) = 0
  JOIN QDNC.sm_resp_func g ON g.pk_responsibility = RTRIM(d.pk_responsibility) AND NVL(g.dr, 0) = 0
 WHERE r.pk_role = '1001A9QDESMR00000001' AND NVL(r.dr, 0) = 0
 ORDER BY g.type, g.busi_pk;
