package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Material targets and native page configuration are immutable across signature and execution. */
public final class MaterialCancelAssignmentRequestScopeTests {
    private MaterialCancelAssignmentRequestScopeTests() { }
    public static void run() throws Exception {
        Data data=new Data();List<BusinessObjectRef> archives=Arrays.asList(archive("S1"),archive("S2"));
        MaterialCancelAssignmentRequestScope.Expanded selection=MaterialCancelAssignmentRequestScope.expand("G","B","CANCEL_ASSIGN",archives,
            Arrays.asList("B","A"),data);List<BusinessObjectRef> units=selection.getUnits();
        require(units.size()==4 && "A".equals(units.get(0).getTargetOrg()) && "B".equals(units.get(1).getTargetOrg()),
            "Material cancellation did not keep the operation organization last");
        MaterialCancelAssignmentRequestScope.verify("G","B","CANCEL_ASSIGN",units,data);
        List<BusinessObjectRef> reversed=new ArrayList<BusinessObjectRef>(units);Collections.reverse(reversed);
        MaterialCancelAssignmentRequestScope.verify("G","B","CANCEL_ASSIGN",reversed,data);
        require(data.routeFingerprint("A").equals(selection.getRouteFingerprint("A")),"Material route evidence missing");
        reject("OTHER","B","CANCEL_ASSIGN",units,data);reject("G","OTHER","CANCEL_ASSIGN",units,data);
        reject("G","B","ASSIGN",units,data);
        List<BusinessObjectRef> changed=new ArrayList<BusinessObjectRef>(units);changed.remove(0);reject("G","B","CANCEL_ASSIGN",changed,data);
        BusinessObjectRef first=units.get(0);changed=new ArrayList<BusinessObjectRef>(units);
        changed.set(0,new BusinessObjectRef(first.getClientObjectId(),"BD_MATERIAL",first.getObjectPk(),"new-code",null,null,null,first.getTargetOrg()));
        reject("G","B","CANCEL_ASSIGN",changed,data);
        changed.set(0,new BusinessObjectRef(first.getClientObjectId(),"BD_MATERIAL",first.getObjectPk(),first.getBillCode(),null,null,"OTHER",first.getTargetOrg()));
        reject("G","B","CANCEL_ASSIGN",changed,data);
        data.version="changed";reject("G","B","CANCEL_ASSIGN",units,data);
        data.version="v1";try { MaterialCancelAssignmentRequestScope.expand("G","B","CANCEL_ASSIGN",archives,Arrays.asList("A","A"),data);
            throw new AssertionError("Duplicate target accepted"); } catch(BusinessException expected) { }
        try { MaterialCancelAssignmentRequestScope.expand("G","B","CANCEL_ASSIGN",archives,Arrays.asList("G"),data);
            throw new AssertionError("Group is not an active business-unit target"); } catch(BusinessException expected) { }
        System.out.println("PASS MaterialCancelAssignmentRequestScopeTests (material and target scope binding; no NC or Oracle)");
    }
    private static BusinessObjectRef archive(String pk) { return new BusinessObjectRef("selected-"+pk,"BD_MATERIAL",pk,pk+"-code",null); }
    private static void reject(String group,String org,String operation,List<BusinessObjectRef> units,Data data) throws Exception {
        try { MaterialCancelAssignmentRequestScope.verify(group,org,operation,units,data);throw new AssertionError("Changed material assignment scope accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static final class Data implements MaterialCancelAssignmentRequestScope.Reader {
        String version="v1";
        public boolean organizationInGroup(String group,String org) { return "G".equals(group) && ("A".equals(org) || "B".equals(org)); }
        public String routeFingerprint(String target) { return SnapshotSupport.sha256(target+"/"+version); }
    }
}

