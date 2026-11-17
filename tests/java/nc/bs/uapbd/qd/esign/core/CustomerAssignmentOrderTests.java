package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;

/** Dependency order is deterministic and hierarchy changes invalidate its evidence. */
public final class CustomerAssignmentOrderTests {
    private CustomerAssignmentOrderTests() { }
    public static void run() throws Exception {
        Data data=new Data();data.parents.put("A",Arrays.asList("Z","OUTSIDE"));data.parents.put("B",Arrays.asList("A","Z","OUTSIDE"));
        List<String> input=Arrays.asList("B","A","Z");
        CustomerAssignmentOrder.Selection assign=CustomerAssignmentOrder.select("G","ASSIGN",input,data);
        require(assign.getOrder().equals(Arrays.asList("Z","A","B")),"Customer ancestor was not assigned before descendant");
        require(CustomerAssignmentOrder.select("G","CANCEL_ASSIGN",input,data).getOrder().equals(Arrays.asList("B","A","Z")),
            "Customer descendant was not cancelled before ancestor");
        require(assign.getFingerprint().equals(CustomerAssignmentOrder.select("G","ASSIGN",Arrays.asList("Z","B","A"),data).getFingerprint()),
            "Hierarchy fingerprint depended on selection order");
        data.parents.put("A",Collections.singletonList("Z"));
        require(!assign.getFingerprint().equals(CustomerAssignmentOrder.select("G","ASSIGN",input,data).getFingerprint()),
            "Unselected ancestor change did not alter evidence");
        data.parents.put("Z",Collections.singletonList("B"));reject(input,data);
        data.parents.put("Z",Collections.singletonList("Z"));reject(input,data);
        data.parents.put("Z",Arrays.asList("A","A"));reject(input,data);
        System.out.println("PASS CustomerAssignmentOrderTests (customer hierarchy and cycle detection; no NC or Oracle)");
    }
    private static void reject(List<String> customers,Data data) throws Exception {
        try { CustomerAssignmentOrder.select("G","ASSIGN",customers,data);throw new AssertionError("Invalid customer hierarchy accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static final class Data implements CustomerAssignmentOrder.Reader {
        final Map<String,List<String>> parents=new HashMap<String,List<String>>();
        public List<String> superiorCustomers(String group,String customer) {
            List<String> values=parents.get(customer);return values==null?Collections.<String>emptyList():values;
        }
    }
}
