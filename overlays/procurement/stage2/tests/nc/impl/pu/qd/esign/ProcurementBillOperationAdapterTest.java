package nc.impl.pu.qd.esign;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pu.m20.context.PraybillContext;
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
import nc.vo.pu.pf.PUPFParameter;
import nc.vo.pu.qd.esign.ProcurementBillPfCommand;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.IVOMetaStatisticInfo;
import nc.vo.pub.JavaType;
import nc.vo.pub.SuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.pubapp.pattern.model.meta.entity.bill.BillMetaFactory;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class ProcurementBillOperationAdapterTest {
    private static final String GROUP = "1001A100000000000001";
    private static final String ORG = "1001A100000000000002";
    private static final String PK = "1001A100000000000003";
    private static final String OTHER = "1001A100000000000004";
    private static final BillKind STORE = new BillKind("40040000", "422X", "PU_STORE_REQ", "pk_storereq",
            StoreReqAppVO.class, StoreReqAppHeaderVO.class, StoreReqAppVOMeta.class);
    private static final BillKind PRAY = new BillKind("40040200", "20", "PU_PRAYBILL", "pk_praybill",
            PraybillVO.class, PraybillHeaderVO.class, PraybillVOMeta.class);
    private static final BillKind INVOICE = new BillKind("40041000", "25", "PU_PURCHASE_INVOICE", "pk_invoice",
            InvoiceVO.class, InvoiceHeaderVO.class, InvoiceVOMeta.class);

    public static void main(String[] args) throws Exception {
        for (BillKind kind : Arrays.asList(STORE, PRAY, INVOICE)) {
            savesOneBillAndDescribesPersistedIdentity(kind);
            updatesSparseHeaderAndKeepsNativeResult(kind);
            deletesSparseHeaderAndConfirmsAbsence(kind);
        }
        insertsWithAnEmptyNativePrimaryKey();
        routesOnlyThePersistedTransactionType();
        passesPraybillReviseContext();
        passesInvoiceConfirmationAndRejectsDifferentTrigger();
        rejectsUnauthorizedCommandsBeforePf();
        rejectsUnverifiedResults();
        propagatesNativeBusinessException();
    }

    private static void savesOneBillAndDescribesPersistedIdentity(BillKind kind) throws Exception {
        SuperVO header = kind.header(null, ORG, VOStatus.NEW);
        AbstractBill bill = kind.bill(header);
        Object user = null;
        if (kind == STORE) {
            PUPFParameter environment = new PUPFParameter();
            environment.setbBudgetCheckkFlag(true);
            user = environment;
        }
        WorkflownoteVO note = new WorkflownoteVO();
        note.setPk_group(GROUP);
        note.setPk_org(ORG);
        note.setPk_billtype(kind.type);
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("notechecked", "notechecked");
        Serializable payload = kind.result(kind.header(PK, null, VOStatus.UNCHANGED));
        Fixture fixture = new Fixture(kind, null, kind.header(PK, ORG, VOStatus.UNCHANGED), payload);
        OperationContext context = kind.context("INSERT");
        ProcurementBillPfCommand command = new ProcurementBillPfCommand("SAVEBASE", kind.type,
                note, bill, user, parameters);
        Serializable result = fixture.adapter.execute(context, command);
        check(result == payload, "Save must return the original native array");
        check(Arrays.equals(fixture.arguments, new Object[] { "SAVEBASE", kind.type, note, bill, user, parameters }),
                "Save must pass the native PF arguments");
        BusinessObjectRef saved = fixture.adapter.describeResult(context, result).get(0);
        check(PK.equals(saved.getObjectPk()) && "PERSISTED-NO".equals(saved.getBillCode())
                && "one".equals(saved.getClientObjectId()) && kind.objectType.equals(saved.getObjectType()),
                "Save must backfill the persisted identity");
    }

    private static void updatesSparseHeaderAndKeepsNativeResult(BillKind kind) throws Exception {
        SuperVO persisted = kind.header(PK, ORG, VOStatus.UNCHANGED);
        Serializable payload = kind.result(kind.header(PK, null, VOStatus.UNCHANGED));
        Fixture fixture = new Fixture(kind, persisted, persisted, payload);
        AbstractBill bill = kind.bill(kind.header(PK, null, VOStatus.UPDATED));
        OperationContext context = kind.context("UPDATE");
        Serializable result = fixture.adapter.execute(context, command(kind, "SAVEBASE", bill));
        check(result == payload && fixture.arguments[3] == bill && fixture.calls == 1,
                "A sparse update must retain native input and output");
        check("PERSISTED-NO".equals(fixture.adapter.describeResult(context, result).get(0).getBillCode()),
                "A sparse result must use persisted bill code");
    }

    private static void insertsWithAnEmptyNativePrimaryKey() throws Exception {
        AbstractBill bill = STORE.bill(STORE.header("", ORG, VOStatus.NEW));
        Serializable payload = STORE.result(STORE.header(PK, null, VOStatus.UNCHANGED));
        Fixture fixture = new Fixture(STORE, null, STORE.header(PK, ORG, VOStatus.UNCHANGED), payload);
        check(fixture.adapter.execute(STORE.context("INSERT"), command(STORE, "SAVEBASE", bill)) == payload
                && fixture.arguments[3] == bill, "A blank unsaved primary key must retain the native aggregate");
    }

    private static void routesOnlyThePersistedTransactionType() throws Exception {
        SuperVO persisted = INVOICE.header(PK, ORG, VOStatus.UNCHANGED);
        persisted.setAttributeValue("vtrantypecode", "25-QD");
        AbstractBill bill = INVOICE.bill(INVOICE.header(PK, null, VOStatus.UPDATED));
        Serializable payload = INVOICE.result(INVOICE.header(PK, null, VOStatus.UNCHANGED));
        Fixture fixture = new Fixture(INVOICE, persisted, persisted, payload, "25");
        OperationContext context = INVOICE.context("UPDATE");
        rejectBeforePf(fixture, context, command(INVOICE, "SAVEBASE", bill));
        ProcurementBillPfCommand command = new ProcurementBillPfCommand("SAVEBASE", "25-QD", null, bill, null, null);
        Fixture outside = new Fixture(INVOICE, persisted, persisted, payload, "20");
        rejectBeforePf(outside, context, command);
        check(fixture.adapter.execute(context, command) == payload && "25-QD".equals(fixture.arguments[1]),
                "A registered invoice transaction type must reach native PF unchanged");
    }

    private static void deletesSparseHeaderAndConfirmsAbsence(BillKind kind) throws Exception {
        SuperVO persisted = kind.header(PK, ORG, VOStatus.UNCHANGED);
        Fixture fixture = new Fixture(kind, persisted, null, null);
        AbstractBill bill = kind.bill(kind.header(PK, null, VOStatus.UNCHANGED));
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("reload_vo", bill);
        ProcurementBillPfCommand command = new ProcurementBillPfCommand("DISCARD", kind.type, null, bill,
                null, parameters);
        OperationContext context = kind.context("DELETE");
        check(fixture.adapter.execute(context, command) == null, "Delete must preserve native null");
        check(Arrays.equals(fixture.arguments, new Object[] { "DISCARD", kind.type, null, bill, null, parameters }),
                "Delete must pass its native arguments");
        check(fixture.adapter.describeResult(context, null).size() == 1,
                "Delete must retain the authorization identity for audit");
    }

    private static void passesPraybillReviseContext() throws Exception {
        SuperVO persisted = PRAY.header(PK, ORG, VOStatus.UNCHANGED);
        Serializable payload = PRAY.result(PRAY.header(PK, null, VOStatus.UNCHANGED));
        Fixture fixture = new Fixture(PRAY, persisted, persisted, payload);
        PfUserObject user = new PfUserObject();
        user.setUserObject(new PraybillContext());
        AbstractBill bill = PRAY.bill(PRAY.header(PK, null, VOStatus.UPDATED));
        ProcurementBillPfCommand command = new ProcurementBillPfCommand("SAVEREVISE", "20", null, bill, user, null);
        check(fixture.adapter.execute(PRAY.context("UPDATE"), command) == payload
                && fixture.arguments[4] == user, "Revise must preserve its typed tolerance context");
    }

    private static void passesInvoiceConfirmationAndRejectsDifferentTrigger() throws Exception {
        SuperVO persisted = INVOICE.header(PK, ORG, VOStatus.UNCHANGED);
        AbstractBill bill = INVOICE.bill(INVOICE.header(PK, null, VOStatus.UNCHANGED));
        InvoiceUIToBSEnv environment = new InvoiceUIToBSEnv();
        environment.setDelAnswer(InvoiceUserAnswerType.YES);
        PfUserObject user = new PfUserObject();
        user.setUserObject(environment);
        ProcurementBillPfCommand command = new ProcurementBillPfCommand("DISCARD", "25", null, bill, user, null);
        environment.setTrigger(InvoiceBillAction.APPROVE);
        rejectBeforePf(new Fixture(INVOICE, persisted, null, null), INVOICE.context("DELETE"), command);
        environment.setTrigger(null);
        Fixture fixture = new Fixture(INVOICE, persisted, null, null);
        fixture.adapter.execute(INVOICE.context("DELETE"), command);
        check(fixture.arguments[4] == user, "Delete must preserve the native invoice confirmation");
    }

    private static void rejectsUnauthorizedCommandsBeforePf() throws Exception {
        SuperVO persisted = STORE.header(PK, ORG, VOStatus.UNCHANGED);
        Fixture fixture = new Fixture(STORE, persisted, persisted, null);
        AbstractBill sparse = STORE.bill(STORE.header(PK, null, VOStatus.UPDATED));
        OperationContext context = STORE.context("UPDATE");
        rejectBeforePf(fixture, context, command(STORE, "SAVE", sparse));
        rejectBeforePf(fixture, context, command(STORE, "SAVEREVISE", sparse));
        rejectBeforePf(fixture, context, command(PRAY, "SAVEBASE", sparse));
        rejectBeforePf(fixture, context, command(STORE, "SAVEBASE", PRAY.bill(PRAY.header(PK, null, VOStatus.UPDATED))));
        AbstractBill wrongOwner = STORE.bill(STORE.header(PK, OTHER, VOStatus.UPDATED));
        rejectBeforePf(fixture, context, command(STORE, "SAVEBASE", wrongOwner));
        AbstractBill wrongPk = STORE.bill(STORE.header(OTHER, null, VOStatus.UPDATED));
        rejectBeforePf(fixture, context, command(STORE, "SAVEBASE", wrongPk));
        SuperVO explicitNull = STORE.header(PK, null, VOStatus.UPDATED);
        explicitNull.setAttributeValue("pk_org", null);
        rejectBeforePf(fixture, context, command(STORE, "SAVEBASE", STORE.bill(explicitNull)));
        AbstractBill changedType = STORE.bill(STORE.header(PK, null, VOStatus.UPDATED));
        changedType.getParent().setAttributeValue("vtrantypecode", "25");
        rejectBeforePf(fixture, context, command(STORE, "SAVEBASE", changedType));
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("reload_vo", wrongPk);
        rejectBeforePf(fixture, STORE.context("DELETE"),
                new ProcurementBillPfCommand("DISCARD", STORE.type, null, sparse, null, parameters));
        parameters.clear();
        parameters.put("directApprove", Boolean.TRUE);
        rejectBeforePf(fixture, context,
                new ProcurementBillPfCommand("SAVEBASE", STORE.type, null, sparse, null, parameters));
        WorkflownoteVO note = new WorkflownoteVO();
        note.setBillid(OTHER);
        rejectBeforePf(fixture, context, new ProcurementBillPfCommand("SAVEBASE", STORE.type, note, sparse, null, null));
        rejectBeforePf(fixture, context,
                new ProcurementBillPfCommand("SAVEBASE", STORE.type, null, sparse, new PfUserObject(), null));
        List<BusinessObjectRef> refs = Arrays.asList(context.getObjects().get(0),
                new BusinessObjectRef("two", STORE.objectType, OTHER, null, null));
        OperationContext batch = new OperationContext("r", "b", GROUP, ORG, STORE.node, "node", "Save",
                "Save", "UPDATE", null, refs);
        rejectBeforePf(fixture, batch, command(STORE, "SAVEBASE", sparse));
        Fixture outside = new Fixture(STORE, STORE.header(PK, OTHER, VOStatus.UNCHANGED), null, null);
        rejectBeforePf(outside, STORE.context("DELETE"), command(STORE, "DISCARD", sparse));
    }

    private static void rejectsUnverifiedResults() throws Exception {
        SuperVO persisted = INVOICE.header(PK, ORG, VOStatus.UNCHANGED);
        AbstractBill bill = INVOICE.bill(INVOICE.header(PK, null, VOStatus.UPDATED));
        ProcurementBillPfCommand command = command(INVOICE, "SAVEBASE", bill);
        Serializable wrongPk = INVOICE.result(INVOICE.header(OTHER, null, VOStatus.UNCHANGED));
        rejectAfterPf(new Fixture(INVOICE, persisted, persisted, wrongPk), INVOICE.context("UPDATE"), command);
        Serializable sparse = INVOICE.result(INVOICE.header(PK, null, VOStatus.UNCHANGED));
        rejectAfterPf(new Fixture(INVOICE, persisted, null, sparse), INVOICE.context("UPDATE"), command);
        SuperVO otherOwner = INVOICE.header(PK, OTHER, VOStatus.UNCHANGED);
        rejectAfterPf(new Fixture(INVOICE, persisted, otherOwner, sparse), INVOICE.context("UPDATE"), command);
        rejectAfterPf(new Fixture(INVOICE, persisted, persisted, null), INVOICE.context("DELETE"),
                command(INVOICE, "DISCARD", bill));
    }

    private static void propagatesNativeBusinessException() throws Exception {
        SuperVO persisted = PRAY.header(PK, ORG, VOStatus.UNCHANGED);
        BusinessException failure = new BusinessException("Native validation");
        Fixture fixture = new Fixture(PRAY, persisted, persisted, failure);
        try {
            fixture.adapter.execute(PRAY.context("UPDATE"),
                    command(PRAY, "SAVEBASE", PRAY.bill(PRAY.header(PK, null, VOStatus.UPDATED))));
            throw new AssertionError("Expected native BusinessException");
        } catch (BusinessException actual) {
            check(actual == failure, "Native exception must be preserved for continuation");
        }
    }

    private static ProcurementBillPfCommand command(BillKind kind, String action, AbstractBill bill) {
        return new ProcurementBillPfCommand(action, kind.type, null, bill, null, null);
    }

    private static void rejectBeforePf(Fixture fixture, OperationContext context, Serializable command)
            throws Exception {
        reject(fixture, context, command, 0);
    }

    private static void rejectAfterPf(Fixture fixture, OperationContext context, Serializable command)
            throws Exception {
        reject(fixture, context, command, 1);
    }

    private static void reject(Fixture fixture, OperationContext context, Serializable command, int calls)
            throws Exception {
        try {
            fixture.adapter.execute(context, command);
            throw new AssertionError("Expected invalid command or result to fail");
        } catch (BusinessException expected) {
            check(fixture.calls == calls, "Validation failed at the wrong point: " + expected.getMessage());
        }
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }

    private static final class Fixture {
        final ProcurementBillOperationAdapter adapter;
        int calls;
        Object[] arguments;

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
            adapter = new ProcurementBillOperationAdapter(pf, new ProcurementBillOperationAdapter.HeaderLookup() {
                public SuperVO find(Class<? extends SuperVO> header, String pkColumn, String pk) {
                    check(kind.headerClass == header && kind.pkColumn.equals(pkColumn), "Wrong header lookup");
                    check(PK.equals(pk), "Wrong persisted primary key");
                    return calls == 0 ? before : after;
                }
            }, new ProcurementBillOperationAdapter.BillTypeLookup() {
                public String baseType(String type) {
                    check("25-QD".equals(type), "Unexpected transaction type lookup");
                    return resolvedType;
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

        OperationContext context(String operation) {
            String button = "DELETE".equals(operation) ? "Delete" : "Save";
            BusinessObjectRef ref = new BusinessObjectRef("one", objectType,
                    "INSERT".equals(operation) ? null : PK, null, null);
            return new OperationContext("r", "b", GROUP, ORG, node, "node", button, button,
                    operation, null, Collections.singletonList(ref));
        }

        SuperVO header(String pk, String org, int status) throws Exception {
            SuperVO header = headerClass.newInstance();
            attachHeaderMetadata(header);
            if (pk != null) {
                header.setAttributeValue(pkColumn, pk);
            }
            if (org != null) {
                header.setAttributeValue("pk_org", org);
                header.setAttributeValue("pk_group", GROUP);
                header.setAttributeValue("vbillcode", "PERSISTED-NO");
            }
            header.setStatus(status);
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
            synchronized (factory) {
                IBillMeta previous = classes.put(metadataClass, metadata);
                try {
                    AbstractBill bill = billClass.newInstance();
                    bill.setParentVO(header);
                    return bill;
                } finally {
                    if (previous == null) {
                        classes.remove(metadataClass);
                    } else {
                        classes.put(metadataClass, previous);
                    }
                }
            }
        }
    }

    private static void attachHeaderMetadata(SuperVO header) throws Exception {
        Field metadata = SuperVO.class.getDeclaredField("vometa");
        metadata.setAccessible(true);
        final IAttributeMeta attribute = proxy(IAttributeMeta.class, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("isSerializable".equals(method.getName())) return true;
                if ("isStatic".equals(method.getName())) return false;
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
                check("getAttribute".equals(method.getName()), "Unexpected header metadata call");
                return attribute;
            }
        }));
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
    }
}
