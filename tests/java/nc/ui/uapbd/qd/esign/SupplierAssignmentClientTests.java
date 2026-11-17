package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.SupplierAssignmentResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.core.io.ClassPathResource;

/** The fast dialog must show the complete server-selected targets and conservative receipts. */
public final class SupplierAssignmentClientTests {
    private SupplierAssignmentClientTests() { }
    public static void run() throws Exception {
        String[] suppliers={"C1","C2"},selected={"A","R"};
        OperationContext draft=context(null,Arrays.asList(ref("d1","C1","A"),ref("d2","C1","R"),
            ref("d3","C2","A"),ref("d4","C2","R")));
        List<BusinessObjectRef> objects=Arrays.asList(ref("sa:one-a","C1","A"),ref("sa:one-r","C1","R"),
            ref("sa:two-a","C2","A"),ref("sa:two-r","C2","R"));
        OperationContext prepared=context("REQ",objects);
        QdSupplierFastAssignmentService.validatePrepared(draft,prepared,suppliers,selected);
        try { context("REQ",Arrays.asList(ref("sa:first","C1","A"),ref("sa:duplicate","C1","A")));
            throw new AssertionError("Duplicate supplier and target pair accepted"); }
        catch(IllegalArgumentException expected) { }
        List<BusinessObjectRef> incomplete=new ArrayList<BusinessObjectRef>(objects);incomplete.remove(0);
        rejectPrepared(draft,context("REQ",incomplete),suppliers,selected);
        incomplete=new ArrayList<BusinessObjectRef>(objects);incomplete.set(0,ref("ca:forged","C1","A"));
        rejectPrepared(draft,context("REQ",incomplete),suppliers,selected);
        incomplete=new ArrayList<BusinessObjectRef>(objects);incomplete.set(0,ref("sa:forged","C3","A"));
        rejectPrepared(draft,context("REQ",incomplete),suppliers,selected);
        List<SupplierAssignmentResult.Unit> outcomes=Arrays.asList(unit("C1","A","SUCCESS"),unit("C1","R","ALREADY_ASSIGNED"),
            unit("C2","A","FAILED"),unit("C2","R","SUCCESS"));
        QdSupplierFastAssignmentService.validateReceipt(prepared,new SupplierAssignmentResult("REQ","PARTIAL",outcomes));
        rejectReceipt(prepared,new SupplierAssignmentResult("REQ","SUCCESS",outcomes));
        rejectReceipt(prepared,new SupplierAssignmentResult("OTHER","PARTIAL",outcomes));
        rejectReceipt(prepared,new SupplierAssignmentResult("REQ","PARTIAL",outcomes.subList(0,3)));
        List<SupplierAssignmentResult.Unit> unknown=Arrays.asList(unit("C1","A","UNKNOWN"),unit("C1","R","PENDING"),
            unit("C2","A","SUCCESS"),unit("C2","R","SUCCESS"));
        QdSupplierFastAssignmentService.validateReceipt(prepared,new SupplierAssignmentResult("REQ","UNKNOWN",unknown));
        QdSupplierFastAssignmentService.validateReceipt(prepared,new SupplierAssignmentResult("REQ","RUNNING",unknown));
        XmlBeanFactory beans=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/QdSupplierFastAssignmentActionsCandidate.xml",
            UIF2BeanFactory.class.getClassLoader()));
        require(QdSupplierFastAssignmentService.class.getName().equals(beans.getBeanDefinition("qdSupplierFastAssignmentService").getBeanClassName())
            && beans.containsBeanDefinition("AssignAction") && beans.containsBeanDefinition("CancelAssignAction")
            && "qdSupplierFastAssignmentContext".equals(((RuntimeBeanReference)beans.getBeanDefinition("AssignWizardAction")
                .getPropertyValues().getPropertyValue("assignContext").getValue()).getBeanName()),
            "Supplier fast assignment candidate did not bind both native action bean names");
        QdSupplierFastAssignmentService service=new QdSupplierFastAssignmentService();
        try { service.assignByPks(suppliers,selected,false);throw new AssertionError("Wizard assignment without a native model accepted"); }
        catch(BusinessException expected) { }
        try { service.cancelAssignByCondition("1=1",null,selected);throw new AssertionError("Condition-based cancellation without a native context accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS SupplierAssignmentClientTests (scope and receipt validation; no NC session)");
    }
    private static BusinessObjectRef ref(String id,String supplier,String target) {
        return new BusinessObjectRef(id,"BD_SUPPLIER",supplier,supplier+"-code",null,null,null,target);
    }
    private static SupplierAssignmentResult.Unit unit(String supplier,String target,String state) {
        return new SupplierAssignmentResult.Unit(supplier,supplier+"-code",target,state,null);
    }
    private static OperationContext context(String id,List<BusinessObjectRef> refs) {
        return new OperationContext(id,id,"G","ORG","10140SUG","供应商","Assign","分配","ASSIGN",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static void rejectPrepared(OperationContext draft,OperationContext prepared,String[] suppliers,String[] targets) throws Exception {
        try { QdSupplierFastAssignmentService.validatePrepared(draft,prepared,suppliers,targets);throw new AssertionError("Invalid assignment scope accepted"); }
        catch(BusinessException expected) { }
    }
    private static void rejectReceipt(OperationContext prepared,SupplierAssignmentResult result) throws Exception {
        try { QdSupplierFastAssignmentService.validateReceipt(prepared,result);throw new AssertionError("Invalid assignment receipt accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
