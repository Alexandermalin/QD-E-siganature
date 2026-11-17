-- QDNC read-only production-SQL compatibility checks, prepared 2026-11-07.
-- SELECT only: no DDL, DML, PL/SQL, transaction statements, EXPLAIN or row locks.
-- Derived from the actual Java repositories and QdTableMetadata column order.
-- Only change from production SQL: table references explicitly use QDNC schema;
-- JDBC bind values are replaced by synthetic literals, page offset=0 and size=100.
-- The archive candidate query retains its production month/closed-request scope.
-- Month 202608 is the precreated historical month. All project tables are empty.
-- These checks prove parsing/projection/query execution on this schema, not data
-- round trips, write constraints, NC authentication, CMT or actual archive moves.
-- Run each SELECT through the normal database connector, stop at the first error.

-- CHECK 01: SIGNATURE online/history union, online-first dedup, stable page
-- Source: QdLogRepository.query/union; lines 38-47, 107-149
-- Expected on empty project tables: 0 rows.
SELECT event_id,request_id,batch_id,attempt_id,object_id,user_id,user_code,user_name,pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,result_code,lock_result,reason,client_ip,terminal_name,event_time,archive_month
  FROM (SELECT qd_o.*,ROWNUM qd_page
          FROM (SELECT event_id,request_id,batch_id,attempt_id,object_id,user_id,user_code,user_name,pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,result_code,lock_result,reason,client_ip,terminal_name,event_time,archive_month
            FROM (SELECT qd_u.*,ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY qd_source) qd_duplicate
                    FROM (
    SELECT event_id,request_id,batch_id,attempt_id,object_id,user_id,user_code,user_name,pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,result_code,lock_result,reason,client_ip,terminal_name,event_time,archive_month,0 qd_source
      FROM QDNC.qd_es_signlog
     WHERE pk_group='QD_ES_TEST_GROUP'
      AND (pk_org IN ('QD_ES_TEST_ORG'))
      AND event_time>='2026-07-31 16:00:00'
      AND event_time<'2026-08-31 16:00:00'
      AND user_id='QD_ES_TEST_USER'
      AND user_code='QD_ES_TEST_CODE'
      AND result_code='SUCCESS'
      AND node_code='QD_ES_TEST_NODE'
      AND bill_code='QD_ES_TEST_BILL'
      AND request_id='00000000000000000000000000000001'
      AND batch_id='00000000000000000000000000000001'
      AND reason LIKE '%SQL\_CHECK%' ESCAPE '\'
    UNION ALL
    SELECT event_id,request_id,batch_id,attempt_id,object_id,user_id,user_code,user_name,pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,result_code,lock_result,reason,client_ip,terminal_name,event_time,archive_month,1 qd_source
      FROM QDNC.qd_es_signlog_h202608
     WHERE pk_group='QD_ES_TEST_GROUP'
      AND (pk_org IN ('QD_ES_TEST_ORG'))
      AND event_time>='2026-07-31 16:00:00'
      AND event_time<'2026-08-31 16:00:00'
      AND user_id='QD_ES_TEST_USER'
      AND user_code='QD_ES_TEST_CODE'
      AND result_code='SUCCESS'
      AND node_code='QD_ES_TEST_NODE'
      AND bill_code='QD_ES_TEST_BILL'
      AND request_id='00000000000000000000000000000001'
      AND batch_id='00000000000000000000000000000001'
      AND reason LIKE '%SQL\_CHECK%' ESCAPE '\'
                    ) qd_u)
           WHERE qd_duplicate=1
                 ORDER BY event_time,event_id) qd_o
         WHERE ROWNUM<=100)
 WHERE qd_page>0;

-- CHECK 02: SIGNATURE online/history distinct count
-- Source: QdLogRepository.count/union; lines 54-59, 107-149
-- Expected on empty project tables: ROW_COUNT=0.
SELECT COUNT(DISTINCT event_id) AS row_count
  FROM (
    SELECT event_id,request_id,batch_id,attempt_id,object_id,user_id,user_code,user_name,pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,result_code,lock_result,reason,client_ip,terminal_name,event_time,archive_month,0 qd_source
      FROM QDNC.qd_es_signlog
     WHERE pk_group='QD_ES_TEST_GROUP'
      AND (pk_org IN ('QD_ES_TEST_ORG'))
      AND event_time>='2026-07-31 16:00:00'
      AND event_time<'2026-08-31 16:00:00'
      AND user_id='QD_ES_TEST_USER'
      AND user_code='QD_ES_TEST_CODE'
      AND result_code='SUCCESS'
      AND node_code='QD_ES_TEST_NODE'
      AND bill_code='QD_ES_TEST_BILL'
      AND request_id='00000000000000000000000000000001'
      AND batch_id='00000000000000000000000000000001'
      AND reason LIKE '%SQL\_CHECK%' ESCAPE '\'
    UNION ALL
    SELECT event_id,request_id,batch_id,attempt_id,object_id,user_id,user_code,user_name,pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,result_code,lock_result,reason,client_ip,terminal_name,event_time,archive_month,1 qd_source
      FROM QDNC.qd_es_signlog_h202608
     WHERE pk_group='QD_ES_TEST_GROUP'
      AND (pk_org IN ('QD_ES_TEST_ORG'))
      AND event_time>='2026-07-31 16:00:00'
      AND event_time<'2026-08-31 16:00:00'
      AND user_id='QD_ES_TEST_USER'
      AND user_code='QD_ES_TEST_CODE'
      AND result_code='SUCCESS'
      AND node_code='QD_ES_TEST_NODE'
      AND bill_code='QD_ES_TEST_BILL'
      AND request_id='00000000000000000000000000000001'
      AND batch_id='00000000000000000000000000000001'
      AND reason LIKE '%SQL\_CHECK%' ESCAPE '\'
  );

-- CHECK 03: AUDIT online/history union, online-first dedup, stable page
-- Source: QdLogRepository.query/union; lines 38-47, 107-149
-- Expected on empty project tables: 0 rows.
SELECT audit_id,request_id,batch_id,object_id,user_id,user_code,user_name,pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,result_code,reason,event_time,archive_month,client_ip,terminal_name
  FROM (SELECT qd_o.*,ROWNUM qd_page
          FROM (SELECT audit_id,request_id,batch_id,object_id,user_id,user_code,user_name,pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,result_code,reason,event_time,archive_month,client_ip,terminal_name
            FROM (SELECT qd_u.*,ROW_NUMBER() OVER (PARTITION BY audit_id ORDER BY qd_source) qd_duplicate
                    FROM (
    SELECT audit_id,request_id,batch_id,object_id,user_id,user_code,user_name,pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,result_code,reason,event_time,archive_month,client_ip,terminal_name,0 qd_source
      FROM QDNC.qd_es_audit
     WHERE pk_group='QD_ES_TEST_GROUP'
      AND (pk_org IN ('QD_ES_TEST_ORG'))
      AND event_time>='2026-07-31 16:00:00'
      AND event_time<'2026-08-31 16:00:00'
      AND user_id='QD_ES_TEST_USER'
      AND user_code='QD_ES_TEST_CODE'
      AND result_code='SUCCESS'
      AND node_code='QD_ES_TEST_NODE'
      AND bill_code='QD_ES_TEST_BILL'
      AND request_id='00000000000000000000000000000001'
      AND batch_id='00000000000000000000000000000001'
      AND reason LIKE '%SQL\_CHECK%' ESCAPE '\'
    UNION ALL
    SELECT audit_id,request_id,batch_id,object_id,user_id,user_code,user_name,pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,result_code,reason,event_time,archive_month,client_ip,terminal_name,1 qd_source
      FROM QDNC.qd_es_audit_h202608
     WHERE pk_group='QD_ES_TEST_GROUP'
      AND (pk_org IN ('QD_ES_TEST_ORG'))
      AND event_time>='2026-07-31 16:00:00'
      AND event_time<'2026-08-31 16:00:00'
      AND user_id='QD_ES_TEST_USER'
      AND user_code='QD_ES_TEST_CODE'
      AND result_code='SUCCESS'
      AND node_code='QD_ES_TEST_NODE'
      AND bill_code='QD_ES_TEST_BILL'
      AND request_id='00000000000000000000000000000001'
      AND batch_id='00000000000000000000000000000001'
      AND reason LIKE '%SQL\_CHECK%' ESCAPE '\'
                    ) qd_u)
           WHERE qd_duplicate=1
                 ORDER BY event_time,audit_id) qd_o
         WHERE ROWNUM<=100)
 WHERE qd_page>0;

-- CHECK 04: AUDIT online/history distinct count
-- Source: QdLogRepository.count/union; lines 54-59, 107-149
-- Expected on empty project tables: ROW_COUNT=0.
SELECT COUNT(DISTINCT audit_id) AS row_count
  FROM (
    SELECT audit_id,request_id,batch_id,object_id,user_id,user_code,user_name,pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,result_code,reason,event_time,archive_month,client_ip,terminal_name,0 qd_source
      FROM QDNC.qd_es_audit
     WHERE pk_group='QD_ES_TEST_GROUP'
      AND (pk_org IN ('QD_ES_TEST_ORG'))
      AND event_time>='2026-07-31 16:00:00'
      AND event_time<'2026-08-31 16:00:00'
      AND user_id='QD_ES_TEST_USER'
      AND user_code='QD_ES_TEST_CODE'
      AND result_code='SUCCESS'
      AND node_code='QD_ES_TEST_NODE'
      AND bill_code='QD_ES_TEST_BILL'
      AND request_id='00000000000000000000000000000001'
      AND batch_id='00000000000000000000000000000001'
      AND reason LIKE '%SQL\_CHECK%' ESCAPE '\'
    UNION ALL
    SELECT audit_id,request_id,batch_id,object_id,user_id,user_code,user_name,pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,result_code,reason,event_time,archive_month,client_ip,terminal_name,1 qd_source
      FROM QDNC.qd_es_audit_h202608
     WHERE pk_group='QD_ES_TEST_GROUP'
      AND (pk_org IN ('QD_ES_TEST_ORG'))
      AND event_time>='2026-07-31 16:00:00'
      AND event_time<'2026-08-31 16:00:00'
      AND user_id='QD_ES_TEST_USER'
      AND user_code='QD_ES_TEST_CODE'
      AND result_code='SUCCESS'
      AND node_code='QD_ES_TEST_NODE'
      AND bill_code='QD_ES_TEST_BILL'
      AND request_id='00000000000000000000000000000001'
      AND batch_id='00000000000000000000000000000001'
      AND reason LIKE '%SQL\_CHECK%' ESCAPE '\'
  );

-- CHECK 05: SECURITY online/history union, online-first dedup, stable page
-- Source: QdLogRepository.query/union; lines 38-47, 107-149 (includeGroupSecurity=true)
-- Expected on empty project tables: 0 rows.
SELECT event_id,source_system,source_event_id,source_version,user_id,user_code,user_name,pk_group,pk_org,event_type,result_code,client_ip,terminal_name,event_time,archive_month
  FROM (SELECT qd_o.*,ROWNUM qd_page
          FROM (SELECT event_id,source_system,source_event_id,source_version,user_id,user_code,user_name,pk_group,pk_org,event_type,result_code,client_ip,terminal_name,event_time,archive_month
            FROM (SELECT qd_u.*,ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY qd_source) qd_duplicate
                    FROM (
    SELECT event_id,source_system,source_event_id,source_version,user_id,user_code,user_name,pk_group,pk_org,event_type,result_code,client_ip,terminal_name,event_time,archive_month,0 qd_source
      FROM QDNC.qd_es_security
     WHERE pk_group='QD_ES_TEST_GROUP'
      AND (pk_org IN ('QD_ES_TEST_ORG') OR pk_org IS NULL)
      AND event_time>='2026-07-31 16:00:00'
      AND event_time<'2026-08-31 16:00:00'
      AND user_id='QD_ES_TEST_USER'
      AND user_code='QD_ES_TEST_CODE'
      AND result_code='SUCCESS'
    UNION ALL
    SELECT event_id,source_system,source_event_id,source_version,user_id,user_code,user_name,pk_group,pk_org,event_type,result_code,client_ip,terminal_name,event_time,archive_month,1 qd_source
      FROM QDNC.qd_es_security_h202608
     WHERE pk_group='QD_ES_TEST_GROUP'
      AND (pk_org IN ('QD_ES_TEST_ORG') OR pk_org IS NULL)
      AND event_time>='2026-07-31 16:00:00'
      AND event_time<'2026-08-31 16:00:00'
      AND user_id='QD_ES_TEST_USER'
      AND user_code='QD_ES_TEST_CODE'
      AND result_code='SUCCESS'
                    ) qd_u)
           WHERE qd_duplicate=1
                 ORDER BY event_time,event_id) qd_o
         WHERE ROWNUM<=100)
 WHERE qd_page>0;

-- CHECK 06: SECURITY online/history distinct count
-- Source: QdLogRepository.count/union; lines 54-59, 107-149
-- Expected on empty project tables: ROW_COUNT=0.
SELECT COUNT(DISTINCT event_id) AS row_count
  FROM (
    SELECT event_id,source_system,source_event_id,source_version,user_id,user_code,user_name,pk_group,pk_org,event_type,result_code,client_ip,terminal_name,event_time,archive_month,0 qd_source
      FROM QDNC.qd_es_security
     WHERE pk_group='QD_ES_TEST_GROUP'
      AND (pk_org IN ('QD_ES_TEST_ORG') OR pk_org IS NULL)
      AND event_time>='2026-07-31 16:00:00'
      AND event_time<'2026-08-31 16:00:00'
      AND user_id='QD_ES_TEST_USER'
      AND user_code='QD_ES_TEST_CODE'
      AND result_code='SUCCESS'
    UNION ALL
    SELECT event_id,source_system,source_event_id,source_version,user_id,user_code,user_name,pk_group,pk_org,event_type,result_code,client_ip,terminal_name,event_time,archive_month,1 qd_source
      FROM QDNC.qd_es_security_h202608
     WHERE pk_group='QD_ES_TEST_GROUP'
      AND (pk_org IN ('QD_ES_TEST_ORG') OR pk_org IS NULL)
      AND event_time>='2026-07-31 16:00:00'
      AND event_time<'2026-08-31 16:00:00'
      AND user_id='QD_ES_TEST_USER'
      AND user_code='QD_ES_TEST_CODE'
      AND result_code='SUCCESS'
  );

-- CHECK 07: qd_es_change primary-key-only online/history pagination; no CLOB set operation
-- Source: AuditDetailsRepository.page/idUnion/orgCondition; lines 75-81, 108-123
-- Expected on empty project tables: 0 rows.
SELECT change_id
  FROM (SELECT qd_ordered.*,ROWNUM qd_page
          FROM (SELECT DISTINCT change_id
                  FROM (
              SELECT change_id
                FROM QDNC.qd_es_change
               WHERE audit_id='00000000000000000000000000000002'
                 AND request_id='00000000000000000000000000000001'
                 AND pk_group='QD_ES_TEST_GROUP'
                 AND (pk_org IN ('QD_ES_TEST_ORG'))
              UNION ALL
              SELECT change_id
                FROM QDNC.qd_es_change_h202608
               WHERE audit_id='00000000000000000000000000000002'
                 AND request_id='00000000000000000000000000000001'
                 AND pk_group='QD_ES_TEST_GROUP'
                 AND (pk_org IN ('QD_ES_TEST_ORG'))
                  )
                 ORDER BY change_id) qd_ordered
         WHERE ROWNUM<=100)
 WHERE qd_page>0;

-- CHECK 08: qd_es_snapshot primary-key-only online/history pagination; no CLOB set operation
-- Source: AuditDetailsRepository.page/idUnion/orgCondition; lines 75-81, 108-123
-- Expected on empty project tables: 0 rows.
SELECT snapshot_id
  FROM (SELECT qd_ordered.*,ROWNUM qd_page
          FROM (SELECT DISTINCT snapshot_id
                  FROM (
              SELECT snapshot_id
                FROM QDNC.qd_es_snapshot
               WHERE audit_id='00000000000000000000000000000002'
                 AND request_id='00000000000000000000000000000001'
                 AND pk_group='QD_ES_TEST_GROUP'
                 AND (pk_org IN ('QD_ES_TEST_ORG'))
              UNION ALL
              SELECT snapshot_id
                FROM QDNC.qd_es_snapshot_h202608
               WHERE audit_id='00000000000000000000000000000002'
                 AND request_id='00000000000000000000000000000001'
                 AND pk_group='QD_ES_TEST_GROUP'
                 AND (pk_org IN ('QD_ES_TEST_ORG'))
                  )
                 ORDER BY snapshot_id) qd_ordered
         WHERE ROWNUM<=100)
 WHERE qd_page>0;

-- CHECK 09: archive eligible closed-request signature candidate batch
-- Source: MonthlyArchiveStore.candidates/eligible; lines 71-77, 111-114
-- Expected on empty project tables: 0 rows.
SELECT event_id
  FROM (SELECT event_id
          FROM QDNC.qd_es_signlog s
         WHERE s.archive_month='202608'
           AND EXISTS (SELECT 1
                         FROM QDNC.qd_es_locator l
                        WHERE l.request_id=s.request_id
                          AND l.archive_month=s.archive_month
                          AND l.request_closed='Y')
         ORDER BY event_id)
 WHERE ROWNUM<=100;

-- CHECK 10: archive snapshot all-column verification including DBMS_LOB.COMPARE
-- Source: MonthlyArchiveStore.move; lines 89-100 (verification SELECT only)
-- Expected on empty project tables: ROW_COUNT=0.
SELECT COUNT(*) AS row_count
  FROM QDNC.qd_es_snapshot s
 WHERE s.snapshot_id IN ('00000000000000000000000000000003')
   AND s.archive_month='202608'
   AND NOT EXISTS (
       SELECT 1
         FROM QDNC.qd_es_snapshot_h202608 h
        WHERE h.snapshot_id=s.snapshot_id
          AND
         ((s.snapshot_id IS NULL AND h.snapshot_id IS NULL) OR s.snapshot_id=h.snapshot_id)
         AND          ((s.audit_id IS NULL AND h.audit_id IS NULL) OR s.audit_id=h.audit_id)
         AND          ((s.request_id IS NULL AND h.request_id IS NULL) OR s.request_id=h.request_id)
         AND          ((s.object_id IS NULL AND h.object_id IS NULL) OR s.object_id=h.object_id)
         AND          ((s.snapshot_phase IS NULL AND h.snapshot_phase IS NULL) OR s.snapshot_phase=h.snapshot_phase)
         AND          ((s.entity_name IS NULL AND h.entity_name IS NULL) OR s.entity_name=h.entity_name)
         AND          ((s.row_id IS NULL AND h.row_id IS NULL) OR s.row_id=h.row_id)
         AND          ((s.parent_row_id IS NULL AND h.parent_row_id IS NULL) OR s.parent_row_id=h.parent_row_id)
         AND          ((s.payload IS NULL AND h.payload IS NULL) OR DBMS_LOB.COMPARE(s.payload,h.payload)=0)
         AND          ((s.payload_hash IS NULL AND h.payload_hash IS NULL) OR s.payload_hash=h.payload_hash)
         AND          ((s.pk_group IS NULL AND h.pk_group IS NULL) OR s.pk_group=h.pk_group)
         AND          ((s.pk_org IS NULL AND h.pk_org IS NULL) OR s.pk_org=h.pk_org)
         AND          ((s.event_time IS NULL AND h.event_time IS NULL) OR s.event_time=h.event_time)
         AND          ((s.archive_month IS NULL AND h.archive_month IS NULL) OR s.archive_month=h.archive_month)
   );

