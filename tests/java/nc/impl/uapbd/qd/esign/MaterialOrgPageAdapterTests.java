package nc.impl.uapbd.qd.esign;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.uapbd.qd.esign.MaterialOrgPage;

/** Checks native page identifiers and child ownership without calling NC or a database. */
public final class MaterialOrgPageAdapterTests {
    private MaterialOrgPageAdapterTests() { }

    public static void run() throws Exception {
        Set<String> buttons = new HashSet<String>(), domains = new HashSet<String>();
        for (MaterialOrgPage page : MaterialOrgPage.values()) {
            require(buttons.add(page.button) && domains.add(page.domain), "Duplicate material page identity");
            SuperVO row = page.type.newInstance();
            require(page.table.equals(row.getTableName()) && page.key.equals(row.getPKFieldName()),
                "Page metadata does not match the installed NC VO: " + page.button);
            require(MaterialOrgPage.forButton(page.button) == page && MaterialOrgPage.forDomain(page.domain) == page,
                "Page button and domain must resolve to the same definition");
            require(MaterialOrgPage.forDeleteButton(page.deleteButton) == page
                && MaterialOrgPage.forButton(page.deleteButton) == null,
                "Delete and save buttons must have distinct identities");
            if (page.childType != null) {
                SuperVO child = page.childType.newInstance();
                require(page.childTable.equals(child.getTableName()) && page.childKey.equals(child.getPKFieldName())
                    && page.key.equals(child.getParentPKFieldName()), "Child metadata does not match NC: " + page.button);
                checkChildren();
            }
        }
        require(MaterialOrgPage.forButton("UNKNOWN") == null && MaterialOrgPage.forDomain("UNKNOWN") == null,
            "Unknown page cannot be mapped");
        System.out.println("PASS MaterialOrgPageAdapterTests (NC VO metadata and child ownership; database services not invoked)");
    }

    private static void checkChildren() throws Exception {
        final Set<String> taken = Collections.singleton("FOREIGN");
        MaterialOrgInfoOperationAdapter.ChildLookup lookup = new MaterialOrgInfoOperationAdapter.ChildLookup() {
            public boolean exists(String pk) { return taken.contains(pk); }
        };
        Set<String> owned = Collections.singleton("OWNED");
        MaterialOrgInfoOperationAdapter.validateChildRow("ROW", "ROW", "OWNED", VOStatus.UPDATED, owned, new HashSet<String>(), lookup);
        expectRejected("ROW", "FOREIGN", VOStatus.UPDATED, owned, lookup);
        expectRejected("ROW", "FOREIGN", VOStatus.NEW, owned, lookup);
        Set<String> seen = new HashSet<String>();
        MaterialOrgInfoOperationAdapter.validateChildRow("ROW", "ROW", "NEW", VOStatus.NEW, owned, seen, lookup);
        try {
            MaterialOrgInfoOperationAdapter.validateChildRow("ROW", "ROW", "NEW", VOStatus.NEW, owned, seen, lookup);
            throw new AssertionError("Duplicate child id accepted");
        } catch (BusinessException expected) { }
        expectRejected("OTHER", "NEW", VOStatus.NEW, owned, lookup);
        expectRejected("ROW", null, VOStatus.UPDATED, owned, lookup);
        MaterialOrgInfoOperationAdapter.validateChildRow("ROW", null, null, VOStatus.NEW, owned, new HashSet<String>(), lookup);
    }

    private static void expectRejected(String parent, String pk, int status, Set<String> owned,
            MaterialOrgInfoOperationAdapter.ChildLookup lookup) throws BusinessException {
        try {
            MaterialOrgInfoOperationAdapter.validateChildRow("ROW", parent, pk, status, owned, new HashSet<String>(), lookup);
            throw new AssertionError("Foreign child row accepted");
        } catch (BusinessException expected) { }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
