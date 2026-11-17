package nc.ui.uapbd.qd.esign;

import java.util.Arrays;
import nc.itf.bd.pub.assign.AssignStatus;
import nc.vo.pub.BusinessException;

/** Condition-based assignment signs a frozen, permission-filtered native result. */
public final class AssignmentConditionSelectionTests {
    private AssignmentConditionSelectionTests() { }
    public static void run() throws Exception {
        final String[] returned={"P1","P2"};final String[] targets={"ORG1","ORG2"};
        final int[] calls={0};
        QdAssignmentConditionSelection.Query query=new QdAssignmentConditionSelection.Query() {
            public String[] find(String where,AssignStatus status,String[] selected) {
                calls[0]++;
                require("code like 'A%'".equals(where) && status==AssignStatus.UNASSIGNED
                    && Arrays.equals(selected,new String[]{"ORG1","ORG2"}),"Native query arguments were changed");
                selected[0]="MUTATED";return returned;
            }
        };
        QdAssignmentConditionSelection.Selection selection=QdAssignmentConditionSelection.resolve(
            "code like 'A%'",AssignStatus.UNASSIGNED,targets,query);
        targets[0]="CHANGED";returned[0]="CHANGED";
        require(calls[0]==1 && Arrays.equals(selection.objects(),new String[]{"P1","P2"})
            && Arrays.equals(selection.targets(),new String[]{"ORG1","ORG2"}),"Authorization scope was not frozen");
        String[] exposed=selection.objects();exposed[0]="CHANGED";
        require("P1".equals(selection.objects()[0]),"Caller could mutate the selected archive scope");
        reject(null,new String[]{"ORG"},new String[]{"P1"});
        reject(AssignStatus.ALL,new String[]{"ORG","ORG"},new String[]{"P1"});
        reject(AssignStatus.ALL,new String[]{"ORG"},new String[0]);
        reject(AssignStatus.ALL,new String[]{"ORG"},new String[]{"P1","P1"});
        reject(AssignStatus.ALL,new String[]{"ORG"},new String[]{"bad id"});
        String[] many=new String[501];for(int i=0;i<many.length;i++) many[i]="P"+i;
        reject(AssignStatus.ALL,new String[]{"ORG1","ORG2"},many);
        final boolean[] invoked={false};
        try { QdAssignmentConditionSelection.resolve(null,AssignStatus.ALL,new String[]{"ORG","ORG"},
                new QdAssignmentConditionSelection.Query() {
                    public String[] find(String where,AssignStatus status,String[] selected) { invoked[0]=true;return new String[]{"P1"}; }
                });throw new AssertionError("Duplicate targets accepted"); }
        catch(BusinessException expected) { require(!invoked[0],"Invalid targets reached the native query"); }
        System.out.println("PASS AssignmentConditionSelectionTests (frozen native results and bounded combinations; no NC session)");
    }
    private static void reject(AssignStatus status,String[] targets,final String[] rows) throws Exception {
        try { QdAssignmentConditionSelection.resolve(null,status,targets,new QdAssignmentConditionSelection.Query() {
                public String[] find(String where,AssignStatus value,String[] selected) { return rows; }
            });throw new AssertionError("Invalid condition selection accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
