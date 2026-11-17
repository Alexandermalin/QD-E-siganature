package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.ui.ic.general.model.ICGenBizModel;
import nc.ui.ic.m4455.action.SapplyBillApproveAction;
import nc.ui.ic.m4455.action.SapplyBillUnApproveAction;
import nc.ui.ic.pub.env.ICUIContext;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.InventoryPfStateResult;
import nc.vo.ic.qd.esign.SapplyApprovalIntent;
import nc.vo.ic.qd.esign.SapplyPfBatchCommand;
import nc.vo.ic.qd.esign.SapplyPfBatchResult;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.SapplyBatchOutcome;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uapbd.qd.esign.SignatureResult;
import nc.vo.uapbd.qd.esign.SapplyBatchScope;
import nc.vo.uif2.LoginContext;

/** Checks approval-confirmation/signature order and protects the selected approval intent. */
public final class SapplyApprovalCoordinatorTests {
    private static final String USER_ID="1001Z810000000000001";
    private static final IBillMeta META=new IBillMeta(){
        public Map<String,Object> getBusinessAttribute(){return Collections.emptyMap();}
        public IAttributeMeta[] getChildForeignKeys(){return new IAttributeMeta[0];}
        public IVOMeta[] getChildren(){return new IVOMeta[0];}
        public String getComponentName(){return "TEST";}
        public IVOMeta getParent(){return null;}
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta){throw new UnsupportedOperationException();}
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type){throw new UnsupportedOperationException();}
    };
    public static void run() throws Exception {
        SapplyApprovalNativeContract.verify(SapplyBillApproveAction.class);
        SapplyApprovalNativeContract.verify(SapplyBillUnApproveAction.class);
        nativeBatchInput();
        nativePartialRefresh();
        for(boolean undo:new boolean[]{false,true})for(boolean sign:new boolean[]{false,true})for(boolean audit:new boolean[]{false,true}){
            Probe probe=new Probe(undo);probe.sign=sign;probe.audit=audit;probe.actionPerformed(probe.event);
            List<String> expected=new ArrayList<String>(Arrays.asList("original-before","policy"));
            if(sign||audit){
                expected.addAll(Arrays.asList("validate","approval-dialog","prepare"));
                if(sign)expected.add("signature-dialog");
                expected.addAll(Arrays.asList("gateway","completed","refresh"));
            }else expected.add("native");
            expected.add("original-success");
            require(probe.events.equals(expected),"Unexpected approval sequence: "+probe.events+"; error="+probe.failure
                    +"; cause="+(probe.failure==null?null:probe.failure.getCause()));
            require(probe.gatewayCalls==(sign||audit?1:0) && probe.getInterceptor()==probe.original,
                    "Approval dispatch or original interceptor differs");
        }
        Probe nativeCancel=new Probe();nativeCancel.sign=true;nativeCancel.cancelApproval=true;nativeCancel.actionPerformed(nativeCancel.event);
        require(nativeCancel.events.equals(Arrays.asList("original-before","policy","validate","approval-dialog","original-success"))
                && nativeCancel.gatewayCalls==0,"Cancelling the native dialog must not prepare a signature");
        Probe signCancel=new Probe();signCancel.sign=true;signCancel.cancelSignature=true;signCancel.actionPerformed(signCancel.event);
        require(signCancel.gatewayCalls==0 && signCancel.events.contains("cancel") && !signCancel.events.contains("gateway"),
                "Cancelling the signature must not execute approval");
        for(boolean appoint:new boolean[]{false,true}) for(int mode=1;mode<=3;mode++) {
            Probe adjusted=new Probe();adjusted.sign=(mode&1)!=0;adjusted.audit=(mode&2)!=0;
            nc.vo.wfengine.pub.WFTask task=new nc.vo.wfengine.pub.WFTask();
            task.setAddApprover(!appoint);task.setAppoint(appoint);adjusted.note.getTaskInfo().setTask(task);
            require(nc.ui.pf.clientutils.PfUtilClientAssistor.isAddApproverOrAppoint(adjusted.note),
                    "The fixture must match NC's native adjustment short circuit");
            adjusted.actionPerformed(adjusted.event);
            require(adjusted.events.equals(Arrays.asList("original-before","policy","validate","approval-dialog","refresh","original-success"))
                    && adjusted.gatewayCalls==0 && adjusted.getInterceptor()==adjusted.original,
                    "A native add-approver or appoint result must refresh without dispatching approval: "+adjusted.events);
        }
        for(boolean light:new boolean[]{false,true}) {
            Probe adjusted=new Probe();adjusted.audit=true;adjusted.useNativeRefresh=true;
            nc.vo.wfengine.pub.WFTask task=new nc.vo.wfengine.pub.WFTask();task.setAddApprover(true);
            adjusted.note.getTaskInfo().setTask(task);
            adjusted.setFullOldVOs(new SapplyBillVO[]{adjusted.originalBill});adjusted.setLightBillUsed(light);
            adjusted.model.setAppUiState(nc.ui.pubapp.uif2app.AppUiState.NOT_EDIT);
            adjusted.actionPerformed(adjusted.event);
            require(adjusted.refreshed instanceof SapplyBillVO[] && ((SapplyBillVO[])adjusted.refreshed).length==1
                    && ((SapplyBillVO[])adjusted.refreshed)[0]==adjusted.originalBill && adjusted.gatewayCalls==0,
                    "The original result must pass through NC's real single-bill refresh; error="+adjusted.failure);
        }
        Probe changed=new Probe();changed.sign=true;changed.changeSelection=true;changed.actionPerformed(changed.event);
        require(changed.gatewayCalls==0 && changed.events.contains("original-failed"),"Changed selection must block approval");
        Probe changedNote=new Probe();changedNote.sign=true;changedNote.changeNote=true;changedNote.actionPerformed(changedNote.event);
        require(changedNote.gatewayCalls==0 && changedNote.events.contains("original-failed"),"Changed approval intent must block dispatch");
        Probe denied=new Probe();denied.original.accept=false;denied.actionPerformed(denied.event);
        require(denied.events.equals(Collections.singletonList("original-before")),"Original interceptor must run first");
        try{denied.doAction(denied.event);throw new AssertionError("Direct approval was accepted");}catch(BusinessException expected) { }
        try{denied.procFlow(denied.getFlowContext());throw new AssertionError("Direct approval flow was accepted");}catch(BusinessException expected) { }
        for(boolean undo:new boolean[]{false,true})for(boolean sign:new boolean[]{false,true})for(boolean audit:new boolean[]{false,true}){
            Probe multi=new Probe(undo);multi.sign=sign;multi.audit=audit;multi.multiple=true;multi.actionPerformed(multi.event);
            List<String> expected=new ArrayList<String>(Arrays.asList("original-before","policy"));
            if(sign||audit){expected.addAll(Arrays.asList("validate","batch-dialog","prepare"));
                if(sign)expected.add("signature-dialog");expected.addAll(Arrays.asList("gateway","batch-refresh"));}
            else expected.add("native");
            expected.add("original-success");
            require(multi.events.equals(expected) && multi.gatewayCalls==(sign||audit?1:0),
                    "Unexpected batch approval sequence: "+multi.events+"; error="+multi.failure);
        }
        Probe multiCancel=new Probe();multiCancel.sign=true;multiCancel.multiple=true;multiCancel.cancelApproval=true;
        multiCancel.actionPerformed(multiCancel.event);
        require(multiCancel.gatewayCalls==0 && !multiCancel.events.contains("prepare"),"Cancelled batch approval must not sign");
        Probe multiSignCancel=new Probe();multiSignCancel.sign=true;multiSignCancel.multiple=true;multiSignCancel.cancelSignature=true;
        multiSignCancel.actionPerformed(multiSignCancel.event);
        require(multiSignCancel.gatewayCalls==0 && multiSignCancel.events.contains("cancel"),"Cancelled batch signature must not execute");
        Probe multiChanged=new Probe();multiChanged.sign=true;multiChanged.multiple=true;multiChanged.changeNote=true;
        multiChanged.actionPerformed(multiChanged.event);
        require(multiChanged.gatewayCalls==0 && multiChanged.events.contains("original-failed"),"Changed batch opinion must not execute");
        Probe multiSelection=new Probe();multiSelection.sign=true;multiSelection.multiple=true;multiSelection.changeSelection=true;
        multiSelection.actionPerformed(multiSelection.event);
        require(multiSelection.gatewayCalls==0 && multiSelection.events.contains("original-failed"),"Changed batch selection must not execute");
        for(boolean undo:new boolean[]{false,true})for(boolean sign:new boolean[]{false,true})for(boolean audit:new boolean[]{false,true}){
            Probe partial=new Probe(undo);partial.sign=sign;partial.audit=audit;partial.multiple=true;partial.skipOne=true;
            partial.actionPerformed(partial.event);
            require(partial.gatewayCalls==(sign||audit?1:0),"Precheck partial selection preserves the switch behavior");
        }
        Probe changedSkip=new Probe();changedSkip.sign=true;changedSkip.multiple=true;changedSkip.skipOne=true;changedSkip.tamperSkip=true;
        changedSkip.actionPerformed(changedSkip.event);
        require(changedSkip.gatewayCalls==0 && changedSkip.events.contains("original-failed"),
                "A changed precheck exclusion must fail after signing and before dispatch");
        Probe mixed=new Probe();mixed.audit=true;mixed.multiple=true;mixed.mixedOrg=true;mixed.actionPerformed(mixed.event);
        require(mixed.gatewayCalls==0 && mixed.events.contains("original-failed"),"Mixed organizations cannot share a signed batch");
        Probe wrongActor=new Probe();wrongActor.sign=true;wrongActor.multiple=true;wrongActor.wrongActor=true;wrongActor.actionPerformed(wrongActor.event);
        require(wrongActor.gatewayCalls==0 && !wrongActor.events.contains("batch-dialog") && wrongActor.events.contains("original-failed"),
                "A different workflow actor must fail before approval confirmation");
        Probe incompatible=new Probe();incompatible.audit=true;incompatible.unsupportedNative=true;incompatible.actionPerformed(incompatible.event);
        require(incompatible.gatewayCalls==0 && incompatible.events.contains("original-failed")
                && !incompatible.events.contains("approval-dialog"),"Unverified native approval version must fail before confirmation");
        for(boolean undo:new boolean[]{false,true})for(boolean injectedDuringFill:new boolean[]{false,true}) {
            Probe beside=new Probe(undo);beside.audit=true;beside.besideDuringFill=injectedDuringFill;
            if(!injectedDuringFill)beside.getFlowContext().getEParam().put("besideapprove",Boolean.FALSE);
            beside.actionPerformed(beside.event);
            require(beside.gatewayCalls==0 && beside.events.contains("original-failed")
                    && !beside.events.contains("approval-dialog") && !beside.events.contains("prepare"),
                    "Non-null besideapprove must be rejected before native approval preparation");
        }
        Probe preparedBeside=new Probe();preparedBeside.audit=true;preparedBeside.preparedBesidePresent=true;
        preparedBeside.preparedBeside=Boolean.FALSE;preparedBeside.actionPerformed(preparedBeside.event);
        require(preparedBeside.gatewayCalls==0 && preparedBeside.events.contains("original-failed")
                && !preparedBeside.events.contains("prepare"),"Non-null prepared side context must stop before signing");
        Probe preparedNull=new Probe();preparedNull.audit=true;preparedNull.preparedBesidePresent=true;
        preparedNull.actionPerformed(preparedNull.event);
        require(preparedNull.gatewayCalls==1 && preparedNull.events.contains("original-success"),
                "A null side context must be removed before creating the signed command");
        Probe bypass=new Probe();bypass.unsupportedNative=true;bypass.actionPerformed(bypass.event);
        require(bypass.events.equals(Arrays.asList("original-before","policy","native","original-success")),
                "Disabled approval policy must preserve the native flow");
        Probe nativeBeside=new Probe();nativeBeside.getFlowContext().getEParam().put("besideapprove",Boolean.FALSE);
        nativeBeside.actionPerformed(nativeBeside.event);
        require(nativeBeside.events.equals(Arrays.asList("original-before","policy","native","original-success")),
                "Uncontrolled approval must leave the original native flow unchanged");
        System.out.println("PASS SapplyApprovalCoordinatorTests (approval confirmation, signing and intent binding; no NC session)");
    }
    private static void nativeBatchInput() throws Exception {
        final Throwable[] failure=new Throwable[1];
        javax.swing.SwingUtilities.invokeAndWait(new Runnable(){public void run(){try{
            java.lang.reflect.Method require=SapplyBatchClientPreparation.class.getDeclaredMethod("requireInput",
                    PFlowContext.class,String.class,String.class,SapplyBillVO[].class,String.class);
            require.setAccessible(true);
            PFlowContext flow=new PFlowContext();SapplyBillVO[] bills={bill("BILL"),bill("OTHER")};
            flow.setBillVos(bills);flow.setTrantype("4455");flow.setUserId(USER_ID);
            for(String base:new String[]{"APPROVE","UNAPPROVE"}){
                flow.setActionName(base);
                require.invoke(null,flow,base+USER_ID,"4455",bills,USER_ID);
                try{require.invoke(null,flow,base+"OTHER","4455",bills,USER_ID);
                    throw new AssertionError("A different native batch actor was accepted");
                }catch(java.lang.reflect.InvocationTargetException expected){
                    if(!(expected.getCause() instanceof BusinessException))throw expected;
                }
            }
            Map<Integer,String> errors=flow.getExceptionResults();
            errors.put(Integer.valueOf(1),"native precheck failed");
            errors.put(Integer.valueOf(0),null);
            require(Arrays.equals(new String[]{"OTHER"},SapplyBatchClientPreparation.precheckSkippedPks(flow,bills)),
                    "Only the native precheck failure is excluded");
            require.invoke(null,flow,"UNAPPROVE"+USER_ID,"4455",bills,USER_ID);
            errors.put(Integer.valueOf(2),"out of range");
            try{SapplyBatchClientPreparation.precheckSkippedPks(flow,bills);
                throw new AssertionError("An invalid native precheck position was accepted");
            }catch(BusinessException expected) { }
            Probe probe=new Probe();probe.setFlowContext(flow);errors.remove(Integer.valueOf(2));
            errors.put(Integer.valueOf(1),"状态不允许\n请检查单据");
            Map<String,String> reasons=SapplyBatchClientResult.precheckMessages(probe,bills);
            BusinessObjectRef first=new BusinessObjectRef("one","IC_SAPPLY","BILL","A01",null);
            BusinessObjectRef second=new BusinessObjectRef("two","IC_SAPPLY","OTHER","B02",null);
            SapplyBatchOutcome outcome=new SapplyBatchOutcome("R","PARTIAL",Arrays.asList(
                    new SapplyBatchOutcome.Unit(first,"SUCCESS",null),
                    new SapplyBatchOutcome.Unit(second,"FAILED","CLIENT_PRECHECK_SKIPPED")));
            String summary=SapplyBatchClientResult.message(new SapplyPfBatchResult(outcome,null,false),false,reasons);
            require(summary.contains("A01  成功") && summary.contains("B02  未提交：原生预校验未通过（状态不允许 请检查单据）"),
                    "A partial result must show the original native precheck reason next to the skipped bill");
            nc.vo.uap.pf.PFBatchExceptionInfo nativeErrors=new nc.vo.uap.pf.PFBatchExceptionInfo();
            SapplyBatchOutcome uncertain=new SapplyBatchOutcome("R","PARTIAL",Arrays.asList(
                    new SapplyBatchOutcome.Unit(first,"UNKNOWN","BUSINESS_OUTCOME_UNKNOWN"),
                    new SapplyBatchOutcome.Unit(second,"FAILED","CLIENT_PRECHECK_SKIPPED")));
            SapplyPfBatchResult nativeResult=new SapplyPfBatchResult(uncertain,
                    new nc.vo.uap.pf.PfProcessBatchRetObject(new Object[0],nativeErrors),false);
            require(!SapplyBatchClientResult.message(nativeResult,false,reasons).contains("NC 原生批次报告异常"),
                    "An empty native error map must not produce an error warning");
            for(String nativeError:new String[]{null,"","native error"}) {
                nativeErrors.putErrorMessage(0,bills[0],nativeError);
                String notice=SapplyBatchClientResult.message(nativeResult,false,reasons);
                require(notice.contains("NC 原生批次报告异常") && notice.contains("A01  结果待核对")
                        && !notice.contains("A01  失败"),
                        "A null or empty native error must be shown without claiming a rolled-back outcome");
            }
            errors.put(Integer.valueOf(0),"第一张原因\r\n请检查");
            try {
                SapplyBatchClientPreparation.prepare(flow,"UNAPPROVE"+USER_ID,"4455",bills,USER_ID);
                throw new AssertionError("A fully rejected batch was accepted");
            } catch(BusinessException expected) {
                require(expected.getMessage().contains("第一张原因  请检查")
                        && expected.getMessage().contains("状态不允许 请检查单据"),
                        "A fully rejected batch must explain its native precheck failures");
            }
            SapplyBillVO[] six={bill("B1"),bill("B2"),bill("B3"),bill("B4"),bill("B5"),bill("B6")};
            flow.setBillVos(six);errors=flow.getExceptionResults();errors.clear();
            String longReason=new String(new char[300]).replace('\0','X');
            for(int index=0;index<six.length;index++)
                errors.put(Integer.valueOf(index),index==0?longReason:"第"+index+"张原因");
            try {
                SapplyBatchClientPreparation.prepare(flow,"UNAPPROVE"+USER_ID,"4455",six,USER_ID);
                throw new AssertionError("A fully rejected six-bill batch was accepted");
            } catch(BusinessException expected) {
                require(expected.getMessage().contains("另有 1 张未通过原生校验")
                        && !expected.getMessage().contains("第5张原因")
                        && expected.getMessage().length()<1000,
                        "The failure summary must bound long reasons and omit additional bills");
            }
        }catch(Throwable error){failure[0]=error;}}});
        if(failure[0]!=null)throw new AssertionError(failure[0]);
    }
    /** Exercise NC's real light-VO merge, including a non-prefix, reversed partial result. */
    private static void nativePartialRefresh() throws Exception {
        final Throwable[] failure=new Throwable[1];
        javax.swing.SwingUtilities.invokeAndWait(new Runnable(){public void run(){try{
            java.lang.reflect.Method refresh=SapplyBatchClientResult.class.getDeclaredMethod("refreshReturned",
                    AbstractScriptExcAction.class,List.class,boolean.class);
            refresh.setAccessible(true);
            RefreshAction action=new RefreshAction();
            final Object[] updated=new Object[1];
            ICGenBizModel model=new ICGenBizModel(){public void directlyUpdate(Object value){updated[0]=value;}};
            model.setIcUIContext(new ICUIContext(new LoginContext()));
            model.setAppUiState(nc.ui.pubapp.uif2app.AppUiState.NOT_EDIT);
            action.setModel(model);action.setFlowContext(new PFlowContext());action.setLightBillUsed(true);
            SapplyBillVO a=refreshBill("A","old-A"),b=refreshBill("B","old-B"),c=refreshBill("C","old-C");
            SapplyBillVO[] originals={a,b,c};action.setFullOldVOs(originals);
            action.getFlowContext().setBillVos(originals);
            action.getMultibillScriptRunner().setTaskSuccessful(true);
            boolean refreshed=(Boolean)refresh.invoke(null,action,Arrays.asList(refreshBill("C","new-C"),
                    refreshBill("A","new-A")),false);
            require(refreshed && updated[0] instanceof SapplyBillVO[],"The native partial refresh must run");
            SapplyBillVO[] changed=(SapplyBillVO[])updated[0];
            require(changed.length==2 && changed[0]==c && changed[1]==a
                    && "new-A".equals(a.getHead().getVnote()) && "old-B".equals(b.getHead().getVnote())
                    && "new-C".equals(c.getHead().getVnote()),"Returned light VOs must merge by bill key");
            require(action.getFullOldVOs()==originals && action.getMultibillScriptRunner().isTaskSuccessful(),
                    "The original selection and native runner state must be restored");
            updated[0]=null;
            require(!((Boolean)refresh.invoke(null,action,Arrays.asList(refreshBill("C","x"),refreshBill("C","y")),false))
                    && updated[0]==null,"Duplicate returned keys must require a requery");
            require(!((Boolean)refresh.invoke(null,action,Arrays.asList(refreshBill("UNKNOWN","x")),false))
                    && updated[0]==null,"An unmatched returned key must require a requery");
        }catch(Throwable error){failure[0]=error;}}});
        if(failure[0]!=null)throw new AssertionError(failure[0]);
    }
    private static final class RefreshAction extends nc.ui.pubapp.uif2app.actions.pflow.ScriptPFlowAction {
        protected void showMsginfo(Object[] results) { }
    }
    private static SapplyBillVO refreshBill(String pk,String note) {
        SapplyBillVO bill=new SapplyBillVO(){public IBillMeta getMetaData(){return REFRESH_META;}};
        SapplyBillHeadVO head=new RefreshHead();head.setCgeneralhid(pk);head.setVnote(note);bill.setParent(head);return bill;
    }
    private static final class RefreshHead extends SapplyBillHeadVO {
        private final Map<String,Object> values=new HashMap<String,Object>();
        public IVOMeta getMetaData(){return null;}
        public Object getAttributeValue(String name){return values.get(name);}
        public void setAttributeValue(String name,Object value){values.put(name,value);}
        public java.util.Set<String> usedAttributeNames(){return values.keySet();}
    }
    private static final IVOMeta REFRESH_PARENT=(IVOMeta)java.lang.reflect.Proxy.newProxyInstance(
            IVOMeta.class.getClassLoader(),new Class<?>[]{IVOMeta.class},new java.lang.reflect.InvocationHandler(){
                public Object invoke(Object proxy,java.lang.reflect.Method method,Object[] args){throw new AssertionError(method.getName());}
            });
    private static final IBillMeta REFRESH_META=new IBillMeta(){
        public Map<String,Object> getBusinessAttribute(){return Collections.emptyMap();}
        public IAttributeMeta[] getChildForeignKeys(){return new IAttributeMeta[0];}
        public IVOMeta[] getChildren(){return new IVOMeta[0];}
        public String getComponentName(){return "TEST";}
        public IVOMeta getParent(){return REFRESH_PARENT;}
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta){throw new UnsupportedOperationException();}
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type){throw new UnsupportedOperationException();}
    };
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class Probe extends SapplyBillApproveAction {
        final List<String> events=new ArrayList<String>();final ActionEvent event=new ActionEvent(this,1,"approve");
        final SapplyBillVO originalBill=bill("BILL"),otherBill=bill("OTHER"),thirdBill=bill("THIRD"),otherOrgBill=bill("OTHER","O2");
        Object refreshed;
        final ICGenBizModel model=new ICGenBizModel(){public Object[] getSelectedOperaDatas(){return multiple
                ?new SapplyBillVO[]{originalBill,changed?thirdBill:mixedOrg?otherOrgBill:otherBill}:new SapplyBillVO[]{changed?otherBill:originalBill};}
            public void directlyUpdate(Object value){refreshed=value;}};
        final Original original=new Original();final Service service=new Service();final WorkflownoteVO note=new WorkflownoteVO();
        final SapplyApprovalCoordinator coordinator;final boolean undo;
        boolean sign,audit,cancelApproval,cancelSignature,changeSelection,changeNote,changed,multiple,mixedOrg,wrongActor,unsupportedNative,besideDuringFill,preparedBesidePresent,skipOne,tamperSkip,useNativeRefresh;
        SapplyPfBatchCommand preparedBatch;
        Object preparedBeside;int gatewayCalls;Throwable failure;
        Probe(){this(false);}
        Probe(boolean undo){
            this.undo=undo;
            model.setIcUIContext(new ICUIContext(new LoginContext()));model.getContext().setNodeCode("40080801");model.getContext().setPk_group("G");model.getContext().setPk_loginUser(USER_ID);
            setModel(model);setFlowContext(new PFlowContext());setInterceptor(original);
            note.setBillid("BILL");note.setPk_group("G");note.setPk_org("O");note.setChecknote("agree");
            coordinator=new SapplyApprovalCoordinator(new SapplyApprovalCoordinator.Host(){
                public AbstractScriptExcAction action(){return Probe.this;}public BillManageModel model(){return model;}
                public Class<?> nativeClass(){return Probe.this.undo?SapplyBillUnApproveAction.class:SapplyBillApproveAction.class;}public boolean isComposite(){return false;}
                public void performNative(ActionEvent e){Probe.super.actionPerformed(e);}
                public void runNative(ActionEvent e) throws Exception {coordinator.procFlow(getFlowContext());}
                public void fillNative(PFlowContext flow){flow.setActionName(Probe.this.undo?"UNAPPROVE":"APPROVE");flow.setTrantype("4455");flow.setBatch(multiple);
                    flow.setUserId(wrongActor?"OTHER":USER_ID);
                    flow.setBillVos(multiple?new SapplyBillVO[]{originalBill,mixedOrg?otherOrgBill:otherBill}:new SapplyBillVO[]{originalBill});
                    if(besideDuringFill)flow.getEParam().put("besideapprove",Boolean.FALSE);}
                public void nativeFlow(PFlowContext flow){events.add("native");}
            },undo?"UnApprove":"Approve",undo?"取消审批":"审批确认",undo?"UNAPPROVE":"APPROVE",new SapplyApprovalCoordinator.Preparation(){
                public void verify(Class<?> type) throws BusinessException {if(unsupportedNative)throw new BusinessException("native version differs");}
                public void validate(AbstractScriptExcAction action){events.add("validate");}
                public SapplyApprovalCoordinator.Prepared prepare(Container parent,String action,String type,AggregatedValueObject bill,Map<?,?> params){
                    require(((Probe.this.undo?"UNAPPROVE":"APPROVE")+USER_ID).equals(action),"Native approval actor must be preserved");
                    events.add("approval-dialog");return new SapplyApprovalCoordinator.Prepared(){
                        public boolean accepted(){return !cancelApproval;}public WorkflownoteVO note(){return note;}
                        public Map<String,Object> parameters(){Map<String,Object> values=new HashMap<String,Object>();
                            if(preparedBesidePresent)values.put("besideapprove",preparedBeside);return values;}
                        public void completed(){events.add("completed");}};
                }
            },new SapplyApprovalCoordinator.PolicyLookup(){public PolicyDecision resolve(String org,String node,String button){
                events.add("policy");return new PolicyDecision(true,sign,false,audit,true,1);}},
            new SapplyApprovalCoordinator.Gateway(){public OperationResponse execute(String request,Serializable value) throws BusinessException {
                events.add("gateway");gatewayCalls++;
                if(value instanceof SapplyPfBatchCommand){SapplyPfBatchCommand batch=(SapplyPfBatchCommand)value;
                    String shared=SapplyApprovalIntent.batchSharedFingerprint(batch.getWorkflowNote(),batch.getParameters(),batch.getPrecheckSkippedPks());
                    for(int index=0;index<batch.getBills().length;index++){
                        require(service.prepared.getObjects().get(index).getClientObjectId().equals(
                                SapplyApprovalIntent.batchClientObjectId(Probe.this.undo?"UnApprove":"Approve",batch.getBillType(),
                                    batch.getBills()[index],null,shared)),"Batch payload differs from signature");}
                    return new OperationResponse(request,false,null,service.prepared.getObjects());}
                InventoryPfStateCommand command=(InventoryPfStateCommand)value;
                if(preparedBesidePresent && preparedBeside==null)
                    require(!command.getParameters().containsKey("besideapprove"),"Null side context must not reach the server");
                require(service.prepared.getObjects().get(0).getClientObjectId().equals(SapplyApprovalIntent.clientObjectId(Probe.this.undo?"UnApprove":"Approve",command)),
                        "Gateway command differs from signed approval");
                return new OperationResponse(request,false,new InventoryPfStateResult(null,"BILL","CODE",false),service.prepared.getObjects());
            }},new SapplyApprovalCoordinator.SigningFactory(){public ElectronicSignatureActionInterceptor create(){return new Signer();}},
            new SapplyApprovalCoordinator.BatchPreparation(){
                public SapplyPfBatchCommand prepare(AbstractScriptExcAction action,PFlowContext flow,String nativeAction,
                        String type,SapplyBillVO[] bills,String verifiedUser){events.add("batch-dialog");if(cancelApproval)return null;
                    require(((Probe.this.undo?"UNAPPROVE":"APPROVE")+USER_ID).equals(nativeAction) && USER_ID.equals(verifiedUser),
                            "Native batch approval actor must be preserved");
                    Map<String,Object> params=new HashMap<String,Object>();params.put("batch","batch");params.put("worknote",null);
                    preparedBatch=new SapplyPfBatchCommand(nativeAction,type,bills,null,Probe.this.undo?null:note,params,
                            skipOne?new String[]{"OTHER"}:null);return preparedBatch;}
                public void apply(AbstractScriptExcAction action,BillManageModel model,Container parent,OperationContext authorized,
                        OperationResponse response,SapplyBillVO[] bills){require(response!=null && "R".equals(response.getRequestId()),"Batch response missing");events.add("batch-refresh");}
            });
        }
        @Override public void actionPerformed(ActionEvent e){coordinator.perform(e);}
        @Override public void doAction(ActionEvent e) throws Exception {coordinator.doAction(e);}
        @Override public void procFlow(PFlowContext flow) throws Exception {coordinator.procFlow(flow);}
        @Override public void processReturnObjs(Object[] values){if(useNativeRefresh)super.processReturnObjs(values);events.add("refresh");}
        @Override protected void showMsginfo(Object[] values) { }
        private final class Signer extends ElectronicSignatureActionInterceptor {
            Signer(){setSignatureService(service);}
            @Override protected SignatureDialogOutcome showSignatureDialog(SignaturePreparation value,RequestCancellation cancellation){
                events.add("signature-dialog");if(changeSelection)changed=true;if(changeNote)note.setChecknote("changed");
                if(tamperSkip)try{java.lang.reflect.Field field=SapplyPfBatchCommand.class.getDeclaredField("precheckSkippedPks");
                    field.setAccessible(true);field.set(preparedBatch,new String[]{"BILL"});}
                catch(Exception error){throw new AssertionError(error);}
                return cancelSignature?new SignatureDialogOutcome(null,null,null)
                        :new SignatureDialogOutcome(new SignatureResult("R","SUCCESS",false,"READY"),null,null);
            }
            @Override protected void reportFailure(Throwable failure){events.add("sign-error");}
        }
        private final class Service implements ISignatureService {
            OperationContext prepared;
            public SignaturePreparation prepare(OperationContext draft){events.add("prepare");
                PolicyDecision policy=new PolicyDecision(true,sign,false,audit,true,1);
                prepared=draft.getObjects().size()==1?draft.withRequest("R","B","G"):
                    new OperationContext("R","B","G",draft.getOrgId(),draft.getNodeCode(),draft.getNodeName(),draft.getButtonCode(),
                            draft.getButtonName(),draft.getOperationType(),draft.getReason(),draft.getObjects(),SapplyBatchScope.POLICY_MODE);
                return new SignaturePreparation(prepared,policy,"PENDING");}
            public SignatureResult confirm(String id,char[] password,String reason){throw new AssertionError("No password UI");}
            public void cancel(String id,String reason){events.add("cancel");}
            public void reportBusinessFailure(String id,String code){events.add("failure");}
            public SignatureResult getResult(String id){return new SignatureResult(id,"SUCCESS",false,"READY");}
        }
        private final class Original implements ActionInterceptor {
            boolean accept=true;
            public boolean beforeDoAction(Action action,ActionEvent e){events.add("original-before");return accept;}
            public void afterDoActionSuccessed(Action action,ActionEvent e){events.add("original-success");}
            public boolean afterDoActionFailed(Action action,ActionEvent e,Throwable error){events.add("original-failed");failure=error;return false;}
        }
    }
    private static SapplyBillVO bill(final String pk){return bill(pk,"O");}
    private static SapplyBillVO bill(final String pk,final String org){
        final SapplyBillHeadVO head=new SapplyBillHeadVO(){public String getCgeneralhid(){return pk;}public String getPk_group(){return "G";}
            public String getPk_org(){return org;}public String getVbillcode(){return "CODE";}public IVOMeta getMetaData(){return null;}
            public java.util.Set<String> usedAttributeNames(){return Collections.singleton("pk");}
            public Object getAttributeValue(String name){return "pk".equals(name)?pk:null;}};
        return new SapplyBillVO(){public IBillMeta getMetaData(){return META;}public SapplyBillHeadVO getHead(){return head;}};
    }
}
