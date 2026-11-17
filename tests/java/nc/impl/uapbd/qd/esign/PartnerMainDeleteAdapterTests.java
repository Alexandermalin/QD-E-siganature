package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** The server candidate accepts one authorized root and only its directly related archives. */
public final class PartnerMainDeleteAdapterTests {
    private PartnerMainDeleteAdapterTests() { }
    public static void run() throws Exception {
        PartnerMainDeleteAdapter adapter=new PartnerMainDeleteAdapter();
        BusinessObjectRef root=ref("root","BD_CUSTOMER","C",null,"OWNER",null);
        BusinessObjectRef parent=ref("parent","BD_CUSTOMER","P","root","OWNER2",null);
        CustomerVO customer=customer("C","G","OWNER");
        OperationContext context=context("10140CUG",Arrays.asList(root,parent));
        adapter.validate(context,customer);
        require(adapter.describeResult(context,customer).size()==2,"Related archive results were omitted");
        reject(adapter,context,customer("OTHER","G","OWNER"));
        reject(adapter,context,customer("C","OTHER","OWNER"));
        reject(adapter,context,customer("C","G","OTHER"));
        reject(adapter,context("10140SUG",Arrays.asList(root,parent)),customer);
        reject(adapter,context("10140CUG",Arrays.asList(root,ref("x","BD_SUPPLIER","C","root","OWNER",null))),customer);
        try { context("10140CUG",Arrays.asList(root,ref("x","BD_CUSTOMER","P","foreign","OWNER",null)));
            throw new AssertionError("Unknown parent accepted"); } catch(IllegalArgumentException expected) { }
        reject(adapter,context("10140CUG",Arrays.asList(root,ref("x","BD_CUSTOMER","P","root","OWNER","OTHER"))),customer);
        reject(adapter,context("10140CUG",Arrays.asList(root,ref("x","BD_CUSTOMER","P",null,"OWNER",null))),customer);
        SupplierVO supplier=new SupplierVO();supplier.setPk_supplier("S");supplier.setPk_group("G");supplier.setPk_org("OWNER");
        BusinessObjectRef sup=ref("supplier","BD_SUPPLIER","S",null,"OWNER",null);
        adapter.validate(context("10140SUO",Collections.singletonList(sup)),supplier);
        reject(adapter,context("10140SUG",Collections.singletonList(sup)),customer);
        try { PartnerMainDeleteAdapter.prelock(null,null);throw new AssertionError("Invalid payload reached database locks"); }
        catch(BusinessException expected) { }
        System.out.println("PASS PartnerMainDeleteAdapterTests (typed root and related scope; no NC or Oracle)");
    }
    private static CustomerVO customer(String id,String group,String owner) {
        CustomerVO row=new CustomerVO();row.setPk_customer(id);row.setPk_group(group);row.setPk_org(owner);return row;
    }
    private static BusinessObjectRef ref(String id,String type,String pk,String parent,String owner,String target) {
        return new BusinessObjectRef(id,type,pk,pk+"-CODE",parent,null,owner,target);
    }
    private static OperationContext context(String node,List<BusinessObjectRef> rows) {
        return new OperationContext("REQ","REQ","G","ORG",node,"档案","Delete","删除","DELETE",null,
            rows,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static void reject(PartnerMainDeleteAdapter adapter,OperationContext context,CustomerVO row) throws Exception {
        try { adapter.validate(context,row);throw new AssertionError("Invalid partner delete accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
