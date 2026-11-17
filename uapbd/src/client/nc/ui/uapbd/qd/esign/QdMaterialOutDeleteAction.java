package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.ui.ic.general.action.GeneralDeleteAction;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.vo.ic.m4d.entity.MaterialOutVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Candidate 材料出库删除 action retaining the original NC action behavior. */
public final class QdMaterialOutDeleteAction extends GeneralDeleteAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private final InventoryStateCoordinator coordinator = new InventoryStateCoordinator(new InventoryStateCoordinator.Host() {
        public AbstractScriptExcAction action() { return QdMaterialOutDeleteAction.this; }
        public BillManageModel model() { return getModel(); }
        public void performNative(ActionEvent event) { QdMaterialOutDeleteAction.super.actionPerformed(event); }
        public void fillNativeContext(PFlowContext flow) { QdMaterialOutDeleteAction.super.fillUpContext(flow); }
        public boolean isComposite() { return QdMaterialOutDeleteAction.this.isComposite; }
        public boolean resumeNative(IResumeException error) { return QdMaterialOutDeleteAction.super.isResume(error); }
    }, "40080804", "材料出库", "IC_MATERIAL_OUT", MaterialOutVO.class, "Delete", "删除", "DELETE", "DELETE");

    @Override public void actionPerformed(ActionEvent event) { coordinator.perform(event); }
    @Override public void doAction(final ActionEvent event) throws Exception {
        if (!coordinator.requiresConfirmedDelete()) { super.doAction(event); return; }
        coordinator.deleteAfterConfirmation(event, new InventoryStateCoordinator.ConfirmedDelete() {
            public boolean hasDataPermission() { return QdMaterialOutDeleteAction.super.checkDataPermission(); }
            public void showPermissionDenied() { InventoryPfPreparation.showNativeDataPermissionDenied(getModel().getContext()); }
            public void run() throws Exception { QdMaterialOutDeleteAction.super.doSuperAction(event); }
        });
    }
    @Override protected boolean checkDataPermission() {
        boolean allowed = super.checkDataPermission();
        if (coordinator != null) coordinator.observeDeletePermission(allowed);
        return allowed;
    }
    @Override public void procFlow(PFlowContext flow) throws Exception {
        if (coordinator.takesOverFlow()) coordinator.processFlow(flow); else super.procFlow(flow);
    }
    @Override public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        return coordinator.createContext(action, event);
    }
    @Override public boolean isAvailable(OperationContext context) { return coordinator.isAvailable(context); }
    @Override public boolean bind(OperationContext context) { return coordinator.bind(context); }
    @Override public boolean wasDispatched(String requestId) { return coordinator.wasDispatched(requestId); }
    @Override public void release(String requestId) { coordinator.release(requestId); }
    @Override public Container getParentContainer() { return coordinator.getParentContainer(); }
    @Override public String getCurrentUserDisplayName() { return coordinator.getCurrentUserDisplayName(); }
}
