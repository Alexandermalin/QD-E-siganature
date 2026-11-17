-- Read only; no policy, plugin or client mapping is changed.
-- Bind :pilot_group, :pilot_org, :pilot_bill_type and (only after checking columns) :bill_pk.
-- First compare actual tables, extension tables, columns and constraints with the
-- NC metadata and client. The local BMF alone does not prove site completeness.
SELECT SYS_CONTEXT('USERENV','SESSION_USER') session_user,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') current_schema
  FROM dual;

SELECT table_name FROM user_tables
 WHERE table_name LIKE 'IC\_SAPPLY%' ESCAPE '\'
 ORDER BY table_name;

SELECT table_name,column_name,data_type,data_length,nullable,column_id
  FROM user_tab_columns
 WHERE table_name LIKE 'IC\_SAPPLY%' ESCAPE '\'
    OR table_name IN ('SCM_BATCHCODE','SM_FUNCREGISTER','SM_PARAMREGISTER','PUB_PLUGINITEM',
                      'SM_BUTNREGISTER','BD_BILLTYPE','BD_BILLTYPE2','PUB_BILLACTION','PUB_EVENTTYPE',
                      'PUB_EVENTLISTENER','QD_ES_NODE',
                      'QD_ES_POLICY','QD_ES_ORG_CONTROL')
 ORDER BY table_name,column_id;

SELECT c.table_name,c.constraint_name,c.constraint_type,c.r_constraint_name,c.status,
       cc.column_name,cc.position
  FROM user_constraints c
  LEFT JOIN user_cons_columns cc ON cc.constraint_name=c.constraint_name
 WHERE c.table_name LIKE 'IC\_SAPPLY%' ESCAPE '\'
 ORDER BY c.table_name,c.constraint_name,cc.position;

-- Only after confirming the candidate key, owner and DR columns above (QDNC
-- uses CHAR(20) for H.CGENERALHID and H.PK_ORG): compare
-- the complete returned bodies and batch references with the NC client; a count
-- alone cannot prove that all related or extension records were captured.
SELECT h.cgeneralhid,h.vbillcode,
       (SELECT COUNT(*) FROM ic_sapply_b b
         WHERE b.cgeneralhid=h.cgeneralhid AND NVL(b.dr,0)=0) body_count,
       (SELECT COUNT(*) FROM ic_sapply_b b JOIN ic_sapply_e e ON e.cgeneralbid=b.cgeneralbid
         WHERE b.cgeneralhid=h.cgeneralhid AND NVL(b.dr,0)=0) body_extension_count,
       (SELECT COUNT(*) FROM ic_sapply_b b
         WHERE b.cgeneralhid=h.cgeneralhid AND NVL(b.dr,0)=0
           AND b.pk_batchcode IS NOT NULL AND b.pk_batchcode<>'~') referenced_batch_count
  FROM ic_sapply_h h
 WHERE h.cgeneralhid=RPAD(:bill_pk,20,' ') AND h.pk_group=:pilot_group AND h.pk_org=RPAD(:pilot_org,20,' ')
   AND LENGTH(:bill_pk) BETWEEN 1 AND 20 AND LENGTH(:pilot_org) BETWEEN 1 AND 20 AND NVL(h.dr,0)=0;

SELECT f.funcode,p.paramname,p.paramvalue
  FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
 WHERE NVL(f.dr,0)=0 AND NVL(p.dr,0)=0 AND f.funcode='40080801'
   AND (p.paramname IN ('BeanConfigFilePath','transtype','pk_transtype')
        OR p.paramname LIKE 'PluginBeanConfigFilePath%')
 ORDER BY p.paramname;

-- Include shared type definitions and the native PF callback for inspection;
-- a configured class does not prove its loaded version or transaction behavior.
SELECT t.pk_billtypeid,t.pk_billtypecode,t.pk_group,t.nodecode,t.parentbilltype,t.component,
       t.checkclassname,t.dr,t.ts
  FROM bd_billtype t
 WHERE NVL(t.dr,0)=0 AND (t.nodecode='40080801' OR t.parentbilltype='4455'
                          OR t.pk_billtypecode='4455')
 ORDER BY t.pk_group,t.nodecode,t.pk_billtypecode,t.pk_billtypeid;

-- The local NC version uses CLASSTYPE=9 for PROC_CLIENT, resolves transaction
-- types to their base type, and also considers the shared 'XX' registrations.
-- Show deleted rows as well: confirm filtering and the actual runtime cache.
-- A registration does not prove invocation, side effects or loaded version.
SELECT x.pk_billtype,x.classtype,x.classname,x.code extended_code,x.pk_billtypeid,x.dr
  FROM bd_billtype2 x
 WHERE x.classtype=9 AND (x.pk_billtype IN ('4455','XX',:pilot_bill_type)
       OR x.pk_billtype IN (SELECT t.parentbilltype FROM bd_billtype t
                             WHERE t.pk_billtypecode=:pilot_bill_type AND t.parentbilltype IS NOT NULL))
 ORDER BY x.pk_billtype,x.classname,x.pk_billtypeid;

-- Check both base 4455 and the selected transaction type. An empty ACTION_TYPE
-- does not prove the loaded PF cache, workflow note or transaction behavior.
SELECT a.pk_billtype,a.pk_billtypeid,a.actiontype,a.action_type,a.pk_billaction
  FROM pub_billaction a
 WHERE NVL(a.dr,0)=0 AND a.pk_billtype IN ('4455',:pilot_bill_type)
   AND UPPER(a.actiontype) IN ('APPROVE','UNAPPROVE')
 ORDER BY a.pk_billtype,a.actiontype,a.pk_billaction;

-- The current upgrade expects NUMBER(6); legacy NUMBER(4) still accommodates
-- this candidate's maximum of 1000 outbound-request objects. This is a
-- compatibility indication only, not authorization to change the schema.
SELECT 'OWNER_OBJECT_COUNT' column_name,MAX(data_type) data_type,MAX(data_precision) data_precision,
       MAX(data_scale) data_scale,MAX(nullable) nullable,
       CASE WHEN COUNT(*)=0 THEN 'MISSING'
            WHEN COUNT(*)=1 AND MAX(data_type)='NUMBER' AND MAX(data_precision)=6
                 AND MAX(data_scale)=0 AND MAX(nullable)='Y' THEN 'MATCH_CURRENT'
            WHEN COUNT(*)=1 AND MAX(data_type)='NUMBER' AND MAX(data_precision)=4
                 AND MAX(data_scale)=0 AND MAX(nullable)='Y' THEN 'LEGACY_NUMBER4'
            ELSE 'REVIEW' END owner_count_schema
  FROM user_tab_columns
 WHERE table_name='QD_ES_REQUEST' AND column_name='OWNER_OBJECT_COUNT';

-- Inspect every registered event type and listener, including disabled/deleted rows.
-- Match listeners through PK_EVENTTYPE, not EVENTTYPECODE. Confirm SOURCEID and
-- runtime registrations separately; absence here does not prove rollback safety.
SELECT t.eventtypecode,t.sourceid,t.sourcename,t.pk_eventtype,t.dr event_dr,
       l.pk_eventlistener,l.implclassname,l.enabled,l.dr listener_dr,l.operindex
  FROM pub_eventtype t LEFT JOIN pub_eventlistener l ON l.pk_eventtype=t.pk_eventtype
 WHERE t.eventtypecode IN ('40080013','40080014','40080015','40080016')
 ORDER BY t.eventtypecode,t.pk_eventtype,l.operindex,l.pk_eventlistener;

-- Enabled triggers can write related tables during the same bill operation.
SELECT trigger_name,table_name,triggering_event,trigger_type,status
  FROM user_triggers
 WHERE table_name IN ('IC_SAPPLY_H','IC_SAPPLY_B')
 ORDER BY table_name,trigger_name;

-- Rule definitions are cached per NC server process. A database row does not
-- establish that the rule is loaded, ordered correctly, or runs in the same transaction.
SELECT p.pk_pluginitem,p.vmodulename,p.vcomponentname,p.vextendpointname,p.veventtype,
       p.vextendtype,p.vruleclass,p.vtargetclass,p.vextendmodule,p.iorder,p.localtype,p.industrytype
  FROM pub_pluginitem p
 WHERE p.vmodulename='IC' AND UPPER(p.vcomponentname)='M4455'
 ORDER BY p.vextendpointname,p.veventtype,p.iorder,p.pk_pluginitem;

-- Also inspect other buttons, including possible combined save/commit actions.
SELECT f.funcode,b.pk_btn,b.btncode,b.btnname,b.isenable,b.isbuttonpower
  FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
 WHERE NVL(f.dr,0)=0 AND NVL(b.dr,0)=0 AND f.funcode='40080801'
 ORDER BY b.btncode,b.pk_btn;

SELECT scope_id,node_code,button_code,adapter_class,operation_types,object_type,mapping_state,validated
  FROM qd_es_node
 WHERE scope_id IN ('QD-ES-41CC70D7534AF3B1','QD-ES-C31C9590475F4297',
                    'QD-ES-931D24D0DD84834A','QD-ES-6D9C72222CF4B33C',
                    'QD-ES-C7EA33244DC87A40','QD-ES-97A9C5615397566C',
                    'QD-ES-92D15BDDBB698776','QD-ES-E8E246982D39D8AD')
 ORDER BY scope_id;

SELECT c.pk_group,c.pk_org,c.master_enabled,c.control_version
  FROM qd_es_org_control c
 WHERE c.pk_group=:pilot_group AND c.pk_org=:pilot_org;

SELECT p.pk_group,p.pk_org,p.node_code,p.button_code,p.sign_enabled,p.reason_required,
       p.audit_enabled,p.enabled,p.validated,p.policy_version
  FROM qd_es_policy p
 WHERE p.pk_group=:pilot_group AND p.pk_org=:pilot_org AND p.node_code='40080801'
 ORDER BY p.button_code;
