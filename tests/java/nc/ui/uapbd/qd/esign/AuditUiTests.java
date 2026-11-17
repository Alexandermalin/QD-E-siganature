package nc.ui.uapbd.qd.esign;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import nc.vo.uapbd.qd.esign.log.AuditDetails;
import nc.vo.uapbd.qd.esign.log.LogQuery;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** 本机 NC 控件及快照读取回归；不替代现场 RPC 和权限验证。 */
public final class AuditUiTests {
    private static int assertions;
    private AuditUiTests() { }
    public static void run() throws Exception {
        assertions = 0;
        testSnapshot(); testSummary(); testTimeParts(); testFilterCopy(); testSelectionRace();
        System.out.println("PASS AuditUiTests assertions=" + assertions + " (NC preview, JSON and asynchronous selection)");
    }
    private static void testSnapshot() {
        check("删除前引用 / 批次档案 / B1".equals(QdAuditDetailsPanel.snapshotTitle("BEFORE",SnapshotRow.INVENTORY_BATCH_REFERENCE,"B1")),
            "Shared inventory batch evidence is labeled as a reference");
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("payload", "{\"code\":\"T-03\",\"name\":\"物料\\\"A\\n二行\",\"empty\":\"\",\"unknown\":null,\"unicode\":\"\\u4e2d\"}");
        Map<String, String> values = AuditUiData.snapshot(row);
        check("T-03".equals(values.get("code")), "code preserved");
        check("物料\"A\n二行".equals(values.get("name")), "escaped quote and newline preserved");
        check(values.containsKey("unknown") && values.get("unknown") == null && "".equals(values.get("empty")), "null differs from empty");
        check("中".equals(values.get("unicode")), "Unicode escape decoded");
        check("物料编码".equals(AuditUiData.fieldName("bd_material", "code", "code")), "material field localized");
        check("unrecognized".equals(AuditUiData.fieldName("other", "unrecognized", null)), "unknown field remains identifiable");
        row.put("payload_truncated", Boolean.TRUE);
        try { AuditUiData.snapshot(row); throw new AssertionError("Truncated snapshot accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        row.remove("payload_truncated"); row.put("payload", "{\"code\":\"partial");
        try { AuditUiData.snapshot(row); throw new AssertionError("Incomplete JSON accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        row.put("payload", "{\"code\":\"first\",\"code\":\"second\"}");
        try { AuditUiData.snapshot(row); throw new AssertionError("Duplicate JSON key accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void testSummary() {
        check("摘要内容".equals(QdLogQueryPanel.previewArea("摘要内容").getText()), "NC text area retains summary content");
    }
    private static void testTimeParts() {
        check("2026-11-09 13:42:17".equals(AuditDateTimeSupport.combine("2026-11-09", "13:42:17")), "NC date and time retain seconds");
        check("2026-11-09 13:42:17".equals(AuditDateTimeSupport.combine("2026-11-09 00:00:00", "13:42:17")), "NC 19-character date is normalized");
        try { AuditDateTimeSupport.combine("2026-02-30", "13:42:17"); throw new AssertionError("Invalid date accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void testFilterCopy() {
        LogQuery q = new LogQuery(); q.setFromUtc("2026-11-09 00:00:00"); q.setToUtc("2026-11-10 00:00:00");
        q.setOrganizationId("org-990"); q.setUserId("user-1");
        LogQuery copied = QdLogQueryPanel.copy(q);
        check(QdLogQueryPanel.sameFilters(q, copied), "copy retains organization and operator");
        copied.setOrganizationId("other");
        check(!QdLogQueryPanel.sameFilters(q, copied), "changed organization requires a new query before export");
    }
    private static void testSelectionRace() throws Exception {
        final CountDownLatch firstStarted = new CountDownLatch(1), releaseFirst = new CountDownLatch(1), firstReturned = new CountDownLatch(1);
        final QdAuditDetailsPanel[] holder = new QdAuditDetailsPanel[1];
        final QdAuditDetailsPanel.DetailLoader fake = new QdAuditDetailsPanel.DetailLoader() {
            public AuditDetails load(String id, String month, int offset, int pageSize) throws Exception {
                if ("first".equals(id)) { firstStarted.countDown(); if (!releaseFirst.await(10, TimeUnit.SECONDS)) { throw new Exception("timeout"); } firstReturned.countDown(); }
                Map<String, Object> event = new LinkedHashMap<String, Object>(); event.put("request_id", id);
                Map<String, Object> snap = new LinkedHashMap<String, Object>(); snap.put("snapshot_phase", "AFTER"); snap.put("entity_name", "bd_material");
                snap.put("row_id", "pk"); snap.put("payload", "{\"version\":2,\"fields\":{\"code\":{\"type\":\"VARCHAR2\",\"label\":\"现场物料编码\",\"value\":\"T-03\"}}}");
                return new AuditDetails(event, Collections.<Map<String, Object>>emptyList(), Arrays.asList(snap), 0, 1);
            }
        };
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { holder[0] = new QdAuditDetailsPanel(fake); holder[0].select("first", "202611"); } });
        check(firstStarted.await(10, TimeUnit.SECONDS), "first detail request started");
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { holder[0].select("second", "202611"); } });
        waitForEvent(holder[0], "second");
        releaseFirst.countDown(); check(firstReturned.await(10, TimeUnit.SECONDS), "older detail request returned");
        Thread.sleep(250);
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { } });
        check("second".equals(eventId(holder[0])), "late old detail does not overwrite selected event");
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { ((JTabbedPane) field(holder[0], "tabs")).setSelectedIndex(2); } });
        JTable snapshot = (JTable) field(holder[0], "fieldTable");
        check(snapshot.getColumnCount() == 5 && "表名".equals(snapshot.getColumnName(0))
            && "字段编码".equals(snapshot.getColumnName(2)) && "字段值".equals(snapshot.getColumnName(4)),
            "snapshot identifies the physical table and field code");
        check(snapshot.getRowCount() == 1 && "bd_material".equals(snapshot.getValueAt(0, 0))
            && "pk".equals(snapshot.getValueAt(0, 1)) && "code".equals(snapshot.getValueAt(0, 2))
            && "现场物料编码".equals(snapshot.getValueAt(0, 3)) && "T-03".equals(snapshot.getValueAt(0, 4)),
            "snapshot presents table, row, code, recorded label, and value");
    }
    private static void waitForEvent(QdAuditDetailsPanel panel, String expected) throws Exception {
        long until = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < until) {
            SwingUtilities.invokeAndWait(new Runnable() { public void run() { } });
            if (expected.equals(eventId(panel))) { return; }
            Thread.sleep(25);
        }
        throw new AssertionError("Selected event was not displayed");
    }
    private static String eventId(QdAuditDetailsPanel panel) {
        JTable table = (JTable) field(panel, "eventTable"); return table.getRowCount() == 0 ? "" : String.valueOf(table.getValueAt(0, 11));
    }
    private static Object field(Object target, String name) {
        try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private static void check(boolean value, String message) { assertions++; if (!value) { throw new AssertionError(message); } }
}
