package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.SupplierAssignmentRoute;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.itf.uapbd.qd.esign.ISupplierAssignmentService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.SupplierAssignmentResult;

/** Tests fixed route binding and conservative per-target terminal outcomes. */
public final class SupplierAssignmentServiceTests {
    private SupplierAssignmentServiceTests() { }
    public static void run() throws Exception {
        Map<String,Object> assign=mapping("10140SUG","Assign","QD-ES-FD8282A38B9C4E89");
        SupplierAssignmentRoute.requireMapping(assign,"ASSIGN");
        SupplierAssignmentRoute.requireMapping(mapping("10140SUO","CancelAssign","QD-ES-309E9ED628D45CDF"),"CANCEL_ASSIGN");
        require(SupplierAssignmentResult.isRoute("10140SUG","Assign","ASSIGN")
            && !SupplierAssignmentResult.isRoute("10140CUG","Assign","ASSIGN"),"Supplier route included another node");
        reject(mapping("10140SUG","Assign","QD-ES-309E9ED628D45CDF"),"ASSIGN");
        reject(mapping("10140SUO","CancelAssign","QD-ES-309E9ED628D45CDF"),"ASSIGN");
        assign.put("validated","N");reject(assign,"ASSIGN");
        require("SUCCESS".equals(summary("SUCCESS","ALREADY_ASSIGNED","ALREADY_UNASSIGNED")),"No-op success summary incorrect");
        require("PARTIAL".equals(summary("SUCCESS","FAILED")),"Partial result incorrectly summarized");
        require("FAILED".equals(summary("FAILED","FAILED")),"Failed result incorrectly summarized");
        require("UNKNOWN".equals(summary("SUCCESS","UNKNOWN")) && "UNKNOWN".equals(summary("FAILED","PENDING")),
            "Uncertain result incorrectly summarized as final");
        System.out.println("PASS SupplierAssignmentServiceTests (formal routes and partial/unknown receipt; no NC or Oracle)");
    }
    private static Map<String,Object> mapping(String node,String button,String scope) {
        return EsignValues.row("node_code",node,"button_code",button,"group_scope_id",scope,"object_type","BD_SUPPLIER",
            "adapter_class",ISupplierAssignmentService.class.getName(),"validated","Y");
    }
    private static void reject(Map<String,Object> mapping,String operation) throws Exception {
        try { SupplierAssignmentRoute.requireMapping(mapping,operation);throw new AssertionError("Invalid supplier route accepted"); }
        catch(BusinessException expected) { }
    }
    private static String summary(String... states) {
        List<Map<String,Object>> units=new java.util.ArrayList<Map<String,Object>>();
        for(String state:states) units.add(EsignValues.row("unit_state",state));
        return SupplierAssignmentTransactionServiceImpl.summarize(units);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
