package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.CustomerAssignmentRoute;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.itf.uapbd.qd.esign.ICustomerAssignmentService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.CustomerAssignmentResult;

/** Tests fixed route binding and conservative per-target terminal outcomes. */
public final class CustomerAssignmentServiceTests {
    private CustomerAssignmentServiceTests() { }
    public static void run() throws Exception {
        Map<String,Object> assign=mapping("10140CUG","Assign","QD-ES-A9C10437DFDE8814");
        CustomerAssignmentRoute.requireMapping(assign,"ASSIGN");
        CustomerAssignmentRoute.requireMapping(mapping("10140CUO","CancelAssign","QD-ES-8555A9BFE51EA438"),"CANCEL_ASSIGN");
        require(CustomerAssignmentResult.isRoute("10140CUG","Assign","ASSIGN")
            && !CustomerAssignmentResult.isRoute("10140SUG","Assign","ASSIGN"),"Customer route included another node");
        reject(mapping("10140CUG","Assign","QD-ES-8555A9BFE51EA438"),"ASSIGN");
        reject(mapping("10140CUO","CancelAssign","QD-ES-8555A9BFE51EA438"),"ASSIGN");
        assign.put("validated","N");reject(assign,"ASSIGN");
        require("SUCCESS".equals(summary("SUCCESS","ALREADY_ASSIGNED","ALREADY_UNASSIGNED")),"No-op success summary incorrect");
        require("PARTIAL".equals(summary("SUCCESS","FAILED")),"Partial result incorrectly summarized");
        require("FAILED".equals(summary("FAILED","FAILED")),"Failed result incorrectly summarized");
        require("UNKNOWN".equals(summary("SUCCESS","UNKNOWN")) && "UNKNOWN".equals(summary("FAILED","PENDING")),
            "Uncertain result incorrectly summarized as final");
        System.out.println("PASS CustomerAssignmentServiceTests (formal routes and partial/unknown receipt; no NC or Oracle)");
    }
    private static Map<String,Object> mapping(String node,String button,String scope) {
        return EsignValues.row("node_code",node,"button_code",button,"group_scope_id",scope,"object_type","BD_CUSTOMER",
            "adapter_class",ICustomerAssignmentService.class.getName(),"validated","Y");
    }
    private static void reject(Map<String,Object> mapping,String operation) throws Exception {
        try { CustomerAssignmentRoute.requireMapping(mapping,operation);throw new AssertionError("Invalid customer route accepted"); }
        catch(BusinessException expected) { }
    }
    private static String summary(String... states) {
        List<Map<String,Object>> units=new java.util.ArrayList<Map<String,Object>>();
        for(String state:states) units.add(EsignValues.row("unit_state",state));
        return CustomerAssignmentTransactionServiceImpl.summarize(units);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
