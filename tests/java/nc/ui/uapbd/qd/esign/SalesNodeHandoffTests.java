package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;

/**
 * 销售管理其他三节点客户端一次性交接与选择判定的离线契约检查。
 *
 * <p>只验证授权绑定/派发/释放与混合组织拒绝逻辑；不打开 NC 窗口、不调用签名服务或业务 RPC。</p>
 */
public final class SalesNodeHandoffTests {
    private interface Checked { void run() throws Exception; }
    private static int assertions;

    private static final String GROUP = SalesOtherNodeScope.GROUP_ID;
    private static final String ORG = "0001A9100000003UDS6R";
    private static final String OTHER_ORG = "0001A9100000003EPIVQ";

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        handoffAcceptsSingleRoot();
        handoffRejectsForeignShapes();
        handoffDispatchAndRelease();
        selectionKinds();
        System.out.println("PASS SalesNodeHandoffTests assertions=" + assertions
                + " (client authorization handoff and selection only; no NC window or RPC invoked)");
    }

    /** 受控单笔根对象（可带已核实行）必须被接受。 */
    private static void handoffAcceptsSingleRoot() throws Exception {
        SalesNodeActionHandoff close = new SalesNodeActionHandoff(SalesOtherNodeScope.CLOSE_NODE);
        check(close.supports(context("40060303", "BillClose", "UPDATE", "REQ-1",
                list(root("r1", "SO_SALE_ORDER", "O1")))), "bill close context is supported");
        check(close.supports(context("40060303", "LineClose", "UPDATE", "REQ-1",
                Arrays.asList(root("r1", "SO_SALE_ORDER", "O1"), line("l1", "r1", "SO_SALE_ORDER_B", "L1")))),
                "line close context is supported");
        SalesNodeActionHandoff delivery = new SalesNodeActionHandoff(SalesOtherNodeScope.DELIVERY_NODE);
        check(delivery.supports(context("40060402", "OutClose", "UPDATE", "REQ-1",
                Arrays.asList(root("r1", "SO_DELIVERY", "D1"), line("l1", "r1", "SO_DELIVERY_B", "DL1")))),
                "delivery row close context is supported");
        SalesNodeActionHandoff invoice = new SalesNodeActionHandoff(SalesOtherNodeScope.INVOICE_NODE);
        check(invoice.supports(context("40060501", "Approve", "UPDATE", "REQ-1",
                list(root("r1", "SO_SALE_INVOICE", "I1")))), "invoice approve context is supported");
        check(!close.supports(null), "missing context is rejected");
    }

    /** 错节点、未登记按钮、错动作域、双根、缺主键、子对象挂错根都必须拒绝。 */
    private static void handoffRejectsForeignShapes() throws Exception {
        SalesNodeActionHandoff close = new SalesNodeActionHandoff(SalesOtherNodeScope.CLOSE_NODE);
        check(!close.supports(context("40060301", "BillClose", "UPDATE", "REQ-1",
                list(root("r1", "SO_SALE_ORDER", "O1")))), "foreign node is rejected");
        check(!close.supports(context("40060303", "Delete", "DELETE", "REQ-1",
                list(root("r1", "SO_SALE_ORDER", "O1")))), "unregistered button is rejected");
        check(!close.supports(context("40060303", "BillClose", "DELETE", "REQ-1",
                list(root("r1", "SO_SALE_ORDER", "O1")))), "wrong action domain is rejected");
        check(!close.supports(context("40060303", "LineClose", "UPDATE", "REQ-1",
                Arrays.asList(root("r1", "SO_SALE_ORDER", "O1"), root("r2", "SO_SALE_ORDER", "O2")))),
                "two roots are rejected");
        check(!close.supports(context("40060303", "BillClose", "UPDATE", "REQ-1",
                list(root("r1", "SO_DELIVERY", "O1")))), "root with a foreign object type is rejected");
        expectInvalidContext(new Checked() { public void run() {
            context("40060303", "LineClose", "UPDATE", "REQ-1",
                    Arrays.asList(root("r1", "SO_SALE_ORDER", "O1"), line("l1", "other", "SO_SALE_ORDER_B", "L1")));
        }}, "line under an unknown root is rejected by the context");
        check(!close.supports(context("40060303", "LineClose", "UPDATE", "REQ-1",
                Arrays.asList(root("r1", "SO_SALE_ORDER", "O1"), line("l1", "r1", "SO_DELIVERY_B", "L1")))),
                "line of another node type is rejected");
        expectInvalidContext(new Checked() { public void run() {
            context("40060303", "BillClose", "UPDATE", "REQ-1", list(line("l1", "r1", "SO_SALE_ORDER_B", "L1")));
        }}, "context without a root is rejected");
        check(!close.supports(context("40060303", "BillClose", "UPDATE", "REQ-1",
                list(root("r1", "SO_SALE_ORDER", null)))), "missing root primary key is rejected");
    }

    /** 一次绑定、一次派发、按请求号释放；变化或重复派发必须拒绝。 */
    private static void handoffDispatchAndRelease() throws Exception {
        final SalesNodeActionHandoff handoff = new SalesNodeActionHandoff(SalesOtherNodeScope.CLOSE_NODE);
        final OperationContext unsigned = context("40060303", "BillClose", "UPDATE", null,
                list(root("r1", "SO_SALE_ORDER", "O1")));
        final OperationContext signed = unsigned.withRequest("REQ-1", null, GROUP);
        check(handoff.context() == null, "no authorization is bound before signing");
        check(!handoff.bind(unsigned), "binding an unsigned context is rejected");
        expectFailure(new Checked() { public void run() throws Exception {
            handoff.dispatch("BillClose", GROUP, ORG, "O1");
        }}, "dispatch without an authorization is rejected");
        check(handoff.bind(signed), "signed authorization binds once");
        check(!handoff.bind(signed), "second bind is rejected");
        check("REQ-1".equals(handoff.dispatch("BillClose", GROUP, ORG, "O1")), "matching dispatch returns the request id");
        check(handoff.wasDispatched("REQ-1"), "dispatch is observable");
        check(!handoff.wasDispatched("OTHER"), "unknown request is not reported as dispatched");
        final SalesNodeActionHandoff changed = new SalesNodeActionHandoff(SalesOtherNodeScope.CLOSE_NODE);
        check(changed.bind(signed), "fresh handoff binds the signed context");
        expectFailure(new Checked() { public void run() throws Exception {
            changed.dispatch("LineClose", GROUP, ORG, "O1");
        }}, "dispatch with another button is rejected");
        final SalesNodeActionHandoff byOrg = new SalesNodeActionHandoff(SalesOtherNodeScope.CLOSE_NODE);
        check(byOrg.bind(signed), "handoff for organization check binds");
        expectFailure(new Checked() { public void run() throws Exception {
            byOrg.dispatch("BillClose", GROUP, OTHER_ORG, "O1");
        }}, "dispatch with another organization is rejected");
        final SalesNodeActionHandoff byObject = new SalesNodeActionHandoff(SalesOtherNodeScope.CLOSE_NODE);
        check(byObject.bind(signed), "handoff for object check binds");
        expectFailure(new Checked() { public void run() throws Exception {
            byObject.dispatch("BillClose", GROUP, ORG, "O2");
        }}, "dispatch with another object is rejected");
        check("O1".equals(byObject.rootObjectPk()), "root object key is exposed for re-checks");
        byObject.release("OTHER");
        check(byObject.context() != null, "release with another request keeps the authorization");
        byObject.release("REQ-1");
        check(byObject.context() == null, "release with the bound request clears the authorization");
        OperationContext second = unsigned.withRequest("REQ-2", null, GROUP);
        check(byObject.bind(second), "released handoff can bind the next authorized request");
        byObject.reset();
        check(byObject.context() == null && !byObject.wasDispatched("REQ-2"),
                "reset clears authorization and dispatch state");
        boolean unsupportedRejected = false;
        try { new SalesNodeActionHandoff("40060301"); } catch (IllegalArgumentException expected) { unsupportedRejected = true; }
        check(unsupportedRejected, "an unsupported node cannot be handed off");
    }

    /** 选择判定：单笔、多笔、混合组织与无法判定。 */
    private static void selectionKinds() throws Exception {
        List<String> single = Collections.singletonList(ORG);
        List<String> multi = Arrays.asList(ORG, ORG);
        List<String> mixed = Arrays.asList(ORG, OTHER_ORG);
        check(SalesNodeSelection.classify(GROUP, single) == SalesNodeSelection.Kind.CONTROLLED_SINGLE,
                "single bill is controlled");
        check(SalesNodeSelection.classify(GROUP, multi) == SalesNodeSelection.Kind.CONTROLLED_MULTI,
                "multi bill is controlled-multi");
        check(SalesNodeSelection.classify(GROUP, mixed) == SalesNodeSelection.Kind.REJECT_MIXED,
                "mixed organizations are rejected");
        check(SalesNodeSelection.classify(GROUP, Arrays.asList(ORG, " ")) == SalesNodeSelection.Kind.REJECT_UNKNOWN,
                "blank organization is unknown");
        check(SalesNodeSelection.classify(null, single) == SalesNodeSelection.Kind.REJECT_UNKNOWN,
                "missing group is unknown");
        check(SalesNodeSelection.classify(GROUP, new ArrayList<String>()) == SalesNodeSelection.Kind.REJECT_UNKNOWN,
                "empty selection is unknown");
        check(SalesNodeSelection.isControlled(SalesNodeSelection.Kind.CONTROLLED_SINGLE), "single is controlled");
        check(SalesNodeSelection.isControlled(SalesNodeSelection.Kind.CONTROLLED_MULTI),
                "multi enters the controlled path");
        check(!SalesNodeSelection.isControlled(SalesNodeSelection.Kind.REJECT_MIXED),
                "mixed does not enter the controlled path");
        check(SalesNodeSelection.rejectionMessage(SalesNodeSelection.Kind.REJECT_MIXED).length() > 0,
                "mixed message exists");
        check(SalesNodeSelection.rejectionMessage(SalesNodeSelection.Kind.REJECT_UNKNOWN).length() > 0,
                "unknown message exists");
    }

    private static List<BusinessObjectRef> list(BusinessObjectRef ref) {
        return Collections.singletonList(ref);
    }

    private static OperationContext context(String node, String button, String operation, String requestId,
            List<BusinessObjectRef> objects) {
        return new OperationContext(requestId, null, GROUP, ORG, node, SalesOtherNodeScope.nodeName(node), button,
                SalesOtherNodeScope.buttonName(button), operation, null, objects);
    }

    private static BusinessObjectRef root(String clientId, String type, String pk) {
        return new BusinessObjectRef(clientId, type, pk, pk == null ? null : "CODE-" + pk, null);
    }

    private static BusinessObjectRef line(String clientId, String parentId, String type, String pk) {
        return new BusinessObjectRef(clientId, type, pk, null, parentId);
    }

    private static void expectFailure(Checked action, String message) throws Exception {
        assertions++;
        try {
            action.run();
        } catch (BusinessException expected) {
            return;
        }
        throw new IllegalStateException("FAILED: expected rejection: " + message);
    }

    private static void expectInvalidContext(Checked action, String message) throws Exception {
        assertions++;
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new IllegalStateException("FAILED: expected invalid context: " + message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new IllegalStateException("FAILED: " + message); }
    }
}
