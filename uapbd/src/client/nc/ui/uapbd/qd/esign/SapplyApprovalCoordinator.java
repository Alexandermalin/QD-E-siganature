package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.uapbd.qd.esign.IPolicyService;
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
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PolicyDecision;

/** Performs the native approval dialog first, then signs and dispatches its confirmed PF action. */
final class SapplyApprovalCoordinator implements OperationContextProvider,AuthorizedRequestConsumer {
    interface Host {
        AbstractScriptExcAction action();BillManageModel model();Class<?> nativeClass();boolean isComposite();
        void performNative(ActionEvent event);void runNative(ActionEvent event) throws Exception;
        void fillNative(PFlowContext flow);void nativeFlow(PFlowContext flow) throws Exception;
    }
    interface Preparation {
        void verify(Class<?> type) throws BusinessException;
        void validate(AbstractScriptExcAction action) throws Exception;
        Prepared prepare(Container parent,String action,String type,AggregatedValueObject bill,Map<?,?> params) throws BusinessException;
    }
    interface Prepared {
        boolean accepted();WorkflownoteVO note();Map<String,Object> parameters();void completed() throws BusinessException;
    }
    interface PolicyLookup {PolicyDecision resolve(String org,String node,String button) throws BusinessException;}
    interface Gateway {OperationResponse execute(String request,Serializable command) throws BusinessException;}
    interface BatchPreparation {
        SapplyPfBatchCommand prepare(AbstractScriptExcAction action,PFlowContext flow,String nativeAction,
                String type,SapplyBillVO[] bills,String verifiedUser) throws Exception;
        void apply(AbstractScriptExcAction action,BillManageModel model,Container parent,OperationContext authorized,
                OperationResponse response,SapplyBillVO[] bills) throws Exception;
    }
    interface SigningFactory {ElectronicSignatureActionInterceptor create();}

    private final Host host;private final String button,buttonName,nativeAction;
    private final Preparation preparation;private final PolicyLookup policies;private final Gateway gateway;private final SigningFactory signingFactory;
    private final BatchPreparation batchPreparation;
    private final InventoryStateHandoff handoff=new InventoryStateHandoff();
    private ActionEvent activeEvent;private ActionInterceptor outer;private OperationContext draft;
    private boolean running,ready,consumed,inside,flowConsumed;

    SapplyApprovalCoordinator(Host host,String button,String buttonName,String nativeAction) {
        this(host,button,buttonName,nativeAction,new Preparation(){
            public void verify(Class<?> type) throws BusinessException {SapplyApprovalNativeContract.verify(type);}
            public void validate(AbstractScriptExcAction action) throws Exception {InventoryPfPreparation.validate(action);}
            public Prepared prepare(Container parent,String action,String type,AggregatedValueObject bill,Map<?,?> params) throws BusinessException {
                final InventoryPfPreparation value=InventoryPfPreparation.prepare(parent,action,type,bill,params);
                return new Prepared(){public boolean accepted(){return value.isAccepted();}public WorkflownoteVO note(){return value.getNote();}
                    public Map<String,Object> parameters(){return value.getParameters();}public void completed() throws BusinessException {value.completed();}};
            }
        },new PolicyLookup(){public PolicyDecision resolve(String org,String node,String button) throws BusinessException {
            return NCLocator.getInstance().lookup(IPolicyService.class).resolve(org,node,button);}},
        new Gateway(){public OperationResponse execute(String request,Serializable command) throws BusinessException {
            return NCLocator.getInstance().lookup(IOperationService.class).execute(request,command);}},
        new SigningFactory(){public ElectronicSignatureActionInterceptor create(){return new ElectronicSignatureActionInterceptor();}});
    }
    SapplyApprovalCoordinator(Host host,String button,String buttonName,String nativeAction,Preparation preparation,
            PolicyLookup policies,Gateway gateway,SigningFactory signingFactory) {
        this(host,button,buttonName,nativeAction,preparation,policies,gateway,signingFactory,nativeBatchPreparation());
    }
    SapplyApprovalCoordinator(Host host,String button,String buttonName,String nativeAction,Preparation preparation,
            PolicyLookup policies,Gateway gateway,SigningFactory signingFactory,BatchPreparation batchPreparation) {
        if(host==null || preparation==null || policies==null || gateway==null || signingFactory==null || batchPreparation==null
                || !("Approve".equals(button) && "APPROVE".equals(nativeAction)
                    || "UnApprove".equals(button) && "UNAPPROVE".equals(nativeAction)))
            throw new IllegalArgumentException("Outbound-request approval dependencies are required");
        this.host=host;this.button=button;this.buttonName=buttonName;this.nativeAction=nativeAction;
        this.preparation=preparation;this.policies=policies;this.gateway=gateway;this.signingFactory=signingFactory;
        this.batchPreparation=batchPreparation;
    }

    private static BatchPreparation nativeBatchPreparation(){return new BatchPreparation(){
        public SapplyPfBatchCommand prepare(AbstractScriptExcAction action,PFlowContext flow,String nativeAction,
                String type,SapplyBillVO[] bills,String verifiedUser) throws Exception {
            SapplyBatchClientPreparation.beforeCheck(action,flow,bills);
            return SapplyBatchClientPreparation.prepare(flow,nativeAction,type,bills,verifiedUser);
        }
        public void apply(AbstractScriptExcAction action,BillManageModel model,Container parent,OperationContext authorized,
                OperationResponse response,SapplyBillVO[] bills) throws Exception {
            SapplyBatchClientResult.apply(action,model,parent,authorized,response,bills);
        }
    };}

    void perform(ActionEvent event) {
        if(running)throw new BusinessExceptionAdapter(new BusinessException("出库申请审批正在处理，请勿重复触发。"));
        if(host.model()==null || host.model().getContext()==null)
            throw new BusinessExceptionAdapter(new BusinessException("出库申请审批客户端上下文不可用。"));
        if(!matchesNode()){host.performNative(event);return;}
        AbstractScriptExcAction action=host.action();ActionInterceptor original=action.getInterceptor();
        try{rejectSigning(original);}catch(BusinessException failure){throw new BusinessExceptionAdapter(failure);}
        ActionInterceptor guard=new OuterGuard(original);running=true;ready=false;consumed=false;inside=false;flowConsumed=false;
        activeEvent=event;outer=guard;
        try{action.setInterceptor(guard);host.performNative(event);}
        finally {
            if(action.getInterceptor()==guard)action.setInterceptor(original);
            running=false;ready=false;consumed=false;inside=false;flowConsumed=false;activeEvent=null;outer=null;draft=null;handoff.reset();
        }
    }
    void doAction(ActionEvent event) throws Exception {
        if(host.model()==null || host.model().getContext()==null)throw new BusinessException("出库申请审批上下文不可用。");
        if(!matchesNode()){if(running)throw new BusinessException("出库申请审批节点已变化。");host.runNative(event);return;}
        if(!running || !ready || consumed || inside || event!=activeEvent || host.action().getInterceptor()!=outer)
            throw new BusinessException("出库申请审批缺少有效的原生动作上下文。");
        consumed=true;inside=true;try{host.runNative(event);}finally{inside=false;}
    }
    void procFlow(PFlowContext flow) throws Exception {
        if(!matchesNode()) {
            if(running)throw new BusinessException("出库申请审批节点已变化。");
            host.nativeFlow(flow);return;
        }
        if(!running || !ready || !inside || flowConsumed || host.action().getInterceptor()!=outer)
            throw new BusinessException("出库申请审批缺少原生流程授权上下文。");
        flowConsumed=true;
        SapplyBillVO[] selected=selectedBills();
        boolean controlled=false;
        Map<String,PolicyDecision> decisions=new HashMap<String,PolicyDecision>();
        for(SapplyBillVO bill:selected) {
            String org=text(bill.getHead().getPk_org());PolicyDecision decision=decisions.get(org);
            if(decision==null){decision=policies.resolve(org,"40080801",button);
                if(decision==null)throw new BusinessException("无法读取出库申请审批策略。");decisions.put(org,decision);}
            controlled|=decision.isControlled();
        }
        if(!controlled){host.nativeFlow(flow);return;}
        if(selected.length>1)requireBatchOrganization(selected);
        preparation.verify(host.nativeClass());
        AbstractScriptExcAction action=host.action();
        if(flow==null || flow!=action.getFlowContext() || host.isComposite() || action.getBesideApproveContext()!=null
                || !empty(action.getPreActionNames()) || !empty(action.getAfterActionNames()))
            throw new BusinessException("出库申请审批包含尚未核对的复合流程。");
        rejectBeside(flow);
        host.fillNative(flow);
        rejectBeside(flow);
        if(!nativeAction.equals(flow.getActionName()) || flow.isBatch()!=(selected.length>1))
            throw new BusinessException("出库申请原生审批动作与授权不一致。");
        String flowUser=text(flow.getUserId()),loginUser=text(host.model().getContext().getPk_loginUser());
        if(flowUser==null || !flowUser.equals(loginUser) || !flowUser.equals(flow.getUserId()))
            throw new BusinessException("出库申请审批流程用户与当前登录用户不一致。");
        String actualAction=nc.vo.ic.qd.esign.SapplyNativeAction.approval(button,flowUser);
        preparation.validate(action);
        if(selected.length>1){
            SapplyBillVO[] bills=flowBills(flow,selected);
            String type=text(flow.getTrantype());if(type==null)type=text(flow.getBillType());
            if(type==null)throw new BusinessException("出库申请批量审批缺少原生交易类型。");
            SapplyPfBatchCommand command=batchPreparation.prepare(action,flow,actualAction,type,bills,loginUser);
            if(command==null){InventoryPfPreparation.setExecuted(action,false);return;}
            if(!actualAction.equals(command.getAction()) || !type.equals(command.getBillType()))
                throw new BusinessException("出库申请批量审批确认与原生流程不一致。");
            requireSameBills(bills,command.getBills());
            requireSameBills(bills,selectedBills());
            draft=batchContext(command);
            authorizeAndDispatchBatch(flow,bills,command);
            return;
        }
        SapplyBillVO bill=flowBill(flow,selected[0]);
        String type=text(flow.getTrantype());if(type==null)type=text(flow.getBillType());
        if(type==null)throw new BusinessException("出库申请审批缺少原生交易类型。");
        Prepared prepared=preparation.prepare(flow.getParent(),actualAction,type,bill,flow.getEParam());
        if(prepared==null)throw new BusinessException("出库申请审批流程准备失败。");
        if(!prepared.accepted())return;
        Map<String,Object> parameters=approvalParameters(prepared);
        WorkflownoteVO confirmed=prepared.note();
        boolean adjusted;
        try { adjusted=SapplyApprovalIntent.isWorkflowAdjustment(confirmed); }
        catch(BusinessException failure) {
            throw new BusinessException("无法确认 NC 审批窗口结果，请先查询单据与审批任务。",failure);
        }
        if(adjusted) {
            // PfUtilClient.runAction returns the original bill for these non-approval actions.
            try {
                requireSelection(bill);flowBill(flow,bill);
                InventoryPfPreparation.completeNativeResult(null);
                action.processReturnObjs(new SapplyBillVO[]{bill});
            } catch(Exception failure) {
                throw new BusinessException("NC 已返回加签或指定审批人结果，界面刷新失败，请先查询审批任务。",failure);
            }
            return;
        }
        requireSelection(bill);
        InventoryPfStateCommand command=new InventoryPfStateCommand(type,bill,flow.getUserObj(),confirmed,parameters);
        draft=context(bill,command);
        authorizeAndDispatch(flow,bill,command,prepared);
    }

    private void authorizeAndDispatch(PFlowContext flow,SapplyBillVO bill,InventoryPfStateCommand command,Prepared prepared) throws Exception {
        rejectSigning(outer);ElectronicSignatureActionInterceptor signing=signingFactory.create();
        if(signing==null)throw new BusinessException("出库申请审批签名适配器不可用。");
        signing.setContextProvider(this);signing.setRequestConsumer(this);
        AbstractScriptExcAction action=host.action();SignatureLifecycleInterceptor lifecycle=null;Throwable failure=null;
        try {
            action.setInterceptor(signing);lifecycle=SignatureLifecycleInstaller.install(action,signing);
            if(!lifecycle.beforeDoAction(action,activeEvent))return;
            if(!running || !inside || !matchesNode() || action.getInterceptor()!=lifecycle)
                throw new BusinessException("出库申请审批签名后的上下文已变化。");
            OperationContext authorized=handoff.context();
            if(authorized==null)throw new BusinessException("出库申请审批策略已变化，请重新操作。");
            requireSelection(bill);flowBill(flow,bill);
            if(!draft.getObjects().get(0).getClientObjectId().equals(SapplyApprovalIntent.clientObjectId(button,command)))
                throw new BusinessException("出库申请审批内容已变化，请重新操作。");
            SapplyBillHeadVO head=bill.getHead();
            String id=handoff.dispatch(text(head.getPk_group()),text(head.getPk_org()),text(head.getCgeneralhid()));
            OperationResponse response=gateway.execute(id,command);
            if(response==null || response.isReplayed() || !id.equals(response.getRequestId())
                    || !(response.getBusinessResult() instanceof InventoryPfStateResult))throw unknown();
            InventoryPfStateResult result=(InventoryPfStateResult)response.getBusinessResult();
            if(result.isDeleted() || !head.getCgeneralhid().equals(result.getPrimaryKey()))throw unknown();
            Object raw=result.getNativeResult();Object[] returned=null;
            if(raw!=null){if(raw.getClass().isArray())returned=(Object[])raw;
                else{returned=(Object[])Array.newInstance(raw.getClass(),1);returned[0]=raw;}}
            try { prepared.completed();action.processReturnObjs(returned); }
            catch(Exception refreshFailure){throw new BusinessException("出库申请审批已返回成功，但客户端刷新失败，请查询单据和审批任务。",refreshFailure);}
            lifecycle.afterDoActionSuccessed(action,activeEvent);
        } catch(Exception error) {
            failure=error;if(lifecycle!=null)try{lifecycle.afterDoActionFailed(action,activeEvent,error);}
                catch(RuntimeException cleanup){if(cleanup!=error)error.addSuppressed(cleanup);}
            throw error;
        } catch(Error error){failure=error;throw error;}
        finally {try{signing.finishLifecycle(signing.lifecycleGeneration(),action,activeEvent,failure);}
            finally{if(action.getInterceptor()==lifecycle || action.getInterceptor()==signing)action.setInterceptor(outer);}}
    }

    private void authorizeAndDispatchBatch(PFlowContext flow,SapplyBillVO[] bills,SapplyPfBatchCommand command) throws Exception {
        rejectSigning(outer);ElectronicSignatureActionInterceptor signing=signingFactory.create();
        if(signing==null)throw new BusinessException("出库申请批量审批签名适配器不可用。");
        signing.setContextProvider(this);signing.setRequestConsumer(this);
        AbstractScriptExcAction action=host.action();SignatureLifecycleInterceptor lifecycle=null;Throwable failure=null;
        try {
            action.setInterceptor(signing);lifecycle=SignatureLifecycleInstaller.install(action,signing);
            if(!lifecycle.beforeDoAction(action,activeEvent))return;
            if(!running || !inside || !matchesNode() || action.getInterceptor()!=lifecycle || !flow.isBatch())
                throw new BusinessException("出库申请批量审批签名后的上下文已变化。");
            OperationContext authorized=handoff.context();
            if(authorized==null)throw new BusinessException("出库申请批量审批策略已变化，请重新操作。");
            requireSameBills(bills,selectedBills());flowBills(flow,bills);
            if(!sameScope(draft,batchContext(command)))
                throw new BusinessException("出库申请批量审批内容已变化，请重新操作。");
            Set<String> keys=new HashSet<String>();
            for(SapplyBillVO bill:bills)keys.add(text(bill.getHead().getCgeneralhid()));
            String id=handoff.dispatchBatch(keys);
            OperationResponse response=gateway.execute(id,command);
            batchPreparation.apply(action,host.model(),getParentContainer(),authorized,response,bills);
            lifecycle.afterDoActionSuccessed(action,activeEvent);
        } catch(Exception error) {
            failure=error;if(lifecycle!=null)try{lifecycle.afterDoActionFailed(action,activeEvent,error);}
                catch(RuntimeException cleanup){if(cleanup!=error)error.addSuppressed(cleanup);}
            throw error;
        } catch(Error error){failure=error;throw error;}
        finally {try{signing.finishLifecycle(signing.lifecycleGeneration(),action,activeEvent,failure);}
            finally{if(action.getInterceptor()==lifecycle || action.getInterceptor()==signing)action.setInterceptor(outer);}}
    }

    private SapplyBillVO[] selectedBills() throws BusinessException {
        Object[] values=host.model().getSelectedOperaDatas();
        if(values==null || values.length==0 || values.length>1000)throw new BusinessException("请选择不超过 1000 张待审批的出库申请单。");
        SapplyBillVO[] result=new SapplyBillVO[values.length];String group=text(host.model().getContext().getPk_group());
        for(int i=0;i<values.length;i++) {
            if(!(values[i] instanceof SapplyBillVO) || ((SapplyBillVO)values[i]).getHead()==null)
                throw new BusinessException("无法确定待审批的出库申请单。");
            SapplyBillVO bill=(SapplyBillVO)values[i];SapplyBillHeadVO head=bill.getHead();
            if(group==null || !group.equals(text(head.getPk_group())) || text(head.getPk_org())==null || text(head.getCgeneralhid())==null)
                throw new BusinessException("出库申请审批单据或组织不完整。");
            result[i]=bill;
        }
        return result;
    }
    private SapplyBillVO flowBill(PFlowContext flow,SapplyBillVO expected) throws BusinessException {
        AggregatedValueObject[] values=flow.getBillVos();
        if(values==null || values.length!=1 || !(values[0] instanceof SapplyBillVO))
            throw new BusinessException("出库申请审批流程只支持一张单据。");
        SapplyBillVO bill=(SapplyBillVO)values[0];
        if(!sameBill(bill,expected))throw new BusinessException("原生审批单据与当前选择不一致。");
        return bill;
    }
    private SapplyBillVO[] flowBills(PFlowContext flow,SapplyBillVO[] expected) throws BusinessException {
        AggregatedValueObject[] values=flow.getBillVos();
        if(values==null || values.length!=expected.length)throw new BusinessException("原生批量审批单据范围已变化。");
        SapplyBillVO[] bills=new SapplyBillVO[values.length];
        for(int index=0;index<values.length;index++){
            if(!(values[index] instanceof SapplyBillVO))throw new BusinessException("原生批量审批包含其他类型单据。");
            bills[index]=(SapplyBillVO)values[index];
        }
        requireSameBills(expected,bills);return bills;
    }
    private static void requireSameBills(SapplyBillVO[] expected,SapplyBillVO[] actual) throws BusinessException {
        if(expected==null || actual==null || expected.length!=actual.length)
            throw new BusinessException("出库申请批量审批选择已变化，请重新操作。");
        Map<String,SapplyBillVO> remaining=new HashMap<String,SapplyBillVO>();
        for(SapplyBillVO bill:expected){String pk=bill==null || bill.getHead()==null?null:text(bill.getHead().getCgeneralhid());
            if(pk==null || remaining.put(pk,bill)!=null)throw new BusinessException("出库申请批量审批单据重复或缺少主键。");}
        for(SapplyBillVO bill:actual){String pk=bill==null || bill.getHead()==null?null:text(bill.getHead().getCgeneralhid());
            if(pk==null || !sameBill(remaining.remove(pk),bill))throw new BusinessException("出库申请批量审批单据或组织已变化。");}
        if(!remaining.isEmpty())throw new BusinessException("出库申请批量审批选择已变化，请重新操作。");
    }
    private static void requireBatchOrganization(SapplyBillVO[] bills) throws BusinessException {
        String group=null,org=null;Set<String> keys=new HashSet<String>();
        for(SapplyBillVO bill:bills){SapplyBillHeadVO head=bill==null?null:bill.getHead();
            String currentGroup=head==null?null:text(head.getPk_group()),currentOrg=head==null?null:text(head.getPk_org());
            String pk=head==null?null:text(head.getCgeneralhid());
            if(currentGroup==null || currentOrg==null || pk==null || !keys.add(pk)
                    || group!=null && !group.equals(currentGroup) || org!=null && !org.equals(currentOrg))
                throw new BusinessException("出库申请批量审批须选择同一集团、业务单元的不重复单据。");
            group=currentGroup;org=currentOrg;
        }
    }
    private void requireSelection(SapplyBillVO expected) throws BusinessException {
        SapplyBillVO[] selected=selectedBills();
        if(selected.length!=1 || !sameBill(expected,selected[0]))throw new BusinessException("出库申请审批选择已变化，请重新操作。");
    }
    private static boolean sameBill(SapplyBillVO first,SapplyBillVO second) {
        if(first==null || second==null || first.getHead()==null || second.getHead()==null)return false;
        SapplyBillHeadVO a=first.getHead(),b=second.getHead();String pk=text(a.getCgeneralhid()),group=text(a.getPk_group()),org=text(a.getPk_org());
        return pk!=null && group!=null && org!=null && pk.equals(text(b.getCgeneralhid()))
                && group.equals(text(b.getPk_group())) && org.equals(text(b.getPk_org()));
    }
    private OperationContext context(SapplyBillVO bill,InventoryPfStateCommand command) throws BusinessException {
        SapplyBillHeadVO head=bill.getHead();
        BusinessObjectRef ref=new BusinessObjectRef(SapplyApprovalIntent.clientObjectId(button,command),"IC_SAPPLY",text(head.getCgeneralhid()),text(head.getVbillcode()),null);
        return new OperationContext(null,null,text(head.getPk_group()),text(head.getPk_org()),"40080801","出库申请单",
                button,buttonName,"UPDATE",null,Collections.singletonList(ref));
    }
    private OperationContext batchContext(SapplyPfBatchCommand command) throws BusinessException {
        SapplyBillVO[] bills=command.getBills();PfUserObject[] users=command.getUserObjects();
        if(bills==null || bills.length<2 || bills.length>1000 || users!=null && users.length!=bills.length)
            throw new BusinessException("出库申请批量审批单据范围不完整。");
        List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();String group=null,org=null;
        Set<String> billKeys=new HashSet<String>();
        String shared=SapplyApprovalIntent.batchSharedFingerprint(command.getWorkflowNote(),command.getParameters(),
                command.getPrecheckSkippedPks());
        for(int index=0;index<bills.length;index++){
            SapplyBillVO bill=bills[index];SapplyBillHeadVO head=bill==null?null:bill.getHead();
            String itemGroup=head==null?null:text(head.getPk_group()),itemOrg=head==null?null:text(head.getPk_org());
            String pk=head==null?null:text(head.getCgeneralhid());
            if(itemGroup==null || itemOrg==null || pk==null || group!=null && !group.equals(itemGroup)
                    || org!=null && !org.equals(itemOrg) || !billKeys.add(pk))
                throw new BusinessException("出库申请批量审批须选择同一集团、业务单元的有效单据。");
            group=itemGroup;org=itemOrg;
            String clientId=SapplyApprovalIntent.batchClientObjectId(button,command.getBillType(),bill,
                    users==null?null:users[index],shared);
            refs.add(new BusinessObjectRef(clientId,"IC_SAPPLY",pk,text(head.getVbillcode()),null));
        }
        String[] skipped=command.getPrecheckSkippedPks();
        if(skipped.length>=bills.length || !billKeys.containsAll(java.util.Arrays.asList(skipped)))
            throw new BusinessException("出库申请批量审批预校验范围无效。");
        try{return new OperationContext(null,null,group,org,"40080801","出库申请单",button,buttonName,"UPDATE",null,refs);}
        catch(IllegalArgumentException invalid){throw new BusinessException("出库申请批量审批包含重复单据。",invalid);}
    }
    private static boolean sameScope(OperationContext first,OperationContext second) {
        if(first==null || second==null || !first.getGroupId().equals(second.getGroupId())
                || !first.getOrgId().equals(second.getOrgId()) || !first.getNodeCode().equals(second.getNodeCode())
                || !first.getButtonCode().equals(second.getButtonCode()) || first.getObjects().size()!=second.getObjects().size())return false;
        Map<String,String> ids=new HashMap<String,String>();
        for(BusinessObjectRef ref:first.getObjects())if(ids.put(ref.getObjectPk(),ref.getClientObjectId())!=null)return false;
        for(BusinessObjectRef ref:second.getObjects())if(!ref.getClientObjectId().equals(ids.remove(ref.getObjectPk())))return false;
        return ids.isEmpty();
    }
    public OperationContext createContext(Action action,ActionEvent event) throws BusinessException {
        if(action!=host.action() || event!=activeEvent || !running || !inside || draft==null || !matchesNode())
            throw new BusinessException("当前动作不属于出库申请审批。");
        return draft;
    }
    public boolean isAvailable(OperationContext context) {
        if(!handoff.supports(context) || !button.equals(context.getButtonCode()))return false;
        try{preparation.verify(host.nativeClass());return true;}catch(BusinessException invalid){return false;}
    }
    public boolean bind(OperationContext context) {
        if(draft==null || context==null || !isAvailable(context) || !sameScope(draft,context))return false;
        return handoff.bind(context);
    }
    public boolean wasDispatched(String requestId){return handoff.wasDispatched(requestId);}
    public void release(String requestId){handoff.release(requestId);}
    public Container getParentContainer(){return host.model()==null || host.model().getContext()==null?null:host.model().getContext().getEntranceUI();}
    public String getCurrentUserDisplayName(){return InvocationInfoProxy.getInstance().getUserCode();}
    private boolean matchesNode(){return host.model()!=null && host.model().getContext()!=null
            && "40080801".equals(host.model().getContext().getNodeCode());}
    private static void rejectBeside(PFlowContext flow) throws BusinessException {
        if(flow.getEParam().get("besideapprove")!=null)
            throw new BusinessException("出库申请旁批流程尚未接入电子签名，请使用普通审批入口。");
    }
    private static Map<String,Object> approvalParameters(Prepared prepared) throws BusinessException {
        Map<String,Object> parameters=prepared.parameters();
        if(parameters==null || !parameters.containsKey("besideapprove"))return parameters;
        if(parameters.get("besideapprove")!=null)
            throw new BusinessException("出库申请旁批流程尚未接入电子签名，请使用普通审批入口。");
        Map<String,Object> normalized=new HashMap<String,Object>(parameters);
        normalized.remove("besideapprove");return normalized;
    }
    private static boolean empty(List<?> values){return values==null||values.isEmpty();}
    private static String text(String value){if(value==null)return null;String s=value.trim();return s.isEmpty()||"~".equals(s)?null:s;}
    private static BusinessException unknown(){return new BusinessException("无法确认出库申请审批结果，请先查询单据和审批任务。");}
    private void rejectSigning(ActionInterceptor value) throws BusinessException {
        if(containsSigning(value,Collections.newSetFromMap(new IdentityHashMap<ActionInterceptor,Boolean>())))
            throw new BusinessException("出库申请审批拦截链已包含签名，请检查配置。");
    }
    private boolean containsSigning(ActionInterceptor value,Set<ActionInterceptor> seen){
        if(value==null||!seen.add(value))return false;
        if(value instanceof ElectronicSignatureActionInterceptor || value instanceof SignatureLifecycleInterceptor)return true;
        if(value instanceof OuterGuard)return containsSigning(((OuterGuard)value).delegate,seen);
        if(value instanceof nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor)
            for(ActionInterceptor child:((nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor)value).getInterceptors())
                if(containsSigning(child,seen))return true;
        if(value instanceof nc.ui.uif2.actions.CompositeActionInterceptor)
            for(ActionInterceptor child:((nc.ui.uif2.actions.CompositeActionInterceptor)value).getInterceptors())
                if(containsSigning(child,seen))return true;
        return false;
    }
    private final class OuterGuard implements ActionInterceptor {
        final ActionInterceptor delegate;OuterGuard(ActionInterceptor delegate){this.delegate=delegate;}
        public boolean beforeDoAction(Action action,ActionEvent event){
            if(!running || action!=host.action() || event!=activeEvent || host.action().getInterceptor()!=this)
                throw new IllegalStateException("Unexpected outbound-request approval event");
            boolean accepted=delegate==null||delegate.beforeDoAction(action,event);
            if(host.action().getInterceptor()!=this)throw new IllegalStateException("Outbound-request approval interceptor changed");
            ready=accepted;return accepted;
        }
        public void afterDoActionSuccessed(Action action,ActionEvent event){ready=false;if(delegate!=null)delegate.afterDoActionSuccessed(action,event);}
        public boolean afterDoActionFailed(Action action,ActionEvent event,Throwable failure){ready=false;return delegate==null||delegate.afterDoActionFailed(action,event,failure);}
    }
}
