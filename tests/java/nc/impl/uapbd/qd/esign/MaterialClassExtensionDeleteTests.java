package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Checks that a server-loaded material class aggregate covers the physical extension rows. */
public final class MaterialClassExtensionDeleteTests {
    private MaterialClassExtensionDeleteTests() { }
    public static void run() throws Exception {
        List<SnapshotRow> rows=rows("M","G","O","H","B");
        List<String> children=Collections.singletonList("B");
        MaterialClassExtension.requireCompleteDeleteRows("M","G","O",rows,"H","M","G","O",children);
        reject(rows,"H","OTHER","G","O",children);
        reject(rows,"H","M","OTHER","O",children);
        reject(rows,"H","M","G","OTHER",children);
        reject(rows,"OTHER","M","G","O",children);
        reject(rows,"H","M","G","O",Collections.<String>emptyList());
        reject(rows,"H","M","G","O",Collections.singletonList("OTHER"));
        reject(rows,"H","M","G","O",Arrays.asList("B","B"));
        List<SnapshotRow> duplicate=new ArrayList<SnapshotRow>(rows);duplicate.add(rows.get(1));
        reject(duplicate,"H","M","G","O",children);
        reject(rows("M","G","O","OTHER","B"),"H","M","G","O",children);
        reject(rows("M","OTHER","O","H","B"),"H","M","G","O",children);
        List<SnapshotRow> missingHead=new ArrayList<SnapshotRow>(rows);missingHead.remove(0);
        reject(missingHead,"H","M","G","O",children);
        List<SnapshotRow> wrongParent=new ArrayList<SnapshotRow>(rows);wrongParent.set(1,row("srmsm_marbasclsext_b","B","OTHER","M","G","O"));
        reject(wrongParent,"H","M","G","O",children);
        System.out.println("PASS MaterialClassExtensionDeleteTests (complete extension row membership; no NC or Oracle)");
    }
    private static void reject(List<SnapshotRow> rows,String header,String material,String group,String owner,List<String> children)
            throws Exception {
        try { MaterialClassExtension.requireCompleteDeleteRows("M","G","O",rows,header,material,group,owner,children);
            throw new AssertionError("Invalid material class extension accepted"); }
        catch(BusinessException expected) { }
    }
    private static List<SnapshotRow> rows(String material,String group,String owner,String header,String body) {
        List<SnapshotRow> result=new ArrayList<SnapshotRow>();
        result.add(row("srmsm_marbasclsext",header,material,material,group,owner));
        result.add(row("srmsm_marbasclsext_b",body,header,material,group,owner));return result;
    }
    private static SnapshotRow row(String table,String id,String parent,String material,String group,String owner) {
        Map<String,String> values=new HashMap<String,String>();values.put("cmarbasclsid",material);
        values.put("pk_group",group);values.put("pk_org",owner);
        return new SnapshotRow(table,id,parent,values,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
}
