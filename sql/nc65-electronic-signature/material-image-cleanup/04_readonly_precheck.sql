-- Read-only site discovery for the material main archive delete candidate.
-- This script does not enable routes, create objects, or update NC data.
-- The delete candidate calls the base deleteMaterial service only; any configured
-- material extension service would leave orphan rows behind. Confirm the installed
-- client and server configuration is supported before enabling the candidate route.

SELECT SYS_CONTEXT('USERENV','SESSION_USER') AS session_user,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AS current_schema,
       SYS_CONTEXT('USERENV','DB_UNIQUE_NAME') AS database_name,
       LOWER(SYS_CONTEXT('USERENV','SERVER_HOST')) AS server_host FROM dual;

-- 1. 真实功能节点与按钮登记：集团/业务单元物料节点必须存在 Delete 按钮。
SELECT f.funcode,b.btncode,b.pk_btn
  FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
 WHERE f.funcode IN ('10140MAG','10140MAO')
   AND UPPER(b.btncode) IN ('DELETE','DEL')
   AND NVL(f.dr,0)=0 AND NVL(b.dr,0)=0
 ORDER BY f.funcode,b.btncode;

-- 2. 物料页签配置：包含普通页签，不能据此判断扩展持久化服务是否为空。
SELECT metadataid,nodekey,exbean_name,ex_configpath
  FROM bd_uitabextend
 WHERE metadataid IN ('10140MAG','10140MAO','c7dc0ccd-8872-4eee-8882-160e8f49dfad')
 ORDER BY metadataid,nodekey;

SELECT f.funcode,p.paramname,p.paramvalue
  FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid
 WHERE f.funcode IN ('10140MAG','10140MAO')
   AND p.paramname='BeanConfigFilePath' AND NVL(f.dr,0)=0 AND NVL(p.dr,0)=0
 ORDER BY f.funcode,p.paramname;

-- 3. 首次安装前只检查对象存在性；建表后再查询任务状态。
SELECT object_name,object_type,status FROM user_objects
 WHERE object_name IN ('QD_ES_IMG_CLEANUP','QD_ES_IMG_CLEANUP_ITEM','QD_ES_MATDEL_ROUTE_BAK') ORDER BY object_name;
SELECT scope_id,group_scope_id,node_code,node_name,button_code,button_name,adapter_class,
       operation_types,object_type,validated,mapping_state
  FROM qd_es_node WHERE scope_id IN ('QD-ES-2F2608B07F6B73DD','QD-ES-2F2608B07F6B73DD@10140MAO')
    OR (node_code IN ('10140MAG','10140MAO') AND button_code='Delete') ORDER BY scope_id;

-- 注：图片目录约定（uapbd/IMG/<物料元数据类>/<物料主键>）来自本机原生字节码证据，
-- 文件系统元数据的路径存在或消失均不能证明底层文件存储结果；服务与物理存储行为
-- 以隔离环境实测为准，本脚本不以元数据查询替代存储核验。
