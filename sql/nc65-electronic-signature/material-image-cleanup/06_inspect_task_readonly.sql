-- Read-only DBA troubleshooting after 01_schema.sql or 05_upgrade_existing_schema.sql.
-- Bind the current NC group primary key and the exact request ID; do not use this as a user-facing log API.
SELECT h.request_id,h.pk_group,h.object_pk,h.status,h.attempt_count,h.created_at,h.finished_at,
       h.next_retry_at,h.lease_until,h.last_error,COUNT(i.file_pk) AS recorded_nodes
  FROM qd_es_img_cleanup h LEFT JOIN qd_es_img_cleanup_item i ON i.cleanup_id=h.cleanup_id
 WHERE h.pk_group=:group_id AND h.request_id=:request_id
 GROUP BY h.request_id,h.pk_group,h.object_pk,h.status,h.attempt_count,h.created_at,h.finished_at,
          h.next_retry_at,h.lease_until,h.last_error
 ORDER BY h.object_pk;

SELECT h.request_id,h.object_pk,i.file_pk,i.file_path,i.is_folder,i.is_doc,i.storage_pk,
       i.modify_time,i.last_time,i.file_length
  FROM qd_es_img_cleanup h JOIN qd_es_img_cleanup_item i ON i.cleanup_id=h.cleanup_id
 WHERE h.pk_group=:group_id AND h.request_id=:request_id
 ORDER BY h.object_pk,i.file_path;
