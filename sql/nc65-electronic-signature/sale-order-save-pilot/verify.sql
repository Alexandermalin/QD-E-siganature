-- Read only: bind the exact request, test group and user code.
SELECT r.request_id,r.user_code,r.created_at,r.pk_group,r.pk_org,r.signature_state,r.business_state,r.error_code,
       o.object_pk,o.bill_code,
       (SELECT COUNT(*) FROM qd_es_signlog s WHERE s.request_id=r.request_id AND s.object_id=o.object_id) sign_count,
       (SELECT MAX(s.result_code) FROM qd_es_signlog s WHERE s.request_id=r.request_id AND s.object_id=o.object_id) sign_result,
       (SELECT COUNT(*) FROM qd_es_audit a WHERE a.request_id=r.request_id AND a.object_id=o.object_id) audit_count,
       (SELECT COUNT(*) FROM so_saleorder h WHERE h.csaleorderid=o.object_pk AND h.vbillcode=o.bill_code
          AND h.pk_group=r.pk_group AND h.pk_org=r.pk_org AND NVL(h.dr,0)=0) saved_order_count
  FROM qd_es_request r JOIN qd_es_object o ON o.request_id=r.request_id
 WHERE r.request_id=:request_id AND r.pk_group=:pilot_group AND r.user_code=:pilot_user_code
   AND r.node_code='40060301' AND r.button_code='Save' AND r.operation_type='INSERT'
   AND o.object_type='SO_SALE_ORDER';

SELECT snapshot_phase,entity_name,row_id,parent_row_id,DBMS_LOB.GETLENGTH(payload) payload_chars,payload_hash
  FROM qd_es_snapshot WHERE request_id=:request_id AND pk_group=:pilot_group
 ORDER BY snapshot_phase,entity_name,row_id;
