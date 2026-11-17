package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

public final class InvoiceApSnapshotsTest {
    private static final String INVOICE = "INVOICE-PK";
    private static final String PAYABLE = "PAYABLE-PK";
    private static final String GROUP = "GROUP";

    public static void main(String[] args) throws Exception {
        List<SnapshotRow> rows = new ArrayList<SnapshotRow>();
        Fixture fixture = new Fixture();
        InvoiceApSnapshots.append(fixture, rows, INVOICE, GROUP, true);
        check(rows.size() == 3 && PAYABLE.equals(rows.get(0).getRowId()), "Actual payable is missing or duplicated");
        check("F1-0001".equals(rows.get(0).getValues().get("billno")), "Actual payable number is missing");
        check(PAYABLE.equals(rows.get(1).getParentRowId()), "Payable line parent changed");
        fixture.removed = true;
        rows.clear();
        InvoiceApSnapshots.append(fixture, rows, INVOICE, GROUP, true);
        check(rows.isEmpty(), "Cancelled payable remains in after snapshot");
        fixture.removed = false;
        fixture.otherSource = true;
        reject(fixture, rows);
        fixture.otherSource = false;
        fixture.group = "OTHER-GROUP";
        reject(fixture, rows);
    }

    private static void reject(Fixture fixture, List<SnapshotRow> rows) throws Exception {
        try {
            InvoiceApSnapshots.append(fixture, rows, INVOICE, GROUP, true);
            throw new AssertionError("Out-of-scope payable was included in the audit");
        } catch (BusinessException expected) {
            check(rows.isEmpty(), "Invalid payable leaked into the audit snapshot");
        }
    }

    private static final class Fixture implements ProcurementSaveDeleteSnapshots.Reader {
        boolean removed;
        boolean otherSource;
        String group = GROUP;

        @Override public List<SnapshotRow> read(String table, String pk, String parent, String key, String value,
                boolean lock) {
            check(lock, "The initial lookup did not preserve the requested lock");
            if ("top_billid".equals(key)) {
                check("ap_payableitem".equals(table) && "pk_payableitem".equals(pk)
                        && "pk_payablebill".equals(parent) && INVOICE.equals(value), "Unexpected source query");
                return removed ? Collections.<SnapshotRow>emptyList() : Arrays.asList(item("LINE-1", INVOICE),
                        item("LINE-2", INVOICE));
            }
            check("pk_payablebill".equals(key) && PAYABLE.equals(value), "Unexpected payable query");
            if ("ap_payablebill".equals(table)) {
                Map<String, String> fields = new HashMap<String, String>();
                fields.put("pk_group", group);
                fields.put("pk_org", "FINANCE-ORG");
                fields.put("pk_billtype", "F1");
                fields.put("billno", "F1-0001");
                return Collections.singletonList(row(table, PAYABLE, null, fields));
            }
            return Arrays.asList(item("LINE-1", INVOICE), item("LINE-2", otherSource ? "OTHER-INVOICE" : INVOICE));
        }

        private SnapshotRow item(String id, String source) {
            Map<String, String> fields = new HashMap<String, String>();
            fields.put("pk_group", GROUP);
            fields.put("top_billid", source);
            return row("ap_payableitem", id, PAYABLE, fields);
        }
    }

    private static SnapshotRow row(String table, String id, String parent, Map<String, String> fields) {
        return new SnapshotRow(table, id, parent, fields, Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
