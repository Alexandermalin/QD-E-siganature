package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Complete customer assignment scope is bound to chosen and NC-derived targets. */
public final class CustomerAssignmentRequestScopeTests {
    private CustomerAssignmentRequestScopeTests() { }
    public static void run() throws Exception {
        Data data=new Data();List<BusinessObjectRef> archives=Arrays.asList(archive("C1","001"),archive("C2","002"));
        List<BusinessObjectRef> units=CustomerAssignmentRequestScope.expand("G","ORG","ASSIGN",archives,
            Arrays.asList("A","B"),data,data,data).getUnits();
        require(units.size()==8,"Selection did not expand to every archive and native target");
        for(BusinessObjectRef unit:units) require(unit.getClientObjectId().length()<=100,"Target identity exceeds storage bound");
        CustomerAssignmentRequestScope.verify("G","ORG","ASSIGN",units,data,data,data);
        List<BusinessObjectRef> reverse=new ArrayList<BusinessObjectRef>(units);Collections.reverse(reverse);
        CustomerAssignmentRequestScope.verify("G","ORG","ASSIGN",reverse,data,data,data);
        reject("OTHER","ORG","ASSIGN",units,data);reject("G","OTHER","ASSIGN",units,data);
        reject("G","ORG","CANCEL_ASSIGN",units,data);
        List<BusinessObjectRef> altered=new ArrayList<BusinessObjectRef>(units);altered.remove(0);
        reject("G","ORG","ASSIGN",altered,data);
        altered=new ArrayList<BusinessObjectRef>(units);altered.add(units.get(0));reject("G","ORG","ASSIGN",altered,data);
        BusinessObjectRef original=units.get(0);altered=new ArrayList<BusinessObjectRef>(units);
        altered.set(0,new BusinessObjectRef(original.getClientObjectId(),"BD_CUSTOMER",original.getObjectPk(),
            original.getBillCode(),null,null,null,"OTHER"));reject("G","ORG","ASSIGN",altered,data);
        altered.set(0,new BusinessObjectRef(original.getClientObjectId(),"BD_CUSTOMER",original.getObjectPk(),
            "changed",null,null,null,original.getTargetOrg()));reject("G","ORG","ASSIGN",altered,data);
        altered.set(0,new BusinessObjectRef(original.getClientObjectId().replaceFirst("ca:S:","ca:D:"),"BD_CUSTOMER",
            original.getObjectPk(),original.getBillCode(),null,null,null,original.getTargetOrg()));
        reject("G","ORG","ASSIGN",altered,data);
        altered=new ArrayList<BusinessObjectRef>(units);altered.set(0,new BusinessObjectRef(original.getClientObjectId(),
            "BD_CUSTOMER",original.getObjectPk(),original.getBillCode(),null,null,"OWNER",original.getTargetOrg()));
        reject("G","ORG","ASSIGN",altered,data);
        data.region="R2";reject("G","ORG","ASSIGN",units,data);
        data.region="R1";data.parent="C1";reject("G","ORG","ASSIGN",units,data);
        data.parent=null;data.routeVersion="changed";reject("G","ORG","ASSIGN",units,data);
        data.routeVersion="v1";data.parent="C1";
        CustomerAssignmentRequestScope.Expanded cancellation=CustomerAssignmentRequestScope.expand("G","B","CANCEL_ASSIGN",archives,
            Arrays.asList("A","B"),data,data,data);
        require("C2".equals(cancellation.getUnits().get(0).getObjectPk()) && "B".equals(cancellation.getUnits().get(2).getTargetOrg())
            && "C1".equals(cancellation.getUnits().get(3).getObjectPk()) && "B".equals(cancellation.getUnits().get(5).getTargetOrg()),
            "Cancellation did not process descendants first and the operation organization last for each customer");
        require(data.fingerprint("A").equals(cancellation.getRouteFingerprint("A")),"Missing per-target route evidence");
        try { CustomerAssignmentRequestScope.expand("G","ORG","ASSIGN",Arrays.asList(archives.get(0),archives.get(0)),
                Arrays.asList("A"),data,data,data);throw new AssertionError("Duplicate archives accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS CustomerAssignmentRequestScopeTests (complete selection and derived targets bound; no NC or Oracle)");
    }
    private static BusinessObjectRef archive(String pk,String code) { return new BusinessObjectRef("select-"+pk,"BD_CUSTOMER",pk,code,null); }
    private static void reject(String group,String org,String operation,List<BusinessObjectRef> units,Data data) throws Exception {
        try { CustomerAssignmentRequestScope.verify(group,org,operation,units,data,data,data);throw new AssertionError("Changed assignment scope accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static final class Data implements CustomerAssignmentTargets.Reader,CustomerAssignmentOrder.Reader,CustomerAssignmentRequestScope.RouteReader {
        String region="R1",parent,routeVersion="v1";
        public boolean organizationInGroup(String group,String org) { return "G".equals(group) && ("A".equals(org) || "B".equals(org)); }
        public List<String> financeOrganizations(List<String> selected) {
            return selected.contains("A")?Collections.singletonList("A"):Collections.<String>emptyList();
        }
        public List<String> groupCreditRegions(String group) { return Collections.singletonList("RG"); }
        public List<String> financeCreditRegions(String org) { return Collections.singletonList(region); }
        public String creditRegionGroup(String region) { return "G"; }
        public List<String> superiorCustomers(String group,String customer) {
            return "C2".equals(customer) && parent!=null?Collections.singletonList(parent):Collections.<String>emptyList();
        }
        public String fingerprint(String target) { return SnapshotSupport.sha256(target+"/"+routeVersion); }
    }
}
