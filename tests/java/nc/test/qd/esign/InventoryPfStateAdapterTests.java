package nc.test.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import nc.impl.ic.qd.esign.InventoryPfStateAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.ic.m45.entity.PurchaseInHeadVO;
import nc.vo.ic.m45.entity.PurchaseInVO;
import nc.vo.ic.m4c.entity.SaleOutHeadVO;
import nc.vo.ic.m4c.entity.SaleOutVO;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.InventoryPfStateResult;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Offline tests of the candidate PF state adapter; no NC transaction or database is invoked. */
public final class InventoryPfStateAdapterTests {
    private static final IBillMeta META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "INVENTORY_STATE_TEST"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    public static void run() throws Exception {
        for (boolean purchase : new boolean[] { true, false }) {
            for (String action : new String[] { "Delete", "Sign", "CancelSign" }) {
                OperationContext context = context(purchase, action, "A");
                ICBillVO bill = bill(purchase, "A", "G", "O", "CODE", 2, 0);
                InventoryPfStateCommand command = new InventoryPfStateCommand(purchase ? "45" : "4C", bill,
                        null, null, Collections.<String, Object>emptyMap());
                Fake adapter = new Fake();
                adapter.before = bill.getHead();
                adapter.after = "Delete".equals(action) ? null
                        : bill(purchase, "A", "G", "O", "CODE", "Sign".equals(action) ? 3 : 2, 0).getHead();
                adapter.raw = "Delete".equals(action) ? null : new ICBillVO[] { bill };
                InventoryPfStateResult result = (InventoryPfStateResult) adapter.execute(context, command);
                String nativeAction = "CancelSign".equals(action) ? "CANCELSIGN" : action.toUpperCase();
                require(adapter.calls == 1 && nativeAction.equals(adapter.action) && adapter.bill == bill,
                        "The original PF action and bill must be passed once");
                require("A".equals(result.getPrimaryKey()) && "CODE".equals(result.getBillCode())
                        && result.isDeleted() == "Delete".equals(action), "PF result identity is incorrect");
                require("C0".equals(adapter.describeResult(context, result).get(0).getClientObjectId()),
                        "Result must preserve the authorized object identifier");

                Fake bad = new Fake(); bad.before = bill.getHead(); bad.after = adapter.after;
                reject(bad, context(!purchase, action, "A"), command);
                reject(bad, context(purchase, action, "B"), command);
                reject(bad, withButton(context, "Save"), command);
                require(bad.calls == 0, "Invalid route or identity reached PF");

                bad = new Fake(); bad.before = bill.getHead(); bad.after = bill.getHead();
                if ("Delete".equals(action) || "Sign".equals(action)) rejectExecution(bad, context, command);
                if ("Delete".equals(action)) {
                    bad = new Fake(); bad.before = bill.getHead();
                    bad.after = bill(purchase, "A", "G", "O", "CODE", 2, 2).getHead();
                    rejectExecution(bad, context, command);
                    bad = new Fake(); bad.before = bill.getHead();
                    bad.after = bill(purchase, "A", "G", "OTHER", "CODE", 2, 1).getHead();
                    rejectExecution(bad, context, command);
                }
                bad = new Fake(); bad.before = bill.getHead();
                bad.after = bill(purchase, "A", "G", "OTHER", "CODE", "Sign".equals(action) ? 3 : 2, 0).getHead();
                if (!"Delete".equals(action)) rejectExecution(bad, context, command);
                bad = new Fake(); bad.before = bill.getHead(); bad.after = adapter.after;
                bad.raw = new ICBillVO[] { bill(purchase, "OTHER", "G", "O", "CODE", 2, 0) };
                rejectExecution(bad, context, command);
                bad = new Fake(); bad.before = bill.getHead(); bad.after = adapter.after;
                bad.raw = new ICBillVO[] { bill(purchase, "A", "OTHER", "O", "CODE", 2, 0) };
                rejectExecution(bad, context, command);
                try {
                    adapter.describeResult(context, new InventoryPfStateResult(null, "OTHER", "CODE", "Delete".equals(action)));
                    throw new AssertionError("A result for another bill was accepted");
                } catch (BusinessException expected) { }
            }
            ICBillVO wrappedBill = bill(purchase, "A", "G", "O", "CODE", 3, 0);
            Fake wrapped = new Fake(); wrapped.before = wrappedBill.getHead(); wrapped.after = wrappedBill.getHead();
            wrapped.raw = PFReturnObject.createReturnObjs(new ICBillVO[] { wrappedBill }, null);
            wrapped.execute(context(purchase, "Sign", "A"), new InventoryPfStateCommand(purchase ? "45" : "4C",
                    wrappedBill, null, null, null));
            require(wrapped.calls == 1, "A native PF wrapper should be accepted");

            ICBillVO submitted = bill(purchase, "A", "G", "O", "CLIENT-CODE", 2, 0);
            Fake deletion = new Fake();
            deletion.before = bill(purchase, "A", "G", "O", "STORED-CODE", 2, 0).getHead();
            OperationContext deleting = context(purchase, "Delete", "A");
            InventoryPfStateResult deleted = (InventoryPfStateResult) deletion.execute(deleting,
                    new InventoryPfStateCommand(purchase ? "45" : "4C", submitted, null, null, null));
            require("STORED-CODE".equals(deleted.getBillCode())
                    && "STORED-CODE".equals(deletion.describeResult(deleting, deleted).get(0).getBillCode()),
                    "Deletion must preserve the server-read bill number, not a client-supplied value");
            Fake missingCode = new Fake();
            missingCode.before = bill(purchase, "A", "G", "O", null, 2, 0).getHead();
            try { missingCode.execute(deleting, new InventoryPfStateCommand(purchase ? "45" : "4C", submitted, null, null, null));
                throw new AssertionError("Deletion with an unknown persisted number was accepted"); }
            catch (BusinessException expected) { }
            require(missingCode.calls == 0, "Missing persisted bill number must fail before native deletion");
        }
        System.out.println("PASS InventoryPfStateAdapterTests (single native PF candidate; no NC or Oracle)");
    }

    private static void reject(Fake adapter, OperationContext context, InventoryPfStateCommand command) throws Exception {
        try { adapter.validate(context, command); throw new AssertionError("Invalid inventory state command accepted"); }
        catch (BusinessException expected) { }
    }
    private static void rejectExecution(Fake adapter, OperationContext context, InventoryPfStateCommand command) throws Exception {
        try { adapter.execute(context, command); throw new AssertionError("Invalid inventory state result accepted"); }
        catch (BusinessException expected) { }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static OperationContext withButton(OperationContext original, String button) {
        return new OperationContext("R", "B", "G", "O", original.getNodeCode(), "inventory", button, button,
                original.getOperationType(), null, original.getObjects());
    }
    private static OperationContext context(boolean purchase, String action, String pk) {
        String op = "Delete".equals(action) ? "DELETE" : "Sign".equals(action) ? "SIGN" : "CANCEL_SIGN";
        return new OperationContext("R", "B", "G", "O", purchase ? "40080602" : "40080802", "inventory",
                action, action, op, null, Collections.singletonList(new BusinessObjectRef("C0",
                        purchase ? "IC_PURCHASE_IN" : "IC_SALE_OUT", pk, "CODE", null)));
    }
    private static ICBillVO bill(boolean purchase, final String pk, final String group, final String org,
            final String code, final int flag, final int dr) {
        if (purchase) {
            final PurchaseInHeadVO head = new PurchaseInHeadVO() {
                public String getCgeneralhid() { return pk; }
                public String getPk_group() { return group; }
                public String getPk_org() { return org; }
                public String getVbillcode() { return code; }
                public String getVtrantypecode() { return "45"; }
                public String getCtrantypeid() { return null; }
                public Integer getFbillflag() { return Integer.valueOf(flag); }
                public Object getAttributeValue(String name) { return "dr".equals(name) ? Integer.valueOf(dr) : null; }
            };
            return new PurchaseInVO() {
                public IBillMeta getMetaData() { return META; }
                public PurchaseInHeadVO getHead() { return head; }
            };
        }
        final SaleOutHeadVO head = new SaleOutHeadVO() {
            public String getCgeneralhid() { return pk; }
            public String getPk_group() { return group; }
            public String getPk_org() { return org; }
            public String getVbillcode() { return code; }
            public String getVtrantypecode() { return "4C"; }
            public String getCtrantypeid() { return null; }
            public Integer getFbillflag() { return Integer.valueOf(flag); }
            public Object getAttributeValue(String name) { return "dr".equals(name) ? Integer.valueOf(dr) : null; }
        };
        return new SaleOutVO() {
            public IBillMeta getMetaData() { return META; }
            public SaleOutHeadVO getHead() { return head; }
        };
    }
    private static final class Fake extends InventoryPfStateAdapter {
        ICBillHeadVO before, after;
        Object raw, bill;
        String action;
        int calls;
        protected int expectedStatus(boolean sign) { return sign ? 3 : 2; }
        protected ICBillHeadVO loadHeader(boolean purchase, String pk) { return calls == 0 ? before : after; }
        protected IplatFormEntry platformEntry() {
            return (IplatFormEntry) Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                    new Class<?>[] { IplatFormEntry.class }, new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            if (!"processAction".equals(method.getName())) throw new AssertionError(method.getName());
                            calls++; action = (String) args[0]; bill = args[3]; return raw;
                        }
                    });
        }
    }
}
