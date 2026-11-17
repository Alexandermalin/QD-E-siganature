package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;

/** Multiple currency relationships retain one signature/audit representative per affected archive. */
public final class BankAccountAuditObjectsTests {
    private BankAccountAuditObjectsTests() { }
    public static void run() throws Exception {
        Map<String,Object> r2=row("C2","BD_CUSTOMER_BANK","R2","C","A",true);
        Map<String,Object> r1=row("C1","BD_CUSTOMER_BANK","R1","C","A",true);
        Map<String,Object> r3=row("S3","BD_SUPPLIER_BANK","R3","S","G",false);
        Set<String> selected=BankAccountAuditObjects.representatives(Arrays.asList(r2,r3,r1));
        require(selected.size()==2 && selected.contains("C1") && selected.contains("S3"),"Representative selection is not per archive and deterministic");
        require(selected.equals(BankAccountAuditObjects.representatives(Arrays.asList(r1,r2,r3))),"Input order changed audit representatives");
        r1.put("object_pk","R9");
        require(selected.equals(BankAccountAuditObjects.representatives(Arrays.asList(r1,r2,r3))),
            "A replaced bank relation changed the archive's signature or audit representative");
        r1.put("object_id","O1");r2.put("object_id","O2");r3.put("object_id","O3");
        BankAccountAuditObjects.requireProofs(Arrays.asList(r1,r2,r3),true,true,
            new java.util.HashSet<String>(Arrays.asList("O2","O3")),new java.util.HashSet<String>(Arrays.asList("O2")));
        try { BankAccountAuditObjects.requireProofs(Arrays.asList(r1,r2,r3),true,true,
                new java.util.HashSet<String>(Arrays.asList("O2","O3")),new java.util.HashSet<String>(Arrays.asList("O1")));
            throw new AssertionError("Mismatched bank signature and audit representatives accepted"); }
        catch(BusinessException expected) { }
        Map<String,Object> conflicting=row("C3","BD_CUSTOMER_BANK","R3","C","A",false);
        reject(Arrays.asList(r1,conflicting));
        conflicting=row("C3","BD_CUSTOMER_BANK","R3","C","A",true);conflicting.put("owner_policy_version",Long.valueOf(2));
        reject(Arrays.asList(r1,conflicting));
        reject(Arrays.asList(r1,r1));
        Map<String,Object> unknown=row("X","BD_CUSTOMER","X","C","A",true);reject(Arrays.asList(unknown));
        System.out.println("PASS BankAccountAuditObjectsTests (one log per archive and consistent policy; no NC or Oracle)");
    }
    private static Map<String,Object> row(String id,String type,String pk,String root,String owner,boolean audit) {
        return EsignValues.row("client_object_id",id,"object_type",type,"object_pk",pk,"bill_code",root,
            "policy_owner_root",root,"policy_owner_org",owner,"policy_owner_type","G".equals(owner)?"G":"O",
            "policy_scope_id",type,"target_org","B","owner_policy_version",Long.valueOf(1),"owner_control_version",Long.valueOf(1),
            "owner_sign_required","Y","owner_reason_required","N","owner_audit_enabled",audit?"Y":"N");
    }
    private static void reject(List<Map<String,Object>> rows) throws Exception {
        try { BankAccountAuditObjects.representatives(rows);throw new AssertionError("Inconsistent account log scope accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
