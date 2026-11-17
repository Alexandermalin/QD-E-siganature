-- QDNC test candidate: stage the eight outbound-request mappings only.
-- Global node mappings are shared across organizations. Stop affected NC operations,
-- disable autocommit, back up the eight rows below, and review every result first.
-- Run the SELECT statements separately as a precheck. Bind :confirm_qdnc_test to
-- 'QDNC_TEST_ONLY' only after verifying the actual test environment. Schema and DB
-- names alone do not prove that an environment is a test environment.
-- The block never changes a policy, never registers a BP rule, and leaves VALIDATED=N.
-- It does not commit; after checking the result, the operator chooses COMMIT or ROLLBACK.

SELECT scope_id,group_scope_id,node_code,node_name,button_code,button_name,
       adapter_class,mapping_state,operation_types,object_type,validated
  FROM qd_es_node
 WHERE scope_id IN ('QD-ES-41CC70D7534AF3B1','QD-ES-C31C9590475F4297',
                    'QD-ES-931D24D0DD84834A','QD-ES-6D9C72222CF4B33C',
                    'QD-ES-C7EA33244DC87A40','QD-ES-97A9C5615397566C',
                    'QD-ES-92D15BDDBB698776','QD-ES-E8E246982D39D8AD')
 ORDER BY scope_id;

-- Check every existing mapping and policy for the global node, not just one org.
SELECT scope_id,node_code,button_code,adapter_class,mapping_state,validated
  FROM qd_es_node
 WHERE node_code='40080801'
 ORDER BY button_code,scope_id;

SELECT pk_group,pk_org,node_code,button_code,enabled,validated,sign_enabled,audit_enabled
  FROM qd_es_policy
 WHERE node_code='40080801'
 ORDER BY pk_group,pk_org,button_code;

SELECT business_state,COUNT(*) request_count
  FROM qd_es_request
 WHERE node_code='40080801' AND business_state IN ('PENDING','READY','RUNNING')
 GROUP BY business_state;

DECLARE
  v_count NUMBER;
  v_confirm CONSTANT VARCHAR2(40) := :confirm_qdnc_test;
  v_save CONSTANT VARCHAR2(100) := 'nc.itf.ic.qd.esign.IInventoryPfSaveAdapter';
  v_state CONSTANT VARCHAR2(100) := 'nc.itf.ic.qd.esign.IInventoryPfStateAdapter';
  v_line CONSTANT VARCHAR2(100) := 'nc.itf.ic.qd.esign.ISapplyLineOperationAdapter';

  PROCEDURE stage_route(p_scope VARCHAR2,p_button VARCHAR2,p_operations VARCHAR2,
                        p_adapter VARCHAR2,p_formal_name VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO v_count FROM qd_es_node
     WHERE node_code='40080801' AND button_code=p_button AND scope_id<>p_scope;
    IF v_count<>0 THEN
      RAISE_APPLICATION_ERROR(-20301,'Conflicting outbound-request button mapping: '||p_button);
    END IF;

    -- Only an untouched seed or the same unvalidated candidate may be staged.
    UPDATE qd_es_node
       SET node_code='40080801',button_code=p_button,adapter_class=p_adapter,
           mapping_state='PILOT',operation_types=p_operations,object_type='IC_SAPPLY'
     WHERE scope_id=p_scope AND node_name='出库申请单' AND button_name=p_formal_name
       AND group_scope_id IS NULL AND validated='N'
       AND ((mapping_state='UNMAPPED' AND node_code IS NULL AND button_code IS NULL
             AND adapter_class IS NULL AND operation_types IS NULL AND object_type IS NULL)
         OR (mapping_state='PILOT' AND node_code='40080801' AND button_code=p_button
             AND adapter_class=p_adapter AND operation_types=p_operations AND object_type='IC_SAPPLY'));
    IF SQL%ROWCOUNT<>1 THEN
      RAISE_APPLICATION_ERROR(-20302,'Outbound-request scope is missing or differs: '||p_scope);
    END IF;
  END;
BEGIN
  SAVEPOINT qd_sapply_routes;
  IF SYS_CONTEXT('USERENV','CURRENT_SCHEMA')<>'QDNC'
     OR v_confirm IS NULL OR v_confirm<>'QDNC_TEST_ONLY' THEN
    RAISE_APPLICATION_ERROR(-20303,'Confirm the QDNC test environment before staging routes');
  END IF;

  -- Prevent concurrent requests, route edits and policy edits until commit/rollback.
  -- These are table locks: perform this only in the agreed test maintenance window.
  LOCK TABLE qd_es_request IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE qd_es_policy IN SHARE ROW EXCLUSIVE MODE NOWAIT;

  SELECT COUNT(*) INTO v_count FROM qd_es_policy
   WHERE node_code='40080801' AND enabled='Y';
  IF v_count<>0 THEN
    RAISE_APPLICATION_ERROR(-20304,'Disable all outbound-request policies before changing global mappings');
  END IF;
  SELECT COUNT(*) INTO v_count FROM qd_es_request
   WHERE node_code='40080801' AND business_state IN ('PENDING','READY','RUNNING');
  IF v_count<>0 THEN
    RAISE_APPLICATION_ERROR(-20305,'Reconcile unfinished outbound-request requests first');
  END IF;

  stage_route('QD-ES-41CC70D7534AF3B1','Save','INSERT,UPDATE',v_save,'保存');
  stage_route('QD-ES-C31C9590475F4297','Delete','DELETE',v_state,'删除');
  stage_route('QD-ES-931D24D0DD84834A','Commit','UPDATE',v_state,'提交');
  stage_route('QD-ES-6D9C72222CF4B33C','UnCommit','UPDATE',v_state,'收回');
  stage_route('QD-ES-C7EA33244DC87A40','Approve','UPDATE',v_state,'审批-确定');
  stage_route('QD-ES-97A9C5615397566C','UnApprove','UPDATE',v_state,'取消审批');
  stage_route('QD-ES-92D15BDDBB698776','LineClose','UPDATE',v_line,'行关闭');
  stage_route('QD-ES-E8E246982D39D8AD','LineOpen','UPDATE',v_line,'行打开');
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_sapply_routes;
    RAISE;
END;
/

-- Re-run the first SELECT and compare all eight rows with the captured precheck.
-- Do not set VALIDATED='Y' or enable a policy merely because this block succeeded.
