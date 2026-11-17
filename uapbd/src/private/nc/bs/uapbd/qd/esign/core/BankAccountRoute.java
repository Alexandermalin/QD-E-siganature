package nc.bs.uapbd.qd.esign.core;

import java.util.Map;
import nc.vo.pub.BusinessException;

/** Native bank-account popup buttons and their formal customer and supplier actions. */
public final class BankAccountRoute {
    public static final String ADAPTER="nc.itf.uapbd.qd.esign.IBankAccountStateAdapter";
    public static final String SAVE_ADAPTER="nc.itf.uapbd.qd.esign.IBankAccountSaveAdapter";
    public static final String DELETE_ADAPTER="nc.itf.uapbd.qd.esign.IBankAccountDeleteAdapter";
    private BankAccountRoute() { }
    public static boolean matches(String node,String button,String operation) {
        return ("10140CBA".equals(node) || "10140SBA".equals(node)) && adapter(button,operation)!=null;
    }
    private static String adapter(String button,String operation) {
        if("Enable".equals(button) && "ENABLE".equals(operation) || "Disable".equals(button) && "DISABLE".equals(operation))
            return ADAPTER;
        if("Add".equals(button) && "INSERT".equals(operation) || "Edit".equals(button) && "UPDATE".equals(operation))
            return SAVE_ADAPTER;
        return "Delete".equals(button) && "DELETE".equals(operation)?DELETE_ADAPTER:null;
    }
    public static boolean isAdapter(Map<String,Object> mapping) {
        String service=mapping==null?null:EsignValues.text(mapping,"adapter_class");
        return ADAPTER.equals(service) || SAVE_ADAPTER.equals(service) || DELETE_ADAPTER.equals(service);
    }
    public static String selectedDomain(String node) throws BusinessException {
        if("10140CBA".equals(node)) return "BD_CUSTOMER_BANK";
        if("10140SBA".equals(node)) return "BD_SUPPLIER_BANK";
        throw new BusinessException("银行账户入口节点不正确。");
    }
    public static String permissionResource(String node) throws BusinessException {
        if("10140CBA".equals(node)) return "custbankacc";
        if("10140SBA".equals(node)) return "supbankacc";
        throw new BusinessException("银行账户数据权限节点不正确。");
    }
    public static String archiveDomain(String bankDomain) throws BusinessException {
        if("BD_CUSTOMER_BANK".equals(bankDomain)) return "BD_CUSTOMER";
        if("BD_SUPPLIER_BANK".equals(bankDomain)) return "BD_SUPPLIER";
        throw new BusinessException("银行账户关联档案类型不正确。");
    }
    public static void requireMapping(Map<String,Object> mapping,String operation) throws BusinessException {
        EsignValues.require(mapping!=null && EsignValues.yes(mapping,"validated") && isAdapter(mapping)
            && matches(EsignValues.text(mapping,"node_code"),EsignValues.text(mapping,"button_code"),operation)
            && adapter(EsignValues.text(mapping,"button_code"),operation).equals(EsignValues.text(mapping,"adapter_class")),
            "银行账户入口尚未完成接入验证。");
        String domain=selectedDomain(EsignValues.text(mapping,"node_code"));
        EsignValues.require(domain.equals(EsignValues.text(mapping,"object_type"))
            && BankAccountActionScopes.scopeFor(domain,operation).equals(EsignValues.text(mapping,"group_scope_id")),
            "银行账户入口与正式清单动作不一致。");
    }
}
