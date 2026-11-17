package nc.bs.uapbd.qd.esign.persistence;

import java.util.List;
import java.util.Map;
import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPreparedScope;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.PartnerBatchRoute;
import nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;

/** Candidate preparation-time writer; no transaction is started or committed by this class. */
public final class NativeOrgBatchTargetStore {
    private final QdEsignStore store;
    public NativeOrgBatchTargetStore() { this(new QdEsignStore()); }
    NativeOrgBatchTargetStore(QdEsignStore store) { if(store==null)throw new IllegalArgumentException("store");this.store=store; }

    public static final class Prepared {
        private final QdEsignStore store;private final Object key;private final Thread thread;
        private final String requestId,group,org,created;private final int count;private final NativeOrgBatchPage page;private boolean used,completed;
        private Prepared(QdEsignStore store,Object key,String requestId,String group,String org,String created,int count,NativeOrgBatchPage page) {
            this.store=store;this.key=key;this.requestId=requestId;this.group=group;this.org=org;this.created=created;
            this.count=count;this.page=page;thread=Thread.currentThread();
        }
    }

    /** Call immediately after inserting the request, before inserting its objects, in the preparation CMT. */
    public Prepared begin(String requestId,NativeOrgBatchPage page) throws BusinessException {
        TransactionSynchronizationRegistry tx=NativeTransactionBoundaryProbe.requireActiveTransaction();
        return beginGuarded(store,tx,tx.getTransactionKey(),requestId,page);
    }
    /** Call after the exact request objects have been inserted, before the preparation CMT ends. */
    public void write(Prepared prepared,PartnerOrgBatchScope.Prepared nativeScope) throws BusinessException {
        TransactionSynchronizationRegistry tx=NativeTransactionBoundaryProbe.requireActiveTransaction();Object key=tx.getTransactionKey();
        requireOriginal(store,key,prepared);
        try {
            EsignValues.require(nativeScope!=null,"组织批改缺少服务端准备范围。");
            NativeOrgBatchPreparedScope.Evidence evidence=NativeOrgBatchPreparedScope.capture(prepared.requestId,prepared.group,prepared.org,nativeScope);
            write(store,tx,key,prepared,evidence);
        } catch(BusinessException failure) { throw TransactionRollback.abort(tx,key,failure); }
        catch(RuntimeException failure) { throw TransactionRollback.abort(tx,key,failure); }
    }

    /** The enclosing preparation service must call this before it returns from the same transaction. */
    public void requireCompleted(Prepared prepared) throws BusinessException {
        TransactionSynchronizationRegistry tx=NativeTransactionBoundaryProbe.requireActiveTransaction();
        requireCompletedGuarded(store,tx,tx.getTransactionKey(),prepared);
    }

    static Prepared beginGuarded(QdEsignStore store,TransactionSynchronizationRegistry tx,Object key,String requestId,
            NativeOrgBatchPage page) throws BusinessException {
        try { return begin(store,tx,key,requestId,page); }
        catch(BusinessException failure) { throw TransactionRollback.abort(tx,key,failure); }
        catch(RuntimeException failure) { throw TransactionRollback.abort(tx,key,failure); }
    }
    /** The real path derives evidence from the native prepared scope; this seam is for offline tests. */
    static void writeGuarded(QdEsignStore store,TransactionSynchronizationRegistry tx,Object key,Prepared prepared,
            NativeOrgBatchPreparedScope.Evidence evidence) throws BusinessException {
        requireOriginal(store,key,prepared);
        try { write(store,tx,key,prepared,evidence); }
        catch(BusinessException failure) { throw TransactionRollback.abort(tx,key,failure); }
        catch(RuntimeException failure) { throw TransactionRollback.abort(tx,key,failure); }
    }
    static void requireCompletedGuarded(QdEsignStore store,TransactionSynchronizationRegistry tx,Object key,Prepared prepared)
            throws BusinessException {
        requireOriginal(store,key,prepared);
        try {
            active(tx,key);
            EsignValues.require(prepared!=null && prepared.completed,"组织批改准备目标尚未完整写入，不能结束准备事务。");
        } catch(BusinessException failure) { throw TransactionRollback.abort(tx,key,failure); }
        catch(RuntimeException failure) { throw TransactionRollback.abort(tx,key,failure); }
    }
    private static Prepared begin(QdEsignStore store,TransactionSynchronizationRegistry tx,Object key,String requestId,
            NativeOrgBatchPage page) throws BusinessException {
        active(tx,key);EsignValues.require(store!=null && validUuid(requestId) && page!=null,"组织批改准备缺少请求或页签。");
        store.requireOrgReceiptStorage();Map<String,Object> request=store.loadRequest(requestId,true);
        int count=header(request,requestId,page);List<Map<String,Object>> objects=store.loadObjects(requestId);
        EsignValues.require(objects!=null && objects.isEmpty(),"组织批改目标预检必须先于档案对象写入。");
        store.requireNoOrgReceiptRows(requestId);active(tx,key);
        return new Prepared(store,key,requestId,EsignValues.text(request,"pk_group"),EsignValues.text(request,"pk_org"),
            EsignValues.text(request,"created_at"),count,page);
    }
    private static void write(QdEsignStore store,TransactionSynchronizationRegistry tx,Object key,Prepared prepared,
            NativeOrgBatchPreparedScope.Evidence evidence) throws BusinessException {
        active(tx,key);EsignValues.require(prepared!=null && !prepared.used,"组织批改目标预检缺失或已经使用。");prepared.used=true;
        Map<String,Object> request=store.loadRequest(prepared.requestId,true);
        int count=header(request,prepared.requestId,prepared.page);
        EsignValues.require(count==prepared.count && prepared.group.equals(EsignValues.text(request,"pk_group"))
            && prepared.org.equals(EsignValues.text(request,"pk_org")) && prepared.created.equals(EsignValues.text(request,"created_at")),
            "组织批改准备请求在同一事务内发生变化。");
        EsignValues.require(evidence!=null,"组织批改缺少准备页签证据。");
        List<Map<String,Object>> objects=store.loadObjects(prepared.requestId);
        List<Map<String,Object>> rows=new java.util.ArrayList<Map<String,Object>>(
            NativeOrgBatchTargetRecords.encode(request,objects,prepared.page,evidence.getTarget()));
        rows.addAll(NativeOrgBatchPreparedRecords.encode(request,objects,prepared.page,evidence));
        EsignValues.require(!rows.isEmpty(),"组织批改准备目标记录为空。");
        store.beginOrgReceiptRows(prepared.requestId,EsignValues.text(rows.get(0),"archive_month"));
        for(Map<String,Object> row:rows) { active(tx,key);store.insertOrgReceiptRow(row); }
        EsignValues.require(store.countOrgReceiptRows(prepared.requestId)==rows.size(),"组织批改准备目标写入数量不完整。");active(tx,key);
        prepared.completed=true;
    }
    private static int header(Map<String,Object> request,String id,NativeOrgBatchPage page) throws BusinessException {
        EsignValues.require(request!=null && id.equals(EsignValues.text(request,"request_id"))
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(EsignValues.text(request,"policy_mode"))
            && ArchiveBatchResult.isPartnerBatchUpdate(EsignValues.text(request,"node_code"),EsignValues.text(request,"button_code"),
                EsignValues.text(request,"operation_type")) && page.domain.equals(PartnerBatchRoute.domain(EsignValues.text(request,"node_code")))
            && request.get("finished_at")==null && request.get("execution_token")==null
            && (("PENDING".equals(EsignValues.text(request,"signature_state")) && "PENDING".equals(EsignValues.text(request,"business_state")))
                || ("NOT_REQUIRED".equals(EsignValues.text(request,"signature_state"))
                    && ("PENDING".equals(EsignValues.text(request,"business_state")) || "READY".equals(EsignValues.text(request,"business_state")))))
            && validPk(EsignValues.text(request,"pk_group")) && validPk(EsignValues.text(request,"pk_org")),"组织批改准备请求状态不正确。");
        int count=NativeOrgBatchReceiptRecords.integer(request.get("owner_object_count"));
        EsignValues.require(count>0 && count<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS
            && NativeOrgBatchReceiptRecords.month(EsignValues.text(request,"created_at")).equals(EsignValues.text(request,"archive_month")),
            "组织批改准备请求数量或月份不正确。");return count;
    }
    private static void requireOriginal(QdEsignStore store,Object key,Prepared prepared) throws BusinessException {
        if(prepared!=null && (prepared.store!=store || prepared.thread!=Thread.currentThread() || !prepared.key.equals(key)))
            throw new BusinessException("组织批改准备的原事务不可用，请按请求号核对。");
    }
    private static void active(TransactionSynchronizationRegistry tx,Object key) throws BusinessException {
        EsignValues.require(tx!=null && key!=null && key.equals(tx.getTransactionKey())
            && tx.getTransactionStatus()==Status.STATUS_ACTIVE && !tx.getRollbackOnly(),"组织批改准备事务不可用或已回滚。");
    }
    private static boolean validUuid(String value) { return value!=null && value.matches("[a-fA-F0-9]{32}"); }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
}
