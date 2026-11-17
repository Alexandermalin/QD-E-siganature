package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.List;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SupplierFreezeCommand;

/** Validates target binding without invoking NC services or locks. */
public final class SupplierFreezeAdapterTests {
    private SupplierFreezeAdapterTests() { }
    public static void run() throws Exception {
        SupplierFreezeAdapter adapter=new SupplierFreezeAdapter();SupplierVO supplier=row("S","G","OWNER");
        OperationContext group=context("10140SUG",Arrays.asList(ref("a","S","T1"),ref("b","S","T2")));
        adapter.validate(group,new SupplierFreezeCommand(supplier,Arrays.asList("T1","T2")));
        reject(adapter,group,new SupplierFreezeCommand(supplier,Arrays.asList("T1","T3")));
        reject(adapter,group,new SupplierFreezeCommand(supplier,Arrays.asList("T1","T1")));
        reject(adapter,group,new SupplierFreezeCommand(row("OTHER","G","OWNER"),Arrays.asList("T1","T2")));
        reject(adapter,group,new SupplierFreezeCommand(row("S","OTHER","OWNER"),Arrays.asList("T1","T2")));
        reject(adapter,group,new SupplierFreezeCommand(row("S","G","OTHER"),Arrays.asList("T1","T2")));
        reject(adapter,context("10140SUO",Arrays.asList(ref("a","S","T1"))),new SupplierFreezeCommand(supplier,Arrays.asList("T1")));
        adapter.validate(context("10140SUO",Arrays.asList(ref("a","S","OWNER"))),new SupplierFreezeCommand(supplier,Arrays.asList("OWNER")));
        try { context("10140SUG",Arrays.asList(ref("a","S","T1"),ref("b","S","T1")));throw new AssertionError("Duplicate target accepted"); }
        catch(IllegalArgumentException expected) { }
        System.out.println("PASS SupplierFreezeAdapterTests (target binding; no NC service or database)");
    }
    private static BusinessObjectRef ref(String id,String pk,String target) {
        return new BusinessObjectRef(id,"BD_SUPPLIER",pk,"CODE",null,null,"OWNER",target);
    }
    private static OperationContext context(String node,List<BusinessObjectRef> refs) {
        return new OperationContext("REQ","REQ","G","B",node,"供应商","Freeze","冻结","FREEZE",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static SupplierVO row(String pk,String group,String owner) {
        SupplierVO value=new SupplierVO();value.setPk_supplier(pk);value.setPk_group(group);value.setPk_org(owner);return value;
    }
    private static void reject(SupplierFreezeAdapter adapter,OperationContext context,SupplierFreezeCommand command) throws Exception {
        try { adapter.validate(context,command);throw new AssertionError("Foreign supplier target accepted"); }
        catch(BusinessException expected) { }
    }
}
