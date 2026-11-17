-- QDNC, 1.3.0. Candidate route registration for customer/supplier bank account save and delete.
-- Formal actions share the account-scoped impact expansion; the popup nodes are 10140CBA / 10140SBA.
-- Run in the stopped maintenance window after 01_schema.sql, the module patch and the readonly precheck.
-- Routes are registered as PILOT with validated='N'; isolated client acceptance is required before
-- setting validated='Y'. This script does not set any policy.
-- Do not run with autocommit. Record the precheck results before executing this block.

DECLARE
  v_cust_save  CONSTANT VARCHAR2(64) := 'QD-ES-E153DD629D00947F';
  v_cust_del   CONSTANT VARCHAR2(64) := 'QD-ES-70EC3A5C19555BD7';
  v_sup_save   CONSTANT VARCHAR2(64) := 'QD-ES-E70DEE0145D79FDE';
  v_sup_del    CONSTANT VARCHAR2(64) := 'QD-ES-00DF272BF31F0AA2';
  v_save_adapter CONSTANT VARCHAR2(100) := 'nc.itf.uapbd.qd.esign.IBankAccountSaveAdapter';
  v_del_adapter  CONSTANT VARCHAR2(100) := 'nc.itf.uapbd.qd.esign.IBankAccountDeleteAdapter';
  v_count NUMBER;

  PROCEDURE require_zero(p_count NUMBER, p_message VARCHAR2) IS
  BEGIN
    IF p_count<>0 THEN RAISE_APPLICATION_ERROR(-20274,p_message); END IF;
  END;

  -- 按正式动作登记弹窗节点路由；同一 scope 已有其他适配器登记时拒绝执行。
  -- 现场弹窗按钮（2026-11-11 只读预检）：Add/Cancel/Delete/Disable/Edit/Enable/Refresh；
  -- 保存动作含新增 Add 与修改 Edit 两个入口，各占一行（node+button 唯一）、同指一个正式动作与适配器。
  PROCEDURE register(p_scope VARCHAR2, p_node VARCHAR2, p_button VARCHAR2, p_ops VARCHAR2,
                     p_object VARCHAR2, p_button_name VARCHAR2, p_adapter VARCHAR2) IS
    v_alias CONSTANT VARCHAR2(64) := p_scope||'@'||p_node||':'||p_button;
  BEGIN
    SELECT COUNT(*) INTO v_count FROM qd_es_node
     WHERE scope_id=v_alias AND group_scope_id=p_scope AND adapter_class IS NOT NULL
       AND adapter_class<>p_adapter;
    IF v_count<>0 THEN RAISE_APPLICATION_ERROR(-20275,'Conflicting bank account mapping for '||p_scope); END IF;
    MERGE INTO qd_es_node dst
      USING (SELECT 1 FROM dual) src
        ON (dst.scope_id=v_alias AND dst.group_scope_id=p_scope)
      WHEN MATCHED THEN UPDATE SET dst.node_code=p_node,dst.button_code=p_button,
        dst.adapter_class=p_adapter,dst.operation_types=p_ops,dst.object_type=p_object,
        dst.validated='N',dst.mapping_state='PILOT'
      WHEN NOT MATCHED THEN INSERT
        (scope_id,group_scope_id,node_code,node_name,button_code,button_name,adapter_class,
         operation_types,object_type,validated,mapping_state)
        VALUES (v_alias,p_scope,p_node,
                CASE WHEN p_node='10140CBA' THEN '客户银行账户' ELSE '供应商银行账户' END,
                p_button,p_button_name,p_adapter,p_ops,p_object,'N','PILOT');
    require_zero(SQL%ROWCOUNT-1,'Unexpected bank account route insert');
  END;
BEGIN
  register(v_cust_save,'10140CBA','Add','INSERT','BD_CUSTOMER_BANK','新增',v_save_adapter);
  register(v_cust_save,'10140CBA','Edit','UPDATE','BD_CUSTOMER_BANK','修改',v_save_adapter);
  register(v_cust_del ,'10140CBA','Delete','DELETE','BD_CUSTOMER_BANK','删除',v_del_adapter);
  register(v_sup_save ,'10140SBA','Add','INSERT','BD_SUPPLIER_BANK','新增',v_save_adapter);
  register(v_sup_save ,'10140SBA','Edit','UPDATE','BD_SUPPLIER_BANK','修改',v_save_adapter);
  register(v_sup_del  ,'10140SBA','Delete','DELETE','BD_SUPPLIER_BANK','删除',v_del_adapter);
  COMMIT;
END;
/

-- 隔离验收通过并逐项登记证据后，才允许把对应行的 validated 置为 'Y'；
-- 回退按 04 预检记录的原 adapter_class 与 validated 恢复。
