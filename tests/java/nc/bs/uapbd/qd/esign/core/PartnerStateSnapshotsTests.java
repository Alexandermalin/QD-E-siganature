package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Covers native shared-state updates and async-log deletion without Oracle. */
public final class PartnerStateSnapshotsTests {
    private PartnerStateSnapshotsTests() { }
    public static void run() throws Exception {
        List<SnapshotRow> before=Arrays.asList(root("3"),shared("3"),log("0"));
        List<SnapshotRow> after=Arrays.asList(shared("2"),root("2"));
        require(!PartnerStateSnapshots.alreadyTarget(before,"bd_customer","P",true),"Disabled row treated as enabled");
        require(PartnerStateSnapshots.alreadyTarget(before,"bd_customer","P",false),"Disabled row not recognized");
        PartnerStateSnapshots.verifySuccess(before,after,"bd_customer","P","custenablestate",true);
        require(!PartnerStateSnapshots.sameRows(before,after),"Deleted async log was not detected");
        require(!PartnerStateSnapshots.sameRows(before,Arrays.asList(root("3"),shared("3"),log("1"))),"Changed async log was not detected");
        require(PartnerStateSnapshots.sameRows(before,Arrays.asList(log("0"),root("3"),shared("3"))),"Stable physical rows differ by order");
        reject(before,Arrays.asList(root("2"),shared("3")));
        reject(before,Arrays.asList(root("2")));
        reject(before,Arrays.asList(root("3"),shared("2")));
        try { PartnerStateSnapshots.sameRows(before,Arrays.asList(root("3"),root("3")));throw new AssertionError("Duplicate snapshot accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS PartnerStateSnapshotsTests (shared state and async log changes; no NC or Oracle)");
    }
    private static SnapshotRow root(String state) { return row("bd_customer","P",null,"enablestate",state); }
    private static SnapshotRow shared(String state) { return row("bd_cust_supplier","P","P","custenablestate",state); }
    private static SnapshotRow log(String state) { return row("bd_ansylog","L","P","dr",state); }
    private static SnapshotRow row(String table,String pk,String parent,String field,String value) {
        Map<String,String> values=new HashMap<String,String>();values.put(field,value);
        return new SnapshotRow(table,pk,parent,values,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static void reject(List<SnapshotRow> before,List<SnapshotRow> after) throws Exception {
        try { PartnerStateSnapshots.verifySuccess(before,after,"bd_customer","P","custenablestate",true);throw new AssertionError("Invalid partner state accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
