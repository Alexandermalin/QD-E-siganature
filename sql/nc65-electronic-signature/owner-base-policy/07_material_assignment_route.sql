-- QDNC, 1.3.0. Registers the fast Assign button for controlled client acceptance.
-- Run in the stopped window with the matching service and client classes installed.
-- This script does not change policy switches. Inspect the result before committing.
DECLARE
  v_scope CONSTANT VARCHAR2(64) := 'QD-ES-755F2D0C43B0646E';
  v_alias CONSTANT VARCHAR2(64) := v_scope||'@10140MAG';
  v_adapter CONSTANT VARCHAR2(100) := 'nc.itf.uapbd.qd.esign.IMaterialAssignmentService';
  v_count NUMBER;
BEGIN
  SAVEPOINT qd_owner_material_assignment;
  IF SYS_CONTEXT('USERENV','SESSION_USER')<>'QDNC'
      OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA')<>'QDNC'
      OR SYS_CONTEXT('USERENV','DB_UNIQUE_NAME')<>'DB'
      OR LOWER(SYS_CONTEXT('USERENV','SERVER_HOST'))<>'ncoa-db' THEN
    RAISE_APPLICATION_ERROR(-20280,'Expected QDNC test environment');
  END IF;
  LOCK TABLE qd_es_schema_version IN SHARE MODE NOWAIT;
  LOCK TABLE qd_es_request IN SHARE MODE NOWAIT;
  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.0'
    AND NOT EXISTS (SELECT 1 FROM qd_es_schema_version WHERE version_no='1.3.0-RB');
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20281,'Expected active owner-policy schema 1.3.0'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_request
   WHERE node_code IN ('10140MAG','10140MAO') AND button_code='Assign'
     AND business_state IN ('RUNNING','PENDING','READY','UNKNOWN');
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20282,'Drain and reconcile existing material assignment requests'); END IF;
  SELECT COUNT(*) INTO v_count FROM sm_funcregister f
    JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
    JOIN sm_paramregister p ON p.parentid=f.cfunid
   WHERE NVL(f.dr,0)=0 AND NVL(b.dr,0)=0 AND NVL(p.dr,0)=0
     AND b.btncode='Assign' AND p.paramname='BeanConfigFilePath'
     AND ((f.funcode='10140MAG' AND b.pk_btn='0001Z010140MAG000003'
            AND p.paramvalue='nc/ui/uapbd/qd/esign/QdMaterialGroupConfig.xml')
       OR (f.funcode='10140MAO' AND b.pk_btn='0001Z010140MAO000003'
            AND p.paramvalue='nc/ui/uapbd/qd/esign/QdMaterialOrgConfig.xml'));
  IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20283,'Material fast assignment registration differs'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_node
   WHERE node_code IN ('10140MAG','10140MAO') AND button_code='Assign' AND scope_id NOT IN (v_scope,v_alias);
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20284,'Conflicting material assignment mapping'); END IF;

  UPDATE qd_es_node SET node_code='10140MAO',button_code='Assign',adapter_class=v_adapter,
         operation_types='ASSIGN',object_type='BD_MATERIAL',mapping_state='PILOT',validated='Y'
   WHERE scope_id=v_scope AND group_scope_id=v_scope
     AND ((node_code IS NULL AND button_code IS NULL AND adapter_class IS NULL
            AND operation_types IS NULL AND object_type IS NULL AND mapping_state='UNMAPPED' AND validated='N')
       OR (node_code='10140MAO' AND button_code='Assign' AND adapter_class=v_adapter
            AND operation_types='ASSIGN' AND object_type='BD_MATERIAL' AND mapping_state='PILOT'));
  IF SQL%ROWCOUNT<>1 THEN RAISE_APPLICATION_ERROR(-20285,'Expected the canonical material assignment scope'); END IF;
  MERGE INTO qd_es_node dst
  USING (SELECT node_name,button_name FROM qd_es_node WHERE scope_id=v_scope) src
     ON (dst.scope_id=v_alias)
  WHEN MATCHED THEN UPDATE SET dst.validated='Y'
    WHERE dst.group_scope_id=v_scope AND dst.node_code='10140MAG' AND dst.button_code='Assign'
      AND dst.adapter_class=v_adapter AND dst.operation_types='ASSIGN'
      AND dst.object_type='BD_MATERIAL' AND dst.mapping_state='PILOT'
  WHEN NOT MATCHED THEN INSERT
    (scope_id,group_scope_id,node_code,node_name,button_code,button_name,adapter_class,
     operation_types,object_type,validated,mapping_state)
    VALUES (v_alias,v_scope,'10140MAG',src.node_name,'Assign',src.button_name,v_adapter,
            'ASSIGN','BD_MATERIAL','Y','PILOT');
  IF SQL%ROWCOUNT<>1 THEN RAISE_APPLICATION_ERROR(-20286,'Expected the group material assignment alias'); END IF;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_owner_material_assignment;
    RAISE;
END;
/

SELECT scope_id,group_scope_id,node_code,button_code,operation_types,object_type,mapping_state,validated
  FROM qd_es_node
 WHERE group_scope_id='QD-ES-755F2D0C43B0646E'
 ORDER BY node_code;
