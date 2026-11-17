package nc.impl.uapbd.qd.esign;

import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.vo.bd.stordoc.StordocVO;
import nc.vo.bd.stordoc.AgentstoreVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.VOStatus;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.WarehouseDeleteCommand;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteCommand;

/** Verify the typed delete unit is bound to the authorized warehouse and its owner. */
public final class WarehouseDeleteAdapterTests {
    private WarehouseDeleteAdapterTests() { }
    public static void run() throws Exception {
        WarehouseDeleteAdapter adapter=new WarehouseDeleteAdapter();
        OperationContext context=context("10140WARH","WAREHOUSE","G","OWNER");
        StordocVO value=row("WAREHOUSE","G","OWNER");
        adapter.validate(context,new WarehouseDeleteCommand(value));
        require(adapter.describeResult(context,value).get(0).getObjectPk().equals("WAREHOUSE"),"Delete result must identify its warehouse");
        reject(adapter,context,row("OTHER","G","OWNER"));
        reject(adapter,context,row("WAREHOUSE","OTHER","OWNER"));
        reject(adapter,context,row("WAREHOUSE","G","OTHER"));
        reject(adapter,context("10140RACK","WAREHOUSE","G","OWNER"),value);
        AgentstoreVO child=new AgentstoreVO();child.setPk_agentstore("CHILD");child.setPk_stordoc("WAREHOUSE");child.setStatus(VOStatus.UNCHANGED);
        WarehouseDeleteAdapter.validateChildren(new AgentstoreVO[]{child},"WAREHOUSE",Collections.singleton("CHILD"));
        try { WarehouseDeleteAdapter.validateChildren(new AgentstoreVO[]{child},"WAREHOUSE",Collections.<String>emptySet());
            throw new AssertionError("Foreign warehouse child was accepted"); } catch(BusinessException expected) { }
        try { WarehouseDeleteAdapter.validateChildren(new AgentstoreVO[]{child,child},"WAREHOUSE",Collections.singleton("CHILD"));
            throw new AssertionError("Duplicate warehouse child was accepted"); } catch(BusinessException expected) { }
        try { WarehouseDeleteAdapter.prelock(null,null);throw new AssertionError("Missing context reached native warehouse locks"); }
        catch(BusinessException expected) { }
        Map<String,Object> request=EsignValues.row("request_id","REQUEST","batch_id","REQUEST","pk_group","G","pk_org","B",
            "node_code","10140WARH","button_code","Delete","operation_type","DELETE");
        Map<String,Object> first=EsignValues.row("object_id","1","client_object_id","one","object_type","BD_STORDOC",
            "object_pk","WAREHOUSE","policy_owner_org","OWNER");
        Map<String,Object> second=EsignValues.row("object_id","2","client_object_id","two","object_type","BD_STORDOC",
            "object_pk","OTHER","policy_owner_org","OWNER");
        Map<String,SuperVO> rows=new LinkedHashMap<String,SuperVO>();rows.put("one",value);rows.put("two",row("OTHER","G","OWNER"));
        MaterialPageDeleteServiceImpl.requireWarehouseCommands(request,Arrays.asList(first,second),new MaterialPageDeleteCommand(rows));
        rows.put("two",row("UNAUTHORIZED","G","OWNER"));
        try { MaterialPageDeleteServiceImpl.requireWarehouseCommands(request,Arrays.asList(first,second),new MaterialPageDeleteCommand(rows));
            throw new AssertionError("A malformed later warehouse was accepted before claim"); } catch(BusinessException expected) { }
        System.out.println("PASS WarehouseDeleteAdapterTests (single-unit identity; no NC service or database)");
    }
    private static void reject(WarehouseDeleteAdapter adapter,OperationContext context,StordocVO value) throws Exception {
        try { adapter.validate(context,new WarehouseDeleteCommand(value));throw new AssertionError("Foreign warehouse delete was accepted"); }
        catch(BusinessException expected) { }
    }
    private static OperationContext context(String node,String pk,String group,String owner) {
        BusinessObjectRef ref=new BusinessObjectRef("row","BD_STORDOC",pk,"CODE",null,null,owner,"B");
        return new OperationContext("REQUEST","REQUEST",group,"B",node,"仓库","Delete","删除","DELETE",null,
            Collections.singletonList(ref),OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static StordocVO row(String pk,String group,String owner) {
        StordocVO value=new StordocVO();value.setPk_stordoc(pk);value.setPk_group(group);value.setPk_org(owner);return value;
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
