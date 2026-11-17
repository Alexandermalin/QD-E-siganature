package nc.bs.uapbd.qd.esign.persistence;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.jdbc.framework.SQLParameter;
import nc.vo.uapbd.qd.esign.log.LogKind;
import nc.vo.uapbd.qd.esign.log.LogQuery;

/** 验证在线和历史分支共用的组织授权与参数化筛选。 */
public final class AuditOrganizationQueryTests {
    private AuditOrganizationQueryTests() { }
    public static void run() {
        LogQuery query = new LogQuery(); query.setFromUtc("2026-10-31 15:00:00"); query.setToUtc("2026-11-01 00:00:00");
        query.setOrganizationId("org-990"); query.setUserId("operator-1");
        List<String> authorized = Arrays.asList("org-990", "org-other");
        List<String> nativeScopes = Arrays.asList("group", "org-990");
        check(LogAccess.businessScopes(nativeScopes, "group", false).equals(Collections.singletonList("org-990")),
            "ordinary business log grant excludes the native group key");
        check(LogAccess.businessScopes(Collections.<String>emptyList(), "group", true).equals(Collections.singletonList("group")),
            "group log grant alone does not add any business unit");
        QdLogRepository.validate(query, "group", authorized, false);
        StringBuilder sql = new StringBuilder(); SQLParameter params = new SQLParameter();
        QdLogRepository.appendScope(sql, LogKind.AUDIT, query, "group", authorized, params, false);
        sql.append(" UNION ALL ");
        QdLogRepository.appendScope(sql, LogKind.AUDIT, query, "group", authorized, params, false);
        check(count(sql.toString(), " AND pk_org=?") == 2, "organization constrains online and history branches");
        check(Collections.frequency(params.getParameters(), "org-990") == 4, "selected organization and authorized scope bound per branch");
        check(Collections.frequency(params.getParameters(), "operator-1") == 2, "operator bound per branch");
        query.setOrganizationId("unauthorized-org");
        try { QdLogRepository.validate(query, "group", authorized, false); throw new AssertionError("Unauthorized organization accepted"); }
        catch (IllegalArgumentException expected) { /* expected */ }
        query.setOrganizationId("org-990");
        StringBuilder signatureSql=new StringBuilder();SQLParameter signatureParams=new SQLParameter();
        QdLogRepository.appendScope(signatureSql,LogKind.SIGNATURE,query,"group",authorized,signatureParams,false);
        check(signatureSql.toString().contains(" AND pk_org=?") && signatureParams.getParameters().contains("org-990"), "signature uses selected organization");
        List<Map<String,Object>> logs=new ArrayList<Map<String,Object>>();
        Map<String,Object> found=new LinkedHashMap<String,Object>();found.put("pk_org","org-990 ");found.put("policy_owner_org","owner-A");
        found.put("target_org","target-C");found.put("event_id","e1");logs.add(found);
        Map<String,Object> missing=new LinkedHashMap<String,Object>();missing.put("pk_org","unknown");missing.put("event_id","e2");logs.add(missing);
        Map<String,String> names=new LinkedHashMap<String,String>();names.put("org-990","测试业务单元1");
        names.put("owner-A","所属业务单元A");names.put("target-C","采购组织C");
        List<Map<String,Object>> translated=QdLogRepository.addOrganizationNames(logs,names);
        check(translated.size()==2 && "测试业务单元1".equals(translated.get(0).get("qd_org_name")), "organization name mapped to result");
        check("org-990 ".equals(translated.get(0).get("pk_org")) && translated.get(1).get("qd_org_name")==null,
            "raw keys and unresolved logs remain intact");
        check("所属业务单元A".equals(translated.get(0).get("qd_owner_org_name"))
            && "采购组织C".equals(translated.get(0).get("qd_target_org_name")),
            "actual operation, policy owner and target organization remain distinct");
        check("未记录".equals(translated.get(1).get("qd_owner_org_name")),
            "historical owner is not inferred from the archive's current owner");
        System.out.println("PASS AuditOrganizationQueryTests (authorization and online/history filter bindings)");
    }
    private static int count(String source, String part) { int n = 0, at = 0; while ((at = source.indexOf(part, at)) >= 0) { n++; at += part.length(); } return n; }
    private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
