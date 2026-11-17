-- QDNC only. Run the SELECT first. REFRESH rows are the intended write scope.
-- After reviewing the result, run the UPDATE in a manual-commit session.
-- Run the same SELECT again before committing. All eleven rows should then be OK.
-- Load the new versions in the NC middleware, then restart the Java client.

SELECT v.cachetabname, v.installed_ts, v.cachetabversion,
       CASE
         WHEN v.installed_ts IS NULL OR v.updatelock IS NOT NULL
              OR v.cachetabversion IS NULL OR NVL(v.dr, 0) <> 0 THEN 'STOP'
         WHEN v.cachetabversion < v.installed_ts THEN 'REFRESH'
         ELSE 'OK'
       END AS status
  FROM (
    SELECT c.cachetabname, c.cachetabversion, c.updatelock, c.dr, (
      SELECT MAX(u.ts)
      FROM QDNC.sm_user_role u
     WHERE u.pk_user_role = '1001A9QDESMU00000001'
       AND u.pk_role = '1001A9QDESMR00000001'
       AND u.cuserid = '1001A910000000016OHR' AND NVL(u.dr, 0) = 0
       AND SYS_CONTEXT('USERENV', 'SESSION_USER') = 'QDNC'
       AND SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') = 'QDNC'
       AND SYS_CONTEXT('USERENV', 'DB_UNIQUE_NAME') = 'DB'
       AND SYS_CONTEXT('USERENV', 'SERVICE_NAME') = 'DB'
       AND LOWER(SYS_CONTEXT('USERENV', 'SERVER_HOST')) = 'ncoa-db'
       AND (SELECT COUNT(*)
              FROM QDNC.sm_funcregister f
             WHERE f.funcode IN ('QD_ES_POLICY', 'QD_ES_SIGNLOG', 'QD_ES_AUDITLOG', 'QD_ES_SECURITY')
               AND f.parent_id = '1001A9QDESMF00000000'
               AND f.isenable = 'Y' AND NVL(f.dr, 0) = 0) = 4
       AND (SELECT COUNT(*)
              FROM QDNC.sm_menuitemreg i
             WHERE i.pk_menu = '1004ZZ10000000000FFL'
               AND i.menuitemcode IN ('109801', '109802', '109803', '109804')
               AND i.funcode IN ('QD_ES_POLICY', 'QD_ES_SIGNLOG', 'QD_ES_AUDITLOG', 'QD_ES_SECURITY')
               AND i.ismenutype = 'N' AND NVL(i.dr, 0) = 0) = 4
       AND TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') >= u.ts
    ) AS installed_ts
    FROM QDNC.bd_cachetabversion c
    WHERE (c.cachetabname, c.pk_cachetabversion) IN (
           ('sm_busiactive_btn', '0001A910000000001NTV'),
           ('sm_busiactive_perm', '0001A910000000001NSN'),
           ('sm_busiactivereg', '0001A91000000004K5JV'),
           ('sm_funcregister', '0001A9100000000005M7'),
           ('sm_menuitemreg', '0001A9100000000005N3'),
           ('sm_perm_func', '0001A910000000001NTX'),
           ('sm_resp_func', '0001A910000000001NSV'),
           ('sm_responsibility', '0001A910000000001NSR'),
           ('sm_role', '0001A910000000001NSO'),
           ('sm_subject_org', '0001A910000000001NTY'),
           ('sm_user_role', '0001A910000000001NTW')
       )
  ) v
 ORDER BY v.cachetabname;

-- Match the exact version records registered by NC. A newer version is retained.
UPDATE QDNC.bd_cachetabversion c
   SET cachetabversion = TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS')
 WHERE (c.cachetabname, c.pk_cachetabversion) IN (
           ('sm_busiactive_btn', '0001A910000000001NTV'),
           ('sm_busiactive_perm', '0001A910000000001NSN'),
           ('sm_busiactivereg', '0001A91000000004K5JV'),
           ('sm_funcregister', '0001A9100000000005M7'),
           ('sm_menuitemreg', '0001A9100000000005N3'),
           ('sm_perm_func', '0001A910000000001NTX'),
           ('sm_resp_func', '0001A910000000001NSV'),
           ('sm_responsibility', '0001A910000000001NSR'),
           ('sm_role', '0001A910000000001NSO'),
           ('sm_subject_org', '0001A910000000001NTY'),
           ('sm_user_role', '0001A910000000001NTW')
       )
   AND NVL(c.dr, 0) = 0 AND c.updatelock IS NULL
   AND c.cachetabversion < (
    SELECT MAX(u.ts)
      FROM QDNC.sm_user_role u
     WHERE u.pk_user_role = '1001A9QDESMU00000001'
       AND u.pk_role = '1001A9QDESMR00000001'
       AND u.cuserid = '1001A910000000016OHR' AND NVL(u.dr, 0) = 0
       AND SYS_CONTEXT('USERENV', 'SESSION_USER') = 'QDNC'
       AND SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') = 'QDNC'
       AND SYS_CONTEXT('USERENV', 'DB_UNIQUE_NAME') = 'DB'
       AND SYS_CONTEXT('USERENV', 'SERVICE_NAME') = 'DB'
       AND LOWER(SYS_CONTEXT('USERENV', 'SERVER_HOST')) = 'ncoa-db'
       AND (SELECT COUNT(*)
              FROM QDNC.sm_funcregister f
             WHERE f.funcode IN ('QD_ES_POLICY', 'QD_ES_SIGNLOG', 'QD_ES_AUDITLOG', 'QD_ES_SECURITY')
               AND f.parent_id = '1001A9QDESMF00000000'
               AND f.isenable = 'Y' AND NVL(f.dr, 0) = 0) = 4
       AND (SELECT COUNT(*)
              FROM QDNC.sm_menuitemreg i
             WHERE i.pk_menu = '1004ZZ10000000000FFL'
               AND i.menuitemcode IN ('109801', '109802', '109803', '109804')
               AND i.funcode IN ('QD_ES_POLICY', 'QD_ES_SIGNLOG', 'QD_ES_AUDITLOG', 'QD_ES_SECURITY')
               AND i.ismenutype = 'N' AND NVL(i.dr, 0) = 0) = 4
       AND TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') >= u.ts
       );
