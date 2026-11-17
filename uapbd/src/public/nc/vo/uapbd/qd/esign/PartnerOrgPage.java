package nc.vo.uapbd.qd.esign;

import nc.vo.bd.cust.creditctl.CustCreditCtlVO;
import nc.vo.bd.cust.finance.CustFinanceVO;
import nc.vo.bd.cust.saleinfo.CustsaleVO;
import nc.vo.bd.supplier.finance.SupFinanceVO;
import nc.vo.bd.supplier.stock.SupStockVO;
import nc.vo.pub.SuperVO;

/** NC's verified customer and supplier organization pages and native button identities. */
public enum PartnerOrgPage {
    CUSTOMER_FINANCE("10140CUG","10140CUO","FiSave","FiDelete","finance_info","BD_CUSTOMER_FINANCE","bd_custfinance","pk_custfinance","bd_customer","pk_customer",CustFinanceVO.class),
    CUSTOMER_SALE("10140CUG","10140CUO","SaSave","SaDelete","sale_info","BD_CUSTOMER_SALE","bd_custsale","pk_custsale","bd_customer","pk_customer",CustsaleVO.class),
    CUSTOMER_CREDIT("10140CUG","10140CUO","CrSave","CrDelete","credit_info","BD_CUSTOMER_CREDIT","bd_custcreditctl","pk_custcreditctl","bd_customer","pk_customer",CustCreditCtlVO.class),
    SUPPLIER_FINANCE("10140SUG","10140SUO","FiSave","FiDel","finance_info","BD_SUPPLIER_FINANCE","bd_supfinance","pk_supfinance","bd_supplier","pk_supplier",SupFinanceVO.class),
    SUPPLIER_STOCK("10140SUG","10140SUO","StSave","StDel","stock_info","BD_SUPPLIER_STOCK","bd_supstock","pk_supstock","bd_supplier","pk_supplier",SupStockVO.class);

    public final String groupNode,unitNode,saveButton,deleteButton,permission,domain,table,key,rootTable,rootKey;
    public final Class<? extends SuperVO> type;
    PartnerOrgPage(String groupNode,String unitNode,String saveButton,String deleteButton,String permission,String domain,
            String table,String key,String rootTable,String rootKey,Class<? extends SuperVO> type) {
        this.groupNode=groupNode;this.unitNode=unitNode;this.saveButton=saveButton;this.deleteButton=deleteButton;
        this.permission=permission;this.domain=domain;this.table=table;this.key=key;this.rootTable=rootTable;this.rootKey=rootKey;this.type=type;
    }
    public static PartnerOrgPage forAction(String node,String button,String operation) {
        for(PartnerOrgPage page:values()) if((page.groupNode.equals(node)||page.unitNode.equals(node))
                && ("UPDATE".equals(operation)&&page.saveButton.equals(button) || "DELETE".equals(operation)&&page.deleteButton.equals(button))) return page;
        return null;
    }
    public static PartnerOrgPage forDomain(String domain) {
        for(PartnerOrgPage page:values()) if(page.domain.equals(domain)) return page;
        return null;
    }
}
