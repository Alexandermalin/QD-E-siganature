package nc.ui.uapbd.qd.esign;

import nc.bs.uif2.validation.IValidationService;
import nc.ui.ic.m4c.SaleOut_Org;
import nc.ui.ic.m4c.action.SaleOutSaveAction;
import nc.ui.ic.m4c.action.SaleOutDeleteAction;
import nc.ui.ic.m4c.action.SaleOutSignAction;
import nc.ui.ic.general.action.GeneralCancelSignAction;
import nc.ui.ic.general.model.ICGenBizEditorModel;
import nc.ui.ic.pub.model.ICBizEditorModel;
import nc.ui.ic.pub.view.ICBizBillForm;
import nc.ui.ic.pub.view.ICBizBillListView;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.uif2.editor.IEditor;
import nc.ui.pubapp.uif2app.model.BillManageModel;

/** Candidate JavaBeanDefinition overlay for the sales-out save action. */
public class QdSaleOutConfig extends SaleOut_Org {
    private SaleOutSaveAction saveAction;
    private SaleOutDeleteAction deleteAction;
    private SaleOutSignAction signAction;
    private GeneralCancelSignAction cancelSignAction;

    @Override public synchronized SaleOutSaveAction getSaveAction() {
        if (saveAction != null) return saveAction;
        QdSaleOutSaveAction action = new QdSaleOutSaveAction();
        BillManageModel model = null;
        saveAction = action;
        try {
            model = getIcBizModel();
            action.setModel(model);
            action.setEditorModel((ICBizEditorModel) findBeanInUIF2BeanFactory("icBizEditorModel"));
            action.setEditor(getCard());
            action.setActionName("WRITE");
            action.setValidationService((IValidationService) findBeanInUIF2BeanFactory("validateService"));
            setBeanFacotryIfBeanFacatoryAware(action);
            invokeInitializingBean(action);
            return action;
        } catch (RuntimeException error) { discard(action, model, error); throw error; }
          catch (Error error) { discard(action, model, error); throw error; }
    }

    private void discard(QdSaleOutSaveAction action, BillManageModel model, Throwable error) {
        saveAction = null;
        if (model == null) return;
        try {
            model.removeAppEventListener(action);
            model.removeAppEventListener(action);
        } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
    }

    @Override public synchronized SaleOutDeleteAction getDeleteAction() {
        if (deleteAction != null) return deleteAction;
        QdSaleOutDeleteAction action = new QdSaleOutDeleteAction();
        BillManageModel model = null; deleteAction = action;
        try {
            model = getIcBizModel(); action.setModel(model);
            action.setEditorModel((ICGenBizEditorModel) findBeanInUIF2BeanFactory("icBizEditorModel"));
            action.setEditor(getCard()); action.setActionName("DELETE");
            setBeanFacotryIfBeanFacatoryAware(action); invokeInitializingBean(action);
            return action;
        } catch (RuntimeException error) { deleteAction = null; discardState(action, model, error); throw error; }
          catch (Error error) { deleteAction = null; discardState(action, model, error); throw error; }
    }

    @Override public synchronized SaleOutSignAction getSignAction() {
        if (signAction != null) return signAction;
        QdSaleOutSignAction action = new QdSaleOutSignAction();
        BillManageModel model = null; signAction = action;
        try {
            model = getIcBizModel(); action.setModel(model); action.setActionName("SIGN");
            action.setFilledUpInFlow(false); action.setEditor(getCard()); action.setBillForm(getCard());
            action.setListView(getList());
            setBeanFacotryIfBeanFacatoryAware(action); invokeInitializingBean(action);
            return action;
        } catch (RuntimeException error) { signAction = null; discardState(action, model, error); throw error; }
          catch (Error error) { signAction = null; discardState(action, model, error); throw error; }
    }

    @Override public synchronized GeneralCancelSignAction getCancelSignAction() {
        if (cancelSignAction != null) return cancelSignAction;
        QdSaleOutCancelSignAction action = new QdSaleOutCancelSignAction();
        BillManageModel model = null; cancelSignAction = action;
        try {
            model = (BillManageModel) findBeanInUIF2BeanFactory("icBizModel");
            action.setModel(model); action.setActionName("CANCELSIGN");
            action.setEditor((IEditor) findBeanInUIF2BeanFactory("card"));
            action.setBillForm((ICBizBillForm) findBeanInUIF2BeanFactory("card"));
            action.setListView((ICBizBillListView) findBeanInUIF2BeanFactory("list"));
            setBeanFacotryIfBeanFacatoryAware(action); invokeInitializingBean(action);
            return action;
        } catch (RuntimeException error) { cancelSignAction = null; discardState(action, model, error); throw error; }
          catch (Error error) { cancelSignAction = null; discardState(action, model, error); throw error; }
    }
    private void discardState(AbstractScriptExcAction action, BillManageModel model, Throwable error) {
        if (model == null) return;
        try { model.removeAppEventListener(action); model.removeAppEventListener(action); }
        catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
    }
}
