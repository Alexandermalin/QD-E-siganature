package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/** 销售订单其他按钮一次性授权交接的离线契约检查；不打开界面、不调用 NC 服务。 */
public final class SaleOrderActionHandoffTests {
    private static int assertions;
    private static final String G = SaleOrderPilotScope.GROUP_ID;
    private static final String O = SaleOrderPilotScope.ORG_ID;

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        SaleOrderActionHandoff handoff = new SaleOrderActionHandoff();
        for (String button : new String[] { "Delete", "Commit", "UnCommit", "Approve", "UnApprove",
                "Freeze", "UnFreeze", "BillClose" }) {
            OperationContext signed = signed(button, key(button), G, O);
            check(handoff.supports(signed), "each verified button must obtain a handoff: " + button);
            check(handoff.bind(signed), "first binding must be accepted: " + button);
            check(!handoff.bind(signed), "second binding must be rejected: " + button);
            handoff.reset();
        }

        SaleOrderActionHandoff save = new SaleOrderActionHandoff();
        check(!save.supports(context("40060301", "Save", "INSERT", G, O, "SO-1")),
                "the existing save pilot keeps its own handoff");
        check(!save.supports(unknownButton("Cancel")), "unverified buttons must not obtain a handoff");
        check(!save.supports(context("OTHER", "Freeze", "UPDATE", G, O, "SO-1")), "other nodes must not obtain a handoff");
        check(!save.supports(context("40060301", "Freeze", "UPDATE", G, "OTHER", "SO-1")), "other organizations must not obtain a handoff");
        check(!save.supports(context("40060301", "Commit", "INSERT", G, O, "SO-1")), "wrong operation domain must be rejected");
        check(!save.supports(context("40060301", "Commit", "UPDATE", G, O, null)), "missing signed order key must be rejected");
        check(!save.supports(twoObjects()), "multi-selection must be rejected until batch support is verified");
        check(!save.supports(childObject()), "child objects must not be signed as independent targets");
        check(!save.bind(context("40060301", "Freeze", "UPDATE", G, O, "SO-1")),
                "a context without a server request id must not bind");
        check(save.context() == null, "rejected binding must not retain an authorization");

        SaleOrderActionHandoff once = new SaleOrderActionHandoff();
        OperationContext signed = signed("Freeze", "SO-1", G, O);
        check(once.bind(signed), "verified freeze must bind");
        check(signed == once.context(), "bound authorization must be observable for re-checks");
        expectRejected(once, "UnFreeze", G, O, "SO-1");
        expectRejected(once, "Freeze", G, "OTHER", "SO-1");
        expectRejected(once, "Freeze", G, O, "SO-2");
        String request = once.dispatch("Freeze", G, O, "SO-1");
        check("R".equals(request) && once.wasDispatched("R"), "authorized freeze must dispatch once");
        expectRejected(once, "Freeze", G, O, "SO-1");
        check(!once.wasDispatched("OTHER"), "only the dispatched request is reported");
        once.release("OTHER");
        check(once.wasDispatched("R"), "another request must not release the handoff");
        once.release("R");
        check(once.context() == null && !once.wasDispatched("R"), "releasing the dispatched request must clear the handoff");
        expectRejected(once, "Freeze", G, O, "SO-1");

        System.out.println("PASS SaleOrderActionHandoffTests assertions=" + assertions
                + " (offline handoff contract; no NC client or service invoked)");
    }

    private static String key(String button) { return "SO-" + button; }

    private static OperationContext signed(String button, String pk, String group, String org) {
        return context("40060301", button, SaleOrderPilotScope.operationTypeFor("40060301", button),
                group, org, pk).withRequest("R", "B", G);
    }

    private static OperationContext context(String node, String button, String operation,
            String group, String org, String pk) {
        return new OperationContext(null, null, group, org, node, "销售订单维护", button,
                SaleOrderPilotScope.buttonName(button), operation, null,
                Collections.singletonList(new BusinessObjectRef("draft", "SO_SALE_ORDER", pk, "SO-CODE", null)));
    }

    private static OperationContext unknownButton(String button) {
        return context("40060301", button, "UPDATE", G, O, "SO-1");
    }

    private static OperationContext twoObjects() {
        List<BusinessObjectRef> objects = new ArrayList<BusinessObjectRef>();
        objects.add(new BusinessObjectRef("a", "SO_SALE_ORDER", "SO-1", "SO-CODE", null));
        objects.add(new BusinessObjectRef("b", "SO_SALE_ORDER", "SO-2", "SO-CODE2", null));
        return new OperationContext(null, null, G, O, "40060301", "销售订单维护", "Commit", "提交",
                "UPDATE", null, objects);
    }

    private static OperationContext childObject() {
        List<BusinessObjectRef> objects = new ArrayList<BusinessObjectRef>();
        objects.add(new BusinessObjectRef("root", "SO_SALE_ORDER", "SO-1", "SO-CODE", null));
        objects.add(new BusinessObjectRef("line", "SO_SALE_ORDER_B", "SO-1-B1", null, "root"));
        return new OperationContext(null, null, G, O, "40060301", "销售订单维护", "Commit", "提交",
                "UPDATE", null, objects);
    }

    private static void expectRejected(SaleOrderActionHandoff handoff, String button, String group,
            String org, String pk) throws Exception {
        try { handoff.dispatch(button, group, org, pk); } catch (BusinessException expected) { assertions++; return; }
        throw new AssertionError("unsafe or duplicate sales-order action was dispatched");
    }

    private static void check(boolean ok, String message) {
        if (!ok) { throw new AssertionError(message); }
        assertions++;
    }
}
