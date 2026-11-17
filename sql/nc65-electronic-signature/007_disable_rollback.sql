-- 这里只停用控制；先核对受影响集团及策略数量，重新启用须按原管理流程操作。
-- 通过部署工具绑定目标集团参数，不自动提交。
SELECT pk_group,master_enabled,policy_version FROM qd_es_control WHERE pk_group=:pk_group;
SELECT COUNT(*) AS affected_policy_count FROM qd_es_policy WHERE pk_group=:pk_group;
-- 停用集团总控并推进版本，旧请求即使随后重新启用总控也不能继续使用。
UPDATE qd_es_control SET master_enabled='N',policy_version=policy_version+1,
 modified_at=TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS')
 WHERE pk_group=:pk_group;
-- 停用该集团节点策略并推进版本，保留策略配置和全部证据。
UPDATE qd_es_policy SET enabled='N',policy_version=policy_version+1,
 modified_at=TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS')
 WHERE pk_group=:pk_group;
-- 应用撤回仅依据已记录备份移除本次新增包及配置。
-- 正常撤回不删除、截断或销毁审计数据。
