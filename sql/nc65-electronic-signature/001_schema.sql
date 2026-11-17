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
