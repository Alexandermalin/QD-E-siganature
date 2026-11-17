-- QDNC only. The group master must be off. Use after verifying the installed native tab overlays.
-- Registers the two UPDATE routes. It does not set or enable any policy.
DECLARE
  v_group CONSTANT VARCHAR2(20) := '0001A9100000000005MH';
  v_service CONSTANT VARCHAR2(100) := 'nc.itf.uapbd.qd.esign.IMaterialOrgInfoOperationAdapter';
  v_count NUMBER;

  PROCEDURE register_page(p_scope VARCHAR2, p_button VARCHAR2, p_object VARCHAR2) IS
    v_alias VARCHAR2(64) := p_scope || '@10140MAG';
  BEGIN
    MERGE INTO qd_es_node target
    USING (SELECT node_name,button_name FROM qd_es_node
            WHERE scope_id=p_scope AND group_scope_id=p_scope) source
       ON (target.scope_id=v_alias)
    WHEN MATCHED THEN UPDATE SET target.validated='Y'
      WHERE target.group_scope_id=p_scope AND target.node_code='10140MAG'
        AND target.button_code=p_button AND target.adapter_class=v_service
        AND target.operation_types='UPDATE' AND target.object_type=p_object AND target.mapping_state='PILOT'
    WHEN NOT MATCHED THEN INSERT
      (scope_id,group_scope_id,node_code,node_name,button_code,button_name,adapter_class,
       operation_types,object_type,validated,mapping_state)
      VALUES (v_alias,p_scope,'10140MAG',source.node_name,p_button,source.button_name,v_service,
              'UPDATE',p_object,'Y','PILOT');
    IF SQL%ROWCOUNT<>1 THEN
      RAISE_APPLICATION_ERROR(-20151,'Material group subpage scope does not match the expected baseline');
    END IF;
  END;
BEGIN
  SAVEPOINT qd_material_org_pages;
  IF SYS_CONTEXT('USERENV','SESSION_USER')<>'QDNC'
      OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA')<>'QDNC'
      OR SYS_CONTEXT('USERENV','DB_UNIQUE_NAME')<>'DB'
      OR LOWER(SYS_CONTEXT('USERENV','SERVER_HOST'))<>'ncoa-db' THEN
    RAISE_APPLICATION_ERROR(-20150,'Expected QDNC test environment');
  END IF;
  LOCK TABLE qd_es_schema_version IN SHARE MODE NOWAIT;
  LOCK TABLE qd_es_request IN SHARE MODE NOWAIT;
  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_bd_control IN SHARE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version WHERE version_no='1.2.0'
    AND NOT EXISTS (SELECT 1 FROM qd_es_schema_version WHERE version_no='1.2.0-RB');
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20154,'Expected active group policy schema 1.2.0'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_request
    WHERE node_code='10140MAG' AND button_code IN ('FiSave','PuSave') AND business_state='RUNNING';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20155,'Reconcile running material subpage requests first'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_bd_control WHERE master_enabled='Y';
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20156,'Close group masters before changing shared mappings'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_bd_control
    WHERE pk_group=v_group AND master_enabled='N';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20152,'Close the group master before mapping the subpages'); END IF;
  SELECT COUNT(*) INTO v_count FROM bd_uitabextend
    WHERE metadataid='10140MAG'
      AND ((nodekey='fi' AND exbean_name='material_fi'
            AND ex_configpath='nc/ui/bd/material/fi/material_fi_config.xml')
        OR (nodekey='pu' AND exbean_name='material_pu'
            AND ex_configpath='nc/ui/bd/material/pu/material_pu_config.xml'));
  IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20153,'Native material tab registration differs'); END IF;
  SELECT COUNT(*) INTO v_count FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
    WHERE f.funcode='10140MAG' AND NVL(f.dr,0)=0 AND NVL(p.dr,0)=0
      AND p.paramname='BeanConfigFilePath'
      AND p.paramvalue='nc/ui/uapbd/qd/esign/QdMaterialGroupConfig.xml';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20157,'Material group client overlay is missing'); END IF;
  register_page('QD-ES-B48245BDDB687314','FiSave','BD_MATERIAL_FI');
  register_page('QD-ES-2DAE0B30BE8A146B','PuSave','BD_MATERIAL_PU');
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_material_org_pages;
    RAISE;
END;
/

SELECT scope_id,group_scope_id,node_code,button_code,operation_types,object_type,validated
  FROM qd_es_node
 WHERE scope_id IN ('QD-ES-B48245BDDB687314@10140MAG','QD-ES-2DAE0B30BE8A146B@10140MAG')
 ORDER BY button_code;
