package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import java.util.Map;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.bs.uif2.validation.IValidationService;
import nc.bs.uif2.validation.ValidationException;
import nc.ui.ic.m45.PurchaseIn_Org;
import nc.ui.ic.m45.action.PurchaseInSaveAction;
import nc.ui.ic.pub.model.ICBizEditorModel;
import nc.ui.ic.pub.view.ICBizBillForm;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.AppUiState;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.ui.uif2.AppEventListener;
import nc.vo.ic.m45.entity.PurchaseInVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.scmpub.res.billtype.ICBillType;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uif2.LoginContext;

/** Verifies the native configuration inheritance and the original PF validation path. */
public final class OutsourceInClientConfigTests {
    private static final IBillMeta META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "PURCHASE_CLIENT_TEST"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    public static void run() throws Exception {
        InventoryPfPreparation.verifyNativeContract();
        require(PurchaseIn_Org.class.isAssignableFrom(QdOutsourceInConfig.class),
                "Candidate config must extend the native Java configuration");
        require(PurchaseInSaveAction.class.isAssignableFrom(QdOutsourceInSaveAction.class),
                "Candidate action must extend the native purchase-in action");

        ProbeConfig config = new ProbeConfig();
        config.hookUIF2BeanFactory(new UIF2BeanFactory());
        PurchaseInSaveAction action = config.getSaveAction();
        require(action instanceof QdOutsourceInSaveAction && action == config.getSaveAction(),
                "Save bean must be the cached controlled action");
        require(config.containsBeanDefinition("saveAction") && config.doGetBean("saveAction") == action,
                "Native JavaBeanDefinition lookup must resolve the controlled action");
        require(action.getModel() == config.model && action.getEditorModel() == config.editorModel
                && action.getValidationService() == config.validation, "Native model and validation bindings must survive");
        QdOutsourceInSaveAction controlled = (QdOutsourceInSaveAction) action;
        require(controlled.isAvailable(context("INSERT", null)) && controlled.isAvailable(context("UPDATE", "A")),
                "Purchase insert and update should admit the verified PF client");
        require(!controlled.isAvailable(context("DELETE", "A")), "Delete is outside the save action");
        config.state = AppUiState.ADD;
        config.model.getContext().setNodeCode("40080602400");
        try { controlled.doAction(null); throw new AssertionError("Direct doAction bypassed signing"); }
        catch (BusinessException expected) { }
        try { controlled.procFlow(controlled.getFlowContext()); throw new AssertionError("Direct PF call bypassed signing"); }
        catch (BusinessException expected) { }

        FailingConfig failed = new FailingConfig();
        failed.hookUIF2BeanFactory(new UIF2BeanFactory());
        int originalListeners = failed.model.getAppEventListener(AppEventListener.class).length;
        try { failed.getSaveAction(); throw new AssertionError("Failed bean initialization was ignored"); }
        catch (IllegalStateException expected) { }
        require(failed.model.getAppEventListener(AppEventListener.class).length == originalListeners,
                "Failed initialization must remove its native model listeners");
        failed.fail = false;
        require(failed.getSaveAction() instanceof QdOutsourceInSaveAction,
                "Failed bean initialization must not poison a later retry");

        ValidationAction validationAction = new ValidationAction();
        CountingValidation validator = new CountingValidation();
        validationAction.setValidationService(validator);
        InventoryPfPreparation.validate(validationAction);
        require(validator.calls == 1 && validationAction.beforeChecks == 1,
                "Original validator and beforeCheck must each execute once");
        validator.fail = true;
        try { InventoryPfPreparation.validate(validationAction); throw new AssertionError("Validation failure was ignored"); }
        catch (BusinessExceptionAdapter expected) { }
        require(validationAction.beforeChecks == 1, "Failed validation must stop the native beforeCheck");
        System.out.println("PASS OutsourceInClientConfigTests (native config and validation; no NC session)");
    }

    private static OperationContext context(String operation, String pk) {
        return new OperationContext(null, null, "G", "O", "40080602400", "委外加工入库", "Save", "保存", operation,
                null, Collections.singletonList(new BusinessObjectRef("item", "IC_OUTSOURCE_IN", pk, null, null)));
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }

    private static class ProbeConfig extends QdOutsourceInConfig {
        AppUiState state = AppUiState.NOT_EDIT;
        final BillManageModel model = new BillManageModel() {
            public AppUiState getAppUiState() { return state; }
        };
        final ICBizEditorModel editorModel = new ICBizEditorModel(new ICBizBillForm(), null) {
            public ICBillType getICBillType() { return ICBillType.PurchaseIn; }
        };
        final CountingValidation validation = new CountingValidation();
        ProbeConfig() { model.setContext(new LoginContext()); }
        protected Object findBeanInUIF2BeanFactory(String name) {
            if ("icBizModel".equals(name)) return model;
            if ("icBizEditorModel".equals(name)) return editorModel;
            if ("validateService".equals(name)) return validation;
            if ("card".equals(name)) return null;
            throw new AssertionError("Unexpected bean " + name);
        }
    }
    private static final class FailingConfig extends ProbeConfig {
        boolean fail = true;
        protected Object findBeanInUIF2BeanFactory(String name) {
            if (fail && "icBizEditorModel".equals(name)) throw new IllegalStateException("fixture failure");
            return super.findBeanInUIF2BeanFactory(name);
        }
    }
    private static final class CountingValidation implements IValidationService {
        int calls;
        boolean fail;
        public void validate(Object value) throws ValidationException {
            calls++;
            if (fail) throw new ValidationException();
        }
    }
    private static final class ValidationAction extends AbstractScriptExcAction {
        private static final long serialVersionUID = 1L;
        private final PFlowContext flow = new PFlowContext();
        int beforeChecks;
        ValidationAction() {
            flow.setBillVos(new AbstractBill[] { new PurchaseInVO() {
                public IBillMeta getMetaData() { return META; }
            } });
        }
        @Override public PFlowContext getFlowContext() { return flow; }
        @Override protected void fillUpContext(PFlowContext context) { }
        @Override protected Object getFullBill(Object bill) { return bill; }
        @Override protected void beforeCheck(Object bill) { beforeChecks++; }
        @Override protected void processReturnObj(Object[] result) { }
    }
}
