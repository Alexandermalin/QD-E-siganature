package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.MaterialCancelAssignmentRoute;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.itf.uapbd.qd.esign.IMaterialCancelAssignmentService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.MaterialCancelAssignmentResult;

/** Tests fixed route binding and conservative per-target terminal outcomes. */
public final class MaterialCancelAssignmentServiceTests {
    private MaterialCancelAssignmentServiceTests() { }
    public static void run() throws Exception {
        Map<String,Object> cancel=mapping("10140MAG","CancelAssign","QD-ES-CE419A6EAAED8307");
        MaterialCancelAssignmentRoute.requireMapping(cancel,"CANCEL_ASSIGN");
        MaterialCancelAssignmentRoute.requireMapping(mapping("10140MAO","CancelAssign","QD-ES-CE419A6EAAED8307"),"CANCEL_ASSIGN");
        require(MaterialCancelAssignmentResult.isRoute("10140MAG","CancelAssign","CANCEL_ASSIGN")
            && !MaterialCancelAssignmentResult.isRoute("10140CUG","CancelAssign","CANCEL_ASSIGN"),"Material route included another node");
        reject(mapping("10140MAG","CancelAssign","QD-ES-755F2D0C43B0646E"),"CANCEL_ASSIGN");
        reject(mapping("10140MAO","CancelAssign","QD-ES-CE419A6EAAED8307"),"ASSIGN");
        cancel.put("operation_type","CANCEL_ASSIGN");
        try { OperationTransactionServiceImpl.requireOrdinaryRoute(cancel);
            throw new AssertionError("Per-target cancellation entered the ordinary business gateway"); }
        catch(BusinessException expected) { }
        cancel.put("validated","N");reject(cancel,"CANCEL_ASSIGN");
        require("SUCCESS".equals(summary("SUCCESS","ALREADY_UNASSIGNED")),"No-op success summary incorrect");
        require("UNKNOWN".equals(summary("ALREADY_ASSIGNED")),"An assignment outcome cannot confirm a cancellation");
        require("PARTIAL".equals(summary("SUCCESS","FAILED")),"Partial result incorrectly summarized");
        require("FAILED".equals(summary("FAILED","FAILED")),"Failed result incorrectly summarized");
        require("UNKNOWN".equals(summary("SUCCESS","UNKNOWN")) && "UNKNOWN".equals(summary("FAILED","PENDING")),
            "Uncertain result incorrectly summarized as final");
        System.out.println("PASS MaterialCancelAssignmentServiceTests (formal routes and partial/unknown receipt; no NC or Oracle)");
    }
    private static Map<String,Object> mapping(String node,String button,String scope) {
        return EsignValues.row("node_code",node,"button_code",button,"group_scope_id",scope,"object_type","BD_MATERIAL",
            "adapter_class",IMaterialCancelAssignmentService.class.getName(),"validated","Y");
    }
    private static void reject(Map<String,Object> mapping,String operation) throws Exception {
        try { MaterialCancelAssignmentRoute.requireMapping(mapping,operation);throw new AssertionError("Invalid material route accepted"); }
        catch(BusinessException expected) { }
    }
    private static String summary(String... states) {
        List<Map<String,Object>> units=new java.util.ArrayList<Map<String,Object>>();
        for(String state:states) units.add(EsignValues.row("unit_state",state));
        return MaterialCancelAssignmentTransactionServiceImpl.summarize(units);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}

