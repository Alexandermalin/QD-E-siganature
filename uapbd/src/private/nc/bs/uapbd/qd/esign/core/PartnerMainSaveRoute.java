package nc.bs.uapbd.qd.esign.core;

import java.util.Map;
import nc.vo.pub.BusinessException;

/** Exact candidate customer/supplier main-save routes and formal catalog policy IDs. */
public final class PartnerMainSaveRoute {
    public static final String ADAPTER="nc.itf.uapbd.qd.esign.IPartnerMainSaveAdapter";
    private PartnerMainSaveRoute() { }
    public static boolean matches(String node,String button,String operation) {
        return ("10140CUG".equals(node) || "10140CUO".equals(node) || "10140SUG".equals(node) || "10140SUO".equals(node))
            && "Save".equals(button) && ("UPDATE".equals(operation)
                || "INSERT".equals(operation) && ("10140CUG".equals(node) || "10140SUG".equals(node)));
    }
    public static boolean isAdapter(Map<String,Object> mapping) { return mapping!=null && ADAPTER.equals(EsignValues.text(mapping,"adapter_class")); }
    public static String selectedDomain(String node) throws BusinessException {
        if("10140CUG".equals(node) || "10140CUO".equals(node)) return "BD_CUSTOMER";
        if("10140SUG".equals(node) || "10140SUO".equals(node)) return "BD_SUPPLIER";
        throw new BusinessException("客户或供应商保存节点无效。");
    }
    public static String scopeFor(String domain) throws BusinessException {
        if("BD_CUSTOMER".equals(domain)) return "QD-ES-E576276F26109C16";
        if("BD_SUPPLIER".equals(domain)) return "QD-ES-E8C69ACAF76ECABF";
        throw new BusinessException("客户或供应商保存策略域无效。");
    }
    public static String permissionResource(String node) throws BusinessException {
        return "BD_CUSTOMER".equals(selectedDomain(node))?"customer":"supplier";
    }
    public static void requireMapping(Map<String,Object> mapping,String operation) throws BusinessException {
        EsignValues.require(mapping!=null && EsignValues.yes(mapping,"validated") && isAdapter(mapping)
            && matches(EsignValues.text(mapping,"node_code"),EsignValues.text(mapping,"button_code"),operation),
            "客户或供应商保存入口尚未完成接入验证。");
        String domain=selectedDomain(EsignValues.text(mapping,"node_code"));
        EsignValues.require(domain.equals(EsignValues.text(mapping,"object_type"))
            && scopeFor(domain).equals(EsignValues.text(mapping,"group_scope_id")),"客户或供应商保存入口与正式清单不一致。");
    }
}
