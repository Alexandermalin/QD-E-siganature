package nc.ui.uapbd.qd.esign;

import nc.bs.uif2.validation.IValidationService;
import nc.ui.ic.m45.PurchaseIn_Org;
import nc.ui.ic.m45.action.PurchaseInSaveAction;
import nc.ui.ic.m45.action.PurchaseInSignAction;
import nc.ui.ic.m45.action.PurchaseInCancelSignAction;
import nc.ui.ic.general.action.GeneralDeleteAction;
import nc.ui.ic.general.model.ICGenBizEditorModel;
import nc.ui.ic.pub.action.ScriptAction;
import nc.ui.ic.pub.model.ICBizEditorModel;
import nc.ui.ic.pub.view.ICBizBillForm;
import nc.ui.ic.pub.view.ICBizBillListView;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.editor.IEditor;

/** Candidate JavaBeanDefinition overlay for the purchase-in save action. */
public class QdOutsourceInConfig extends PurchaseIn_Org {
    private PurchaseInSaveAction saveAction;
    private GeneralDeleteAction deleteAction;
    private PurchaseInSignAction signAction;
    private PurchaseInCancelSignAction cancelSignAction;

    @Override public synchronized PurchaseInSaveAction getSaveAction() {
        if (saveAction != null) return saveAction;
        QdOutsourceInSaveAction action = new QdOutsourceInSaveAction();
        BillManageModel model = null;
        saveAction = action;
        try {
            model = (BillManageModel) findBeanInUIF2BeanFactory("icBizModel");
            action.setModel(model);
            action.setEditorModel((ICBizEditorModel) findBeanInUIF2BeanFactory("icBizEditorModel"));
            action.setEditor((IEditor) findBeanInUIF2BeanFactory("card"));
            action.setActionName("WRITE");
            action.setValidationService((IValidationService) findBeanInUIF2BeanFactory("validateService"));
            setBeanFacotryIfBeanFacatoryAware(action);
            invokeInitializingBean(action);
            return action;
        } catch (RuntimeException error) { discard(action, model, error); throw error; }
          catch (Error error) { discard(action, model, error); throw error; }
    }

    private void discard(QdOutsourceInSaveAction action, BillManageModel model, Throwable error) {
        saveAction = null;
        if (model == null) return;
        try {
            // Both the native base action and its inventory subclass register this listener.
            model.removeAppEventListener(action);
            model.removeAppEventListener(action);
        } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
    }

    @Override public synchronized GeneralDeleteAction getDeleteAction() {
        if (deleteAction != null) return deleteAction;
        QdOutsourceInDeleteAction action = new QdOutsourceInDeleteAction();
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

    @Override public synchronized PurchaseInSignAction getSignAction() {
        if (signAction != null) return signAction;
        QdOutsourceInSignAction action = new QdOutsourceInSignAction();
        BillManageModel model = null; signAction = action;
        try {
            model = (BillManageModel) findBeanInUIF2BeanFactory("icBizModel");
            wireState(action, model, "SIGN", true);
            return action;
        } catch (RuntimeException error) { signAction = null; discardState(action, model, error); throw error; }
          catch (Error error) { signAction = null; discardState(action, model, error); throw error; }
    }

    @Override public synchronized PurchaseInCancelSignAction getCancelSignAction() {
        if (cancelSignAction != null) return cancelSignAction;
        QdOutsourceInCancelSignAction action = new QdOutsourceInCancelSignAction();
        BillManageModel model = null; cancelSignAction = action;
        try {
            model = (BillManageModel) findBeanInUIF2BeanFactory("icBizModel");
            wireState(action, model, "CANCELSIGN", false);
            return action;
        } catch (RuntimeException error) { cancelSignAction = null; discardState(action, model, error); throw error; }
          catch (Error error) { cancelSignAction = null; discardState(action, model, error); throw error; }
    }

    private void wireState(ScriptAction action, BillManageModel model, String nativeAction, boolean explicitlyUnfilled) {
        action.setModel(model); action.setActionName(nativeAction);
        if (explicitlyUnfilled) action.setFilledUpInFlow(false);
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
