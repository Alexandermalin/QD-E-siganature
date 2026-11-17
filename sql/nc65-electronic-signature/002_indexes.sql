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
