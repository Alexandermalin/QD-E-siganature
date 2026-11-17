package nc.impl.ic.qd.esign;

import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.vo.ic.m40.entity.InitializtionInHeadVO;
import nc.vo.ic.m40.entity.InitializtionInVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/**
 * Same-thread capture of a native insert result. The future caller must verify the signed request
 * before opening this scope and independently verify the persisted bill before recording audit.
 */
final class InventoryInitialImportCapture implements AutoCloseable {
    private static final ThreadLocal<InventoryInitialImportCapture> CURRENT = new ThreadLocal<InventoryInitialImportCapture>();
    private final OperationContext context;
    private final TransactionSynchronizationRegistry transaction;
    private final Object transactionKey;
    private final Thread owner;
    private Result result;
    private volatile boolean closed;
    private volatile boolean consumed;
    private volatile boolean poisoned;

    private InventoryInitialImportCapture(OperationContext context, TransactionSynchronizationRegistry transaction) {
        this.context = context;
        this.transaction = transaction;
        this.transactionKey = transaction.getTransactionKey();
        this.owner = Thread.currentThread();
    }

    static InventoryInitialImportCapture open(OperationContext context, TransactionSynchronizationRegistry transaction) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested initial import capture is not supported");
        if (context == null || clean(context.getRequestId()) == null
                || !context.getRequestId().equals(clean(context.getRequestId()))
                || !"40080402".equals(context.getNodeCode()) || !"Save".equals(context.getButtonCode())
                || !"IMPORT".equals(context.getOperationType()) || clean(context.getGroupId()) == null
                || clean(context.getOrgId()) == null || !context.getGroupId().equals(clean(context.getGroupId()))
                || !context.getOrgId().equals(clean(context.getOrgId())) || context.getObjects().size() != 1) {
            throw new IllegalArgumentException("A single authorized initial-balance insert is required");
        }
        BusinessObjectRef ref = context.getObjects().get(0);
        if (!"IC_INITIAL_IN".equals(ref.getObjectType()) || ref.getObjectPk() != null
                || ref.getParentClientObjectId() != null || ref.getDesiredOwnerOrg() != null
                || ref.getPolicyOwnerOrg() != null || ref.getTargetOrg() != null) {
            throw new IllegalArgumentException("Invalid authorized initial-balance import object");
        }
        if (transaction == null || transaction.getTransactionKey() == null
                || transaction.getTransactionStatus() != Status.STATUS_ACTIVE || transaction.getRollbackOnly()) {
            throw new IllegalStateException("An active initial import transaction is required");
        }
        InventoryInitialImportCapture scope = new InventoryInitialImportCapture(context, transaction);
        CURRENT.set(scope);
        return scope;
    }

    /** Called by the native after-insert rule; ordinary NC operations are left untouched. */
    static void observe(InitializtionInVO[] bills) {
        InventoryInitialImportCapture scope = CURRENT.get();
        if (scope != null) scope.capture(bills);
    }

    private void capture(InitializtionInVO[] bills) {
        try {
            ensureTransaction();
            if (result != null || consumed || poisoned || bills == null || bills.length != 1 || bills[0] == null
                    || bills[0].getHead() == null) {
                throw new IllegalStateException("Native initial import returned an ambiguous insert result");
            }
            InitializtionInHeadVO head = bills[0].getHead();
            String pk = clean(head.getCgeneralhid()), code = clean(head.getVbillcode());
            if (pk == null || code == null || !context.getGroupId().equals(clean(head.getPk_group()))
                    || !context.getOrgId().equals(clean(head.getPk_org()))) {
                throw new IllegalStateException("Native initial import returned an invalid bill identity or organization");
            }
            result = new Result(pk, code, clean(head.getCtrantypeid()), clean(head.getVtrantypecode()));
        } catch (RuntimeException failure) { poison(failure); throw failure; }
          catch (Error failure) { poison(failure); throw failure; }
    }

    Result finish() {
        try {
            ensureTransaction();
            if (consumed || poisoned || result == null) throw new IllegalStateException("Native initial import insert result was not captured exactly once");
            consumed = true;
            return result;
        } catch (RuntimeException failure) { poison(failure); throw failure; }
          catch (Error failure) { poison(failure); throw failure; }
    }

    /** Mark the current transaction even if an NC layer catches and wraps the rule failure. */
    private void poison(Throwable failure) {
        poisoned = true;
        try {
            if (closed || Thread.currentThread() != owner || CURRENT.get() != this) {
                throw new IllegalStateException("Capture no longer owns the current thread's transaction");
            }
            if (transaction.getTransactionKey() == null) throw new IllegalStateException("No transaction to mark for rollback");
            if (!transaction.getRollbackOnly()) {
                if (transaction.getTransactionStatus() != Status.STATUS_ACTIVE) {
                    throw new IllegalStateException("Current transaction is not active");
                }
                transaction.setRollbackOnly();
            }
            if (!transaction.getRollbackOnly()) throw new IllegalStateException("Rollback marker was not retained");
        } catch (RuntimeException markerFailure) {
            if (markerFailure != failure) failure.addSuppressed(markerFailure);
        }
    }

    private void ensureTransaction() {
        if (closed || Thread.currentThread() != owner || CURRENT.get() != this
                || !transactionKey.equals(transaction.getTransactionKey())
                || transaction.getTransactionStatus() != Status.STATUS_ACTIVE || transaction.getRollbackOnly()) {
            throw new IllegalStateException("Initial import transaction or capture scope changed");
        }
    }

    public void close() {
        if (closed) return;
        if (Thread.currentThread() != owner || CURRENT.get() != this) {
            throw new IllegalStateException("Initial import capture must close on its owning thread");
        }
        try {
            if (!consumed || poisoned) {
                IllegalStateException failure = new IllegalStateException("Initial import capture closed without a verified result");
                poison(failure);
                throw failure;
            }
        } finally {
            CURRENT.remove();
            closed = true;
        }
    }

    private static String clean(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() || "~".equals(result) ? null : result;
    }

    static final class Result {
        final String primaryKey, billCode, transactionTypePk, transactionTypeCode;
        Result(String primaryKey, String billCode, String transactionTypePk, String transactionTypeCode) {
            this.primaryKey = primaryKey; this.billCode = billCode;
            this.transactionTypePk = transactionTypePk; this.transactionTypeCode = transactionTypeCode;
        }
    }
}
