-- QDNC only. Run after the existing management-menu installation and group-policy schema upgrade.
-- Use a transaction-capable session with auto-commit off. Inspect the verification results before committing.
DECLARE
  v_group CONSTANT VARCHAR2(20) := '0001A9100000000005MH';
  v_parent CONSTANT VARCHAR2(20) := '1001A9QDESMF00000000';
  v_func CONSTANT VARCHAR2(20) := '1001A9QDBDF000000001';
  v_menu CONSTANT VARCHAR2(20) := '1004ZZ10000000000FFL';
  v_code CONSTANT VARCHAR2(30) := 'QD_ES_BD_POLICY';
  v_name CONSTANT VARCHAR2(100) := UNISTR('\57FA\7840\6863\6848\96C6\56E2\7B56\7565');
  v_now CONSTANT VARCHAR2(19) := TO_CHAR(SYSDATE,'YYYY-MM-DD HH24:MI:SS');
  v_count NUMBER;

  PROCEDURE add_button(p_number NUMBER, p_parent VARCHAR2, p_function VARCHAR2,
      p_code VARCHAR2, p_name VARCHAR2) IS
    v_suffix VARCHAR2(8) := LPAD(TO_CHAR(p_number),8,'0');
    v_button VARCHAR2(20) := '1001A9QDBDB0' || v_suffix;
    v_action VARCHAR2(20) := '1001A9QDBDA0' || v_suffix;
    v_resource VARCHAR2(100) := p_function || '_' || p_code;
  BEGIN
    SELECT COUNT(*) INTO v_count FROM QDNC.sm_butnregister
      WHERE parent_id=p_parent AND btncode=p_code AND NVL(dr,0)=0;
    IF v_count<>0 THEN
      RAISE_APPLICATION_ERROR(-20141,'A group policy or log button already exists: ' || v_resource);
    END IF;
    INSERT INTO QDNC.sm_butnregister
      (pk_btn,parent_id,btncode,btnname,btnownertype,isenable,isbuttonpower,iskeyfunc,resid)
      VALUES(v_button,p_parent,p_code,p_name,0,'Y','Y','N','btn_' || v_resource);
    INSERT INTO QDNC.sm_busiactivereg
      (pk_busiactive,parent_id,code,name,busi_pk,ownertype,resid)
      VALUES(v_action,p_parent,p_code,p_name,v_resource,0,v_resource);
    INSERT INTO QDNC.sm_busiactive_btn(pk_relation,pk_busiactive,pk_butn)
      VALUES('1001A9QDBDL0' || v_suffix,v_action,v_button);
  END;
BEGIN
  SAVEPOINT qd_group_menu;
  IF SYS_CONTEXT('USERENV','SESSION_USER')<>'QDNC'
      OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA')<>'QDNC'
      OR SYS_CONTEXT('USERENV','DB_UNIQUE_NAME')<>'DB'
      OR LOWER(SYS_CONTEXT('USERENV','SERVER_HOST'))<>'ncoa-db' THEN
    RAISE_APPLICATION_ERROR(-20140,'Expected QDNC test environment');
  END IF;
  SELECT COUNT(*) INTO v_count FROM QDNC.qd_es_schema_version
    WHERE version_no='1.2.0';
  IF v_count<>1 THEN RAISE_APPLICATION_ERROR(-20147,'Expected the 1.2.0 group policy schema'); END IF;
  LOCK TABLE QDNC.sm_funcregister IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE QDNC.sm_menuitemreg IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  LOCK TABLE QDNC.sm_butnregister IN SHARE ROW EXCLUSIVE MODE NOWAIT;
  SELECT COUNT(*) INTO v_count FROM QDNC.sm_funcregister
    WHERE NVL(dr,0)=0 AND ((cfunid=v_parent AND funcode='QD_ES_MGMT')
      OR (cfunid='1001A9QDESMF00000002' AND funcode='QD_ES_SIGNLOG')
      OR (cfunid='1001A9QDESMF00000003' AND funcode='QD_ES_AUDITLOG'));
  IF v_count<>3 THEN RAISE_APPLICATION_ERROR(-20142,'Management menu baseline is missing'); END IF;
  SELECT COUNT(*) INTO v_count FROM QDNC.sm_funcregister
    WHERE cfunid=v_func OR funcode=v_code;
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20143,'Group policy function already exists'); END IF;
  SELECT COUNT(*) INTO v_count FROM QDNC.sm_menuitemreg
    WHERE pk_menuitem='1001A9QDBDM000000001' OR funcode=v_code
      OR (pk_menu=v_menu AND menuitemcode='109805');
  IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20144,'Group policy menu item collides'); END IF;
  INSERT INTO QDNC.sm_funcregister
    (cfunid,funcode,fun_name,class_name,own_module,parent_id,orgtypecode,
     funtype,fun_property,isfunctype,isbuttonpower,isenable,iscauserusable)
    VALUES(v_func,v_code,v_name,'nc.ui.uapbd.qd.esign.QdGroupPolicyFunclet','1014',v_parent,
     'GROUPORGTYPE00000000',0,0,'N','Y','Y','N');
  INSERT INTO QDNC.sm_menuitemreg
    (pk_menuitem,pk_menu,menuitemcode,menuitemname,funcode,ismenutype,resid)
    VALUES('1001A9QDBDM000000001',v_menu,'109805',v_name,v_code,'N','D109805');
  INSERT INTO QDNC.sm_busiactive_perm
    (pk_busiactive_perm,pk_subject,subjectcode,displayname,subjecttype,parentid,own_module,
     pk_group,isbuttonpower,resid,creationtime)
    VALUES('1001A9QDBDC000000001',v_func,v_code,v_name,0,v_parent,'1014',v_group,'Y',v_code,v_now);

  add_button(1,v_func,v_code,'QUERY',UNISTR('\67E5\8BE2'));
  add_button(2,v_func,v_code,'SAVE',UNISTR('\4FDD\5B58\7B56\7565'));
  add_button(3,v_func,v_code,'MASTER',UNISTR('\4FDD\5B58\96C6\56E2\603B\63A7'));
  add_button(4,'1001A9QDESMF00000002','QD_ES_SIGNLOG','GROUP_QUERY',UNISTR('\67E5\770B\96C6\56E2\65E5\5FD7'));
  add_button(5,'1001A9QDESMF00000002','QD_ES_SIGNLOG','GROUP_EXPORT',UNISTR('\5BFC\51FA\96C6\56E2\65E5\5FD7'));
  add_button(6,'1001A9QDESMF00000003','QD_ES_AUDITLOG','GROUP_QUERY',UNISTR('\67E5\770B\96C6\56E2\65E5\5FD7'));
  add_button(7,'1001A9QDESMF00000003','QD_ES_AUDITLOG','GROUP_EXPORT',UNISTR('\5BFC\51FA\96C6\56E2\65E5\5FD7'));
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK TO qd_group_menu;
    RAISE;
END;
/

SELECT funcode, orgtypecode, class_name FROM QDNC.sm_funcregister WHERE cfunid='1001A9QDBDF000000001';
SELECT f.funcode,b.btncode,a.busi_pk
  FROM QDNC.sm_butnregister b JOIN QDNC.sm_funcregister f ON f.cfunid=b.parent_id
  JOIN QDNC.sm_busiactive_btn l ON l.pk_butn=b.pk_btn
  JOIN QDNC.sm_busiactivereg a ON a.pk_busiactive=l.pk_busiactive
 WHERE b.pk_btn BETWEEN '1001A9QDBDB000000001' AND '1001A9QDBDB000000007'
 ORDER BY b.pk_btn;
