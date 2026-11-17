package nc.ui.pu.qd.esign.status;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.framework.exception.ComponentException;
import nc.vo.pub.BusinessException;

/** Replaces one native mutation on the current action thread. Other service methods stay native. */
public final class StatusServiceLocator extends NCLocator {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<Scope>();

    interface Call { Object run() throws Exception; }
    interface Dispatch { Object execute(Method method, Object[] args, Call original) throws Exception; }

    static Scope open(String service, String method, Dispatch dispatch) throws Exception {
        if (CURRENT.get() != null) throw new BusinessException("采购状态操作正在处理。");
        Class<?> api = Class.forName(service, false, StatusServiceLocator.class.getClassLoader());
        InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
        Scope scope = new Scope(api, method, dispatch, NCLocator.getInstance(),
                invocation.getProperty(LOCATOR_PROVIDER_PROPERTY));
        CURRENT.set(scope);
        boolean installed = false;
        try {
            invocation.setProperty(LOCATOR_PROVIDER_PROPERTY, StatusServiceLocator.class.getName());
            if (!(NCLocator.getInstance() instanceof StatusServiceLocator)) {
                throw new BusinessException("无法安装采购状态操作签章入口。");
            }
            installed = true;
            return scope;
        } finally {
            if (!installed) scope.close();
        }
    }

    @Override protected void init(Properties properties) { }

    @Override public Object lookup(String name) throws ComponentException {
        final Scope scope = CURRENT.get();
        if (scope == null) throw new ComponentException("Missing procurement status scope");
        final Object target = scope.original.lookup(name);
        if (!scope.api.getName().equals(name)) return target;
        if (!scope.api.isInstance(target)) throw new ComponentException("Unexpected procurement status service");
        return Proxy.newProxyInstance(scope.api.getClassLoader(), new Class<?>[] {scope.api}, new InvocationHandler() {
            @Override public Object invoke(Object proxy, final Method method, final Object[] args) throws Exception {
                Call original = new Call() {
                    @Override public Object run() throws Exception { return invokeNative(target, method, args); }
                };
                if (!scope.method.equals(method.getName())) return original.run();
                if (CURRENT.get() != scope) throw new BusinessException("采购状态授权已离开当前操作线程。");
                return scope.dispatch.execute(method, args, original);
            }
        });
    }

    private static Object invokeNative(Object target, Method method, Object[] args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw new IllegalStateException(cause);
        }
    }

    static final class Scope implements AutoCloseable {
        private final Class<?> api;
        private final String method;
        private final Dispatch dispatch;
        private final NCLocator original;
        private final String previous;

        private Scope(Class<?> api, String method, Dispatch dispatch, NCLocator original, String previous) {
            this.api = api;
            this.method = method;
            this.dispatch = dispatch;
            this.original = original;
            this.previous = previous;
        }

        @Override public void close() {
            if (CURRENT.get() == this) {
                try {
                    InvocationInfoProxy.getInstance().setProperty(LOCATOR_PROVIDER_PROPERTY, previous);
                } finally {
                    CURRENT.remove();
                }
            }
        }
    }
}
