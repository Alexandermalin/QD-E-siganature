-- Read-only. Bind the isolated pilot BU and its test user's NC user code.
-- The last 20 attempts include cancellation and failed saves without material codes.
SELECT * FROM (
  SELECT r.request_id,r.user_code,r.created_at,r.signature_state,r.business_state,r.error_code,
         o.object_pk AS material_pk,o.bill_code AS material_code,
         (SELECT COUNT(*) FROM qd_es_signlog s WHERE s.request_id=r.request_id AND s.object_id=o.object_id) AS sign_count,
         (SELECT MAX(s.result_code) FROM qd_es_signlog s WHERE s.request_id=r.request_id AND s.object_id=o.object_id) AS sign_result,
         (SELECT COUNT(*) FROM qd_es_audit a WHERE a.request_id=r.request_id AND a.object_id=o.object_id) AS audit_count,
         (SELECT COUNT(*) FROM qd_es_snapshot t WHERE t.request_id=r.request_id AND t.object_id=o.object_id
             AND t.snapshot_phase='AFTER' AND t.row_id=o.object_pk) AS after_count,
         (SELECT COUNT(*) FROM bd_material m WHERE m.pk_material=o.object_pk AND m.code=o.bill_code
             AND m.pk_group=r.pk_group AND m.pk_org=r.pk_org AND NVL(m.dr,0)=0) AS saved_material_count
    FROM qd_es_request r JOIN qd_es_object o ON o.request_id=r.request_id
   WHERE r.pk_org=:pilot_org AND r.user_code=:pilot_user_code AND r.node_code='10140MAO'
     AND r.button_code='Save' AND r.operation_type='INSERT' AND o.object_type='BD_MATERIAL'
   ORDER BY r.created_at DESC,r.request_id DESC
) WHERE ROWNUM<=20;
