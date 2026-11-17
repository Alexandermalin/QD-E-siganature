-- QDNC candidate precheck, read only. Does not register a route or enable any policy.
-- Execute against the intended NC schema only after confirming the test connection.
SELECT t.pk_batchupdatetab,t.mdid,t.code AS tab_code,t.nodekey,t.ismaintab,
       a.pk_batchupdateattr,a.code AS attr_code,a.relatedattr,a.validator,a.editlistener,a.service,a.issingleorg
  FROM bd_batchupdatetab t LEFT JOIN bd_batchupdateattr a
    ON a.pk_batchupdatetab=t.pk_batchupdatetab AND NVL(a.dr,0)=0
 WHERE t.mdid='c7dc0ccd-8872-4eee-8882-160e8f49dfad' AND NVL(t.dr,0)=0
 ORDER BY t.pk_batchupdatetab,a.showorder,a.pk_batchupdateattr;

-- These are the full-row tables that the candidate must be able to snapshot.
SELECT table_name,column_name,data_type,data_length,nullable
  FROM user_tab_columns
 WHERE table_name IN ('BD_MATERIAL','BD_MATERIAL_V','BD_MATERIALCONVERT','BD_MATERIALTAXTYPE',
                      'BD_BATCHUPDATETAB','BD_BATCHUPDATEATTR')
 ORDER BY table_name,column_id;

-- Check the candidate's latest-material/version identity assumption without exposing archive values.
SELECT COUNT(*) AS latest_materials,
       SUM(CASE WHEN m.pk_source IS NULL THEN 1 ELSE 0 END) AS missing_source,
       SUM(CASE WHEN v.pk_source IS NULL THEN 1 ELSE 0 END) AS missing_version,
       SUM(CASE WHEN v.pk_source IS NOT NULL AND
           (NVL(v.pk_group,'~')<>NVL(m.pk_group,'~') OR NVL(v.pk_org,'~')<>NVL(m.pk_org,'~')
            OR NVL(v.pk_material,'~')<>NVL(m.pk_material,'~')) THEN 1 ELSE 0 END) AS version_mismatch
  FROM bd_material m LEFT JOIN bd_material_v v ON v.pk_source=m.pk_source
 WHERE NVL(m.dr,0)=0 AND m.latest='Y';

-- Native version synchronization addresses the source globally; group is deliberately not a filter here.
SELECT COUNT(*) AS sources_with_multiple_latest
  FROM (SELECT pk_source FROM bd_material WHERE NVL(dr,0)=0 AND latest='Y' AND pk_source IS NOT NULL
         GROUP BY pk_source HAVING COUNT(*)>1);
