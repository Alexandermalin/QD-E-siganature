-- QDNC sales-order pilot only. Stop new requests and disable autocommit.
-- Bind :pilot_group, :pilot_org, :pilot_mode ('REGISTER' or 'DISABLE').
-- This script changes the shared mapping only; it never enables an organization policy or master switch.
DECLARE
  v_group CONSTANT VARCHAR2(20) := :pilot_group;
  v_org CONSTANT VARCHAR2(20) := :pilot_org;
  v_mode CONSTANT VARCHAR2(8) := :pilot_mode;
  v_scope CONSTANT VARCHAR2(64) := 'QD-ES-EAF88AC5CAF75442';
  v_adapter CONSTANT VARCHAR2(300) := 'nc.itf.so.qd.esign.ISaleOrderPfSaveAdapter';
  v_count NUMBER;
  v_now CHAR(19) := TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS');
BEGIN
  SAVEPOINT qd_sale_save_pilot;
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'QDNC'
      OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'QDNC'
      OR v_group IS NULL OR v_group <> '0001A9100000000005MH'
      OR v_org IS NULL OR v_org <> '0001A9100000003UDS6R'
      OR v_mode IS NULL OR v_mode NOT IN ('REGISTER','DISABLE') THEN
    RAISE_APPLICATION_ERROR(-20210, 'Expected QDNC, group 001, pilot BU 990 and REGISTER or DISABLE');
  END IF;
  LOCK TABLE qd_es_schema_version IN SHARE MODE NOWAIT;
  LOCK TABLE qd_es_request IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_policy IN SHARE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_schema_version
   WHERE version_no='1.2.0' AND NOT EXISTS
       (SELECT 1 FROM qd_es_schema_version WHERE version_no='1.2.0-RB');
  IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20211, 'Expected active schema version 1.2.0'); END IF;
  SELECT COUNT(*) INTO v_count FROM org_orgs o JOIN org_salesorg s ON s.pk_salesorg=o.pk_org
   WHERE o.pk_group=v_group AND o.pk_org=v_org AND o.isbusinessunit='Y' AND o.orgtype8='Y'
     AND o.islastversion='Y' AND o.enablestate=2 AND NVL(o.dr,0)=0
     AND s.pk_group=v_group AND s.islastversion='Y' AND s.enablestate=2 AND NVL(s.dr,0)=0;
  IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20212, 'Pilot 990 must be an active business unit and sales organization'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_request
   WHERE node_code='40060301' AND button_code='Save' AND business_state='RUNNING';
  IF v_count <> 0 THEN RAISE_APPLICATION_ERROR(-20213, 'Reconcile running sales-order requests first'); END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_policy
   WHERE node_code='40060301' AND button_code='Save' AND enabled='Y';
  IF v_count <> 0 THEN RAISE_APPLICATION_ERROR(-20214, 'Disable existing sales-save policies before changing the shared mapping'); END IF;

  IF v_mode='DISABLE' THEN
    UPDATE qd_es_node SET validated='N'
     WHERE scope_id=v_scope AND node_code='40060301' AND button_code='Save'
       AND adapter_class=v_adapter AND mapping_state='PILOT';
    IF SQL%ROWCOUNT <> 1 THEN RAISE_APPLICATION_ERROR(-20215, 'Sales pilot mapping does not match'); END IF;
    UPDATE qd_es_request SET business_state='EXPIRED',error_code='SALE_SAVE_PILOT_DISABLED',finished_at=v_now
     WHERE node_code='40060301' AND button_code='Save' AND business_state IN ('PENDING','READY');
    RETURN;
  END IF;

  SELECT COUNT(*) INTO v_count
    FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
   WHERE NVL(f.dr,0)=0 AND NVL(p.dr,0)=0 AND f.funcode='40060301'
     AND p.paramname='BeanConfigFilePath'
     AND p.paramvalue='nc/ui/uapbd/qd/esign/QdSaleOrderConfig.xml';
  IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20216, 'Sales-order client configuration does not match'); END IF;
  SELECT COUNT(*) INTO v_count
    FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
   WHERE NVL(f.dr,0)=0 AND NVL(b.dr,0)=0 AND f.funcode='40060301'
     AND b.btncode='Save' AND b.pk_btn='1001Z810000000004TP6';
  IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20217, 'Sales-order Save button registration differs from site evidence'); END IF;
  UPDATE qd_es_node SET node_code='40060301',button_code='Save',adapter_class=v_adapter,
      mapping_state='PILOT',operation_types='INSERT',object_type='SO_SALE_ORDER',validated='Y'
   WHERE scope_id=v_scope AND group_scope_id IS NULL
     AND ((mapping_state='UNMAPPED' AND validated='N' AND node_code IS NULL AND button_code IS NULL
           AND adapter_class IS NULL AND operation_types IS NULL AND object_type IS NULL)
       OR (mapping_state='PILOT' AND node_code='40060301' AND button_code='Save'
           AND adapter_class=v_adapter AND operation_types='INSERT' AND object_type='SO_SALE_ORDER'));
  IF SQL%ROWCOUNT <> 1 THEN RAISE_APPLICATION_ERROR(-20218, 'Sales Save mapping changed or is absent'); END IF;
EXCEPTION
  WHEN OTHERS THEN ROLLBACK TO qd_sale_save_pilot; RAISE;
END;
/

SELECT scope_id,node_code,button_code,adapter_class,operation_types,object_type,mapping_state,validated
  FROM qd_es_node WHERE scope_id='QD-ES-EAF88AC5CAF75442';
SELECT pk_org,master_enabled,control_version FROM qd_es_org_control WHERE pk_group=:pilot_group AND pk_org=:pilot_org;
SELECT pk_org,sign_enabled,reason_required,audit_enabled,enabled,validated,policy_version FROM qd_es_policy
 WHERE pk_group=:pilot_group AND pk_org=:pilot_org AND node_code='40060301' AND button_code='Save';
