package nc.bs.uapbd.qd.esign.audit;

import java.util.Arrays;
import java.util.Collections;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Removed rows and unchanged parent rows retain distinct phases during cancellation. */
public final class SnapshotPhasesTests {
    private SnapshotPhasesTests() { }
    public static void run() {
        SnapshotRow root=row("bd_customer","C"),relation=row("bd_custorg","R"),page=row("bd_custsale","P");
        SnapshotPhases.Plan cancel=SnapshotPhases.forOperation("CANCEL_ASSIGN",Arrays.asList(root,relation,page),Arrays.asList(root));
        require(cancel.getBefore().equals(Arrays.asList(root)) && cancel.getDeleted().equals(Arrays.asList(relation,page)),
            "Cancellation did not distinguish retained parent and deleted relation rows");
        SnapshotPhases.Plan delete=SnapshotPhases.forOperation("DELETE",Arrays.asList(root,relation),Collections.<SnapshotRow>emptyList());
        require(delete.getBefore().isEmpty() && delete.getDeleted().size()==2,"Delete phase changed");
        SnapshotPhases.Plan partialDelete=SnapshotPhases.forOperation("DELETE",Arrays.asList(root,relation),Arrays.asList(root));
        require(partialDelete.getBefore().equals(Arrays.asList(root)) && partialDelete.getDeleted().equals(Arrays.asList(relation)),
            "Retained rows in a deletion must not be labeled as deleted");
        SnapshotPhases.Plan update=SnapshotPhases.forOperation("UPDATE",Arrays.asList(root,relation),Arrays.asList(root));
        require(update.getBefore().size()==2 && update.getDeleted().isEmpty(),"Existing update phase changed");
        try { SnapshotPhases.forOperation("CANCEL_ASSIGN",Arrays.asList(root,root),Arrays.asList(root));
            throw new AssertionError("Duplicate physical identity accepted"); }
        catch(IllegalArgumentException expected) { }
        System.out.println("PASS SnapshotPhasesTests (per-row cancellation and deletion phases; no NC or Oracle)");
    }
    private static SnapshotRow row(String table,String id) {
        return new SnapshotRow(table,id,null,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),
            Collections.<String,String>emptyMap(),true);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
