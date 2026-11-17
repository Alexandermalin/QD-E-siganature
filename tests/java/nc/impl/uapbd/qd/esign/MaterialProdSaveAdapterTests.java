package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import nc.vo.bd.material.prod.MaterialProdVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialOrgInfoCommand;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Typed cascade binding tests; no native service, permissions, database or transaction are invoked. */
public final class MaterialProdSaveAdapterTests {
    private MaterialProdSaveAdapterTests() { }
    public static void run() throws Exception {
        MaterialProdSaveAdapter adapter=new MaterialProdSaveAdapter();
        BusinessObjectRef root=new BusinessObjectRef("root","BD_MATERIAL_PROD","P0","CODE0",null,null,"A","ORG");
        BusinessObjectRef child=new BusinessObjectRef("child","BD_MATERIAL_PROD","P1","CODE1","root",null,"G","ORG");
        OperationContext context=context(Arrays.asList(child,root));MaterialProdVO value=value("P0","G","ORG","M0");
        adapter.validate(context,new MaterialOrgInfoCommand(value));
        if(adapter.describeResult(context,value).size()!=2) throw new AssertionError("Both affected owners must be returned");
        reject(adapter,context,value("OTHER","G","ORG","M0"));
        reject(adapter,context,value("P0","OTHER","ORG","M0"));
        reject(adapter,context,value("P0","G","OTHER","M0"));
        BusinessObjectRef secondRoot=new BusinessObjectRef("other","BD_MATERIAL_PROD","P1","CODE1",null,null,"G","ORG");
        reject(adapter,context(Arrays.asList(root,secondRoot)),value);
        BusinessObjectRef foreignTarget=new BusinessObjectRef("child","BD_MATERIAL_PROD","P1","CODE1","root",null,"G","OTHER");
        reject(adapter,context(Arrays.asList(root,foreignTarget)),value);
        try { MaterialProdSaveAdapter.prelock(null,null);throw new AssertionError("Missing context reached native scope lookup"); }
        catch(BusinessException expected) { }
        System.out.println("PASS MaterialProdSaveAdapterTests (typed owner cascade; no NC service or database)");
    }
    private static void reject(MaterialProdSaveAdapter adapter,OperationContext context,MaterialProdVO value) throws Exception {
        try { adapter.validate(context,new MaterialOrgInfoCommand(value));throw new AssertionError("Foreign production draft was accepted"); }
        catch(BusinessException expected) { }
    }
    private static OperationContext context(java.util.List<BusinessObjectRef> refs) {
        return new OperationContext("REQUEST","REQUEST","G","B","10140MAO","物料","PrSave","生产信息保存","UPDATE",null,
            refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static MaterialProdVO value(String pk,String group,String org,String material) {
        MaterialProdVO value=new MaterialProdVO();value.setPk_materialprod(pk);value.setPk_group(group);value.setPk_org(org);
        value.setPk_material(material);value.setPk_marcostclass("COST");value.setClassfeature(UFBoolean.TRUE);return value;
    }
}
