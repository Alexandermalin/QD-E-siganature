package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.ui.ic.m4a.action.GeneralInSignAction;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.vo.ic.m4a.entity.GeneralInVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Candidate 其他入库签字 action retaining the original NC action behavior. */
public final class QdGeneralInSignAction extends GeneralInSignAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private final InventoryStateCoordinator coordinator = new InventoryStateCoordinator(new InventoryStateCoordinator.Host() {
        public AbstractScriptExcAction action() { return QdGeneralInSignAction.this; }
        public BillManageModel model() { return getModel(); }
        public void performNative(ActionEvent event) { QdGeneralInSignAction.super.actionPerformed(event); }
        public void fillNativeContext(PFlowContext flow) { QdGeneralInSignAction.super.fillUpContext(flow); }
        public boolean isComposite() { return QdGeneralInSignAction.this.isComposite; }
        public boolean resumeNative(IResumeException error) { return QdGeneralInSignAction.super.isResume(error); }
    }, "40080608", "其他入库", "IC_GENERAL_IN", GeneralInVO.class, "Sign", "签字", "SIGN", "SIGN");

    @Override public void actionPerformed(ActionEvent event) { coordinator.perform(event); }
    @Override public void doAction(ActionEvent event) throws Exception { coordinator.guardDoAction(event); super.doAction(event); }
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
