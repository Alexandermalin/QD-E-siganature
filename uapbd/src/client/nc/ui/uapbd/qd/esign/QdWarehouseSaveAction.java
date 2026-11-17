package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.ui.pub.bill.BillItem;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.UIState;
import nc.ui.uif2.actions.ActionInitializer;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.actions.AddAction;
import nc.ui.uif2.actions.SaveAction;
import nc.ui.uif2.editor.BillForm;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.bd.stordoc.StordocVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Native warehouse save and save-add workflows with the same owner-policy guard. */
public final class QdWarehouseSaveAction extends SaveAction implements OperationContextProvider {
    private static final long serialVersionUID=1L;
    private final QdWarehouseModelService service;
    private QdWarehouseModelDataManager operationScope;
    private boolean saveAdd;
    private AddAction addAction;
    private ActionEvent activeEvent;
    private SignatureLifecycleInterceptor activeInterceptor;
    private OperationContext draft;

    public QdWarehouseSaveAction(QdWarehouseModelService service) {
        if(service==null) throw new IllegalArgumentException("Warehouse service is required");
        this.service=service;
    }
    public void setOperationScope(QdWarehouseModelDataManager value) { operationScope=value; }
    public void setSaveAdd(boolean value) {
        saveAdd=value;
        if(value) ActionInitializer.initializeAction(this,"SaveAdd");
    }
    public void setAddAction(AddAction value) { addAction=value; }
    @Override protected boolean isActionEnable() {
        return getModel()!=null && (saveAdd?getModel().getUiState()==UIState.ADD:super.isActionEnable());
    }
    @Override public void actionPerformed(ActionEvent event) {
        if(activeInterceptor!=null) { if(isClosing(event)) throw saveNotFinished();return; }
        if(operation()==null) { super.actionPerformed(event);return; }
        ActionInterceptor original=getInterceptor();
        ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor() {
            @Override public boolean beforeDoAction(Action action,ActionEvent event) {
                boolean accepted=super.beforeDoAction(action,event);
                if(accepted) service.acceptPreparation(draft);
                return accepted;
            }
        };
        signing.setContextProvider(this);signing.setRequestConsumer(service);
        CompositeActionInterceptor chain=new CompositeActionInterceptor();
        chain.addInterceptor(signing);if(original!=null) chain.addInterceptor(original);
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
        if(isClosing(event) && operation()!=null) throw saveNotFinished();
    }
    @Override public void doAction(ActionEvent event) throws Exception {
        if(activeInterceptor==null || event!=activeEvent || getInterceptor()!=activeInterceptor
                || operation()==null || model().getService()!=service)
            throw new BusinessException("仓库保存接入状态已变化，请重新保存。");
        if(!saveAdd) { super.doAction(event);return; }
        if(addAction==null || getModel().getUiState()!=UIState.ADD) throw new BusinessException("仓库保存新增状态不正确。");
        // 与 NC SaveAddAction 的顺序一致：取值、校验、add、提示、继续新增。
        Object value=getEditor().getValue();validate(value);service.beginSave(value,"INSERT");
        try { getModel().add(value); } finally { service.endSave(); }
        showSuccessInfo();addAction.doAction(event);
    }
    @Override protected void doAddSave(Object value) throws Exception {
        service.beginSave(value,"INSERT");
        try { super.doAddSave(value); } finally { service.endSave(); }
    }
    @Override protected void doEditSave(Object value) throws Exception {
        service.beginSave(value,"UPDATE");
        try { super.doEditSave(value); } finally { service.endSave(); }
    }
    public OperationContext createContext(Action action,ActionEvent event) throws BusinessException {
        String op=operation();
        if(action!=this || event!=activeEvent || op==null || model().getService()!=service)
            throw new BusinessException("当前动作不属于已接入的仓库保存。");
        String group=getModel().getContext().getPk_group();String desired=headText("pk_org");
        String org="INSERT".equals(op)?desired:operationScope==null?null:operationScope.requireOperationOrganization();
        if(group==null || org==null || desired==null) throw new BusinessException("无法确定仓库所属集团、操作组织或所属组织。");
        String pk=null;
        if("UPDATE".equals(op)) {
            Object selected=getModel().getSelectedData();
            if(!(selected instanceof StordocVO)) throw new BusinessException("无法确定待修改的仓库。");
            StordocVO warehouse=(StordocVO)selected;pk=warehouse.getPk_stordoc();
            if(pk==null || !group.equals(warehouse.getPk_group())) throw new BusinessException("待修改仓库不属于当前集团。");
        }
        BusinessObjectRef ref=new BusinessObjectRef(UUID.randomUUID().toString(),"BD_STORDOC",pk,headText("code"),null,desired);
        draft=new OperationContext(null,null,group,org,"10140WARH","仓库","Save","保存",op,null,
            Collections.singletonList(ref),OperationContext.OWNER_POLICY_PROTOCOL);
        return draft;
    }
    public Container getParentContainer() { return getModel().getContext().getEntranceUI(); }
    public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }
    private String operation() {
        if(!(getModel() instanceof BillManageModel) || getModel().getContext()==null || !"10140WARH".equals(getModel().getContext().getNodeCode())) return null;
        if(getModel().getUiState()==UIState.ADD) return "INSERT";
        if(!saveAdd && getModel().getUiState()==UIState.EDIT) return "UPDATE";
        return null;
    }
    private BillManageModel model() { return (BillManageModel)getModel(); }
    private String headText(String field) {
        BillItem item=((BillForm)getEditor()).getBillCardPanel().getHeadItem(field);
        Object value=item==null?null:item.getValueObject();
        return value instanceof String && !((String)value).trim().isEmpty()?(String)value:null;
    }
    private boolean isClosing(ActionEvent event) { return event!=null && event.getSource()==this && event.getID()==0 && event.getActionCommand()==null; }
    private BusinessExceptionAdapter saveNotFinished() { return new BusinessExceptionAdapter(new BusinessException("仓库尚未保存，窗口保持打开。")); }
}
