-- Isolated QDNC test only. Stop user operations first and disable autocommit.
-- Bind :pilot_group and :pilot_mode ('REGISTER' or 'DISABLE'). No implicit commit.
-- Set both native BeanConfigFilePath parameters as documented before REGISTER.
DECLARE
  v_group CONSTANT VARCHAR2(20) := :pilot_group;
  v_mode CONSTANT VARCHAR2(8) := :pilot_mode;
  v_scope CONSTANT VARCHAR2(64) := 'QD-ES-A7FBCA61C6991D48';
  v_alias CONSTANT VARCHAR2(64) := 'QD-ES-A7FBCA61C6991D48@10140MAG';
  v_adapter CONSTANT VARCHAR2(300) := 'nc.itf.uapbd.qd.esign.IMaterialOperationAdapter';
  v_count NUMBER;
  v_now CHAR(19) := TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS');
BEGIN
  SAVEPOINT qd_group_material_save;
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'QDNC'
      OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'QDNC'
      OR v_group IS NULL OR v_group <> '0001A9100000000005MH'
      OR v_mode IS NULL OR v_mode NOT IN ('REGISTER','DISABLE') THEN
    RAISE_APPLICATION_ERROR(-20140, 'Expected the approved QDNC test group and REGISTER or DISABLE mode');
  END IF;
  LOCK TABLE qd_es_schema_version IN SHARE MODE NOWAIT;
  LOCK TABLE qd_es_request IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_bd_control IN SHARE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.2.0';
  IF v_count <> 1 THEN
    RAISE_APPLICATION_ERROR(-20141, 'Apply group base policy upgrade 1.2.0 first');
  END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.2.0-RB';
  IF v_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20142, 'Group base policy upgrade is rolled back');
  END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_request
   WHERE node_code IN ('10140MAG','10140MAO') AND button_code='Save' AND business_state='RUNNING';
  IF v_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20143, 'Reconcile running material save requests first');
  END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_bd_control WHERE master_enabled='Y';
  IF v_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20144, 'Close group master switches before changing the shared material mapping');
  END IF;
  IF v_mode='DISABLE' THEN
    UPDATE qd_es_node SET validated='N'
     WHERE scope_id=v_alias AND group_scope_id=v_scope AND node_code='10140MAG'
       AND button_code='Save' AND adapter_class=v_adapter;
    UPDATE qd_es_request
       SET business_state='EXPIRED',error_code='MATERIAL_GROUP_PILOT_DISABLED',finished_at=v_now
     WHERE node_code='10140MAG' AND button_code='Save' AND business_state IN ('PENDING','READY');
    RETURN;
  END IF;

  SELECT COUNT(*) INTO v_count
    FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
   WHERE NVL(f.dr,0)=0 AND NVL(p.dr,0)=0 AND p.paramname='BeanConfigFilePath'
     AND ((f.funcode='10140MAG' AND p.paramvalue='nc/ui/uapbd/qd/esign/QdMaterialGroupConfig.xml')
       OR (f.funcode='10140MAO' AND p.paramvalue='nc/ui/uapbd/qd/esign/QdMaterialOrgConfig.xml'));
  IF v_count <> 2 THEN
    RAISE_APPLICATION_ERROR(-20145, 'Expected both exact material client bindings');
  END IF;
  SELECT COUNT(*) INTO v_count
    FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
   WHERE NVL(f.dr,0)=0 AND NVL(b.dr,0)=0 AND b.btncode='Save'
     AND ((f.funcode='10140MAG' AND b.pk_btn='0001Z010140MAG000082')
       OR (f.funcode='10140MAO' AND b.pk_btn='0001Z010140MAO000082'));
  IF v_count <> 2 THEN
    RAISE_APPLICATION_ERROR(-20146, 'Material Save button registration differs from QDNC evidence');
  END IF;

  UPDATE qd_es_node
     SET node_code='10140MAO',button_code='Save',adapter_class=v_adapter,mapping_state='PILOT',
         operation_types='INSERT',object_type='BD_MATERIAL',validated='Y'
   WHERE scope_id=v_scope AND group_scope_id=v_scope
     AND ((mapping_state='UNMAPPED' AND validated='N' AND node_code IS NULL AND button_code IS NULL)
       OR (mapping_state='PILOT' AND node_code='10140MAO' AND button_code='Save'
           AND adapter_class=v_adapter AND operation_types='INSERT' AND object_type='BD_MATERIAL'));
  IF SQL%ROWCOUNT <> 1 THEN
    RAISE_APPLICATION_ERROR(-20147, 'Canonical material Save scope differs from the verified pilot mapping');
  END IF;

  UPDATE qd_es_node SET validated='Y'
   WHERE scope_id=v_alias AND group_scope_id=v_scope AND node_code='10140MAG'
     AND button_code='Save' AND adapter_class=v_adapter AND mapping_state='PILOT'
     AND operation_types='INSERT,UPDATE' AND object_type='BD_MATERIAL';
  IF SQL%ROWCOUNT=0 THEN
    INSERT INTO qd_es_node (scope_id,group_scope_id,node_code,node_name,button_code,button_name,
        adapter_class,mapping_state,operation_types,object_type,validated)
    SELECT v_alias,v_scope,'10140MAG',node_name,'Save',button_name,
        v_adapter,'PILOT','INSERT,UPDATE','BD_MATERIAL','Y'
      FROM qd_es_node WHERE scope_id=v_scope;
  END IF;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_group_material_save;
    RAISE;
END;
/

SELECT scope_id,group_scope_id,node_code,button_code,operation_types,mapping_state,validated
  FROM qd_es_node WHERE group_scope_id='QD-ES-A7FBCA61C6991D48' ORDER BY node_code;
SELECT pk_group,master_enabled,control_version FROM qd_es_bd_control WHERE pk_group=:pilot_group;
