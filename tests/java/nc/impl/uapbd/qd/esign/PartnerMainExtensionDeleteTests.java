package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Checks physical membership of a supplier extension aggregate without initializing NC metadata. */
public final class PartnerMainExtensionDeleteTests {
    private PartnerMainExtensionDeleteTests() { }
    public static void run() throws Exception {
        List<SnapshotRow> rows=rows("S","G","A");List<String> child=Collections.singletonList("srmsm_supplierext_a\u0000A");
        PartnerMainExtension.requireCompleteDeleteRows("S","G",rows,"S","G",child);
        reject(rows,"OTHER","G",child);reject(rows,"S","OTHER",child);
        reject(rows,"S","G",Collections.singletonList("srmsm_supplierext_a\u0000OTHER"));
        reject(rows,"S","G",Collections.<String>emptyList());
        reject(rows("S","OTHER","A"),"S","G",child);
        List<SnapshotRow> duplicate=rows("S","G","A");duplicate.add(duplicate.get(1));reject(duplicate,"S","G",child);
        List<SnapshotRow> withoutHeader=rows("S","G","A");withoutHeader.remove(0);reject(withoutHeader,"S","G",child);
        reject(rows,"S","G",Arrays.asList("srmsm_supplierext_a\u0000A","srmsm_supplierext_a\u0000A"));
        System.out.println("PASS PartnerMainExtensionDeleteTests (complete extension row membership; no NC or Oracle)");
    }
    private static void reject(List<SnapshotRow> rows,String supplier,String group,List<String> children) throws Exception {
        try { PartnerMainExtension.requireCompleteDeleteRows("S","G",rows,supplier,group,children);
            throw new AssertionError("Invalid supplier extension accepted"); }
        catch(BusinessException expected) { }
    }
    private static List<SnapshotRow> rows(String supplier,String group,String id) {
        List<SnapshotRow> result=new ArrayList<SnapshotRow>();
        result.add(row("srmsm_supplierext_h",supplier,supplier,group));
        result.add(row("srmsm_supplierext_a",id,supplier,group));return result;
    }
    private static SnapshotRow row(String table,String id,String supplier,String group) {
        Map<String,String> values=new HashMap<String,String>();values.put("pk_supplier",supplier);values.put("pk_group",group);
        return new SnapshotRow(table,id,null,values,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
}
