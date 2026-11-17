package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only planning snapshot; each slice must still be separately authorized and revalidated. */
public final class PartnerOrgBatchPlan implements Serializable {
    private static final long serialVersionUID=1L;
    private final String group,operationOrg,node,tabCode,selectionFingerprint,manifestFingerprint;
    private final List<String> targets;private final List<Slice> slices;private final long pageCount;

    public PartnerOrgBatchPlan(String group,String operationOrg,String node,String tabCode,String selectionFingerprint,
            List<String> targets,List<Slice> slices,long pageCount) {
        this(group,operationOrg,node,tabCode,selectionFingerprint,targets,slices,pageCount,null);
    }
    public PartnerOrgBatchPlan(String group,String operationOrg,String node,String tabCode,String selectionFingerprint,
            List<String> targets,List<Slice> slices,long pageCount,String manifestFingerprint) {
        if(group==null || operationOrg==null || node==null || tabCode==null || selectionFingerprint==null
                || targets==null || targets.isEmpty() || slices==null || slices.isEmpty() || pageCount<0)
            throw new IllegalArgumentException("Organization batch plan is incomplete");
        this.group=group;this.operationOrg=operationOrg;this.node=node;this.tabCode=tabCode;this.selectionFingerprint=selectionFingerprint;
        this.manifestFingerprint=manifestFingerprint;
        this.targets=Collections.unmodifiableList(new ArrayList<String>(targets));
        this.slices=Collections.unmodifiableList(new ArrayList<Slice>(slices));this.pageCount=pageCount;
    }
    public String getGroup() { return group; }
    public String getOperationOrg() { return operationOrg; }
    public String getNode() { return node; }
    public String getTabCode() { return tabCode; }
    public String getSelectionFingerprint() { return selectionFingerprint; }
    /** Null for older serialized plans, which a current client must not dispatch. */
    public String getManifestFingerprint() { return manifestFingerprint; }
    public List<String> getTargets() { return targets; }
    public List<Slice> getSlices() { return slices; }
    public long getPageCount() { return pageCount; }

    /** A root may occur in several slices; the page identities must not repeat. */
    public static final class Slice implements Serializable {
        private static final long serialVersionUID=1L;
        private final List<String> roots;private final List<Page> pages;
        public Slice(List<String> roots,List<Page> pages) {
            if(roots==null || roots.isEmpty() || pages==null) throw new IllegalArgumentException("Organization batch slice is incomplete");
            this.roots=Collections.unmodifiableList(new ArrayList<String>(roots));
            this.pages=Collections.unmodifiableList(new ArrayList<Page>(pages));
        }
        public List<String> getRoots() { return roots; }
        public List<Page> getPages() { return pages; }
    }
    /** The archive and target are shown with each fixed page identity before authorization. */
    public static final class Page implements Serializable {
        private static final long serialVersionUID=1L;
        private final String id,root,target;
        public Page(String id,String root,String target) { this.id=id;this.root=root;this.target=target; }
        public String getId() { return id; }
        public String getRoot() { return root; }
        public String getTarget() { return target; }
    }
}
