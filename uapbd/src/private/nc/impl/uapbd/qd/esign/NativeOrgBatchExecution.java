package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.core.ArchiveBatchReceipt;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchReconciliation;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchReceipt;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchSelection;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchSnapshots;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.PartnerBatchRoute;
import nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.bs.uapbd.qd.esign.persistence.NativeOrgBatchReceiptStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.md.persist.framework.MDPersistenceService;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Local organization-batch execution; the public business route remains guarded. */
final class NativeOrgBatchExecution {
    private NativeOrgBatchExecution() { }
    interface Flow {
        void preflight(PartnerOrgBatchScope.Prepared scope,String request,String token) throws BusinessException;
        List<SuperVO> load(PartnerOrgBatchScope.Prepared scope) throws BusinessException;
        void requireBoundary() throws BusinessException;
        NativeOrgBatchNativeService.Execution invoke(PartnerOrgBatchScope.Prepared scope,String[] permissions) throws BusinessException;
        NativeOrgBatchSnapshots.Capture recapture(PartnerOrgBatchScope.Prepared scope) throws BusinessException;
        void write(NativeOrgBatchReceipt.Evidence evidence) throws BusinessException;
        void complete() throws BusinessException;
    }
    static final class Outcome {
        final ErrLogReturnValue nativeResult;final ArchiveBatchReceipt receipt;final List<ArchiveBatchResult.Unit> units;
        final NativeOrgBatchReceipt.Evidence evidence;
        Outcome(ErrLogReturnValue nativeResult,ArchiveBatchReceipt receipt,List<ArchiveBatchResult.Unit> units,
                NativeOrgBatchReceipt.Evidence evidence) {
            this.nativeResult=nativeResult;this.receipt=receipt;
            this.units=Collections.unmodifiableList(new ArrayList<ArchiveBatchResult.Unit>(units));this.evidence=evidence;
        }
    }
    /** Execute only inside the already locked CMT; the enclosing service still persists root states and audit. */
    static Outcome execute(OperationContext context,PartnerOrgBatchScope.Prepared scope,String token,AuditCaptureScope audit)
            throws BusinessException {
        TransactionSynchronizationRegistry tx=NativeTransactionBoundaryProbe.requireActiveTransaction();Object key=tx.getTransactionKey();
        try {
            ServerIdentity identity=ServerIdentity.current();PartnerOrgBatchScope.recheckPermissions(identity,context,scope);
            List<String> allowed=EsignPermission.authorizedOrgs(identity,context.getNodeCode(),context.getButtonCode(),false);
            EsignValues.require(allowed!=null && !allowed.isEmpty(),"无法确定原生组织页签批改权限。");
            Outcome result=run(context,scope,token,allowed.toArray(new String[allowed.size()]),audit,new DatabaseFlow(tx,key));
            scope.requireExecutionContext(context);NativeTransactionBoundaryProbe.requireSingleDataSource(tx,key);return result;
        } catch(BusinessException failure) {
            if(TransactionRollback.isMarked(failure))throw failure;
            throw TransactionRollback.abort(tx,key,failure);
        } catch(RuntimeException failure) { throw TransactionRollback.abort(tx,key,failure); }
    }
    static Outcome run(OperationContext context,PartnerOrgBatchScope.Prepared scope,String token,String[] permissions,
            AuditCaptureScope audit,Flow flow) throws BusinessException {
        EsignValues.require(context!=null && scope!=null && flow!=null && audit!=null && AuditCaptureScope.current()==audit
            && audit.getContext()==context && token!=null && token.matches("[a-fA-F0-9]{32}")
            && permissions!=null && permissions.length>0,"组织页签执行缺少请求、事务或审计上下文。");
        scope.requireExecutionContext(context);
        Set<String> allowed=new HashSet<String>();
        for(String org:permissions)EsignValues.require(org!=null && org.matches("[A-Za-z0-9_-]{1,20}") && allowed.add(org),
            "组织页签执行包含无效或重复的原生组织权限。");
        NativeOrgBatchPage page=scope.getImpact().getPage();String domain=PartnerBatchRoute.domain(context.getNodeCode());
        flow.preflight(scope,context.getRequestId(),token);scope.requireExecutionContext(context);
        List<SuperVO> before=flow.load(scope);NativeOrgBatchReconciliation.requireBefore(scope,context,before);
        flow.requireBoundary();scope.requireExecutionContext(context);
        NativeOrgBatchNativeService.Execution nativeResult=flow.invoke(scope,permissions.clone());
        EsignValues.require(nativeResult!=null && nativeResult.excluded!=null,"原生组织页签执行缺少可核对的结果。");
        flow.requireBoundary();scope.requireExecutionContext(context);
        NativeOrgBatchSnapshots.Capture after=flow.recapture(scope);List<SuperVO> current=flow.load(scope);
        NativeOrgBatchReconciliation.Result reconciled=NativeOrgBatchReconciliation.reconcile(scope,context.getRequestId(),
            context.getGroupId(),context.getOrgId(),domain,after,before,current,nativeResult.result,nativeResult.excluded);
        NativeOrgBatchReceipt.Evidence evidence=NativeOrgBatchReceipt.capture(context.getRequestId(),context.getGroupId(),
            context.getOrgId(),page,context.getObjects(),reconciled);
        ArchiveBatchReceipt receipt=ArchiveBatchReceipt.verifyOrganization(context.getRequestId(),context.getGroupId(),
            context.getOrgId(),page,context.getObjects(),evidence);
        List<ArchiveBatchResult.Unit> units=new ArrayList<ArchiveBatchResult.Unit>();
        Map<String,List<SnapshotRow>> prior=reconciled.getBeforeByArchive(),saved=reconciled.getAfterByArchive();
        for(NativeOrgBatchReceipt.Header header:evidence.getHeaders()) {
            String client=header.getClientId(),state=header.getState();
            units.add(new ArchiveBatchResult.Unit(client,header.getRootPk(),state));
            if(("SUCCESS".equals(state) || "PARTIAL".equals(state)) && audit.isEnabledFor(client))
                EsignValues.require(prior.get(client)!=null && !prior.get(client).isEmpty()
                    && saved.get(client)!=null && !saved.get(client).isEmpty(),"组织页签变更缺少完整审计快照。");
        }
        flow.write(evidence);
        for(ArchiveBatchResult.Unit unit:units) if(("SUCCESS".equals(unit.getState()) || "PARTIAL".equals(unit.getState()))
                && audit.isEnabledFor(unit.getClientId())) {
            audit.before(unit.getClientId(),prior.get(unit.getClientId()));audit.after(unit.getClientId(),saved.get(unit.getClientId()));
        }
        flow.complete();scope.requireExecutionContext(context);
        return new Outcome(nativeResult.result,receipt,units,evidence);
    }

    /** Real dependencies are fixed here; caller-provided payloads never name a class, table or native service. */
    private static final class DatabaseFlow implements Flow {
        private final NativeOrgBatchReceiptStore store=new NativeOrgBatchReceiptStore();
        private final TransactionSynchronizationRegistry transaction;private final Object transactionKey;
        private NativeOrgBatchReceiptStore.Prepared receipt;
        DatabaseFlow(TransactionSynchronizationRegistry transaction,Object transactionKey) {
            this.transaction=transaction;this.transactionKey=transactionKey;
        }
        public void preflight(PartnerOrgBatchScope.Prepared scope,String request,String token) throws BusinessException {
            receipt=store.preflight(request,token,scope);
        }
        public List<SuperVO> load(PartnerOrgBatchScope.Prepared scope) throws BusinessException {
            NativeOrgBatchPage page=scope.getImpact().getPage();List<NativeOrgBatchSelection.Row> rows=scope.getImpact().getRows();
            List<SuperVO> result=new ArrayList<SuperVO>();
            for(int start=0;start<rows.size();) {
                int end=Math.min(start+400,rows.size());String[] pks=new String[end-start];
                for(int index=start;index<end;index++)pks[index-start]=rows.get(index).getId();
                Object[] values=MDPersistenceService.lookupPersistenceQueryService().queryBillOfVOByPKsWithOrder(
                    page.nativeType(),pks,page.childAttributes());
                EsignValues.require(values!=null && values.length==pks.length,"无法完整读取已授权的组织页签。");
                for(Object value:values) {
                    EsignValues.require(value instanceof SuperVO,"组织页签服务端数据类型不正确。");result.add((SuperVO)value);
                }
                scope.requireLockedVerification();start=end;
            }
            return result;
        }
        public void requireBoundary() throws BusinessException {
            NativeTransactionBoundaryProbe.requireSingleDataSource(transaction,transactionKey);
        }
        public NativeOrgBatchNativeService.Execution invoke(PartnerOrgBatchScope.Prepared scope,String[] permissions) throws BusinessException {
            return NativeOrgBatchNativeService.open(scope.getImpact().getPage()).execute(scope,permissions);
        }
        public NativeOrgBatchSnapshots.Capture recapture(PartnerOrgBatchScope.Prepared scope) throws BusinessException {
            return NativeOrgBatchSnapshots.recapture(scope.getEvidence(),false);
        }
        public void write(NativeOrgBatchReceipt.Evidence evidence) throws BusinessException { store.write(receipt,evidence); }
        public void complete() throws BusinessException { store.requireCompleted(receipt); }
    }
}
