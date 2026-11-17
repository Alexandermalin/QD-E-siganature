-- Read-only preflight for the approved QDNC migration. Target status must be OK.
SELECT CASE WHEN SYS_CONTEXT('USERENV','SESSION_USER') = 'QDNC'
       AND SYS_CONTEXT('USERENV','CURRENT_SCHEMA') = 'QDNC'
       AND SYS_CONTEXT('USERENV','DB_UNIQUE_NAME') = 'DB'
       AND SYS_CONTEXT('USERENV','SERVICE_NAME') = 'DB'
       AND LOWER(SYS_CONTEXT('USERENV','SERVER_HOST')) = 'ncoa-db'
            THEN 'OK' ELSE 'STOP' END AS target_status,
       (SELECT COUNT(*) FROM QDNC.qd_es_schema_version WHERE version_no = '1.0.0') AS baseline_version,
       (SELECT COUNT(*) FROM QDNC.qd_es_schema_version WHERE version_no = '1.1.0') AS applied_version,
       (SELECT COUNT(*) FROM QDNC.qd_es_schema_version WHERE version_no = '1.1.0-RB') AS rollback_marker,
       (SELECT COUNT(*) FROM all_tables WHERE owner = 'QDNC' AND table_name = 'QD_ES_ORG_CONTROL') AS org_table_exists,
       (SELECT COUNT(*) FROM QDNC.qd_es_request WHERE business_state = 'RUNNING') AS running_requests
  FROM dual;

-- An effective legacy policy with invalid mapping or organization must be reconciled before migration.
SELECT p.pk_group, p.pk_org, p.node_code, p.button_code
  FROM QDNC.qd_es_policy p
  JOIN QDNC.qd_es_control c ON c.pk_group = p.pk_group AND c.master_enabled = 'Y'
  LEFT JOIN QDNC.qd_es_node n ON n.node_code = p.node_code AND n.button_code = p.button_code
  LEFT JOIN QDNC.org_orgs o ON o.pk_group = p.pk_group AND o.pk_org = p.pk_org
       AND NVL(o.dr, 0) = 0 AND o.enablestate = 2 AND o.isbusinessunit = 'Y' AND o.islastversion = 'Y'
 WHERE p.enabled = 'Y' AND (p.sign_enabled = 'Y' OR p.audit_enabled = 'Y')
   AND (p.validated <> 'Y' OR NVL(n.validated, 'N') <> 'Y' OR TRIM(n.adapter_class) IS NULL
        OR TRIM(n.operation_types) IS NULL OR TRIM(n.object_type) IS NULL OR o.pk_org IS NULL)
 ORDER BY p.pk_group, p.pk_org, p.node_code, p.button_code;

-- Planned rows. Review only after the preceding query returns zero rows.
WITH org_scopes (pk_group, pk_org, request_version) AS (
    SELECT pk_group, pk_org, 0 FROM QDNC.qd_es_policy
    UNION ALL
    SELECT pk_group, pk_org, control_version FROM QDNC.qd_es_request
), org_versions AS (
    SELECT pk_group, pk_org, MAX(request_version) AS request_version
      FROM org_scopes
     GROUP BY pk_group, pk_org
)
SELECT v.pk_group, v.pk_org,
       CASE WHEN c.master_enabled = 'Y' AND EXISTS (
         SELECT 1 FROM QDNC.qd_es_policy p
          WHERE p.pk_group = v.pk_group AND p.pk_org = v.pk_org AND p.enabled = 'Y'
            AND (p.sign_enabled = 'Y' OR p.audit_enabled = 'Y')
       ) THEN 'Y' ELSE 'N' END AS master_enabled,
       GREATEST(NVL(c.policy_version, 0), NVL(v.request_version, 0), 0) + 1 AS control_version
  FROM org_versions v
  LEFT JOIN QDNC.qd_es_control c ON c.pk_group = v.pk_group
 ORDER BY v.pk_group, v.pk_org;
