package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPreparedScope;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchTargetScope;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** Fixed preparation header and page rows; W marks even a deliberate empty selection. */
public final class NativeOrgBatchPreparedRecords {
    private static final String HEADER="@SELECTED";
    private NativeOrgBatchPreparedRecords() { }

    static List<Map<String,Object>> targetRows(List<Map<String,Object>> rows) throws BusinessException { return select(rows,false); }
    static List<Map<String,Object>> pageRows(List<Map<String,Object>> rows) throws BusinessException { return select(rows,true); }
    private static List<Map<String,Object>> select(List<Map<String,Object>> rows,boolean pages) throws BusinessException {
        EsignValues.require(rows!=null,"组织批改准备记录缺失。");List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();
        for(Map<String,Object> row:rows) {
            String kind=row==null?null:EsignValues.text(row,"row_kind");
            boolean target="S".equals(kind) || "U".equals(kind),selected="W".equals(kind) || "V".equals(kind);
            EsignValues.require(target || selected || "T".equals(kind) || "H".equals(kind) || "P".equals(kind),
                "组织批改记录包含未知类型。");
            if(pages?selected:target)result.add(row);
        }
        return result;
    }

    public static List<Map<String,Object>> encode(Map<String,Object> request,List<Map<String,Object>> objects,
            NativeOrgBatchPage page,NativeOrgBatchPreparedScope.Evidence evidence) throws BusinessException {
        NativeOrgBatchReceiptRecords.Scope scope=NativeOrgBatchReceiptRecords.scope(request,objects,page,
            NativeOrgBatchReceiptRecords.Mode.PREPARING);
        EsignValues.require(evidence!=null && evidence.getTarget().getPage()==page,"组织批改缺少已准备的页签范围。");
        NativeOrgBatchPreparedScope.Evidence checked=NativeOrgBatchPreparedScope.restore(scope.id,scope.group,scope.org,
            evidence.getTarget(),scope.refs,evidence.getPages(),evidence.getDigest());
        String event=NativeOrgBatchReceiptRecords.text(request,"created_at"),month=NativeOrgBatchReceiptRecords.month(event);
        EsignValues.require(month.equals(NativeOrgBatchReceiptRecords.text(request,"archive_month")),"组织批改准备月份不一致。");
        Map<String,String> objectByRoot=new HashMap<String,String>();
        for(BusinessObjectRef ref:scope.refs)EsignValues.require(objectByRoot.put(ref.getObjectPk(),
            scope.objectByClient.get(ref.getClientObjectId()))==null,"组织批改准备主档重复。");
        List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();Set<String> ids=new HashSet<String>();
        rows.add(NativeOrgBatchReceiptRecords.record(scope,page,NativeOrgBatchReceiptRecords.TARGETS,"W",HEADER,null,null,null,
            Integer.valueOf(checked.getPages().size()),checked.getDigest(),event,month,ids));
        for(PartnerOrgBatchPlan.Page row:checked.getPages()) {
            String objectId=objectByRoot.get(row.getRoot());EsignValues.require(objectId!=null,"组织批改准备页签缺少授权主档。");
            rows.add(NativeOrgBatchReceiptRecords.record(scope,page,objectId,"V",row.getId(),row.getTarget(),null,null,
                null,null,event,month,ids));
        }
        return rows;
    }

    public static NativeOrgBatchPreparedScope.Evidence decode(Map<String,Object> request,List<Map<String,Object>> objects,
            NativeOrgBatchPage page,NativeOrgBatchTargetScope.Evidence target,List<Map<String,Object>> rows) throws BusinessException {
        NativeOrgBatchReceiptRecords.Scope scope=NativeOrgBatchReceiptRecords.scope(request,objects,page,
            NativeOrgBatchReceiptRecords.Mode.READABLE);
        String created=NativeOrgBatchReceiptRecords.text(request,"created_at"),month=NativeOrgBatchReceiptRecords.month(created);
        EsignValues.require(month.equals(NativeOrgBatchReceiptRecords.text(request,"archive_month")) && target!=null
            && target.getPage()==page,"组织批改准备页签月份或目标不一致。");
        String digest=null;int count=-1;List<PartnerOrgBatchPlan.Page> pages=new ArrayList<PartnerOrgBatchPlan.Page>();
        for(Map<String,Object> row:NativeOrgBatchReceiptRecords.verifiedRows(scope,page,rows)) {
            String kind=EsignValues.text(row,"row_kind"),objectId=EsignValues.text(row,"object_id"),
                item=EsignValues.text(row,"item_key"),org=EsignValues.text(row,"target_org"),hash=EsignValues.text(row,"evidence_hash");
            Object number=row.get("page_count");
            EsignValues.require(created.equals(EsignValues.text(row,"event_time")) && month.equals(EsignValues.text(row,"archive_month"))
                && row.get("result_state")==null && row.get("failure_stage")==null,"组织批改准备页签的时间或状态无效。");
            if("W".equals(kind)) {
                EsignValues.require(digest==null && NativeOrgBatchReceiptRecords.TARGETS.equals(objectId) && HEADER.equals(item)
                    && org==null && hash!=null,"组织批改准备页签表头重复或无效。");
                count=NativeOrgBatchReceiptRecords.integer(number);digest=hash;
            } else {
                BusinessObjectRef ref=scope.refByObject.get(objectId);
                EsignValues.require("V".equals(kind) && ref!=null && item!=null && org!=null && number==null && hash==null,
                    "组织批改准备页签记录无效或不属于授权档案。");
                pages.add(new PartnerOrgBatchPlan.Page(item,ref.getObjectPk(),org));
            }
        }
        EsignValues.require(digest!=null && count==pages.size(),"组织批改准备页签明细不完整。");
        return NativeOrgBatchPreparedScope.restore(scope.id,scope.group,scope.org,target,scope.refs,pages,digest);
    }
}
