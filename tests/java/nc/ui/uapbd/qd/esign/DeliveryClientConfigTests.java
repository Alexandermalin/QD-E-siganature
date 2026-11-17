package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * 发货单维护（节点 40060402）客户端配置与动作边界检查。
 *
 * <p>解析 {@code QdDeliveryConfig.xml} 与其导入的原生 {@code delivery_config.xml}，核对只覆盖 5 个 PF 动作、
 * 原生 property 逐项保留、10 个按钮的启用边界只读且与正式清单一致；不打开 NC 界面，不连接服务端。</p>
 */
public final class DeliveryClientConfigTests {
    private static int assertions;
    private static final String CONFIG = "nc/ui/uapbd/qd/esign/QdDeliveryConfig.xml";
    private static final String NATIVE_ACTION_PACKAGE = "nc.ui.so.m4331.billui.action.";

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        noJavaShadow();
        XmlBeanFactory definitions = new XmlBeanFactory(
                new ClassPathResource(CONFIG, UIF2BeanFactory.class.getClassLoader()));
        nativeResourcesResolved(definitions);
        overlaidActions(definitions);
        saveProperties(definitions);
        pfProperties(definitions);
        nativeActionsNotOverlaid(definitions);
        actionClasses();
        scopeBoundaries();
        handoffBoundaries();
        System.out.println("PASS DeliveryClientConfigTests assertions=" + assertions
                + " (delivery client XML, native properties, 10-button scope; no NC session)");
    }

    /** Java 配置类不得遮蔽 XML 配置，避免实际加载到未覆盖的 native bean。 */
    private static void noJavaShadow() throws Exception {
        try {
            Class.forName("nc.ui.uapbd.qd.esign.QdDeliveryConfig");
            throw new AssertionError("A Java configuration shadows the delivery overlay XML");
        } catch (ClassNotFoundException expected) { }
        check(new ClassPathResource(CONFIG, UIF2BeanFactory.class.getClassLoader()).exists(),
                "发货单客户端覆盖配置必须在 classpath 中");
    }

    /** 导入的原生配置必须可用，覆盖配置只在其上替换 5 个 bean。 */
    private static void nativeResourcesResolved(XmlBeanFactory definitions) {
        check(definitions.containsBeanDefinition("ManageAppModel"), "原生领域模型必须来自导入配置");
        check(definitions.containsBeanDefinition("billFormEditor"), "原生卡片编辑器必须来自导入配置");
        check(definitions.containsBeanDefinition("cardRefreshAction"), "原生卡片刷新动作必须可用");
        check(definitions.containsBeanDefinition("powersendapprovevalidservice"), "原生提交权限校验服务必须可用");
        check(definitions.containsBeanDefinition("powerunsendvalidservice"), "原生收回权限校验服务必须可用");
        check(definitions.containsBeanDefinition("powerapprovevalidservice"), "原生审批权限校验服务必须可用");
        check(definitions.containsBeanDefinition("powerunapprovevalidservice"), "原生弃审权限校验服务必须可用");
    }

    /** 只覆盖已核实的 5 个 PF 动作 bean。 */
    private static void overlaidActions(XmlBeanFactory definitions) {
        check("nc.ui.uapbd.qd.esign.QdDeliverySaveAction".equals(beanClass(definitions, "saveAction")),
                "保存按钮必须使用签名覆盖动作");
        check("nc.ui.uapbd.qd.esign.QdDeliverySendApproveAction".equals(beanClass(definitions, "sendApproveAction")),
                "提交按钮必须使用签名覆盖动作");
        check("nc.ui.uapbd.qd.esign.QdDeliveryUnSendApproveAction".equals(beanClass(definitions, "unsendApproveAction")),
                "收回按钮必须使用签名覆盖动作");
        check("nc.ui.uapbd.qd.esign.QdDeliveryApproveAction".equals(beanClass(definitions, "approveAction")),
                "审批按钮必须使用签名覆盖动作");
        check("nc.ui.uapbd.qd.esign.QdDeliveryUnApproveAction".equals(beanClass(definitions, "unApproveAction")),
                "取消审批按钮必须使用签名覆盖动作");
    }

    /** 保存：逐项保留原生 property（含 filledUpInFlow=false 与 cardRefreshAction），且不引入权限校验服务。 */
    private static void saveProperties(XmlBeanFactory definitions) {
        AbstractBeanDefinition save = bean(definitions, "saveAction");
        check("WRITE".equals(text(save, "actionName")), "保存的 PF 动作名沿用原生 WRITE");
        check("4331".equals(text(save, "billType")), "保存的单据类型沿用原生 4331");
        check("false".equals(text(save, "filledUpInFlow")), "保存的 filledUpInFlow 沿用原生 false");
        check("ManageAppModel".equals(refName(save, "model")), "保存沿用原生领域模型");
        check("billFormEditor".equals(refName(save, "editor")), "保存沿用原生卡片编辑器");
        check("cardRefreshAction".equals(refName(save, "refreshAction")), "保存沿用原生卡片刷新动作");
        check(text(save, "validationService") == null, "保存不额外引入权限校验服务（原生未配置）");
    }

    /** 提交/收回/审批/取消审批：保留原生动作名、单据类型、filledUpInFlow=true 与原生权限校验服务。 */
    private static void pfProperties(XmlBeanFactory definitions) {
        String[][] expected = new String[][] {
            { "sendApproveAction", "SAVE", "powersendapprovevalidservice" },
            { "unsendApproveAction", "UNSAVE", "powerunsendvalidservice" },
            { "approveAction", "APPROVE", "powerapprovevalidservice" },
            { "unApproveAction", "UNAPPROVE", "powerunapprovevalidservice" }
        };
        for (String[] row : expected) {
            AbstractBeanDefinition bean = bean(definitions, row[0]);
            check(row[1].equals(text(bean, "actionName")), "PF 动作名沿用原生：" + row[0]);
            check("4331".equals(text(bean, "billType")), "单据类型沿用原生 4331：" + row[0]);
            check("true".equals(text(bean, "filledUpInFlow")), "filledUpInFlow 沿用原生 true：" + row[0]);
            check("ManageAppModel".equals(refName(bean, "model")), "沿用原生领域模型：" + row[0]);
            check("billFormEditor".equals(refName(bean, "editor")), "沿用原生卡片编辑器：" + row[0]);
            check(row[2].equals(refName(bean, "validationService")), "沿用原生权限校验服务：" + row[0]);
        }
    }

    /** 删除与四个直接服务动作保持原生配置，本批不在客户端覆盖。 */
    private static void nativeActionsNotOverlaid(XmlBeanFactory definitions) {
        String[][] nativeBeans = new String[][] {
            { "deleteAction", NATIVE_ACTION_PACKAGE + "DeliveryDeleteAction" },
            { "deliveryCloseAction", NATIVE_ACTION_PACKAGE + "assitfunc.DeliveryCloseAction" },
            { "deliveryOpenAction", NATIVE_ACTION_PACKAGE + "assitfunc.DeliveryOpenAction" },
            { "rowCloseAction", NATIVE_ACTION_PACKAGE + "assitfunc.DeliveryRowCloseAction" },
            { "rowOpenAction", NATIVE_ACTION_PACKAGE + "assitfunc.DeliveryRowOpenAction" }
        };
        for (String[] row : nativeBeans) {
            check(row[1].equals(beanClass(definitions, row[0])),
                    "本批不得覆盖原生 bean，保持原确认/重试顺序：" + row[0]);
        }
        check("nc.ui.so.m4331.billui.action.DeliverySaveCommitAction".equals(beanClass(definitions, "saveandsendAction")),
                "保存并提交复合动作保持原生实现");
    }

    /** 5 个覆盖类必须继承对应原生动作并实现上下文提供与授权消费两个接口。 */
    private static void actionClasses() throws Exception {
        Class<?>[][] pairs = new Class<?>[][] {
            { QdDeliverySaveAction.class, nc.ui.so.m4331.billui.action.DeliverySaveAction.class },
            { QdDeliverySendApproveAction.class, nc.ui.so.m4331.billui.action.DeliverySendApproveAction.class },
            { QdDeliveryUnSendApproveAction.class, nc.ui.so.m4331.billui.action.DeliveryUnSendApproveAction.class },
            { QdDeliveryApproveAction.class, nc.ui.so.m4331.billui.action.DeliveryApproveAction.class },
            { QdDeliveryUnApproveAction.class, nc.ui.so.m4331.billui.action.DeliveryUnApproveAction.class }
        };
        for (Class<?>[] pair : pairs) {
            check(pair[1].isAssignableFrom(pair[0]) && !pair[1].equals(pair[0]),
                    "覆盖类必须继承原生动作：" + pair[0].getSimpleName());
            check(OperationContextProvider.class.isAssignableFrom(pair[0]),
                    "覆盖类必须提供签名上下文：" + pair[0].getSimpleName());
            check(AuthorizedRequestConsumer.class.isAssignableFrom(pair[0]),
                    "覆盖类必须消费一次性授权：" + pair[0].getSimpleName());
            check(!java.lang.reflect.Modifier.isAbstract(pair[0].getModifiers()),
                    "覆盖类必须可直接实例化：" + pair[0].getSimpleName());
        }
    }

    /** 10 个按钮的启用边界与正式清单只读核对：删除与两个「打开」动作保持关闭。 */
    private static void scopeBoundaries() {
        check(SalesOtherNodeScope.isNode(SalesOtherNodeScope.DELIVERY_NODE), "发货单节点必须登记在固定范围白名单");
        check("发货单维护".equals(SalesOtherNodeScope.nodeName(SalesOtherNodeScope.DELIVERY_NODE)),
                "节点名称固定为发货单维护");
        check("SO_DELIVERY".equals(SalesOtherNodeScope.objectType(SalesOtherNodeScope.DELIVERY_NODE)),
                "发货单根对象类型固定");
        check("SO_DELIVERY_B".equals(SalesOtherNodeScope.lineObjectType(SalesOtherNodeScope.DELIVERY_NODE)),
                "发货单行对象类型固定");
        check("TRAFFICORGTYPE000000".equals(SalesOtherNodeScope.orgTypeCode(SalesOtherNodeScope.DELIVERY_NODE)),
                "发货单注册组织类型固定为物流组织");
        Map<String, String> scopes = SalesOtherNodeScope.buttonScopes(SalesOtherNodeScope.DELIVERY_NODE);
        check(scopes.size() == 10, "发货单固定白名单必须恰好登记 10 个按钮");
        check("QD-ES-04ECAF336F8932CA".equals(scopes.get("Save")), "保存范围 ID 与正式清单一致");
        check("QD-ES-FF81305763F3CF1F".equals(scopes.get("Delete")), "删除范围 ID 与正式清单一致");
        check("QD-ES-D494A8A893DDBC0B".equals(scopes.get("Commit")), "提交范围 ID 与正式清单一致");
        check("QD-ES-14232F4638F48C8C".equals(scopes.get("UnCommit")), "收回范围 ID 与正式清单一致");
        check("QD-ES-257B45A6A4F80EF6".equals(scopes.get("Approve")), "审批范围 ID 与正式清单一致");
        check("QD-ES-7DA00D0A9032E168".equals(scopes.get("UnApprove")), "取消审批范围 ID 与正式清单一致");
        check("QD-ES-52A72C09C2BE1F47".equals(scopes.get("BillClose")), "整单关闭范围 ID 与正式清单一致");
        check("QD-ES-61C46C0C9ED0BC7D".equals(scopes.get("BillOpen")), "整单打开范围 ID 与正式清单一致");
        check("QD-ES-AF1994D66608BEF1".equals(scopes.get("OutClose")), "出库关闭范围 ID 与正式清单一致");
        check("QD-ES-5275CF042A861E3D".equals(scopes.get("OutOpen")), "出库打开范围 ID 与正式清单一致");
        Map<String, Boolean> enabled = SalesOtherNodeScope.enabledButtons(SalesOtherNodeScope.DELIVERY_NODE);
        check(enabled.size() == 10, "启用开关必须逐按钮登记 10 项");
        int open = 0;
        for (Map.Entry<String, Boolean> entry : enabled.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) { open++; }
        }
        check(open == 7, "首轮只开放 7 个已完成受控接入的按钮");
        check(!SalesOtherNodeScope.isEnabled(SalesOtherNodeScope.DELIVERY_NODE, "Delete"),
                "删除按钮保持关闭（确认顺序未核实）");
        check(!SalesOtherNodeScope.isEnabled(SalesOtherNodeScope.DELIVERY_NODE, "BillOpen"),
                "整单打开保持关闭（继续协议未核实）");
        check(!SalesOtherNodeScope.isEnabled(SalesOtherNodeScope.DELIVERY_NODE, "OutOpen"),
                "出库打开保持关闭（继续协议未核实）");
        check("INSERT".equals(SalesOtherNodeScope.operationTypeFor(SalesOtherNodeScope.DELIVERY_NODE, "Save")),
                "保存归 INSERT 动作域");
        check("DELETE".equals(SalesOtherNodeScope.operationTypeFor(SalesOtherNodeScope.DELIVERY_NODE, "Delete")),
                "删除归 DELETE 动作域");
        check("UPDATE".equals(SalesOtherNodeScope.operationTypeFor(SalesOtherNodeScope.DELIVERY_NODE, "BillClose")),
                "整单关闭归 UPDATE 动作域");
        check(SalesOtherNodeScope.supportsAction(SalesOtherNodeScope.GROUP_ID, "ORG-1",
                SalesOtherNodeScope.DELIVERY_NODE, "Commit", "UPDATE"), "同集团明确组织的提交动作受控");
        check(!SalesOtherNodeScope.supportsAction(SalesOtherNodeScope.GROUP_ID, "ORG-1",
                SalesOtherNodeScope.DELIVERY_NODE, "Commit", "INSERT"), "动作域不一致必须拒绝");
        check(!SalesOtherNodeScope.supportsAction("OTHER-GROUP", "ORG-1",
                SalesOtherNodeScope.DELIVERY_NODE, "Commit", "UPDATE"), "非已核实集团必须拒绝");
    }

    /** 一次性交接与选择判定对发货单节点可用：整单动作不接受行，行动作接受一个已授权行。 */
    private static void handoffBoundaries() {
        SalesNodeActionHandoff handoff = new SalesNodeActionHandoff(SalesOtherNodeScope.DELIVERY_NODE);
        check(handoff.supports(signed("Commit", "UPDATE", root())), "整单 PF 动作应获得一次性交接");
        check(!handoff.supports(signed("Commit", "INSERT", root())), "动作域不一致不得获得交接");
        check(handoff.supports(signed("OutClose", "UPDATE", rootAndLine())), "行动作应接受根对象加已授权行");
        check(!handoff.supports(signed("OutClose", "UPDATE", foreignLine())), "其他对象类型的行不得混入发货单授权");
        check(!handoff.supports(otherGroup()), "非已核实集团不得获得一次性交接");
        check(SalesNodeSelection.classify(SalesOtherNodeScope.GROUP_ID,
                Collections.singletonList("ORG-1")) == SalesNodeSelection.Kind.CONTROLLED_SINGLE,
                "单组织单笔选择进入受控路径");
        check(SalesNodeSelection.classify(SalesOtherNodeScope.GROUP_ID,
                java.util.Arrays.asList("ORG-1", "ORG-2")) == SalesNodeSelection.Kind.REJECT_MIXED,
                "混合组织必须拒绝");
        check(SalesNodeSelection.classify(SalesOtherNodeScope.GROUP_ID,
                Collections.singletonList((String) null)) == SalesNodeSelection.Kind.REJECT_UNKNOWN,
                "无法判定组织归属必须拒绝");
    }

    private static OperationContext signed(String button, String operation, List<BusinessObjectRef> objects) {
        return new OperationContext(null, null, SalesOtherNodeScope.GROUP_ID, "ORG-1",
                SalesOtherNodeScope.DELIVERY_NODE, "发货单维护", button,
                SalesOtherNodeScope.buttonName(button), operation, null, objects)
                .withRequest("R", "B", SalesOtherNodeScope.GROUP_ID);
    }

    private static List<BusinessObjectRef> root() {
        return Collections.singletonList(new BusinessObjectRef("root", SalesOtherNodeScope.DELIVERY,
                "1001A9100000000001DL", "FH-0001", null));
    }

    private static List<BusinessObjectRef> line() {
        List<BusinessObjectRef> objects = new ArrayList<BusinessObjectRef>();
        objects.add(new BusinessObjectRef("root", SalesOtherNodeScope.DELIVERY, "1001A9100000000001DL", "FH-0001", null));
        objects.add(new BusinessObjectRef("line", SalesOtherNodeScope.DELIVERY_LINE, "1001A9100000000001D1", null, "root"));
        return objects;
    }

    private static List<BusinessObjectRef> rootAndLine() { return line(); }

    private static List<BusinessObjectRef> foreignLine() {
        List<BusinessObjectRef> objects = new ArrayList<BusinessObjectRef>();
        objects.add(new BusinessObjectRef("root", SalesOtherNodeScope.DELIVERY, "1001A9100000000001DL", "FH-0001", null));
        objects.add(new BusinessObjectRef("line", SalesOtherNodeScope.SALE_ORDER_LINE,
                "1001A9100000000001D1", null, "root"));
        return objects;
    }

    private static OperationContext otherGroup() {
        return new OperationContext(null, null, "OTHER-GROUP", "ORG-1", SalesOtherNodeScope.DELIVERY_NODE,
                "发货单维护", "Commit", SalesOtherNodeScope.buttonName("Commit"), "UPDATE", null, root());
    }

    private static AbstractBeanDefinition bean(XmlBeanFactory definitions, String id) {
        return (AbstractBeanDefinition) definitions.getBeanDefinition(id);
    }

    private static String beanClass(XmlBeanFactory definitions, String id) {
        AbstractBeanDefinition definition = (AbstractBeanDefinition) definitions.getBeanDefinition(id);
        return definition == null ? null : definition.getBeanClassName();
    }

    private static Object property(AbstractBeanDefinition definition, String name) {
        if (definition == null) { return null; }
        if (!definition.getPropertyValues().contains(name)) { return null; }
        return definition.getPropertyValues().getPropertyValue(name).getValue();
    }

    private static String text(AbstractBeanDefinition definition, String name) {
        Object value = property(definition, name);
        if (value == null) { return null; }
        if (value instanceof TypedStringValue) {
            Object inner = ((TypedStringValue) value).getValue();
            return inner == null ? null : String.valueOf(inner);
        }
        return String.valueOf(value);
    }

    private static String refName(AbstractBeanDefinition definition, String name) {
        Object value = property(definition, name);
        return value instanceof RuntimeBeanReference ? ((RuntimeBeanReference) value).getBeanName() : null;
    }

    private static void check(boolean ok, String message) {
        if (!ok) { throw new AssertionError(message); }
        assertions++;
    }
}
