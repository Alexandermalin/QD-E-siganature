package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.ui.ic.general.action.GeneralSaveAction;
import nc.ui.ic.pub.model.ICBizEditorModel;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.vo.ic.m46.entity.FinProdInVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Candidate product-in action retaining native validation and result handling. */
public final class QdProductionOrderSaveAction extends GeneralSaveAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private final InventorySaveCoordinator coordinator = new InventorySaveCoordinator(new InventorySaveCoordinator.Host() {
        public AbstractScriptExcAction action() { return QdProductionOrderSaveAction.this; }
        public BillManageModel model() { return getModel(); }
        public ICBizEditorModel editorModel() { return getEditorModel(); }
        public void performNative(ActionEvent event) { QdProductionOrderSaveAction.super.actionPerformed(event); }
        public void fillNativeContext(PFlowContext flow) { QdProductionOrderSaveAction.super.fillUpContext(flow); }
        public boolean isComposite() { return QdProductionOrderSaveAction.this.isComposite; }
        public boolean resumeNative(IResumeException error) { return QdProductionOrderSaveAction.super.isResume(error); }
        public void setNativeExecuted(boolean value) { QdProductionOrderSaveAction.super.setExecuted(value); }
    }, "40080604400", "投产单", "IC_PRODUCTION_ORDER", FinProdInVO.class);

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
