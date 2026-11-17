package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.ArchiveBatchStatus;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Checks a read-only persisted batch status against the exact request after its RPC reply is lost. */
final class QdPartnerBatchRecovery {
    private QdPartnerBatchRecovery() { }
    interface Reader { ArchiveBatchStatus read(String requestId) throws BusinessException; }

    static Summary read(OperationContext request,Reader reader) throws BusinessException {
        if(request==null || request.getRequestId()==null || reader==null) throw new BusinessException("快速批改恢复查询缺少请求号。");
        return verify(request,reader.read(request.getRequestId()));
    }

    static final class Summary {
        final String requestId,state;final int succeeded,failed,partial,unchanged,pending,unknown;final List<String> examples;
        final List<ArchiveBatchStatus.Unit> units;final ArchiveBatchStatus.Organization organization;
        Summary(String requestId,String state,int succeeded,int failed,int partial,int unchanged,int pending,int unknown,List<String> examples,
                List<ArchiveBatchStatus.Unit> units,ArchiveBatchStatus.Organization organization) {
            this.requestId=requestId;this.state=state;this.succeeded=succeeded;this.failed=failed;
            this.partial=partial;this.unchanged=unchanged;this.pending=pending;this.unknown=unknown;this.organization=organization;
            this.examples=java.util.Collections.unmodifiableList(new ArrayList<String>(examples));
            this.units=java.util.Collections.unmodifiableList(new ArrayList<ArchiveBatchStatus.Unit>(units));
        }
        boolean terminal() { return "SUCCESS".equals(state) || "PARTIAL".equals(state) || "FAILED".equals(state)
            || "CANCELLED".equals(state) || "EXPIRED".equals(state); }
        String describe() {
            String label="SUCCESS".equals(state)?"已完成":"PARTIAL".equals(state)?"部分完成":"FAILED".equals(state)?"失败"
                :"CANCELLED".equals(state)?"已取消":"EXPIRED".equals(state)?"已过期":"RUNNING".equals(state)?"执行中"
                :"UNKNOWN".equals(state)?"结果未知":"待执行";
            StringBuilder text=new StringBuilder("请求号 ").append(requestId).append(" 的持久化状态：").append(label)
                .append("；成功 ").append(succeeded).append("，失败 ").append(failed);
            if(organization!=null)text.append("，部分成功 ").append(partial);
            text.append("，未变化 ").append(unchanged)
                .append("，待定 ").append(pending).append("，未知 ").append(unknown).append("。");
            if(!examples.isEmpty()) {
                text.append("失败档案示例：");
                for(int i=0;i<examples.size();i++) { if(i>0) text.append('、');text.append(examples.get(i)); }
                if(failed>examples.size()) text.append("（仅列前 ").append(examples.size()).append(" 条）");
                text.append('。');
            }
            if(organization!=null) text.append(organization.isResultAvailable()?"组织页签明细已核验。":"尚无组织页签业务结果，目标组织范围仍可查看。");
            return text.append("原生回执不可用，请重新查询档案并核对请求结果，不要直接重试整批。").toString();
        }
    }

    static Summary verify(OperationContext request,ArchiveBatchStatus status) throws BusinessException {
        if(request==null || status==null || request.getRequestId()==null || !request.getRequestId().equals(status.getRequestId())
                || !ArchiveBatchResult.isPartnerBatchUpdate(request.getNodeCode(),request.getButtonCode(),request.getOperationType())
                || !OperationContext.OWNER_POLICY_PROTOCOL.equals(request.getPolicyProtocol()) || request.getOrgId()==null
                || request.getObjects().isEmpty() || status.getUnits()==null || status.getUnits().size()!=request.getObjects().size())
            throw new BusinessException("快速批改恢复结果与请求不一致。");
        String node=request.getNodeCode();
        String domain=("10140CUG".equals(node) || "10140CUO".equals(node))?"BD_CUSTOMER"
            :("10140SUG".equals(node) || "10140SUO".equals(node))?"BD_SUPPLIER":"BD_MATERIAL";
        Map<String,BusinessObjectRef> expected=new HashMap<String,BusinessObjectRef>();Set<String> pks=new HashSet<String>();
        for(BusinessObjectRef ref:request.getObjects()) if(ref==null || ref.getClientObjectId()==null || ref.getObjectPk()==null
                || !domain.equals(ref.getObjectType()) || ref.getParentClientObjectId()!=null || ref.getBillCode()==null
                || !pks.add(ref.getObjectPk()) || expected.put(ref.getClientObjectId(),ref)!=null)
            throw new BusinessException("快速批改恢复请求缺少唯一档案标识。");
        ArchiveBatchStatus.Organization organization=status.getOrganization();
        boolean organizationRequest=false;
        for(BusinessObjectRef ref:request.getObjects())if(ref.getClientObjectId().startsWith("orgbulk"))organizationRequest=true;
        if(organizationRequest!=(organization!=null))throw new BusinessException("快速批改恢复结果缺少对应组织页签范围。");
        Map<String,ArchiveBatchStatus.Unit> actual=new HashMap<String,ArchiveBatchStatus.Unit>();
        int succeeded=0,failed=0,partial=0,unchanged=0,pending=0,unknown=0;List<String> examples=new ArrayList<String>();
        for(ArchiveBatchStatus.Unit unit:status.getUnits()) {
            BusinessObjectRef ref=unit==null?null:expected.remove(unit.getClientId());
            if(ref==null || !ref.getObjectPk().equals(unit.getObjectPk()) || !ref.getBillCode().equals(unit.getCode())
                    || unit.getOwnerOrg()==null || unit.getOwnerOrg().trim().isEmpty() || !request.getOrgId().equals(unit.getTargetOrg()))
                throw new BusinessException("快速批改恢复结果的档案或目标组织不一致。");
            actual.put(unit.getClientId(),unit);
            String value=unit.getState();
            if("SUCCESS".equals(value)) succeeded++;
            else if("FAILED".equals(value)) { failed++;if(examples.size()<10) examples.add(unit.getCode()); }
            else if(organizationRequest && "PARTIAL".equals(value))partial++;
            else if("UNCHANGED".equals(value)) unchanged++;
            else if("PENDING".equals(value)) pending++;
            else if("UNKNOWN".equals(value)) unknown++;
            else throw new BusinessException("快速批改恢复结果包含未知对象状态。");
        }
        String state=status.getState();boolean complete=pending==0 && unknown==0;
        boolean valid="SUCCESS".equals(state)?complete && failed==0 && partial==0
            :"PARTIAL".equals(state)?complete && succeeded+partial>0 && failed+partial>0
            :"FAILED".equals(state)?(complete && succeeded==0 && partial==0 && failed>0) || pending==request.getObjects().size()
            :"UNKNOWN".equals(state)?succeeded==0 && failed==0 && partial==0 && unchanged==0
            :"PENDING".equals(state) || "READY".equals(state) || "RUNNING".equals(state)
                || "CANCELLED".equals(state) || "EXPIRED".equals(state)?pending==request.getObjects().size()
            :false;
        if(!expected.isEmpty() || !valid) throw new BusinessException("快速批改恢复结果的批次和对象状态不一致。");
        if(organizationRequest)verifyOrganization(request,state,organization,actual);
        return new Summary(status.getRequestId(),state,succeeded,failed,partial,unchanged,pending,unknown,examples,status.getUnits(),organization);
    }

    private static void verifyOrganization(OperationContext request,String businessState,ArchiveBatchStatus.Organization details,
            Map<String,ArchiveBatchStatus.Unit> units) throws BusinessException {
        nc.vo.uapbd.qd.esign.PartnerBatchDraft draft=request.getPartnerBatchDraft();
        if(draft==null || draft.getTabCode()==null || !draft.getTabCode().equals(details.getTabCode())
                || details.getTargets()==null || details.getHeaders()==null || details.getPages()==null)
            throw new BusinessException("组织批改恢复结果缺少已授权的页签或目标。");
        Set<String> expectedTargets=new HashSet<String>(java.util.Arrays.asList(draft.getTargetOrgs()));
        Set<String> actualTargets=new HashSet<String>(details.getTargets());
        if(expectedTargets.isEmpty() || expectedTargets.size()!=draft.getTargetOrgs().length
                || actualTargets.size()!=details.getTargets().size() || !expectedTargets.equals(actualTargets))
            throw new BusinessException("组织批改恢复目标与请求不一致。");
        Map<String,nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page> prepared=new HashMap<String,nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page>();
        if(draft.hasPageSelection()!=details.hasPreparedSelection())
            throw new BusinessException("组织批改恢复结果缺少已授权的页签行。");
        if(draft.hasPageSelection()) {
            Set<String> roots=new HashSet<String>();for(BusinessObjectRef ref:request.getObjects())roots.add(ref.getObjectPk());
            for(nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page page:draft.getSelectedPages())
                if(page==null || !roots.contains(page.getRoot()) || !actualTargets.contains(page.getTarget())
                        || page.getId()==null || !page.getId().matches("[A-Za-z0-9_-]{1,20}")
                        || prepared.put(page.getId(),page)!=null)
                    throw new BusinessException("组织批改请求的已授权页签行无效或重复。");
            for(nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page page:details.getPreparedPages()) {
                nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page expected=page==null?null:prepared.remove(page.getId());
                if(expected==null || !expected.getRoot().equals(page.getRoot()) || !expected.getTarget().equals(page.getTarget()))
                    throw new BusinessException("组织批改恢复范围与已授权的页签行不一致。");
            }
            if(!prepared.isEmpty())throw new BusinessException("组织批改恢复范围缺少已授权页签行。");
        }
        String fingerprint=null;
        for(BusinessObjectRef ref:request.getObjects()) {
            String client=ref.getClientObjectId(),pk=ref.getObjectPk();
            if(pk==null || !pk.matches("[A-Za-z0-9_-]{1,20}") || client==null || !client.matches("orgbulk:[a-f0-9]{64}:[A-Za-z0-9_-]{1,20}"))
                throw new BusinessException("组织批改恢复请求的档案标识无效。");
            String digest=client.substring(8,72);
            if(!client.equals("orgbulk:"+digest+":"+pk) || (fingerprint!=null && !fingerprint.equals(digest)))
                throw new BusinessException("组织批改恢复请求混入其他授权范围。");
            fingerprint=digest;
        }
        if(!details.isResultAvailable()) {
            if(!details.getHeaders().isEmpty() || !details.getPages().isEmpty())
                throw new BusinessException("组织批改没有已确认结果却包含页签明细。");
            if("SUCCESS".equals(businessState) || "PARTIAL".equals(businessState))
                throw new BusinessException("组织批改缺少业务页签回执。");
            for(ArchiveBatchStatus.Unit unit:units.values())
                if(!"PENDING".equals(unit.getState()) && !"UNKNOWN".equals(unit.getState())
                        && !("FAILED".equals(businessState) && "FAILED".equals(unit.getState())
                            && "BATCH_ROLLED_BACK".equals(unit.getErrorCode())))
                    throw new BusinessException("组织批改缺少能证明档案结果的页签回执。");
            return;
        }
        Map<String,Integer> counts=new HashMap<String,Integer>();Map<String,int[]> flags=new HashMap<String,int[]>();
        for(String client:units.keySet()) flags.put(client,new int[3]);
        for(ArchiveBatchStatus.PageHeader header:details.getHeaders())
            if(header==null || !units.containsKey(header.getClientId()) || header.getPageCount()<0
                    || counts.put(header.getClientId(),Integer.valueOf(header.getPageCount()))!=null)
                throw new BusinessException("组织批改恢复结果的页签表头无效。");
        Set<String> pageIds=new HashSet<String>();
        Map<String,nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page> selectedPages=new HashMap<String,nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page>();
        if(details.hasPreparedSelection())for(nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page page:details.getPreparedPages())
            selectedPages.put(page.getId(),page);
        for(ArchiveBatchStatus.Page page:details.getPages()) {
            int[] flagsForRoot=page==null?null:flags.get(page.getClientId());String state=page==null?null:page.getState();
            if(flagsForRoot==null || page.getPagePk()==null || !page.getPagePk().matches("[A-Za-z0-9_-]{1,20}")
                    || !pageIds.add(page.getPagePk()) || !actualTargets.contains(page.getTargetOrg()))
                throw new BusinessException("组织批改恢复结果包含重复或未授权页签。");
            if(details.hasPreparedSelection()) {
                nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page selected=selectedPages.remove(page.getPagePk());
                if(selected==null || !selected.getRoot().equals(units.get(page.getClientId()).getObjectPk())
                        || !selected.getTarget().equals(page.getTargetOrg()))
                    throw new BusinessException("组织批改业务结果与准备的页签范围不一致。");
            }
            if("SUCCESS".equals(state) && page.getFailureStage()==null)flagsForRoot[0]++;
            else if("FAILED".equals(state) && ("PERMISSION".equals(page.getFailureStage())
                    || "VALIDATION".equals(page.getFailureStage()) || "BEFORE_EVENT".equals(page.getFailureStage())))flagsForRoot[1]++;
            else if(!"UNCHANGED".equals(state) || page.getFailureStage()!=null)
                throw new BusinessException("组织批改恢复结果的页签状态无效。");
            flagsForRoot[2]++;
        }
        if(!selectedPages.isEmpty())throw new BusinessException("组织批改业务结果遗漏已授权的页签行。");
        if(counts.size()!=units.size())throw new BusinessException("组织批改恢复结果缺少档案页签表头。");
        for(Map.Entry<String,int[]> item:flags.entrySet()) {
            int[] value=item.getValue();String state=value[1]>0?(value[0]>0?"PARTIAL":"FAILED"):value[0]>0?"SUCCESS":"UNCHANGED";
            if(counts.get(item.getKey()).intValue()!=value[2] || !state.equals(units.get(item.getKey()).getState()))
                throw new BusinessException("组织批改恢复结果的页签数量或档案状态不一致。");
        }
    }
}
