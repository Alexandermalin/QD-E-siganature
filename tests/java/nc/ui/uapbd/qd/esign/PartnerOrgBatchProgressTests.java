package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.bd.errorlog.ErrorLogVO;
import nc.vo.bd.errorlog.ErrorMsgVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.ArchiveBatchStatus;
import nc.vo.uapbd.qd.esign.ArchiveOrgBatchUpdateResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** Independent slice results and uncertain replies, without an NC session. */
public final class PartnerOrgBatchProgressTests {
    private static final String HASH="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private PartnerOrgBatchProgressTests() { }
    public static void run(final QdPartnerOrgBatchSlices.Prepared prepared) throws Exception {
        final int[] calls={0};
        List<QdPartnerOrgBatchProgress.Completed> completed=QdPartnerOrgBatchProgress.execute(prepared,
            new QdPartnerOrgBatchProgress.Operation() {
                public void execute(QdPartnerOrgBatchSlices.Slice slice,int number,int total,QdPartnerOrgBatchProgress.Attempt attempt)
                        throws BusinessException {
                    calls[0]++;OperationContext request=request(slice,number);attempt.dispatched(request);
                    attempt.confirmed(reply(request,false),status(request,false));
                }
            });
        require(calls[0]==3 && completed.size()==3 && completed.get(2).result.getNativeResult()==null
            && completed.get(2).summary.organization.getPages().isEmpty(),"Empty pages or independently committed results were lost");
        try { completed.clear();throw new AssertionError("Completed organization batches can be altered"); }
        catch(UnsupportedOperationException expected) { }
        ErrLogReturnValue unchanged=QdPartnerOrgBatchCombine.combine(completed,OfflinePage.class,new QdPartnerOrgBatchCombine.LogWriter() {
            public ErrLogResult write(List<String> messages) { throw new AssertionError("No-page or unchanged results wrote an error log"); }
        },null);
        require(unchanged.getTotalNum()==3 && unchanged.getErrLogResult().getErrorMessagegNum()==0
            && ((SuperVO[])unchanged.getReturnValue()).length==0,"An unchanged or empty page batch was presented as a success or failure");
        ErrLogReturnValue empty=QdPartnerOrgBatchCombine.combine(Collections.singletonList(completed.get(2)),OfflinePage.class,
            new QdPartnerOrgBatchCombine.LogWriter() { public ErrLogResult write(List<String> messages) {
                throw new AssertionError("A no-page batch wrote an error log");
            } },null);
        require(empty.getTotalNum()==0 && ((SuperVO[])empty.getReturnValue()).length==0,
            "A zero-page result fabricated a native operation or failure");

        calls[0]=0;
        completed=QdPartnerOrgBatchProgress.execute(prepared,new QdPartnerOrgBatchProgress.Operation() {
            public void execute(QdPartnerOrgBatchSlices.Slice slice,int number,int total,QdPartnerOrgBatchProgress.Attempt attempt)
                    throws BusinessException {
                calls[0]++;OperationContext request=request(slice,number);attempt.dispatched(request);
                attempt.confirmed(number==1?mixedReply(request,false):reply(request,false),number==1?mixedStatus(request):status(request,false));
            }
        });
        require(completed.get(0).summary.partial==1 && completed.get(0).summary.organization.getPages().size()==2,
            "A successful and a rejected page under the same archive were not retained as partial success");
        final List<String> combinedMessages=new ArrayList<String>();
        ErrLogReturnValue combined=QdPartnerOrgBatchCombine.combine(completed,OfflinePage.class,new QdPartnerOrgBatchCombine.LogWriter() {
            public ErrLogResult write(List<String> messages) {
                combinedMessages.addAll(messages);ErrorLogVO log=new ErrorLogVO();log.setPk_errorlog("LOG1");
                return new ErrLogResult(log,messages.size(),new ErrorMsgVO[0]);
            }
        },null);
        require(combined.getTotalNum()==3 && combined.getErrLogResult().getErrorMessagegNum()==1
            && combined.getReturnValue() instanceof OfflinePage[] && ((OfflinePage[])combined.getReturnValue()).length==1
            && combinedMessages.toString().contains("P2") && combinedMessages.toString().contains("native validation")
            && combinedMessages.toString().contains(id(1)),"Mixed pages or original native errors were lost in the combined result");
        try { QdPartnerOrgBatchCombine.combine(completed,nc.vo.bd.cust.finance.CustFinanceVO.class,
                new QdPartnerOrgBatchCombine.LogWriter() { public ErrLogResult write(List<String> messages) { return null; } },null);
            throw new AssertionError("A different native page type was accepted"); }
        catch(BusinessException expected) { require(expected.getMessage().contains(id(1)),"An invalid native page type lost the request number"); }
        try { QdPartnerOrgBatchCombine.combine(java.util.Arrays.asList(completed.get(0),completed.get(0)),OfflinePage.class,
                new QdPartnerOrgBatchCombine.LogWriter() { public ErrLogResult write(List<String> messages) { return null; } },null);
            throw new AssertionError("A repeated committed page was accepted into a combined result"); }
        catch(BusinessException expected) { require(expected.getMessage().contains(id(1)),"A failed result merge lost the committed request number"); }
        try { QdPartnerOrgBatchCombine.combine(completed,OfflinePage.class,new QdPartnerOrgBatchCombine.LogWriter() {
                public ErrLogResult write(List<String> messages) throws BusinessException { throw new BusinessException("simulated log failure"); }
            },null);throw new AssertionError("A failed result log was reported as a successful merge"); }
        catch(BusinessException expected) { require(expected.getMessage().contains(id(1)) && expected.getMessage().contains(id(3)),
            "A log failure lost independently committed request numbers"); }

        calls[0]=0;
        try { QdPartnerOrgBatchProgress.execute(prepared,new QdPartnerOrgBatchProgress.Operation() {
            public void execute(QdPartnerOrgBatchSlices.Slice slice,int number,int total,QdPartnerOrgBatchProgress.Attempt attempt)
                    throws BusinessException {
                calls[0]++;OperationContext request=request(slice,number);attempt.dispatched(request);
                attempt.confirmed(mixedReply(request,true),mixedStatus(request));
            }
        });throw new AssertionError("A native success belonging to another page was accepted"); }
        catch(BusinessException expected) { require(calls[0]==1 && expected.getMessage().contains(id(1)),
            "An invalid native page did not preserve the uncertain request"); }

        calls[0]=0;
        try { QdPartnerOrgBatchProgress.execute(prepared,new QdPartnerOrgBatchProgress.Operation() {
            public void execute(QdPartnerOrgBatchSlices.Slice slice,int number,int total,QdPartnerOrgBatchProgress.Attempt attempt)
                    throws BusinessException {
                calls[0]++;OperationContext request=request(slice,1);attempt.dispatched(request);
                attempt.confirmed(reply(request,false),status(request,false));
            }
        });throw new AssertionError("A request number was reused across independent batches"); }
        catch(BusinessException expected) { require(calls[0]==2 && expected.getMessage().contains(id(1))
            && expected.getMessage().contains("未派发"),"A reused request number reached the next business call"); }

        calls[0]=0;
        try { QdPartnerOrgBatchProgress.execute(prepared,new QdPartnerOrgBatchProgress.Operation() {
            public void execute(QdPartnerOrgBatchSlices.Slice slice,int number,int total,QdPartnerOrgBatchProgress.Attempt attempt)
                    throws BusinessException {
                calls[0]++;OperationContext request=request(slice,number);attempt.dispatched(request);
                if(number==2)throw new BusinessException("simulated lost response");
                attempt.confirmed(reply(request,false),status(request,false));
            }
        });throw new AssertionError("An unknown middle batch did not stop later batches"); }
        catch(BusinessException expected) { require(calls[0]==2 && expected.getMessage().contains(id(1))
            && expected.getMessage().contains(id(2)) && expected.getMessage().contains("不要直接重试"),
            "An interrupted batch omitted confirmed or uncertain request numbers"); }

        calls[0]=0;
        try { QdPartnerOrgBatchProgress.execute(prepared,new QdPartnerOrgBatchProgress.Operation() {
            public void execute(QdPartnerOrgBatchSlices.Slice slice,int number,int total,QdPartnerOrgBatchProgress.Attempt attempt)
                    throws BusinessException {
                calls[0]++;OperationContext request=request(slice,number);attempt.dispatched(request);
                attempt.confirmed(reply(request,true),status(request,false));
            }
        });throw new AssertionError("A replayed native result was accepted"); }
        catch(BusinessException expected) { require(calls[0]==1 && expected.getMessage().contains(id(1)),
            "An invalid response did not retain its uncertain request number"); }

        calls[0]=0;
        try { QdPartnerOrgBatchProgress.execute(prepared,new QdPartnerOrgBatchProgress.Operation() {
            public void execute(QdPartnerOrgBatchSlices.Slice slice,int number,int total,QdPartnerOrgBatchProgress.Attempt attempt)
                    throws BusinessException {
                calls[0]++;OperationContext request=request(slice,number);attempt.dispatched(request);
                attempt.recovered(status(request,true));throw new BusinessException("simulated timeout");
            }
        });throw new AssertionError("Unknown recovered status continued to a later batch"); }
        catch(BusinessException expected) { require(calls[0]==1 && expected.getMessage().contains(id(1))
            && expected.getMessage().contains("未知"),"The checked unknown state was not retained"); }
        System.out.println("PASS PartnerOrgBatchProgressTests (independent receipts and uncertain batches; no NC or Oracle)");
    }
    static OperationContext request(QdPartnerOrgBatchSlices.Slice slice,int number) {
        List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        for(String pk:slice.roots())refs.add(new BusinessObjectRef("orgbulk:"+HASH+":"+pk,"BD_CUSTOMER",pk,pk,null));
        return new OperationContext(id(number),id(number),"G","ORG","10140CUG","客户","BatchUpdate","批改","UPDATE",null,
            refs,OperationContext.OWNER_POLICY_PROTOCOL,null,null,null,slice.draft());
    }
    private static String id(int number) { return "0000000000000000000000000000000"+number; }
    static ArchiveBatchStatus status(OperationContext request,boolean unknown) {
        List<ArchiveBatchStatus.Unit> units=new ArrayList<ArchiveBatchStatus.Unit>();
        List<ArchiveBatchStatus.PageHeader> headers=new ArrayList<ArchiveBatchStatus.PageHeader>();
        List<ArchiveBatchStatus.Page> pages=new ArrayList<ArchiveBatchStatus.Page>();
        PartnerBatchDraft draft=request.getPartnerBatchDraft();
        for(BusinessObjectRef ref:request.getObjects()) {
            int count=0;
            for(PartnerOrgBatchPlan.Page page:draft.getSelectedPages())if(ref.getObjectPk().equals(page.getRoot())) {
                count++;if(!unknown)pages.add(new ArchiveBatchStatus.Page(ref.getClientObjectId(),page.getId(),page.getTarget(),"UNCHANGED",null));
            }
            units.add(new ArchiveBatchStatus.Unit(ref.getClientObjectId(),ref.getObjectPk(),ref.getBillCode(),"OWNER",request.getOrgId(),
                unknown?"UNKNOWN":"UNCHANGED",null));
            if(!unknown)headers.add(new ArchiveBatchStatus.PageHeader(ref.getClientObjectId(),count));
        }
        ArchiveBatchStatus.Organization org=new ArchiveBatchStatus.Organization(draft.getTabCode(),java.util.Arrays.asList(draft.getTargetOrgs()),
            !unknown,headers,pages,java.util.Arrays.asList(draft.getSelectedPages()));
        return new ArchiveBatchStatus(request.getRequestId(),unknown?"UNKNOWN":"SUCCESS",units,org);
    }
    static OperationResponse reply(OperationContext request,boolean replayed) {
        List<ArchiveBatchResult.Unit> units=new ArrayList<ArchiveBatchResult.Unit>();
        for(BusinessObjectRef ref:request.getObjects())units.add(new ArchiveBatchResult.Unit(ref.getClientObjectId(),ref.getObjectPk(),"UNCHANGED"));
        int count=request.getPartnerBatchDraft().getSelectedPages().length;
        ErrLogReturnValue nativeResult=count==0?null:new ErrLogReturnValue(new ErrLogResult(null,0,null),new SuperVO[0],count);
        return new OperationResponse(request.getRequestId(),replayed,new ArchiveOrgBatchUpdateResult(nativeResult,units,count),request.getObjects());
    }
    private static ArchiveBatchStatus mixedStatus(OperationContext request) {
        BusinessObjectRef ref=request.getObjects().get(0);PartnerBatchDraft draft=request.getPartnerBatchDraft();
        PartnerOrgBatchPlan.Page[] selected=draft.getSelectedPages();
        List<ArchiveBatchStatus.Unit> units=Collections.singletonList(new ArchiveBatchStatus.Unit(ref.getClientObjectId(),ref.getObjectPk(),
            ref.getBillCode(),"OWNER",request.getOrgId(),"PARTIAL","PARTIAL_NATIVE_REJECTED"));
        List<ArchiveBatchStatus.Page> pages=java.util.Arrays.asList(
            new ArchiveBatchStatus.Page(ref.getClientObjectId(),selected[0].getId(),selected[0].getTarget(),"SUCCESS",null),
            new ArchiveBatchStatus.Page(ref.getClientObjectId(),selected[1].getId(),selected[1].getTarget(),"FAILED","VALIDATION"));
        ArchiveBatchStatus.Organization org=new ArchiveBatchStatus.Organization(draft.getTabCode(),java.util.Arrays.asList(draft.getTargetOrgs()),
            true,Collections.singletonList(new ArchiveBatchStatus.PageHeader(ref.getClientObjectId(),2)),pages,java.util.Arrays.asList(selected));
        return new ArchiveBatchStatus(request.getRequestId(),"PARTIAL",units,org);
    }
    private static OperationResponse mixedReply(OperationContext request,boolean wrongPage) {
        BusinessObjectRef ref=request.getObjects().get(0);PartnerOrgBatchPlan.Page page=request.getPartnerBatchDraft().getSelectedPages()[0];
        SuperVO[] values=new SuperVO[]{new OfflinePage(wrongPage?"OTHER":page.getId(),page.getRoot(),page.getTarget())};
        ErrorMsgVO message=new ErrorMsgVO();message.setErrormsg("native validation");
        ErrLogReturnValue nativeResult=new ErrLogReturnValue(new ErrLogResult(null,1,new ErrorMsgVO[]{message}),values,2);
        return new OperationResponse(request.getRequestId(),false,new ArchiveOrgBatchUpdateResult(nativeResult,
            Collections.singletonList(new ArchiveBatchResult.Unit(ref.getClientObjectId(),ref.getObjectPk(),"PARTIAL")),2),request.getObjects());
    }
    private static final class OfflinePage extends SuperVO {
        private static final long serialVersionUID=1L;private final String id,root,target;
        OfflinePage(String id,String root,String target) { this.id=id;this.root=root;this.target=target; }
        @Override public String getPrimaryKey() { return id; }
        @Override public Object getAttributeValue(String name) {
            if("pk_customer".equals(name))return root;if("pk_org".equals(name))return target;if("pk_group".equals(name))return "G";
            throw new AssertionError("Unexpected page field "+name);
        }
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
