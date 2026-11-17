-- QDNC, 1.3.0. Run in the stopped maintenance window after installing the matching overlays.
-- Enables the candidate save routes for isolated client acceptance, without setting any policy.
-- Do not run with autocommit. Record the precheck results before executing this block.
DECLARE
  v_save CONSTANT VARCHAR2(64) := 'QD-ES-A7FBCA61C6991D48';
  v_fi CONSTANT VARCHAR2(64) := 'QD-ES-B48245BDDB687314';
  v_pu CONSTANT VARCHAR2(64) := 'QD-ES-2DAE0B30BE8A146B';
  v_base_adapter CONSTANT VARCHAR2(100) := 'nc.itf.uapbd.qd.esign.IMaterialOperationAdapter';
  v_page_adapter CONSTANT VARCHAR2(100) := 'nc.itf.uapbd.qd.esign.IMaterialOrgInfoOperationAdapter';
  v_count NUMBER;

  PROCEDURE require_one(p_count NUMBER, p_message VARCHAR2) IS
  BEGIN
    IF p_count<>1 THEN RAISE_APPLICATION_ERROR(-20271,p_message); END IF;
  END;

  PROCEDURE register_org_page(p_scope VARCHAR2, p_button VARCHAR2, p_object VARCHAR2) IS
    v_alias CONSTANT VARCHAR2(64) := p_scope || '@10140MAO';
  BEGIN
    SELECT COUNT(*) INTO v_count FROM qd_es_node
     WHERE scope_id=p_scope||'@10140MAG' AND group_scope_id=p_scope
       AND node_code='10140MAG' AND button_code=p_button AND adapter_class=v_page_adapter
       AND operation_types='UPDATE' AND object_type=p_object AND mapping_state='PILOT' AND validated='Y';
    require_one(v_count,'Expected verified group material tab mapping');
    SELECT COUNT(*) INTO v_count FROM qd_es_node
     WHERE node_code='10140MAO' AND button_code=p_button AND scope_id<>v_alias;
    IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20272,'Conflicting organization material tab mapping'); END IF;
    MERGE INTO qd_es_node dst
    USING (SELECT node_name,button_name FROM qd_es_node
            WHERE scope_id=p_scope AND group_scope_id=p_scope) src
       ON (dst.scope_id=v_alias)
    WHEN MATCHED THEN UPDATE SET dst.validated='Y'
      WHERE dst.group_scope_id=p_scope AND dst.node_code='10140MAO' AND dst.button_code=p_button
        AND dst.adapter_class=v_page_adapter AND dst.operation_types='UPDATE'
        AND dst.object_type=p_object AND dst.mapping_state='PILOT'
    WHEN NOT MATCHED THEN INSERT
      (scope_id,group_scope_id,node_code,node_name,button_code,button_name,adapter_class,
       operation_types,object_type,validated,mapping_state)
      VALUES (v_alias,p_scope,'10140MAO',src.node_name,p_button,src.button_name,v_page_adapter,
              'UPDATE',p_object,'Y','PILOT');
    require_one(SQL%ROWCOUNT,'Organization material tab mapping differs from the expected route');
  END;
BEGIN
  SAVEPOINT qd_owner_material_routes;
  IF SYS_CONTEXT('USERENV','SESSION_USER')<>'QDNC'
      OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA')<>'QDNC'
      OR SYS_CONTEXT('USERENV','DB_UNIQUE_NAME')<>'DB'
      OR LOWER(SYS_CONTEXT('USERENV','SERVER_HOST'))<>'ncoa-db' THEN
    RAISE_APPLICATION_ERROR(-20270,'Expected QDNC test environment');
  END IF;
  LOCK TABLE qd_es_schema_version IN SHARE MODE NOWAIT;
  LOCK TABLE qd_es_request IN SHARE MODE NOWAIT;
  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.3.0'
    AND NOT EXISTS (SELECT 1 FROM qd_es_schema_version WHERE version_no='1.3.0-RB');
  require_one(v_count,'Expected active owner-policy schema 1.3.0');
  SELECT COUNT(*) INTO v_count FROM qd_es_request
   WHERE node_code IN ('10140MAG','10140MAO') AND button_code IN ('Save','FiSave','PuSave')
     AND business_state IN ('RUNNING','PENDING','READY');
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20273,'Drain and retire existing material save requests'); END IF;

  SELECT COUNT(*) INTO v_count FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
   WHERE f.funcode='10140MAO' AND NVL(f.dr,0)=0 AND NVL(p.dr,0)=0
     AND p.paramname='BeanConfigFilePath' AND p.paramvalue='nc/ui/uapbd/qd/esign/QdMaterialOrgConfig.xml';
  require_one(v_count,'Expected the organization material client overlay');
  SELECT COUNT(*) INTO v_count FROM sm_funcregister f JOIN sm_butnregister b
      ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
   WHERE f.funcode='10140MAO' AND NVL(f.dr,0)=0 AND NVL(b.dr,0)=0
     AND b.btncode='Save' AND b.pk_btn='0001Z010140MAO000082';
  require_one(v_count,'Expected the native organization material Save button');
  SELECT COUNT(*) INTO v_count FROM bd_uitabextend
   WHERE metadataid='10140MAG'
     AND ((nodekey='fi' AND exbean_name='material_fi'
           AND ex_configpath='nc/ui/bd/material/fi/material_fi_config.xml')
       OR (nodekey='pu' AND exbean_name='material_pu'
           AND ex_configpath='nc/ui/bd/material/pu/material_pu_config.xml'));
  IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20274,'Native finance and purchasing tabs differ'); END IF;

  UPDATE qd_es_node SET operation_types='INSERT,UPDATE'
   WHERE scope_id=v_save AND group_scope_id=v_save AND node_code='10140MAO' AND button_code='Save'
     AND operation_types IN ('INSERT','INSERT,UPDATE') AND object_type='BD_MATERIAL'
     AND adapter_class=v_base_adapter AND mapping_state='PILOT' AND validated='Y';
  require_one(SQL%ROWCOUNT,'Expected the canonical organization material save route');
  register_org_page(v_fi,'FiSave','BD_MATERIAL_FI');
  register_org_page(v_pu,'PuSave','BD_MATERIAL_PU');
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_owner_material_routes;
    RAISE;
END;
/

SELECT scope_id,group_scope_id,node_code,button_code,operation_types,object_type,mapping_state,validated
  FROM qd_es_node
 WHERE node_code IN ('10140MAG','10140MAO') AND button_code IN ('Save','FiSave','PuSave')
 ORDER BY node_code,button_code;
