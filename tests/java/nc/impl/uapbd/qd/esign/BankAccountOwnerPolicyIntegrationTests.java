package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.BankAccountRoute;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.ObjectPolicyDecision;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;

/** Exercises the real owner-policy resolver with a shared bank account and an in-memory store. */
public final class BankAccountOwnerPolicyIntegrationTests {
    private BankAccountOwnerPolicyIntegrationTests() { }
    public static void run() throws Exception {
        Store store=new Store();String customerScope="QD-ES-DDC46B3D9F641DFF",supplierScope="QD-ES-B3A292BB1D2F3E84";
        store.owner("BD_CUSTOMER_BANK","R1","C","A");store.owner("BD_SUPPLIER_BANK","R2","S","G");
        store.entry("A",customerScope,true,false,false,4);store.entry("G",supplierScope,false,true,true,7);
        store.controls.put("A",control(true,3));store.controls.put("G",control(true,8));
        Map<String,Object> mapping=EsignValues.row("node_code","10140CBA","button_code","Enable","object_type","BD_CUSTOMER_BANK",
            "adapter_class",BankAccountRoute.ADAPTER,"operation_types","ENABLE","validated","Y","group_scope_id",customerScope);
        OperationContext context=new OperationContext(null,null,"G","B","10140CBA","客户银行账户","Enable","启用","ENABLE",null,
            Arrays.asList(new BusinessObjectRef("C","BD_CUSTOMER_BANK","R1","C",null),
                new BusinessObjectRef("S","BD_SUPPLIER_BANK","R2","S","C")),OperationContext.OWNER_POLICY_PROTOCOL);
        List<ObjectPolicyDecision> decisions=PolicyServiceImpl.resolveOwnerPolicies(store,"G",context,mapping,true);
        require(decisions.size()==2 && "A".equals(decisions.get(0).getOwnerOrg()) && customerScope.equals(decisions.get(0).getScopeId())
            && decisions.get(0).getPolicy().isSignatureRequired() && !decisions.get(0).getPolicy().isAuditEnabled(),"Customer owner or scope incorrect");
        require("G".equals(decisions.get(1).getOwnerOrg()) && supplierScope.equals(decisions.get(1).getScopeId())
            && !decisions.get(1).getPolicy().isSignatureRequired() && decisions.get(1).getPolicy().isAuditEnabled(),"Supplier owner or scope incorrect");
        PolicyDecision combined=PolicyServiceImpl.aggregateOwnerPolicies(decisions);
        require(combined.isSignatureRequired() && combined.isReasonRequired() && combined.isAuditEnabled(),"Shared-account flags were not combined");
        require(store.locked.equals(Arrays.asList("A","G")),"Owner guards were not locked deterministically");
        List<Map<String,Object>> saved=new ArrayList<Map<String,Object>>();for(ObjectPolicyDecision item:decisions) saved.add(snapshot(item));
        PolicyServiceImpl.verifyOwnerSnapshot(decisions,saved);
        store.entry("G",supplierScope,false,true,true,9);
        try { PolicyServiceImpl.verifyOwnerSnapshot(PolicyServiceImpl.resolveOwnerPolicies(store,"G",context,mapping,false),saved);
            throw new AssertionError("Changed supplier policy version accepted"); }
        catch(BusinessException expected) { }
        store.entry("G",supplierScope,false,true,true,7).put("configurable","N");
        try { PolicyServiceImpl.resolveOwnerPolicies(store,"G",context,mapping,false);throw new AssertionError("Unverified supplier action accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS BankAccountOwnerPolicyIntegrationTests (mixed scopes and stale policy; no NC or Oracle)");
    }
    private static Map<String,Object> snapshot(ObjectPolicyDecision item) {
        PolicyDecision p=item.getPolicy();
        return EsignValues.row("client_object_id",item.getClientObjectId(),"policy_owner_org",item.getOwnerOrg(),
            "policy_owner_type",item.getOwnerType(),"policy_scope_id",item.getScopeId(),"policy_owner_root",item.getOwnerRoot(),
            "target_org",item.getTargetOrg(),"owner_policy_version",Long.valueOf(p.getVersion()),"owner_control_version",Long.valueOf(p.getControlVersion()),
            "owner_sign_required",p.isSignatureRequired()?"Y":"N","owner_reason_required",p.isReasonRequired()?"Y":"N",
            "owner_audit_enabled",p.isAuditEnabled()?"Y":"N");
    }
    private static Map<String,Object> control(boolean enabled,long version) {
        return EsignValues.row("master_enabled",enabled?"Y":"N","control_version",Long.valueOf(version));
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    static final class Store extends QdEsignStore {
        final Map<String,Map<String,Object>> owners=new HashMap<String,Map<String,Object>>(),entries=new HashMap<String,Map<String,Object>>(),
            controls=new HashMap<String,Map<String,Object>>();final List<String> locked=new ArrayList<String>();
        void owner(String type,String relation,String archive,String org) {
            owners.put(type+"/"+relation,EsignValues.row("pk_group","G","pk_org",org,"pk_archive_root",archive));
        }
        Map<String,Object> entry(String org,String scope,boolean sign,boolean reason,boolean audit,long version) {
            Map<String,Object> row=EsignValues.row("configurable","Y","sign_enabled",sign?"Y":"N","reason_required",reason?"Y":"N",
                "audit_enabled",audit?"Y":"N","policy_version",Long.valueOf(version));entries.put(org+"/"+scope,row);return row;
        }
        public Map<String,Object> loadBaseArchiveOwner(String group,String type,String pk,boolean lock) { return owners.get(type+"/"+pk); }
        public boolean isBusinessUnitInGroup(String group,String org) { return "G".equals(group) && ("A".equals(org) || "B".equals(org)); }
        public boolean isOrganizationInGroup(String group,String org) { return isBusinessUnitInGroup(group,org); }
        public boolean isBaseArchiveAssignedTo(String group,String type,String pk,String org,boolean lock) { return "B".equals(org) && "C".equals(pk); }
        public void lockBaseOwner(String group,String owner) { locked.add(owner); }
        public Map<String,Object> loadGroupPolicyEntry(String group,String scope) { return entries.get("G/"+scope); }
        public Map<String,Object> loadOrgBasePolicyEntry(String group,String org,String scope) { return entries.get(org+"/"+scope); }
        public Map<String,Object> loadGroupControl(String group) { return controls.get("G"); }
        public Map<String,Object> loadOrganizationControl(String group,String org) { return controls.get(org); }
    }
}
