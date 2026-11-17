package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.MappingRules;
import nc.bs.uapbd.qd.esign.core.PartnerMainSaveRoute;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.ObjectPolicyDecision;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;

/** Real policy resolver applied to a server-expanded main-save scope using an in-memory store. */
public final class PartnerMainOwnerPolicyTests {
    private PartnerMainOwnerPolicyTests() { }
    public static void run() throws Exception {
        BankAccountOwnerPolicyIntegrationTests.Store store=new BankAccountOwnerPolicyIntegrationTests.Store();
        String customerScope="QD-ES-E576276F26109C16",supplierScope="QD-ES-E8C69ACAF76ECABF";
        store.owner("BD_CUSTOMER","C","C","A");store.owner("BD_SUPPLIER","C","C","G");store.owner("BD_CUSTOMER","D","D","A");
        store.entry("A",customerScope,true,false,false,4);store.entry("G",supplierScope,false,true,true,7);
        store.controls.put("A",EsignValues.row("master_enabled","Y","control_version",Long.valueOf(3)));
        store.controls.put("G",EsignValues.row("master_enabled","Y","control_version",Long.valueOf(8)));
        Map<String,Object> mapping=EsignValues.row("node_code","10140CUO","button_code","Save","object_type","BD_CUSTOMER",
            "adapter_class",PartnerMainSaveRoute.ADAPTER,"operation_types","UPDATE","validated","Y","group_scope_id",customerScope);
        OperationContext context=new OperationContext(null,null,"G","B","10140CUO","客户","Save","保存","UPDATE",null,
            Arrays.asList(new BusinessObjectRef("C","BD_CUSTOMER","C","C",null),
                new BusinessObjectRef("S","BD_SUPPLIER","C","S","C"),
                new BusinessObjectRef("D","BD_CUSTOMER","D","D","C")),OperationContext.OWNER_POLICY_PROTOCOL);
        MappingRules.requireDomain(mapping,"UPDATE",context.getObjects());
        List<ObjectPolicyDecision> decisions=PolicyServiceImpl.resolveOwnerPolicies(store,"G",context,mapping,true);
        require(customerScope.equals(decisions.get(0).getScopeId()) && "A".equals(decisions.get(0).getOwnerOrg())
            && decisions.get(0).getPolicy().isSignatureRequired(),"Selected customer owner policy incorrect");
        require(supplierScope.equals(decisions.get(1).getScopeId()) && "G".equals(decisions.get(1).getOwnerOrg())
            && decisions.get(1).getPolicy().isAuditEnabled(),"Paired supplier must use its own formal save policy");
        require(customerScope.equals(decisions.get(2).getScopeId()) && "A".equals(decisions.get(2).getOwnerOrg()),
            "Structure descendant must use its own owner policy");
        PolicyDecision combined=PolicyServiceImpl.aggregateOwnerPolicies(decisions);
        require(combined.isSignatureRequired() && combined.isReasonRequired() && combined.isAuditEnabled(),"Affected archive decisions were not aggregated");
        System.out.println("PASS PartnerMainOwnerPolicyTests (affected archive scopes and owner policy; no NC or Oracle)");
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
