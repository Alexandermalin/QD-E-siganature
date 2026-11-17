package nc.bs.uapbd.qd.esign.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchManifest;

/** Page-based planning and exact slice replay using read-only doubles, without an NC session. */
public final class NativeOrgBatchPartitionTests {
    private NativeOrgBatchPartitionTests() { }
    public static void run() throws Exception {
        List<BusinessObjectRef> roots=roots("D","A","C","B");String[] targets={"T3","T2","T1"};
        List<NativeOrgBatchSelection.Row> rows=Arrays.asList(row("P4","A","T1"),row("P1","A","T1"),
            row("P2","A","T1"),row("P3","A","T1"),row("P5","B","T2"),row("P6","D","T1"));
        NativeOrgBatchSelection.Impact selection=capture(roots,targets,rows);
        PartnerOrgBatchPlan plan=NativeOrgBatchPartition.plan("G","ORG","10140CUG",selection,2,3);
        require(plan.getPageCount()==6 && plan.getSlices().size()==3 && plan.getTargets().equals(Arrays.asList("T1","T2","T3")),
            "A large root/target combination was not split or an empty target was lost");
        verify(plan,selection,2,3);
        require(plan.getSlices().get(0).getRoots().contains("A") && plan.getSlices().get(1).getRoots().contains("A"),
            "One root with more page rows than the limit was not split across slices");
        require(plan.getSlices().get(2).getRoots().contains("C"),"A selected root without pages was lost");
        try { plan.getSlices().clear();throw new AssertionError("Plan slices are mutable"); } catch(UnsupportedOperationException expected) { }
        try { plan.getSlices().get(0).getPages().clear();throw new AssertionError("Page identities are mutable"); }
        catch(UnsupportedOperationException expected) { }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ObjectOutputStream out=new ObjectOutputStream(bytes);out.writeObject(plan);out.close();
        PartnerOrgBatchPlan restored=(PartnerOrgBatchPlan)new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
        verify(restored,selection,2,3);
        PartnerOrgBatchPlan empty=NativeOrgBatchPartition.plan("G","ORG","10140CUG",
            capture(roots,new String[]{"T1"},Collections.<NativeOrgBatchSelection.Row>emptyList()),2,3);
        require(empty.getPageCount()==0 && empty.getSlices().size()==2 && empty.getSlices().get(0).getPages().isEmpty(),
            "A batch without page rows failed to retain its roots and target");
        verify(empty,capture(roots,new String[]{"T1"},Collections.<NativeOrgBatchSelection.Row>emptyList()),2,3);
        try { NativeOrgBatchPartition.plan("OTHER","ORG","10140CUG",selection,2,3);throw new AssertionError("Foreign group plan accepted"); }
        catch(BusinessException expected) { }
        try { NativeOrgBatchPartition.plan("G","ORG","10140SUG",selection,2,3);throw new AssertionError("Foreign domain plan accepted"); }
        catch(BusinessException expected) { }
        signedPageSlice();
        System.out.println("PASS NativeOrgBatchPartitionTests (exact page slices, repeated roots and empty targets; no NC or Oracle)");
    }

    private static void signedPageSlice() throws Exception {
        final List<BusinessObjectRef> roots=roots("A");
        NativeOrgBatchSelection.PageReader reader=new NativeOrgBatchSelection.PageReader() {
            public List<NativeOrgBatchSelection.Row> read(NativeOrgBatchPage p,String[] r,String[] t,boolean l) {
                throw new AssertionError("Planned authorization expanded to the full root");
            }
            public List<NativeOrgBatchSelection.Row> readPages(NativeOrgBatchPage p,String[] ids,boolean l) {
                List<NativeOrgBatchSelection.Row> result=new ArrayList<NativeOrgBatchSelection.Row>();
                for(String id:ids)result.add(row(id,"A","T1"));return result;
            }
        };
        NativeOrgBatchSelection.Impact first=NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,
            roots,new String[]{"T1"},reader,false,planned("P1"));
        Map<String,Object> values=new HashMap<String,Object>();values.put("name","new");
        NativeBatchChangeSet changes=NativeBatchChangeSet.create(new String[]{"name"},values,
            new HashSet<String>(Arrays.asList("name")));
        NativeOrgBatchSnapshots.Capture evidence=NativeOrgBatchSnapshotTests.captureForImpact(first,false);
        List<BusinessObjectRef> authorized=NativeBatchRequestScope.bindOrganization("G","ORG","BD_CUSTOMER",roots,changes,first,evidence);
        NativeBatchRequestScope.verifyOrganization("G","ORG","BD_CUSTOMER",authorized,changes,first,evidence);
        for(String[] different:new String[][]{new String[]{"P2"},new String[]{"P1","P2"},new String[0]}) {
            NativeOrgBatchSelection.Impact changed=NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,
                roots,new String[]{"T1"},reader,false,planned(different));
            try { NativeBatchRequestScope.verifyOrganization("G","ORG","BD_CUSTOMER",authorized,changes,changed,
                    NativeOrgBatchSnapshotTests.captureForImpact(changed,false));
                throw new AssertionError("A different planned page slice reused the signed authorization");
            } catch(BusinessException expected) { }
        }
    }

    /** Explicit scale test; kept out of the ordinary test suite. */
    public static void scale() throws Exception {
        final int n=nc.vo.uapbd.qd.esign.PartnerBatchDraft.MAX_SINGLE_TRANSACTION_PAGE_ROWS+1;
        List<NativeOrgBatchSelection.Row> rows=new ArrayList<NativeOrgBatchSelection.Row>();
        for(int i=0;i<n;i++)rows.add(row("P"+i,"A","T1"));
        NativeOrgBatchSelection.Impact selection=capture(roots("A"),new String[]{"T1"},rows);
        PartnerOrgBatchPlan plan=NativeOrgBatchPartition.plan("G","ORG","10140CUG",selection);
        require(plan.getPageCount()==n && plan.getSlices().size()==2
            && plan.getSlices().get(0).getPages().size()==n-1 && plan.getSlices().get(1).getPages().size()==1,
            "More than 199999 pages under one root were not split");
        try { PartnerOrgBatchScope.requireSingleTransaction(selection);throw new AssertionError("Oversized unsplit page transaction accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS NativeOrgBatchPartitionScale rows="+n+" (offline only)");
    }

    private static void verify(PartnerOrgBatchPlan plan,NativeOrgBatchSelection.Impact selection,int maxRoots,int maxPages)
            throws Exception {
        require("G".equals(plan.getGroup()) && "ORG".equals(plan.getOperationOrg()) && "10140CUG".equals(plan.getNode())
            && selection.getPage().tabCode.equals(plan.getTabCode()) && selection.getFingerprint().equals(plan.getSelectionFingerprint())
            && selection.getTargets().equals(plan.getTargets()),"Plan identity or target scope changed");
        final Map<String,NativeOrgBatchSelection.Row> byPage=new HashMap<String,NativeOrgBatchSelection.Row>();
        for(NativeOrgBatchSelection.Row row:selection.getRows())byPage.put(row.getId(),row);
        final Map<String,NativeOrgBatchSelection.Row> current=new HashMap<String,NativeOrgBatchSelection.Row>(byPage);
        current.put("NEW_AFTER_PLAN",row("NEW_AFTER_PLAN",selection.getRoots().get(0),selection.getTargets().get(0)));
        NativeOrgBatchSelection.PageReader reader=new NativeOrgBatchSelection.PageReader() {
            public List<NativeOrgBatchSelection.Row> read(NativeOrgBatchPage p,String[] r,String[] t,boolean l) {
                throw new AssertionError("A planned slice expanded beyond its physical page identities");
            }
            public List<NativeOrgBatchSelection.Row> readPages(NativeOrgBatchPage p,String[] ids,boolean l) {
                List<NativeOrgBatchSelection.Row> rows=new ArrayList<NativeOrgBatchSelection.Row>();
                for(String id:ids)if(current.containsKey(id))rows.add(current.get(id));return rows;
            }
        };
        Set<String> seenRoots=new HashSet<String>(),seenPages=new HashSet<String>();
        List<PartnerOrgBatchPlan.Page> manifested=new ArrayList<PartnerOrgBatchPlan.Page>();long pageCount=0;
        for(PartnerOrgBatchPlan.Slice slice:plan.getSlices()) {
            require(slice.getRoots().size()>0 && slice.getRoots().size()<=maxRoots && slice.getPages().size()<=maxPages,
                "A planned slice exceeds an independent transaction limit");
            List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
            for(String root:slice.getRoots()) { require(selection.getRoots().contains(root),"Plan added a foreign root");
                seenRoots.add(root);refs.add(new BusinessObjectRef("c"+root,"BD_CUSTOMER",root,root,null)); }
            for(PartnerOrgBatchPlan.Page page:slice.getPages()) {
                NativeOrgBatchSelection.Row row=page==null?null:byPage.get(page.getId());
                require(row!=null && slice.getRoots().contains(row.getRoot()) && row.getRoot().equals(page.getRoot())
                    && row.getTarget().equals(page.getTarget()) && seenPages.add(page.getId()),"Plan omitted, duplicated or moved a page");
                manifested.add(page);
            }
            NativeOrgBatchSelection.Impact recaptured=NativeOrgBatchSelection.capture("G","BD_CUSTOMER",selection.getPage(),refs,
                plan.getTargets().toArray(new String[plan.getTargets().size()]),reader,false,
                slice.getPages().toArray(new PartnerOrgBatchPlan.Page[slice.getPages().size()]));
            require(recaptured.getRows().size()==slice.getPages().size(),"A planned slice lost a page or included a later insertion");
            pageCount+=slice.getPages().size();
        }
        require(seenRoots.equals(new HashSet<String>(selection.getRoots())) && seenPages.equals(byPage.keySet())
            && pageCount==plan.getPageCount(),"Plan lost selected roots or physical pages");
        require(PartnerOrgBatchManifest.fingerprint("G","ORG","10140CUG",selection.getPage().tabCode,selection.getFingerprint(),
            new ArrayList<String>(seenRoots),plan.getTargets(),manifested).equals(plan.getManifestFingerprint()),
            "Partition changed or failed to carry the selection manifest");
    }
    private static NativeOrgBatchSelection.Impact capture(List<BusinessObjectRef> roots,String[] targets,
            final List<NativeOrgBatchSelection.Row> rows) throws BusinessException {
        return NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,roots,targets,
            new NativeOrgBatchSelection.Reader() { public List<NativeOrgBatchSelection.Row> read(
                    NativeOrgBatchPage page,String[] ids,String[] orgs,boolean lock) { return rows; } },false);
    }
    private static List<BusinessObjectRef> roots(String... ids) {
        List<BusinessObjectRef> result=new ArrayList<BusinessObjectRef>();
        for(String id:ids)result.add(new BusinessObjectRef("c"+id,"BD_CUSTOMER",id,id,null));return result;
    }
    private static NativeOrgBatchSelection.Row row(String id,String root,String target) {
        return new NativeOrgBatchSelection.Row(id,root,target,"G");
    }
    private static PartnerOrgBatchPlan.Page[] planned(String... ids) {
        PartnerOrgBatchPlan.Page[] result=new PartnerOrgBatchPlan.Page[ids.length];
        for(int i=0;i<ids.length;i++)result[i]=new PartnerOrgBatchPlan.Page(ids[i],"A","T1");return result;
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
