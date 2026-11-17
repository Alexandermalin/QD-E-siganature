package nc.ui.pu.qd.esign.status;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;

public final class ProcurementStatusSpringTest {
    public static void main(String[] args) {
        verify("qd_storereq_stage3_config.xml", 4);
        verify("qd_praybill_stage3_config.xml", 4);
        verify("qd_order_stage3_config.xml", 6);
        verify("qd_invoice_stage3_config.xml", 2);
    }

    private static void verify(String file, int expectedStatusActions) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
        reader.setValidationMode(XmlBeanDefinitionReader.VALIDATION_NONE);
        reader.setNamespaceAware(true);
        reader.loadBeanDefinitions(new ClassPathResource("nc/ui/pu/qd/esign/" + file));
        int actual = 0;
        for (String id : factory.getBeanDefinitionNames()) {
            String name = factory.getBeanDefinition(id).getBeanClassName();
            if (name != null && name.startsWith(StatusActions.class.getName() + "$")) actual++;
        }
        if (actual != expectedStatusActions) throw new AssertionError(file + " contains " + actual + " status actions");
        for (String action : new String[] {"saveAction", "deleteAction"}) {
            String name = factory.getBeanDefinition(action).getBeanClassName();
            if (name == null || !name.contains(".qd.esign.")) {
                throw new AssertionError(file + " lost prior procurement integration: " + action);
            }
        }
    }
}
