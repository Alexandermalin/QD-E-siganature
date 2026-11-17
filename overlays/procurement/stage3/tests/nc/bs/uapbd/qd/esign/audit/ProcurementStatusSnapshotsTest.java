package nc.bs.uapbd.qd.esign.audit;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

public final class ProcurementStatusSnapshotsTest {
    private static final String ADAPTER = "nc.itf.pu.qd.esign.IProcurementStatusOperationAdapter";

    public static void main(String[] args) throws Exception {
        orderLineCloseAuditsPhysicalHeaderAndRows();
        invoiceFreezeRecordsOriginalBusinessReason();
        invoiceLineCloseCannotUseThisAdapter();
    }

    private static void orderLineCloseAuditsPhysicalHeaderAndRows() throws Exception {
        BusinessObjectRef order = ref("PU_PURCHASE_ORDER");
        OperationContext context = context("40040400", "LineClose", "LINE_CLOSE", order);
        Reader reader = new Reader("po_order", "po_order_b", "po_order_payment");
        ProcurementSaveDeleteSnapshots plan = plan(context, reader);
        check(plan != null, "line close must have a physical snapshot plan");
        try (AuditCaptureScope scope = new AuditCaptureScope(context, true)) {
            plan.before(scope);
            reader.changed = true;
            plan.after(scope, Collections.singletonList(order));
            check(scope.getBefore("obj").size() == 3 && scope.getAfter("obj").size() == 3, "entire order aggregate");
            check("N".equals(scope.getBefore("obj").get(1).getValues().get("browclose")), "old row status");
            check("Y".equals(scope.getAfter("obj").get(1).getValues().get("browclose")), "actual row status");
            check(plan.requiresBefore("obj") && plan.requiresAfter("obj"), "both sides required");
            check("LINE_CLOSE".equals(plan.auditRequestForObject(Collections.<String, Object>emptyMap(), "obj")
                    .get("operation_type")), "preserve status audit operation");
        }
    }

    private static void invoiceFreezeRecordsOriginalBusinessReason() throws Exception {
        BusinessObjectRef invoice = ref("PU_PURCHASE_INVOICE");
        OperationContext context = context("40041000", "Freeze", "FREEZE", invoice);
        Reader reader = new Reader("po_invoice", "po_invoice_b", "po_invoice_settle");
        ProcurementSaveDeleteSnapshots plan = plan(context, reader);
        try (AuditCaptureScope scope = new AuditCaptureScope(context, true)) {
            plan.before(scope);
            reader.changed = true;
            plan.after(scope, Collections.singletonList(invoice));
            check("业务冻结原因".equals(scope.getAfter("obj").get(0).getValues().get("vfrozenreason")),
                    "persisted native freeze reason distinct from signing reason");
        }
    }

    private static void invoiceLineCloseCannotUseThisAdapter() throws Exception {
        try {
            plan(context("40041000", "LineClose", "LINE_CLOSE", ref("PU_PURCHASE_INVOICE")), new Reader());
            throw new AssertionError("invoice line close is outside Stage 3");
        } catch (BusinessException expected) { }
    }

    private static ProcurementSaveDeleteSnapshots plan(OperationContext context, Reader reader) throws BusinessException {
        return ProcurementSaveDeleteSnapshots.create(ADAPTER, context, Collections.<String, String>emptyMap(), reader);
    }

    private static BusinessObjectRef ref(String type) {
        return new BusinessObjectRef("obj", type, "PK", "BC", null);
    }

    private static OperationContext context(String node, String button, String operation, BusinessObjectRef ref) {
        return new OperationContext("req", "batch", "G", "O", node, node, button, button, operation,
                "签章事由", Collections.singletonList(ref));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Reader implements ProcurementSaveDeleteSnapshots.Reader {
        private final List<String> tables;
        private boolean changed;

        Reader(String... names) { tables = Arrays.asList(names); }

        @Override public List<SnapshotRow> read(String table, String pk, String parent, String key, String value,
                boolean lock) {
            check(tables.contains(table) && "PK".equals(value), "known physical aggregate");
            check(lock != changed, "pre-write rows are locked");
            Map<String, String> values = new HashMap<String, String>();
            values.put("pk_group", "G");
            values.put("pk_org", "O");
            values.put("vbillcode", "BC");
            values.put("browclose", changed ? "Y" : "N");
            values.put("vfrozenreason", changed ? "业务冻结原因" : null);
            Map<String, String> empty = Collections.emptyMap();
            return Collections.singletonList(new SnapshotRow(table, value, parent == null ? null : value,
                    values, empty, empty, true));
        }
    }
}
