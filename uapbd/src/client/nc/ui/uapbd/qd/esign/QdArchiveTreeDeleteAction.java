package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.UUID;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.tree.DefaultMutableTreeNode;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.bd.pub.actions.ManageModeActionInterceptor;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.AppEvent;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.actions.DeleteAction;
import nc.ui.uif2.model.AbstractAppModel;
import nc.ui.uif2.model.HierachicalDataAppModel;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveTreeDeleteCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;

/** Candidate asynchronous tree delete, preserving native checks and a single server transaction. */
public final class QdArchiveTreeDeleteAction extends DeleteAction implements OperationContextProvider,AuthorizedRequestConsumer {
    private static final long serialVersionUID=1L;
    private final ManageModeActionInterceptor manageMode=new ManageModeActionInterceptor();
    private ActionEvent activeEvent;private SuperVO selected;private DefaultMutableTreeNode selectedNode;
    private OperationContext authorized;private volatile String dispatched;private String lastRequestId;private boolean workerStarted;

    public QdArchiveTreeDeleteAction() { setInterceptor(manageMode); }
    @Override public void setModel(AbstractAppModel value) { super.setModel(value);manageMode.setModel(value); }
    @Override public void doAction(ActionEvent event) throws Exception {
        throw new BusinessException("分类删除必须通过受控异步入口执行。");
    }
    @Override public void actionPerformed(final ActionEvent event) {
        if(activeEvent!=null) return;
        activeEvent=event;workerStarted=false;final ActionInterceptor original=getInterceptor();
        final ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor();
        SignatureLifecycleInterceptor outer=null;boolean monitor=false,accepted=false;
        try {
            beforeDoAction();if(original!=null && !original.beforeDoAction(this,event)) return;
            if(!super.beforeStartDoAction(event)) return;monitor=true;
            signing.setContextProvider(this);signing.setRequestConsumer(this);
            CompositeActionInterceptor chain=new CompositeActionInterceptor();chain.addInterceptor(signing);
            setInterceptor(chain);outer=SignatureLifecycleInstaller.install(this,signing);
            if(!outer.beforeDoAction(this,event)) { super.doAfterFailure(event,new BusinessException("分类删除已取消。"));monitor=false;return; }
            accepted=true;final OperationContext request=authorized;
            if(request==null || !sameSelection(request) || dispatched!=null) throw new BusinessException("分类选择或操作组织已变化。");
            final SignatureLifecycleInterceptor lifecycle=outer;final SuperVO value=selected;lastRequestId=request.getRequestId();
            workerStarted=true;
            try {
                new NcClientWorker<OperationResponse>(getParentContainer()) {
                    protected OperationResponse callService() throws Exception {
                        dispatched=request.getRequestId();return NCLocator.getInstance().lookup(IOperationService.class)
                            .execute(request.getRequestId(),new ArchiveTreeDeleteCommand(value));
                    }
                    protected void completed(OperationResponse response) {
                        boolean committed=false;
                        try {
                            if(response==null || response.isReplayed() || !request.getRequestId().equals(response.getRequestId())
                                    || response.getBusinessResult()==null || response.getBusinessResult().getClass()!=value.getClass()
                                    || !QdArchiveTreeSaveCoordinator.pk(value).equals(QdArchiveTreeSaveCoordinator.pk((SuperVO)response.getBusinessResult())))
                                throw new BusinessException("无法确认分类删除结果，请按请求号核对。");
                            committed=true;
                            lifecycle.afterDoActionSuccessed(QdArchiveTreeDeleteAction.this,event);
                            if(original!=null) original.afterDoActionSuccessed(QdArchiveTreeDeleteAction.this,event);
                            applyCommittedResult(request,value);QdArchiveTreeDeleteAction.super.doAfterSuccess(event);
                        } catch(Exception error) {
                            if(committed) { QdArchiveTreeDeleteAction.super.doAfterFailure(event,error);report(error); }
                            else failed(error);
                        }
                    }
                    protected void failed(Throwable error) { finishFailed(event,original,lifecycle,error); }
                    protected void settled() {
                        try { signing.finishLifecycle(signing.lifecycleGeneration(),QdArchiveTreeDeleteAction.this,event,null); }
                        finally { setInterceptor(original);clear(); }
                    }
                }.execute();
            } catch(RuntimeException error) { workerStarted=false;throw error; }
        } catch(Exception error) {
            if(accepted && outer!=null) outer.afterDoActionFailed(this,event,error);
            if(monitor) super.doAfterFailure(event,error);report(error);
        } catch(Error error) {
            if(accepted && outer!=null) outer.afterDoActionFailed(this,event,error);
            if(monitor) super.doAfterFailure(event,error);throw error;
        } finally {
            if(!workerStarted) { signing.finishLifecycle(signing.lifecycleGeneration(),this,event,null);setInterceptor(original);clear(); }
        }
    }
    private void applyCommittedResult(OperationContext request,SuperVO value) throws BusinessException {
        if(!sameSelection(request) || model().getSelectedNode()!=selectedNode) throw new BusinessException("分类已删除，但当前选择已变化，请重新查询。");
        DefaultMutableTreeNode next=selectedNode.getNextNode();if(next==null) next=selectedNode.getPreviousNode();
        if(selectedNode.getParent()==null) throw new BusinessException("分类已删除，但界面无法刷新，请重新查询。");
        model().getTree().removeNodeFromParent(selectedNode);
        model().fireEvent(new AppEvent("Data_Deleted",model(),value));model().setSelectedNode(next);
    }
    private void finishFailed(ActionEvent event,ActionInterceptor original,SignatureLifecycleInterceptor lifecycle,Throwable error) {
        boolean handled=super.doAfterFailure(event,error);
        if(handled) handled=lifecycle.afterDoActionFailed(this,event,error);
        if(handled && original!=null) handled=original.afterDoActionFailed(this,event,error);
        if(handled) report(error);
    }
    private void report(Throwable error) {
        String detail=error==null || error.getMessage()==null?"请重新查询后核对。":error.getMessage();
        JOptionPane.showMessageDialog(getParentContainer(),detail+(lastRequestId==null?"":"\n请求号："+lastRequestId),"分类删除结果",JOptionPane.WARNING_MESSAGE);
    }
    private void clear() { activeEvent=null;selected=null;selectedNode=null;authorized=null;dispatched=null;lastRequestId=null;workerStarted=false; }
    private HierachicalDataAppModel model() { return (HierachicalDataAppModel)getModel(); }
    public OperationContext createContext(Action action,ActionEvent event) throws BusinessException {
        if(action!=this || event!=activeEvent || !(getModel() instanceof HierachicalDataAppModel) || model().getContext()==null)
            throw new BusinessException("分类删除上下文无效。");
        Object current=model().getSelectedData();selectedNode=model().getSelectedNode();
        if(!(current instanceof SuperVO) || selectedNode==null) throw new BusinessException("请选择待删除分类。");
        selected=(SuperVO)current;String node=model().getContext().getNodeCode(),domain=QdArchiveTreeSaveCoordinator.domain(node);
        String group=model().getContext().getPk_group(),org=QdArchiveTreeSaveCoordinator.groupNode(node)?group:model().getContext().getPk_org();
        String pk=QdArchiveTreeSaveCoordinator.pk(selected);
        if(domain==null || group==null || org==null || pk==null || !QdArchiveTreeSaveCoordinator.matches(domain,selected)
                || !group.equals(QdArchiveTreeSaveCoordinator.field(selected,"pk_group"))) throw new BusinessException("无法确定待删除档案及集团。");
        BusinessObjectRef ref=new BusinessObjectRef(UUID.randomUUID().toString(),domain,pk,QdArchiveTreeSaveCoordinator.field(selected,"code"),null);
        return new OperationContext(null,null,group,org,node,"10140RACK".equals(node)?"货位":"基础档案分类","Delete","删除","DELETE",null,
            Collections.singletonList(ref),OperationContext.OWNER_POLICY_PROTOCOL);
    }
    public boolean isAvailable(OperationContext context) {
        return context!=null && "DELETE".equals(context.getOperationType()) && "Delete".equals(context.getButtonCode())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null && context.getOrgId()!=null
            && QdArchiveTreeSaveCoordinator.domain(context.getNodeCode())!=null && context.getObjects().size()==1;
    }
    public boolean bind(OperationContext context) {
        if(authorized!=null || !isAvailable(context) || context.getRequestId()==null || !sameSelection(context)) return false;
        authorized=context;return true;
    }
    public boolean wasDispatched(String id) { return id!=null && id.equals(dispatched); }
    public void release(String id) { if(authorized!=null && authorized.getRequestId().equals(id)) { authorized=null;dispatched=null; } }
    public Container getParentContainer() { return getModel()==null || getModel().getContext()==null?null:getModel().getContext().getEntranceUI(); }
    public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }
    private boolean sameSelection(OperationContext context) {
        if(selected==null || !(getModel() instanceof HierachicalDataAppModel) || model().getContext()==null || model().getSelectedNode()!=selectedNode) return false;
        Object now=model().getSelectedData();String node=model().getContext().getNodeCode(),group=model().getContext().getPk_group();
        String org=QdArchiveTreeSaveCoordinator.groupNode(node)?group:model().getContext().getPk_org();
        BusinessObjectRef ref=context.getObjects().get(0);
        return now instanceof SuperVO && node.equals(context.getNodeCode()) && group!=null && group.equals(context.getGroupId())
            && org!=null && org.equals(context.getOrgId()) && ref.getObjectType().equals(QdArchiveTreeSaveCoordinator.domain(node))
            && ref.getObjectPk().equals(QdArchiveTreeSaveCoordinator.pk(selected)) && ref.getObjectPk().equals(QdArchiveTreeSaveCoordinator.pk((SuperVO)now));
    }
}
