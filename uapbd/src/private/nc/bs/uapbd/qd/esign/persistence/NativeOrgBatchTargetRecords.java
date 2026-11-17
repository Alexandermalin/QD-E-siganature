package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchTargetScope;
import nc.vo.pub.BusinessException;

/** Preparation-time target records survive cancellation and rollback of the later business transaction. */
public final class NativeOrgBatchTargetRecords {
    private NativeOrgBatchTargetRecords() { }

    public static List<Map<String,Object>> encode(Map<String,Object> request,List<Map<String,Object>> objects,NativeOrgBatchPage page,
            NativeOrgBatchTargetScope.Evidence evidence) throws BusinessException {
        NativeOrgBatchReceiptRecords.Scope scope=NativeOrgBatchReceiptRecords.scope(request,objects,page,
            NativeOrgBatchReceiptRecords.Mode.PREPARING);
        EsignValues.require(evidence!=null && evidence.getPage()==page,"组织批改缺少已核实的准备目标。");
        NativeOrgBatchTargetScope.Evidence checked=NativeOrgBatchTargetScope.restore(scope.id,scope.group,scope.org,page,scope.refs,
            evidence.getTargets(),evidence.getDigest());
        String event=NativeOrgBatchReceiptRecords.text(request,"created_at"),month=NativeOrgBatchReceiptRecords.month(event);
        EsignValues.require(month.equals(NativeOrgBatchReceiptRecords.text(request,"archive_month")),"组织批改准备月份不一致。");
        List<Map<String,Object>> records=new ArrayList<Map<String,Object>>();Set<String> ids=new HashSet<String>();
        records.add(NativeOrgBatchReceiptRecords.record(scope,page,NativeOrgBatchReceiptRecords.TARGETS,"S",NativeOrgBatchReceiptRecords.HEADER,
            null,null,null,Integer.valueOf(checked.getTargets().size()),checked.getDigest(),event,month,ids));
        for(String target:checked.getTargets()) records.add(NativeOrgBatchReceiptRecords.record(scope,page,
            NativeOrgBatchReceiptRecords.TARGETS,"U",target,null,null,null,null,null,event,month,ids));
        return Collections.unmodifiableList(records);
    }

    /** The caller supplies only prepared-scope rows, from online or trusted monthly history. */
    public static NativeOrgBatchTargetScope.Evidence decode(Map<String,Object> request,List<Map<String,Object>> objects,
            NativeOrgBatchPage page,List<Map<String,Object>> rows) throws BusinessException {
        NativeOrgBatchReceiptRecords.Scope scope=NativeOrgBatchReceiptRecords.scope(request,objects,page,
            NativeOrgBatchReceiptRecords.Mode.READABLE);
        String created=NativeOrgBatchReceiptRecords.text(request,"created_at"),month=NativeOrgBatchReceiptRecords.month(created);
        EsignValues.require(month.equals(NativeOrgBatchReceiptRecords.text(request,"archive_month")),"组织批改准备月份不一致。");
        List<String> targets=new ArrayList<String>();String digest=null;int count=-1;
        for(Map<String,Object> row:NativeOrgBatchReceiptRecords.verifiedRows(scope,page,rows)) {
            String kind=NativeOrgBatchReceiptRecords.text(row,"row_kind"),objectId=NativeOrgBatchReceiptRecords.text(row,"object_id"),
                key=NativeOrgBatchReceiptRecords.text(row,"item_key"),hash=NativeOrgBatchReceiptRecords.text(row,"evidence_hash");
            Object target=row.get("target_org"),state=row.get("result_state"),stage=row.get("failure_stage"),number=row.get("page_count");
            EsignValues.require(created.equals(NativeOrgBatchReceiptRecords.text(row,"event_time"))
                && month.equals(NativeOrgBatchReceiptRecords.text(row,"archive_month"))
                && NativeOrgBatchReceiptRecords.TARGETS.equals(objectId) && target==null && state==null && stage==null,
                "组织批改准备目标记录的请求、时间或类型不一致。");
            if("S".equals(kind)) {
                EsignValues.require(digest==null && NativeOrgBatchReceiptRecords.HEADER.equals(key) && hash!=null,
                    "组织批改准备目标表头重复或无效。");
                count=NativeOrgBatchReceiptRecords.integer(number);digest=hash;
            } else {
                EsignValues.require("U".equals(kind) && key!=null && number==null && hash==null,"组织批改准备目标明细无效。");
                targets.add(key);
            }
        }
        EsignValues.require(digest!=null && count==targets.size(),"组织批改准备目标明细不完整。");
        return NativeOrgBatchTargetScope.restore(scope.id,scope.group,scope.org,page,scope.refs,targets,digest);
    }
}
