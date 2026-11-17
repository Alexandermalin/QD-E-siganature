package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** The result must identify exactly the removed rows and keep the material master unchanged. */
public final class MaterialCancelAssignmentSnapshotsTests {
    private MaterialCancelAssignmentSnapshotsTests() { }
    public static void run() throws Exception {
        Set<String> tables=new HashSet<String>(Arrays.asList("bd_marorg","bd_materialfi","bd_materialpu"));
        SnapshotRow root=row("bd_material","pk_material","M","OWNER","v1");
        SnapshotRow relation=row("bd_marorg","pk_marorg","O","T","v1");
        SnapshotRow finance=row("bd_materialfi","pk_materialfi","F","T","v1");
        SnapshotRow purchase=row("bd_materialpu","pk_materialpu","P","T","v1");
        List<SnapshotRow> initial=Arrays.asList(root,relation,finance,purchase),done=Arrays.asList(root);
        require("SUCCESS".equals(check(tables,initial,done)),"Cancellation did not report success");
        require("ALREADY_UNASSIGNED".equals(check(tables,done,done)),"Repeated cancellation not detected");
        reject(tables,initial,Arrays.asList(root,finance));
        reject(tables,initial,Arrays.asList(row("bd_material","pk_material","M","OWNER","v2")));
        reject(tables,Arrays.asList(root,relation,relation),done);
        reject(tables,Arrays.asList(root,row("bd_materialfi","pk_materialfi","F","OTHER","v1")),done);
        reject(new HashSet<String>(Arrays.asList("bd_marorg","bd_unverified")),initial,done);
        SnapshotRow logical=new SnapshotRow("bd_materialfi","F",null,finance.getValues(),finance.getTypes(),finance.getLabels());
        reject(tables,Arrays.asList(root,logical),done);
        System.out.println("PASS MaterialCancelAssignmentSnapshotsTests (physical rows and conservative results; no NC or Oracle)");
    }
    private static String check(Set<String> tables,List<SnapshotRow> before,List<SnapshotRow> after) throws Exception {
        return MaterialCancelAssignmentSnapshots.reconcile("G","M","T",tables,before,after);
    }
    private static void reject(Set<String> tables,List<SnapshotRow> before,List<SnapshotRow> after) throws Exception {
        try { check(tables,before,after);throw new AssertionError("Ambiguous material cancellation accepted"); }
        catch(BusinessException expected) { }
    }
    private static SnapshotRow row(String table,String key,String id,String org,String version) {
        Map<String,String> fields=new HashMap<String,String>();fields.put(key,id);fields.put("pk_material","M");
        fields.put("pk_group","G");fields.put("pk_org",org);fields.put("ts",version);
        return new SnapshotRow(table,id,null,fields,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
