package nc.bs.uapbd.qd.esign.core;

import java.util.Map;
import nc.itf.uapbd.qd.esign.IMaterialCancelAssignmentService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.MaterialCancelAssignmentResult;

/** Candidate mapping is pinned to the material cancellation in the formal operation list. */
public final class MaterialCancelAssignmentRoute {
    private MaterialCancelAssignmentRoute() { }
    public static boolean isAdapter(Map<String,Object> mapping) {
        return mapping!=null && IMaterialCancelAssignmentService.class.getName().equals(EsignValues.text(mapping,"adapter_class"));
    }
    public static void requireMapping(Map<String,Object> mapping,String operation) throws BusinessException {
        EsignValues.require(mapping!=null && EsignValues.yes(mapping,"validated") && isAdapter(mapping)
            && MaterialCancelAssignmentResult.isRoute(EsignValues.text(mapping,"node_code"),EsignValues.text(mapping,"button_code"),operation)
            && "BD_MATERIAL".equals(EsignValues.text(mapping,"object_type")),"物料分配入口尚未完成接入验证。");
        EsignValues.require("QD-ES-CE419A6EAAED8307".equals(EsignValues.text(mapping,"group_scope_id")),
            "物料取消分配入口与正式清单不一致。");
    }
}

