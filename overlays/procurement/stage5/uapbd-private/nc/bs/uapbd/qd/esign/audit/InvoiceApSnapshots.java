package nc.bs.uapbd.qd.esign.audit;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

final class InvoiceApSnapshots {
    private static final String HEADER = "ap_payablebill";
    private static final String ITEM = "ap_payableitem";
    private static final String HEADER_KEY = "pk_payablebill";
    private static final String ITEM_KEY = "pk_payableitem";

    private InvoiceApSnapshots() { }

    static boolean supportsTable(String table) {
        return HEADER.equals(table) || ITEM.equals(table);
    }

    static void append(ProcurementSaveDeleteSnapshots.Reader reader, List<SnapshotRow> rows, String invoicePk,
            String group, boolean lock) throws BusinessException {
        Set<String> payableKeys = new LinkedHashSet<String>();
        for (SnapshotRow item : reader.read(ITEM, ITEM_KEY, HEADER_KEY, "top_billid", invoicePk, lock)) {
            String pk = item.getParentRowId();
            if (!present(pk) || !present(item.getRowId()) || !invoicePk.equals(item.getValues().get("top_billid"))
                    || !group.equals(item.getValues().get("pk_group"))) throw invalidLink();
            payableKeys.add(pk);
        }
        for (String pk : payableKeys) {
            List<SnapshotRow> head = reader.read(HEADER, HEADER_KEY, null, HEADER_KEY, pk, lock);
            if (head.size() != 1 || !pk.equals(head.get(0).getRowId())) throw invalidLink();
            SnapshotRow payable = head.get(0);
            Map<String, String> fields = payable.getValues();
            if (!group.equals(fields.get("pk_group")) || !"F1".equals(fields.get("pk_billtype"))
                    || !present(fields.get("billno")) || !present(fields.get("pk_org"))) throw invalidLink();
            List<SnapshotRow> details = reader.read(ITEM, ITEM_KEY, HEADER_KEY, HEADER_KEY, pk, lock);
            if (details.isEmpty()) throw invalidLink();
            for (SnapshotRow item : details) {
                if (!pk.equals(item.getParentRowId()) || !present(item.getRowId())
                        || !invoicePk.equals(item.getValues().get("top_billid"))
                        || !group.equals(item.getValues().get("pk_group"))) throw invalidLink();
            }
            // The audit object remains the purchase invoice. The snapshot preserves the real F1 keys and bill number.
            rows.add(payable);
            rows.addAll(details);
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isEmpty() && value.equals(value.trim()) && !"~".equals(value);
    }

    private static BusinessException invalidLink() {
        return new BusinessException("采购发票应付审计无法唯一核对应付单及其来源。");
    }
}
