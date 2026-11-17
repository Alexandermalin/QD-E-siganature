-- V1.0.0 当前没有后续结构迁移，不能把本预检当作升级执行脚本。
-- 这里只读检查升级前状态；后续迁移必须使用新的不可变版本脚本。
-- 核对已应用版本及实际自建表，不对数据库做结构修改。
SELECT version_no,applied_at,description FROM qd_es_schema_version ORDER BY applied_at;
SELECT table_name FROM user_tables WHERE table_name LIKE 'QD_ES_%' ORDER BY table_name;
