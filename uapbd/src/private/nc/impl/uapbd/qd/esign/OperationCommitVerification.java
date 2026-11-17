package nc.impl.uapbd.qd.esign;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.bs.uapbd.qd.esign.core.PartnerBatchRoute;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.ArchiveBatchStatus;
import nc.vo.uapbd.qd.esign.ArchiveBatchUpdateResult;
import nc.vo.uapbd.qd.esign.ArchiveOrgBatchUpdateResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationResponse;

/** Compares the reply with independently reread durable request and object evidence. */
final class OperationCommitVerification {
    private OperationCommitVerification() { }

    static void verify(String id,String token,OperationResponse response,Map<String,Object> request,
            List<Map<String,Object>> objects,ArchiveBatchStatus batchStatus) throws BusinessException {
        verify(id,token,response,request,objects,batchStatus,null);
    }
    /** The optional VO type is used by offline fixtures where NC primary-key metadata is unavailable. */
    static void verify(String id,String token,OperationResponse response,Map<String,Object> request,
            List<Map<String,Object>> objects,ArchiveBatchStatus batchStatus,Class<? extends SuperVO> nativeType) throws BusinessException {
        EsignValues.require(id!=null && request!=null && response!=null && id.equals(response.getRequestId())
            && id.equals(EsignValues.text(request,"request_id")) && request.get("finished_at")!=null
            && ("SUCCESS".equals(EsignValues.text(request,"signature_state"))
                || "NOT_REQUIRED".equals(EsignValues.text(request,"signature_state")))
            && (token==null?response.isReplayed() && response.getBusinessResult()==null
                :!response.isReplayed() && token.equals(EsignValues.text(request,"execution_token"))),
            "业务提交结果尚未确认，请按请求号核对。");
        String state=EsignValues.text(request,"business_state");
        boolean batch=ArchiveBatchResult.isArchiveBatch(EsignValues.text(request,"node_code"),EsignValues.text(request,"button_code"),
            EsignValues.text(request,"operation_type"));
        EsignValues.require(("SUCCESS".equals(state) || (batch && ("PARTIAL".equals(state) || "FAILED".equals(state))))
            && (!response.isReplayed() || "SUCCESS".equals(state)) && request.get("error_code")==null,
            "业务结果尚未持久化或状态不一致，请按请求号核对。");
        EsignValues.require(objects!=null && !objects.isEmpty() && response.getObjects()!=null
            && objects.size()==response.getObjects().size(),"业务提交对象不完整，请按请求号核对。");
        Map<String,BusinessObjectRef> saved=new HashMap<String,BusinessObjectRef>();
        for(BusinessObjectRef ref:ObjectRelations.restore(objects))
            EsignValues.require(ref!=null && saved.put(ref.getClientObjectId(),ref)==null,"持久化业务对象重复。");
        for(BusinessObjectRef ref:response.getObjects()) {
            BusinessObjectRef persisted=ref==null?null:saved.remove(ref.getClientObjectId());
            EsignValues.require(persisted!=null && same(ref.getObjectType(),persisted.getObjectType())
                && same(ref.getObjectPk(),persisted.getObjectPk()) && same(ref.getBillCode(),persisted.getBillCode())
                && same(ref.getParentClientObjectId(),persisted.getParentClientObjectId()),"业务返回对象与持久化结果不一致。");
        }
        EsignValues.require(saved.isEmpty(),"业务返回缺少持久化对象。");
        if(!batch) { EsignValues.require(batchStatus==null,"非批量业务包含批量回执。");return; }
        EsignValues.require(batchStatus!=null && id.equals(batchStatus.getRequestId()) && state.equals(batchStatus.getState())
            && batchStatus.getUnits()!=null && batchStatus.getUnits().size()==objects.size(),"批量业务持久化结果不完整。");
        if(response.isReplayed())return;
        List<ArchiveBatchResult.Unit> result;
        if(batchStatus.getOrganization()!=null) {
            EsignValues.require(ArchiveBatchResult.isPartnerBatchUpdate(EsignValues.text(request,"node_code"),EsignValues.text(request,"button_code"),
                EsignValues.text(request,"operation_type")) && response.getBusinessResult() instanceof ArchiveOrgBatchUpdateResult,
                "组织页签批改返回类型不正确。");
            ArchiveOrgBatchUpdateResult organization=(ArchiveOrgBatchUpdateResult)response.getBusinessResult();
            NativeOrgBatchPage page=NativeOrgBatchPage.require(PartnerBatchRoute.domain(EsignValues.text(request,"node_code")),
                batchStatus.getOrganization().getTabCode());
            verifyOrganizationNative(organization,batchStatus,EsignValues.text(request,"pk_group"),page,
                nativeType==null?page.nativeType():nativeType);
            result=organization.getUnits();
        } else if(ArchiveBatchResult.isPartnerBatchUpdate(EsignValues.text(request,"node_code"),EsignValues.text(request,"button_code"),
                EsignValues.text(request,"operation_type"))) {
            EsignValues.require(response.getBusinessResult() instanceof ArchiveBatchUpdateResult,"批改返回类型不正确。");
            result=((ArchiveBatchUpdateResult)response.getBusinessResult()).getUnits();
        } else {
            EsignValues.require(response.getBusinessResult() instanceof ArchiveBatchResult,"档案批量返回类型不正确。");
            result=((ArchiveBatchResult)response.getBusinessResult()).getUnits();
        }
        EsignValues.require(result!=null && result.size()==batchStatus.getUnits().size(),"批量业务返回结果不完整。");
        Map<String,ArchiveBatchStatus.Unit> durable=new HashMap<String,ArchiveBatchStatus.Unit>();
        for(ArchiveBatchStatus.Unit unit:batchStatus.getUnits()) EsignValues.require(unit!=null && unit.getClientId()!=null
            && durable.put(unit.getClientId(),unit)==null,"批量业务持久化结果重复。");
        for(ArchiveBatchResult.Unit unit:result) {
            ArchiveBatchStatus.Unit persisted=unit==null?null:durable.remove(unit.getClientId());
            EsignValues.require(persisted!=null && same(unit.getObjectPk(),persisted.getObjectPk())
                && same(unit.getState(),persisted.getState()),"批量业务返回与持久化结果不一致。");
        }
        EsignValues.require(durable.isEmpty(),"批量业务返回缺少持久化结果。");
    }
    private static void verifyOrganizationNative(ArchiveOrgBatchUpdateResult result,ArchiveBatchStatus status,String group,
            NativeOrgBatchPage page,Class<? extends SuperVO> nativeType) throws BusinessException {
        ArchiveBatchStatus.Organization organization=status.getOrganization();
        EsignValues.require(result!=null && organization!=null && organization.isResultAvailable() && organization.hasPreparedSelection()
            && result.getPageCount()>=0 && result.getPageCount()<=nc.vo.uapbd.qd.esign.PartnerBatchDraft.MAX_SINGLE_TRANSACTION_PAGE_ROWS
            && result.getPageCount()==organization.getPreparedPages().size()
            && result.getPageCount()==organization.getPages().size() && group!=null && page!=null && nativeType!=null,
            "组织页签业务回执或准备范围不完整。");
        Map<String,ArchiveBatchStatus.Unit> roots=new HashMap<String,ArchiveBatchStatus.Unit>();
        for(ArchiveBatchStatus.Unit unit:status.getUnits()) EsignValues.require(unit!=null && unit.getClientId()!=null
            && roots.put(unit.getClientId(),unit)==null,"组织页签业务档案重复。");
        Set<String> targets=new HashSet<String>(organization.getTargets());
        EsignValues.require(!targets.isEmpty() && targets.size()==organization.getTargets().size(),"组织页签业务目标无效或重复。");
        for(String target:targets)EsignValues.require(validPk(target),"组织页签业务目标无效。");
        Map<String,nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page> selected=new HashMap<String,nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page>();
        for(nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page item:organization.getPreparedPages())
            EsignValues.require(item!=null && validPk(item.getId()) && validPk(item.getRoot()) && validPk(item.getTarget())
                && targets.contains(item.getTarget()) && selected.put(item.getId(),item)==null,"组织页签准备记录重复或无效。");
        Map<String,ArchiveBatchStatus.Page> successes=new HashMap<String,ArchiveBatchStatus.Page>();int failed=0;
        for(ArchiveBatchStatus.Page item:organization.getPages()) {
            ArchiveBatchStatus.Unit root=item==null?null:roots.get(item.getClientId());
            nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page planned=item==null?null:selected.remove(item.getPagePk());
            EsignValues.require(root!=null && planned!=null && same(root.getObjectPk(),planned.getRoot())
                && same(item.getTargetOrg(),planned.getTarget()),"组织页签业务结果与准备范围不一致。");
            if("SUCCESS".equals(item.getState())) {
                EsignValues.require(item.getFailureStage()==null && successes.put(item.getPagePk(),item)==null,"组织页签成功记录重复或无效。");
            } else if("FAILED".equals(item.getState())) {
                EsignValues.require("PERMISSION".equals(item.getFailureStage()) || "VALIDATION".equals(item.getFailureStage())
                    || "BEFORE_EVENT".equals(item.getFailureStage()),"组织页签失败阶段无效。");failed++;
            } else EsignValues.require("UNCHANGED".equals(item.getState()) && item.getFailureStage()==null,"组织页签状态无效。");
        }
        EsignValues.require(selected.isEmpty(),"组织页签结果遗漏已准备的行。");
        ErrLogReturnValue nativeResult=result.getNativeResult();
        if(result.getPageCount()==0) {
            EsignValues.require(nativeResult==null && successes.isEmpty(),"无页签时不能返回原生业务结果。");return;
        }
        EsignValues.require(nativeResult!=null && nativeResult.getErrLogResult()!=null && nativeResult.getTotalNum()==result.getPageCount()
            && nativeResult.getErrLogResult().getErrorMessagegNum()>=0
            && (nativeResult.getErrLogResult().getErrorMessagegNum()==0 || failed>0),"原生组织页签结果数量不正确。");
        Object returned=nativeResult.getReturnValue();
        EsignValues.require(returned==null || returned instanceof SuperVO[],"原生组织页签返回值类型不正确。");
        if(returned!=null)for(SuperVO row:(SuperVO[])returned) {
            String pk=row==null?null:row.getPrimaryKey();ArchiveBatchStatus.Page item=pk==null?null:successes.remove(pk);
            ArchiveBatchStatus.Unit root=item==null?null:roots.get(item.getClientId());
            EsignValues.require(row!=null && row.getClass()==nativeType && item!=null && root!=null
                && sameValue(root.getObjectPk(),row.getAttributeValue(page.rootKey))
                && sameValue(item.getTargetOrg(),row.getAttributeValue("pk_org"))
                && sameValue(group,row.getAttributeValue("pk_group")),"原生组织页签返回了重复、未授权或已转移的行。");
        }
        EsignValues.require(successes.isEmpty(),"原生组织页签返回值遗漏已确认成功的行。");
    }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
    private static boolean sameValue(String first,Object second) { return first==null?second==null:first.equals(second); }
    private static boolean same(String first,String second) { return first==null?second==null:first.equals(second); }
}
