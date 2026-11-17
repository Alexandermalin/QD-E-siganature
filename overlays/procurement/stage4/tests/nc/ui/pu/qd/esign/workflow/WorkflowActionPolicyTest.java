package nc.ui.pu.qd.esign.workflow;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import nc.itf.uapbd.qd.esign.IPolicyService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.PolicyDecision;

public final class WorkflowActionPolicyTest {
    public static void main(String[] args) throws Exception {
        Set<String> orgs = new LinkedHashSet<String>(Arrays.asList("O1", "O2"));
        Set<String> checked = new LinkedHashSet<String>();
        WorkflowActionBridge.requireUncontrolled(policy(checked, null), orgs, "40040400", "Approve", "single only");
        if (!checked.equals(orgs)) throw new AssertionError("all organizations must be checked");
        checked.clear();
        try {
            WorkflowActionBridge.requireUncontrolled(policy(checked, "O2"), orgs, "40040400", "Approve", "single only");
            throw new AssertionError("controlled organization must block the whole native batch");
        } catch (BusinessException expected) {
            if (!checked.equals(orgs) || !"single only".equals(expected.getMessage())) throw expected;
        }
        String user = "1001A110000000000001";
        if (!WorkflowActionBridge.matchesPfAction("APPROVE", "APPROVE" + user, user)
                || !WorkflowActionBridge.matchesPfAction("UNAPPROVE", "UNAPPROVE", user)
                || WorkflowActionBridge.matchesPfAction("SAVE", "SAVE" + user, user)
                || WorkflowActionBridge.matchesPfAction("APPROVE", "APPROVE" + user, "1001A110000000000002")) {
            throw new AssertionError("approval suffix must be the current NC user");
        }
    }

    private static IPolicyService policy(final Set<String> checked, final String controlled) {
        return (IPolicyService) Proxy.newProxyInstance(IPolicyService.class.getClassLoader(),
                new Class<?>[] {IPolicyService.class}, new InvocationHandler() {
                    @Override public Object invoke(Object proxy, Method method, Object[] args) {
                        if (!"resolve".equals(method.getName()) || !"40040400".equals(args[1])
                                || !"Approve".equals(args[2])) throw new AssertionError("exact policy route");
                        String org = (String) args[0];
                        checked.add(org);
                        return new PolicyDecision(true, false, false, controlled != null && controlled.equals(org), false, 1L);
                    }
                });
    }
}
