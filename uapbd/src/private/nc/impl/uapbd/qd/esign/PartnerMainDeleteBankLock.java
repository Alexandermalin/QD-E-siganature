package nc.impl.uapbd.qd.esign;

import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.dao.BaseDAO;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.vo.pub.BusinessException;

/** Candidate short-lived Oracle lock for relationship writes that do not use our account lock table. */
final class PartnerMainDeleteBankLock {
    private static final String[] TABLES={"bd_bankaccbas","bd_bankaccsub","bd_bankaccuse","bd_custbank"};
    private PartnerMainDeleteBankLock() { }
    interface Executor { void lock(String table) throws BusinessException; }

    /** Call after the native selected-master lock and keep the surrounding CMT through deletion and audit. */
    static void acquire() throws BusinessException {
        final TransactionSynchronizationRegistry transaction=NativeTransactionBoundaryProbe.requireActiveTransaction();
        final Object key=transaction.getTransactionKey();
        acquire(new Executor() {
            private final BaseDAO dao=new BaseDAO();
            public void lock(String table) throws BusinessException {
                dao.executeUpdate("LOCK TABLE "+table+" IN SHARE ROW EXCLUSIVE MODE NOWAIT");
            }
        });
        EsignValues.require(key.equals(transaction.getTransactionKey()) && !transaction.getRollbackOnly(),
            "银行账户锁未处于原主档删除事务内。");
    }

    static void acquire(Executor executor) throws BusinessException {
        EsignValues.require(executor!=null,"银行账户锁执行器缺失。");
        for(String table:TABLES) executor.lock(table);
    }
}
