-- Read-only. Bind the exact QDNC test request ID or test group/user as indicated.
SELECT r.request_id,r.user_code,r.created_at,r.node_code,r.operation_type,r.pk_group,r.pk_org,
       r.signature_state,r.business_state,r.error_code,o.object_pk,o.bill_code,
       (SELECT COUNT(*) FROM qd_es_signlog s WHERE s.request_id=r.request_id AND s.object_id=o.object_id) sign_count,
       (SELECT COUNT(*) FROM qd_es_audit a WHERE a.request_id=r.request_id AND a.object_id=o.object_id) audit_count
  FROM qd_es_request r JOIN qd_es_object o ON o.request_id=r.request_id
 WHERE r.request_id=:request_id AND r.pk_group=:pilot_group AND r.user_code=:pilot_user_code
   AND r.node_code IN ('10140MAG','10140MAO') AND r.button_code='Save';

SELECT snapshot_phase,entity_name,row_id,parent_row_id,DBMS_LOB.GETLENGTH(payload) payload_chars,payload_hash
  FROM qd_es_snapshot
 WHERE request_id=:request_id AND pk_group=:pilot_group
 ORDER BY snapshot_phase,entity_name,row_id;

SELECT entity_name,row_id,field_code,change_type,before_value,after_value
  FROM qd_es_change
 WHERE request_id=:request_id AND pk_group=:pilot_group
   AND ((entity_name='bd_material' AND field_code IN ('code','pk_measdoc'))
       OR entity_name='bd_materialconvert')
 ORDER BY entity_name,row_id,field_code;
