package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerMainSaveDraft;

/** Limits mixed customer/supplier impact to the exact main-save adapter and catalog mapping. */
public final class PartnerMainSaveRouteTests {
    private PartnerMainSaveRouteTests() { }
    public static void run() throws Exception {
        Map<String,Object> mapping=EsignValues.row("node_code","10140CUO","button_code","Save","operation_types","UPDATE",
            "object_type","BD_CUSTOMER","group_scope_id","QD-ES-E576276F26109C16","validated","Y","adapter_class",PartnerMainSaveRoute.ADAPTER);
        List<BusinessObjectRef> refs=Arrays.asList(new BusinessObjectRef("root","BD_CUSTOMER","C","C",null),
            new BusinessObjectRef("paired","BD_SUPPLIER","C","S","root"));
        MappingRules.requireDomain(mapping,"UPDATE",refs);
        mapping.put("group_scope_id","QD-ES-E8C69ACAF76ECABF");reject(mapping,refs);mapping.put("group_scope_id","QD-ES-E576276F26109C16");
        mapping.put("adapter_class","arbitrary");reject(mapping,refs);mapping.put("adapter_class",PartnerMainSaveRoute.ADAPTER);
        reject(mapping,Arrays.asList(refs.get(0),new BusinessObjectRef("unknown","BD_MATERIAL","M","M","root")));
        reject(mapping,Arrays.asList(refs.get(0),new BusinessObjectRef("second","BD_SUPPLIER","S","S",null)));
        if(!"customer".equals(PartnerMainSaveRoute.permissionResource("10140CUO"))
            || !"supplier".equals(PartnerMainSaveRoute.permissionResource("10140SUG"))) throw new AssertionError("Native partner permission resource incorrect");
        PartnerMainSaveDraft draft=new PartnerMainSaveDraft("P",true);
        OperationContext context=new OperationContext(null,null,"G","B","10140CUO","客户","Save","保存","UPDATE",null,
            Collections.singletonList(refs.get(0)),OperationContext.OWNER_POLICY_PROTOCOL,null,null,draft);
        if(context.withRequest("R","R","G").getPartnerMainSaveDraft()!=draft)
            throw new AssertionError("Request copy lost partner save scope draft");
        System.out.println("PASS PartnerMainSaveRouteTests (pinned mixed-domain main save; no NC or Oracle)");
    }
    private static void reject(Map<String,Object> mapping,List<BusinessObjectRef> refs) throws Exception {
        try { MappingRules.requireDomain(mapping,"UPDATE",refs);throw new AssertionError("Invalid main save route accepted"); }
        catch(BusinessException expected) { }
    }
}
