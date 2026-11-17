package nc.vo.uapbd.qd.esign;

import nc.vo.bd.material.cost.MaterialCostVO;
import nc.vo.bd.material.cost.MaterialCostmodeVO;
import nc.vo.bd.material.fi.MaterialFiVO;
import nc.vo.bd.material.plan.MaterialPlanVO;
import nc.vo.bd.material.prod.MaterialProdVO;
import nc.vo.bd.material.pu.MaterialPuVO;
import nc.vo.bd.material.sale.MaterialSaleVO;
import nc.vo.bd.material.sale.MaterialBindleVO;
import nc.vo.bd.material.stock.MaterialStockVO;
import nc.vo.bd.material.stock.MaterialReplVO;
import nc.vo.bd.material.stock.MaterialWarhVO;
import nc.vo.pub.SuperVO;

/** The fixed NC material page identities shared by policy, gateway and client code. */
public enum MaterialOrgPage {
    FINANCE("FiSave", "财务信息-保存", "fi_info", "BD_MATERIAL_FI", "bd_materialfi", "pk_materialfi", null, null, MaterialFiVO.class, null),
    PURCHASE("PuSave", "采购信息-保存", "pu_info", "BD_MATERIAL_PU", "bd_materialpu", "pk_materialpu", null, null, MaterialPuVO.class, null),
    SALE("SaSave", "销售信息-保存", "sale_info", "BD_MATERIAL_SALE", "bd_materialsale", "pk_materialsale", "bd_materialbindle", "pk_materialbindle", MaterialSaleVO.class, MaterialBindleVO.class),
    STOCK("StSave", "库存信息-保存", "stock_info", "BD_MATERIAL_STOCK", "bd_materialstock", "pk_materialstock", "bd_materialwarh", "pk_materialwarh", MaterialStockVO.class, MaterialWarhVO.class),
    PLAN("PlSave", "计划信息-保存", "plan_info", "BD_MATERIAL_PLAN", "bd_materialplan", "pk_materialplan", "bd_materialrepl", "pk_materialrepl", MaterialPlanVO.class, MaterialReplVO.class),
    PROD("PrSave", "生产信息-保存", "prod_info", "BD_MATERIAL_PROD", "bd_materialprod", "pk_materialprod", null, null, MaterialProdVO.class, null),
    COST("CoSave", "成本信息-保存", "cost_info", "BD_MATERIAL_COST", "bd_materialcost", "pk_materialcost", "bd_materialcostmod", "pk_materialcostmod", MaterialCostVO.class, MaterialCostmodeVO.class);

    public final String button, label, permission, domain, table, key, childTable, childKey;
    public final String deleteButton;
    public final Class<? extends SuperVO> type;
    public final Class<? extends SuperVO> childType;

    MaterialOrgPage(String button, String label, String permission, String domain, String table, String key,
            String childTable, String childKey, Class<? extends SuperVO> type, Class<? extends SuperVO> childType) {
        this.button = button;
        this.deleteButton = button.substring(0, button.length() - "Save".length()) + "Delete";
        this.label = label;
        this.permission = permission;
        this.domain = domain;
        this.table = table;
        this.key = key;
        this.childTable = childTable;
        this.childKey = childKey;
        this.type = type;
        this.childType = childType;
    }

    public static MaterialOrgPage forButton(String button) {
        for (MaterialOrgPage page : values()) if (page.button.equals(button)) return page;
        return null;
    }

    public static MaterialOrgPage forDomain(String domain) {
        for (MaterialOrgPage page : values()) if (page.domain.equals(domain)) return page;
        return null;
    }

    public static MaterialOrgPage forDeleteButton(String button) {
        for (MaterialOrgPage page : values()) if (page.deleteButton.equals(button)) return page;
        return null;
    }

    /** Production saves can cascade to other materials and need a separate impact-aware adapter. */
    public boolean supportsUpdate() { return this != PROD; }

    /** Native child arrays are scoped by the verified organization-page primary key. */
    public SuperVO[] children(SuperVO value) {
        switch (this) {
            case SALE: return ((MaterialSaleVO)value).getMaterialbindle();
            case STOCK: return ((MaterialStockVO)value).getMaterialwarh();
            case PLAN: return ((MaterialPlanVO)value).getMaterialrepl();
            case COST: return ((MaterialCostVO)value).getMaterialcostmode();
            default: return null;
        }
    }
}
