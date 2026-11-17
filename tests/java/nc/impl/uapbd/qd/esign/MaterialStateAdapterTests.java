package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerStateCommand;

/** Tests typed material state payload and mixed policy ownership, without an NC transaction. */
public final class MaterialStateAdapterTests {
    private MaterialStateAdapterTests() { }
    public static void run() throws Exception {
        MaterialStateAdapter adapter=new MaterialStateAdapter();OperationContext context=context("10140MAO","Enable","ENABLE","ORG");
        Map<String,SuperVO> values=new LinkedHashMap<String,SuperVO>();values.put("a",row("A","G","OA"));values.put("b",row("B","G","OB"));
        adapter.validate(context,new PartnerStateCommand(values));
        Map<String,SuperVO> bad=new LinkedHashMap<String,SuperVO>(values);bad.put("b",row("B","G","OA"));reject(adapter,context,bad);
        bad.put("b",row("X","G","OB"));reject(adapter,context,bad);
        bad.put("b",row("B","OTHER","OB"));reject(adapter,context,bad);
        reject(adapter,context("10140MAO","Disable","ENABLE","ORG"),values);
        reject(adapter,context("10140MAO","Enable","ENABLE","OTHER"),values);
        adapter.validate(context("10140MAG","Disable","DISABLE","ORG"),new PartnerStateCommand(values));
        System.out.println("PASS MaterialStateAdapterTests (mixed-owner material batch; no NC service or database)");
    }
    private static OperationContext context(String node,String button,String operation,String target) {
        BusinessObjectRef a=new BusinessObjectRef("a","BD_MATERIAL","A","A",null,null,"OA",target);
        BusinessObjectRef b=new BusinessObjectRef("b","BD_MATERIAL","B","B",null,null,"OB",target);
        return new OperationContext("R","R","G","ORG",node,"物料",button,"启停",operation,null,Arrays.asList(a,b),OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static MaterialVO row(String pk,String group,String owner) { MaterialVO value=new MaterialVO();value.setPk_material(pk);value.setPk_group(group);value.setPk_org(owner);return value; }
    private static void reject(MaterialStateAdapter adapter,OperationContext context,Map<String,SuperVO> values) throws Exception {
        try { adapter.validate(context,new PartnerStateCommand(values));throw new AssertionError("Invalid material batch accepted"); }
        catch(BusinessException expected) { }
    }
}
