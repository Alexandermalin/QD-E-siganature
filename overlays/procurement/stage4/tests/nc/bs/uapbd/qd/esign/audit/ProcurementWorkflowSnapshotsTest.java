package nc.bs.uapbd.qd.esign.audit;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

public final class ProcurementWorkflowSnapshotsTest {
    private static final String ADAPTER = "nc.itf.pu.qd.esign.IProcurementWorkflowOperationAdapter";

    public static void main(String[] args) throws Exception {
        final boolean[] updated = {false};
        ProcurementSaveDeleteSnapshots.Reader reader = new ProcurementSaveDeleteSnapshots.Reader() {
            @Override public List<SnapshotRow> read(String table, String pk, String parent, String key,
                    String value, boolean lock) {
                if (lock == updated[0] || !"PK".equals(value)) throw new AssertionError("capture boundary");
                if ("po_order_payplan".equals(table) && (!"pk_order_payplan".equals(pk)
                        || !"pk_order".equals(parent) || !"pk_order".equals(key))) {
                    throw new AssertionError("payment plan snapshot must follow the order primary key");
                }
                Map<String, String> values = new HashMap<String, String>();
                values.put("pk_group", "G");
                values.put("pk_org", "O");
                values.put("vbillcode", "BC");
                values.put("forderstatus", updated[0] ? "3" : "2");
                return Collections.singletonList(new SnapshotRow(table, value, parent == null ? null : value,
                        values, Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap(), true));
            }
        };
        BusinessObjectRef order = new BusinessObjectRef("bill", "PU_PURCHASE_ORDER", "PK", "BC", null);
        OperationContext approval = context("40040400", "Approve", "APPROVE", order);
        ProcurementSaveDeleteSnapshots snapshots = ProcurementSaveDeleteSnapshots.create(ADAPTER, approval, null, reader);
        if (snapshots == null) throw new AssertionError("workflow approval must have a physical snapshot plan");
        try (AuditCaptureScope scope = new AuditCaptureScope(approval, true)) {
            snapshots.before(scope);
            updated[0] = true;
            snapshots.after(scope, Collections.singletonList(order));
            if (!snapshots.requiresBefore("bill") || !snapshots.requiresAfter("bill")
                    || !snapshots.recordsBusinessChange() || scope.getBefore("bill").size() != 4
                    || scope.getAfter("bill").size() != 4
                    || !"po_order_payplan".equals(scope.getAfter("bill").get(3).getEntity())
                    || !"3".equals(scope.getAfter("bill").get(0).getValues().get("forderstatus"))
                    || !"APPROVE".equals(snapshots.auditRequestForObject(Collections.<String, Object>emptyMap(), "bill")
                            .get("operation_type"))) {
                throw new AssertionError("native state change and audit operation must be preserved");
            }
        }
        try {
            ProcurementSaveDeleteSnapshots.create(ADAPTER, context("40040400", "Approve", "COMMIT", order), null, reader);
            throw new AssertionError("wrong operation must be rejected");
        } catch (BusinessException expected) { }
    }

    private static OperationContext context(String node, String button, String op, BusinessObjectRef object) {
        return new OperationContext("request", "batch", "G", "O", node, node, button, button, op, null,
                Collections.singletonList(object));
    }
}
