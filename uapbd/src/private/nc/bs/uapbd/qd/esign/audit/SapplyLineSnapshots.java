package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SapplyLineScope;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Capture the entire bill plus separate evidence for each authorized outbound-request row. */
public final class SapplyLineSnapshots {
    private SapplyLineSnapshots() { }

    public static void before(AuditCaptureScope scope,OperationContext context) throws BusinessException {
        before(scope,context,new InventoryPhysicalSnapshots());
    }
    static void before(AuditCaptureScope scope,OperationContext context,InventoryPhysicalSnapshots snapshots) throws BusinessException {
        SapplyLineScope.Selection selection=SapplyLineScope.require(context);
        OperationContext root=rootContext(context,selection.getRoot());
        snapshots.before(scope,root);
        captureLines(scope,selection,scope.getBefore(selection.getRoot().getClientObjectId()),true);
    }

    public static void after(AuditCaptureScope scope,OperationContext context,List<BusinessObjectRef> results) throws BusinessException {
        after(scope,context,results,new InventoryPhysicalSnapshots());
    }
    static void after(AuditCaptureScope scope,OperationContext context,List<BusinessObjectRef> results,
            InventoryPhysicalSnapshots snapshots) throws BusinessException {
        SapplyLineScope.Selection expected=SapplyLineScope.require(context), actual=SapplyLineScope.require(results);
        if (!same(expected.getRoot(),actual.getRoot()) || expected.getLines().size()!=actual.getLines().size())
            throw new BusinessException("出库申请行操作返回的单据或行数量与授权不一致。");
        Map<String,BusinessObjectRef> returned=new HashMap<String,BusinessObjectRef>();
        for(BusinessObjectRef line:actual.getLines())returned.put(line.getClientObjectId(),line);
        for(BusinessObjectRef line:expected.getLines())if(!same(line,returned.get(line.getClientObjectId())))
            throw new BusinessException("出库申请行操作返回的行与授权不一致。");
        OperationContext root=rootContext(context,expected.getRoot());
        snapshots.after(scope,root,Collections.singletonList(actual.getRoot()));
        SapplyLineEffects.requireSnapshotChanges(scope.getBefore(expected.getRoot().getClientObjectId()),
                scope.getAfter(expected.getRoot().getClientObjectId()),expected.lineKeys(),"LineClose".equals(context.getButtonCode()));
        captureLines(scope,expected,scope.getAfter(expected.getRoot().getClientObjectId()),false);
    }

    private static void captureLines(AuditCaptureScope scope,SapplyLineScope.Selection selected,
            List<SnapshotRow> bill,boolean before) throws BusinessException {
        Map<String,SnapshotRow> rows=new HashMap<String,SnapshotRow>(),extensions=new HashMap<String,SnapshotRow>();
        for(SnapshotRow row:bill) if("ic_sapply_b".equals(row.getEntity())) {
            if(rows.put(row.getRowId(),row)!=null)throw new BusinessException("出库申请审计表体主键重复。");
        } else if("ic_sapply_e".equals(row.getEntity()) && extensions.put(row.getRowId(),row)!=null)
            throw new BusinessException("出库申请审计扩展行主键重复。");
        for(BusinessObjectRef line:selected.getLines()) {
            if(!scope.isEnabledFor(line.getClientObjectId()))continue;
            SnapshotRow row=rows.get(line.getObjectPk());
            if(row==null || !selected.getRoot().getObjectPk().equals(row.getParentRowId()))
                throw new BusinessException("出库申请授权行不属于已持久化单据。");
            List<SnapshotRow> evidence=new ArrayList<SnapshotRow>();evidence.add(row);
            SnapshotRow extension=extensions.get(line.getObjectPk());if(extension!=null)evidence.add(extension);
            if(before)scope.before(line.getClientObjectId(),evidence);
            else scope.after(line.getClientObjectId(),evidence);
        }
    }

    private static boolean same(BusinessObjectRef left,BusinessObjectRef right) {
        return right!=null && left.getClientObjectId().equals(right.getClientObjectId())
                && left.getObjectType().equals(right.getObjectType()) && left.getObjectPk().equals(right.getObjectPk())
                && (left.getParentClientObjectId()==null ? right.getParentClientObjectId()==null
                    : left.getParentClientObjectId().equals(right.getParentClientObjectId()));
    }

    private static OperationContext rootContext(OperationContext context,BusinessObjectRef root) {
        return new OperationContext(context.getRequestId(),context.getBatchId(),context.getGroupId(),context.getOrgId(),
                context.getNodeCode(),context.getNodeName(),context.getButtonCode(),context.getButtonName(),
                context.getOperationType(),context.getReason(),new ArrayList<BusinessObjectRef>(Collections.singletonList(root)),
                context.getPolicyProtocol());
    }
}
