package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/** Checks the tested native configuration and action boundary without an NC session. */
public final class SaleOrderPilotConfigTests {
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        SaleOrderPfPreparation.verifyNativeContract();
        String config = "nc/ui/uapbd/qd/esign/QdSaleOrderConfig.xml";
        try {
            Class.forName("nc.ui.uapbd.qd.esign.QdSaleOrderConfig");
            throw new AssertionError("A Java configuration shadows the pilot XML");
        } catch (ClassNotFoundException expected) { }
        XmlBeanFactory definitions = new XmlBeanFactory(new ClassPathResource(config, UIF2BeanFactory.class.getClassLoader()));
        if (!QdSaleOrderSaveAction.class.getName().equals(definitions.getBeanDefinition("saveAction").getBeanClassName())) {
            throw new AssertionError("Pilot must replace only the native save action");
        }
        if (!"nc.ui.so.m30.billui.action.SaleOrderSaveAndSendAction".equals(
                definitions.getBeanDefinition("saveandsendApproveAction").getBeanClassName())) {
            throw new AssertionError("Native save-and-send action must remain configured");
        }
        if (!definitions.containsBeanDefinition("cardRefreshAction")) {
            throw new AssertionError("Native sales card refresh is missing");
        }
        if (!"nc.ui.uapbd.qd.esign.QdSaleOrderFreezeAction".equals(
                definitions.getBeanDefinition("freezeAction").getBeanClassName())) {
            throw new AssertionError("Freeze must use the signed overlay action");
        }
        if (!"nc.ui.uapbd.qd.esign.QdSaleOrderCloseAction".equals(
                definitions.getBeanDefinition("closeAction").getBeanClassName())) {
            throw new AssertionError("Bill close must use the signed overlay action");
        }
        if (!"nc.ui.uapbd.qd.esign.QdSaleOrderUnFreezeAction".equals(
                definitions.getBeanDefinition("unFreezeAction").getBeanClassName())) {
            throw new AssertionError("Unfreeze must use the signed overlay action");
        }
        if (!"nc.ui.uapbd.qd.esign.QdSaleOrderDeleteAction".equals(
                definitions.getBeanDefinition("deleteAction").getBeanClassName())) {
            throw new AssertionError("Delete must use the signed overlay action");
        }
        if (!"nc.ui.uapbd.qd.esign.QdSaleOrderApproveAction".equals(
                definitions.getBeanDefinition("approveAction").getBeanClassName())) {
            throw new AssertionError("Approve must use the signed overlay action");
        }
        if (!"nc.ui.uapbd.qd.esign.QdSaleOrderUnApproveAction".equals(
                definitions.getBeanDefinition("unApproveAction").getBeanClassName())) {
            throw new AssertionError("Unapprove must use the signed overlay action");
        }
        if (!"nc.ui.uapbd.qd.esign.QdSaleOrderSendApproveAction".equals(
                definitions.getBeanDefinition("sendApproveAction").getBeanClassName())) {
            throw new AssertionError("Commit must use the signed overlay action");
        }
        if (!"nc.ui.uapbd.qd.esign.QdSaleOrderUnSendApproveAction".equals(
                definitions.getBeanDefinition("unsendApproveAction").getBeanClassName())) {
            throw new AssertionError("Uncommit must use the signed overlay action");
        }
        QdSaleOrderSaveAction action = new QdSaleOrderSaveAction();
        if (!action.isAvailable(context("40060301", "Save", "INSERT", null))) {
            throw new AssertionError("Single new sales order must be available for this native PF version");
        }
        if (action.isAvailable(context("40060301", "Save", "UPDATE", "EXISTING"))
                || action.isAvailable(context("40060301", "Commit", "INSERT", null))
                || action.isAvailable(context("OTHER", "Save", "INSERT", null))) {
            throw new AssertionError("Unsupported actions must not obtain a sales-save handoff");
        }
        OperationContext otherOrg = new OperationContext(null, null, SaleOrderPilotScope.GROUP_ID, "OTHER",
                "40060301", "Sales", "Save", "Save", "INSERT", null,
                Collections.singletonList(new BusinessObjectRef("draft", "SO_SALE_ORDER", null, null, null)));
        if (action.isAvailable(otherOrg)) { throw new AssertionError("Other organizations must not be admitted"); }
        SaleOrderSaveHandoff handoff = new SaleOrderSaveHandoff();
        OperationContext signed = context("40060301", "Save", "INSERT", null).withRequest("R1", "R1", SaleOrderPilotScope.GROUP_ID);
        if (!handoff.bind(signed) || handoff.bind(signed)) { throw new AssertionError("Authorization binding must be one-time"); }
        expectRejected(handoff, SaleOrderPilotScope.GROUP_ID, "OTHER", "KEY");
        if (!"R1".equals(handoff.dispatch(SaleOrderPilotScope.GROUP_ID, SaleOrderPilotScope.ORG_ID, "KEY")) || !handoff.wasDispatched("R1")) {
            throw new AssertionError("Authorized save was not dispatched");
        }
        expectRejected(handoff, SaleOrderPilotScope.GROUP_ID, SaleOrderPilotScope.ORG_ID, "KEY");
        handoff.release("OTHER");
        if (!handoff.wasDispatched("R1")) { throw new AssertionError("Another request must not release the handoff"); }
        handoff.release("R1");
        expectRejected(handoff, SaleOrderPilotScope.GROUP_ID, SaleOrderPilotScope.ORG_ID, "KEY");
        System.out.println("PASS SaleOrderPilotConfigTests (native XML and pinned PF client; no NC session)");
    }
    private static void expectRejected(SaleOrderSaveHandoff handoff, String group, String org, String key) throws Exception {
        try { handoff.dispatch(group, org, key); } catch (BusinessException expected) { return; }
        throw new AssertionError("Unsafe or duplicate sales request was dispatched");
    }
    private static OperationContext context(String node, String button, String operation, String key) {
        return new OperationContext(null, null, SaleOrderPilotScope.GROUP_ID, SaleOrderPilotScope.ORG_ID, node, "Sales", button, "Button", operation, null,
                Collections.singletonList(new BusinessObjectRef("draft", "SO_SALE_ORDER", key, null, null)));
    }
}
