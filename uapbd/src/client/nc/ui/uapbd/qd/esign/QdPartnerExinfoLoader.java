package nc.ui.uapbd.qd.esign;

import nc.ui.bd.uitabextend.ExinfoLoader;
import nc.vo.bd.uitabextend.UITabExtVO;

/** Candidate routing is opt-in and only replaces the exact verified native tab paths. */
public final class QdPartnerExinfoLoader extends ExinfoLoader {
    private boolean candidatePagesEnabled;
    private boolean candidatePageDeletesEnabled;
    public void setCandidatePagesEnabled(boolean enabled) { candidatePagesEnabled=enabled; }
    public void setCandidatePageDeletesEnabled(boolean enabled) { candidatePageDeletesEnabled=enabled; }
    @Override public UITabExtVO[] queryExInfos() {
        UITabExtVO[] nativeEntries=super.queryExInfos();
        if(nativeEntries==null || !candidatePagesEnabled || getCurrent_md_ID()==null) return nativeEntries;
        UITabExtVO[] result=nativeEntries.clone();
        for(int i=0;i<result.length;i++) if(result[i]!=null && getCurrent_md_ID().equals(result[i].getMetadataid()))
            result[i]=replaceVerifiedPath(result[i],true,candidatePageDeletesEnabled);
        return result;
    }
    static UITabExtVO replaceVerifiedPath(UITabExtVO entry,boolean enabled) {
        return replaceVerifiedPath(entry,enabled,false);
    }
    static UITabExtVO replaceVerifiedPath(UITabExtVO entry,boolean enabled,boolean deletesEnabled) {
        if(!enabled || entry==null) return entry;
        String id=entry.getMetadataid(),node=entry.getNodekey(),bean=entry.getExbean_name(),path=entry.getEx_configpath(),replacement=null;
        if("e4f48eaf-5567-4383-a370-a59cb3e8a451".equals(id)) {
            if("finance".equals(node) && "cust_finance".equals(bean) && "nc/ui/bd/cust/finance/cust_finance_config.xml".equals(path)) replacement="QdCustomerFinancePageConfig.xml";
            else if("sale".equals(node) && "cust_sale".equals(bean) && "nc/ui/bd/cust/sale/cust_sale_config.xml".equals(path)) replacement="QdCustomerSalePageConfig.xml";
            else if("creditctl".equals(node) && "cust_creditctl".equals(bean) && "nc/ui/bd/cust/creditctl/cust_creditctl_config.xml".equals(path)) replacement="QdCustomerCreditPageConfig.xml";
        } else if("720dcc7c-ff19-48f4-b9c5-b90906682f45".equals(id)) {
            if("finance".equals(node) && "supplier_finance".equals(bean) && "nc/ui/bd/supplier/config/supplier_finance_config.xml".equals(path)) replacement="QdSupplierFinancePageConfig.xml";
            else if("stock".equals(node) && "supplier_stock".equals(bean) && "nc/ui/bd/supplier/config/supplier_stock_config.xml".equals(path)) replacement="QdSupplierStockPageConfig.xml";
        }
        if(replacement==null) return entry;
        if(deletesEnabled) replacement=replacement.replace("PageConfig.xml","PageDeleteConfig.xml");
        UITabExtVO copy=(UITabExtVO)entry.clone();copy.setEx_configpath("nc/ui/uapbd/qd/esign/"+replacement);return copy;
    }
}
