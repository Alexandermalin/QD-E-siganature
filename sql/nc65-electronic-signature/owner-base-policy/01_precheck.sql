-- Read-only precheck. The operator must stop new base-data operations and drain RUNNING requests.
SELECT SYS_CONTEXT('USERENV','SESSION_USER') session_user,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') current_schema,
       SYS_CONTEXT('USERENV','DB_UNIQUE_NAME') database_name,
       SYS_CONTEXT('USERENV','SERVER_HOST') server_host FROM dual;
SELECT version_no, applied_at, description FROM qd_es_schema_version
 WHERE version_no IN ('1.2.0','1.2.0-RB','1.3.0','1.3.0-RB') ORDER BY version_no;
SELECT privilege FROM session_privs WHERE privilege='CREATE TRIGGER';
SELECT trigger_name,table_name,status FROM user_triggers
 WHERE trigger_name IN ('QDES_OWNER_REQ_RB_GUARD','QDES_OWNER_OBJ_RB_GUARD',
                        'QDES_OWNER_SIGN_RB_GUARD','QDES_OWNER_AUDIT_RB_GUARD')
 ORDER BY trigger_name;
SELECT business_state, signature_state, COUNT(*) request_count
  FROM qd_es_request r
 WHERE EXISTS (SELECT 1 FROM qd_es_node n WHERE n.node_code=r.node_code
                AND n.button_code=r.button_code AND n.group_scope_id IS NOT NULL)
   AND r.business_state IN ('PENDING','READY','RUNNING')
 GROUP BY business_state, signature_state ORDER BY business_state, signature_state;
SELECT pk_group, master_enabled, control_version FROM qd_es_bd_control ORDER BY pk_group;
SELECT pk_group, COUNT(*) configured_count,
       SUM(CASE WHEN sign_enabled='Y' THEN 1 ELSE 0 END) sign_count,
       SUM(CASE WHEN audit_enabled='Y' THEN 1 ELSE 0 END) audit_count
  FROM qd_es_bd_policy GROUP BY pk_group ORDER BY pk_group;
SELECT table_name, column_name, data_type, data_length, data_precision, nullable
  FROM user_tab_columns
 WHERE table_name IN ('BD_MATERIAL','BD_MARORG','BD_MATERIALFI','BD_MATERIALPU')
   AND column_name IN ('PK_MATERIAL','PK_MARORG','PK_MATERIALFI','PK_MATERIALPU',
                       'PK_GROUP','PK_ORG','PK_SOURCE','DR')
 ORDER BY table_name, column_id;
SELECT table_name FROM user_tables
 WHERE table_name IN ('QD_ES_REQUEST','QD_ES_OBJECT','QD_ES_SIGNLOG','QD_ES_AUDIT','QD_ES_BD_ORG_POLICY')
    OR REGEXP_LIKE(table_name, '^QD_ES_(REQUEST|OBJECT|SIGNLOG|AUDIT)_H[0-9]{6}$')
 ORDER BY table_name;
-- 在线及已预建历史请求表必须均支持大于 9999 的对象数；未安装时列可以缺失。
SELECT t.table_name,c.data_type,c.data_precision,c.data_scale,c.nullable
  FROM user_tables t LEFT JOIN user_tab_columns c
    ON c.table_name=t.table_name AND c.column_name='OWNER_OBJECT_COUNT'
 WHERE t.table_name='QD_ES_REQUEST' OR REGEXP_LIKE(t.table_name,'^QD_ES_REQUEST_H[0-9]{6}$')
 ORDER BY t.table_name;
