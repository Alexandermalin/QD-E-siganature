package nc.bs.uapbd.qd.esign.core;

import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;

/** Fixed NC organization-tab identities, independent of any table or service named by the client. */
public enum NativeOrgBatchPage {
    CUSTOMER_FINANCE("BD_CUSTOMER","finance_info","111985be-242b-4d59-b316-f71bb0f5df3c","bd_custfinance","pk_custfinance","pk_customer","nc.ui.bd.cust.finance.model.CustFinanceBatchUpdateService"),
    CUSTOMER_SALE("BD_CUSTOMER","sale_info","2f6e0d52-ada9-43d1-b729-ef640b524625","bd_custsale","pk_custsale","pk_customer","nc.ui.bd.cust.sale.model.CustSaleBatchUpdateService"),
    CUSTOMER_CREDIT("BD_CUSTOMER","credit_info","93669a0d-9abf-4510-bb35-385b24527664","bd_custcreditctl","pk_custcreditctl","pk_customer","nc.ui.bd.cust.creditctl.model.CustCreditctlBatchUpdateService"),
    SUPPLIER_FINANCE("BD_SUPPLIER","finance_info","3b6d7504-95e9-4557-94c5-f6b562509420","bd_supfinance","pk_supfinance","pk_supplier","nc.ui.bd.supplier.finance.model.SupFinanceBatchUpdateService"),
    SUPPLIER_STOCK("BD_SUPPLIER","stock_info","92eed375-f4bb-4852-b537-e31c5269bab8","bd_supstock","pk_supstock","pk_supplier","nc.ui.bd.supplier.stock.model.SupStockBatchUpdateService"),
    MATERIAL_FINANCE("BD_MATERIAL","fi_info","59dc3ecc-6a55-4661-967c-0da637b3d440","bd_materialfi","pk_materialfi","pk_material","nc.bs.bd.material.batchupdate.MaterialFiBatchUpdateService"),
    MATERIAL_PURCHASE("BD_MATERIAL","pu_info","6ad42446-6188-48ba-8271-effe4c2ccb51","bd_materialpu","pk_materialpu","pk_material","nc.bs.bd.material.batchupdate.MaterialPuBatchUpdateService"),
    MATERIAL_SALE("BD_MATERIAL","sale_info","9a3fa854-5770-466b-bddb-37dbcb38fb84","bd_materialsale","pk_materialsale","pk_material","nc.bs.bd.material.batchupdate.MaterialSaleBatchUpdateService"),
    MATERIAL_STOCK("BD_MATERIAL","stock_info","13636fc1-0215-4102-a11e-af4be73a05ab","bd_materialstock","pk_materialstock","pk_material","nc.bs.bd.material.batchupdate.MaterialStockBatchUpdateService"),
    MATERIAL_PLAN("BD_MATERIAL","plan_info","8b7d49ab-7a65-4885-bcf1-7df89b84df4b","bd_materialplan","pk_materialplan","pk_material","nc.bs.bd.material.batchupdate.MaterialPlanBatchUpdateService"),
    MATERIAL_PROD("BD_MATERIAL","prod_info","a3133495-8c94-47b4-83d4-0d32bd08ac82","bd_materialprod","pk_materialprod","pk_material","nc.bs.bd.material.batchupdate.MaterialProdBatchUpdateService"),
    MATERIAL_COST("BD_MATERIAL","cost_info","d0ff92c6-1059-46b4-97fd-ed8b3d0a85d9","bd_materialcost","pk_materialcost","pk_material","nc.bs.bd.material.batchupdate.MaterialCostBatchUpdateService"),
    MATERIAL_PFC("BD_MATERIAL","pfc_info","052b52ec-5706-4661-b37f-806ee71b930d","bd_materialpfc","pk_materialpfc","pk_material","nc.bs.bd.material.batchupdate.MaterialPfcBatchUpdateService"),
    MATERIAL_PFCC("BD_MATERIAL","pfcc_info","9dd5ba49-1311-4c6a-af99-905532c84890","bd_materialpfcc","pk_mateprofcost","pk_material","nc.bs.bd.material.batchupdate.MaterialPfccBatchUpdateService");

    public final String domain,tabCode,metadataId,table,key,rootKey,service;
    NativeOrgBatchPage(String domain,String tabCode,String metadataId,String table,String key,String rootKey,String service) {
        this.domain=domain;this.tabCode=tabCode;this.metadataId=metadataId;this.table=table;this.key=key;this.rootKey=rootKey;this.service=service;
    }
    public String rootMetadataId() {
        return "BD_CUSTOMER".equals(domain)?"e4f48eaf-5567-4383-a370-a59cb3e8a451"
            :"BD_SUPPLIER".equals(domain)?"720dcc7c-ff19-48f4-b9c5-b90906682f45":"c7dc0ccd-8872-4eee-8882-160e8f49dfad";
    }
    /** Child VOs explicitly loaded by the corresponding native NC batch service. */
    public String[] childAttributes() {
        switch(this) {
            case CUSTOMER_SALE: return new String[]{"custaddress"};
            case SUPPLIER_STOCK: return new String[]{"supaddress"};
            case MATERIAL_SALE: return new String[]{"materialbindle"};
            case MATERIAL_STOCK: return new String[]{"materialwarh"};
            case MATERIAL_PLAN: return new String[]{"materialrepl"};
            case MATERIAL_COST: return new String[]{"materialcostmode"};
            case MATERIAL_PFC: return new String[]{"materialpfcsub"};
            case MATERIAL_PFCC: return new String[]{"profitcostlist"};
            default: return new String[0];
        }
    }
    /** Concrete native VO expected for this fixed page; never taken from client input. */
    public Class<? extends SuperVO> nativeType() {
        switch(this) {
            case CUSTOMER_FINANCE: return nc.vo.bd.cust.finance.CustFinanceVO.class;
            case CUSTOMER_SALE: return nc.vo.bd.cust.saleinfo.CustsaleVO.class;
            case CUSTOMER_CREDIT: return nc.vo.bd.cust.creditctl.CustCreditCtlVO.class;
            case SUPPLIER_FINANCE: return nc.vo.bd.supplier.finance.SupFinanceVO.class;
            case SUPPLIER_STOCK: return nc.vo.bd.supplier.stock.SupStockVO.class;
            case MATERIAL_FINANCE: return nc.vo.bd.material.fi.MaterialFiVO.class;
            case MATERIAL_PURCHASE: return nc.vo.bd.material.pu.MaterialPuVO.class;
            case MATERIAL_SALE: return nc.vo.bd.material.sale.MaterialSaleVO.class;
            case MATERIAL_STOCK: return nc.vo.bd.material.stock.MaterialStockVO.class;
            case MATERIAL_PLAN: return nc.vo.bd.material.plan.MaterialPlanVO.class;
            case MATERIAL_PROD: return nc.vo.bd.material.prod.MaterialProdVO.class;
            case MATERIAL_COST: return nc.vo.bd.material.cost.MaterialCostVO.class;
            case MATERIAL_PFC: return nc.vo.bd.material.pfc.MaterialPfcVO.class;
            case MATERIAL_PFCC: return nc.vo.bd.material.pfcc.MaterialPFCCVO.class;
            default: throw new IllegalStateException("Unknown native organization page");
        }
    }
    public static NativeOrgBatchPage require(String domain,String tabCode) throws BusinessException {
        for(NativeOrgBatchPage page:values()) if(page.domain.equals(domain) && page.tabCode.equals(tabCode)) return page;
        throw new BusinessException("快速批改组织页签尚未核实。");
    }
}
