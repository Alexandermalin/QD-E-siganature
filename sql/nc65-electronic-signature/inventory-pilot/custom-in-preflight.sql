-- Read only. Verify table and column results before running later queries.
-- Bind :pilot_group and :pilot_org to the authorized trial group and business unit.
-- These checks do not register or enable a client mapping or policy.
SELECT SYS_CONTEXT('USERENV','SESSION_USER') session_user,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') current_schema
  FROM dual;

SELECT table_name,column_name,data_type,data_length,nullable,column_id
  FROM user_tab_columns
 WHERE table_name IN ('IC_PURCHASEIN_H','IC_PURCHASEIN_B','IC_PURCHASEIN_L',
                      'IC_FINPRODIN_H','IC_FINPRODIN_B','IC_FINPRODIN_L','SCM_BATCHCODE',
                      'SM_FUNCREGISTER','SM_PARAMREGISTER','SM_BUTNREGISTER',
                      'BD_BILLTYPE','QD_ES_NODE','QD_ES_POLICY','QD_ES_ORG_CONTROL')
 ORDER BY table_name,column_id;

-- The two custom nodes are separate authorization scopes despite sharing native VOs and tables.
SELECT f.funcode,p.paramname,p.paramvalue
  FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
 WHERE NVL(f.dr,0)=0 AND NVL(p.dr,0)=0
   AND f.funcode IN ('40080602400','40080604400')
   AND (p.paramname IN ('BeanConfigFilePath','transtype','pk_transtype')
        OR p.paramname LIKE 'PluginBeanConfigFilePath%')
 ORDER BY f.funcode,p.paramname;

SELECT t.pk_billtypeid,t.pk_billtypecode,t.pk_group,t.nodecode,t.parentbilltype,t.component,t.dr,t.ts
  FROM bd_billtype t
 WHERE NVL(t.dr,0)=0
   AND (t.nodecode IN ('40080602400','40080604400')
        OR t.pk_billtypecode IN ('45-Cxx-08','46-Cxx-2'))
 ORDER BY t.nodecode,t.pk_group,t.pk_billtypecode,t.pk_billtypeid;

SELECT f.funcode,b.pk_btn,b.btncode,b.btnname,b.isenable,b.isbuttonpower
  FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
 WHERE NVL(f.dr,0)=0 AND NVL(b.dr,0)=0
   AND f.funcode IN ('40080602400','40080604400')
   AND b.btncode IN ('Save','Delete','Sign','CancelSign')
 ORDER BY f.funcode,b.btncode,b.pk_btn;

SELECT scope_id,node_code,button_code,adapter_class,operation_types,object_type,mapping_state,validated
  FROM qd_es_node
 WHERE scope_id IN ('QD-ES-2C30480472C0E461','QD-ES-87BCC12A32FE28F8',
                    'QD-ES-97BDCF1EFAE8DFF9','QD-ES-643237D91DBA2D52',
                    'QD-ES-C97F7D23C57508FB','QD-ES-2E5602AD2FF78DCA',
                    'QD-ES-EB68D7B4AED73E99','QD-ES-646556AA195B8A48')
 ORDER BY scope_id;

SELECT c.pk_group,c.pk_org,c.master_enabled,c.control_version
  FROM qd_es_org_control c
 WHERE c.pk_group=:pilot_group AND c.pk_org=:pilot_org;

SELECT p.pk_group,p.pk_org,p.node_code,p.button_code,p.sign_enabled,p.reason_required,
       p.audit_enabled,p.enabled,p.validated,p.policy_version
  FROM qd_es_policy p
 WHERE p.pk_group=:pilot_group AND p.pk_org=:pilot_org
   AND p.node_code IN ('40080602400','40080604400')
   AND p.button_code IN ('Save','Delete','Sign','CancelSign')
 ORDER BY p.node_code,p.button_code;
