package nc.bs.uapbd.qd.esign.core;

import java.util.Map;
import nc.vo.pub.BusinessException;

/** Candidate main-tab quick-batch route; it is not a registration or runtime validation. */
public final class PartnerBatchRoute {
    public static final String ADAPTER="nc.itf.uapbd.qd.esign.IPartnerBatchAdapter";
    private PartnerBatchRoute() { }
    public static boolean matches(String node,String button,String operation) {
        return ("10140CUG".equals(node) || "10140CUO".equals(node) || "10140SUG".equals(node) || "10140SUO".equals(node)
                || "10140MAG".equals(node) || "10140MAO".equals(node))
            && "BatchUpdate".equals(button) && "UPDATE".equals(operation);
    }
    public static boolean isAdapter(Map<String,Object> mapping) { return mapping!=null && ADAPTER.equals(EsignValues.text(mapping,"adapter_class")); }
    public static String domain(String node) throws BusinessException {
        if("10140CUG".equals(node) || "10140CUO".equals(node)) return "BD_CUSTOMER";
        if("10140SUG".equals(node) || "10140SUO".equals(node)) return "BD_SUPPLIER";
        if("10140MAG".equals(node) || "10140MAO".equals(node)) return "BD_MATERIAL";
        throw new BusinessException("快速批改节点不正确。");
    }
    public static String scope(String domain) throws BusinessException {
        if("BD_CUSTOMER".equals(domain)) return "QD-ES-56EC7D5EE24A0804";
        if("BD_SUPPLIER".equals(domain)) return "QD-ES-122DB04892CFDA8B";
        if("BD_MATERIAL".equals(domain)) return "QD-ES-4CE7157ECDB4A443";
        throw new BusinessException("快速批改档案类型不正确。");
    }
    public static String permissionResource(String node) throws BusinessException {
        String domain=domain(node);return "BD_CUSTOMER".equals(domain)?"customer":"BD_SUPPLIER".equals(domain)?"supplier":"material";
    }
    public static void requireMapping(Map<String,Object> mapping,String operation) throws BusinessException {
        EsignValues.require(mapping!=null && EsignValues.yes(mapping,"validated") && isAdapter(mapping)
            && matches(EsignValues.text(mapping,"node_code"),EsignValues.text(mapping,"button_code"),operation),
            "基础档案快速批改入口尚未完成接入验证。");
        String domain=domain(EsignValues.text(mapping,"node_code"));
        EsignValues.require(domain.equals(EsignValues.text(mapping,"object_type"))
            && scope(domain).equals(EsignValues.text(mapping,"group_scope_id")),"快速批改入口与正式清单不一致。");
    }
}
