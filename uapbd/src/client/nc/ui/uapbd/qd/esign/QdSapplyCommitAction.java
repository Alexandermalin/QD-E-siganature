package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.ui.ic.general.action.GeneralCommitAction;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.editor.IEditor;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Candidate 出库申请单提交 action retaining the original NC action behavior. */
public final class QdSapplyCommitAction extends GeneralCommitAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private SapplySaveCommitHandoff saveCommit;
    private final InventoryStateCoordinator coordinator = new InventoryStateCoordinator(new InventoryStateCoordinator.SelectionHost() {
        public AbstractScriptExcAction action() { return QdSapplyCommitAction.this; }
        public BillManageModel model() { return getModel(); }
        public void performNative(ActionEvent event) { QdSapplyCommitAction.super.actionPerformed(event); }
        public void fillNativeContext(PFlowContext flow) { QdSapplyCommitAction.this.fillUpContext(flow); }
        public boolean isComposite() { return QdSapplyCommitAction.this.isComposite; }
        public boolean resumeNative(IResumeException error) { return QdSapplyCommitAction.super.isResume(error); }
        public Object[] selectedBills() throws BusinessException {
            return saveCommit == null ? getModel().getSelectedOperaDatas() : saveCommit.selectedBills();
        }
    }, "40080801", "出库申请单", "IC_SAPPLY", SapplyBillVO.class, "Commit", "提交", "UPDATE", "SAVE");

    void performAfterSave(ActionEvent event, SapplySaveCommitHandoff handoff) throws BusinessException {
        if (saveCommit != null || !handoff.matches(event, getModel())) {
            throw SapplySaveCommitHandoff.unconfirmed();
        }
        saveCommit = handoff;
        try {
            actionPerformed(event);
        } finally {
            saveCommit = null;
        }
    }

    IEditor flowEditor() { return editor; }

    @Override public void actionPerformed(ActionEvent event) {
        if ((saveCommit != null && !saveCommit.matches(event, getModel()))
                || (saveCommit == null && SapplySaveCommitHandoff.isCompositeEvent(event))) {
            throw new BusinessExceptionAdapter(SapplySaveCommitHandoff.unconfirmed());
        }
        coordinator.perform(event);
    }

    @Override public void doAction(ActionEvent event) throws Exception {
        coordinator.guardDoAction(event);
        if (saveCommit != null) saveCommit.selectedBills();
        super.doAction(event);
    }

    @Override public void procFlow(PFlowContext flow) throws Exception {
        if (coordinator.takesOverFlow()) coordinator.processFlow(flow); else super.procFlow(flow);
    }
    @Override protected void fillUpContext(PFlowContext flow) {
        super.fillUpContext(flow);
        if (saveCommit != null) {
            try {
                if (!"SAVE".equals(flow.getActionName())) throw SapplySaveCommitHandoff.unconfirmed();
                saveCommit.selectedBills();
                saveCommit.requireBill(flow.getBillVos());
            } catch (BusinessException failure) {
                throw new BusinessExceptionAdapter(failure);
            }
        }
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
