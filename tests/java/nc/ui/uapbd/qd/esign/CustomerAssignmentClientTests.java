package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.CustomerAssignmentResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.core.io.ClassPathResource;

/** The fast dialog must show the complete server-selected targets and conservative receipts. */
public final class CustomerAssignmentClientTests {
    private CustomerAssignmentClientTests() { }
    public static void run() throws Exception {
        String[] customers={"C1","C2"},selected={"A"};
        OperationContext draft=context(null,Arrays.asList(ref("d1","C1","A"),ref("d2","C2","A")));
        List<BusinessObjectRef> objects=Arrays.asList(ref("ca:S:one","C1","A"),ref("ca:D:one","C1","R"),
            ref("ca:S:two","C2","A"),ref("ca:D:two","C2","R"));
        OperationContext prepared=context("REQ",objects);
        QdCustomerFastAssignmentService.validatePrepared(draft,prepared,customers,selected);
        try { context("REQ",Arrays.asList(ref("ca:S:first","C1","A"),ref("ca:S:duplicate","C1","A")));
            throw new AssertionError("Duplicate customer and target pair accepted"); }
        catch(IllegalArgumentException expected) { }
        List<BusinessObjectRef> incomplete=new ArrayList<BusinessObjectRef>(objects);incomplete.remove(0);
        rejectPrepared(draft,context("REQ",incomplete),customers,selected);
        incomplete=new ArrayList<BusinessObjectRef>(objects);incomplete.set(0,ref("ca:D:forged","C1","A"));
        rejectPrepared(draft,context("REQ",incomplete),customers,selected);
        incomplete=new ArrayList<BusinessObjectRef>(objects);incomplete.set(0,ref("ca:S:forged","C3","A"));
        rejectPrepared(draft,context("REQ",incomplete),customers,selected);
        List<CustomerAssignmentResult.Unit> outcomes=Arrays.asList(unit("C1","A","SUCCESS"),unit("C1","R","ALREADY_ASSIGNED"),
            unit("C2","A","FAILED"),unit("C2","R","SUCCESS"));
        QdCustomerFastAssignmentService.validateReceipt(prepared,new CustomerAssignmentResult("REQ","PARTIAL",outcomes));
        rejectReceipt(prepared,new CustomerAssignmentResult("REQ","SUCCESS",outcomes));
        rejectReceipt(prepared,new CustomerAssignmentResult("OTHER","PARTIAL",outcomes));
        rejectReceipt(prepared,new CustomerAssignmentResult("REQ","PARTIAL",outcomes.subList(0,3)));
        List<CustomerAssignmentResult.Unit> unknown=Arrays.asList(unit("C1","A","UNKNOWN"),unit("C1","R","PENDING"),
            unit("C2","A","SUCCESS"),unit("C2","R","SUCCESS"));
        QdCustomerFastAssignmentService.validateReceipt(prepared,new CustomerAssignmentResult("REQ","UNKNOWN",unknown));
        QdCustomerFastAssignmentService.validateReceipt(prepared,new CustomerAssignmentResult("REQ","RUNNING",unknown));
        XmlBeanFactory beans=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/QdCustomerFastAssignmentActionsCandidate.xml",
            UIF2BeanFactory.class.getClassLoader()));
        require(QdCustomerFastAssignmentService.class.getName().equals(beans.getBeanDefinition("qdCustomerFastAssignmentService").getBeanClassName())
            && beans.containsBeanDefinition("assignAction") && beans.containsBeanDefinition("CancelAssignAction")
            && "qdCustomerFastAssignmentContext".equals(((RuntimeBeanReference)beans.getBeanDefinition("AssignWizardAction")
                .getPropertyValues().getPropertyValue("assignContext").getValue()).getBeanName()),
            "Customer fast assignment candidate did not bind both native action bean names");
        QdCustomerFastAssignmentService service=new QdCustomerFastAssignmentService();
        try { service.assignByPks(customers,selected,false);throw new AssertionError("Wizard assignment without a native model accepted"); }
        catch(BusinessException expected) { }
        try { service.cancelAssignByCondition("1=1",null,selected);throw new AssertionError("Condition-based cancellation without a native context accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS CustomerAssignmentClientTests (scope, derived targets and receipt validation; no NC session)");
    }
    private static BusinessObjectRef ref(String id,String customer,String target) {
        return new BusinessObjectRef(id,"BD_CUSTOMER",customer,customer+"-code",null,null,null,target);
    }
    private static CustomerAssignmentResult.Unit unit(String customer,String target,String state) {
        return new CustomerAssignmentResult.Unit(customer,customer+"-code",target,state,null);
    }
    private static OperationContext context(String id,List<BusinessObjectRef> refs) {
        return new OperationContext(id,id,"G","ORG","10140CUG","客户","Assign","分配","ASSIGN",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static void rejectPrepared(OperationContext draft,OperationContext prepared,String[] customers,String[] targets) throws Exception {
        try { QdCustomerFastAssignmentService.validatePrepared(draft,prepared,customers,targets);throw new AssertionError("Invalid assignment scope accepted"); }
        catch(BusinessException expected) { }
    }
    private static void rejectReceipt(OperationContext prepared,CustomerAssignmentResult result) throws Exception {
        try { QdCustomerFastAssignmentService.validateReceipt(prepared,result);throw new AssertionError("Invalid assignment receipt accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
