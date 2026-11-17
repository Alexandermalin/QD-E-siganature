package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.UUID;
import javax.swing.Action;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import nc.bs.bd.util.IEnableMsgConstant;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.bd.pub.actions.ManageModeActionInterceptor;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.ShowStatusBarMsgUtil;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.actions.EnableAction;
import nc.ui.uif2.model.AbstractAppModel;
import nc.ui.uif2.model.HierachicalDataAppModel;
import nc.vo.bd.supplier.supplierclass.SupplierclassVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveTreeEnableCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;

/** Runs the native enable checks and confirmation before authorizing the selected classification. */
public final class QdArchiveTreeEnableAction extends EnableAction implements OperationContextProvider,AuthorizedRequestConsumer {
    private static final long serialVersionUID=1L;
    private final ManageModeActionInterceptor manageMode=new ManageModeActionInterceptor();
    private ActionEvent activeEvent;
    private SuperVO activeRoot;
    private OperationContext authorized;
    private String dispatched;

    public QdArchiveTreeEnableAction() { setInterceptor(manageMode); }
    @Override public void setModel(AbstractAppModel model) { super.setModel(model);manageMode.setModel(model); }
    @Override public void doAction(ActionEvent event) throws Exception {
        if(activeEvent!=null) throw new BusinessException("分类启用正在处理，请勿重复操作。");
        activeEvent=event;
        try { checkSupplierParents();super.doAction(event); }
        finally { activeEvent=null;activeRoot=null;authorized=null;dispatched=null; }
    }
    @Override public Object doEnable(Object selected) throws Exception {
        if(activeEvent==null || activeRoot!=null || !(selected instanceof SuperVO)) throw new BusinessException("分类启用缺少原生操作上下文。");
        SuperVO value=(SuperVO)selected;activeRoot=value;
        ActionInterceptor original=getInterceptor();
        ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);signing.setRequestConsumer(this);
        CompositeActionInterceptor chain=new CompositeActionInterceptor();chain.addInterceptor(signing);
        SignatureLifecycleInterceptor outer=null;Throwable failure=null;boolean accepted=false;
        try {
            setInterceptor(chain);outer=SignatureLifecycleInstaller.install(this,signing);
            accepted=outer.beforeDoAction(this,activeEvent);
            if(!accepted) throw new BusinessException("分类启用已取消或授权未通过。");
            OperationContext request=authorized;
            if(request==null || !sameCurrentScope(request,value) || dispatched!=null) throw new BusinessException("分类启用范围已变化，请重新操作。");
            String requestId=request.getRequestId();dispatched=requestId;
            OperationResponse response=NCLocator.getInstance().lookup(IOperationService.class).execute(requestId,new ArchiveTreeEnableCommand(value));
            if(response==null || response.isReplayed() || !requestId.equals(response.getRequestId())
                    || response.getBusinessResult()==null || response.getBusinessResult().getClass()!=value.getClass()
                    || !value.getPrimaryKey().equals(((SuperVO)response.getBusinessResult()).getPrimaryKey())) {
                throw new BusinessException("无法确认分类启用结果，请重新查询后核对。请求号："+requestId);
            }
            outer.afterDoActionSuccessed(this,activeEvent);
            ShowStatusBarMsgUtil.showStatusBarMsg(IEnableMsgConstant.getEnableSuccessMsg(),getModel().getContext());
            return response.getBusinessResult();
        } catch(Exception error) {
            failure=error;if(accepted && outer!=null) outer.afterDoActionFailed(this,activeEvent,error);throw error;
        } catch(Error error) { failure=error;throw error; }
        finally {
            signing.finishLifecycle(signing.lifecycleGeneration(),this,activeEvent,failure);
            setInterceptor(original);activeRoot=null;authorized=null;dispatched=null;
        }
    }
    @Override public OperationContext createContext(Action action,ActionEvent event) throws BusinessException {
        if(action!=this || event!=activeEvent || activeRoot==null || getModel()==null || getModel().getContext()==null)
            throw new BusinessException("无法确定本次分类启用。");
        String node=getModel().getContext().getNodeCode(),domain=domain(node),group=getModel().getContext().getPk_group();
        String org=groupNode(node)?group:getModel().getContext().getPk_org();
        if(domain==null || group==null || org==null || activeRoot.getPrimaryKey()==null
                || !group.equals(activeRoot.getAttributeValue("pk_group"))) throw new BusinessException("无法确定所选分类的节点、集团或操作组织。");
        Object code=activeRoot.getAttributeValue("code");
        BusinessObjectRef root=new BusinessObjectRef(UUID.randomUUID().toString(),domain,activeRoot.getPrimaryKey(),code instanceof String?(String)code:null,null);
        return new OperationContext(null,null,group,org,node,"10140RACK".equals(node)?"货位":"基础档案分类","Enable","启用","ENABLE",null,
            Collections.singletonList(root),OperationContext.OWNER_POLICY_PROTOCOL);
    }
    @Override public boolean isAvailable(OperationContext context) {
        if(context==null || !"ENABLE".equals(context.getOperationType()) || !"Enable".equals(context.getButtonCode())
                || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) || domain(context.getNodeCode())==null
                || context.getGroupId()==null || context.getOrgId()==null || context.getObjects().size()!=1) return false;
        BusinessObjectRef ref=context.getObjects().get(0);
        return ref!=null && domain(context.getNodeCode()).equals(ref.getObjectType()) && ref.getObjectPk()!=null
            && ref.getParentClientObjectId()==null;
    }
    @Override public boolean bind(OperationContext context) {
        if(authorized!=null || !isAvailable(context) || context.getRequestId()==null || activeRoot==null || !sameCurrentScope(context,activeRoot)) return false;
        authorized=context;return true;
    }
    @Override public boolean wasDispatched(String requestId) { return requestId!=null && requestId.equals(dispatched); }
    @Override public void release(String requestId) { if(authorized!=null && authorized.getRequestId().equals(requestId)) { authorized=null;dispatched=null; } }
    @Override public Container getParentContainer() { return getModel().getContext().getEntranceUI(); }
    @Override public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }

    private boolean sameCurrentScope(OperationContext context,SuperVO value) {
        if(getModel()==null || getModel().getContext()==null || value==null || value.getPrimaryKey()==null) return false;
        String node=getModel().getContext().getNodeCode(),group=getModel().getContext().getPk_group();
        String org=groupNode(node)?group:getModel().getContext().getPk_org();
        Object selected=getModel().getSelectedData();
        return context.getNodeCode().equals(node) && context.getGroupId().equals(group) && context.getOrgId().equals(org)
            && selected instanceof SuperVO && value.getPrimaryKey().equals(((SuperVO)selected).getPrimaryKey())
            && context.getObjects().get(0).getObjectPk().equals(value.getPrimaryKey());
    }
    /** Preserve the supplier action's native ancestor check; the server checks it under the tree lock as well. */
    private void checkSupplierParents() throws BusinessException {
        String node=getModel().getContext().getNodeCode();
        if(!"10140SCLG".equals(node) && !"10140SCLO".equals(node)) return;
        if(!(getModel() instanceof HierachicalDataAppModel)) throw new BusinessException("无法核对供应商分类上级。");
        HierachicalDataAppModel model=(HierachicalDataAppModel)getModel();
        DefaultMutableTreeNode selected=model.getSelectedNode();
        if(selected==null || model.getTree()==null) throw new BusinessException("无法核对供应商分类上级。");
        Object root=model.getTree().getRoot();
        for(TreeNode parent=selected.getParent();parent!=null && parent!=root;parent=parent.getParent()) {
            if(parent instanceof DefaultMutableTreeNode) {
                Object value=((DefaultMutableTreeNode)parent).getUserObject();
                if(!(value instanceof SupplierclassVO)) throw new BusinessException("无法核对供应商分类上级。");
                if(Integer.valueOf(3).equals(((SupplierclassVO)value).getEnablestate())) throw new BusinessException("上级供应商分类已停用，不能启用当前分类。");
            }
        }
    }
    private static boolean groupNode(String node) { return "10140CCLG".equals(node)||"10140SCLG".equals(node)||"10140MCLG".equals(node); }
    private static String domain(String node) {
        if("10140CCLG".equals(node)||"10140CCLO".equals(node)) return "BD_CUSTCLASS";
        if("10140SCLG".equals(node)||"10140SCLO".equals(node)) return "BD_SUPPLIERCLASS";
        if("10140MCLG".equals(node)||"10140MCLO".equals(node)) return "BD_MARBASCLASS";
        if("10140RACK".equals(node)) return "BD_RACK";
        return null;
    }
}
