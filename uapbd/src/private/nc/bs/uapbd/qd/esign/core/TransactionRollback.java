package nc.bs.uapbd.qd.esign.core;

import javax.transaction.TransactionSynchronizationRegistry;
import nc.vo.pub.BusinessException;

/** Marks the current business transaction for rollback and preserves uncertain outcomes. */
public final class TransactionRollback {
    private TransactionRollback() { }

    public static BusinessException abort(TransactionSynchronizationRegistry registry, Object transactionKey,
            Throwable failure) {
        if (failure == null) { throw new IllegalArgumentException("failure"); }
        try {
            if (registry == null || transactionKey == null || !transactionKey.equals(registry.getTransactionKey())) {
                throw new IllegalStateException("The original business transaction is unavailable");
            }
            registry.setRollbackOnly();
            if (!registry.getRollbackOnly()) {
                throw new IllegalStateException("The rollback marker was not retained");
            }
            return new MarkedRollback(failure);
        } catch (RuntimeException markerFailure) {
            failure.addSuppressed(markerFailure);
            return new BusinessException("无法确认业务事务回滚，请按请求号核对。", failure);
        }
    }

    /** This proves only that the active transaction retained a rollback marker, not that rollback completed. */
    public static boolean isMarked(Throwable failure) {
        for (int depth = 0; failure != null && depth < 16; depth++, failure = failure.getCause()) {
            if (failure instanceof MarkedRollback) { return true; }
        }
        return false;
    }

    private static final class MarkedRollback extends BusinessException {
        private static final long serialVersionUID = 1L;
        MarkedRollback(Throwable cause) {
            super("业务事务已标记回滚，实际结果需按请求号核对。"
                +(cause.getMessage()==null?"":"原因："+cause.getMessage()), cause);
        }
    }
}
