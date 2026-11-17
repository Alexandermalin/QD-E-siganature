package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.CustomerFreezeCommand;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Typed customer batches reject changed rows before any native call or lock. */
public final class CustomerFreezeAdapterTests {
    private CustomerFreezeAdapterTests() { }
    public static void run() throws Exception {
        CustomerFreezeAdapter adapter=new CustomerFreezeAdapter();
        OperationContext context=context("10140CUO","Freeze","FREEZE");
        Map<String,CustomerVO> rows=new LinkedHashMap<String,CustomerVO>();rows.put("a",row("A","G","OWNER_A"));rows.put("b",row("B","G","OWNER_B"));
        adapter.validate(context,new CustomerFreezeCommand(rows));
        Map<String,CustomerVO> bad=new LinkedHashMap<String,CustomerVO>(rows);bad.put("b",row("OTHER","G","OWNER_B"));
        reject(adapter,context,bad);
        bad.put("b",row("B","G","OWNER_A"));reject(adapter,context,bad);
        bad.put("b",row("B","OTHER","OWNER_B"));reject(adapter,context,bad);
        reject(adapter,context("10140SUO","Freeze","FREEZE"),rows);
        reject(adapter,context("10140CUO","Unfreeze","FREEZE"),rows);
        try { CustomerFreezeAdapter.prelock(null,null);throw new AssertionError("Missing context reached native locks"); }
        catch(BusinessException expected) { }
        System.out.println("PASS CustomerFreezeAdapterTests (typed mixed-owner batch; no NC service or database)");
    }
    private static OperationContext context(String node,String button,String operation) {
        BusinessObjectRef a=new BusinessObjectRef("a","BD_CUSTOMER","A","A",null,null,"OWNER_A","ORG");
        BusinessObjectRef b=new BusinessObjectRef("b","BD_CUSTOMER","B","B",null,null,"OWNER_B","ORG");
        return new OperationContext("REQ","REQ","G","ORG",node,"客户",button,"冻结",operation,null,Arrays.asList(a,b),OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static CustomerVO row(String pk,String group,String owner) {
        CustomerVO value=new CustomerVO();value.setPk_customer(pk);value.setPk_group(group);value.setPk_org(owner);return value;
    }
    private static void reject(CustomerFreezeAdapter adapter,OperationContext context,Map<String,CustomerVO> rows) throws Exception {
        try { adapter.validate(context,new CustomerFreezeCommand(rows));throw new AssertionError("Foreign customer batch accepted"); }
        catch(BusinessException expected) { }
    }
}
