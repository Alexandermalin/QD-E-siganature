package nc.test.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import nc.impl.ic.qd.esign.InventoryPfSaveAdapter;
import nc.impl.ic.qd.esign.InventoryPfStateAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.ic.m4q.entity.LocAdjustHeadVO;
import nc.vo.ic.m4q.entity.LocAdjustVO;
import nc.vo.ic.m4e.entity.TransInVO;
import nc.vo.ic.pub.define.BillOperator;
import nc.vo.ic.pub.pf.ICPFParameter;
import nc.vo.ic.qd.esign.InventoryPfSaveCommand;
import nc.vo.ic.qd.esign.InventoryPfSaveResult;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.InventoryPfStateResult;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.billtype.BilltypeVO;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Exercises typed m4q single-bill routes with native VO subclasses and PF doubles. */
public final class LocAdjustPfAdapterTests {
    private static final IBillMeta META = new IBillMeta() {
        public Map<String,Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "LOC_ADJUST_TEST"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    public static void run() throws Exception {
        LocAdjustVO draft = bill(null, "G", "O", null, null, null, 0);
        LocAdjustVO saved = bill("NEW", "G", "O", "CODE", "4Q", null, 0);
        SaveFake insert = new SaveFake(); insert.after = saved.getHead(); insert.result = new ICBillVO[] { saved };
        OperationContext newContext = context("40081014", "IC_LOC_ADJUST", "Save", "INSERT", null);
        InventoryPfSaveResult outcome = (InventoryPfSaveResult) insert.execute(newContext, command("4Q", draft, BillOperator.New));
        require(insert.calls == 1 && "WRITE".equals(insert.action) && "4Q".equals(insert.type)
                && "NEW".equals(outcome.getPrimaryKey()) && "CODE".equals(outcome.getBillCode())
                && "NEW".equals(insert.describeResult(newContext, outcome).get(0).getObjectPk()),
                "Native save must return and backfill the persisted location adjustment identity");

        LocAdjustVO existing = bill("A", "G", "O", "CODE", "4Q", null, 0);
        OperationContext update = context("40081014", "IC_LOC_ADJUST", "Save", "UPDATE", "A");
        SaveFake save = new SaveFake(); save.before = existing.getHead(); save.after = existing.getHead();
        save.result = new ICBillVO[] { existing };
        save.execute(update, command("4Q", existing, BillOperator.Edit));
        require(save.calls == 1 && "WRITE".equals(save.action), "Update must delegate to native PF WRITE");

        SaveFake bad = new SaveFake(); bad.before = existing.getHead();
        rejectSave(bad, context("40080618", "IC_LOC_ADJUST", "Save", "UPDATE", "A"), command("4Q", existing, BillOperator.Edit));
        rejectSave(bad, update, command("4E", existing, BillOperator.Edit));
        rejectSave(bad, update, command("4Q", bill("A", "G", "OTHER", "CODE", "4Q", null, 0), BillOperator.Edit));
        rejectSave(bad, update, command("4Q", new TransInVO() { public IBillMeta getMetaData() { return META; } }, BillOperator.Edit));
        require(bad.calls == 0, "Wrong node, type, organization or VO must not reach PF");
        bad = new SaveFake(); bad.before = existing.getHead(); bad.after = saved.getHead(); bad.result = new ICBillVO[] { saved };
        try { bad.execute(update, command("4Q", existing, BillOperator.Edit)); throw new AssertionError("Different result accepted"); }
        catch (BusinessException expected) { }
        require(bad.calls == 1, "A changed persisted identity must be rejected after native PF");

        LocAdjustVO custom = bill("A", "G", "O", "CODE", "4Q-TEST", "TYPE", 0);
        save = new SaveFake(); save.before = custom.getHead(); save.after = custom.getHead(); save.result = new ICBillVO[] { custom };
        save.metadata = metadata("40081014", "ic_locadjust");
        save.execute(update, command("4Q-TEST", custom, BillOperator.Edit));
        require(save.calls == 1 && save.typeChecks >= 3, "Subtype metadata must be verified before and after native PF");
        bad = new SaveFake(); bad.before = custom.getHead(); bad.metadata = metadata("OTHER", "ic_locadjust");
        rejectSave(bad, update, command("4Q-TEST", custom, BillOperator.Edit));
        require(bad.calls == 0, "Another node's subtype must not reach PF");

        OperationContext deletion = context("40081014", "IC_LOC_ADJUST", "Delete", "DELETE", "A");
        StateFake delete = new StateFake(); delete.before = existing.getHead(); delete.after = null;
        InventoryPfStateResult removed = (InventoryPfStateResult) delete.execute(deletion,
                new InventoryPfStateCommand("4Q", existing, null, null, null));
        require(delete.calls == 1 && "DELETE".equals(delete.action) && removed.isDeleted()
                && "A".equals(delete.describeResult(deletion, removed).get(0).getObjectPk()),
                "Native delete must retain the authorized identity");
        StateFake rejected = new StateFake(); rejected.before = existing.getHead();
        try { rejected.validate(context("40081014", "IC_LOC_ADJUST", "Sign", "SIGN", "A"),
                new InventoryPfStateCommand("4Q", existing, null, null, null));
            throw new AssertionError("Unscoped location sign accepted"); }
        catch (BusinessException expected) { }
        require(rejected.calls == 0, "Unscoped state action must not reach PF");
        rejected = new StateFake(); rejected.before = existing.getHead(); rejected.after = existing.getHead();
        try { rejected.execute(deletion, new InventoryPfStateCommand("4Q", existing, null, null, null));
            throw new AssertionError("Active bill after delete accepted"); }
        catch (BusinessException expected) { }
        require(rejected.calls == 1, "Deletion must be verified after native PF");
        System.out.println("PASS LocAdjustPfAdapterTests (typed m4q PF routes; no NC or Oracle)");
    }

    private static void rejectSave(SaveFake adapter, OperationContext context, InventoryPfSaveCommand command) throws Exception {
        try { adapter.validate(context, command); throw new AssertionError("Invalid m4q save accepted"); }
        catch (BusinessException expected) { }
    }
    private static OperationContext context(String node, String type, String button, String op, String pk) {
        return new OperationContext("request", "batch", "G", "O", node, "location", button, button, op, null,
                Collections.singletonList(new BusinessObjectRef("item", type, pk, null, null)));
    }
    private static InventoryPfSaveCommand command(String type, ICBillVO bill, BillOperator operator) {
        ICPFParameter user = new ICPFParameter(); user.setBillaction(operator);
        return new InventoryPfSaveCommand(type, bill, user, null, Collections.<String,Object>emptyMap());
    }
    private static LocAdjustVO bill(final String pk, final String group, final String org, final String code,
            final String type, final String typePk, final int dr) {
        final LocAdjustHeadVO head = new LocAdjustHeadVO() {
            public String getCgeneralhid() { return pk; } public String getPk_group() { return group; }
            public String getPk_org() { return org; } public String getVbillcode() { return code; }
            public String getVtrantypecode() { return type; } public String getCtrantypeid() { return typePk; }
            public Object getAttributeValue(String name) { return "dr".equals(name) ? Integer.valueOf(dr) : null; }
        };
        return new LocAdjustVO() { public IBillMeta getMetaData() { return META; } public LocAdjustHeadVO getHead() { return head; } };
    }
    private static BilltypeVO metadata(String node, String component) {
        BilltypeVO row = new BilltypeVO(); row.setPrimaryKey("TYPE"); row.setPk_billtypecode("4Q-TEST");
        row.setPk_group("G"); row.setNodecode(node); row.setParentbilltype("4Q"); row.setComponent(component);
        row.setDr(Integer.valueOf(0)); return row;
    }
    private interface Capture { void called(String action, String type); Object result(); }
    private static IplatFormEntry entry(final Capture owner) {
        return (IplatFormEntry) Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(), new Class<?>[] { IplatFormEntry.class },
                new InvocationHandler() { public Object invoke(Object proxy, Method method, Object[] args) {
                    if (!"processAction".equals(method.getName())) throw new AssertionError(method.getName());
                    owner.called((String) args[0], (String) args[1]); return owner.result();
                }});
    }
    private static final class SaveFake extends InventoryPfSaveAdapter implements Capture {
        ICBillHeadVO before, after; Object result; String action, type; int calls, typeChecks; BilltypeVO metadata;
        protected ICBillHeadVO loadLocAdjustHeader(String pk) { return calls == 0 ? before : after; }
        protected ICBillHeadVO loadHeader(boolean purchase, String pk) { throw new AssertionError("Wrong header route"); }
        protected BilltypeVO loadTransactionType(String pk) { typeChecks++; return metadata; }
        protected IplatFormEntry platformEntry() { return entry(this); }
        public void called(String action, String type) { calls++; this.action = action; this.type = type; }
        public Object result() { return result; }
    }
    private static final class StateFake extends InventoryPfStateAdapter implements Capture {
        ICBillHeadVO before, after; Object result; String action; int calls;
        protected ICBillHeadVO loadLocAdjustHeader(String pk) { return calls == 0 ? before : after; }
        protected ICBillHeadVO loadHeader(boolean purchase, String pk) { throw new AssertionError("Wrong header route"); }
        protected IplatFormEntry platformEntry() { return entry(this); }
        public void called(String action, String type) { calls++; this.action = action; }
        public Object result() { return result; }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
