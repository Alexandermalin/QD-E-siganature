-- Read only. Run section 1 first and verify site columns before later statements.
-- Bind :pilot_group and :pilot_org to the explicitly selected NC group and business unit.
-- No client mapping, node registration or policy is changed by this file.

SELECT SYS_CONTEXT('USERENV','SESSION_USER') session_user,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') current_schema
  FROM dual;

SELECT table_name,column_name,data_type,data_length,nullable,column_id
  FROM user_tab_columns
 WHERE table_name IN ('IC_OPENBAL_H','IC_OPENBAL_B','IC_OPENBAL_L','SCM_BATCHCODE',
                      'SM_FUNCREGISTER','SM_PARAMREGISTER','SM_BUTNREGISTER',
                      'BD_BILLTYPE','PUB_PLUGINITEM','QD_ES_NODE','QD_ES_POLICY','QD_ES_ORG_CONTROL')
 ORDER BY table_name,column_id;

-- The four formal scope IDs and original action metadata are taken from the local coverage matrix.
SELECT f.funcode,p.paramname,p.paramvalue
  FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
 WHERE NVL(f.dr,0)=0 AND NVL(p.dr,0)=0 AND f.funcode='40080402'
   AND (p.paramname IN ('BeanConfigFilePath','transtype','pk_transtype')
        OR p.paramname LIKE 'PluginBeanConfigFilePath%')
 ORDER BY f.funcode,p.paramname;

SELECT t.pk_billtypeid,t.pk_billtypecode,t.pk_group,t.nodecode,t.parentbilltype,t.component,t.dr,t.ts
  FROM bd_billtype t
 WHERE t.pk_group=:pilot_group AND NVL(t.dr,0)=0
   AND (t.nodecode='40080402' OR t.parentbilltype='40')
 ORDER BY t.nodecode,t.pk_billtypecode,t.pk_billtypeid;

-- Native NC loads this extension point from PUB_PLUGINITEM and caches it per server process.
-- Run only after verifying the columns above; no DR filter is assumed for this NC table.
SELECT p.pk_pluginitem,p.vmodulename,p.vcomponentname,p.vextendpointname,p.veventtype,
       p.vextendtype,p.vruleclass,p.vtargetclass,p.vextendmodule,p.iorder,p.localtype,p.industrytype
  FROM pub_pluginitem p
 WHERE p.vmodulename='IC' AND UPPER(p.vcomponentname)='M40'
   AND p.vextendpointname='nc.impl.ic.m40.action.InsertAction'
 ORDER BY p.iorder,p.pk_pluginitem;

SELECT f.funcode,b.pk_btn,b.btncode,b.btnname,b.isenable,b.isbuttonpower
  FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
 WHERE NVL(f.dr,0)=0 AND NVL(b.dr,0)=0 AND f.funcode='40080402'
   AND b.btncode IN ('Save','Delete','Sign','CancelSign')
 ORDER BY b.btncode,b.pk_btn;

SELECT scope_id,node_code,button_code,adapter_class,operation_types,object_type,mapping_state,validated
  FROM qd_es_node
 WHERE scope_id IN ('QD-ES-F7DBD94ACB7D9546','QD-ES-2A81D447CED47CF6',
                    'QD-ES-C10800F14217C2EA','QD-ES-AAFA9227A1F96898')
 ORDER BY scope_id;

SELECT c.pk_group,c.pk_org,c.master_enabled,c.control_version
  FROM qd_es_org_control c
 WHERE c.pk_group=:pilot_group AND c.pk_org=:pilot_org;

SELECT p.pk_group,p.pk_org,p.node_code,p.button_code,p.sign_enabled,p.reason_required,
       p.audit_enabled,p.enabled,p.validated,p.policy_version
  FROM qd_es_policy p
 WHERE p.pk_group=:pilot_group AND p.pk_org=:pilot_org AND p.node_code='40080402'
   AND p.button_code IN ('Save','Delete','Sign','CancelSign')
 ORDER BY p.button_code;
