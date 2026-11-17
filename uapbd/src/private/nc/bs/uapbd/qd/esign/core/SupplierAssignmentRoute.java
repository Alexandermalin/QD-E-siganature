package nc.bs.uapbd.qd.esign.core;

import java.util.Map;
import nc.itf.uapbd.qd.esign.ISupplierAssignmentService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.SupplierAssignmentResult;

/** Candidate mappings are pinned to the two supplier actions in the formal operation list. */
public final class SupplierAssignmentRoute {
    private SupplierAssignmentRoute() { }
    public static boolean isAdapter(Map<String,Object> mapping) {
        return mapping!=null && ISupplierAssignmentService.class.getName().equals(EsignValues.text(mapping,"adapter_class"));
    }
    public static void requireMapping(Map<String,Object> mapping,String operation) throws BusinessException {
        EsignValues.require(mapping!=null && EsignValues.yes(mapping,"validated") && isAdapter(mapping)
            && SupplierAssignmentResult.isRoute(EsignValues.text(mapping,"node_code"),EsignValues.text(mapping,"button_code"),operation)
            && "BD_SUPPLIER".equals(EsignValues.text(mapping,"object_type")),"供应商分配入口尚未完成接入验证。");
        String scope="ASSIGN".equals(operation)?"QD-ES-FD8282A38B9C4E89":"QD-ES-309E9ED628D45CDF";
        EsignValues.require(scope.equals(EsignValues.text(mapping,"group_scope_id")),"供应商分配入口与正式清单不一致。");
    }
}
