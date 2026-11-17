package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import nc.vo.bd.cust.custclass.CustClassVO;
import nc.vo.bd.material.marbasclass.MarBasClassVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFDateTime;
import nc.vo.uapbd.qd.esign.ArchiveTreeDeleteCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Checks delete identity and native leaf scope without NC services or a database. */
public final class ArchiveTreeDeleteAdapterTests {
    private ArchiveTreeDeleteAdapterTests() { }
    public static void run() throws Exception {
        ArchiveTreeDeleteAdapter adapter=new ArchiveTreeDeleteAdapter();
        BusinessObjectRef root=new BusinessObjectRef("root","BD_CUSTCLASS","R","CODE",null,null,"OWNER","B");
        OperationContext context=context("10140CCLO",root);adapter.validate(context,new ArchiveTreeDeleteCommand(row("R","G","OWNER")));
        reject(adapter,context,new ArchiveTreeDeleteCommand(row("OTHER","G","OWNER")));
        reject(adapter,context,new ArchiveTreeDeleteCommand(row("R","OTHER","OWNER")));
        reject(adapter,context,new ArchiveTreeDeleteCommand(row("R","G","OTHER")));
        reject(adapter,context("10140SCLG",root),new ArchiveTreeDeleteCommand(row("R","G","OWNER")));
        BusinessObjectRef material=new BusinessObjectRef("m","BD_MARBASCLASS","M","CODE",null,null,"OWNER","B");
        MarBasClassVO raw=new MarBasClassVO();raw.setPk_marbasclass("M");raw.setPk_group("G");raw.setPk_org("OWNER");
        adapter.validate(context("10140MCLO",material),new ArchiveTreeDeleteCommand(raw));
        reject(adapter,context("10140CCLO",material),new ArchiveTreeDeleteCommand(raw));
        ArchiveTreeDeleteAdapter.requireLeaf(Collections.singletonList(root),root);
        BusinessObjectRef child=new BusinessObjectRef("child","BD_CUSTCLASS","C","CODE","root");
        try { ArchiveTreeDeleteAdapter.requireLeaf(Arrays.asList(root,child),root);throw new AssertionError("Tree delete accepted a descendant"); }
        catch(BusinessException expected) { }
        try { ArchiveTreeDeleteAdapter.prelock(null,null);throw new AssertionError("Missing context reached native locks"); }
        catch(BusinessException expected) { }
        CustClassVO requested=versioned("R","G","OWNER","P","2026-11-11 12:00:00");
        CustClassVO stored=versioned("R","G","OWNER","P","2026-11-11 12:00:00");
        ArchiveTreeDeleteAdapter.requireCurrent(context,requested,stored);
        rejectCurrent(context,requested,versioned("R","G","OWNER","OTHER","2026-11-11 12:00:00"));
        rejectCurrent(context,requested,versioned("R","G","OWNER","P","2026-11-11 12:00:01"));
        rejectCurrent(context,requested,versioned("R","G","OTHER","P","2026-11-11 12:00:00"));
        CustClassVO missingVersion=versioned("R","G","OWNER","P","2026-11-11 12:00:00");missingVersion.setTs(null);
        rejectCurrent(context,requested,missingVersion);
        System.out.println("PASS ArchiveTreeDeleteAdapterTests (typed leaf scope; no NC service or database)");
    }
    private static OperationContext context(String node,BusinessObjectRef ref) {
        return new OperationContext("REQ","REQ","G","B",node,"分类","Delete","删除","DELETE",null,Collections.singletonList(ref),OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static CustClassVO row(String pk,String group,String owner) {
        CustClassVO value=new CustClassVO();value.setPk_custclass(pk);value.setPk_group(group);value.setPk_org(owner);return value;
    }
    private static CustClassVO versioned(String pk,String group,String owner,String parent,String timestamp) {
        CustClassVO value=row(pk,group,owner);value.setCode("CODE");value.setInnercode("INNER");
        value.setParent_id(parent);value.setTs(new UFDateTime(timestamp));return value;
    }
    private static void rejectCurrent(OperationContext context,CustClassVO requested,CustClassVO current) throws Exception {
        try { ArchiveTreeDeleteAdapter.requireCurrent(context,requested,current);throw new AssertionError("Changed tree delete payload accepted"); }
        catch(BusinessException expected) { }
    }
    private static void reject(ArchiveTreeDeleteAdapter adapter,OperationContext context,ArchiveTreeDeleteCommand command) throws Exception {
        try { adapter.validate(context,command);throw new AssertionError("Invalid tree delete accepted"); }
        catch(BusinessException expected) { }
    }
}
