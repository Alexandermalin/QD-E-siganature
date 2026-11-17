package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import nc.bs.uif2.validation.IValidationService;
import nc.ui.ic.general.action.GeneralDeleteAction;
import nc.ui.ic.general.action.GeneralSaveAction;
import nc.ui.ic.general.action.GeneralCommitAction;
import nc.ui.ic.general.action.GeneralUnCommitAction;
import nc.ui.ic.general.action.GeneralSaveCommitAction;
import nc.ui.ic.general.model.ICGenBizEditorModel;
import nc.ui.ic.general.model.ICGenBizModel;
import nc.ui.ic.general.view.ICBizView;
import nc.ui.ic.m4455.SapplyBill_Org;
import nc.ui.ic.m4455.action.RowCloseAction;
import nc.ui.ic.m4455.action.RowOpenAction;
import nc.ui.ic.m4455.action.SapplyBillApproveAction;
import nc.ui.ic.m4455.action.SapplyBillUnApproveAction;
import nc.ui.ic.pub.env.ICUIContext;
import nc.ui.pubapp.uif2app.AppUiState;
import nc.ui.uif2.AppEventListener;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.scmpub.res.billtype.ICBillType;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uif2.LoginContext;

/** Checks the candidate outbound-request beans using native base models and local doubles. */
public final class SapplyClientConfigTests {
    public static void run() throws Exception {
        require(SapplyBill_Org.class.isAssignableFrom(QdSapplyConfig.class), "Native node configuration must be retained");
        Probe config = new Probe(); config.hookUIF2BeanFactory(new UIF2BeanFactory());
        GeneralSaveAction save = config.getSaveAction();
        GeneralDeleteAction delete = config.getDeleteAction();
        GeneralCommitAction commit = config.getCommitAction();
        GeneralUnCommitAction uncommit = config.getUnCommitAction();
        require(save instanceof QdSapplySaveAction && delete instanceof QdSapplyDeleteAction,
                "Candidate beans must preserve native save/delete subclasses");
        require(commit instanceof QdSapplyCommitAction && uncommit instanceof QdSapplyUnCommitAction,
                "Workflow beans must preserve native commit/withdraw subclasses");
        require(config.doGetBean("saveAction") == save && config.doGetBean("deleteAction") == delete
                && config.doGetBean("commitAction") == commit && config.doGetBean("unCommitAction") == uncommit,
                "Native bean lookup must return the controlled beans");
        require(save.getModel() == config.model && save.getEditorModel() == config.editor
                && save.getValidationService() == config.validation
                && delete.getModel() == config.model && delete.getEditorModel() == config.editor,
                "Native model, editor and save validation bindings must be retained");
        require(commit.getModel() == config.model && commit.getEditorModel() == config.editor
                && uncommit.getModel() == config.model && uncommit.getEditorModel() == config.editor,
                "Submit and withdraw retain their native editor/model bindings");
        GeneralSaveCommitAction savecommit=config.getSavecommitAction();
        require(savecommit instanceof QdSapplySaveCommitAction && savecommit.getModel()==config.model
                && config.doGetBean("savecommitAction")==savecommit && config.getSavecommitAction()==savecommit
                && savecommit.getSaveAction()==save && savecommit.getCommitAction()==commit,
                "Native save-and-submit must delegate to the controlled save and submit actions");
        SapplyBillApproveAction approve=config.getApproveAction();SapplyBillUnApproveAction unapprove=config.getUnApproveAction();
        require(approve instanceof QdSapplyApproveAction && unapprove instanceof QdSapplyUnApproveAction
                && config.doGetBean("approveAction")==approve && config.doGetBean("unApproveAction")==unapprove,
                "Native approval lookup must return controlled subclasses");
        require(approve.getModel()==config.model && approve.getEditorModel()==config.editor
                && unapprove.getModel()==config.model && unapprove.getEditorModel()==config.editor,
                "Approval actions must retain native model and editor bindings");
        require(((AuthorizedRequestConsumer) save).isAvailable(context("Save", "INSERT", null))
                && ((AuthorizedRequestConsumer) save).isAvailable(context("Save", "UPDATE", "A"))
                && ((AuthorizedRequestConsumer) delete).isAvailable(context("Delete", "DELETE", "A")),
                "Outbound request save/delete handoffs must be available");
        require(((AuthorizedRequestConsumer) commit).isAvailable(context("Commit", "UPDATE", "A"))
                && ((AuthorizedRequestConsumer) uncommit).isAvailable(context("UnCommit", "UPDATE", "A")),
                "Outbound request workflow handoffs must be available");
        config.model.getContext().setNodeCode("40080801");
        config.model.setAppUiState(AppUiState.ADD);
        try { save.doAction(null); throw new AssertionError("Direct save bypassed the guard"); }
        catch (BusinessException expected) { }
        try { save.procFlow(save.getFlowContext()); throw new AssertionError("Direct save PF bypassed the guard"); }
        catch (BusinessException expected) { }
        config.model.setAppUiState(AppUiState.NOT_EDIT);
        try { delete.doAction(null); throw new AssertionError("Direct delete bypassed the guard"); }
        catch (BusinessException expected) { }
        try { delete.procFlow(delete.getFlowContext()); throw new AssertionError("Direct delete PF bypassed the guard"); }
        catch (BusinessException expected) { }
        try { commit.doAction(null); throw new AssertionError("Direct submit bypassed the guard"); }
        catch (BusinessException expected) { }
        try { uncommit.doAction(null); throw new AssertionError("Direct withdraw bypassed the guard"); }
        catch (BusinessException expected) { }
        try { commit.procFlow(commit.getFlowContext()); throw new AssertionError("Direct submit PF bypassed the guard"); }
        catch (BusinessException expected) { }
        try { uncommit.procFlow(uncommit.getFlowContext()); throw new AssertionError("Direct withdraw PF bypassed the guard"); }
        catch (BusinessException expected) { }
        try { approve.doAction(null); throw new AssertionError("Direct approval bypassed the guard"); }
        catch (BusinessException expected) { }
        try { unapprove.doAction(null); throw new AssertionError("Direct unapproval bypassed the guard"); }
        catch (BusinessException expected) { }
        try { approve.procFlow(approve.getFlowContext()); throw new AssertionError("Direct approval PF bypassed the guard"); }
        catch (BusinessException expected) { }
        try { unapprove.procFlow(unapprove.getFlowContext()); throw new AssertionError("Direct unapproval PF bypassed the guard"); }
        catch (BusinessException expected) { }
        RowCloseAction close=config.getRowCloseAction();RowOpenAction open=config.getRowOpenAction();
        require(close instanceof QdSapplyRowCloseAction && open instanceof QdSapplyRowOpenAction
                && config.doGetBean("rowCloseAction")==close && config.doGetBean("rowOpenAction")==open,
                "Native row-action lookup must return the controlled subclasses");
        try { close.doAction(null); throw new AssertionError("Direct close bypassed the guard"); }
        catch (BusinessException expected) { }
        try { open.doAction(null); throw new AssertionError("Direct open bypassed the guard"); }
        catch (BusinessException expected) { }
        for (boolean testSave : new boolean[] {true, false}) {
            Probe failing = new Probe(); failing.hookUIF2BeanFactory(new UIF2BeanFactory());
            int listeners = failing.model.getAppEventListener(AppEventListener.class).length;
            failing.failEditor = true;
            try {
                if (testSave) failing.getSaveAction(); else failing.getDeleteAction();
                throw new AssertionError("Bean initialization failure was ignored");
            } catch (IllegalStateException expected) { }
            require(failing.model.getAppEventListener(AppEventListener.class).length == listeners,
                    "Failed bean initialization must not leak listeners");
            failing.failEditor = false;
            require(testSave ? failing.getSaveAction() instanceof QdSapplySaveAction
                    : failing.getDeleteAction() instanceof QdSapplyDeleteAction,
                    "Initialization must be retryable");
        }
        Probe rowFailure=new Probe();rowFailure.hookUIF2BeanFactory(new UIF2BeanFactory());
        int before=rowFailure.model.getAppEventListener(AppEventListener.class).length;rowFailure.failList=true;
        try{rowFailure.getRowCloseAction();throw new AssertionError("Row-action initialization failure was ignored");}
        catch(IllegalStateException expected) { }
        require(rowFailure.model.getAppEventListener(AppEventListener.class).length==before,
                "Failed row-action initialization must not leak listeners");
        rowFailure.failList=false;require(rowFailure.getRowCloseAction() instanceof QdSapplyRowCloseAction,
                "Row-action initialization must be retryable");
        System.out.println("PASS SapplyClientConfigTests (candidate native bindings and handoff; no NC session)");
    }
    private static OperationContext context(String button, String op, String pk) {
        return new OperationContext(null, null, "G", "O", "40080801", "出库申请单", button, button, op, null,
                Collections.singletonList(new BusinessObjectRef("item", "IC_SAPPLY", pk, null, null)));
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class Probe extends QdSapplyConfig {
        final ICGenBizModel model = new ICGenBizModel();
        final ICBizView card = new ICBizView() { public ICGenBizModel getModel(){return model;} };
        final ICGenBizEditorModel editor = new ICGenBizEditorModel(card, null) {
            public ICBillType getICBillType() { return ICBillType.SapplyBill; }
            public ICGenBizModel getICBizModel() { return model; }
        };
        final IValidationService validation = new IValidationService() { public void validate(Object value) { } };
        boolean failEditor,failList;
        Probe() { model.setIcUIContext(new ICUIContext(new LoginContext())); }
        @Override public ICGenBizModel getIcBizModel(){return model;}
        @Override protected Object findBeanInUIF2BeanFactory(String name) {
            if ("icBizModel".equals(name)) return model;
            if ("icBizEditorModel".equals(name)) {
                if (failEditor) throw new IllegalStateException("fixture failure");
                return editor;
            }
            if ("validateService".equals(name)) return validation;
            if ("card".equals(name)) return card;
            if ("list".equals(name)) {if(failList)throw new IllegalStateException("fixture failure");return null;}
            throw new AssertionError("Unexpected bean " + name);
        }
    }
}
