package nc.ui.uapbd.qd.esign;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import nc.funcnode.ui.AbstractFunclet;
import nc.ui.org.ref.BusinessUnitDefaultRefModel;
import nc.ui.bd.ref.busi.UserDefaultRefModel;
import nc.ui.pub.beans.UIRefPane;
import nc.ui.pub.beans.UITextField;
import nc.ui.querytemplate.querytype.UFDateTimeTextField;
import nc.vo.sm.funcreg.FuncRegisterVO;
import nc.vo.uapbd.qd.esign.log.LogKind;

/** 使用本机 NC 工厂和 Swing 事件队列验证内嵌页签。 */
public final class ManagementFuncletTests {
    private static int assertions;

    private ManagementFuncletTests() { }

    public static void run() throws Exception {
        assertions = 0;
        testPolicyPage();
        testGroupPolicyPage();
        testLogEntry(QdSignatureLogFunclet.class, "QD_ES_SIGNLOG", LogKind.SIGNATURE);
        testLogEntry(QdAuditLogFunclet.class, "QD_ES_AUDITLOG", LogKind.AUDIT);
        testLogEntry(QdSecurityLogFunclet.class, "QD_ES_SECURITY", LogKind.SECURITY);
        System.out.println("ManagementFuncletTests: " + assertions
                + " assertions passed (native factory and offline Swing event checks).");
    }

    private static void testGroupPolicyPage() throws Exception {
        final QdGroupPolicyFunclet entry = nativeEntry(QdGroupPolicyFunclet.class, "QD_ES_BD_POLICY");
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                entry.init();
                check(entry.getComponentCount() == 1 && entry.getComponent(0) instanceof QdPolicyPanel,
                    "group policy is embedded in the NC function tab");
                check(!hasComponent(entry, UIRefPane.class) && hasComponent(entry, JTable.class),
                    "group policy has no business-unit selector");
                check(findButton(entry, "保存集团总控") != null && findButton(entry, "保存策略") != null,
                    "group policy and master have separate save controls");
                check(!hasLabel(entry, "当前登录集团的基础档案") && hasLabel(entry, "功能节点"),
                    "group policy shows the node filter without the redundant group label");
                entry.setSize(640, 480);
                layout(entry);
                try {
                    checkPolicyHeaderLayout((QdPolicyPanel) entry.getComponent(0), false);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
                check(entry.funcletCloseing(), "an unedited group policy tab may close");
            }
        });
    }

    private static <T extends AbstractFunclet> T nativeEntry(Class<T> type, String functionCode) throws Exception {
        // 仅调用原生工厂的类加载路径，正式 NC 环境仍负责菜单、会话和授权。
        Class<?> factory = Class.forName("nc.funcnode.ui.FuncletWidgetFactory");
        Method create = factory.getDeclaredMethod("newFuncletInstance", String.class);
        create.setAccessible(true);
        Object created = create.invoke(null, type.getName());
        check(type.isInstance(created), "NC factory loads " + functionCode);
        T entry = type.cast(created);

        FuncRegisterVO registration = new FuncRegisterVO();
        registration.setFuncode(functionCode);
        check(entry.preOpenCheck(registration) == null, "matching registration accepted");
        registration.setFuncode("13050QDES");
        check(entry.preOpenCheck(registration) != null, "unrelated registration rejected");
        check(entry.preOpenCheck(null) != null, "missing registration rejected");
        return entry;
    }

    private static void testPolicyPage() throws Exception {
        final QdPolicyFunclet entry = nativeEntry(QdPolicyFunclet.class, "QD_ES_POLICY");
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                entry.init();
                check(entry.getComponentCount() == 1 && entry.getComponent(0) instanceof JTabbedPane,
                    "both policy scopes are embedded in the function tab");
                JTabbedPane scopes = (JTabbedPane) entry.getComponent(0);
                check(scopes.getTabCount() == 2
                    && "业务单元策略".equals(scopes.getTitleAt(0))
                    && "基础档案集团策略".equals(scopes.getTitleAt(1)),
                    "business-unit and group policies are selectable in the same entry");
                check(scopes.getComponentAt(0) instanceof QdPolicyPanel
                    && scopes.getComponentAt(1) instanceof QdPolicyPanel,
                    "both scopes use independent policy panels");
                check(hasComponent(entry, UIRefPane.class) && hasComponent(entry, JTable.class),
                    "policy tab contains the native organization reference and results table");
                entry.setSize(640, 480);
                layout(entry);
                JButton query = findButton(entry, "查询");
                JButton save = findButton(entry, "保存策略");
                JButton master = findButton(entry, "保存组织总控");
                check(query != null && save != null && master != null, "policy controls are present");
                Rectangle saveBounds = SwingUtilities.convertRectangle(save.getParent(), save.getBounds(), entry);
                Rectangle masterBounds = SwingUtilities.convertRectangle(master.getParent(), master.getBounds(), entry);
                check(saveBounds.width > 0 && masterBounds.width > 0 && saveBounds.y < 130 && masterBounds.y < 130
                    && entry.getBounds().contains(saveBounds) && entry.getBounds().contains(masterBounds),
                    "the save controls are inside the top of a narrow tab: " + saveBounds + " and " + masterBounds);
                check(findButton(entry, "再次打开") == null, "embedded policy has no reopen button");
                check(entry.funcletCloseing(), "an unedited policy tab may close");
                try {
                    checkPolicyHeaderLayout((QdPolicyPanel) scopes.getComponentAt(0), true);
                    scopes.setSelectedIndex(1);
                    layout(entry);
                    checkPolicyHeaderLayout((QdPolicyPanel) scopes.getComponentAt(1), false);
                    scopes.setSelectedIndex(0);
                    Field busy = QdPolicyPanel.class.getDeclaredField("busy");
                    busy.setAccessible(true);
                    busy.setBoolean(scopes.getComponentAt(1), true);
                    check(!entry.funcletCloseing(), "group policy operation prevents closing the combined tab");
                    busy.setBoolean(scopes.getComponentAt(1), false);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            }
        });
        JTabbedPane scopes = (JTabbedPane) entry.getComponent(0);
        UIRefPane reference = (UIRefPane) readField(scopes.getComponentAt(0), "orgRef");
        check(reference.isEnabled() && reference.getRefModel() instanceof BusinessUnitDefaultRefModel,
            "the standard business unit reference opens independently of policy permissions");
        check(((Boolean)readField(scopes.getComponentAt(1), "groupPolicy")).booleanValue(),
            "group tab uses the group policy service path");
    }

    private static void testLogEntry(Class<? extends QdLogFunclet> type,
            String functionCode, LogKind kind) throws Exception {
        final QdLogFunclet entry = nativeEntry(type, functionCode);
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override public void run() {
                entry.init();
                check(entry.getComponentCount() == 1 && entry.getComponent(0) instanceof QdLogQueryPanel,
                    "log query panel is embedded in its function tab");
                check(hasComponent(entry, UITextField.class) && hasComponent(entry, JTable.class),
                    "log tab contains filters and results");
                JButton search = findButton(entry, "查询");
                check(search != null && search.isEnabled() && entry.funcletCloseing(),
                    "hidden tab has not started querying");
                check(findButton(entry, "上一页") != null && findButton(entry, "下一页") != null
                    && findButton(entry, "导出已加载页") != null && findButton(entry, "导出筛选结果") != null,
                    "pagination and both export modes remain in the tab");
                check(findButton(entry, "再次打开") == null && findButton(entry, "关闭") == null,
                    "embedded logs rely on the NC tab lifecycle");
            }
        });
        final QdLogQueryPanel panel = (QdLogQueryPanel) entry.getComponent(0);
        check(readField(panel, "kind") == kind, "embedded log category matches registration");
        UITextField request = (UITextField) readField(panel, "request");
        check(request.isEnabled() == (kind != LogKind.SECURITY), "request filter matches the log category");
        if (kind == LogKind.AUDIT) {
            UIRefPane org = (UIRefPane) readField(panel, "orgRef"), operator = (UIRefPane) readField(panel, "operatorRef");
            check(org.getRefModel() instanceof BusinessUnitDefaultRefModel && operator.getRefModel() instanceof UserDefaultRefModel,
                "audit uses NC business unit and user references");
            check(readField(panel, "auditFrom") instanceof UFDateTimeTextField && readField(panel, "auditTo") instanceof UFDateTimeTextField,
                "audit uses NC date and time controls");
            check(hasComponent(entry, JSplitPane.class) && hasComponent(entry, QdAuditDetailsPanel.class),
                "audit list and details are embedded in a split panel");
            check(!hasLabel(entry, "操作人员主键") && !hasLabel(entry, "批次编号"), "audit removes obsolete filters");
            // NC UITextField consults screen dimensions while setting text and cannot do so in headless mode.
            if (!GraphicsEnvironment.isHeadless()) {
                SwingUtilities.invokeAndWait(new Runnable() { public void run() { panel.initializeTimeRange(); } });
                Method readFilter = QdLogQueryPanel.class.getDeclaredMethod("readFilter"); readFilter.setAccessible(true);
                nc.vo.uapbd.qd.esign.log.LogQuery initial = (nc.vo.uapbd.qd.esign.log.LogQuery) readFilter.invoke(panel);
                check(initial.getFromUtc().length() == 19 && initial.getToUtc().length() == 19 && initial.getFromUtc().compareTo(initial.getToUtc()) < 0,
                    "native audit dates preserve valid second-resolution UTC bounds");
            } else {
                check("2026-11-08 16:00:00".equals(LogUiFormats.utcFromBeijing(new nc.vo.pub.lang.UFDateTime("2026-11-09 00:00:00").toString())),
                    "native timestamp text converts from Beijing to UTC");
            }
        }
    }

    private static Object readField(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void checkPolicyHeaderLayout(QdPolicyPanel panel, boolean organization) throws Exception {
        String[] names = organization
            ? new String[] {"orgRef", "nodeFilter", "query", "master", "saveMaster", "save"}
            : new String[] {"nodeFilter", "query", "master", "saveMaster", "save"};
        Rectangle previous = null;
        for (String name : names) {
            Component component = (Component) readField(panel, name);
            Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), panel);
            check(bounds.width >= component.getMinimumSize().width && bounds.height > 0 && bounds.x >= 0
                && bounds.x + bounds.width <= panel.getWidth(),
                name + " is visible inside a narrow policy page: " + bounds);
            if (previous != null) {
                check(previous.x + previous.width <= bounds.x
                    && previous.y < bounds.y + bounds.height && bounds.y < previous.y + previous.height,
                    name + " follows the previous policy control on the same row: " + previous + " then " + bounds);
            }
            previous = bounds;
        }
    }

    private static boolean hasComponent(Container container, Class<?> type) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child) || (child instanceof Container && hasComponent((Container) child, type))) {
                return true;
            }
        }
        return false;
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) { layout((Container) child); }
        }
    }

    private static JButton findButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton) {
                JButton button = (JButton) component;
                if (text.equals(button.getText())) { return button; }
            }
            if (component instanceof Container) {
                JButton found = findButton((Container) component, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean hasLabel(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel && text.equals(((JLabel) component).getText())) { return true; }
            if (component instanceof Container && hasLabel((Container) component, text)) { return true; }
        }
        return false;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        assertions++;
    }

}
