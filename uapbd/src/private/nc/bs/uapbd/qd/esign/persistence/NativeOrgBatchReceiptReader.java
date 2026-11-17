package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchReceipt;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchTargetScope;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPreparedScope;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.vo.pub.BusinessException;

/** Internal read-only candidate. The caller must authorize the request, archives, page and every returned target. */
public final class NativeOrgBatchReceiptReader {
    private final QdEsignStore store;
    public NativeOrgBatchReceiptReader() { this(new QdEsignStore()); }
    NativeOrgBatchReceiptReader(QdEsignStore store) {
        if(store==null)throw new IllegalArgumentException("store");this.store=store;
    }
    public static final class Result {
        private final NativeOrgBatchPage page;private final NativeOrgBatchTargetScope.Evidence target;
        private final NativeOrgBatchReceipt.Evidence evidence;
        private final NativeOrgBatchPreparedScope.Evidence selection;
        private Result(NativeOrgBatchPage page,NativeOrgBatchTargetScope.Evidence target,NativeOrgBatchPreparedScope.Evidence selection,
                NativeOrgBatchReceipt.Evidence evidence) {
            this.page=page;this.target=target;this.selection=selection;this.evidence=evidence;
        }
        public NativeOrgBatchPage getPage() { return page; }
        public NativeOrgBatchTargetScope.Evidence getTarget() { return target; }
        public NativeOrgBatchReceipt.Evidence getEvidence() { return evidence; }
        /** Null for historical candidate requests that did not persist their page selection. */
        public NativeOrgBatchPreparedScope.Evidence getSelection() { return selection; }
    }

    /** Objects must be the complete authoritative online/history set for this finished request. */
    public Result read(Map<String,Object> request,List<Map<String,Object>> objects) throws BusinessException {
        EsignValues.require(request!=null && request.get("finished_at")!=null && objects!=null,"组织页签回执请求未结束或档案缺失。");
        Result result=readAvailable(request,objects);
        EsignValues.require(result.getEvidence()!=null,"组织页签执行结果缺失。");return result;
    }

    /** A pending, cancelled or rolled-back request can have target evidence without any business outcome. */
    public Result readAvailable(Map<String,Object> request,List<Map<String,Object>> objects) throws BusinessException {
        EsignValues.require(request!=null && objects!=null,"组织页签回执请求或档案缺失。");
        List<Map<String,Object>> rows=NativeOrgBatchRecordReader.read(store,request,false);
        NativeOrgBatchPage page=NativeOrgBatchRecordReader.page(request,rows);
        List<Map<String,Object>> targetRows=new ArrayList<Map<String,Object>>(),selectionRows=new ArrayList<Map<String,Object>>(),
            outcomeRows=new ArrayList<Map<String,Object>>();
        for(Map<String,Object> row:rows) {
            String kind=EsignValues.text(row,"row_kind");
            EsignValues.require(page.tabCode.equals(EsignValues.text(row,"tab_code")),"组织页签回执包含多个页签。");
            if("S".equals(kind) || "U".equals(kind)) targetRows.add(row);
            else if("W".equals(kind) || "V".equals(kind)) selectionRows.add(row);
            else if("T".equals(kind) || "H".equals(kind) || "P".equals(kind)) outcomeRows.add(row);
            else throw new BusinessException("组织页签回执包含未知记录类型。");
        }
        NativeOrgBatchTargetScope.Evidence target=NativeOrgBatchTargetRecords.decode(request,objects,page,targetRows);
        NativeOrgBatchPreparedScope.Evidence selection=selectionRows.isEmpty()?null:
            NativeOrgBatchPreparedRecords.decode(request,objects,page,target,selectionRows);
        if(outcomeRows.isEmpty())return new Result(page,target,selection,null);
        NativeOrgBatchReceipt.Evidence outcome=NativeOrgBatchReceiptRecords.decode(request,objects,page,outcomeRows);
        EsignValues.require(target.getTargets().equals(outcome.getTargets()),"组织页签结果目标与准备阶段不一致。");
        if(selection!=null)NativeOrgBatchPreparedScope.requireOutcome(EsignValues.text(request,"request_id"),
            EsignValues.text(request,"pk_group"),EsignValues.text(request,"pk_org"),ObjectRelations.restore(objects),selection,outcome);
        return new Result(page,target,selection,outcome);
    }
}
