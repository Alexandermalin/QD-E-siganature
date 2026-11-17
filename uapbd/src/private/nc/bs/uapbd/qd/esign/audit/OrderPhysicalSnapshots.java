package nc.bs.uapbd.qd.esign.audit;

import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Capture the known order aggregates for the native typed Save/Delete adapters. */
public enum OrderPhysicalSnapshots {
    SALE("so_saleorder", "csaleorderid", "csaleorderbid"),
    PURCHASE("po_order", "pk_order", "pk_order_b");

    private final String table;
    private final String rootKey;
    private final String detailKey;

    OrderPhysicalSnapshots(String table, String rootKey, String detailKey) {
        this.table = table;
        this.rootKey = rootKey;
        this.detailKey = detailKey;
    }

    public static OrderPhysicalSnapshots forAdapter(String adapter, String operation) {
        if (!"INSERT".equals(operation) && !"UPDATE".equals(operation) && !"DELETE".equals(operation)) {
            return null;
        }
        if ("nc.itf.so.qd.esign.ISaleOrderOperationAdapter".equals(adapter)
                || "nc.itf.so.qd.esign.ISaleOrderPfSaveAdapter".equals(adapter)) {
            return SALE;
        }
        if ("nc.itf.pu.qd.esign.IPurchaseOrderOperationAdapter".equals(adapter)) {
            return PURCHASE;
        }
        return null;
    }

    public void before(AuditCaptureScope scope, OperationContext context) throws BusinessException {
        if ("INSERT".equals(context.getOperationType())) {
            return;
        }
        PhysicalSnapshotReader reader = new PhysicalSnapshotReader();
        for (BusinessObjectRef ref : context.getObjects()) {
            scope.before(ref.getClientObjectId(), aggregate(reader, context, ref, true));
        }
    }

    public void after(AuditCaptureScope scope, OperationContext context, List<BusinessObjectRef> result)
            throws BusinessException {
        PhysicalSnapshotReader reader = new PhysicalSnapshotReader();
        for (BusinessObjectRef ref : result) {
            if ("DELETE".equals(context.getOperationType())) {
                if (!reader.readByPk(table, rootKey, ref.getObjectPk()).isEmpty()) {
                    throw new BusinessException("Native order deletion left an active authorized order");
                }
            } else {
                scope.after(ref.getClientObjectId(), aggregate(reader, context, ref, false));
            }
        }
    }

    private List<SnapshotRow> aggregate(PhysicalSnapshotReader reader, OperationContext context,
            BusinessObjectRef ref, boolean lock) throws BusinessException {
        String pk = ref.getObjectPk();
        List<SnapshotRow> rows = lock ? reader.readLocked(table, rootKey, null, rootKey, pk, true)
                : reader.readByPk(table, rootKey, pk);
        if (rows.size() != 1 || !context.getGroupId().equals(rows.get(0).getValues().get("pk_group"))
                || !context.getOrgId().equals(rows.get(0).getValues().get("pk_org"))) {
            throw new BusinessException("Persisted order is outside the authorized group or organization");
        }
        if (!lock && ref.getBillCode() != null && !ref.getBillCode().equals(rows.get(0).getValues().get("vbillcode"))) {
            throw new BusinessException("Persisted order code does not match the native save result");
        }
        rows.addAll(details(reader, table + "_b", detailKey, pk, lock));
        if (this == PURCHASE) {
            rows.addAll(details(reader, "po_order_payment", "pk_payment", pk, lock));
        }
        return rows;
    }

    private List<SnapshotRow> details(PhysicalSnapshotReader reader, String childTable,
            String childKey, String pk, boolean lock) throws BusinessException {
        return lock ? reader.readLocked(childTable, childKey, rootKey, rootKey, pk, true)
                : reader.read(childTable, childKey, rootKey, rootKey, pk, true);
    }
}
