package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import nc.ui.ic.general.model.ICGenBizEditorModel;
import nc.ui.ic.general.model.ICGenBizModel;
import nc.ui.ic.general.view.ICBizView;
import nc.ui.ic.m4c.view.SaleOutBizBillListView;
import nc.ui.ic.m4c.view.SaleOutBizView;
import nc.ui.ic.pub.env.ICUIContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.uif2.AppEventListener;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.scmpub.res.billtype.ICBillType;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uif2.LoginContext;

/** Offline checks that state actions preserve their native configuration and require a handoff. */
public final class InventoryStateClientConfigTests {
    public static void run() throws Exception {
        Purchase purchase = new Purchase(); purchase.hookUIF2BeanFactory(new UIF2BeanFactory());
        require(purchase.getDeleteAction() instanceof QdPurchaseInDeleteAction
                && purchase.getSignAction() instanceof QdPurchaseInSignAction
                && purchase.getCancelSignAction() instanceof QdPurchaseInCancelSignAction,
                "Purchase state beans must use their native action subclasses");
        require(purchase.doGetBean("deleteAction") == purchase.getDeleteAction()
                && purchase.doGetBean("signAction") == purchase.getSignAction()
                && purchase.doGetBean("cancelSignAction") == purchase.getCancelSignAction(),
                "Purchase JavaBeanDefinition must resolve the cached state actions");
        require(purchase.getDeleteAction().getEditorModel() == purchase.editor
                && purchase.getSignAction().getModel() == purchase.model,
                "Purchase state actions must retain their original model bindings");
        purchase.model.getContext().setNodeCode("40080602");
        checkGuard(purchase.getDeleteAction(), "40080602", "IC_PURCHASE_IN", "Delete", "DELETE");
        checkGuard(purchase.getSignAction(), "40080602", "IC_PURCHASE_IN", "Sign", "SIGN");
        checkGuard(purchase.getCancelSignAction(), "40080602", "IC_PURCHASE_IN", "CancelSign", "CANCEL_SIGN");

        Sale sale = new Sale(); sale.hookUIF2BeanFactory(new UIF2BeanFactory());
        require(sale.getDeleteAction() instanceof QdSaleOutDeleteAction
                && sale.getSignAction() instanceof QdSaleOutSignAction
                && sale.getCancelSignAction() instanceof QdSaleOutCancelSignAction,
                "Sale-out state beans must use their native action subclasses");
        require(sale.doGetBean("deleteAction") == sale.getDeleteAction()
                && sale.doGetBean("signAction") == sale.getSignAction()
                && sale.doGetBean("cancelSignAction") == sale.getCancelSignAction(),
                "Sale-out JavaBeanDefinition must resolve the cached state actions");
        require(sale.getDeleteAction().getEditorModel() == sale.editor && sale.getSignAction().getModel() == sale.model,
                "Sale-out state actions must retain their original model bindings");
        sale.model.getContext().setNodeCode("40080802");
        checkGuard(sale.getDeleteAction(), "40080802", "IC_SALE_OUT", "Delete", "DELETE");
        checkGuard(sale.getSignAction(), "40080802", "IC_SALE_OUT", "Sign", "SIGN");
        checkGuard(sale.getCancelSignAction(), "40080802", "IC_SALE_OUT", "CancelSign", "CANCEL_SIGN");

        Purchase failing = new Purchase(); failing.hookUIF2BeanFactory(new UIF2BeanFactory());
        int listeners = failing.model.getAppEventListener(AppEventListener.class).length;
        failing.failList = true;
        try { failing.getSignAction(); throw new AssertionError("Failed state initialization was ignored"); }
        catch (IllegalStateException expected) { }
        require(failing.model.getAppEventListener(AppEventListener.class).length == listeners,
                "Failed state initialization must remove its listeners");
        failing.failList = false;
        require(failing.getSignAction() instanceof QdPurchaseInSignAction, "State bean initialization must be retryable");
        System.out.println("PASS InventoryStateClientConfigTests (native state action bindings; no NC session)");
    }
    private static void checkGuard(AbstractScriptExcAction action, String node, String type, String button, String op)
            throws Exception {
        OperationContext context = new OperationContext(null, null, "G", "O", node, "inventory", button, button, op,
                null, Collections.singletonList(new BusinessObjectRef("C0", type, "A", "CODE", null)));
        require(((AuthorizedRequestConsumer) action).isAvailable(context), "A valid state route was not available");
        try { action.doAction(null); throw new AssertionError("Direct state doAction bypassed signing"); }
        catch (BusinessException expected) { }
        try { action.procFlow(action.getFlowContext()); throw new AssertionError("Direct state PF call bypassed signing"); }
        catch (BusinessException expected) { }
    }
    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }

    private static final class Purchase extends QdPurchaseInConfig {
        final ICGenBizModel model = new ICGenBizModel();
        final ICBizView card = new ICBizView();
        final ICGenBizEditorModel editor = new ICGenBizEditorModel(card, null) {
            public ICBillType getICBillType() { return ICBillType.PurchaseIn; }
        };
        boolean failList;
        Purchase() { model.setIcUIContext(new ICUIContext(new LoginContext())); }
        protected Object findBeanInUIF2BeanFactory(String name) {
            if ("icBizModel".equals(name)) return model;
            if ("icBizEditorModel".equals(name)) return editor;
            if ("card".equals(name)) return card;
            if ("list".equals(name)) {
                if (failList) throw new IllegalStateException("fixture failure");
                return null;
            }
            throw new AssertionError("Unexpected bean " + name);
        }
    }
    private static final class Sale extends QdSaleOutConfig {
        final ICGenBizModel model = new ICGenBizModel();
        final SaleOutBizView card = new SaleOutBizView();
        final ICGenBizEditorModel editor = new ICGenBizEditorModel(card, null) {
            public ICBillType getICBillType() { return ICBillType.SaleOut; }
        };
        Sale() { model.setIcUIContext(new ICUIContext(new LoginContext())); }
        public ICGenBizModel getIcBizModel() { return model; }
        public SaleOutBizView getCard() { return card; }
        public SaleOutBizBillListView getList() { return null; }
        protected Object findBeanInUIF2BeanFactory(String name) {
            if ("icBizModel".equals(name)) return model;
            if ("icBizEditorModel".equals(name)) return editor;
            if ("card".equals(name)) return card;
            if ("list".equals(name)) return null;
            throw new AssertionError("Unexpected bean " + name);
        }
    }
}
