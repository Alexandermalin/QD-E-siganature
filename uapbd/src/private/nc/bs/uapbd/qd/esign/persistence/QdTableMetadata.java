package nc.bs.uapbd.qd.esign.persistence;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 自建表列名、主键与历史表的固定元数据白名单；SQL 标识符不能直接取自 RPC 客户端输入。 */
public final class QdTableMetadata {
    /** COLUMNS：自建表名到有序列清单的固定映射。 */
    private static final Map<String,List<String>> COLUMNS = new LinkedHashMap<String,List<String>>();
    // 集中登记固定自建表结构，字段顺序必须与部署 DDL 契约一致。
    static {
        define("qd_es_control", "pk_group master_enabled policy_version modified_at");
        define("qd_es_org_control", "pk_group pk_org master_enabled control_version modified_at");
        define("qd_es_bd_control", "pk_group master_enabled control_version modified_at");
        define("qd_es_bd_policy", "pk_group scope_id sign_enabled reason_required audit_enabled policy_version modified_at");
        define("qd_es_bd_org_policy", "pk_group pk_org scope_id sign_enabled reason_required audit_enabled policy_version modified_at");
        define("qd_es_bd_owner_guard", "pk_group pk_owner_org");
        define("qd_es_node", "scope_id node_code node_name button_code button_name adapter_class mapping_state operation_types object_type validated group_scope_id");
        define("qd_es_policy", "policy_id pk_group pk_org node_code button_code sign_enabled reason_required audit_enabled enabled validated policy_version modified_at");
        define("qd_es_request", "request_id batch_id user_id user_code user_name pk_group pk_org node_code node_name button_code button_name operation_type object_type reason signature_state business_state execution_token policy_version control_version created_at expires_at finished_at client_ip terminal_name error_code error_message archive_month policy_mode owner_object_count");
        define("qd_es_object", "object_id request_id client_object_id object_type object_pk bill_code parent_object_id relation_type pk_group pk_org created_at archive_month policy_owner_org policy_owner_type policy_scope_id target_org policy_owner_root desired_owner_org owner_policy_version owner_control_version owner_sign_required owner_reason_required owner_audit_enabled unit_state unit_error_code unit_finished_at");
        define("qd_es_batch_page", "record_id request_id object_id row_kind item_key target_org result_state failure_stage page_count evidence_hash tab_code pk_group pk_org event_time archive_month");
        define("qd_es_signlog", "event_id request_id batch_id attempt_id object_id user_id user_code user_name pk_group pk_org node_code node_name button_code button_name operation_type object_type object_pk bill_code result_code lock_result reason client_ip terminal_name event_time archive_month policy_owner_org policy_owner_type policy_scope_id target_org");
        define("qd_es_audit", "audit_id request_id batch_id object_id user_id user_code user_name pk_group pk_org node_code node_name button_code button_name operation_type object_type object_pk bill_code result_code reason event_time archive_month client_ip terminal_name policy_owner_org policy_owner_type policy_scope_id target_org");
        define("qd_es_change", "change_id audit_id request_id object_id entity_name row_id parent_row_id field_code field_name data_type change_type before_value after_value pk_group pk_org event_time archive_month");
        define("qd_es_snapshot", "snapshot_id audit_id request_id object_id snapshot_phase entity_name row_id parent_row_id payload payload_hash pk_group pk_org event_time archive_month");
        define("qd_es_security", "event_id source_system source_event_id source_version user_id user_code user_name pk_group pk_org event_type result_code client_ip terminal_name event_time archive_month");
        define("qd_es_security_key", "event_id source_system source_event_id source_version");
        define("qd_es_sync_cursor", "pk_group source_name last_source_ts last_source_pk cycle_started_at cycle_completed_at modified_at");
        define("qd_es_locator", "request_id archive_month request_closed");
        define("qd_es_archive", "archive_month status started_at finished_at last_error");
        define("qd_es_job", "job_id job_type job_key status attempt_count next_retry_at started_at finished_at last_error");
        define("qd_es_fault", "fault_id request_id pk_group pk_org fault_type error_code error_message event_time resolved");
    }
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private QdTableMetadata() { }
    /** 注册固定表的有序字段清单，供 SQL 构造与结构契约核对共同使用。 */
    private static void define(String table, String fields) {
        COLUMNS.put(table, Collections.unmodifiableList(Arrays.asList(fields.split(" "))));
    }
    /** 查找白名单表的有序列集合，未知表名直接拒绝。 */
    public static List<String> columns(String table) {
        List<String> result = COLUMNS.get(table);
        if (result == null) { throw new IllegalArgumentException("Unknown QD table: " + table); }
        return result;
    }
    /** 将固定列清单连接为 SQL 投影，避免使用未经审核的客户端列名。 */
    public static String columnList(String table) {
        StringBuilder b = new StringBuilder();
        for (String c : columns(table)) { if (b.length() > 0) { b.append(','); } b.append(c); }
        return b.toString();
    }
    /** 识别需要按大字段方式绑定或比较的固定列。 */
    public static boolean isClob(String column) {
        return "payload".equals(column) || "before_value".equals(column) || "after_value".equals(column);
    }
    /** 返回固定表的主键列，未知表或无约定时拒绝使用。 */
    public static String primaryKey(String table) { return columns(table).get(0); }
    /** 判断表是否属于允许移动到月度历史表的固定日志集合。 */
    public static boolean isArchivable(String table) {
        return "qd_es_request".equals(table) || "qd_es_object".equals(table) || "qd_es_batch_page".equals(table)
            || "qd_es_signlog".equals(table) || "qd_es_audit".equals(table)
            || "qd_es_change".equals(table) || "qd_es_snapshot".equals(table)
            || "qd_es_security".equals(table);
    }
    /** 校验允许归档的表与六位合法月份后构造历史表名，避免任意 SQL 标识符注入。 */
    public static String historyTable(String table, String month) {
        if (!isArchivable(table) || month == null || !month.matches("[0-9]{4}(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("Invalid history table/month");
        }
        return table + "_h" + month;
    }
}
