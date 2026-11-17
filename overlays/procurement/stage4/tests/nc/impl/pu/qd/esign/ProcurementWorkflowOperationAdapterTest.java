package nc.impl.pu.qd.esign;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import nc.bs.framework.common.InvocationInfoProxy;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pu.m21.context.OrderContext;
import nc.vo.pu.m20.entity.PraybillHeaderVO;
import nc.vo.pu.m20.entity.PraybillVO;
import nc.vo.pu.m20.entity.PraybillVOMeta;
import nc.vo.pu.m25.entity.InvoiceHeaderVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pu.m25.entity.InvoiceVOMeta;
import nc.vo.pu.m25.enumeration.InvoiceBillAction;
import nc.vo.pu.m25.enumeration.InvoiceUserAnswerType;
import nc.vo.pu.m25.env.InvoiceUIToBSEnv;
import nc.vo.pu.m422x.entity.StoreReqAppHeaderVO;
import nc.vo.pu.m422x.entity.StoreReqAppVO;
import nc.vo.pu.m422x.entity.StoreReqAppVOMeta;
import nc.vo.pu.qd.esign.ProcurementWorkflowCommand;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pu.m21.entity.OrderHeaderVO;
import nc.vo.pu.m21.entity.OrderVOMeta;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.IVOMetaStatisticInfo;
import nc.vo.pub.JavaType;
import nc.vo.pub.SuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.pub.pf.WfTaskInfo;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.pubapp.pattern.model.meta.entity.bill.BillMetaFactory;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.wfengine.pub.WFTask;
import nc.vo.wfengine.pub.WfTaskOrInstanceStatus;

public final class ProcurementWorkflowOperationAdapterTest {
    private static final String GROUP = "1001A100000000000001";
    private static final String ORG = "1001A100000000000002";
    private static final String PK = "1001A100000000000003";
    private static final String OTHER = "1001A100000000000004";
    private static final String WORK_ITEM = "1001A100000000000005";
    private static final String TASK = "1001A100000000000006";
    private static final String INSTANCE = "1001A100000000000007";
    private static final BillKind STORE = new BillKind("40040000", "422X", "PU_STORE_REQ", "pk_storereq",
            StoreReqAppVO.class, StoreReqAppHeaderVO.class, StoreReqAppVOMeta.class);
    private static final BillKind PRAY = new BillKind("40040200", "20", "PU_PRAYBILL", "pk_praybill",
            PraybillVO.class, PraybillHeaderVO.class, PraybillVOMeta.class);
    private static final BillKind ORDER = new BillKind("40040400", "21", "PU_PURCHASE_ORDER", "pk_order",
            OrderVO.class, OrderHeaderVO.class, OrderVOMeta.class);
    private static final BillKind INVOICE = new BillKind("40041000", "25", "PU_PURCHASE_INVOICE", "pk_invoice",
            InvoiceVO.class, InvoiceHeaderVO.class, InvoiceVOMeta.class);

    public static void main(String[] args) throws Exception {
        for (BillKind kind : Arrays.asList(STORE, PRAY, ORDER, INVOICE)) {
            runs(kind, "Commit", "COMMIT", "SAVE", 0, 1);
            runs(kind, "UnCommit", "UNCOMMIT", "UNSAVEBILL", 1, 0);
            runs(kind, "Approve", "APPROVE", "APPROVE", 2, 3);
            runs(kind, "UnApprove", "UNAPPROVE", "UNAPPROVE", 3, 0);
        }
        rejectsWrongActionOrNoteBeforeDispatch();
        rejectsUnexpectedNativeResultOrUncommittedState();
        acceptsNativeUpdatedMarkerOnlyForUnchangedValues();
        acceptsNativeInvoiceConfirmationOnApproval();
        preservesOrderResumeContextAndResultWrapper();
        acceptsNewCommitWorkflowNote();
        rejectsUnboundWorkflowTaskBeforeDispatch();
        bindsDelegatedApprovalToCurrentWorkItemAndInstance();
        propagatesNativeException();
    }

    private static void runs(BillKind kind, String button, String operation, String action, int before, int after)
            throws Exception {
        SuperVO persistedBefore = kind.header(PK, ORG, before);
        AbstractBill selected = kind.bill(persistedBefore);
        OperationContext context = ProcurementWorkflowCommand.draft(kind.node, button, GROUP, selected);
        check(operation.equals(context.getOperationType()), "Wrong route for " + button);
        check(kind.objectType.equals(context.getObjects().get(0).getObjectType()), "Wrong object type for " + kind.node);
        SuperVO persistedAfter = kind.header(PK, ORG, after);
        Serializable payload = kind.result(kind.header(PK, ORG, after));
        Fixture fixture = new Fixture(kind, persistedBefore, persistedAfter, payload);
        WorkflownoteVO note = new WorkflownoteVO();
        note.setBillid(PK);
        note.setPk_group(GROUP);
        note.setPk_org(ORG);
        note.setPk_billtype(kind.type);
        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("notechecked", "notechecked");
        PfUserObject[] user = { new PfUserObject() };
        ProcurementWorkflowCommand command = new ProcurementWorkflowCommand(action, kind.type, note, selected, user, params);
        Serializable result = fixture.adapter.execute(context, command);
        check(result == payload, "PF return must reach native client unchanged");
        check(Arrays.equals(fixture.arguments, new Object[] {action, kind.type, note, selected, user, params}),
                "Native PF argument sequence must be retained");
        BusinessObjectRef ref = fixture.adapter.describeResult(context, result).get(0);
        check(PK.equals(ref.getObjectPk()) && "PERSISTED-NO".equals(ref.getBillCode()), "Expected persisted audit object");
    }

    private static void rejectsWrongActionOrNoteBeforeDispatch() throws Exception {
        SuperVO header = ORDER.header(PK, ORG, 2);
        AbstractBill bill = ORDER.bill(header);
        OperationContext context = ProcurementWorkflowCommand.draft(ORDER.node, "Approve", GROUP, bill);
        Fixture fixture = new Fixture(ORDER, header, null, null);
        rejects(fixture, context, new ProcurementWorkflowCommand("SAVE", ORDER.type, null, bill, null, null), 0);
        rejects(fixture, context, new ProcurementWorkflowCommand("APPROVE" + OTHER, ORDER.type, null, bill, null, null), 0);
        WorkflownoteVO note = new WorkflownoteVO();
        note.setBillid(OTHER);
        rejects(fixture, context, new ProcurementWorkflowCommand("APPROVE", ORDER.type, note, bill, null, null), 0);
        HashMap<String, Object> unsafe = new HashMap<String, Object>();
        unsafe.put("nolockandconsist", Boolean.TRUE);
        rejects(fixture, context, new ProcurementWorkflowCommand("APPROVE", ORDER.type, null, bill, null, unsafe), 0);
        header = ORDER.header(OTHER, ORG, 2);
        rejects(fixture, context, new ProcurementWorkflowCommand("APPROVE", ORDER.type, null, ORDER.bill(header), null, null), 0);
    }

    private static void rejectsUnexpectedNativeResultOrUncommittedState() throws Exception {
        SuperVO before = STORE.header(PK, ORG, 0);
        AbstractBill bill = STORE.bill(before);
        OperationContext context = ProcurementWorkflowCommand.draft(STORE.node, "Commit", GROUP, bill);
        ProcurementWorkflowCommand command = new ProcurementWorkflowCommand("SAVE", STORE.type, null, bill, null, null);
        rejects(new Fixture(STORE, before, STORE.header(PK, ORG, 1), null), context, command, 1);
        rejects(new Fixture(STORE, before, STORE.header(PK, ORG, 1), STORE.result(STORE.header(OTHER, ORG, 1))),
                context, command, 1);
        rejects(new Fixture(STORE, before, before, STORE.result(STORE.header(PK, ORG, 1))), context, command, 1);
    }

    private static void acceptsNativeUpdatedMarkerOnlyForUnchangedValues() throws Exception {
        SuperVO before = ORDER.header(PK, ORG, 2);
        SuperVO submitted = ORDER.header(PK, ORG, 2);
        SuperVO after = ORDER.header(PK, ORG, 3);
        submitted.setStatus(VOStatus.UPDATED);
        AbstractBill bill = ORDER.bill(submitted);
        OperationContext context = ProcurementWorkflowCommand.draft(ORDER.node, "Approve", GROUP, bill);
        ProcurementWorkflowCommand command = new ProcurementWorkflowCommand("APPROVE", ORDER.type, null, bill, null, null);
        Serializable payload = ORDER.result(after);
        Fixture fixture = new Fixture(ORDER, before, after, payload);
        check(fixture.adapter.execute(context, command) == payload, "Stock light VO's UPDATED marker is permitted");
        submitted.setAttributeValue("vbillcode", "CHANGED");
        rejects(new Fixture(ORDER, before, after, payload), context, command, 0);
        submitted.setAttributeValue("vbillcode", "PERSISTED-NO");
        submitted.setStatus(VOStatus.DELETED);
        rejects(new Fixture(ORDER, before, after, payload), context, command, 0);
    }

    private static void acceptsNativeInvoiceConfirmationOnApproval() throws Exception {
        SuperVO before = INVOICE.header(PK, ORG, 2);
        SuperVO after = INVOICE.header(PK, ORG, 3);
        AbstractBill bill = INVOICE.bill(before);
        OperationContext context = ProcurementWorkflowCommand.draft(INVOICE.node, "Approve", GROUP, bill);
        Fixture fixture = new Fixture(INVOICE, before, after, INVOICE.result(after));
        InvoiceUIToBSEnv environment = new InvoiceUIToBSEnv();
        environment.setApproveFee(InvoiceUserAnswerType.NO_ANSWER);
        environment.setTrigger(InvoiceBillAction.APPROVE);
        PfUserObject user = new PfUserObject();
        user.setUserObject(environment);
        fixture.adapter.execute(context, new ProcurementWorkflowCommand("APPROVE", INVOICE.type, null, bill,
                new PfUserObject[] {user}, null));
        check(fixture.calls == 1, "Invoice must receive its native approval confirmation");
    }

    private static void propagatesNativeException() throws Exception {
        SuperVO before = PRAY.header(PK, ORG, 2);
        AbstractBill bill = PRAY.bill(before);
        OperationContext context = ProcurementWorkflowCommand.draft(PRAY.node, "Approve", GROUP, bill);
        BusinessException failure = new BusinessException("native");
        Fixture fixture = new Fixture(PRAY, before, before, failure);
        try {
            fixture.adapter.execute(context, new ProcurementWorkflowCommand("APPROVE", PRAY.type, null, bill, null, null));
            throw new AssertionError("Expected native rejection");
        } catch (BusinessException expected) {
            check(failure == expected, "PF failure must keep its native type and payload");
        }
    }

    private static void preservesOrderResumeContextAndResultWrapper() throws Exception {
        SuperVO before = ORDER.header(PK, ORG, 0);
        SuperVO after = ORDER.header(PK, ORG, 2);
        AbstractBill bill = ORDER.bill(before);
        OperationContext context = ProcurementWorkflowCommand.draft(ORDER.node, "Commit", GROUP, bill);
        PFReturnObject[] nativeResult = PFReturnObject.createReturnObjs(new AbstractBill[] {ORDER.bill(after)}, "hint");
        Fixture fixture = new Fixture(ORDER, before, after, nativeResult);
        OrderContext confirmation = new OrderContext();
        confirmation.setMaxPriceConfirm(UFBoolean.TRUE);
        PfUserObject user = new PfUserObject();
        user.setUserObject(confirmation);
        PfUserObject[] users = {user};
        ProcurementWorkflowCommand command = new ProcurementWorkflowCommand("SAVE", ORDER.type, null, bill, users, null);
        check(fixture.adapter.execute(context, command) == nativeResult, "Native result wrapper must reach the client");
        check(fixture.arguments[4] == users, "Native order confirmation must reach PF");
    }

    private static void rejectsUnboundWorkflowTaskBeforeDispatch() throws Exception {
        InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
        String old = invocation.getUserId();
        invocation.setUserId(OTHER);
        try {
            SuperVO before = ORDER.header(PK, ORG, 2);
            AbstractBill bill = ORDER.bill(before);
            OperationContext context = ProcurementWorkflowCommand.draft(ORDER.node, "Approve", GROUP, bill);
            Fixture fixture = new Fixture(ORDER, before, null, null);
            WorkflownoteVO note = new WorkflownoteVO();
            note.setCheckman(OTHER);
            note.setBillid(PK);
            note.setPk_wf_task(OTHER);
            ProcurementWorkflowCommand command = new ProcurementWorkflowCommand("APPROVE" + OTHER, ORDER.type, note,
                    bill, null, null);
            rejects(fixture, context, command, 0);
        } finally {
            invocation.setUserId(old);
        }
    }

    private static void acceptsNewCommitWorkflowNote() throws Exception {
        SuperVO before = STORE.header(PK, ORG, 0);
        SuperVO after = STORE.header(PK, ORG, 2);
        AbstractBill bill = STORE.bill(before);
        OperationContext context = ProcurementWorkflowCommand.draft(STORE.node, "Commit", GROUP, bill);
        WorkflownoteVO note = new WorkflownoteVO();
        WFTask task = new WFTask();
        task.setBillID(PK);
        task.setWorknoteVO(note);
        note.setTaskInfo(new WfTaskInfo(task));
        Fixture fixture = new Fixture(STORE, before, after, STORE.result(after));
        fixture.adapter.execute(context, new ProcurementWorkflowCommand("SAVE", STORE.type, note, bill, null, null));
        check(fixture.arguments[2] == note, "A new workflow note can refer to its initial task and back to the note");
    }

    private static void bindsDelegatedApprovalToCurrentWorkItemAndInstance() throws Exception {
        InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
        String old = invocation.getUserId();
        invocation.setUserId(OTHER);
        try {
            SuperVO before = ORDER.header(PK, ORG, 2);
            SuperVO after = ORDER.header(PK, ORG, 3);
            AbstractBill bill = ORDER.bill(before);
            OperationContext context = ProcurementWorkflowCommand.draft(ORDER.node, "Approve", GROUP, bill);
            WFTask task = task();
            WorkflownoteVO current = delegatedNote();
            WorkflownoteVO submitted = delegatedNote();
            WFTask submittedTask = task();
            submittedTask.setWorknoteVO(submitted);
            submitted.setTaskInfo(new WfTaskInfo(submittedTask));
            ProcurementWorkflowCommand command = new ProcurementWorkflowCommand("APPROVE" + OTHER, ORDER.type,
                    submitted, bill, null, null);
            Fixture fixture = new Fixture(ORDER, before, after, ORDER.result(after));
            fixture.workItem = current;
            fixture.task = task;
            submittedTask.setWfProcessInstancePK(OTHER);
            rejects(fixture, context, command, 0);
            submittedTask.setWfProcessInstancePK(INSTANCE);
            fixture.adapter.execute(context, command);
            check(fixture.arguments[2] == submitted, "PF must retain the delegated approval note");
        } finally {
            invocation.setUserId(old);
        }
    }

    private static WFTask task() {
        WFTask task = new WFTask();
        task.setTaskPK(TASK);
        task.setWfProcessInstancePK(INSTANCE);
        task.setStatus(WfTaskOrInstanceStatus.Started.getIntValue());
        task.setOperator(OTHER);
        return task;
    }

    private static WorkflownoteVO delegatedNote() {
        WorkflownoteVO note = new WorkflownoteVO();
        note.setPk_checkflow(WORK_ITEM);
        note.setPk_wf_task(TASK);
        note.setBillid(PK);
        note.setPk_org(ORG);
        note.setPk_group(GROUP);
        note.setPk_billtype(ORDER.type);
        note.setCheckman(OTHER);
        note.setAgencyuser(GROUP);
        note.setIsmsgbind("N");
        note.setApprovestatus(WfTaskOrInstanceStatus.Started.getIntValue());
        return note;
    }

    private static void rejects(Fixture fixture, OperationContext context, ProcurementWorkflowCommand command, int calls)
            throws Exception {
        try {
            fixture.adapter.execute(context, command);
            throw new AssertionError("Expected an invalid command or result to fail");
        } catch (BusinessException expected) {
            check(fixture.calls == calls, "Validation failed at the wrong point: " + expected.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static final class Fixture {
        final ProcurementWorkflowOperationAdapter adapter;
        int calls;
        Object[] arguments;
        WorkflownoteVO workItem;
        WFTask task;

        Fixture(BillKind kind, SuperVO before, SuperVO after, Object result) {
            this(kind, before, after, result, kind.type);
        }

        Fixture(final BillKind kind, final SuperVO before, final SuperVO after, final Object result,
                final String resolvedType) {
            IplatFormEntry pf = (IplatFormEntry) Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                    new Class<?>[] { IplatFormEntry.class }, new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) throws BusinessException {
                            check("processAction".equals(method.getName()), "Unexpected native PF method");
                            calls++;
                            arguments = args;
                            if (result instanceof BusinessException) {
                                throw (BusinessException) result;
                            }
                            return result;
                        }
                    });
            adapter = new ProcurementWorkflowOperationAdapter(pf, new ProcurementWorkflowOperationAdapter.HeaderLookup() {
                public SuperVO find(Class<? extends SuperVO> header, String pkColumn, String pk) {
                    if (header == WorkflownoteVO.class) {
                        check("pk_checkflow".equals(pkColumn) && WORK_ITEM.equals(pk), "Wrong work-item lookup");
                        return workItem;
                    }
                    check(kind.headerClass == header && kind.pkColumn.equals(pkColumn), "Wrong header lookup");
                    check(PK.equals(pk), "Wrong persisted primary key");
                    return calls == 0 ? before : after;
                }
            }, new ProcurementWorkflowOperationAdapter.BillTypeLookup() {
                public String baseType(String type) {
                    check("25-QD".equals(type), "Unexpected transaction type lookup");
                    return resolvedType;
                }
            }, new ProcurementWorkflowOperationAdapter.TaskLookup() {
                public WFTask find(String pk) {
                    check(TASK.equals(pk), "Wrong workflow task lookup");
                    return task;
                }
            });
        }
    }

    private static final class BillKind {
        final String node;
        final String type;
        final String objectType;
        final String pkColumn;
        final Class<? extends AbstractBill> billClass;
        final Class<? extends SuperVO> headerClass;
        final Class<?> metadataClass;

        BillKind(String node, String type, String objectType, String pkColumn,
                Class<? extends AbstractBill> billClass, Class<? extends SuperVO> headerClass,
                Class<?> metadataClass) {
            this.node = node;
            this.type = type;
            this.objectType = objectType;
            this.pkColumn = pkColumn;
            this.billClass = billClass;
            this.headerClass = headerClass;
            this.metadataClass = metadataClass;
        }

        SuperVO header(String pk, String org, int status) throws Exception {
            SuperVO header = headerClass.newInstance();
            attachHeaderMetadata(header, pkColumn);
            if (pk != null) {
                header.setAttributeValue(pkColumn, pk);
            }
            if (org != null) {
                header.setAttributeValue("pk_org", org);
                header.setAttributeValue("pk_group", GROUP);
                header.setAttributeValue("vbillcode", "PERSISTED-NO");
            }
            header.setAttributeValue("21".equals(type) ? "forderstatus" : "fbillstatus", Integer.valueOf(status));
            header.setStatus(VOStatus.UNCHANGED);
            return header;
        }

        Serializable result(SuperVO header) throws Exception {
            Object result = Array.newInstance(billClass, 1);
            Array.set(result, 0, bill(header));
            return (Serializable) result;
        }

        @SuppressWarnings("unchecked")
        AbstractBill bill(SuperVO header) throws Exception {
            BillMetaFactory factory = BillMetaFactory.getInstance();
            Field cache = BillMetaFactory.class.getDeclaredField("classIndex");
            cache.setAccessible(true);
            Map<Class<?>, IBillMeta> classes = (Map<Class<?>, IBillMeta>) cache.get(factory);
            IBillMeta metadata = proxy(IBillMeta.class, new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) {
                    check("getChildren".equals(method.getName()), "Unexpected bill metadata call");
                    return new IVOMeta[0];
                }
            });
            classes.put(metadataClass, metadata);
            AbstractBill bill = billClass.newInstance();
            bill.setParentVO(header);
            return bill;
        }
    }

    private static void attachHeaderMetadata(SuperVO header, final String pkColumn) throws Exception {
        Field metadata = SuperVO.class.getDeclaredField("vometa");
        metadata.setAccessible(true);
        final IAttributeMeta attribute = proxy(IAttributeMeta.class, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("isSerializable".equals(method.getName())) return true;
                if ("isStatic".equals(method.getName())) return false;
                if ("getName".equals(method.getName())) return pkColumn;
                check("getJavaType".equals(method.getName()), "Unexpected header attribute metadata call");
                return JavaType.Object;
            }
        });
        final IVOMetaStatisticInfo statistics = proxy(IVOMetaStatisticInfo.class, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                check("getStaticAttributes".equals(method.getName()), "Unexpected header metadata statistics call");
                return new IAttributeMeta[0];
            }
        });
        metadata.set(header, proxy(IVOMeta.class, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("getStatisticInfo".equals(method.getName())) return statistics;
                check("getAttribute".equals(method.getName()) || "getPrimaryAttribute".equals(method.getName()),
                        "Unexpected header metadata call");
                return attribute;
            }
        }));
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
    }
}
