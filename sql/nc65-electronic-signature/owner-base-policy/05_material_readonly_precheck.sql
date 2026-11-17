-- QDNC material pilot evidence. Read only; no binds, DDL, DML or COMMIT.
-- Catalog mapping alone does not prove that a native client action or transaction is verified.
SELECT SYS_CONTEXT('USERENV','SESSION_USER') session_user,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') current_schema,
       SYS_CONTEXT('USERENV','DB_UNIQUE_NAME') database_name,
       LOWER(SYS_CONTEXT('USERENV','SERVER_HOST')) server_host FROM dual;

SELECT scope_id,group_scope_id,node_code,button_code,adapter_class,
       operation_types,object_type,mapping_state,validated
  FROM qd_es_node
 WHERE group_scope_id IN ('QD-ES-A7FBCA61C6991D48','QD-ES-B48245BDDB687314',
                          'QD-ES-2DAE0B30BE8A146B','QD-ES-755F2D0C43B0646E')
 ORDER BY group_scope_id,node_code,button_code;

SELECT f.funcode,p.paramvalue
  FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
 WHERE f.funcode IN ('10140MAG','10140MAO') AND p.paramname='BeanConfigFilePath'
   AND NVL(f.dr,0)=0 AND NVL(p.dr,0)=0
 ORDER BY f.funcode;

SELECT metadataid,nodekey,exbean_name,ex_configpath
  FROM bd_uitabextend
 WHERE metadataid IN ('10140MAG','10140MAO') AND exbean_name IN ('material_fi','material_pu')
 ORDER BY metadataid,nodekey;

SELECT f.funcode,b.btncode,b.pk_btn
  FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
 WHERE f.funcode IN ('10140MAG','10140MAO') AND b.btncode IN ('Save','FiSave','PuSave','Assign')
   AND NVL(f.dr,0)=0 AND NVL(b.dr,0)=0
 ORDER BY f.funcode,b.btncode;

SELECT table_name,column_name,data_type,data_length,nullable
  FROM user_tab_columns
 WHERE (table_name='BD_MATERIAL' AND column_name IN ('PK_MATERIAL','PK_GROUP','PK_ORG','PK_SOURCE','DR'))
    OR (table_name='BD_MARORG' AND column_name IN ('PK_MARORG','PK_MATERIAL','PK_GROUP','PK_ORG','DR'))
    OR (table_name='BD_MATERIALFI' AND column_name IN ('PK_MATERIALFI','PK_MATERIAL','PK_GROUP','PK_ORG','DR'))
    OR (table_name='BD_MATERIALPU' AND column_name IN ('PK_MATERIALPU','PK_MATERIAL','PK_GROUP','PK_ORG','DR'))
 ORDER BY table_name,column_id;

SELECT tabid,mdid,classmdid,orgservice,modulename
  FROM bd_assign_tab
 WHERE mdid='c7dc0ccd-8872-4eee-8882-160e8f49dfad' AND NVL(dr,0)=0
 ORDER BY showindex,tabid;

SELECT table_name,trigger_name,status,triggering_event
  FROM user_triggers
 WHERE table_name='BD_MARORG' OR table_name LIKE 'BD_MATERIAL%'
 ORDER BY table_name,trigger_name;
