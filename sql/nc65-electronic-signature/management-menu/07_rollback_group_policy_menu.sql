-- QDNC only. Revoke the seven new activities and the group function from assigned NC duties first.
-- Execute in a transaction-capable session with auto-commit off.
DECLARE
  v_count NUMBER;

  PROCEDURE remove_button(p_number NUMBER, p_parent VARCHAR2, p_resource VARCHAR2) IS
    v_suffix VARCHAR2(8) := LPAD(TO_CHAR(p_number),8,'0');
    v_button VARCHAR2(20) := '1001A9QDBDB0' || v_suffix;
    v_action VARCHAR2(20) := '1001A9QDBDA0' || v_suffix;
  BEGIN
    DELETE FROM QDNC.sm_busiactive_btn
      WHERE pk_relation='1001A9QDBDL0' || v_suffix AND pk_busiactive=v_action AND pk_butn=v_button
        AND EXISTS (SELECT 1 FROM QDNC.sm_busiactivereg WHERE pk_busiactive=v_action
                    AND parent_id=p_parent AND busi_pk=p_resource)
        AND EXISTS (SELECT 1 FROM QDNC.sm_butnregister WHERE pk_btn=v_button
                    AND parent_id=p_parent AND resid='btn_' || p_resource);
    DELETE FROM QDNC.sm_busiactivereg
      WHERE pk_busiactive=v_action AND parent_id=p_parent AND busi_pk=p_resource;
    DELETE FROM QDNC.sm_butnregister
      WHERE pk_btn=v_button AND parent_id=p_parent AND resid='btn_' || p_resource;
  END;
BEGIN
  SAVEPOINT qd_group_menu_rollback;
  IF SYS_CONTEXT('USERENV','SESSION_USER')<>'QDNC'
      OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA')<>'QDNC'
      OR SYS_CONTEXT('USERENV','DB_UNIQUE_NAME')<>'DB'
      OR LOWER(SYS_CONTEXT('USERENV','SERVER_HOST'))<>'ncoa-db' THEN
    RAISE_APPLICATION_ERROR(-20145,'Expected QDNC test environment');
  END IF;
  LOCK TABLE QDNC.sm_funcregister IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE QDNC.sm_menuitemreg IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE QDNC.sm_butnregister IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE QDNC.sm_busiactivereg IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE QDNC.sm_busiactive_btn IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE QDNC.sm_busiactive_perm IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE QDNC.sm_resp_func IN SHARE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM QDNC.sm_resp_func
    WHERE busi_pk IN ('QD_ES_BD_POLICY','QD_ES_BD_POLICY_QUERY','QD_ES_BD_POLICY_SAVE',
      'QD_ES_BD_POLICY_MASTER','QD_ES_SIGNLOG_GROUP_QUERY','QD_ES_SIGNLOG_GROUP_EXPORT',
      'QD_ES_AUDITLOG_GROUP_QUERY','QD_ES_AUDITLOG_GROUP_EXPORT') AND NVL(dr,0)=0;
  IF v_count<>0 THEN
    RAISE_APPLICATION_ERROR(-20146,'Remove active group policy and group log grants before rollback');
  END IF;
  remove_button(1,'1001A9QDBDF000000001','QD_ES_BD_POLICY_QUERY');
  remove_button(2,'1001A9QDBDF000000001','QD_ES_BD_POLICY_SAVE');
  remove_button(3,'1001A9QDBDF000000001','QD_ES_BD_POLICY_MASTER');
  remove_button(4,'1001A9QDESMF00000002','QD_ES_SIGNLOG_GROUP_QUERY');
  remove_button(5,'1001A9QDESMF00000002','QD_ES_SIGNLOG_GROUP_EXPORT');
  remove_button(6,'1001A9QDESMF00000003','QD_ES_AUDITLOG_GROUP_QUERY');
  remove_button(7,'1001A9QDESMF00000003','QD_ES_AUDITLOG_GROUP_EXPORT');
  DELETE FROM QDNC.sm_busiactive_perm
    WHERE pk_busiactive_perm='1001A9QDBDC000000001' AND subjectcode='QD_ES_BD_POLICY';
  DELETE FROM QDNC.sm_menuitemreg
    WHERE pk_menuitem='1001A9QDBDM000000001' AND funcode='QD_ES_BD_POLICY';
  DELETE FROM QDNC.sm_funcregister
    WHERE cfunid='1001A9QDBDF000000001' AND funcode='QD_ES_BD_POLICY';
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_group_menu_rollback;
    RAISE;
END;
/
