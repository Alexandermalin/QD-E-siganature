package nc.ui.pu.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;

public final class PurchaseOrderPfLocatorTest {
    public static final class TestLocator extends NCLocator {
        @Override protected void init(Properties properties) { }

        @Override public Object lookup(String name) {
            if (!IplatFormEntry.class.getName().equals(name)) return name;
            return Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                    new Class<?>[] { IplatFormEntry.class }, new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method method, Object[] args) {
                            return "native";
                        }
                    });
        }
    }

    public static void main(String[] args) throws Exception {
        InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
        String property = NCLocator.LOCATOR_PROVIDER_PROPERTY;
        String original = invocation.getProperty(property);
        invocation.setProperty(property, TestLocator.class.getName());
        try {
            exactPreparedSaveIsRoutedAndOtherCallsStayNative();
            unpreparedPurchaseSaveCannotFallThrough();
            check(TestLocator.class.getName().equals(invocation.getProperty(property)), "previous provider restored");
        } finally {
            invocation.setProperty(property, original);
        }
    }

    private static void exactPreparedSaveIsRoutedAndOtherCallsStayNative() throws Exception {
        final OrderVO bill = new LocalOrder();
        final AtomicInteger calls = new AtomicInteger();
        try (PurchaseOrderPfLocator.Scope scope = PurchaseOrderPfLocator.open(new PurchaseOrderPfLocator.Dispatch() {
            @Override public Object execute(Object[] args, IplatFormEntry nativePf) {
                check(args[3] == bill, "same native order instance");
                calls.incrementAndGet();
                return "controlled";
            }
        })) {
            scope.expect("SAVEBASE", "21", bill);
            NCLocator current = NCLocator.getInstance();
            IplatFormEntry pf = current.lookup(IplatFormEntry.class);
            check("OtherService".equals(current.lookup("OtherService")), "non-PF lookup delegated");
            check("native".equals(pf.processAction("SAVE", "21", null, new LocalOrder(), null, null)), "unrelated PF delegated");
            check(calls.get() == 0, "preparing PF does not request authorization");
            try {
                pf.processAction("SAVEBASE unexpected", "21", null, bill, null, null);
                throw new AssertionError("A changed PF action on the authorized order reached the native write");
            } catch (BusinessException expected) {
                check(calls.get() == 0, "mismatched action was rejected before dispatch");
            }
            check("controlled".equals(pf.processAction("SAVEBASE", "21", null, bill, null, null)), "exact PF save routed");
            try {
                pf.processAction("SAVEBASE", "21", null, bill, null, null);
                throw new AssertionError("Duplicate write without native re-preparation");
            } catch (BusinessException expected) {
                check(calls.get() == 1, "duplicate was rejected before dispatch");
            }
            check(calls.get() == 1, "one controlled call");
        }
        check(NCLocator.getInstance() instanceof TestLocator, "baseline locator restored");
    }

    private static void unpreparedPurchaseSaveCannotFallThrough() throws Exception {
        try (PurchaseOrderPfLocator.Scope scope = PurchaseOrderPfLocator.open(new PurchaseOrderPfLocator.Dispatch() {
            @Override public Object execute(Object[] args, IplatFormEntry nativePf) {
                throw new AssertionError("Unexpected authorized call");
            }
        })) {
            scope.expect("REVISE", "21", new LocalOrder());
            try {
                NCLocator.getInstance().lookup(IplatFormEntry.class)
                        .processAction("REVISE", "21", null, new LocalOrder(), null, null);
                throw new AssertionError("Mismatched purchase order reached the native write");
            } catch (BusinessException expected) {
                check(NCLocator.getInstance() instanceof PurchaseOrderPfLocator, "failure keeps scope until close");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    // The locator needs a real OrderVO identity but does not read bill metadata.
    private static final class LocalOrder extends OrderVO {
        private static final long serialVersionUID = 1L;

        @Override public IBillMeta getMetaData() {
            return (IBillMeta) Proxy.newProxyInstance(IBillMeta.class.getClassLoader(),
                    new Class<?>[] { IBillMeta.class }, new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method method, Object[] args) {
                            if ("getChildren".equals(method.getName())) return new IVOMeta[0];
                            throw new AssertionError(method.getName());
                        }
                    });
        }
    }
}
