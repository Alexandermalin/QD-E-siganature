package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Reconciles the actual NC page results with the signed selection and physical before/after evidence.
 * The caller must hold the verified authorization and the same business transaction throughout execution.
 */
public final class NativeOrgBatchReconciliation {
    private NativeOrgBatchReconciliation() { }

    public static final class Page {
        private final String pk,root,target,state,failureStage;
        private Page(NativeOrgBatchSelection.Row row,NativeBatchUpdateOutcome.Unit unit,String failureStage) {
            this.pk=row.getId();this.root=row.getRoot();this.target=row.getTarget();
            this.state=unit.getState();this.failureStage=failureStage;
        }
        public String getPk() { return pk; }
        public String getRoot() { return root; }
        public String getTarget() { return target; }
        public String getState() { return state; }
        public String getFailureStage() { return failureStage; }
    }

    /** Check the exact locked page set before invoking any native business service. */
    public static void requireBefore(PartnerOrgBatchScope.Prepared scope,OperationContext context,
            List<? extends SuperVO> beforeVOs) throws BusinessException {
        EsignValues.require(scope!=null && context!=null && scope.getImpact()!=null,"组织页签批改缺少锁定范围。");
        scope.requireExecutionContext(context);
        NativeOrgBatchPage page=scope.getImpact().getPage();
        EsignValues.require(page!=null,"组织页签批改缺少原生页签。");
        validateVOs(page,scope.getImpact(),beforeVOs,page.nativeType());
    }

    public static final class Result {
        private final List<Page> pages;
        private final List<NativeOrgBatchOutcome.Root> roots;
        private final NativeOrgBatchPage page;
        private final List<String> targets;
        private final Map<String,List<SnapshotRow>> before,after;
        private final String requestId,group,operationOrg,domain;
        private final Map<String,BusinessObjectRef> authorized;
        private Result(NativeOrgBatchPage page,List<String> targets,List<Page> pages,List<NativeOrgBatchOutcome.Root> roots,Map<String,List<SnapshotRow>> before,
                Map<String,List<SnapshotRow>> after,String requestId,String group,String operationOrg,String domain,List<BusinessObjectRef> authorized) {
            this.page=page;this.targets=Collections.unmodifiableList(new ArrayList<String>(targets));
            this.pages=Collections.unmodifiableList(new ArrayList<Page>(pages));this.roots=roots;
            this.before=before;this.after=after;
            this.requestId=requestId;this.group=group;this.operationOrg=operationOrg;this.domain=domain;
            Map<String,BusinessObjectRef> refs=new LinkedHashMap<String,BusinessObjectRef>();
            for(BusinessObjectRef ref:authorized)refs.put(ref.getClientObjectId(),ref);
            this.authorized=Collections.unmodifiableMap(refs);
        }
        public NativeOrgBatchPage getPage() { return page; }
        public List<String> getTargets() { return targets; }
        public List<Page> getPages() { return pages; }
        public List<NativeOrgBatchOutcome.Root> getRoots() { return roots; }
        public Map<String,List<SnapshotRow>> getBeforeByArchive() { return before; }
        public Map<String,List<SnapshotRow>> getAfterByArchive() { return after; }
        /** A reconciled result may only be turned into a receipt for its original authorization. */
        boolean matches(String requestId,String group,String operationOrg,String domain,List<BusinessObjectRef> objects) {
            if(!this.requestId.equals(requestId) || !this.group.equals(group) || !this.operationOrg.equals(operationOrg) || !this.domain.equals(domain)
                    || objects==null || objects.size()!=authorized.size())return false;
            Map<String,BusinessObjectRef> expected=new HashMap<String,BusinessObjectRef>(authorized);
            for(BusinessObjectRef actual:objects) {
                BusinessObjectRef prior=actual==null?null:expected.remove(actual.getClientObjectId());
                if(prior==null || !same(prior.getObjectType(),actual.getObjectType()) || !same(prior.getObjectPk(),actual.getObjectPk())
                        || !same(prior.getBillCode(),actual.getBillCode()) || !same(prior.getParentClientObjectId(),actual.getParentClientObjectId())
                        || !same(prior.getDesiredOwnerOrg(),actual.getDesiredOwnerOrg()) || !same(prior.getPolicyOwnerOrg(),actual.getPolicyOwnerOrg())
                        || !same(prior.getTargetOrg(),actual.getTargetOrg()))return false;
            }
            return expected.isEmpty();
        }
        private static boolean same(String left,String right) { return left==null?right==null:left.equals(right); }
    }

    /** VOs must be loaded by the server in the same transaction, before and after the native operation. */
    public static Result reconcile(PartnerOrgBatchScope.Prepared scope,String requestId,String group,String operationOrg,String domain,
            NativeOrgBatchSnapshots.Capture after,
            List<? extends SuperVO> beforeVOs,List<? extends SuperVO> afterVOs,
            ErrLogReturnValue nativeResult,Map<String,String> excluded) throws BusinessException {
        EsignValues.require(scope!=null,"组织页签批改缺少已验证的锁定范围。");
        scope.requireRequest(requestId);
        NativeOrgBatchSelection.Impact impact=scope.getImpact();PartnerBatchPayload.Organization payload=scope.getPayload();
        EsignValues.require(impact!=null && payload!=null && impact.getPage()==payload.getPage() && payload.getChanges()!=null,
            "组织页签批改锁定范围与载荷不一致。");
        return reconcileInternal(requestId,group,operationOrg,domain,scope.getObjects(),impact,payload.getChanges(),scope.getEvidence(),after,
            beforeVOs,afterVOs,nativeResult,excluded,impact.getPage().nativeType());
    }

    /** Offline-only seam for VOs that cannot load NC metadata without a running server. */
    static Result reconcileForTest(String requestId,String group,String operationOrg,String domain,List<BusinessObjectRef> authorized,
            NativeOrgBatchSelection.Impact impact,NativeBatchChangeSet changes,
            NativeOrgBatchSnapshots.Capture before,NativeOrgBatchSnapshots.Capture after,
            List<? extends SuperVO> beforeVOs,List<? extends SuperVO> afterVOs,
            ErrLogReturnValue nativeResult,Map<String,String> excluded,Class<? extends SuperVO> nativeType) throws BusinessException {
        return reconcileInternal(requestId,group,operationOrg,domain,authorized,impact,changes,before,after,beforeVOs,afterVOs,
            nativeResult,excluded,nativeType);
    }

    private static Result reconcileInternal(String requestId,String group,String operationOrg,String domain,List<BusinessObjectRef> authorized,
            NativeOrgBatchSelection.Impact impact,NativeBatchChangeSet changes,
            NativeOrgBatchSnapshots.Capture before,NativeOrgBatchSnapshots.Capture after,
            List<? extends SuperVO> beforeVOs,List<? extends SuperVO> afterVOs,
            ErrLogReturnValue nativeResult,Map<String,String> excluded,Class<? extends SuperVO> nativeType) throws BusinessException {
        EsignValues.require(requestId!=null && requestId.matches("[a-fA-F0-9]{32}")
            && impact!=null && changes!=null && before!=null && after!=null && after.follows(before)
            && after.matches(impact) && excluded!=null && nativeType!=null,"组织页签批改缺少同一执行前范围的完整结果或快照。");
        NativeBatchRequestScope.verifyOrganization(group,operationOrg,domain,authorized,changes,impact,before);
        NativeOrgBatchPage page=impact.getPage();
        List<SuperVO> prior=validateVOs(page,impact,beforeVOs,nativeType),current=validateVOs(page,impact,afterVOs,nativeType);
        List<NativeBatchUpdateOutcome.Unit> units;
        if(impact.getRows().isEmpty()) {
            EsignValues.require(nativeResult==null && excluded.isEmpty(),"无匹配组织页签时不能提交原生执行结果。");
            units=Collections.emptyList();
        } else {
            Set<String> unchanged=changes.unchanged(prior),persisted=changes.unchanged(current);
            units=NativeBatchUpdateOutcome.reconcile(before.pageObjects(),unchanged,excluded,nativeType,nativeResult,
                before.pages(),after.pages());
            Map<String,SuperVO> priorByPk=new HashMap<String,SuperVO>();
            for(SuperVO vo:prior) priorByPk.put(vo.getPrimaryKey(),vo);
            for(NativeBatchUpdateOutcome.Unit unit:units) if("SUCCESS".equals(unit.getState()))
            {
                EsignValues.require(persisted.contains(unit.getPk()),"组织页签批改成功结果与实际保存值不一致，事务必须回滚。");
                changes.requirePhysicalChanges(priorByPk.get(unit.getPk()),page.table,unit.getPk(),
                    before.pages().get(unit.getPk()),after.pages().get(unit.getPk()));
            }
        }
        Map<String,NativeBatchUpdateOutcome.Unit> results=new HashMap<String,NativeBatchUpdateOutcome.Unit>();
        for(NativeBatchUpdateOutcome.Unit unit:units) results.put(unit.getPk(),unit);
        List<Page> pages=new ArrayList<Page>();
        for(NativeOrgBatchSelection.Row row:impact.getRows()) {
            NativeBatchUpdateOutcome.Unit unit=results.get(row.getId());
            EsignValues.require(unit!=null,"组织页签批改缺少逐行结果。");
            pages.add(new Page(row,unit,"FAILED".equals(unit.getState())?excluded.get(row.getId()):null));
        }
        return new Result(page,impact.getTargets(),pages,NativeOrgBatchOutcome.group(group,domain,authorized,impact,units),
            before.byArchive(group,domain,authorized),after.byArchive(group,domain,authorized),requestId,group,operationOrg,domain,authorized);
    }

    private static List<SuperVO> validateVOs(NativeOrgBatchPage page,NativeOrgBatchSelection.Impact impact,
            List<? extends SuperVO> values,Class<? extends SuperVO> nativeType) throws BusinessException {
        EsignValues.require(page!=null && values!=null,"组织页签批改缺少服务端数据。");
        Map<String,NativeOrgBatchSelection.Row> expected=new LinkedHashMap<String,NativeOrgBatchSelection.Row>();
        for(NativeOrgBatchSelection.Row row:impact.getRows()) expected.put(row.getId(),row);
        Map<String,SuperVO> actual=new HashMap<String,SuperVO>();
        for(SuperVO vo:values) {
            EsignValues.require(vo!=null && nativeType.equals(vo.getClass()),"组织页签批改服务端数据类型不正确。");
            String pk=vo.getPrimaryKey();NativeOrgBatchSelection.Row row=expected.get(pk);
            EsignValues.require(row!=null && actual.put(pk,vo)==null && row.getRoot().equals(vo.getAttributeValue(page.rootKey))
                && row.getTarget().equals(vo.getAttributeValue("pk_org"))
                && row.getGroup().equals(vo.getAttributeValue("pk_group")),
                "组织页签批改服务端数据重复、转移或不属于授权范围。");
        }
        EsignValues.require(actual.size()==expected.size(),"组织页签批改服务端数据不完整。");
        List<SuperVO> ordered=new ArrayList<SuperVO>();
        for(String pk:expected.keySet()) ordered.add(actual.get(pk));
        return ordered;
    }
}
