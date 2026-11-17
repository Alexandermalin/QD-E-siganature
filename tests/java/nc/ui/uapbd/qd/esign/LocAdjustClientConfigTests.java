package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import nc.ui.ic.general.action.GeneralSaveAction;
import nc.ui.ic.general.view.ICBizView;
import nc.ui.ic.m4q.Locadjust_org;
import nc.ui.ic.m4q.action.LocAdjDeleteAction;
import nc.ui.ic.m4q.model.LocAdjustBizEditorModel;
import nc.ui.ic.m4q.model.LocAdjustGenBizModel;
import nc.ui.ic.pub.env.ICUIContext;
import nc.ui.pubapp.uif2app.AppUiState;
import nc.ui.uif2.AppEventListener;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.scmpub.res.billtype.ICBillType;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uif2.LoginContext;

/** Tests the locally available m4q actions and authorized handoff without an NC session. */
public final class LocAdjustClientConfigTests {
    public static void run() throws Exception {
        require(Locadjust_org.class.isAssignableFrom(QdLocAdjustConfig.class), "Native node configuration must be retained");
        Probe config = new Probe();
        config.hookUIF2BeanFactory(new UIF2BeanFactory());
        GeneralSaveAction save = config.getSaveAction();
        LocAdjDeleteAction delete = config.getDeleteAction();
        require(save instanceof QdLocAdjustSaveAction && delete instanceof QdLocAdjustDeleteAction,
                "Native save and specialized delete actions must be retained");
        require(config.doGetBean("saveAction") == save && config.doGetBean("deleteAction") == delete,
                "Native bean lookup must return the cached controlled actions");
        require(save.getModel() == config.model && save.getEditorModel() == config.editor
                && delete.getModel() == config.model && delete.getEditorModel() == config.editor,
                "Both actions must retain the native model and editor");
        require(save.getValidationService() == null, "Native m4q save does not bind a validation service");
        require(((AuthorizedRequestConsumer) save).isAvailable(context("Save", "INSERT", null))
                && ((AuthorizedRequestConsumer) save).isAvailable(context("Save", "UPDATE", "A"))
                && ((AuthorizedRequestConsumer) delete).isAvailable(context("Delete", "DELETE", "A")),
                "Location adjustment handoff routes must be available");
        config.model.getContext().setNodeCode("40081014");
        config.model.setAppUiState(AppUiState.ADD);
        try { save.doAction(null); throw new AssertionError("Direct save bypassed signing"); }
        catch (BusinessException expected) { }
        try { save.procFlow(save.getFlowContext()); throw new AssertionError("Direct save PF bypassed signing"); }
        catch (BusinessException expected) { }
        config.model.setAppUiState(AppUiState.NOT_EDIT);
        try { delete.doAction(null); throw new AssertionError("Direct delete bypassed signing"); }
        catch (BusinessException expected) { }
        try { delete.procFlow(delete.getFlowContext()); throw new AssertionError("Direct delete PF bypassed signing"); }
        catch (BusinessException expected) { }

        Probe failing = new Probe(); failing.hookUIF2BeanFactory(new UIF2BeanFactory());
        int listeners = failing.model.getAppEventListener(AppEventListener.class).length;
        failing.failEditor = true;
        try { failing.getDeleteAction(); throw new AssertionError("Failed initialization was ignored"); }
        catch (IllegalStateException expected) { }
        require(failing.model.getAppEventListener(AppEventListener.class).length == listeners,
                "Failed initialization must not retain listeners");
        failing.failEditor = false;
        require(failing.getDeleteAction() instanceof QdLocAdjustDeleteAction, "Initialization must be retryable");
        System.out.println("PASS LocAdjustClientConfigTests (native m4q bindings and handoff; no NC session)");
    }

    private static OperationContext context(String button, String op, String pk) {
        return new OperationContext(null, null, "G", "O", "40081014", "货位调整", button, button, op, null,
                Collections.singletonList(new BusinessObjectRef("item", "IC_LOC_ADJUST", pk, null, null)));
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class Probe extends QdLocAdjustConfig {
        final LocAdjustGenBizModel model = new LocAdjustGenBizModel();
        final ICBizView card = new ICBizView();
        final LocAdjustBizEditorModel editor = new LocAdjustBizEditorModel(card, null) {
            public ICBillType getICBillType() { return ICBillType.LocAdjust; }
        };
        boolean failEditor;
        Probe() { model.setIcUIContext(new ICUIContext(new LoginContext())); }
        @Override public LocAdjustGenBizModel getIcBizModel() { return model; }
        @Override public LocAdjustBizEditorModel getIcBizEditorModel() {
            if (failEditor) throw new IllegalStateException("fixture failure");
            return editor;
        }
        @Override protected Object findBeanInUIF2BeanFactory(String name) {
            if ("card".equals(name)) return card;
            throw new AssertionError("Unexpected bean " + name);
        }
    }
}
