package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.SuperVO;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/** Candidate material delete wiring and route availability checks, without an NC session. */
public final class MaterialMainDeleteClientTests {
    private MaterialMainDeleteClientTests() { }

    public static void run() throws Exception {
        XmlBeanFactory group=new XmlBeanFactory(new ClassPathResource(
            "nc/ui/uapbd/qd/esign/QdMaterialGroupDeleteCandidateConfig.xml",UIF2BeanFactory.class.getClassLoader()));
        BeanDefinition delete=group.getBeanDefinition("deleteAction");
        require(QdMaterialDeleteAction.class.getName().equals(delete.getBeanClassName()),
            "Group material delete candidate must replace the native delete action");
        require(delete.getPropertyValues().getPropertyValue("operationScope")!=null
            && delete.getPropertyValues().getPropertyValue("service")!=null
            && delete.getPropertyValues().getPropertyValue("refreshaction")!=null,
            "Material delete candidate must keep native model service and refresh wiring");

        XmlBeanFactory org=new XmlBeanFactory(new ClassPathResource(
            "nc/ui/uapbd/qd/esign/QdMaterialOrgDeleteCandidateConfig.xml",UIF2BeanFactory.class.getClassLoader()));
        require(QdMaterialDeleteAction.class.getName().equals(org.getBeanDefinition("deleteAction").getBeanClassName()),
            "Unit material delete candidate must replace the native delete action");

        // 动作构造依赖 NC 客户端环境，离线只校验纯路由判断。
        require(QdMaterialDeleteAction.accepts(context("10140MAG","Delete","DELETE","PK1")),"Group delete route must be available");
        require(QdMaterialDeleteAction.accepts(context("10140MAO","Delete","DELETE","PK1")),"Unit delete route must be available");
        require(!QdMaterialDeleteAction.accepts(context("10140MAG","Save","DELETE","PK1")),"Save button must not enter delete entry");
        require(!QdMaterialDeleteAction.accepts(context("10140MAG","Delete","INSERT","PK1")),"Insert operation must not enter delete entry");
        require(!QdMaterialDeleteAction.accepts(context("10140WARH","Delete","DELETE","PK1")),"Warehouse node must not enter material delete");
        require(!QdMaterialDeleteAction.accepts(context("10140MAG","Delete","DELETE",null)),"Unsaved material must not bind");
        require(!QdMaterialDeleteAction.accepts(context("10140MAG","Delete","DELETE","PK1","BD_STORDOC")),"Other object types must not bind");
        BusinessObjectRef a=new BusinessObjectRef("C1","BD_MATERIAL","PK1","M-01",null);
        BusinessObjectRef b=new BusinessObjectRef("C2","BD_MATERIAL","PK2","M-02",null);
        OperationContext batch=new OperationContext("R0000000000000000000000000000001","B1","G1","ORG1",
            "10140MAO","物料","Delete","删除","DELETE",null,Arrays.asList(a,b),OperationContext.OWNER_POLICY_PROTOCOL);
        MaterialVO first=material("PK1"),second=material("PK2");Map<String,SuperVO> bound=new LinkedHashMap<String,SuperVO>();
        bound.put("C1",first);bound.put("C2",second);
        require(QdMaterialDeleteAction.accepts(batch) && QdMaterialDeleteAction.sameRows(batch,bound,new Object[]{second,first}),
            "Mixed-owner batch must preserve the complete selection independent of row order");
        require(!QdMaterialDeleteAction.sameRows(batch,bound,new Object[]{first,first}),"Changed or duplicated selection accepted");
        require(!QdMaterialDeleteAction.sameRows(batch,bound,new Object[]{first,material("OTHER")}),"Foreign selection accepted");

        System.out.println("PASS MaterialMainDeleteClientTests (candidate wiring for both nodes and route guards; no NC session)");
    }

    private static MaterialVO material(String key) {
        MaterialVO row=new MaterialVO();row.setPk_material(key);row.setPk_group("G1");return row;
    }

    private static OperationContext context(String node,String button,String operation,String pk) {
        return context(node,button,operation,pk,"BD_MATERIAL");
    }

    private static OperationContext context(String node,String button,String operation,String pk,String type) {
        BusinessObjectRef ref=new BusinessObjectRef("C1",type,pk,"M-01",null);
        return new OperationContext("R0000000000000000000000000000001","B1","G1",
            "10140MAG".equals(node)?"G1":"ORG1",node,"物料",button,"删除",operation,null,
            Collections.singletonList(ref),OperationContext.OWNER_POLICY_PROTOCOL);
    }

    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
