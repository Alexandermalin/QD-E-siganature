package nc.ui.pu.qd.esign.workflow;

import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;

public final class ProcurementWorkflowSpringTest {
    public static void main(String[] args) throws Exception {
        verify("storereq", "m422x/config/storereq_action.xml", "revokeAction", 4);
        verify("praybill", "m20/config/praybill_action.xml", "unSendApproveAction", 4);
        verify("order", "m21/config/order_action.xml", "unSendApproveAction", 6);
        verify("invoice", "m25/config/poInvoice_action.xml", "unSendApproveAction", 2);
    }

    private static void verify(String node, String nativeXml, String withdrawId, int statusActions) throws Exception {
        DefaultListableBeanFactory stock = load("nc/ui/pu/" + nativeXml);
        DefaultListableBeanFactory patch = load("nc/ui/pu/qd/esign/qd_" + node + "_stage4_config.xml");
        ClassLoader loader = ProcurementWorkflowSpringTest.class.getClassLoader();
        for (String id : new String[] {"sendApproveAction", withdrawId, "approveAction", "unApproveAction"}) {
            BeanDefinition original = stock.getBeanDefinition(id);
            BeanDefinition replacement = patch.getBeanDefinition(id);
            Class<?> nativeClass = Class.forName(original.getBeanClassName(), false, loader);
            Class<?> patchClass = Class.forName(replacement.getBeanClassName(), false, loader);
            if (nativeClass == patchClass || !nativeClass.isAssignableFrom(patchClass)) {
                throw new AssertionError(node + " must retain the native action for " + id);
            }
            if (!original.getPropertyValues().equals(replacement.getPropertyValues())) {
                throw new AssertionError(node + " changed the native Spring properties for " + id);
            }
            assertOverrides(patchClass.getMethod("doAction", ActionEvent.class), nativeClass);
            assertOverrides(patchClass.getMethod("getMultibillScriptRunner"), nativeClass);
        }
        for (String id : new String[] {"saveAction", "deleteAction"}) {
            if (!patch.getBeanDefinition(id).getBeanClassName().contains(".qd.esign.")) {
                throw new AssertionError(node + " lost prior procurement integration for " + id);
            }
        }
        int actual = 0;
        for (String id : patch.getBeanDefinitionNames()) {
            String name = patch.getBeanDefinition(id).getBeanClassName();
            if (name != null && name.startsWith("nc.ui.pu.qd.esign.status.StatusActions$")) {
                actual++;
            }
        }
        if (actual != statusActions) {
            throw new AssertionError(node + " lost a prior status action");
        }
    }

    private static DefaultListableBeanFactory load(String path) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
        reader.setValidationMode(XmlBeanDefinitionReader.VALIDATION_NONE);
        reader.setNamespaceAware(true);
        reader.loadBeanDefinitions(new ClassPathResource(path));
        return factory;
    }

    private static void assertOverrides(Method hook, Class<?> nativeClass) {
        if (hook.getDeclaringClass().isAssignableFrom(nativeClass)) {
            throw new AssertionError(nativeClass.getName() + " is missing the workflow hook " + hook.getName());
        }
    }
}
