package nc.bs.uapbd.qd.esign.persistence;

import java.util.List;
import java.util.Map;
import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchReceipt;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPreparedScope;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchTargetScope;
import nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.vo.pub.BusinessException;

/** Candidate CMT writer; it never starts or commits a transaction and is not yet registered as an action. */
public final class NativeOrgBatchReceiptStore {
    private final QdEsignStore store;
    public NativeOrgBatchReceiptStore() { this(new QdEsignStore()); }
    NativeOrgBatchReceiptStore(QdEsignStore store) {
        if(store==null) throw new IllegalArgumentException("store");this.store=store;
    }

    public static final class Prepared {
        private final QdEsignStore store;private final Object key;private final Thread thread;
        private final String requestId,token,group,org;private final NativeOrgBatchPage page;private final NativeOrgBatchTargetScope.Evidence scope;
        private final NativeOrgBatchPreparedScope.Evidence selection;
        private final int scopeRows;private boolean selectionMatched,used,completed;
        private Prepared(QdEsignStore store,Object key,String requestId,String token,String group,String org,NativeOrgBatchPage page,
                NativeOrgBatchTargetScope.Evidence scope,NativeOrgBatchPreparedScope.Evidence selection,int scopeRows) {
            this.store=store;this.key=key;this.requestId=requestId;this.token=token;this.page=page;this.scope=scope;
            this.group=group;this.org=org;this.selection=selection;this.scopeRows=scopeRows;thread=Thread.currentThread();
        }
    }

    /** Must run before the native update, holding request and schema locks through the enclosing transaction. */
    public Prepared preflight(String requestId,String token,PartnerOrgBatchScope.Prepared lockedScope) throws BusinessException {
        TransactionSynchronizationRegistry tx=NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object key=tx.getTransactionKey();
        try {
            EsignValues.require(lockedScope!=null && lockedScope.getImpact()!=null,"组织页签回执缺少锁定的执行范围。");
            lockedScope.requireRequest(requestId);
            Prepared prepared=preflightGuarded(store,tx,key,requestId,token,lockedScope.getImpact().getPage());
            NativeOrgBatchPreparedScope.Evidence actual=NativeOrgBatchPreparedScope.capture(requestId,prepared.group,prepared.org,lockedScope);
            matchSelectionGuarded(store,tx,key,prepared,actual);
            lockedScope.requireRequest(requestId);return prepared;
        } catch(BusinessException failure) {
            if(TransactionRollback.isMarked(failure))throw failure;
            throw TransactionRollback.abort(tx,key,failure);
        } catch(RuntimeException failure) { throw TransactionRollback.abort(tx,key,failure); }
    }

    /** The one-use ticket ties every receipt row to the exact preflight thread and transaction. */
    public void write(Prepared prepared,NativeOrgBatchReceipt.Evidence evidence) throws BusinessException {
        TransactionSynchronizationRegistry tx=NativeTransactionBoundaryProbe.requireActiveTransaction();
        writeGuarded(store,tx,tx.getTransactionKey(),prepared,evidence,QdUtcClock.now());
    }

    /** The enclosing business service must call this before it returns from the same transaction. */
    public void requireCompleted(Prepared prepared) throws BusinessException {
        TransactionSynchronizationRegistry tx=NativeTransactionBoundaryProbe.requireActiveTransaction();
        requireCompletedGuarded(store,tx,tx.getTransactionKey(),prepared);
    }

    static Prepared preflightGuarded(QdEsignStore store,TransactionSynchronizationRegistry tx,Object key,String requestId,
            String token,NativeOrgBatchPage page) throws BusinessException {
        try { return preflight(store,tx,key,requestId,token,page); }
        catch(BusinessException failure) { throw TransactionRollback.abort(tx,key,failure); }
        catch(RuntimeException failure) { throw TransactionRollback.abort(tx,key,failure); }
    }
    static void writeGuarded(QdEsignStore store,TransactionSynchronizationRegistry tx,Object key,Prepared prepared,
            NativeOrgBatchReceipt.Evidence evidence,String eventTime) throws BusinessException {
        // A different thread/transaction cannot prove that the original business transaction rolled back.
        requireOriginal(store,key,prepared);
        try { write(store,tx,key,prepared,evidence,eventTime); }
        catch(BusinessException failure) { throw TransactionRollback.abort(tx,key,failure); }
        catch(RuntimeException failure) { throw TransactionRollback.abort(tx,key,failure); }
    }
    /** Verify the persisted page set against the locked scope before the native service is called. */
    static void matchSelectionGuarded(QdEsignStore store,TransactionSynchronizationRegistry tx,Object key,Prepared prepared,
            NativeOrgBatchPreparedScope.Evidence actual) throws BusinessException {
        requireOriginal(store,key,prepared);
        try {
            active(tx,key);
            EsignValues.require(prepared!=null && !prepared.selectionMatched && !prepared.used && actual!=null
                && prepared.selection.getDigest().equals(actual.getDigest()),"锁定的执行页签与签名前准备范围不一致。");
            prepared.selectionMatched=true;
        } catch(BusinessException failure) { throw TransactionRollback.abort(tx,key,failure); }
        catch(RuntimeException failure) { throw TransactionRollback.abort(tx,key,failure); }
    }
    static void requireCompletedGuarded(QdEsignStore store,TransactionSynchronizationRegistry tx,Object key,Prepared prepared)
            throws BusinessException {
        requireOriginal(store,key,prepared);
        try {
            active(tx,key);
            EsignValues.require(prepared!=null && prepared.completed,"组织页签结果尚未完整写入，不能结束业务事务。");
        } catch(BusinessException failure) { throw TransactionRollback.abort(tx,key,failure); }
        catch(RuntimeException failure) { throw TransactionRollback.abort(tx,key,failure); }
    }
    private static Prepared preflight(QdEsignStore store,TransactionSynchronizationRegistry tx,Object key,String requestId,
            String token,NativeOrgBatchPage page) throws BusinessException {
        active(tx,key);
        EsignValues.require(store!=null && requestId!=null && requestId.matches("[a-fA-F0-9]{32}") && token!=null
            && token.matches("[a-fA-F0-9]{32}") && page!=null,"组织页签回执缺少有效请求、执行令牌或页签。");
        store.requireOrgReceiptStorage();Map<String,Object> request=claimed(store,requestId,token);
        List<Map<String,Object>> objects=store.loadObjects(requestId);NativeOrgBatchReceiptRecords.requireRunning(request,objects,page);
        List<Map<String,Object>> rows=store.loadOrgReceiptRows(requestId);
        List<Map<String,Object>> targetRows=NativeOrgBatchPreparedRecords.targetRows(rows),selectedRows=NativeOrgBatchPreparedRecords.pageRows(rows);
        NativeOrgBatchTargetScope.Evidence scope=NativeOrgBatchTargetRecords.decode(request,objects,page,targetRows);
        NativeOrgBatchPreparedScope.Evidence selection=NativeOrgBatchPreparedRecords.decode(request,objects,page,scope,selectedRows);
        EsignValues.require(rows.size()==targetRows.size()+selectedRows.size()
            && targetRows.size()==scope.getTargets().size()+1 && selectedRows.size()==selection.getPages().size()+1,
            "组织批改准备目标或页签记录数量不完整。");
        store.requireNoOrgOutcomeRows(requestId);active(tx,key);
        return new Prepared(store,key,requestId,token,EsignValues.text(request,"pk_group"),EsignValues.text(request,"pk_org"),
            page,scope,selection,rows.size());
    }
    private static void write(QdEsignStore store,TransactionSynchronizationRegistry tx,Object key,Prepared prepared,
            NativeOrgBatchReceipt.Evidence evidence,String eventTime) throws BusinessException {
        active(tx,key);
        EsignValues.require(prepared!=null && prepared.store==store && prepared.thread==Thread.currentThread()
            && prepared.key.equals(key) && prepared.selectionMatched && !prepared.used,
            "组织页签回执未核对锁定范围或预检已经使用。");
        prepared.used=true;
        Map<String,Object> request=claimed(store,prepared.requestId,prepared.token);
        List<Map<String,Object>> objects=store.loadObjects(prepared.requestId),scopeRows=store.loadOrgReceiptRows(prepared.requestId);
        List<Map<String,Object>> targetRows=NativeOrgBatchPreparedRecords.targetRows(scopeRows),selectedRows=NativeOrgBatchPreparedRecords.pageRows(scopeRows);
        NativeOrgBatchTargetScope.Evidence scope=NativeOrgBatchTargetRecords.decode(request,objects,prepared.page,targetRows);
        NativeOrgBatchPreparedScope.Evidence selection=NativeOrgBatchPreparedRecords.decode(request,objects,prepared.page,scope,selectedRows);
        EsignValues.require(scopeRows.size()==prepared.scopeRows && scope.getDigest().equals(prepared.scope.getDigest())
            && selection.getDigest().equals(prepared.selection.getDigest()) && evidence!=null
            && scope.getTargets().equals(evidence.getTargets()),"组织批改准备目标、页签或结果目标已变化。");
        NativeOrgBatchPreparedScope.requireOutcome(prepared.requestId,EsignValues.text(request,"pk_group"),
            EsignValues.text(request,"pk_org"),ObjectRelations.restore(objects),selection,evidence);
        List<Map<String,Object>> records=NativeOrgBatchReceiptRecords.encode(request,objects,prepared.page,evidence,eventTime);
        EsignValues.require(!records.isEmpty(),"组织页签回执记录为空。");
        String month=EsignValues.text(records.get(0),"archive_month");store.beginOrgOutcomeRows(prepared.requestId,month);
        for(Map<String,Object> row:records) { active(tx,key);store.insertOrgReceiptRow(row); }
        EsignValues.require(store.countOrgReceiptRows(prepared.requestId)==(long)prepared.scopeRows+records.size(),"组织页签回执写入数量不完整。");
        active(tx,key);
        prepared.completed=true;
    }
    private static void requireOriginal(QdEsignStore store,Object key,Prepared prepared) throws BusinessException {
        if(prepared!=null && (prepared.store!=store || prepared.thread!=Thread.currentThread() || !prepared.key.equals(key)))
            throw new BusinessException("组织页签回执的原事务不可用，请按请求号核对业务结果。");
    }
    private static Map<String,Object> claimed(QdEsignStore store,String requestId,String token) throws BusinessException {
        Map<String,Object> request=store.loadRequest(requestId,true);
        EsignValues.require(request!=null && requestId.equals(EsignValues.text(request,"request_id"))
            && token.equals(EsignValues.text(request,"execution_token")),"组织页签回执的执行认领不匹配。");
        return request;
    }
    private static void active(TransactionSynchronizationRegistry tx,Object key) throws BusinessException {
        EsignValues.require(tx!=null && key!=null && key.equals(tx.getTransactionKey())
            && tx.getTransactionStatus()==Status.STATUS_ACTIVE && !tx.getRollbackOnly(),
            "组织页签回执未处于原业务事务或事务已回滚。");
    }
}
