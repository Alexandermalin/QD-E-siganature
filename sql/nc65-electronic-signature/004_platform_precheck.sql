-- 只读预检；记录查询结果后才能编写对应现场的注册变更。
-- 待核实原生表列来自本地验证的加载器及 NC65 数据字典。
-- 不根据名称推测外键、节点编码或现场既有规则值。
-- 第一步确认现场表列、类型、长度及可空性；后续查询必须在相关列核实后运行。
SELECT table_name,column_name,data_type,data_length,nullable
  FROM user_tab_columns
 WHERE table_name IN ('SM_FUNCREGISTER','PUB_PLUGINITEM','PUB_EVENTLISTENER',
                      'PUB_EVENTTYPE','BD_BILLTYPE','DAP_DAPSYSTEM','SM_LOG_OPERATELOG')
 ORDER BY table_name,column_id;
-- 定位清单中待确认节点的候选记录，结果需人工核实，不能仅凭名称确定节点编码。
SELECT funcode,fun_name,class_name,pk_group,isenable
  FROM sm_funcregister
 WHERE fun_name LIKE '%投产%' OR fun_name LIKE '%委外%' OR fun_name LIKE '%委托加工入库%'
 ORDER BY funcode;
-- 读取对应单据类型既有规则类，避免接入时覆盖原检查链。
SELECT pk_billtypecode,billtypename,nodecode,checkclassname
  FROM bd_billtype
 WHERE pk_billtypecode IN ('21','25','30','422X');
-- 核对原服务插件与扩展点的顺序及规则实现，接入须保留现有原生行为。
SELECT vmodulename,vcomponentname,vextendpointname,veventtype,vextendtype,
       vruleclass,vextendmodule,vtargetclass,iorder
  FROM pub_pluginitem
 WHERE vmodulename IN ('uapbd','pu','so','ic','purp')
 ORDER BY vmodulename,vcomponentname,vextendpointname,iorder;
-- 核对同步业务事件监听器的来源、启用状态和执行顺序，判断权威快照是否可采集。
SELECT t.sourceid,t.eventtypecode,l.pk_eventlistener,l.implclassname,l.enabled,l.operindex
  FROM pub_eventlistener l
  LEFT JOIN pub_eventtype t ON t.pk_eventtype=l.pk_eventtype
 WHERE t.eventtypecode IN ('1001','1002','1003','1004','1005','1006')
 ORDER BY t.sourceid,t.eventtypecode,l.operindex;
-- 先确认前述实际源字段，再评估能否可靠增量导入。
-- 统计缺少源时间戳的登录日志，非零时不能宣称增量水位可靠完整。
SELECT COUNT(*) AS unversioned_login_records
  FROM sm_log_operatelog WHERE pk_group=:pk_group AND type=0 AND ts IS NULL;
