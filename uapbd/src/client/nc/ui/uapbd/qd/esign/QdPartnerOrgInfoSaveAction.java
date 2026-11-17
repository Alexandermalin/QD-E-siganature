package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.ui.bd.pub.orginfo.model.OrgInfoBillManageModel;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.UIState;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.actions.SaveAction;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerOrgPage;

/** Keeps the original page save workflow and hands its update to the owner-policy gateway. */
public final class QdPartnerOrgInfoSaveAction extends SaveAction implements OperationContextProvider {
    private static final long serialVersionUID=1L;
    private final QdPartnerOrgInfoModelService service;
    private PartnerOperationOrganization operationScope;
    private ActionEvent activeEvent;
    private SignatureLifecycleInterceptor activeInterceptor;
    private OperationContext draft;
    public QdPartnerOrgInfoSaveAction(QdPartnerOrgInfoModelService service) { this.service=service; }
    public void setOperationScope(PartnerOperationOrganization value) { operationScope=value; }
    @Override public void actionPerformed(ActionEvent event) {
        if(activeInterceptor!=null) { if(isClosing(event)) throw saveNotFinished();return; }
        if(!isPartnerEdit()) { super.actionPerformed(event);return; }
        ActionInterceptor original=getInterceptor();
        ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor() {
            @Override public boolean beforeDoAction(Action action,ActionEvent event) {
                boolean accepted=super.beforeDoAction(action,event);if(accepted) service.acceptPreparation(draft);return accepted;
            }
        };
        signing.setContextProvider(this);signing.setRequestConsumer(service);
        CompositeActionInterceptor chain=new CompositeActionInterceptor();chain.addInterceptor(signing);if(original!=null) chain.addInterceptor(original);
        activeEvent=event;Throwable failure=null;
        try { setInterceptor(chain);activeInterceptor=SignatureLifecycleInstaller.install(this,signing);super.actionPerformed(event); }
        catch(RuntimeException error) { failure=error;throw error; }
        catch(Error error) { failure=error;throw error; }
        finally {
            try { signing.finishLifecycle(signing.lifecycleGeneration(),this,event,failure); }
            finally {
                service.finishAttempt();if(getInterceptor()==activeInterceptor || getInterceptor()==chain) setInterceptor(original);
                activeInterceptor=null;activeEvent=null;draft=null;
            }
        }
        if(isClosing(event) && isPartnerEdit()) throw saveNotFinished();
    }
    @Override public void doAction(ActionEvent event) throws Exception {
        if((activeInterceptor!=null || isPartnerEdit()) && (activeInterceptor==null || event!=activeEvent
                || getInterceptor()!=activeInterceptor || !isPartnerEdit() || model().getService()!=service))
            throw new BusinessException("往来档案页签保存接入状态已变化，请重新保存。");
        super.doAction(event);
    }
    @Override protected void doEditSave(Object value) throws Exception {
        if(activeInterceptor==null) { super.doEditSave(value);return; }
        requireParent(service.requireRow(value));service.beginSave(value);
        try { super.doEditSave(value); } finally { service.endSave(); }
    }
    public OperationContext createContext(Action action,ActionEvent event) throws BusinessException {
        if(action!=this || event!=activeEvent || !isPartnerEdit() || model().getService()!=service) throw new BusinessException("往来档案页签保存上下文无效。");
        SuperVO row=service.requireRow(model().getSelectedData()),parent=requireParent(row);PartnerOrgPage page=service.page();
        String node=model().getContext().getNodeCode(),group=model().getContext().getPk_group();
        String org=page.groupNode.equals(node)?group:operationScope==null?null:operationScope.requireOperationOrganization();
        if(org==null || PartnerOrgPage.forAction(node,page.saveButton,"UPDATE")!=page) throw new BusinessException("无法确定往来档案操作节点或业务单元。");
        Object code=parent.getAttributeValue("code");
        BusinessObjectRef ref=new BusinessObjectRef(UUID.randomUUID().toString(),page.domain,row.getPrimaryKey(),code instanceof String?(String)code:null,null);
        draft=new OperationContext(null,null,group,org,node,page.rootTable.equals("bd_customer")?"客户":"供应商",
            page.saveButton,"组织信息保存","UPDATE",null,Collections.singletonList(ref),OperationContext.OWNER_POLICY_PROTOCOL);
        return draft;
    }
    private SuperVO requireParent(SuperVO row) throws BusinessException {
        Object raw=model().getBaseInfoVO();String group=model().getContext().getPk_group();PartnerOrgPage page=service.page();
        if(!(raw instanceof SuperVO) || group==null || !group.equals(row.getAttributeValue("pk_group"))) throw new BusinessException("无法确定往来档案主档。");
        SuperVO root=(SuperVO)raw;
        if(!page.rootTable.equals(root.getTableName()) || root.getPrimaryKey()==null || !group.equals(root.getAttributeValue("pk_group"))
                || root.getAttributeValue("pk_org")==null || !root.getPrimaryKey().equals(row.getAttributeValue(page.rootKey)))
            throw new BusinessException("往来档案页签与当前主档不一致。");
        return root;
    }
    private OrgInfoBillManageModel model() { return (OrgInfoBillManageModel)getModel(); }
    private boolean isPartnerEdit() {
        if(!(getModel() instanceof OrgInfoBillManageModel) || model().getContext()==null || model().getUiState()!=UIState.EDIT) return false;
        PartnerOrgPage page=service.page();return PartnerOrgPage.forAction(model().getContext().getNodeCode(),page.saveButton,"UPDATE")==page;
    }
    public Container getParentContainer() { return model().getContext().getEntranceUI(); }
    public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }
    private boolean isClosing(ActionEvent event) { return event!=null && event.getSource()==this && event.getID()==0 && event.getActionCommand()==null; }
    private BusinessExceptionAdapter saveNotFinished() { return new BusinessExceptionAdapter(new BusinessException("往来档案页签尚未保存，窗口保持打开。")); }
}
