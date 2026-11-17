package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import nc.bs.uif2.validation.IValidationService;
import nc.ui.ic.general.model.ICGenBizModel;
import nc.ui.ic.m4c.SaleOut_Org;
import nc.ui.ic.m4c.action.SaleOutSaveAction;
import nc.ui.ic.m4c.view.SaleOutBizView;
import nc.ui.ic.pub.model.ICBizEditorModel;
import nc.ui.ic.pub.env.ICUIContext;
import nc.ui.pubapp.uif2app.AppUiState;
import nc.ui.uif2.AppEventListener;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.scmpub.res.billtype.ICBillType;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uif2.LoginContext;

/** Tests the native sales-out configuration overlay without an NC session. */
public final class SaleOutClientConfigTests {
    public static void run() throws Exception {
        require(SaleOut_Org.class.isAssignableFrom(QdSaleOutConfig.class), "Native sale-out config must be retained");
        require(SaleOutSaveAction.class.isAssignableFrom(QdSaleOutSaveAction.class), "Native sale-out action must be retained");
        ProbeConfig config = new ProbeConfig();
        config.hookUIF2BeanFactory(new UIF2BeanFactory());
        SaleOutSaveAction action = config.getSaveAction();
        require(action instanceof QdSaleOutSaveAction && action == config.getSaveAction(), "Save action must be cached");
        require(config.containsBeanDefinition("saveAction") && config.doGetBean("saveAction") == action,
                "Native JavaBeanDefinition lookup must resolve the controlled action");
        require(action.getModel() == config.model && action.getEditorModel() == config.editorModel
                && action.getValidationService() == config.validation, "Native sale-out model and validator must survive");
        QdSaleOutSaveAction controlled = (QdSaleOutSaveAction) action;
        require(controlled.isAvailable(context("40080802", "IC_SALE_OUT", "INSERT", null))
                && controlled.isAvailable(context("40080802", "IC_SALE_OUT", "UPDATE", "A")),
                "Sale-out insert and update should admit the verified PF client");
        require(!controlled.isAvailable(context("40080602", "IC_PURCHASE_IN", "INSERT", null))
                && !controlled.isAvailable(context("40080802", "IC_SALE_OUT", "DELETE", "A")),
                "Other nodes and actions must not obtain the sale-out handoff");
        config.state = AppUiState.ADD;
        config.model.getContext().setNodeCode("40080802");
        try { controlled.doAction(null); throw new AssertionError("Direct doAction bypassed signing"); }
        catch (BusinessException expected) { }
        try { controlled.procFlow(controlled.getFlowContext()); throw new AssertionError("Direct PF call bypassed signing"); }
        catch (BusinessException expected) { }

        FailingConfig failed = new FailingConfig();
        failed.hookUIF2BeanFactory(new UIF2BeanFactory());
        int listeners = failed.model.getAppEventListener(AppEventListener.class).length;
        try { failed.getSaveAction(); throw new AssertionError("Failed bean initialization was ignored"); }
        catch (IllegalStateException expected) { }
        require(failed.model.getAppEventListener(AppEventListener.class).length == listeners,
                "Failed initialization must remove its native model listeners");
        failed.fail = false;
        require(failed.getSaveAction() instanceof QdSaleOutSaveAction, "Initialization must be retryable");
        System.out.println("PASS SaleOutClientConfigTests (native config and action; no NC session)");
    }

    private static OperationContext context(String node, String type, String operation, String pk) {
        return new OperationContext(null, null, "G", "O", node, "销售出库", "Save", "保存", operation, null,
                Collections.singletonList(new BusinessObjectRef("item", type, pk, null, null)));
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }

    private static class ProbeConfig extends QdSaleOutConfig {
        AppUiState state = AppUiState.NOT_EDIT;
        final ICGenBizModel model = new ICGenBizModel() { public AppUiState getAppUiState() { return state; } };
        final SaleOutBizView card = new SaleOutBizView();
        final ICBizEditorModel editorModel = new ICBizEditorModel(card, null) {
            public ICBillType getICBillType() { return ICBillType.SaleOut; }
        };
        final IValidationService validation = new IValidationService() { public void validate(Object value) { } };
        ProbeConfig() { model.setIcUIContext(new ICUIContext(new LoginContext())); }
        @Override public ICGenBizModel getIcBizModel() { return model; }
        @Override public SaleOutBizView getCard() { return card; }
        @Override protected Object findBeanInUIF2BeanFactory(String name) {
            if ("icBizEditorModel".equals(name)) return editorModel;
            if ("validateService".equals(name)) return validation;
            throw new AssertionError("Unexpected bean " + name);
        }
    }
    private static final class FailingConfig extends ProbeConfig {
        boolean fail = true;
        @Override protected Object findBeanInUIF2BeanFactory(String name) {
            if (fail && "icBizEditorModel".equals(name)) throw new IllegalStateException("fixture failure");
            return super.findBeanInUIF2BeanFactory(name);
        }
    }
}
