package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.ArchiveBatchStatus;
import nc.vo.uapbd.qd.esign.ArchiveOrgBatchUpdateResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;

/** Preserve independently committed slices and never retry an uncertain request automatically. */
final class QdPartnerOrgBatchProgress {
    private QdPartnerOrgBatchProgress() { }
    interface Operation { void execute(QdPartnerOrgBatchSlices.Slice slice,int number,int total,Attempt attempt) throws BusinessException; }

    static final class Completed {
        final String requestId,group,domain;final ArchiveOrgBatchUpdateResult result;final QdPartnerBatchRecovery.Summary summary;
        private Completed(Attempt attempt) { requestId=attempt.request.getRequestId();group=attempt.request.getGroupId();
            domain=attempt.request.getObjects().get(0).getObjectType();result=attempt.result;summary=attempt.summary; }
    }
    static final class Attempt {
        private final QdPartnerOrgBatchSlices.Slice slice;private final Set<String> used;
        private OperationContext request;private ArchiveOrgBatchUpdateResult result;private QdPartnerBatchRecovery.Summary summary,recovered;
        private Attempt(QdPartnerOrgBatchSlices.Slice slice,Set<String> used) { this.slice=slice;this.used=used; }

        /** Call immediately before the business RPC, after verifying the signed context. */
        void dispatched(OperationContext context) throws BusinessException {
            if(request!=null)throw new BusinessException("组织批改批次已派发。");
            slice.requireAuthorized(context);
            if(!used.add(context.getRequestId()))throw new BusinessException("组织批改重复使用请求号。");
            request=context;
        }
        /** A successful gateway reply and its independently queried durable status must agree. */
        void confirmed(OperationResponse response,ArchiveBatchStatus status) throws BusinessException {
            if(request==null || result!=null || recovered!=null || response==null || response.isReplayed()
                    || !request.getRequestId().equals(response.getRequestId())
                    || !(response.getBusinessResult() instanceof ArchiveOrgBatchUpdateResult))
                throw new BusinessException("组织批改业务回执不完整。");
            QdPartnerBatchRecovery.Summary checked=QdPartnerBatchRecovery.verify(request,status);
            ArchiveOrgBatchUpdateResult value=(ArchiveOrgBatchUpdateResult)response.getBusinessResult();
            verifyResponse(request,response,value,checked);
            result=value;summary=checked;
        }
        /** A read-only recovery can be refreshed until terminal, but does not resume a failed slice. */
        void recovered(ArchiveBatchStatus status) throws BusinessException {
            if(request==null || result!=null || (recovered!=null && recovered.terminal()))
                throw new BusinessException("组织批改恢复状态无效。");
            recovered=QdPartnerBatchRecovery.verify(request,status);
        }
    }

    static List<Completed> execute(QdPartnerOrgBatchSlices.Prepared prepared,Operation operation) throws BusinessException {
        if(prepared==null || operation==null || prepared.slices().isEmpty())throw new BusinessException("组织批改缺少已核对的分批计划。");
        List<Completed> completed=new ArrayList<Completed>();Set<String> used=new HashSet<String>();
        List<QdPartnerOrgBatchSlices.Slice> slices=prepared.slices();
        for(int i=0;i<slices.size();i++) {
            Attempt attempt=new Attempt(slices.get(i),used);
            try {
                if(Thread.currentThread().isInterrupted())throw new BusinessException("组织批改已中断。");
                operation.execute(slices.get(i),i+1,slices.size(),attempt);
                if(attempt.result==null || attempt.summary==null)throw new BusinessException("组织批改没有已确认的业务回执。");
                completed.add(new Completed(attempt));
            } catch(BusinessException failure) { throw incomplete(completed,attempt,i+1,slices.size(),failure); }
            catch(RuntimeException failure) { throw incomplete(completed,attempt,i+1,slices.size(),failure); }
        }
        return Collections.unmodifiableList(completed);
    }
    private static BusinessException incomplete(List<Completed> completed,Attempt attempt,int number,int total,Throwable cause) {
        StringBuilder text=new StringBuilder("组织批改分批中断。已确认 ").append(completed.size()+(attempt.result==null?0:1))
            .append(" 批；请按请求号核对每批的档案及页签结果。");
        if(!completed.isEmpty() || attempt.result!=null) {
            text.append(" 已确认请求号：");for(Completed item:completed)text.append(item.requestId).append(' ');
            if(attempt.result!=null)text.append(attempt.request.getRequestId());
        }
        if(attempt.result!=null)text.append("第 ").append(number).append('/').append(total).append(" 批业务已确认，但后续处理失败。");
        else if(attempt.recovered!=null)text.append("第 ").append(number).append('/').append(total).append(" 批：").append(attempt.recovered.describe());
        else if(attempt.request!=null)text.append("第 ").append(number).append('/').append(total).append(" 批结果尚未确认，请核对请求号 ")
            .append(attempt.request.getRequestId()).append("，不要直接重试。");
        else text.append("第 ").append(number).append('/').append(total).append(" 批未派发。");
        if(number<total)text.append("后续 ").append(total-number).append(" 批未发起。");
        if(cause!=null && cause.getMessage()!=null)text.append(" 原因：").append(cause.getMessage());
        return new BusinessException(text.toString(),cause);
    }

    private static void verifyResponse(OperationContext request,OperationResponse response,ArchiveOrgBatchUpdateResult result,
            QdPartnerBatchRecovery.Summary status) throws BusinessException {
        if(status==null || !status.terminal() || status.organization==null || !status.organization.isResultAvailable()
                || result.getPageCount()!=request.getPartnerBatchDraft().getSelectedPages().length
                || result.getPageCount()!=status.organization.getPages().size()
                || result.getUnits().size()!=status.units.size() || response.getObjects().size()!=request.getObjects().size())
            throw new BusinessException("组织批改持久结果与业务回执不一致。");
        Map<String,ArchiveBatchStatus.Unit> units=new HashMap<String,ArchiveBatchStatus.Unit>();
        for(ArchiveBatchStatus.Unit unit:status.units)units.put(unit.getClientId(),unit);
        for(ArchiveBatchResult.Unit item:result.getUnits()) {
            ArchiveBatchStatus.Unit unit=item==null?null:units.remove(item.getClientId());
            if(unit==null || !unit.getObjectPk().equals(item.getObjectPk()) || !unit.getState().equals(item.getState()))
                throw new BusinessException("组织批改档案结果与持久状态不一致。");
        }
        if(!units.isEmpty())throw new BusinessException("组织批改回执遗漏档案结果。");
        Map<String,BusinessObjectRef> refs=new HashMap<String,BusinessObjectRef>();
        for(BusinessObjectRef ref:request.getObjects())refs.put(ref.getClientObjectId(),ref);
        for(BusinessObjectRef ref:response.getObjects()) {
            BusinessObjectRef prior=ref==null?null:refs.remove(ref.getClientObjectId());
            if(prior==null || !prior.getObjectPk().equals(ref.getObjectPk()) || !prior.getObjectType().equals(ref.getObjectType())
                    || !prior.getBillCode().equals(ref.getBillCode()))throw new BusinessException("组织批改回执包含不同档案。");
        }
        if(!refs.isEmpty())throw new BusinessException("组织批改回执缺少已授权档案。");
        if(result.getPageCount()==0) {
            if(result.getNativeResult()!=null)throw new BusinessException("无匹配页签不能包含原生回执。");
            return;
        }
        ErrLogReturnValue nativeResult=result.getNativeResult();
        if(nativeResult==null || nativeResult.getErrLogResult()==null || nativeResult.getTotalNum()!=result.getPageCount()
                || nativeResult.getErrLogResult().getErrorMessagegNum()<0)
            throw new BusinessException("组织批改原生回执不完整。");
        Map<String,ArchiveBatchStatus.Page> successes=new HashMap<String,ArchiveBatchStatus.Page>();boolean failed=false;
        for(ArchiveBatchStatus.Page page:status.organization.getPages()) {
            if("SUCCESS".equals(page.getState()))successes.put(page.getPagePk(),page);
            if("FAILED".equals(page.getState()))failed=true;
        }
        if(nativeResult.getErrLogResult().getErrorMessagegNum()>0 && !failed)
            throw new BusinessException("组织批改原生错误无法对应失败页签。");
        Object returned=nativeResult.getReturnValue();
        if(returned!=null && !(returned instanceof SuperVO[]))throw new BusinessException("组织批改原生返回类型不正确。");
        Map<String,ArchiveBatchStatus.Unit> byClient=new HashMap<String,ArchiveBatchStatus.Unit>();
        for(ArchiveBatchStatus.Unit unit:status.units)byClient.put(unit.getClientId(),unit);
        String domain=request.getObjects().get(0).getObjectType();
        String rootKey="BD_CUSTOMER".equals(domain)?"pk_customer":"BD_SUPPLIER".equals(domain)?"pk_supplier":"pk_material";
        if(returned!=null)for(SuperVO row:(SuperVO[])returned) {
            ArchiveBatchStatus.Page page=row==null?null:successes.remove(row.getPrimaryKey());
            ArchiveBatchStatus.Unit unit=page==null?null:byClient.get(page.getClientId());
            if(unit==null || !unit.getObjectPk().equals(row.getAttributeValue(rootKey))
                    || !page.getTargetOrg().equals(row.getAttributeValue("pk_org"))
                    || !request.getGroupId().equals(row.getAttributeValue("pk_group")))
                throw new BusinessException("组织批改原生返回包含未授权或重复页签。");
        }
        if(!successes.isEmpty())throw new BusinessException("组织批改原生回执遗漏成功页签。");
    }
}
