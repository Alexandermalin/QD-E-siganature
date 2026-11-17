package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import nc.bs.bd.batchupdate.BatchUpdateOrgTabService;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.jdbc.framework.SQLParameter;
import nc.vo.bd.material.pfc.MaterialPfcVO;
import nc.vo.bd.material.pfcc.MaterialPFCCVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialOrgPage;
import nc.vo.uapbd.qd.esign.PartnerOrgPage;

/** Checks server-derived page scope and native metadata without invoking a database or NC service. */
public final class NativeOrgBatchSelectionTests {
    private NativeOrgBatchSelectionTests() { }
    public static void run() throws Exception {
        nativeDescriptors();
        final List<BusinessObjectRef> roots=Arrays.asList(ref("b","B"),ref("a","A"));
        final List<NativeOrgBatchSelection.Row> rows=Arrays.asList(row("P3","B","T2","G"),row("P2","A","T1","G"),row("P1","A","T1","G"));
        final int[] calls={0};
        NativeOrgBatchSelection.Reader reader=new NativeOrgBatchSelection.Reader() {
            public List<NativeOrgBatchSelection.Row> read(NativeOrgBatchPage page,String[] ids,String[] targets,boolean lock) {
                calls[0]++;require(page==NativeOrgBatchPage.CUSTOMER_FINANCE && Arrays.equals(ids,new String[]{"A","B"})
                    && Arrays.equals(targets,new String[]{"T1","T2"}) && !lock,"Native scope was not normalized before query");
                ids[0]="MUTATED";targets[0]="MUTATED";return rows;
            }
        };
        NativeOrgBatchSelection.Impact impact=NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,
            roots,new String[]{"T2","T1"},reader,false);
        require(calls[0]==1 && impact.getRoots().equals(Arrays.asList("A","B")) && impact.getTargets().equals(Arrays.asList("T1","T2"))
            && impact.getRows().size()==3 && "P1".equals(impact.getRows().get(0).getId())
            && "P2".equals(impact.getRows().get(1).getId()),"Multiple native page rows were lost or mutable");
        try { impact.getRows().clear();throw new AssertionError("Page scope was mutable"); } catch(UnsupportedOperationException expected) { }
        NativeBatchChangeSet change=change("one");
        NativeOrgBatchSnapshots.Capture evidence=NativeOrgBatchSnapshotTests.captureForImpact(impact,false);
        List<BusinessObjectRef> bound=NativeBatchRequestScope.bindOrganization("G","ORG","BD_CUSTOMER",roots,change,impact,evidence);
        require(bound.get(0).getClientObjectId().startsWith("orgbulk:") && bound.get(1).getClientObjectId().startsWith("orgbulk:"),
            "Organization batch requests cannot be distinguished during result recovery");
        NativeBatchRequestScope.verifyOrganization("G","ORG","BD_CUSTOMER",bound,change,impact,evidence);
        List<BusinessObjectRef> reversed=new ArrayList<BusinessObjectRef>(bound);Collections.reverse(reversed);
        NativeBatchRequestScope.verifyOrganization("G","ORG","BD_CUSTOMER",reversed,change,impact,evidence);
        try { NativeBatchRequestScope.verify("G","ORG","BD_CUSTOMER",bound,change);throw new AssertionError("Organization scope was accepted as a main tab"); }
        catch(BusinessException expected) { }
        rejectBound(bound,change,capture(roots,new String[]{"T1","T2"},Arrays.asList(row("P1","A","T1","G"),row("P3","B","T2","G"))));
        rejectBound(bound,change,capture(roots,new String[]{"T1","T2"},Arrays.asList(row("P1","A","T1","G"),row("P2","A","T2","G"),row("P3","B","T2","G"))));
        rejectBound(bound,change,capture(roots,new String[]{"T1","T2","T3"},rows));
        try { NativeBatchRequestScope.verifyOrganization("G","ORG","BD_CUSTOMER",bound,change("two"),impact,evidence);
            throw new AssertionError("Changed signed values were accepted"); } catch(BusinessException expected) { }
        try { NativeBatchRequestScope.verifyOrganization("G","ORG","BD_CUSTOMER",bound.subList(0,1),change,impact,evidence);
            throw new AssertionError("An incomplete signed root set was accepted"); } catch(BusinessException expected) { }
        invalidRows(roots);
        NativeOrgBatchSelection.Impact empty=capture(roots,new String[]{"T1"},Collections.<NativeOrgBatchSelection.Row>emptyList());
        require(empty.getRows().isEmpty() && empty.getRoots().size()==2,"A root without a matching page was not represented");
        groupedOutcomes();
        NativeOrgBatchSnapshotTests.run();
        String sql=NativeOrgBatchSelection.sql(NativeOrgBatchPage.CUSTOMER_FINANCE,2,3,true);
        require(sql.equals("SELECT pk_custfinance,pk_customer,pk_org,pk_group FROM bd_custfinance WHERE pk_customer IN (?,?)"
            +" AND pk_org IN (?,?,?) ORDER BY pk_customer,pk_org,pk_custfinance FOR UPDATE NOWAIT")
            && !sql.toLowerCase().contains("dr"),"Native selection predicate, bind parameters or lock changed");
        try { NativeOrgBatchSelection.sql(NativeOrgBatchPage.CUSTOMER_FINANCE,401,1,false);throw new AssertionError("Oversized SQL chunk accepted"); }
        catch(IllegalArgumentException expected) { }
        batchedQuery();
        plannedPageSelection(roots);
        organizationMembership();
        System.out.println("PASS NativeOrgBatchSelectionTests (native page identities, target scope and signed selection; no NC or Oracle)");
    }
    private static void plannedPageSelection(final List<BusinessObjectRef> roots) throws Exception {
        final int[] calls={0};
        NativeOrgBatchSelection.PageReader reader=new NativeOrgBatchSelection.PageReader() {
            public List<NativeOrgBatchSelection.Row> read(NativeOrgBatchPage p,String[] r,String[] t,boolean lock) {
                throw new AssertionError("A planned page slice expanded to every matching page");
            }
            public List<NativeOrgBatchSelection.Row> readPages(NativeOrgBatchPage p,String[] ids,boolean lock) {
                calls[0]++;require(Arrays.equals(ids,new String[]{"P1","P3"}) && lock,"Page slice was not normalized and locked");
                ids[0]="MUTATED";return Arrays.asList(row("P3","B","T2","G"),row("P1","A","T1","G"));
            }
        };
        NativeOrgBatchSelection.Impact selected=NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,
            roots,new String[]{"T1","T2"},reader,true,planned("P3","P1"));
        require(calls[0]==1 && selected.getRows().size()==2 && "P1".equals(selected.getRows().get(0).getId())
            && "P3".equals(selected.getRows().get(1).getId()),"Planned page identities were lost or expanded");
        NativeOrgBatchSelection.Impact empty=NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,
            roots,new String[]{"T1","T2"},reader,true,planned());
        require(empty.getRows().isEmpty() && empty.getRoots().size()==2 && calls[0]==1,"Explicit no-op page selection queried or expanded rows");
        rejectPageSelection(roots,new String[]{"P1","P1"},Collections.<NativeOrgBatchSelection.Row>emptyList());
        rejectPageSelection(roots,new String[]{"BAD'PK"},Collections.<NativeOrgBatchSelection.Row>emptyList());
        rejectPageSelection(roots,new String[]{"P1","P3"},Arrays.asList(row("P1","A","T1","G")));
        rejectPageSelection(roots,new String[]{"P1"},Arrays.asList(row("P1","A","OTHER","G")));
        rejectPageSelection(roots,new String[]{"P1"},Arrays.asList(row("P1","A","T2","G")));
        rejectPageSelection(roots,new String[]{"P1"},Arrays.asList(row("P1","A","T1","OTHER")));
        rejectPageSelection(roots,new String[]{"P1"},Arrays.asList(row("P1","OTHER","T1","G")));
        rejectPageSelection(roots,new String[]{"P1"},Arrays.asList(row("P1","B","T1","G")));
        rejectPageSelection(roots,new String[]{"P1"},Arrays.asList(row("P2","A","T1","G")));
        String[] tooMany=new String[nc.vo.uapbd.qd.esign.PartnerBatchDraft.MAX_SINGLE_TRANSACTION_PAGE_ROWS+1];
        rejectPageSelection(roots,tooMany,Collections.<NativeOrgBatchSelection.Row>emptyList());

        final int[] chunks={0};String[] ids=new String[401];
        for(int i=0;i<ids.length;i++)ids[i]=String.format(java.util.Locale.ENGLISH,"P%03d",Integer.valueOf(i));
        NativeOrgBatchSelection.Reader batched=NativeOrgBatchSelection.batched(new NativeOrgBatchSelection.Query() {
            public List<NativeOrgBatchSelection.Row> read(String sql,String[] values) {
                int number=chunks[0]++;require(values.length==(number==0?400:1) && sql.contains("WHERE pk_custfinance IN (")
                    && sql.endsWith("FOR UPDATE NOWAIT") && !sql.toLowerCase().contains("dr"),"Planned page SQL was not safely partitioned");
                List<NativeOrgBatchSelection.Row> result=new ArrayList<NativeOrgBatchSelection.Row>();
                for(String id:values)result.add(row(id,"A","T1","G"));return result;
            }
        });
        NativeOrgBatchSelection.Impact partitioned=NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,
            Collections.singletonList(ref("a","A")),new String[]{"T1"},batched,true,planned(ids));
        require(chunks[0]==2 && partitioned.getRows().size()==401,"Planned page query dropped a chunk");
    }
    private static void rejectPageSelection(List<BusinessObjectRef> roots,String[] ids,final List<NativeOrgBatchSelection.Row> rows) throws Exception {
        try { NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,roots,new String[]{"T1","T2"},
                new NativeOrgBatchSelection.PageReader() {
                    public List<NativeOrgBatchSelection.Row> read(NativeOrgBatchPage p,String[] r,String[] t,boolean l) { return rows; }
                    public List<NativeOrgBatchSelection.Row> readPages(NativeOrgBatchPage p,String[] selected,boolean l) { return rows; }
                },false,planned(ids));throw new AssertionError("Invalid planned page selection was accepted"); }
        catch(BusinessException expected) { }
    }
    private static nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page[] planned(String... ids) {
        nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page[] result=new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page[ids.length];
        for(int i=0;i<ids.length;i++)result[i]=new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page(ids[i],
            "P3".equals(ids[i])?"B":"A","P3".equals(ids[i])?"T2":"T1");
        return result;
    }
    private static void groupedOutcomes() throws Exception {
        List<BusinessObjectRef> roots=Arrays.asList(ref("a","A"),ref("b","B"),ref("c","C"));
        List<NativeOrgBatchSelection.Row> rows=Arrays.asList(row("P1","A","T1","G"),row("P2","A","T1","G"),
            row("P3","B","T1","G"));
        NativeOrgBatchSelection.Impact impact=capture(roots,new String[]{"T1"},rows);
        List<NativeBatchUpdateOutcome.Unit> pages=Arrays.asList(unit("P1","P1","SUCCESS"),unit("P2","P2","FAILED"),
            unit("P3","P3","UNCHANGED"));
        List<NativeOrgBatchOutcome.Root> result=NativeOrgBatchOutcome.group("G","BD_CUSTOMER",roots,impact,pages);
        require("PARTIAL".equals(result.get(0).getState()) && result.get(0).getSucceeded()==1 && result.get(0).getFailed()==1
            && "UNCHANGED".equals(result.get(1).getState()) && result.get(1).getPageCount()==1
            && "UNCHANGED".equals(result.get(2).getState()) && result.get(2).getPageCount()==0,
            "Mixed page outcomes or a root without matching pages were hidden");
        List<NativeBatchUpdateOutcome.Unit> failed=Arrays.asList(unit("P1","P1","FAILED"),unit("P2","P2","UNCHANGED"),
            unit("P3","P3","SUCCESS"));
        result=NativeOrgBatchOutcome.group("G","BD_CUSTOMER",roots,impact,failed);
        require("FAILED".equals(result.get(0).getState()) && "SUCCESS".equals(result.get(1).getState()),
            "Failure plus unchanged was incorrectly reported as a partial success");
        rejectOutcome(roots,impact,Arrays.asList(unit("P1","P1","SUCCESS"),unit("P2","P2","FAILED")));
        rejectOutcome(roots,impact,Arrays.asList(unit("P1","P1","SUCCESS"),unit("P2","P2","FAILED"),unit("P3","OTHER","SUCCESS")));
        rejectOutcome(roots,impact,Arrays.asList(unit("P1","P1","SUCCESS"),unit("P1","P2","FAILED"),unit("P3","P3","UNCHANGED")));
        rejectOutcome(roots,impact,Arrays.asList(unit("P1","P1","SUCCESS"),unit("P2","P2","UNKNOWN"),unit("P3","P3","UNCHANGED")));
    }
    private static NativeBatchUpdateOutcome.Unit unit(String client,String pk,String state) {
        return new NativeBatchUpdateOutcome.Unit(client,pk,state);
    }
    private static void rejectOutcome(List<BusinessObjectRef> roots,NativeOrgBatchSelection.Impact impact,
            List<NativeBatchUpdateOutcome.Unit> pages) throws Exception {
        try { NativeOrgBatchOutcome.group("G","BD_CUSTOMER",roots,impact,pages);
            throw new AssertionError("Incomplete or foreign organization-page result was accepted"); } catch(BusinessException expected) { }
    }
    private static void organizationMembership() throws Exception {
        final int[] calls={0};
        QdEsignStore store=new QdEsignStore() {
            @Override public List<Map<String,Object>> list(String sql,SQLParameter params) {
                calls[0]++;List<?> bound=params.getParameters();
                require(sql.contains("pk_group=?") && sql.contains("islastversion='Y'") && !sql.contains("isbusinessunit")
                    && !sql.contains("enablestate") && bound.size()<=901 && "G".equals(bound.get(0)),
                    "Group organization query lost native scope or parameter bounds");
                List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();
                for(int i=1;i<bound.size();i++) { Map<String,Object> row=new HashMap<String,Object>();row.put("pk_org",bound.get(i));result.add(row); }
                return result;
            }
        };
        List<String> ids=new ArrayList<String>();for(int i=0;i<901;i++) ids.add("T"+i);
        List<String> found=store.listOrganizationsInGroup("G",ids);
        require(calls[0]==2 && found.equals(ids),"Target organization group checks were not fully partitioned");
        PartnerOrgBatchScope.requireGroupMembership(ids,found);
        rejectMembership(Arrays.asList("T1","T2"),Arrays.asList("T1"));
        rejectMembership(Arrays.asList("T1","T2"),Arrays.asList("T1","OTHER"));
        rejectMembership(Arrays.asList("T1","T2"),Arrays.asList("T1","T1"));
    }
    private static void rejectMembership(List<String> expected,List<String> found) throws Exception {
        try { PartnerOrgBatchScope.requireGroupMembership(expected,found);throw new AssertionError("Invalid group membership accepted"); }
        catch(BusinessException correct) { }
    }
    private static void batchedQuery() throws Exception {
        List<BusinessObjectRef> roots=new ArrayList<BusinessObjectRef>();String[] targets=new String[401];
        for(int i=0;i<401;i++) {
            String suffix=String.format(java.util.Locale.ENGLISH,"%03d",Integer.valueOf(i));
            roots.add(ref("C"+suffix,"R"+suffix));targets[i]="T"+suffix;
        }
        final int[] calls={0};
        NativeOrgBatchSelection.Reader reader=NativeOrgBatchSelection.batched(new NativeOrgBatchSelection.Query() {
            public List<NativeOrgBatchSelection.Row> read(String sql,String[] args) {
                int part=calls[0]++;int[] sizes={800,401,401,2};
                require(part<sizes.length && args.length==sizes[part] && sql.endsWith("FOR UPDATE NOWAIT")
                    && !sql.toLowerCase().contains("dr"),"Organization selection query was not parameterized and split");
                require((part<2?"R000":"R400").equals(args[0])
                    && (part%2==0?"T000":"T400").equals(args[part<2?400:1]),"Selection query bind order changed");
                int placeholders=0;for(int i=0;i<sql.length();i++) if(sql.charAt(i)=='?') placeholders++;
                require(placeholders==args.length,"Selection query bind count differs from its placeholders");
                return Collections.emptyList();
            }
        });
        NativeOrgBatchSelection.Impact result=NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,
            roots,targets,reader,true);
        require(calls[0]==4 && result.getRoots().size()==401 && result.getTargets().size()==401,
            "Large organization selection was not fully partitioned");
    }
    private static void invalidRows(final List<BusinessObjectRef> roots) throws Exception {
        rejectCapture(roots,new String[]{"T1","T1"},Collections.<NativeOrgBatchSelection.Row>emptyList());
        rejectCapture(roots,new String[]{"T1"},Arrays.asList(row("P1","A","T1","OTHER")));
        rejectCapture(roots,new String[]{"T1"},Arrays.asList(row("P1","OUTSIDE","T1","G")));
        rejectCapture(roots,new String[]{"T1"},Arrays.asList(row("P1","A","T2","G")));
        rejectCapture(roots,new String[]{"T1"},Arrays.asList(row("P1","A","T1","G"),row("P1","B","T1","G")));
        rejectCapture(roots,new String[]{"T1"},Arrays.asList(row("P'1","A","T1","G")));
        try { NativeOrgBatchSelection.capture("G","BD_SUPPLIER",NativeOrgBatchPage.CUSTOMER_FINANCE,roots,new String[]{"T1"},
            new NativeOrgBatchSelection.Reader() { public List<NativeOrgBatchSelection.Row> read(NativeOrgBatchPage p,String[] r,String[] t,boolean l) {
                throw new AssertionError("Mismatched domain reached the database");
            }},false);throw new AssertionError("Mismatched domain accepted"); } catch(BusinessException expected) { }
    }
    private static void nativeDescriptors() throws Exception {
        String[] processors={
            "nc.impl.bd.cust.finance.CustFinanceBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.cust.sale.CustSaleBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.cust.creditctl.CustCreditctlBatchUpdateServiceImpl",
            "nc.impl.bd.supplier.finance.SupplierFiBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.supplier.stock.SupplierStockBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.fi.MaterialFiBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.pu.MaterialPuBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.sale.MaterialSaleBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.stock.MaterialStockBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.plan.MaterialPlanBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.prod.MaterialProdBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.cost.MaterialCostBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.pfc.MaterialPfcBatchUpdateForNewTransServiceImpl",
            "nc.impl.bd.material.pfcc.MaterialProfitCostBatchUpdateForNewTransServiceImpl"};
        NativeOrgBatchPage[] pages=NativeOrgBatchPage.values();require(pages.length==processors.length,"Native page descriptors changed");
        for(int i=0;i<pages.length;i++) {
            NativeOrgBatchPage page=pages[i];Object raw=Class.forName(processors[i]).newInstance();
            require(raw instanceof BatchUpdateOrgTabService,"Unexpected native organization batch service");
            BatchUpdateOrgTabService<?> service=(BatchUpdateOrgTabService<?>)raw;
            String base="BD_CUSTOMER".equals(page.domain)?"e4f48eaf-5567-4383-a370-a59cb3e8a451"
                :"BD_SUPPLIER".equals(page.domain)?"720dcc7c-ff19-48f4-b9c5-b90906682f45":"c7dc0ccd-8872-4eee-8882-160e8f49dfad";
            require(page.metadataId.equals(service.getMDId()) && base.equals(service.getBaseMDId())
                && NativeOrgBatchPage.require(page.domain,page.tabCode)==page,"Native metadata or page identity changed");
            java.lang.reflect.ParameterizedType parent=(java.lang.reflect.ParameterizedType)raw.getClass().getGenericSuperclass();
            require(page.nativeType().equals(parent.getActualTypeArguments()[0]),"Native page VO type changed");
            String[] children=service.getChildrenAttrNames();
            require(Arrays.equals(children==null?new String[0]:children,page.childAttributes()),
                "Native page child attributes changed without updating the audit scope");
            Class.forName(page.service,false,NativeOrgBatchSelectionTests.class.getClassLoader());
        }
        PartnerOrgPage[] partners=PartnerOrgPage.values();
        for(int i=0;i<partners.length;i++) require(partners[i].table.equals(pages[i].table) && partners[i].key.equals(pages[i].key)
            && partners[i].rootKey.equals(pages[i].rootKey),"Partner page physical mapping changed");
        MaterialOrgPage[] material={MaterialOrgPage.FINANCE,MaterialOrgPage.PURCHASE,MaterialOrgPage.SALE,MaterialOrgPage.STOCK,
            MaterialOrgPage.PLAN,MaterialOrgPage.PROD,MaterialOrgPage.COST};
        for(int i=0;i<material.length;i++) require(material[i].table.equals(pages[i+5].table) && material[i].key.equals(pages[i+5].key),
            "Material page physical mapping changed");
        MaterialPfcVO pfc=new MaterialPfcVO();MaterialPFCCVO pfcc=new MaterialPFCCVO();
        require(pfc.getTableName().equals(NativeOrgBatchPage.MATERIAL_PFC.table) && pfc.getPKFieldName().equals(NativeOrgBatchPage.MATERIAL_PFC.key)
            && pfcc.getTableName().equals(NativeOrgBatchPage.MATERIAL_PFCC.table) && pfcc.getPKFieldName().equals(NativeOrgBatchPage.MATERIAL_PFCC.key),
            "Native material profit/cost page mapping changed");
    }
    private static NativeOrgBatchSelection.Impact capture(List<BusinessObjectRef> roots,String[] targets,final List<NativeOrgBatchSelection.Row> rows)
            throws Exception {
        return NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,roots,targets,
            new NativeOrgBatchSelection.Reader() { public List<NativeOrgBatchSelection.Row> read(NativeOrgBatchPage p,String[] r,String[] t,boolean l) { return rows; }},false);
    }
    private static void rejectCapture(List<BusinessObjectRef> roots,String[] targets,List<NativeOrgBatchSelection.Row> rows) throws Exception {
        try { capture(roots,targets,rows);throw new AssertionError("Invalid native page scope was accepted"); } catch(BusinessException expected) { }
    }
    private static void rejectBound(List<BusinessObjectRef> refs,NativeBatchChangeSet change,NativeOrgBatchSelection.Impact impact) throws Exception {
        try { NativeBatchRequestScope.verifyOrganization("G","ORG","BD_CUSTOMER",refs,change,impact,
                NativeOrgBatchSnapshotTests.captureForImpact(impact,false));
            throw new AssertionError("Changed organization scope was accepted"); } catch(BusinessException expected) { }
    }
    private static NativeBatchChangeSet change(String value) throws Exception {
        Map<String,Object> values=new HashMap<String,Object>();values.put("name",value);
        return NativeBatchChangeSet.create(new String[]{"name"},values,new HashSet<String>(Arrays.asList("name")));
    }
    private static BusinessObjectRef ref(String client,String pk) { return new BusinessObjectRef(client,"BD_CUSTOMER",pk,pk,null); }
    private static NativeOrgBatchSelection.Row row(String pk,String root,String target,String group) {
        return new NativeOrgBatchSelection.Row(pk,root,target,group);
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
