package nc.ui.pu.qd.esign.workflow;

import java.util.HashMap;
import java.util.Map;
import nc.vo.pu.qd.esign.InvoiceApCommand;
import nc.vo.pub.BusinessException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;

public final class InvoiceApSpringTest {
    public static void main(String[] args) throws Exception {
        DefaultListableBeanFactory stock = load("nc/ui/pu/m25/config/poInvoice_action.xml");
        DefaultListableBeanFactory patch = load("nc/ui/pu/qd/esign/qd_invoice_stage5_config.xml");
        verify(stock, patch, "sendApAction", InvoiceApActions.Send.class);
        verify(stock, patch, "cancelSendApAction", InvoiceApActions.Cancel.class);
        if (!patch.getBeanDefinition("approveAction").getBeanClassName().endsWith("$InvoiceApprove")) {
            throw new AssertionError("Stage 4 invoice approval overlay is missing");
        }

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("notechecked", "notechecked");
        parameters.put("worknote", null);
        if (InvoiceApActions.command(InvoiceApCommand.SEND, "25", null, null, parameters).isReload()) {
            throw new AssertionError("Unexpected native reload");
        }
        parameters.put("nolockandconsist", Boolean.TRUE);
        try {
            InvoiceApActions.command(InvoiceApCommand.SEND, "25", null, null, parameters);
            throw new AssertionError("The PF request can disable native locking");
        } catch (BusinessException expected) {
            // The guarded RPC accepts only the native AP arguments.
        }
    }

    private static void verify(DefaultListableBeanFactory stock, DefaultListableBeanFactory patch, String id,
            Class<?> replacement) throws Exception {
        BeanDefinition old = stock.getBeanDefinition(id);
        BeanDefinition next = patch.getBeanDefinition(id);
        Class<?> nativeClass = Class.forName(old.getBeanClassName(), false, replacement.getClassLoader());
        if (!nativeClass.isAssignableFrom(replacement) || !replacement.getName().equals(next.getBeanClassName())
                || !old.getPropertyValues().equals(next.getPropertyValues())) {
            throw new AssertionError("Native action or configuration changed for " + id);
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
}
