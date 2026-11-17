-- Read-only site discovery for the bank account save/delete candidates.
-- This script does not enable routes, create objects, or update NC data.
-- The unified lock only covers controlled entries; every native bank-account write
-- entry must be inventoried before the lock can be treated as complete coverage.

SELECT SYS_CONTEXT('USERENV','SESSION_USER') AS session_user,
       SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AS current_schema,
       SYS_CONTEXT('USERENV','DB_UNIQUE_NAME') AS database_name,
       LOWER(SYS_CONTEXT('USERENV','SERVER_HOST')) AS server_host FROM dual;

-- 1. 弹窗节点与按钮登记：保存/删除按钮必须存在于两个弹窗节点。
SELECT f.funcode,b.btncode,b.pk_btn
  FROM sm_funcregister f JOIN sm_butnregister b ON (b.parent_id=f.cfunid OR b.parent_id=f.funcode)
 WHERE f.funcode IN ('10140CBA','10140SBA')
   AND UPPER(b.btncode) IN ('SAVE','DELETE','DEL','ADDSUB','EDIT')
   AND NVL(f.dr,0)=0 AND NVL(b.dr,0)=0
 ORDER BY f.funcode,b.btncode;

-- 2. 共享账户与关系规模抽样：多档案共用的账户数量与最大关系数，用于核对影响范围上限。
SELECT COUNT(*) AS shared_accounts FROM (
  SELECT pk_bankaccbas FROM bd_custbank WHERE NVL(dr,0)=0
  GROUP BY pk_bankaccbas HAVING COUNT(*)>1);

SELECT MAX(cnt) AS max_relations_per_account FROM (
  SELECT COUNT(*) cnt FROM bd_custbank WHERE NVL(dr,0)=0 GROUP BY pk_bankaccbas);

-- 3. 账户主档与关系行的组织字段结构核对（快照列与所属组织解析依据）。
SELECT table_name,column_name,data_type,data_length,nullable
  FROM user_tab_columns
 WHERE table_name IN ('BD_BANKACCBAS','BD_BANKACCSUB','BD_BANKACCUSE','BD_CUSTBANK')
 ORDER BY table_name,column_id;

-- 4. 既有锁行规模（首次安装应为零）与启停候选已登记路由。
SELECT COUNT(*) AS lock_rows FROM qd_es_bankacc_lock;
SELECT scope_id,node_code,button_code,adapter_class,validated,mapping_state
  FROM qd_es_node
 WHERE node_code IN ('10140CBA','10140SBA')
 ORDER BY scope_id,button_code;
