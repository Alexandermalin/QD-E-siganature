package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.ui.ic.general.action.GeneralSaveAction;
import nc.ui.ic.pub.model.ICBizEditorModel;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.pub.pf.PfUtilClient;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Candidate outbound-request action retaining native validation and result handling. */
public final class QdSapplySaveAction extends GeneralSaveAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private SapplySaveCommitHandoff saveCommit;
    private boolean capturingSave;
    private SapplySaveCommitHandoff.SavedBill returnedBill;
    private final InventorySaveCoordinator coordinator = new InventorySaveCoordinator(new InventorySaveCoordinator.Host() {
        public AbstractScriptExcAction action() { return QdSapplySaveAction.this; }
        public BillManageModel model() { return getModel(); }
        public ICBizEditorModel editorModel() { return getEditorModel(); }
        public void performNative(ActionEvent event) { QdSapplySaveAction.super.actionPerformed(event); }
        public void fillNativeContext(PFlowContext flow) { QdSapplySaveAction.super.fillUpContext(flow); }
        public boolean isComposite() { return QdSapplySaveAction.this.isComposite; }
        public boolean resumeNative(IResumeException error) { return QdSapplySaveAction.super.isResume(error); }
        public void setNativeExecuted(boolean value) { QdSapplySaveAction.super.setExecuted(value); }
    }, "40080801", "出库申请单", "IC_SAPPLY", SapplyBillVO.class);

    void performForCommit(ActionEvent event, SapplySaveCommitHandoff handoff) {
        if (saveCommit != null || !handoff.matches(event, getModel())) {
            throw new BusinessExceptionAdapter(SapplySaveCommitHandoff.unconfirmed());
        }
        saveCommit = handoff;
        try {
            actionPerformed(event);
        } finally {
            saveCommit = null;
            returnedBill = null;
        }
    }

    @Override public void actionPerformed(ActionEvent event) {
        if ((saveCommit != null && !saveCommit.matches(event, getModel()))
                || (saveCommit == null && SapplySaveCommitHandoff.isCompositeEvent(event))) {
            throw new BusinessExceptionAdapter(SapplySaveCommitHandoff.unconfirmed());
        }
        coordinator.perform(event);
    }

    @Override public void doAction(ActionEvent event) throws Exception {
        coordinator.guardDoAction(event);
        super.doAction(event);
        if (saveCommit != null) saveCommit.recordSave(returnedBill);
    }

    @Override public void procFlow(PFlowContext flow) throws Exception {
        capturingSave = saveCommit != null && flow == getFlowContext() && "WRITE".equals(flow.getActionName());
        try {
            if (coordinator.takesOverFlow()) coordinator.processFlow(flow); else super.procFlow(flow);
        } finally {
            capturingSave = false;
        }
    }

    @Override protected void processReturnObj(Object[] returned) throws Exception {
        SapplySaveCommitHandoff.SavedBill saved = capturingSave
                ? SapplySaveCommitHandoff.savedBill(returned, PfUtilClient.isSuccess()) : null;
        super.processReturnObj(returned);
        if (capturingSave) returnedBill = saved;
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
