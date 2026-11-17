package nc.vo.ic.qd.esign;

import nc.vo.ic.m45.entity.PurchaseInVO;
import nc.vo.ic.m4c.entity.SaleOutVO;
import nc.vo.uapbd.qd.esign.InventoryNativeCommand;

/** 库存业务类型化命令，明确区分采购入库与销售出库，并保留更新所需的新旧 VO 位置配对关系。 */
public final class InventoryOperationCommand implements InventoryNativeCommand {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;
    /**
     * PURCHASE_IN：采购入库对象类型标识。
     * SALE_OUT：销售出库对象类型标识。
     */
    public static final String PURCHASE_IN = "IC_PURCHASE_IN", SALE_OUT = "IC_SALE_OUT";
    /**
     * INSERT：新增业务动作标识。
     * UPDATE：修改业务动作标识。
     * DELETE：删除业务动作标识。
     * SIGN：库存签字动作标识。
     * CANCEL_SIGN：取消库存签字动作标识。
     */
    public static final String INSERT = "INSERT", UPDATE = "UPDATE", DELETE = "DELETE",
            SIGN = "SIGN", CANCEL_SIGN = "CANCEL_SIGN";
    /** objectType：受控业务对象类型。 */
    private final String objectType;
    /**
     * purchaseBills：采购入库的新单据数组。
     * purchaseOrigins：与采购入库新单据逐位置对应的原始单据数组。
     */
    private final PurchaseInVO[] purchaseBills, purchaseOrigins;
    /**
     * saleBills：销售出库的新单据数组。
     * saleOrigins：与销售出库新单据逐位置对应的原始单据数组。
     */
    private final SaleOutVO[] saleBills, saleOrigins;

    /** 按固定库存类型保存新旧单据数组副本，保持原生位置对应关系；混合类型由适配器拒绝。 */
    private InventoryOperationCommand(String type, PurchaseInVO[] purchases, PurchaseInVO[] oldPurchases,
            SaleOutVO[] sales, SaleOutVO[] oldSales) {
        objectType = type;
        purchaseBills = purchases == null ? null : purchases.clone();
        purchaseOrigins = oldPurchases == null ? null : oldPurchases.clone();
        saleBills = sales == null ? null : sales.clone();
        saleOrigins = oldSales == null ? null : oldSales.clone();
    }
    /** 构造采购入库命令，同时保存新旧单据的对应数组。 */
    public static InventoryOperationCommand purchaseIn(PurchaseInVO[] bills, PurchaseInVO[] origins) {
        return new InventoryOperationCommand(PURCHASE_IN, bills, origins, null, null);
    }
    /** 构造销售出库命令，同时保存新旧单据的对应数组。 */
    public static InventoryOperationCommand saleOut(SaleOutVO[] bills, SaleOutVO[] origins) {
        return new InventoryOperationCommand(SALE_OUT, null, null, bills, origins);
    }
    /** 返回受控业务对象类型。 */
    public String getObjectType() { return objectType; }
    /** 返回采购入库的新单据数组。 */
    public PurchaseInVO[] getPurchaseBills() { return purchaseBills == null ? null : purchaseBills.clone(); }
    /** 返回与采购入库新单据逐位置对应的原始单据数组。 */
    public PurchaseInVO[] getPurchaseOrigins() { return purchaseOrigins == null ? null : purchaseOrigins.clone(); }
    /** 返回销售出库的新单据数组。 */
    public SaleOutVO[] getSaleBills() { return saleBills == null ? null : saleBills.clone(); }
    /** 返回与销售出库新单据逐位置对应的原始单据数组。 */
    public SaleOutVO[] getSaleOrigins() { return saleOrigins == null ? null : saleOrigins.clone(); }
}
