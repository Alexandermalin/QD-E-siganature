package nc.ui.uapbd.qd.esign;

import java.util.Arrays;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/** Candidate action wiring and target-specific response validation without an NC session. */
public final class SupplierFreezeClientTests {
    private SupplierFreezeClientTests() { }
    public static void run() throws Exception {
        checkConfig("QdSupplierGroupFreezeCandidateConfig");checkConfig("QdSupplierOrgFreezeCandidateConfig");
        BusinessObjectRef a=ref("a","T1"),b=ref("b","T2");
        OperationContext request=new OperationContext("R","R","G","G","10140SUG","供应商","Freeze","冻结","FREEZE",null,
            Arrays.asList(a,b),OperationContext.OWNER_POLICY_PROTOCOL);
        ArchiveBatchResult good=new ArchiveBatchResult(null,Arrays.asList(unit("a","S","SUCCESS"),unit("b","S","UNCHANGED")));
        int[] counts=QdSupplierFreezeCoordinator.validateReceipt(request,good);
        require(counts[0]==1 && counts[1]==1,"Changed and unchanged targets were not counted");
        reject(request,new ArchiveBatchResult(null,Arrays.asList(unit("a","S","SUCCESS"),unit("a","S","UNCHANGED"))));
        reject(request,new ArchiveBatchResult(null,Arrays.asList(unit("a","S","SUCCESS"),unit("b","OTHER","UNCHANGED"))));
        reject(request,new ArchiveBatchResult(null,Arrays.asList(unit("a","S","UNCHANGED"),unit("b","S","UNCHANGED"))));
        reject(request,new ArchiveBatchResult(null,Arrays.asList(unit("a","S","SUCCESS"),unit("b","S","FAILED"))));
        System.out.println("PASS SupplierFreezeClientTests (candidate actions and per-target receipt; no NC session)");
    }
    private static void checkConfig(String name) {
        XmlBeanFactory beans=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/"+name+".xml",UIF2BeanFactory.class.getClassLoader()));
        require(QdSupplierFreezeAction.class.getName().equals(beans.getBeanDefinition("freezeAction").getBeanClassName())
            && QdSupplierUnfreezeAction.class.getName().equals(beans.getBeanDefinition("unFreezeAction").getBeanClassName()),
            "Both supplier actions must use the guarded candidate");
    }
    private static BusinessObjectRef ref(String id,String target) { return new BusinessObjectRef(id,"BD_SUPPLIER","S","CODE",null,null,null,target); }
    private static ArchiveBatchResult.Unit unit(String id,String pk,String state) { return new ArchiveBatchResult.Unit(id,pk,state); }
    private static void reject(OperationContext request,ArchiveBatchResult result) throws Exception {
        try { QdSupplierFreezeCoordinator.validateReceipt(request,result);throw new AssertionError("Invalid supplier receipt was accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
