package nc.bs.uapbd.qd.esign.persistence;

import java.sql.Types;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import nc.bs.dao.BaseDAO;
import nc.bs.dao.DAOException;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.MapListProcessor;
import nc.jdbc.framework.processor.MapProcessor;
import nc.bs.uapbd.qd.esign.core.DatabaseTextRules;
import nc.bs.uapbd.qd.esign.core.ReasonRules;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.vo.pub.BusinessException;

/** 加入调用方 NC CMT 的底层数据访问工具，集中处理固定表结构、参数绑定、日志冗余字段和跨月定位；不自行提交或压制失败。 */
public class QdEsignStore {
    private static final String ACTIVE_BUSINESS_UNIT =
        " FROM org_orgs WHERE pk_group=? AND isbusinessunit='Y' AND islastversion='Y' AND enablestate=2 AND NVL(dr,0)=0";
    private static final String PARTNER_BATCH_SIGN_OBJECT = "o.pk_group=r.pk_group AND o.pk_org=r.pk_org"
        +" AND o.target_org=r.pk_org AND o.parent_object_id IS NULL AND o.relation_type='ROOT'"
        +" AND o.unit_state='PENDING' AND o.policy_owner_type IN ('G','O')"
        +" AND o.object_pk IS NOT NULL AND o.bill_code IS NOT NULL AND o.policy_owner_org IS NOT NULL AND o.policy_scope_id IS NOT NULL"
        +" AND o.owner_policy_version IS NOT NULL AND o.owner_control_version IS NOT NULL"
        +" AND o.owner_reason_required IN ('Y','N') AND o.owner_audit_enabled IN ('Y','N')"
        +" AND o.object_type=CASE WHEN r.node_code IN ('10140CUG','10140CUO') THEN 'BD_CUSTOMER'"
        +" WHEN r.node_code IN ('10140SUG','10140SUO') THEN 'BD_SUPPLIER'"
        +" WHEN r.node_code IN ('10140MAG','10140MAO') THEN 'BD_MATERIAL' END";
    private static final String PARTNER_BATCH_SIGN_REQUEST = "r.policy_mode='OWNER_V1' AND r.button_code='BatchUpdate'"
        +" AND r.operation_type='UPDATE' AND r.signature_state='PENDING' AND r.business_state='PENDING'"
        +" AND r.finished_at IS NULL AND r.execution_token IS NULL"
        +" AND r.node_code IN ('10140CUG','10140CUO','10140SUG','10140SUO','10140MAG','10140MAO')";
    private static final String GROUP_POLICY_CATALOG =
        "SELECT n.scope_id,n.node_code,n.node_name,n.button_code,n.button_name,n.mapping_state,"
        + "p.sign_enabled,p.reason_required,p.audit_enabled,p.policy_version,"
        + "CASE WHEN EXISTS (SELECT 1 FROM qd_es_node a WHERE a.group_scope_id=n.scope_id AND a.validated='Y'"
        + " AND TRIM(a.node_code) IS NOT NULL AND TRIM(a.button_code) IS NOT NULL"
        + " AND TRIM(a.adapter_class) IS NOT NULL AND TRIM(a.operation_types) IS NOT NULL AND TRIM(a.object_type) IS NOT NULL)"
        + " THEN 'Y' ELSE 'N' END configurable"
        + " FROM qd_es_node n LEFT JOIN qd_es_bd_policy p ON p.pk_group=? AND p.scope_id=n.scope_id"
        + " WHERE n.group_scope_id=n.scope_id";
    private static final String ORG_BASE_POLICY_CATALOG =
        "SELECT n.scope_id,n.node_code,n.node_name,n.button_code,n.button_name,n.mapping_state,"
        + "'Y' base_scope,p.sign_enabled,p.reason_required,p.audit_enabled,p.policy_version,"
        + "CASE WHEN EXISTS (SELECT 1 FROM qd_es_node a WHERE a.group_scope_id=n.scope_id AND a.validated='Y'"
        + " AND TRIM(a.node_code) IS NOT NULL AND TRIM(a.button_code) IS NOT NULL"
        + " AND TRIM(a.adapter_class) IS NOT NULL AND TRIM(a.operation_types) IS NOT NULL AND TRIM(a.object_type) IS NOT NULL)"
        + " THEN 'Y' ELSE 'N' END configurable"
        + " FROM qd_es_node n LEFT JOIN qd_es_bd_org_policy p ON p.pk_group=? AND p.pk_org=? AND p.scope_id=n.scope_id"
        + " WHERE n.group_scope_id=n.scope_id";

    /** dao：NC 原生数据访问入口，使用调用方事务。 */
    private final BaseDAO dao;
    /** 仅本次仓储实例缓存当前数据源字符集，不跨用户或数据源使用静态缓存。 */
    private Charset textCharset;

    /** 保存原生 BaseDAO 依赖并加入调用方事务，无参形式创建 DAO；本对象不拥有提交权。 */
    public QdEsignStore() { this(new BaseDAO()); }
    /** 保存原生 BaseDAO 依赖并加入调用方事务，无参形式创建 DAO；本对象不拥有提交权。 */
    public QdEsignStore(BaseDAO dao) {
        if (dao == null) { throw new IllegalArgumentException("dao is required"); }
        // 本项目表使用各自的时间与版本字段，NC 自动追加 TS 会导致 Oracle 写入失败。
        dao.setAddTimeStamp(false);
        this.dao = dao;
    }

    /** A large owner-policy batch must fit both the online request table and every precreated history table. */
    public void requireBatchObjectCountCapacity(int size) throws DAOException {
        if (size <= 9999) { return; }
        Map<String,Object> row=one("SELECT COUNT(*) table_count,"
            +"COUNT(CASE WHEN t.table_name='QD_ES_REQUEST' THEN 1 END) online_count,"
            +"COUNT(CASE WHEN c.data_type='NUMBER' AND c.data_precision>=6 AND c.data_scale=0"
            +" AND c.nullable='Y' THEN 1 END) compatible_count"
            +" FROM user_tables t LEFT JOIN user_tab_columns c ON c.table_name=t.table_name"
            +" AND c.column_name='OWNER_OBJECT_COUNT'"
            +" WHERE t.table_name='QD_ES_REQUEST' OR REGEXP_LIKE(t.table_name,'^QD_ES_REQUEST_H[0-9]{6}$')",
            parameters());
        Object total=row==null?null:row.get("table_count"),online=row==null?null:row.get("online_count"),
            compatible=row==null?null:row.get("compatible_count");
        if (!(total instanceof Number) || !(online instanceof Number) || !(compatible instanceof Number)
                || ((Number)total).longValue()<1 || ((Number)online).longValue()!=1
                || ((Number)compatible).longValue()!=((Number)total).longValue())
            throw new DAOException("快速批改超过 9999 条前，须先核实并升级在线及历史请求表的对象数量字段。");
    }

    /** Only the still-uncommitted batch preparation may establish its final signing deadline. */
    public void finishBatchPreparation(String requestId,String expiresAt) throws DAOException {
        if (requestId==null || !requestId.matches("[a-fA-F0-9]{32}"))
            throw new IllegalArgumentException("Valid request identity is required");
        QdUtcClock.parseUtc(expiresAt);
        requireOne(updateBatchPreparationDeadline(requestId,expiresAt),"finish batch preparation");
    }
    protected int updateBatchPreparationDeadline(String requestId,String expiresAt) throws DAOException {
        return dao.executeUpdate("UPDATE qd_es_request SET expires_at=? WHERE request_id=? AND expires_at<=?"
            +" AND finished_at IS NULL AND execution_token IS NULL AND policy_mode='OWNER_V1'"
            +" AND signature_state IN ('PENDING','NOT_REQUIRED') AND business_state IN ('PENDING','READY')"
            +" AND button_code='BatchUpdate' AND operation_type='UPDATE'"
            +" AND node_code IN ('10140CUG','10140CUO','10140SUG','10140SUO','10140MAG','10140MAO')",
            parameters(expiresAt,requestId,expiresAt));
    }

    /** Write one batch authentication attempt from the authoritative request and object rows in this CMT. */
    public int recordPartnerBatchAttempt(String requestId,String attemptId,String status,boolean locked,String reason,String eventTime)
            throws DAOException {
        if (requestId==null || !requestId.matches("[a-fA-F0-9]{32}") || attemptId==null
                || !attemptId.matches("[a-fA-F0-9]{32}") || !java.util.Arrays.asList("SUCCESS","PASSWORD_INCORRECT",
                    "ACCOUNT_LOCKED","ACCOUNT_UNAVAILABLE","CANCELLED","EXPIRED").contains(status)
                || locked!="ACCOUNT_LOCKED".equals(status))
            throw new IllegalArgumentException("Invalid batch authentication attempt");
        java.util.Date instant=QdUtcClock.parseUtc(eventTime);String month=QdUtcClock.beijingMonth(instant);
        requireLossless("reason",reason);
        if(reason!=null && reason.getBytes(databaseCharset()).length>4000) throw new DAOException("Operation reason exceeds database byte limit");
        Map<String,Object> counts=readPartnerBatchAttemptCounts(requestId);
        Object expected=counts==null?null:counts.get("expected_count"),actual=counts==null?null:counts.get("actual_count"),
            valid=counts==null?null:counts.get("valid_count"),sign=counts==null?null:counts.get("sign_count");
        if(!(expected instanceof Number) || !(actual instanceof Number) || !(valid instanceof Number) || !(sign instanceof Number))
            throw new DAOException("快速批改签名对象数量无法核实。");
        long total=((Number)expected).longValue(),real=((Number)actual).longValue(),checked=((Number)valid).longValue(),
            needed=((Number)sign).longValue();
        if(total<1 || total>nc.vo.uapbd.qd.esign.PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS
                || total!=real || total!=checked || needed<0 || needed>total)
            throw new DAOException("快速批改签名对象范围不完整。");
        if(needed==0) return 0;
        int written=writePartnerBatchAttemptRows(requestId,attemptId,status,locked,reason,eventTime,month);
        if(written!=needed) throw new DAOException("快速批改签名日志数量不一致，必须回滚。");
        ensurePartnerBatchAttemptLocator(requestId,month);
        return written;
    }
    protected Map<String,Object> readPartnerBatchAttemptCounts(String requestId) throws DAOException {
        return one("SELECT r.owner_object_count expected_count,COUNT(o.object_id) actual_count,"
            +"COUNT(CASE WHEN "+PARTNER_BATCH_SIGN_OBJECT+" AND o.owner_sign_required IN ('Y','N') THEN 1 END) valid_count,"
            +"COUNT(CASE WHEN "+PARTNER_BATCH_SIGN_OBJECT+" AND o.owner_sign_required='Y' THEN 1 END) sign_count"
            +" FROM qd_es_request r LEFT JOIN qd_es_object o ON o.request_id=r.request_id"
            +" WHERE r.request_id=? AND "+PARTNER_BATCH_SIGN_REQUEST
            +" GROUP BY r.owner_object_count",parameters(requestId));
    }
    protected int writePartnerBatchAttemptRows(String requestId,String attemptId,String status,boolean locked,String reason,
            String eventTime,String month) throws DAOException {
        String sql="INSERT INTO qd_es_signlog (event_id,request_id,batch_id,attempt_id,object_id,user_id,user_code,user_name,"
            +"pk_group,pk_org,node_code,node_name,button_code,button_name,operation_type,object_type,object_pk,bill_code,"
            +"result_code,lock_result,reason,client_ip,terminal_name,event_time,archive_month,"
            +"policy_owner_org,policy_owner_type,policy_scope_id,target_org)"
            +" SELECT LOWER(RAWTOHEX(SYS_GUID())),r.request_id,r.batch_id,?,o.object_id,r.user_id,r.user_code,r.user_name,"
            +"r.pk_group,r.pk_org,r.node_code,r.node_name,r.button_code,r.button_name,r.operation_type,"
            +"o.object_type,o.object_pk,o.bill_code,?,?,?,r.client_ip,r.terminal_name,?,?,"
            +"o.policy_owner_org,o.policy_owner_type,o.policy_scope_id,o.target_org"
            +" FROM qd_es_request r JOIN qd_es_object o ON o.request_id=r.request_id AND o.pk_group=r.pk_group AND o.pk_org=r.pk_org"
            +" WHERE r.request_id=? AND "+PARTNER_BATCH_SIGN_REQUEST+" AND "+PARTNER_BATCH_SIGN_OBJECT+" AND o.owner_sign_required='Y'";
        return executePartnerBatchAttemptSql(sql,parameters(attemptId,status,locked?"Y":"N",reason,eventTime,month,requestId));
    }
    protected int executePartnerBatchAttemptSql(String sql,SQLParameter parameters) throws DAOException {
        return dao.executeUpdate(sql,parameters);
    }
    protected void ensurePartnerBatchAttemptLocator(String requestId,String month) throws DAOException {
        ensureLocator(requestId,month);
    }

    /** The request and its month locator are already inserted in the caller's uncommitted preparation transaction. */
    public PreparedBatchObjects beginPreparedBatchObjects(String requestId,String month) throws DAOException {
        if(requestId==null || !requestId.matches("[a-fA-F0-9]{32}") || month==null || !month.matches("[0-9]{4}(0[1-9]|1[0-2])"))
            throw new IllegalArgumentException("Valid request and month are required");
        Map<String,Object> request=one("SELECT r.pk_group,r.pk_org,r.node_code,r.owner_object_count FROM qd_es_request r"
            +" JOIN qd_es_locator l ON l.request_id=r.request_id AND l.archive_month=r.archive_month AND l.request_closed='N'"
            +" WHERE r.request_id=? AND r.archive_month=? AND r.policy_mode='OWNER_V1' AND r.button_code='BatchUpdate'"
            +" AND r.operation_type='UPDATE' AND r.signature_state IN ('PENDING','NOT_REQUIRED')"
            +" AND r.business_state IN ('PENDING','READY') AND r.finished_at IS NULL AND r.execution_token IS NULL"
            +" AND NOT EXISTS (SELECT 1 FROM qd_es_object o WHERE o.request_id=r.request_id)",parameters(requestId,month));
        Object count=request==null?null:request.get("owner_object_count");String group=request==null?null:EsignValues.text(request,"pk_group"),
            org=request==null?null:EsignValues.text(request,"pk_org"),node=request==null?null:EsignValues.text(request,"node_code");
        String type="10140CUG".equals(node)||"10140CUO".equals(node)?"BD_CUSTOMER"
            :"10140SUG".equals(node)||"10140SUO".equals(node)?"BD_SUPPLIER"
            :"10140MAG".equals(node)||"10140MAO".equals(node)?"BD_MATERIAL":null;
        if(group==null || org==null || type==null || !(count instanceof Number)
                || ((Number)count).longValue()<1 || ((Number)count).longValue()>nc.vo.uapbd.qd.esign.PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS)
            throw new DAOException("快速批改对象写入缺少新建请求或有效月份定位。");
        return new PreparedBatchObjects(this,requestId,month,group,org,type,((Number)count).intValue());
    }
    /** This writer is thread-confined and may not outlive its caller's preparation transaction. */
    public static final class PreparedBatchObjects implements AutoCloseable {
        private final QdEsignStore store;private final String requestId,month,group,org,type;private final int expected;
        private final Thread thread;private int written;private boolean closed;
        private PreparedBatchObjects(QdEsignStore store,String requestId,String month,String group,String org,String type,int expected) {
            this.store=store;this.requestId=requestId;this.month=month;this.group=group;this.org=org;this.type=type;
            this.expected=expected;this.thread=Thread.currentThread();
        }
        public void insert(Map<String,Object> values) throws DAOException {
            if(closed || thread!=Thread.currentThread() || written>=expected || values==null
                    || !requestId.equals(values.get("request_id")) || !month.equals(values.get("archive_month"))
                    || !group.equals(values.get("pk_group")) || !org.equals(values.get("pk_org"))
                    || !type.equals(values.get("object_type")) || !"ROOT".equals(values.get("relation_type"))
                    || values.get("parent_object_id")!=null || !"PENDING".equals(values.get("unit_state"))
                    || !org.equals(values.get("target_org")))
                throw new DAOException("快速批改对象不属于当前准备事务。");
            store.insertPreparedBatchObjectRow(values);written++;
        }
        public void finish() throws DAOException {
            if(closed || thread!=Thread.currentThread() || written!=expected)
                throw new DAOException("快速批改对象写入数量不完整。");
            closed=true;
        }
        public void close() { closed=true; }
    }
    protected void insertPreparedBatchObjectRow(Map<String,Object> values) throws DAOException {
        insertInternal("qd_es_object",values,false);
    }

    /** Hold a shared schema-version lock until CMT completion, and fail closed on rollback or missing guards. */
    protected void requireOrgReceiptStorage() throws DAOException {
        lockOrgReceiptSchema();
        Map<String,Object> versions=one("SELECT COUNT(CASE WHEN version_no='1.3.0' THEN 1 END) owner_count,"
            +"COUNT(CASE WHEN version_no='1.3.1' THEN 1 END) receipt_count,"
            +"COUNT(CASE WHEN version_no='1.3.1-SEL' THEN 1 END) selection_count,"
            +"COUNT(CASE WHEN version_no IN ('1.3.0-RB','1.3.1-RB','1.3.1-SEL-RB') THEN 1 END) rollback_count"
            +" FROM qd_es_schema_version WHERE version_no IN ('1.3.0','1.3.1','1.3.1-SEL','1.3.0-RB','1.3.1-RB','1.3.1-SEL-RB')",parameters());
        if(versions==null || !countIs(versions,"owner_count",1) || !countIs(versions,"receipt_count",1)
                || !countIs(versions,"selection_count",1) || !countIs(versions,"rollback_count",0) || !orgReceiptTableExists())
            throw new DAOException("组织页签回执结构未准备或已回退。");
        Map<String,Object> guards=one("SELECT COUNT(*) guard_count FROM user_triggers t JOIN user_objects o ON o.object_name=t.trigger_name"
            +" WHERE o.object_type='TRIGGER' AND o.status='VALID' AND t.status='ENABLED' AND t.triggering_event='DELETE'"
            +" AND t.trigger_type='BEFORE EACH ROW' AND ((t.trigger_name='QDES_BPAGE_OBJ_GUARD' AND t.table_name='QD_ES_OBJECT')"
            +" OR (t.trigger_name='QDES_BPAGE_REQ_GUARD' AND t.table_name='QD_ES_REQUEST'))",parameters());
        if(guards==null || !countIs(guards,"guard_count",2)) throw new DAOException("组织页签回执归档保护不可用。");
        list("SELECT "+QdTableMetadata.columnList(NativeOrgBatchReceiptRecords.TABLE)+" FROM qd_es_batch_page WHERE 1=0",parameters());
    }
    protected void lockOrgReceiptSchema() throws DAOException {
        dao.executeUpdate("LOCK TABLE qd_es_schema_version IN SHARE MODE NOWAIT",parameters());
    }
    protected boolean orgReceiptTableExists() throws DAOException { return dao.isTableExisted(NativeOrgBatchReceiptRecords.TABLE); }
    protected void requireNoOrgReceiptRows(String requestId) throws DAOException {
        if(one("SELECT record_id FROM qd_es_batch_page WHERE request_id=? AND ROWNUM=1",parameters(requestId))!=null)
            throw new DAOException("组织页签回执已经存在，不能重复写入。");
    }
    protected void requireNoOrgOutcomeRows(String requestId) throws DAOException {
        if(one("SELECT record_id FROM qd_es_batch_page WHERE request_id=? AND row_kind IN ('T','H','P') AND ROWNUM=1",
                parameters(requestId))!=null) throw new DAOException("组织页签结果回执已经存在，不能重复写入。");
    }
    protected List<Map<String,Object>> loadOrgReceiptRows(String requestId) throws DAOException {
        return list("SELECT "+QdTableMetadata.columnList(NativeOrgBatchReceiptRecords.TABLE)
            +" FROM qd_es_batch_page WHERE request_id=?",parameters(requestId));
    }
    protected void beginOrgReceiptRows(String requestId,String month) throws DAOException {
        requireNoOrgReceiptRows(requestId);
        ensureOpenOrgReceiptLocator(requestId,month);
    }
    protected void beginOrgOutcomeRows(String requestId,String month) throws DAOException {
        requireNoOrgOutcomeRows(requestId);
        ensureOpenOrgReceiptLocator(requestId,month);
    }
    private void ensureOpenOrgReceiptLocator(String requestId,String month) throws DAOException {
        ensureLocator(requestId,month);
        Map<String,Object> locator=one("SELECT request_closed FROM qd_es_locator WHERE request_id=? AND archive_month=? FOR UPDATE",
            parameters(requestId,month));
        if(locator==null || !"N".equals(locator.get("request_closed"))) throw new DAOException("组织页签回执月份定位不处于开放状态。");
    }
    protected void insertOrgReceiptRow(Map<String,Object> values) throws DAOException {
        insertInternal(NativeOrgBatchReceiptRecords.TABLE,values,false);
    }
    protected long countOrgReceiptRows(String requestId) throws DAOException {
        Map<String,Object> count=one("SELECT COUNT(*) row_count FROM qd_es_batch_page WHERE request_id=?",parameters(requestId));
        if(count==null || !(count.get("row_count") instanceof Number)) throw new DAOException("组织页签回执数量不可核实。");
        return ((Number)count.get("row_count")).longValue();
    }
    private static boolean countIs(Map<String,Object> row,String name,long expected) {
        Object value=row.get(name);if(!(value instanceof Number))return false;
        try{return new java.math.BigDecimal(String.valueOf(value)).compareTo(java.math.BigDecimal.valueOf(expected))==0;}
        catch(NumberFormatException invalid){return false;}
    }

    /** 在原生密码验证之前检查事由，避免认证完成后才发现字符已经被数据库替换。 */
    public void validateReason(String reason) throws BusinessException {
        ReasonRules.validate(reason);
        requireLossless("reason", reason);
        EsignValues.require(reason == null || reason.getBytes(databaseCharset()).length <= 4000,
            "Operation reason exceeds database byte limit");
    }

    /** 取消时保留可解释的转义文本，不能因用户输入 emoji 等 GBK 不支持的字符丢失取消事件。 */
    public String cancellationReason(String reason) throws DAOException {
        return DatabaseTextRules.cancellation(reason, databaseCharset());
    }

    /** 从当前 NC DAO 所属数据源读取事实；无法核实字符集时失败关闭，不以 JVM 默认字符集代替。 */
    private Charset databaseCharset() throws DAOException {
        if (textCharset == null) {
            Map<String,Object> row = one("SELECT value FROM nls_database_parameters WHERE parameter='NLS_CHARACTERSET'", parameters());
            try {
                textCharset = DatabaseTextRules.oracleCharset(row == null ? null : String.valueOf(row.get("value")));
            } catch (IllegalArgumentException exception) {
                throw new DAOException("QD database charset cannot be verified or is unsupported");
            }
        }
        return textCharset;
    }

    private void requireLossless(String field, String value) throws DAOException {
        if (value != null && !DatabaseTextRules.lossless(value, databaseCharset())) {
            throw new DAOException("QD field " + field + " contains text not representable in the database charset");
        }
    }

    /** 按固定列白名单绑定并插入数据，必要时补齐日志冗余字段与月份定位。 */
    public void insert(String table, Map<String,Object> input) throws DAOException {
        insertInternal(table,input,true);
    }
    private void insertInternal(String table, Map<String,Object> input,boolean maintainLocator) throws DAOException {
        if (input == null || input.isEmpty()) { throw new IllegalArgumentException("Empty insert"); }
        List<String> allowed = QdTableMetadata.columns(table);
        Map<String,Object> values = new LinkedHashMap<String,Object>(input);
        enrichLog(table, values);
        for (String field : values.keySet()) {
            if (!allowed.contains(field)) { throw new IllegalArgumentException("Unknown field: " + table + "." + field); }
        }
        // 安全事件的幂等目录永久在线，日志本身归档后也不能重复导入同一来源版本。
        if ("qd_es_security".equals(table)) {
            Map<String,Object> existing = one("SELECT event_id FROM qd_es_security_key WHERE source_system=? AND source_event_id=? AND source_version=?",
                parameters(values.get("source_system"), values.get("source_event_id"), values.get("source_version")));
            if (existing != null) { return; }
            Map<String,Object> key = new LinkedHashMap<String,Object>();
            for (String field : QdTableMetadata.columns("qd_es_security_key")) { key.put(field, values.get(field)); }
            insert("qd_es_security_key", key);
        }
        StringBuilder names = new StringBuilder();
        StringBuilder holders = new StringBuilder();
        SQLParameter parameters = new SQLParameter();
        for (String field : allowed) {
            if (!values.containsKey(field)) { continue; }
            if (names.length() > 0) { names.append(','); holders.append(','); }
            names.append(field); holders.append('?'); bind(parameters, field, values.get(field));
        }
        int count = dao.executeUpdate("INSERT INTO " + table + " (" + names + ") VALUES (" + holders + ")", parameters);
        requireOne(count, "insert " + table);
        if (maintainLocator && QdTableMetadata.isArchivable(table) && values.get("request_id") != null) {
            ensureLocator(String.valueOf(values.get("request_id")), String.valueOf(values.get("archive_month")));
        }
    }

    /** 从权威请求和对象补齐日志公共字段，保存操作当时的名称与组织信息，避免后续档案变化影响证据解释。 */
    protected void enrichLog(String table, Map<String,Object> values) throws DAOException {
        if (!"qd_es_signlog".equals(table) && !"qd_es_audit".equals(table)) { return; }
        Object id = values.get("request_id");
        if (id == null) { throw new IllegalArgumentException("request_id is required"); }
        Map<String,Object> request = loadRequest(String.valueOf(id), false);
        if (request == null) { throw new DAOException("QD request not found"); }
        String[] shared = {"batch_id","user_id","user_code","user_name","pk_group","pk_org",
            "node_code","node_name","button_code","button_name","operation_type",
            "reason","client_ip","terminal_name"};
        for (String field : shared) {
            if (!values.containsKey(field)) { values.put(field, request.get(field)); }
        }
        if (values.get("object_id") != null) {
            Map<String,Object> object = one("SELECT object_type,object_pk,bill_code,policy_owner_org,policy_owner_type,policy_scope_id,target_org"
                + " FROM qd_es_object WHERE object_id=? AND request_id=?",
                parameters(values.get("object_id"), id));
            if (object == null) { throw new DAOException("QD request object not found"); }
            // 对象类型、真实主键和编码始终以已保存对象为准，不能被请求空值或调用载荷覆盖。
            for (String field : new String[] {"object_type","object_pk","bill_code","policy_owner_org","policy_owner_type","policy_scope_id","target_org"}) {
                values.put(field, object.get(field));
            }
        }
    }

    /** 按请求标识读取请求，可选择 FOR UPDATE 加锁以串行化状态转换。 */
    public Map<String,Object> loadRequest(String requestId, boolean forUpdate) throws DAOException {
        return one("SELECT " + QdTableMetadata.columnList("qd_es_request")
            + " FROM qd_es_request WHERE request_id=?" + (forUpdate ? " FOR UPDATE" : ""), parameters(requestId));
    }
    /** 同一次认证可能为多个对象写日志，按尝试编号去重统计密码错误。 */
    public int countSignaturePasswordFailures(String requestId) throws DAOException {
        Map<String,Object> row = one("SELECT COUNT(DISTINCT attempt_id) failure_count FROM qd_es_signlog"
            + " WHERE request_id=? AND result_code='PASSWORD_INCORRECT'", parameters(requestId));
        if (row == null || !(row.get("failure_count") instanceof Number)) {
            throw new DAOException("QD signature failure count is unavailable");
        }
        return ((Number) row.get("failure_count")).intValue();
    }
    /** 结果读取可回退到定位目录指向的历史终态请求；不锁定或修改历史数据。 */
    public Map<String,Object> loadRequestResult(String requestId) throws DAOException {
        Map<String,Object> online = loadRequest(requestId, false);
        return online == null ? loadArchivedRequest(requestId) : online;
    }
    /** 仅供结果恢复使用；活跃在线请求及业务认领仍使用原 loadRequest 路径。 */
    public Map<String,Object> loadArchivedRequest(String requestId) throws DAOException {
        return new CompletedRequestReader(this).request(requestId);
    }
    /** 恢复成功请求的完整对象集合，兼容对象先于请求归档及跨月归档的情况。 */
    public List<Map<String,Object>> loadResultObjects(Map<String,Object> request) throws DAOException {
        return new CompletedRequestReader(this).objects(request);
    }
    public List<Map<String,Object>> loadAssignmentResultObjects(Map<String,Object> request) throws DAOException {
        if (request.get("finished_at") == null) { return loadObjects(String.valueOf(request.get("request_id"))); }
        return new CompletedRequestReader(this).assignmentObjects(request);
    }
    public List<Map<String,Object>> loadPageDeleteResultObjects(Map<String,Object> request) throws DAOException {
        if (request.get("finished_at") == null) { return loadObjects(String.valueOf(request.get("request_id"))); }
        return new CompletedRequestReader(this).pageDeleteObjects(request);
    }
    public List<Map<String,Object>> loadArchiveBatchResultObjects(Map<String,Object> request) throws DAOException {
        if(request.get("finished_at")==null) return loadObjects(String.valueOf(request.get("request_id")));
        return new CompletedRequestReader(this).batchObjects(request);
    }
    public List<Map<String,Object>> loadSapplyBatchResultObjects(Map<String,Object> request) throws DAOException {
        if (request.get("finished_at") == null) return loadObjects(String.valueOf(request.get("request_id")));
        return new CompletedRequestReader(this).sapplyBatchObjects(request);
    }
    /** 历史结果标记仅由服务器读取器加入，不属于持久化字段，也不能成为业务执行授权。 */
    public static boolean isArchivedResult(Map<String,Object> request) {
        return CompletedRequestReader.isArchived(request);
    }
    /** 查询前验证目标月表实际存在；可覆盖的边界只用于明确的离线仓储测试替身。 */
    public void requireHistoryTable(String base, String month) throws DAOException {
        String table = QdTableMetadata.historyTable(base, month);
        if (!dao.isTableExisted(table)) { throw new DAOException("Missing approved history table: " + table); }
    }
    /** 读取请求关联对象，供范围校验、执行结果重入和审计关联使用。 */
    public List<Map<String,Object>> loadObjects(String requestId) throws DAOException {
        return list("SELECT " + QdTableMetadata.columnList("qd_es_object")
            + " FROM qd_es_object WHERE request_id=? ORDER BY object_id", parameters(requestId));
    }
    public Map<String,Object> loadAssignmentUnit(String requestId, String objectId) throws DAOException {
        return one("SELECT " + QdTableMetadata.columnList("qd_es_object")
            + " FROM qd_es_object WHERE request_id=? AND object_id=? FOR UPDATE", parameters(requestId, objectId));
    }
    public Map<String,Object> lockSapplyBatchBill(String requestId, String billPk) throws DAOException {
        List<Map<String,Object>> rows = list("SELECT " + QdTableMetadata.columnList("qd_es_object")
            + " FROM qd_es_object WHERE request_id=? AND object_type='IC_SAPPLY' AND object_pk=? FOR UPDATE",
            parameters(requestId, billPk));
        if (rows.size() > 1) throw new DAOException("Ambiguous outbound request batch bill");
        return rows.isEmpty() ? null : rows.get(0);
    }
    /** Avoid loading a large main-tab batch merely to detect an organization-page request. */
    public boolean hasOrganizationBatchMarker(String requestId) throws DAOException {
        return one("SELECT object_id FROM qd_es_object WHERE request_id=? AND client_object_id LIKE 'orgbulk%' AND ROWNUM=1",
            parameters(requestId))!=null;
    }
    public void finishAssignmentUnit(String requestId, String objectId, String state, String code) throws DAOException {
        requireOne(dao.executeUpdate("UPDATE qd_es_object SET unit_state=?,unit_error_code=?,unit_finished_at=?"
            + " WHERE request_id=? AND object_id=? AND unit_state='PENDING'",
            parameters(state, code, EsignValues.utc(), requestId, objectId)), "finish material assignment unit");
    }
    /** 按范围目录标识读取已登记的节点映射。 */
    public Map<String,Object> loadNode(String scopeId) throws DAOException {
        return one("SELECT " + QdTableMetadata.columnList("qd_es_node") + " FROM qd_es_node WHERE scope_id=?", parameters(scopeId));
    }
    /** 根据真实节点及按钮编码读取唯一映射，不按名称猜测适配关系。 */
    public Map<String,Object> loadNodeByAction(String nodeCode, String buttonCode) throws DAOException {
        List<Map<String,Object>> rows = list("SELECT " + QdTableMetadata.columnList("qd_es_node")
            + " FROM qd_es_node WHERE node_code=? AND button_code=?", parameters(nodeCode, buttonCode));
        if (rows.size() > 1) { throw new DAOException("Ambiguous QD node/button mapping"); }
        return rows.isEmpty() ? null : rows.get(0);
    }
    /** 按集团、业务单元和真实节点按钮查找已保存的配置。 */
    public Map<String,Object> loadPolicy(String groupId, String orgId, String nodeCode, String buttonCode) throws DAOException {
        return one("SELECT " + QdTableMetadata.columnList("qd_es_policy")
            + " FROM qd_es_policy WHERE pk_group=? AND pk_org=? AND node_code=? AND button_code=?",
            parameters(groupId, orgId, nodeCode, buttonCode));
    }
    public boolean isActiveBusinessUnit(String groupId, String orgId) throws DAOException {
        return one("SELECT pk_org" + ACTIVE_BUSINESS_UNIT + " AND pk_org=?", parameters(groupId, orgId)) != null;
    }
    /** 对既有档案允许读取当前集团内已停用组织的历史策略；未知或其他集团组织不允许作为归属。 */
    public boolean isBusinessUnitInGroup(String groupId, String orgId) throws DAOException {
        return one("SELECT pk_org FROM org_orgs WHERE pk_group=? AND pk_org=? AND isbusinessunit='Y'"
            + " AND islastversion='Y' AND NVL(dr,0)=0", parameters(groupId, orgId)) != null;
    }

    /** 财务、采购等目标组织不一定是业务单元，但仍须属于当前集团。 */
    public boolean isOrganizationInGroup(String groupId, String orgId) throws DAOException {
        return one("SELECT pk_org FROM org_orgs WHERE pk_group=? AND pk_org=?"
            + " AND islastversion='Y' AND NVL(dr,0)=0", parameters(groupId, orgId)) != null;
    }

    /** Match arbitrary organization types in the group, using bounded parameterized IN clauses. */
    public List<String> listOrganizationsInGroup(String groupId,List<String> ids) throws DAOException {
        if(groupId==null || groupId.trim().isEmpty() || ids==null) throw new IllegalArgumentException("Organization scope is required");
        List<String> result=new ArrayList<String>();
        for(int start=0;start<ids.size();start+=900) {
            int end=Math.min(start+900,ids.size());
            StringBuilder sql=new StringBuilder("SELECT pk_org FROM org_orgs WHERE pk_group=? AND islastversion='Y' AND NVL(dr,0)=0 AND pk_org IN (");
            SQLParameter args=parameters(groupId);
            for(int i=start;i<end;i++) { if(i>start) sql.append(',');sql.append('?');args.addParam(ids.get(i)); }
            for(Map<String,Object> row:list(sql.append(')').toString(),args)) result.add(EsignValues.text(row,"pk_org"));
        }
        return result;
    }

    /** 已验证物料主档与组织子页的物理关联；客户端不能决定实际所属组织。 */
    public Map<String,Object> loadMaterialOwner(String groupId, String type, String objectPk, boolean lock)
            throws DAOException {
        String root = objectPk, target = null;
        String childTable = null, childKey = null;
        nc.vo.uapbd.qd.esign.MaterialOrgPage page = nc.vo.uapbd.qd.esign.MaterialOrgPage.forDomain(type);
        if (page != null) {
            childTable = page.table;
            childKey = page.key;
            Map<String,Object> child = one("SELECT pk_material,pk_org FROM " + childTable + " WHERE " + childKey
                + "=? AND pk_group=? AND NVL(dr,0)=0", parameters(objectPk, groupId));
            if (child == null) { return null; }
            root = EsignValues.text(child, "pk_material"); target = EsignValues.text(child, "pk_org");
            // 子页所属的财务/采购组织必须来自实际落库行，不能以操作组织代替。
            if (target == null) { return null; }
        } else if (!"BD_MATERIAL".equals(type)) { return null; }
        if (root == null) { return null; }
        Map<String,Object> parent = one("SELECT pk_material,pk_group,pk_org,code FROM bd_material"
            + " WHERE pk_material=? AND pk_group=? AND NVL(dr,0)=0" + (lock ? " FOR UPDATE" : ""),
            parameters(root, groupId));
        if (parent != null && lock && childTable != null) {
            Map<String,Object> child = one("SELECT pk_material,pk_org FROM " + childTable + " WHERE " + childKey
                + "=? AND pk_group=? AND NVL(dr,0)=0 FOR UPDATE", parameters(objectPk, groupId));
            if (child == null || !root.equals(EsignValues.text(child, "pk_material"))
                    || !java.util.Objects.equals(target, EsignValues.text(child, "pk_org"))) { return null; }
        }
        if (parent != null && target != null) { parent.put("target_org", target); }
        return parent;
    }

    public boolean isMaterialAssignedTo(String groupId, String rootPk, String orgId) throws DAOException {
        return isMaterialAssignedTo(groupId, rootPk, orgId, false);
    }
    public boolean isMaterialAssignedTo(String groupId, String rootPk, String orgId, boolean lock) throws DAOException {
        return one("SELECT pk_marorg FROM bd_marorg WHERE pk_group=? AND pk_material=? AND pk_org=? AND NVL(dr,0)=0"
                + (lock ? " FOR UPDATE" : ""),
            parameters(groupId, rootPk, orgId)) != null;
    }

    /** Resolve only predeclared NC physical relationships, never identifiers supplied by a caller. */
    public Map<String,Object> loadBaseArchiveOwner(String groupId, String objectType, String objectPk, boolean lock)
            throws DAOException {
        nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType type =
            nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType.forDomain(objectType);
        if (type == null || objectPk == null) return null;
        if (type == nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType.MATERIAL) {
            return loadMaterialOwner(groupId, objectType, objectPk, lock);
        }
        String root = objectPk, target = null;
        if (type.childTable != null) {
            Map<String,Object> child = type.bankAccountClass == null
                ? one("SELECT " + type.childRootKey + ",pk_org FROM " + type.childTable
                    + " WHERE " + type.childKey + "=? AND pk_group=? AND NVL(dr,0)=0", parameters(objectPk, groupId))
                : one("SELECT " + type.childRootKey + " FROM " + type.childTable
                    + " WHERE " + type.childKey + "=? AND accclass=? AND NVL(dr,0)=0", parameters(objectPk, type.bankAccountClass));
            if (child == null) return null;
            root = EsignValues.text(child, type.childRootKey);
            target = EsignValues.text(child, "pk_org");
            if (root == null || (type.bankAccountClass == null && target == null)) return null;
        }
        Map<String,Object> parent = one("SELECT " + type.rootKey + " pk_archive_root,pk_group,pk_org,code FROM "
            + type.rootTable + " WHERE " + type.rootKey + "=? AND pk_group=? AND NVL(dr,0)=0"
            + (lock ? " FOR UPDATE" : ""), parameters(root, groupId));
        if (parent != null && lock && type.childTable != null) {
            Map<String,Object> child = type.bankAccountClass == null
                ? one("SELECT " + type.childRootKey + ",pk_org FROM " + type.childTable
                    + " WHERE " + type.childKey + "=? AND pk_group=? AND NVL(dr,0)=0 FOR UPDATE", parameters(objectPk, groupId))
                : one("SELECT " + type.childRootKey + " FROM " + type.childTable
                    + " WHERE " + type.childKey + "=? AND accclass=? AND NVL(dr,0)=0 FOR UPDATE", parameters(objectPk, type.bankAccountClass));
            if (child == null || !root.equals(EsignValues.text(child, type.childRootKey))
                    || (target != null && !target.equals(EsignValues.text(child, "pk_org")))) return null;
        }
        if (parent != null && target != null) parent.put("target_org", target);
        return parent;
    }

    public boolean isBaseArchiveAssignedTo(String groupId, String objectType, String rootPk, String orgId, boolean lock)
            throws DAOException {
        nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType type =
            nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType.forDomain(objectType);
        if (type == null || type.assignmentTable == null) return false;
        if (type == nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType.MATERIAL) {
            return isMaterialAssignedTo(groupId, rootPk, orgId, lock);
        }
        return one("SELECT " + type.assignmentKey + " FROM " + type.assignmentTable + " WHERE pk_group=? AND "
            + type.rootKey + "=? AND pk_org=? AND NVL(dr,0)=0" + (lock ? " FOR UPDATE" : ""),
            parameters(groupId, rootPk, orgId)) != null;
    }

    /** 从已授权主键中筛选当前集团的有效业务单元，分段绑定以适配 Oracle 的 IN 限制。 */
    public List<String> listActiveBusinessUnits(String groupId, List<String> authorizedIds) throws DAOException {
        List<String> result = new ArrayList<String>();
        for (int start = 0; start < authorizedIds.size(); start += 900) {
            int end = Math.min(start + 900, authorizedIds.size());
            StringBuilder sql = new StringBuilder("SELECT pk_org").append(ACTIVE_BUSINESS_UNIT).append(" AND pk_org IN (");
            SQLParameter args = parameters(groupId);
            for (int index = start; index < end; index++) {
                if (index > start) { sql.append(','); }
                sql.append('?');
                args.addParam(authorizedIds.get(index));
            }
            for (Map<String,Object> row : list(sql.append(')').toString(), args)) {
                result.add(EsignValues.text(row, "pk_org"));
            }
        }
        return result;
    }

    public List<Map<String,Object>> listOrganizationPolicies(String groupId, String orgId) throws DAOException {
        List<Map<String,Object>> result = new ArrayList<Map<String,Object>>(list("SELECT n.scope_id,n.node_code,n.node_name,n.button_code,n.button_name,n.adapter_class,"
            + "n.mapping_state,n.operation_types,n.object_type,n.validated,p.enabled,p.sign_enabled,"
            + "p.reason_required,p.audit_enabled,p.policy_version FROM qd_es_node n LEFT JOIN qd_es_policy p"
            + " ON p.pk_group=? AND p.pk_org=? AND p.node_code=n.node_code AND p.button_code=n.button_code"
            + " WHERE n.group_scope_id IS NULL ORDER BY n.node_name,n.button_name,n.scope_id", parameters(groupId, orgId)));
        result.addAll(list(ORG_BASE_POLICY_CATALOG + " ORDER BY n.node_name,n.button_name,n.scope_id", parameters(groupId, orgId)));
        return result;
    }

    /** 业务单元基础档案策略按正式动作存储，不复用 1.2 以前的节点级旧配置。 */
    public Map<String,Object> loadOrgBasePolicyEntry(String groupId, String orgId, String scopeId) throws DAOException {
        return one(ORG_BASE_POLICY_CATALOG + " AND n.scope_id=?", parameters(groupId, orgId, scopeId));
    }

    public List<Map<String,Object>> listGroupPolicies(String groupId) throws DAOException {
        return list(GROUP_POLICY_CATALOG + " ORDER BY n.node_name,n.button_name,n.scope_id", parameters(groupId));
    }

    public Map<String,Object> loadGroupPolicyEntry(String groupId, String scopeId) throws DAOException {
        return one(GROUP_POLICY_CATALOG + " AND n.scope_id=?", parameters(groupId, scopeId));
    }

    /** 由实际节点映射选择唯一的策略作用域；集团范围不回退至旧业务单元配置。 */
    public Map<String,Object> loadPolicyWithControl(String groupId, String orgId, String node, String button) throws DAOException {
        return one("SELECT CASE WHEN n.group_scope_id IS NULL THEN c.master_enabled ELSE g.master_enabled END master_enabled,"
            + " CASE WHEN n.group_scope_id IS NULL THEN c.control_version ELSE g.control_version END control_version,"
            + " CASE WHEN n.group_scope_id IS NULL THEN p.enabled WHEN b.scope_id IS NOT NULL THEN 'Y' ELSE 'N' END enabled,"
            + " CASE WHEN n.group_scope_id IS NULL THEN p.sign_enabled ELSE b.sign_enabled END sign_enabled,"
            + " CASE WHEN n.group_scope_id IS NULL THEN p.reason_required ELSE b.reason_required END reason_required,"
            + " CASE WHEN n.group_scope_id IS NULL THEN p.audit_enabled ELSE b.audit_enabled END audit_enabled,"
            + " CASE WHEN n.group_scope_id IS NULL THEN p.validated ELSE n.validated END validated,"
            + " CASE WHEN n.group_scope_id IS NULL THEN p.policy_version ELSE b.policy_version END policy_version"
            + " FROM dual LEFT JOIN qd_es_node n ON n.node_code=? AND n.button_code=?"
            + " LEFT JOIN qd_es_org_control c ON c.pk_group=? AND c.pk_org=?"
            + " LEFT JOIN qd_es_policy p ON p.pk_group=? AND p.pk_org=? AND p.node_code=n.node_code AND p.button_code=n.button_code"
            + " LEFT JOIN qd_es_bd_control g ON g.pk_group=?"
            + " LEFT JOIN qd_es_bd_policy b ON b.pk_group=? AND b.scope_id=n.group_scope_id",
            parameters(node, button, groupId, orgId, groupId, orgId, groupId, groupId));
    }

    /** 已由服务端核实所属组织的基础档案，按所属组织和正式动作读取配置。 */
    public Map<String,Object> loadOwnerBasePolicyWithControl(String groupId, String ownerOrg, String node, String button)
            throws DAOException {
        if (groupId.equals(ownerOrg)) { return loadPolicyWithControl(groupId, ownerOrg, node, button); }
        return one("SELECT c.master_enabled,c.control_version,CASE WHEN p.scope_id IS NOT NULL THEN 'Y' ELSE 'N' END enabled,"
            + "p.sign_enabled,p.reason_required,p.audit_enabled,n.validated,p.policy_version"
            + " FROM dual JOIN qd_es_node n ON n.node_code=? AND n.button_code=? AND n.group_scope_id IS NOT NULL"
            + " LEFT JOIN qd_es_org_control c ON c.pk_group=? AND c.pk_org=?"
            + " LEFT JOIN qd_es_bd_org_policy p ON p.pk_group=? AND p.pk_org=? AND p.scope_id=n.group_scope_id",
            parameters(node, button, groupId, ownerOrg, groupId, ownerOrg));
    }

    public Map<String,Object> loadOrganizationControl(String groupId, String orgId) throws DAOException {
        return one("SELECT " + QdTableMetadata.columnList("qd_es_org_control")
            + " FROM qd_es_org_control WHERE pk_group=? AND pk_org=?", parameters(groupId, orgId));
    }

    public Map<String,Object> loadGroupControl(String groupId) throws DAOException {
        return one("SELECT " + QdTableMetadata.columnList("qd_es_bd_control")
            + " FROM qd_es_bd_control WHERE pk_group=?", parameters(groupId));
    }

    /** 串行化同一所属组织的基础档案策略变动与业务提交，缺失行不隐含启用策略。 */
    public void lockBaseOwner(String groupId, String ownerOrg) throws DAOException {
        String sql = "SELECT pk_group FROM qd_es_bd_owner_guard WHERE pk_group=? AND pk_owner_org=? FOR UPDATE";
        if (one(sql, parameters(groupId, ownerOrg)) != null) { return; }
        try {
            insert("qd_es_bd_owner_guard", EsignValues.row("pk_group", groupId, "pk_owner_org", ownerOrg));
        } catch (DAOException failure) {
            if (!uniqueViolation(failure) || one(sql, parameters(groupId, ownerOrg)) == null) { throw failure; }
        }
    }

    private static boolean uniqueViolation(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof java.sql.SQLException && ((java.sql.SQLException)current).getErrorCode() == 1) { return true; }
            if (current.getMessage() != null && current.getMessage().contains("ORA-00001")) { return true; }
            if (current.getCause() == current) { break; }
        }
        return false;
    }

    /** 比对组织自己的版本。开关实际变化才写入并推进版本。 */
    public long setOrganizationMaster(String groupId, String orgId, boolean enabled, long expected) throws DAOException {
        lockBaseOwner(groupId, orgId);
        Map<String,Object> current = loadOrganizationControl(groupId, orgId);
        long next = nextControlVersion(current, enabled, expected);
        if (next == expected) { return expected; }
        String now = QdUtcClock.now();
        if (current == null) {
            insert("qd_es_org_control", EsignValues.row("pk_group", groupId, "pk_org", orgId,
                "master_enabled", "Y", "control_version", Long.valueOf(next), "modified_at", now));
        } else {
            int count = dao.executeUpdate("UPDATE qd_es_org_control SET master_enabled=?,control_version=?,modified_at=?"
                + " WHERE pk_group=? AND pk_org=? AND control_version=?",
                parameters(enabled ? "Y" : "N", Long.valueOf(next), now, groupId, orgId, Long.valueOf(expected)));
            if (count != 1) { throw new DAOException("总控已被修改，请重新查询。"); }
        }
        return next;
    }

    /** 集团总控只控制基础档案。 */
    public long setGroupMaster(String groupId, boolean enabled, long expected) throws DAOException {
        lockBaseOwner(groupId, groupId);
        Map<String,Object> current = loadGroupControl(groupId);
        long next = nextControlVersion(current, enabled, expected);
        if (next == expected) { return expected; }
        String now = QdUtcClock.now();
        if (current == null) {
            insert("qd_es_bd_control", EsignValues.row("pk_group", groupId, "master_enabled", "Y",
                "control_version", Long.valueOf(next), "modified_at", now));
        } else {
            int count = dao.executeUpdate("UPDATE qd_es_bd_control SET master_enabled=?,control_version=?,modified_at=?"
                + " WHERE pk_group=? AND control_version=?",
                parameters(enabled ? "Y" : "N", Long.valueOf(next), now, groupId, Long.valueOf(expected)));
            if (count != 1) { throw new DAOException("集团总控已被修改，请重新查询。"); }
        }
        return next;
    }

    private static long nextControlVersion(Map<String,Object> current, boolean enabled, long expected) throws DAOException {
        long version = current == null ? 0L : EsignValues.number(current, "control_version");
        if (expected != version || version < 0L) { throw new DAOException("总控已被修改，请重新查询。"); }
        if (enabled == (current != null && EsignValues.yes(current, "master_enabled"))) { return version; }
        if (version == Long.MAX_VALUE) { throw new DAOException("总控版本已达到上限。"); }
        return version + 1L;
    }

    /** 首次保存受联合主键保护，后续更新须命中读取时的行版本。 */
    public long saveGroupPolicy(String groupId, String scopeId, boolean sign, boolean reason, boolean audit, long expected)
            throws DAOException {
        lockBaseOwner(groupId, groupId);
        if (expected < 0L || expected == Long.MAX_VALUE) { throw new DAOException("集团策略版本无效或已达到上限。"); }
        long next = expected + 1L;
        String signFlag = sign ? "Y" : "N", reasonFlag = reason ? "Y" : "N", auditFlag = audit ? "Y" : "N";
        String now = QdUtcClock.now();
        if (expected == 0L) {
            insert("qd_es_bd_policy", EsignValues.row("pk_group", groupId, "scope_id", scopeId,
                "sign_enabled", signFlag, "reason_required", reasonFlag, "audit_enabled", auditFlag,
                "policy_version", Long.valueOf(next), "modified_at", now));
        } else {
            int count = dao.executeUpdate("UPDATE qd_es_bd_policy SET sign_enabled=?,reason_required=?,audit_enabled=?,"
                + "policy_version=?,modified_at=? WHERE pk_group=? AND scope_id=? AND policy_version=?",
                parameters(signFlag, reasonFlag, auditFlag, Long.valueOf(next), now, groupId, scopeId, Long.valueOf(expected)));
            if (count != 1) { throw new DAOException("集团按钮策略已被修改，请重新查询。"); }
        }
        return next;
    }

    /** 业务单元所属基础档案的按钮策略，未配置时不创建或启用记录。 */
    public long saveOrgBasePolicy(String groupId, String orgId, String scopeId, boolean sign, boolean reason,
                                  boolean audit, long expected) throws DAOException {
        lockBaseOwner(groupId, orgId);
        if (expected < 0L || expected == Long.MAX_VALUE) { throw new DAOException("业务单元基础档案策略版本无效。"); }
        long next = expected + 1L;
        String signFlag = sign ? "Y" : "N", reasonFlag = reason ? "Y" : "N", auditFlag = audit ? "Y" : "N";
        String now = QdUtcClock.now();
        if (expected == 0L) {
            insert("qd_es_bd_org_policy", EsignValues.row("pk_group", groupId, "pk_org", orgId, "scope_id", scopeId,
                "sign_enabled", signFlag, "reason_required", reasonFlag, "audit_enabled", auditFlag,
                "policy_version", Long.valueOf(next), "modified_at", now));
        } else {
            int count = dao.executeUpdate("UPDATE qd_es_bd_org_policy SET sign_enabled=?,reason_required=?,audit_enabled=?,"
                + "policy_version=?,modified_at=? WHERE pk_group=? AND pk_org=? AND scope_id=? AND policy_version=?",
                parameters(signFlag, reasonFlag, auditFlag, Long.valueOf(next), now, groupId, orgId, scopeId, Long.valueOf(expected)));
            if (count != 1) { throw new DAOException("业务单元基础档案策略已被修改，请重新查询。"); }
        }
        return next;
    }

    /** 新增策略使用初始版本；更新既有策略必须匹配 expected_version，防止其他管理员的修改被静默覆盖。 */
    public long savePolicy(Map<String,Object> input) throws DAOException {
        Map<String,Object> values = new LinkedHashMap<String,Object>(input);
        // expected_version 只用于并发比较，不能作为数据库列写入策略表。
        Object expected = values.remove("expected_version");
        if (expected == null) { throw new IllegalArgumentException("expected_version is required"); }
        long version = Long.parseLong(String.valueOf(expected));
        if (version < 0 || version == Long.MAX_VALUE) { throw new DAOException("按钮策略版本无效或已达到上限。"); }
        Map<String,Object> current = loadPolicy(String.valueOf(values.get("pk_group")), String.valueOf(values.get("pk_org")),
            String.valueOf(values.get("node_code")), String.valueOf(values.get("button_code")));
        if (current == null) {
            if (version != 0) { throw new DAOException("QD policy does not exist at expected version"); }
            if (values.get("policy_id") == null) { throw new IllegalArgumentException("policy_id is required for insert"); }
            values.put("policy_version", Long.valueOf(1)); values.put("modified_at", QdUtcClock.now());
            insert("qd_es_policy", values); return 1L;
        }
        if (version == 0) { throw new DAOException("QD policy already exists; reload its current version"); }
        Object policyId = current.get("policy_id"); values.put("policy_id", policyId);
        values.put("policy_version", Long.valueOf(version + 1)); values.put("modified_at", QdUtcClock.now());
        requireOne(update("qd_es_policy", "policy_id", policyId, values, " AND policy_version=?", expected), "optimistic policy update");
        return version + 1L;
    }
    /** 仅更新允许的请求字段，由状态规则所在服务决定转换时机。 */
    public void updateRequest(String requestId, Map<String,Object> changes) throws DAOException {
        for (String field : changes.keySet()) {
            if (!java.util.Arrays.asList("signature_state","business_state","reason","finished_at","user_name","execution_token",
                    "error_code","error_message").contains(field)) {
                throw new IllegalArgumentException("Immutable request field: " + field);
            }
        }
        requireOne(update("qd_es_request", "request_id", requestId, changes, null, null), "update request");
        // 请求真正结束后同步关闭其已有月份定位，相关证据随后才具备归档资格。
        if (changes.get("finished_at") != null) {
            dao.executeUpdate("UPDATE qd_es_locator SET request_closed='Y' WHERE request_id=?", parameters(requestId));
        }
    }
    /** 在期望状态仍匹配时原子更新业务状态，返回影响行数供调用方判断并发竞争。 */
    public int compareAndSetBusinessState(String requestId, String expected, String target) throws DAOException {
        return dao.executeUpdate("UPDATE qd_es_request SET business_state=? WHERE request_id=? AND business_state=?",
            parameters(target, requestId, expected));
    }
    /** 在真实业务成功事务内回填对象主键及编码，并同步相关证据中的标识信息。 */
    public void backfillObject(String requestId, String clientObjectId, String objectPk, String billCode) throws DAOException {
        requireLossless("object_pk", objectPk); requireLossless("bill_code", billCode);
        requireOne(dao.executeUpdate("UPDATE qd_es_object SET object_pk=?,bill_code=? WHERE request_id=? AND client_object_id=?",
            parameters(objectPk, billCode, requestId, clientObjectId)), "backfill object");
        dao.executeUpdate("UPDATE qd_es_signlog SET object_pk=?,bill_code=? WHERE request_id=? AND object_id IN"
            + " (SELECT object_id FROM qd_es_object WHERE request_id=? AND client_object_id=?)",
            parameters(objectPk, billCode, requestId, requestId, clientObjectId));
    }
    /** 按固定表列生成参数化更新，可附加期望版本条件；影响行数检查由调用路径决定。 */
    private int update(String table, String idField, Object id, Map<String,Object> input, String suffix, Object tail) throws DAOException {
        List<String> allowed = QdTableMetadata.columns(table);
        StringBuilder assignments = new StringBuilder(); SQLParameter p = new SQLParameter();
        for (String field : input.keySet()) {
            if (!allowed.contains(field)) { throw new IllegalArgumentException("Unknown update field: " + field); }
            if (field.equals(idField)) {
                if (!String.valueOf(id).equals(String.valueOf(input.get(field)))) { throw new IllegalArgumentException("Cannot change primary key"); }
                continue;
            }
            if (assignments.length() > 0) { assignments.append(','); }
            assignments.append(field).append("=?"); bind(p, field, input.get(field));
        }
        if (assignments.length() == 0) { throw new IllegalArgumentException("Empty update"); }
        p.addParam(id); if (suffix != null) { p.addParam(tail); }
        return dao.executeUpdate("UPDATE " + table + " SET " + assignments + " WHERE " + idField + "=?" + (suffix == null ? "" : suffix), p);
    }
    /** 确保请求与实际事件月份的定位关系存在，供跨月日志及审计详情查找使用。 */
    private void ensureLocator(String requestId, String month) throws DAOException {
        if (!month.matches("[0-9]{4}(0[1-9]|1[0-2])")) { throw new IllegalArgumentException("archive_month is required"); }
        Map<String,Object> request = one("SELECT finished_at FROM qd_es_request WHERE request_id=?", parameters(requestId));
        String closed = request != null && request.get("finished_at") != null ? "Y" : "N";
        dao.executeUpdate("MERGE INTO qd_es_locator d USING (SELECT ? request_id,? archive_month,? request_closed FROM dual) s"
            + " ON (d.request_id=s.request_id AND d.archive_month=s.archive_month) WHEN NOT MATCHED THEN"
            + " INSERT(request_id,archive_month,request_closed) VALUES(s.request_id,s.archive_month,s.request_closed)",
            parameters(requestId, month, closed));
    }
    /** 执行参数化查询并取单行，以统一的小写字段键返回，未查到时返回空值。 */
    public Map<String,Object> one(String sql, SQLParameter p) throws DAOException {
        @SuppressWarnings("unchecked") Map<String,Object> result = (Map<String,Object>) dao.executeQuery(sql, p, new MapProcessor());
        return normalize(result);
    }
    /** 执行参数化查询并统一结果行字段键，DAO 异常向外传播。 */
    public List<Map<String,Object>> list(String sql, SQLParameter p) throws DAOException {
        @SuppressWarnings("unchecked") List<Map<String,Object>> rows = (List<Map<String,Object>>) dao.executeQuery(sql, p, new MapListProcessor());
        List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
        if (rows != null) { for (Map<String,Object> row : rows) { result.add(normalize(row)); } }
        return result;
    }
    /** 把数据库返回行的列名统一转为小写，供服务和日志代码按固定字段访问。 */
    static Map<String,Object> normalize(Map<String,Object> row) {
        if (row == null) { return null; }
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        for (Map.Entry<String,Object> e : row.entrySet()) { result.put(e.getKey().toLowerCase(Locale.ENGLISH), e.getValue()); }
        return result;
    }
    /** 按传入顺序构造 SQL 参数，与占位符顺序保持一致。 */
    static SQLParameter parameters(Object... values) {
        SQLParameter result = new SQLParameter();
        for (Object value : values) {
            if (value == null) { result.addNullParam(Types.VARCHAR); }
            else { result.addParam(value); }
        }
        return result;
    }
    /** 写入前验证普通文本和 CLOB 均可无损编码，随后按原生 NC 参数类型绑定。 */
    private void bind(SQLParameter p, String field, Object value) throws DAOException {
        if (value instanceof String || (value != null && QdTableMetadata.isClob(field))) {
            requireLossless(field, String.valueOf(value));
        }
        if (QdTableMetadata.isClob(field)) {
            if (value == null) { p.addNullParam(Types.CLOB); } else { p.addClobParam(String.valueOf(value)); }
        } else if (value == null) {
            p.addNullParam(Types.VARCHAR);
        } else {
            p.addParam(value);
        }
    }
    /** 要求数据库操作恰好影响一行，零行或多行视为并发或数据异常。 */
    static void requireOne(int count, String operation) throws DAOException {
        if (count != 1) { throw new DAOException("QD " + operation + " affected " + count + " rows; expected 1"); }
    }
}
