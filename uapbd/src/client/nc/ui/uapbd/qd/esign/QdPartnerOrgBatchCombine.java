package nc.ui.uapbd.qd.esign;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.bd.errorlog.ErrorLogVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchStatus;

/** Combine already confirmed physical page outcomes for NC's batch-result window. */
final class QdPartnerOrgBatchCombine {
    private QdPartnerOrgBatchCombine() { }
    interface LogWriter { ErrLogResult write(List<String> messages) throws BusinessException; }
    static ErrLogReturnValue combine(List<QdPartnerOrgBatchProgress.Completed> completed,Class<? extends SuperVO> type,
            LogWriter writer,QdPartnerBatchChunks.NativeLogReader reader) throws BusinessException {
        try { return checked(completed,type,writer,reader); }
        catch(BusinessException error) { throw mergeFailure(completed,error); }
        catch(RuntimeException error) { throw mergeFailure(completed,error); }
    }
    private static ErrLogReturnValue checked(List<QdPartnerOrgBatchProgress.Completed> completed,Class<? extends SuperVO> type,
            LogWriter writer,QdPartnerBatchChunks.NativeLogReader reader) throws BusinessException {
        if(completed==null || completed.isEmpty() || type==null || writer==null)throw new BusinessException("组织批改缺少已确认的分批结果。");
        List<SuperVO> saved=new ArrayList<SuperVO>();List<String> messages=new ArrayList<String>();
        Set<String> requests=new HashSet<String>(),allPages=new HashSet<String>();long total=0,failures=0;
        String group=null,domain=null;
        for(QdPartnerOrgBatchProgress.Completed part:completed) {
            if(part==null || part.requestId==null || !requests.add(part.requestId) || part.result==null || part.summary==null
                    || !part.requestId.equals(part.summary.requestId) || part.summary.organization==null
                    || !part.summary.organization.isResultAvailable() || part.group==null || part.domain==null)
                throw new BusinessException("组织批改分批回执不完整或重复。");
            if(group==null) { group=part.group;domain=part.domain; }
            else if(!group.equals(part.group) || !domain.equals(part.domain))throw new BusinessException("组织批改分批回执跨集团或档案类型。");
            ArchiveBatchStatus.Organization org=part.summary.organization;int count=part.result.getPageCount();
            if(count!=org.getPages().size() || count!=org.getPreparedPages().size() || !org.hasPreparedSelection())
                throw new BusinessException("组织批改分批页签数量不一致。");
            total+=count;if(total>Integer.MAX_VALUE)throw new BusinessException("组织批改页签汇总数量超出原生界面上限。");
            Map<String,ArchiveBatchStatus.Unit> roots=new HashMap<String,ArchiveBatchStatus.Unit>();
            for(ArchiveBatchStatus.Unit unit:part.summary.units)roots.put(unit.getClientId(),unit);
            Map<String,ArchiveBatchStatus.Page> success=new HashMap<String,ArchiveBatchStatus.Page>();
            List<String> rejected=new ArrayList<String>();
            for(ArchiveBatchStatus.Page page:org.getPages()) {
                ArchiveBatchStatus.Unit root=page==null?null:roots.get(page.getClientId());
                if(root==null || page.getPagePk()==null || !allPages.add(page.getPagePk()))
                    throw new BusinessException("组织批改分批页签重复或缺少档案。");
                if("SUCCESS".equals(page.getState()))success.put(page.getPagePk(),page);
                else if("FAILED".equals(page.getState())) {
                    String reason="PERMISSION".equals(page.getFailureStage())?"权限检查"
                        :"VALIDATION".equals(page.getFailureStage())?"业务校验"
                        :"BEFORE_EVENT".equals(page.getFailureStage())?"更新前检查":null;
                    if(reason==null)throw new BusinessException("组织批改失败页签缺少原生检查阶段。");
                    failures++;rejected.add("档案 "+root.getCode()+"（"+root.getObjectPk()+"），目标组织 "+page.getTargetOrg()
                        +"，页签 "+page.getPagePk()+" 未通过原生"+reason+"。");
                } else if(!"UNCHANGED".equals(page.getState()))throw new BusinessException("组织批改页签结果不明确。");
            }
            ErrLogReturnValue nativeResult=part.result.getNativeResult();
            if(count==0) {
                if(nativeResult!=null || !success.isEmpty() || !rejected.isEmpty())throw new BusinessException("无页签批次包含原生结果。");
                continue;
            }
            if(nativeResult==null || nativeResult.getErrLogResult()==null || nativeResult.getTotalNum()!=count)
                throw new BusinessException("组织批改原生分批结果不完整。");
            String rootKey="BD_CUSTOMER".equals(domain)?"pk_customer":"BD_SUPPLIER".equals(domain)?"pk_supplier"
                :"BD_MATERIAL".equals(domain)?"pk_material":null;
            if(rootKey==null)throw new BusinessException("组织批改档案类型不正确。");
            Object raw=nativeResult.getReturnValue();
            if(raw!=null && !(raw instanceof SuperVO[]))throw new BusinessException("组织批改原生返回类型不正确。");
            if(raw!=null)for(SuperVO row:(SuperVO[])raw) {
                ArchiveBatchStatus.Page page=row==null?null:success.remove(row.getPrimaryKey());
                ArchiveBatchStatus.Unit root=page==null?null:roots.get(page.getClientId());
                if(row==null || row.getClass()!=type || root==null || !root.getObjectPk().equals(row.getAttributeValue(rootKey))
                        || !page.getTargetOrg().equals(row.getAttributeValue("pk_org")) || !group.equals(row.getAttributeValue("pk_group")))
                    throw new BusinessException("组织批改原生返回包含重复、未授权或已转移的页签。");
                saved.add(row);
            }
            if(!success.isEmpty())throw new BusinessException("组织批改原生分批返回缺少成功页签。");
            ErrLogResult errors=nativeResult.getErrLogResult();int errorCount=errors.getErrorMessagegNum();
            if(errorCount<0 || (errorCount>0 && rejected.isEmpty()))throw new BusinessException("组织批改原生错误与页签结果不一致。");
            List<String> preview=QdPartnerBatchChunks.texts(errors.getErrorMsgs());
            if(preview.size()>errorCount)throw new BusinessException("组织批改原生错误明细数量不一致。");
            if(!rejected.isEmpty()) {
                messages.add("组织页签批改请求号："+part.requestId);
                messages.addAll(QdPartnerBatchChunks.completeNativeErrors(errors,preview,reader));messages.addAll(rejected);
            }
        }
        if(failures>Integer.MAX_VALUE)throw new BusinessException("组织批改失败数量超出原生界面上限。");
        @SuppressWarnings("unchecked") SuperVO[] values=(SuperVO[])Array.newInstance(type,saved.size());saved.toArray(values);
        ErrLogResult detail;
        if(failures==0) { ErrorLogVO log=new ErrorLogVO();log.setOperationtype("快速批改");detail=new ErrLogResult(log,0,new nc.vo.bd.errorlog.ErrorMsgVO[0]); }
        else {
            ErrLogResult written=writer.write(Collections.unmodifiableList(messages));
            if(written==null || written.getErrorLog()==null || written.getErrorLog().getPk_errorlog()==null
                    || !written.getErrorLog().getPk_errorlog().matches("[A-Za-z0-9_-]{1,80}") || written.getErrorMsgs()==null)
                throw new BusinessException("无法核实组织批改汇总错误日志。");
            detail=new ErrLogResult(written.getErrorLog(),(int)failures,written.getErrorMsgs());
        }
        return new ErrLogReturnValue(detail,values,(int)total);
    }
    static BusinessException mergeFailure(List<QdPartnerOrgBatchProgress.Completed> completed,Throwable cause) {
        StringBuilder text=new StringBuilder("组织批改各批业务结果已确认，但原生汇总未生成；请勿重复发起，请按请求号核对。");
        if(completed!=null)for(QdPartnerOrgBatchProgress.Completed item:completed)
            if(item!=null && item.requestId!=null)text.append(' ').append(item.requestId);
        if(cause!=null && cause.getMessage()!=null)text.append(" 原因：").append(cause.getMessage());
        return new BusinessException(text.toString(),cause);
    }
}
