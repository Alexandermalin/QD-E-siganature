package nc.ui.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchManifest;

/** A returned plan cannot omit selected roots, add targets or repeat physical page identities. */
public final class PartnerOrgBatchPlanTests {
    private static final String HASH="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private PartnerOrgBatchPlanTests() { }
    public static void run() throws Exception {
        final OperationContext request=context(new String[]{"A","B","C"},new String[]{"T1","T2"},null);
        final PartnerOrgBatchPlan valid=plan("G","ORG","10140CUG",Arrays.asList("T1","T2"),3,
            slice(new String[]{"A"},new String[]{"P1","P2"}),slice(new String[]{"A","B"},new String[]{"P3"}),
            slice(new String[]{"C"},new String[0]));
        final int[] calls={0};PartnerOrgBatchPlan result=QdPartnerOrgBatchPlan.read(request,new QdPartnerOrgBatchPlan.Reader() {
            public PartnerOrgBatchPlan read(OperationContext actual) { calls[0]++;require(actual==request,"Plan request changed");return valid; }
        });
        require(result==valid && calls[0]==1,"A complete plan with repeated roots was rejected");
        reject(request,new PartnerOrgBatchPlan("G","ORG","10140CUG","finance_info",HASH,Arrays.asList("T1","T2"),
            Arrays.asList(slice(new String[]{"A","B","C"},new String[]{"P1","P3"})),2,valid.getManifestFingerprint()));
        reject(request,new PartnerOrgBatchPlan("G","ORG","10140CUG","finance_info",HASH,Arrays.asList("T1","T2"),
            valid.getSlices(),3));
        reject(request,plan("G","ORG","10140CUG",Arrays.asList("T1","T2"),3,
            slice(new String[]{"A"},new String[]{"P1","P2"}),slice(new String[]{"A","B"},new String[]{"P3"})));
        reject(request,plan("G","ORG","10140CUG",Arrays.asList("T1","T2"),3,
            slice(new String[]{"A"},new String[]{"P1","P2"}),slice(new String[]{"B","C"},new String[]{"P2"})));
        reject(request,plan("G","ORG","10140CUG",Arrays.asList("T1","T3"),3,
            slice(new String[]{"A","B","C"},new String[]{"P1","P2","P3"})));
        reject(request,plan("OTHER","ORG","10140CUG",Arrays.asList("T1","T2"),3,
            slice(new String[]{"A","B","C"},new String[]{"P1","P2","P3"})));
        reject(request,plan("G","ORG","10140SUG",Arrays.asList("T1","T2"),3,
            slice(new String[]{"A","B","C"},new String[]{"P1","P2","P3"})));
        reject(request,plan("G","ORG","10140CUG",Arrays.asList("T1","T2"),4,
            slice(new String[]{"A","B","C"},new String[]{"P1","P2","P3"})));
        reject(request,plan("G","ORG","10140CUG",Arrays.asList("T1","T2"),2,
            slice(new String[]{"A","B","OTHER"},new String[]{"P1","P2"})));
        reject(request,plan("G","ORG","10140CUG",Arrays.asList("T1","T2"),2,
            slice(new String[]{"A","B","C"},new String[]{"P1","P'2"})));
        reject(request,plan("G","ORG","10140CUG",Arrays.asList("T1","T2"),1,
            new PartnerOrgBatchPlan.Slice(Arrays.asList("A","B","C"),Arrays.asList(new PartnerOrgBatchPlan.Page("P1","OTHER","T1")))));
        reject(request,plan("G","ORG","10140CUG",Arrays.asList("T1","T2"),1,
            new PartnerOrgBatchPlan.Slice(Arrays.asList("A","B","C"),Arrays.asList(new PartnerOrgBatchPlan.Page("P1","A","OTHER")))));
        reject(request,plan("G","ORG","10140CUG",Arrays.asList("T1","T2"),1,
            slice(new String[]{"A","B","C"},new String[]{"P1"}),slice(new String[]{"A"},new String[0])));
        reject(request,plan("G","ORG","10140CUG",Arrays.asList("T1","T2"),2,
            slice(new String[]{"A","B"},new String[]{"P1"}),slice(new String[]{"A","B","C"},new String[]{"P2"})));
        reject(request,plan("G","ORG","10140CUG",Arrays.asList("T1","T2"),2,
            slice(new String[]{"A","B"},new String[]{"P1"}),slice(new String[]{"B","C"},new String[]{"P2"})));
        reject(context(new String[]{"A","B","C"},new String[]{"T1","T2"},new String[]{"P1"}),valid);
        planAllRoots();
        fixedSlices(request,valid);
        System.out.println("PASS PartnerOrgBatchPlanTests (plan completeness and cross-slice page uniqueness; no NC session)");
    }
    private static void fixedSlices(OperationContext request,final PartnerOrgBatchPlan valid) throws Exception {
        QdPartnerOrgBatchSlices.Prepared prepared=QdPartnerOrgBatchSlices.prepare(request,new QdPartnerOrgBatchPlan.Reader() {
            public PartnerOrgBatchPlan read(OperationContext context) { return valid; }
        });
        List<QdPartnerOrgBatchSlices.Slice> slices=prepared.slices();
        require(slices.size()==3 && Arrays.equals(slices.get(0).roots(),new String[]{"A"})
            && Arrays.equals(slices.get(1).roots(),new String[]{"A","B"})
            && slices.get(2).draft().hasPageSelection() && slices.get(2).draft().getSelectedPages().length==0,
            "Planned pages, repeated roots or no-page roots were lost when preparing separately authorized slices");
        String[] changed=slices.get(0).roots();changed[0]="OTHER";
        require("A".equals(slices.get(0).roots()[0]),"A caller changed a prepared slice after planning");
        try { slices.clear();throw new AssertionError("Prepared slices can be removed"); } catch(UnsupportedOperationException expected) { }
        QdPartnerOrgBatchSlices.Slice first=slices.get(0);OperationContext authorized=authorized(first,first.draft(),"10140CUG");
        first.requireAuthorized(authorized);
        PartnerBatchDraft draft=first.draft();Map<String,Object> values=draft.getValues();values.put("name","altered");
        rejectSlice(first,authorized(first,new PartnerBatchDraft(draft.getTabId(),draft.getTabCode(),draft.getSelectedAttribute(),
            draft.getSelected(),values,draft.getTargetOrgs(),draft.getSelectedPages()),"10140CUG"));
        PartnerOrgBatchPlan.Page[] pages=draft.getSelectedPages();pages[0]=new PartnerOrgBatchPlan.Page(pages[0].getId(),"OTHER",pages[0].getTarget());
        rejectSlice(first,authorized(first,new PartnerBatchDraft(draft.getTabId(),draft.getTabCode(),draft.getSelectedAttribute(),
            draft.getSelected(),draft.getValues(),draft.getTargetOrgs(),pages),"10140CUG"));
        rejectSlice(first,authorized(first,draft,"10140CUO"));
        PartnerBatchDraft withoutPages=new PartnerBatchDraft(draft.getTabId(),draft.getTabCode(),draft.getSelectedAttribute(),
            draft.getSelected(),draft.getValues(),draft.getTargetOrgs());
        rejectSlice(first,authorized(first,withoutPages,"10140CUG"));
        slices.get(2).requireAuthorized(authorized(slices.get(2),slices.get(2).draft(),"10140CUG"));
        PartnerOrgBatchProgressTests.run(prepared);
        PartnerOrgBatchWorkflowTests.run(request,valid);
    }
    private static OperationContext authorized(QdPartnerOrgBatchSlices.Slice slice,PartnerBatchDraft draft,String node) {
        java.util.ArrayList<BusinessObjectRef> refs=new java.util.ArrayList<BusinessObjectRef>();
        for(String pk:slice.roots())refs.add(new BusinessObjectRef("orgbulk:"+HASH+":"+pk,"BD_CUSTOMER",pk,pk,null));
        return new OperationContext("0123456789abcdef0123456789abcdef","0123456789abcdef0123456789abcdef","G","ORG",node,
            "客户","BatchUpdate","批改","UPDATE",null,refs,OperationContext.OWNER_POLICY_PROTOCOL,null,null,null,draft);
    }
    private static void rejectSlice(QdPartnerOrgBatchSlices.Slice slice,OperationContext request) throws Exception {
        try { slice.requireAuthorized(request);throw new AssertionError("A changed authorization was accepted for a planned slice"); }
        catch(BusinessException expected) { }
    }
    private static void planAllRoots() throws Exception {
        final OperationContext request=context(new String[]{"A","B","C","D","E"},new String[]{"T1"},null);
        final int[] calls={0};
        final QdPartnerOrgBatchPlan.Reader reader=new QdPartnerOrgBatchPlan.Reader() {
            public PartnerOrgBatchPlan read(OperationContext chunk) {
                calls[0]++;require(chunk.getObjects().size()<=2 && chunk.getPartnerBatchDraft()==request.getPartnerBatchDraft(),
                    "Root chunk changed the request or exceeded its limit");
                java.util.ArrayList<String> roots=new java.util.ArrayList<String>();
                java.util.ArrayList<PartnerOrgBatchPlan.Page> pages=new java.util.ArrayList<PartnerOrgBatchPlan.Page>();
                for(BusinessObjectRef ref:chunk.getObjects()) {
                    roots.add(ref.getObjectPk());pages.add(new PartnerOrgBatchPlan.Page("P"+ref.getObjectPk(),ref.getObjectPk(),"T1"));
                }
                return plan("G","ORG","10140CUG",Arrays.asList("T1"),pages.size(),new PartnerOrgBatchPlan.Slice(roots,pages));
            }
        };
        List<PartnerOrgBatchPlan> plans=QdPartnerOrgBatchPlan.readAll(request,reader,2);
        require(calls[0]==3 && plans.size()==3 && plans.get(2).getSlices().get(0).getRoots().equals(Arrays.asList("E")),
            "Root selections beyond one planning request were lost or reordered");
        try { plans.clear();throw new AssertionError("Complete plans can be changed after validation"); }
        catch(UnsupportedOperationException expected) { }
        final int[] duplicateCalls={0};
        try { QdPartnerOrgBatchPlan.readAll(request,new QdPartnerOrgBatchPlan.Reader() {
            public PartnerOrgBatchPlan read(OperationContext chunk) {
                duplicateCalls[0]++;java.util.ArrayList<String> roots=new java.util.ArrayList<String>();
                for(BusinessObjectRef ref:chunk.getObjects())roots.add(ref.getObjectPk());
                return plan("G","ORG","10140CUG",Arrays.asList("T1"),1,new PartnerOrgBatchPlan.Slice(roots,
                    Arrays.asList(new PartnerOrgBatchPlan.Page("SAME",roots.get(0),"T1"))));
            }
        },2);throw new AssertionError("Page identity duplicated across root plans was accepted"); }
        catch(BusinessException expected) { require(duplicateCalls[0]==2,"Cross-plan duplicate was not rejected promptly"); }
        final int[] failedCalls={0};
        try { QdPartnerOrgBatchPlan.readAll(request,new QdPartnerOrgBatchPlan.Reader() {
            public PartnerOrgBatchPlan read(OperationContext chunk) throws BusinessException {
                failedCalls[0]++;if(failedCalls[0]==2)throw new BusinessException("offline failure");
                return reader.read(chunk);
            }
        },2);throw new AssertionError("An incomplete plan was returned after a later planning failure"); }
        catch(BusinessException expected) { require(failedCalls[0]==2,"Planning continued after a failure"); }
    }
    /** Explicit scale test, kept out of the ordinary offline suite. */
    public static void scale() throws Exception {
        int count=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS+1;String[] roots=new String[count];
        for(int i=0;i<count;i++)roots[i]="A"+i;
        OperationContext request=context(roots,new String[]{"T1"},null);final int[] calls={0};
        List<PartnerOrgBatchPlan> plans=QdPartnerOrgBatchPlan.readAll(request,new QdPartnerOrgBatchPlan.Reader() {
            public PartnerOrgBatchPlan read(OperationContext chunk) {
                calls[0]++;java.util.ArrayList<String> selected=new java.util.ArrayList<String>();
                for(BusinessObjectRef ref:chunk.getObjects())selected.add(ref.getObjectPk());
                return plan("G","ORG","10140CUG",Arrays.asList("T1"),0,
                    new PartnerOrgBatchPlan.Slice(selected,Collections.<PartnerOrgBatchPlan.Page>emptyList()));
            }
        });
        require(calls[0]==2 && plans.size()==2
            && plans.get(0).getSlices().get(0).getRoots().size()==PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS
            && plans.get(1).getSlices().get(0).getRoots().size()==1,
            "More than 199999 roots did not produce two complete planning requests");
        System.out.println("PASS PartnerOrgBatchPlanScale roots="+count+" (offline only)");
    }
    private static OperationContext context(String[] roots,String[] targets,String[] pages) {
        Map<String,Object> values=new HashMap<String,Object>();values.put("name","value");
        PartnerOrgBatchPlan.Page[] selectedPages=null;
        if(pages!=null) {
            selectedPages=new PartnerOrgBatchPlan.Page[pages.length];
            for(int i=0;i<pages.length;i++)selectedPages[i]=new PartnerOrgBatchPlan.Page(pages[i],roots[0],targets[0]);
        }
        PartnerBatchDraft draft=new PartnerBatchDraft("TAB","finance_info","name",new String[]{"name"},values,targets,selectedPages);
        java.util.ArrayList<BusinessObjectRef> refs=new java.util.ArrayList<BusinessObjectRef>();
        for(String root:roots)refs.add(new BusinessObjectRef("client-"+root,"BD_CUSTOMER",root,null,null));
        return new OperationContext(null,null,"G","ORG","10140CUG","客户","BatchUpdate","批改","UPDATE",null,refs,
            OperationContext.OWNER_POLICY_PROTOCOL,null,null,null,draft);
    }
    private static PartnerOrgBatchPlan plan(String group,String org,String node,List<String> targets,long count,
            PartnerOrgBatchPlan.Slice... slices) {
        java.util.Set<String> roots=new java.util.HashSet<String>();
        java.util.List<PartnerOrgBatchPlan.Page> pages=new java.util.ArrayList<PartnerOrgBatchPlan.Page>();
        for(PartnerOrgBatchPlan.Slice slice:slices) { roots.addAll(slice.getRoots());pages.addAll(slice.getPages()); }
        String manifest=PartnerOrgBatchManifest.fingerprint(group,org,node,"finance_info",HASH,
            new java.util.ArrayList<String>(roots),targets,pages);
        return new PartnerOrgBatchPlan(group,org,node,"finance_info",HASH,targets,Arrays.asList(slices),count,manifest);
    }
    private static PartnerOrgBatchPlan.Slice slice(String[] roots,String[] pages) {
        java.util.ArrayList<PartnerOrgBatchPlan.Page> result=new java.util.ArrayList<PartnerOrgBatchPlan.Page>();
        for(String page:pages)result.add(new PartnerOrgBatchPlan.Page(page,roots[0],"T1"));
        return new PartnerOrgBatchPlan.Slice(Arrays.asList(roots),result);
    }
    private static void reject(OperationContext request,PartnerOrgBatchPlan plan) throws Exception {
        try { QdPartnerOrgBatchPlan.verify(request,plan);throw new AssertionError("Invalid organization plan accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
