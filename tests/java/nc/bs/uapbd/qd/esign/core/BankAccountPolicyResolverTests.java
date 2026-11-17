package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.PolicyDecision;

/** Mixed customer/supplier policy decisions remain per owner and invalidate stale authorization. */
public final class BankAccountPolicyResolverTests {
    private BankAccountPolicyResolverTests() { }
    public static void run() throws Exception {
        BankAccountImpactScope.Selection selection=BankAccountImpactScope.select("G","BANK",
            new BusinessObjectRef("selected","BD_CUSTOMER_BANK","C_REL",null,null),new Relations(),false);
        Lookup policies=new Lookup();policies.rows.put("OC",policy(true,true,false,false,11,1));
        policies.rows.put("OS",policy(true,false,true,true,22,2));
        BankAccountPolicyResolver.Batch original=BankAccountPolicyResolver.resolve("G","ENABLE",selection,policies);
        require(original.isSignatureRequired() && original.isReasonRequired() && original.isAuditEnabled(),"Mixed policy requirements were not aggregated");
        require(original.getDecisions().size()==2,"Linked archives did not keep individual decisions");
        for(BankAccountPolicyResolver.Decision decision:original.getDecisions()) {
            if("BD_CUSTOMER_BANK".equals(decision.getDomain())) require(decision.getPolicy().isSignatureRequired()
                && !decision.getPolicy().isAuditEnabled() && "QD-ES-DDC46B3D9F641DFF".equals(decision.getScope()),"Customer policy scope is incorrect");
            else require(!decision.getPolicy().isSignatureRequired() && decision.getPolicy().isAuditEnabled()
                && decision.getPolicy().isReasonRequired() && "QD-ES-B3A292BB1D2F3E84".equals(decision.getScope()),"Supplier policy scope is incorrect");
        }
        BankAccountPolicyResolver.verifyUnchanged(original,BankAccountPolicyResolver.resolve("G","ENABLE",selection,policies));
        policies.rows.put("OS",policy(true,false,true,true,23,2));
        rejectChanged(original,BankAccountPolicyResolver.resolve("G","ENABLE",selection,policies));
        policies.rows.put("OS",policy(false,false,true,true,22,3));
        BankAccountPolicyResolver.Batch stopped=BankAccountPolicyResolver.resolve("G","ENABLE",selection,policies);
        require(stopped.isSignatureRequired() && !stopped.isReasonRequired() && !stopped.isAuditEnabled(),"Closed owner master still controls the batch");
        rejectChanged(original,stopped);
        policies.rows.put("OS",new PolicyDecision(true,false,false,false,false,1,1,true));
        try { BankAccountPolicyResolver.resolve("G","ENABLE",selection,policies);throw new AssertionError("Unverified action accepted"); }
        catch(BusinessException expected) { }
        Map<String,Object> entry=EsignValues.row("configurable","Y","sign_enabled","N","reason_required","Y",
            "audit_enabled","Y","policy_version",Long.valueOf(7));
        PolicyDecision disabled=BankAccountPolicyResolver.decode(entry,null);
        require(!disabled.isControlled() && disabled.getVersion()==7 && disabled.getControlVersion()==0,
            "Missing owner master must default to disabled");
        PolicyDecision decoded=BankAccountPolicyResolver.decode(entry,EsignValues.row("master_enabled","Y","control_version",Long.valueOf(4)));
        require(!decoded.isSignatureRequired() && decoded.isReasonRequired() && decoded.isAuditEnabled()
            && decoded.getControlVersion()==4,"Formal owner policy or independent reason decoded incorrectly");
        entry.put("configurable","N");require(!BankAccountPolicyResolver.decode(entry,null).isValidated(),"Unverified bank action was enabled");
        System.out.println("PASS BankAccountPolicyResolverTests (per-owner formal scopes and versions; no NC or Oracle)");
    }
    private static PolicyDecision policy(boolean master,boolean sign,boolean reason,boolean audit,long version,long control) {
        return new PolicyDecision(master,sign,reason,audit,true,version,control,true);
    }
    private static void rejectChanged(BankAccountPolicyResolver.Batch before,BankAccountPolicyResolver.Batch after) throws Exception {
        try { BankAccountPolicyResolver.verifyUnchanged(before,after);throw new AssertionError("Stale account policy accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static final class Lookup implements BankAccountPolicyResolver.Lookup {
        final Map<String,PolicyDecision> rows=new HashMap<String,PolicyDecision>();
        public PolicyDecision load(String group,String owner,String scope) { return rows.get(owner); }
    }
    private static final class Relations implements BankAccountImpactScope.Reader {
        public List<BankAccountImpactScope.Relation> relations(String account,boolean lock) {
            return Arrays.asList(new BankAccountImpactScope.Relation("C_REL",account,"C",Integer.valueOf(1)),
                new BankAccountImpactScope.Relation("S_REL",account,"S",Integer.valueOf(3)));
        }
        public BankAccountImpactScope.Owner owner(String group,String domain,String relation,boolean lock) {
            return "C_REL".equals(relation)?new BankAccountImpactScope.Owner(group,"OC","C","C_CODE")
                :new BankAccountImpactScope.Owner(group,"OS","S","S_CODE");
        }
    }
}
