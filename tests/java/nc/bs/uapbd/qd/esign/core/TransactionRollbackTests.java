package nc.bs.uapbd.qd.esign.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.vo.pub.BusinessException;

/** Exercises the exact rollback marker and uncertain-outcome branches without a transaction server. */
public final class TransactionRollbackTests {
    private TransactionRollbackTests() { }

    public static void run() {
        Object key = new Object();
        FakeRegistry good = new FakeRegistry(key);
        BusinessException source = new BusinessException("audit write failed");
        BusinessException marked = TransactionRollback.abort(good.proxy(), key, source);
        check(good.marked && TransactionRollback.isMarked(marked)
            && marked.getCause() == source && marked.getMessage().contains("实际结果需按请求号核对"),
            "A rollback marker must retain the original failure without claiming completion");
        check(TransactionRollback.isMarked(new BusinessException("container", marked)),
            "A container wrapper must retain the rollback marker");

        FakeRegistry noMarker = new FakeRegistry(key);
        noMarker.ignoreMarker = true;
        BusinessException unmarked = TransactionRollback.abort(noMarker.proxy(), key, new BusinessException("write failed"));
        check(!TransactionRollback.isMarked(unmarked) && unmarked.getCause().getSuppressed().length == 1,
            "An unretained rollback marker must yield an uncertain outcome");

        FakeRegistry changed = new FakeRegistry(new Object());
        BusinessException different = TransactionRollback.abort(changed.proxy(), key, new BusinessException("write failed"));
        check(!changed.marked && !TransactionRollback.isMarked(different),
            "A changed transaction must never be reported as a verified rollback");
        System.out.println("PASS TransactionRollbackTests (marker and outcome classification; no NC transaction invoked)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static final class FakeRegistry implements InvocationHandler {
        private final Object key;
        private boolean marked, ignoreMarker;

        FakeRegistry(Object key) { this.key = key; }

        TransactionSynchronizationRegistry proxy() {
            return (TransactionSynchronizationRegistry)Proxy.newProxyInstance(
                TransactionSynchronizationRegistry.class.getClassLoader(),
                new Class<?>[] {TransactionSynchronizationRegistry.class}, this);
        }

        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("getTransactionKey".equals(method.getName())) { return key; }
            if ("setRollbackOnly".equals(method.getName())) { marked = !ignoreMarker; return null; }
            if ("getRollbackOnly".equals(method.getName())) { return Boolean.valueOf(marked); }
            throw new UnsupportedOperationException(method.getName());
        }
    }
}
