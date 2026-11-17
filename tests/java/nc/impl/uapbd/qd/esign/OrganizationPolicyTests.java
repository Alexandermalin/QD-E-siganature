package nc.impl.uapbd.qd.esign;

import static nc.bs.uapbd.qd.esign.core.EsignValues.row;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.bs.dao.DAOException;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ButtonPolicyChange;
import nc.vo.uapbd.qd.esign.ButtonPolicyRow;
import nc.vo.uapbd.qd.esign.OrganizationPolicyView;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/** 使用内存仓储验证组织策略逻辑和事务异常边界。 */
public final class OrganizationPolicyTests {
    private static final String GROUP = "GROUP";
    private static final String FIRST = "ORG_A";
    private static final String SECOND = "ORG_B";
    private static final String NODE = "10140MAO";

    public static void run() throws Exception {
        NativeBpPolicyResolver.requireContext("GROUP","GROUP","DS");
        for(String[] context:new String[][]{{"GROUP","OTHER","DS"},{"GROUP",null,"DS"},
                {"GROUP","GROUP",null},{"GROUP","GROUP"," "},{null,"GROUP","DS"},{" GROUP"," GROUP","DS"}}) {
            try { NativeBpPolicyResolver.requireContext(context[0],context[1],context[2]);
                throw new AssertionError("Untrusted native BP policy context was accepted");
            } catch(BusinessException expected) { }
        }
        NativePolicyStore nativeStore=new NativePolicyStore();
        for(boolean sign:new boolean[]{false,true})for(boolean audit:new boolean[]{false,true}) {
            nativeStore.policy=row("master_enabled","Y","enabled","Y",
                    "sign_enabled",sign?"Y":"N","audit_enabled",audit?"Y":"N");
            check(NativeBpPolicyResolver.isControlled(nativeStore,"GROUP","ORG_A","40080801","Save")== (sign || audit),
                    "An enabled native BP policy is checked even without a node mapping");
            nativeStore.policy.put("master_enabled","N");
            check(!NativeBpPolicyResolver.isControlled(nativeStore,"GROUP","ORG_A","40080801","Save"),
                    "Disabled master preserves the native path");
        }
        nativeStore.policy=row("master_enabled","Y","enabled","N","sign_enabled","Y");
        check(!NativeBpPolicyResolver.isControlled(nativeStore,"GROUP","ORG_A","40080801","Save"),
                "Disabled button policy preserves the native path");
        nativeStore.mapping=row("group_scope_id","BASE_SCOPE");
        try { NativeBpPolicyResolver.isControlled(nativeStore,"GROUP","ORG_A","40080801","Save");
            throw new AssertionError("An unexpected base-data mapping was accepted");
        } catch(BusinessException expected) { }
        nativeStore.mapping=null;nativeStore.policy=null;
        try { NativeBpPolicyResolver.isControlled(nativeStore,"GROUP","ORG_A","40080801","Save");
            throw new AssertionError("An unavailable policy query was treated as disabled");
        } catch(BusinessException expected) { }
        MemoryStore store = new MemoryStore();
        OrganizationPolicyView initial = PolicyServiceImpl.readOrganization(store, GROUP, FIRST, true, false);
        check(initial.getRows().size() == 243 && initial.canSavePolicies() && !initial.canSaveMaster(),
            "Full catalog and independent management permissions");
        check(!initial.isMasterEnabled() && initial.getControlVersion() == 0L, "Absent control defaults off");
        ButtonPolicyRow unmapped = initial.getRows().get(242);
        ButtonPolicyRow unconfigured = initial.getRows().get(0);
        check(!unmapped.isConfigurable() && unconfigured.isConfigurable(), "Unmapped rows are returned read-only");
        check(!unconfigured.isSignatureEnabled() && !unconfigured.isReasonRequired() && !unconfigured.isAuditEnabled()
            && unconfigured.getVersion() == 0L, "Unconfigured verified row defaults off");

        ButtonPolicyChange sign = new ButtonPolicyChange("sign", true, true, false, 0L);
        ButtonPolicyChange audit = new ButtonPolicyChange("audit", false, true, true, 0L);
        reject(store, Arrays.asList(sign, new ButtonPolicyChange("row-242", true, false, false, 0L)));
        reject(store, Arrays.asList(sign, sign));
        List<ButtonPolicyRow> saved = PolicyServiceImpl.saveChanges(store, GROUP, FIRST, Arrays.asList(sign, audit));
        check(saved.size() == 2 && saved.get(0).getVersion() == 1L && saved.get(1).getVersion() == 1L,
            "Batch returns all updated row versions");
        reject(store, Arrays.asList(new ButtonPolicyChange("audit", false, false, false, 1L), sign));
        check(!decision(store, FIRST, "Save").isControlled() && decision(store, FIRST, "Save").getConfiguredSignature(),
            "Closed organization retains its configuration");
        check(!decision(store, SECOND, "Save").getConfiguredSignature(), "Button rows are organization-specific");

        check(store.setOrganizationMaster(GROUP, FIRST, false, 0L) == 0L && store.controls.isEmpty(),
            "Saving the default switch keeps its epoch");
        check(store.setOrganizationMaster(GROUP, FIRST, true, 0L) == 1L, "First enable creates epoch one");
        check(store.setOrganizationMaster(GROUP, FIRST, true, 1L) == 1L, "Unchanged switch preserves epoch");
        try {
            store.setOrganizationMaster(GROUP, FIRST, false, 0L);
            throw new AssertionError("Stale master change accepted");
        } catch (DAOException expected) {
            check(expected.getMessage().contains("重新查询"), "Stale master change prompts requery");
        }
        PolicyDecision issued = decision(store, FIRST, "Save");
        check(issued.isSignatureRequired() && issued.isReasonRequired() && !issued.isAuditEnabled(), "Sign-only effective");
        PolicyDecision auditOnly = decision(store, FIRST, "Audit");
        check(!auditOnly.isSignatureRequired() && !auditOnly.isReasonRequired() && auditOnly.isAuditEnabled(),
            "Audit-only ignores the configured reason requirement");
        check(!decision(store, SECOND, "Save").isMasterEnabled(), "Enabling the first org leaves the second org off");
        store.controls.put(FIRST, row("master_enabled", "N", "control_version", Long.valueOf(2L)));
        check(!decision(store, FIRST, "Save").isControlled(), "Closed master pauses policy");
        store.controls.put(FIRST, row("master_enabled", "Y", "control_version", Long.valueOf(3L)));
        PolicyDecision reopened = decision(store, FIRST, "Save");
        check(reopened.isSignatureRequired() && reopened.getVersion() == issued.getVersion()
            && reopened.getControlVersion() != issued.getControlVersion(), "Reopened epoch cannot match old authorization");

        store.conflictButton = "Audit";
        try {
            PolicyServiceImpl.saveChanges(store, GROUP, FIRST, Arrays.asList(
                new ButtonPolicyChange("sign", true, false, true, 1L), new ButtonPolicyChange("audit", false, false, false, 1L)));
            throw new AssertionError("Concurrent write conflict swallowed");
        } catch (DAOException expected) {
            check(store.writes == 3 && "Concurrent write".equals(expected.getMessage()), "Write conflict reaches CMT");
        }
        try {
            new PolicyServiceImpl().setMasterEnabled(true);
            throw new AssertionError("Legacy group write accepted");
        } catch (BusinessException expected) {
            check(expected.getMessage().contains("升级") && expected.getMessage().contains("业务单元"), "Legacy client upgrade notice");
        }
        checkGroupPolicy();
        checkSalesPilotPolicy();
        System.out.println("PASS OrganizationPolicyTests (offline policies and epochs; Oracle rollback pending)");
    }

    private static void checkSalesPilotPolicy() throws Exception {
        ButtonPolicyChange enabled = new ButtonPolicyChange(SaleOrderPilotScope.SCOPE_ID, true, true, true, 0L);
        PolicyServiceImpl.requirePilotPolicy(SaleOrderPilotScope.GROUP_ID, SaleOrderPilotScope.ORG_ID, enabled);
        try {
            PolicyServiceImpl.requirePilotPolicy(SaleOrderPilotScope.GROUP_ID, "OTHER", enabled);
            throw new AssertionError("Other organizations must not enable the sales pilot");
        } catch (BusinessException expected) { }
        PolicyServiceImpl.requirePilotPolicy(SaleOrderPilotScope.GROUP_ID, "OTHER",
            new ButtonPolicyChange(SaleOrderPilotScope.SCOPE_ID, false, false, false, 0L));
    }

    private static void checkGroupPolicy() throws Exception {
        MemoryStore store = new MemoryStore();
        store.catalog.get("sign").put("group_scope_id", "sign");
        store.catalog.get("audit").put("group_scope_id", "audit");
        store.catalog.get("row-242").put("group_scope_id", "row-242");
        store.catalog.get("sign").put("configurable", "Y");
        store.catalog.get("audit").put("configurable", "Y");

        OrganizationPolicyView group = PolicyServiceImpl.readGroup(store, GROUP, true, false);
        check(GROUP.equals(group.getOrgId()) && group.getRows().size() == 3 && !group.isMasterEnabled(),
            "Group view shows only formal base-data scopes and defaults off");
        OrganizationPolicyView unit = PolicyServiceImpl.readOrganization(store, GROUP, FIRST, true, true);
        check(unit.getRows().size() == 243, "Organization view includes formal base-data actions");
        check(!unit.getRows().get(0).isSignatureEnabled() && !unit.getRows().get(0).isAuditEnabled(),
            "New organization base-data policies default off");
        ButtonPolicyChange sign = new ButtonPolicyChange("sign", true, true, false, 0L);
        check(PolicyServiceImpl.saveChanges(store, GROUP, FIRST, Arrays.asList(sign)).get(0).getVersion() == 1L,
            "Organization base-data policy is stored by owner and canonical action");
        check(PolicyServiceImpl.readOrganization(store, GROUP, FIRST, true, true).getRows().get(0).isSignatureEnabled()
            && !PolicyServiceImpl.readOrganization(store, GROUP, SECOND, true, true).getRows().get(0).isSignatureEnabled(),
            "Organization base policies are isolated by owner");
        reject(store, Arrays.asList(new ButtonPolicyChange("row-242", true, false, false, 0L)));
        try {
            PolicyServiceImpl.saveGroupChanges(store, GROUP, Arrays.asList(sign,
                new ButtonPolicyChange("row-242", false, false, true, 0L)));
            throw new AssertionError("Unverified group scope accepted");
        } catch (BusinessException expected) {
            check(store.groupPolicies.isEmpty(), "Unverified group scope rejects the batch before writes");
        }
        check(PolicyServiceImpl.saveGroupChanges(store, GROUP, Arrays.asList(sign)).get(0).getVersion() == 1L,
            "Verified group scope is stored by group and formal scope");
        try {
            PolicyServiceImpl.saveGroupChanges(store, GROUP, Arrays.asList(sign));
            throw new AssertionError("Stale group policy write accepted");
        } catch (BusinessException expected) {
            check(expected.getMessage().contains("重新查询"), "Stale group policy requires a fresh version");
        }
        check(PolicyServiceImpl.readGroup(store, GROUP, true, false).getRows().get(0).isSignatureEnabled()
            && !PolicyServiceImpl.readGroup(store, "OTHER", true, false).getRows().get(0).isSignatureEnabled(),
            "Group policy is isolated by the authenticated group");
    }

    private static void reject(MemoryStore store, List<ButtonPolicyChange> changes) throws Exception {
        int writes = store.writes;
        try {
            PolicyServiceImpl.saveChanges(store, GROUP, FIRST, changes);
            throw new AssertionError("Invalid batch accepted");
        } catch (BusinessException expected) {
            check(writes == store.writes, "Invalid batch rejected before first write");
        }
    }

    private static PolicyDecision decision(MemoryStore store, String org, String button) throws BusinessException {
        return PolicyServiceImpl.resolve(store, GROUP, org, NODE, button);
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static final class NativePolicyStore extends QdEsignStore {
        Map<String,Object> mapping,policy;
        @Override public Map<String,Object> loadNodeByAction(String node,String button){return mapping;}
        @Override public Map<String,Object> one(String sql,SQLParameter parameters){
            check(sql.contains("p.node_code=?") && sql.contains("p.button_code=?") && !sql.contains("qd_es_node")
                    && parameters!=null,"Native policy lookup must not depend on a node mapping");
            return policy;
        }
    }

    private static final class MemoryStore extends QdEsignStore {
        final Map<String,Map<String,Object>> catalog = new LinkedHashMap<String,Map<String,Object>>();
        final Map<String,Map<String,Object>> controls = new LinkedHashMap<String,Map<String,Object>>();
        final Map<List<String>,Map<String,Object>> policies = new LinkedHashMap<List<String>,Map<String,Object>>();
        final Map<List<String>,Map<String,Object>> groupPolicies = new LinkedHashMap<List<String>,Map<String,Object>>();
        final Map<List<String>,Map<String,Object>> orgBasePolicies = new LinkedHashMap<List<String>,Map<String,Object>>();
        int writes;
        String conflictButton;

        MemoryStore() {
            catalog.put("sign", mapping("sign", "Save"));
            catalog.put("audit", mapping("audit", "Audit"));
            for (int i = 2; i < 243; i++) {
                String scope = "row-" + i;
                catalog.put(scope, row("scope_id", scope, "node_name", "待映射节点", "button_name", "按钮",
                    "validated", "N", "mapping_state", "UNMAPPED"));
            }
        }

        private Map<String,Object> mapping(String scope, String button) {
            return row("scope_id", scope, "node_name", "物料", "node_code", NODE, "button_name", button,
                "button_code", button, "validated", "Y", "mapping_state", "PILOT", "adapter_class", "Adapter",
                "operation_types", "UPDATE", "object_type", "MATERIAL");
        }

        @Override public Map<String,Object> loadNode(String scope) { return catalog.get(scope); }
        @Override public void lockBaseOwner(String group, String org) { }
        @Override public Map<String,Object> loadOrganizationControl(String group, String org) {
            check(GROUP.equals(group), "Expected authorized group");
            return controls.get(org);
        }
        @Override public void insert(String table, Map<String,Object> input) {
            check("qd_es_org_control".equals(table) && GROUP.equals(input.get("pk_group")), "Scoped master insert");
            controls.put((String) input.get("pk_org"), input);
        }
        @Override public Map<String,Object> loadPolicy(String group, String org, String node, String button) {
            return policies.get(Arrays.asList(group, org, node, button));
        }
        @Override public Map<String,Object> loadPolicyWithControl(String group, String org, String node, String button) {
            Map<String,Object> result = new LinkedHashMap<String,Object>();
            Map<String,Object> control = loadOrganizationControl(group, org);
            Map<String,Object> policy = loadPolicy(group, org, node, button);
            if (control != null) { result.putAll(control); }
            if (policy != null) { result.putAll(policy); }
            return result;
        }
        @Override public List<Map<String,Object>> listOrganizationPolicies(String group, String org) {
            List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
            for (Map<String,Object> mapping : catalog.values()) {
                if (mapping.get("group_scope_id") != null) {
                    Map<String,Object> base = loadOrgBasePolicyEntry(group, org, (String)mapping.get("scope_id"));
                    if (base != null) { result.add(base); }
                    continue;
                }
                Map<String,Object> policy = loadPolicy(group, org, (String) mapping.get("node_code"),
                    (String) mapping.get("button_code"));
                Map<String,Object> entry = new LinkedHashMap<String,Object>(mapping);
                entry.putAll(policy == null ? Collections.<String,Object>emptyMap() : policy);
                result.add(entry);
            }
            return result;
        }
        @Override public Map<String,Object> loadOrgBasePolicyEntry(String group, String org, String scope) {
            Map<String,Object> mapping = catalog.get(scope);
            if (mapping == null || !scope.equals(mapping.get("group_scope_id"))) { return null; }
            Map<String,Object> entry = new LinkedHashMap<String,Object>(mapping);
            entry.put("base_scope", "Y");
            Map<String,Object> policy = orgBasePolicies.get(Arrays.asList(group, org, scope));
            if (policy != null) { entry.putAll(policy); }
            return entry;
        }
        @Override public long saveOrgBasePolicy(String group, String org, String scope, boolean sign, boolean reason,
                                                boolean audit, long expected) {
            long version = expected + 1L;
            orgBasePolicies.put(Arrays.asList(group, org, scope), row("sign_enabled", sign ? "Y" : "N",
                "reason_required", reason ? "Y" : "N", "audit_enabled", audit ? "Y" : "N",
                "policy_version", Long.valueOf(version)));
            writes++;
            return version;
        }
        @Override public Map<String,Object> loadGroupControl(String group) { return null; }
        @Override public List<Map<String,Object>> listGroupPolicies(String group) {
            List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
            for (String scope : catalog.keySet()) {
                Map<String,Object> entry = loadGroupPolicyEntry(group, scope);
                if (entry != null) { result.add(entry); }
            }
            return result;
        }
        @Override public Map<String,Object> loadGroupPolicyEntry(String group, String scope) {
            Map<String,Object> mapping = catalog.get(scope);
            if (mapping == null || !scope.equals(mapping.get("group_scope_id"))) { return null; }
            Map<String,Object> entry = new LinkedHashMap<String,Object>(mapping);
            Map<String,Object> policy = groupPolicies.get(Arrays.asList(group, scope));
            if (policy != null) { entry.putAll(policy); }
            return entry;
        }
        @Override public long saveGroupPolicy(String group, String scope, boolean sign, boolean reason,
                                               boolean audit, long expected) {
            long version = expected + 1L;
            groupPolicies.put(Arrays.asList(group, scope), row("sign_enabled", sign ? "Y" : "N",
                "reason_required", reason ? "Y" : "N", "audit_enabled", audit ? "Y" : "N",
                "policy_version", Long.valueOf(version)));
            return version;
        }
        @Override public long savePolicy(Map<String,Object> input) throws DAOException {
            String button = (String) input.get("button_code");
            if (button.equals(conflictButton)) { throw new DAOException("Concurrent write"); }
            long version = ((Long) input.get("expected_version")).longValue() + 1L;
            Map<String,Object> saved = new LinkedHashMap<String,Object>(input);
            saved.put("policy_version", Long.valueOf(version));
            policies.put(Arrays.asList((String) input.get("pk_group"), (String) input.get("pk_org"),
                (String) input.get("node_code"), button), saved);
            writes++;
            return version;
        }
    }
}
