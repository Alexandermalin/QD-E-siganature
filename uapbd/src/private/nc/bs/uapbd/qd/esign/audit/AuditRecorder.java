package nc.bs.uapbd.qd.esign.audit;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.FieldChange;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** 在已验证的原业务事务中写入审计主记录、字段差异和前后快照，任何写入失败向外传播，使业务与证据一同回滚。 */
public final class AuditRecorder {
    private final QdEsignStore store;
    public AuditRecorder(){this(new QdEsignStore());}
    AuditRecorder(QdEsignStore store){if(store==null)throw new IllegalArgumentException("Audit store is required");this.store=store;}
    /** 在当前业务事务内写入审计主记录、字段差异及快照，沿用同一请求和对象关联。 */
    public void record(Map<String,Object> request,Map<String,Object> object,List<SnapshotRow> before,List<SnapshotRow> after) throws BusinessException {
        record(request,object,before,after,Collections.<SnapshotRow>emptyList());
    }
    /** Shared batch references are written before the action and excluded from business row diffs. */
    public void record(Map<String,Object> request,Map<String,Object> object,List<SnapshotRow> before,List<SnapshotRow> after,List<SnapshotRow> references) throws BusinessException {
        requireReferences(references);
        String audit=EsignValues.id(),time=EsignValues.utc(),month=EsignValues.monthForUtc(time);
        store.insert("qd_es_audit",EsignValues.row("audit_id",audit,"request_id",request.get("request_id"),"object_id",object.get("object_id"),"result_code","SUCCESS","event_time",time,"archive_month",month));
        for(FieldChange f:SnapshotSupport.diff(before,after))store.insert("qd_es_change",common(request,object,audit,time,month,
            "change_id",EsignValues.id(),"entity_name",f.getRow().getEntity(),"row_id",f.getRow().getRowId(),"parent_row_id",f.getRow().getParentRowId(),
            "field_code",f.getFieldCode(),"field_name",f.getRow().getLabels().get(f.getFieldCode()),"data_type",f.getRow().getTypes().get(f.getFieldCode()),
            "change_type",f.getChangeType(),"before_value",f.getBeforeValue(),"after_value",f.getAfterValue()));
        // 取消分配可能只删除子行；按表和行区分被删除快照及仍保留的前值。
        SnapshotPhases.Plan phases=SnapshotPhases.forOperation(EsignValues.text(request,"operation_type"),before,after);
        snapshots(store,request,object,audit,time,month,phases.getBefore(),"BEFORE");
        snapshots(store,request,object,audit,time,month,references,"BEFORE");
        snapshots(store,request,object,audit,time,month,phases.getDeleted(),"DELETE");
        snapshots(store,request,object,audit,time,month,after,"AFTER");
    }
    static void requireReferences(List<SnapshotRow> rows) throws BusinessException {
        EsignValues.require(rows!=null,"审计引用证据缺失。");Set<String> ids=new HashSet<String>();
        for(SnapshotRow row:rows) EsignValues.require(row!=null
            && InventoryPhysicalSnapshots.BATCH_REFERENCE_ENTITY.equals(row.getEntity())
            && row.getRowId()!=null && !row.getRowId().trim().isEmpty() && ids.add(row.getRowId()),
            "审计引用证据的实体或主键不正确。");
    }
    /** 按指定阶段写入每行快照及其内容摘要，保留父子行关系和事件月份。 */
    private void snapshots(QdEsignStore store,Map<String,Object> request,Map<String,Object> object,String audit,String time,String month,List<SnapshotRow> rows,String phase) throws BusinessException {
        for(SnapshotRow row:rows){String payload=SnapshotSupport.json(row);store.insert("qd_es_snapshot",common(request,object,audit,time,month,
            "snapshot_id",EsignValues.id(),"snapshot_phase",phase,"entity_name",row.getEntity(),"row_id",row.getRowId(),"parent_row_id",row.getParentRowId(),"payload",payload,"payload_hash",SnapshotSupport.sha256(payload)));}
    }
    /** 为审计明细补齐请求、对象、集团、组织、时间及月份等公共关联字段。 */
    private Map<String,Object> common(Map<String,Object> r,Map<String,Object> o,String audit,String time,String month,Object... pairs){Map<String,Object> v=EsignValues.row(pairs);v.put("audit_id",audit);v.put("request_id",r.get("request_id"));v.put("object_id",o.get("object_id"));v.put("pk_group",r.get("pk_group"));v.put("pk_org",r.get("pk_org"));v.put("event_time",time);v.put("archive_month",month);return v;}
}
