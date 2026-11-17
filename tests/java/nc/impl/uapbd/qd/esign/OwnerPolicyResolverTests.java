package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.bs.uapbd.qd.esign.core.RequestRules;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.ObjectPolicyDecision;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteCommand;

/** 归属策略的对象粒度、策略版本和分配关系检查；不连接真实 NC 服务。 */
public final class OwnerPolicyResolverTests {
    private static int checks;
    private OwnerPolicyResolverTests() { }

    public static void run() throws Exception {
        checks = 0;
        FakeStore store = new FakeStore();
        store.add("MG", "G"); store.add("MA", "A");
        store.assigned.add("MG/B"); store.assigned.add("MA/B");
        Map<String,Object> mapping = EsignValues.row("group_scope_id", "SAVE", "validated", "Y");
        store.actionMapping = mapping;
        try { PolicyServiceImpl.resolveLegacy(store, "G", "B", "10140MAO", "Save");
            throw new AssertionError("Legacy base-data lookup accepted"); }
        catch (BusinessException expected) { check(expected.getMessage().contains("升级"), "Legacy base-data lookup requires an upgrade"); }
        store.policy("G", true, false, false, false, 1, 1);
        store.policy("A", true, false, false, true, 2, 3);
        store.policy("B", true, false, false, true, 5, 7);
        check(!resolve(store, mapping, "MG", "G").getPolicy().isAuditEnabled(),
            "Group-owned archive does not inherit B audit");
        check(resolve(store, mapping, "MA", "A").getPolicy().isAuditEnabled(),
            "A-owned archive follows A when edited by B");
        store.policy("G", true, false, false, true, 2, 1);
        store.policy("A", true, false, false, false, 3, 3);
        check(resolve(store, mapping, "MG", "G").getPolicy().isAuditEnabled(),
            "Group audit applies in B regardless of B policy");
        check(!resolve(store, mapping, "MA", "A").getPolicy().isAuditEnabled(),
            "A audit off remains off even when B audit is on");
        store.policy("A", false, true, true, true, 4, 4);
        check(!resolve(store, mapping, "MA", "A").getPolicy().isControlled(), "A master pauses its own policy");

        store.policy("A", true, false, true, false, 5, 5);
        PolicyDecision reasonOnly = resolve(store, mapping, "MA", "A").getPolicy();
        check(!reasonOnly.isSignatureRequired() && reasonOnly.isReasonRequired() && !reasonOnly.isAuditEnabled()
            && reasonOnly.isControlled(), "Owner reason requirement applies independently of signing and audit");
        for (int combination = 0; combination < 8; combination++) {
            boolean sign = (combination & 1) != 0, reason = (combination & 2) != 0, audit = (combination & 4) != 0;
            store.policy("A", true, sign, reason, audit, 5, 5);
            PolicyDecision actual = resolve(store, mapping, "MA", "A").getPolicy();
            check(actual.isSignatureRequired() == sign && actual.isReasonRequired() == reason
                    && actual.isAuditEnabled() == audit && actual.isControlled() == (sign || reason || audit),
                "Independent owner flags combination " + combination);
        }
        check(RequestRules.mayConfirmReason(true, "NOT_REQUIRED", "PENDING")
            && !RequestRules.mayConfirmReason(false, "NOT_REQUIRED", "PENDING")
            && !RequestRules.mayConfirmReason(true, "SUCCESS", "PENDING")
            && !RequestRules.mayConfirmReason(true, "NOT_REQUIRED", "READY")
            && !RequestRules.mayExecute("NOT_REQUIRED", "PENDING")
            && RequestRules.mayExecute("NOT_REQUIRED", "READY"),
            "Only an unconfirmed owner reason can transition to ready without authentication");

        store.policy("G", true, true, true, false, 3, 1);
        store.policy("A", true, false, false, true, 6, 5);
        OperationContext mixed = context("B", Arrays.asList(ref("g", "MG", "G"), ref("a", "MA", "A")));
        SignatureServiceImpl.requireOwnerMapping(mixed,true);
        try { SignatureServiceImpl.requireOwnerMapping(mixed,false);throw new AssertionError("Unregistered owner action bypassed its execution guard"); }
        catch(BusinessException expected) { check(true,"An owner-protocol action cannot fall through to the legacy bypass"); }
        List<ObjectPolicyDecision> items = PolicyServiceImpl.resolveOwnerPolicies(store, "G", mixed, mapping, true);
        PolicyDecision combined = PolicyServiceImpl.aggregateOwnerPolicies(items);
        check(combined.isSignatureRequired() && combined.isReasonRequired() && combined.isAuditEnabled(),
            "A mixed batch asks once for required signature and reason");
        check(store.locked.equals(Arrays.asList("A", "G")), "Owner policy anchors are locked in stable order");
        AuditCaptureScope scope = new AuditCaptureScope(mixed, Collections.singleton("a"));
        try { check(!scope.isEnabledFor("g") && scope.isEnabledFor("a"), "Audit remains per archive"); }
        finally { scope.close(); }
        List<Map<String,Object>> snapshot = new ArrayList<Map<String,Object>>();
        for (ObjectPolicyDecision d : items) snapshot.add(snapshot(d));
        PolicyServiceImpl.verifyOwnerSnapshot(items, snapshot);
        snapshot.get(0).put("owner_control_version", Long.valueOf(99));
        try { PolicyServiceImpl.verifyOwnerSnapshot(items, snapshot); throw new AssertionError("Stale control accepted"); }
        catch (BusinessException expected) { check(true, "Changed control invalidates authorization"); }

        store.assigned.remove("MG/B");
        try { resolve(store, mapping, "MG", "G"); throw new AssertionError("Unassigned archive accepted"); }
        catch (BusinessException expected) { check(true, "Cross-unit operation requires native allocation"); }
        OperationContext create = new OperationContext(null, null, "G", "B", "10140MAO", "物料", "Save", "保存",
            "INSERT", null, Collections.singletonList(new BusinessObjectRef("new", "BD_MATERIAL", null, null, null, "A")),
            OperationContext.OWNER_POLICY_PROTOCOL);
        try { PolicyServiceImpl.resolveOwnerPolicies(store, "G", create, mapping, false); throw new AssertionError("Forged create owner accepted"); }
        catch (BusinessException expected) { check(true, "Create owner must match authorized operation organization"); }
        try { PolicyServiceImpl.resolveOwnerPolicies(store, "G", context("OTHER_GROUP", Collections.singletonList(ref("ma", "MA", "A"))), mapping, false);
            throw new AssertionError("Cross-group operation organization accepted"); }
        catch (BusinessException expected) { check(true, "Operation organization must belong to the current group"); }
        store.targets.put("MA", "FOREIGN_FINANCE");
        try { resolve(store, mapping, "MA", "A"); throw new AssertionError("Cross-group page target accepted"); }
        catch (BusinessException expected) { check(true, "Page target must belong to the current group"); }
        store.targets.remove("MA");
        Map<String,Object> transfer = snapshot(resolve(store, mapping, "MA", "B"));
        transfer.put("desired_owner_org", "B");
        store.add("MA", "B");
        PolicyServiceImpl.validateSavedOwners(store, "G", "UPDATE", Collections.singletonList(transfer),
            Collections.singletonList(ref("MA", "MA", "B")));
        check("A".equals(transfer.get("policy_owner_org")), "Transfer uses the owner saved before the operation");
        store.add("MA", "A");
        try { PolicyServiceImpl.validateSavedOwners(store, "G", "UPDATE", Collections.singletonList(transfer),
                Collections.singletonList(ref("MA", "MA", "B"))); throw new AssertionError("Incorrect saved owner accepted"); }
        catch (BusinessException expected) { check(true, "Saved owner must match the authorized transfer"); }

        store.generic("BD_CUSTOMER", "CUSTOMER", "CUSTOMER", "A", null);
        store.generic("BD_CUSTOMER_FINANCE", "CUSTOMER_FI", "CUSTOMER", "A", "C");
        store.generic("BD_SUPPLIER", "SUPPLIER", "SUPPLIER", "G", null);
        store.generic("BD_SUPPLIER_STOCK", "SUP_STOCK", "SUPPLIER", "G", "C");
        store.generic("BD_CUSTOMER_BANK", "CUST_BANK", "CUSTOMER", "A", null);
        store.generic("BD_SUPPLIER_BANK", "SUP_BANK", "SUPPLIER", "G", null);
        store.assigned.add("CUSTOMER/B"); store.assigned.add("SUPPLIER/B");
        store.policy("A", true, false, false, true, 6, 5);
        store.policy("G", true, false, false, false, 3, 1);
        ObjectPolicyDecision customer = resolveGeneric(store, mapping, "BD_CUSTOMER", "CUSTOMER");
        ObjectPolicyDecision customerPage = resolveGeneric(store, mapping, "BD_CUSTOMER_FINANCE", "CUSTOMER_FI");
        ObjectPolicyDecision supplierPage = resolveGeneric(store, mapping, "BD_SUPPLIER_STOCK", "SUP_STOCK");
        check("A".equals(customer.getOwnerOrg()) && customer.getPolicy().isAuditEnabled(),
            "Customer edited in B follows its owner A");
        check("CUSTOMER".equals(customerPage.getOwnerRoot()) && "C".equals(customerPage.getTargetOrg())
            && customerPage.getPolicy().isAuditEnabled(), "Customer finance page follows the parent owner and retains target C");
        check("G".equals(supplierPage.getOwnerOrg()) && !supplierPage.getPolicy().isAuditEnabled()
            && "SUPPLIER".equals(supplierPage.getOwnerRoot()), "Supplier stock page follows the group-owned parent");
        BusinessObjectRef supA=new BusinessObjectRef("sup-a","BD_SUPPLIER","SUPPLIER",null,null,null,null,"A");
        BusinessObjectRef supC=new BusinessObjectRef("sup-c","BD_SUPPLIER","SUPPLIER",null,null,null,null,"C");
        OperationContext supplierFreeze=new OperationContext(null,null,"G","G","10140SUG","供应商","Freeze","冻结",
            "FREEZE",null,Arrays.asList(supA,supC),OperationContext.OWNER_POLICY_PROTOCOL);
        List<ObjectPolicyDecision> supplierTargets=PolicyServiceImpl.resolveOwnerPolicies(store,"G",supplierFreeze,mapping,true);
        check("G".equals(supplierTargets.get(0).getOwnerOrg()) && "G".equals(supplierTargets.get(1).getOwnerOrg())
            && "A".equals(supplierTargets.get(0).getTargetOrg()) && "C".equals(supplierTargets.get(1).getTargetOrg())
            && !supplierTargets.get(0).getPolicy().isAuditEnabled() && !supplierTargets.get(1).getPolicy().isAuditEnabled(),
            "Supplier target organizations retain the parent owner's policy rather than the operator's policy");
        List<Map<String,Object>> supplierSnapshot=Arrays.asList(snapshot(supplierTargets.get(0)),snapshot(supplierTargets.get(1)));
        PolicyServiceImpl.verifyOwnerSnapshot(supplierTargets,supplierSnapshot);
        supplierSnapshot.get(1).put("target_org","A");
        try { PolicyServiceImpl.verifyOwnerSnapshot(supplierTargets,supplierSnapshot);throw new AssertionError("Changed supplier target accepted"); }
        catch(BusinessException expected) { check(true,"Changed supplier target invalidates the authorization"); }
        ObjectPolicyDecision customerBank=resolveGeneric(store,mapping,"BD_CUSTOMER_BANK","CUST_BANK");
        ObjectPolicyDecision supplierBank=resolveGeneric(store,mapping,"BD_SUPPLIER_BANK","SUP_BANK");
        check("CUSTOMER".equals(customerBank.getOwnerRoot()) && "A".equals(customerBank.getOwnerOrg())
            && customerBank.getPolicy().isAuditEnabled() && "B".equals(customerBank.getTargetOrg()),
            "Customer bank policy follows its root owner, not the operation unit");
        check("SUPPLIER".equals(supplierBank.getOwnerRoot()) && "G".equals(supplierBank.getOwnerOrg())
            && !supplierBank.getPolicy().isAuditEnabled(),"Supplier bank policy follows its own root");
        store.assigned.remove("CUSTOMER/B");
        try { resolveGeneric(store, mapping, "BD_CUSTOMER_FINANCE", "CUSTOMER_FI");
            throw new AssertionError("Unassigned customer page accepted"); }
        catch (BusinessException expected) { check(true, "Customer pages require the parent assignment"); }
        try {
            OperationContext unknown = new OperationContext(null, null, "G", "B", "10140CUO", "客户", "Save", "保存",
                "INSERT", null, Collections.singletonList(new BusinessObjectRef("new", "UNDECLARED", null, null, null, "B")),
                OperationContext.OWNER_POLICY_PROTOCOL);
            PolicyServiceImpl.resolveOwnerPolicies(store, "G", unknown, mapping, false);
            throw new AssertionError("Unknown archive insert accepted");
        } catch (BusinessException expected) { check(true, "Only a declared root type can be created"); }
        store.policy("G", true, true, true, false, 3, 1);

        BusinessObjectRef assignA = new BusinessObjectRef("ma-a", "BD_MATERIAL", "MA", null, null, null, null, "A");
        BusinessObjectRef assignC = new BusinessObjectRef("ma-c", "BD_MATERIAL", "MA", null, null, null, null, "C");
        BusinessObjectRef assignGroup = new BusinessObjectRef("mg-c", "BD_MATERIAL", "MG", null, null, null, null, "C");
        store.assigned.add("MG/B");
        OperationContext assignment = new OperationContext(null, null, "G", "B", "10140MAO", "物料", "Assign", "分配",
            "ASSIGN", null, Arrays.asList(assignA, assignC, assignGroup), OperationContext.OWNER_POLICY_PROTOCOL);
        List<ObjectPolicyDecision> assignedPolicies = PolicyServiceImpl.resolveOwnerPolicies(store, "G", assignment, mapping, false);
        check("A".equals(assignedPolicies.get(0).getOwnerOrg()) && "A".equals(assignedPolicies.get(1).getOwnerOrg())
            && "A".equals(assignedPolicies.get(0).getTargetOrg()) && "C".equals(assignedPolicies.get(1).getTargetOrg())
            && "G".equals(assignedPolicies.get(2).getOwnerOrg()),
            "Assignment scopes each material/target while retaining its original policy owner");
        PolicyDecision assignmentCombined = PolicyServiceImpl.aggregateOwnerPolicies(assignedPolicies);
        check(assignmentCombined.isSignatureRequired() && assignmentCombined.isAuditEnabled()
            && !assignedPolicies.get(2).getPolicy().isAuditEnabled()
            && assignedPolicies.get(0).getPolicy().isAuditEnabled(),
            "Mixed-owner assignment combines confirmation but retains per-material audit flags");
        List<BusinessObjectRef> restoredTargets = ObjectRelations.restore(Arrays.asList(
            EsignValues.row("object_id", "o-a", "client_object_id", "ma-a", "object_type", "BD_MATERIAL", "object_pk", "MA",
                "policy_owner_org", "A", "target_org", "A"),
            EsignValues.row("object_id", "o-c", "client_object_id", "ma-c", "object_type", "BD_MATERIAL", "object_pk", "MA",
                "policy_owner_org", "A", "target_org", "C")));
        check("A".equals(restoredTargets.get(0).getTargetOrg()) && "C".equals(restoredTargets.get(1).getTargetOrg())
            && "A".equals(restoredTargets.get(1).getPolicyOwnerOrg()),
            "Persisted assignment targets and owner survive request reconstruction");
        AuditCaptureScope assignmentScope = new AuditCaptureScope(assignment, new HashSet<String>(Arrays.asList("ma-a", "ma-c")));
        try {
            check("ma-a".equals(assignmentScope.objectId("BD_MATERIAL", "MA", "A"))
                && "ma-c".equals(assignmentScope.objectId("BD_MATERIAL", "MA", "C")), "Target identifies one assignment object");
            try { assignmentScope.objectId("BD_MATERIAL", "MA"); throw new AssertionError("Ambiguous assignment accepted"); }
            catch (IllegalStateException expected) { check(true, "Assignment cannot be identified by material alone"); }
        } finally { assignmentScope.close(); }
        try { new OperationContext(null, null, "G", "B", "10140MAO", "物料", "Assign", "分配", "ASSIGN", null,
                Arrays.asList(assignA, new BusinessObjectRef("duplicate", "BD_MATERIAL", "MA", null, null, null, null, "A")),
                OperationContext.OWNER_POLICY_PROTOCOL); throw new AssertionError("Duplicate material and target accepted"); }
        catch (IllegalArgumentException expected) { check(true, "Duplicate material/target pair is rejected"); }
        try { new OperationContext(null, null, "G", "B", "10140MAO", "物料", "Assign", "分配", "ASSIGN", null,
                Collections.singletonList(ref("missing", "MA", null)), OperationContext.OWNER_POLICY_PROTOCOL);
            throw new AssertionError("Assignment without target accepted"); }
        catch (IllegalArgumentException expected) { check(true, "Assignment requires an explicit target organization"); }
        Map<String,Object> success = EsignValues.row("unit_state","SUCCESS");
        Map<String,Object> repeat = EsignValues.row("unit_state","ALREADY_ASSIGNED");
        Map<String,Object> rejected = EsignValues.row("unit_state","FAILED");
        Map<String,Object> unknown = EsignValues.row("unit_state","UNKNOWN");
        check("SUCCESS".equals(MaterialAssignmentTransactionServiceImpl.summarize(Arrays.asList(success, repeat))),
            "Duplicates do not turn a successful batch into a failure");
        check("PARTIAL".equals(MaterialAssignmentTransactionServiceImpl.summarize(Arrays.asList(success, rejected, repeat))),
            "A failed target does not discard other successful targets");
        check("FAILED".equals(MaterialAssignmentTransactionServiceImpl.summarize(Collections.singletonList(rejected))),
            "All rejected targets yield a failed batch");
        check("UNKNOWN".equals(MaterialAssignmentTransactionServiceImpl.summarize(Arrays.asList(success, unknown))),
            "Unknown outcomes require reconciliation even when another target committed");
        check("PARTIAL".equals(MaterialPageDeleteTransactionServiceImpl.summarize(Arrays.asList(success,rejected)))
            && "UNKNOWN".equals(MaterialPageDeleteTransactionServiceImpl.summarize(Arrays.asList(success,unknown)))
            && "UNKNOWN".equals(MaterialPageDeleteTransactionServiceImpl.summarize(Collections.singletonList(repeat))),
            "Page delete reports partial and unknown without accepting assignment-only states");
        Map<String,nc.vo.pub.SuperVO> deleteRows=new LinkedHashMap<String,nc.vo.pub.SuperVO>();
        deleteRows.put("a",new nc.vo.bd.material.fi.MaterialFiVO());
        deleteRows.put("b",new nc.vo.bd.material.fi.MaterialFiVO());
        List<Map<String,Object>> deleteObjects=Arrays.asList(EsignValues.row("client_object_id","a"),
            EsignValues.row("client_object_id","b"));
        MaterialPageDeleteServiceImpl.requireCommand(deleteObjects,new MaterialPageDeleteCommand(deleteRows));
        check(true,"Page delete accepts exactly the rows bound to the request");
        deleteRows.remove("b");deleteRows.put("forged",new nc.vo.bd.material.fi.MaterialFiVO());
        try { MaterialPageDeleteServiceImpl.requireCommand(deleteObjects,new MaterialPageDeleteCommand(deleteRows));
            throw new AssertionError("Page delete accepted a different row set"); }
        catch (BusinessException expected) { check(true,"Page delete rejects substituted client object ids"); }
        System.out.println("PASS OwnerPolicyResolverTests assertions=" + checks + " (in-memory owner policy; NC integration pending)");
    }

    private static ObjectPolicyDecision resolve(FakeStore store, Map<String,Object> mapping, String pk, String desired)
            throws BusinessException {
        return PolicyServiceImpl.resolveOwnerPolicies(store, "G", context("B", Collections.singletonList(ref(pk, pk, desired))),
            mapping, false).get(0);
    }
    private static ObjectPolicyDecision resolveGeneric(FakeStore store, Map<String,Object> mapping, String type, String pk)
            throws BusinessException {
        OperationContext context = new OperationContext(null, null, "G", "B", "10140CUO", "基础档案", "Save", "保存",
            "UPDATE", null, Collections.singletonList(new BusinessObjectRef("generic", type, pk, null, null)),
            OperationContext.OWNER_POLICY_PROTOCOL);
        return PolicyServiceImpl.resolveOwnerPolicies(store, "G", context, mapping, false).get(0);
    }
    private static BusinessObjectRef ref(String id, String pk, String desired) {
        return new BusinessObjectRef(id, "BD_MATERIAL", pk, null, null, desired);
    }
    private static OperationContext context(String org, List<BusinessObjectRef> objects) {
        return new OperationContext(null, null, "G", org, "10140MAO", "物料", "Save", "保存", "UPDATE", null,
            objects, OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static Map<String,Object> snapshot(ObjectPolicyDecision d) {
        PolicyDecision p = d.getPolicy();
        return EsignValues.row("client_object_id", d.getClientObjectId(), "policy_owner_org", d.getOwnerOrg(),
            "policy_owner_type", d.getOwnerType(), "policy_scope_id", d.getScopeId(), "policy_owner_root", d.getOwnerRoot(),
            "target_org", d.getTargetOrg(), "owner_policy_version", Long.valueOf(p.getVersion()),
            "owner_control_version", Long.valueOf(p.getControlVersion()), "owner_sign_required", p.isSignatureRequired()?"Y":"N",
            "owner_reason_required", p.isReasonRequired()?"Y":"N", "owner_audit_enabled", p.isAuditEnabled()?"Y":"N");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }

    private static final class FakeStore extends QdEsignStore {
        final Map<String,Map<String,Object>> owners = new HashMap<String,Map<String,Object>>();
        final Map<String,Map<String,Object>> policies = new HashMap<String,Map<String,Object>>();
        final Set<String> assigned = new HashSet<String>();
        final Map<String,String> targets = new HashMap<String,String>();
        final Map<String,Map<String,Object>> genericOwners = new HashMap<String,Map<String,Object>>();
        final List<String> locked = new ArrayList<String>();
        Map<String,Object> actionMapping;
        void add(String pk,String owner) { owners.put(pk, EsignValues.row("pk_material",pk,"pk_group","G","pk_org",owner)); }
        void generic(String type,String pk,String root,String owner,String target) {
            genericOwners.put(type+"/"+pk, EsignValues.row("pk_archive_root",root,"pk_group","G","pk_org",owner,"target_org",target));
        }
        void policy(String owner,boolean master,boolean sign,boolean reason,boolean audit,long version,long control) {
            policies.put(owner, EsignValues.row("enabled","Y","validated","Y","master_enabled",master?"Y":"N",
                "sign_enabled",sign?"Y":"N","reason_required",reason?"Y":"N","audit_enabled",audit?"Y":"N",
                "policy_version",Long.valueOf(version),"control_version",Long.valueOf(control)));
        }
        @Override public boolean isBusinessUnitInGroup(String group,String org) { return "G".equals(group) && Arrays.asList("A","B","C").contains(org); }
        @Override public Map<String,Object> loadNodeByAction(String node,String button) { return actionMapping; }
        @Override public boolean isOrganizationInGroup(String group,String org) { return isBusinessUnitInGroup(group,org); }
        @Override public Map<String,Object> loadMaterialOwner(String group,String type,String pk,boolean lock) {
            Map<String,Object> row=owners.get(pk);if(row==null)return null;
            Map<String,Object> copy=new LinkedHashMap<String,Object>(row);if(targets.containsKey(pk))copy.put("target_org",targets.get(pk));return copy;
        }
        @Override public boolean isMaterialAssignedTo(String group,String pk,String org,boolean lock) { return assigned.contains(pk+"/"+org); }
        @Override public Map<String,Object> loadBaseArchiveOwner(String group,String type,String pk,boolean lock) throws nc.bs.dao.DAOException {
            Map<String,Object> generic = genericOwners.get(type+"/"+pk);
            return generic == null ? super.loadBaseArchiveOwner(group,type,pk,lock) : new LinkedHashMap<String,Object>(generic);
        }
        @Override public boolean isBaseArchiveAssignedTo(String group,String type,String pk,String org,boolean lock) {
            return assigned.contains(pk+"/"+org);
        }
        @Override public Map<String,Object> loadOwnerBasePolicyWithControl(String group,String owner,String node,String button) { return policies.get(owner); }
        @Override public void lockBaseOwner(String group,String owner) { locked.add(owner); }
    }
}
