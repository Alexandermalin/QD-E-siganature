package nc.bs.uapbd.qd.esign.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.audit.NativeDeleteMetadataGraph;
import nc.bs.uapbd.qd.esign.persistence.NativeOrgBatchReceiptRecords;
import nc.bs.uapbd.qd.esign.persistence.NativeOrgBatchTargetRecords;
import nc.md.model.IBean;
import nc.md.model.IColumn;
import nc.md.model.ITable;
import nc.vo.bd.cust.finance.CustFinanceVO;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Exercises page-level audit attribution, metadata drift and historical-row checks without a database. */
public final class NativeOrgBatchSnapshotTests {
    private NativeOrgBatchSnapshotTests() { }
    public static final class ReceiptFixture {
        public final Map<String,Object> request;public final List<Map<String,Object>> objects;
        public final NativeOrgBatchReceipt.Evidence evidence;public final NativeOrgBatchTargetScope.Evidence targetEvidence;
        public final NativeOrgBatchPreparedScope.Evidence preparedEvidence;
        private ReceiptFixture(Map<String,Object> request,List<Map<String,Object>> objects,NativeOrgBatchReceipt.Evidence evidence,
                NativeOrgBatchTargetScope.Evidence targetEvidence,NativeOrgBatchPreparedScope.Evidence preparedEvidence) {
            this.request=request;this.objects=objects;this.evidence=evidence;this.targetEvidence=targetEvidence;this.preparedEvidence=preparedEvidence;
        }
    }
    public static ReceiptFixture receiptFixture() throws Exception {
        Fixture fixture=new Fixture(false);
        NativeOrgBatchReconciliation.Result result=fixture.reconcile(fixture.beforeVOs(),fixture.afterVOs(),fixture.after,
            fixture.returned(),fixture.excluded);
        NativeOrgBatchReceipt.Evidence evidence=NativeOrgBatchReceipt.capture("0123456789abcdef0123456789abcdef","G","ORG",
            NativeOrgBatchPage.CUSTOMER_FINANCE,fixture.authorized,result);
        return receiptFixture(fixture,evidence);
    }
    public static ReceiptFixture emptyReceiptFixture() throws Exception {
        Fixture fixture=new Fixture(true);
        NativeOrgBatchReconciliation.Result result=fixture.reconcile(Collections.<SuperVO>emptyList(),Collections.<SuperVO>emptyList(),
            fixture.after,null,Collections.<String,String>emptyMap());
        NativeOrgBatchReceipt.Evidence evidence=NativeOrgBatchReceipt.capture("0123456789abcdef0123456789abcdef","G","ORG",
            NativeOrgBatchPage.CUSTOMER_FINANCE,fixture.authorized,result);
        return receiptFixture(fixture,evidence);
    }
    /** The native VO uses an offline primary-key override because NC metadata is unavailable here. */
    public static ErrLogReturnValue nativeReplyFixture() throws Exception { return new Fixture(false).returned(); }
    public static Class<? extends SuperVO> nativeReplyTypeFixture() { return TestFinanceVO.class; }
    /** Explicit scale exercise; callers run separately so ordinary unit tests stay quick. */
    public static void scale() throws Exception {
        final int size=10001;long start=System.currentTimeMillis();List<BusinessObjectRef> roots=new ArrayList<BusinessObjectRef>();
        for(int i=0;i<size;i++) { String pk="I"+i;roots.add(ref("c"+i,pk)); }
        NativeOrgBatchSelection.Impact impact=NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,
            roots,new String[]{"T"},new NativeOrgBatchSelection.Reader() {
                public List<NativeOrgBatchSelection.Row> read(NativeOrgBatchPage page,String[] ids,String[] targets,boolean lock) {
                    return Collections.emptyList();
                }
            },true);
        Data data=new Data();NativeOrgBatchSnapshots.Capture before=NativeOrgBatchSnapshots.capture(impact,data.bean(),data,true);
        Map<String,Object> values=new HashMap<String,Object>();values.put("pk_payterm","new");
        NativeBatchChangeSet changes=NativeBatchChangeSet.create(new String[]{"pk_payterm"},values,
            new java.util.HashSet<String>(Arrays.asList("pk_payterm")));
        List<BusinessObjectRef> authorized=NativeBatchRequestScope.bindOrganization("G","ORG","BD_CUSTOMER",roots,changes,impact,before);
        NativeOrgBatchSnapshots.Capture after=NativeOrgBatchSnapshots.recapture(before,data.bean(),data,false);
        NativeOrgBatchReconciliation.Result outcome=NativeOrgBatchReconciliation.reconcileForTest("0123456789abcdef0123456789abcdef","G","ORG","BD_CUSTOMER",authorized,
            impact,changes,before,after,Collections.<SuperVO>emptyList(),Collections.<SuperVO>emptyList(),null,
            Collections.<String,String>emptyMap(),TestFinanceVO.class);
        String id="0123456789abcdef0123456789abcdef",event="2026-11-30 16:01:01";
        NativeOrgBatchReceipt.Evidence evidence=NativeOrgBatchReceipt.capture(id,"G","ORG",NativeOrgBatchPage.CUSTOMER_FINANCE,authorized,outcome);
        Map<String,Object> request=EsignValues.row("request_id",id,"pk_group","G","pk_org","ORG","node_code","10140CUG",
            "button_code","BatchUpdate","operation_type","UPDATE","policy_mode",OperationContext.OWNER_POLICY_PROTOCOL,
            "signature_state","SUCCESS","business_state","RUNNING","finished_at",null,"created_at","2026-11-30 15:59:59",
            "archive_month","202611","owner_object_count",Integer.valueOf(size));
        List<Map<String,Object>> objects=new ArrayList<Map<String,Object>>();String zero="00000000000000000000000000000000";
        for(int i=0;i<size;i++) {
            BusinessObjectRef ref=authorized.get(i);String suffix=Integer.toHexString(i+1),objectId=zero.substring(suffix.length())+suffix;
            objects.add(EsignValues.row("object_id",objectId,"request_id",id,"client_object_id",ref.getClientObjectId(),
                "object_type",ref.getObjectType(),"object_pk",ref.getObjectPk(),"bill_code",ref.getBillCode(),"pk_group","G",
                "pk_org","ORG","target_org","ORG","policy_owner_org","G","relation_type","ROOT","parent_object_id",null,"desired_owner_org",null,"unit_state","PENDING"));
        }
        NativeOrgBatchTargetScope.Evidence target=NativeOrgBatchTargetScope.captureForTest(id,"G","ORG",NativeOrgBatchPage.CUSTOMER_FINANCE,
            authorized,impact.getTargets());
        Map<String,Object> preparing=new HashMap<String,Object>(request);preparing.put("business_state","PENDING");preparing.put("signature_state","PENDING");
        List<Map<String,Object>> targetRows=NativeOrgBatchTargetRecords.encode(preparing,objects,NativeOrgBatchPage.CUSTOMER_FINANCE,target);
        List<Map<String,Object>> records=NativeOrgBatchReceiptRecords.encode(request,objects,NativeOrgBatchPage.CUSTOMER_FINANCE,evidence,event);
        for(Map<String,Object> object:objects) object.put("unit_state","UNCHANGED");
        request.put("business_state","SUCCESS");request.put("finished_at",event);
        NativeOrgBatchReceipt.Evidence decoded=NativeOrgBatchReceiptRecords.decode(request,objects,NativeOrgBatchPage.CUSTOMER_FINANCE,records);
        require(records.size()==size+1 && decoded.getHeaders().size()==size && decoded.getPages().isEmpty()
            && decoded.getTargets().equals(Collections.singletonList("T")) && targetRows.size()==2
            && NativeOrgBatchTargetRecords.decode(request,objects,NativeOrgBatchPage.CUSTOMER_FINANCE,targetRows).getTargets().equals(target.getTargets()),
            "Large receipt dropped archive or target evidence");
        System.out.println("PASS NativeOrgBatchReceiptScale " + size + " roots, no matching pages, elapsed_ms="
            + (System.currentTimeMillis()-start) + " (offline only)");
    }
    public static void run() throws Exception {
        final List<BusinessObjectRef> roots=Arrays.asList(ref("a","A"),ref("b","B"),ref("c","C"));
        final List<NativeOrgBatchSelection.Row> rows=Arrays.asList(selected("P1","A"),selected("P2","A"),selected("P3","B"));
        NativeOrgBatchSelection.Impact impact=NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,
            roots,new String[]{"T"},new NativeOrgBatchSelection.Reader() {
                public List<NativeOrgBatchSelection.Row> read(NativeOrgBatchPage page,String[] ids,String[] targets,boolean lock) { return rows; }
            },false);
        Data data=new Data();
        NativeOrgBatchSnapshots.Capture before=NativeOrgBatchSnapshots.capture(impact,data.bean(),data,true);
        require(data.locked>0 && before.pageObjects().size()==3 && "P1".equals(before.pageObjects().get(0).getClientObjectId())
            && "P1".equals(before.pageObjects().get(0).getObjectPk()),"Page references are not stable server-selected identities");
        Map<String,List<SnapshotRow>> byRoot=before.byArchive("G","BD_CUSTOMER",roots);
        require(byRoot.get("a").size()==3 && byRoot.get("b").size()==2 && byRoot.get("c").isEmpty(),
            "Page or physical extension rows were attributed to the wrong archive");
        try { byRoot.get("a").clear();throw new AssertionError("Snapshot rows are mutable"); } catch(UnsupportedOperationException expected) { }
        try { before.byArchive("G","BD_CUSTOMER",roots.subList(0,2));throw new AssertionError("Incomplete authorization accepted"); }
        catch(BusinessException expected) { }
        Map<String,Object> changeValues=new HashMap<String,Object>();changeValues.put("name","new");
        NativeBatchChangeSet change=NativeBatchChangeSet.create(new String[]{"name"},changeValues,
            new java.util.HashSet<String>(Arrays.asList("name")));
        List<BusinessObjectRef> authorized=NativeBatchRequestScope.bindOrganization("G","ORG","BD_CUSTOMER",roots,change,impact,before);
        NativeBatchRequestScope.verifyOrganization("G","ORG","BD_CUSTOMER",authorized,change,impact,before);
        data.replace(page("P1","A","T","G","new"));data.rows.add(extension("P2","added"));
        NativeOrgBatchSnapshots.Capture after=NativeOrgBatchSnapshots.recapture(before,data.bean(),data,false);
        require(after.pages().get("P2").size()==2 && "new".equals(find(after.pages().get("P1"),"bd_custfinance").getValues().get("value"))
            && after.byArchive("G","BD_CUSTOMER",roots).get("a").size()==4,
            "Recapture missed a changed page or newly linked extension");
        try { NativeBatchRequestScope.verifyOrganization("G","ORG","BD_CUSTOMER",authorized,change,impact,after);
            throw new AssertionError("Changed page or extension evidence reused an authorization"); }
        catch(BusinessException expected) { }
        data=new Data();
        for(java.util.Iterator<SnapshotRow> it=data.rows.iterator();it.hasNext();) if("bd_custfinance_ext".equals(it.next().getEntity())) it.remove();
        NativeOrgBatchSnapshots.Capture metadataBefore=NativeOrgBatchSnapshots.capture(impact,data.bean(),data,false);
        List<BusinessObjectRef> metadataAuthorized=NativeBatchRequestScope.bindOrganization("G","ORG","BD_CUSTOMER",roots,change,impact,metadataBefore);
        Collections.reverse(data.rows);
        require(metadataBefore.getFingerprint().equals(NativeOrgBatchSnapshots.capture(impact,data.bean(),data,false).getFingerprint()),
            "Identical physical evidence changed when database row order changed");
        data.extra=false;
        NativeOrgBatchSnapshots.Capture metadataAfter=NativeOrgBatchSnapshots.capture(impact,data.bean(),data,false);
        try { NativeBatchRequestScope.verifyOrganization("G","ORG","BD_CUSTOMER",metadataAuthorized,change,impact,metadataAfter);
            throw new AssertionError("Changed empty extension metadata reused an authorization"); }
        catch(BusinessException expected) { }
        data=new Data();data.replace(page("P1","A","T","G","main"));
        data.replace(row("bd_custfinance_ext","P1","pk_custfinance","P1","pk_customer","A","pk_org","T","pk_group","G","value","extra"));
        NativeOrgBatchSnapshots.Capture tableBefore=NativeOrgBatchSnapshots.capture(impact,data.bean(),data,false);
        data.replace(page("P1","A","T","G","extra"));
        data.replace(row("bd_custfinance_ext","P1","pk_custfinance","P1","pk_customer","A","pk_org","T","pk_group","G","value","main"));
        NativeOrgBatchSnapshots.Capture tableAfter=NativeOrgBatchSnapshots.capture(impact,data.bean(),data,false);
        require(!tableBefore.getFingerprint().equals(tableAfter.getFingerprint()),"Swapping values between physical tables was not bound to authorization");
        data=new Data();before=NativeOrgBatchSnapshots.capture(impact,data.bean(),data,false);
        data.replace(page("P1","A","OTHER","G","old"));
        rejectRecapture(before,data,"A page transferred to a different organization was accepted");
        data=new Data();before=NativeOrgBatchSnapshots.capture(impact,data.bean(),data,false);
        data.extra=false;rejectRecapture(before,data,"Changed page metadata was accepted");
        data=new Data();data.replace(page("P1","OTHER","T","G","old"));
        try { NativeOrgBatchSnapshots.capture(impact,data.bean(),data,false);throw new AssertionError("A foreign page was accepted"); }
        catch(BusinessException expected) { }
        data=new Data();data.rows.add(page("P1","A","T","G","duplicate"));
        try { NativeOrgBatchSnapshots.capture(impact,data.bean(),data,false);throw new AssertionError("A duplicate page was accepted"); }
        catch(BusinessException expected) { }
        data=new Data();data.wrongBean=true;
        try { NativeOrgBatchSnapshots.capture(impact,data.bean(),data,false);throw new AssertionError("A different metadata identity was accepted"); }
        catch(BusinessException expected) { }
        reconciliation();
        System.out.println("PASS NativeOrgBatchSnapshotTests (page, extension and outcome evidence; no NC or Oracle)");
    }

    private static void reconciliation() throws Exception {
        Fixture f=new Fixture(false);
        NativeOrgBatchReconciliation.Result result=f.reconcile(f.beforeVOs(),f.afterVOs(),f.after,f.returned(),f.excluded);
        require(result.getPages().size()==3 && "SUCCESS".equals(result.getPages().get(0).getState())
            && "FAILED".equals(result.getPages().get(1).getState()) && "VALIDATION".equals(result.getPages().get(1).getFailureStage())
            && "UNCHANGED".equals(result.getPages().get(2).getState()),"Page outcomes were not reconciled");
        require("PARTIAL".equals(result.getRoots().get(0).getState()) && result.getRoots().get(0).getSucceeded()==1
            && result.getRoots().get(0).getFailed()==1 && "UNCHANGED".equals(result.getRoots().get(1).getState())
            && "UNCHANGED".equals(result.getRoots().get(2).getState()) && result.getRoots().get(2).getPageCount()==0,
            "Mixed or empty archive outcome was not preserved");
        String first=f.authorized.get(0).getClientObjectId();
        require(result.getBeforeByArchive().get(first).size()==3 && result.getAfterByArchive().get(first).size()==3,
            "Page and extension audit rows were not attributed to the signed archive");
        try { result.getPages().clear();throw new AssertionError("Page results are mutable"); } catch(UnsupportedOperationException expected) { }
        try { result.getAfterByArchive().get(first).clear();throw new AssertionError("Archive evidence is mutable"); }
        catch(UnsupportedOperationException expected) { }
        receiptTests(f,result);

        reject(f,f.beforeVOs(),f.afterVOs(),f.before,f.returned(),f.excluded);
        reject(f,f.beforeVOs(),Arrays.<SuperVO>asList(finance("P1","A","old"),finance("P2","A","old"),finance("P3","B","new")),
            f.after,f.returned(),f.excluded);
        reject(f,f.beforeVOs(),Arrays.<SuperVO>asList(finance("P1","OTHER","new"),finance("P2","A","old"),finance("P3","B","new")),
            f.after,f.returned(),f.excluded);
        CustFinanceVO wrongOrg=finance("P1","A","new");wrongOrg.setPk_org("OTHER");
        reject(f,f.beforeVOs(),Arrays.<SuperVO>asList(wrongOrg,finance("P2","A","old"),finance("P3","B","new")),
            f.after,f.returned(),f.excluded);
        reject(f,f.beforeVOs(),Arrays.<SuperVO>asList(finance("P1","A","new"),finance("P2","A","old")),
            f.after,f.returned(),f.excluded);
        reject(f,f.beforeVOs(),Arrays.<SuperVO>asList(finance("P1","A","new"),finance("P1","A","new"),finance("P3","B","new")),
            f.after,f.returned(),f.excluded);
        reject(f,f.beforeVOs(),Arrays.<SuperVO>asList(new nc.vo.bd.cust.CustomerVO(),finance("P2","A","old"),finance("P3","B","new")),
            f.after,f.returned(),f.excluded);
        reject(f,f.beforeVOs(),f.afterVOs(),f.after,new ErrLogReturnValue(new ErrLogResult(null,1,null),
            new SuperVO[]{finance("P1","A","new"),finance("P1","A","new")},3),f.excluded);
        Map<String,String> invalid=new HashMap<String,String>();invalid.put("P2","UNKNOWN");
        reject(f,f.beforeVOs(),f.afterVOs(),f.after,f.returned(),invalid);

        Fixture noPhysicalChange=new Fixture(false);noPhysicalChange.data.replace(page("P1","A","T","G","old"));
        NativeOrgBatchSnapshots.Capture unchanged=NativeOrgBatchSnapshots.recapture(noPhysicalChange.before,noPhysicalChange.data.bean(),noPhysicalChange.data,false);
        reject(noPhysicalChange,noPhysicalChange.beforeVOs(),noPhysicalChange.afterVOs(),unchanged,noPhysicalChange.returned(),noPhysicalChange.excluded);
        Fixture unrelatedChange=new Fixture(false);
        unrelatedChange.data.replace(row("bd_custfinance","P1","pk_custfinance","P1","pk_customer","A","pk_org","T",
            "pk_group","G","value","unrelated","pk_payterm","old"));
        NativeOrgBatchSnapshots.Capture unrelated=NativeOrgBatchSnapshots.recapture(unrelatedChange.before,unrelatedChange.data.bean(),unrelatedChange.data,false);
        reject(unrelatedChange,unrelatedChange.beforeVOs(),unrelatedChange.afterVOs(),unrelated,unrelatedChange.returned(),unrelatedChange.excluded);
        Fixture failedChanged=new Fixture(false);failedChanged.data.replace(page("P2","A","T","G","changed"));
        NativeOrgBatchSnapshots.Capture changed=NativeOrgBatchSnapshots.recapture(failedChanged.before,failedChanged.data.bean(),failedChanged.data,false);
        reject(failedChanged,failedChanged.beforeVOs(),failedChanged.afterVOs(),changed,failedChanged.returned(),failedChanged.excluded);

        Fixture empty=new Fixture(true);
        NativeOrgBatchReconciliation.Result noPages=empty.reconcile(Collections.<SuperVO>emptyList(),Collections.<SuperVO>emptyList(),
            empty.after,null,Collections.<String,String>emptyMap());
        require(noPages.getPages().isEmpty() && noPages.getRoots().size()==3 && "UNCHANGED".equals(noPages.getRoots().get(0).getState()),
            "No matching pages did not preserve the selected archives");
        reject(empty,Collections.<SuperVO>emptyList(),Collections.<SuperVO>emptyList(),empty.after,
            new ErrLogReturnValue(new ErrLogResult(null,0,null),new SuperVO[0],0),Collections.<String,String>emptyMap());
        NativeOrgBatchReceipt.Evidence emptyReceipt=NativeOrgBatchReceipt.capture("0123456789abcdef0123456789abcdef","G","ORG",
            NativeOrgBatchPage.CUSTOMER_FINANCE,empty.authorized,noPages);
        require(emptyReceipt.getPages().isEmpty() && emptyReceipt.getHeaders().size()==3
            && emptyReceipt.getHeaders().get(0).getPageCount()==0 && emptyReceipt.getTargets().equals(Arrays.asList("NO_ROWS","T")),
            "Archives or target organizations without pages lost their receipt markers");
        rejectReceipt(empty,emptyReceipt.getHeaders().subList(1,3),emptyReceipt.getPages(),receiptStates(noPages));
    }

    private static void receiptTests(Fixture f,NativeOrgBatchReconciliation.Result result) throws Exception {
        NativeOrgBatchReceipt.Evidence captured=NativeOrgBatchReceipt.capture("0123456789abcdef0123456789abcdef","G","ORG",
            NativeOrgBatchPage.CUSTOMER_FINANCE,f.authorized,result);
        try { NativeOrgBatchReceipt.capture("ffffffffffffffffffffffffffffffff","G","ORG",
                NativeOrgBatchPage.CUSTOMER_FINANCE,f.authorized,result);
            throw new AssertionError("A reconciled result was reused for another request"); }
        catch(BusinessException expected) { }
        try { NativeOrgBatchReceipt.capture("0123456789abcdef0123456789abcdef","OTHER","ORG",
                NativeOrgBatchPage.CUSTOMER_FINANCE,f.authorized,result);
            throw new AssertionError("A reconciled result was reused under another group"); }
        catch(BusinessException expected) { }
        try { NativeOrgBatchReceipt.capture("0123456789abcdef0123456789abcdef","G","OTHER",
                NativeOrgBatchPage.CUSTOMER_FINANCE,f.authorized,result);
            throw new AssertionError("A reconciled result was reused under another operation organization"); }
        catch(BusinessException expected) { }
        BusinessObjectRef original=f.authorized.get(0);
        List<BusinessObjectRef> changed=new ArrayList<BusinessObjectRef>(f.authorized);
        changed.set(0,new BusinessObjectRef(original.getClientObjectId(),original.getObjectType(),original.getObjectPk(),"OTHER-CODE",
            original.getParentClientObjectId(),original.getDesiredOwnerOrg(),original.getPolicyOwnerOrg(),original.getTargetOrg()));
        try { NativeOrgBatchReceipt.capture("0123456789abcdef0123456789abcdef","G","ORG",
                NativeOrgBatchPage.CUSTOMER_FINANCE,changed,result);
            throw new AssertionError("A reconciled result was reused with changed archive identity details"); }
        catch(BusinessException expected) { }
        List<BusinessObjectRef> reordered=new ArrayList<BusinessObjectRef>(f.authorized);Collections.reverse(reordered);
        require(NativeOrgBatchReceipt.capture("0123456789abcdef0123456789abcdef","G","ORG",
            NativeOrgBatchPage.CUSTOMER_FINANCE,reordered,result).getHeaders().size()==f.authorized.size(),
            "Reordering the same authorization changed the outcome");
        receiptRecords(f,result,captured);
        require(captured.getHeaders().size()==3 && captured.getHeaders().get(0).getPageCount()==2
            && captured.getHeaders().get(2).getPageCount()==0 && captured.getPages().size()==3
            && captured.getTargets().equals(Arrays.asList("NO_ROWS","T")),
            "Receipt markers do not include every signed archive");
        Map<String,String> states=receiptStates(result);
        List<NativeOrgBatchReceipt.Header> headers=new ArrayList<NativeOrgBatchReceipt.Header>(captured.getHeaders());
        List<NativeOrgBatchReceipt.Page> rows=new ArrayList<NativeOrgBatchReceipt.Page>(captured.getPages());
        Collections.reverse(headers);Collections.reverse(rows);
        NativeOrgBatchReceipt.Evidence restored=NativeOrgBatchReceipt.verify("0123456789abcdef0123456789abcdef","G","ORG",
            NativeOrgBatchPage.CUSTOMER_FINANCE,f.authorized,states,Arrays.asList("T","NO_ROWS"),headers,rows);
        require("P1".equals(restored.getPages().get(0).getPk()) && "P2".equals(restored.getPages().get(1).getPk()),
            "Stored receipt order changed its meaning");
        try { restored.getHeaders().clear();throw new AssertionError("Receipt headers are mutable"); } catch(UnsupportedOperationException expected) { }
        try { restored.getPages().clear();throw new AssertionError("Receipt rows are mutable"); } catch(UnsupportedOperationException expected) { }
        try { restored.getTargets().clear();throw new AssertionError("Receipt targets are mutable"); } catch(UnsupportedOperationException expected) { }
        rejectReceipt(f,headers,rows.subList(1,rows.size()),states);
        List<NativeOrgBatchReceipt.Page> duplicate=new ArrayList<NativeOrgBatchReceipt.Page>(rows);duplicate.add(rows.get(0));
        rejectReceipt(f,headers,duplicate,states);
        List<NativeOrgBatchReceipt.Page> altered=new ArrayList<NativeOrgBatchReceipt.Page>(rows);NativeOrgBatchReceipt.Page item=altered.get(0);
        altered.set(0,new NativeOrgBatchReceipt.Page(item.getClientId(),item.getPk(),"OTHER",item.getState(),item.getFailureStage()));
        rejectReceipt(f,headers,altered,states);
        altered=new ArrayList<NativeOrgBatchReceipt.Page>(rows);item=altered.get(0);
        altered.set(0,new NativeOrgBatchReceipt.Page(item.getClientId(),item.getPk(),item.getTarget(),"FAILED","UNKNOWN"));
        rejectReceipt(f,headers,altered,states);
        Map<String,String> wrongStates=new HashMap<String,String>(states);wrongStates.put(f.authorized.get(0).getClientObjectId(),"SUCCESS");
        rejectReceipt(f,headers,rows,wrongStates);
        rejectReceipt(f,headers.subList(1,headers.size()),rows,states);
        try { NativeOrgBatchReceipt.verify("1123456789abcdef0123456789abcdef","G","ORG",NativeOrgBatchPage.CUSTOMER_FINANCE,
                f.authorized,states,f.impact.getTargets(),headers,rows);throw new AssertionError("Receipt reused by another request"); }
        catch(BusinessException expected) { }
        try { NativeOrgBatchReceipt.verify("0123456789abcdef0123456789abcdef","G","OTHER",NativeOrgBatchPage.CUSTOMER_FINANCE,
                f.authorized,states,f.impact.getTargets(),headers,rows);throw new AssertionError("Receipt reused by another organization"); }
        catch(BusinessException expected) { }
        try { NativeOrgBatchReceipt.verify("0123456789abcdef0123456789abcdef","G","ORG",NativeOrgBatchPage.CUSTOMER_FINANCE,
                f.authorized,states,Arrays.asList("T"),headers,rows);throw new AssertionError("Target without pages was omitted"); }
        catch(BusinessException expected) { }
        try { NativeOrgBatchReceipt.verify("0123456789abcdef0123456789abcdef","G","ORG",NativeOrgBatchPage.CUSTOMER_FINANCE,
                f.authorized,states,Arrays.asList("T","NO_ROWS","EXTRA"),headers,rows);throw new AssertionError("Unselected target was added"); }
        catch(BusinessException expected) { }
    }
    private static void receiptRecords(Fixture f,NativeOrgBatchReconciliation.Result result,NativeOrgBatchReceipt.Evidence evidence)
            throws Exception {
        String event="2026-11-30 16:01:01";
        ReceiptFixture fixture=receiptFixture(f,evidence);Map<String,Object> request=fixture.request;
        List<Map<String,Object>> objects=fixture.objects;
        targetRecords(fixture);
        List<Map<String,Object>> records=NativeOrgBatchReceiptRecords.encode(request,objects,NativeOrgBatchPage.CUSTOMER_FINANCE,evidence,event);
        require(records.size()==8 && "202612".equals(records.get(0).get("archive_month")),
            "Receipt records lost target, root or page evidence, or used the wrong Beijing month");
        try { records.clear();throw new AssertionError("Receipt records are mutable"); } catch(UnsupportedOperationException expected) { }
        try { records.get(0).put("request_id","other");throw new AssertionError("Receipt record is mutable"); }
        catch(UnsupportedOperationException expected) { }
        request.put("business_state","PARTIAL");request.put("finished_at",event);
        for(int n=0;n<objects.size();n++) objects.get(n).put("unit_state",result.getRoots().get(n).getState());
        List<Map<String,Object>> overlap=new ArrayList<Map<String,Object>>(records);Collections.reverse(overlap);
        Map<String,Object> header=copyKind(records,"H");header.put("page_count",new java.math.BigDecimal(String.valueOf(header.get("page_count"))+".0"));
        overlap.add(header);
        NativeOrgBatchReceipt.Evidence restored=NativeOrgBatchReceiptRecords.decode(request,objects,NativeOrgBatchPage.CUSTOMER_FINANCE,overlap);
        require(restored.getTargets().equals(Arrays.asList("NO_ROWS","T")) && restored.getPages().size()==3
            && restored.getHeaders().get(0).getPageCount()==2,"Online and archived receipt copies did not reconcile");
        List<Map<String,Object>> missingTarget=new ArrayList<Map<String,Object>>(records);
        for(java.util.Iterator<Map<String,Object>> it=missingTarget.iterator();it.hasNext();) {
            Map<String,Object> row=it.next();if("T".equals(row.get("row_kind")) && "NO_ROWS".equals(row.get("item_key"))) it.remove();
        }
        rejectRecords(request,objects,missingTarget);
        List<Map<String,Object>> missingPage=new ArrayList<Map<String,Object>>(records);missingPage.remove(copyIndex(records,"P"));
        rejectRecords(request,objects,missingPage);
        List<Map<String,Object>> conflicting=new ArrayList<Map<String,Object>>(records);Map<String,Object> bad=copyKind(records,"H");
        bad.put("result_state","SUCCESS");conflicting.add(bad);rejectRecords(request,objects,conflicting);
        List<Map<String,Object>> duplicatePage=new ArrayList<Map<String,Object>>(records);bad=copyKind(records,"P");
        bad.put("record_id","ffffffffffffffffffffffffffffffff");duplicatePage.add(bad);rejectRecords(request,objects,duplicatePage);
        List<Map<String,Object>> wrongMonth=new ArrayList<Map<String,Object>>(records);int index=copyIndex(records,"H");bad=new HashMap<String,Object>(records.get(index));
        bad.put("archive_month","202611");wrongMonth.set(index,bad);rejectRecords(request,objects,wrongMonth);
        List<Map<String,Object>> badCount=new ArrayList<Map<String,Object>>(records);bad=new HashMap<String,Object>(records.get(index));
        bad.put("page_count",new java.math.BigDecimal("2.5"));badCount.set(index,bad);rejectRecords(request,objects,badCount);
        List<Map<String,Object>> wrongScope=new ArrayList<Map<String,Object>>(records);bad=new HashMap<String,Object>(records.get(index));
        bad.put("pk_group","OTHER");wrongScope.set(index,bad);rejectRecords(request,objects,wrongScope);
        List<Map<String,Object>> orphan=new ArrayList<Map<String,Object>>(records);bad=copyKind(records,"P");
        bad.put("record_id","eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");bad.put("object_id","dddddddddddddddddddddddddddddddd");
        orphan.add(bad);rejectRecords(request,objects,orphan);
        List<Map<String,Object>> mixedEvents=new ArrayList<Map<String,Object>>(records);bad=new HashMap<String,Object>(records.get(index));
        bad.put("event_time","2026-11-30 16:01:02");mixedEvents.set(index,bad);rejectRecords(request,objects,mixedEvents);
        objects.get(0).put("unit_state","SUCCESS");rejectRecords(request,objects,records);objects.get(0).put("unit_state","PARTIAL");
        request.put("business_state","SUCCESS");rejectRecords(request,objects,records);request.put("business_state","PARTIAL");
        request.put("finished_at","invalid");rejectRecords(request,objects,records);request.put("finished_at",event);
        request.put("business_state","RUNNING");request.put("finished_at",null);rejectRecords(request,objects,records);
        for(Map<String,Object> object:objects) object.put("unit_state","PENDING");
        objects.get(0).put("target_org","OTHER");
        try { NativeOrgBatchReceiptRecords.encode(request,objects,NativeOrgBatchPage.CUSTOMER_FINANCE,evidence,event);
            throw new AssertionError("Receipt encoded for an unauthorized target"); } catch(BusinessException expected) { }
    }
    private static void targetRecords(ReceiptFixture fixture) throws Exception {
        Map<String,Object> request=new HashMap<String,Object>(fixture.request);
        request.put("signature_state","PENDING");request.put("business_state","PENDING");request.put("execution_token",null);
        List<Map<String,Object>> rows=NativeOrgBatchTargetRecords.encode(request,fixture.objects,NativeOrgBatchPage.CUSTOMER_FINANCE,
            fixture.targetEvidence);
        require(rows.size()==3 && "202611".equals(rows.get(0).get("archive_month")),"Prepared target records lost their request month");
        NativeOrgBatchTargetScope.Evidence restored=NativeOrgBatchTargetRecords.decode(request,fixture.objects,
            NativeOrgBatchPage.CUSTOMER_FINANCE,rows);
        require(restored.getTargets().equals(Arrays.asList("NO_ROWS","T")),"Prepared target records lost an empty target");
        request.put("business_state","CANCELLED");request.put("signature_state","CANCELLED");request.put("finished_at","2026-12-01 00:00:00");
        require(NativeOrgBatchTargetRecords.decode(request,fixture.objects,NativeOrgBatchPage.CUSTOMER_FINANCE,rows).getTargets().size()==2,
            "Cancellation lost its prepared targets");
        List<Map<String,Object>> missing=new ArrayList<Map<String,Object>>(rows);missing.remove(1);rejectTargets(request,fixture.objects,missing);
        List<Map<String,Object>> duplicate=new ArrayList<Map<String,Object>>(rows);Map<String,Object> bad=new HashMap<String,Object>(rows.get(1));
        bad.put("record_id","ffffffffffffffffffffffffffffffff");duplicate.add(bad);rejectTargets(request,fixture.objects,duplicate);
        List<Map<String,Object>> wrong=new ArrayList<Map<String,Object>>(rows);bad=new HashMap<String,Object>(rows.get(0));
        bad.put("page_count",Integer.valueOf(1));wrong.set(0,bad);rejectTargets(request,fixture.objects,wrong);
        wrong=new ArrayList<Map<String,Object>>(rows);bad=new HashMap<String,Object>(rows.get(1));bad.put("item_key","OTHER");wrong.set(1,bad);
        rejectTargets(request,fixture.objects,wrong);
        wrong=new ArrayList<Map<String,Object>>(rows);bad=new HashMap<String,Object>(rows.get(0));bad.put("event_time","2026-11-30 15:59:58");wrong.set(0,bad);
        rejectTargets(request,fixture.objects,wrong);
        List<Map<String,Object>> subset=new ArrayList<Map<String,Object>>(fixture.objects);subset.remove(0);
        Map<String,Object> shortRequest=new HashMap<String,Object>(request);shortRequest.put("owner_object_count",Integer.valueOf(2));
        rejectTargets(shortRequest,subset,rows);
    }
    private static void rejectTargets(Map<String,Object> request,List<Map<String,Object>> objects,List<Map<String,Object>> rows) throws Exception {
        try { NativeOrgBatchTargetRecords.decode(request,objects,NativeOrgBatchPage.CUSTOMER_FINANCE,rows);
            throw new AssertionError("Incomplete prepared target records accepted"); } catch(BusinessException expected) { }
    }
    private static ReceiptFixture receiptFixture(Fixture f,NativeOrgBatchReceipt.Evidence evidence) {
        String requestId="0123456789abcdef0123456789abcdef";
        Map<String,Object> request=EsignValues.row("request_id",requestId,"pk_group","G","pk_org","ORG",
            "node_code","10140CUG","button_code","BatchUpdate","operation_type","UPDATE",
            "policy_mode",OperationContext.OWNER_POLICY_PROTOCOL,"signature_state","SUCCESS","business_state","RUNNING",
            "execution_token","abcdefabcdefabcdefabcdefabcdefab","finished_at",null,"created_at","2026-11-30 15:59:59",
            "archive_month","202611","owner_object_count",Integer.valueOf(f.authorized.size()));
        List<Map<String,Object>> objects=new ArrayList<Map<String,Object>>();int i=0;
        for(BusinessObjectRef ref:f.authorized) objects.add(EsignValues.row("object_id",String.format("%032x",Integer.valueOf(++i)),
            "request_id",requestId,"client_object_id",ref.getClientObjectId(),"object_type",ref.getObjectType(),
            "object_pk",ref.getObjectPk(),"bill_code",ref.getBillCode(),"pk_group","G","pk_org","ORG","target_org","ORG",
            "policy_owner_org","G","relation_type","ROOT","parent_object_id",null,"desired_owner_org",null,"unit_state","PENDING"));
        try {
            NativeOrgBatchTargetScope.Evidence target=NativeOrgBatchTargetScope.captureForTest(requestId,"G","ORG",
                NativeOrgBatchPage.CUSTOMER_FINANCE,f.authorized,f.impact.getTargets());
            NativeOrgBatchPreparedScope.Evidence prepared=NativeOrgBatchPreparedScope.captureForTest(requestId,"G","ORG",target,
                f.authorized,f.impact);
            return new ReceiptFixture(request,objects,evidence,target,prepared);
        }
        catch(BusinessException failure) { throw new IllegalStateException(failure); }
    }
    private static int copyIndex(List<Map<String,Object>> rows,String kind) {
        for(int i=0;i<rows.size();i++) if(kind.equals(rows.get(i).get("row_kind"))) return i;
        throw new AssertionError("Missing receipt record kind "+kind);
    }
    private static Map<String,Object> copyKind(List<Map<String,Object>> rows,String kind) {
        return new HashMap<String,Object>(rows.get(copyIndex(rows,kind)));
    }
    private static void rejectRecords(Map<String,Object> request,List<Map<String,Object>> objects,List<Map<String,Object>> rows) throws Exception {
        try { NativeOrgBatchReceiptRecords.decode(request,objects,NativeOrgBatchPage.CUSTOMER_FINANCE,rows);
            throw new AssertionError("Invalid persisted receipt records accepted"); } catch(BusinessException expected) { }
    }
    private static Map<String,String> receiptStates(NativeOrgBatchReconciliation.Result result) {
        Map<String,String> values=new HashMap<String,String>();
        for(NativeOrgBatchOutcome.Root root:result.getRoots()) values.put(root.getClientId(),root.getState());return values;
    }
    private static void rejectReceipt(Fixture f,List<NativeOrgBatchReceipt.Header> headers,List<NativeOrgBatchReceipt.Page> rows,
            Map<String,String> states) throws Exception {
        try { NativeOrgBatchReceipt.verify("0123456789abcdef0123456789abcdef","G","ORG",NativeOrgBatchPage.CUSTOMER_FINANCE,
                f.authorized,states,f.impact.getTargets(),headers,rows);throw new AssertionError("Incomplete organization-page receipt accepted"); }
        catch(BusinessException expected) { }
    }
    private static void reject(Fixture f,List<? extends SuperVO> before,List<? extends SuperVO> after,
            NativeOrgBatchSnapshots.Capture evidence,ErrLogReturnValue result,Map<String,String> excluded) throws Exception {
        try { f.reconcile(before,after,evidence,result,excluded);throw new AssertionError("Unverifiable page outcome accepted"); }
        catch(BusinessException expected) { }
    }
    private static TestFinanceVO finance(String pk,String root,String payterm) {
        TestFinanceVO vo=new TestFinanceVO();vo.setPk_custfinance(pk);vo.setPk_customer(root);vo.setPk_org("T");vo.setPk_group("G");
        vo.setPk_payterm(payterm);return vo;
    }
    /** Native CustFinanceVO primary-key metadata requires a running NC server; values remain native accessors. */
    private static final class TestFinanceVO extends CustFinanceVO {
        private static final long serialVersionUID=1L;
        @Override public String getPrimaryKey() { return getPk_custfinance(); }
        @Override public Object getAttributeValue(String name) {
            if("pk_customer".equals(name)) return getPk_customer();
            if("pk_org".equals(name)) return getPk_org();
            if("pk_group".equals(name)) return getPk_group();
            if("pk_payterm".equals(name)) return getPk_payterm();
            throw new AssertionError("Unexpected test field "+name);
        }
    }
    private static final class Fixture {
        final Data data=new Data();final NativeOrgBatchSelection.Impact impact;
        final NativeBatchChangeSet changes;final List<BusinessObjectRef> authorized;
        final NativeOrgBatchSnapshots.Capture before,after;final Map<String,String> excluded=new HashMap<String,String>();
        Fixture(final boolean empty) throws Exception {
            final List<BusinessObjectRef> roots=Arrays.asList(ref("a","A"),ref("b","B"),ref("c","C"));
            impact=NativeOrgBatchSelection.capture("G","BD_CUSTOMER",NativeOrgBatchPage.CUSTOMER_FINANCE,roots,new String[]{"T","NO_ROWS"},
                new NativeOrgBatchSelection.Reader() { public List<NativeOrgBatchSelection.Row> read(NativeOrgBatchPage page,
                        String[] ids,String[] targets,boolean lock) {
                    return empty?Collections.<NativeOrgBatchSelection.Row>emptyList():Arrays.asList(selected("P1","A"),selected("P2","A"),selected("P3","B"));
                } },true);
            Map<String,Object> values=new HashMap<String,Object>();values.put("pk_payterm","new");
            changes=NativeBatchChangeSet.create(new String[]{"pk_payterm"},values,new java.util.HashSet<String>(Arrays.asList("pk_payterm")));
            before=NativeOrgBatchSnapshots.capture(impact,data.bean(),data,true);
            authorized=NativeBatchRequestScope.bindOrganization("G","ORG","BD_CUSTOMER",roots,changes,impact,before);
            if(!empty) { data.replace(page("P1","A","T","G","new"));excluded.put("P2","VALIDATION"); }
            after=NativeOrgBatchSnapshots.recapture(before,data.bean(),data,false);
        }
        List<SuperVO> beforeVOs() { return Arrays.<SuperVO>asList(finance("P1","A","old"),finance("P2","A","old"),finance("P3","B","new")); }
        List<SuperVO> afterVOs() { return Arrays.<SuperVO>asList(finance("P1","A","new"),finance("P2","A","old"),finance("P3","B","new")); }
        ErrLogReturnValue returned() { return new ErrLogReturnValue(new ErrLogResult(null,1,null),new SuperVO[]{finance("P1","A","new")},3); }
        NativeOrgBatchReconciliation.Result reconcile(List<? extends SuperVO> prior,List<? extends SuperVO> current,
                NativeOrgBatchSnapshots.Capture after,ErrLogReturnValue returned,Map<String,String> excluded) throws BusinessException {
            return NativeOrgBatchReconciliation.reconcileForTest("0123456789abcdef0123456789abcdef","G","ORG","BD_CUSTOMER",authorized,impact,changes,before,after,
                prior,current,returned,excluded,TestFinanceVO.class);
        }
    }
    static NativeOrgBatchSnapshots.Capture captureForImpact(NativeOrgBatchSelection.Impact impact,boolean lock) throws BusinessException {
        return captureForImpact(impact,lock,true);
    }
    static NativeOrgBatchSnapshots.Capture captureForImpact(NativeOrgBatchSelection.Impact impact,boolean lock,boolean extra) throws BusinessException {
        Data data=new Data();data.extra=extra;data.rows.clear();
        for(NativeOrgBatchSelection.Row row:impact.getRows())
            data.rows.add(executionPage(row,false));
        return NativeOrgBatchSnapshots.capture(impact,data.bean(),data,lock);
    }
    public static NativeOrgBatchSnapshots.Capture recaptureUnchanged(PartnerOrgBatchScope.Prepared scope) throws BusinessException {
        Data data=new Data();data.rows.clear();
        for(NativeOrgBatchSelection.Row row:scope.getImpact().getRows())
            data.rows.add(executionPage(row,false));
        return NativeOrgBatchSnapshots.recapture(scope.getEvidence(),data.bean(),data,false);
    }
    public static List<SuperVO> executionVOs(PartnerOrgBatchScope.Prepared scope,boolean after) throws Exception {
        List<SuperVO> result=new ArrayList<SuperVO>();
        for(NativeOrgBatchSelection.Row row:scope.getImpact().getRows()) {
            CustFinanceVO vo=new CustFinanceVO();vo.setPk_custfinance(row.getId());vo.setPk_customer(row.getRoot());
            vo.setPk_org(row.getTarget());vo.setPk_group(row.getGroup());
            java.lang.reflect.Field metadata=SuperVO.class.getDeclaredField("vometa");metadata.setAccessible(true);metadata.set(vo,executionMetadata());
            java.lang.reflect.Field index=SuperVO.class.getDeclaredField("valueIndex");index.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String,Object> values=(Map<String,Object>)index.get(vo);
            boolean changed=after && "P1".equals(row.getId());
            values.put("name",changed?"n":"old");values.put("shortname",changed?"s":"old");values.put("taxcode",changed?"t":"old");
            result.add(vo);
        }
        return result;
    }
    public static NativeOrgBatchSnapshots.Capture recaptureExecutionChange(PartnerOrgBatchScope.Prepared scope) throws BusinessException {
        Data data=new Data();data.rows.clear();
        for(NativeOrgBatchSelection.Row row:scope.getImpact().getRows())data.rows.add(executionPage(row,"P1".equals(row.getId())));
        return NativeOrgBatchSnapshots.recapture(scope.getEvidence(),data.bean(),data,false);
    }
    private static SnapshotRow executionPage(NativeOrgBatchSelection.Row row,boolean changed) {
        return row("bd_custfinance",row.getId(),"pk_custfinance",row.getId(),"pk_customer",row.getRoot(),"pk_org",row.getTarget(),
            "pk_group",row.getGroup(),"value","old","pk_payterm","old","name",changed?"n":"old",
            "shortname",changed?"s":"old","taxcode",changed?"t":"old");
    }
    /** A synthetic local VO metadata proxy allows exact NC VO classes to be exercised without a running NC server. */
    private static nc.vo.pub.IVOMeta executionMetadata() {
        return proxy(nc.vo.pub.IVOMeta.class,new InvocationHandler() {
            public Object invoke(Object ignored,Method method,Object[] args) {
                if("getPrimaryAttribute".equals(method.getName()))return executionAttribute("pk_custfinance",true);
                if("getAttribute".equals(method.getName())) {
                    String name=(String)args[0];boolean fixed="pk_custfinance".equals(name) || "pk_customer".equals(name)
                        || "pk_org".equals(name) || "pk_group".equals(name);
                    return executionAttribute(name,fixed);
                }
                return empty(method);
            }
        });
    }
    private static nc.vo.pub.IAttributeMeta executionAttribute(final String name,final boolean fixed) {
        return proxy(nc.vo.pub.IAttributeMeta.class,new InvocationHandler() {
            public Object invoke(Object ignored,Method method,Object[] args) {
                if("getName".equals(method.getName()))return name;
                if("isStatic".equals(method.getName()))return Boolean.valueOf(fixed);
                return empty(method);
            }
        });
    }
    static NativeDeleteMetadataGraph.Capture metadataForTest(boolean extra) throws BusinessException {
        Data data=new Data();data.extra=extra;
        return NativeOrgBatchSnapshots.metadata(NativeOrgBatchPage.CUSTOMER_FINANCE,data.bean());
    }
    private static void rejectRecapture(NativeOrgBatchSnapshots.Capture before,Data data,String message) throws Exception {
        try { NativeOrgBatchSnapshots.recapture(before,data.bean(),data,false);throw new AssertionError(message); }
        catch(BusinessException expected) { }
    }
    private static BusinessObjectRef ref(String id,String pk) { return new BusinessObjectRef(id,"BD_CUSTOMER",pk,pk,null); }
    private static NativeOrgBatchSelection.Row selected(String id,String root) { return new NativeOrgBatchSelection.Row(id,root,"T","G"); }
    private static SnapshotRow page(String id,String root,String org,String group,String value) {
        return row("bd_custfinance",id,"pk_custfinance",id,"pk_customer",root,"pk_org",org,"pk_group",group,
            "value",value,"pk_payterm",value);
    }
    private static SnapshotRow extension(String id,String value) { return row("bd_custfinance_ext",id,"pk_custfinance",id,"value",value); }
    private static SnapshotRow row(String entity,String id,String... fields) {
        Map<String,String> values=new HashMap<String,String>();for(int i=0;i<fields.length;i+=2) values.put(fields[i],fields[i+1]);
        return new SnapshotRow(entity,id,null,values,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static SnapshotRow find(List<SnapshotRow> rows,String entity) {
        for(SnapshotRow row:rows) if(entity.equals(row.getEntity())) return row;throw new AssertionError("Missing "+entity);
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }

    private static final class Data implements NativeDeleteMetadataGraph.Reader {
        final List<SnapshotRow> rows=new ArrayList<SnapshotRow>();int locked;boolean extra=true,wrongBean;
        final ITable main=table("bd_custfinance"),extension=table("bd_custfinance_ext");
        Data() { rows.addAll(Arrays.asList(page("P1","A","T","G","old"),page("P2","A","T","G","old"),
            page("P3","B","T","G","old"),extension("P1","old"),extension("P3","old"))); }
        void replace(SnapshotRow replacement) {
            for(int i=0;i<rows.size();i++) if(rows.get(i).getEntity().equals(replacement.getEntity())
                    && rows.get(i).getRowId().equals(replacement.getRowId())) { rows.set(i,replacement);return; }
            throw new AssertionError("Cannot replace missing row");
        }
        public List<SnapshotRow> read(ITable table,String match,String value,boolean lock) {
            if(lock) locked++;List<SnapshotRow> result=new ArrayList<SnapshotRow>();
            for(SnapshotRow row:rows) if(table.getName().equals(row.getEntity()) && value.equals(row.getValues().get(match))) result.add(row);
            return result;
        }
        IBean bean() { return proxy(IBean.class,new InvocationHandler() {
            public Object invoke(Object ignored,Method method,Object[] args) {
                String name=method.getName();
                if("getID".equals(name)) return wrongBean?"OTHER":NativeOrgBatchPage.CUSTOMER_FINANCE.metadataId;
                if("getTable".equals(name)) return main;
                if("getExtendTableList".equals(name)) return extra?Collections.singletonList(extension):Collections.emptyList();
                if("getAssociations".equals(name) || "getORMAPTableList".equals(name)) return Collections.emptyList();
                return empty(method);
            }
        }); }
    }
    private static ITable table(final String name) { return proxy(ITable.class,new InvocationHandler() {
        public Object invoke(Object ignored,Method method,Object[] args) {
            String operation=method.getName();if("getName".equals(operation)) return name;
            if("getPrimaryKeyName".equals(operation)) return "pk_custfinance";
            if("getForeignKeies".equals(operation)) return Collections.emptyList();
            if("getColumnByName".equals(operation) && "pk_custfinance".equalsIgnoreCase((String)args[0])) return column();
            return empty(method);
        }
    }); }
    private static IColumn column() { return proxy(IColumn.class,new InvocationHandler() {
        public Object invoke(Object ignored,Method method,Object[] args) { return "getName".equals(method.getName())?"pk_custfinance":empty(method); }
    }); }
    private static Object empty(Method method) {
        if(method.getReturnType()==boolean.class) return Boolean.FALSE;if(method.getReturnType()==int.class) return Integer.valueOf(0);return null;
    }
    private static <T> T proxy(Class<T> type,InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));
    }
}
