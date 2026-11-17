-- Read only. Bind :request_id, :pilot_group, :pilot_org and :pilot_user_code.
-- Confirm IC_SAPPLY_H/B, their keys, ownership and DR columns in sapply-preflight.sql first.
-- This QDNC query assumes H.CGENERALHID/H.PK_ORG and B.CGENERALHID/B.CGENERALBID are CHAR(20).
-- These are online rows; historical requests need the locator/history service.
SELECT r.request_id,r.user_code,r.created_at,r.pk_group,r.pk_org,r.node_code,r.button_code,
       r.operation_type,r.signature_state,r.business_state,r.error_code,
       o.client_object_id,o.object_type,o.object_pk,o.bill_code,p.object_pk parent_bill_pk,
       (SELECT COUNT(*) FROM qd_es_signlog s
         WHERE s.request_id=r.request_id AND s.object_id=o.object_id) sign_count,
       (SELECT COUNT(*) FROM qd_es_audit a
         WHERE a.request_id=r.request_id AND a.object_id=o.object_id) audit_count,
       CASE WHEN o.object_type='IC_SAPPLY' THEN
         (SELECT COUNT(*) FROM ic_sapply_h h
           WHERE h.cgeneralhid=RPAD(o.object_pk,20,' ') AND h.pk_group=r.pk_group AND h.pk_org=RPAD(r.pk_org,20,' ')
             AND LENGTH(o.object_pk) BETWEEN 1 AND 20 AND LENGTH(r.pk_org) BETWEEN 1 AND 20 AND NVL(h.dr,0)=0)
       WHEN o.object_type='IC_SAPPLY_LINE' THEN
         (SELECT COUNT(*) FROM ic_sapply_b b JOIN ic_sapply_h h ON h.cgeneralhid=b.cgeneralhid
           WHERE b.cgeneralbid=RPAD(o.object_pk,20,' ') AND b.cgeneralhid=RPAD(p.object_pk,20,' ') AND NVL(b.dr,0)=0
             AND LENGTH(o.object_pk) BETWEEN 1 AND 20 AND LENGTH(p.object_pk) BETWEEN 1 AND 20
             AND h.pk_group=r.pk_group AND h.pk_org=RPAD(r.pk_org,20,' ')
             AND LENGTH(r.pk_org) BETWEEN 1 AND 20 AND NVL(h.dr,0)=0)
       END active_object_count
  FROM qd_es_request r JOIN qd_es_object o ON o.request_id=r.request_id
  LEFT JOIN qd_es_object p ON p.request_id=o.request_id AND p.object_id=o.parent_object_id
 WHERE r.request_id=:request_id AND r.pk_group=:pilot_group AND r.pk_org=:pilot_org
   AND r.user_code=:pilot_user_code AND r.node_code='40080801'
   AND o.object_type IN ('IC_SAPPLY','IC_SAPPLY_LINE')
   AND r.button_code IN ('Save','Delete','Commit','UnCommit','Approve','UnApprove','LineClose','LineOpen')
 ORDER BY o.parent_object_id NULLS FIRST,o.object_type,o.object_pk;

SELECT s.event_id,s.object_id,s.attempt_id,s.event_time,s.result_code,s.lock_result
  FROM qd_es_signlog s JOIN qd_es_request r ON r.request_id=s.request_id
 WHERE r.request_id=:request_id AND r.pk_group=:pilot_group AND r.pk_org=:pilot_org
   AND r.user_code=:pilot_user_code AND r.node_code='40080801'
 ORDER BY s.event_time,s.attempt_id,s.object_id,s.event_id;

SELECT s.snapshot_phase,s.entity_name,s.row_id,s.parent_row_id,
       DBMS_LOB.GETLENGTH(s.payload) payload_chars,s.payload_hash
  FROM qd_es_snapshot s JOIN qd_es_request r ON r.request_id=s.request_id
 WHERE r.request_id=:request_id AND r.pk_group=:pilot_group AND r.pk_org=:pilot_org
   AND r.user_code=:pilot_user_code AND r.node_code='40080801'
 ORDER BY s.snapshot_phase,s.entity_name,s.row_id;

-- For line actions, show every authorized row, including rows now missing or deleted.
-- Current state may have changed since the request and cannot prove commit or rollback.
-- BEGIN sapply-line-current
SELECT r.request_id,r.button_code,r.signature_state,r.business_state,
       p.object_pk bill_pk,o.object_pk line_pk,h.fbillflag,b.crowno,b.isclosed,
       CASE WHEN h.cgeneralhid IS NULL THEN 'BILL_NOT_FOUND'
            WHEN b.cgeneralbid IS NULL THEN 'LINE_NOT_FOUND'
            ELSE 'PRESENT' END current_lookup
  FROM qd_es_request r
  JOIN qd_es_object p ON p.request_id=r.request_id AND p.object_type='IC_SAPPLY'
                         AND p.parent_object_id IS NULL
  JOIN qd_es_object o ON o.request_id=r.request_id AND o.parent_object_id=p.object_id
                         AND o.object_type='IC_SAPPLY_LINE'
  LEFT JOIN ic_sapply_h h ON h.cgeneralhid=RPAD(p.object_pk,20,' ')
       AND LENGTH(p.object_pk) BETWEEN 1 AND 20 AND h.pk_group=r.pk_group
       AND h.pk_org=RPAD(r.pk_org,20,' ') AND LENGTH(r.pk_org) BETWEEN 1 AND 20 AND NVL(h.dr,0)=0
  LEFT JOIN ic_sapply_b b ON b.cgeneralhid=h.cgeneralhid AND b.cgeneralbid=RPAD(o.object_pk,20,' ')
       AND LENGTH(o.object_pk) BETWEEN 1 AND 20 AND NVL(b.dr,0)=0
 WHERE r.request_id=:request_id AND r.pk_group=:pilot_group AND r.pk_org=:pilot_org
   AND r.user_code=:pilot_user_code AND r.node_code='40080801'
   AND r.button_code IN ('LineClose','LineOpen')
 ORDER BY p.object_pk,o.object_pk;
-- END sapply-line-current

-- An empty result can mean audit is disabled or no committed change is recorded.
-- Values are shown up to 120 characters; compare them with the complete audit if needed.
-- BEGIN sapply-line-audit
SELECT r.request_id,p.object_pk bill_pk,o.object_pk line_pk,c.entity_name,c.row_id,c.field_code,c.change_type,
       DBMS_LOB.SUBSTR(c.before_value,120,1) before_value,DBMS_LOB.SUBSTR(c.after_value,120,1) after_value
  FROM qd_es_request r
  JOIN qd_es_object p ON p.request_id=r.request_id AND p.object_type='IC_SAPPLY'
                         AND p.parent_object_id IS NULL
  JOIN qd_es_object o ON o.request_id=r.request_id AND o.parent_object_id=p.object_id
                         AND o.object_type='IC_SAPPLY_LINE'
  JOIN qd_es_change c ON c.request_id=r.request_id AND c.object_id=o.object_id
                         AND c.row_id=o.object_pk AND c.parent_row_id=p.object_pk
 WHERE r.request_id=:request_id AND r.pk_group=:pilot_group AND r.pk_org=:pilot_org
   AND r.user_code=:pilot_user_code AND r.node_code='40080801'
   AND r.button_code IN ('LineClose','LineOpen')
   AND UPPER(c.entity_name)='IC_SAPPLY_B' AND UPPER(c.field_code)='ISCLOSED'
 ORDER BY p.object_pk,o.object_pk;
-- END sapply-line-audit
