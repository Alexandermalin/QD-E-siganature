package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nc.bs.dao.BaseDAO;
import nc.bs.dao.DAOException;
import nc.jdbc.framework.SQLParameter;

/**
 * 有批量上限、可再次运行的归档 DML，必须经 CMT 服务或任务调用。历史表须已由审批后的离线脚本预建；本类不建表、不提交、不清除历史证据。
 */
public final class MonthlyArchiveStore {
    /** TABLES：归档表的固定移动顺序，先处理证据明细，最后处理请求主记录。 */
    private static final String[] TABLES = {"qd_es_signlog","qd_es_change","qd_es_snapshot",
        "qd_es_audit","qd_es_object","qd_es_security","qd_es_request"};
    /** dao：NC 原生数据访问入口，使用调用方事务。 */
    private final BaseDAO dao;
    /** store：与当前 DAO 协作的数据存取工具。 */
    private final QdEsignStore store;
    /** 让归档 SQL 与辅助存取工具使用同一 DAO，所有复制、核验及删除由调用方 CMT 统一提交。 */
    public MonthlyArchiveStore() { this(new BaseDAO()); }
    /** 让归档 SQL 与辅助存取工具使用同一 DAO，所有复制、核验及删除由调用方 CMT 统一提交。 */
    public MonthlyArchiveStore(BaseDAO dao) { this.dao = dao; store = new QdEsignStore(dao); }

    /** 锁定月份目录，核对全部目标结构，逐表复制并校验后删除同批在线记录；保留未结束请求，返回剩余量及待关闭数。 */
    public Map<String,Long> archiveMonth(String month, Date now, int batchSize) throws DAOException {
        if (now == null || batchSize < 1 || batchSize > 900) { throw new IllegalArgumentException("batchSize must be 1..900"); }
        QdTableMetadata.historyTable("qd_es_request", month);
        if (month.compareTo(QdUtcClock.firstOnlineMonth(now)) >= 0) {
            throw new IllegalArgumentException("Archive month is within the current plus previous two Beijing months");
        }
        // 用月份目录行的 NOWAIT 锁互斥同月归档；目标表未审批预建时禁止移动数据。
        Map<String,Object> directory = store.one("SELECT archive_month,status FROM qd_es_archive WHERE archive_month=? FOR UPDATE NOWAIT",
            QdEsignStore.parameters(month));
        if (directory == null) { throw new DAOException("No approved archive directory for " + month); }
        boolean receiptTable=dao.isTableExisted(NativeOrgBatchReceiptRecords.TABLE);
        if (!receiptTable && store.one("SELECT o.object_id FROM qd_es_object o WHERE o.client_object_id LIKE 'orgbulk:%'"
                + " AND EXISTS (SELECT 1 FROM qd_es_locator l WHERE l.request_id=o.request_id AND l.archive_month=?"
                + " AND l.request_closed='Y') AND ROWNUM=1",QdEsignStore.parameters(month))!=null) {
            throw new DAOException("Organization batch evidence exists but its receipt table is unavailable");
        }
        List<String> tables=new ArrayList<String>(java.util.Arrays.asList(TABLES));
        if(receiptTable) tables.add(tables.indexOf("qd_es_object"),NativeOrgBatchReceiptRecords.TABLE);
        for (String table : tables) {
            String history = QdTableMetadata.historyTable(table, month);
            if (!dao.isTableExisted(history)) { throw new DAOException("Missing precreated archive table: " + history); }
            // 移动数据前核对全部预期列，结构不一致时立即使本次 CMT 失败。
            store.list("SELECT " + QdTableMetadata.columnList(table) + " FROM " + history + " WHERE 1=0", new SQLParameter());
        }
        dao.executeUpdate("UPDATE qd_es_archive SET status='RUNNING',started_at=?,finished_at=NULL,last_error=NULL WHERE archive_month=?",
            QdEsignStore.parameters(QdUtcClock.now(), month));
        Map<String,Long> result = new LinkedHashMap<String,Long>(); long remaining = 0, blockedReceipts = 0;
        for (String table : tables) {
            List<String> ids = candidates(table, month, batchSize,receiptTable);
            long moved = move(table, month, ids); result.put(table, Long.valueOf(moved));
            Map<String,Object> count = store.one("SELECT COUNT(*) row_count FROM " + table + " s WHERE " + eligible(table,receiptTable),
                QdEsignStore.parameters(month));
            long eligibleCount=((Number)count.get("row_count")).longValue();remaining += eligibleCount;
            if(receiptTable && ("qd_es_object".equals(table) || "qd_es_request".equals(table))) {
                Map<String,Object> allClosed=store.one("SELECT COUNT(*) row_count FROM " + table + " s WHERE " + eligible(table,false),
                    QdEsignStore.parameters(month));
                long total=((Number)allClosed.get("row_count")).longValue();
                if(total<eligibleCount) throw new DAOException("Archive receipt dependency count changed unexpectedly for " + table);
                blockedReceipts+=total-eligibleCount;
            }
        }
        result.put("remaining", Long.valueOf(remaining));
        result.put("blocked_receipts", Long.valueOf(blockedReceipts));
        Map<String,Object> pending = store.one("SELECT COUNT(*) row_count FROM qd_es_locator WHERE archive_month=? AND request_closed='N'",
            QdEsignStore.parameters(month));
        // 没有可移动行仍可能存在未结束请求，此时保持 PARTIAL，不能宣称全部归档完成。
        long pendingOpen = ((Number)pending.get("row_count")).longValue(); result.put("pending_open", Long.valueOf(pendingOpen));
        String completion=completionState(remaining,pendingOpen,blockedReceipts);
        if ("ARCHIVED".equals(completion)) {
            dao.executeUpdate("UPDATE qd_es_archive SET status='ARCHIVED',finished_at=? WHERE archive_month=?",
                QdEsignStore.parameters(QdUtcClock.now(), month));
        } else if ("PARTIAL".equals(completion)) {
            dao.executeUpdate("UPDATE qd_es_archive SET status='PARTIAL',last_error=? WHERE archive_month=?",
                QdEsignStore.parameters("Open requests: " + pendingOpen + "; rows awaiting receipt archive: " + blockedReceipts, month));
        }
        return result;
    }
    /** 选择指定月份、已满足结束条件且在批量上限内的主键，未结束请求及其证据保持在线。 */
    private List<String> candidates(String table, String month, int batchSize,boolean receiptTable) throws DAOException {
        String pk = QdTableMetadata.primaryKey(table);
        List<Map<String,Object>> rows = store.list("SELECT " + pk + " FROM (SELECT " + pk + " FROM " + table
            + " s WHERE " + eligible(table,receiptTable) + " ORDER BY " + pk + ") WHERE ROWNUM<=?",
            QdEsignStore.parameters(month, Integer.valueOf(batchSize)));
        List<String> result = new ArrayList<String>();
        for (Map<String,Object> row : rows) { result.add(String.valueOf(row.get(pk))); } return result;
    }
    /** 先复制指定主键到预建历史表，再逐列核验一致性后删除在线副本；CLOB 单独比较，任何不一致使事务失败。 */
    private long move(String table, String month, List<String> ids) throws DAOException {
        if (ids.isEmpty()) { return 0; }
        String pk = QdTableMetadata.primaryKey(table); String target = QdTableMetadata.historyTable(table, month);
        String columns = QdTableMetadata.columnList(table); String sourceColumns = aliasColumns(table, "s");
        String filter = "s." + pk + " IN (" + placeholders(ids.size()) + ") AND s.archive_month=?";
        // 只补入目标中尚不存在的主键，使重复批次可再次核验而不重复插入。
        SQLParameter insert = idParameters(ids, month);
        dao.executeUpdate("INSERT INTO " + target + " (" + columns + ") SELECT " + sourceColumns + " FROM " + table
            + " s WHERE " + filter + " AND NOT EXISTS (SELECT 1 FROM " + target + " h WHERE h." + pk + "=s." + pk + ")", insert);
        // 逐列区分双空值与实际相等；CLOB 使用 DBMS_LOB.COMPARE，不能按普通文本等号比较。
        StringBuilder matches = new StringBuilder();
        for (String field : QdTableMetadata.columns(table)) {
            if (matches.length() > 0) { matches.append(" AND "); }
            matches.append("((s.").append(field).append(" IS NULL AND h.").append(field).append(" IS NULL) OR ");
            if (QdTableMetadata.isClob(field)) {
                matches.append("DBMS_LOB.COMPARE(s.").append(field).append(",h.").append(field).append(")=0)");
            } else { matches.append("s.").append(field).append("=h.").append(field).append(')'); }
        }
        Map<String,Object> mismatches = store.one("SELECT COUNT(*) row_count FROM " + table + " s WHERE " + filter
            + " AND NOT EXISTS (SELECT 1 FROM " + target + " h WHERE h." + pk + "=s." + pk + " AND " + matches + ")",
            idParameters(ids, month));
        if (((Number)mismatches.get("row_count")).longValue() != 0) {
            throw new DAOException("Archive content verification failed for " + table + "/" + month + "; no source deletion allowed");
        }
        // 同批主键全部核验一致后才删除在线副本；删除数量变化也会使容器事务回滚。
        int deleted = dao.executeUpdate("DELETE FROM " + table + " WHERE " + pk + " IN (" + placeholders(ids.size())
            + ") AND archive_month=?", idParameters(ids, month));
        if (deleted != ids.size()) { throw new DAOException("Archive source row count changed for " + table); }
        return deleted;
    }
    /** 为不同表生成允许归档的结束条件，避免仅按事件时间误移动活跃请求的证据。 */
    static String eligible(String table,boolean receiptTable) {
        if ("qd_es_security".equals(table)) { return "s.archive_month=?"; }
        String result="s.archive_month=? AND EXISTS (SELECT 1 FROM qd_es_locator l WHERE l.request_id=s.request_id"
            + " AND l.archive_month=s.archive_month AND l.request_closed='Y')";
        // Receipt rows can be in a different month. Do not let an old or new archiver delete their owners first.
        if(receiptTable && "qd_es_object".equals(table)) result+=" AND NOT EXISTS (SELECT 1 FROM qd_es_batch_page p"
            + " WHERE p.request_id=s.request_id AND p.object_id=s.object_id)";
        if(receiptTable && "qd_es_request".equals(table)) result+=" AND NOT EXISTS (SELECT 1 FROM qd_es_batch_page p"
            + " WHERE p.request_id=s.request_id)";
        return result;
    }
    static String completionState(long remaining,long pendingOpen,long blockedReceipts) {
        if(remaining<0 || pendingOpen<0 || blockedReceipts<0) throw new IllegalArgumentException("Negative archive counts");
        return remaining>0?"RUNNING":pendingOpen==0 && blockedReceipts==0?"ARCHIVED":"PARTIAL";
    }
    /** 为固定投影列添加表别名，保证在线与历史核验字段一一对应。 */
    private static String aliasColumns(String table, String alias) {
        StringBuilder b = new StringBuilder();
        for (String field : QdTableMetadata.columns(table)) { if (b.length() > 0) { b.append(','); } b.append(alias).append('.').append(field); }
        return b.toString();
    }
    /** 按已限制的数量生成参数占位符，不拼接主键值。 */
    private static String placeholders(int size) {
        StringBuilder b = new StringBuilder(); for (int i = 0; i < size; i++) { if (i > 0) { b.append(','); } b.append('?'); } return b.toString();
    }
    /** 以主键顺序和月份构造归档批次参数，确保复制、核验及删除针对相同记录。 */
    private static SQLParameter idParameters(List<String> ids, String month) {
        SQLParameter p = new SQLParameter(); for (String id : ids) { p.addParam(id); } p.addParam(month); return p;
    }
}
