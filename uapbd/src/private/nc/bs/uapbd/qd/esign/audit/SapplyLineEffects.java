package nc.bs.uapbd.qd.esign.audit;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import nc.vo.ic.m4455.entity.SapplyBillBodyVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Ensure the native row BP changes only selected rows and reaches the requested state. */
final class SapplyLineEffects {
    private SapplyLineEffects() { }

    static void requireVoChanges(SapplyBillVO current,SapplyBillVO old,Set<String> selected,boolean close)
            throws BusinessException {
        if (current==null || old==null || selected==null || selected.isEmpty())
            throw new BusinessException("出库申请行操作缺少新旧单据或授权行。");
        requireBodies(current.getBodys(),old.getBodys(),selected,close);
    }

    static void requireBodies(SapplyBillBodyVO[] current,SapplyBillBodyVO[] old,Set<String> selected,boolean close)
            throws BusinessException {
        requireChanges(index(old),index(current),selected,close);
    }

    static void requireSnapshotChanges(List<SnapshotRow> before,List<SnapshotRow> after,Set<String> selected,boolean close)
            throws BusinessException {
        requireChanges(indexRows(before),indexRows(after),selected,close);
    }

    private static void requireChanges(Map<String,Boolean> previous,Map<String,Boolean> next,
            Set<String> selected,boolean close) throws BusinessException {
        if(selected==null || selected.isEmpty() || !previous.keySet().equals(next.keySet()) || !previous.keySet().containsAll(selected))
            throw new BusinessException("出库申请行集合与授权或原单据不一致。");
        boolean changed=false;
        for(String id:previous.keySet()) {
            Boolean before=previous.get(id),after=next.get(id);
            if(selected.contains(id)) {
                if(before==null || after==null)throw new BusinessException("出库申请选中行的关闭状态不完整。");
                if(after.booleanValue()!=close)throw new BusinessException("出库申请选中行未达到目标状态。");
                changed|=before.booleanValue()!=after.booleanValue();
            } else if(before==null?after!=null:!before.equals(after))
                throw new BusinessException("出库申请未授权行的关闭状态发生变化。");
        }
        if(!changed)throw new BusinessException("出库申请所选行没有可执行的状态变化。");
    }

    private static Map<String,Boolean> indexRows(List<SnapshotRow> rows) throws BusinessException {
        if(rows==null)throw new BusinessException("出库申请行审计快照不可用。");
        Map<String,Boolean> result=new HashMap<String,Boolean>();
        for(SnapshotRow row:rows) if(row!=null && "ic_sapply_b".equals(row.getEntity())) {
            String id=row.getRowId();
            if(id==null || id.trim().isEmpty() || !row.getValues().containsKey("isclosed"))
                throw new BusinessException("出库申请行审计缺少主键或关闭状态。");
            String value=row.getValues().get("isclosed");Boolean closed;
            if(value==null)closed=null;
            else if("N".equalsIgnoreCase(value) || "0".equals(value) || "FALSE".equalsIgnoreCase(value)) closed=Boolean.FALSE;
            else if("Y".equalsIgnoreCase(value) || "1".equals(value) || "TRUE".equalsIgnoreCase(value)) closed=Boolean.TRUE;
            else throw new BusinessException("出库申请行关闭状态无法识别。");
            if(result.containsKey(id))throw new BusinessException("出库申请行审计主键重复。");
            result.put(id,closed);
        }
        return result;
    }

    private static Map<String,Boolean> index(SapplyBillBodyVO[] bodies) throws BusinessException {
        if(bodies==null)throw new BusinessException("出库申请 BP 表体不可用。");
        Map<String,Boolean> result=new HashMap<String,Boolean>();
        for(SapplyBillBodyVO body:bodies) {
            String id=body==null?null:body.getCgeneralbid();UFBoolean value=body==null?null:body.getIsclosed();
            if(id==null || id.trim().isEmpty() || !id.equals(id.trim()) || result.containsKey(id))
                throw new BusinessException("出库申请 BP 行主键或关闭状态无效。");
            result.put(id,value==null?null:Boolean.valueOf(value.booleanValue()));
        }
        return result;
    }
}
