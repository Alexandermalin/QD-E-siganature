package nc.bs.uapbd.qd.esign.core;

import java.util.Set;
import java.util.TreeSet;
import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.dao.BaseDAO;
import nc.bs.uapbd.qd.esign.audit.NativeDeleteMetadataGraph;
import nc.md.MDBaseQueryFacade;
import nc.vo.pub.BusinessException;

/** Candidate Oracle table locks for the metadata-resolved page graph, including currently empty tables. */
final class NativeOrgBatchTableLock {
    private static final int MAX_TABLES=64;
    private NativeOrgBatchTableLock() { }
    interface Metadata { NativeDeleteMetadataGraph.Capture read() throws BusinessException; }
    interface Executor { void lock(String table) throws BusinessException; }

    static final class Ticket {
        private final NativeOrgBatchPage page;private final TransactionSynchronizationRegistry registry;
        private final Object key;private final Thread thread;private final Metadata metadata;
        private final NativeDeleteMetadataGraph.Capture expected;private boolean verifyDataSource;
        private Ticket(NativeOrgBatchPage page,TransactionSynchronizationRegistry registry,Object key,Metadata metadata,
                NativeDeleteMetadataGraph.Capture expected) {
            this.page=page;this.registry=registry;this.key=key;this.metadata=metadata;this.expected=expected;
            this.thread=Thread.currentThread();
        }
        void require(NativeOrgBatchPage current) throws BusinessException {
            EsignValues.require(page==current && thread==Thread.currentThread() && key.equals(registry.getTransactionKey())
                && registry.getTransactionStatus()==Status.STATUS_ACTIVE && !registry.getRollbackOnly(),
                "组织页签锁定的原事务不可用，请按请求号核对。");
            if(verifyDataSource) NativeTransactionBoundaryProbe.requireSingleDataSource(registry,key);
        }
        private void requireNativeDataSource() throws BusinessException {
            require(page);NativeTransactionBoundaryProbe.requireSingleDataSource(registry,key);verifyDataSource=true;
        }
        void requireEvidence(NativeOrgBatchSnapshots.Capture evidence) throws BusinessException {
            require(page);EsignValues.require(evidence!=null,"组织页签锁定后缺少物理快照。");
            evidence.requireMetadata(expected);requireCurrentMetadata();
        }
        void requireCurrentMetadata() throws BusinessException {
            require(page);NativeDeleteMetadataGraph.Capture current=metadata.read();
            EsignValues.require(current!=null && expected.tableNames().equals(current.tableNames())
                && expected.metadataEvidence().equals(current.metadataEvidence()),"组织页签锁定范围或元数据已变化。");
            require(page);
        }
    }

    static Ticket acquire(final NativeOrgBatchPage page,final TransactionSynchronizationRegistry registry) throws BusinessException {
        final Object key=registry==null?null:registry.getTransactionKey();
        try {
            Ticket ticket=acquire(page,registry,new Metadata() {
                public NativeDeleteMetadataGraph.Capture read() throws BusinessException {
                    return NativeOrgBatchSnapshots.metadata(page,MDBaseQueryFacade.getInstance().getBeanByID(page.metadataId));
                }
            },new Executor() {
                private final BaseDAO dao=new BaseDAO();
                public void lock(String table) throws BusinessException {
                    dao.executeUpdate("LOCK TABLE "+table+" IN SHARE ROW EXCLUSIVE MODE NOWAIT");
                }
            });
            ticket.requireNativeDataSource();ticket.require(page);return ticket;
        } catch(BusinessException failure) {
            if(TransactionRollback.isMarked(failure)) throw failure;
            throw TransactionRollback.abort(registry,key,failure);
        } catch(RuntimeException failure) { throw TransactionRollback.abort(registry,key,failure); }
    }

    /** Package-private seams allow deterministic lock-order and transaction-identity tests without Oracle. */
    static Ticket acquire(NativeOrgBatchPage page,TransactionSynchronizationRegistry registry,Metadata metadata,Executor executor)
            throws BusinessException {
        Object key=registry==null?null:registry.getTransactionKey();
        try {
            EsignValues.require(page!=null && registry!=null && key!=null && metadata!=null && executor!=null
                && registry.getTransactionStatus()==Status.STATUS_ACTIVE && !registry.getRollbackOnly(),
                "组织页签批改没有活动的锁定事务。");
            NativeDeleteMetadataGraph.Capture initial=metadata.read();
            EsignValues.require(initial!=null && initial.tableNames()!=null && initial.tableNames().contains(page.table)
                && !initial.tableNames().isEmpty() && initial.tableNames().size()<=MAX_TABLES,
                "组织页签物理锁范围缺失或过大。");
            Set<String> tables=new TreeSet<String>(initial.tableNames());
            for(String table:tables) EsignValues.require(table!=null && table.matches("[a-z][a-z0-9_$#]{0,29}"),
                "组织页签物理锁表名无效。");
            Ticket ticket=new Ticket(page,registry,key,metadata,initial);
            for(String table:tables) { ticket.require(page);executor.lock(table);ticket.require(page); }
            ticket.requireCurrentMetadata();return ticket;
        } catch(BusinessException failure) { throw TransactionRollback.abort(registry,key,failure); }
        catch(RuntimeException failure) { throw TransactionRollback.abort(registry,key,failure); }
    }
}
