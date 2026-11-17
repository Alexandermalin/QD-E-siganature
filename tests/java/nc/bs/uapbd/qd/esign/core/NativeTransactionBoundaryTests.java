package nc.bs.uapbd.qd.esign.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.transaction.Status;
import javax.transaction.Transaction;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.vo.pub.BusinessException;
import uap.mw.trans.UAPTransactionManagerProxy;
import uap.mw.trans.itf.IUAPTransactionManager;
import uap.mw.trans.syn.UAPTransSynchronizationRegistry;

/** 对实际 NC65 注册器及其他 JTA 提供者验证事务边界。 */
public final class NativeTransactionBoundaryTests {
    private NativeTransactionBoundaryTests() { }

    public static void run() throws Exception {
        ThreadLocal<IUAPTransactionManager> managers = UAPTransactionManagerProxy.getTransactionManagerContainer();
        IUAPTransactionManager previous = managers.get();
        try {
            managers.remove();
            TransactionSynchronizationRegistry nativeRegistry = UAPTransSynchronizationRegistry.getInstance();
            check(nativeRegistry.getTransactionStatus() == Status.STATUS_ACTIVE,
                "NC65 native registry reproduces the no-transaction status anomaly");
            NativeTransactionBoundaryProbe.requireNoTransaction(nativeRegistry);

            final Transaction active = (Transaction) Proxy.newProxyInstance(Transaction.class.getClassLoader(),
                new Class<?>[] {Transaction.class}, new InvocationHandler() {
                    @Override public Object invoke(Object proxy, Method method, Object[] args) { return null; }
                });
            managers.set((IUAPTransactionManager) Proxy.newProxyInstance(IUAPTransactionManager.class.getClassLoader(),
                new Class<?>[] {IUAPTransactionManager.class}, new InvocationHandler() {
                    @Override public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("getTransaction".equals(method.getName())) { return active; }
                        throw new UnsupportedOperationException(method.getName());
                    }
                }));
            expectRejected(nativeRegistry);

            NativeTransactionBoundaryProbe.requireNoTransaction(registry(Status.STATUS_NO_TRANSACTION));
            expectRejected(registry(Status.STATUS_ACTIVE));
            java.util.Map<String,Object> sources=new java.util.HashMap<String,Object>();
            expectSourcesRejected(sources);sources.put("primary",new Object());
            NativeTransactionBoundaryProbe.requireSingleConnectionMap(sources);
            sources.put("second",new Object());expectSourcesRejected(sources);
            sources.remove("second");sources.put("primary",null);expectSourcesRejected(sources);
            sources.clear();sources.put("",new Object());expectSourcesRejected(sources);
            final uap.mw.trans.UAPTransaction nativeTx=new uap.mw.trans.UAPTransaction();
            managers.set((IUAPTransactionManager) Proxy.newProxyInstance(IUAPTransactionManager.class.getClassLoader(),
                new Class<?>[] {IUAPTransactionManager.class}, new InvocationHandler() {
                    public Object invoke(Object proxy,Method method,Object[] args) {
                        if("getTransaction".equals(method.getName()))return nativeTx;
                        throw new UnsupportedOperationException(method.getName());
                    }
                }));
            try { NativeTransactionBoundaryProbe.requireSingleDataSource(nativeRegistry,nativeTx.getKey());
                throw new AssertionError("Transaction without an enlisted source was accepted"); } catch(BusinessException expected) { }
            @SuppressWarnings({"rawtypes","unchecked"}) java.util.Map raw=nativeTx.getConnectionMap();
            raw.put("primary",new Object());
            NativeTransactionBoundaryProbe.requireSingleDataSource(nativeRegistry,nativeTx.getKey());
            try { NativeTransactionBoundaryProbe.requireSingleDataSource(nativeRegistry,"different");
                throw new AssertionError("Another transaction key was accepted"); } catch(BusinessException expected) { }
            raw.put("second",new Object());
            try { NativeTransactionBoundaryProbe.requireSingleDataSource(nativeRegistry,nativeTx.getKey());
                throw new AssertionError("Multiple enlisted sources were accepted"); } catch(BusinessException expected) { }
        } finally {
            if (previous == null) { managers.remove(); } else { managers.set(previous); }
        }
        System.out.println("PASS NativeTransactionBoundaryTests (NC65 registry and active transaction rejection)");
    }

    private static void expectSourcesRejected(java.util.Map<String,Object> sources) throws Exception {
        try { NativeTransactionBoundaryProbe.requireSingleConnectionMap(sources);throw new AssertionError("Ambiguous data sources accepted"); }
        catch(BusinessException expected) { }
    }

    private static TransactionSynchronizationRegistry registry(final int status) {
        return (TransactionSynchronizationRegistry) Proxy.newProxyInstance(
            TransactionSynchronizationRegistry.class.getClassLoader(),
            new Class<?>[] {TransactionSynchronizationRegistry.class}, new InvocationHandler() {
                @Override public Object invoke(Object proxy, Method method, Object[] args) {
                    if ("getTransactionStatus".equals(method.getName())) { return Integer.valueOf(status); }
                    throw new UnsupportedOperationException(method.getName());
                }
            });
    }

    private static void expectRejected(TransactionSynchronizationRegistry registry) throws Exception {
        try {
            NativeTransactionBoundaryProbe.requireNoTransaction(registry);
            throw new AssertionError("Active transaction was accepted");
        } catch (BusinessException expected) {
            check(expected.getMessage().contains("nontransactional"), "active transaction rejection");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
