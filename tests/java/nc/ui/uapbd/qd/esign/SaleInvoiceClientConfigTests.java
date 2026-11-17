package nc.ui.uapbd.qd.esign;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import nc.ui.so.m32.billui.action.SaleInvoiceApproveAction;
import nc.ui.so.m32.billui.action.SaleInvoiceCommitAction;
import nc.ui.so.m32.billui.action.SaleInvoiceSaveAction;
import nc.ui.so.m32.billui.action.SaleInvoiceUnApproveAction;
import nc.ui.so.m32.billui.action.SaleInvoiceUnCommitAction;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * 销售发票维护（节点 40060501）客户端覆盖层的离线配置检查。
 *
 * <p>解析 {@code QdSaleInvoiceConfig.xml}，核对只替换 5 个 PF 动作 bean、保留原生 import 与
 * deleteAction（删除本批关闭）、属性与原生同名 bean 一致；并核对动作类继承关系、签名接口与
 * 共享白名单的 6 按钮启用边界。不启动 NC 会话、不打开界面。</p>
 */
public final class SaleInvoiceClientConfigTests {
    private static int assertions;
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        // 原生 PF 客户端版本钉死：覆盖层复用同一套反射准备与审批窗口入口。
        SalesPfPreparation.verifyNativeContract();
        String config = "nc/ui/uapbd/qd/esign/QdSaleInvoiceConfig.xml";
        try {
            Class.forName("nc.ui.uapbd.qd.esign.QdSaleInvoiceConfig");
            throw new AssertionError("A Java configuration shadows the invoice XML");
        } catch (ClassNotFoundException expected) { }
        XmlBeanFactory definitions = new XmlBeanFactory(
                new ClassPathResource(config, UIF2BeanFactory.class.getClassLoader()));
        nativeBeansSurvive(definitions);
        overlays(definitions);
        actionBoundary();
        scopeBoundary();
        System.out.println("PASS SaleInvoiceClientConfigTests assertions=" + assertions
                + " (invoice XML overlay and pinned PF client; no NC session)");
    }

    /** import 必须保留原生 bean：删除动作、保存并提交、组合校验器与原生查询动作都不被覆盖。 */
    private static void nativeBeansSurvive(XmlBeanFactory definitions) {
        require("nc.ui.so.m32.billui.action.SaleInvoiceDeleteAction".equals(
                definitions.getBeanDefinition("deleteAction").getBeanClassName()),
                "Delete must stay native: the confirm dialog order is not verified for this batch");
        require("nc.ui.so.m32.billui.action.SaleInvoiceSaveCommitAction".equals(
                definitions.getBeanDefinition("saveandcommitAction").getBeanClassName()),
                "Native save-and-commit must stay configured");
        require(definitions.containsBeanDefinition("compositevalidateService"),
                "Native composite validator must survive the import");
        require(definitions.containsBeanDefinition("validateService"),
                "Native non-empty validator must survive the import");
        require("nc.ui.pubapp.uif2app.query2.action.DefaultQueryAction".equals(
                definitions.getBeanDefinition("queryAction").getBeanClassName()),
                "Native query action must survive the import");
        require(definitions.containsBeanDefinition("commitGroupAction")
                && definitions.containsBeanDefinition("approveGroupAction"),
                "Native commit and approve button groups must survive the import");
    }

    /** 5 个 PF 动作 bean：覆盖类、原生动作名、交易类型、校验器与流程填充标记逐项一致。 */
    private static void overlays(XmlBeanFactory definitions) {
        overlay(definitions, "saveAction", QdSaleInvoiceSaveAction.class, "WRITE",
                "compositevalidateService", false);
        overlay(definitions, "commitAction", QdSaleInvoiceCommitAction.class, "SAVE",
                "powercommitvalidservice", true);
        overlay(definitions, "unCommitAction", QdSaleInvoiceUnCommitAction.class, "UNSAVE",
                "poweruncommitvalidservice", true);
        overlay(definitions, "approveAction", QdSaleInvoiceApproveAction.class, "APPROVE",
                "powerapprovevalidservice", true);
        overlay(definitions, "unApproveAction", QdSaleInvoiceUnApproveAction.class, "UNAPPROVE",
                "powerunapprovevalidservice", true);
    }

    private static void overlay(XmlBeanFactory definitions, String bean, Class<?> expected,
            String actionName, String validator, boolean filledUpInFlow) {
        require(expected.getName().equals(definitions.getBeanDefinition(bean).getBeanClassName()),
                bean + " must use the signed overlay action");
        require("ManageAppModel".equals(ref(definitions, bean, "model")),
                bean + " keeps the native model");
        require("billFormEditor".equals(ref(definitions, bean, "editor")),
                bean + " keeps the native editor");
        require(actionName.equals(text(definitions, bean, "actionName")),
                bean + " keeps the native PF action name " + actionName);
        require("32".equals(text(definitions, bean, "billType")),
                bean + " keeps the invoice bill type 32");
        require(validator.equals(ref(definitions, bean, "validationService")),
                bean + " keeps the native validator " + validator);
        if (filledUpInFlow) {
            require("true".equals(text(definitions, bean, "filledUpInFlow")),
                    bean + " keeps the native filledUpInFlow flag");
        } else {
            require(definitions.getBeanDefinition(bean).getPropertyValues()
                    .getPropertyValue("filledUpInFlow") == null,
                    bean + " must not invent the native filledUpInFlow flag");
        }
    }

    /** 动作类必须继承原生类并实现签名上下文与授权交接接口。 */
    private static void actionBoundary() {
        require(SaleInvoiceSaveAction.class.isAssignableFrom(QdSaleInvoiceSaveAction.class),
                "Save overlay must extend the native sale-invoice save action");
        require(SaleInvoiceCommitAction.class.isAssignableFrom(QdSaleInvoiceCommitAction.class),
                "Commit overlay must extend the native sale-invoice commit action");
        require(SaleInvoiceUnCommitAction.class.isAssignableFrom(QdSaleInvoiceUnCommitAction.class),
                "Uncommit overlay must extend the native sale-invoice uncommit action");
        require(SaleInvoiceApproveAction.class.isAssignableFrom(QdSaleInvoiceApproveAction.class),
                "Approve overlay must extend the native sale-invoice approve action");
        require(SaleInvoiceUnApproveAction.class.isAssignableFrom(QdSaleInvoiceUnApproveAction.class),
                "Unapprove overlay must extend the native sale-invoice unapprove action");
        Class<?>[] overlays = new Class<?>[] { QdSaleInvoiceSaveAction.class, QdSaleInvoiceCommitAction.class,
            QdSaleInvoiceUnCommitAction.class, QdSaleInvoiceApproveAction.class,
            QdSaleInvoiceUnApproveAction.class };
        for (Class<?> overlay : overlays) {
            require(OperationContextProvider.class.isAssignableFrom(overlay),
                    overlay.getSimpleName() + " must provide the signed context");
            require(AuthorizedRequestConsumer.class.isAssignableFrom(overlay),
                    overlay.getSimpleName() + " must consume the one-time authorization");
        }
    }

    /** 共享白名单的 6 按钮启用边界与固定映射。 */
    private static void scopeBoundary() {
        String node = SalesOtherNodeScope.INVOICE_NODE;
        require("40060501".equals(node), "invoice node code is fixed");
        require("销售发票维护".equals(SalesOtherNodeScope.nodeName(node)), "invoice node name is fixed");
        require("FINANCEORGTYPE000000".equals(SalesOtherNodeScope.orgTypeCode(node)),
                "invoice registration organization type is the finance organization");
        require(SalesOtherNodeScope.SALE_INVOICE.equals(SalesOtherNodeScope.objectType(node)),
                "invoice aggregate object type is fixed");
        require(SalesOtherNodeScope.SALE_INVOICE_LINE.equals(SalesOtherNodeScope.lineObjectType(node)),
                "invoice line object type is fixed");
        Map<String, String> scopes = SalesOtherNodeScope.buttonScopes(node);
        require(scopes.size() == 6, "six invoice buttons are registered");
        Set<String> scopeIds = new HashSet<String>(scopes.values());
        require(scopeIds.size() == 6, "each invoice button keeps a distinct scope id");
        require("QD-ES-176DC431DD4F5803".equals(scopes.get("Save")), "save scope id is fixed");
        require("QD-ES-8F8846F2D6E1ECD0".equals(scopes.get("Delete")), "delete scope id is fixed");
        require("QD-ES-56499B75D3E91C33".equals(scopes.get("Commit")), "commit scope id is fixed");
        require("QD-ES-29895E169F92EB91".equals(scopes.get("UnCommit")), "uncommit scope id is fixed");
        require("QD-ES-9AE255EF674BD8E2".equals(scopes.get("Approve")), "approve scope id is fixed");
        require("QD-ES-5EBFFEF0FFE24CAA".equals(scopes.get("UnApprove")), "unapprove scope id is fixed");
        require("INSERT".equals(SalesOtherNodeScope.operationTypeFor(node, "Save")),
                "save keeps the INSERT operation domain");
        require("DELETE".equals(SalesOtherNodeScope.operationTypeFor(node, "Delete")),
                "delete keeps the DELETE operation domain");
        for (String button : new String[] { "Commit", "UnCommit", "Approve", "UnApprove" }) {
            require("UPDATE".equals(SalesOtherNodeScope.operationTypeFor(node, button)),
                    button + " keeps the UPDATE operation domain");
        }
        require(SalesOtherNodeScope.isEnabled(node, "Save"), "save is enabled for the controlled path");
        require(!SalesOtherNodeScope.isEnabled(node, "Delete"), "delete stays closed in this batch");
        for (String button : new String[] { "Commit", "UnCommit", "Approve", "UnApprove" }) {
            require(SalesOtherNodeScope.isEnabled(node, button), button + " is enabled for the controlled path");
        }
        require(SalesOtherNodeScope.enabledButtons(node).size() == 6, "six enable flags are documented");
        require(SalesOtherNodeScope.isAdapter("nc.itf.so.qd.esign.ISaleInvoiceOperationAdapter"),
                "invoice adapter interface is registered in the shared scope");
    }

    private static String ref(XmlBeanFactory definitions, String bean, String property) {
        Object value = definitions.getBeanDefinition(bean).getPropertyValues().getPropertyValue(property).getValue();
        return value instanceof RuntimeBeanReference ? ((RuntimeBeanReference) value).getBeanName() : null;
    }

    private static String text(XmlBeanFactory definitions, String bean, String property) {
        Object value = definitions.getBeanDefinition(bean).getPropertyValues().getPropertyValue(property).getValue();
        if (value instanceof TypedStringValue) { return ((TypedStringValue) value).getValue(); }
        return value == null ? null : value.toString();
    }

    private static void require(boolean valid, String message) {
        if (!valid) { throw new AssertionError(message); }
        assertions++;
    }
}
