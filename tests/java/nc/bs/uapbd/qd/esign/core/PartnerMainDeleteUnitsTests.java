package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** The same related archive may occur in two units; each unit retains its own root relationship. */
public final class PartnerMainDeleteUnitsTests {
    private PartnerMainDeleteUnitsTests() { }
    public static void run() throws Exception {
        Map<String,Object> first=row("o1","c1","BD_CUSTOMER","A",null),second=row("o2","c2","BD_CUSTOMER","B",null);
        Map<String,Object> a=row("o3","c3","BD_SUPPLIER","A","o1"),b=row("o4","c4","BD_CUSTOMER","P","o1");
        Map<String,Object> c=row("o5","c5","BD_CUSTOMER","P","o2");
        List<Map<String,Object>> shuffled=Arrays.asList(c,b,second,a,first);
        List<PartnerMainDeleteUnits.Unit> units=PartnerMainDeleteUnits.group(shuffled,"BD_CUSTOMER");
        require(units.size()==2 && units.get(0).getObjects().size()==2 && units.get(1).getObjects().size()==3,
            "Delete unit grouping depended on persisted row order");
        for(PartnerMainDeleteUnits.Unit unit:units) {
            List<BusinessObjectRef> refs=ObjectRelations.restore(unit.getObjects());
            require(refs.get(0).getParentClientObjectId()==null,"Delete unit did not start with its selected root");
            for(int i=1;i<refs.size();i++) require(refs.get(0).getClientObjectId().equals(refs.get(i).getParentClientObjectId()),
                "Related archive was attached to another unit");
        }
        reject(Arrays.asList(first,row("o3","c3","BD_SUPPLIER","A","unknown")));
        reject(Arrays.asList(first,a,row("o4","c4","BD_CUSTOMER","P","o3")));
        reject(Arrays.asList(first,row("o2","c2","BD_CUSTOMER","A",null)));
        reject(Arrays.asList(first,row("o2","c2","BD_CUSTOMER","A","o1")));
        reject(Arrays.asList(first,row("o1","c2","BD_CUSTOMER","B",null)));
        reject(Arrays.asList(first,row("o2","c1","BD_CUSTOMER","B",null)));
        Map<String,Object> moved=row("o2","c2","BD_CUSTOMER","B",null);moved.put("desired_owner_org","OTHER");
        reject(Arrays.asList(first,moved));
        System.out.println("PASS PartnerMainDeleteUnitsTests (root and policy/audit-only unit boundaries; no NC or Oracle)");
    }
    private static void reject(List<Map<String,Object>> rows) throws Exception {
        try { PartnerMainDeleteUnits.group(rows,"BD_CUSTOMER");throw new AssertionError("Invalid delete units accepted"); }
        catch(BusinessException expected) { }
    }
    private static Map<String,Object> row(String id,String client,String type,String pk,String parent) {
        return EsignValues.row("object_id",id,"client_object_id",client,"object_type",type,"object_pk",pk,"parent_object_id",parent);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
