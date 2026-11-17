package nc.ui.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;

/** 选择范围判定的离线契约检查：混合组织与无法判定归属必须拒绝，禁止回落原生写入。 */
public final class SaleOrderSelectionTests {
    private static int assertions;
    private static final String G = nc.vo.uapbd.qd.esign.SaleOrderPilotScope.GROUP_ID;
    private static final String O = nc.vo.uapbd.qd.esign.SaleOrderPilotScope.ORG_ID;

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        check(SaleOrderSelection.classify(G, Collections.singletonList(O)) == SaleOrderSelection.Kind.CONTROLLED_SINGLE,
                "one pilot order enters the controlled path");
        check(SaleOrderSelection.classify(G, Arrays.asList(O, O)) == SaleOrderSelection.Kind.CONTROLLED_MULTI,
                "multiple pilot orders are classified as controlled multi");
        check(SaleOrderSelection.classify(G, Collections.singletonList("OTHER")) == SaleOrderSelection.Kind.NATIVE,
                "orders outside the pilot keep the native path");
        check(SaleOrderSelection.classify(G, Arrays.asList("OTHER", "OTHER2")) == SaleOrderSelection.Kind.NATIVE,
                "multiple outside orders keep the native path");
        check(SaleOrderSelection.classify(G, Arrays.asList(O, "OTHER")) == SaleOrderSelection.Kind.REJECT_MIXED,
                "mixed pilot and outside orders must be rejected");
        check(SaleOrderSelection.classify(G, Arrays.asList("OTHER", O)) == SaleOrderSelection.Kind.REJECT_MIXED,
                "mixed order sequences are rejected regardless of order");
        check(SaleOrderSelection.classify(G, null) == SaleOrderSelection.Kind.REJECT_UNKNOWN,
                "missing selection is rejected");
        check(SaleOrderSelection.classify(G, Collections.<String>emptyList()) == SaleOrderSelection.Kind.REJECT_UNKNOWN,
                "empty selection is rejected");
        check(SaleOrderSelection.classify(null, Collections.singletonList(O)) == SaleOrderSelection.Kind.REJECT_UNKNOWN,
                "missing group is rejected");
        check(SaleOrderSelection.classify(G, Arrays.asList(O, (String) null)) == SaleOrderSelection.Kind.REJECT_UNKNOWN,
                "null organization is rejected instead of guessing");
        check(SaleOrderSelection.classify(G, Arrays.asList(O, "  ")) == SaleOrderSelection.Kind.REJECT_UNKNOWN,
                "blank organization is rejected instead of guessing");
        check(SaleOrderSelection.isControlled(SaleOrderSelection.Kind.CONTROLLED_SINGLE)
                && SaleOrderSelection.isControlled(SaleOrderSelection.Kind.CONTROLLED_MULTI),
                "controlled kinds enter the signing interceptor");
        check(!SaleOrderSelection.isControlled(SaleOrderSelection.Kind.NATIVE)
                && !SaleOrderSelection.isControlled(SaleOrderSelection.Kind.REJECT_MIXED)
                && !SaleOrderSelection.isControlled(SaleOrderSelection.Kind.REJECT_UNKNOWN),
                "native and rejected kinds do not enter the signing interceptor");
        check(SaleOrderSelection.rejectionMessage(SaleOrderSelection.Kind.REJECT_MIXED).indexOf("混合组织") >= 0,
                "mixed rejection explains the organization problem");
        check(SaleOrderSelection.rejectionMessage(SaleOrderSelection.Kind.REJECT_UNKNOWN).indexOf("归属") >= 0,
                "unknown rejection explains the ambiguous ownership");
        System.out.println("PASS SaleOrderSelectionTests assertions=" + assertions
                + " (offline selection classification; no NC client or service invoked)");
    }

    private static void check(boolean ok, String message) {
        if (!ok) { throw new AssertionError(message); }
        assertions++;
    }
}
