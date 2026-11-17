-- Read-only candidate checks. No DDL, DML or connection is performed by this file itself.
-- Run against the isolated NC65/Oracle environment selected for acceptance, never the shared WMS-connected system.
SELECT SYS_CONTEXT('USERENV','SESSION_USER') session_user,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') current_schema,
       SYS_CONTEXT('USERENV','DB_UNIQUE_NAME') db_unique_name,
       SYS_CONTEXT('USERENV','SERVER_HOST') server_host FROM dual;

SELECT version_no,applied_at,description FROM qd_es_schema_version
 WHERE version_no IN ('1.3.0','1.3.0-RB','1.3.1','1.3.1-RB','1.3.1-SEL','1.3.1-SEL-RB') ORDER BY applied_at;

SELECT business_state,COUNT(*) request_count FROM qd_es_request
 WHERE business_state IN ('PENDING','READY','RUNNING') GROUP BY business_state ORDER BY business_state;

SELECT archive_month,status FROM qd_es_archive ORDER BY archive_month;

-- An unregistered precreated month must be reviewed before migration; do not silently adopt it.
SELECT table_name FROM user_tables t
 WHERE REGEXP_LIKE(t.table_name,'^QD_ES_REQUEST_H[0-9]{6}$')
   AND NOT EXISTS (SELECT 1 FROM qd_es_archive a WHERE TRIM(a.archive_month)=SUBSTR(t.table_name,-6))
 ORDER BY table_name;

SELECT object_name,object_type,status FROM user_objects
 WHERE object_name='QD_ES_BATCH_PAGE' OR REGEXP_LIKE(object_name,'^QD_ES_BATCH_PAGE_H[0-9]{6}$')
    OR object_name IN ('QDES_BPAGE_OBJ_GUARD','QDES_BPAGE_REQ_GUARD') ORDER BY object_name;

SELECT COUNT(*) existing_org_batch_objects FROM qd_es_object WHERE client_object_id LIKE 'orgbulk:%';
