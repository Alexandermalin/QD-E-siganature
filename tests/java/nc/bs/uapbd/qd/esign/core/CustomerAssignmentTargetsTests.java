package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;

/** Target expansion follows NC assignment/cancellation rules and cannot hide a changed region mapping. */
public final class CustomerAssignmentTargetsTests {
    private CustomerAssignmentTargetsTests() { }
    public static void run() throws Exception {
        Data data=new Data();
        CustomerAssignmentTargets.Selection assign=CustomerAssignmentTargets.select("G","ASSIGN",Arrays.asList("A","B"),data);
        require(assign.getTargets().equals(Arrays.asList("A","B","GROUP_REGION","R1")),"Native assignment omitted derived credit regions");
        CustomerAssignmentTargets.Selection cancel=CustomerAssignmentTargets.select("G","CANCEL_ASSIGN",Arrays.asList("A","B"),data);
        require(cancel.getTargets().equals(Arrays.asList("A","B","R1")),"Native cancellation incorrectly included the group credit region");
        CustomerAssignmentTargets.Selection nonFinance=CustomerAssignmentTargets.select("G","ASSIGN",Collections.singletonList("B"),data);
        require(nonFinance.getTargets().equals(Collections.singletonList("B")),"Non-finance target gained credit regions");
        CustomerAssignmentTargets.verify(assign,CustomerAssignmentTargets.select("G","ASSIGN",Arrays.asList("B","A"),data));
        data.regions.put("A",Arrays.asList("R1","R1"));
        CustomerAssignmentTargets.verify(assign,CustomerAssignmentTargets.select("G","ASSIGN",Arrays.asList("A","B"),data));
        data.regions.put("A",Collections.singletonList("R2"));
        try { CustomerAssignmentTargets.verify(assign,CustomerAssignmentTargets.select("G","ASSIGN",Arrays.asList("A","B"),data));
            throw new AssertionError("Changed finance-to-credit mapping reused authorization"); }
        catch(BusinessException expected) { }
        data.group.put("R2","OTHER");reject(data,Arrays.asList("A","B"));
        data.group.put("R2","G");data.organizationGroup.put("B","OTHER");reject(data,Arrays.asList("A","B"));
        data.organizationGroup.put("B","G");reject(data,Arrays.asList("A","A"));
        System.out.println("PASS CustomerAssignmentTargetsTests (native credit-region expansion; no NC or Oracle)");
    }
    private static void reject(Data data,List<String> requested) throws Exception {
        try { CustomerAssignmentTargets.select("G","ASSIGN",requested,data);throw new AssertionError("Invalid customer assignment target accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static final class Data implements CustomerAssignmentTargets.Reader {
        final Set<String> finance=new HashSet<String>(Arrays.asList("A"));
        final Map<String,String> group=new HashMap<String,String>(),organizationGroup=new HashMap<String,String>();
        final Map<String,List<String>> regions=new HashMap<String,List<String>>();
        Data() { organizationGroup.put("A","G");organizationGroup.put("B","G");group.put("GROUP_REGION","G");group.put("R1","G");group.put("R2","G");
            regions.put("A",Collections.singletonList("R1")); }
        public boolean organizationInGroup(String g,String org) { return g.equals(organizationGroup.get(org)); }
        public List<String> financeOrganizations(List<String> selected) {
            List<String> result=new ArrayList<String>();for(String org:selected) if(finance.contains(org)) result.add(org);return result;
        }
        public List<String> groupCreditRegions(String g) { return Collections.singletonList("GROUP_REGION"); }
        public List<String> financeCreditRegions(String org) { return regions.containsKey(org)?regions.get(org):Collections.<String>emptyList(); }
        public String creditRegionGroup(String region) { return group.get(region); }
    }
}
