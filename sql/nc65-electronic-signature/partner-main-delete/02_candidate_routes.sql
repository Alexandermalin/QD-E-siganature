-- Candidate route rows for an isolated NC acceptance environment only.
-- Run the read-only precheck first. This script only inserts previously absent aliases;
-- it does not modify an existing mapping, enable a route, set policy, or commit.
-- The original client configuration and WMS integration must be isolated before acceptance.

DECLARE
  v_adapter CONSTANT VARCHAR2(300) := 'nc.itf.uapbd.qd.esign.IMaterialPageDeleteService';
  v_count NUMBER;

  PROCEDURE register_route(p_scope VARCHAR2,p_node VARCHAR2,p_object VARCHAR2,p_name VARCHAR2) IS
    v_alias VARCHAR2(64) := p_scope||'@'||p_node;
  BEGIN
    SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE scope_id=p_scope AND group_scope_id=p_scope;
    IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20310,'Formal action scope is missing: '||p_scope); END IF;
    SELECT COUNT(*) INTO v_count
      FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
     WHERE f.funcode=p_node AND b.btncode='Delete' AND NVL(f.dr,0)=0 AND NVL(b.dr,0)=0;
    IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20311,'Native delete button is missing or ambiguous: '||p_node); END IF;
    SELECT COUNT(*) INTO v_count FROM qd_es_node
     WHERE node_code=p_node AND button_code='Delete' AND scope_id<>v_alias;
    IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20312,'Another route owns the native delete button: '||p_node); END IF;
    SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE scope_id=v_alias;
    IF v_count=0 THEN
      INSERT INTO qd_es_node(scope_id,group_scope_id,node_code,node_name,button_code,button_name,
                             adapter_class,operation_types,object_type,validated,mapping_state)
        VALUES(v_alias,p_scope,p_node,p_name,'Delete','删除',v_adapter,'DELETE',p_object,'N','PILOT');
      IF SQL%ROWCOUNT<>1 THEN RAISE_APPLICATION_ERROR(-20313,'Candidate route insert failed: '||p_node); END IF;
    ELSE
      -- Existing rows are never overwritten or downgraded by this script.
      SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE scope_id=v_alias AND group_scope_id=p_scope
        AND node_code=p_node AND button_code='Delete' AND adapter_class=v_adapter
        AND operation_types='DELETE' AND object_type=p_object;
      IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20314,'Existing route differs; reconcile manually: '||p_node); END IF;
    END IF;
  END;
BEGIN
  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  register_route('QD-ES-35B5B1E09B5A4A80','10140CUG','BD_CUSTOMER','客户');
  register_route('QD-ES-35B5B1E09B5A4A80','10140CUO','BD_CUSTOMER','客户');
  register_route('QD-ES-83A95678666FC134','10140SUG','BD_SUPPLIER','供应商');
  register_route('QD-ES-83A95678666FC134','10140SUO','BD_SUPPLIER','供应商');
END;
/

SELECT scope_id,group_scope_id,node_code,button_code,adapter_class,operation_types,object_type,validated,mapping_state
  FROM qd_es_node WHERE scope_id IN (
    'QD-ES-35B5B1E09B5A4A80@10140CUG','QD-ES-35B5B1E09B5A4A80@10140CUO',
    'QD-ES-83A95678666FC134@10140SUG','QD-ES-83A95678666FC134@10140SUO') ORDER BY scope_id;
-- Review the output, then explicitly COMMIT or ROLLBACK in the maintenance session.
