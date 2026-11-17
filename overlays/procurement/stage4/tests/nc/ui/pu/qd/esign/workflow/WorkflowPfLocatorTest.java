package nc.ui.pu.qd.esign.workflow;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Properties;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.workflownote.WorkflownoteVO;

public final class WorkflowPfLocatorTest {
    private static int mutations;

    public static final class NativeLocator extends NCLocator {
        @Override protected void init(Properties values) { }

        @Override public Object lookup(String name) {
            if (!IplatFormEntry.class.getName().equals(name)) return name;
            return Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(), new Class<?>[] {IplatFormEntry.class},
                    new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method method, Object[] args) {
                            if (method.getName().startsWith("process")) mutations++;
                            return null;
                        }
                    });
        }
    }

    public static void main(String[] args) throws Exception {
        InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
        String key = NCLocator.LOCATOR_PROVIDER_PROPERTY;
        String old = invocation.getProperty(key);
        invocation.setProperty(key, NativeLocator.class.getName());
        final WorkflownoteVO note = new WorkflownoteVO();
        final String[] intercepted = new String[2];
        try {
            try (WorkflowPfLocator.Scope scope = WorkflowPfLocator.open(new WorkflowPfLocator.Dispatch() {
                @Override public Object execute(Method method, Object[] arguments, WorkflowPfLocator.Call original)
                        throws BusinessException {
                    if (arguments[2] != note) throw new AssertionError("the original approval note must survive");
                    if ("processAction".equals(method.getName())) {
                        intercepted[0] = (String) arguments[0];
                        throw new BusinessException("signature cancelled");
                    }
                    intercepted[1] = (String) arguments[0];
                    return original.run();
                }
            })) {
                IplatFormEntry entry = NCLocator.getInstance().lookup(IplatFormEntry.class);
                entry.queryValidCheckers("21", "bill");
                if (intercepted[0] != null || intercepted[1] != null) throw new AssertionError("dialog lookup intercepted");
                try {
                    entry.processAction("APPROVE", "21", note, null, null, new HashMap<Object,Object>());
                    throw new AssertionError("cancelled approval reached PF");
                } catch (BusinessException expected) {
                    if (mutations != 0 || !"APPROVE".equals(intercepted[0])) throw expected;
                }
                entry.processBatch("UNAPPROVE", "21", note, new AggregatedValueObject[0], null, null);
                if (mutations != 1 || !"UNAPPROVE".equals(intercepted[1])) throw new AssertionError("native batch");
            }
            if (!(NCLocator.getInstance() instanceof NativeLocator)) throw new AssertionError("native scope restored");
        } finally {
            invocation.setProperty(key, old);
        }
    }
}
