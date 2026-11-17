-- Read only. Bind :request_id, :pilot_group, :pilot_org and :pilot_user_code.
-- Request/sign/audit/snapshot rows below are online rows; archived requests require
-- the project's locator/history query service. An absent row alone proves nothing.
SELECT r.request_id,r.user_code,r.created_at,r.pk_group,r.pk_org,r.node_code,r.button_code,
       r.operation_type,r.signature_state,r.business_state,r.error_code,
       o.client_object_id,o.object_type,o.object_pk,o.bill_code,
       (SELECT COUNT(*) FROM qd_es_signlog s
         WHERE s.request_id=r.request_id AND s.object_id=o.object_id) sign_count,
       (SELECT COUNT(*) FROM qd_es_audit a
         WHERE a.request_id=r.request_id AND a.object_id=o.object_id) audit_count,
       CASE o.object_type
         WHEN 'IC_PURCHASE_IN' THEN
           (SELECT COUNT(*) FROM ic_purchasein_h h
             WHERE h.cgeneralhid=o.object_pk AND h.pk_group=r.pk_group AND h.pk_org=r.pk_org AND NVL(h.dr,0)=0)
         WHEN 'IC_SALE_OUT' THEN
           (SELECT COUNT(*) FROM ic_saleout_h h
             WHERE h.cgeneralhid=o.object_pk AND h.pk_group=r.pk_group AND h.pk_org=r.pk_org AND NVL(h.dr,0)=0)
         ELSE NULL
       END active_bill_count
  FROM qd_es_request r JOIN qd_es_object o ON o.request_id=r.request_id
 WHERE r.request_id=:request_id AND r.pk_group=:pilot_group AND r.pk_org=:pilot_org
   AND r.user_code=:pilot_user_code AND r.node_code IN ('40080602','40080802')
   AND r.button_code IN ('Save','Delete','Sign','CancelSign');

-- Show every authentication attempt; do not infer its chronology from MAX(result_code).
SELECT s.event_id,s.object_id,s.attempt_id,s.event_time,s.result_code,s.lock_result
  FROM qd_es_signlog s JOIN qd_es_request r ON r.request_id=s.request_id
 WHERE r.request_id=:request_id AND r.pk_group=:pilot_group AND r.pk_org=:pilot_org
   AND r.user_code=:pilot_user_code
 ORDER BY s.event_time,s.attempt_id,s.object_id,s.event_id;

SELECT s.snapshot_phase,s.entity_name,s.row_id,s.parent_row_id,
       DBMS_LOB.GETLENGTH(s.payload) payload_chars,s.payload_hash
  FROM qd_es_snapshot s JOIN qd_es_request r ON r.request_id=s.request_id
 WHERE r.request_id=:request_id AND r.pk_group=:pilot_group AND r.pk_org=:pilot_org
   AND r.user_code=:pilot_user_code
 ORDER BY s.snapshot_phase,s.entity_name,s.row_id;
