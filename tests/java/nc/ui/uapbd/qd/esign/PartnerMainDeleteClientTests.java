package nc.ui.uapbd.qd.esign;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/** Related archives are signed for policy/audit, but only selected roots are submitted and shown as results. */
public final class PartnerMainDeleteClientTests {
    private PartnerMainDeleteClientTests() { }
    public static void run() throws Exception {
        BusinessObjectRef root=new BusinessObjectRef("signed-root","BD_CUSTOMER","C","C-CODE",null);
        BusinessObjectRef parent=new BusinessObjectRef("signed-parent","BD_CUSTOMER","P","P-CODE","signed-root");
        BusinessObjectRef root2=new BusinessObjectRef("signed-root2","BD_CUSTOMER","D","D-CODE",null);
        OperationContext context=new OperationContext("REQ","REQ","G","ORG","10140CUG","客户","Delete","删除","DELETE",null,
            Arrays.asList(root,parent,root2),OperationContext.OWNER_POLICY_PROTOCOL);
        CustomerVO c=customer("C","G"),d=customer("D","G");Map<String,SuperVO> initial=new HashMap<String,SuperVO>();
        initial.put("C",c);initial.put("D",d);
        require(QdPartnerMainDeleteAction.accepts(context) && QdPartnerMainDeleteAction.sameRows(context,initial,new Object[]{d,c}),
            "Expanded authorization did not match selected root archives");
        require(!QdPartnerMainDeleteAction.sameRows(context,initial,new Object[]{c,c}),"Duplicate UI selection accepted");
        require(!QdPartnerMainDeleteAction.sameRows(context,initial,new Object[]{c,customer("D","OTHER")}),"Cross-group selection accepted");
        MaterialPageDeleteResult receipt=new MaterialPageDeleteResult("REQ","PARTIAL",Arrays.asList(
            new MaterialPageDeleteResult.Unit("signed-root","C","C-CODE",null,"SUCCESS",null),
            new MaterialPageDeleteResult.Unit("signed-root2","D","D-CODE",null,"FAILED",null)));
        QdMaterialOrgInfoDeleteAction.validateReceipt(context,receipt);
        try { QdMaterialOrgInfoDeleteAction.validateReceipt(context,new MaterialPageDeleteResult("REQ","SUCCESS",Arrays.asList(
                new MaterialPageDeleteResult.Unit("signed-parent","P","P-CODE",null,"SUCCESS",null),
                new MaterialPageDeleteResult.Unit("signed-root2","D","D-CODE",null,"SUCCESS",null))));
            throw new AssertionError("A related archive was accepted as a selected delete result"); } catch(BusinessException expected) { }
        SupplierVO supplier=new SupplierVO();supplier.setPk_supplier("S");supplier.setPk_group("G");
        Map<String,SuperVO> supplierRows=new HashMap<String,SuperVO>();supplierRows.put("S",supplier);
        OperationContext sup=new OperationContext("R2","R2","G","ORG","10140SUO","供应商","Delete","删除","DELETE",null,
            Arrays.asList(new BusinessObjectRef("root","BD_SUPPLIER","S","S-CODE",null)),OperationContext.OWNER_POLICY_PROTOCOL);
        require(QdPartnerMainDeleteAction.sameRows(sup,supplierRows,new Object[]{supplier}),"Supplier delete selection not accepted");
        require(!QdPartnerMainDeleteAction.sameRows(sup,supplierRows,new Object[]{c}),"Wrong-domain UI row accepted");
        checkBean("QdCustomerMainDeleteActionsCandidate.xml","deleteAction");
        checkBean("QdSupplierMainDeleteActionsCandidate.xml","list_baseinfodeleteaction");
        System.out.println("PASS PartnerMainDeleteClientTests (root-only results, selection and candidate wiring; no NC session)");
    }
    private static void checkBean(String filename,String name) {
        XmlBeanFactory factory=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/"+filename));
        require(QdPartnerMainDeleteAction.class.getName().equals(factory.getBeanDefinition(name).getBeanClassName()),"Candidate delete bean not wired");
    }
    private static CustomerVO customer(String id,String group) { CustomerVO row=new CustomerVO();row.setPk_customer(id);row.setPk_group(group);return row; }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
