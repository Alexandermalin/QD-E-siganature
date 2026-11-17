package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.bd.errorlog.ErrorLogVO;
import nc.vo.bd.errorlog.ErrorMsgVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.ArchiveBatchUpdateResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Tests multi-transaction receipts without a Swing dialog, NC services or database writes. */
public final class PartnerBatchChunksTests {
    private PartnerBatchChunksTests() { }
    public static void run() throws Exception {
        completedAndCombined();
        singleFailureLog();
        filteredFailureWithoutNativeMessage();
        completeNativeErrorLog();
        rejectIncompleteNativeLog();
        duplicateRejectedBeforeDispatch();
        contextCannotChange();
        dispatchFailureMessages();
        interruptionCases();
        rejectForeignResults();
        thresholdBoundary();
        PartnerBatchRecoveryTests.run();
        System.out.println("PASS PartnerBatchChunksTests (chunk selection, partial outcomes and result aggregation; no NC or Oracle)");
    }
    private static void dispatchFailureMessages() {
        BusinessException original=new BusinessException("before dispatch");
        require(QdPartnerBatchDispatcher.dispatchFailure(null,false,original)==original,"An unsent error was obscured");
        String unknown=QdPartnerBatchDispatcher.dispatchFailure("REQ",false,original).getMessage();
        String confirmed=QdPartnerBatchDispatcher.dispatchFailure("REQ",true,original).getMessage();
        require(unknown.contains("尚未确认") && unknown.contains("REQ"),"Unknown outcome was not identified");
        require(confirmed.contains("已确认") && !confirmed.contains("尚未确认") && confirmed.contains("REQ"),
            "Post-commit client failure was reported as an unknown transaction");
    }
    private static void singleFailureLog() throws Exception {
        List<QdPartnerBatchChunks.Completed> parts=QdPartnerBatchChunks.execute(new String[]{"A"},2,
            new QdPartnerBatchChunks.Operation() {
                public void execute(String[] selected,int number,int total,QdPartnerBatchChunks.Attempt attempt) throws BusinessException {
                    attempt.dispatched("ONLY");attempt.confirmed(result(new String[0],1,1,"A rejected"));
                }
            });
        final int[] writes={0};ErrLogReturnValue merged=QdPartnerBatchChunks.combine(parts,CustomerVO.class,
            new QdPartnerBatchChunks.LogWriter() {
                public ErrLogResult write(List<String> messages) { writes[0]++;return persisted(messages); }
            });
        require(writes[0]==1 && merged.getErrLogResult().getErrorMessagegNum()==1
            && "LOG".equals(merged.getErrLogResult().getErrorLog().getPk_errorlog()),
            "Single-batch filtered errors did not get a queryable log");
    }
    private static void completeNativeErrorLog() throws Exception {
        final int count=601;final int[] pages={0};final List<String> logged=new ArrayList<String>();
        List<QdPartnerBatchChunks.Completed> parts=truncatedNativeReceipt(count);
        ErrLogReturnValue combined=QdPartnerBatchChunks.combine(parts,CustomerVO.class,new QdPartnerBatchChunks.LogWriter() {
            public ErrLogResult write(List<String> messages) { logged.addAll(messages);return persisted(messages); }
        },new QdPartnerBatchChunks.NativeLogReader() {
            public String[] messageIds(String logId) {
                require("NATIVE".equals(logId),"Wrong native error log queried");
                String[] ids=new String[count];for(int i=0;i<count;i++) ids[i]="E"+i;return ids;
            }
            public List<ErrorMsgVO> messages(String[] ids) {
                require(ids.length<=500,"Native error log was not read in bounded pages");pages[0]++;
                List<ErrorMsgVO> rows=new ArrayList<ErrorMsgVO>();
                for(int i=ids.length-1;i>=0;i--) rows.add(nativeMessage(ids[i],"NATIVE"));return rows;
            }
        });
        require(pages[0]==2 && logged.size()==count+2 && logged.get(0).contains("ORIGINAL-REQUEST") && logged.get(0).contains("NATIVE")
            && logged.get(1).equals("native E0") && logged.get(count).equals("native E600")
            && logged.get(count+1).contains("A-code"),"Full native reasons or filtered object details were lost");
        require(combined.getErrLogResult().getErrorMessagegNum()==1,
            "Native message count was confused with failed object count");
    }
    private static void filteredFailureWithoutNativeMessage() throws Exception {
        final BusinessObjectRef ref=new BusinessObjectRef("a","BD_CUSTOMER","A","A-code",null);
        final OperationContext request=new OperationContext("R","R","G","ORG","10140CUG","客户","BatchUpdate","批改","UPDATE",null,
            Arrays.asList(ref),OperationContext.OWNER_POLICY_PROTOCOL);
        final ErrLogResult original=new ErrLogResult(null,0,null);
        final ArchiveBatchUpdateResult response=new ArchiveBatchUpdateResult(new ErrLogReturnValue(original,new CustomerVO[0],1),
            Arrays.asList(new ArchiveBatchResult.Unit("a","A","FAILED")));
        List<QdPartnerBatchChunks.Completed> parts=QdPartnerBatchChunks.execute(new String[]{"A"},2,new QdPartnerBatchChunks.Operation() {
            public void execute(String[] selected,int number,int total,QdPartnerBatchChunks.Attempt attempt) throws BusinessException {
                attempt.dispatched("FILTERED");attempt.confirmed(QdPartnerBatchDispatcher.validateResult(request,response),original);
            }
        });
        final List<String> logged=new ArrayList<String>();
        QdPartnerBatchChunks.combine(parts,CustomerVO.class,new QdPartnerBatchChunks.LogWriter() {
            public ErrLogResult write(List<String> messages) { logged.addAll(messages);return persisted(messages); }
        },new QdPartnerBatchChunks.NativeLogReader() {
            public String[] messageIds(String logId) { throw new AssertionError("No native message should be queried"); }
            public List<ErrorMsgVO> messages(String[] ids) { throw new AssertionError("No native message should be queried"); }
        });
        require(logged.size()==2 && logged.get(1).contains("A-code"),"Filtered failure without native text was lost");
    }
    private static void rejectIncompleteNativeLog() throws Exception {
        final List<QdPartnerBatchChunks.Completed> parts=truncatedNativeReceipt(12);
        for(final int mode:new int[]{0,1,2,3,4}) {
            try {
                QdPartnerBatchChunks.combine(parts,CustomerVO.class,new QdPartnerBatchChunks.LogWriter() {
                    public ErrLogResult write(List<String> messages) { throw new AssertionError("Invalid native log was written"); }
                },new QdPartnerBatchChunks.NativeLogReader() {
                    public String[] messageIds(String logId) {
                        String[] ids=new String[mode==0?11:12];for(int i=0;i<ids.length;i++) ids[i]="E"+i;
                        if(mode==1) ids[11]="E0";
                        if(mode==4) ids[11]="E' OR '1'='1";
                        return ids;
                    }
                    public List<ErrorMsgVO> messages(String[] ids) {
                        List<ErrorMsgVO> rows=new ArrayList<ErrorMsgVO>();
                        for(String id:ids) rows.add(nativeMessage(id,mode==2 && "E11".equals(id)?"OTHER":"NATIVE"));
                        if(mode==3) rows.get(0).setErrormsg("wrong preview");return rows;
                    }
                });
                throw new AssertionError("Incomplete or foreign native error log was accepted");
            } catch(BusinessException expected) { require(expected.getMessage().contains("原生日志"),"Missing native log context"); }
        }
        try {
            QdPartnerBatchChunks.combine(truncatedNativeReceipt(12,"LOG' OR '1'='1"),CustomerVO.class,
                new QdPartnerBatchChunks.LogWriter() {
                    public ErrLogResult write(List<String> messages) { throw new AssertionError("Invalid log was written"); }
                },new QdPartnerBatchChunks.NativeLogReader() {
                    public String[] messageIds(String logId) { throw new AssertionError("Unsafe log identifier reached native SQL"); }
                    public List<ErrorMsgVO> messages(String[] ids) { throw new AssertionError("Unexpected message query"); }
                });
            throw new AssertionError("Unsafe native log identifier was accepted");
        } catch(BusinessException expected) { require(expected.getMessage().contains("无法查询原生日志"),"Unsafe log was not rejected"); }
    }
    private static List<QdPartnerBatchChunks.Completed> truncatedNativeReceipt(final int count) throws Exception {
        return truncatedNativeReceipt(count,"NATIVE");
    }
    private static List<QdPartnerBatchChunks.Completed> truncatedNativeReceipt(final int count,String logId) throws Exception {
        final BusinessObjectRef ref=new BusinessObjectRef("a","BD_CUSTOMER","A","A-code",null);
        final OperationContext request=new OperationContext("R","R","G","ORG","10140CUG","客户","BatchUpdate","批改","UPDATE",null,
            Arrays.asList(ref),OperationContext.OWNER_POLICY_PROTOCOL);
        ErrorLogVO log=new ErrorLogVO();log.setPk_errorlog(logId);ErrorMsgVO[] preview=new ErrorMsgVO[10];
        for(int i=0;i<preview.length;i++) preview[i]=nativeMessage("E"+i,"NATIVE");
        final ErrLogResult original=new ErrLogResult(log,count,preview);
        final ArchiveBatchUpdateResult response=new ArchiveBatchUpdateResult(new ErrLogReturnValue(original,new CustomerVO[0],1),
            Arrays.asList(new ArchiveBatchResult.Unit("a","A","FAILED")));
        return QdPartnerBatchChunks.execute(new String[]{"A"},2,new QdPartnerBatchChunks.Operation() {
            public void execute(String[] selected,int number,int total,QdPartnerBatchChunks.Attempt attempt) throws BusinessException {
                attempt.dispatched("ORIGINAL-REQUEST");attempt.confirmed(QdPartnerBatchDispatcher.validateResult(request,response),original);
            }
        });
    }
    private static ErrorMsgVO nativeMessage(String id,String logId) {
        ErrorMsgVO row=message("native "+id);row.setPk_errormsg(id);row.setPk_errorlog(logId);return row;
    }
    private static void contextCannotChange() throws Exception {
        QdPartnerBatchDispatcher.BatchAnchor anchor=new QdPartnerBatchDispatcher.BatchAnchor("G","10140CUG","ORG","USER");
        anchor.requireSame(new QdPartnerBatchDispatcher.BatchAnchor("G","10140CUG","ORG","USER"));
        rejectAnchor(anchor,new QdPartnerBatchDispatcher.BatchAnchor("OTHER","10140CUG","ORG","USER"));
        rejectAnchor(anchor,new QdPartnerBatchDispatcher.BatchAnchor("G","10140CUO","ORG","USER"));
        rejectAnchor(anchor,new QdPartnerBatchDispatcher.BatchAnchor("G","10140CUG","OTHER","USER"));
        rejectAnchor(anchor,new QdPartnerBatchDispatcher.BatchAnchor("G","10140CUG","ORG","OTHER"));
        rejectAnchor(anchor,null);
    }
    private static void rejectAnchor(QdPartnerBatchDispatcher.BatchAnchor anchor,QdPartnerBatchDispatcher.BatchAnchor changed)
            throws Exception {
        try { anchor.requireSame(changed);throw new AssertionError("Batch context change was accepted"); }
        catch(BusinessException expected) { }
    }
    private static void completedAndCombined() throws Exception {
        final String[] source={"A","B","C","D","E"};final List<String> observed=new ArrayList<String>();
        List<QdPartnerBatchChunks.Completed> parts=QdPartnerBatchChunks.execute(source,2,new QdPartnerBatchChunks.Operation() {
            public void execute(String[] selected,int number,int total,QdPartnerBatchChunks.Attempt attempt) throws BusinessException {
                require(total==3,"Incorrect batch count");observed.add(Arrays.toString(selected));
                source[2]="MUTATED";attempt.dispatched("REQ"+number);
                if(number==1) attempt.confirmed(result(new String[]{"A"},2,1,"B rejected"));
                else if(number==2) attempt.confirmed(result(new String[]{"C","D"},2,0,null));
                else attempt.confirmed(result(new String[0],1,0,null));
                selected[0]="CALLER_MUTATION";
            }
        });
        require(observed.equals(Arrays.asList("[A, B]","[C, D]","[E]")),"Original selection was not frozen");
        final List<String> logged=new ArrayList<String>();
        ErrLogReturnValue merged=QdPartnerBatchChunks.combine(parts,CustomerVO.class,new QdPartnerBatchChunks.LogWriter() {
            public ErrLogResult write(List<String> messages) { logged.addAll(messages);return persisted(messages); }
        });
        require(merged.getTotalNum()==5 && merged.getErrLogResult().getErrorMessagegNum()==1
            && "LOG".equals(merged.getErrLogResult().getErrorLog().getPk_errorlog()),"Failed batch summary was not retained");
        require(merged.getReturnValue().getClass()==CustomerVO[].class && ((CustomerVO[])merged.getReturnValue()).length==3,
            "Combined results lost their native VO array type or successes");
        require(logged.size()==2 && logged.get(0).contains("REQ1") && logged.get(1).contains("B rejected"),
            "Combined error log did not preserve batch provenance and failure details");
    }
    private static void duplicateRejectedBeforeDispatch() throws Exception {
        final int[] calls={0};
        try { QdPartnerBatchChunks.execute(new String[]{"A","B","A"},2,new QdPartnerBatchChunks.Operation() {
            public void execute(String[] selected,int number,int total,QdPartnerBatchChunks.Attempt attempt) { calls[0]++; }
        });throw new AssertionError("Duplicate across chunks accepted"); }
        catch(BusinessException expected) { require(calls[0]==0,"Duplicate was found after a transaction was dispatched"); }
    }
    private static void interruptionCases() throws Exception {
        String before=interrupted(false,false);require(before.contains("已确认结果 1 批、2 个档案")
            && before.contains("第 2/3 批未派发") && before.contains("后续 1 个档案未发起") && before.contains("REQ1"),
            "Unsent chunk did not report prior and remaining work accurately");
        String unknown=interrupted(true,false);require(unknown.contains("第 2/3 批结果未确认")
            && unknown.contains("REQ2") && unknown.contains("不要直接重试") && unknown.contains("后续 1 个档案未发起"),
            "Dispatched chunk was not reported as unknown");
        String committed=interrupted(true,true);require(committed.contains("已确认结果 2 批、4 个档案")
            && committed.contains("第 2/3 批已确认") && committed.contains("REQ1") && committed.contains("REQ2"),
            "Confirmed chunk was incorrectly reported as uncommitted");
    }
    private static String interrupted(final boolean dispatch,final boolean confirm) throws Exception {
        try { QdPartnerBatchChunks.execute(new String[]{"A","B","C","D","E"},2,new QdPartnerBatchChunks.Operation() {
            public void execute(String[] selected,int number,int total,QdPartnerBatchChunks.Attempt attempt) throws BusinessException {
                if(number==1) { attempt.dispatched("REQ1");attempt.confirmed(result(selected,selected.length,0,null));return; }
                if(dispatch) attempt.dispatched("REQ2");if(confirm) attempt.confirmed(result(selected,selected.length,0,null));
                throw new BusinessException("simulated cancellation or lost reply");
            }
        });throw new AssertionError("Interrupted batch returned success"); }
        catch(BusinessException expected) { return expected.getMessage(); }
    }
    private static void rejectForeignResults() throws Exception {
        List<QdPartnerBatchChunks.Completed> parts=QdPartnerBatchChunks.execute(new String[]{"A","B","C"},2,
            new QdPartnerBatchChunks.Operation() {
                public void execute(String[] selected,int number,int total,QdPartnerBatchChunks.Attempt attempt) throws BusinessException {
                    attempt.dispatched("REQ"+number);
                    attempt.confirmed(result(number==1?new String[]{"C"}:new String[0],selected.length,0,null));
                }
            });
        try { QdPartnerBatchChunks.combine(parts,CustomerVO.class,new QdPartnerBatchChunks.LogWriter() {
            public ErrLogResult write(List<String> messages) { throw new AssertionError("Unexpected log write"); }
        });throw new AssertionError("Result from another chunk accepted"); }
        catch(BusinessException expected) { }
        String description=QdPartnerBatchChunks.mergeFailure(parts,new BusinessException("simulated log failure")).getMessage();
        require(description.contains("3 个档案") && description.contains("REQ1") && description.contains("REQ2")
            && description.contains("请勿重复发起"),"Summary failure lost committed request identities");
    }
    private static void thresholdBoundary() throws Exception {
        final int maximum=PartnerBatchDraft.MAX_SINGLE_TRANSACTION_OBJECTS;
        String[] selected=new String[maximum+1];for(int i=0;i<selected.length;i++) selected[i]="M"+i;
        final List<Integer> sizes=new ArrayList<Integer>();
        List<QdPartnerBatchChunks.Completed> parts=QdPartnerBatchChunks.execute(selected,maximum,new QdPartnerBatchChunks.Operation() {
            public void execute(String[] values,int number,int total,QdPartnerBatchChunks.Attempt attempt) throws BusinessException {
                sizes.add(Integer.valueOf(values.length));attempt.dispatched("REQ"+number);
                attempt.confirmed(result(new String[0],values.length,0,null));
            }
        });
        require(sizes.equals(Arrays.asList(Integer.valueOf(maximum),Integer.valueOf(1))) && parts.size()==2,
            "Selection beyond the native threshold was not split exactly once");
        ErrLogReturnValue merged=QdPartnerBatchChunks.combine(parts,CustomerVO.class,new QdPartnerBatchChunks.LogWriter() {
            public ErrLogResult write(List<String> messages) { throw new AssertionError("A clean batch must not write an error log"); }
        });
        require(merged.getTotalNum()==maximum+1 && merged.getErrLogResult().getErrorMessagegNum()==0,
            "Threshold-spanning batch total was lost");
    }
    private static ErrLogReturnValue result(String[] pks,int total,int failures,String error) {
        SuperVO[] rows=new CustomerVO[pks.length];
        for(int i=0;i<pks.length;i++) { CustomerVO value=new CustomerVO();value.setPk_customer(pks[i]);rows[i]=value; }
        ErrorLogVO log=new ErrorLogVO();log.setOperationtype("快速批改");
        ErrorMsgVO[] messages=error==null?new ErrorMsgVO[0]:new ErrorMsgVO[]{message(error)};
        return new ErrLogReturnValue(new ErrLogResult(log,failures,messages),rows,total);
    }
    private static ErrLogResult persisted(List<String> messages) {
        ErrorLogVO log=new ErrorLogVO();log.setPk_errorlog("LOG");log.setOperationtype("快速批改");
        List<ErrorMsgVO> values=new ArrayList<ErrorMsgVO>();for(String text:messages) values.add(message(text));
        return new ErrLogResult(log,values.size(),values.toArray(new ErrorMsgVO[values.size()]));
    }
    private static ErrorMsgVO message(String text) { ErrorMsgVO row=new ErrorMsgVO();row.setErrormsg(text);return row; }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
