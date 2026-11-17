package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.ui.ic.general.action.GeneralDeleteAction;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.vo.ic.m40.entity.InitializtionInVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Candidate 期初余额删除 action retaining the original NC action behavior. */
public final class QdInitialInDeleteAction extends GeneralDeleteAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private final InventoryStateCoordinator coordinator = new InventoryStateCoordinator(new InventoryStateCoordinator.Host() {
        public AbstractScriptExcAction action() { return QdInitialInDeleteAction.this; }
        public BillManageModel model() { return getModel(); }
        public void performNative(ActionEvent event) { QdInitialInDeleteAction.super.actionPerformed(event); }
        public void fillNativeContext(PFlowContext flow) { QdInitialInDeleteAction.super.fillUpContext(flow); }
        public boolean isComposite() { return QdInitialInDeleteAction.this.isComposite; }
        public boolean resumeNative(IResumeException error) { return QdInitialInDeleteAction.super.isResume(error); }
    }, "40080402", "期初余额", "IC_INITIAL_IN", InitializtionInVO.class, "Delete", "删除", "DELETE", "DELETE");

    @Override public void actionPerformed(ActionEvent event) { coordinator.perform(event); }
    @Override public void doAction(final ActionEvent event) throws Exception {
        if (!coordinator.requiresConfirmedDelete()) { super.doAction(event); return; }
        coordinator.deleteAfterConfirmation(event, new InventoryStateCoordinator.ConfirmedDelete() {
            public boolean hasDataPermission() { return QdInitialInDeleteAction.super.checkDataPermission(); }
            public void showPermissionDenied() { InventoryPfPreparation.showNativeDataPermissionDenied(getModel().getContext()); }
            public void run() throws Exception { QdInitialInDeleteAction.super.doSuperAction(event); }
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
