package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import nc.bs.bd.service.ErrLogElement;
import nc.bs.bd.service.ValueObjWithErrLog;
import nc.ui.uif2.factory.UIF2BeanFactory;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/** Checks candidate list/card wiring and native batch receipt integrity. */
public final class PartnerStateClientTests {
    private PartnerStateClientTests() { }
    public static void run() throws Exception {
        config("QdCustomerGroupStateCandidateConfig",true);config("QdCustomerOrgStateCandidateConfig",true);
        config("QdSupplierGroupStateCandidateConfig",false);config("QdSupplierOrgStateCandidateConfig",false);
        materialConfig("QdMaterialGroupStateCandidateConfig");materialConfig("QdMaterialOrgStateCandidateConfig");
        warehouseConfig();
        BusinessObjectRef a=new BusinessObjectRef("a","BD_CUSTOMER","A","A",null),b=new BusinessObjectRef("b","BD_CUSTOMER","B","B",null);
        OperationContext context=new OperationContext("R","R","G","O","10140CUO","客户","Enable","启用","ENABLE",null,
            Arrays.asList(a,b),OperationContext.OWNER_POLICY_PROTOCOL);
        CustomerVO saved=new CustomerVO();saved.setPk_customer("A");
        ValueObjWithErrLog nativeResult=new ValueObjWithErrLog(new SuperVO[]{saved},new ArrayList<ErrLogElement>());
        ArchiveBatchResult valid=new ArchiveBatchResult(nativeResult,Arrays.asList(unit("a","A","SUCCESS"),unit("b","B","UNCHANGED")));
        QdPartnerStateCoordinator.validateReceipt(context,valid,true);
        reject(context,new ArchiveBatchResult(nativeResult,Arrays.asList(unit("a","A","SUCCESS"),unit("b","X","UNCHANGED"))));
        reject(context,new ArchiveBatchResult(nativeResult,Arrays.asList(unit("a","A","SUCCESS"),unit("b","B","FAILED"))));
        reject(context,new ArchiveBatchResult(nativeResult,Arrays.asList(unit("a","A","UNCHANGED"),unit("b","B","UNCHANGED"))));
        System.out.println("PASS PartnerStateClientTests (candidate list/card wiring and receipts; no NC session)");
    }
    private static void config(String name,boolean customer) {
        XmlBeanFactory beans=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/"+name+".xml",UIF2BeanFactory.class.getClassLoader()));
        String[] ids=customer?new String[]{"baseinfoEnableAction","baseinfoDisableAction","card_enableAction","card_disableAction"}
            :new String[]{"baseinfoenableaction","baseinfodisableaction","card_enableaction","card_disableaction"};
        Class<?>[] types=customer?new Class<?>[]{QdCustomerBatchEnableAction.class,QdCustomerBatchDisableAction.class,QdCustomerCardEnableAction.class,QdCustomerCardDisableAction.class}
            :new Class<?>[]{QdSupplierBatchEnableAction.class,QdSupplierBatchDisableAction.class,QdSupplierCardEnableAction.class,QdSupplierCardDisableAction.class};
        for(int i=0;i<ids.length;i++) if(!types[i].getName().equals(beans.getBeanDefinition(ids[i]).getBeanClassName()))
            throw new AssertionError("Native state action was not replaced: "+ids[i]);
    }
    private static ArchiveBatchResult.Unit unit(String id,String pk,String state) { return new ArchiveBatchResult.Unit(id,pk,state); }
    private static void materialConfig(String name) {
        XmlBeanFactory beans=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/"+name+".xml",UIF2BeanFactory.class.getClassLoader()));
        String[] ids={"list_enableAction","list_disableAction","card_enableAction","card_disableAction"};
        Class<?>[] types={QdMaterialBatchEnableAction.class,QdMaterialBatchDisableAction.class,QdMaterialCardEnableAction.class,QdMaterialCardDisableAction.class};
        for(int i=0;i<ids.length;i++) if(!types[i].getName().equals(beans.getBeanDefinition(ids[i]).getBeanClassName()))
            throw new AssertionError("Native material action was not replaced: "+ids[i]);
    }
    private static void warehouseConfig() {
        XmlBeanFactory beans=new XmlBeanFactory(new ClassPathResource("nc/ui/uapbd/qd/esign/QdWarehouseStateCandidateConfig.xml",UIF2BeanFactory.class.getClassLoader()));
        String[] ids={"list_enableAction","list_disableAction","enableAction","disableAction"};
        Class<?>[] types={QdWarehouseBatchEnableAction.class,QdWarehouseBatchDisableAction.class,QdWarehouseCardEnableAction.class,QdWarehouseCardDisableAction.class};
        for(int i=0;i<ids.length;i++) if(!types[i].getName().equals(beans.getBeanDefinition(ids[i]).getBeanClassName()))
            throw new AssertionError("Native warehouse action was not replaced: "+ids[i]);
    }
    private static void reject(OperationContext context,ArchiveBatchResult result) throws Exception {
        try { QdPartnerStateCoordinator.validateReceipt(context,result,true);throw new AssertionError("Invalid state receipt accepted"); }
        catch(BusinessException expected) { }
    }
}
