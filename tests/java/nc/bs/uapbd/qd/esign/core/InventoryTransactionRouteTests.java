package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;

/** Exercises scoped inventory transaction and uncertain-outcome classification. */
public final class InventoryTransactionRouteTests {
    public static void run() throws Exception {
        for (String node : new String[] { "40080602", "40080802", "40080618", "40080402", "40081014", "40080608", "40080604", "40080606", "40080808", "40080804", "40080820", "40080602400", "40080604400", "40080801" }) {
            require(InventoryTransactionRoute.matches(node, "Save", "INSERT"), "Insert must use a guarded transaction");
            require(InventoryTransactionRoute.matches(node, "Save", "UPDATE"), "Update must use a guarded transaction");
            require(InventoryTransactionRoute.matches(node, "Delete", "DELETE"), "Delete must use a guarded transaction");
            boolean location = "40081014".equals(node) || "40080801".equals(node);
            require(InventoryTransactionRoute.matches(node, "Sign", "SIGN") != location, "Sign scope must match the approved node");
            require(InventoryTransactionRoute.matches(node, "CancelSign", "CANCEL_SIGN") != location, "Cancel-sign scope must match the approved node");
            require(!InventoryTransactionRoute.matches(node, "Save", "DELETE"), "Mismatched actions must not match");
            require(InventoryTransactionRoute.isUncertain(false, node, "Save", "INSERT", "BUSINESS_OUTCOME_UNKNOWN"),
                    "Unknown inventory transaction outcomes must remain unknown");
            require(!InventoryTransactionRoute.isUncertain(false, node, "Save", "INSERT", "BUSINESS_EXECUTION_FAILED"),
                    "Confirmed rollback must not be classified as unknown");
            for (String[] action : new String[][] { {"Save","INSERT"}, {"Save","UPDATE"},
                    {"Delete","DELETE"}, {"Sign","SIGN"}, {"CancelSign","CANCEL_SIGN"} }) {
                if (location && ("Sign".equals(action[0]) || "CancelSign".equals(action[0]))) continue;
                OperationContext single=context(node,action[0],action[1],false);
                OperationContext batch=context(node,action[0],action[1],true);
                for (boolean sign : new boolean[] {false,true}) for (boolean audit : new boolean[] {false,true}) {
                    PolicyDecision policy=new PolicyDecision(true,sign,false,audit,true,1);
                    InventoryTransactionRoute.requireSupportedPreparation(single,false,policy);
                    if (!sign && !audit) InventoryTransactionRoute.requireSupportedPreparation(batch,false,policy);
                    else try {
                        InventoryTransactionRoute.requireSupportedPreparation(batch,false,policy);
                        throw new AssertionError("Controlled inventory batch was accepted");
                    } catch (BusinessException expected) { }
                }
                try {
                    InventoryTransactionRoute.requireSupportedPreparation(batch,true,
                            new PolicyDecision(false,false,false,false,true,1));
                    throw new AssertionError("Owner guard allowed inventory batch");
                } catch (BusinessException expected) { }
            }
        }
        require(InventoryTransactionRoute.matches("40080801", "Commit", "UPDATE")
                && InventoryTransactionRoute.matches("40080801", "UnCommit", "UPDATE")
                && !InventoryTransactionRoute.matches("40080801", "Commit", "INSERT")
                && !InventoryTransactionRoute.matches("40080602", "Commit", "UPDATE"),
                "Submit and withdraw routes must remain limited to outbound requests");
        for (String button : new String[] {"Commit", "UnCommit"}) {
            OperationContext batch = context("40080801", button, "UPDATE", true);
            try {
                InventoryTransactionRoute.requireSupportedPreparation(batch, false,
                        new PolicyDecision(true,true,false,true,true,1));
                throw new AssertionError("Controlled outbound-request workflow batch was accepted");
            } catch (BusinessException expected) { }
        }
        require(!InventoryTransactionRoute.matches("OTHER", "Save", "INSERT"), "Other nodes must keep their route");
        require(InventoryTransactionRoute.isUncertain(true, "OTHER", "Save", "INSERT", "BUSINESS_OUTCOME_UNKNOWN"),
                "Existing owner-policy unknown outcomes must remain unknown");
        require(!InventoryTransactionRoute.isUncertain(false, "OTHER", "Save", "INSERT", "BUSINESS_OUTCOME_UNKNOWN"),
                "Unrelated ordinary routes must keep their previous behavior");
        InventoryTransactionRoute.requireSupportedPreparation(context("OTHER","Sign","SIGN",true),false,
                new PolicyDecision(true,true,false,true,true,1));
        System.out.println("PASS InventoryTransactionRouteTests (scoped transactions, preparation guard and outcomes; no NC transaction)");
    }
    private static OperationContext context(String node,String button,String operation,boolean batch) {
        String type="40080801".equals(node)?"IC_SAPPLY":"40080602400".equals(node)?"IC_OUTSOURCE_IN":"40080604400".equals(node)?"IC_PRODUCTION_ORDER":"40080820".equals(node)?"IC_TRANS_OUT":"40080804".equals(node)?"IC_MATERIAL_OUT":"40080808".equals(node)?"IC_GENERAL_OUT":"40080606".equals(node)?"IC_SUBCONT_IN":"40080604".equals(node)?"IC_PRODUCT_IN":"40080608".equals(node)?"IC_GENERAL_IN":"40081014".equals(node)?"IC_LOC_ADJUST":"40080402".equals(node)?"IC_INITIAL_IN":"40080618".equals(node)?"IC_TRANS_IN":"40080802".equals(node)?"IC_SALE_OUT":"IC_PURCHASE_IN";
        BusinessObjectRef first=new BusinessObjectRef("one",type,"pk1","bill1",null);
        BusinessObjectRef second=new BusinessObjectRef("two",type,"pk2","bill2",null);
        return new OperationContext(null,null,"group","org",node,"inventory",button,button,operation,null,
                batch?Arrays.asList(first,second):Arrays.asList(first));
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
