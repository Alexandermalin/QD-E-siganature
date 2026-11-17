-- Read-only discovery for the customer/supplier master-delete candidate.
-- This script does not install a route, lock business tables, or modify NC data.
-- Do not enable any candidate without isolated NC/Oracle integration and rollback acceptance.

SELECT SYS_CONTEXT('USERENV','SESSION_USER') AS session_user,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AS current_schema,
       SYS_CONTEXT('USERENV','DB_UNIQUE_NAME') AS database_name FROM dual;

SELECT f.funcode,b.btncode,b.pk_btn
  FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
 WHERE f.funcode IN ('10140CUG','10140CUO','10140SUG','10140SUO')
   AND b.btncode='Delete' AND NVL(f.dr,0)=0 AND NVL(b.dr,0)=0
 ORDER BY f.funcode,b.btncode;

SELECT f.funcode,p.paramname,p.paramvalue
  FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
 WHERE f.funcode IN ('10140CUG','10140CUO','10140SUG','10140SUO')
   AND (p.paramname='BeanConfigFilePath' OR p.paramname LIKE 'PluginBeanConfigFilePath%')
   AND NVL(f.dr,0)=0 AND NVL(p.dr,0)=0
 ORDER BY f.funcode,p.paramname,p.paramvalue;

SELECT scope_id,group_scope_id,node_code,button_code,adapter_class,operation_types,object_type,validated,mapping_state
  FROM qd_es_node
 WHERE group_scope_id IN ('QD-ES-35B5B1E09B5A4A80','QD-ES-83A95678666FC134')
    OR (node_code IN ('10140CUG','10140CUO','10140SUG','10140SUO') AND button_code='Delete')
 ORDER BY group_scope_id,node_code,scope_id;

-- The candidate uses table-level NOWAIT locks and therefore needs separate privilege and contention testing.
SELECT table_name,constraint_name,constraint_type,status
  FROM user_constraints
 WHERE table_name IN ('BD_CUSTBANK','BD_BANKACCBAS','BD_BANKACCSUB','BD_BANKACCUSE',
                      'BD_CUSTSTRUCTURE','BD_SUPSTRUCTURE')
 ORDER BY table_name,constraint_type,constraint_name;

SELECT b.accclass,COUNT(*) AS relation_count,
       SUM(CASE WHEN b.pk_bankaccbas IS NOT NULL AND a.pk_bankaccbas IS NULL THEN 1 ELSE 0 END) AS missing_account_count
  FROM bd_custbank b LEFT JOIN bd_bankaccbas a ON a.pk_bankaccbas=b.pk_bankaccbas
 GROUP BY b.accclass ORDER BY b.accclass;

SELECT trigger_name,table_name,status,triggering_event
  FROM user_triggers WHERE table_name IN ('BD_CUSTOMER','BD_SUPPLIER')
 ORDER BY table_name,trigger_name;
