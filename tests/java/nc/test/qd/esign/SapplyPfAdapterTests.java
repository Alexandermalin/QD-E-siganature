package nc.test.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nc.impl.ic.qd.esign.InventoryPfSaveAdapter;
import nc.impl.ic.qd.esign.InventoryPfStateAdapter;
import nc.itf.ic.qd.esign.IInventoryPfStateAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
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
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Exercises typed m4455 single-bill routes with native VO subclasses and PF doubles. */
public final class SapplyPfAdapterTests {
    private static final IBillMeta META = new IBillMeta() {
        public Map<String,Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "LOC_ADJUST_TEST"; }
        public IVOMeta getParent() { return null; }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    public static void run() throws Exception {
        SapplyBillVO draft = bill(null, "G", "O", null, null, null, 0);
        SapplyBillVO saved = bill("NEW", "G", "O", "CODE", "4455", null, 0);
        SaveFake insert = new SaveFake(); insert.after = saved.getHead(); insert.result = new ICBillVO[] { saved };
        OperationContext newContext = context("40080801", "IC_SAPPLY", "Save", "INSERT", null);
        InventoryPfSaveResult outcome = (InventoryPfSaveResult) insert.execute(newContext, command("4455", draft, BillOperator.New));
        require(insert.calls == 1 && "WRITE".equals(insert.action) && "4455".equals(insert.type)
                && "NEW".equals(outcome.getPrimaryKey()) && "CODE".equals(outcome.getBillCode())
                && "NEW".equals(insert.describeResult(newContext, outcome).get(0).getObjectPk()),
                "Native save must return and backfill the persisted outbound request identity");

        SapplyBillVO existing = bill("A", "G", "O", "CODE", "4455", null, 0);
        OperationContext update = context("40080801", "IC_SAPPLY", "Save", "UPDATE", "A");
        SaveFake save = new SaveFake(); save.before = existing.getHead(); save.after = existing.getHead();
        save.result = new ICBillVO[] { existing };
        save.execute(update, command("4455", existing, BillOperator.Edit));
        require(save.calls == 1 && "WRITE".equals(save.action), "Update must delegate to native PF WRITE");

        SaveFake bad = new SaveFake(); bad.before = existing.getHead();
        rejectSave(bad, context("40080618", "IC_SAPPLY", "Save", "UPDATE", "A"), command("4455", existing, BillOperator.Edit));
        rejectSave(bad, update, command("4E", existing, BillOperator.Edit));
        rejectSave(bad, update, command("4455", bill("A", "G", "OTHER", "CODE", "4455", null, 0), BillOperator.Edit));
        rejectSave(bad, update, command("4455", new TransInVO() { public IBillMeta getMetaData() { return META; } }, BillOperator.Edit));
        require(bad.calls == 0, "Wrong node, type, organization or VO must not reach PF");
        bad = new SaveFake(); bad.before = existing.getHead(); bad.after = saved.getHead(); bad.result = new ICBillVO[] { saved };
        try { bad.execute(update, command("4455", existing, BillOperator.Edit)); throw new AssertionError("Different result accepted"); }
        catch (BusinessException expected) { }
        require(bad.calls == 1, "A changed persisted identity must be rejected after native PF");

        SapplyBillVO custom = bill("A", "G", "O", "CODE", "4455-TEST", "TYPE", 0);
        save = new SaveFake(); save.before = custom.getHead(); save.after = custom.getHead(); save.result = new ICBillVO[] { custom };
        save.metadata = metadata("40080801", "ic_sapply");
        save.execute(update, command("4455-TEST", custom, BillOperator.Edit));
        require(save.calls == 1 && save.typeChecks >= 3, "Subtype metadata must be verified before and after native PF");
        bad = new SaveFake(); bad.before = custom.getHead(); bad.metadata = metadata("OTHER", "ic_sapply");
        rejectSave(bad, update, command("4455-TEST", custom, BillOperator.Edit));
        require(bad.calls == 0, "Another node's subtype must not reach PF");

        OperationContext deletion = context("40080801", "IC_SAPPLY", "Delete", "DELETE", "A");
        StateFake delete = new StateFake(); delete.before = existing.getHead(); delete.after = null;
        InventoryPfStateResult removed = (InventoryPfStateResult) delete.execute(deletion,
                new InventoryPfStateCommand("4455", existing, null, null, null));
        require(delete.calls == 1 && "DELETE".equals(delete.action) && removed.isDeleted()
                && "A".equals(delete.describeResult(deletion, removed).get(0).getObjectPk()),
                "Native delete must retain the authorized identity");
        StateFake rejected = new StateFake(); rejected.before = existing.getHead();
        try { rejected.validate(context("40080801", "IC_SAPPLY", "Sign", "SIGN", "A"),
                new InventoryPfStateCommand("4455", existing, null, null, null));
            throw new AssertionError("Unscoped location sign accepted"); }
        catch (BusinessException expected) { }
        require(rejected.calls == 0, "Unscoped state action must not reach PF");
        rejected = new StateFake(); rejected.before = existing.getHead(); rejected.after = existing.getHead();
        try { rejected.execute(deletion, new InventoryPfStateCommand("4455", existing, null, null, null));
            throw new AssertionError("Active bill after delete accepted"); }
        catch (BusinessException expected) { }
        require(rejected.calls == 1, "Deletion must be verified after native PF");

        OperationContext commit = context("40080801", "IC_SAPPLY", "Commit", "UPDATE", "A");
        OperationContext uncommit = context("40080801", "IC_SAPPLY", "UnCommit", "UPDATE", "A");
        SapplyBillVO free = bill("A", "G", "O", "CODE", "4455", null, 0, Integer.valueOf(2));
        SapplyBillVO auditing = bill("A", "G", "O", "CODE", "4455", null, 0, Integer.valueOf(5));
        SapplyBillVO audited = bill("A", "G", "O", "CODE", "4455", null, 0, Integer.valueOf(4));
        StateFake submitted = new StateFake(); submitted.before = free.getHead(); submitted.after = auditing.getHead();
        submitted.execute(commit, new InventoryPfStateCommand("4455", free, null, null, null));
        require(submitted.calls == 1 && "SAVE".equals(submitted.action), "Submit must delegate to PF SAVE");
        verifyBatchUnit(commit, free, auditing);
        submitted = new StateFake(); submitted.before = free.getHead(); submitted.after = audited.getHead();
        submitted.execute(commit, new InventoryPfStateCommand("4455", free, null, null, null));
        require(submitted.calls == 1, "Native immediate approval is a valid submit result");
        StateFake withdrawn = new StateFake(); withdrawn.before = auditing.getHead(); withdrawn.after = free.getHead();
        withdrawn.execute(uncommit, new InventoryPfStateCommand("4455", auditing, null, null, null));
        require(withdrawn.calls == 1 && "UNSAVE".equals(withdrawn.action), "Withdraw must delegate to PF UNSAVE");
        rejected = new StateFake(); rejected.before = audited.getHead(); rejected.after = auditing.getHead();
        try { rejected.execute(commit, new InventoryPfStateCommand("4455", audited, null, null, null));
            throw new AssertionError("Invalid submit status accepted"); }
        catch (BusinessException expected) { }
        require(rejected.calls == 0, "Invalid persisted status must be rejected before PF");
        rejected = new StateFake(); rejected.before = free.getHead(); rejected.after = free.getHead();
        try { rejected.execute(commit, new InventoryPfStateCommand("4455", free, null, null, null));
            throw new AssertionError("Unchanged status after submit accepted"); }
        catch (BusinessException expected) { }
        require(rejected.calls == 1, "Invalid post-submit status must trigger rollback");

        WorkflownoteVO note=new WorkflownoteVO();note.setBillid("A");note.setPk_group("G");note.setPk_org("O");note.setCheckman("U");
        InventoryPfStateCommand approvalCommand=new InventoryPfStateCommand("4455",auditing,null,note,null);
        InventoryPfStateCommand unapprovalCommand=new InventoryPfStateCommand("4455",audited,null,null,null);
        OperationContext approval=approvalContext("Approve",approvalCommand);
        OperationContext unapproval=approvalContext("UnApprove",unapprovalCommand);
        java.io.ByteArrayOutputStream approvalBytes=new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream approvalOut=new java.io.ObjectOutputStream(approvalBytes);
        approvalOut.writeObject(approvalCommand);approvalOut.close();
        InventoryPfStateCommand afterWire=(InventoryPfStateCommand)new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(approvalBytes.toByteArray())).readObject();
        require(approval.getObjects().get(0).getClientObjectId().equals(
                nc.vo.ic.qd.esign.SapplyApprovalIntent.clientObjectId("Approve",afterWire)),
                "Approval intent must survive an NC VO serialization round trip");
        StateFake approving=new StateFake();approving.before=auditing.getHead();approving.after=audited.getHead();
        approving.execute(approval,approvalCommand);
        require(approving.calls==1 && "APPROVEU".equals(approving.action),"Approval must retain the native PF actor");
        StateFake unapproving=new StateFake();unapproving.before=audited.getHead();unapproving.after=free.getHead();
        unapproving.execute(unapproval,unapprovalCommand);
        require(unapproving.calls==1 && "UNAPPROVEU".equals(unapproving.action),"Unapproval must retain the native PF actor");
        rejected=new StateFake();rejected.before=auditing.getHead();
        try{rejected.execute(approval,new InventoryPfStateCommand("4455",auditing,null,null,null));
            throw new AssertionError("Approval without confirmation was accepted");}catch(BusinessException expected) { }
        require(rejected.calls==0,"Missing confirmation must be rejected before PF");
        System.out.println("PASS SapplyPfAdapterTests (typed m4455 PF routes; no NC or Oracle)");
    }

    private static void verifyBatchUnit(OperationContext context, SapplyBillVO before, final SapplyBillVO after)
            throws Exception {
        final StateFake adapter = new StateFake();
        adapter.before = before.getHead();
        adapter.after = after.getHead();
        Map<String,Object> parameters = new HashMap<String,Object>();
        parameters.put("batch", "batch");
        parameters.put("worknote", null);
        InventoryPfStateCommand command = new InventoryPfStateCommand("4455", before, null, null, parameters);
        IInventoryPfStateAdapter.NativeCall callback = new IInventoryPfStateAdapter.NativeCall() {
            public Object proceed() {
                adapter.calls++;
                return after;
            }
        };
        try {
            adapter.validate(context, command);
            throw new AssertionError("Single-bill PF accepted batch metadata");
        } catch (BusinessException expected) { }
        parameters.put("silently", "true");
        try {
            adapter.executeSapplyUnit(context, new InventoryPfStateCommand("4455", before, null, null, parameters),
                    "SAVE", callback);
            throw new AssertionError("Batch PF accepted a request to skip native prompts");
        } catch (BusinessException expected) { }
          catch (Throwable unexpected) { throw new AssertionError(unexpected); }
        try {
            adapter.executeSapplyUnit(context, command, "UNSAVE", callback);
            throw new AssertionError("Another native action used the batch authorization");
        } catch (BusinessException expected) { }
          catch (Throwable unexpected) { throw new AssertionError(unexpected); }
        require(adapter.calls == 0, "Invalid batch action must stop before the native invocation");
        try {
            InventoryPfStateResult result = adapter.executeSapplyUnit(context, command, "SAVE", callback);
            require(adapter.calls == 1 && adapter.action == null && result.getNativeResult() == after
                    && "A".equals(result.getPrimaryKey()), "Batch unit must validate and return the current PF invocation");
        } catch (Throwable unexpected) { throw new AssertionError(unexpected); }
    }

    private static void rejectSave(SaveFake adapter, OperationContext context, InventoryPfSaveCommand command) throws Exception {
        try { adapter.validate(context, command); throw new AssertionError("Invalid m4455 save accepted"); }
        catch (BusinessException expected) { }
    }
    private static OperationContext approvalContext(String button,InventoryPfStateCommand command) throws BusinessException {
        return new OperationContext("request","batch","G","O","40080801","outbound request",button,button,"UPDATE",null,
                Collections.singletonList(new BusinessObjectRef(nc.vo.ic.qd.esign.SapplyApprovalIntent.clientObjectId(button,command),"IC_SAPPLY","A",null,null)));
    }
    private static OperationContext context(String node, String type, String button, String op, String pk) {
        return new OperationContext("request", "batch", "G", "O", node, "outbound request", button, button, op, null,
                Collections.singletonList(new BusinessObjectRef("item", type, pk, null, null)));
    }
    private static InventoryPfSaveCommand command(String type, ICBillVO bill, BillOperator operator) {
        ICPFParameter user = new ICPFParameter(); user.setBillaction(operator);
        return new InventoryPfSaveCommand(type, bill, user, null, Collections.<String,Object>emptyMap());
    }
    private static SapplyBillVO bill(final String pk, final String group, final String org, final String code,
            final String type, final String typePk, final int dr) {
        return bill(pk,group,org,code,type,typePk,dr,null);
    }
    private static SapplyBillVO bill(final String pk, final String group, final String org, final String code,
            final String type, final String typePk, final int dr, final Integer flag) {
        final SapplyBillHeadVO head = new SapplyBillHeadVO() {
            public String getCgeneralhid() { return pk; } public String getPk_group() { return group; }
            public String getPk_org() { return org; } public String getVbillcode() { return code; }
            public String getVtrantypecode() { return type; } public String getCtrantypeid() { return typePk; }
            public Integer getFbillflag() { return flag; }
            public IVOMeta getMetaData() { return null; }
            public Object getAttributeValue(String name) { return "dr".equals(name) ? Integer.valueOf(dr) : null; }
        };
        return new SapplyBillVO() { public IBillMeta getMetaData() { return META; } public SapplyBillHeadVO getHead() { return head; } };
    }
    private static BilltypeVO metadata(String node, String component) {
        BilltypeVO row = new BilltypeVO(); row.setPrimaryKey("TYPE"); row.setPk_billtypecode("4455-TEST");
        row.setPk_group("G"); row.setNodecode(node); row.setParentbilltype("4455"); row.setComponent(component);
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
        protected ICBillHeadVO loadSapplyHeader(String pk) { return calls == 0 ? before : after; }
        protected ICBillHeadVO loadHeader(boolean purchase, String pk) { throw new AssertionError("Wrong header route"); }
        protected BilltypeVO loadTransactionType(String pk) { typeChecks++; return metadata; }
        protected IplatFormEntry platformEntry() { return entry(this); }
        public void called(String action, String type) { calls++; this.action = action; this.type = type; }
        public Object result() { return result; }
    }
    private static final class StateFake extends InventoryPfStateAdapter implements Capture {
        ICBillHeadVO before, after; Object result; String action; int calls;
        protected ICBillHeadVO loadSapplyHeader(String pk) { return calls == 0 ? before : after; }
        protected ICBillHeadVO loadHeader(boolean purchase, String pk) { throw new AssertionError("Wrong header route"); }
        protected IplatFormEntry platformEntry() { return entry(this); }
        protected int[] sapplyStatusFlags() { return new int[] { 2, 5, 4, 6 }; }
        protected String currentUserId() { return "U"; }
        public void called(String action, String type) { calls++; this.action = action; }
        public Object result() { return result; }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
