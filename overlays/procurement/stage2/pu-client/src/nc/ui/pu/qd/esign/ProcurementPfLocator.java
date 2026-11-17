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
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;

/** Installs a thread-local substitution for the final native PF call. */
public final class ProcurementPfLocator extends NCLocator {
    private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<Scope>();

    interface Dispatch {
        Object execute(Object[] arguments, IplatFormEntry nativePf) throws BusinessException;
    }

    static Scope open(ProcurementActionGuard.Kind kind, Dispatch dispatch) throws BusinessException {
        if (ACTIVE.get() != null) throw new BusinessException("采购保存或删除正在处理中。");
        InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
        Scope scope = new Scope(NCLocator.getInstance(), invocation.getProperty(LOCATOR_PROVIDER_PROPERTY),
                kind, dispatch);
        ACTIVE.set(scope);
        boolean installed = false;
        try {
            invocation.setProperty(LOCATOR_PROVIDER_PROPERTY, ProcurementPfLocator.class.getName());
            if (!(NCLocator.getInstance() instanceof ProcurementPfLocator)) {
                throw new BusinessException("无法安装采购流程执行入口。");
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
        if (scope == null) throw new ComponentException("Missing procurement PF scope");
        final Object target = scope.original.lookup(name);
        if (!IplatFormEntry.class.getName().equals(name)) return target;
        return Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                new Class<?>[] { IplatFormEntry.class }, new InvocationHandler() {
                    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("processAction".equals(method.getName()) && (args[3] == scope.bill
                                || (scope.action != null && scope.action.equals(args[0]) && scope.billType.equals(args[1]))
                                || (scope.kind.billClass.isInstance(args[3]) && isWrite(args[0])))) {
                            return scope.execute(args, (IplatFormEntry) target);
                        }
                        if ("processBatch".equals(method.getName()) && isWrite(args[0])
                                && !scope.matchesBatch(args)) {
                            throw new BusinessException("采购批量流程缺少本次策略检查。");
                        }
                        try {
                            return method.invoke(target, args);
                        } catch (InvocationTargetException ex) {
                            throw ex.getCause();
                        }
                    }
                });
    }

    private static boolean isWrite(Object action) {
        return "SAVEBASE".equals(action) || "SAVEREVISE".equals(action) || "DISCARD".equals(action);
    }

    static final class Scope implements AutoCloseable {
        private final NCLocator original;
        private final String previousProvider;
        private final ProcurementActionGuard.Kind kind;
        private final Dispatch dispatch;
        private String action;
        private String billType;
        private AbstractBill bill;
        private boolean consumed;
        private AggregatedValueObject[] batch;

        private Scope(NCLocator original, String previousProvider, ProcurementActionGuard.Kind kind, Dispatch dispatch) {
            this.original = original;
            this.previousProvider = previousProvider;
            this.kind = kind;
            this.dispatch = dispatch;
        }

        void expect(String action, String billType, AbstractBill bill) {
            this.action = action;
            this.billType = billType;
            this.bill = bill;
            this.consumed = false;
        }

        void allowBatch(String action, String billType, AggregatedValueObject[] bills) {
            this.action = action;
            this.billType = billType;
            this.batch = bills.clone();
        }

        private boolean matchesBatch(Object[] args) {
            if (batch == null || !action.equals(args[0]) || !billType.equals(args[1])
                    || args.length != 6 || !(args[3] instanceof AggregatedValueObject[])) return false;
            AggregatedValueObject[] bills = (AggregatedValueObject[]) args[3];
            if (batch.length != bills.length) return false;
            for (int i = 0; i < batch.length; i++) {
                if (batch[i] != bills[i]) return false;
            }
            return true;
        }

        private Object execute(Object[] args, IplatFormEntry nativePf) throws BusinessException {
            if (ACTIVE.get() != this || consumed || bill == null || bill != args[3]
                    || !action.equals(args[0]) || !billType.equals(args[1])) {
                throw new BusinessException("采购流程参数与本次确认的操作不一致。");
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
