package nc.bs.uapbd.qd.esign.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.core.RequestRules;
import nc.bs.uapbd.qd.esign.core.BankAccountAuditObjects;
import nc.bs.uapbd.qd.esign.core.BankAccountRoute;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;
import nc.vo.uapbd.qd.esign.MaterialCancelAssignmentResult;
import nc.vo.uapbd.qd.esign.CustomerAssignmentResult;
import nc.vo.uapbd.qd.esign.SupplierAssignmentResult;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;

/** 仅恢复已经结束的持久化结果；历史查询不认领授权、不加行锁，也不执行业务。 */
final class CompletedRequestReader {
    private static final String ARCHIVED = "_qd_archived_result";
    private final QdEsignStore store;
    CompletedRequestReader(QdEsignStore store) { this.store = store; }
    static boolean isArchived(Map<String,Object> row) { return row != null && Boolean.TRUE.equals(row.get(ARCHIVED)); }

    /** 在线请求缺失后按服务器定位目录查原请求，任何未结束、冲突或缺表证据都失败关闭。 */
    Map<String,Object> request(String id) throws DAOException {
        Map<String,Object> found = null;
        for (String month : closedMonths(id)) {
            if (!hasDirectory(month)) { continue; }
            store.requireHistoryTable("qd_es_request", month);
            String table = QdTableMetadata.historyTable("qd_es_request", month);
            Map<String,Object> row = store.one("SELECT " + QdTableMetadata.columnList("qd_es_request")
                + " FROM " + table + " WHERE request_id=? AND archive_month=?", QdEsignStore.parameters(id, month));
            if (row == null) { continue; }
            require(id.equals(row.get("request_id")) && month.equals(row.get("archive_month")), "Archived request location is inconsistent");
            requireCompleted(row);
            if (found != null) { require(equal("qd_es_request", found, row), "Conflicting archived request copies"); }
            else { found = new LinkedHashMap<String,Object>(row); }
        }
        if (found != null) { found.put(ARCHIVED, Boolean.TRUE); }
        return found;
    }

    /** 成功结果对象可以分处在线与多个月表；只接受同请求、同组织且逐字段一致的副本。 */
    List<Map<String,Object>> objects(Map<String,Object> request) throws DAOException {
        require("SUCCESS".equals(request.get("business_state")), "Only successful business results may replay objects");
        return readObjects(request, false);
    }

    List<Map<String,Object>> assignmentObjects(Map<String,Object> request) throws DAOException {
        require(("ASSIGN".equals(request.get("operation_type")) || CustomerAssignmentResult.isRoute(text(request,"node_code"),
                text(request,"button_code"),text(request,"operation_type")) || SupplierAssignmentResult.isRoute(text(request,"node_code"),
                text(request,"button_code"),text(request,"operation_type")) || MaterialCancelAssignmentResult.isRoute(text(request,"node_code"),
                text(request,"button_code"),text(request,"operation_type")))
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(request.get("policy_mode")), "Expected owner assignment result");
        return readObjects(request, true);
    }

    List<Map<String,Object>> pageDeleteObjects(Map<String,Object> request) throws DAOException {
        require(MaterialPageDeleteResult.isRoute(text(request,"node_code"),text(request,"button_code"),
                text(request,"operation_type")) && OperationContext.OWNER_POLICY_PROTOCOL.equals(request.get("policy_mode")),
            "Expected owner material page delete result");
        return readObjects(request, true);
    }

    List<Map<String,Object>> batchObjects(Map<String,Object> request) throws DAOException {
        require(ArchiveBatchResult.isArchiveBatch(text(request,"node_code"),text(request,"button_code"),text(request,"operation_type"))
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(request.get("policy_mode")),"Expected owner archive batch result");
        return readObjects(request,true);
    }

    List<Map<String,Object>> sapplyBatchObjects(Map<String,Object> request) throws DAOException {
        require("40080801".equals(request.get("node_code")) && "SAPPLY_BATCH".equals(request.get("policy_mode")),
            "Expected outbound-request batch result");
        return readObjects(request, true);
    }

    private List<Map<String,Object>> readObjects(Map<String,Object> request, boolean assignment) throws DAOException {
        requireCompleted(request);
        String id = text(request, "request_id");
        List<String> months = closedMonths(id);
        require(!months.isEmpty(), "Completed request has no month locator");
        Map<String,Map<String,Object>> found = new LinkedHashMap<String,Map<String,Object>>();
        addObjects(found, store.loadObjects(id), request, months);
        for (String month : months) {
            if (!hasDirectory(month)) { continue; }
            store.requireHistoryTable("qd_es_object", month);
            String table = QdTableMetadata.historyTable("qd_es_object", month);
            List<Map<String,Object>> rows = store.list("SELECT " + QdTableMetadata.columnList("qd_es_object")
                + " FROM " + table + " WHERE request_id=? AND archive_month=? ORDER BY object_id", QdEsignStore.parameters(id, month));
            for (Map<String,Object> row : rows) { require(month.equals(row.get("archive_month")), "Archived object location is inconsistent"); }
            addObjects(found, rows, request, months);
        }
        require(!found.isEmpty(), "Completed request object evidence is missing");
        Set<String> clients = new LinkedHashSet<String>();
        for (Map<String,Object> row : found.values()) {
            require(clients.add(text(row,"client_object_id")), "Conflicting client object identities");
            Object parent = row.get("parent_object_id");
            require(parent == null || found.containsKey(String.valueOf(parent)), "Completed result parent object is missing");
        }
        if (OperationContext.OWNER_POLICY_PROTOCOL.equals(request.get("policy_mode"))) {
            Object expectedCount = request.get("owner_object_count");
            require(expectedCount instanceof Number && ((Number)expectedCount).intValue() == found.size(),
                "Completed owner-policy object count is incomplete");
            boolean bank=BankAccountRoute.matches(optional(request,"node_code"),optional(request,"button_code"),optional(request,"operation_type"));
            Set<String> signed = new LinkedHashSet<String>(), audited = new LinkedHashSet<String>();
            for (Map.Entry<String,Map<String,Object>> object : found.entrySet()) {
                Map<String,Object> row = object.getValue();
                if ("SUCCESS".equals(request.get("signature_state")) && "Y".equals(row.get("owner_sign_required"))) {
                    signed.add(object.getKey());
                }
                boolean orgBatchPartial=ArchiveBatchResult.isPartnerBatchUpdate(optional(request,"node_code"),optional(request,"button_code"),
                    optional(request,"operation_type")) && text(row,"client_object_id").startsWith("orgbulk:")
                    && "PARTIAL".equals(row.get("unit_state"));
                boolean committed = assignment ? "SUCCESS".equals(row.get("unit_state")) || orgBatchPartial
                    : "SUCCESS".equals(request.get("business_state"));
                if (committed && "Y".equals(row.get("owner_audit_enabled"))) { audited.add(object.getKey()); }
            }
            Set<String> signatureProofs=proofObjects(request,months,"qd_es_signlog"),auditProofs=proofObjects(request,months,"qd_es_audit");
            if(bank) {
                try { BankAccountAuditObjects.requireProofs(new ArrayList<Map<String,Object>>(found.values()),
                    "SUCCESS".equals(request.get("signature_state")),"SUCCESS".equals(request.get("business_state")),signatureProofs,auditProofs); }
                catch(nc.vo.pub.BusinessException failure) {
                    DAOException problem=new DAOException("Bank account proof scope is inconsistent");problem.initCause(failure);throw problem;
                }
            } else require(signed.equals(signatureProofs) && audited.equals(auditProofs),
                "Completed owner-policy result does not match per-object evidence");
        } else if ("SAPPLY_BATCH".equals(request.get("policy_mode"))) {
            Object expectedCount = request.get("owner_object_count");
            require(expectedCount instanceof Number && ((Number)expectedCount).intValue() == found.size(),
                "Completed outbound-request batch object count is incomplete");
            if ("SUCCESS".equals(request.get("signature_state"))) {
                require(found.keySet().equals(proofObjects(request,months,"qd_es_signlog")),
                    "Completed outbound-request batch does not match signed objects");
            } else {
                Set<String> committed = new LinkedHashSet<String>();
                for (Map.Entry<String,Map<String,Object>> item : found.entrySet()) {
                    if ("SUCCESS".equals(item.getValue().get("unit_state"))) committed.add(item.getKey());
                }
                require(committed.equals(proofObjects(request,months,"qd_es_audit")),
                    "Completed outbound-request batch does not match audited objects");
            }
        } else {
            String proof = "SUCCESS".equals(request.get("signature_state")) ? "qd_es_signlog" : "qd_es_audit";
            require(found.keySet().equals(proofObjects(request, months, proof)), "Completed object set does not match committed evidence");
        }
        return new ArrayList<Map<String,Object>>(found.values());
    }

    private Set<String> proofObjects(Map<String,Object> request, List<String> months, String base) throws DAOException {
        Set<String> result = new LinkedHashSet<String>();
        addProof(result, store.list("SELECT object_id FROM " + base
            + " WHERE request_id=? AND pk_group=? AND pk_org=? AND result_code='SUCCESS'",
            QdEsignStore.parameters(request.get("request_id"),request.get("pk_group"),request.get("pk_org"))));
        for (String month : months) {
            if (!hasDirectory(month)) { continue; }
            store.requireHistoryTable(base, month);
            addProof(result, store.list("SELECT object_id FROM " + QdTableMetadata.historyTable(base,month)
                + " WHERE request_id=? AND pk_group=? AND pk_org=? AND result_code='SUCCESS' AND archive_month=?",
                QdEsignStore.parameters(request.get("request_id"),request.get("pk_group"),request.get("pk_org"),month)));
        }
        return result;
    }
    private static void addProof(Set<String> ids, List<Map<String,Object>> rows) throws DAOException {
        for (Map<String,Object> row : rows) { ids.add(text(row,"object_id")); }
    }

    private void addObjects(Map<String,Map<String,Object>> found, List<Map<String,Object>> rows,
            Map<String,Object> request, List<String> months) throws DAOException {
        for (Map<String,Object> row : rows) {
            String id = text(row,"object_id");
            text(row,"client_object_id"); text(row,"object_type");
            require(text(request,"request_id").equals(row.get("request_id"))
                && text(request,"pk_group").equals(row.get("pk_group"))
                && text(request,"pk_org").equals(row.get("pk_org"))
                && months.contains(text(row,"archive_month")), "Completed object scope is inconsistent");
            Map<String,Object> old = found.get(id);
            require(old == null || equal("qd_es_object", old, row), "Conflicting completed object copies");
            if (old == null) { found.put(id, new LinkedHashMap<String,Object>(row)); }
        }
    }

    private List<String> closedMonths(String id) throws DAOException {
        require(id != null && id.matches("[a-fA-F0-9]{32}"), "Invalid completed request identifier");
        Set<String> months = new LinkedHashSet<String>();
        for (Map<String,Object> row : store.list("SELECT archive_month,request_closed FROM qd_es_locator WHERE request_id=? ORDER BY archive_month", QdEsignStore.parameters(id))) {
            String month = text(row,"archive_month");
            QdTableMetadata.historyTable("qd_es_request", month);
            require("Y".equals(row.get("request_closed")), "Request locator is not closed");
            require(months.add(month), "Duplicate completed request locator");
        }
        return new ArrayList<String>(months);
    }
    private boolean hasDirectory(String month) throws DAOException {
        return store.one("SELECT archive_month FROM qd_es_archive WHERE archive_month=?", QdEsignStore.parameters(month)) != null;
    }
    private static void requireCompleted(Map<String,Object> row) throws DAOException {
        require(row != null && RequestRules.terminal(text(row,"business_state")), "Historical request is not terminal");
        text(row,"request_id"); text(row,"user_id"); text(row,"pk_group"); text(row,"pk_org");
        QdUtcClock.parseUtc(text(row,"finished_at"));
        if ("SUCCESS".equals(row.get("business_state"))) {
            require("SUCCESS".equals(row.get("signature_state")) || "NOT_REQUIRED".equals(row.get("signature_state")), "Committed result has inconsistent signature state");
        }
    }
    private static String text(Map<String,Object> row, String field) throws DAOException {
        Object value = row.get(field);
        require(value != null && String.valueOf(value).length() > 0, "Incomplete result field: " + field);
        return String.valueOf(value);
    }
    private static String optional(Map<String,Object> row,String field) {
        Object value=row.get(field);return value==null?null:String.valueOf(value);
    }
    private static boolean equal(String table, Map<String,Object> left, Map<String,Object> right) {
        for (String field : QdTableMetadata.columns(table)) {
            Object a = left.get(field), b = right.get(field);
            if (a == null || b == null) { if (a != b) { return false; } }
            else if (a instanceof Number && b instanceof Number) {
                if (new BigDecimal(String.valueOf(a)).compareTo(new BigDecimal(String.valueOf(b))) != 0) { return false; }
            } else if (!a.equals(b)) { return false; }
        }
        return true;
    }
    private static void require(boolean condition, String message) throws DAOException { if (!condition) { throw new DAOException(message); } }
}
