package nc.impl.uapbd.qd.esign;

import nc.vo.bd.stordoc.AgentstoreVO;
import nc.vo.bd.stordoc.StordocVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.VOStatus;
import java.util.Collections;
import java.util.Set;

/** Physical row identities are taken from the installed NC65 VO metadata. */
public final class WarehouseSaveAdapterTests {
    private WarehouseSaveAdapterTests() { }
    public static void run() throws Exception {
        StordocVO warehouse=new StordocVO();AgentstoreVO child=new AgentstoreVO();
        require("bd_stordoc".equals(warehouse.getTableName()) && "pk_stordoc".equals(warehouse.getPKFieldName()),
            "Warehouse identity disagrees with NC VO");
        require("bd_agentstore".equals(child.getTableName()) && "pk_agentstore".equals(child.getPKFieldName())
            && "pk_stordoc".equals(child.getParentPKFieldName()),"Warehouse child relationship disagrees with NC VO");
        try { new WarehouseSaveAdapter().validate(null,null);throw new AssertionError("Missing warehouse context was accepted"); }
        catch(BusinessException expected) { }
        try { WarehouseSaveAdapter.prelock(null,null);throw new AssertionError("Missing context reached native warehouse locks"); }
        catch(BusinessException expected) { }
        final Set<String> taken=Collections.singleton("FOREIGN");
        MaterialOrgInfoOperationAdapter.ChildLookup lookup=new MaterialOrgInfoOperationAdapter.ChildLookup() {
            public boolean exists(String pk) { return taken.contains(pk); }
        };
        WarehouseSaveAdapter.validateChildPayload(new AgentstoreVO[]{row("OWNED","WAREHOUSE",VOStatus.UPDATED)},
            "WAREHOUSE",Collections.singleton("OWNED"),lookup);
        WarehouseSaveAdapter.validateChildPayload(new AgentstoreVO[]{row(null,null,VOStatus.NEW)},null,Collections.<String>emptySet(),lookup);
        reject(new AgentstoreVO[]{row("FOREIGN","WAREHOUSE",VOStatus.UPDATED)},"WAREHOUSE",Collections.singleton("OWNED"),lookup);
        reject(new AgentstoreVO[]{row("FOREIGN",null,VOStatus.NEW)},null,Collections.<String>emptySet(),lookup);
        reject(new AgentstoreVO[]{row("NEW",null,VOStatus.NEW),row("NEW",null,VOStatus.NEW)},null,Collections.<String>emptySet(),lookup);
        reject(new AgentstoreVO[]{row(null,"OTHER",VOStatus.NEW)},null,Collections.<String>emptySet(),lookup);
        System.out.println("PASS WarehouseSaveAdapterTests (native row identities; no NC service or database)");
    }
    private static AgentstoreVO row(String pk,String parent,int status) {
        AgentstoreVO row=new AgentstoreVO();row.setPk_agentstore(pk);row.setPk_stordoc(parent);row.setStatus(status);return row;
    }
    private static void reject(AgentstoreVO[] rows,String parent,Set<String> owned,MaterialOrgInfoOperationAdapter.ChildLookup lookup) throws Exception {
        try { WarehouseSaveAdapter.validateChildPayload(rows,parent,owned,lookup);throw new AssertionError("Foreign warehouse child accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
