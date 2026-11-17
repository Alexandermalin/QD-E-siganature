package nc.test.qd.esign;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nc.impl.ic.qd.esign.InventoryPfSaveAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.ic.m45.entity.PurchaseInHeadVO;
import nc.vo.ic.m45.entity.PurchaseInVO;
import nc.vo.ic.m4c.entity.SaleOutHeadVO;
import nc.vo.ic.m4c.entity.SaleOutVO;
import nc.vo.ic.pub.define.BillOperator;
import nc.vo.ic.pub.pf.ICPFParameter;
import nc.vo.ic.qd.esign.InventoryOperationCommand;
import nc.vo.ic.qd.esign.InventoryPfSaveCommand;
import nc.vo.ic.qd.esign.InventoryPfSaveResult;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Offline PF delegation and authorization checks using native VO subclasses. */
public final class InventoryPfSaveAdapterTests {
    private static final IBillMeta META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "INVENTORY_PF_TEST"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    public static void run() throws Exception {
        for (boolean purchase : new boolean[] { true, false }) {
            String type = purchase ? InventoryOperationCommand.PURCHASE_IN : InventoryOperationCommand.SALE_OUT;
            String node = purchase ? "40080602" : "40080802";
            String billType = purchase ? "45" : "4C";
            ICBillVO submitted = bill(purchase, null, "G", "O", null, billType);
            ICBillVO saved = bill(purchase, "NEW", "G", "O", "BILL-1", billType);
            InventoryPfSaveCommand command = command(billType, submitted, BillOperator.New);
            OperationContext context = context(node, type, "INSERT", null);
            Fake adapter = new Fake();
            adapter.result = new ICBillVO[] { saved };
            adapter.after = saved.getHead();
            InventoryPfSaveResult result = (InventoryPfSaveResult) adapter.execute(context, command);
            require(adapter.calls == 1 && "WRITE".equals(adapter.action) && billType.equals(adapter.billType),
                    "PF must execute once with the native action and bill type");
            require(adapter.bill == submitted && adapter.userObject == command.getUserObject()
                    && "value".equals(adapter.parameters.get("native")), "Native PF context was changed");
            require("NEW".equals(result.getPrimaryKey()) && "BILL-1".equals(result.getBillCode()),
                    "New identity must come from the native result and persisted bill");
            require("NEW".equals(adapter.describeResult(context, result).get(0).getObjectPk()),
                    "Saved identity must be attached to the signed object");

            Fake wrapped = new Fake();
            wrapped.result = PFReturnObject.createReturnObjs(new ICBillVO[] { saved }, null);
            wrapped.after = saved.getHead();
            wrapped.execute(context, command);
            require(wrapped.calls == 1, "PF return wrapper must be accepted");

            ICBillVO existing = bill(purchase, "A", "G", "O", "BILL-1", billType);
            OperationContext update = context(node, type, "UPDATE", "A");
            InventoryPfSaveCommand updateCommand = command(billType, existing, BillOperator.Edit);
            Fake updateAdapter = new Fake();
            updateAdapter.before = existing.getHead();
            updateAdapter.after = existing.getHead();
            updateAdapter.result = new ICBillVO[] { existing };
            updateAdapter.execute(update, updateCommand);
            require(updateAdapter.calls == 1, "Existing bill must use PF once");
            try {
                updateAdapter.describeResult(update, new InventoryPfSaveResult(existing, "OTHER", "BILL-1"));
                throw new AssertionError("A result for another inventory bill was accepted");
            } catch (BusinessException expected) { }

            Fake invalid = new Fake();
            reject(invalid, context(node, type, "DELETE", "A"), updateCommand);
            reject(invalid, context("OTHER", type, "INSERT", null), command);
            reject(invalid, context(node, type, "INSERT", "A"), command);
            BusinessObjectRef targeted = new BusinessObjectRef("draft", type, null, null, null, null, null, "OTHER");
            reject(invalid, new OperationContext("request", "batch", "G", "O", node, "inventory", "Save", "save",
                    "INSERT", null, Collections.singletonList(targeted)), command);
            reject(invalid, context, command(billType, submitted, BillOperator.Edit));
            reject(invalid, context, command("OTHER", submitted, BillOperator.New));
            reject(invalid, context, command(" " + billType, submitted, BillOperator.New));
            String customType = purchase ? "45-Cxx-08" : "4C-CUSTOM";
            reject(invalid, update, command(customType, bill(purchase, "A", "G", "O", "BILL-1", customType), BillOperator.Edit));
            reject(invalid, context, command(billType, bill(purchase, null, "OTHER", "O", null, billType), BillOperator.New));
            reject(invalid, context, command(billType, bill(purchase, null, "G", "OTHER", null, billType), BillOperator.New));
            reject(invalid, update, command(billType, bill(purchase, "B", "G", "O", "BILL-1", billType), BillOperator.Edit));

            invalid.before = existing.getHead();
            reject(invalid, context, command(billType, existing, BillOperator.New));
            invalid.before = bill(purchase, "A", "G", "OTHER", "BILL-1", billType).getHead();
            reject(invalid, update, updateCommand);
            require(invalid.calls == 0, "Invalid payload must never reach PF");

            Fake wrongResult = new Fake();
            wrongResult.result = new ICBillVO[] { bill(!purchase, "NEW", "G", "O", "BILL-1", purchase ? "4C" : "45") };
            wrongResult.after = saved.getHead();
            rejectExecution(wrongResult, context, command);
            wrongResult = new Fake();
            wrongResult.result = new ICBillVO[] { saved, saved };
            wrongResult.after = saved.getHead();
            rejectExecution(wrongResult, context, command);
            wrongResult = new Fake();
            wrongResult.result = new ICBillVO[] { saved };
            wrongResult.after = bill(purchase, "NEW", "G", "OTHER", "BILL-1", billType).getHead();
            rejectExecution(wrongResult, context, command);
            wrongResult = new Fake();
            wrongResult.before = existing.getHead();
            wrongResult.result = new ICBillVO[] { saved };
            wrongResult.after = saved.getHead();
            rejectExecution(wrongResult, update, updateCommand);
        }
        System.out.println("PASS InventoryPfSaveAdapterTests (PF delegation and authorization; no NC or Oracle)");
    }

    private static void reject(Fake adapter, OperationContext context, InventoryPfSaveCommand command) throws Exception {
        try { adapter.validate(context, command); throw new AssertionError("Invalid inventory PF command accepted"); }
        catch (BusinessException expected) { }
    }
    private static void rejectExecution(Fake adapter, OperationContext context, InventoryPfSaveCommand command) throws Exception {
        try { adapter.execute(context, command); throw new AssertionError("Invalid inventory PF result accepted"); }
        catch (BusinessException expected) { }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static OperationContext context(String node, String type, String operation, String pk) {
        BusinessObjectRef ref = new BusinessObjectRef("draft", type, pk, null, null);
        return new OperationContext("request", "batch", "G", "O", node, "inventory", "Save", "save", operation,
                null, Collections.singletonList(ref));
    }
    private static InventoryPfSaveCommand command(String billType, ICBillVO bill, BillOperator action) {
        ICPFParameter user = new ICPFParameter();
        user.setBillaction(action);
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("native", "value");
        return new InventoryPfSaveCommand(billType, bill, user, null, parameters);
    }
    private static ICBillVO bill(boolean purchase, final String pk, final String group, final String org,
            final String code, final String billType) {
        if (purchase) {
            final PurchaseInHeadVO head = new PurchaseInHeadVO() {
                public String getCgeneralhid() { return pk; }
                public String getPk_group() { return group; }
                public String getPk_org() { return org; }
                public String getVbillcode() { return code; }
                public String getVtrantypecode() { return billType; }
                public String getCtrantypeid() { return null; }
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
            public String getVtrantypecode() { return billType; }
            public String getCtrantypeid() { return null; }
        };
        return new SaleOutVO() {
            public IBillMeta getMetaData() { return META; }
            public SaleOutHeadVO getHead() { return head; }
        };
    }
    private static final class Fake extends InventoryPfSaveAdapter {
        ICBillHeadVO before, after;
        Object result, bill, userObject;
        String action, billType;
        Map<?, ?> parameters;
        int calls;
        protected ICBillHeadVO loadHeader(boolean purchase, String pk) { return calls == 0 ? before : after; }
        protected IplatFormEntry platformEntry() {
            return (IplatFormEntry) Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                    new Class<?>[] { IplatFormEntry.class }, new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            if (!"processAction".equals(method.getName())) throw new AssertionError(method.getName());
                            calls++;
                            action = (String) args[0]; billType = (String) args[1]; bill = args[3];
                            userObject = args[4]; parameters = (Map<?, ?>) args[5];
                            return result;
                        }
                    });
        }
    }
}
