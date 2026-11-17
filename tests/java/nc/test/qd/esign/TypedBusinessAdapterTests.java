package nc.test.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.impl.ic.qd.esign.InventoryOperationAdapter;
import nc.impl.pu.qd.esign.PurchaseOrderOperationAdapter;
import nc.impl.purp.qd.esign.SupplierPriceOperationAdapter;
import nc.impl.uapbd.qd.esign.MaterialOperationAdapter;
import nc.ui.bd.pub.extend.PrivateServiceContext;
import nc.ui.bd.pub.extend.PrivateServiceInfo;
import nc.vo.bd.material.MaterialVO;
import nc.vo.bd.meta.BatchOperateVO;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.m45.entity.PurchaseInHeadVO;
import nc.vo.ic.m45.entity.PurchaseInVO;
import nc.vo.ic.m4c.entity.SaleOutVO;
import nc.vo.ic.qd.esign.InventoryOperationCommand;
import nc.vo.pp.supplierprice.entity.SupplierPriceVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.billtype.BilltypeVO;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.pu.m21.context.OrderContext;
import nc.vo.pu.m21.entity.OrderHeaderVO;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pu.qd.esign.PurchaseOrderOperationCommand;
import nc.vo.purp.qd.esign.SupplierPriceOperationCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialCommand;
import nc.vo.uapbd.qd.esign.OperationContext;

/** 类型化业务适配器的离线契约测试，使用 VO 取值测试桩核对授权对象、组织及参数配对；不运行原生业务服务或完整元数据系统。 */
public final class TypedBusinessAdapterTests {
    /** assertions：已执行的离线断言计数。 */
    private static int assertions;
    /** 允许抛出异常的测试操作接口，用于断言非法业务载荷必须被拒绝。 */
    private interface Checked {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        void run() throws Exception; }
    // 聚合 VO 构造时会请求元数据；本测试仅提供空子实体布局，
    // 其余原生元数据操作仍不可用，不模拟 NC 业务行为。
    /** FIXTURE_META：聚合 VO 构造所需的最小元数据测试替身，仅提供空子实体布局。 */
    private static final IBillMeta FIXTURE_META = new IBillMeta() {
        /** 返回测试所需的最小业务属性数据，不模拟原生元数据查询。 */
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        /** 提供测试聚合 VO 的最小子表外键布局。 */
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        /** 提供空子实体布局，使聚合 VO 测试不依赖原生元数据服务。 */
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        /** 返回测试元数据组件名称。 */
        public String getComponentName() { return "OFFLINE_ADAPTER_FIXTURE"; }
        /** 提供测试需要的父元数据引用，不调用原生元数据系统。 */
        public IVOMeta getParent() { throw new UnsupportedOperationException("No native metadata in offline fixture"); }
        /** 返回测试元数据对应的 VO 类型，不支持的原生能力明确保持不可用。 */
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException("No native metadata in offline fixture"); }
        /** 提供测试所需 VO 元数据访问，不模拟完整 NC 元数据行为。 */
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException("No native metadata in offline fixture"); }
    };

    /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
    public static int run() throws Exception {
        assertions = 0;
        materialPilot();
        purchaseOrders();
        inventory();
        supplierPrices();
        return assertions;
    }
    /** 命令行入口，执行本文件的离线检查或原有工程示例。 */
    public static void main(String[] args) throws Exception {
        System.out.println("PASS typed-adapter validation/correlation assertions=" + run()
                + " (NC native business services were not invoked)");
    }

    private static void materialPilot() throws Exception {
        final MaterialOperationAdapter adapter = new MaterialOperationAdapter();
        final OperationContext insert = new OperationContext("R", "B", "G", "O", "10140MAO", "Material",
                "Save", "Save", "INSERT", null,
                Collections.singletonList(new BusinessObjectRef("draft", "BD_MATERIAL", null, null, null)));
        final MaterialVO draft = material(null, "O", null, null);
        final MaterialCommand ordinary = new MaterialCommand(draft, Collections.<String, Object>emptyMap(),
                new PrivateServiceContext());
        adapter.validate(insert, ordinary);
        check(ordinary.getMaterial() == draft, "Native MaterialVO is preserved");

        PrivateServiceInfo untrustedService = new PrivateServiceInfo();
        untrustedService.setIdentifier("outside-pilot");
        untrustedService.setPersistenceService("unknown.Service");
        PrivateServiceContext untrustedContext = new PrivateServiceContext();
        untrustedContext.addExtendInfo(untrustedService);
        List<MaterialCommand> invalid = new ArrayList<MaterialCommand>();
        invalid.add(new MaterialCommand(draft));
        invalid.add(new MaterialCommand(draft, Collections.<String, Object>emptyMap(), untrustedContext));
        invalid.add(new MaterialCommand(draft, Collections.<String, Object>singletonMap("extension", "value"),
                new PrivateServiceContext()));
        invalid.add(new MaterialCommand(material(null, "OTHER", null, null), Collections.<String, Object>emptyMap(),
                new PrivateServiceContext()));
        invalid.add(new MaterialCommand(material(null, "O", "PF", null), Collections.<String, Object>emptyMap(),
                new PrivateServiceContext()));
        invalid.add(new MaterialCommand(material("existing", "O", null, null), Collections.<String, Object>emptyMap(),
                new PrivateServiceContext()));
        for (final MaterialCommand command : invalid) {
            expectFailure(new Checked() {
                public void run() throws Exception { adapter.validate(insert, command); }
            });
        }

        final OperationContext coded = new OperationContext("R", "B", "G", "O", "10140MAO", "Material",
                "Save", "Save", "INSERT", null,
                Collections.singletonList(new BusinessObjectRef("draft", "BD_MATERIAL", null, "M001", null)));
        expectFailure(new Checked() {
            public void run() throws Exception { adapter.validate(coded, ordinary); }
        });
        adapter.validate(coded, new MaterialCommand(material(null, "O", null, "M001"),
                Collections.<String, Object>emptyMap(), new PrivateServiceContext()));
        check(true, "Pilot accepts the signed material code");

        final OperationContext otherNode = context("BD_MATERIAL", "INSERT", (String) null);
        expectFailure(new Checked() {
            public void run() throws Exception { adapter.validate(otherNode, ordinary); }
        });
        adapter.validate(otherNode, new MaterialCommand(draft));
        check(true, "Previous base-material command is still accepted outside the pilot");

        final OperationContext delete = context("BD_MATERIAL", "DELETE", "A");
        expectFailure(new Checked() {
            public void run() throws Exception {
                adapter.validate(delete, new MaterialCommand(material("A", "O", null, "M001")));
            }
        });

        BusinessObjectRef saved = adapter.describeResult(insert,
                new MaterialVO[]{material("persisted", "O", null, "M001")}).get(0);
        check("draft".equals(saved.getClientObjectId()) && "persisted".equals(saved.getObjectPk())
                && "M001".equals(saved.getBillCode()), "Material save links the signed draft to the native result");
    }

    private static MaterialVO material(final String pk, final String org, final String pf, final String code) {
        return new MaterialVO() {
            public String getPrimaryKey() { return pk; }
            public String getPk_group() { return "G"; }
            public String getPk_org() { return org; }
            public String getPk_material_pf() { return pf; }
            public String getCode() { return code; }
        };
    }

    /** 验证采购订单命令、授权对象、真实组织归属及保存删除上下文的离线契约。 */
    private static void purchaseOrders() throws Exception {
        final String type = PurchaseOrderOperationCommand.OBJECT_TYPE;
        final Map<String, OrderHeaderVO> database = new HashMap<String, OrderHeaderVO>();
        database.put("A", order("A", "G", "O", "A1").getHVO());
        database.put("B", order("B", "G", "O", "B1").getHVO());
        final PurchaseOrderOperationAdapter adapter = new PurchaseOrderOperationAdapter() {
            /** 从原生数据库读取现存单据表头供归属校验，避免仅信任客户端提交的组织字段；测试可覆盖本方法提供替身。 */
            protected OrderHeaderVO loadPersistedHeader(String pk) { return database.get(pk); }
        };
        final OperationContext insert = context(type, "INSERT", (String) null);
        final PurchaseOrderOperationCommand add = PurchaseOrderOperationCommand.forSave(
                new OrderVO[] { order(null, "G", "O", null) }, new OrderContext());
        adapter.validate(insert, add); check(true, "Order insert shape accepted");
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(insert, PurchaseOrderOperationCommand.forSave(new OrderVO[] { order(null, "G", "X", null) }, new OrderContext()));
        }});
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context(type, "INSERT", null, null), PurchaseOrderOperationCommand.forSave(
                    new OrderVO[] { order(null, "G", "O", null), order(null, "G", "O", null) }, new OrderContext()));
        }});
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context(type, "APPROVE", "A"), PurchaseOrderOperationCommand.forSave(
                    new OrderVO[] { order("A", "G", "O", "A1") }, new OrderContext()));
        }});
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context("WRONG", "UPDATE", "A"), PurchaseOrderOperationCommand.forSave(
                    new OrderVO[] { order("A", "G", "O", "A1") }, new OrderContext()));
        }});
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context(type, "UPDATE", "A"), PurchaseOrderOperationCommand.forSave(
                    new OrderVO[] { order("B", "G", "O", "B1") }, new OrderContext()));
        }});
        database.put("A", order("A", "G", "OTHER", "A1").getHVO());
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context(type, "UPDATE", "A"), PurchaseOrderOperationCommand.forSave(
                    new OrderVO[] { order("A", "G", "O", "A1") }, new OrderContext()));
        }});
        database.put("A", order("A", "G", "O", "A1").getHVO());
        adapter.validate(context(type, "UPDATE", "A"), PurchaseOrderOperationCommand.forSave(
                new OrderVO[] { order("A", "G", "O", "A1") }, new OrderContext()));
        check(true, "Order update checks stored ownership");
        List<BusinessObjectRef> refs = adapter.describeResult(context(type, "UPDATE", "A", "B"),
                new OrderVO[] { order("B", "G", "O", "B2"), order("A", "G", "O", "A2") });
        check("A".equals(refs.get(0).getObjectPk()) && "A2".equals(refs.get(0).getBillCode())
                && "C0".equals(refs.get(0).getClientObjectId()), "Order result correlated by PK");
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.describeResult(context(type, "UPDATE", "A", "B"),
                    new OrderVO[] { order("A", "G", "O", "A1"), order("A", "G", "O", "A1") });
        }});
        refs = adapter.describeResult(insert, new OrderVO[] { order("NEW", "G", "O", "NEW-001") });
        check("NEW".equals(refs.get(0).getObjectPk()) && "NEW-001".equals(refs.get(0).getBillCode()), "Order insert uses actual returned identity");
        final OperationContext delete = context(type, "DELETE", "A");
        check(adapter.describeResult(delete, null).get(0) == delete.getObjects().get(0), "Order delete preserves authorized identity");
    }

    /** 验证库存对象类型、新旧单据按位置配对、授权主键和真实组织归属。 */
    private static void inventory() throws Exception {
        final String type = InventoryOperationCommand.PURCHASE_IN;
        final Map<String, ICBillHeadVO> database = new HashMap<String, ICBillHeadVO>();
        final Map<String, BilltypeVO> transactionTypes = new HashMap<String, BilltypeVO>();
        database.put("A", purchase("A", "G", "O", "PA").getHead());
        database.put("B", purchase("B", "G", "O", "PB").getHead());
        final InventoryOperationAdapter adapter = new InventoryOperationAdapter() {
            /** 从原生数据库读取现存单据表头供归属校验，避免仅信任客户端提交的组织字段；测试可覆盖本方法提供替身。 */
            protected ICBillHeadVO loadPersistedHeader(String objectType, String pk) { return database.get(pk); }
            protected BilltypeVO loadTransactionType(String pk) { return transactionTypes.get(pk); }
        };
        adapter.validate(context(type, "INSERT", (String) null), InventoryOperationCommand.purchaseIn(
                new PurchaseInVO[] { purchase(null, "G", "O", null) }, null));
        check(true, "Inventory single insert accepted");
        adapter.validate(context(type, "UPDATE", "A", "B"), InventoryOperationCommand.purchaseIn(
                new PurchaseInVO[] { purchase("A", "G", "O", "PA"), purchase("B", "G", "O", "PB") },
                new PurchaseInVO[] { purchase("A", "G", "O", "PA"), purchase("B", "G", "O", "PB") }));
        check(true, "Inventory update preserves native new/old pairing");
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context(type, "UPDATE", "A", "B"), InventoryOperationCommand.purchaseIn(
                    new PurchaseInVO[] { purchase("A", "G", "O", "PA"), purchase("B", "G", "O", "PB") },
                    new PurchaseInVO[] { purchase("B", "G", "O", "PB"), purchase("A", "G", "O", "PA") }));
        }});
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context(type, "UPDATE", "A"), InventoryOperationCommand.purchaseIn(
                    new PurchaseInVO[] { purchase("A", "G", "O", "PA") }, null));
        }});
        database.put("A", purchase("A", "OTHER", "O", "PA").getHead());
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context(type, "SIGN", "A"), InventoryOperationCommand.purchaseIn(
                    new PurchaseInVO[] { purchase("A", "G", "O", "PA") }, null));
        }});
        database.put("A", purchase("A", "G", "O", "PA").getHead());
        BilltypeVO otherTransaction = new BilltypeVO();
        otherTransaction.setPrimaryKey("OTHER-TYPE"); otherTransaction.setPk_billtypecode("45-Cxx-08");
        otherTransaction.setPk_group("G"); otherTransaction.setNodecode("40080602400");
        otherTransaction.setParentbilltype("45"); otherTransaction.setComponent("ic_purchasein");
        otherTransaction.setDr(Integer.valueOf(0)); transactionTypes.put("OTHER-TYPE", otherTransaction);
        expectFailure(new Checked() {
        public void run() throws Exception {
            adapter.validate(context(type, "SIGN", "A"), InventoryOperationCommand.purchaseIn(
                    new PurchaseInVO[] { purchase("A", "G", "O", "PA", "45", "OTHER-TYPE") }, null));
        }});
        database.put("A", purchase("A", "G", "O", "PA", "45", "OTHER-TYPE").getHead());
        expectFailure(new Checked() {
        public void run() throws Exception {
            adapter.validate(context(type, "SIGN", "A"), InventoryOperationCommand.purchaseIn(
                    new PurchaseInVO[] { purchase("A", "G", "O", "PA") }, null));
        }});
        database.put("A", purchase("A", "G", "O", "PA").getHead());
        adapter.validate(context(type, "SIGN", "A"), InventoryOperationCommand.purchaseIn(
                new PurchaseInVO[] { purchase("A", "G", "O", "PA") }, null));
        check(true, "Inventory sign passes ownership checks");
        expectFailure(new Checked() {
        public void run() throws Exception {
            adapter.validate(context(type, "SIGN", "A"), InventoryOperationCommand.purchaseIn(
                    new PurchaseInVO[] { purchase("A", "G", "O", "PA", "45-Cxx-08") }, null));
        }});
        database.put("A", purchase("A", "G", "O", "PA", "45-Cxx-08").getHead());
        expectFailure(new Checked() {
        public void run() throws Exception {
            adapter.validate(context(type, "SIGN", "A"), InventoryOperationCommand.purchaseIn(
                    new PurchaseInVO[] { purchase("A", "G", "O", "PA") }, null));
        }});
        database.put("A", purchase("A", "G", "O", "PA", null).getHead());
        expectFailure(new Checked() {
        public void run() throws Exception {
            adapter.validate(context(type, "SIGN", "A"), InventoryOperationCommand.purchaseIn(
                    new PurchaseInVO[] { purchase("A", "G", "O", "PA") }, null));
        }});
        database.put("A", purchase("A", "G", "O", "PA").getHead());
        expectFailure(new Checked() {
        public void run() throws Exception {
            adapter.describeResult(context(type, "SIGN", "A"),
                    new PurchaseInVO[] { purchase("A", "G", "O", "PA", "45-Cxx-08") });
        }});
        final InventoryOperationCommand sign = InventoryOperationCommand.purchaseIn(
                new PurchaseInVO[] { purchase("A", "G", "O", "PA") }, null);
        expectFailure(new Checked() {
        public void run() throws Exception {
            adapter.validate(inventoryRoute("40080802", "Sign", "SIGN", type, "A"), sign);
        }});
        expectFailure(new Checked() {
        public void run() throws Exception {
            adapter.validate(inventoryRoute("40080602", "Delete", "SIGN", type, "A"), sign);
        }});
        expectFailure(new Checked() {
        public void run() throws Exception {
            adapter.validate(inventoryRoute("40080618", "Sign", "SIGN", "IC_TRANS_IN", "A"), sign);
        }});
        expectFailure(new Checked() {
        public void run() throws Exception {
            adapter.validate(inventoryRoute("40080402", "Sign", "SIGN", "IC_INITIAL_IN", "A"), sign);
        }});
        expectFailure(new Checked() {
        public void run() throws Exception {
            adapter.describeResult(inventoryRoute("OTHER", "Sign", "SIGN", type, "A"),
                    new PurchaseInVO[] { purchase("A", "G", "O", "PA") });
        }});
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context(type, "APPROVE", "A"), InventoryOperationCommand.purchaseIn(
                    new PurchaseInVO[] { purchase("A", "G", "O", "PA") }, null));
        }});
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.describeResult(context(type, "SIGN", "A"), new SaleOutVO[0]);
        }});
        List<BusinessObjectRef> refs = adapter.describeResult(context(type, "CANCEL_SIGN", "A", "B"),
                new PurchaseInVO[] { purchase("B", "G", "O", "PB"), purchase("A", "G", "O", "PA") });
        check("A".equals(refs.get(0).getObjectPk()) && "C0".equals(refs.get(0).getClientObjectId()), "Inventory result correlated by PK");
        refs = adapter.describeResult(context(type, "INSERT", (String) null),
                new PurchaseInVO[] { purchase("NEWIC", "G", "O", "IC-001") });
        check("NEWIC".equals(refs.get(0).getObjectPk()) && "IC-001".equals(refs.get(0).getBillCode()), "Inventory insert uses actual returned identity");
        final OperationContext delete = context(type, "DELETE", "A");
        check(adapter.describeResult(delete, null).get(0) == delete.getObjects().get(0), "Inventory void delete preserves authorized identity");
    }

    /** 验证供应商价格动作分组、行下标及单对象限制，非法混合或越权载荷必须失败。 */
    private static void supplierPrices() throws Exception {
        final String type = SupplierPriceOperationCommand.OBJECT_TYPE;
        final Map<String, SupplierPriceVO> database = new HashMap<String, SupplierPriceVO>();
        database.put("A", price("A", "G", "O")); database.put("B", price("B", "G", "O"));
        final SupplierPriceOperationAdapter adapter = new SupplierPriceOperationAdapter() {
            /** 查询现存供应商价格行的实际归属，防止提交载荷伪造组织；测试可覆盖本方法提供替身。 */
            protected SupplierPriceVO loadPersistedPrice(String pk) { return database.get(pk); }
        };
        adapter.validate(context(type, "INSERT", (String) null), new SupplierPriceOperationCommand(batch("INSERT", price(null, "G", "O"))));
        check(true, "Supplier-price single add accepted");
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            BatchOperateVO mixed = batch("INSERT", price(null, "G", "O")); mixed.setDelObjs(new Object[] { price("A", "G", "O") });
            adapter.validate(context(type, "INSERT", (String) null), new SupplierPriceOperationCommand(mixed));
        }});
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context(type, "INSERT", null, null), new SupplierPriceOperationCommand(
                    batch("INSERT", price(null, "G", "O"), price(null, "G", "O"))));
        }});
        database.put("A", price("A", "G", "OTHER"));
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context(type, "DELETE", "A"), new SupplierPriceOperationCommand(batch("DELETE", price("A", "G", "O"))));
        }});
        database.put("A", price("A", "G", "O"));
        adapter.validate(context(type, "UPDATE", "A"), new SupplierPriceOperationCommand(batch("UPDATE", price("A", "G", "O"))));
        check(true, "Supplier-price update verifies stored ownership");
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context(type, "UPDATE", "A", "B"), new SupplierPriceOperationCommand(
                    batch("UPDATE", price("A", "G", "O"), price("B", "G", "O"))));
        }});
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.validate(context(type, "DELETE", "A", "B"), new SupplierPriceOperationCommand(
                    batch("DELETE", price("A", "G", "O"), price("B", "G", "O"))));
        }});
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.describeResult(context(type, "UPDATE", "A", "B"),
                    batch("UPDATE", price("B", "G", "O"), price("A", "G", "O")));
        }});
        List<BusinessObjectRef> refs = adapter.describeResult(context(type, "INSERT", (String) null), batch("INSERT", price("NEWPRICE", "G", "O")));
        check("NEWPRICE".equals(refs.get(0).getObjectPk()) && refs.get(0).getBillCode() == null, "Supplier price does not invent a bill code");
        OperationContext unverifiedCode = new OperationContext("R", "B", "G", "O", "NODE", "Node", "BUTTON", "Button",
                "UPDATE", null, Collections.singletonList(new BusinessObjectRef("C0", type, "A", "UNVERIFIED", null)));
        check(adapter.describeResult(unverifiedCode, batch("UPDATE", price("A", "G", "O"))).get(0).getBillCode() == null,
                "Supplier price never promotes a client-provided code into a saved bill number");
        expectFailure(new Checked() {
        /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
        public void run() throws Exception {
            adapter.describeResult(context(type, "UPDATE", "A"), null);
        }});
        final OperationContext delete = context(type, "DELETE", "A");
        check(adapter.describeResult(delete, new BatchOperateVO()).get(0) == delete.getObjects().get(0), "Price delete preserves authorized identity");
    }

    /** 为离线适配测试构造明确对象类型、动作和主键集合的操作上下文。 */
    private static OperationContext context(String type, String op, String... pks) {
        List<BusinessObjectRef> refs = new ArrayList<BusinessObjectRef>();
        for (int i = 0; i < pks.length; i++) refs.add(new BusinessObjectRef("C" + i, type, pks[i], null, null));
        if (InventoryOperationCommand.PURCHASE_IN.equals(type) || InventoryOperationCommand.SALE_OUT.equals(type)) {
            String node = InventoryOperationCommand.PURCHASE_IN.equals(type) ? "40080602" : "40080802";
            String button = "INSERT".equals(op) || "UPDATE".equals(op) ? "Save"
                    : "DELETE".equals(op) ? "Delete" : "SIGN".equals(op) ? "Sign"
                    : "CANCEL_SIGN".equals(op) ? "CancelSign" : "Unsupported";
            return new OperationContext("R", "B", "G", "O", node, "Inventory", button, "Button", op, null, refs);
        }
        return new OperationContext("R", "B", "G", "O", "NODE", "Node", "BUTTON", "Button", op, null, refs);
    }
    private static OperationContext inventoryRoute(String node, String button, String op, String type, String pk) {
        return new OperationContext("R", "B", "G", "O", node, "Inventory", button, "Button", op, null,
                java.util.Collections.singletonList(new BusinessObjectRef("C0", type, pk, null, null)));
    }
    /** 构造只提供测试需要字段的采购订单 VO，不调用原生元数据或业务服务。 */
    private static OrderVO order(final String pk, final String group, final String org, final String code) {
        final OrderHeaderVO head = new OrderHeaderVO() {
            /** 返回采购订单主键。 */
            public String getPk_order() { return pk; }
            /** 返回NC 集团主键。 */
            public String getPk_group() { return group; }
            /** 返回NC 业务单元主键。 */
            public String getPk_org() { return org; }
            /** 返回单据编码。 */
            public String getVbillcode() { return code; }
        };
        return new OrderVO() {
            /** 返回聚合 VO 构造所需的最小测试元数据替身。 */
            public IBillMeta getMetaData() { return FIXTURE_META; }
            /** 返回测试采购订单表头，避免从原生元数据获取字段。 */
            public OrderHeaderVO getHVO() { return head; }
        };
    }
    /** 构造采购入库 VO 测试数据，固定主键与归属供安全校验断言。 */
    private static PurchaseInVO purchase(final String pk, final String group, final String org, final String code) {
        return purchase(pk, group, org, code, "45");
    }
    private static PurchaseInVO purchase(final String pk, final String group, final String org, final String code,
            final String transactionType) {
        return purchase(pk, group, org, code, transactionType, null);
    }
    private static PurchaseInVO purchase(final String pk, final String group, final String org, final String code,
            final String transactionType, final String transactionTypePk) {
        final PurchaseInHeadVO head = new PurchaseInHeadVO() {
            /** 返回库存单据表头主键。 */
            public String getCgeneralhid() { return pk; }
            /** 返回NC 集团主键。 */
            public String getPk_group() { return group; }
            /** 返回NC 业务单元主键。 */
            public String getPk_org() { return org; }
            /** 返回单据编码。 */
            public String getVbillcode() { return code; }
            public String getVtrantypecode() { return transactionType; }
            public String getCtrantypeid() { return transactionTypePk; }
        };
        return new PurchaseInVO() {
            /** 返回聚合 VO 构造所需的最小测试元数据替身。 */
            public IBillMeta getMetaData() { return FIXTURE_META; }
            /** 返回测试库存单据表头，供对象及归属校验使用。 */
            public PurchaseInHeadVO getHead() { return head; }
        };
    }
    /** 构造供应商价格 VO 测试数据，固定主键与组织归属供安全校验断言。 */
    private static SupplierPriceVO price(final String pk, final String group, final String org) {
        return new SupplierPriceVO() {
            /** 返回供应商价格主键。 */
            public String getPk_supplierprice() { return pk; }
            /** 返回NC 集团主键。 */
            public String getPk_group() { return group; }
            /** 返回NC 业务单元主键。 */
            public String getPk_org() { return org; }
        };
    }
    /** 构造保留原生动作分组和下标信息的供应商价格批处理测试输入。 */
    private static BatchOperateVO batch(String op, SupplierPriceVO... rows) {
        BatchOperateVO batch = new BatchOperateVO();
        if ("INSERT".equals(op)) batch.setAddObjs(rows);
        else if ("UPDATE".equals(op)) batch.setUpdObjs(rows);
        else batch.setDelObjs(rows);
        return batch;
    }
    /** 执行预期非法的测试操作，要求其抛出异常；正常返回反而视为测试失败。 */
    private static void expectFailure(Checked action) throws Exception {
        try { action.run(); } catch (BusinessException expected) { assertions++; return; }
        throw new AssertionError("Expected typed-adapter rejection");
    }
    /** 累计断言并在条件不满足时抛出 AssertionError，使测试失败可被构建发现。 */
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
