package nc.ui.uapbd.qd.esign;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchManifest;

/** Validate the complete read-only server plan before any independent slice is dispatched. */
final class QdPartnerOrgBatchPlan {
    private QdPartnerOrgBatchPlan() { }
    interface Reader { PartnerOrgBatchPlan read(OperationContext context) throws BusinessException; }

    static PartnerOrgBatchPlan read(OperationContext context,Reader reader) throws BusinessException {
        if(context==null || reader==null) throw new BusinessException("组织批改缺少分批计划请求或服务。");
        return verify(context,reader.read(context));
    }

    /** Plan every root chunk before returning anything that could be independently dispatched. */
    static List<PartnerOrgBatchPlan> readAll(OperationContext context,Reader reader) throws BusinessException {
        return readAll(context,reader,PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS);
    }

    static List<PartnerOrgBatchPlan> readAll(OperationContext context,Reader reader,int maxRoots) throws BusinessException {
        if(context==null || reader==null || context.getObjects()==null || context.getObjects().isEmpty()
                || context.getPartnerBatchDraft()==null || context.getPartnerBatchDraft().hasPageSelection()
                || context.getMaterialProdImpact()!=null || context.getArchiveTreeSaveDraft()!=null || context.getPartnerMainSaveDraft()!=null
                || maxRoots<=0 || maxRoots>PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS)
            throw new BusinessException("组织批改缺少有效的完整规划请求。");
        List<PartnerOrgBatchPlan> plans=new ArrayList<PartnerOrgBatchPlan>();Set<String> pages=new HashSet<String>();
        List<BusinessObjectRef> objects=context.getObjects();
        for(int start=0;start<objects.size();) {
            int end=(int)Math.min((long)objects.size(),(long)start+maxRoots);
            OperationContext chunk=new OperationContext(context.getRequestId(),context.getBatchId(),context.getGroupId(),context.getOrgId(),
                context.getNodeCode(),context.getNodeName(),context.getButtonCode(),context.getButtonName(),context.getOperationType(),
                context.getReason(),objects.subList(start,end),context.getPolicyProtocol(),null,null,null,context.getPartnerBatchDraft());
            PartnerOrgBatchPlan plan=read(chunk,reader);
            for(PartnerOrgBatchPlan.Slice slice:plan.getSlices())for(PartnerOrgBatchPlan.Page page:slice.getPages())
                if(!pages.add(page.getId()))throw new BusinessException("组织批改分批计划跨档案分段重复了页签行。");
            plans.add(plan);start=end;
        }
        return Collections.unmodifiableList(plans);
    }

    static PartnerOrgBatchPlan verify(OperationContext request,PartnerOrgBatchPlan plan) throws BusinessException {
        if(request==null || plan==null || !OperationContext.OWNER_POLICY_PROTOCOL.equals(request.getPolicyProtocol())
                || !ArchiveBatchResult.isPartnerBatchUpdate(request.getNodeCode(),request.getButtonCode(),request.getOperationType())
                || request.getGroupId()==null || request.getOrgId()==null || request.getObjects()==null || request.getObjects().isEmpty()
                || request.getObjects().size()>PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS)
            throw new BusinessException("组织批改分批计划缺少有效请求。");
        PartnerBatchDraft draft=request.getPartnerBatchDraft();
        if(draft==null || draft.hasPageSelection() || draft.getTabCode()==null || draft.getTargetOrgs().length==0
                || !request.getGroupId().equals(plan.getGroup()) || !request.getOrgId().equals(plan.getOperationOrg())
                || !request.getNodeCode().equals(plan.getNode()) || !draft.getTabCode().equals(plan.getTabCode())
                || plan.getSelectionFingerprint()==null || !plan.getSelectionFingerprint().matches("[a-f0-9]{64}")
                || plan.getManifestFingerprint()==null || !plan.getManifestFingerprint().matches("[a-f0-9]{64}")
                || plan.getTargets()==null || plan.getSlices()==null || plan.getSlices().isEmpty() || plan.getPageCount()<0)
            throw new BusinessException("组织批改分批计划与原请求不一致。");
        String node=request.getNodeCode();
        String domain=("10140CUG".equals(node) || "10140CUO".equals(node))?"BD_CUSTOMER"
            :("10140SUG".equals(node) || "10140SUO".equals(node))?"BD_SUPPLIER":"BD_MATERIAL";
        Set<String> roots=new HashSet<String>();
        for(BusinessObjectRef ref:request.getObjects()) if(ref==null || !domain.equals(ref.getObjectType())
                || !validPk(ref.getObjectPk()) || !roots.add(ref.getObjectPk()) || ref.getParentClientObjectId()!=null
                || ref.getDesiredOwnerOrg()!=null || ref.getTargetOrg()!=null)
            throw new BusinessException("组织批改分批计划的所选档案无效或重复。");
        Set<String> targets=new HashSet<String>(Arrays.asList(draft.getTargetOrgs()));
        Set<String> plannedTargets=new HashSet<String>();
        for(String target:plan.getTargets()) if(!validPk(target) || !plannedTargets.add(target))
            throw new BusinessException("组织批改分批计划的目标组织无效或重复。");
        if(targets.size()!=draft.getTargetOrgs().length || !plannedTargets.equals(targets))
            throw new BusinessException("组织批改分批计划遗漏或增加了目标组织。");
        Set<String> coveredRoots=new HashSet<String>(),pageIds=new HashSet<String>(),noPageRoots=new HashSet<String>();
        Set<String> rootsWithPages=new HashSet<String>();List<PartnerOrgBatchPlan.Page> allPages=new ArrayList<PartnerOrgBatchPlan.Page>();long pages=0;
        for(PartnerOrgBatchPlan.Slice slice:plan.getSlices()) {
            List<String> selected=slice==null?null:slice.getRoots();
            List<PartnerOrgBatchPlan.Page> ids=slice==null?null:slice.getPages();
            if(selected==null || selected.isEmpty() || selected.size()>PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS
                    || ids==null || ids.size()>PartnerBatchDraft.MAX_SINGLE_TRANSACTION_PAGE_ROWS)
                throw new BusinessException("组织批改分批计划包含无效批次或超过单事务上限。");
            Set<String> inSlice=new HashSet<String>();
            for(String root:selected) if(!roots.contains(root) || !inSlice.add(root))
                throw new BusinessException("组织批改分批计划包含未选或重复档案。");
            coveredRoots.addAll(inSlice);
            Set<String> slicePageRoots=new HashSet<String>();
            for(PartnerOrgBatchPlan.Page page:ids) {
                if(page==null || !validPk(page.getId()) || !pageIds.add(page.getId())
                        || !inSlice.contains(page.getRoot()) || !targets.contains(page.getTarget()))
                    throw new BusinessException("组织批改分批计划包含无效、跨批重复或不属于所选档案和目标的页签行。");
                slicePageRoots.add(page.getRoot());rootsWithPages.add(page.getRoot());allPages.add(page);
            }
            for(String root:inSlice) if(!slicePageRoots.contains(root) && !noPageRoots.add(root))
                throw new BusinessException("组织批改分批计划重复了无页签档案。");
            pages+=ids.size();
        }
        if(!coveredRoots.equals(roots) || !Collections.disjoint(noPageRoots,rootsWithPages)
                || pages!=plan.getPageCount())
            throw new BusinessException("组织批改分批计划遗漏档案或页签行数量不一致。");
        String manifest=PartnerOrgBatchManifest.fingerprint(plan.getGroup(),plan.getOperationOrg(),plan.getNode(),plan.getTabCode(),
            plan.getSelectionFingerprint(),new ArrayList<String>(roots),plan.getTargets(),allPages);
        if(!manifest.equals(plan.getManifestFingerprint()))
            throw new BusinessException("组织批改分批计划与服务端完整选择不一致。");
        return plan;
    }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
}
