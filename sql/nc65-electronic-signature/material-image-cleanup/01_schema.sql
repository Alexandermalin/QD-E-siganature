-- QDNC. Installs the material post-commit image cleanup task table.
-- Run in the stopped maintenance window together with the matching module patch.
-- Oracle DDL commits implicitly. Back up first; this script does not enable a route or policy.

CREATE TABLE qd_es_img_cleanup (

  -- 清理任务 UUID32 标识。
  cleanup_id VARCHAR2(32) NOT NULL,
  -- 登记任务的业务请求号，指向 qd_es_request.request_id。
  request_id VARCHAR2(32) NOT NULL,
  -- NC 集团原生主键。
  pk_group VARCHAR2(20) NOT NULL,

  -- 固定业务对象类型，当前仅 BD_MATERIAL。
  object_type VARCHAR2(30) NOT NULL,
  -- 已删除档案的原生主键；删除后主键不会被原生复用。
  object_pk VARCHAR2(20) NOT NULL,
  -- 业务事务中登记的图片目录完整路径，清理只允许删除该目录。
  folder_path VARCHAR2(500) NOT NULL,

  -- 登记时目录节点主键，防止同路径目录重建后误删。
  folder_pk VARCHAR2(50),
  -- 登记时目录节点修改时间毫秒，作为版本证据。
  folder_version NUMBER(20),
  -- PENDING 待清理、RUNNING 已认领、CLEANED 已清理、NEEDS_REVIEW 待人工核对。
  status VARCHAR2(30) DEFAULT 'PENDING' NOT NULL,
  -- 抢占令牌和租约到期 UTC；到期后转待核对，不自动重复执行不确定的文件操作。
  lease_token VARCHAR2(32),
  lease_until CHAR(19),

  -- 已尝试次数，达到上限转 NEEDS_REVIEW。
  attempt_count NUMBER(10) DEFAULT 0 NOT NULL,
  -- 可供调度采用的下一重试 UTC 时间。
  next_retry_at CHAR(19),
  -- 任务登记 UTC 时间。
  created_at CHAR(19) NOT NULL,

  -- 任务终态 UTC 时间。
  finished_at CHAR(19),
  -- 最近错误或待核对说明。
  last_error VARCHAR2(2000),

  -- 主键保证任务身份唯一。
  CONSTRAINT qdes_imgcleanup_pk PRIMARY KEY(cleanup_id),
  -- 同一请求和物料只登记一次，恢复后重新删除可创建新任务。
  CONSTRAINT qdes_imgcleanup_obj_uq UNIQUE(request_id,object_pk),
  -- 状态取值固定，防止随手扩展语义。
  CONSTRAINT qdes_imgcleanup_status_ck CHECK (status IN ('PENDING','RUNNING','CLEANED','NEEDS_REVIEW')),
  CONSTRAINT qdes_imgcleanup_ident_ck CHECK (status='NEEDS_REVIEW' OR (folder_pk IS NOT NULL AND folder_version IS NOT NULL)),
  CONSTRAINT qdes_imgcleanup_lease_ck CHECK ((status='RUNNING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
    OR (status<>'RUNNING' AND lease_token IS NULL AND lease_until IS NULL))
);

CREATE INDEX qdes_img_cleanup_dispatch_idx ON qd_es_img_cleanup (pk_group,status,next_retry_at);

COMMENT ON TABLE qd_es_img_cleanup IS '物料提交后图片清理任务；随业务事务登记，后台按登记身份幂等清理';

-- Directory and file metadata captured with the material delete transaction. This evidence does
-- not by itself authorize physical deletion: the storage version and writer fencing still need verification.
CREATE TABLE qd_es_img_cleanup_item (
  cleanup_id VARCHAR2(32) NOT NULL,
  file_pk VARCHAR2(100) NOT NULL,
  file_path VARCHAR2(2000) NOT NULL,
  is_folder VARCHAR2(10),
  is_doc VARCHAR2(10),
  storage_pk VARCHAR2(2000),
  modify_time VARCHAR2(100),
  last_time VARCHAR2(100),
  file_length VARCHAR2(100),
  CONSTRAINT qdes_imgcleanup_item_pk PRIMARY KEY(cleanup_id,file_pk),
  CONSTRAINT qdes_imgcleanup_item_fk FOREIGN KEY(cleanup_id) REFERENCES qd_es_img_cleanup(cleanup_id)
);

-- Keep the pre-install values and cleanup evidence across rollback.
CREATE TABLE qd_es_matdel_route_bak (
  scope_id VARCHAR2(64) NOT NULL,
  existed CHAR(1) NOT NULL,
  node_code VARCHAR2(100),
  button_code VARCHAR2(100),
  button_name VARCHAR2(200 CHAR),
  adapter_class VARCHAR2(300),
  operation_types VARCHAR2(200),
  object_type VARCHAR2(100),
  validated CHAR(1),
  mapping_state VARCHAR2(30),
  CONSTRAINT qdes_matdel_route_bak_pk PRIMARY KEY (scope_id),
  CONSTRAINT qdes_matdel_route_bak_ck CHECK (existed IN ('Y','N'))
);
