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
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** A per-target result is accepted only when all server-verified physical rows agree. */
public final class PartnerAssignmentSnapshotsTests {
    private PartnerAssignmentSnapshotsTests() { }
    public static void run() throws Exception {
        Set<String> tables=set("bd_custorg","bd_custfinance","bd_custsale");
        SnapshotRow root=row("bd_customer","pk_customer","C","pk_customer","C","G","OWNER","v1");
        SnapshotRow relation=row("bd_custorg","pk_custorg","O","pk_customer","C","G","T","v1");
        SnapshotRow finance=row("bd_custfinance","pk_custfinance","F","pk_customer","C","G","T","v1");
        SnapshotRow sale=row("bd_custsale","pk_custsale","S","pk_customer","C","G","T","v1");
        List<SnapshotRow> empty=Arrays.asList(root),assigned=Arrays.asList(root,relation,finance,sale);
        require("SUCCESS".equals(check("BD_CUSTOMER","ASSIGN",tables,empty,assigned)),"New customer assignment not successful");
        require("SUCCESS".equals(check("BD_CUSTOMER","ASSIGN",tables,Arrays.asList(root,relation),assigned)),"Partial existing assignment not detected");
        require("ALREADY_ASSIGNED".equals(check("BD_CUSTOMER","ASSIGN",tables,assigned,assigned)),"Duplicate assignment not detected");
        require("SUCCESS".equals(check("BD_CUSTOMER","CANCEL_ASSIGN",tables,assigned,empty)),"Customer cancellation not successful");
        require("ALREADY_UNASSIGNED".equals(check("BD_CUSTOMER","CANCEL_ASSIGN",tables,empty,empty)),"Duplicate cancellation not detected");
        reject("BD_CUSTOMER","ASSIGN",tables,empty,Arrays.asList(root,relation,finance));
        reject("BD_CUSTOMER","CANCEL_ASSIGN",tables,assigned,Arrays.asList(root,relation));
        SnapshotRow modified=row("bd_custorg","pk_custorg","O","pk_customer","C","G","T","v2");
        reject("BD_CUSTOMER","ASSIGN",tables,Arrays.asList(root,relation),Arrays.asList(root,modified,finance,sale));
        SnapshotRow wrongTarget=row("bd_custsale","pk_custsale","S","pk_customer","C","G","OTHER","v1");
        reject("BD_CUSTOMER","ASSIGN",tables,empty,Arrays.asList(root,relation,finance,wrongTarget));
        SnapshotRow wrongGroup=row("bd_custsale","pk_custsale","S","pk_customer","C","OTHER","T","v1");
        reject("BD_CUSTOMER","ASSIGN",tables,empty,Arrays.asList(root,relation,finance,wrongGroup));
        reject("BD_CUSTOMER","ASSIGN",tables,empty,Arrays.asList(root,relation,finance,sale,sale));
        reject("BD_CUSTOMER","ASSIGN",set("bd_custorg","bd_custsale","bd_unverified"),empty,assigned);
        SnapshotRow changedRoot=row("bd_customer","pk_customer","C","pk_customer","C","G","OWNER","v2");
        reject("BD_CUSTOMER","ASSIGN",tables,empty,Arrays.asList(changedRoot,relation,finance,sale));
        SnapshotRow logicalOnly=new SnapshotRow("bd_custsale","S",null,sale.getValues(),sale.getTypes(),sale.getLabels());
        reject("BD_CUSTOMER","ASSIGN",tables,empty,Arrays.asList(root,relation,finance,logicalOnly));

        Set<String> supplierTables=set("bd_suporg","bd_supfinance","bd_supstock");
        SnapshotRow sup=row("bd_supplier","pk_supplier","C","pk_supplier","C","G","OWNER","v1");
        SnapshotRow supOrg=row("bd_suporg","pk_suporg","SO","pk_supplier","C","G","T","v1");
        SnapshotRow supFin=row("bd_supfinance","pk_supfinance","SF","pk_supplier","C","G","T","v1");
        SnapshotRow supStock=row("bd_supstock","pk_supstock","SS","pk_supplier","C","G","T","v1");
        require("SUCCESS".equals(check("BD_SUPPLIER","ASSIGN",supplierTables,Arrays.asList(sup),Arrays.asList(sup,supOrg,supFin,supStock))),
            "Supplier assignment not successful");
        System.out.println("PASS PartnerAssignmentSnapshotsTests (per-target physical evidence and duplicate results; no NC or Oracle)");
    }
    private static String check(String domain,String op,Set<String> tables,List<SnapshotRow> before,List<SnapshotRow> after) throws Exception {
        return PartnerAssignmentSnapshots.reconcile(domain,op,"G","C","T",tables,before,after);
    }
    private static void reject(String domain,String op,Set<String> tables,List<SnapshotRow> before,List<SnapshotRow> after) throws Exception {
        try { check(domain,op,tables,before,after);throw new AssertionError("Ambiguous assignment result accepted"); }
        catch(BusinessException expected) { }
    }
    private static Set<String> set(String... names) { return new HashSet<String>(Arrays.asList(names)); }
    private static SnapshotRow row(String table,String key,String id,String rootKey,String root,String group,String org,String version) {
        Map<String,String> fields=new HashMap<String,String>();fields.put(key,id);fields.put(rootKey,root);
        fields.put("pk_group",group);fields.put("pk_org",org);fields.put("ts",version);
        return new SnapshotRow(table,id,null,fields,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
