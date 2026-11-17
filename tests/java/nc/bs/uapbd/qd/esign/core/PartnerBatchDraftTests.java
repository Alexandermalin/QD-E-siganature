package nc.bs.uapbd.qd.esign.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;

/** The batch draft is copied across the client boundary and routes only to the matching formal action. */
public final class PartnerBatchDraftTests {
    private PartnerBatchDraftTests() { }
    public static void run() throws Exception {
        String[] fields={"name"};java.util.Date date=new java.util.Date(1001L);
        Map<String,Object> values=new HashMap<String,Object>();values.put("name",date);
        PartnerBatchDraft draft=new PartnerBatchDraft("TAB","base","name",fields,values);
        fields[0]="modified";date.setTime(9000L);
        require("name".equals(draft.getSelected()[0]) && ((java.util.Date)draft.getValues().get("name")).getTime()==1001L,
            "Client mutation changed the proposed batch payload");
        ((java.util.Date)draft.getValues().get("name")).setTime(5000L);
        require(((java.util.Date)draft.getValues().get("name")).getTime()==1001L,"Returned mutable values changed the draft");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ObjectOutputStream out=new ObjectOutputStream(bytes);out.writeObject(draft);out.close();
        PartnerBatchDraft restored=(PartnerBatchDraft)new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
        require("TAB".equals(restored.getTabId()) && ((java.util.Date)restored.getValues().get("name")).getTime()==1001L,
            "Batch draft did not survive serialization");
        require(restored.getTargetOrgs().length==0,"A main-tab draft acquired organization targets");
        String[] targets={"T1","T2"};
        PartnerBatchDraft orgDraft=new PartnerBatchDraft("TAB","finance_info","name",new String[]{"name"},draft.getValues(),targets);
        targets[0]="MUTATED";orgDraft.getTargetOrgs()[1]="MUTATED";
        require(Arrays.equals(orgDraft.getTargetOrgs(),new String[]{"T1","T2"}),"Organization targets were not frozen");
        bytes=new ByteArrayOutputStream();out=new ObjectOutputStream(bytes);out.writeObject(orgDraft);out.close();
        restored=(PartnerBatchDraft)new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
        require(Arrays.equals(restored.getTargetOrgs(),new String[]{"T1","T2"}),"Organization targets did not survive serialization");
        require(!restored.hasPageSelection() && restored.getSelectedPageIds()==null,"A legacy organization draft acquired a page slice");
        nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page[] pages={page("P1"),page("P2")};
        PartnerBatchDraft sliced=new PartnerBatchDraft("TAB","finance_info","name",new String[]{"name"},draft.getValues(),
            new String[]{"T1"},pages);
        pages[0]=page("MUTATED");sliced.getSelectedPages()[1]=page("MUTATED");sliced.getSelectedPageIds()[1]="MUTATED";
        require(sliced.hasPageSelection() && Arrays.equals(sliced.getSelectedPageIds(),new String[]{"P1","P2"}),
            "Planned page identities were not frozen");
        bytes=new ByteArrayOutputStream();out=new ObjectOutputStream(bytes);out.writeObject(sliced);out.close();
        restored=(PartnerBatchDraft)new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
        require(restored.hasPageSelection() && Arrays.equals(restored.getSelectedPageIds(),new String[]{"P1","P2"}),
            "Planned page identities did not survive serialization");
        PartnerBatchDraft noPages=new PartnerBatchDraft("TAB","finance_info","name",new String[]{"name"},draft.getValues(),
            new String[]{"T1"},new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page[0]);
        require(noPages.hasPageSelection() && noPages.getSelectedPageIds().length==0,"An explicit no-op slice was lost");
        try { new PartnerBatchDraft("TAB","base","name",new String[]{"name"},draft.getValues(),null,new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page[0]);
            throw new AssertionError("Main tab accepted a page slice"); } catch(IllegalArgumentException expected) { }
        try { new PartnerBatchDraft("TAB","finance_info","name",new String[]{"name"},draft.getValues(),new String[]{"T1"},
                new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page[]{page("P1"),page("P1")});
            throw new AssertionError("Duplicate planned page accepted"); } catch(IllegalArgumentException expected) { }
        try { PartnerBatchPayload.resolve("BD_CUSTOMER",restored);throw new AssertionError("Unfinished organization execution was accepted as a main tab"); }
        catch(BusinessException expected) { }
        try { new PartnerBatchDraft("TAB","finance_info","name",new String[]{"name"},draft.getValues(),new String[]{"T1","T1"});
            throw new AssertionError("Duplicate organization target accepted"); } catch(IllegalArgumentException expected) { }
        OperationContext context=new OperationContext(null,null,"G","ORG","10140CUG","客户","BatchUpdate","批改","UPDATE",null,
            Arrays.asList(new BusinessObjectRef("A","BD_CUSTOMER","A","A",null)),OperationContext.OWNER_POLICY_PROTOCOL,null,null,null,draft);
        require(context.withRequest("R","B","G").getPartnerBatchDraft()==draft,"Request copy dropped the batch draft");
        Map<String,Object> mapping=EsignValues.row("validated","Y","adapter_class",PartnerBatchRoute.ADAPTER,
            "node_code","10140CUG","button_code","BatchUpdate","object_type","BD_CUSTOMER","group_scope_id",PartnerBatchRoute.scope("BD_CUSTOMER"));
        PartnerBatchRoute.requireMapping(mapping,"UPDATE");mapping.put("group_scope_id",PartnerBatchRoute.scope("BD_SUPPLIER"));
        try { PartnerBatchRoute.requireMapping(mapping,"UPDATE");throw new AssertionError("Wrong formal batch action accepted"); }
        catch(BusinessException expected) { }
        Map<String,Object> material=EsignValues.row("validated","Y","adapter_class",PartnerBatchRoute.ADAPTER,
            "node_code","10140MAO","button_code","BatchUpdate","object_type","BD_MATERIAL",
            "group_scope_id","QD-ES-4CE7157ECDB4A443");
        PartnerBatchRoute.requireMapping(material,"UPDATE");
        require("material".equals(PartnerBatchRoute.permissionResource("10140MAO")),"Material batch uses wrong native data permission");
        material.put("group_scope_id",PartnerBatchRoute.scope("BD_SUPPLIER"));
        try { PartnerBatchRoute.requireMapping(material,"UPDATE");throw new AssertionError("Wrong material batch scope accepted"); }
        catch(BusinessException expected) { }
        values.put("name",new byte[]{1});
        try { new PartnerBatchDraft("TAB","base","name",new String[]{"name"},values);throw new AssertionError("Unsupported batch value accepted"); }
        catch(IllegalArgumentException expected) { }
        nc.bs.uapbd.qd.esign.persistence.BatchCountCapacityTests.run();
        nc.bs.uapbd.qd.esign.persistence.BatchPreparedObjectsTests.run();
        nc.bs.uapbd.qd.esign.persistence.BatchSignatureAttemptTests.run();
        nc.impl.uapbd.qd.esign.BatchPreparationDeadlineTests.run();
        System.out.println("PASS PartnerBatchDraftTests (immutable RPC draft and candidate formal route; no NC or Oracle)");
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page page(String id) {
        return new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page(id,"A","T1");
    }
}
