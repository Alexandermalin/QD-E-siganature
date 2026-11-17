package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nc.bs.dao.BaseDAO;
import nc.bs.dao.DAOException;
import nc.jdbc.framework.SQLParameter;
import nc.vo.uapbd.qd.esign.log.LogKind;
import nc.vo.uapbd.qd.esign.log.LogQuery;

/** 按固定日志类别联合在线和历史表，每个查询分支都带集团、组织及筛选约束，权限缺失时不提供放宽范围的回退。 */
public final class QdLogRepository {
    /** dao：NC 原生数据访问入口，使用调用方事务。 */
    private final BaseDAO dao;
    /** store：与当前 DAO 协作的数据存取工具。 */
    private final QdEsignStore store;
    /** 建立使用同一 BaseDAO 的查询仓储和存取工具；无参构造使用原生 DAO，带参形式便于明确依赖。 */
    public QdLogRepository() { this(new BaseDAO()); }
    /** 建立使用同一 BaseDAO 的查询仓储和存取工具；无参构造使用原生 DAO，带参形式便于明确依赖。 */
    public QdLogRepository(BaseDAO dao) { this.dao = dao; store = new QdEsignStore(dao); }

    /** 对已授权的本页组织批量解析当前名称，不扩大日志查询范围或更改原始记录。 */
    public List<Map<String,Object>> withOrganizationNames(List<Map<String,Object>> rows,String groupId) throws DAOException {
        Set<String> ids=new LinkedHashSet<String>();
        for(Map<String,Object> row:rows) for(String key:new String[]{"pk_org","policy_owner_org","target_org"}) {
            Object value=row.get(key); if(value!=null&&!String.valueOf(value).trim().isEmpty()) ids.add(String.valueOf(value).trim());
        }
        Map<String,String> names=new LinkedHashMap<String,String>();
        List<String> all=new ArrayList<String>(ids);
        for(int start=0;start<all.size();start+=900) {
            SQLParameter p=new SQLParameter();p.addParam(groupId);StringBuilder holders=new StringBuilder();
            for(int i=start;i<Math.min(start+900,all.size());i++) { if(holders.length()>0)holders.append(',');holders.append('?');p.addParam(all.get(i)); }
            List<Map<String,Object>> organizations=store.list("SELECT pk_org,name FROM org_orgs WHERE pk_group=? AND islastversion='Y' AND NVL(dr,0)=0 AND pk_org IN ("+holders+")",p);
            for(Map<String,Object> org:organizations) {
                String id=String.valueOf(org.get("pk_org")).trim();Object raw=org.get("name");String name=raw==null?null:String.valueOf(raw).trim();
                if(names.containsKey(id) && !java.util.Objects.equals(names.get(id),name))throw new DAOException("Ambiguous business unit name: "+id);
                names.put(id,name);
            }
        }
        if (ids.contains(groupId) && !names.containsKey(groupId)) {
            Map<String,Object> group=store.one("SELECT name FROM org_group WHERE pk_group=? AND NVL(dr,0)=0",
                    QdEsignStore.parameters(groupId));
            if (group!=null && group.get("name")!=null) {
                names.put(groupId,String.valueOf(group.get("name")).trim());
            }
        }
        return addOrganizationNames(rows,names);
    }
    static List<Map<String,Object>> addOrganizationNames(List<Map<String,Object>> rows,Map<String,String> names) {
        List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();
        for(Map<String,Object> row:rows) {
            Map<String,Object> copy=new LinkedHashMap<String,Object>(row);Object id=row.get("pk_org");
            copy.put("qd_org_name",id==null?null:names.get(String.valueOf(id).trim()));
            Object owner=row.get("policy_owner_org"),target=row.get("target_org");
            copy.put("qd_owner_org_name",owner==null?"未记录":names.get(String.valueOf(owner).trim()));
            copy.put("qd_target_org_name",target==null?"未记录":names.get(String.valueOf(target).trim()));
            result.add(copy);
        }
        return result;
    }

    /** 在每个在线及历史分支应用集团、服务端组织和筛选条件，按稳定顺序返回有限分页数据。 */
    public List<Map<String,Object>> query(LogKind kind, LogQuery filter, String groupId,
            List<String> authorizedOrgs) throws DAOException {
        return query(kind,filter,groupId,authorizedOrgs,false);
    }
    /** 在每个在线及历史分支应用集团、服务端组织和筛选条件，按稳定顺序返回有限分页数据。 */
    public List<Map<String,Object>> query(LogKind kind, LogQuery filter, String groupId,
            List<String> authorizedOrgs, boolean includeGroupSecurity) throws DAOException {
        boolean global = kind == LogKind.SECURITY && includeGroupSecurity;
        validate(filter, groupId, authorizedOrgs,global);
        if (filter.getOffset() > Integer.MAX_VALUE - filter.getPageSize()) { throw new IllegalArgumentException("Page range overflow"); }
        SQLParameter p = new SQLParameter(); String union = union(kind, filter, groupId, authorizedOrgs, p,global);
        String table = table(kind); String fields = QdTableMetadata.columnList(table);
        String id = QdTableMetadata.primaryKey(table);
        // 按事件主键去重并优先在线来源，避免归档切换期间的重叠副本重复展示。
        String dedup = "SELECT " + fields + " FROM (SELECT qd_u.*,ROW_NUMBER() OVER (PARTITION BY " + id
            + " ORDER BY qd_source) qd_duplicate FROM (" + union + ") qd_u) WHERE qd_duplicate=1";
        String sql = "SELECT " + fields + " FROM (SELECT qd_o.*,ROWNUM qd_page FROM (" + dedup
            + " ORDER BY event_time," + id + ") qd_o WHERE ROWNUM<=?) WHERE qd_page>?";
        p.addParam(filter.getOffset() + filter.getPageSize()); p.addParam(filter.getOffset());
        return store.list(sql, p);
    }
    /** 计算符合权限和筛选条件的去重记录总数，不将在线与历史重叠副本重复计数。 */
    public long count(LogKind kind, LogQuery filter, String groupId, List<String> authorizedOrgs) throws DAOException {
        return count(kind,filter,groupId,authorizedOrgs,false);
    }
    /** 计算符合权限和筛选条件的去重记录总数，不将在线与历史重叠副本重复计数。 */
    public long count(LogKind kind, LogQuery filter, String groupId, List<String> authorizedOrgs, boolean includeGroupSecurity) throws DAOException {
        boolean global = kind == LogKind.SECURITY && includeGroupSecurity;
        validate(filter, groupId, authorizedOrgs,global); SQLParameter p = new SQLParameter();
        String sql = "SELECT COUNT(DISTINCT " + QdTableMetadata.primaryKey(table(kind)) + ") AS row_count FROM ("
            + union(kind, filter, groupId, authorizedOrgs, p,global) + ")";
        Map<String,Object> row = store.one(sql, p); return ((Number)row.get("row_count")).longValue();
    }

    /**
     * 用一次 SELECT 取得受权限和筛选约束的导出集合，maxRows 为 1 至 100000；多取一条检测超限，不以多次分页拼接变化中的数据。
     */
    public List<Map<String,Object>> queryForExport(LogKind kind,LogQuery filter,String groupId,
            List<String> authorizedOrgs,int maxRows) throws DAOException {
        return queryForExport(kind,filter,groupId,authorizedOrgs,maxRows,false);
    }
    /**
     * 用一次 SELECT 取得受权限和筛选约束的导出集合，maxRows 为 1 至 100000；多取一条检测超限，不以多次分页拼接变化中的数据。
     */
    public List<Map<String,Object>> queryForExport(LogKind kind,LogQuery filter,String groupId,
            List<String> authorizedOrgs,int maxRows,boolean includeGroupSecurity) throws DAOException {
        if (maxRows < 1 || maxRows > 100000) { throw new IllegalArgumentException("Export maximum must be 1..100000"); }
        boolean global = kind == LogKind.SECURITY && includeGroupSecurity;
        validate(filter,groupId,authorizedOrgs,global); SQLParameter p = new SQLParameter();
        String union = union(kind,filter,groupId,authorizedOrgs,p,global);
        String table = table(kind),fields=QdTableMetadata.columnList(table),id=QdTableMetadata.primaryKey(table);
        String sql = "SELECT " + fields + " FROM (SELECT " + fields + " FROM (SELECT qd_u.*,"
            + "ROW_NUMBER() OVER (PARTITION BY " + id + " ORDER BY qd_source) qd_duplicate FROM ("
            + union + ") qd_u) WHERE qd_duplicate=1 ORDER BY event_time," + id + ") WHERE ROWNUM<=?";
        // 多取一行用于显式发现超限，不能把截断的前十万行误报为完整导出。
        p.addParam(maxRows + 1); List<Map<String,Object>> rows=store.list(sql,p);
        if (rows.size() > maxRows) { throw new DAOException("Export row limit exceeded; narrow the filter instead of exporting a partial result"); }
        return rows;
    }

    /** 精确查询已加载页 ID 集合，保留所有原筛选及权限条件；缺失、重复或越权 ID 均不静默跳过。 */
    public List<Map<String,Object>> queryLoaded(LogKind kind,LogQuery filter,String groupId,
            List<String> authorizedOrgs,String[] eventIds,boolean includeGroupSecurity) throws DAOException {
        boolean global=kind==LogKind.SECURITY&&includeGroupSecurity;validate(filter,groupId,authorizedOrgs,global);
        if(eventIds==null||eventIds.length<1||eventIds.length>1000) { throw new IllegalArgumentException("Loaded export requires 1..1000 IDs"); }
        // 已加载页必须是 1 至 1000 个唯一合法 ID，之后再次带原筛选和授权查询。
        Set<String> ids=new LinkedHashSet<String>();
        for(String value:eventIds) { if(value==null||!value.matches("[a-fA-F0-9]{32}")||!ids.add(value)) { throw new IllegalArgumentException("Invalid or duplicate loaded log ID"); } }
        SQLParameter p=new SQLParameter();String union=union(kind,filter,groupId,authorizedOrgs,p,global);
        String table=table(kind),fields=QdTableMetadata.columnList(table),id=QdTableMetadata.primaryKey(table);
        StringBuilder holders=new StringBuilder();for(String value:ids){if(holders.length()>0){holders.append(',');}holders.append('?');p.addParam(value);}
        String sql="SELECT "+fields+" FROM (SELECT qd_u.*,ROW_NUMBER() OVER(PARTITION BY "+id
            +" ORDER BY qd_source) qd_duplicate FROM ("+union+") qd_u) WHERE qd_duplicate=1 AND "+id
            +" IN ("+holders+") ORDER BY event_time,"+id;
        List<Map<String,Object>> rows=store.list(sql,p);
        if(rows.size()!=ids.size()) { throw new DAOException("Loaded export records are missing or no longer authorized by the current filter"); }
        return rows;
    }
    /** 为在线表及经核实存在的历史表构造联合查询，每个分支都带相同集团、组织及筛选约束。 */
    private String union(LogKind kind, LogQuery q, String group, List<String> orgs, SQLParameter p,boolean global) throws DAOException {
        String base = table(kind); List<String> tables = new ArrayList<String>(); tables.add(base);
        Date from = QdUtcClock.parseUtc(q.getFromUtc()); Date to = QdUtcClock.parseUtc(q.getToUtc());
        String firstMonth = QdUtcClock.beijingMonth(from);
        String lastMonth = QdUtcClock.beijingMonth(new Date(to.getTime() - 1));
        List<Map<String,Object>> directories = store.list("SELECT archive_month FROM qd_es_archive WHERE archive_month>=? AND archive_month<=? ORDER BY archive_month",
            QdEsignStore.parameters(firstMonth, lastMonth));
        for (Map<String,Object> directory : directories) {
            String history = QdTableMetadata.historyTable(base, String.valueOf(directory.get("archive_month")));
            if (!dao.isTableExisted(history)) { throw new DAOException("Missing approved QD history table: " + history); }
            tables.add(history);
        }
        StringBuilder union = new StringBuilder();
        for (int i = 0; i < tables.size(); i++) {
            if (union.length() > 0) { union.append(" UNION ALL "); }
            union.append("SELECT ").append(QdTableMetadata.columnList(base)).append(',').append(i).append(" qd_source FROM ")
                .append(tables.get(i)).append(" WHERE ");
            appendScope(union,kind,q,group,orgs,p,global);
        }
        return union.toString();
    }
    /** 统一在线与历史分支的组织授权、时间和其他筛选；分页、计数与两种导出复用。 */
    static void appendScope(StringBuilder sql,LogKind kind,LogQuery q,String group,List<String> orgs,SQLParameter p,boolean global) {
        sql.append("pk_group=? AND ("); p.addParam(group);
        Set<String> unique = new LinkedHashSet<String>(orgs); int n = 0;
        for (String org : unique) {
            if (n > 0 && n % 900 == 0) { sql.append(") OR pk_org IN ("); }
            else if (n > 0) { sql.append(','); }
            else { sql.append("pk_org IN ("); }
            sql.append('?'); p.addParam(org); n++;
        }
        if (n > 0) { sql.append(')'); }
        if (global) { if (n > 0) { sql.append(" OR "); } sql.append("pk_org IS NULL"); }
        sql.append(") AND event_time>=? AND event_time<?"); p.addParam(q.getFromUtc()); p.addParam(q.getToUtc());
        exact(sql,p,"pk_org",q.getOrganizationId());
        exact(sql,p,"user_id",q.getUserId()); exact(sql,p,"user_code",q.getUserCode());
        exact(sql,p,"result_code",q.getResultCode());
        if (kind != LogKind.SECURITY) {
            exact(sql,p,"node_code",q.getNodeCode()); exact(sql,p,"bill_code",q.getBillCode());
            exact(sql,p,"request_id",q.getRequestId()); exact(sql,p,"batch_id",q.getBatchId());
            if (present(q.getReasonContains())) {
                sql.append(" AND reason LIKE ? ESCAPE '\\'");
                p.addParam("%" + q.getReasonContains().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            }
        } else if (present(q.getNodeCode()) || present(q.getBillCode()) || present(q.getRequestId())
                || present(q.getBatchId()) || present(q.getReasonContains())) {
            throw new IllegalArgumentException("Business-only filters cannot be applied to security logs");
        }
    }
    /** 把日志枚举映射到固定自建表，拒绝未知类别。 */
    private static String table(LogKind kind) {
        if (kind == LogKind.SIGNATURE) { return "qd_es_signlog"; }
        if (kind == LogKind.AUDIT) { return "qd_es_audit"; }
        if (kind == LogKind.SECURITY) { return "qd_es_security"; }
        throw new IllegalArgumentException("Log kind is required");
    }
    /** 判断可选筛选文本是否有实际内容。 */
    private static boolean present(String value) { return value != null && value.length() > 0; }
    /** 将非空精确匹配条件与参数同步追加到查询，不将筛选文本拼入 SQL。 */
    private static void exact(StringBuilder sql, SQLParameter p, String field, String value) {
        if (present(value)) { sql.append(" AND ").append(field).append("=?"); p.addParam(value); }
    }
    /** 检查时间筛选、分页边界及服务端授权范围，非集团安全模式必须具有合法组织集合。 */
    static void validate(LogQuery q, String group, List<String> orgs,boolean global) {
        if (q == null || group == null || group.length() == 0 || orgs == null || (orgs.isEmpty()&&!global)) {
            throw new IllegalArgumentException("Explicit server-authorized group and organization scope is required");
        }
        for (String org : orgs) { if (!present(org)) { throw new IllegalArgumentException("Empty authorized organization"); } }
        if (present(q.getOrganizationId()) && !orgs.contains(q.getOrganizationId())) {
            throw new IllegalArgumentException("当前账号没有所选业务单元的日志权限。");
        }
        if (!QdUtcClock.parseUtc(q.getFromUtc()).before(QdUtcClock.parseUtc(q.getToUtc()))) {
            throw new IllegalArgumentException("Time interval must be non-empty and half-open");
        }
        if (q.getOffset() < 0 || q.getPageSize() < 1 || q.getPageSize() > 1000) {
            throw new IllegalArgumentException("Invalid pagination; pageSize must be 1..1000");
        }
    }
}
