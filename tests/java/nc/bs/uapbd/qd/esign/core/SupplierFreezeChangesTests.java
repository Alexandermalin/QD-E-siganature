package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Exercises row identity, full-field changes and actual freeze state independently of NC/Oracle. */
public final class SupplierFreezeChangesTests {
    private SupplierFreezeChangesTests() { }
    public static void run() throws Exception {
        List<SnapshotRow> before=Arrays.asList(row("bd_supfinance","F","T",false),row("bd_supstock","P","T",false));
        List<SnapshotRow> after=Arrays.asList(row("bd_supstock","P","T",true),row("bd_supfinance","F","T",true));
        require(SupplierFreezeChanges.changed(before,after,"S","G","T",true),"Changed rows not detected");
        require(!SupplierFreezeChanges.changed(after,after,"S","G","T",true),"Unchanged rows misclassified");
        require(!SupplierFreezeChanges.changed(Collections.<SnapshotRow>emptyList(),Collections.<SnapshotRow>emptyList(),"S","G","T",true),"Empty target changed");
        reject(before,Arrays.asList(row("bd_supfinance","F","T",true)));
        reject(before,Arrays.asList(row("bd_supfinance","F","OTHER",true),row("bd_supstock","P","T",true)));
        reject(before,Arrays.asList(row("bd_supfinance","F","T",true),row("bd_supstock","P","T",false)));
        reject(before,Arrays.asList(row("bd_supfinance","F","T",true),row("bd_supfinance","F","T",true)));
        require(SupplierFreezeChanges.changed(after,before,"S","G","T",false),"Unfreeze not detected");
        System.out.println("PASS SupplierFreezeChangesTests (per-target physical rows; no NC or Oracle)");
    }
    private static SnapshotRow row(String table,String pk,String target,boolean frozen) {
        Map<String,String> values=new HashMap<String,String>();
        values.put("pk_supplier","S");values.put("pk_group","G");values.put("pk_org",target);
        if("bd_supfinance".equals(table)) { values.put("pk_supfinance",pk);values.put("payfreezeflag",frozen?"Y":"N");values.put("makeoutfreezeflag",frozen?"Y":"N"); }
        else { values.put("pk_supstock",pk);values.put("orderfreezeflag",frozen?"1":"0"); }
        return new SnapshotRow(table,pk,"S",values,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static void reject(List<SnapshotRow> before,List<SnapshotRow> after) throws Exception {
        try { SupplierFreezeChanges.changed(before,after,"S","G","T",true);throw new AssertionError("Invalid supplier snapshot accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
