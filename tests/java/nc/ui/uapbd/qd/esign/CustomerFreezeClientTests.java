package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import nc.bs.bd.service.ErrLogElement;
import nc.bs.bd.service.ValueObjWithErrLog;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/** Candidate action wiring and per-customer response checks, without an NC client session. */
public final class CustomerFreezeClientTests {
    private CustomerFreezeClientTests() { }
    public static void run() throws Exception {
        checkConfig("QdCustomerGroupFreezeCandidateConfig");checkConfig("QdCustomerOrgFreezeCandidateConfig");
        BusinessObjectRef a=new BusinessObjectRef("a","BD_CUSTOMER","A","A",null),b=new BusinessObjectRef("b","BD_CUSTOMER","B","B",null);
        OperationContext request=new OperationContext("R","R","G","B","10140CUO","客户","Freeze","冻结","FREEZE",null,
            Arrays.asList(a,b),OperationContext.OWNER_POLICY_PROTOCOL);
        CustomerVO saved=new CustomerVO();saved.setPk_customer("A");
        ErrLogElement failed=new ErrLogElement();failed.setErrPK("B");
        ArrayList<ErrLogElement> errors=new ArrayList<ErrLogElement>();errors.add(failed);
        ValueObjWithErrLog nativeResult=new ValueObjWithErrLog(new SuperVO[]{saved},errors);
        ArchiveBatchResult result=new ArchiveBatchResult(nativeResult,Arrays.asList(unit("a","A","SUCCESS"),unit("b","B","FAILED")));
        require("PARTIAL".equals(QdCustomerFreezeCoordinator.validateReceipt(request,result)),"Partial customer freeze was not recognized");
        reject(request,new ArchiveBatchResult(nativeResult,Arrays.asList(unit("a","A","SUCCESS"),unit("b","OTHER","FAILED"))));
        reject(request,new ArchiveBatchResult(nativeResult,Arrays.asList(unit("a","A","SUCCESS"),unit("b","B","SUCCESS"))));
        failed.setErrPK("A");reject(request,result);
        System.out.println("PASS CustomerFreezeClientTests (candidate actions and native receipt consistency; no NC session)");
    }
    private static void checkConfig(String name) {
        XmlBeanFactory beans=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/"+name+".xml",UIF2BeanFactory.class.getClassLoader()));
        require(QdCustomerBatchFreezeAction.class.getName().equals(beans.getBeanDefinition("list_freezeaction").getBeanClassName())
            && QdCustomerBatchUnfreezeAction.class.getName().equals(beans.getBeanDefinition("list_unfreezeaction").getBeanClassName())
            && QdCustomerCardFreezeAction.class.getName().equals(beans.getBeanDefinition("card_freezeaction").getBeanClassName())
            && QdCustomerCardUnfreezeAction.class.getName().equals(beans.getBeanDefinition("card_unfreezeaction").getBeanClassName()),
            "Both native list and card freeze actions must use guarded candidates");
    }
    private static ArchiveBatchResult.Unit unit(String id,String pk,String state) { return new ArchiveBatchResult.Unit(id,pk,state); }
    private static void reject(OperationContext request,ArchiveBatchResult result) throws Exception {
        try { QdCustomerFreezeCoordinator.validateReceipt(request,result);throw new AssertionError("Invalid customer freeze receipt was accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
