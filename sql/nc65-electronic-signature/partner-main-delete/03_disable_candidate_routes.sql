-- Disable only this candidate's four alias mappings. Stop clients and drain in-flight requests first.
-- Restore the native client configuration in the same maintenance window. This script does not commit.
DECLARE
  v_adapter CONSTANT VARCHAR2(300) := 'nc.itf.uapbd.qd.esign.IMaterialPageDeleteService';
  v_count NUMBER;
BEGIN
  LOCK TABLE qd_es_node IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  FOR r IN (
    SELECT 'QD-ES-35B5B1E09B5A4A80@10140CUG' scope_id,'QD-ES-35B5B1E09B5A4A80' group_scope_id,
           '10140CUG' node_code,'BD_CUSTOMER' object_type FROM dual
    UNION ALL SELECT 'QD-ES-35B5B1E09B5A4A80@10140CUO','QD-ES-35B5B1E09B5A4A80',
                     '10140CUO','BD_CUSTOMER' FROM dual
    UNION ALL SELECT 'QD-ES-83A95678666FC134@10140SUG','QD-ES-83A95678666FC134',
                     '10140SUG','BD_SUPPLIER' FROM dual
    UNION ALL SELECT 'QD-ES-83A95678666FC134@10140SUO','QD-ES-83A95678666FC134',
                     '10140SUO','BD_SUPPLIER' FROM dual
  ) LOOP
    SELECT COUNT(*) INTO v_count FROM qd_es_node
     WHERE scope_id=r.scope_id AND group_scope_id=r.group_scope_id
       AND node_code=r.node_code AND button_code='Delete' AND adapter_class=v_adapter
       AND operation_types='DELETE' AND object_type=r.object_type;
    IF v_count<>1 THEN
      RAISE_APPLICATION_ERROR(-20315,'Candidate route missing or changed; reconcile manually: '||r.scope_id);
    END IF;
  END LOOP;
  UPDATE qd_es_node SET adapter_class=NULL,validated='N',mapping_state='UNMAPPED'
   WHERE scope_id IN (
    'QD-ES-35B5B1E09B5A4A80@10140CUG','QD-ES-35B5B1E09B5A4A80@10140CUO',
    'QD-ES-83A95678666FC134@10140SUG','QD-ES-83A95678666FC134@10140SUO')
     AND adapter_class=v_adapter;
  IF SQL%ROWCOUNT<>4 THEN RAISE_APPLICATION_ERROR(-20316,'Candidate route disable count changed'); END IF;
END;
/
-- Review the resulting aliases, then explicitly COMMIT or ROLLBACK.
