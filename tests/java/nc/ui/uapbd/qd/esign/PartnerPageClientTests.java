package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.bs.bd.batchupdate.IBatchUpdateService;
import nc.ui.bd.cust.finance.model.CustFinanceAppModelService;
import nc.ui.bd.supplier.finance.model.SupFinanceAppModelService;
import nc.ui.uif2.components.pagination.IPaginationQueryService;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.bd.uitabextend.UITabExtVO;
import nc.vo.bd.cust.finance.CustFinanceVO;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerOrgPage;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/** Candidate page wiring and strict extension routing; no native client session is started. */
public final class PartnerPageClientTests {
    private PartnerPageClientTests() { }
    public static void run() throws Exception {
        checkRoot("QdCustomerGroupCandidateConfig","QdCustomerListModelDataManager");
        checkRoot("QdCustomerOrgCandidateConfig","QdCustomerListModelDataManager");
        checkRoot("QdSupplierGroupCandidateConfig","QdSupplierListModelDataManager");
        checkRoot("QdSupplierOrgCandidateConfig","QdSupplierListModelDataManager");
        checkMain("QdCustomerGroupMainCandidateConfig",QdCustomerMainModelService.class);
        checkMain("QdCustomerOrgMainCandidateConfig",QdCustomerMainModelService.class);
        checkMain("QdSupplierGroupMainCandidateConfig",QdSupplierMainModelService.class);
        checkMain("QdSupplierOrgMainCandidateConfig",QdSupplierMainModelService.class);
        checkPage("QdCustomerFinancePageConfig","custFinanceSaveAction","custFinanceService");
        checkPage("QdCustomerSalePageConfig","custSaleSaveAction","custSaleAppModelsService");
        checkPage("QdCustomerCreditPageConfig","custCreditctlSaveAction","custCreditctlService");
        checkPage("QdSupplierFinancePageConfig","supFinanceSaveAction","supFinanceService");
        checkPage("QdSupplierStockPageConfig","SupStockSaveAction","SupStockAppModelService");
        checkDelete("QdCustomerFinancePageDeleteConfig","custFinanceDeleteAction");
        checkDelete("QdCustomerSalePageDeleteConfig","custSaleDeleteAction");
        checkDelete("QdCustomerCreditPageDeleteConfig","custCreditctlDeleteAction");
        checkDelete("QdSupplierFinancePageDeleteConfig","supFinanceDeleteAction");
        checkDelete("QdSupplierStockPageDeleteConfig","SupStockDeleteAction");
        UITabExtVO tab=new UITabExtVO();tab.setMetadataid("e4f48eaf-5567-4383-a370-a59cb3e8a451");
        tab.setNodekey("finance");tab.setExbean_name("cust_finance");tab.setEx_configpath("nc/ui/bd/cust/finance/cust_finance_config.xml");
        require(QdPartnerExinfoLoader.replaceVerifiedPath(tab,false)==tab,"Disabled candidate must retain native tab");
        require("nc/ui/uapbd/qd/esign/QdCustomerFinancePageConfig.xml".equals(QdPartnerExinfoLoader.replaceVerifiedPath(tab,true).getEx_configpath()),
            "Verified customer finance tab should resolve to candidate");
        require("nc/ui/uapbd/qd/esign/QdCustomerFinancePageDeleteConfig.xml".equals(QdPartnerExinfoLoader.replaceVerifiedPath(tab,true,true).getEx_configpath())
            && QdPartnerExinfoLoader.replaceVerifiedPath(tab,false,true)==tab,"Page deletion candidate requires both switches");
        tab.setEx_configpath("custom.xml");require(QdPartnerExinfoLoader.replaceVerifiedPath(tab,true)==tab,"Customized tabs must not be replaced");
        QdPartnerOrgInfoModelService service=new QdPartnerOrgInfoModelService(new CustFinanceAppModelService(),"BD_CUSTOMER_FINANCE");
        QdSupplierOrgInfoModelService supplier=new QdSupplierOrgInfoModelService(new SupFinanceAppModelService(),"BD_SUPPLIER_FINANCE");
        require(service instanceof IBatchUpdateService && !(service instanceof IPaginationQueryService)
            && supplier instanceof IBatchUpdateService && supplier instanceof IPaginationQueryService,
            "Each native page service interface must be preserved");
        BusinessObjectRef ref=new BusinessObjectRef("row","BD_CUSTOMER_FINANCE","PK",null,null);
        OperationContext request=new OperationContext(null,null,"G","B","10140CUO","客户","FiSave","保存","UPDATE",null,
            Collections.singletonList(ref),OperationContext.OWNER_POLICY_PROTOCOL);
        require(service.isAvailable(request),"Candidate customer update must accept its exact node and domain");
        OperationContext foreign=new OperationContext(null,null,"G","B","10140SUO","供应商","FiSave","保存","UPDATE",null,
            Collections.singletonList(ref),OperationContext.OWNER_POLICY_PROTOCOL);
        require(!service.isAvailable(foreign),"Customer page cannot be dispatched through supplier node");
        require(MaterialPageDeleteResult.isRoute("10140CUG","FiDelete","DELETE")
            && MaterialPageDeleteResult.isRoute("10140SUO","FiDel","DELETE")
            && !MaterialPageDeleteResult.isRoute("10140SUO","FiDelete","DELETE"),"Partner page delete routes must use native node/button pairs");
        BusinessObjectRef other=new BusinessObjectRef("other","BD_CUSTOMER_FINANCE","PK2",null,null);
        OperationContext deletion=new OperationContext(null,null,"G","B","10140CUO","客户","FiDelete","删除","DELETE",null,
            Arrays.asList(ref,other),OperationContext.OWNER_POLICY_PROTOCOL);
        CustFinanceVO first=row("PK","G","CUSTOMER"),second=row("PK2","G","CUSTOMER");
        Map<String,SuperVO> bound=new LinkedHashMap<String,SuperVO>();bound.put("row",first);bound.put("other",second);
        require(QdPartnerOrgInfoDeleteAction.sameRows(deletion,bound,new Object[]{second,first},"CUSTOMER",PartnerOrgPage.CUSTOMER_FINANCE),
            "Reordered partner rows should match");
        require(!QdPartnerOrgInfoDeleteAction.sameRows(deletion,bound,new Object[]{first,first},"CUSTOMER",PartnerOrgPage.CUSTOMER_FINANCE)
            && !QdPartnerOrgInfoDeleteAction.sameRows(deletion,bound,new Object[]{first,row("PK2","G","OTHER")},"CUSTOMER",PartnerOrgPage.CUSTOMER_FINANCE),
            "Duplicate or foreign partner selections must fail");
        System.out.println("PASS PartnerPageClientTests (candidate XML and strict tab routing; no NC session)");
    }
    private static void checkRoot(String name,String manager) {
        XmlBeanFactory beans=definitions(name);BeanDefinition loader=beans.getBeanDefinition("exinfoloader");
        require(QdPartnerExinfoLoader.class.getName().equals(loader.getBeanClassName())
            && loader.getPropertyValues().getPropertyValue("candidatePagesEnabled")==null,
            "Candidate pages must default to disabled: "+name);
        require(("nc.ui.uapbd.qd.esign."+manager).equals(beans.getBeanDefinition("modelDataManager").getBeanClassName()),
            "Query organization manager is missing: "+name);
    }
    private static void checkPage(String name,String action,String service) {
        XmlBeanFactory beans=definitions(name);
        String serviceClass=name.startsWith("QdSupplier")?QdSupplierOrgInfoModelService.class.getName():QdPartnerOrgInfoModelService.class.getName();
        require(QdPartnerOrgInfoSaveAction.class.getName().equals(beans.getBeanDefinition(action).getBeanClassName())
            && serviceClass.equals(beans.getBeanDefinition(service).getBeanClassName()),
            "Partner page candidate is incomplete: "+name);
    }
    private static void checkMain(String name,Class<?> modelService) {
        XmlBeanFactory beans=definitions(name);
        BeanDefinition service=beans.getBeanDefinition("service");
        require(modelService.getName().equals(service.getBeanClassName())
            && "baseinfoModel".equals(((RuntimeBeanReference)service.getPropertyValues().getPropertyValue("model").getValue()).getBeanName())
            && "baseExtendContext".equals(((RuntimeBeanReference)service.getPropertyValues().getPropertyValue("extendContext").getValue()).getBeanName()),
            "Main-card save must replace the native model service and retain the extension editor: "+name);
    }
    private static void checkDelete(String name,String action) {
        XmlBeanFactory beans=definitions(name);
        require(QdPartnerOrgInfoDeleteAction.class.getName().equals(beans.getBeanDefinition(action).getBeanClassName()),
            "Partner page delete candidate is incomplete: "+name);
        if("custSaleDeleteAction".equals(action)) require(beans.getBeanDefinition(action).getPropertyValues().getPropertyValue("mdOperateCode")!=null,
            "Customer sales native data permission must be retained");
    }
    private static CustFinanceVO row(final String pk,final String group,final String customer) {
        return new CustFinanceVO() {
            @Override public String getPrimaryKey() { return pk; }
            @Override public Object getAttributeValue(String name) {
                return "pk_group".equals(name)?group:"pk_customer".equals(name)?customer:null;
            }
        };
    }
    private static XmlBeanFactory definitions(String name) {
        return new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/"+name+".xml",UIF2BeanFactory.class.getClassLoader()));
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
