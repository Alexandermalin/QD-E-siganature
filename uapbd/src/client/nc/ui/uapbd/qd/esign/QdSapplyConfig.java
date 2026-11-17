package nc.ui.uapbd.qd.esign;

import nc.bs.uif2.validation.IValidationService;
import nc.ui.ic.general.action.GeneralDeleteAction;
import nc.ui.ic.general.action.GeneralSaveAction;
import nc.ui.ic.general.action.GeneralSaveCommitAction;
import nc.ui.ic.general.action.GeneralCommitAction;
import nc.ui.ic.general.action.GeneralUnCommitAction;
import nc.ui.ic.general.model.ICGenBizEditorModel;
import nc.ui.ic.m4455.SapplyBill_Org;
import nc.ui.ic.m4455.action.RowCloseAction;
import nc.ui.ic.m4455.action.RowOpenAction;
import nc.ui.ic.m4455.action.SapplyBillApproveAction;
import nc.ui.ic.m4455.action.SapplyBillUnApproveAction;
import nc.ui.ic.pub.model.ICBizEditorModel;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.editor.IEditor;
import nc.ui.uif2.editor.BillListView;
import nc.ui.uif2.NCAction;
import nc.ui.pubapp.uif2app.view.BillForm;

/** Candidate overlay for outbound-request actions; other native actions remain inherited. */
public class QdSapplyConfig extends SapplyBill_Org {
    private GeneralSaveAction saveAction;
    private GeneralSaveCommitAction saveCommitAction;
    private GeneralDeleteAction deleteAction;
    private GeneralCommitAction commitAction;
    private GeneralUnCommitAction unCommitAction;
    private RowCloseAction rowCloseAction;
    private RowOpenAction rowOpenAction;
    private SapplyBillApproveAction approveAction;
    private SapplyBillUnApproveAction unApproveAction;

    @Override public synchronized GeneralSaveAction getSaveAction() {
        if (saveAction != null) return saveAction;
        QdSapplySaveAction action=new QdSapplySaveAction();
        BillManageModel model=null; saveAction=action;
        try {
            model=(BillManageModel)findBeanInUIF2BeanFactory("icBizModel");
            action.setModel(model);
            action.setEditorModel((ICBizEditorModel)findBeanInUIF2BeanFactory("icBizEditorModel"));
            action.setEditor((IEditor)findBeanInUIF2BeanFactory("card"));
            action.setActionName("WRITE");
            action.setValidationService((IValidationService)findBeanInUIF2BeanFactory("validateService"));
            setBeanFacotryIfBeanFacatoryAware(action); invokeInitializingBean(action);
            return action;
        } catch(RuntimeException failure) { saveAction=null; discard(action,model,failure); throw failure; }
          catch(Error failure) { saveAction=null; discard(action,model,failure); throw failure; }
    }

    @Override public synchronized GeneralSaveCommitAction getSavecommitAction() {
        if (saveCommitAction != null) return saveCommitAction;
        QdSapplySaveAction save = (QdSapplySaveAction) getSaveAction();
        QdSapplyCommitAction commit = (QdSapplyCommitAction) getCommitAction();
        BillManageModel model = save.getModel();
        if (model == null || commit.getModel() != model) {
            throw new IllegalStateException("Outbound request save and commit model bindings differ");
        }
        GeneralSaveCommitAction action = new QdSapplySaveCommitAction(save, commit);
        saveCommitAction = action;
        try {
            action.setModel(model);
            setBeanFacotryIfBeanFacatoryAware(action);
            invokeInitializingBean(action);
            return action;
        } catch (RuntimeException failure) {
            saveCommitAction = null;
            discardRow(action, model, failure);
            throw failure;
        } catch (Error failure) {
            saveCommitAction = null;
            discardRow(action, model, failure);
            throw failure;
        }
    }

    @Override public synchronized GeneralDeleteAction getDeleteAction() {
        if (deleteAction != null) return deleteAction;
        QdSapplyDeleteAction action=new QdSapplyDeleteAction();
        BillManageModel model=null; deleteAction=action;
        try {
            model=(BillManageModel)findBeanInUIF2BeanFactory("icBizModel");
            action.setModel(model);
            action.setEditorModel((ICGenBizEditorModel)findBeanInUIF2BeanFactory("icBizEditorModel"));
            action.setEditor((IEditor)findBeanInUIF2BeanFactory("card"));
            action.setActionName("DELETE");
            setBeanFacotryIfBeanFacatoryAware(action); invokeInitializingBean(action);
            return action;
        } catch(RuntimeException failure) { deleteAction=null; discard(action,model,failure); throw failure; }
          catch(Error failure) { deleteAction=null; discard(action,model,failure); throw failure; }
    }

    @Override public synchronized GeneralCommitAction getCommitAction() {
        if (commitAction != null) return commitAction;
        QdSapplyCommitAction action = new QdSapplyCommitAction();
        BillManageModel model = null; commitAction = action;
        try {
            ICBizEditorModel editor = (ICBizEditorModel) findBeanInUIF2BeanFactory("icBizEditorModel");
            model = editor.getICBizModel();
            if (model == null) throw new IllegalStateException("Outbound request model is unavailable");
            action.setEditorModel(editor);
            action.setActionName("SAVE");
            setBeanFacotryIfBeanFacatoryAware(action); invokeInitializingBean(action);
            return action;
        } catch (RuntimeException failure) { commitAction = null; discard(action,model,failure); throw failure; }
          catch (Error failure) { commitAction = null; discard(action,model,failure); throw failure; }
    }

    @Override public synchronized GeneralUnCommitAction getUnCommitAction() {
        if (unCommitAction != null) return unCommitAction;
        QdSapplyUnCommitAction action = new QdSapplyUnCommitAction();
        BillManageModel model = null; unCommitAction = action;
        try {
            ICBizEditorModel editor = (ICBizEditorModel) findBeanInUIF2BeanFactory("icBizEditorModel");
            model = editor.getICBizModel();
            if (model == null) throw new IllegalStateException("Outbound request model is unavailable");
            action.setEditorModel(editor);
            BillManageModel nativeModel = (BillManageModel) findBeanInUIF2BeanFactory("icBizModel");
            if (nativeModel != model) throw new IllegalStateException("Outbound request model bindings differ");
            action.setModel(nativeModel);
            action.setActionName("UNSAVE");
            setBeanFacotryIfBeanFacatoryAware(action); invokeInitializingBean(action);
            return action;
        } catch (RuntimeException failure) { unCommitAction = null; discard(action,model,failure); throw failure; }
          catch (Error failure) { unCommitAction = null; discard(action,model,failure); throw failure; }
    }

    @Override public synchronized RowCloseAction getRowCloseAction() {
        if(rowCloseAction!=null)return rowCloseAction;
        QdSapplyRowCloseAction action=new QdSapplyRowCloseAction();BillManageModel model=null;rowCloseAction=action;
        try {
            BillForm form=(BillForm)findBeanInUIF2BeanFactory("card");
            ICBizEditorModel editor=(ICBizEditorModel)findBeanInUIF2BeanFactory("icBizEditorModel");
            model=checkedRowModel(form,editor);action.setBillForm(form);
            action.setList((BillListView)findBeanInUIF2BeanFactory("list"));action.setEditorModel(editor);
            setBeanFacotryIfBeanFacatoryAware(action);invokeInitializingBean(action);return action;
        } catch(RuntimeException failure){rowCloseAction=null;discardRow(action,model,failure);throw failure;}
          catch(Error failure){rowCloseAction=null;discardRow(action,model,failure);throw failure;}
    }

    @Override public synchronized RowOpenAction getRowOpenAction() {
        if(rowOpenAction!=null)return rowOpenAction;
        QdSapplyRowOpenAction action=new QdSapplyRowOpenAction();BillManageModel model=null;rowOpenAction=action;
        try {
            BillForm form=(BillForm)findBeanInUIF2BeanFactory("card");
            ICBizEditorModel editor=(ICBizEditorModel)findBeanInUIF2BeanFactory("icBizEditorModel");
            model=checkedRowModel(form,editor);action.setBillForm(form);
            action.setList((BillListView)findBeanInUIF2BeanFactory("list"));action.setEditorModel(editor);
            setBeanFacotryIfBeanFacatoryAware(action);invokeInitializingBean(action);return action;
        } catch(RuntimeException failure){rowOpenAction=null;discardRow(action,model,failure);throw failure;}
          catch(Error failure){rowOpenAction=null;discardRow(action,model,failure);throw failure;}
    }

    private BillManageModel checkedRowModel(BillForm form,ICBizEditorModel editor) {
        if(form==null || editor==null || !(form.getModel() instanceof BillManageModel)
                || form.getModel()!=editor.getICBizModel())
            throw new IllegalStateException("Outbound request row action model bindings differ");
        return (BillManageModel)form.getModel();
    }

    @Override public synchronized SapplyBillApproveAction getApproveAction() {
        if(approveAction!=null)return approveAction;
        QdSapplyApproveAction action=new QdSapplyApproveAction();BillManageModel model=null;approveAction=action;
        try {
            model=getIcBizModel();action.setModel(model);
            ICBizEditorModel editor=(ICBizEditorModel)findBeanInUIF2BeanFactory("icBizEditorModel");
            if(model==null || editor==null || editor.getICBizModel()!=model)
                throw new IllegalStateException("Outbound request approval model bindings differ");
            action.setEditorModel(editor);
            setBeanFacotryIfBeanFacatoryAware(action);invokeInitializingBean(action);return action;
        } catch(RuntimeException failure){approveAction=null;discard(action,model,failure);throw failure;}
          catch(Error failure){approveAction=null;discard(action,model,failure);throw failure;}
    }

    @Override public synchronized SapplyBillUnApproveAction getUnApproveAction() {
        if(unApproveAction!=null)return unApproveAction;
        QdSapplyUnApproveAction action=new QdSapplyUnApproveAction();BillManageModel model=null;unApproveAction=action;
        try {
            model=getIcBizModel();action.setModel(model);
            ICBizEditorModel editor=(ICBizEditorModel)findBeanInUIF2BeanFactory("icBizEditorModel");
            if(model==null || editor==null || editor.getICBizModel()!=model)
                throw new IllegalStateException("Outbound request unapproval model bindings differ");
            action.setEditorModel(editor);
            setBeanFacotryIfBeanFacatoryAware(action);invokeInitializingBean(action);return action;
        } catch(RuntimeException failure){unApproveAction=null;discard(action,model,failure);throw failure;}
          catch(Error failure){unApproveAction=null;discard(action,model,failure);throw failure;}
    }
    private void discardRow(NCAction action,BillManageModel model,Throwable failure) {
        if(model==null)return;
        try{model.removeAppEventListener(action);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
    }

    private void discard(AbstractScriptExcAction action,BillManageModel model,Throwable failure) {
        if (model==null) return;
        // GeneralUnCommitAction can register itself via editor, superclass and subclass.
        try { for (int i=0; i<4; i++) model.removeAppEventListener(action); }
        catch(RuntimeException cleanup) { failure.addSuppressed(cleanup); }
    }
}
