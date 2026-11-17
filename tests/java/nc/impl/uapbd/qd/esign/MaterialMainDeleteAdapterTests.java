package nc.impl.uapbd.qd.esign;

import java.util.Collections;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nc.ui.bd.pub.extend.PrivateServiceContext;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialCommand;
import nc.vo.uapbd.qd.esign.MaterialMainDeleteResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Tests the fixed material delete route, payload draft checks and result description; no NC or Oracle. */
public final class MaterialMainDeleteAdapterTests {
    private MaterialMainDeleteAdapterTests() { }

    public static void run() throws Exception {
        require(MaterialMainDeleteAdapter.isRoute("10140MAG","Delete","DELETE"),"Group material delete route rejected");
        require(MaterialMainDeleteAdapter.isRoute("10140MAO","Delete","DELETE"),"Unit material delete route rejected");
        require(!MaterialMainDeleteAdapter.isRoute("10140MAO","Save","DELETE"),"Save button must not enter delete adapter");
        require(!MaterialMainDeleteAdapter.isRoute("10140MAO","Delete","INSERT"),"Insert must not enter delete adapter");
        require(!MaterialMainDeleteAdapter.isRoute("10140WARH","Delete","DELETE"),"Warehouse node must not enter material delete adapter");

        MaterialMainDeleteAdapter adapter=new MaterialMainDeleteAdapter();
        OperationContext context=context("DELETE","ORG1");
        adapter.validate(context,command(context));
        describe(adapter,context);

        // 扩展载荷、主键不一致与错误操作都必须在执行前被拒绝。
        reject(adapter,context,commandWithExtensions(context),"Extension payload must be rejected");
        reject(adapter,context,otherMaterial(context),"Material key mismatch must be rejected");
        reject(adapter,context,command(context),"Insert context must be rejected",context("INSERT","ORG1"));

        // 授权对象缺所属组织或类型不符时同样拒绝。
        reject(adapter,context("DELETE",null),command(context("DELETE",null)),"Missing policy owner must be rejected");
        try {
            adapter.validate(context("DELETE","ORG1","BD_STORDOC"),command(context("DELETE","ORG1")));
            throw new AssertionError("Warehouse object type context accepted");
        } catch(BusinessException expected) { }

        // After a parent disappears, also look up new children by the historic parent key.
        List<SnapshotRow> historical=Arrays.asList(snapshot("bd_materialsale","S"),
            snapshot("bd_materialbindle","B"),snapshot("bd_materialconvert","C"));
        Probe probe=new Probe();MaterialMainDeleteAdapter.requireHistoricalRowsGone(historical,probe);
        rejectHistorical(historical,probe,"bd_materialsale","pk_materialsale","S");
        rejectHistorical(historical,probe,"bd_materialbindle","pk_materialsale","S");
        rejectHistorical(historical,probe,"bd_materialbindle","pk_materialbindle","B");
        rejectHistorical(historical,probe,"bd_materialconvert","pk_materialconvert","C");
        try { MaterialMainDeleteAdapter.requireHistoricalRowsGone(Collections.singletonList(snapshot("unknown_table","X")),probe);
            throw new AssertionError("Unknown historical table accepted"); }
        catch(BusinessException expected) { }

        System.out.println("PASS MaterialMainDeleteAdapterTests (fixed route, draft guards, result description; no NC or Oracle)");
    }

    private static SnapshotRow snapshot(String table,String id) {
        return new SnapshotRow(table,id,null,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),
            Collections.<String,String>emptyMap(),true);
    }
    private static void rejectHistorical(List<SnapshotRow> rows,Probe probe,String table,String match,String value) throws Exception {
        String key=table+"\u0000"+match+"\u0000"+value;probe.existing.add(key);
        try { MaterialMainDeleteAdapter.requireHistoricalRowsGone(rows,probe);throw new AssertionError("Historical linked row was missed: "+table); }
        catch(BusinessException expected) { }
        finally { probe.existing.remove(key); }
    }
    private static final class Probe implements MaterialMainDeleteAdapter.RelatedProbe {
        final Set<String> existing=new HashSet<String>();
        public boolean exists(String table,String key,String match,String value) {
            return existing.contains(table+"\u0000"+match+"\u0000"+value);
        }
    }

    private static void describe(MaterialMainDeleteAdapter adapter,OperationContext context) throws Exception {
        MaterialVO material=new MaterialVO();
        material.setPk_material("PK1");
        List<BusinessObjectRef> saved=adapter.describeResult(context,new MaterialMainDeleteResult(material,true));
        require(saved.size()==1 && context.getObjects().get(0).getClientObjectId().equals(saved.get(0).getClientObjectId()),
            "Delete result must identify the signed object");
        try { adapter.describeResult(context,"untyped"); throw new AssertionError("Untyped result accepted"); }
        catch(BusinessException expected) { }
        try {
            MaterialVO other=new MaterialVO();other.setPk_material("OTHER");
            adapter.describeResult(context,new MaterialMainDeleteResult(other,false));
            throw new AssertionError("Foreign material result accepted");
        } catch(BusinessException expected) { }
    }

    private static void reject(MaterialMainDeleteAdapter adapter,OperationContext context,MaterialCommand command,String message)
            throws Exception { reject(adapter,context,command,message,context); }

    private static void reject(MaterialMainDeleteAdapter adapter,OperationContext context,MaterialCommand command,
            String message,OperationContext validationContext) throws Exception {
        try { adapter.validate(validationContext,command); throw new AssertionError(message); }
        catch(BusinessException expected) { }
    }

    private static MaterialCommand otherMaterial(OperationContext context) {
        MaterialVO material=material(context);
        material.setPk_material("OTHER");
        return new MaterialCommand(material);
    }

    private static MaterialCommand commandWithExtensions(OperationContext context) {
        return new MaterialCommand(material(context),Collections.<String,Object>emptyMap(),new PrivateServiceContext());
    }

    private static MaterialCommand command(OperationContext context) {
        return new MaterialCommand(material(context));
    }

    private static MaterialVO material(OperationContext context) {
        MaterialVO material=new MaterialVO();
        material.setPk_material("PK1");
        material.setPk_group(context.getGroupId());
        String owner=context.getObjects().get(0).getPolicyOwnerOrg();
        if(owner!=null) material.setPk_org(owner);
        material.setCode("M-01");
        return material;
    }

    /** 构造受控删除上下文；objectType 传 BD_STORDOC 时用于验证类型拒绝。 */
    private static OperationContext context(String operation,String owner) {
        return context(operation,owner,"BD_MATERIAL");
    }

    private static OperationContext context(String operation,String owner,String objectType) {
        BusinessObjectRef ref=new BusinessObjectRef("C1",objectType,"PK1","M-01",null,null,owner);
        return new OperationContext(requestId(),"B1","G1","ORG1",
            "10140MAG","物料","Delete","删除",operation,"reason",
            Collections.singletonList(ref),OperationContext.OWNER_POLICY_PROTOCOL);
    }

    private static String requestId() {
        StringBuilder id=new StringBuilder("R");
        for(int index=0;index<31;index++) id.append('0');
        return id.toString();
    }

    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
