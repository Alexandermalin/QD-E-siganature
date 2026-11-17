package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerStateCommand;

/** Ensures a signed mixed-owner batch cannot be redirected to other partner rows. */
public final class PartnerStateAdapterTests {
    private PartnerStateAdapterTests() { }
    public static void run() throws Exception {
        PartnerStateAdapter adapter=new PartnerStateAdapter();
        Map<String,SuperVO> customers=new LinkedHashMap<String,SuperVO>();customers.put("a",customer("A","G","OA"));customers.put("b",customer("B","G","OB"));
        OperationContext cust=context("10140CUO","BD_CUSTOMER","Enable","ENABLE","ORG");
        adapter.validate(cust,new PartnerStateCommand(customers));
        Map<String,SuperVO> bad=new LinkedHashMap<String,SuperVO>(customers);bad.put("b",customer("X","G","OB"));reject(adapter,cust,bad);
        bad.put("b",customer("B","G","OA"));reject(adapter,cust,bad);
        bad.put("b",customer("B","OTHER","OB"));reject(adapter,cust,bad);
        reject(adapter,context("10140CUO","BD_CUSTOMER","Disable","ENABLE","ORG"),customers);
        reject(adapter,context("10140CUO","BD_CUSTOMER","Enable","ENABLE","OTHER"),customers);
        Map<String,SuperVO> suppliers=new LinkedHashMap<String,SuperVO>();suppliers.put("a",supplier("A","G","OA"));suppliers.put("b",supplier("B","G","OB"));
        adapter.validate(context("10140SUG","BD_SUPPLIER","Disable","DISABLE","ORG"),new PartnerStateCommand(suppliers));
        reject(adapter,context("10140SUG","BD_SUPPLIER","Disable","DISABLE","ORG"),customers);
        System.out.println("PASS PartnerStateAdapterTests (typed mixed-owner batches; no NC service or database)");
    }
    private static OperationContext context(String node,String type,String button,String operation,String target) {
        BusinessObjectRef a=new BusinessObjectRef("a",type,"A","A",null,null,"OA",target);
        BusinessObjectRef b=new BusinessObjectRef("b",type,"B","B",null,null,"OB",target);
        return new OperationContext("R","R","G","ORG",node,"档案",button,"启停",operation,null,Arrays.asList(a,b),OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static CustomerVO customer(String pk,String group,String owner) { CustomerVO v=new CustomerVO();v.setPk_customer(pk);v.setPk_group(group);v.setPk_org(owner);return v; }
    private static SupplierVO supplier(String pk,String group,String owner) { SupplierVO v=new SupplierVO();v.setPk_supplier(pk);v.setPk_group(group);v.setPk_org(owner);return v; }
    private static void reject(PartnerStateAdapter adapter,OperationContext context,Map<String,SuperVO> rows) throws Exception {
        try { adapter.validate(context,new PartnerStateCommand(rows));throw new AssertionError("Invalid partner state batch accepted"); }
        catch(BusinessException expected) { }
    }
}
