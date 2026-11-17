package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.ui.ic.general.action.GeneralCancelSignAction;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.vo.ic.m46.entity.FinProdInVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Candidate 投产单取消签字 action retaining the original NC action behavior. */
public final class QdProductionOrderCancelSignAction extends GeneralCancelSignAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private final InventoryStateCoordinator coordinator = new InventoryStateCoordinator(new InventoryStateCoordinator.Host() {
        public AbstractScriptExcAction action() { return QdProductionOrderCancelSignAction.this; }
        public BillManageModel model() { return getModel(); }
        public void performNative(ActionEvent event) { QdProductionOrderCancelSignAction.super.actionPerformed(event); }
        public void fillNativeContext(PFlowContext flow) { QdProductionOrderCancelSignAction.super.fillUpContext(flow); }
        public boolean isComposite() { return QdProductionOrderCancelSignAction.this.isComposite; }
        public boolean resumeNative(IResumeException error) { return QdProductionOrderCancelSignAction.super.isResume(error); }
    }, "40080604400", "投产单", "IC_PRODUCTION_ORDER", FinProdInVO.class, "CancelSign", "取消签字", "CANCEL_SIGN", "CANCELSIGN");

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
