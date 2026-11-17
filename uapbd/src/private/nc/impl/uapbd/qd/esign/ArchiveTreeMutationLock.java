package nc.impl.uapbd.qd.esign;

import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.dao.BaseDAO;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.vo.pub.BusinessException;

/** Candidate transaction-held locks covering tree-row and material-class extension inserts. */
final class ArchiveTreeMutationLock {
    private ArchiveTreeMutationLock() { }
    interface Executor { void lock(String table) throws BusinessException; }

    static void acquire(String domain,boolean materialExtensions) throws BusinessException {
        final TransactionSynchronizationRegistry registry=NativeTransactionBoundaryProbe.requireActiveTransaction();
        final Object key=registry.getTransactionKey();
        try {
            acquire(domain,materialExtensions,registry,new Executor() {
                private final BaseDAO dao=new BaseDAO();
                public void lock(String table) throws BusinessException {
                    dao.executeUpdate("LOCK TABLE "+table+" IN SHARE ROW EXCLUSIVE MODE NOWAIT");
                }
            });
            NativeTransactionBoundaryProbe.requireSingleDataSource(registry,key);active(registry,key);
        } catch(BusinessException failure) {
            if(TransactionRollback.isMarked(failure)) throw failure;
            throw TransactionRollback.abort(registry,key,failure);
        } catch(RuntimeException failure) { throw TransactionRollback.abort(registry,key,failure); }
    }

    /** Test seam for deterministic lock ordering and same-transaction failures. */
    static void acquire(String domain,boolean materialExtensions,TransactionSynchronizationRegistry registry,Executor executor)
            throws BusinessException {
        Object key=registry==null?null:registry.getTransactionKey();
        try {
            EsignValues.require(registry!=null && key!=null && executor!=null,"树形档案缺少活动的锁定事务。");
            String[] tables=tables(domain,materialExtensions);
            for(String table:tables) { active(registry,key);executor.lock(table);active(registry,key); }
        } catch(BusinessException failure) { throw TransactionRollback.abort(registry,key,failure); }
        catch(RuntimeException failure) { throw TransactionRollback.abort(registry,key,failure); }
    }

    private static String[] tables(String domain,boolean materialExtensions) throws BusinessException {
        EsignValues.require(!materialExtensions || "BD_MARBASCLASS".equals(domain),"树形档案扩展锁范围无效。");
        if("BD_CUSTCLASS".equals(domain)) return new String[]{"bd_custclass"};
        if("BD_SUPPLIERCLASS".equals(domain)) return new String[]{"bd_supplierclass"};
        if("BD_RACK".equals(domain)) return new String[]{"bd_rack"};
        if("BD_MARBASCLASS".equals(domain)) return materialExtensions
            ?new String[]{"bd_marbasclass","srmsm_marbasclsext","srmsm_marbasclsext_b"}:new String[]{"bd_marbasclass"};
        throw new BusinessException("未知的树形档案锁范围。");
    }
    private static void active(TransactionSynchronizationRegistry registry,Object key) throws BusinessException {
        EsignValues.require(registry!=null && key!=null && key.equals(registry.getTransactionKey())
            && registry.getTransactionStatus()==Status.STATUS_ACTIVE && !registry.getRollbackOnly(),
            "树形档案锁定的原事务不可用，请按请求号核对。");
    }
}
