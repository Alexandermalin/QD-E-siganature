package nc.vo.uapbd.qd.esign;

/** Fixed inventory node/object mapping shared by the client, gateway and typed PF adapters. */
public final class InventoryBillScope {
    public static final String PURCHASE_IN="IC_PURCHASE_IN";
    public static final String SALE_OUT="IC_SALE_OUT";
    public static final String TRANS_IN="IC_TRANS_IN";
    public static final String INITIAL_IN="IC_INITIAL_IN";
    public static final String LOC_ADJUST="IC_LOC_ADJUST";
    public static final String GENERAL_IN="IC_GENERAL_IN";
    public static final String PRODUCT_IN="IC_PRODUCT_IN";
    public static final String SUBCONT_IN="IC_SUBCONT_IN";
    public static final String GENERAL_OUT="IC_GENERAL_OUT";
    public static final String MATERIAL_OUT="IC_MATERIAL_OUT";
    public static final String TRANS_OUT="IC_TRANS_OUT";
    public static final String OUTSOURCE_IN="IC_OUTSOURCE_IN";
    public static final String PRODUCTION_ORDER="IC_PRODUCTION_ORDER";
    public static final String SAPPLY="IC_SAPPLY";
    private InventoryBillScope() { }

    public static String objectType(String node) {
        if("40080602".equals(node)) return PURCHASE_IN;
        if("40080802".equals(node)) return SALE_OUT;
        if("40080618".equals(node)) return TRANS_IN;
        if("40080402".equals(node)) return INITIAL_IN;
        if("40081014".equals(node)) return LOC_ADJUST;
        if("40080608".equals(node)) return GENERAL_IN;
        // The custom production-order node 40080604400 is a separate scope.
        if("40080604".equals(node)) return PRODUCT_IN;
        if("40080606".equals(node)) return SUBCONT_IN;
        if("40080808".equals(node)) return GENERAL_OUT;
        if("40080804".equals(node)) return MATERIAL_OUT;
        if("40080820".equals(node)) return TRANS_OUT;
        if("40080602400".equals(node)) return OUTSOURCE_IN;
        if("40080604400".equals(node)) return PRODUCTION_ORDER;
        if("40080801".equals(node)) return SAPPLY;
        return null;
    }
    public static boolean matches(String node,String button,String operation) {
        if(objectType(node)==null) return false;
        if("Save".equals(button)) return "INSERT".equals(operation)||"UPDATE".equals(operation);
        if("Delete".equals(button)) return "DELETE".equals(operation);
        if(SAPPLY.equals(objectType(node)))
            return ("Commit".equals(button) || "UnCommit".equals(button)
                    || "Approve".equals(button) || "UnApprove".equals(button)
                    || "LineClose".equals(button) || "LineOpen".equals(button)) && "UPDATE".equals(operation);
        if(LOC_ADJUST.equals(objectType(node))) return false;
        if("Sign".equals(button)) return "SIGN".equals(operation);
        return "CancelSign".equals(button)&&"CANCEL_SIGN".equals(operation);
    }
}
