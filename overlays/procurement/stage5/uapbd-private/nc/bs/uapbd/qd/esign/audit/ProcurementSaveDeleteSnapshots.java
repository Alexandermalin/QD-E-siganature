package nc.bs.uapbd.qd.esign.audit;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import nc.itf.uapbd.qd.esign.spi.BusinessOperationAdapter;
import nc.itf.uapbd.qd.esign.spi.PerObjectAuditOperations;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Physical snapshots for the authorized aggregates, taken in the existing business CMT. */
public final class ProcurementSaveDeleteSnapshots {
    public static final String BILL_ADAPTER = "nc.itf.pu.qd.esign.IProcurementBillOperationAdapter";
    public static final String PRICE_ADAPTER = "nc.itf.purp.qd.esign.ISupplierPriceBatchOperationAdapter";
    public static final String STATUS_ADAPTER = "nc.itf.pu.qd.esign.IProcurementStatusOperationAdapter";
    public static final String WORKFLOW_ADAPTER = "nc.itf.pu.qd.esign.IProcurementWorkflowOperationAdapter";
    public static final String INVOICE_RELATION_ADAPTER = "nc.itf.pu.qd.esign.IInvoiceRelationOperationAdapter";
    public static final String INVOICE_AP_ADAPTER = "nc.itf.pu.qd.esign.IInvoiceApOperationAdapter";
    private static final String INSERT = "INSERT";
    private static final String UPDATE = "UPDATE";
    private static final String DELETE = "DELETE";
    private static final String STAGE_DELETE = "STAGE_DELETE";
    private static final Table ORDER_PAY_PLAN = new Table("po_order_payplan", "pk_order_payplan");

    private final Route route;
    private final OperationContext context;
    private final Map<String, BusinessObjectRef> requested;
    private final Map<String, String> kinds;
    private final Reader reader;
    private final boolean staged;
    private final boolean includeOrderPayPlan;
    private final boolean includePayable;

    interface Reader {
        List<SnapshotRow> read(String table, String pk, String parent, String key, String value, boolean lock)
                throws BusinessException;
    }

    public static boolean supportsTable(String table) {
        if (ORDER_PAY_PLAN.name.equals(table) || InvoiceApSnapshots.supportsTable(table)) return true;
        for (Route route : Route.values()) {
            if (route.header.name.equals(table)) return true;
            for (Table detail : route.details) {
                if (detail.name.equals(table)) return true;
            }
        }
        return false;
    }

    public static ProcurementSaveDeleteSnapshots create(String adapter, OperationContext context,
            Map<String, String> perObject) throws BusinessException {
        return create(adapter, context, perObject, null);
    }

    public static ProcurementSaveDeleteSnapshots forValidatedAdapter(String adapterName, BusinessOperationAdapter adapter,
            OperationContext context, Serializable command) throws BusinessException {
        Map<String, String> perObject = adapter instanceof PerObjectAuditOperations
                ? ((PerObjectAuditOperations) adapter).auditOperations(context, command) : null;
        return create(adapterName, context, perObject);
    }

    public static List<BusinessObjectRef> describeResult(BusinessOperationAdapter adapter, OperationContext context,
            Serializable command, Serializable result) throws BusinessException {
        if (adapter instanceof PerObjectAuditOperations) {
            return ((PerObjectAuditOperations) adapter).describeResult(context, command, result);
        }
        return adapter.describeResult(context, result);
    }

    static ProcurementSaveDeleteSnapshots create(String adapter, OperationContext context,
            Map<String, String> perObject, Reader reader) throws BusinessException {
        Route route = Route.find(context.getNodeCode());
        boolean status = STATUS_ADAPTER.equals(adapter);
        boolean workflow = WORKFLOW_ADAPTER.equals(adapter);
        boolean payable = INVOICE_AP_ADAPTER.equals(adapter);
        boolean relation = INVOICE_RELATION_ADAPTER.equals(adapter);
        boolean stateChange = status || workflow || payable || relation;
        if (stateChange) {
            String button = context.getButtonCode();
            String expected = status ? statusOperation(route, button) : workflow ? workflowOperation(route, button)
                    : invoiceOperation(route, button, payable);
            if (expected == null || !expected.equals(context.getOperationType())) {
                throw new BusinessException("采购按钮、操作与审计快照范围不匹配。");
            }
        } else if (route == null || route == Route.ORDER
                || !("Save".equals(context.getButtonCode()) || "Delete".equals(context.getButtonCode()))) {
            return null;
        }
        if (!stateChange && !route.adapter.equals(adapter)) throw new BusinessException("采购保存或删除的审计适配器不匹配。");
        return new ProcurementSaveDeleteSnapshots(route, context, perObject, reader, stateChange,
                workflow && route == Route.ORDER, payable);
    }

    private ProcurementSaveDeleteSnapshots(Route route, OperationContext context, Map<String, String> perObject,
            Reader reader, boolean stateChange, boolean includeOrderPayPlan, boolean includePayable) throws BusinessException {
        this.route = route;
        this.includeOrderPayPlan = includeOrderPayPlan;
        this.includePayable = includePayable;
        this.context = context;
        this.reader = reader == null ? physicalReader() : reader;
        this.requested = new LinkedHashMap<String, BusinessObjectRef>();
        this.kinds = new LinkedHashMap<String, String>();
        Map<String, String> provided = perObject == null ? Collections.<String, String>emptyMap() : perObject;
        boolean deleting = "Delete".equals(context.getButtonCode());
        String operation = context.getOperationType();
        this.staged = route == Route.PRICE && deleting && STAGE_DELETE.equals(operation);
        boolean mixed = route == Route.PRICE && !deleting && "BATCH_SAVE".equals(operation);
        if (!stateChange && ((deleting && !staged && !DELETE.equals(operation))
                || (!deleting && !mixed && !INSERT.equals(operation) && !UPDATE.equals(operation)))) {
            throw new BusinessException("采购保存或删除的操作类型不匹配。");
        }
        Set<String> pks = new HashSet<String>();
        for (BusinessObjectRef ref : context.getObjects()) {
            String id = ref.getClientObjectId();
            String kind = mixed ? provided.get(id) : operation;
            String pk = ref.getObjectPk();
            if (!route.objectType.equals(ref.getObjectType()) || ref.getParentClientObjectId() != null
                    || blank(id) || requested.put(id, ref) != null || (!stateChange && !staged && !isKind(kind))
                    || (INSERT.equals(kind) && pk != null) || (!staged && !INSERT.equals(kind) && blank(pk))
                    || (pk != null && (blank(pk) || !pks.add(pk)))
                    || (!provided.isEmpty() && !Objects.equals(kind, provided.get(id)))) {
                throw new BusinessException("采购审计对象与授权范围不匹配。");
            }
            kinds.put(id, kind);
        }
        if (requested.isEmpty() || (mixed || !provided.isEmpty()) && !provided.keySet().equals(requested.keySet())) {
            throw new BusinessException("采购审计缺少已授权对象的操作类型。");
        }
    }

    public boolean requiresBefore(String id) {
        return !staged && !INSERT.equals(kinds.get(id));
    }

    public boolean requiresAfter(String id) {
        return !staged && !DELETE.equals(kinds.get(id));
    }

    public boolean recordsBusinessChange() {
        return !staged;
    }

    public Map<String, Object> auditRequestForObject(Map<String, Object> request, String id) {
        Map<String, Object> row = new LinkedHashMap<String, Object>(request);
        row.put("operation_type", kinds.get(id));
        return row;
    }

    public void before(AuditCaptureScope scope) throws BusinessException {
        for (BusinessObjectRef ref : requested.values()) {
            String id = ref.getClientObjectId();
            if (scope.isEnabledFor(id) && requiresBefore(id)) scope.before(id, aggregate(ref, true));
        }
    }

    public void after(AuditCaptureScope scope, List<BusinessObjectRef> results) throws BusinessException {
        Set<String> ids = new HashSet<String>();
        Set<String> pks = new HashSet<String>();
        for (BusinessObjectRef ref : results) {
            String id = ref.getClientObjectId();
            BusinessObjectRef original = requested.get(id);
            String pk = ref.getObjectPk();
            if (original == null || !ids.add(id) || (!staged && blank(pk)) || (pk != null && !pks.add(pk))
                    || !route.objectType.equals(ref.getObjectType()) || ref.getParentClientObjectId() != null
                    || ((staged || requiresBefore(id)) && !Objects.equals(pk, original.getObjectPk()))) {
                throw new BusinessException("采购审计返回对象与授权范围不匹配。");
            }
            if (staged || !scope.isEnabledFor(id)) continue;
            if (requiresAfter(id)) {
                scope.after(id, aggregate(ref, false));
            } else if (!readHeader(pk, false).isEmpty()) {
                throw new BusinessException("采购删除返回后仍能查到有效单据或价目行。");
            }
        }
        if (!ids.equals(requested.keySet())) throw new BusinessException("采购审计返回缺少授权对象。");
    }

    private List<SnapshotRow> aggregate(BusinessObjectRef ref, boolean lock) throws BusinessException {
        String pk = ref.getObjectPk();
        List<SnapshotRow> rows = new ArrayList<SnapshotRow>(readHeader(pk, lock));
        if (rows.size() != 1) throw new BusinessException("无法取得唯一的采购审计表头。");
        Map<String, String> head = rows.get(0).getValues();
        if (!context.getGroupId().equals(head.get("pk_group")) || !context.getOrgId().equals(head.get("pk_org"))) {
            throw new BusinessException("采购审计表头所属集团或组织不匹配。");
        }
        if (!lock && route != Route.PRICE && ref.getBillCode() != null && !ref.getBillCode().equals(head.get("vbillcode"))) {
            throw new BusinessException("采购保存后的单据号与持久化结果不匹配。");
        }
        for (Table detail : route.details) {
            rows.addAll(reader.read(detail.name, detail.pk, route.header.pk, route.header.pk, pk, lock));
        }
        if (includeOrderPayPlan) {
            rows.addAll(reader.read(ORDER_PAY_PLAN.name, ORDER_PAY_PLAN.pk, route.header.pk, route.header.pk, pk, lock));
        }
        if (includePayable) InvoiceApSnapshots.append(reader, rows, pk, context.getGroupId(), lock);
        return rows;
    }

    private List<SnapshotRow> readHeader(String pk, boolean lock) throws BusinessException {
        return reader.read(route.header.name, route.header.pk, null, route.header.pk, pk, lock);
    }

    private static Reader physicalReader() {
        final PhysicalSnapshotReader nativeReader = new PhysicalSnapshotReader();
        return new Reader() {
            @Override public List<SnapshotRow> read(String table, String pk, String parent, String key, String value,
                    boolean lock) throws BusinessException {
                return lock ? nativeReader.readLocked(table, pk, parent, key, value, true)
                        : nativeReader.read(table, pk, parent, key, value, true);
            }
        };
    }

    private static boolean isKind(String kind) {
        return INSERT.equals(kind) || UPDATE.equals(kind) || DELETE.equals(kind);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String statusOperation(Route route, String button) {
        if (route == Route.ORDER || route == Route.INVOICE) {
            if ("Freeze".equals(button)) return "FREEZE";
            if ("UnFreeze".equals(button)) return "UNFREEZE";
        }
        if (route == Route.STORE_REQ || route == Route.PRAYBILL || route == Route.ORDER) {
            if ("BillClose".equals(button)) return "BILL_CLOSE";
            if ("BillOpen".equals(button)) return "BILL_OPEN";
            if ("LineClose".equals(button)) return "LINE_CLOSE";
            if ("LineOpen".equals(button)) return "LINE_OPEN";
        }
        return null;
    }

    private static String workflowOperation(Route route, String button) {
        if (route == null || route == Route.PRICE) return null;
        if ("Commit".equals(button)) return "COMMIT";
        if ("UnCommit".equals(button)) return "UNCOMMIT";
        if ("Approve".equals(button)) return "APPROVE";
        if ("UnApprove".equals(button)) return "UNAPPROVE";
        return null;
    }

    private static String invoiceOperation(Route route, String button, boolean payable) {
        if (route != Route.INVOICE) return null;
        if (payable) {
            if ("SendToAP".equals(button)) return "SENDAP";
            if ("CancelToAP".equals(button)) return "CANCELSENDAP";
        } else {
            if ("RelevanceInvoice".equals(button)) return "RELATE_INVOICE";
            if ("UnRelevanceInvoice".equals(button)) return "UNRELATE_INVOICE";
        }
        return null;
    }

    private enum Route {
        STORE_REQ("40040000", "PU_STORE_REQ", BILL_ADAPTER, new Table("po_storereq", "pk_storereq"),
                new Table("po_storereq_b", "pk_storereq_b")),
        PRAYBILL("40040200", "PU_PRAYBILL", BILL_ADAPTER, new Table("po_praybill", "pk_praybill"),
                new Table("po_praybill_b", "pk_praybill_b")),
        INVOICE("40041000", "PU_PURCHASE_INVOICE", BILL_ADAPTER, new Table("po_invoice", "pk_invoice"),
                new Table("po_invoice_b", "pk_invoice_b"), new Table("po_invoice_settle", "pk_invoice_settle")),
        ORDER("40040400", "PU_PURCHASE_ORDER", STATUS_ADAPTER, new Table("po_order", "pk_order"),
                new Table("po_order_b", "pk_order_b"), new Table("po_order_payment", "pk_payment")),
        PRICE("40050200", "PURP_SUPPLIER_PRICE", PRICE_ADAPTER, new Table("purp_supplierprice", "pk_supplierprice"));

        private final String node;
        private final String objectType;
        private final String adapter;
        private final Table header;
        private final Table[] details;

        Route(String node, String objectType, String adapter, Table header, Table... details) {
            this.node = node;
            this.objectType = objectType;
            this.adapter = adapter;
            this.header = header;
            this.details = details;
        }

        static Route find(String node) {
            for (Route route : values()) {
                if (route.node.equals(node)) return route;
            }
            return null;
        }
    }

    private static final class Table {
        private final String name;
        private final String pk;

        Table(String name, String pk) {
            this.name = name;
            this.pk = pk;
        }
    }
}
