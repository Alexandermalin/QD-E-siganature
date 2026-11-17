-- QDNC TEST ONLY. Approval required before execution.
-- Execute as a stop-on-first-error Oracle batch. DDL commits implicitly.
-- Adds 17 isolated QD tables + 7 August history tables; seeds 243 disabled entries.
-- Does not register, enable or replace any native NC node, plugin, listener or rule.
DECLARE
  n NUMBER;
  c VARCHAR2(100);
BEGIN
  IF SYS_CONTEXT('USERENV','SESSION_USER') <> 'QDNC'
     OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'QDNC' THEN
    RAISE_APPLICATION_ERROR(-20001,'Expected QDNC session user and current schema');
  END IF;
  SELECT value INTO c FROM nls_database_parameters WHERE parameter='NLS_CHARACTERSET';
  IF c <> 'ZHS16GBK' THEN RAISE_APPLICATION_ERROR(-20002,'Site charset changed; re-review'); END IF;
  SELECT COUNT(*) INTO n FROM user_objects WHERE object_name IN ('QD_ES_SCHEMA_VERSION','QD_ES_CONTROL','QD_ES_NODE','QD_ES_POLICY','QD_ES_REQUEST','QD_ES_OBJECT','QD_ES_SIGNLOG','QD_ES_AUDIT','QD_ES_CHANGE','QD_ES_SNAPSHOT','QD_ES_SECURITY','QD_ES_SECURITY_KEY','QD_ES_SYNC_CURSOR','QD_ES_LOCATOR','QD_ES_ARCHIVE','QD_ES_JOB','QD_ES_FAULT','QD_ES_REQUEST_H202608','QD_ES_OBJECT_H202608','QD_ES_SIGNLOG_H202608','QD_ES_AUDIT_H202608','QD_ES_CHANGE_H202608','QD_ES_SNAPSHOT_H202608','QD_ES_SECURITY_H202608','QDES_REQUEST_USER_IX','QDES_REQUEST_BATCH_IX','QDES_REQUEST_EXPIRY_IX','QDES_REQUEST_MONTH_IX','QDES_OBJECT_REQUEST_IX','QDES_OBJECT_BILL_IX','QDES_OBJECT_PK_IX','QDES_SIGNLOG_QUERY_IX','QDES_SIGNLOG_REQUEST_IX','QDES_SIGNLOG_BATCH_IX','QDES_SIGNLOG_BILL_IX','QDES_SIGNLOG_MONTH_IX','QDES_AUDIT_QUERY_IX','QDES_AUDIT_BATCH_IX','QDES_AUDIT_BILL_IX','QDES_AUDIT_MONTH_IX','QDES_CHANGE_AUDIT_IX','QDES_CHANGE_REQUEST_IX','QDES_CHANGE_MONTH_IX','QDES_SNAPSHOT_AUDIT_IX','QDES_SNAPSHOT_REQUEST_IX','QDES_SNAPSHOT_MONTH_IX','QDES_SECURITY_QUERY_IX','QDES_SECURITY_MONTH_IX','QDES_JOB_RETRY_IX','QDES_FAULT_REQUEST_IX','QDH202608_1_SCOPE_IX','QDH202608_2_SCOPE_IX','QDH202608_2_REQ_IX','QDH202608_2_BILL_IX','QDH202608_3_SCOPE_IX','QDH202608_3_REQ_IX','QDH202608_3_BILL_IX','QDH202608_4_SCOPE_IX','QDH202608_4_REQ_IX','QDH202608_4_BILL_IX','QDH202608_5_SCOPE_IX','QDH202608_5_REQ_IX','QDH202608_5_AUDIT_IX','QDH202608_6_SCOPE_IX','QDH202608_6_REQ_IX','QDH202608_6_AUDIT_IX','QDH202608_7_SCOPE_IX');
  IF n <> 0 THEN RAISE_APPLICATION_ERROR(-20003,'Target object already exists; inspect partial install'); END IF;
END;
/

-- BEGIN SOURCE 001_schema.sql
-- 齐都 NC65 电子签名自建表结构 V1.0.0，适用 Oracle 11g 及以上。
-- 执行前审核审批；Oracle DDL 会隐式提交，不能依靠事务回滚撤销建表。
-- 先在专用 NC 测试应用用户下核实；本脚本不显式提交。
-- 自建记录标识使用 UUID32；此处不创建 NC 原生平台注册。
-- 表用途：部署版本登记，保留实际应用的结构版本和时间。
CREATE TABLE qd_es_schema_version (
  
  -- 部署结构版本号。
  version_no VARCHAR2(20) NOT NULL, 
  -- 应用版本时的 UTC 时间。
  applied_at CHAR(19) NOT NULL,
  
  -- 版本描述，原内容属于实际存储值。
  description VARCHAR2(500), 
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_schema_pk PRIMARY KEY(version_no)
);
-- 表用途：按集团保存总控开关及版本，缺省关闭。
CREATE TABLE qd_es_control (
  
  -- NC 集团原生主键。
  pk_group VARCHAR2(20) NOT NULL, 
  -- 集团总控 Y/N，默认 N。
  master_enabled CHAR(1) DEFAULT 'N' NOT NULL,
  
  -- 配置版本，修改开关或策略时推进以使旧授权失效。
  policy_version NUMBER(19) DEFAULT 1 NOT NULL, 
  -- 最近修改的 UTC 时间。
  modified_at CHAR(19) NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_control_pk PRIMARY KEY(pk_group),
  
  -- 限制持久化状态、开关或月份格式，应用层仍需验证业务语义。
  CONSTRAINT qdes_control_ck CHECK(master_enabled IN ('Y','N'))
);
-- 表用途：正式范围目录及待验证映射，条目存在不表示已启用。
CREATE TABLE qd_es_node (
  
  -- 正式清单范围 ID，不等同于真实 NC 节点编码。
  scope_id VARCHAR2(64) NOT NULL, 
  -- 现场核实的真实功能节点编码。
  node_code VARCHAR2(100), 
  -- 功能节点显示名称，日志保存操作时名称。
  node_name VARCHAR2(200 CHAR) NOT NULL,
  
  -- 现场核实的真实按钮编码。
  button_code VARCHAR2(100), 
  -- 按钮显示名称，日志保存操作时名称。
  button_name VARCHAR2(200 CHAR) NOT NULL,
  
  -- 固定白名单内的本地适配服务接口名。
  adapter_class VARCHAR2(300), 
  -- 映射状态，目录种子默认 UNMAPPED。
  mapping_state VARCHAR2(30) DEFAULT 'UNMAPPED' NOT NULL,
  
  -- 映射允许的动作集合，不能以按钮授权代替动作域校验。
  operation_types VARCHAR2(200), 
  -- 固定业务对象类型。
  object_type VARCHAR2(100),
  
  -- 是否经验证，默认 N；目录存在不代表验证通过。
  validated CHAR(1) DEFAULT 'N' NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_node_pk PRIMARY KEY(scope_id),
  
  -- 业务唯一约束防止重复范围、对象或来源事实。
  CONSTRAINT qdes_node_action_uq UNIQUE(node_code,button_code),
  
  -- 限制持久化状态、开关或月份格式，应用层仍需验证业务语义。
  CONSTRAINT qdes_node_valid_ck CHECK(validated IN ('Y','N'))
);
-- 表用途：按集团、业务单元、节点和按钮保存策略与并发版本。
CREATE TABLE qd_es_policy (
  
  -- 策略 UUID32 主键。
  policy_id VARCHAR2(32) NOT NULL, 
  -- NC 集团原生主键。
  pk_group VARCHAR2(20) NOT NULL,
  
  -- 业务单元原生主键；安全事件可未知。
  pk_org VARCHAR2(20) NOT NULL, 
  -- 现场核实的真实功能节点编码。
  node_code VARCHAR2(100) NOT NULL,
  
  -- 现场核实的真实按钮编码。
  button_code VARCHAR2(100) NOT NULL, 
  -- 节点签名开关，实际生效还受集团总控影响。
  sign_enabled CHAR(1) DEFAULT 'N' NOT NULL,
  
  -- 节点事由必填配置，仅在签名生效时要求。
  reason_required CHAR(1) DEFAULT 'N' NOT NULL, 
  -- 业务审计开关，实际生效还受集团总控影响。
  audit_enabled CHAR(1) DEFAULT 'N' NOT NULL,
  
  -- 策略是否启用，默认 N。
  enabled CHAR(1) DEFAULT 'N' NOT NULL, 
  -- 是否经验证，默认 N；目录存在不代表验证通过。
  validated CHAR(1) DEFAULT 'N' NOT NULL,
  
  -- 配置版本，修改开关或策略时推进以使旧授权失效。
  policy_version NUMBER(19) DEFAULT 1 NOT NULL, 
  -- 最近修改的 UTC 时间。
  modified_at CHAR(19) NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_policy_pk PRIMARY KEY(policy_id),
  
  -- 业务唯一约束防止重复范围、对象或来源事实。
  CONSTRAINT qdes_policy_scope_uq UNIQUE(pk_group,pk_org,node_code,button_code),
  
  -- 限制持久化状态、开关或月份格式，应用层仍需验证业务语义。
  CONSTRAINT qdes_policy_flags_ck CHECK(sign_enabled IN ('Y','N') AND
    reason_required IN ('Y','N') AND audit_enabled IN ('Y','N') AND
    enabled IN ('Y','N') AND validated IN ('Y','N'))
);
-- 表用途：签名请求主记录，分别维护认证和业务状态及一次性执行令牌。
CREATE TABLE qd_es_request (
  
  -- 贯通签名、业务及审计的 UUID32 请求号。
  request_id VARCHAR2(32) NOT NULL, 
  -- 同一操作批次的关联标识。
  batch_id VARCHAR2(32), 
  -- 操作时的 NC 用户主键。
  user_id VARCHAR2(20) NOT NULL,
  
  -- 操作时的用户编码，来源未知时不伪造。
  user_code VARCHAR2(100), 
  -- 操作时保存的人员名称。
  user_name VARCHAR2(200 CHAR), 
  -- NC 集团原生主键。
  pk_group VARCHAR2(20) NOT NULL,
  
  -- 业务单元原生主键；安全事件可未知。
  pk_org VARCHAR2(20) NOT NULL, 
  -- 现场核实的真实功能节点编码。
  node_code VARCHAR2(100) NOT NULL, 
  -- 功能节点显示名称，日志保存操作时名称。
  node_name VARCHAR2(200 CHAR),
  
  -- 现场核实的真实按钮编码。
  button_code VARCHAR2(100) NOT NULL, 
  -- 按钮显示名称，日志保存操作时名称。
  button_name VARCHAR2(200 CHAR),
  
  -- 当前业务动作类型，如新增、修改或删除。
  operation_type VARCHAR2(50) NOT NULL, 
  -- 固定业务对象类型。
  object_type VARCHAR2(100), 
  -- 操作事由，最多 4000 字节，服务端另校验字符长度。
  reason VARCHAR2(4000 BYTE),
  
  -- 认证状态，与业务执行结果分别维护。
  signature_state VARCHAR2(30) NOT NULL, 
  -- 业务状态，认领进入 RUNNING，结束后才可归档。
  business_state VARCHAR2(30) NOT NULL, 
  -- 一次性业务认领令牌，不是登录或密码令牌。
  execution_token VARCHAR2(32),
  
  -- 配置版本，修改开关或策略时推进以使旧授权失效。
  policy_version NUMBER(19) NOT NULL, 
  -- 请求准备时的集团总控版本。
  control_version NUMBER(19) NOT NULL, 
  -- 记录创建的 UTC 时间。
  created_at CHAR(19) NOT NULL,
  
  -- 授权截止的 UTC 时间。
  expires_at CHAR(19) NOT NULL, 
  -- 业务结束的 UTC 时间，缺少时不能按已结束请求归档。
  finished_at CHAR(19), 
  -- 实际来源客户端 IP，不推测 DNS 地址。
  client_ip VARCHAR2(64),
  
  -- 实际可取得的终端名称，未知保留为空。
  terminal_name VARCHAR2(200), 
  -- 受控流程或维护故障的错误码。
  error_code VARCHAR2(100), 
  -- 错误说明，不写入认证凭据。
  error_message VARCHAR2(2000),
  
  -- 按事件瞬时值转换的北京时间自然月 YYYYMM。
  archive_month CHAR(6) NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_request_pk PRIMARY KEY(request_id),
  
  -- 限制持久化状态、开关或月份格式，应用层仍需验证业务语义。
  CONSTRAINT qdes_request_month_ck CHECK(REGEXP_LIKE(archive_month,'^[0-9]{6}$'))
);
-- 表用途：请求关联对象，保留未保存对象的客户端身份及父子关系。
CREATE TABLE qd_es_object (
  
  -- 请求对象关联的 UUID32 主键。
  object_id VARCHAR2(32) NOT NULL, 
  -- 贯通签名、业务及审计的 UUID32 请求号。
  request_id VARCHAR2(32) NOT NULL,
  
  -- 客户端稳定对象标识，新增未保存时仍可关联。
  client_object_id VARCHAR2(100) NOT NULL, 
  -- 固定业务对象类型。
  object_type VARCHAR2(100) NOT NULL,
  
  -- 真实业务主键，新增成功后回填。
  object_pk VARCHAR2(100), 
  -- 真实单据或档案编码，没有已验证字段时保留为空。
  bill_code VARCHAR2(200), 
  -- 同请求父对象的数据库 ID，不能按返回顺序猜测。
  parent_object_id VARCHAR2(32),
  
  -- ROOT 或 CHILD 等已登记对象关系。
  relation_type VARCHAR2(50), 
  -- NC 集团原生主键。
  pk_group VARCHAR2(20) NOT NULL, 
  -- 业务单元原生主键；安全事件可未知。
  pk_org VARCHAR2(20) NOT NULL,
  
  -- 记录创建的 UTC 时间。
  created_at CHAR(19) NOT NULL, 
  -- 按事件瞬时值转换的北京时间自然月 YYYYMM。
  archive_month CHAR(6) NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_object_pk PRIMARY KEY(object_id),
  
  -- 业务唯一约束防止重复范围、对象或来源事实。
  CONSTRAINT qdes_object_client_uq UNIQUE(request_id,client_object_id)
);
-- 表用途：逐对象、逐尝试的签名结果，记录失败、锁定、取消等事实。
CREATE TABLE qd_es_signlog (
  
  -- 日志事件 UUID32 主键。
  event_id VARCHAR2(32) NOT NULL, 
  -- 贯通签名、业务及审计的 UUID32 请求号。
  request_id VARCHAR2(32) NOT NULL, 
  -- 同一操作批次的关联标识。
  batch_id VARCHAR2(32),
  
  -- 同一次认证尝试的标识，逐对象签名日志共用。
  attempt_id VARCHAR2(32) NOT NULL, 
  -- 请求对象关联的 UUID32 主键。
  object_id VARCHAR2(32) NOT NULL,
  
  -- 操作时的 NC 用户主键。
  user_id VARCHAR2(20) NOT NULL, 
  -- 操作时的用户编码，来源未知时不伪造。
  user_code VARCHAR2(100), 
  -- 操作时保存的人员名称。
  user_name VARCHAR2(200 CHAR),
  
  -- NC 集团原生主键。
  pk_group VARCHAR2(20) NOT NULL, 
  -- 业务单元原生主键；安全事件可未知。
  pk_org VARCHAR2(20) NOT NULL,
  
  -- 现场核实的真实功能节点编码。
  node_code VARCHAR2(100) NOT NULL, 
  -- 功能节点显示名称，日志保存操作时名称。
  node_name VARCHAR2(200 CHAR),
  
  -- 现场核实的真实按钮编码。
  button_code VARCHAR2(100) NOT NULL, 
  -- 按钮显示名称，日志保存操作时名称。
  button_name VARCHAR2(200 CHAR), 
  -- 当前业务动作类型，如新增、修改或删除。
  operation_type VARCHAR2(50),
  
  -- 固定业务对象类型。
  object_type VARCHAR2(100), 
  -- 真实业务主键，新增成功后回填。
  object_pk VARCHAR2(100), 
  -- 真实单据或档案编码，没有已验证字段时保留为空。
  bill_code VARCHAR2(200),
  
  -- 认证、安全事实或业务审计的明确结果码。
  result_code VARCHAR2(30) NOT NULL, 
  -- 该认证尝试是否导致或发现账号锁定。
  lock_result VARCHAR2(100), 
  -- 操作事由，最多 4000 字节，服务端另校验字符长度。
  reason VARCHAR2(4000 BYTE),
  
  -- 实际来源客户端 IP，不推测 DNS 地址。
  client_ip VARCHAR2(64), 
  -- 实际可取得的终端名称，未知保留为空。
  terminal_name VARCHAR2(200), 
  -- 事实发生的 UTC 时间。
  event_time CHAR(19) NOT NULL,
  
  -- 按事件瞬时值转换的北京时间自然月 YYYYMM。
  archive_month CHAR(6) NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_signlog_pk PRIMARY KEY(event_id),
  
  -- 业务唯一约束防止重复范围、对象或来源事实。
  CONSTRAINT qdes_signlog_attempt_uq UNIQUE(request_id,attempt_id,object_id)
);
-- 表用途：真实成功业务的审计主记录，与原业务使用同一 CMT。
CREATE TABLE qd_es_audit (
  
  -- 业务成功审计的 UUID32 标识。
  audit_id VARCHAR2(32) NOT NULL, 
  -- 贯通签名、业务及审计的 UUID32 请求号。
  request_id VARCHAR2(32) NOT NULL,
  
  -- 同一操作批次的关联标识。
  batch_id VARCHAR2(32), 
  -- 请求对象关联的 UUID32 主键。
  object_id VARCHAR2(32) NOT NULL,
  
  -- 操作时的 NC 用户主键。
  user_id VARCHAR2(20) NOT NULL, 
  -- 操作时的用户编码，来源未知时不伪造。
  user_code VARCHAR2(100), 
  -- 操作时保存的人员名称。
  user_name VARCHAR2(200 CHAR),
  
  -- NC 集团原生主键。
  pk_group VARCHAR2(20) NOT NULL, 
  -- 业务单元原生主键；安全事件可未知。
  pk_org VARCHAR2(20) NOT NULL,
  
  -- 现场核实的真实功能节点编码。
  node_code VARCHAR2(100) NOT NULL, 
  -- 功能节点显示名称，日志保存操作时名称。
  node_name VARCHAR2(200 CHAR),
  
  -- 现场核实的真实按钮编码。
  button_code VARCHAR2(100) NOT NULL, 
  -- 按钮显示名称，日志保存操作时名称。
  button_name VARCHAR2(200 CHAR), 
  -- 当前业务动作类型，如新增、修改或删除。
  operation_type VARCHAR2(50) NOT NULL,
  
  -- 固定业务对象类型。
  object_type VARCHAR2(100), 
  -- 真实业务主键，新增成功后回填。
  object_pk VARCHAR2(100), 
  -- 真实单据或档案编码，没有已验证字段时保留为空。
  bill_code VARCHAR2(200),
  
  -- 认证、安全事实或业务审计的明确结果码。
  result_code VARCHAR2(30) NOT NULL, 
  -- 操作事由，最多 4000 字节，服务端另校验字符长度。
  reason VARCHAR2(4000 BYTE), 
  -- 事实发生的 UTC 时间。
  event_time CHAR(19) NOT NULL,
  
  -- 按事件瞬时值转换的北京时间自然月 YYYYMM。
  archive_month CHAR(6) NOT NULL, 
  -- 实际来源客户端 IP，不推测 DNS 地址。
  client_ip VARCHAR2(64), 
  -- 实际可取得的终端名称，未知保留为空。
  terminal_name VARCHAR2(200),
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_audit_pk PRIMARY KEY(audit_id),
  
  -- 业务唯一约束防止重复范围、对象或来源事实。
  CONSTRAINT qdes_audit_object_uq UNIQUE(request_id,object_id,operation_type),
  
  -- 限制持久化状态、开关或月份格式，应用层仍需验证业务语义。
  CONSTRAINT qdes_audit_result_ck CHECK(result_code='SUCCESS')
);
-- 表用途：白名单字段及父子关系的前后差异，大字段按 CLOB 保存。
CREATE TABLE qd_es_change (
  
  -- 字段差异的 UUID32 标识。
  change_id VARCHAR2(32) NOT NULL, 
  -- 业务成功审计的 UUID32 标识。
  audit_id VARCHAR2(32) NOT NULL,
  
  -- 贯通签名、业务及审计的 UUID32 请求号。
  request_id VARCHAR2(32) NOT NULL, 
  -- 请求对象关联的 UUID32 主键。
  object_id VARCHAR2(32) NOT NULL,
  
  -- 快照及差异所属实体。
  entity_name VARCHAR2(200) NOT NULL, 
  -- 实体内用于前后对齐的稳定行标识。
  row_id VARCHAR2(100) NOT NULL,
  
  -- 对应父行的稳定标识。
  parent_row_id VARCHAR2(100), 
  -- 白名单字段编码。
  field_code VARCHAR2(100) NOT NULL, 
  -- 操作时的字段显示名称。
  field_name VARCHAR2(200 CHAR),
  
  -- 字段原始类型说明。
  data_type VARCHAR2(50), 
  -- INSERT、UPDATE 或 DELETE 差异类型。
  change_type VARCHAR2(30) NOT NULL,
  
  -- 变更前值，保留 null 与空字符串差异。
  before_value CLOB, 
  -- 变更后值，按大字段保存。
  after_value CLOB, 
  -- NC 集团原生主键。
  pk_group VARCHAR2(20) NOT NULL,
  
  -- 业务单元原生主键；安全事件可未知。
  pk_org VARCHAR2(20) NOT NULL, 
  -- 事实发生的 UTC 时间。
  event_time CHAR(19) NOT NULL, 
  -- 按事件瞬时值转换的北京时间自然月 YYYYMM。
  archive_month CHAR(6) NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_change_pk PRIMARY KEY(change_id)
);
-- 表用途：允许字段的完整行快照及内容摘要，不包含认证秘密。
CREATE TABLE qd_es_snapshot (
  
  -- 快照的 UUID32 标识。
  snapshot_id VARCHAR2(32) NOT NULL, 
  -- 业务成功审计的 UUID32 标识。
  audit_id VARCHAR2(32) NOT NULL,
  
  -- 贯通签名、业务及审计的 UUID32 请求号。
  request_id VARCHAR2(32) NOT NULL, 
  -- 请求对象关联的 UUID32 主键。
  object_id VARCHAR2(32) NOT NULL,
  
  -- BEFORE、AFTER 或 DELETE 快照阶段。
  snapshot_phase VARCHAR2(10) NOT NULL, 
  -- 快照及差异所属实体。
  entity_name VARCHAR2(200) NOT NULL,
  
  -- 实体内用于前后对齐的稳定行标识。
  row_id VARCHAR2(100) NOT NULL, 
  -- 对应父行的稳定标识。
  parent_row_id VARCHAR2(100), 
  -- 白名单字段规范化 JSON 内容。
  payload CLOB NOT NULL,
  
  -- UTF-8 内容 SHA-256 摘要，不是证书电子签名。
  payload_hash VARCHAR2(64) NOT NULL, 
  -- NC 集团原生主键。
  pk_group VARCHAR2(20) NOT NULL,
  
  -- 业务单元原生主键；安全事件可未知。
  pk_org VARCHAR2(20) NOT NULL, 
  -- 事实发生的 UTC 时间。
  event_time CHAR(19) NOT NULL, 
  -- 按事件瞬时值转换的北京时间自然月 YYYYMM。
  archive_month CHAR(6) NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_snapshot_pk PRIMARY KEY(snapshot_id),
  
  -- 限制持久化状态、开关或月份格式，应用层仍需验证业务语义。
  CONSTRAINT qdes_snapshot_phase_ck CHECK(snapshot_phase IN ('BEFORE','AFTER','DELETE'))
);
-- 表用途：按原生来源及版本保存安全事件事实，未知信息保持为空。
CREATE TABLE qd_es_security (
  
  -- 日志事件 UUID32 主键。
  event_id VARCHAR2(32) NOT NULL, 
  -- 原生安全事实来源标识。
  source_system VARCHAR2(50) NOT NULL,
  
  -- 来源系统真实事件主键。
  source_event_id VARCHAR2(100) NOT NULL, 
  -- 来源事实版本，用于区分同一日志的后续变化。
  source_version VARCHAR2(100) NOT NULL,
  
  -- 操作时的 NC 用户主键。
  user_id VARCHAR2(20), 
  -- 操作时的用户编码，来源未知时不伪造。
  user_code VARCHAR2(100), 
  -- 操作时保存的人员名称。
  user_name VARCHAR2(200 CHAR),
  
  -- NC 集团原生主键。
  pk_group VARCHAR2(20) NOT NULL, 
  -- 业务单元原生主键；安全事件可未知。
  pk_org VARCHAR2(20),
  
  -- 来源中确实记录的事件类型。
  event_type VARCHAR2(50) NOT NULL, 
  -- 认证、安全事实或业务审计的明确结果码。
  result_code VARCHAR2(50) NOT NULL,
  
  -- 实际来源客户端 IP，不推测 DNS 地址。
  client_ip VARCHAR2(64), 
  -- 实际可取得的终端名称，未知保留为空。
  terminal_name VARCHAR2(200), 
  -- 事实发生的 UTC 时间。
  event_time CHAR(19) NOT NULL,
  
  -- 按事件瞬时值转换的北京时间自然月 YYYYMM。
  archive_month CHAR(6) NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_security_pk PRIMARY KEY(event_id),
  
  -- 业务唯一约束防止重复范围、对象或来源事实。
  CONSTRAINT qdes_security_source_uq UNIQUE(source_system,source_event_id,source_version)
);
-- 原生安全事件归档后，幂等目录仍在线保留，防止重复导入。
-- 表用途：始终在线的安全事件幂等目录，防止归档后再次导入同一事实。
CREATE TABLE qd_es_security_key (
  
  -- 日志事件 UUID32 主键。
  event_id VARCHAR2(32) NOT NULL, 
  -- 原生安全事实来源标识。
  source_system VARCHAR2(50) NOT NULL,
  
  -- 来源系统真实事件主键。
  source_event_id VARCHAR2(100) NOT NULL, 
  -- 来源事实版本，用于区分同一日志的后续变化。
  source_version VARCHAR2(100) NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_security_key_pk PRIMARY KEY(event_id),
  
  -- 业务唯一约束防止重复范围、对象或来源事实。
  CONSTRAINT qdes_security_key_uq UNIQUE(source_system,source_event_id,source_version)
);
-- 表用途：增量及全主键对账各自的进度，同批事件与游标共同提交。
CREATE TABLE qd_es_sync_cursor (
  
  -- NC 集团原生主键。
  pk_group VARCHAR2(20) NOT NULL, 
  -- 增量或全扫各自使用的游标名。
  source_name VARCHAR2(50) NOT NULL,
  
  -- 增量源时间水位，按源日志自身时间约定保存。
  last_source_ts CHAR(19) NOT NULL, 
  -- 增量同秒排序或全扫描进度的主键。
  last_source_pk VARCHAR2(100) NOT NULL,
  
  -- 本轮完整重扫开始的 UTC 时间。
  cycle_started_at CHAR(19), 
  -- 最近一次完整重扫结束的 UTC 时间。
  cycle_completed_at CHAR(19), 
  -- 最近修改的 UTC 时间。
  modified_at CHAR(19) NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_sync_cursor_pk PRIMARY KEY(pk_group,source_name)
);
-- 稳定目录始终在线；跨月关系使用逻辑关联，不建立会阻碍归档移动的物理外键。
-- 表用途：始终在线的请求到月份定位，保护活跃请求并支持跨月查详情。
CREATE TABLE qd_es_locator (
  
  -- 贯通签名、业务及审计的 UUID32 请求号。
  request_id VARCHAR2(32) NOT NULL, 
  -- 按事件瞬时值转换的北京时间自然月 YYYYMM。
  archive_month CHAR(6) NOT NULL,
  
  -- 请求是否已结束，N 时相关证据继续在线保护。
  request_closed CHAR(1) DEFAULT 'N' NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_locator_pk PRIMARY KEY(request_id,archive_month),
  
  -- 限制持久化状态、开关或月份格式，应用层仍需验证业务语义。
  CONSTRAINT qdes_locator_closed_ck CHECK(request_closed IN ('Y','N'))
);
-- 表用途：审批预建历史表的月份目录与归档状态，兼作同月互斥锁。
CREATE TABLE qd_es_archive (
  
  -- 按事件瞬时值转换的北京时间自然月 YYYYMM。
  archive_month CHAR(6) NOT NULL, 
  -- 目录或任务当前状态；归档 PARTIAL 表示仍有待结束请求。
  status VARCHAR2(20) DEFAULT 'PREPARED' NOT NULL,
  
  -- 本轮维护开始的 UTC 时间。
  started_at CHAR(19), 
  -- 业务结束的 UTC 时间，缺少时不能按已结束请求归档。
  finished_at CHAR(19), 
  -- 最近错误或待处理说明。
  last_error VARCHAR2(2000),
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_archive_pk PRIMARY KEY(archive_month),
  
  -- 限制持久化状态、开关或月份格式，应用层仍需验证业务语义。
  CONSTRAINT qdes_archive_status_ck CHECK(status IN ('PREPARED','RUNNING','PARTIAL','ARCHIVED','ERROR'))
);
-- 表用途：维护任务状态的数据结构，具体调度和故障独立持久化仍需现场验证。
CREATE TABLE qd_es_job (
  
  -- 任务记录 UUID32 标识。
  job_id VARCHAR2(32) NOT NULL, 
  -- 维护任务类型。
  job_type VARCHAR2(50) NOT NULL,
  
  -- 任务类型内的业务幂等键。
  job_key VARCHAR2(100) NOT NULL, 
  -- 目录或任务当前状态；归档 PARTIAL 表示仍有待结束请求。
  status VARCHAR2(30) NOT NULL,
  
  -- 任务尝试次数。
  attempt_count NUMBER(10) DEFAULT 0 NOT NULL, 
  -- 可供调度采用的下一重试 UTC 时间。
  next_retry_at CHAR(19),
  
  -- 本轮维护开始的 UTC 时间。
  started_at CHAR(19), 
  -- 业务结束的 UTC 时间，缺少时不能按已结束请求归档。
  finished_at CHAR(19), 
  -- 最近错误或待处理说明。
  last_error VARCHAR2(2000),
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_job_pk PRIMARY KEY(job_id),
  
  -- 业务唯一约束防止重复范围、对象或来源事实。
  CONSTRAINT qdes_job_key_uq UNIQUE(job_type,job_key)
);
-- 表用途：故障记录的数据结构，不保证与失败事务中的写入能够独立提交。
CREATE TABLE qd_es_fault (
  
  -- 故障记录 UUID32 标识。
  fault_id VARCHAR2(32) NOT NULL, 
  -- 贯通签名、业务及审计的 UUID32 请求号。
  request_id VARCHAR2(32), 
  -- NC 集团原生主键。
  pk_group VARCHAR2(20),
  
  -- 业务单元原生主键；安全事件可未知。
  pk_org VARCHAR2(20), 
  -- 故障类别。
  fault_type VARCHAR2(100) NOT NULL,
  
  -- 受控流程或维护故障的错误码。
  error_code VARCHAR2(100), 
  -- 错误说明，不写入认证凭据。
  error_message VARCHAR2(2000) NOT NULL,
  
  -- 事实发生的 UTC 时间。
  event_time CHAR(19) NOT NULL, 
  -- 故障是否已处理的 Y/N 标记。
  resolved CHAR(1) DEFAULT 'N' NOT NULL,
  
  -- 主键保证记录身份唯一。
  CONSTRAINT qdes_fault_pk PRIMARY KEY(fault_id),
  
  -- 限制持久化状态、开关或月份格式，应用层仍需验证业务语义。
  CONSTRAINT qdes_fault_resolved_ck CHECK(resolved IN ('Y','N'))
);
-- 以下为实际执行的表元数据语句，原描述值保持不变；上方中文说明属于源码注释。
COMMENT ON TABLE qd_es_request IS 'QD electronic signature request; UTC timestamps, independent signature/business states';
COMMENT ON TABLE qd_es_audit IS 'Committed successful business facts only; same CMT as original business';
COMMENT ON TABLE qd_es_snapshot IS 'Approved-field snapshots; never authentication secrets';
COMMENT ON TABLE qd_es_locator IS 'Cross-month request lookup; retained online after archive';

-- END SOURCE 001_schema.sql
-- BEGIN SOURCE 002_indexes.sql
-- 审批后在结构脚本之后执行索引脚本；不显式提交。
-- 查询用途：支持NC 集团原生主键、业务单元原生主键；安全事件可未知、操作时的 NC 用户主键、记录创建的 UTC 时间相关筛选或关联。
CREATE INDEX qdes_request_user_ix ON qd_es_request(pk_group,pk_org,user_id,created_at);
-- 查询用途：支持同一操作批次的关联标识相关筛选或关联。
CREATE INDEX qdes_request_batch_ix ON qd_es_request(batch_id);
-- 查询用途：支持业务状态、授权截止的 UTC 时间相关筛选或关联。
CREATE INDEX qdes_request_expiry_ix ON qd_es_request(business_state,expires_at);
-- 查询用途：支持按事件瞬时值转换的北京时间自然月 YYYYMM、业务结束的 UTC 时间相关筛选或关联。
CREATE INDEX qdes_request_month_ix ON qd_es_request(archive_month,finished_at);
-- 查询用途：支持贯通签名、业务及审计的 UUID32 请求号相关筛选或关联。
CREATE INDEX qdes_object_request_ix ON qd_es_object(request_id);
-- 查询用途：支持NC 集团原生主键、业务单元原生主键；安全事件可未知、真实单据或档案编码相关筛选或关联。
CREATE INDEX qdes_object_bill_ix ON qd_es_object(pk_group,pk_org,bill_code);
-- 查询用途：支持固定业务对象类型、真实业务主键相关筛选或关联。
CREATE INDEX qdes_object_pk_ix ON qd_es_object(object_type,object_pk);
-- 查询用途：支持NC 集团原生主键、业务单元原生主键；安全事件可未知、事实发生的 UTC 时间相关筛选或关联。
CREATE INDEX qdes_signlog_query_ix ON qd_es_signlog(pk_group,pk_org,event_time);
-- 查询用途：支持贯通签名、业务及审计的 UUID32 请求号相关筛选或关联。
CREATE INDEX qdes_signlog_request_ix ON qd_es_signlog(request_id);
-- 查询用途：支持同一操作批次的关联标识相关筛选或关联。
CREATE INDEX qdes_signlog_batch_ix ON qd_es_signlog(batch_id);
-- 查询用途：支持NC 集团原生主键、业务单元原生主键；安全事件可未知、真实单据或档案编码相关筛选或关联。
CREATE INDEX qdes_signlog_bill_ix ON qd_es_signlog(pk_group,pk_org,bill_code);
-- 查询用途：支持按事件瞬时值转换的北京时间自然月 YYYYMM相关筛选或关联。
CREATE INDEX qdes_signlog_month_ix ON qd_es_signlog(archive_month);
-- 查询用途：支持NC 集团原生主键、业务单元原生主键；安全事件可未知、事实发生的 UTC 时间相关筛选或关联。
CREATE INDEX qdes_audit_query_ix ON qd_es_audit(pk_group,pk_org,event_time);
-- 查询用途：支持同一操作批次的关联标识相关筛选或关联。
CREATE INDEX qdes_audit_batch_ix ON qd_es_audit(batch_id);
-- 查询用途：支持NC 集团原生主键、业务单元原生主键；安全事件可未知、真实单据或档案编码相关筛选或关联。
CREATE INDEX qdes_audit_bill_ix ON qd_es_audit(pk_group,pk_org,bill_code);
-- 查询用途：支持按事件瞬时值转换的北京时间自然月 YYYYMM相关筛选或关联。
CREATE INDEX qdes_audit_month_ix ON qd_es_audit(archive_month);
-- 查询用途：支持业务成功审计的 UUID32 标识相关筛选或关联。
CREATE INDEX qdes_change_audit_ix ON qd_es_change(audit_id);
-- 查询用途：支持贯通签名、业务及审计的 UUID32 请求号相关筛选或关联。
CREATE INDEX qdes_change_request_ix ON qd_es_change(request_id);
-- 查询用途：支持按事件瞬时值转换的北京时间自然月 YYYYMM相关筛选或关联。
CREATE INDEX qdes_change_month_ix ON qd_es_change(archive_month);
-- 查询用途：支持业务成功审计的 UUID32 标识相关筛选或关联。
CREATE INDEX qdes_snapshot_audit_ix ON qd_es_snapshot(audit_id);
-- 查询用途：支持贯通签名、业务及审计的 UUID32 请求号相关筛选或关联。
CREATE INDEX qdes_snapshot_request_ix ON qd_es_snapshot(request_id);
-- 查询用途：支持按事件瞬时值转换的北京时间自然月 YYYYMM相关筛选或关联。
CREATE INDEX qdes_snapshot_month_ix ON qd_es_snapshot(archive_month);
-- 查询用途：支持NC 集团原生主键、业务单元原生主键；安全事件可未知、事实发生的 UTC 时间相关筛选或关联。
CREATE INDEX qdes_security_query_ix ON qd_es_security(pk_group,pk_org,event_time);
-- 查询用途：支持按事件瞬时值转换的北京时间自然月 YYYYMM相关筛选或关联。
CREATE INDEX qdes_security_month_ix ON qd_es_security(archive_month);
-- 查询用途：支持目录或任务当前状态；归档 PARTIAL 表示仍有待结束请求、可供调度采用的下一重试 UTC 时间相关筛选或关联。
CREATE INDEX qdes_job_retry_ix ON qd_es_job(status,next_retry_at);
-- 查询用途：支持贯通签名、业务及审计的 UUID32 请求号相关筛选或关联。
CREATE INDEX qdes_fault_request_ix ON qd_es_fault(request_id);

-- END SOURCE 002_indexes.sql
-- BEGIN SOURCE 003_init.sql
-- 初始状态没有集团总控或策略即为关闭；本脚本不显式提交。
-- 集团控制行由有管理权限的用户通过策略界面显式创建。
-- 节点目录通过离线生成器读取已批准范围 CSV 产生。
-- 范围目录或适配器类存在都不代表启用策略，验证标记仍默认 N。
-- 只登记本结构版本及 UTC 应用时间，不创建默认启用的集团或节点策略。
INSERT INTO qd_es_schema_version(version_no,applied_at,description)
VALUES ('1.0.0',TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS'),
        'Initial QD NC65 electronic signature schema; all controls default OFF');

-- END SOURCE 003_init.sql
-- BEGIN SOURCE 008_scope_seed.sql
-- 中文说明：下方每组先记录原工作表及行号，再以范围 ID 仅补缺插入；SQL 有效语句须与生成器一致。
-- 由批准的操作范围清单生成，仅包含 243 项目录种子。
-- 执行前审核审批，不编造 NC 功能节点或按钮编码。
-- 保留已存在行；新条目保持未映射且未验证。
-- 不显式提交，不启用或修改任何已有策略。
-- 来源工作表及行号：Sheet2 第 2 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B9C29A5B958696F4' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户基本分类','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 3 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-0DA86324DBBF4DE6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户基本分类','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 4 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E332E41B042F9540' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户基本分类','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 5 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-9F902C76B60FBE04' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户基本分类','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 6 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E576276F26109C16' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 7 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-35B5B1E09B5A4A80' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 8 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E9B352C5EACB0820' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 9 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-0245CC5E8FA59478' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 10 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-56EC7D5EE24A0804' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','批改-快速批改','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 11 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A9C10437DFDE8814' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','分配-分配','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 12 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8555A9BFE51EA438' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','取消分配-快速取消分配','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 13 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7B87FD76F6ADBDC0' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','冻结','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 14 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B856D13140D5457B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','解冻','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 15 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E153DD629D00947F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','银行账户-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 16 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-70EC3A5C19555BD7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','银行账户-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 17 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7493FA0F20E75AF0' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','银行账户-停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 18 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-DDC46B3D9F641DFF' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','银行账户-启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 19 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-36702F5EC874AF77' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','财务信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 20 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-6FCD3C3549E70950' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','财务信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 21 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-56F5E6C99AD9B4B7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','销售信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 22 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D1DA60E564CAF142' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','销售信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 23 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A46C77B59E5AD49E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','信用控制信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 24 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-AA8986FF31D0B647' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'客户','信用控制信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 25 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2D9AA713970C7E86' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商基本分类','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 26 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-0BB4B107C5D6B51F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商基本分类','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 27 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D9F13F397DF1DF0B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商基本分类','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 28 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-93CFA69510E56403' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商基本分类','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 29 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E8C69ACAF76ECABF' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 30 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-83A95678666FC134' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 31 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F6D1E9BB792F6075' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 32 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D9EF39B9A666E88D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 33 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-122DB04892CFDA8B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','批改-快速批改','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 34 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FD8282A38B9C4E89' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','分配-分配','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 35 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-309E9ED628D45CDF' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','取消分配-快速取消分配','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 36 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1EFAFB0888F5A7CA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','冻结','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 37 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E2F3A16029CD1BC1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','解冻','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 38 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E70DEE0145D79FDE' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','银行账户-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 39 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-00DF272BF31F0AA2' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','银行账户-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 40 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-24C25407F1C36AF6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','银行账户-停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 41 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B3A292BB1D2F3E84' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','银行账户-启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 42 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C9ED571D3AB7DD28' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','财务信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 43 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-643C1BCD6537DD6B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','财务信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 44 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8D2A39DEDA2F588D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','采购信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 45 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F136737476331BC2' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商','采购信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 46 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-5D9816B2EE6203BC' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料基本分类','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 47 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-689A1D26EF57426B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料基本分类','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 48 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8C9F0ACFE6E32A2B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料基本分类','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 49 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-42DE49AC3986C6B1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料基本分类','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 50 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A7FBCA61C6991D48' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 51 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2F2608B07F6B73DD' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 52 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4D029F8FC9693DD9' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 53 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-93014EDACA48E4D1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 54 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4CE7157ECDB4A443' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','批改-快速批改','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 55 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-755F2D0C43B0646E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','分配-分配','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 56 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-CE419A6EAAED8307' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','取消分配-快速取消分配','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 57 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B48245BDDB687314' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','财务信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 58 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FA635534B542BA71' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','财务信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 59 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2DAE0B30BE8A146B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','采购信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 60 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2B20B2A6925C5B1A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','采购信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 61 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E5FA2B4E1183BF51' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','销售信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 62 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4DA3476F6CC93426' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','销售信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 63 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-92F433355082640B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','库存信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 64 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2148AD881831CEFA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','库存信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 65 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B54294B82124C93D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','计划信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 66 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-AF7F85BA60C742D9' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','计划信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 67 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-765869462A8F9898' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','生成信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 68 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-DB52192A0DE19F91' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','生成信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 69 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-98DD9EFAD6E1C54E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','成本信息-保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 70 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E8808CF0B60EED9C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物料','成本信息-删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 71 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-00D8C4530BE5A4C4' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'仓库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 72 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-25C42CF52E33610D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'仓库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 73 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-22C93F5AF42F721D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'仓库','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 74 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B9B8E830DE3C5B6D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'仓库','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 75 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-5BD5E8E063ECF621' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'货位','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 76 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-62A408A680D6A829' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'货位','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 77 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-69884D289EB5AFCC' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'货位','停用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 78 行，分类： 基础数据
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8B947039D517EEF6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'货位','启用','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 79 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-01D5908833FEB210' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 80 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-985FB7742F2A5B92' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 81 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B840FE4C55926A43' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 82 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-441DAD9CFDDEF982' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 83 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-63E91AC6F7B692B3' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 84 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C3E8EDB0010ADE64' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 85 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-205F657A98383B3C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','辅助功能-整单关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 86 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FCF630A4D7ABCB0E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','辅助功能-整单打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 87 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-88AD4ED26D06F0CD' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','辅助功能-行关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 88 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-92362BAAB3F1F75E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'物资需求申请单','辅助功能-行打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 89 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-490EA13F8CCF818D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 90 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-6CE160397F888C22' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 91 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4FEB4DC30CF718EC' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 92 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D0431E7A49F7EECF' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 93 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-39FAB956E796E631' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 94 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8C3548A53305EFF6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 95 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-92837F547CF79D01' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','辅助功能-整单关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 96 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-42210241A288DA84' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','辅助功能-整单打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 97 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1143845FE4B694C7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','辅助功能-行关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 98 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1B6844EFCAE114B1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'请购单维护','辅助功能-行打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 99 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-700B183E3B829BE5' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 100 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-496383D5683CD177' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 101 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-156925368009D472' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 102 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F9BFB7B253EDC029' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 103 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7E89B6942DBFFA41' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 104 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-51D6153BAF1913D8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 105 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-764897D0C3CDC231' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','辅助功能-冻结','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 106 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F284551644C877AD' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','辅助功能-解冻','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 107 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-5F83691030EF585B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','辅助功能-整单关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 108 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-07F548767DF29BC8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','辅助功能-整单打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 109 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-60D1EAE5C335DBCB' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','辅助功能-行关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 110 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D46BE2973050B272' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购订单维护','辅助功能-行打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 111 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A2C3C7E7BD8DA3A8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 112 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-25794507518C73A9' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 113 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-BEEBC60CC1F9F64C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 114 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-398D18AE89E2C083' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 115 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E3FB97ABA534CDC7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 116 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D5A17A54EEFD1E8D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 117 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-81027D3704101930' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','辅助功能-冻结','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 118 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-0F907C5F9342D2A7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','辅助功能-解冻','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 119 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-63036038836AE4FE' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','关联发票-确认','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 120 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7130EDE50CABE8FE' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','取消关联发票','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 121 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-94AFEFCA6F5958DA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','关联功能-传应付','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 122 行，分类： 采购管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1652A63F9BC99CC3' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购发票维护','关联功能-取消传应付','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 123 行，分类： 采购价格
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8FDD3381BD545EED' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商价目表维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 124 行，分类： 采购价格
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-3C98471632CF0434' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'供应商价目表维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 125 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F7DBD94ACB7D9546' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'期初余额','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 126 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2A81D447CED47CF6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'期初余额','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 127 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C10800F14217C2EA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'期初余额','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 128 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-AAFA9227A1F96898' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'期初余额','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 129 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-43CFB46A5D6F614C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购入库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 130 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-BBB6F605E37071ED' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购入库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 131 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-3DD170546891640A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购入库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 132 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-888EEE9C09C7EA2E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'采购入库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 133 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C97F7D23C57508FB' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'投产单','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 134 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2E5602AD2FF78DCA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'投产单','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 135 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-EB68D7B4AED73E99' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'投产单','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 136 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-646556AA195B8A48' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'投产单','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 137 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1B5D1E16EF083AD2' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨入库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 138 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2E25582C897E1BD5' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨入库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 139 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-AF5A7A4A15748875' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨入库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 140 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8F2B5546A8578867' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨入库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 141 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2C30480472C0E461' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委外加工入库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 142 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-87BCC12A32FE28F8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委外加工入库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 143 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-97BDCF1EFAE8DFF9' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委外加工入库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 144 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-643237D91DBA2D52' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委外加工入库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 145 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F76979AADB8E2E71' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'产成品入库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 146 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C05A0CA556EED8E7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'产成品入库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 147 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2AAA91681E6A0692' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'产成品入库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 148 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-624D28082F8424E8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'产成品入库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 149 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-EEA7B163450FFE7B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他入库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 150 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1D188A6A017BF122' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他入库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 151 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-17289E7A161A00E7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他入库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 152 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7375F62EF78833C6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他入库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 153 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-91536308A44A2EEC' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委托加工入库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 154 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8D0A4945C08F4EC8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委托加工入库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 155 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-EF0C02845E215DDE' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委托加工入库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 156 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-88A31272A089EA2D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'委托加工入库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 157 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-41CC70D7534AF3B1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 158 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C31C9590475F4297' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 159 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-931D24D0DD84834A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 160 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-6D9C72222CF4B33C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 161 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C7EA33244DC87A40' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 162 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-97A9C5615397566C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 163 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-92D15BDDBB698776' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','行关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 164 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E8E246982D39D8AD' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'出库申请单','行打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 165 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-11F0F608424EC25A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售出库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 166 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-41800183BD27AB33' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售出库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 167 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A3F1772B910FC6A8' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售出库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 168 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-29F2810C210A1B58' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售出库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 169 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8EAA046EE4BC2096' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨出库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 170 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A38A5E0163F76F5A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨出库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 171 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8767AE5097F761E6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨出库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 172 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-DC0D566DC68C848E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'调拨出库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 173 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C34C17D921A22959' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'材料出库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 174 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7A912AE266075A13' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'材料出库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 175 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B6AC766DEB957C2A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'材料出库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 176 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-3EE79F6544A4BE12' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'材料出库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 177 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-6A609F1B56ADF143' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他出库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 178 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-A2EFF9E33873AB4F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他出库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 179 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8E55706979387FAB' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他出库','签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 180 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-1AF23ED29EF95F14' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'其他出库','取消签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 181 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B56EB0514CBEE93F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 182 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-143AF63950B3FA7C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 183 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-163569189B7E0E2D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 184 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C23E3866BABAA0F5' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 185 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-549D8CA6BFE533A9' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 186 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8CEAEBD876F03C72' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 187 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-817766AFE162CF43' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','调整-保存&签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 188 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-38F4DC644D8F263D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'盘点','取消调整','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 189 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FC88F9EEFF039FDC' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 190 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8C642A3CAE8C797F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 191 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-AB053148A6BF31F1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 192 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-E31D2EFC02DBA25A' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 193 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-240C2DCFECE6E17E' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 194 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-339A6950CDA25292' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 195 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8DAFD17383D827EA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','直接转出-保存&签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 196 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8103F7CD6D4B4180' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','转出-保存&签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 197 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B62E3522D6CAD698' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'转库','转入-保存&签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 198 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F33D5BEE0B239BAF' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'货位调整','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 199 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-195766C97B0515E3' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'货位调整','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 200 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F3256F1FE0D536F5' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 201 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-286EB541FD910A2F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 202 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-33E54B1A6F7F8BB3' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 203 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4195634597158FF5' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 204 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2101228438A33DAC' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 205 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-ADC1AF5474A17427' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 206 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-96F6A5E398D5E2F7' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'形态转换','生成出入库单-保存&签字','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 207 行，分类： 库存管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-B3F539FB0440DD14' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'库存状态调整','状态调整-确认','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 208 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-EAF88AC5CAF75442' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 209 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FEFBC5B292C330AD' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 210 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4671CC2E70A34B97' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 211 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4F23D38FB22BA45F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 212 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-9C0B035FFEB2110D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 213 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-51BB76212FFCD156' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 214 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C7E51B3ED69F8B05' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','辅助功能-冻结','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 215 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-BF1F44B944C037B3' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','辅助功能-解冻','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 216 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-2A439AB1FE3628B0' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单维护','辅助功能-整单关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 217 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-6DA27F33C45CD3B0' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','整单关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 218 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-4A1493B793E22F6D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','整单打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 219 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-F598A0B4A16EFE84' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','行关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 220 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FF713F12789B45A1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','行打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 221 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-887831FDC8C23F76' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','发货关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 222 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8A7BC4FA7E1631FB' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','发货打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 223 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-32542641C9DC8398' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','出库关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 224 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-64B839C61D768D19' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','出库打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 225 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-9985A340EB7EFFC4' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','开票关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 226 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-C6E139BBEF4B8BE6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','开票打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 227 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-BA71E137623CEC43' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','结算关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 228 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-42EE6E523F56E9D0' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售订单关闭管理','结算打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 229 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-04ECAF336F8932CA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 230 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-FF81305763F3CF1F' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 231 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-D494A8A893DDBC0B' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 232 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-14232F4638F48C8C' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 233 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-257B45A6A4F80EF6' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 234 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-7DA00D0A9032E168' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','取消审批','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 235 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-52A72C09C2BE1F47' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','辅助功能-整单关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 236 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-61C46C0C9ED0BC7D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','辅助功能-整单打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 237 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-AF1994D66608BEF1' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','辅助功能-出库关闭','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 238 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-5275CF042A861E3D' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'发货单维护','辅助功能-出库打开','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 239 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-176DC431DD4F5803' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售发票维护','保存','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 240 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-8F8846F2D6E1ECD0' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售发票维护','删除','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 241 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-56499B75D3E91C33' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售发票维护','提交','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 242 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-29895E169F92EB91' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售发票维护','收回','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 243 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-9AE255EF674BD8E2' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售发票维护','审批-确定','UNMAPPED','N');
-- 来源工作表及行号：Sheet2 第 244 行，分类： 销售管理
MERGE INTO qd_es_node d USING (SELECT 'QD-ES-5EBFFEF0FFE24CAA' scope_id FROM dual) s ON (d.scope_id=s.scope_id) WHEN NOT MATCHED THEN INSERT(scope_id,node_name,button_name,mapping_state,validated) VALUES(s.scope_id,'销售发票维护','取消审批','UNMAPPED','N');

-- END SOURCE 008_scope_seed.sql
-- BEGIN GENERATED HISTORY 202608
-- Generated monthly history DDL. REVIEW AND APPROVE BEFORE EXECUTION.
-- Oracle DDL commits implicitly. Run only after 001/002/003.
-- Existing monthly tables are not dropped or overwritten. No COMMIT command.
-- CTAS uses existing approved QD columns; all data remains in the same schema.
CREATE TABLE qd_es_request_h202608 AS SELECT * FROM qd_es_request WHERE 1=0;
ALTER TABLE qd_es_request_h202608 ADD CONSTRAINT qdh202608_1_pk PRIMARY KEY(request_id);
CREATE INDEX qdh202608_1_scope_ix ON qd_es_request_h202608(pk_group,pk_org,created_at);
CREATE TABLE qd_es_object_h202608 AS SELECT * FROM qd_es_object WHERE 1=0;
ALTER TABLE qd_es_object_h202608 ADD CONSTRAINT qdh202608_2_pk PRIMARY KEY(object_id);
ALTER TABLE qd_es_object_h202608 ADD CONSTRAINT qdh202608_2_uq UNIQUE(request_id,client_object_id);
CREATE INDEX qdh202608_2_scope_ix ON qd_es_object_h202608(pk_group,pk_org,created_at);
CREATE INDEX qdh202608_2_req_ix ON qd_es_object_h202608(request_id);
CREATE INDEX qdh202608_2_bill_ix ON qd_es_object_h202608(pk_group,pk_org,bill_code);
CREATE TABLE qd_es_signlog_h202608 AS SELECT * FROM qd_es_signlog WHERE 1=0;
ALTER TABLE qd_es_signlog_h202608 ADD CONSTRAINT qdh202608_3_pk PRIMARY KEY(event_id);
ALTER TABLE qd_es_signlog_h202608 ADD CONSTRAINT qdh202608_3_uq UNIQUE(request_id,attempt_id,object_id);
CREATE INDEX qdh202608_3_scope_ix ON qd_es_signlog_h202608(pk_group,pk_org,event_time);
CREATE INDEX qdh202608_3_req_ix ON qd_es_signlog_h202608(request_id);
CREATE INDEX qdh202608_3_bill_ix ON qd_es_signlog_h202608(pk_group,pk_org,bill_code);
CREATE TABLE qd_es_audit_h202608 AS SELECT * FROM qd_es_audit WHERE 1=0;
ALTER TABLE qd_es_audit_h202608 ADD CONSTRAINT qdh202608_4_pk PRIMARY KEY(audit_id);
ALTER TABLE qd_es_audit_h202608 ADD CONSTRAINT qdh202608_4_uq UNIQUE(request_id,object_id,operation_type);
CREATE INDEX qdh202608_4_scope_ix ON qd_es_audit_h202608(pk_group,pk_org,event_time);
CREATE INDEX qdh202608_4_req_ix ON qd_es_audit_h202608(request_id);
CREATE INDEX qdh202608_4_bill_ix ON qd_es_audit_h202608(pk_group,pk_org,bill_code);
CREATE TABLE qd_es_change_h202608 AS SELECT * FROM qd_es_change WHERE 1=0;
ALTER TABLE qd_es_change_h202608 ADD CONSTRAINT qdh202608_5_pk PRIMARY KEY(change_id);
CREATE INDEX qdh202608_5_scope_ix ON qd_es_change_h202608(pk_group,pk_org,event_time);
CREATE INDEX qdh202608_5_req_ix ON qd_es_change_h202608(request_id);
CREATE INDEX qdh202608_5_audit_ix ON qd_es_change_h202608(audit_id);
CREATE TABLE qd_es_snapshot_h202608 AS SELECT * FROM qd_es_snapshot WHERE 1=0;
ALTER TABLE qd_es_snapshot_h202608 ADD CONSTRAINT qdh202608_6_pk PRIMARY KEY(snapshot_id);
CREATE INDEX qdh202608_6_scope_ix ON qd_es_snapshot_h202608(pk_group,pk_org,event_time);
CREATE INDEX qdh202608_6_req_ix ON qd_es_snapshot_h202608(request_id);
CREATE INDEX qdh202608_6_audit_ix ON qd_es_snapshot_h202608(audit_id);
CREATE TABLE qd_es_security_h202608 AS SELECT * FROM qd_es_security WHERE 1=0;
ALTER TABLE qd_es_security_h202608 ADD CONSTRAINT qdh202608_7_pk PRIMARY KEY(event_id);
ALTER TABLE qd_es_security_h202608 ADD CONSTRAINT qdh202608_7_uq UNIQUE(source_system,source_event_id,source_version);
CREATE INDEX qdh202608_7_scope_ix ON qd_es_security_h202608(pk_group,pk_org,event_time);
INSERT INTO qd_es_archive(archive_month,status) VALUES ('202608','PREPARED');

-- END GENERATED HISTORY 202608
-- Explicitly approved initialization only; all controls remain off.
COMMIT;
