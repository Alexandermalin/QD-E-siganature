package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchSnapshotTests;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchStatus;
import nc.vo.uapbd.qd.esign.ArchiveOrgBatchUpdateResult;

/** Validates result reconstruction without database, NC authorization, or archival I/O. */
public final class ArchiveBatchStatusTests {
    private ArchiveBatchStatusTests() { }
    public static void run() throws Exception {
        Map<String,Object> request=EsignValues.row("pk_group","G","pk_org","B","owner_object_count",Integer.valueOf(2),
            "node_code","10140CUG","button_code","Freeze","operation_type","FREEZE");
        Map<String,Object> first=row("1","a","A","SUCCESS"),second=row("2","b","B","FAILED");
        ArchiveBatchStatus partial=ArchiveBatchResultServiceImpl.buildStatus("R","PARTIAL",request,Arrays.asList(first,second));
        require("PARTIAL".equals(partial.getState()) && "OWNER".equals(partial.getUnits().get(0).getOwnerOrg()),"Partial result lost status or owner");
        reject("SUCCESS",request,first,second);
        second.put("pk_org","OTHER");reject("PARTIAL",request,first,second);second.put("pk_org","B");
        second.put("client_object_id","a");reject("PARTIAL",request,first,second);second.put("client_object_id","b");
        first.put("unit_state","UNKNOWN");second.put("unit_state","UNKNOWN");
        ArchiveBatchResultServiceImpl.buildStatus("R","UNKNOWN",request,Arrays.asList(first,second));
        first.put("unit_state","PENDING");second.put("unit_state","PENDING");
        ArchiveBatchResultServiceImpl.buildStatus("R","FAILED",request,Arrays.asList(first,second));
        request.put("node_code","10140SUG");request.put("button_code","Freeze");request.put("operation_type","FREEZE");
        first.put("object_type","BD_SUPPLIER");second.put("object_type","BD_SUPPLIER");second.put("object_pk","A");
        first.put("target_org","T1");second.put("target_org","T2");first.put("unit_state","SUCCESS");second.put("unit_state","UNCHANGED");
        ArchiveBatchStatus supplier=ArchiveBatchResultServiceImpl.buildStatus("R","SUCCESS",request,Arrays.asList(first,second));
        require("T2".equals(supplier.getUnits().get(1).getTargetOrg()),"Supplier target was lost");
        second.put("target_org","T1");reject("SUCCESS",request,first,second);
        second.put("target_org","T2");second.put("object_pk","OTHER");reject("SUCCESS",request,first,second);
        request.put("button_code","Enable");request.put("operation_type","ENABLE");
        first.put("target_org","B");second.put("target_org","B");first.put("object_pk","A");second.put("object_pk","B");
        ArchiveBatchResultServiceImpl.buildStatus("R","SUCCESS",request,Arrays.asList(first,second));
        second.put("object_pk","A");reject("SUCCESS",request,first,second);
        request.put("node_code","10140MAG");first.put("object_type","BD_MATERIAL");second.put("object_type","BD_MATERIAL");second.put("object_pk","B");
        ArchiveBatchResultServiceImpl.buildStatus("R","SUCCESS",request,Arrays.asList(first,second));
        second.put("object_type","BD_SUPPLIER");reject("SUCCESS",request,first,second);
        request.put("node_code","10140WARH");first.put("object_type","BD_STORDOC");second.put("object_type","BD_STORDOC");
        ArchiveBatchResultServiceImpl.buildStatus("R","SUCCESS",request,Arrays.asList(first,second));
        quickBatchDomains();
        organizationBatch();
        committedReply();
        System.out.println("PASS ArchiveBatchStatusTests (archive types, partial, uncertain and pre-execution outcomes; no NC database)");
    }
    private static void committedReply() throws Exception {
        Map<String,Object> request=EsignValues.row("request_id","R","pk_group","G","pk_org","B","owner_object_count",Integer.valueOf(2),
            "node_code","10140CUG","button_code","Enable","operation_type","ENABLE","signature_state","SUCCESS",
            "business_state","SUCCESS","execution_token","T","finished_at","2026-11-12 00:00:00");
        Map<String,Object> first=row("1","a","A","SUCCESS"),second=row("2","b","B","UNCHANGED");
        java.util.List<Map<String,Object>> objects=Arrays.asList(first,second);
        java.util.List<BusinessObjectRef> refs=nc.bs.uapbd.qd.esign.core.ObjectRelations.restore(objects);
        ArchiveBatchResult result=new ArchiveBatchResult(null,Arrays.asList(new ArchiveBatchResult.Unit("a","A","SUCCESS"),
            new ArchiveBatchResult.Unit("b","B","UNCHANGED")));
        OperationResponse reply=new OperationResponse("R",false,result,refs);
        ArchiveBatchStatus status=ArchiveBatchResultServiceImpl.buildStatus("R","SUCCESS",request,objects);
        OperationCommitVerification.verify("R","T",reply,request,objects,status);
        request.put("finished_at",null);
        try { OperationCommitVerification.verify("R","T",reply,request,objects,status);
            throw new AssertionError("A swallowed commit failure was reported as success"); } catch(BusinessException expected) { }
        request.put("finished_at","2026-11-12 00:00:00");
        try { OperationCommitVerification.verify("R","OTHER",reply,request,objects,status);
            throw new AssertionError("Another execution token reused a durable result"); } catch(BusinessException expected) { }
        OperationResponse wrong=new OperationResponse("R",false,new ArchiveBatchResult(null,Arrays.asList(
            new ArchiveBatchResult.Unit("a","A","FAILED"),new ArchiveBatchResult.Unit("b","B","UNCHANGED"))),refs);
        try { OperationCommitVerification.verify("R","T",wrong,request,objects,status);
            throw new AssertionError("The native reply disagreed with the durable unit states"); } catch(BusinessException expected) { }
        wrong=new OperationResponse("R",false,result,Arrays.asList(refs.get(0)));
        try { OperationCommitVerification.verify("R","T",wrong,request,objects,status);
            throw new AssertionError("An incomplete returned object set was accepted"); } catch(BusinessException expected) { }
        OperationResponse replay=new OperationResponse("R",true,null,refs);
        OperationCommitVerification.verify("R",null,replay,request,objects,status);
        try { OperationCommitVerification.verify("R",null,new OperationResponse("R",true,result,refs),request,objects,status);
            throw new AssertionError("A replay fabricated a native business result"); } catch(BusinessException expected) { }
        request.put("node_code","10140CBA");request.put("button_code","Edit");request.put("operation_type","UPDATE");
        second.put("parent_object_id","1");refs=nc.bs.uapbd.qd.esign.core.ObjectRelations.restore(objects);
        reply=new OperationResponse("R",false,null,refs);
        OperationCommitVerification.verify("R","T",reply,request,objects,null);
        wrong=new OperationResponse("R",false,null,Arrays.asList(refs.get(0),new BusinessObjectRef("b","BD_CUSTOMER","B",null,null)));
        try { OperationCommitVerification.verify("R","T",wrong,request,objects,null);
            throw new AssertionError("A returned child lost its persisted parent"); } catch(BusinessException expected) { }
    }
    private static void organizationBatch() throws Exception {
        NativeOrgBatchSnapshotTests.ReceiptFixture fixture=NativeOrgBatchSnapshotTests.receiptFixture();
        final int[] checks={0};
        ArchiveBatchResultServiceImpl.OrganizationAccess access=new ArchiveBatchResultServiceImpl.OrganizationAccess() {
            public void require(java.util.List<BusinessObjectRef> refs,NativeOrgBatchPage page,java.util.List<String> targets) {
                ArchiveBatchStatusTests.require(refs.size()==3 && page==NativeOrgBatchPage.CUSTOMER_FINANCE
                    && targets.equals(Arrays.asList("NO_ROWS","T")),"Organization permission check missed the page, archive or target");checks[0]++;
            }
        };
        String id="0123456789abcdef0123456789abcdef";
        fixture.request.put("business_state","PARTIAL");fixture.request.put("finished_at","2026-11-30 16:01:01");
        fixture.objects.get(0).put("unit_state","PARTIAL");fixture.objects.get(1).put("unit_state","UNCHANGED");
        fixture.objects.get(2).put("unit_state","UNCHANGED");
        ArchiveBatchStatus status=ArchiveBatchResultServiceImpl.buildOrganizationStatus(id,"PARTIAL",fixture.request,fixture.objects,
            fixture.targetEvidence,fixture.evidence,access);
        require(checks[0]==1 && status.getOrganization()!=null && status.getOrganization().isResultAvailable()
            && status.getOrganization().getPages().size()==3 && "PARTIAL".equals(status.getUnits().get(0).getState()),
            "Partial archive or page details were lost");
        ArchiveBatchStatus detailed=ArchiveBatchResultServiceImpl.buildOrganizationStatus(id,"PARTIAL",fixture.request,fixture.objects,
            fixture.targetEvidence,fixture.preparedEvidence,fixture.evidence,access);
        require(detailed.getOrganization().hasPreparedSelection() && detailed.getOrganization().getPreparedPages().size()==3
            && "P1".equals(detailed.getOrganization().getPreparedPages().get(0).getId()),
            "Prepared page identities were not returned with the independently checked result");
        java.util.List<BusinessObjectRef> refs=nc.bs.uapbd.qd.esign.core.ObjectRelations.restore(fixture.objects);
        verifyCommittedOrganization(fixture,detailed,refs);
        OperationContext context=new OperationContext(id,id,"G","ORG","10140CUG","客户","BatchUpdate","批改","UPDATE",null,
            refs,OperationContext.OWNER_POLICY_PROTOCOL);
        ArchiveBatchResult.Unit partialUnit=new ArchiveBatchResult.Unit(refs.get(0).getClientObjectId(),refs.get(0).getObjectPk(),"PARTIAL");
        require(OperationTransactionServiceImpl.requiresUnitAudit(context,partialUnit),"Partially changed archive was excluded from audit");
        require(!OperationTransactionServiceImpl.requiresUnitAudit(context,new ArchiveBatchResult.Unit("other","A","PARTIAL"))
            && !OperationTransactionServiceImpl.requiresUnitAudit(context,new ArchiveBatchResult.Unit(refs.get(0).getClientObjectId(),"A","UNCHANGED")),
            "Unverified or unchanged archive was selected for partial audit");
        try { ArchiveBatchResultServiceImpl.buildOrganizationStatus(id,"PARTIAL",fixture.request,fixture.objects,
                fixture.targetEvidence,null,access);throw new AssertionError("A partial result without page evidence was accepted"); }
        catch(BusinessException expected) { }
        try { ArchiveBatchResultServiceImpl.buildOrganizationStatus(id,"PARTIAL",fixture.request,fixture.objects,
                fixture.targetEvidence,fixture.evidence,new ArchiveBatchResultServiceImpl.OrganizationAccess() {
                    public void require(java.util.List<BusinessObjectRef> refs,NativeOrgBatchPage page,java.util.List<String> targets)
                            throws BusinessException { throw new BusinessException("target permission revoked"); }
                });throw new AssertionError("Result was returned without target permission"); }
        catch(BusinessException expected) { }
        fixture.objects.get(0).put("unit_state","SUCCESS");
        try { ArchiveBatchResultServiceImpl.buildOrganizationStatus(id,"PARTIAL",fixture.request,fixture.objects,
                fixture.targetEvidence,fixture.evidence,access);throw new AssertionError("Changed root state accepted"); }
        catch(BusinessException expected) { }
        fixture.request.put("business_state","FAILED");fixture.request.put("error_code","BUSINESS_EXECUTION_FAILED");
        for(Map<String,Object> row:fixture.objects) { row.put("unit_state","FAILED");row.put("unit_error_code","BATCH_ROLLED_BACK"); }
        status=ArchiveBatchResultServiceImpl.buildOrganizationStatus(id,"FAILED",fixture.request,fixture.objects,fixture.targetEvidence,null,access);
        require(!status.getOrganization().isResultAvailable() && status.getOrganization().getPages().isEmpty(),
            "A rolled-back batch invented a page result");
        fixture.objects.get(0).put("unit_error_code","NATIVE_REJECTED");
        try { ArchiveBatchResultServiceImpl.buildOrganizationStatus(id,"FAILED",fixture.request,fixture.objects,
                fixture.targetEvidence,null,access);throw new AssertionError("An unproven native failure was accepted"); }
        catch(BusinessException expected) { }
        fixture.request.put("business_state","PENDING");fixture.request.put("finished_at",null);
        for(Map<String,Object> row:fixture.objects) { row.put("unit_state","PENDING");row.put("unit_error_code",null); }
        status=ArchiveBatchResultServiceImpl.buildOrganizationStatus(id,"PENDING",fixture.request,fixture.objects,fixture.targetEvidence,null,access);
        require(!status.getOrganization().isResultAvailable() && status.getOrganization().getTargets().size()==2,
            "A pending request lost its target scope");
    }
    private static void verifyCommittedOrganization(NativeOrgBatchSnapshotTests.ReceiptFixture fixture,ArchiveBatchStatus status,
            java.util.List<BusinessObjectRef> refs) throws Exception {
        String id="0123456789abcdef0123456789abcdef",token="abcdefabcdefabcdefabcdefabcdefab";
        java.util.List<ArchiveBatchResult.Unit> units=new java.util.ArrayList<ArchiveBatchResult.Unit>();
        for(ArchiveBatchStatus.Unit row:status.getUnits())units.add(new ArchiveBatchResult.Unit(row.getClientId(),row.getObjectPk(),row.getState()));
        nc.vo.bd.errorlog.ErrLogReturnValue nativeResult=NativeOrgBatchSnapshotTests.nativeReplyFixture();
        OperationResponse reply=new OperationResponse(id,false,new ArchiveOrgBatchUpdateResult(nativeResult,units,3),refs);
        OperationCommitVerification.verify(id,token,reply,fixture.request,fixture.objects,status,NativeOrgBatchSnapshotTests.nativeReplyTypeFixture());
        OperationResponse wrong=new OperationResponse(id,false,new ArchiveOrgBatchUpdateResult(
            new nc.vo.bd.errorlog.ErrLogReturnValue(new nc.vo.bd.errorlog.ErrLogResult(null,0,null),new nc.vo.pub.SuperVO[0],2),units,2),refs);
        try { OperationCommitVerification.verify(id,token,wrong,fixture.request,fixture.objects,status,NativeOrgBatchSnapshotTests.nativeReplyTypeFixture());
            throw new AssertionError("An organization reply omitted an authorized page"); }
        catch(BusinessException expected) { }
        wrong=new OperationResponse(id,false,new nc.vo.uapbd.qd.esign.ArchiveBatchUpdateResult(nativeResult,units),refs);
        try { OperationCommitVerification.verify(id,token,wrong,fixture.request,fixture.objects,status,NativeOrgBatchSnapshotTests.nativeReplyTypeFixture());
            throw new AssertionError("A main-tab reply was accepted for an organization batch"); }
        catch(BusinessException expected) { }
        ArchiveBatchStatus.Organization old=status.getOrganization();
        ArchiveBatchStatus noPrepared=new ArchiveBatchStatus(id,status.getState(),status.getUnits(),new ArchiveBatchStatus.Organization(
            old.getTabCode(),old.getTargets(),true,old.getHeaders(),old.getPages()));
        try { OperationCommitVerification.verify(id,token,reply,fixture.request,fixture.objects,noPrepared,NativeOrgBatchSnapshotTests.nativeReplyTypeFixture());
            throw new AssertionError("A committed organization reply without prepared pages was accepted"); }
        catch(BusinessException expected) { }
        nc.vo.pub.SuperVO nativeRow=((nc.vo.pub.SuperVO[])nativeResult.getReturnValue())[0];
        nc.vo.bd.errorlog.ErrLogReturnValue duplicate=new nc.vo.bd.errorlog.ErrLogReturnValue(
            new nc.vo.bd.errorlog.ErrLogResult(null,1,null),new nc.vo.pub.SuperVO[]{nativeRow,nativeRow},3);
        wrong=new OperationResponse(id,false,new ArchiveOrgBatchUpdateResult(duplicate,units,3),refs);
        try { OperationCommitVerification.verify(id,token,wrong,fixture.request,fixture.objects,status,NativeOrgBatchSnapshotTests.nativeReplyTypeFixture());
            throw new AssertionError("A duplicated native page was accepted after commit"); }
        catch(BusinessException expected) { }
        nc.vo.bd.errorlog.ErrLogReturnValue missing=new nc.vo.bd.errorlog.ErrLogReturnValue(
            new nc.vo.bd.errorlog.ErrLogResult(null,1,null),new nc.vo.pub.SuperVO[0],3);
        wrong=new OperationResponse(id,false,new ArchiveOrgBatchUpdateResult(missing,units,3),refs);
        try { OperationCommitVerification.verify(id,token,wrong,fixture.request,fixture.objects,status,NativeOrgBatchSnapshotTests.nativeReplyTypeFixture());
            throw new AssertionError("An omitted native success was accepted after commit"); }
        catch(BusinessException expected) { }
        NativeOrgBatchSnapshotTests.ReceiptFixture empty=NativeOrgBatchSnapshotTests.emptyReceiptFixture();
        empty.request.put("business_state","SUCCESS");empty.request.put("finished_at","2026-11-30 16:01:01");
        for(Map<String,Object> row:empty.objects)row.put("unit_state","UNCHANGED");
        ArchiveBatchStatus emptyStatus=ArchiveBatchResultServiceImpl.buildOrganizationStatus(id,"SUCCESS",empty.request,empty.objects,
            empty.targetEvidence,empty.preparedEvidence,empty.evidence,new ArchiveBatchResultServiceImpl.OrganizationAccess() {
                public void require(java.util.List<BusinessObjectRef> objects,NativeOrgBatchPage page,java.util.List<String> targets) { }
            });
        java.util.List<BusinessObjectRef> emptyRefs=nc.bs.uapbd.qd.esign.core.ObjectRelations.restore(empty.objects);
        java.util.List<ArchiveBatchResult.Unit> unchanged=new java.util.ArrayList<ArchiveBatchResult.Unit>();
        for(ArchiveBatchStatus.Unit row:emptyStatus.getUnits())unchanged.add(new ArchiveBatchResult.Unit(row.getClientId(),row.getObjectPk(),row.getState()));
        OperationCommitVerification.verify(id,token,new OperationResponse(id,false,new ArchiveOrgBatchUpdateResult(null,unchanged,0),emptyRefs),
            empty.request,empty.objects,emptyStatus);
    }
    private static void quickBatchDomains() throws Exception {
        String[][] routes={{"10140CUG","BD_CUSTOMER"},{"10140CUO","BD_CUSTOMER"},
            {"10140SUG","BD_SUPPLIER"},{"10140SUO","BD_SUPPLIER"},{"10140MAG","BD_MATERIAL"},{"10140MAO","BD_MATERIAL"}};
        for(String[] route:routes) {
            Map<String,Object> request=EsignValues.row("pk_group","G","pk_org","B","owner_object_count",Integer.valueOf(2),
                "node_code",route[0],"button_code","BatchUpdate","operation_type","UPDATE");
            Map<String,Object> first=row("1","a","A","SUCCESS"),second=row("2","b","B","FAILED");
            first.put("object_type",route[1]);second.put("object_type",route[1]);
            ArchiveBatchStatus partial=ArchiveBatchResultServiceImpl.buildStatus("R","PARTIAL",request,Arrays.asList(first,second));
            require("PARTIAL".equals(partial.getState()) && "FAILED".equals(partial.getUnits().get(1).getState()),
                "Quick-batch partial results were not reconstructed for "+route[0]);
            reject("SUCCESS",request,first,second);
            second.put("object_type",route[1].equals("BD_CUSTOMER")?"BD_SUPPLIER":"BD_CUSTOMER");
            reject("PARTIAL",request,first,second);second.put("object_type",route[1]);
            second.put("target_org","OTHER");reject("PARTIAL",request,first,second);second.put("target_org","B");
            second.put("object_pk","A");reject("PARTIAL",request,first,second);second.put("object_pk","B");
            first.put("unit_state","UNKNOWN");second.put("unit_state","UNKNOWN");
            require("UNKNOWN".equals(ArchiveBatchResultServiceImpl.buildStatus("R","UNKNOWN",request,Arrays.asList(first,second)).getState()),
                "An uncertain batch was reported as a confirmed result");
            first.put("unit_state","PENDING");second.put("unit_state","PENDING");
            ArchiveBatchResultServiceImpl.buildStatus("R","RUNNING",request,Arrays.asList(first,second));
        }
        Map<String,Object> invalid=EsignValues.row("pk_group","G","pk_org","B","owner_object_count",Integer.valueOf(2),
            "node_code","UNKNOWN","button_code","BatchUpdate","operation_type","UPDATE");
        reject("SUCCESS",invalid,row("1","a","A","SUCCESS"),row("2","b","B","SUCCESS"));
    }
    private static Map<String,Object> row(String id,String client,String pk,String state) {
        return EsignValues.row("object_id",id,"request_id","R","client_object_id",client,"object_type","BD_CUSTOMER",
            "object_pk",pk,"pk_group","G","pk_org","B","policy_owner_org","OWNER","target_org","B","unit_state",state);
    }
    private static void reject(String state,Map<String,Object> request,Map<String,Object> a,Map<String,Object> b) throws Exception {
        try { ArchiveBatchResultServiceImpl.buildStatus("R",state,request,Arrays.asList(a,b));throw new AssertionError("Invalid batch status accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
