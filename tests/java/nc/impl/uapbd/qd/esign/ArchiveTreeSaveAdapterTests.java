package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import nc.vo.bd.cust.custclass.CustClassVO;
import nc.vo.bd.material.marbasclass.MarBasClassVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveTreeSaveCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Tests the typed tree-save boundary without invoking NC locks or native services. */
public final class ArchiveTreeSaveAdapterTests {
    private ArchiveTreeSaveAdapterTests() { }
    public static void run() throws Exception {
        ArchiveTreeSaveAdapter adapter=new ArchiveTreeSaveAdapter();
        BusinessObjectRef root=new BusinessObjectRef("root","BD_CUSTCLASS","R","CODE",null,"NEW","OLD","B");
        BusinessObjectRef child=new BusinessObjectRef("child","BD_CUSTCLASS","C","CHILD","root",null,"OWNER_C","B");
        OperationContext context=context("10140CCLO",Arrays.asList(root,child));
        adapter.validate(context,new ArchiveTreeSaveCommand(row("R","G","NEW")));
        reject(adapter,context,new ArchiveTreeSaveCommand(row("OTHER","G","NEW")));
        reject(adapter,context,new ArchiveTreeSaveCommand(row("R","OTHER","NEW")));
        reject(adapter,context,new ArchiveTreeSaveCommand(row("R","G","OLD")));
        reject(adapter,context("10140SCLG",Arrays.asList(root,child)),new ArchiveTreeSaveCommand(row("R","G","NEW")));
        BusinessObjectRef foreignChild=new BusinessObjectRef("child","BD_CUSTCLASS","C","CHILD","root","OTHER","OWNER_C","B");
        reject(adapter,context("10140CCLO",Arrays.asList(root,foreignChild)),new ArchiveTreeSaveCommand(row("R","G","NEW")));
        BusinessObjectRef material=new BusinessObjectRef("material","BD_MARBASCLASS","M","CODE",null,null,"OLD","B");
        MarBasClassVO materialValue=new MarBasClassVO();materialValue.setPk_marbasclass("M");materialValue.setPk_group("G");materialValue.setPk_org("OLD");
        adapter.validate(context("10140MCLO",Collections.singletonList(material)),new ArchiveTreeSaveCommand(materialValue));
        reject(adapter,context("10140CCLO",Collections.singletonList(material)),new ArchiveTreeSaveCommand(materialValue));
        BusinessObjectRef added=new BusinessObjectRef("new","BD_CUSTCLASS",null,"CODE",null,"B","B","B");
        OperationContext insert=new OperationContext("REQ","REQ","G","B","10140CCLO","客户分类","Save","保存","INSERT",null,
            Collections.singletonList(added),OperationContext.OWNER_POLICY_PROTOCOL);
        adapter.validate(insert,new ArchiveTreeSaveCommand(row(null,"G","B")));
        reject(adapter,insert,new ArchiveTreeSaveCommand(row("EXISTING","G","B")));
        reject(adapter,insert,new ArchiveTreeSaveCommand(row(null,"G","OLD")));
        try { ArchiveTreeSaveAdapter.prelock(null,null);throw new AssertionError("Missing context reached native locks"); }
        catch(BusinessException expected) { }
        System.out.println("PASS ArchiveTreeSaveAdapterTests (typed branch and owner transfer; no NC service or database)");
    }
    private static CustClassVO row(String pk,String group,String owner) {
        CustClassVO value=new CustClassVO();value.setPk_custclass(pk);value.setPk_group(group);value.setPk_org(owner);return value;
    }
    private static OperationContext context(String node,java.util.List<BusinessObjectRef> refs) {
        return new OperationContext("REQ","REQ","G","B",node,"客户分类","Save","保存","UPDATE",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static void reject(ArchiveTreeSaveAdapter adapter,OperationContext context,ArchiveTreeSaveCommand command) throws Exception {
        try { adapter.validate(context,command);throw new AssertionError("Invalid tree save was accepted"); }
        catch(BusinessException expected) { }
    }
}
