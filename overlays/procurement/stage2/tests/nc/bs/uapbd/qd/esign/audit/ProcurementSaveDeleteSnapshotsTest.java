package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

public final class ProcurementSaveDeleteSnapshotsTest {
    private static final String PU_ADAPTER = "nc.itf.pu.qd.esign.IProcurementBillOperationAdapter";
    private static final String PRICE_ADAPTER = "nc.itf.purp.qd.esign.ISupplierPriceBatchOperationAdapter";

    public static void main(String[] args) throws Exception {
        invoiceUpdateCapturesHeaderItemsAndSettlementByInvoiceKey();
        priceMixedSaveUsesEachRowsActualOperation();
        editDeleteDoesNotClaimPhysicalWrite();
        mismatchedOwnerCannotEnterTheAudit();
    }

    private static void invoiceUpdateCapturesHeaderItemsAndSettlementByInvoiceKey() throws Exception {
        BusinessObjectRef invoice = ref("invoice", "PU_PURCHASE_INVOICE", "p1", "25-1");
        OperationContext context = context("40041000", "Save", "UPDATE", invoice);
        FakeReader reader = new FakeReader();
        ProcurementSaveDeleteSnapshots snapshots = ProcurementSaveDeleteSnapshots.create(
                PU_ADAPTER, context, Collections.<String, String>emptyMap(), reader);
        try (AuditCaptureScope scope = new AuditCaptureScope(context, true)) {
            snapshots.before(scope);
            snapshots.after(scope, Collections.singletonList(invoice));
            check(scope.getBefore("invoice").size() == 3 && scope.getAfter("invoice").size() == 3,
                    "invoice includes both detail tables");
        }
        check(reader.calls.equals(Arrays.asList("po_invoice/pk_invoice/pk_invoice/p1/true",
                "po_invoice_b/pk_invoice_b/pk_invoice/p1/true",
                "po_invoice_settle/pk_invoice_settle/pk_invoice/p1/true",
                "po_invoice/pk_invoice/pk_invoice/p1/false", "po_invoice_b/pk_invoice_b/pk_invoice/p1/false",
                "po_invoice_settle/pk_invoice_settle/pk_invoice/p1/false")), "physical keys and lock boundary");
    }

    private static void priceMixedSaveUsesEachRowsActualOperation() throws Exception {
        BusinessObjectRef add = ref("add", "PURP_SUPPLIER_PRICE", null, null);
        BusinessObjectRef update = ref("edit", "PURP_SUPPLIER_PRICE", "p2", null);
        BusinessObjectRef delete = ref("remove", "PURP_SUPPLIER_PRICE", "p3", null);
        OperationContext context = context("40050200", "Save", "BATCH_SAVE", add, update, delete);
        Map<String, String> kinds = new HashMap<String, String>();
        kinds.put("add", "INSERT");
        kinds.put("edit", "UPDATE");
        kinds.put("remove", "DELETE");
        FakeReader reader = new FakeReader();
        ProcurementSaveDeleteSnapshots snapshots = ProcurementSaveDeleteSnapshots.create(PRICE_ADAPTER, context, kinds, reader);
        try (AuditCaptureScope scope = new AuditCaptureScope(context, true)) {
            snapshots.before(scope);
            reader.removed = "p3";
            snapshots.after(scope, Arrays.asList(ref("add", "PURP_SUPPLIER_PRICE", "p4", null), update, delete));
            check(!snapshots.requiresBefore("add") && snapshots.requiresAfter("add"), "insert snapshot rules");
            check(snapshots.requiresBefore("edit") && snapshots.requiresAfter("edit"), "update snapshot rules");
            check(snapshots.requiresBefore("remove") && !snapshots.requiresAfter("remove"), "delete snapshot rules");
            check(!scope.hasBefore("add") && scope.hasAfter("add"), "insert captured once");
            check(scope.hasBefore("edit") && scope.hasAfter("edit"), "update captured on both sides");
            check(scope.hasBefore("remove") && !scope.hasAfter("remove"), "delete retains prior row only");
        }
    }

    private static void mismatchedOwnerCannotEnterTheAudit() throws Exception {
        BusinessObjectRef order = ref("req", "PU_STORE_REQ", "req-pk", "R1");
        OperationContext context = context("40040000", "Delete", "DELETE", order);
        FakeReader reader = new FakeReader();
        reader.org = "OTHER";
        ProcurementSaveDeleteSnapshots snapshots = ProcurementSaveDeleteSnapshots.create(
                PU_ADAPTER, context, Collections.<String, String>emptyMap(), reader);
        try (AuditCaptureScope scope = new AuditCaptureScope(context, true)) {
            try {
                snapshots.before(scope);
                throw new AssertionError("Wrong-organization request was accepted");
            } catch (BusinessException expected) {
                check(!scope.hasBefore("req"), "no audit for other org");
            }
        }
    }

    private static void editDeleteDoesNotClaimPhysicalWrite() throws Exception {
        BusinessObjectRef existing = ref("existing", "PURP_SUPPLIER_PRICE", "p2", null);
        BusinessObjectRef draft = ref("draft", "PURP_SUPPLIER_PRICE", null, null);
        OperationContext context = context("40050200", "Delete", "STAGE_DELETE", existing, draft);
        Map<String, String> operations = new HashMap<String, String>();
        operations.put("existing", "STAGE_DELETE");
        operations.put("draft", "STAGE_DELETE");
        FakeReader reader = new FakeReader();
        ProcurementSaveDeleteSnapshots snapshots = ProcurementSaveDeleteSnapshots.create(PRICE_ADAPTER, context,
                operations, reader);
        try (AuditCaptureScope scope = new AuditCaptureScope(context, true)) {
            snapshots.before(scope);
            snapshots.after(scope, Arrays.asList(existing, draft));
            check(!snapshots.recordsBusinessChange(), "editor Delete is not persisted");
            check(reader.calls.isEmpty(), "no physical audit queries");
        }
    }

    private static BusinessObjectRef ref(String id, String objectType, String pk, String billCode) {
        return new BusinessObjectRef(id, objectType, pk, billCode, null);
    }

    private static OperationContext context(String node, String button, String operation, BusinessObjectRef... objects) {
        return new OperationContext("r", "b", "G", "O", node, node, button, button, operation, null, Arrays.asList(objects));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeReader implements ProcurementSaveDeleteSnapshots.Reader {
        private final List<String> calls = new ArrayList<String>();
        private String org = "O";
        private String removed;

        @Override public List<SnapshotRow> read(String table, String pk, String parent, String key, String value,
                boolean lock) {
            calls.add(table + "/" + pk + "/" + key + "/" + value + "/" + lock);
            if (value.equals(removed)) return Collections.emptyList();
            Map<String, String> values = new HashMap<String, String>();
            values.put("pk_group", "G");
            values.put("pk_org", org);
            values.put("vbillcode", "25-1");
            Map<String, String> empty = Collections.emptyMap();
            return Collections.singletonList(new SnapshotRow(table, value, parent == null ? null : value,
                    values, empty, empty, true));
        }
    }
}
