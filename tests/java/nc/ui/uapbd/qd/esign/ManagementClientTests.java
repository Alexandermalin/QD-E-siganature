package nc.ui.uapbd.qd.esign;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.vo.uapbd.qd.esign.log.LogKind;

/** 管理客户端的离线测试，验证时区转换、已加载页对象标识和用户指定文件的发布行为；不证明 NC 窗口集成通过。 */
public final class ManagementClientTests {
    /** assertions：已执行的离线断言计数。 */
    private static int assertions;
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private ManagementClientTests() { }
    /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
    public static void run() throws Exception {
        check("2026-11-06 16:00:00".equals(LogUiFormats.utcFromBeijing("2026-11-07 00:00:00")),
                "Beijing query converted to previous UTC day");
        check("2026-11-07 00:00:00".equals(LogUiFormats.beijingFromUtc("2026-11-06 16:00:00")),
                "UTC query boundary converted back to Beijing");
        invalidTime("2026-02-30 12:00:00");
        invalidTime("2026-11-07 00:00:00 trailing");
        testLoadedPageIdentities();
        testChosenFilePublishing();
        System.out.println("ManagementClientTests: " + assertions
                + " assertions passed (local format/file checks; no NC service/UI invoked).");
    }
    /** 命令行入口，执行本文件的离线检查或原有工程示例。 */
    public static void main(String[] args) throws Exception { run(); }

    /** 验证指定无效日期时间输入被拒绝，避免宽松解析悄悄修正查询时间。 */
    private static void invalidTime(String value) {
        try { LogUiFormats.utcFromBeijing(value); throw new AssertionError("Invalid time accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }

    /** 验证用户指定 CSV 的完整发布、覆盖确认及文件变化检测，不写入任意默认目标。 */
    private static void testChosenFilePublishing() throws Exception {
        Path root = Files.createTempDirectory("qd-esign-csv-ui-test-");
        Path path = root.resolve("selected.csv");
        File file = path.toFile();
        byte[] first = "first CSV\r\n".getBytes("UTF-8");
        byte[] second = "second CSV\r\n".getBytes("UTF-8");
        try {
            CsvFileSaver.save(file, first, false, 0, 0);
            check(Arrays.equals(first, Files.readAllBytes(path)), "complete selected file published");
            try { CsvFileSaver.save(file, second, false, 0, 0); throw new AssertionError("Unapproved overwrite"); }
            catch (IOException expected) { assertions++; }
            check(Arrays.equals(first, Files.readAllBytes(path)), "unapproved overwrite preserves existing file");
            try { CsvFileSaver.save(file, second, true, file.length() + 1, file.lastModified()); throw new AssertionError("Changed target accepted"); }
            catch (IOException expected) { assertions++; }
            check(Arrays.equals(first, Files.readAllBytes(path)), "changed-target rejection preserves existing file");
            CsvFileSaver.save(file, second, true, file.length(), file.lastModified());
            check(Arrays.equals(second, Files.readAllBytes(path)), "confirmed existing target replaced completely");
            String[] remaining = root.toFile().list();
            check(remaining != null && remaining.length == 1 && "selected.csv".equals(remaining[0]),
                    "temporary files removed after publishing");
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(root);
            Arrays.fill(first, (byte) 0); Arrays.fill(second, (byte) 0);
        }
    }

    /** 验证已加载页只传递合法唯一 ID，避免导出不同筛选或缺少标识的数据。 */
    private static void testLoadedPageIdentities() {
        Map<String, Object> first = new LinkedHashMap<String, Object>(); first.put("event_id", "loaded-first");
        Map<String, Object> second = new LinkedHashMap<String, Object>(); second.put("event_id", "loaded-second");
        check(Arrays.equals(new String[] {"loaded-first", "loaded-second"}, LogUiSelections.loadedIds(
                LogKind.SIGNATURE, Arrays.asList(first, second))), "loaded-page identities preserve observed membership/order");
        Map<String, Object> audit = new LinkedHashMap<String, Object>(); audit.put("audit_id", "audit-first"); audit.put("event_id", "unused");
        check("audit-first".equals(LogUiSelections.loadedIds(LogKind.AUDIT, Collections.singletonList(audit))[0]),
                "audit export uses audit ID rather than unrelated event ID");
        try { LogUiSelections.loadedIds(LogKind.SIGNATURE, Arrays.asList(first, first)); throw new AssertionError("Duplicate loaded IDs accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { LogUiSelections.loadedIds(LogKind.AUDIT, Collections.singletonList(first)); throw new AssertionError("Missing audit ID accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { LogUiSelections.loadedIds(LogKind.SIGNATURE, Collections.<Map<String, Object>>emptyList()); throw new AssertionError("Empty loaded page accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }

    /** 累计断言并在条件不满足时抛出 AssertionError，使测试失败可被构建发现。 */
    private static void check(boolean value, String description) {
        assertions++;
        if (!value) { throw new AssertionError(description); }
    }
}
