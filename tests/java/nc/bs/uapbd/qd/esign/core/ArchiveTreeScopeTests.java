package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Branch expansion and authorization recheck; no NC or database service is invoked. */
public final class ArchiveTreeScopeTests {
    private ArchiveTreeScopeTests() { }

    public static void run() throws Exception {
        final Map<String,List<ArchiveTreeScope.Row>> branches = new HashMap<String,List<ArchiveTreeScope.Row>>();
        branches.put("ROOT", rows(row("ROOT", "G", "A"), row("CHILD", "G", "A"), row("LEAF", "G", "B")));
        branches.put("CHILD", rows(row("CHILD", "G", "A"), row("LEAF", "G", "B")));
        final boolean[] locked = { false };
        ArchiveTreeScope.Reader reader = new ArchiveTreeScope.Reader() {
            public List<ArchiveTreeScope.Row> descendants(String type, String pk, String group, boolean lock) {
                locked[0] |= lock;
                List<ArchiveTreeScope.Row> values = branches.get(pk);
                return values == null ? null : new ArrayList<ArchiveTreeScope.Row>(values);
            }
        };
        BusinessObjectRef root = new BusinessObjectRef("root-id", "BD_CUSTCLASS", "ROOT", "CLIENT_CODE", null);
        BusinessObjectRef childRoot = ref("child-id", "CHILD");
        List<BusinessObjectRef> expanded = ArchiveTreeScope.expand("G", Arrays.asList(root, childRoot), false, reader);
        require(expanded.size() == 3, "Overlapping branches must identify each archive once");
        require("ROOT".equals(expanded.get(1).getBillCode()), "Root code must be loaded from the server branch");
        BusinessObjectRef leaf = expanded.get(2);
        require("LEAF".equals(leaf.getObjectPk()) && "child-id".equals(leaf.getParentClientObjectId()),
            "A generated descendant must retain its root relationship");
        ArchiveTreeScope.verify("G", expanded, true, reader);
        require(locked[0], "Execution recheck must ask the native reader for the branch lock");
        branches.get("ROOT").add(row("NEW", "G", "A"));
        try { ArchiveTreeScope.verify("G", expanded, true, reader); throw new AssertionError("New descendant was accepted"); }
        catch (BusinessException expected) { }
        branches.get("ROOT").remove(3);
        branches.get("ROOT").add(row("FOREIGN", "OTHER_GROUP", "B"));
        try { ArchiveTreeScope.expand("G", Collections.singletonList(root), false, reader);
            throw new AssertionError("Cross-group descendant was accepted"); }
        catch (BusinessException expected) { }
        require(!ArchiveTreeScope.supports("BD_CUSTOMER"), "A non-tree archive cannot use tree expansion");
        require(ArchiveTreeScope.supports("BD_RACK") && "rack".equals(ArchiveTreeScope.permissionResource("BD_RACK")),
            "Rack trees require their verified native data permission resource");
        System.out.println("PASS ArchiveTreeScopeTests (cascade scope and membership recheck; native tree not invoked)");
    }

    private static BusinessObjectRef ref(String id, String pk) {
        return new BusinessObjectRef(id, "BD_CUSTCLASS", pk, null, null);
    }
    private static ArchiveTreeScope.Row row(String pk, String group, String owner) {
        return new ArchiveTreeScope.Row(pk, group, owner, pk);
    }
    private static List<ArchiveTreeScope.Row> rows(ArchiveTreeScope.Row... values) {
        return new ArrayList<ArchiveTreeScope.Row>(Arrays.asList(values));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
