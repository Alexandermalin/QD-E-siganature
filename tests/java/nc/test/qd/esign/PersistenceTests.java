package nc.test.qd.esign;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nc.bs.dao.BaseDAO;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.persistence.MonthlyArchiveStore;
import nc.bs.uapbd.qd.esign.persistence.QdTableMetadata;
import nc.bs.uapbd.qd.esign.persistence.QdUtcClock;
import nc.bs.uapbd.qd.esign.persistence.NativeReconciliationRules;
import nc.vo.uapbd.qd.esign.log.CsvLogWriter;

/** 仅测试生产代码中的纯持久化辅助规则，不模拟 Oracle 服务或 NC 事务，因此不能作为数据库执行与 CMT 提交的验收证据。 */
public final class PersistenceTests {
    /** assertions：已执行的离线断言计数。 */
    private static int assertions;
    /** 命令行入口，执行本文件的离线检查或原有工程示例。 */
    public static void main(String[] args) throws Exception {
        run();
        System.out.println("PASS PersistenceTests assertions=" + assertions);
    }
    /** 验证 UTC 与北京月边界、历史标识白名单、对账分页规则及 CSV 转义和上限，不执行真实数据库操作。 */
    public static void run() throws Exception {
        assertions=0;
        BaseDAO repositoryDao = new BaseDAO();
        new QdEsignStore(repositoryDao);
        check(!repositoryDao.getAddTimeStamp(), "QD repository must disable NC TS injection");
        BaseDAO archiveDao = new BaseDAO();
        new MonthlyArchiveStore(archiveDao);
        check(!archiveDao.getAddTimeStamp(), "Archive writes must use the same TS-free DAO");
        // 覆盖北京时间月切换、跨年在线保留及非法日期，避免默认时区影响归档路由。
        check("202611".equals(QdUtcClock.beijingMonth(QdUtcClock.parseUtc("2026-10-31 16:00:00"))), "Beijing month rollover");
        check("202610".equals(QdUtcClock.beijingMonth(QdUtcClock.parseUtc("2026-10-31 15:59:59"))), "Before Beijing rollover");
        check("202609".equals(QdUtcClock.firstOnlineMonth(QdUtcClock.parseUtc("2026-10-31 16:00:00"))), "Three online months");
        check("202511".equals(QdUtcClock.firstOnlineMonth(QdUtcClock.parseUtc("2026-01-01 00:00:00"))), "Cross-year retention");
        try { QdUtcClock.parseUtc("2026-02-30 00:00:00"); fail("Invalid calendar date accepted"); } catch (IllegalArgumentException expected) { assertions++; }
        try { QdUtcClock.parseUtc("2026-11-01 00:00:00extra"); fail("Timestamp suffix accepted"); } catch (IllegalArgumentException expected) { assertions++; }
        // 检查固定历史表白名单和月份格式，未知表或标识符注入必须失败。
        check("qd_es_audit_h202608".equals(QdTableMetadata.historyTable("qd_es_audit", "202608")), "History identifier");
        try { QdTableMetadata.historyTable("qd_es_policy", "202608"); fail("Non-log archive accepted"); } catch (IllegalArgumentException expected) { assertions++; }
        try { QdTableMetadata.historyTable("qd_es_audit", "202613"); fail("Invalid month accepted"); } catch (IllegalArgumentException expected) { assertions++; }
        try { QdTableMetadata.columns("qd_es_audit; DELETE FROM qd_es_audit"); fail("SQL identifier injection accepted"); } catch (IllegalArgumentException expected) { assertions++; }
        // 满批继续扫描，空批或尾批才重置周期，避免因一页刚好满而漏扫。
        check(!NativeReconciliationRules.completesCycle(900,900),"Full source page does not end a scan");
        check("0001Z".equals(NativeReconciliationRules.nextPrimaryKey("0001Z",900,900)),"Continue from real source PK");
        check(NativeReconciliationRules.completesCycle(0,900),"Empty source page ends the cycle");
        check("!".equals(NativeReconciliationRules.nextPrimaryKey("0001Z",899,900)),"Tail page resets next cycle to the beginning");
        try { NativeReconciliationRules.completesCycle(901,900);fail("Oversized source page accepted"); } catch (IllegalArgumentException expected) { assertions++; }
        // 构造中文、引号、换行、空值和带空白公式前缀，直接验证生产 CSV 工具。
        List<Map<String,Object>> rows = new ArrayList<Map<String,Object>>();
        Map<String,Object> first = new LinkedHashMap<String,Object>();
        first.put("name", "中文,\"引号\"\r\n换行"); first.put("reason", "  =1+1"); rows.add(first);
        Map<String,Object> second = new LinkedHashMap<String,Object>();
        second.put("name", null); second.put("reason", "正常说明"); rows.add(second);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int written = CsvLogWriter.write(output, Arrays.asList("name","reason"), Arrays.asList("名称","事由"), rows, 2);
        byte[] bytes = output.toByteArray();
        check(written == 2, "CSV row count");
        check((bytes[0]&255)==239 && (bytes[1]&255)==187 && (bytes[2]&255)==191, "UTF-8 BOM");
        String csv = new String(bytes, "UTF-8");
        check(csv.contains("\"中文,\"\"引号\"\"\r\n换行\""), "RFC4180 Chinese quote/newline preservation");
        check(csv.contains("\"'  =1+1\""), "Leading-whitespace formula neutralization");
        check(csv.endsWith("\"\",\"正常说明\"\r\n"), "Null cells and CRLF");
        try {
            CsvLogWriter.write(new ByteArrayOutputStream(), Arrays.asList("name"), Arrays.asList("名称"), rows, 1);
            fail("Export row limit ignored");
        } catch (IOException expected) { assertions++; }
    }
    /** 累计断言并在条件不满足时抛出 AssertionError，使测试失败可被构建发现。 */
    private static void check(boolean condition, String message) { if (!condition) { fail(message); } assertions++; }
    /** 抛出明确失败，防止非法输入继续进入原生业务调用或使测试误判为成功。 */
    private static void fail(String message) { throw new AssertionError(message); }
}
