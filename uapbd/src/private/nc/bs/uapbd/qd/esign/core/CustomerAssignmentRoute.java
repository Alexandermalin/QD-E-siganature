package nc.bs.uapbd.qd.esign.core;

import java.util.Map;
import nc.itf.uapbd.qd.esign.ICustomerAssignmentService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.CustomerAssignmentResult;

/** Candidate mappings are pinned to the two customer actions in the formal operation list. */
public final class CustomerAssignmentRoute {
    private CustomerAssignmentRoute() { }
    public static boolean isAdapter(Map<String,Object> mapping) {
        return mapping!=null && ICustomerAssignmentService.class.getName().equals(EsignValues.text(mapping,"adapter_class"));
    }
    public static void requireMapping(Map<String,Object> mapping,String operation) throws BusinessException {
        EsignValues.require(mapping!=null && EsignValues.yes(mapping,"validated") && isAdapter(mapping)
            && CustomerAssignmentResult.isRoute(EsignValues.text(mapping,"node_code"),EsignValues.text(mapping,"button_code"),operation)
            && "BD_CUSTOMER".equals(EsignValues.text(mapping,"object_type")),"客户分配入口尚未完成接入验证。");
        String scope="ASSIGN".equals(operation)?"QD-ES-A9C10437DFDE8814":"QD-ES-8555A9BFE51EA438";
        EsignValues.require(scope.equals(EsignValues.text(mapping,"group_scope_id")),"客户分配入口与正式清单不一致。");
    }
}
