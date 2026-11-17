-- QDNC, 1.3.0. Candidate route registration for the material main archive DELETE action.
-- Run in the stopped maintenance window after 01_schema.sql, the module patch and the readonly precheck
-- (04_readonly_precheck.sql) have confirmed the real node/button and the candidate's supported
-- extension configuration. This script enables routes for isolated client acceptance only;
-- it does not set any policy and does not enable the background cleanup schedule.
-- Do not run with autocommit. Record the precheck results before executing this block.

DECLARE
  v_delete CONSTANT VARCHAR2(64) := 'QD-ES-2F2608B07F6B73DD';
  v_adapter CONSTANT VARCHAR2(100) := 'nc.itf.uapbd.qd.esign.IMaterialPageDeleteService';
  v_count NUMBER;

  PROCEDURE require_zero(p_count NUMBER, p_message VARCHAR2) IS
  BEGIN
    IF p_count<>0 THEN RAISE_APPLICATION_ERROR(-20273,p_message); END IF;
  END;

  -- 集团节点路由已由物料目录登记，业务单元节点按集团节点别名登记并保持同一正式动作。
  PROCEDURE register_unit_node IS
    v_alias CONSTANT VARCHAR2(64) := v_delete||'@10140MAO';
    v_exists NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_exists FROM qd_es_node WHERE scope_id=v_alias;
    IF v_exists=0 THEN
      INSERT INTO qd_es_node(scope_id,group_scope_id,node_code,node_name,button_code,button_name,
        adapter_class,operation_types,object_type,validated,mapping_state)
      SELECT v_alias,v_delete,'10140MAO',node_name,'Delete',button_name,v_adapter,'DELETE','BD_MATERIAL','N','PILOT'
        FROM qd_es_node WHERE scope_id=v_delete AND group_scope_id=v_delete;
      require_zero(SQL%ROWCOUNT-1,'Unexpected unit material delete insert');
    ELSE
      SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE scope_id=v_alias
        AND group_scope_id=v_delete AND node_code='10140MAO'
        AND (adapter_class IS NULL OR adapter_class=v_adapter);
      require_zero(v_count-1,'Existing unit material delete route belongs to another mapping');
      UPDATE qd_es_node SET button_code='Delete',button_name='删除',adapter_class=v_adapter,
        operation_types='DELETE',object_type='BD_MATERIAL',validated='N',mapping_state='PILOT'
        WHERE scope_id=v_alias AND adapter_class IS NULL;
      SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE scope_id=v_alias AND group_scope_id=v_delete
        AND node_code='10140MAO' AND button_code='Delete' AND adapter_class=v_adapter
        AND operation_types='DELETE' AND object_type='BD_MATERIAL';
      IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20286,'Existing candidate unit route needs manual reconciliation'); END IF;
    END IF;
  END;
BEGIN
  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE node_code='10140MAG' AND button_code='Delete'
    AND scope_id<>v_delete;
  require_zero(v_count,'Another group material delete route already uses this node/button');
  SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE node_code='10140MAO' AND button_code='Delete'
    AND scope_id<>v_delete||'@10140MAO';
  require_zero(v_count,'Another unit material delete route already uses this node/button');
  -- Snapshot both old mappings before changing either. Do not overwrite evidence on rerun.
  FOR route IN (SELECT v_delete AS scope_id FROM dual UNION ALL SELECT v_delete||'@10140MAO' FROM dual) LOOP
    SELECT COUNT(*) INTO v_count FROM qd_es_matdel_route_bak WHERE scope_id=route.scope_id;
    IF v_count=0 THEN
      SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE scope_id=route.scope_id AND adapter_class=v_adapter;
      IF v_count<>0 THEN
        RAISE_APPLICATION_ERROR(-20281,'Candidate route already installed without its original backup');
      END IF;
      INSERT INTO qd_es_matdel_route_bak
        (scope_id,existed,node_code,button_code,button_name,adapter_class,operation_types,object_type,validated,mapping_state)
      SELECT scope_id,'Y',node_code,button_code,button_name,adapter_class,operation_types,object_type,validated,mapping_state
        FROM qd_es_node WHERE scope_id=route.scope_id;
      IF SQL%ROWCOUNT=0 THEN
        INSERT INTO qd_es_matdel_route_bak(scope_id,existed) VALUES(route.scope_id,'N');
      END IF;
    END IF;
  END LOOP;
  -- 集团节点动作必须尚未映射任何适配器，避免覆盖既有登记。
  SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE scope_id=v_delete
   AND adapter_class IS NOT NULL AND adapter_class<>v_adapter;
  require_zero(v_count,'Existing group material delete route belongs to another adapter');
  SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE scope_id=v_delete
    AND (NVL(group_scope_id,'~')<>v_delete OR (node_code IS NOT NULL AND node_code<>'10140MAG')
      OR (button_code IS NOT NULL AND button_code<>'Delete'));
  require_zero(v_count,'Existing group material delete route has unexpected identity');
  SELECT COUNT(*) INTO v_count FROM qd_es_node
   WHERE scope_id=v_delete AND group_scope_id=v_delete AND adapter_class IS NOT NULL;
  IF v_count=0 THEN
    MERGE INTO qd_es_node dst
      USING (SELECT 1 FROM dual) src ON (dst.scope_id=v_delete AND dst.group_scope_id=v_delete)
      WHEN MATCHED THEN UPDATE SET dst.node_code='10140MAG',dst.button_code='Delete',dst.button_name='删除',
        dst.adapter_class=v_adapter,dst.operation_types='DELETE',dst.object_type='BD_MATERIAL',
        dst.validated='N',dst.mapping_state='PILOT'
      WHEN NOT MATCHED THEN INSERT
        (scope_id,group_scope_id,node_code,node_name,button_code,button_name,adapter_class,
         operation_types,object_type,validated,mapping_state)
        VALUES (v_delete,v_delete,'10140MAG','物料','Delete','删除',v_adapter,
                'DELETE','BD_MATERIAL','N','PILOT');
  ELSE
    SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE scope_id=v_delete AND node_code='10140MAG'
      AND button_code='Delete' AND adapter_class=v_adapter AND operation_types='DELETE' AND object_type='BD_MATERIAL';
    IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20283,'Existing candidate group route needs manual reconciliation'); END IF;
  END IF;
  register_unit_node;
END;
/

-- 隔离验收通过并逐项登记证据后，才允许把上述两行的 validated 置为 'Y'；
-- 复核路由及备份后由执行人员决定 COMMIT 或 ROLLBACK。
