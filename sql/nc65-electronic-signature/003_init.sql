-- 初始状态没有集团总控或策略即为关闭；本脚本不显式提交。
-- 集团控制行由有管理权限的用户通过策略界面显式创建。
-- 节点目录通过离线生成器读取已批准范围 CSV 产生。
-- 范围目录或适配器类存在都不代表启用策略，验证标记仍默认 N。
-- 只登记本结构版本及 UTC 应用时间，不创建默认启用的集团或节点策略。
INSERT INTO qd_es_schema_version(version_no,applied_at,description)
VALUES ('1.0.0',TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP),'YYYY-MM-DD HH24:MI:SS'),
        'Initial QD NC65 electronic signature schema; all controls default OFF');
