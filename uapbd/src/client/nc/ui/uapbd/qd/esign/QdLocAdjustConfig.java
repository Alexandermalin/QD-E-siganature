package nc.ui.uapbd.qd.esign;

import nc.ui.ic.general.action.GeneralSaveAction;
import nc.ui.ic.m4q.Locadjust_org;
import nc.ui.ic.m4q.action.LocAdjDeleteAction;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.editor.IEditor;

/** Candidate overlay preserving the m4q node's native model, editor and delete action. */
public class QdLocAdjustConfig extends Locadjust_org {
    private GeneralSaveAction saveAction;
    private LocAdjDeleteAction deleteAction;

    @Override public synchronized GeneralSaveAction getSaveAction() {
        if (saveAction != null) return saveAction;
        QdLocAdjustSaveAction action = new QdLocAdjustSaveAction();
        BillManageModel model = null;
        saveAction = action;
        try {
            model = getIcBizModel();
            action.setModel(model);
            action.setEditorModel(getIcBizEditorModel());
            action.setEditor((IEditor) findBeanInUIF2BeanFactory("card"));
            action.setActionName("WRITE");
            setBeanFacotryIfBeanFacatoryAware(action);
            invokeInitializingBean(action);
            return action;
        } catch (RuntimeException error) { saveAction = null; discard(action, model, error); throw error; }
          catch (Error error) { saveAction = null; discard(action, model, error); throw error; }
    }

    @Override public synchronized LocAdjDeleteAction getDeleteAction() {
        if (deleteAction != null) return deleteAction;
        QdLocAdjustDeleteAction action = new QdLocAdjustDeleteAction();
        BillManageModel model = null;
        deleteAction = action;
        try {
            model = getIcBizModel();
            action.setModel(model);
            action.setEditorModel(getIcBizEditorModel());
            action.setEditor((IEditor) findBeanInUIF2BeanFactory("card"));
            action.setActionName("DELETE");
            setBeanFacotryIfBeanFacatoryAware(action);
            invokeInitializingBean(action);
            return action;
        } catch (RuntimeException error) { deleteAction = null; discard(action, model, error); throw error; }
          catch (Error error) { deleteAction = null; discard(action, model, error); throw error; }
    }

    private void discard(AbstractScriptExcAction action, BillManageModel model, Throwable error) {
        if (model == null) return;
        try { model.removeAppEventListener(action); model.removeAppEventListener(action); }
        catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
    }
}
