package nc.ui.uapbd.qd.esign;

import nc.ui.bd.cust.baseinfo.model.CustBaseInfoModel;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.uif2.LoginContext;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/** Candidate popup wiring and preservation of the parent query's actual operation organization. */
public final class BankAccountStateClientTests {
    private BankAccountStateClientTests() { }
    public static void run() throws Exception {
        config("QdCustomerBankAccountStateCandidateConfig","custbankacc");
        config("QdSupplierBankAccountStateCandidateConfig","supbankacc");
        require(QdSupplierBankAccountBeanDefinition.class.getMethod("getEnableaction").getDeclaringClass()==QdSupplierBankAccountBeanDefinition.class
            && QdSupplierBankAccountBeanDefinition.class.getMethod("getDisableaction").getDeclaringClass()==QdSupplierBankAccountBeanDefinition.class
            && QdSupplierBankAccountBeanDefinition.class.getMethod("getModelservice").getReturnType()==QdBankAccountModelService.class,
            "Supplier generated bean definition did not override native action factories");
        Parent parent=new Parent();LoginContext parentContext=new LoginContext();parentContext.setPk_group("G");parentContext.setNodeCode("10140CUO");parent.setContext(parentContext);
        try { QdPartnerContextOrganizations.operationOrganization(parent);throw new AssertionError("Unregistered operation organization accepted"); }
        catch(BusinessException expected) { }
        Scope scope=new Scope();QdPartnerContextOrganizations.register(parent,scope);
        require("B".equals(QdPartnerContextOrganizations.operationOrganization(parent)),"Popup did not use the queried business unit");
        parentContext.setNodeCode("10140CUG");require("G".equals(QdPartnerContextOrganizations.operationOrganization(parent)),"Group node did not use the group");
        parentContext.setNodeCode("10140CUO");
        scope.organization="C";require("C".equals(QdPartnerContextOrganizations.operationOrganization(parent)),"Popup retained a stale queried organization");
        parentContext.setNodeCode("OTHER");
        try { QdPartnerContextOrganizations.operationOrganization(parent);throw new AssertionError("Unsupported parent node accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS BankAccountStateClientTests (candidate configuration and parent organization registry; NC metadata/session required for popup execution)");
    }
    private static void config(String file,String resource) {
        XmlBeanFactory beans=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/"+file+".xml",UIF2BeanFactory.class.getClassLoader()));
        require(QdBankAccountEnableAction.class.getName().equals(beans.getBeanDefinition("enableaction").getBeanClassName())
            && QdBankAccountDisableAction.class.getName().equals(beans.getBeanDefinition("disableaction").getBeanClassName()),"Bank state actions not replaced");
        require(QdBankAccountModelService.class.getName().equals(beans.getBeanDefinition("modelservice").getBeanClassName()),
            "Native bank save and delete actions did not use the controlled model service");
        Object value=beans.getBeanDefinition("enableaction").getPropertyValues().getPropertyValue("resourceCode").getValue();
        require(value instanceof TypedStringValue && resource.equals(((TypedStringValue)value).getValue()),"Native account permission resource is incorrect");
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static final class Scope implements PartnerOperationOrganization {
        String organization="B";public String requireOperationOrganization() { return organization; }
    }
    private static final class Parent extends CustBaseInfoModel { }
}
