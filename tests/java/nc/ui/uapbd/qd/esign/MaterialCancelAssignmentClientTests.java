package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialCancelAssignmentResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.core.io.ClassPathResource;

/** The fast dialog must show the complete server-selected targets and conservative receipts. */
public final class MaterialCancelAssignmentClientTests {
    private MaterialCancelAssignmentClientTests() { }
    public static void run() throws Exception {
        String[] materials={"C1","C2"},selected={"A","R"};
        OperationContext draft=context(null,Arrays.asList(ref("d1","C1","A"),ref("d2","C1","R"),
            ref("d3","C2","A"),ref("d4","C2","R")));
        List<BusinessObjectRef> objects=Arrays.asList(ref("mc:one-a","C1","A"),ref("mc:one-r","C1","R"),
            ref("mc:two-a","C2","A"),ref("mc:two-r","C2","R"));
        OperationContext prepared=context("REQ",objects);
        QdMaterialFastCancelAssignmentService.validatePrepared(draft,prepared,materials,selected);
        try { context("REQ",Arrays.asList(ref("mc:first","C1","A"),ref("mc:duplicate","C1","A")));
            throw new AssertionError("Duplicate material and target pair accepted"); }
        catch(IllegalArgumentException expected) { }
        List<BusinessObjectRef> incomplete=new ArrayList<BusinessObjectRef>(objects);incomplete.remove(0);
        rejectPrepared(draft,context("REQ",incomplete),materials,selected);
        incomplete=new ArrayList<BusinessObjectRef>(objects);incomplete.set(0,ref("ca:forged","C1","A"));
        rejectPrepared(draft,context("REQ",incomplete),materials,selected);
        incomplete=new ArrayList<BusinessObjectRef>(objects);incomplete.set(0,ref("mc:forged","C3","A"));
        rejectPrepared(draft,context("REQ",incomplete),materials,selected);
        List<MaterialCancelAssignmentResult.Unit> outcomes=Arrays.asList(unit("C1","A","SUCCESS"),unit("C1","R","ALREADY_UNASSIGNED"),
            unit("C2","A","FAILED"),unit("C2","R","SUCCESS"));
        QdMaterialFastCancelAssignmentService.validateReceipt(prepared,new MaterialCancelAssignmentResult("REQ","PARTIAL",outcomes));
        rejectReceipt(prepared,new MaterialCancelAssignmentResult("REQ","SUCCESS",outcomes));
        rejectReceipt(prepared,new MaterialCancelAssignmentResult("OTHER","PARTIAL",outcomes));
        rejectReceipt(prepared,new MaterialCancelAssignmentResult("REQ","PARTIAL",outcomes.subList(0,3)));
        List<MaterialCancelAssignmentResult.Unit> unknown=Arrays.asList(unit("C1","A","UNKNOWN"),unit("C1","R","PENDING"),
            unit("C2","A","SUCCESS"),unit("C2","R","SUCCESS"));
        QdMaterialFastCancelAssignmentService.validateReceipt(prepared,new MaterialCancelAssignmentResult("REQ","UNKNOWN",unknown));
        QdMaterialFastCancelAssignmentService.validateReceipt(prepared,new MaterialCancelAssignmentResult("REQ","RUNNING",unknown));
        XmlBeanFactory beans=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/QdMaterialFastCancelAssignmentActionsCandidate.xml",
            UIF2BeanFactory.class.getClassLoader()));
        require(QdMaterialFastCancelAssignmentService.class.getName().equals(beans.getBeanDefinition("qdMaterialFastCancelAssignmentService").getBeanClassName())
            && beans.containsBeanDefinition("CancelAssignAction")
            && "qdMaterialFastCancelAssignmentContext".equals(((RuntimeBeanReference)beans.getBeanDefinition("AssignWizardAction")
                .getPropertyValues().getPropertyValue("assignContext").getValue()).getBeanName()),
            "Material fast assignment candidate did not bind both native action bean names");
        QdMaterialFastCancelAssignmentService service=new QdMaterialFastCancelAssignmentService();
        try { service.assignByPks(materials,selected,false);throw new AssertionError("Wizard assignment without a controlled assignment service accepted"); }
        catch(BusinessException expected) { }
        try { service.cancelAssignByCondition("1=1",null,selected);throw new AssertionError("Condition-based cancellation without a native context accepted"); }
        catch(BusinessException expected) { }
        nc.vo.uif2.LoginContext current=new nc.vo.uif2.LoginContext(),other=new nc.vo.uif2.LoginContext();
        QdMaterialAssignService delegate=new QdMaterialAssignService();delegate.setContext(other);
        service.setContext(current);service.setAssignService(delegate);
        try { service.assignByCondition("1=1",nc.itf.bd.pub.assign.AssignStatus.ALL,selected);
            throw new AssertionError("Wizard delegated condition query to a different page context"); }
        catch(BusinessException expected) { }
        try { service.assignByPks(materials,selected,false);
            throw new AssertionError("Wizard delegated explicit assignment to a different page context"); }
        catch(BusinessException expected) { }
        System.out.println("PASS MaterialCancelAssignmentClientTests (scope and receipt validation; no NC session)");
    }
    private static BusinessObjectRef ref(String id,String material,String target) {
        return new BusinessObjectRef(id,"BD_MATERIAL",material,material+"-code",null,null,null,target);
    }
    private static MaterialCancelAssignmentResult.Unit unit(String material,String target,String state) {
        return new MaterialCancelAssignmentResult.Unit(material,material+"-code",target,state,null);
    }
    private static OperationContext context(String id,List<BusinessObjectRef> refs) {
        return new OperationContext(id,id,"G","ORG","10140MAG","物料","CancelAssign","取消分配","CANCEL_ASSIGN",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static void rejectPrepared(OperationContext draft,OperationContext prepared,String[] materials,String[] targets) throws Exception {
        try { QdMaterialFastCancelAssignmentService.validatePrepared(draft,prepared,materials,targets);throw new AssertionError("Invalid assignment scope accepted"); }
        catch(BusinessException expected) { }
    }
    private static void rejectReceipt(OperationContext prepared,MaterialCancelAssignmentResult result) throws Exception {
        try { QdMaterialFastCancelAssignmentService.validateReceipt(prepared,result);throw new AssertionError("Invalid assignment receipt accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}

