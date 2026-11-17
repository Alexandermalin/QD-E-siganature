-- Roll back candidate routing in the stopped maintenance window; stop the scheduler and drain requests first.
-- Keep qd_es_img_cleanup and qd_es_matdel_route_bak as cleanup and rollback evidence.
-- Oracle DDL is intentionally not used. Review the results before COMMIT or ROLLBACK.
DECLARE
  v_adapter CONSTANT VARCHAR2(300) := 'nc.itf.uapbd.qd.esign.IMaterialPageDeleteService';
  v_count NUMBER;
BEGIN
  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM qd_es_matdel_route_bak;
  IF v_count<>2 THEN RAISE_APPLICATION_ERROR(-20274,'Material delete route backup is missing or incomplete'); END IF;
  FOR b IN (SELECT * FROM qd_es_matdel_route_bak ORDER BY scope_id) LOOP
    SELECT COUNT(*) INTO v_count FROM qd_es_node WHERE scope_id=b.scope_id
      AND adapter_class IS NOT NULL AND adapter_class<>v_adapter
      AND NVL(adapter_class,'~')<>NVL(b.adapter_class,'~');
    IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20275,'Route was changed by another installation'); END IF;
    IF b.existed='Y' THEN
      UPDATE qd_es_node SET node_code=b.node_code,button_code=b.button_code,button_name=b.button_name,adapter_class=b.adapter_class,
        operation_types=b.operation_types,object_type=b.object_type,validated=b.validated,mapping_state=b.mapping_state
        WHERE scope_id=b.scope_id;
      IF SQL%ROWCOUNT<>1 THEN RAISE_APPLICATION_ERROR(-20276,'Original route no longer exists'); END IF;
    ELSE
      -- Keep the new identity for existing history or configuration references, but disable it.
      UPDATE qd_es_node SET adapter_class=NULL,validated='N',mapping_state='UNMAPPED'
        WHERE scope_id=b.scope_id AND adapter_class=v_adapter;
    END IF;
  END LOOP;
END;
/

SELECT scope_id,node_code,button_code,adapter_class,validated,mapping_state
  FROM qd_es_node WHERE scope_id IN ('QD-ES-2F2608B07F6B73DD','QD-ES-2F2608B07F6B73DD@10140MAO');
SELECT status,COUNT(*) AS tasks FROM qd_es_img_cleanup GROUP BY status ORDER BY status;
-- COMMIT or ROLLBACK only after the operator reviews the routes and pending tasks.
