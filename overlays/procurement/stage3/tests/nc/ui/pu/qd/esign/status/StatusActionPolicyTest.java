package nc.ui.pu.qd.esign.status;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.itf.uapbd.qd.esign.IPolicyService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;

public final class StatusActionPolicyTest {
    public static void main(String[] args) throws Exception {
        List<OperationContext> batch = Arrays.asList(context("ORG1"), context("ORG2"));
        List<String> checked = new ArrayList<String>();
        StatusActionBridge.requireUncontrolledOrganizations(batch, policies(checked, null));
        check(checked.equals(Arrays.asList("ORG1", "ORG2")), "every affected business unit is checked");
        checked.clear();
        try {
            StatusActionBridge.requireUncontrolledOrganizations(batch, policies(checked, "ORG2"));
            throw new AssertionError("cross-organization batch must not fall through when one policy is on");
        } catch (BusinessException expected) {
            check(checked.equals(Arrays.asList("ORG1", "ORG2")), "the second organization's policy is considered");
        }
    }

    private static OperationContext context(String org) {
        BusinessObjectRef ref = new BusinessObjectRef(org, "PU_PRAYBILL", "pk-" + org, "code", null);
        return new OperationContext(null, null, "G", org, "40040200", "请购单维护", "BillClose", "整单关闭",
                "BILL_CLOSE", null, Collections.singletonList(ref));
    }

    private static IPolicyService policies(final List<String> checked, final String controlled) {
        return (IPolicyService) Proxy.newProxyInstance(IPolicyService.class.getClassLoader(),
                new Class<?>[] {IPolicyService.class}, new InvocationHandler() {
                    @Override public Object invoke(Object proxy, Method method, Object[] args) {
                        check("resolve".equals(method.getName()) && "40040200".equals(args[1])
                                && "BillClose".equals(args[2]), "resolve policy for exact status button");
                        String org = (String) args[0];
                        checked.add(org);
                        return new PolicyDecision(true, org.equals(controlled), false, false, true, 1L);
                    }
                });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
