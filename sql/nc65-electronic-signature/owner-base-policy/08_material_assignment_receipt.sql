-- Read-only online acceptance evidence for 1.3.0. Bind request_id as the receipt's 32-character string.
SELECT request_id,node_code,pk_group,pk_org,signature_state,business_state,owner_object_count,
       finished_at,error_code
  FROM qd_es_request
 WHERE request_id=:request_id AND operation_type='ASSIGN' AND policy_mode='OWNER_V1';

SELECT o.bill_code,o.object_pk,o.policy_owner_org,o.target_org,
       o.owner_sign_required,o.owner_audit_enabled,o.unit_state,o.unit_error_code,o.unit_finished_at,
       (SELECT COUNT(*) FROM bd_marorg m WHERE m.pk_material=o.object_pk AND m.pk_org=o.target_org
           AND m.pk_group=o.pk_group AND NVL(m.dr,0)=0) live_relation_count,
       (SELECT COUNT(*) FROM qd_es_signlog l WHERE l.request_id=o.request_id AND l.object_id=o.object_id
           AND l.result_code='SUCCESS') successful_signatures,
       (SELECT COUNT(*) FROM qd_es_audit a WHERE a.request_id=o.request_id AND a.object_id=o.object_id
           AND a.result_code='SUCCESS') successful_audits
  FROM qd_es_object o JOIN qd_es_request r ON r.request_id=o.request_id
 WHERE r.request_id=:request_id AND r.operation_type='ASSIGN' AND r.policy_mode='OWNER_V1'
 ORDER BY o.object_pk,o.target_org;

SELECT a.audit_id,a.object_pk,a.target_org,s.snapshot_phase,s.entity_name,s.row_id,
       s.parent_row_id,s.payload_hash,DBMS_LOB.GETLENGTH(s.payload) payload_chars
  FROM qd_es_audit a JOIN qd_es_snapshot s ON s.audit_id=a.audit_id AND s.request_id=a.request_id
 WHERE a.request_id=:request_id AND a.operation_type='ASSIGN'
 ORDER BY a.object_pk,a.target_org,s.entity_name,s.row_id,s.snapshot_phase;
