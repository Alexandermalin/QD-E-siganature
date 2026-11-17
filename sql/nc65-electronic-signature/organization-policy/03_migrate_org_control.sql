-- Run after creating and verifying qd_es_org_control. NC services must be quiesced.
-- Use manual commit. Review the result sets and commit the migration as one transaction.
DECLARE
  v_count NUMBER;
  v_now CHAR(19) := TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP), 'YYYY-MM-DD HH24:MI:SS');
BEGIN
  SAVEPOINT qd_org_control_migration;
  IF NVL(SYS_CONTEXT('USERENV','SESSION_USER'),'~') <> 'QDNC'
      OR NVL(SYS_CONTEXT('USERENV','CURRENT_SCHEMA'),'~') <> 'QDNC'
      OR NVL(SYS_CONTEXT('USERENV','DB_UNIQUE_NAME'),'~') <> 'DB'
      OR NVL(SYS_CONTEXT('USERENV','SERVICE_NAME'),'~') <> 'DB'
      OR NVL(LOWER(SYS_CONTEXT('USERENV','SERVER_HOST')),'~') <> 'ncoa-db' THEN
    RAISE_APPLICATION_ERROR(-20091, 'Expected the verified QDNC test database');
  END IF;
  LOCK TABLE QDNC.qd_es_schema_version IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM QDNC.qd_es_schema_version WHERE version_no = '1.1.0-RB';
  IF v_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20092, 'Prior rollback recorded. Review a new cutover before reapplying.');
  END IF;
  SELECT COUNT(*) INTO v_count FROM QDNC.qd_es_schema_version WHERE version_no = '1.1.0';
  IF v_count = 1 THEN RETURN; END IF;

  SELECT COUNT(*) INTO v_count FROM QDNC.qd_es_schema_version WHERE version_no = '1.0.0';
  IF v_count <> 1 THEN RAISE_APPLICATION_ERROR(-20093, 'Expected baseline 1.0.0'); END IF;

  LOCK TABLE QDNC.qd_es_control IN SHARE MODE NOWAIT;
  LOCK TABLE QDNC.qd_es_node IN SHARE MODE NOWAIT;
  LOCK TABLE QDNC.qd_es_policy IN SHARE MODE NOWAIT;
  LOCK TABLE QDNC.qd_es_request IN SHARE MODE NOWAIT;
  LOCK TABLE QDNC.qd_es_org_control IN SHARE ROW EXCLUSIVE MODE NOWAIT;

  SELECT COUNT(*) INTO v_count FROM QDNC.qd_es_request WHERE business_state = 'RUNNING';
  IF v_count <> 0 THEN RAISE_APPLICATION_ERROR(-20094, 'Reconcile running requests before cutover'); END IF;
  SELECT COUNT(*) INTO v_count FROM QDNC.qd_es_org_control;
  IF v_count <> 0 THEN RAISE_APPLICATION_ERROR(-20095, 'Organization controls exist without the upgrade marker'); END IF;

  SELECT COUNT(*) INTO v_count
    FROM QDNC.qd_es_policy p
    JOIN QDNC.qd_es_control c ON c.pk_group = p.pk_group AND c.master_enabled = 'Y'
    LEFT JOIN QDNC.qd_es_node n ON n.node_code = p.node_code AND n.button_code = p.button_code
    LEFT JOIN QDNC.org_orgs o ON o.pk_group = p.pk_group AND o.pk_org = p.pk_org
         AND NVL(o.dr, 0) = 0 AND o.enablestate = 2 AND o.isbusinessunit = 'Y' AND o.islastversion = 'Y'
   WHERE p.enabled = 'Y' AND (p.sign_enabled = 'Y' OR p.audit_enabled = 'Y')
     AND (p.validated <> 'Y' OR NVL(n.validated, 'N') <> 'Y' OR TRIM(n.adapter_class) IS NULL
          OR TRIM(n.operation_types) IS NULL OR TRIM(n.object_type) IS NULL OR o.pk_org IS NULL);
  IF v_count <> 0 THEN
    RAISE_APPLICATION_ERROR(-20096, 'Active legacy policies have invalid organizations or mappings. See precheck.');
  END IF;

  INSERT INTO QDNC.qd_es_org_control (pk_group, pk_org, master_enabled, control_version, modified_at)
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
         GREATEST(NVL(c.policy_version, 0), NVL(v.request_version, 0), 0) + 1 AS control_version,
         v_now
    FROM org_versions v
    LEFT JOIN QDNC.qd_es_control c ON c.pk_group = v.pk_group;

  INSERT INTO QDNC.qd_es_schema_version (version_no, applied_at, description)
  VALUES ('1.1.0', v_now, 'Organization-scoped electronic signature and audit controls');
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_org_control_migration;
    RAISE;
END;
/

SELECT version_no, applied_at FROM QDNC.qd_es_schema_version WHERE version_no = '1.1.0';
SELECT pk_group, pk_org, master_enabled, control_version, modified_at
  FROM QDNC.qd_es_org_control ORDER BY pk_group, pk_org;
