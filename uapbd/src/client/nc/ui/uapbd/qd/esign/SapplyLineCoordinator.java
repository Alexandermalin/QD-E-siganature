package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.ic.m4455.entity.SapplyBillBodyVO;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.qd.esign.SapplyLineCommand;
import nc.vo.ic.qd.esign.SapplyLineResult;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pattern.model.transfer.bill.ClientBillCombinServer;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SapplyLineScope;

/** Preserves NC selection and outer callbacks while authorizing the final row-operation RPC. */
final class SapplyLineCoordinator implements OperationContextProvider,AuthorizedRequestConsumer {
    interface Host {
        NCAction action(); BillManageModel model(); Class<?> nativeClass();
        void performNative(ActionEvent event); void runNative(ActionEvent event) throws Exception;
    }
    interface SelectionReader {
        void verify(Class<?> type) throws BusinessException;
        SapplyLineNativeBridge.Selection select(NCAction action,Class<?> type,BillManageModel model,boolean hint) throws Exception;
    }
    interface Gateway { OperationResponse execute(String request,SapplyLineCommand command) throws BusinessException; }
    interface SigningFactory { ElectronicSignatureActionInterceptor create(); }
    interface Refresh { void combine(BillManageModel model,SapplyBillVO old,SapplyBillVO[] returned) throws Exception; }

    private final Host host;private final String button,buttonName;
    private final SelectionReader reader;private final Gateway gateway;private final SigningFactory signingFactory;private final Refresh refresh;
    private final SapplyLineHandoff handoff=new SapplyLineHandoff();
    private ActionEvent activeEvent;private ActionInterceptor outer;private OperationContext draft;
    private boolean running,ready,consumed,inside;

    SapplyLineCoordinator(Host host,String button,String buttonName) {
        this(host,button,buttonName,new SelectionReader(){
            public void verify(Class<?> type) throws BusinessException {SapplyLineNativeBridge.verify(type);}
            public SapplyLineNativeBridge.Selection select(NCAction action,Class<?> type,BillManageModel model,boolean hint) throws Exception {
                return SapplyLineNativeBridge.select(action,type,model,hint);
            }
        },new Gateway(){
            public OperationResponse execute(String request,SapplyLineCommand command) throws BusinessException {
                return NCLocator.getInstance().lookup(IOperationService.class).execute(request,command);
            }
        },new SigningFactory(){public ElectronicSignatureActionInterceptor create(){return new ElectronicSignatureActionInterceptor();}},
        new Refresh(){public void combine(BillManageModel model,SapplyBillVO old,SapplyBillVO[] returned){
            new ClientBillCombinServer().combine(new SapplyBillVO[]{old},returned);model.directlyUpdate(old);
        }});
    }
    SapplyLineCoordinator(Host host,String button,String buttonName,SelectionReader reader,Gateway gateway,
            SigningFactory signingFactory,Refresh refresh) {
        if(host==null||reader==null||gateway==null||signingFactory==null||refresh==null
                || !("LineClose".equals(button)||"LineOpen".equals(button)))
            throw new IllegalArgumentException("Outbound-request row action dependencies are required");
        this.host=host;this.button=button;this.buttonName=buttonName;this.reader=reader;
        this.gateway=gateway;this.signingFactory=signingFactory;this.refresh=refresh;
    }

    void perform(ActionEvent event) {
        if(running)throw new BusinessExceptionAdapter(new BusinessException("出库申请行操作正在处理，请勿重复触发。"));
        if(host.model()==null || host.model().getContext()==null)
            throw new BusinessExceptionAdapter(new BusinessException("出库申请客户端上下文不可用。"));
        if(!matchesNode()){host.performNative(event);return;}
        NCAction action=host.action();ActionInterceptor original=action.getInterceptor();
        try{rejectSigning(original);}catch(BusinessException failure){throw new BusinessExceptionAdapter(failure);}
        ActionInterceptor guard=new OuterGuard(original);
        running=true;ready=false;consumed=false;inside=false;activeEvent=event;outer=guard;
        try{action.setInterceptor(guard);host.performNative(event);}
        finally {
            if(action.getInterceptor()==guard)action.setInterceptor(original);
            running=false;ready=false;consumed=false;inside=false;activeEvent=null;outer=null;draft=null;handoff.reset();
        }
    }

    void doAction(ActionEvent event) throws Exception {
        if(host.model()==null || host.model().getContext()==null)
            throw new BusinessException("出库申请客户端上下文不可用。");
        if(!matchesNode()) {
            if(running)throw new BusinessException("出库申请行操作节点已变化。");
            host.runNative(event);return;
        }
        if(!running || !ready || consumed || inside || event!=activeEvent || host.action().getInterceptor()!=outer)
            throw new BusinessException("出库申请行操作缺少有效的原生动作上下文。");
        consumed=true;inside=true;
        try{runAuthorized(event);}finally{inside=false;}
    }

    private void runAuthorized(ActionEvent event) throws Exception {
        reader.verify(host.nativeClass());
        SapplyLineNativeBridge.Selection selected=reader.select(host.action(),host.nativeClass(),host.model(),true);
        if(selected==null)return;
        draft=selectionContext(selected.selected);
        assertOriginalMatches(draft,selected.original);
        rejectSigning(outer);
        ElectronicSignatureActionInterceptor signing=signingFactory.create();
        if(signing==null)throw new BusinessException("出库申请行操作签名适配器不可用。");
        signing.setContextProvider(this);signing.setRequestConsumer(this);
        NCAction action=host.action();SignatureLifecycleInterceptor lifecycle=null;Throwable failure=null;
        try {
            action.setInterceptor(signing);lifecycle=SignatureLifecycleInstaller.install(action,signing);
            if(!lifecycle.beforeDoAction(action,event))return;
            if(!running || event!=activeEvent || !matchesNode() || action.getInterceptor()!=lifecycle)
                throw new BusinessException("出库申请行操作签名后的上下文已变化。");
            OperationContext authorized=handoff.context();
            if(authorized==null)host.runNative(event);
            else dispatch(authorized,event);
            lifecycle.afterDoActionSuccessed(action,event);
        } catch(Exception error) {
            failure=error;
            if(lifecycle!=null)try{lifecycle.afterDoActionFailed(action,event,error);}
                catch(RuntimeException cleanup){if(cleanup!=error)error.addSuppressed(cleanup);}
            throw error;
        } catch(Error error) {failure=error;throw error;}
        finally {
            try{signing.finishLifecycle(signing.lifecycleGeneration(),action,event,failure);}
            finally{if(action.getInterceptor()==lifecycle || action.getInterceptor()==signing)action.setInterceptor(outer);}
        }
    }

    private void dispatch(OperationContext authorized,ActionEvent event) throws Exception {
        SapplyLineNativeBridge.Selection current=reader.select(host.action(),host.nativeClass(),host.model(),false);
        if(current==null)throw new BusinessException("出库申请所选行已变化，请重新操作。");
        OperationContext observed=selectionContext(current.selected);
        assertOriginalMatches(observed,current.original);
        SapplyLineScope.Selection actual=SapplyLineScope.require(observed);
        String id=handoff.dispatch(observed.getGroupId(),observed.getOrgId(),actual.getRoot().getObjectPk(),actual.lineKeys());
        OperationResponse response;
        try{response=gateway.execute(id,new SapplyLineCommand(false));}
        catch(BusinessException failure){throw unknown(id,failure);}
        catch(RuntimeException failure){throw unknown(id,failure);}
        if(response==null || response.isReplayed() || !id.equals(response.getRequestId())
                || !(response.getBusinessResult() instanceof SapplyLineResult))throw unknown(id,null);
        SapplyLineResult result=(SapplyLineResult)response.getBusinessResult();
        SapplyBillVO[] nativeResult=result.getNativeResult();
        if(!actual.getRoot().getObjectPk().equals(result.getBillPk()) || nativeResult==null || nativeResult.length!=1
                || nativeResult[0]==null || nativeResult[0].getHead()==null
                || !result.getBillPk().equals(text(nativeResult[0].getHead().getCgeneralhid()))
                || !authorized.getGroupId().equals(text(nativeResult[0].getHead().getPk_group()))
                || !authorized.getOrgId().equals(text(nativeResult[0].getHead().getPk_org())))throw unknown(id,null);
        try{refresh.combine(host.model(),current.original,nativeResult);}
        catch(Exception failure){throw new BusinessException("出库申请行操作已返回结果，但客户端刷新失败，请先重新查询。请求号："+id,failure);}
    }

    private OperationContext selectionContext(SapplyBillVO bill) throws BusinessException {
        if(bill==null || bill.getHead()==null || host.model()==null || host.model().getContext()==null)
            throw new BusinessException("请选择出库申请单及需要处理的行。");
        SapplyBillHeadVO head=bill.getHead();String group=text(head.getPk_group()),org=text(head.getPk_org()),pk=text(head.getCgeneralhid());
        if(group==null || org==null || pk==null || !group.equals(text(host.model().getContext().getPk_group())))
            throw new BusinessException("出库申请单主键或组织无法核对。");
        SapplyBillBodyVO[] bodies=bill.getBodys();
        if(bodies==null || bodies.length==0 || bodies.length>999)throw new BusinessException("请选择不超过 999 行。");
        String rootId=UUID.randomUUID().toString();List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        refs.add(new BusinessObjectRef(rootId,"IC_SAPPLY",pk,text(head.getVbillcode()),null));
        for(SapplyBillBodyVO body:bodies) {
            String rowPk=body==null?null:text(body.getCgeneralbid());
            if(rowPk==null || !pk.equals(text(body.getCgeneralhid())))throw new BusinessException("所选出库申请行与单据不一致。");
            refs.add(new BusinessObjectRef(UUID.randomUUID().toString(),SapplyLineScope.LINE_TYPE,rowPk,text(body.getCrowno()),rootId));
        }
        OperationContext result=new OperationContext(null,null,group,org,"40080801","出库申请单",button,buttonName,"UPDATE",null,refs);
        SapplyLineScope.require(result);return result;
    }

    private static void assertOriginalMatches(OperationContext selected,SapplyBillVO original) throws BusinessException {
        if(original==null || original.getHead()==null || !selected.getGroupId().equals(text(original.getHead().getPk_group()))
                || !selected.getOrgId().equals(text(original.getHead().getPk_org()))
                || !SapplyLineScope.require(selected).getRoot().getObjectPk().equals(text(original.getHead().getCgeneralhid())))
            throw new BusinessException("出库申请原单据已变化，请重新选择。");
    }
    public OperationContext createContext(Action action,ActionEvent event) throws BusinessException {
        if(action!=host.action() || event!=activeEvent || !running || !inside || draft==null || !matchesNode())
            throw new BusinessException("当前动作不属于出库申请行操作。");
        return draft;
    }
    public boolean isAvailable(OperationContext context) {
        if(!handoff.supports(context) || !button.equals(context.getButtonCode()))return false;
        try{reader.verify(host.nativeClass());return true;}catch(BusinessException invalid){return false;}
    }
    public boolean bind(OperationContext context) throws BusinessException {
        if(draft==null || !sameScope(draft,context) || !isAvailable(context))return false;
        return handoff.bind(context);
    }
    public boolean wasDispatched(String requestId){return handoff.wasDispatched(requestId);}
    public void release(String requestId){handoff.release(requestId);}
    public Container getParentContainer(){return host.model()==null || host.model().getContext()==null?null:host.model().getContext().getEntranceUI();}
    public String getCurrentUserDisplayName(){return InvocationInfoProxy.getInstance().getUserCode();}
    private boolean matchesNode(){return host.model()!=null && host.model().getContext()!=null
            && "40080801".equals(host.model().getContext().getNodeCode());}

    private static boolean sameScope(OperationContext left,OperationContext right) throws BusinessException {
        if(right==null || !left.getGroupId().equals(right.getGroupId()) || !left.getOrgId().equals(right.getOrgId())
                || !left.getNodeCode().equals(right.getNodeCode()) || !left.getButtonCode().equals(right.getButtonCode())
                || !left.getOperationType().equals(right.getOperationType()))return false;
        SapplyLineScope.Selection a=SapplyLineScope.require(left),b=SapplyLineScope.require(right);
        if(!a.getRoot().getClientObjectId().equals(b.getRoot().getClientObjectId())
                || !a.getRoot().getObjectPk().equals(b.getRoot().getObjectPk()) || a.getLines().size()!=b.getLines().size())return false;
        Map<String,String> keys=new HashMap<String,String>();
        for(BusinessObjectRef ref:a.getLines())keys.put(ref.getClientObjectId(),ref.getObjectPk());
        for(BusinessObjectRef ref:b.getLines())if(!ref.getObjectPk().equals(keys.get(ref.getClientObjectId())))return false;
        return true;
    }
    private static String text(String value){if(value==null)return null;String s=value.trim();return s.isEmpty()||"~".equals(s)?null:s;}
    private static BusinessException unknown(String requestId,Throwable cause){
        String message="无法确认出库申请行操作结果，请先重新查询单据并按请求号核对："+requestId;
        if(cause==null)return new BusinessException(message);
        Throwable hint=budgetAlert(cause);
        String detail=hint.getMessage();
        if((detail==null || detail.trim().isEmpty()) && hint!=cause)detail=cause.getMessage();
        if(detail!=null){
            detail=detail.replace('\r',' ').replace('\n',' ').trim();
            if(detail.length()>240)detail=detail.substring(0,240)+"…";
            if(!detail.isEmpty())message+="\n原始提示："+detail;
        }
        return new BusinessException(message,cause);
    }

    private static Throwable budgetAlert(Throwable cause){
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        try {
            for(Throwable current=cause;current!=null && seen.size()<64 && seen.add(current);current=current.getCause())
                if(current instanceof IResumeException && "BUDGET_ALERT".equals(
                        ((IResumeException)current).getBusiExceptionType()))return current;
        } catch(RuntimeException ignored) { }
        return cause;
    }

    private void rejectSigning(ActionInterceptor value) throws BusinessException {
        if(containsSigning(value,Collections.newSetFromMap(new IdentityHashMap<ActionInterceptor,Boolean>())))
            throw new BusinessException("出库申请行操作拦截链已包含签名，请检查配置。");
    }
    private boolean containsSigning(ActionInterceptor value,Set<ActionInterceptor> seen) {
        if(value==null || !seen.add(value))return false;
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
        public boolean beforeDoAction(Action action,ActionEvent event) {
            if(!running || action!=host.action() || event!=activeEvent || host.action().getInterceptor()!=this)
                throw new IllegalStateException("Unexpected outbound-request row action event");
            boolean accepted=delegate==null || delegate.beforeDoAction(action,event);
            if(host.action().getInterceptor()!=this)throw new IllegalStateException("Outbound-request interceptor changed during preparation");
            ready=accepted;return accepted;
        }
        public void afterDoActionSuccessed(Action action,ActionEvent event){ready=false;if(delegate!=null)delegate.afterDoActionSuccessed(action,event);}
        public boolean afterDoActionFailed(Action action,ActionEvent event,Throwable failure){ready=false;return delegate==null||delegate.afterDoActionFailed(action,event,failure);}
    }
}
