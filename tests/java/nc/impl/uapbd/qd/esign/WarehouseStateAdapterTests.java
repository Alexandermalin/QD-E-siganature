package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.vo.bd.stordoc.StordocVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerStateCommand;

/** Tests typed warehouse state payloads and per-row owner identity without NC services. */
public final class WarehouseStateAdapterTests {
    private WarehouseStateAdapterTests() { }
    public static void run() throws Exception {
        WarehouseStateAdapter adapter=new WarehouseStateAdapter();OperationContext context=context("Enable","ENABLE","ORG");
        Map<String,SuperVO> values=new LinkedHashMap<String,SuperVO>();values.put("a",row("A","G","OA"));values.put("b",row("B","G","OB"));
        adapter.validate(context,new PartnerStateCommand(values));
        Map<String,SuperVO> bad=new LinkedHashMap<String,SuperVO>(values);bad.put("b",row("B","G","OA"));reject(adapter,context,bad);
        bad.put("b",row("X","G","OB"));reject(adapter,context,bad);
        bad.put("b",row("B","OTHER","OB"));reject(adapter,context,bad);
        reject(adapter,context("Disable","ENABLE","ORG"),values);reject(adapter,context("Enable","ENABLE","OTHER"),values);
        adapter.validate(context("Disable","DISABLE","ORG"),new PartnerStateCommand(values));
        System.out.println("PASS WarehouseStateAdapterTests (mixed-owner warehouse batch; no NC service or database)");
    }
    private static OperationContext context(String button,String operation,String target) {
        BusinessObjectRef a=new BusinessObjectRef("a","BD_STORDOC","A","A",null,null,"OA",target);
        BusinessObjectRef b=new BusinessObjectRef("b","BD_STORDOC","B","B",null,null,"OB",target);
        return new OperationContext("R","R","G","ORG","10140WARH","仓库",button,"启停",operation,null,Arrays.asList(a,b),OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static StordocVO row(String pk,String group,String owner) { StordocVO value=new StordocVO();value.setPk_stordoc(pk);value.setPk_group(group);value.setPk_org(owner);return value; }
    private static void reject(WarehouseStateAdapter adapter,OperationContext context,Map<String,SuperVO> values) throws Exception {
        try { adapter.validate(context,new PartnerStateCommand(values));throw new AssertionError("Invalid warehouse batch accepted"); }
        catch(BusinessException expected) { }
    }
}
