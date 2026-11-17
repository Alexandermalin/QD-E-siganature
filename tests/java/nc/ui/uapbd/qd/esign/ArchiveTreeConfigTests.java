package nc.ui.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import nc.ui.bd.pub.actions.ManageModeActionInterceptor;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/** Candidate overlays use the native tree model and preserve the NC manage-mode interceptor. */
public final class ArchiveTreeConfigTests {
    private ArchiveTreeConfigTests() { }
    public static void run() throws Exception {
        checkConfig("QdCustomerClassDisableConfig", "model");
        checkConfig("QdSupplierClassDisableConfig", "HAppModel");
        checkConfig("QdMaterialClassDisableConfig", "appmodel");
        checkStateConfig("QdCustomerClassStateConfig", "model");
        checkStateConfig("QdSupplierClassStateConfig", "HAppModel");
        checkStateConfig("QdMaterialClassStateConfig", "appmodel");
        checkStateConfig("QdRackStateCandidateConfig", "appmodel");
        checkSaveConfig("QdCustomerClassSaveCandidateConfig","saveaction","saveaddaction",QdArchiveTreeSaveAction.class);
        checkSaveConfig("QdSupplierClassSaveCandidateConfig","saveAction","saveAddAction",QdArchiveTreeSaveAction.class);
        checkSaveConfig("QdRackSaveCandidateConfig","saveaction","saveaddAction",QdArchiveTreeCodeSaveAction.class);
        checkSaveConfig("QdMaterialClassCandidateConfig","saveaction","saveaddAction",QdArchiveTreeCodeSaveAction.class);
        checkDeleteConfig("QdCustomerClassDeleteCandidateConfig","delaction","model");
        checkDeleteConfig("QdSupplierClassDeleteCandidateConfig","delAction","HAppModel");
        checkDeleteConfig("QdRackDeleteCandidateConfig","delaction","appmodel");
        checkDeleteConfig("QdMaterialClassCandidateConfig","delaction","appmodel");
        try { new QdArchiveTreeDeleteAction().doAction(null);throw new AssertionError("Direct native delete bypass accepted"); }
        catch(nc.vo.pub.BusinessException expected) { }
        QdArchiveTreeDisableAction action = new QdArchiveTreeDisableAction();
        BusinessObjectRef root = new BusinessObjectRef("root", "BD_CUSTCLASS", "R", null, null);
        BusinessObjectRef child = new BusinessObjectRef("child", "BD_CUSTCLASS", "C", null, "root");
        require(action.isAvailable(context("10140CCLO", Arrays.asList(root, child), OperationContext.OWNER_POLICY_PROTOCOL)),
            "A server-expanded customer classification branch should be accepted");
        BusinessObjectRef other = new BusinessObjectRef("other", "BD_CUSTCLASS", "X", null, null);
        require(!action.isAvailable(context("10140CCLO", Arrays.asList(root, other), OperationContext.OWNER_POLICY_PROTOCOL)),
            "An ambiguous multi-root candidate must not be dispatched by the single-root action");
        require(!action.isAvailable(context("10140CCLO", Collections.singletonList(root), null)),
            "Classification actions require owner-policy protocol");
        require(!action.isAvailable(context("10140SCLG", Collections.singletonList(root), OperationContext.OWNER_POLICY_PROTOCOL)),
            "A supplier node must not accept a customer classification");
        QdArchiveTreeEnableAction enable=new QdArchiveTreeEnableAction();
        OperationContext single=new OperationContext(null,null,"G","A","10140CCLO","分类","Enable","启用","ENABLE",null,
            Collections.singletonList(root),OperationContext.OWNER_POLICY_PROTOCOL);
        require(enable.isAvailable(single),"A single customer classification should be available for enable");
        OperationContext multiple=new OperationContext(null,null,"G","A","10140CCLO","分类","Enable","启用","ENABLE",null,
            Arrays.asList(root,child),OperationContext.OWNER_POLICY_PROTOCOL);
        require(!enable.isAvailable(multiple),"Enable must not claim a cascade of classifications");
        BusinessObjectRef rack=new BusinessObjectRef("rack","BD_RACK","RACK",null,null);
        require(action.isAvailable(context("10140RACK",Collections.singletonList(rack),OperationContext.OWNER_POLICY_PROTOCOL)),
            "The native rack tree disable route must use the rack object domain");
        OperationContext enableRack=new OperationContext(null,null,"G","A","10140RACK","货位","Enable","启用","ENABLE",null,
            Collections.singletonList(rack),OperationContext.OWNER_POLICY_PROTOCOL);
        require(enable.isAvailable(enableRack),"The rack enable route must retain the rack object domain");
        System.out.println("PASS ArchiveTreeConfigTests (candidate NC tree overlays; no NC session)");
    }

    private static void checkStateConfig(String name,String modelId) throws Exception {
        String config="nc/ui/uapbd/qd/esign/"+name+".xml";
        XmlBeanFactory definitions=new XmlBeanFactory(new ClassPathResource(config,UIF2BeanFactory.class.getClassLoader()));
        org.springframework.beans.factory.config.BeanDefinition enable=definitions.getBeanDefinition("enableAction");
        require(QdArchiveTreeEnableAction.class.getName().equals(enable.getBeanClassName())
            && QdArchiveTreeDisableAction.class.getName().equals(definitions.getBeanDefinition("disableAction").getBeanClassName()),
            "Classification enable/disable candidates must both be present: "+name);
        Object model=enable.getPropertyValues().getPropertyValue("model").getValue();
        require(model instanceof org.springframework.beans.factory.config.RuntimeBeanReference
            && modelId.equals(((org.springframework.beans.factory.config.RuntimeBeanReference)model).getBeanName())
            && new QdArchiveTreeEnableAction().getInterceptor() instanceof ManageModeActionInterceptor,
            "The native model and manage-mode check must be retained: "+name);
        if("QdSupplierClassStateConfig".equals(name)) require(enable.getPropertyValues().getPropertyValue("editor")!=null,
            "The supplier's original editor binding must be retained");
    }

    private static void checkSaveConfig(String name,String save,String add,Class<?> action) {
        XmlBeanFactory definitions=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/"+name+".xml",UIF2BeanFactory.class.getClassLoader()));
        require(action.getName().equals(definitions.getBeanDefinition(save).getBeanClassName())
            && QdArchiveTreeSaveAddAction.class.getName().equals(definitions.getBeanDefinition(add).getBeanClassName()),
            "Candidate save and save-add entry must both be guarded: "+name);
        require(definitions.getBeanDefinition(save).getPropertyValues().getPropertyValue("editor")!=null,
            "Native tree editor must be retained: "+name);
    }
    private static void checkDeleteConfig(String name,String id,String model) {
        XmlBeanFactory definitions=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/"+name+".xml",UIF2BeanFactory.class.getClassLoader()));
        org.springframework.beans.factory.config.BeanDefinition value=definitions.getBeanDefinition(id);
        Object binding=value.getPropertyValues().getPropertyValue("model").getValue();
        require(QdArchiveTreeDeleteAction.class.getName().equals(value.getBeanClassName())
            && binding instanceof org.springframework.beans.factory.config.RuntimeBeanReference
            && model.equals(((org.springframework.beans.factory.config.RuntimeBeanReference)binding).getBeanName()),
            "Candidate delete must retain the native tree model: "+name);
        require(new QdArchiveTreeDeleteAction().getInterceptor() instanceof ManageModeActionInterceptor,
            "Candidate delete must retain native manage-mode checks");
    }

    private static void checkConfig(String name, String modelId) throws Exception {
        String config = "nc/ui/uapbd/qd/esign/" + name + ".xml";
        UIF2BeanFactory factory = new UIF2BeanFactory();
        factory.loadBeanConfig(config);
        Object action = factory.getBean("disableAction");
        if (!(action instanceof QdArchiveTreeDisableAction)) throw new AssertionError("Missing controlled disable: " + name);
        QdArchiveTreeDisableAction disable = (QdArchiveTreeDisableAction)action;
        if (disable.getTreeModel() != factory.getBean(modelId) || !(disable.getInterceptor() instanceof ManageModeActionInterceptor)) {
            throw new AssertionError("Native tree model or manage-mode check was not retained: " + name);
        }
        XmlBeanFactory definitions = new XmlBeanFactory(new ClassPathResource(config, UIF2BeanFactory.class.getClassLoader()));
        if (definitions.getBeanDefinition("disableAction").getPropertyValues().getPropertyValue("refreshAction") == null) {
            throw new AssertionError("Native refresh action is required: " + name);
        }
    }

    private static OperationContext context(String node, java.util.List<BusinessObjectRef> refs, String protocol) {
        return new OperationContext(null, null, "G", "A", node, "分类", "Disable", "停用", "DISABLE", null, refs, protocol);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
