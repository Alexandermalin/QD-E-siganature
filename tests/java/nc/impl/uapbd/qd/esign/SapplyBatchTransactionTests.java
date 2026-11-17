package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect;
import nc.bs.uapbd.qd.esign.core.TransactionBoundaryProbe;
import nc.itf.uap.pf.IplatFormEntry;
import nc.itf.uapbd.qd.esign.ISapplyBatchTransactionService;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.pub.pf.ICPFParameter;
import nc.vo.ic.qd.esign.SapplyPfBatchCommand;
import nc.vo.ic.qd.esign.SapplyPfBatchResult;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uap.pf.PFBatchExceptionInfo;
import nc.vo.uap.pf.PfProcessBatchRetObject;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SapplyBatchOutcome;

public final class SapplyBatchTransactionTests {
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        long now = System.currentTimeMillis();
        Map<String,Object> request = EsignValues.row("node_code", "40080801", "button_code", "Commit",
                "operation_type", "UPDATE", "signature_state", "SUCCESS", "pk_org", "O",
                "policy_mode", SapplyWorkflowBatchUnit.POLICY_MODE, "business_state", "READY",
                "expires_at", EsignValues.utc(now + 60000));
        SapplyBatchTransactionServiceImpl.requireReady(request, "SAVE", now);
        require(SapplyWorkflowBatchUnit.matchesNativeAction("Approve","UPDATE","APPROVEU","U")
                && SapplyWorkflowBatchUnit.matchesNativeAction("UnApprove","UPDATE","UNAPPROVEU","U")
                && !SapplyWorkflowBatchUnit.matchesNativeAction("Approve","UPDATE","APPROVEOTHER","U"),
                "Native approval action must retain the signed request user");
        String ncUser="1001Z810000000000001";
        require(ncUser.length()==20 && SapplyWorkflowBatchUnit.matchesNativeAction("Approve","UPDATE","APPROVE"+ncUser,ncUser),
                "The native action retains a 20-character NC user key");
        Map<String,Object> approvalRequest=new java.util.HashMap<String,Object>(request);
        approvalRequest.put("button_code","Approve");approvalRequest.put("user_id",ncUser);
        SapplyBatchTransactionServiceImpl.requireReady(approvalRequest,"APPROVE"+ncUser,now);
        try{SapplyBatchTransactionServiceImpl.requireReady(approvalRequest,"APPROVEOTHER",now);
            throw new AssertionError("A different native workflow user was accepted");
        }catch(BusinessException expected) { }
        try {
            OperationTransactionServiceImpl.requireOrdinaryRoute(request);
            throw new AssertionError("Batch request accepted by the ordinary claim");
        } catch (BusinessException expected) { }
        try {
            SapplyBatchTransactionServiceImpl.requireReady(request, "UNSAVE", now);
            throw new AssertionError("Other PF action accepted");
        } catch (BusinessException expected) { }
        request.put("business_state", "RUNNING");
        request.put("execution_token", "token");
        SapplyWorkflowBatchUnit.requireRequest(request, "token", "SAVE", now);
        try {
            SapplyBatchTransactionServiceImpl.requireReady(request, "SAVE", now);
            throw new AssertionError("Previously claimed batch was accepted");
        } catch (BusinessException expected) { }
        try {
            SapplyWorkflowBatchUnit.requireRequest(request, "wrong", "SAVE", now);
            throw new AssertionError("Wrong execution token accepted");
        } catch (BusinessException expected) { }
        try {
            SapplyWorkflowBatchUnit.requireRequest(request, "token", "SAVE", now + 120000);
            throw new AssertionError("Expired authorization accepted");
        } catch (BusinessException expected) { }

        List<Map<String,Object>> rows = Arrays.asList(bill("A", "PENDING"), bill("B", "PENDING"), bill("C", "PENDING"));
        List<nc.vo.uapbd.qd.esign.BusinessObjectRef> approved=
                SapplyBatchTransactionServiceImpl.requireExactSelection(rows,new String[]{"C","A","B"},"G","O");
        require(approved.size() == 3,
                "Membership must be checked by primary key regardless of PF order");
        String[] signedSelection={"C","A","B"};String[] ids={"client-C","client-A","client-B"};
        SapplyBatchTransactionServiceImpl.requireApprovalIntents("APPROVEU",signedSelection,ids,approved);
        try {
            SapplyBatchTransactionServiceImpl.requireApprovalIntents("APPROVEU",signedSelection,
                    new String[]{"client-A","client-C","client-B"},approved);
            throw new AssertionError("Approval intent was attached to a different bill");
        } catch(BusinessException expected) { }
        try {
            SapplyBatchTransactionServiceImpl.requireApprovalIntents("SAVE",signedSelection,ids,approved);
            throw new AssertionError("Non-approval accepted approval payloads");
        } catch(BusinessException expected) { }
        for (String[] selected : new String[][]{{"A","B"}, {"A","B","B"}, {"A","B","D"}, {"A","B","C "}}) {
            try {
                SapplyBatchTransactionServiceImpl.requireExactSelection(rows, selected, "G", "O");
                throw new AssertionError("Different or duplicate selected objects were accepted");
            } catch (BusinessException expected) { }
        }
        try {
            SapplyWorkflowBatchUnit.requirePendingBill(rows.get(0), "G", "OTHER", "A");
            throw new AssertionError("An unauthorized organization was accepted");
        } catch (BusinessException expected) { }

        SapplyBatchOutcome result = SapplyBatchTransactionServiceImpl.summarize("request",
                Arrays.asList(bill("A", "SUCCESS"), bill("B", "FAILED"), bill("C", "SUCCESS")), "G", "O");
        require("PARTIAL".equals(result.getState()) && "FAILED".equals(result.getUnits().get(1).getState()),
                "A failed middle bill must preserve successful siblings");
        result = SapplyBatchTransactionServiceImpl.summarize("request",
                Arrays.asList(bill("A", "SUCCESS"), bill("B", "PENDING"), bill("C", "SUCCESS")), "G", "O");
        require("UNKNOWN".equals(result.getState()) && "UNKNOWN".equals(result.getUnits().get(1).getState())
                && "SUCCESS".equals(result.getUnits().get(2).getState()),
                "A missing receipt must stay unknown without overwriting successful siblings");
        try {
            SapplyWorkflowBatchUnit.requirePendingBill(bill("A", "SUCCESS"), "G", "O", "A");
            throw new AssertionError("Previously completed unit can execute again");
        } catch (BusinessException expected) { }
        verifyDispatch(result);
        verifyApprovalDispatch("APPROVEU");
        verifyApprovalDispatch("UNAPPROVEU");
        verifyPrecheckDispatch(false);
        verifyPrecheckDispatch(true);
        verifyRejectionValidation();
        System.out.println("PASS SapplyBatchTransactionTests (batch claim, scope and durable partial outcomes)");
    }

    private static void verifyRejectionValidation() throws BusinessException {
        List<Map<String,Object>> rows=Arrays.asList(bill("A","PENDING"),bill("B","SUCCESS"),bill("C","PENDING"));
        SapplyBatchTransactionServiceImpl.RejectionSets result=SapplyBatchTransactionServiceImpl.requireRejections(
                rows,new String[]{"A"},new String[]{"C"},"G","O");
        require(result.rejected.contains("A") && result.skipped.contains("C"),"Valid pre-execution results are retained");
        rejectLists(rows,null,new String[0]);
        rejectLists(rows,new String[]{"A","A"},new String[0]);
        rejectLists(rows,new String[]{"A"},new String[]{"A"});
        rejectLists(rows,new String[]{"D"},new String[0]);
        rejectLists(rows,new String[]{"B"},new String[0]);
        rejectLists(rows,new String[0],new String[]{"B"});
        rejectLists(Arrays.asList(bill("A","PENDING"),bill("A","PENDING")),new String[0],new String[0]);
        rejectLists(Arrays.asList(bill("A","INVALID")),new String[0],new String[0]);
        Map<String,Object> unfinished=bill("B","SUCCESS");unfinished.put("unit_finished_at",null);
        rejectLists(Arrays.asList(unfinished),new String[0],new String[0]);
    }

    private static void rejectLists(List<Map<String,Object>> rows,String[] rejected,String[] skipped) throws BusinessException {
        try { SapplyBatchTransactionServiceImpl.requireRejections(rows,rejected,skipped,"G","O");
            throw new AssertionError("An invalid pre-execution rejection was accepted");
        } catch(BusinessException expected) { }
    }

    private static void verifyPrecheckDispatch(final boolean approval) throws BusinessException {
        final String id="abcd0000000000000000000000000123",token="ba980000000000000000000000000456";
        final String action=approval?"APPROVEU":"SAVE";final int[] step={0};
        final ICPFParameter firstUser=new ICPFParameter(),secondUser=new ICPFParameter(),thirdUser=new ICPFParameter();
        WorkflownoteVO note=approval?new WorkflownoteVO():null;
        SapplyPfBatchCommand command=new SapplyPfBatchCommand(action,"4455",
                new SapplyBillVO[]{outbound("A"),outbound("B"),outbound("C")},
                new PfUserObject[]{firstUser,secondUser,thirdUser},note,null,new String[]{"B","C"});
        final SapplyBatchDispatch.Submission submission=SapplyBatchDispatch.Submission.check(command);
        final String[] signed=approval?submission.approvalProof.signedIds():null;
        Map<String,Object> first=bill("A","SUCCESS"),second=bill("B","FAILED"),third=bill("C","FAILED");
        second.put("unit_error_code","CLIENT_PRECHECK_SKIPPED");third.put("unit_error_code","CLIENT_PRECHECK_SKIPPED");
        final SapplyBatchOutcome outcome=SapplyBatchTransactionServiceImpl.summarize(id,Arrays.asList(first,second,third),"G","O");
        ISapplyBatchTransactionService tx=new ISapplyBatchTransactionService(){
            public String claim_RequiresNew(String request,String actual,String[] pks,String[] ids){
                require(++step[0]==1 && action.equals(actual) && Arrays.equals(pks,new String[]{"A","B","C"})
                        && Arrays.equals(ids,signed),"The full signed selection is claimed first");return token;}
            public void finish_RequiresNew(String request,String actual,String[] rejected,String[] skipped){
                require(++step[0]==3 && rejected.length==0 && Arrays.equals(skipped,new String[]{"B","C"}),
                        "Precheck exclusions are finalized separately from native failures");}
            public SapplyBatchOutcome verifyFinished_RequiresNew(String request){require(++step[0]==4,"Receipts read last");return outcome;}
        };
        IplatFormEntry platform=(IplatFormEntry)Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                new Class<?>[]{IplatFormEntry.class},new InvocationHandler(){
            @SuppressWarnings("unchecked") public Object invoke(Object proxy,Method method,Object[] args) throws Throwable {
                SapplyBillVO[] submitted=(SapplyBillVO[])args[3];Object[] users=(Object[])args[4];
                require(++step[0]==2 && submitted.length==1 && "A".equals(submitted[0].getHead().getCgeneralhid())
                        && users.length==1 && users[0]==firstUser,"Only passing bills and their matching users reach native PF");
                if(!approval)nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspectTests.verifyStandardDispatchBoundary(
                        (Map<String,Object>)args[5],submitted);
                return new PfProcessBatchRetObject(null,null);}
        });
        OperationResponse result=SapplyBatchDispatch.execute(id,submission,
                new TransactionBoundaryProbe(){public void assertNoTransaction(){}},tx,platform,!approval);
        require(step[0]==4 && result.getObjects().size()==1
                && !((SapplyPfBatchResult)result.getBusinessResult()).isNativeBatchInterrupted()
                && "CLIENT_PRECHECK_SKIPPED".equals(outcome.getUnits().get(1).getErrorCode()),
                "Only the actually submitted success is returned as committed");
        for(String[] invalid:new String[][]{{"D"},{"B","B"},{"A","B","C"}}){
            try{SapplyBatchDispatch.Submission.check(new SapplyPfBatchCommand(action,"4455",command.getBills(),
                    command.getUserObjects(),note,null,invalid));
                throw new AssertionError("An invalid exclusion list was accepted");
            }catch(BusinessException expected) { }
        }
    }

    private static void verifyApprovalDispatch(final String action) throws BusinessException {
        final String id="abcd0000000000000000000000000123",token="ba980000000000000000000000000456";
        final int[] step={0};
        final WorkflownoteVO note=action.startsWith("APPROVE")?new WorkflownoteVO():null;
        if(note!=null){note.setApproveresult("Y");note.setChecknote("approved");}
        SapplyPfBatchCommand command=new SapplyPfBatchCommand(action,"4455",new SapplyBillVO[]{outbound("A"),outbound("B")},
                null,note,Collections.<String,Object>singletonMap("batch","batch"));
        final SapplyBatchDispatch.Submission submission=SapplyBatchDispatch.Submission.check(command);
        final String[] signed=submission.approvalProof.signedIds();
        Map<String,Object> a=bill("A","SUCCESS"),b=bill("B","SUCCESS");
        a.put("client_object_id",signed[0]);b.put("client_object_id",signed[1]);
        final SapplyBatchOutcome outcome=SapplyBatchTransactionServiceImpl.summarize(id,Arrays.asList(a,b),"G","O");
        ISapplyBatchTransactionService transactions=new ISapplyBatchTransactionService(){
            public String claim_RequiresNew(String request,String actual,String[] pks,String[] ids){
                require(++step[0]==1 && action.equals(actual) && Arrays.equals(signed,ids)
                        && Arrays.equals(new String[]{"A","B"},pks),"Approval content is checked before native processing");return token;}
            public void finish_RequiresNew(String request,String actual,String[] rejected,String[] skipped){
                require(++step[0]==3 && rejected.length==0 && skipped.length==0,"Batch approval finalizes after native execution");}
            public SapplyBatchOutcome verifyFinished_RequiresNew(String request){require(++step[0]==4,"Durable receipts read last");return outcome;}
        };
        IplatFormEntry platform=(IplatFormEntry)Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                new Class<?>[]{IplatFormEntry.class},new InvocationHandler(){
            public Object invoke(Object proxy,Method method,Object[] args){
                require(++step[0]==2 && action.equals(args[0]) && args[2]==note
                        && ((Map<?,?>)args[5]).get(SapplyWorkflowBatchAspect.REQUEST_PARAMETER)
                            instanceof SapplyWorkflowBatchAspect.RequestEnvelope,
                        "One native batch receives the confirmed approval note and a local proof");
                return new PfProcessBatchRetObject(null,null);}
        });
        OperationResponse response=SapplyBatchDispatch.execute(id,submission,
                new TransactionBoundaryProbe(){public void assertNoTransaction(){}},transactions,platform);
        require(step[0]==4 && response.getObjects().size()==2,"Approval exposes only committed bill receipts");
    }

    private static void verifyDispatch(final SapplyBatchOutcome durable) throws BusinessException {
        final String id = "abcd0000000000000000000000000123";
        final String token = "ba980000000000000000000000000456";
        final int[] step = {0};
        final SapplyBatchOutcome committed = new SapplyBatchOutcome(id, durable.getState(), durable.getUnits());
        final PFBatchExceptionInfo errors = new PFBatchExceptionInfo();
        errors.putErrorMessage(1, outbound("B"), null);
        ISapplyBatchTransactionService transactions = new ISapplyBatchTransactionService() {
            public String claim_RequiresNew(String requestId, String action, String[] pks,String[] approvalIds) {
                require(++step[0] == 1 && id.equals(requestId) && "SAVE".equals(action)
                        && approvalIds==null && Arrays.equals(new String[]{"A","B","C"}, pks), "Claim validates the full native selection");
                return token;
            }
            public void finish_RequiresNew(String requestId, String actual, String[] rejected, String[] skipped) {
                require(++step[0] == 3 && token.equals(actual) && rejected.length == 0 && skipped.length == 0,
                        "A native error message alone cannot prove that a bill never ran");
            }
            public SapplyBatchOutcome verifyFinished_RequiresNew(String requestId) {
                require(++step[0] == 4, "Read receipts after finalization");
                return committed;
            }
        };
        IplatFormEntry platform = (IplatFormEntry) Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                new Class<?>[]{IplatFormEntry.class}, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        require(++step[0] == 2 && "processBatch".equals(method.getName()), "Only one native batch RPC");
                        Object envelope = ((Map<?,?>) args[5]).get(SapplyWorkflowBatchAspect.REQUEST_PARAMETER);
                        require(envelope instanceof SapplyWorkflowBatchAspect.RequestEnvelope
                                && !envelope.toString().contains(token), "Only an internal redacted envelope enters PF");
                        return new PfProcessBatchRetObject(null, errors);
                    }
                });
        SapplyPfBatchCommand command = new SapplyPfBatchCommand("SAVE", "4455",
                new SapplyBillVO[]{outbound("A"), outbound("B"), outbound("C")}, null, null, null);
        OperationResponse response = SapplyBatchDispatch.execute(id, SapplyBatchDispatch.Submission.check(command),
                new TransactionBoundaryProbe() { public void assertNoTransaction() { } }, transactions, platform);
        require(step[0] == 4 && response.getObjects().size() == 2
                && ((SapplyPfBatchResult) response.getBusinessResult()).getOutcome() == committed,
                "Only durable success is exposed as committed");
    }

    private static SapplyBillVO outbound(final String pk) {
        return new SapplyBillVO() {
            @Override public IBillMeta getMetaData() { return META; }
            @Override public SapplyBillHeadVO getHead() {
                return new SapplyBillHeadVO() { @Override public String getCgeneralhid() { return pk; }
                    @Override public String getPk_group(){return "G";}
                    @Override public String getPk_org(){return "O";}
                    @Override public java.util.Set<String> usedAttributeNames(){return Collections.singleton("pk");}
                    @Override public Object getAttributeValue(String name){return "pk".equals(name)?pk:null;}
                    @Override public IVOMeta getMetaData(){return null;}
                };
            }
        };
    }

    private static final IBillMeta META = new IBillMeta() {
        public Map<String,Object> getBusinessAttribute() { return java.util.Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "SAPPLY_BATCH_TEST"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    private static Map<String,Object> bill(String pk, String state) {
        return EsignValues.row("object_id", "row-" + pk, "client_object_id", "client-" + pk,
                "object_pk", pk, "object_type", "IC_SAPPLY", "relation_type", "ROOT",
                "pk_group", "G", "pk_org", "O", "unit_state", state,
                "unit_finished_at", "PENDING".equals(state) ? null : EsignValues.utc());
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
