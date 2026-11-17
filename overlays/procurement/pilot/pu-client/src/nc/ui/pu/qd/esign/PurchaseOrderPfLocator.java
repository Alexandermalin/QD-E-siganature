package nc.ui.pu.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.framework.exception.ComponentException;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pub.BusinessException;

/** Substitutes only the final PF write during a controlled purchase-order save. */
public final class PurchaseOrderPfLocator extends NCLocator {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<Scope>();

    interface Dispatch {
        Object execute(Object[] arguments, IplatFormEntry nativePf) throws BusinessException;
    }

    static Scope open(Dispatch dispatch) throws BusinessException {
        if (ACTIVE.get() != null) throw new BusinessException("采购订单保存正在处理中。");
        InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
        Scope scope = new Scope(NCLocator.getInstance(), invocation.getProperty(LOCATOR_PROVIDER_PROPERTY), dispatch);
        ACTIVE.set(scope);
        boolean installed = false;
        try {
            invocation.setProperty(LOCATOR_PROVIDER_PROPERTY, PurchaseOrderPfLocator.class.getName());
            if (!(NCLocator.getInstance() instanceof PurchaseOrderPfLocator)) {
                throw new BusinessException("无法安装采购订单流程执行入口。");
            }
            installed = true;
            return scope;
        } finally {
            if (!installed) scope.close();
        }
    }

    @Override protected void init(Properties properties) { }

    @Override public Object lookup(String name) throws ComponentException {
        final Scope scope = ACTIVE.get();
        if (scope == null) throw new ComponentException("Missing purchase-order PF scope");
        final Object target = scope.original.lookup(name);
        if (!IplatFormEntry.class.getName().equals(name)) return target;
        return Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                new Class<?>[] { IplatFormEntry.class }, new InvocationHandler() {
                    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("processAction".equals(method.getName()) && args[3] instanceof OrderVO
                                && (scope.order == args[3] || isPurchaseSave(args[0]))) {
                            return scope.execute(args, (IplatFormEntry) target);
                        }
                        try {
                            return method.invoke(target, args);
                        } catch (InvocationTargetException ex) {
                            throw ex.getCause();
                        }
                    }
                });
    }

    static boolean isPurchaseSave(Object action) {
        return "SAVEBASE".equals(action) || "REVISE".equals(action);
    }

    static final class Scope implements AutoCloseable {
        private final NCLocator original;
        private final String previousProvider;
        private final Dispatch dispatch;
        private String action;
        private String billType;
        private OrderVO order;
        private boolean consumed;

        private Scope(NCLocator original, String previousProvider, Dispatch dispatch) {
            this.original = original;
            this.previousProvider = previousProvider;
            this.dispatch = dispatch;
        }

        void expect(String action, String billType, OrderVO order) {
            this.action = action;
            this.billType = billType;
            this.order = order;
            this.consumed = false;
        }

        private Object execute(Object[] args, IplatFormEntry nativePf) throws BusinessException {
            if (ACTIVE.get() != this || consumed || order != args[3] || !action.equals(args[0])
                    || !billType.equals(args[1])) {
                throw new BusinessException("采购订单流程参数与本次签名授权不一致。");
            }
            consumed = true;
            return dispatch.execute(args, nativePf);
        }

        @Override public void close() {
            if (ACTIVE.get() == this) {
                try {
                    InvocationInfoProxy.getInstance().setProperty(LOCATOR_PROVIDER_PROPERTY, previousProvider);
                } finally {
                    ACTIVE.remove();
                }
            }
        }
    }
}
