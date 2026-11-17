package nc.impl.pu.qd.esign;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pu.m21.context.OrderContext;
import nc.vo.pu.m21.entity.OrderHeaderVO;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pu.m21.entity.OrderVOMeta;
import nc.vo.pu.qd.esign.PurchaseOrderOperationCommand;
import nc.vo.pu.qd.esign.PurchaseOrderPfCommand;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.IVOMetaStatisticInfo;
import nc.vo.pub.JavaType;
import nc.vo.pub.SuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.pub.pf.WfTaskInfo;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.pubapp.pattern.model.meta.entity.bill.BillMetaFactory;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.wfengine.pub.WFTask;

public final class PurchaseOrderPilotAdapterTest {
    private static final String PK = "1001A100000000000001";
    private static final String OTHER_PK = "1001A100000000000002";
    private static final String ORG = "1001A100000000000003";
    private static final String GROUP = "1001A100000000000004";

    public static void main(String[] args) throws Exception {
        sparseInsertResultUsesPersistedOwnerAndBillCode();
        sparseInsertResultRequiresPersistedOwnerAndBillCode();
        nativeSaveReturnsOriginalResultAndBackfillsIdentity();
        nativeReviseKeepsAuthorizedIdentity();
        lightUpdateReachesNativePfWithoutChangingTheCommand();
        lightReviseResultUsesCurrentPersistedBillCode();
        lightUpdateRequiresPersistedOwnershipAndType();
        lightUpdateRejectsExplicitOwnershipAndTypeChanges();
        lightReviseResultRequiresCurrentPersistedOwnership();
        insertRequiresCompleteOwner();
        preallocatedInsertMustBeUnused();
        authorizationMismatchStopsBeforePf();
        unsafePfParameterStopsBeforePf();
        workflowNoteMustMatchAuthorizedOrder();
        missingOrUnexpectedPfResultFails();
        nativeBusinessExceptionIsPreserved();
        legacySaveCannotBypassPf();
        System.out.println("PurchaseOrderPilotAdapterTest passed");
    }

    private static void sparseInsertResultUsesPersistedOwnerAndBillCode() throws Exception {
        OrderVO[] saved = { lightOrder(PK, VOStatus.UNCHANGED) };
        OrderHeaderVO persisted = order(PK, ORG, VOStatus.UNCHANGED).getHVO();
        Fixture fixture = new Fixture(saved, null, persisted);
        OperationContext insert = context("40040400", "Save", "INSERT", null);
        Serializable result = fixture.adapter.execute(insert,
                command("SAVEBASE", "21", order(null, ORG, VOStatus.NEW)));
        BusinessObjectRef ref = fixture.adapter.describeResult(insert, result).get(0);
        check(PK.equals(fixture.queriedPk) && PK.equals(ref.getObjectPk()) && "PO-001".equals(ref.getBillCode()),
                "a sparse insert result must use the persisted order identity");
        check(result == saved && saved[0].getHVO().getPk_group() == null
                && saved[0].getHVO().getPk_org() == null && saved[0].getHVO().getVbillcode() == null,
                "audit backfill must preserve the native insert result");
    }

    private static void sparseInsertResultRequiresPersistedOwnerAndBillCode() throws Exception {
        OrderHeaderVO otherOrg = order(PK, "other-org", VOStatus.UNCHANGED).getHVO();
        OrderHeaderVO otherGroup = order(PK, ORG, VOStatus.UNCHANGED).getHVO();
        otherGroup.setPk_group("other-group");
        OrderHeaderVO deleted = order(PK, ORG, VOStatus.UNCHANGED).getHVO();
        deleted.setDr(Integer.valueOf(1));
        OrderHeaderVO withoutNumber = order(PK, ORG, VOStatus.UNCHANGED).getHVO();
        withoutNumber.setVbillcode(" ");
        OrderHeaderVO otherOrder = order(OTHER_PK, ORG, VOStatus.UNCHANGED).getHVO();
        OperationContext insert = context("40040400", "Save", "INSERT", null);
        for (OrderHeaderVO persisted : new OrderHeaderVO[] { null, otherOrg, otherGroup, deleted,
                withoutNumber, otherOrder }) {
            Fixture fixture = new Fixture(new OrderVO[] { lightOrder(PK, VOStatus.UNCHANGED) }, null, persisted);
            Serializable result = fixture.adapter.execute(insert,
                    command("SAVEBASE", "21", order(null, ORG, VOStatus.NEW)));
            try {
                fixture.adapter.describeResult(insert, result);
                throw new AssertionError("Expected invalid persisted insert result to fail");
            } catch (BusinessException expected) {
                check(fixture.calls == 1 && PK.equals(fixture.queriedPk),
                        "result validation must check the exact row after the native insert");
            }
        }
    }

    private static void nativeSaveReturnsOriginalResultAndBackfillsIdentity() throws Exception {
        OrderVO draft = order(null, ORG, VOStatus.NEW);
        OrderVO[] saved = { order(PK, ORG, VOStatus.UNCHANGED) };
        Fixture fixture = new Fixture(saved, null, saved[0].getHVO());
        PfUserObject user = new PfUserObject();
        user.setUserObject(new OrderContext());
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("notechecked", "notechecked");
        OperationContext context = context("40040400", "Save", "INSERT", null);
        Serializable result = fixture.adapter.execute(context,
                new PurchaseOrderPfCommand("SAVEBASE", "21", null, draft, user, params));
        check(result == saved, "native result identity");
        check(fixture.calls == 1, "single native save call");
        check("SAVEBASE".equals(fixture.arguments[0]) && "21".equals(fixture.arguments[1])
                && fixture.arguments[2] == null && fixture.arguments[3] == draft
                && fixture.arguments[4] == user && params.equals(fixture.arguments[5]), "native PF arguments");
        BusinessObjectRef ref = fixture.adapter.describeResult(context, result).get(0);
        check(PK.equals(ref.getObjectPk()) && "PO-001".equals(ref.getBillCode())
                && "selected".equals(ref.getClientObjectId()), "returned order identity");
    }

    private static void nativeReviseKeepsAuthorizedIdentity() throws Exception {
        OrderVO draft = order(PK, ORG, VOStatus.UPDATED);
        OrderVO[] saved = { order(PK, ORG, VOStatus.UNCHANGED) };
        Fixture fixture = new Fixture(saved, saved[0].getHVO());
        Serializable result = fixture.adapter.execute(context("40040400", "Save", "UPDATE", PK),
                command("REVISE", "21", draft));
        check(result == saved && "REVISE".equals(fixture.arguments[0]), "native revise result");
    }

    private static void lightUpdateReachesNativePfWithoutChangingTheCommand() throws Exception {
        OrderVO draft = lightOrder(PK, VOStatus.UPDATED);
        OrderVO[] saved = { order(PK, ORG, VOStatus.UNCHANGED) };
        OrderHeaderVO persisted = saved[0].getHVO();
        persisted.setCtrantypeid("purchase-type-id");
        Fixture fixture = new Fixture(saved, persisted);
        WorkflownoteVO note = new WorkflownoteVO();
        note.setBillid(PK);
        note.setPk_group(GROUP);
        note.setPk_org(ORG);
        note.setPk_billtype("purchase-type-id");
        Serializable result = fixture.adapter.execute(context("40040400", "Save", "UPDATE", PK),
                new PurchaseOrderPfCommand("SAVEBASE", "21", note, draft, null, null));
        check(result == saved && fixture.calls == 1 && fixture.arguments[2] == note
                && fixture.arguments[3] == draft && draft.getHVO().getPk_org() == null,
                "light update must reach PF with the original command");
    }

    private static void lightReviseResultUsesCurrentPersistedBillCode() throws Exception {
        OrderVO draft = order(PK, ORG, VOStatus.UPDATED);
        OrderVO[] saved = { lightOrder(PK, VOStatus.UNCHANGED) };
        OrderHeaderVO before = order(PK, ORG, VOStatus.UNCHANGED).getHVO();
        OrderHeaderVO after = order(PK, ORG, VOStatus.UNCHANGED).getHVO();
        after.setVbillcode("PO-REVISED");
        Fixture fixture = new Fixture(saved, before, after);
        OperationContext update = context("40040400", "Save", "UPDATE", PK);
        Serializable result = fixture.adapter.execute(update, command("REVISE", "21", draft));
        BusinessObjectRef ref = fixture.adapter.describeResult(update, result).get(0);
        check(PK.equals(ref.getObjectPk()) && "PO-REVISED".equals(ref.getBillCode()),
                "a sparse revise result must backfill the currently persisted identity");
        check(result == saved && saved[0].getHVO().getVbillcode() == null,
                "audit backfill must preserve the sparse native result");
    }

    private static void lightUpdateRequiresPersistedOwnershipAndType() throws Exception {
        OrderVO draft = lightOrder(PK, VOStatus.UPDATED);
        OrderHeaderVO foreign = order(PK, "other-org", VOStatus.UNCHANGED).getHVO();
        OrderHeaderVO typed = order(PK, ORG, VOStatus.UNCHANGED).getHVO();
        typed.setVtrantypecode("21-OTHER");
        OperationContext update = context("40040400", "Save", "UPDATE", PK);
        PurchaseOrderPfCommand request = command("SAVEBASE", "21", draft);
        rejectBeforePf(new Fixture(null, null), update, request);
        rejectBeforePf(new Fixture(null, foreign), update, request);
        rejectBeforePf(new Fixture(null, typed), update, request);
    }

    private static void lightUpdateRejectsExplicitOwnershipAndTypeChanges() throws Exception {
        OperationContext update = context("40040400", "Save", "UPDATE", PK);
        OrderHeaderVO persisted = order(PK, ORG, VOStatus.UNCHANGED).getHVO();
        persisted.setCtrantypeid("purchase-type-id");
        Fixture fixture = new Fixture(null, persisted);
        String[] protectedFields = { OrderHeaderVO.PK_GROUP, OrderHeaderVO.PK_ORG, OrderHeaderVO.CTRANTYPEID };
        for (String field : protectedFields) {
            OrderVO draft = order(PK, ORG, VOStatus.UPDATED);
            draft.getHVO().setAttributeValue(field, null);
            check(draft.getHVO().usedAttributeNames().contains(field), "explicit null must be in the light VO");
            rejectBeforePf(fixture, update, command("SAVEBASE", "21", draft));
        }
        OrderVO draft = order(PK, ORG, VOStatus.UPDATED);
        draft.getHVO().setVtrantypecode("21");
        rejectBeforePf(fixture, update, command("SAVEBASE", "21", draft));
    }

    private static void lightReviseResultRequiresCurrentPersistedOwnership() throws Exception {
        OrderVO draft = order(PK, ORG, VOStatus.UPDATED);
        OrderVO[] saved = { lightOrder(PK, VOStatus.UNCHANGED) };
        OrderHeaderVO before = order(PK, ORG, VOStatus.UNCHANGED).getHVO();
        OrderHeaderVO after = order(PK, "other-org", VOStatus.UNCHANGED).getHVO();
        Fixture fixture = new Fixture(saved, before, after);
        OperationContext update = context("40040400", "Save", "UPDATE", PK);
        Serializable result = fixture.adapter.execute(update, command("REVISE", "21", draft));
        try {
            fixture.adapter.describeResult(update, result);
            throw new AssertionError("Expected current persisted ownership to be validated");
        } catch (BusinessException expected) {
            check(fixture.calls == 1, "ownership check follows the native PF result");
        }
    }

    private static void insertRequiresCompleteOwner() throws Exception {
        OperationContext insert = context("40040400", "Save", "INSERT", null);
        rejectBeforePf(new Fixture(null, null), insert, command("SAVEBASE", "21", lightOrder(null, VOStatus.NEW)));
        rejectBeforePf(new Fixture(null, null), insert,
                command("SAVEBASE", "21", order(null, "other-org", VOStatus.NEW)));
    }

    private static void preallocatedInsertMustBeUnused() throws Exception {
        OrderVO draft = order(PK, ORG, VOStatus.NEW);
        OrderVO[] saved = { order(PK, ORG, VOStatus.UNCHANGED) };
        OperationContext insert = context("40040400", "Save", "INSERT", null);
        Fixture unused = new Fixture(saved, null);
        check(unused.adapter.execute(insert, command("SAVEBASE", "21", draft)) == saved,
                "unallocated persisted identity");
        check(PK.equals(unused.queriedPk), "preallocated identity checked");
        rejectBeforePf(new Fixture(saved, saved[0].getHVO()), insert, command("SAVEBASE", "21", draft));
    }

    private static void authorizationMismatchStopsBeforePf() throws Exception {
        OrderVO draft = order(PK, ORG, VOStatus.UPDATED);
        Fixture fixture = new Fixture(null, draft.getHVO());
        OperationContext update = context("40040400", "Save", "UPDATE", PK);
        rejectBeforePf(fixture, context("40041000", "Save", "UPDATE", PK), command("SAVEBASE", "21", draft));
        rejectBeforePf(fixture, context("40040400", "Delete", "UPDATE", PK), command("SAVEBASE", "21", draft));
        rejectBeforePf(fixture, update, command("APPROVE", "21", draft));
        rejectBeforePf(fixture, update, command("SAVEBASE", "25", draft));
        rejectBeforePf(fixture, update, command("SAVEBASE", "21", order(OTHER_PK, ORG, VOStatus.UPDATED)));
        rejectBeforePf(fixture, update, command("SAVEBASE", "21", order(PK, "other-org", VOStatus.UPDATED)));
        rejectBeforePf(fixture, update, command("SAVEBASE", "21", order(PK, ORG, VOStatus.NEW)));
        rejectBeforePf(fixture, context("40040400", "Save", "INSERT", null), command("REVISE", "21",
                order(null, ORG, VOStatus.NEW)));
        Fixture crossOrg = new Fixture(null, order(PK, "other-org", VOStatus.UPDATED).getHVO());
        rejectBeforePf(crossOrg, update, command("SAVEBASE", "21", draft));
    }

    private static void unsafePfParameterStopsBeforePf() throws Exception {
        Fixture fixture = new Fixture(null, null);
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("nolockandconsist", "true");
        rejectBeforePf(fixture, context("40040400", "Save", "INSERT", null),
                new PurchaseOrderPfCommand("SAVEBASE", "21", null, order(null, ORG, VOStatus.NEW), null, params));
    }

    private static void workflowNoteMustMatchAuthorizedOrder() throws Exception {
        OrderVO draft = order(PK, ORG, VOStatus.UPDATED);
        WorkflownoteVO note = new WorkflownoteVO();
        note.setBillid(PK);
        WfTaskInfo info = new WfTaskInfo();
        WFTask task = new WFTask();
        task.setBillID(OTHER_PK);
        info.setTask(task);
        note.setTaskInfo(info);
        Fixture fixture = new Fixture(new OrderVO[] { draft }, draft.getHVO());
        rejectBeforePf(fixture, context("40040400", "Save", "UPDATE", PK),
                new PurchaseOrderPfCommand("SAVEBASE", "21", note, draft, null, null));
    }

    private static void missingOrUnexpectedPfResultFails() throws Exception {
        OrderVO draft = order(PK, ORG, VOStatus.UPDATED);
        OperationContext update = context("40040400", "Save", "UPDATE", PK);
        Object[] results = { null, "completed", new OrderVO[0], new OrderVO[] { draft, draft },
                new OrderVO[] { order(OTHER_PK, ORG, VOStatus.UNCHANGED) },
                new OrderVO[] { order(PK, "other-org", VOStatus.UNCHANGED) } };
        for (Object result : results) {
            Fixture fixture = new Fixture(result, draft.getHVO());
            try {
                fixture.adapter.execute(update, command("REVISE", "21", draft));
                throw new AssertionError("Expected invalid native result to fail");
            } catch (BusinessException expected) {
                check(fixture.calls == 1, "native PF was executed before result rejection");
            }
        }
    }

    private static void nativeBusinessExceptionIsPreserved() throws Exception {
        BusinessException nativeError = new BusinessException("native rule");
        Fixture fixture = new Fixture(nativeError, null);
        try {
            fixture.adapter.execute(context("40040400", "Save", "INSERT", null),
                    command("SAVEBASE", "21", order(null, ORG, VOStatus.NEW)));
            throw new AssertionError("Expected native rule to fail");
        } catch (BusinessException caught) {
            check(caught == nativeError, "native rule error must reach the client");
        }
    }

    private static void legacySaveCannotBypassPf() throws Exception {
        OrderVO draft = order(null, ORG, VOStatus.NEW);
        Fixture fixture = new Fixture(null, null);
        rejectBeforePf(fixture, context("40040400", "Save", "INSERT", null),
                PurchaseOrderOperationCommand.forSave(new OrderVO[] { draft }, new OrderContext()));
    }

    private static OperationContext context(String node, String button, String operation, String pk) {
        BusinessObjectRef object = new BusinessObjectRef("selected", "PU_PURCHASE_ORDER", pk, null, null);
        return new OperationContext("request", "batch", GROUP, ORG, node, "Purchase order", button,
                button, operation, null, Collections.singletonList(object));
    }

    private static OrderVO order(String pk, String org, int status) throws Exception {
        OrderVO order = lightOrder(pk, status);
        OrderHeaderVO header = order.getHVO();
        header.setPk_group(GROUP);
        header.setPk_org(org);
        header.setVbillcode("PO-001");
        return order;
    }

    private static OrderVO lightOrder(String pk, int status) throws Exception {
        OrderHeaderVO header = new OrderHeaderVO();
        // Use SuperVO's dynamic attributes without connecting to the NC metadata server.
        Field metadata = SuperVO.class.getDeclaredField("vometa");
        metadata.setAccessible(true);
        final IAttributeMeta attribute = (IAttributeMeta) Proxy.newProxyInstance(
                IAttributeMeta.class.getClassLoader(), new Class<?>[] { IAttributeMeta.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("isSerializable".equals(method.getName())) return true;
                        if ("isStatic".equals(method.getName())) return false;
                        check("getJavaType".equals(method.getName()), "unexpected attribute metadata call");
                        return JavaType.Object;
                    }
                });
        final IVOMetaStatisticInfo statistics = (IVOMetaStatisticInfo) Proxy.newProxyInstance(
                IVOMetaStatisticInfo.class.getClassLoader(), new Class<?>[] { IVOMetaStatisticInfo.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        check("getStaticAttributes".equals(method.getName()), "unexpected VO statistics call");
                        return new IAttributeMeta[0];
                    }
                });
        metadata.set(header, Proxy.newProxyInstance(IVOMeta.class.getClassLoader(),
                new Class<?>[] { IVOMeta.class }, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("getStatisticInfo".equals(method.getName())) return statistics;
                        check("getAttribute".equals(method.getName()), "unexpected VO metadata call");
                        return attribute;
                    }
                }));
        if (pk != null) header.setPk_order(pk);
        header.setStatus(status);
        OrderVO order = newOrderWithoutMetadataServer();
        order.setHVO(header);
        return order;
    }

    @SuppressWarnings("unchecked")
    public static OrderVO newOrderWithoutMetadataServer() throws Exception {
        BillMetaFactory factory = BillMetaFactory.getInstance();
        Field cache = BillMetaFactory.class.getDeclaredField("classIndex");
        cache.setAccessible(true);
        Map<Class<?>, IBillMeta> classes = (Map<Class<?>, IBillMeta>) cache.get(factory);
        IBillMeta metadata = (IBillMeta) Proxy.newProxyInstance(IBillMeta.class.getClassLoader(),
                new Class<?>[] { IBillMeta.class }, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        check("getChildren".equals(method.getName()), "unexpected order metadata call");
                        return new IVOMeta[0];
                    }
                });
        synchronized (factory) {
            IBillMeta original = classes.put(OrderVOMeta.class, metadata);
            try {
                return new OrderVO();
            } finally {
                if (original == null) {
                    classes.remove(OrderVOMeta.class);
                } else {
                    classes.put(OrderVOMeta.class, original);
                }
            }
        }
    }

    private static PurchaseOrderPfCommand command(String action, String type, OrderVO bill) {
        return new PurchaseOrderPfCommand(action, type, null, bill, null, null);
    }

    private static void rejectBeforePf(Fixture fixture, OperationContext context, Serializable command)
            throws Exception {
        try {
            fixture.adapter.execute(context, command);
            throw new AssertionError("Expected invalid command to fail");
        } catch (BusinessException expected) {
            check(fixture.calls == 0, "invalid command reached native PF");
        }
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }

    private static final class Fixture {
        final PurchaseOrderPilotAdapter adapter;
        int calls;
        Object[] arguments;
        String queriedPk;

        Fixture(final Object result, final OrderHeaderVO persisted) {
            this(result, persisted, persisted);
        }

        Fixture(final Object result, final OrderHeaderVO before, final OrderHeaderVO after) {
            IplatFormEntry pf = (IplatFormEntry) Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                    new Class<?>[] { IplatFormEntry.class }, new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) throws BusinessException {
                            check("processAction".equals(method.getName()), "unexpected native entry");
                            calls++;
                            arguments = args;
                            if (result instanceof BusinessException) {
                                throw (BusinessException) result;
                            }
                            return result;
                        }
                    });
            adapter = new PurchaseOrderPilotAdapter(pf, new PurchaseOrderPilotAdapter.OrderHeaderLookup() {
                public OrderHeaderVO find(String pk) {
                    queriedPk = pk;
                    return calls == 0 ? before : after;
                }
            });
        }
    }
}
