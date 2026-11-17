package nc.ui.uapbd.qd.esign;

import java.util.Arrays;
import java.util.HashSet;
import nc.ui.ic.m4455.action.RowCloseAction;
import nc.ui.ic.m4455.action.RowOpenAction;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Checks that a line authorization cannot be replayed or applied to a changed selection. */
public final class SapplyLineHandoffTests {
    public static void run() throws Exception {
        SapplyLineNativeBridge.verify(RowCloseAction.class);
        SapplyLineNativeBridge.verify(RowOpenAction.class);
        try{SapplyLineNativeBridge.verify(String.class);throw new AssertionError("Unknown native row class accepted");}
        catch(BusinessException expected) { }
        SapplyLineHandoff handoff=new SapplyLineHandoff();OperationContext context=context();
        require(handoff.supports(context) && handoff.bind(context) && !handoff.bind(context),"Request must bind once");
        reject(handoff,"G","O","BILL","L1");
        reject(handoff,"OTHER","O","BILL","L1","L2");
        reject(handoff,"G","O","OTHER","L1","L2");
        require("R".equals(handoff.dispatch("G","O","BILL",new HashSet<String>(Arrays.asList("L2","L1"))))
                && handoff.wasDispatched("R"),"Exact selection must dispatch once");
        reject(handoff,"G","O","BILL","L1","L2");
        handoff.release("OTHER");require(handoff.wasDispatched("R"),"Another request must not release this authorization");
        handoff.release("R");require(!handoff.wasDispatched("R"),"Completed authorization must be cleared");
        System.out.println("PASS SapplyLineHandoffTests (native class guard and exact row selection; no NC session)");
    }
    private static OperationContext context(){
        return new OperationContext("R","B","G","O","40080801","request","LineClose","close","UPDATE",null,
                Arrays.asList(new BusinessObjectRef("root","IC_SAPPLY","BILL","CODE",null),
                    new BusinessObjectRef("one","IC_SAPPLY_LINE","L1",null,"root"),
                    new BusinessObjectRef("two","IC_SAPPLY_LINE","L2",null,"root")));
    }
    private static void reject(SapplyLineHandoff handoff,String group,String org,String pk,String... rows) throws Exception {
        try{handoff.dispatch(group,org,pk,new HashSet<String>(Arrays.asList(rows)));throw new AssertionError("Invalid dispatch accepted");}
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
