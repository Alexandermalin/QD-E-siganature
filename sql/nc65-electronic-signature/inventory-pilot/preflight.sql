-- QDNC inventory pilot, read only. Run section 1 first and verify all referenced columns.
-- Bind :pilot_group, :pilot_org and :pilot_stockorg to explicitly selected NC keys.
-- No mapping, client configuration or policy is changed by this file.

-- 1. Verify the schema and the physical columns, including both location tables.
SELECT SYS_CONTEXT('USERENV','SESSION_USER') session_user,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') current_schema
  FROM dual;

SELECT table_name,column_name,data_type,data_length,nullable,column_id
  FROM user_tab_columns
 WHERE table_name IN ('ORG_ORGS','ORG_STOCKORG','SM_FUNCREGISTER','SM_PARAMREGISTER',
                      'SM_BUTNREGISTER','IC_PURCHASEIN_H','IC_PURCHASEIN_B','IC_PURCHASEIN_L',
                      'IC_SALEOUT_H','IC_SALEOUT_B','IC_SALEOUT_L','SCM_BATCHCODE',
                      'BD_BILLTYPE','QD_ES_NODE','QD_ES_POLICY','QD_ES_ORG_CONTROL')
 ORDER BY table_name,column_id;

-- 2. Business unit and stock organization are displayed separately: their relationship
-- must be verified in the actual NC configuration, not inferred from matching codes.
-- ORG_STOCKORG.DR is an NC convention; confirm it in section 1 before this statement.
SELECT o.pk_group,o.pk_org,o.code,o.name,o.isbusinessunit,o.islastversion,o.enablestate,o.dr
  FROM org_orgs o
 WHERE o.pk_group=:pilot_group AND o.pk_org=:pilot_org AND NVL(o.dr,0)=0;

SELECT s.pk_group,s.pk_stockorg,s.pk_org,s.code,s.name,s.islastversion,s.enablestate,s.dr
  FROM org_stockorg s
 WHERE s.pk_group=:pilot_group AND s.pk_stockorg=:pilot_stockorg AND NVL(s.dr,0)=0;

-- 3. Inspect the original JavaBeanDefinition and every installed plugin path.
SELECT f.funcode,p.paramname,p.paramvalue
  FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
 WHERE NVL(f.dr,0)=0 AND NVL(p.dr,0)=0 AND f.funcode IN ('40080602','40080802')
   AND (p.paramname='BeanConfigFilePath' OR p.paramname LIKE 'PluginBeanConfigFilePath%')
 ORDER BY f.funcode,p.paramname;

-- Compare configured transaction types across the standard and custom nodes.
-- This is evidence for a future server-side node/type allowlist, not an automatic authorization.
SELECT f.funcode,p.paramname,p.paramvalue
  FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
 WHERE NVL(f.dr,0)=0 AND NVL(p.dr,0)=0
   AND f.funcode IN ('40080602','40080602400','40080802')
   AND p.paramname IN ('transtype','pk_transtype')
 ORDER BY f.funcode,p.paramname;

-- Candidate type checks require the subtype row to belong to this group and node.
-- Review global/shared rows and actual data before deciding whether the candidate is usable.
SELECT t.pk_billtypeid,t.pk_billtypecode,t.pk_group,t.nodecode,t.parentbilltype,t.component,t.dr,t.ts
  FROM bd_billtype t
 WHERE t.pk_group=:pilot_group AND NVL(t.dr,0)=0
   AND (t.nodecode IN ('40080602','40080602400','40080802') OR t.parentbilltype IN ('45','4C'))
 ORDER BY t.nodecode,t.pk_billtypecode,t.pk_billtypeid;

SELECT f.funcode,b.pk_btn,b.btncode,b.btnname,b.isenable,b.isbuttonpower
  FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
 WHERE NVL(f.dr,0)=0 AND NVL(b.dr,0)=0 AND f.funcode IN ('40080602','40080802')
   AND b.btncode IN ('Save','Delete','Sign','CancelSign')
 ORDER BY f.funcode,b.btncode,b.pk_btn;

-- 4. Check only the eight formal first-batch scopes and the selected unit's current policy.
SELECT scope_id,node_code,button_code,adapter_class,operation_types,object_type,mapping_state,validated
  FROM qd_es_node
 WHERE scope_id IN ('QD-ES-43CFB46A5D6F614C','QD-ES-BBB6F605E37071ED',
                    'QD-ES-3DD170546891640A','QD-ES-888EEE9C09C7EA2E',
                    'QD-ES-11F0F608424EC25A','QD-ES-41800183BD27AB33',
                    'QD-ES-A3F1772B910FC6A8','QD-ES-29F2810C210A1B58')
 ORDER BY scope_id;

SELECT c.pk_group,c.pk_org,c.master_enabled,c.control_version
  FROM qd_es_org_control c
 WHERE c.pk_group=:pilot_group AND c.pk_org=:pilot_org;

SELECT p.pk_group,p.pk_org,p.node_code,p.button_code,p.sign_enabled,p.reason_required,
       p.audit_enabled,p.enabled,p.validated,p.policy_version
  FROM qd_es_policy p
 WHERE p.pk_group=:pilot_group AND p.pk_org=:pilot_org
   AND p.node_code IN ('40080602','40080802')
   AND p.button_code IN ('Save','Delete','Sign','CancelSign')
 ORDER BY p.node_code,p.button_code;
