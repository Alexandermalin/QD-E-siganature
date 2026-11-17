package nc.ui.pu.qd.esign.status;

import java.lang.reflect.Method;
import java.util.Properties;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.vo.pub.BusinessException;

public final class StatusServiceLocatorTest {
    public interface NativeService {
        String close(String reason) throws BusinessException;
        String read();
    }

    public static final class NativeLocator extends NCLocator {
        @Override protected void init(Properties properties) { }

        @Override public Object lookup(String name) {
            if (!NativeService.class.getName().equals(name)) return name;
            return new NativeService() {
                @Override public String close(String reason) { return "native:" + reason; }
                @Override public String read() { return "read"; }
            };
        }
    }

    public static void main(String[] args) throws Exception {
        InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
        String key = NCLocator.LOCATOR_PROVIDER_PROPERTY;
        String previous = invocation.getProperty(key);
        invocation.setProperty(key, NativeLocator.class.getName());
        try {
            exactMutationIsInterceptedAndReadIsNative();
            failedAuthorizationDoesNotReachNativeAndRestoresLocator();
        } finally {
            invocation.setProperty(key, previous);
        }
    }

    private static void exactMutationIsInterceptedAndReadIsNative() throws Exception {
        try (StatusServiceLocator.Scope scope = StatusServiceLocator.open(NativeService.class.getName(), "close",
                new StatusServiceLocator.Dispatch() {
                    @Override public Object execute(Method method, Object[] arguments, StatusServiceLocator.Call original)
                            throws Exception {
                        check("close".equals(method.getName()) && "原始事由".equals(arguments[0]), "native input intact");
                        return original.run();
                    }
                })) {
            NCLocator locator = NCLocator.getInstance();
            check("unrelated".equals(locator.lookup("unrelated")), "unrelated services remain native");
            NativeService service = locator.lookup(NativeService.class);
            check("read".equals(service.read()), "only actual mutation is intercepted");
            check("native:原始事由".equals(service.close("原始事由")), "native result intact when bypassed");
        }
    }

    private static void failedAuthorizationDoesNotReachNativeAndRestoresLocator() throws Exception {
        try (StatusServiceLocator.Scope scope = StatusServiceLocator.open(NativeService.class.getName(), "close",
                new StatusServiceLocator.Dispatch() {
                    @Override public Object execute(Method method, Object[] arguments, StatusServiceLocator.Call original)
                            throws Exception {
                        throw new BusinessException("拒绝授权");
                    }
                })) {
            NCLocator.getInstance().lookup(NativeService.class).close("原始事由");
            throw new AssertionError("native close reached after refusal");
        } catch (BusinessException expected) {
            check(NCLocator.getInstance() instanceof NativeLocator, "native locator restored after refusal");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
