package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.ic.general.action.GeneralSaveCommitAction;
import nc.vo.pub.BusinessException;

/** Runs the two native button actions in order, using the WRITE result to select the submission. */
final class QdSapplySaveCommitAction extends GeneralSaveCommitAction {
    private static final long serialVersionUID = 1L;
    private boolean running;

    QdSapplySaveCommitAction(QdSapplySaveAction save, QdSapplyCommitAction commit) {
        super(save, commit);
    }

    @Override public void doAction(ActionEvent event) throws Exception {
        if (running) throw new BusinessException("出库申请保存并提交正在处理，请勿重复触发。");
        if (event == null || getModel() == null || !isActionEnable() || !(getSaveAction() instanceof QdSapplySaveAction)
                || !(getCommitAction() instanceof QdSapplyCommitAction)) {
            throw SapplySaveCommitHandoff.unconfirmed();
        }
        QdSapplySaveAction save = (QdSapplySaveAction) getSaveAction();
        QdSapplyCommitAction commit = (QdSapplyCommitAction) getCommitAction();
        if (getModel() != save.getModel() || getModel() != commit.getModel()) {
            throw SapplySaveCommitHandoff.unconfirmed();
        }
        Object originalSource = event.getSource();
        running = true;
        try {
            SapplySaveCommitHandoff handoff = new SapplySaveCommitHandoff(event, getModel(), commit.flowEditor());
            save.performForCommit(event, handoff);
            if (handoff.readyToSubmit()) {
                setExceptionHandler(commit.getExceptionHandler());
                commit.performAfterSave(event, handoff);
            }
        } finally {
            event.setSource(originalSource);
            running = false;
        }
    }
}
