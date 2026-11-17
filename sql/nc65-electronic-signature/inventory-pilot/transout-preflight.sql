-- Read only. Run section 1 first and verify site columns before later statements.
-- Bind :pilot_group and :pilot_org to the explicitly selected NC group and business unit.
-- No client mapping, node registration or policy is changed by this file.

SELECT SYS_CONTEXT('USERENV','SESSION_USER') session_user,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') current_schema
  FROM dual;

SELECT table_name,column_name,data_type,data_length,nullable,column_id
  FROM user_tab_columns
 WHERE table_name IN ('IC_TRANSOUT_H','IC_TRANSOUT_B','IC_TRANSOUT_L','SCM_BATCHCODE',
                      'SM_FUNCREGISTER','SM_PARAMREGISTER','SM_BUTNREGISTER',
                      'BD_BILLTYPE','QD_ES_NODE','QD_ES_POLICY','QD_ES_ORG_CONTROL')
 ORDER BY table_name,column_id;

-- Only after confirming the candidate join and DR columns above, bind :bill_pk
-- to a live, authorized m4y bill and compare both counts with the NC client.
-- A zero orphan count alone does not prove that the relationship is complete.
SELECT h.cgeneralhid,h.vbillcode,
       (SELECT COUNT(*) FROM ic_transout_b b
         WHERE b.cgeneralhid=h.cgeneralhid AND NVL(b.dr,0)=0) body_count,
       (SELECT COUNT(*) FROM ic_transout_l l
         WHERE l.cgeneralhid=h.cgeneralhid AND NVL(l.dr,0)=0) location_count,
       (SELECT COUNT(*) FROM ic_transout_l l
         WHERE l.cgeneralhid=h.cgeneralhid AND NVL(l.dr,0)=0
           AND NOT EXISTS (SELECT 1 FROM ic_transout_b b
                            WHERE b.cgeneralhid=h.cgeneralhid AND b.cgeneralbid=l.cgeneralbid
                              AND NVL(b.dr,0)=0)) orphan_location_count
  FROM ic_transout_h h
 WHERE h.cgeneralhid=:bill_pk AND h.pk_group=:pilot_group AND h.pk_org=:pilot_org AND NVL(h.dr,0)=0;

-- Compare 4Y and 4y metadata; do not infer that they are interchangeable in native PF.
-- The four formal scope IDs and original action metadata are taken from the local coverage matrix.
SELECT f.funcode,p.paramname,p.paramvalue
  FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
 WHERE NVL(f.dr,0)=0 AND NVL(p.dr,0)=0 AND f.funcode='40080820'
   AND (p.paramname IN ('BeanConfigFilePath','transtype','pk_transtype')
        OR p.paramname LIKE 'PluginBeanConfigFilePath%')
 ORDER BY f.funcode,p.paramname;

SELECT t.pk_billtypeid,t.pk_billtypecode,t.pk_group,t.nodecode,t.parentbilltype,t.component,t.dr,t.ts
  FROM bd_billtype t
 WHERE t.pk_group=:pilot_group AND NVL(t.dr,0)=0
   AND (t.nodecode='40080820' OR t.parentbilltype IN ('4Y','4y') OR t.pk_billtypecode IN ('4Y','4y'))
 ORDER BY t.nodecode,t.pk_billtypecode,t.pk_billtypeid;

SELECT f.funcode,b.pk_btn,b.btncode,b.btnname,b.isenable,b.isbuttonpower
  FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
 WHERE NVL(f.dr,0)=0 AND NVL(b.dr,0)=0 AND f.funcode='40080820'
   AND b.btncode IN ('Save','Delete','Sign','CancelSign')
 ORDER BY b.btncode,b.pk_btn;

SELECT scope_id,node_code,button_code,adapter_class,operation_types,object_type,mapping_state,validated
  FROM qd_es_node
 WHERE scope_id IN ('QD-ES-8EAA046EE4BC2096','QD-ES-A38A5E0163F76F5A',
                    'QD-ES-8767AE5097F761E6','QD-ES-DC0D566DC68C848E')
 ORDER BY scope_id;

SELECT c.pk_group,c.pk_org,c.master_enabled,c.control_version
  FROM qd_es_org_control c
 WHERE c.pk_group=:pilot_group AND c.pk_org=:pilot_org;

SELECT p.pk_group,p.pk_org,p.node_code,p.button_code,p.sign_enabled,p.reason_required,
       p.audit_enabled,p.enabled,p.validated,p.policy_version
  FROM qd_es_policy p
 WHERE p.pk_group=:pilot_group AND p.pk_org=:pilot_org AND p.node_code='40080820'
   AND p.button_code IN ('Save','Delete','Sign','CancelSign')
 ORDER BY p.button_code;
