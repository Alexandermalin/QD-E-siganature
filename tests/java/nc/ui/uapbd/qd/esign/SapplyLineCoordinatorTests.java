package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.ui.ic.general.model.ICGenBizModel;
import nc.ui.ic.m4455.action.RowCloseAction;
import nc.ui.ic.pub.env.ICUIContext;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.ic.m4455.entity.SapplyBillBodyVO;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.qd.esign.SapplyLineCommand;
import nc.vo.ic.qd.esign.SapplyLineResult;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uapbd.qd.esign.SignatureResult;
import nc.vo.uif2.LoginContext;

/** Exercises native callbacks, selection rechecks and all four policy combinations without an NC session. */
public final class SapplyLineCoordinatorTests {
    private static final IBillMeta META=new IBillMeta(){
        public Map<String,Object> getBusinessAttribute(){return Collections.emptyMap();}
        public IAttributeMeta[] getChildForeignKeys(){return new IAttributeMeta[0];}
        public IVOMeta[] getChildren(){return new IVOMeta[0];}
        public String getComponentName(){return "TEST";}
        public IVOMeta getParent(){throw new UnsupportedOperationException();}
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta){throw new UnsupportedOperationException();}
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type){throw new UnsupportedOperationException();}
    };
    public static void run() throws Exception {
        for(boolean sign:new boolean[]{false,true})for(boolean audit:new boolean[]{false,true}){
            Probe probe=new Probe();probe.sign=sign;probe.audit=audit;probe.actionPerformed(probe.event);
            List<String> expected=new ArrayList<String>(Arrays.asList("original-before","select","prepare"));
            if(sign)expected.add("dialog");
            if(sign||audit)expected.addAll(Arrays.asList("reselect","gateway","refresh"));else expected.add("native");
            expected.add("original-success");
            require(probe.events.equals(expected),"Unexpected line policy sequence: "+probe.events);
            require(probe.getInterceptor()==probe.original && probe.gatewayCalls==(sign||audit?1:0),"Original action chain or dispatch differs");
        }
        Probe cancelled=new Probe();cancelled.sign=true;cancelled.cancel=true;cancelled.actionPerformed(cancelled.event);
        require(cancelled.events.equals(Arrays.asList("original-before","select","prepare","dialog","cancel","original-success"))
                && cancelled.gatewayCalls==0,"Cancelled signature must not execute a row action");
        Probe changed=new Probe();changed.sign=true;changed.changeDuringDialog=true;changed.actionPerformed(changed.event);
        require(changed.gatewayCalls==0 && changed.events.contains("failure") && changed.events.contains("original-failed"),
                "A changed line selection must reject the authorized dispatch");
        Probe denied=new Probe();denied.original.accept=false;denied.actionPerformed(denied.event);
        require(denied.events.equals(Collections.singletonList("original-before")),"Native interceptor denial must precede signing");
        Probe empty=new Probe();empty.empty=true;empty.actionPerformed(empty.event);
        require(empty.events.equals(Arrays.asList("original-before","select","original-success")),"No selection must not prepare a signature");
        Probe reentrant=new Probe();reentrant.original.reenter=true;reentrant.actionPerformed(reentrant.event);
        require(reentrant.events.contains("reentry-rejected") && reentrant.events.contains("native"),"Outer callback reentry must be rejected");
        try{reentrant.doAction(reentrant.event);throw new AssertionError("Direct row action was accepted");}
        catch(BusinessException expected) { }
        Probe failing=new Probe();failing.audit=true;failing.failGateway=true;failing.actionPerformed(failing.event);
        require(failing.gatewayCalls==1 && failing.events.contains("failure") && failing.events.contains("original-failed")
                && !failing.events.contains("refresh"),"Gateway failure must preserve native failure handling");
        require(failing.original.failure!=null && failing.original.failure.getMessage().contains("请求号")
                && failing.original.failure.getMessage().contains("R")
                && failing.original.failure.getMessage().contains("重新查询")
                && failing.original.failure.getCause() instanceof BusinessException,
                "An unconfirmed gateway outcome must include the request ID and retain the original failure");
        Probe budget=new Probe();budget.audit=true;budget.failBudget=true;budget.actionPerformed(budget.event);
        require(budget.gatewayCalls==1 && !budget.events.contains("refresh") && budget.original.failure!=null
                && budget.original.failure.getMessage().contains("预算金额不足")
                && budget.original.failure.getMessage().contains("请求号")
                && budget.original.failure.getMessage().contains("重新查询")
                && budget.original.failure.getCause() instanceof BusinessException,
                "A wrapped budget alert must retain its useful message and remain an unconfirmed single attempt");
        Probe otherResume=new Probe();otherResume.audit=true;otherResume.failOtherResume=true;
        otherResume.actionPerformed(otherResume.event);
        require(otherResume.gatewayCalls==1 && otherResume.original.failure!=null
                && otherResume.original.failure.getMessage().contains("other wrapper")
                && !otherResume.original.failure.getMessage().contains("other native detail"),
                "Other resumable exceptions must not be identified as budget alerts");
        Probe cycle=new Probe();cycle.audit=true;cycle.failCyclicCause=true;cycle.actionPerformed(cycle.event);
        require(cycle.gatewayCalls==1 && cycle.original.failure!=null
                && cycle.original.failure.getMessage().contains("cycle wrapper"),
                "A cyclic cause chain must retain the normal unknown-outcome message");
        Probe network=new Probe();network.audit=true;network.failRuntime=true;network.actionPerformed(network.event);
        require(network.gatewayCalls==1 && !network.events.contains("refresh") && network.original.failure!=null
                && network.original.failure.getMessage().contains("请求号") && network.original.failure.getMessage().contains("R")
                && network.original.failure.getCause() instanceof IllegalStateException,
                "A runtime transport failure after dispatch must include the request ID and retain its cause");
        Probe malformed=new Probe();malformed.audit=true;malformed.invalidResponse=true;malformed.actionPerformed(malformed.event);
        require(malformed.gatewayCalls==1 && !malformed.events.contains("refresh") && malformed.original.failure!=null
                && malformed.original.failure.getMessage().contains("请求号") && malformed.original.failure.getMessage().contains("R"),
                "An invalid gateway response must include the request ID for reconciliation");
        Probe refreshFailure=new Probe();refreshFailure.audit=true;refreshFailure.failRefresh=true;refreshFailure.actionPerformed(refreshFailure.event);
        require(refreshFailure.gatewayCalls==1 && refreshFailure.original.failure!=null
                && refreshFailure.original.failure.getMessage().contains("请求号")
                && refreshFailure.original.failure.getMessage().contains("R")
                && refreshFailure.original.failure.getCause() instanceof IllegalStateException,
                "A confirmed gateway result followed by refresh failure must retain the request ID and cause");
        System.out.println("PASS SapplyLineCoordinatorTests (callbacks, four policy combinations and row recheck; no NC session)");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class Probe extends NCAction {
        final List<String> events=new ArrayList<String>();final ActionEvent event=new ActionEvent(this,1,"line");
        final ICGenBizModel model=new ICGenBizModel();final Original original=new Original();final Service service=new Service();
        final SapplyLineCoordinator coordinator;boolean sign,audit,cancel,empty,changeDuringDialog,changed,failGateway,failBudget,failOtherResume,failCyclicCause,failRuntime,invalidResponse,failRefresh;int gatewayCalls;
        Probe(){
            model.setIcUIContext(new ICUIContext(new LoginContext()));model.getContext().setNodeCode("40080801");model.getContext().setPk_group("G");
            setInterceptor(original);
            coordinator=new SapplyLineCoordinator(new SapplyLineCoordinator.Host(){
                public NCAction action(){return Probe.this;}public BillManageModel model(){return model;}
                public Class<?> nativeClass(){return RowCloseAction.class;}
                public void performNative(ActionEvent e){Probe.super.actionPerformed(e);}
                public void runNative(ActionEvent e){events.add("native");}
            },"LineClose","行关闭",new SapplyLineCoordinator.SelectionReader(){
                public void verify(Class<?> type){}
                public SapplyLineNativeBridge.Selection select(NCAction action,Class<?> type,BillManageModel value,boolean hint){
                    events.add(hint?"select":"reselect");if(empty)return null;
                    SapplyBillVO bill=bill(changed?"OTHER":"L1");return new SapplyLineNativeBridge.Selection(bill,bill);
                }
            },new SapplyLineCoordinator.Gateway(){
                public OperationResponse execute(String request,SapplyLineCommand command) throws BusinessException {
                    events.add("gateway");gatewayCalls++;
                    require("R".equals(request)&&!command.isResumeBudget(),"Wrong line dispatch");
                    if(failGateway)throw new BusinessException("fixture failure");
                    if(failBudget)throw new BusinessException("framework wrapper",new IllegalStateException(new BudgetAlert()));
                    if(failOtherResume)throw new BusinessException("other wrapper",new OtherResume());
                    if(failCyclicCause) {
                        IllegalStateException first=new IllegalStateException("first"),second=new IllegalStateException("second");
                        first.initCause(second);second.initCause(first);throw new BusinessException("cycle wrapper",first);
                    }
                    if(failRuntime)throw new IllegalStateException("transport failure");
                    if(invalidResponse)return null;
                    return new OperationResponse(request,false,new SapplyLineResult("BILL","CODE",new SapplyBillVO[]{bill("L1")}),service.prepared.getObjects());
                }
            },new SapplyLineCoordinator.SigningFactory(){public ElectronicSignatureActionInterceptor create(){return new Signer();}},
            new SapplyLineCoordinator.Refresh(){public void combine(BillManageModel value,SapplyBillVO old,SapplyBillVO[] returned){
                events.add("refresh");if(failRefresh)throw new IllegalStateException("refresh failure");
            }});
        }
        @Override public void actionPerformed(ActionEvent e){coordinator.perform(e);}
        @Override public void doAction(ActionEvent e) throws Exception {coordinator.doAction(e);}
        private final class Signer extends ElectronicSignatureActionInterceptor {
            Signer(){setSignatureService(service);}
            @Override protected SignatureDialogOutcome showSignatureDialog(SignaturePreparation prepared,RequestCancellation cancellation){
                events.add("dialog");if(changeDuringDialog)changed=true;
                return cancel?new SignatureDialogOutcome(null,null,null)
                    :new SignatureDialogOutcome(new SignatureResult("R","SUCCESS",false,"READY"),null,null);
            }
            @Override protected void reportFailure(Throwable error){events.add("sign-error");}
        }
        private final class Service implements ISignatureService {
            OperationContext prepared;
            public SignaturePreparation prepare(OperationContext draft){
                events.add("prepare");PolicyDecision policy=new PolicyDecision(true,sign,false,audit,true,1);
                prepared=policy.isControlled()?draft.withRequest("R","B","G"):draft;
                return new SignaturePreparation(prepared,policy,policy.isControlled()?"PENDING":"BYPASS");
            }
            public SignatureResult confirm(String id,char[] password,String reason){throw new AssertionError("No password UI");}
            public void cancel(String id,String reason){events.add("cancel");}
            public void reportBusinessFailure(String id,String code){events.add("failure");}
            public SignatureResult getResult(String id){return new SignatureResult(id,"SUCCESS",false,"READY");}
        }
        private final class Original implements ActionInterceptor {
            boolean accept=true,reenter;Throwable failure;
            public boolean beforeDoAction(Action action,ActionEvent e){
                events.add("original-before");
                if(reenter)try{doAction(e);throw new AssertionError("Reentry accepted");}
                    catch(BusinessException expected){events.add("reentry-rejected");}
                    catch(Exception failure){throw new AssertionError(failure);}
                return accept;
            }
            public void afterDoActionSuccessed(Action action,ActionEvent e){events.add("original-success");}
            public boolean afterDoActionFailed(Action action,ActionEvent e,Throwable error){failure=error;events.add("original-failed");return false;}
        }
    }
    private static final class BudgetAlert extends BusinessException implements IResumeException {
        BudgetAlert(){super("预算金额不足，请核对");}
        public String getBusiExceptionType(){return "BUDGET_ALERT";}
    }
    private static final class OtherResume extends BusinessException implements IResumeException {
        OtherResume(){super("other native detail");}
        public String getBusiExceptionType(){return "OTHER";}
    }
    private static SapplyBillVO bill(final String linePk){
        final SapplyBillHeadVO head=new SapplyBillHeadVO(){
            public String getCgeneralhid(){return "BILL";}public String getPk_group(){return "G";}
            public String getPk_org(){return "O";}public String getVbillcode(){return "CODE";}
        };
        final SapplyBillBodyVO row=new SapplyBillBodyVO(){
            public String getCgeneralbid(){return linePk;}public String getCgeneralhid(){return "BILL";}
            public String getCrowno(){return "1";}
        };
        return new SapplyBillVO(){public IBillMeta getMetaData(){return META;}public SapplyBillHeadVO getHead(){return head;}
            public SapplyBillBodyVO[] getBodys(){return new SapplyBillBodyVO[]{row};}};
    }
}
