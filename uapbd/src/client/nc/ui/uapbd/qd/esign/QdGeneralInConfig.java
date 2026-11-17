package nc.ui.uapbd.qd.esign;

import nc.bs.uif2.validation.IValidationService;
import nc.ui.ic.m4a.GeneralIn_Org;
import nc.ui.ic.general.action.GeneralSaveAction;
import nc.ui.ic.m4a.action.GeneralInSignAction;
import nc.ui.ic.general.action.GeneralCancelSignAction;
import nc.ui.ic.general.action.GeneralDeleteAction;
import nc.ui.ic.general.model.ICGenBizEditorModel;
import nc.ui.ic.pub.action.ScriptAction;
import nc.ui.ic.pub.model.ICBizEditorModel;
import nc.ui.ic.pub.view.ICBizBillForm;
import nc.ui.ic.pub.view.ICBizBillListView;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.editor.IEditor;

/** Candidate JavaBeanDefinition overlay for the general-in save action. */
public class QdGeneralInConfig extends GeneralIn_Org {
    private GeneralSaveAction saveAction;
    private GeneralDeleteAction deleteAction;
    private GeneralInSignAction signAction;
    private GeneralCancelSignAction cancelSignAction;

    @Override public synchronized GeneralSaveAction getSaveAction() {
        if (saveAction != null) return saveAction;
        QdGeneralInSaveAction action = new QdGeneralInSaveAction();
        BillManageModel model = null;
        saveAction = action;
        try {
            model = getIcBizModel();
            action.setModel(model);
            action.setEditorModel((ICBizEditorModel) findBeanInUIF2BeanFactory("icBizEditorModel"));
            action.setEditor((IEditor) findBeanInUIF2BeanFactory("card"));
            action.setActionName("WRITE");
            action.setValidationService((IValidationService) findBeanInUIF2BeanFactory("validateService"));
            action.setExceptionHandler(getMyExceptionHandler());
            setBeanFacotryIfBeanFacatoryAware(action);
            invokeInitializingBean(action);
            return action;
        } catch (RuntimeException error) { discard(action, model, error); throw error; }
          catch (Error error) { discard(action, model, error); throw error; }
    }

    private void discard(QdGeneralInSaveAction action, BillManageModel model, Throwable error) {
        saveAction = null;
        if (model == null) return;
        try {
            // The native action hierarchy can register the listener twice.
            model.removeAppEventListener(action);
            model.removeAppEventListener(action);
        } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
    }

    @Override public synchronized GeneralDeleteAction getDeleteAction() {
        if (deleteAction != null) return deleteAction;
        QdGeneralInDeleteAction action = new QdGeneralInDeleteAction();
        BillManageModel model = null; deleteAction = action;
        try {
            model = (BillManageModel) findBeanInUIF2BeanFactory("icBizModel");
            action.setModel(model);
            action.setEditorModel((ICGenBizEditorModel) findBeanInUIF2BeanFactory("icBizEditorModel"));
            action.setEditor((IEditor) findBeanInUIF2BeanFactory("card"));
            action.setActionName("DELETE");
            setBeanFacotryIfBeanFacatoryAware(action); invokeInitializingBean(action);
            return action;
        } catch (RuntimeException error) { deleteAction = null; discardState(action, model, error); throw error; }
          catch (Error error) { deleteAction = null; discardState(action, model, error); throw error; }
    }

    @Override public synchronized GeneralInSignAction getSignAction() {
        if (signAction != null) return signAction;
        QdGeneralInSignAction action = new QdGeneralInSignAction();
        BillManageModel model = null; signAction = action;
        try {
            model = getIcBizModel();
            wireState(action, model, "SIGN");
            return action;
        } catch (RuntimeException error) { signAction = null; discardState(action, model, error); throw error; }
          catch (Error error) { signAction = null; discardState(action, model, error); throw error; }
    }

    @Override public synchronized GeneralCancelSignAction getCancelSignAction() {
        if (cancelSignAction != null) return cancelSignAction;
        QdGeneralInCancelSignAction action = new QdGeneralInCancelSignAction();
        BillManageModel model = null; cancelSignAction = action;
        try {
            model = (BillManageModel) findBeanInUIF2BeanFactory("icBizModel");
            wireState(action, model, "CANCELSIGN");
            return action;
        } catch (RuntimeException error) { cancelSignAction = null; discardState(action, model, error); throw error; }
          catch (Error error) { cancelSignAction = null; discardState(action, model, error); throw error; }
    }

    private void wireState(ScriptAction action, BillManageModel model, String nativeAction) {
        action.setModel(model); action.setActionName(nativeAction);
        action.setEditor((IEditor) findBeanInUIF2BeanFactory("card"));
        action.setBillForm((ICBizBillForm) findBeanInUIF2BeanFactory("card"));
        action.setListView((ICBizBillListView) findBeanInUIF2BeanFactory("list"));
        setBeanFacotryIfBeanFacatoryAware(action); invokeInitializingBean(action);
    }
    private void discardState(AbstractScriptExcAction action, BillManageModel model, Throwable error) {
        if (model == null) return;
        try { model.removeAppEventListener(action); model.removeAppEventListener(action); }
        catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
    }
}
