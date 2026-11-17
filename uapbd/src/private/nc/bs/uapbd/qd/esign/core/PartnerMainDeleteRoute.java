package nc.bs.uapbd.qd.esign.core;

import java.util.Map;
import nc.vo.pub.BusinessException;

/** Exact customer/supplier master-delete buttons and their formal action catalog entries. */
public final class PartnerMainDeleteRoute {
    private static final String ADAPTER="nc.itf.uapbd.qd.esign.IMaterialPageDeleteService";
    private PartnerMainDeleteRoute() { }
    public static boolean matches(String node,String button,String operation) {
        return ("10140CUG".equals(node) || "10140CUO".equals(node) || "10140SUG".equals(node) || "10140SUO".equals(node))
            && "Delete".equals(button) && "DELETE".equals(operation);
    }
    public static boolean isMapping(Map<String,Object> mapping) {
        return mapping!=null && ADAPTER.equals(EsignValues.text(mapping,"adapter_class"))
            && matches(EsignValues.text(mapping,"node_code"),EsignValues.text(mapping,"button_code"),"DELETE");
    }
    public static String domain(String node) throws BusinessException {
        if("10140CUG".equals(node) || "10140CUO".equals(node)) return "BD_CUSTOMER";
        if("10140SUG".equals(node) || "10140SUO".equals(node)) return "BD_SUPPLIER";
        throw new BusinessException("客户或供应商删除节点无效。");
    }
    public static String scopeFor(String domain) throws BusinessException {
        if("BD_CUSTOMER".equals(domain)) return "QD-ES-35B5B1E09B5A4A80";
        if("BD_SUPPLIER".equals(domain)) return "QD-ES-83A95678666FC134";
        throw new BusinessException("客户或供应商删除策略域无效。");
    }
    public static String permissionResource(String node) throws BusinessException {
        return "BD_CUSTOMER".equals(domain(node))?"customer":"supplier";
    }
    public static void requireMapping(Map<String,Object> mapping,String operation) throws BusinessException {
        EsignValues.require(isMapping(mapping) && EsignValues.yes(mapping,"validated")
            && matches(EsignValues.text(mapping,"node_code"),EsignValues.text(mapping,"button_code"),operation),
            "客户或供应商删除入口尚未完成接入验证。");
        String domain=domain(EsignValues.text(mapping,"node_code"));
        EsignValues.require(domain.equals(EsignValues.text(mapping,"object_type"))
            && scopeFor(domain).equals(EsignValues.text(mapping,"group_scope_id")),"客户或供应商删除入口与正式清单不一致。");
    }
}
