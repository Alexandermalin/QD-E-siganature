package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import java.util.Arrays;
import nc.ui.bd.material.baseinfo.action.MaterialSaveAddAction;
import nc.ui.bd.material.baseinfo.model.MaterialBaseInfoModel;
import nc.ui.bd.material.baseinfo.model.MaterialAssignService;
import nc.ui.bd.pub.assign.AssignContext;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.MaterialOrgPage;
import nc.vo.bd.uitabextend.UITabExtVO;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

public final class MaterialPilotConfigTests {
    public static void run() throws Exception {
        QdMaterialModelService pilot = loadConfig("QdMaterialOrgConfig");
        expectRejected(pilot, material(null, "OUTSIDE"), "INSERT");
        pilot.acceptPreparation(context("OUTSIDE", "10140MAO", "INSERT", null));
        expectRejected(pilot, material(null, "PILOT"), "INSERT");
        pilot.beginSave(material(null, "OUTSIDE"), "INSERT");
        pilot.endSave();
        pilot.finishAttempt();
        expectRejected(pilot, material(null, "OUTSIDE"), "INSERT");

        QdMaterialModelService group = loadConfig("QdMaterialGroupConfig");
        group.acceptPreparation(context("G", "10140MAG", "UPDATE", "MATERIAL"));
        expectRejected(group, material("MATERIAL", "OTHER"), "UPDATE");
        expectRejected(group, material("OTHER", "G"), "UPDATE");
        expectRejected(group, material(null, "G"), "INSERT");
        group.beginSave(material("MATERIAL", "G"), "UPDATE");
        group.finishAttempt();
        expectRejected(group, material("MATERIAL", "G"), "UPDATE");
        OperationContext assigned = new OperationContext(null, null, "G", "B", "10140MAO", "Material", "Save", "Save",
                "UPDATE", null, Collections.singletonList(new BusinessObjectRef("draft", "BD_MATERIAL", "MATERIAL", null, null, "A")),
                OperationContext.OWNER_POLICY_PROTOCOL);
        if (!nc.vo.uapbd.qd.esign.MaterialCommand.matchesDraft(assigned, material("MATERIAL", "A"))
                || nc.vo.uapbd.qd.esign.MaterialCommand.matchesDraft(assigned, material("MATERIAL", "B"))) {
            throw new AssertionError("Assigned material draft must retain its owner rather than the operation unit");
        }
        verifyPage("Sale", "SaSave", "materialSaleAppModelsService");
        verifyPage("Stock", "StSave", "materialStockAppModelService");
        verifyPage("Plan", "PlSave", "materialPlanAppModelsService");
        verifyPage("Prod", "PrSave", "materialProdAppModelsService");
        verifyProdCascadeRouting();
        verifyPage("Cost", "CoSave", "materialCostAppModelService");
        verifyDeletePage("Fi", "materialFiAppModelService");
        verifyDeletePage("Pu", "materialPuAppModelService");
        verifyDeletePage("Sale", "materialSaleAppModelsService");
        verifyDeletePage("Stock", "materialStockAppModelService");
        verifyDeletePage("Plan", "materialPlanAppModelsService");
        verifyDeletePage("Prod", "materialProdAppModelsService");
        verifyDeletePage("Cost", "materialCostAppModelService");
        System.out.println("PASS MaterialPilotConfigTests (native UIF2 XML loading and draft routing; no NC session)");
    }

    private static void verifyProdCascadeRouting() {
        QdMaterialOrgInfoModelService service=new QdMaterialOrgInfoModelService(
            new nc.ui.bd.material.prod.model.MaterialProdAppModelService(),"PrSave");
        BusinessObjectRef root=new BusinessObjectRef("root","BD_MATERIAL_PROD","P0","M0",null);
        BusinessObjectRef related=new BusinessObjectRef("peer","BD_MATERIAL_PROD","P1","M1","root");
        OperationContext expanded=new OperationContext("REQUEST","REQUEST","G","B","10140MAO","物料","PrSave","保存",
            "UPDATE",null,Arrays.asList(root,related),OperationContext.OWNER_POLICY_PROTOCOL);
        if(!service.isAvailable(expanded)) throw new AssertionError("Production cascade was rejected by its model service");
        BusinessObjectRef anotherRoot=new BusinessObjectRef("peer","BD_MATERIAL_PROD","P1","M1",null);
        OperationContext malformed=new OperationContext("REQUEST","REQUEST","G","B","10140MAO","物料","PrSave","保存",
            "UPDATE",null,Arrays.asList(root,anotherRoot),OperationContext.OWNER_POLICY_PROTOCOL);
        if(service.isAvailable(malformed)) throw new AssertionError("Production cascade accepted a second root");
    }

    private static void verifyDeletePage(String name,String serviceId) throws Exception {
        String config="nc/ui/uapbd/qd/esign/QdMaterial"+name+"DeleteConfig.xml";
        UIF2BeanFactory factory=new UIF2BeanFactory();
        factory.loadBeanConfig("nc/ui/uapbd/qd/esign/QdMaterialGroupConfig.xml");
        factory.loadBeanConfig(config);
        Object action=factory.getBean("material"+name+"DeleteAction");
        if (!(action instanceof QdMaterialOrgInfoDeleteAction)
                || !(factory.getBean(serviceId) instanceof QdMaterialOrgInfoModelService)) {
            throw new AssertionError("Candidate material page delete action not installed: "+name);
        }
        XmlBeanFactory definitions=new XmlBeanFactory(new ClassPathResource(config,UIF2BeanFactory.class.getClassLoader()));
        if (!QdMaterialOrgInfoDeleteAction.class.getName().equals(
                definitions.getBeanDefinition("material"+name+"DeleteAction").getBeanClassName())) {
            throw new AssertionError("Candidate delete action definition does not match: "+name);
        }
        String lower=name.toLowerCase(java.util.Locale.ENGLISH);
        UITabExtVO entry=new UITabExtVO();entry.setNodekey(lower);entry.setExbean_name("material_"+lower);
        entry.setEx_configpath("nc/ui/bd/material/"+lower+"/material_"+lower+"_config.xml");
        UITabExtVO active=QdMaterialGroupExinfoLoader.replaceVerifiedPath(entry,true,true);
        String expectedActive="Prod".equals(name)?"nc/ui/uapbd/qd/esign/QdMaterialProdBothConfig.xml":config;
        if (!expectedActive.equals(active.getEx_configpath())) throw new AssertionError("Candidate delete path not selected: "+name);
        UITabExtVO safe=QdMaterialGroupExinfoLoader.replaceVerifiedPath(entry,true,false);
        if (config.equals(safe.getEx_configpath())) throw new AssertionError("Uninstalled delete candidate was enabled: "+name);
        if("Prod".equals(name)) {
            XmlBeanFactory both=new XmlBeanFactory(new ClassPathResource(expectedActive,UIF2BeanFactory.class.getClassLoader()));
            if(!QdMaterialOrgInfoSaveAction.class.getName().equals(both.getBeanDefinition("materialProdSaveAction").getBeanClassName())
                    || !QdMaterialOrgInfoDeleteAction.class.getName().equals(both.getBeanDefinition("materialProdDeleteAction").getBeanClassName()))
                throw new AssertionError("Combined production candidate must guard save and delete");
            if(!QdMaterialOrgInfoSaveAction.productionFeature(Boolean.TRUE)
                    || QdMaterialOrgInfoSaveAction.productionFeature(nc.vo.pub.lang.UFBoolean.FALSE))
                throw new AssertionError("Production feature checkbox value is not interpreted correctly");
            try { QdMaterialOrgInfoSaveAction.productionFeature(null);throw new AssertionError("Unknown production feature accepted"); }
            catch(BusinessException expected) { }
        }
    }

    private static void verifyPage(String name, String button, String serviceId) throws Exception {
        String config = "nc/ui/uapbd/qd/esign/QdMaterial" + name + "Config.xml";
        UIF2BeanFactory factory = new UIF2BeanFactory();
        factory.loadBeanConfig("nc/ui/uapbd/qd/esign/QdMaterialGroupConfig.xml");
        factory.loadBeanConfig(config);
        Object service = factory.getBean(serviceId);
        if (!(service instanceof QdMaterialOrgInfoModelService)
                || !button.equals(((QdMaterialOrgInfoModelService)service).getButton())
                || !MaterialOrgPage.forButton(button).domain.equals(((QdMaterialOrgInfoModelService)service).getObjectType())) {
            throw new AssertionError("Material page model service not installed: " + name);
        }
        XmlBeanFactory definitions = new XmlBeanFactory(new ClassPathResource(config, UIF2BeanFactory.class.getClassLoader()));
        org.springframework.beans.factory.config.BeanDefinition action = definitions.getBeanDefinition("material" + name + "SaveAction");
        if (!QdMaterialOrgInfoSaveAction.class.getName().equals(action.getBeanClassName())) {
            throw new AssertionError("Material page action not installed: " + name);
        }
        Object validation = action.getPropertyValues().getPropertyValue("validationService").getValue();
        if (!(validation instanceof org.springframework.beans.factory.config.RuntimeBeanReference)
                || !("material" + name + "ValidationService").equals(
                    ((org.springframework.beans.factory.config.RuntimeBeanReference)validation).getBeanName())) {
            throw new AssertionError("Native page validator was not preserved: " + name);
        }
        String lower = name.toLowerCase(java.util.Locale.ENGLISH);
        UITabExtVO entry = new UITabExtVO();
        entry.setNodekey(lower);
        entry.setExbean_name("material_" + lower);
        entry.setEx_configpath("nc/ui/bd/material/" + lower + "/material_" + lower + "_config.xml");
        if (QdMaterialGroupExinfoLoader.replaceVerifiedPath(entry, false, false) != entry) {
            throw new AssertionError("An uninstalled candidate must retain the original material page");
        }
        UITabExtVO replaced = QdMaterialGroupExinfoLoader.replaceVerifiedPath(entry, true, false);
        if (replaced == entry || !config.equals(replaced.getEx_configpath())) {
            throw new AssertionError("Native material extension path was not replaced: " + name);
        }
        entry.setEx_configpath("custom.xml");
        if (QdMaterialGroupExinfoLoader.replaceVerifiedPath(entry, true, false) != entry) {
            throw new AssertionError("A customized native extension was unexpectedly replaced");
        }
    }

    private static QdMaterialModelService loadConfig(String name) throws Exception {
        String configPath = "nc/ui/uapbd/qd/esign/" + name + ".xml";
        try {
            Class.forName("nc.ui.uapbd.qd.esign." + name);
            throw new AssertionError("A Java configuration shadows the material XML");
        } catch (ClassNotFoundException expected) {
            // UIF2 should load the XML resource for this path.
        }
        UIF2BeanFactory factory = new UIF2BeanFactory();
        factory.loadBeanConfig(configPath);
        Object service = factory.getBean("service");
        if (!(service instanceof QdMaterialModelService)) throw new AssertionError("Pilot material service was not loaded");
        MaterialBaseInfoModel model = (MaterialBaseInfoModel) factory.getBean("baseinfoModel");
        if (model.getService() != service || ((QdMaterialModelService) service).getModel() != model) {
            throw new AssertionError("Native material model and pilot service must share the same page");
        }
        if (!(factory.getBean("modelDataManager") instanceof QdMaterialListModelDataManager)) {
            throw new AssertionError("Material query must expose its explicit operation organization");
        }
        XmlBeanFactory definitions = new XmlBeanFactory(new ClassPathResource(configPath, UIF2BeanFactory.class.getClassLoader()));
        if (!QdMaterialSaveAction.class.getName().equals(definitions.getBeanDefinition("saveAction").getBeanClassName())) {
            throw new AssertionError("Pilot XML must replace the ordinary save action");
        }
        if (!MaterialSaveAddAction.class.getName().equals(definitions.getBeanDefinition("saveAddAction").getBeanClassName())) {
            throw new AssertionError("Pilot XML must retain the original save-and-add action");
        }
        AssignContext fast = (AssignContext)factory.getBean("qdMaterialAssignContext");
        AssignContext original = (AssignContext)factory.getBean("assignContext");
        if (!(fast.getAssignService() instanceof QdMaterialAssignService)
                || original.getAssignService().getClass() != MaterialAssignService.class) {
            throw new AssertionError("Only the native fast assignment dialog may use the controlled service");
        }
        Object contextRef = definitions.getBeanDefinition("assignAction").getPropertyValues()
            .getPropertyValue("assignContext").getValue();
        if (!(contextRef instanceof org.springframework.beans.factory.config.RuntimeBeanReference)
                || !"qdMaterialAssignContext".equals(((org.springframework.beans.factory.config.RuntimeBeanReference)contextRef).getBeanName())) {
            throw new AssertionError("Fast Assign action does not use the controlled context");
        }

        return (QdMaterialModelService) service;
    }

    private static OperationContext context(String org, String node, String operation, String material) {
        return new OperationContext(null, null, "G", org, node, "Material", "Save", "Save", operation, null,
                Collections.singletonList(new BusinessObjectRef("draft", "BD_MATERIAL", material, null, null)));
    }

    private static void expectRejected(QdMaterialModelService service, MaterialVO draft, String operation) throws Exception {
        try {
            service.beginSave(draft, operation);
        } catch (BusinessException expected) {
            return;
        }
        throw new AssertionError("Material was allowed without a matching preparation");
    }

    private static MaterialVO material(final String key, final String org) {
        return new MaterialVO() {
            public String getPk_group() { return "G"; }
            public String getPk_org() { return org; }
            public String getPrimaryKey() { return key; }
        };
    }
}
