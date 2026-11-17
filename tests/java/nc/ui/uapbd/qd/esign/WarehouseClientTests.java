package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.ui.bd.stordoc.model.StordocAppModelService;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.bd.stordoc.StordocVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/** Candidate warehouse entry wiring and draft identity checks, without an NC session. */
public final class WarehouseClientTests {
    private WarehouseClientTests() { }
    public static void run() throws Exception {
        XmlBeanFactory beans=new XmlBeanFactory(new ClassPathResource(
            "nc/ui/uapbd/qd/esign/QdWarehouseCandidateConfig.xml",UIF2BeanFactory.class.getClassLoader()));
        BeanDefinition save=beans.getBeanDefinition("saveAction"),saveAdd=beans.getBeanDefinition("saveAddAction");
        require(QdWarehouseSaveAction.class.getName().equals(save.getBeanClassName())
            && QdWarehouseSaveAction.class.getName().equals(saveAdd.getBeanClassName()),"Both warehouse save entries must be guarded");
        require(QdWarehouseDeleteAction.class.getName().equals(beans.getBeanDefinition("deleteAction").getBeanClassName()),
            "Warehouse delete candidate must use the guarded worker");
        require(saveAdd.getPropertyValues().getPropertyValue("saveAdd")!=null
            && saveAdd.getPropertyValues().getPropertyValue("addAction")!=null,"Save-add must retain native add action");
        require(QdWarehouseModelService.class.getName().equals(beans.getBeanDefinition("ManageModelService").getBeanClassName())
            && QdWarehouseModelDataManager.class.getName().equals(beans.getBeanDefinition("modelDataManager").getBeanClassName()),
            "Warehouse save candidate must use guarded service and explicit query organization");
        QdWarehouseModelService service=new QdWarehouseModelService(new StordocAppModelService());
        QdWarehouseSaveAction action=new QdWarehouseSaveAction(service);action.setSaveAdd(true);
        new QdWarehouseDeleteAction(service);
        OperationContext insert=context("INSERT",null,"B","10140WARH");
        StordocVO value=row(null,"G","B");
        require(service.isAvailable(insert) && QdWarehouseModelService.sameDraft(insert,value),"Matching warehouse insert must be recognized");
        require(!service.isAvailable(context("INSERT",null,"B","10140RACK")),"Another node must not use the warehouse entry");
        require(!QdWarehouseModelService.sameDraft(insert,row(null,"G","OTHER")),"Draft owner changes must be rejected");
        OperationContext edit=context("UPDATE","WAREHOUSE","B","10140WARH");
        require(QdWarehouseModelService.sameDraft(edit,row("WAREHOUSE","G","B")),"Matching warehouse edit must be recognized");
        require(!QdWarehouseModelService.sameDraft(edit,row("OTHER","G","B"))
            && !QdWarehouseModelService.sameDraft(edit,row("WAREHOUSE","OTHER","B")),"Other warehouse or group must be rejected");
        service.acceptPreparation(insert);
        try { service.beginSave(value,"INSERT");throw new AssertionError("Unbound request was accepted"); }
        catch(BusinessException expected) { }
        BusinessObjectRef a=new BusinessObjectRef("a","BD_STORDOC","A",null,null),b=new BusinessObjectRef("b","BD_STORDOC","B",null,null);
        OperationContext deletion=new OperationContext("request","request","G","B","10140WARH","仓库","Delete","删除","DELETE",null,
            Arrays.asList(a,b),OperationContext.OWNER_POLICY_PROTOCOL);
        StordocVO first=row("A","G","B"),second=row("B","G","B");
        Map<String,SuperVO> bound=new LinkedHashMap<String,SuperVO>();bound.put("a",first);bound.put("b",second);
        require(QdWarehouseDeleteAction.sameRows(deletion,bound,new Object[]{first,second}),"Signed warehouse selection must match in order");
        require(!QdWarehouseDeleteAction.sameRows(deletion,bound,new Object[]{second,first})
            && !QdWarehouseDeleteAction.sameRows(deletion,bound,new Object[]{first,first})
            && !QdWarehouseDeleteAction.sameRows(deletion,bound,new Object[]{first,row("B","OTHER","B")}),
            "Reordered, duplicated or cross-group warehouse selections must be rejected");
        System.out.println("PASS WarehouseClientTests (candidate XML and draft binding; no NC client session)");
    }
    private static OperationContext context(String op,String pk,String desired,String node) {
        BusinessObjectRef ref=new BusinessObjectRef("row","BD_STORDOC",pk,"CODE",null,desired);
        return new OperationContext(null,null,"G","B",node,"仓库","Save","保存",op,null,
            Collections.singletonList(ref),OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static StordocVO row(String pk,String group,String org) {
        StordocVO value=new StordocVO();value.setPk_stordoc(pk);value.setPk_group(group);value.setPk_org(org);return value;
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
