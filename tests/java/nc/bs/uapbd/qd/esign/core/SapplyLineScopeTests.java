package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.SapplyLineScope;

/** Checks that a row action authorizes one bill and only its explicitly selected rows. */
public final class SapplyLineScopeTests {
    public static void run() throws Exception {
        BusinessObjectRef bill=ref("bill","IC_SAPPLY","B",null);
        BusinessObjectRef one=ref("one","IC_SAPPLY_LINE","L1","bill");
        BusinessObjectRef two=ref("two","IC_SAPPLY_LINE","L2","bill");
        List<BusinessObjectRef> selected=Arrays.asList(two,bill,one);
        SapplyLineScope.Selection scope=SapplyLineScope.require(selected);
        require(scope.getRoot()==bill && scope.getLines().size()==2 && scope.lineKeys().containsAll(Arrays.asList("L1","L2")),
                "Selection must be independent of object ordering");
        reject(Collections.singletonList(bill));
        reject(Arrays.asList(bill,ref("one","IC_SAPPLY_LINE","L1","OTHER")));
        reject(Arrays.asList(bill,one,ref("two","IC_SAPPLY_LINE","L1","bill")));
        reject(Arrays.asList(bill,one,ref("two","IC_SAPPLY","B2",null)));
        reject(Arrays.asList(bill,ref("one","IC_SAPPLY","L1","bill")));
        reject(Arrays.asList(bill,ref("one","IC_SAPPLY_LINE"," L1 ","bill")));
        reject(Arrays.asList(bill,ref("one","IC_SAPPLY_LINE","~","bill")));
        for(String button:new String[]{"LineClose","LineOpen"}) {
            OperationContext context=new OperationContext("R","B","G","O","40080801","request",button,button,"UPDATE",null,selected);
            require(SapplyLineScope.matches(context.getNodeCode(),button,"UPDATE"),"Line route must match");
            InventoryTransactionRoute.requireSupportedPreparation(context,false,new PolicyDecision(true,true,false,true,true,1));
        }
        require(!SapplyLineScope.matches("40080802","LineClose","UPDATE")
                && !SapplyLineScope.matches("40080801","LineClose","DELETE"),"Line route must not bleed into other actions");
        System.out.println("PASS SapplyLineScopeTests (one bill and selected rows; no NC session)");
    }
    private static BusinessObjectRef ref(String id,String type,String pk,String parent) {
        return new BusinessObjectRef(id,type,pk,null,parent);
    }
    private static void reject(List<BusinessObjectRef> values) throws Exception {
        try { SapplyLineScope.require(values); throw new AssertionError("Invalid line scope accepted"); }
        catch (BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
