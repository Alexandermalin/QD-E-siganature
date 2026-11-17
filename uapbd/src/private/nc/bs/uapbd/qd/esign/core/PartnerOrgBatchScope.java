package nc.bs.uapbd.qd.esign.core;

import java.util.List;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** Couples verified native page configuration, target organizations and selected physical page identities. */
public final class PartnerOrgBatchScope {
    private PartnerOrgBatchScope() { }
    interface SnapshotReader {
        NativeOrgBatchSnapshots.Capture capture(NativeOrgBatchSelection.Impact impact,boolean lock) throws BusinessException;
    }
    public static final class Prepared {
        private final PartnerBatchPayload.Organization payload;private final NativeOrgBatchSelection.Impact impact;
        private final NativeOrgBatchSnapshots.Capture evidence;
        private final List<BusinessObjectRef> objects;
        private final NativeOrgBatchTableLock.Ticket lockTicket;
        private final String requestId;
        private final OperationContext executionContext;
        private Prepared(PartnerBatchPayload.Organization payload,NativeOrgBatchSelection.Impact impact,
                NativeOrgBatchSnapshots.Capture evidence,List<BusinessObjectRef> objects,NativeOrgBatchTableLock.Ticket lockTicket,
                String requestId,OperationContext executionContext) {
            this.payload=payload;this.impact=impact;this.evidence=evidence;this.objects=objects;this.lockTicket=lockTicket;
            this.requestId=requestId;this.executionContext=executionContext;
        }
        public PartnerBatchPayload.Organization getPayload() { return payload; }
        public NativeOrgBatchSelection.Impact getImpact() { return impact; }
        public NativeOrgBatchSnapshots.Capture getEvidence() { return evidence; }
        public List<BusinessObjectRef> getObjects() { return objects; }
        /** Native execution must remain on the same thread and in the same active lock transaction. */
        public void requireLockedVerification() throws BusinessException {
            EsignValues.require(lockTicket!=null && impact!=null,"组织页签批改缺少锁定并核实的数据范围。");
            lockTicket.require(impact.getPage());
        }
        /** A locked scope cannot be reused by another execution request. */
        public void requireRequest(String requestId) throws BusinessException {
            requireLockedVerification();
            EsignValues.require(this.requestId!=null && this.requestId.equals(requestId),"组织页签锁定范围不属于当前请求。");
        }
        Prepared bindRequest(OperationContext context) throws BusinessException {
            requireLockedVerification();
            String requestId=context==null?null:context.getRequestId();
            EsignValues.require(requestId!=null && requestId.matches("[a-fA-F0-9]{32}")
                && (this.requestId==null || this.requestId.equals(requestId)),"组织页签锁定范围缺少有效请求或已绑定其他请求。");
            EsignValues.require(context!=null && impact.getGroup().equals(context.getGroupId())
                && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
                && PartnerBatchRoute.matches(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
                && impact.getDomain().equals(PartnerBatchRoute.domain(context.getNodeCode()))
                && sameObjects(objects,context.getObjects()),"组织页签锁定范围与请求对象或入口不一致。");
            NativeBatchRequestScope.verifyOrganization(context.getGroupId(),context.getOrgId(),impact.getDomain(),context.getObjects(),
                payload.getChanges(),impact,evidence);
            if(this.requestId!=null) { requireExecutionContext(context);return this; }
            return new Prepared(payload,impact,evidence,objects,lockTicket,requestId,context);
        }
        public void requireExecutionContext(OperationContext context) throws BusinessException {
            requireRequest(context==null?null:context.getRequestId());
            EsignValues.require(executionContext!=null && context!=null
                && same(executionContext.getBatchId(),context.getBatchId()) && same(executionContext.getGroupId(),context.getGroupId())
                && same(executionContext.getOrgId(),context.getOrgId()) && same(executionContext.getNodeCode(),context.getNodeCode())
                && same(executionContext.getNodeName(),context.getNodeName()) && same(executionContext.getButtonCode(),context.getButtonCode())
                && same(executionContext.getButtonName(),context.getButtonName()) && same(executionContext.getOperationType(),context.getOperationType())
                && same(executionContext.getReason(),context.getReason()) && same(executionContext.getPolicyProtocol(),context.getPolicyProtocol())
                && sameObjects(objects,context.getObjects()),"组织页签锁定范围与业务上下文不一致。");
        }
        private static boolean sameObjects(List<BusinessObjectRef> expected,List<BusinessObjectRef> actual) {
            if(expected==null || actual==null || expected.size()!=actual.size())return false;
            java.util.Map<String,BusinessObjectRef> remaining=new java.util.HashMap<String,BusinessObjectRef>();
            for(BusinessObjectRef ref:expected)if(ref==null || remaining.put(ref.getClientObjectId(),ref)!=null)return false;
            for(BusinessObjectRef ref:actual) {
                BusinessObjectRef prior=ref==null?null:remaining.remove(ref.getClientObjectId());
                if(prior==null || !same(prior.getObjectType(),ref.getObjectType()) || !same(prior.getObjectPk(),ref.getObjectPk())
                    || !same(prior.getBillCode(),ref.getBillCode()) || !same(prior.getParentClientObjectId(),ref.getParentClientObjectId())
                    || !same(prior.getDesiredOwnerOrg(),ref.getDesiredOwnerOrg()) || !same(prior.getPolicyOwnerOrg(),ref.getPolicyOwnerOrg())
                    || !same(prior.getTargetOrg(),ref.getTargetOrg()))return false;
            }
            return remaining.isEmpty();
        }
        private static boolean same(String left,String right) { return left==null?right==null:left.equals(right); }
    }
    /** The public path rechecks function, page, target and per-archive data permissions before querying page rows. */
    public static Prepared prepare(ServerIdentity identity,OperationContext context,PartnerBatchDraft draft)
            throws BusinessException {
        String domain=requireContext(identity,context);
        requireArchivePermissions(identity,context);
        PartnerBatchPayload.Organization payload=PartnerBatchPayload.resolveOrganization(domain,draft);
        requireTargets(identity,context,payload);
        return prepare(identity.groupId,context.getOrgId(),domain,context.getObjects(),payload,
            NativeOrgBatchSelection.database(),databaseSnapshots());
    }
    /** Plan exact physical page identities; every returned slice still requires a fresh authorization. */
    public static PartnerOrgBatchPlan plan(ServerIdentity identity,OperationContext context,PartnerBatchDraft draft)
            throws BusinessException {
        String domain=requireContext(identity,context);
        EsignValues.require(draft!=null && !draft.hasPageSelection(),"组织批改分批计划必须从完整选择开始。");
        requireArchivePermissions(identity,context);
        PartnerBatchPayload.Organization payload=PartnerBatchPayload.resolveOrganization(domain,draft);
        requireTargets(identity,context,payload);
        TransactionSynchronizationRegistry registry=NativeTransactionBoundaryProbe.requireActiveTransaction();Object key=registry.getTransactionKey();
        try {
            // The full selection is read in chunks, so prevent inserts or transfers between those queries.
            NativeOrgBatchTableLock.Ticket ticket=NativeOrgBatchTableLock.acquire(payload.getPage(),registry);
            NativeOrgBatchSelection.Impact selection=impact(identity.groupId,domain,context.getObjects(),payload,
                NativeOrgBatchSelection.database(),false);
            ticket.requireCurrentMetadata();
            PartnerOrgBatchPlan result=NativeOrgBatchPartition.plan(identity.groupId,context.getOrgId(),context.getNodeCode(),selection);
            ticket.require(payload.getPage());return result;
        } catch(BusinessException failure) {
            if(TransactionRollback.isMarked(failure))throw failure;
            throw TransactionRollback.abort(registry,key,failure);
        } catch(RuntimeException failure) { throw TransactionRollback.abort(registry,key,failure); }
    }
    static Prepared prepare(String group,String org,String domain,List<BusinessObjectRef> selected,
            PartnerBatchPayload.Organization payload,NativeOrgBatchSelection.Reader reader,SnapshotReader snapshots) throws BusinessException {
        NativeOrgBatchSelection.Impact impact=impact(group,domain,selected,payload,reader,false);
        requireSingleTransaction(impact);
        EsignValues.require(snapshots!=null,"组织页签批改缺少关联证据读取器。");
        NativeOrgBatchSnapshots.Capture evidence=snapshots.capture(impact,false);
        return new Prepared(payload,impact,evidence,
            NativeBatchRequestScope.bindOrganization(group,org,domain,selected,payload.getChanges(),impact,evidence),null,null,null);
    }
    /** Recompute the complete scope; locked mode is required before executing the exact returned page IDs. */
    public static Prepared verify(ServerIdentity identity,OperationContext context,PartnerBatchDraft draft,boolean lock)
            throws BusinessException {
        String domain=requireContext(identity,context);
        requireArchivePermissions(identity,context);
        PartnerBatchPayload.Organization payload=PartnerBatchPayload.resolveOrganization(domain,draft);
        requireTargets(identity,context,payload);
        if(!lock) return verify(identity.groupId,context.getOrgId(),domain,context.getObjects(),payload,
            NativeOrgBatchSelection.database(),databaseSnapshots(),false,null);
        EsignValues.require(context.getRequestId()!=null && context.getRequestId().matches("[a-fA-F0-9]{32}"),
            "组织页签执行缺少有效请求标识。");
        TransactionSynchronizationRegistry registry=NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object key=registry.getTransactionKey();
        try {
            NativeOrgBatchTableLock.Ticket ticket=NativeOrgBatchTableLock.acquire(payload.getPage(),registry);
            Prepared result=verify(identity.groupId,context.getOrgId(),domain,context.getObjects(),payload,
                NativeOrgBatchSelection.database(),databaseSnapshots(),true,ticket).bindRequest(context);
            NativeTransactionBoundaryProbe.requireSingleDataSource(registry,key);result.requireLockedVerification();return result;
        } catch(BusinessException failure) {
            if(TransactionRollback.isMarked(failure)) throw failure;
            throw TransactionRollback.abort(registry,key,failure);
        } catch(RuntimeException failure) { throw TransactionRollback.abort(registry,key,failure); }
    }
    /** Recheck the current principal immediately before the transaction invokes the native page service. */
    public static void recheckPermissions(ServerIdentity identity,OperationContext context,Prepared scope) throws BusinessException {
        String domain=requireContext(identity,context);
        EsignValues.require(scope!=null && scope.getImpact()!=null && domain.equals(scope.getImpact().getDomain())
            && scope.getPayload()!=null,"组织页签执行缺少已验证范围。");
        scope.requireExecutionContext(context);requireArchivePermissions(identity,context);requireTargets(identity,context,scope.getPayload());
    }
    static Prepared verify(String group,String org,String domain,List<BusinessObjectRef> authorized,
            PartnerBatchPayload.Organization payload,NativeOrgBatchSelection.Reader reader,SnapshotReader snapshots,boolean lock,
            NativeOrgBatchTableLock.Ticket ticket)
            throws BusinessException {
        EsignValues.require(payload!=null && (lock?ticket!=null:ticket==null),"组织页签批改缺少匹配的锁定范围。");
        if(lock) ticket.require(payload.getPage());
        NativeOrgBatchSelection.Impact impact=impact(group,domain,authorized,payload,reader,lock);
        requireSingleTransaction(impact);
        EsignValues.require(snapshots!=null,"组织页签批改缺少关联证据读取器。");
        NativeOrgBatchSnapshots.Capture evidence=snapshots.capture(impact,lock);
        NativeBatchRequestScope.verifyOrganization(group,org,domain,authorized,payload.getChanges(),impact,evidence);
        if(lock) ticket.requireEvidence(evidence);
        return new Prepared(payload,impact,evidence,
            java.util.Collections.unmodifiableList(new java.util.ArrayList<BusinessObjectRef>(authorized)),ticket,null,null);
    }
    private static SnapshotReader databaseSnapshots() {
        return new SnapshotReader() {
            public NativeOrgBatchSnapshots.Capture capture(NativeOrgBatchSelection.Impact impact,boolean lock) throws BusinessException {
                return NativeOrgBatchSnapshots.capture(impact,lock);
            }
        };
    }
    private static NativeOrgBatchSelection.Impact impact(String group,String domain,List<BusinessObjectRef> refs,
            PartnerBatchPayload.Organization payload,NativeOrgBatchSelection.Reader reader,boolean lock) throws BusinessException {
        EsignValues.require(payload!=null && payload.getPage()!=null,"组织页签批改缺少已核实载荷。");
        return NativeOrgBatchSelection.capture(group,domain,payload.getPage(),refs,
            payload.getTargets().toArray(new String[payload.getTargets().size()]),reader,lock,payload.getSelectedPages());
    }
    static void requireSingleTransaction(NativeOrgBatchSelection.Impact impact) throws BusinessException {
        EsignValues.require(impact!=null && impact.getRows().size()<=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_PAGE_ROWS,
            "组织页签实际行超过单事务上限，请先按页签行分批并分别授权。");
    }
    private static String requireContext(ServerIdentity identity,OperationContext context) throws BusinessException {
        EsignValues.require(identity!=null && context!=null && identity.groupId!=null && identity.groupId.equals(context.getGroupId())
            && context.getOrgId()!=null && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            && PartnerBatchRoute.matches(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && !context.getObjects().isEmpty(),"组织页签批改缺少有效身份或业务上下文。");
        return PartnerBatchRoute.domain(context.getNodeCode());
    }
    private static void requireArchivePermissions(ServerIdentity identity,OperationContext context) throws BusinessException {
        EsignPermission.requireBusiness(identity,context.getNodeCode(),context.getButtonCode(),context.getOrgId());
        EsignPermission.requireArchiveData(identity,PartnerBatchRoute.permissionResource(context.getNodeCode()),"batchupdate",context.getObjects());
    }
    private static void requireTargets(ServerIdentity identity,OperationContext context,PartnerBatchPayload.Organization payload)
            throws BusinessException {
        QdEsignStore store=new QdEsignStore();
        java.util.List<String> nonGroup=new java.util.ArrayList<String>();
        for(String target:payload.getTargets()) if(!identity.groupId.equals(target)) nonGroup.add(target);
        java.util.List<String> found=store.listOrganizationsInGroup(identity.groupId,nonGroup);
        requireGroupMembership(nonGroup,found);
        EsignPermission.requirePageOrganizations(identity,context.getNodeCode(),context.getButtonCode(),payload.getPage().tabCode,
            context.getOrgId(),payload.getTargets());
    }
    static void requireGroupMembership(List<String> nonGroup,List<String> found) throws BusinessException {
        EsignValues.require(nonGroup!=null && found!=null,"组织页签批改缺少目标组织校验结果。");
        java.util.Set<String> expected=new java.util.HashSet<String>(nonGroup),actual=new java.util.HashSet<String>(found);
        EsignValues.require(!expected.contains(null) && expected.size()==nonGroup.size() && actual.size()==found.size() && actual.equals(expected),
            "组织页签批改目标不属于当前集团。");
    }
}
