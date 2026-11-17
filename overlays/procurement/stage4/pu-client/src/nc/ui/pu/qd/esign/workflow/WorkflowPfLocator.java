package nc.ui.pu.qd.esign.workflow;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.framework.exception.ComponentException;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pub.BusinessException;

/** Intercepts final PF writes on the native action thread or the native batch worker. */
public final class WorkflowPfLocator extends NCLocator {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<Scope>();

    interface Call { Object run() throws BusinessException; }
    interface Dispatch {
        Object execute(Method method, Object[] args, Call original) throws BusinessException;
    }

    static Scope open(Dispatch dispatch) throws BusinessException {
        if (CURRENT.get() != null) throw new BusinessException("采购工作流正在处理。");
        InvocationInfoProxy info = InvocationInfoProxy.getInstance();
        Scope scope = new Scope(dispatch, NCLocator.getInstance(), info.getProperty(LOCATOR_PROVIDER_PROPERTY));
        CURRENT.set(scope);
        boolean installed = false;
        try {
            info.setProperty(LOCATOR_PROVIDER_PROPERTY, WorkflowPfLocator.class.getName());
            if (!(NCLocator.getInstance() instanceof WorkflowPfLocator)) {
                throw new BusinessException("无法安装采购工作流签章入口。");
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
        if (scope == null) throw new ComponentException("Missing procurement workflow scope");
        final Object target = scope.original.lookup(name);
        if (!IplatFormEntry.class.getName().equals(name)) return target;
        return Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(), new Class<?>[] {IplatFormEntry.class},
                new InvocationHandler() {
                    @Override public Object invoke(Object proxy, final Method method, final Object[] args)
                            throws Throwable {
                        String name = method.getName();
                        if (!"processAction".equals(name) && !"processBatch".equals(name)) {
                            return invokeNative(target, method, args);
                        }
                        if (CURRENT.get() != scope) throw new BusinessException("采购工作流已离开原动作线程。");
                        return scope.dispatch.execute(method, args, new Call() {
                            @Override public Object run() throws BusinessException {
                                return invokeNative(target, method, args);
                            }
                        });
                    }
                });
    }

    private static Object invokeNative(Object target, Method method, Object[] args) throws BusinessException {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof BusinessException) throw (BusinessException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw failure(cause);
        } catch (IllegalAccessException ex) {
            throw failure(ex);
        }
    }

    static BusinessException failure(Throwable cause) {
        BusinessException failure = new BusinessException("采购工作流执行失败。" + cause.getMessage());
        failure.initCause(cause);
        return failure;
    }

    static final class Scope implements AutoCloseable {
        private final Dispatch dispatch;
        private final NCLocator original;
        private final String previous;

        Scope(Dispatch dispatch, NCLocator original, String previous) {
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
