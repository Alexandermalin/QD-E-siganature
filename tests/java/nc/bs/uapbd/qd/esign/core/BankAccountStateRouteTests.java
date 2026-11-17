package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Mixed domains are accepted only for the exact server-pinned account-state adapter and route. */
public final class BankAccountStateRouteTests {
    private BankAccountStateRouteTests() { }
    public static void run() throws Exception {
        Map<String,Object> mapping=map("10140CBA","Enable","BD_CUSTOMER_BANK","QD-ES-DDC46B3D9F641DFF");
        List<BusinessObjectRef> mixed=Arrays.asList(new BusinessObjectRef("R","BD_CUSTOMER_BANK","REL1","C",null),
            new BusinessObjectRef("S","BD_SUPPLIER_BANK","REL2","S","R"));
        MappingRules.requireDomain(mapping,"ENABLE",mixed);
        reject(map("10140CBA","Disable","BD_CUSTOMER_BANK","QD-ES-DDC46B3D9F641DFF"),"ENABLE",mixed);
        reject(map("10140CBA","Enable","BD_CUSTOMER_BANK","QD-ES-B3A292BB1D2F3E84"),"ENABLE",mixed);
        reject(map("10140SBA","Enable","BD_CUSTOMER_BANK","QD-ES-DDC46B3D9F641DFF"),"ENABLE",mixed);
        reject(mapping,"ENABLE",Arrays.asList(mixed.get(0),new BusinessObjectRef("X","BD_SUPPLIER","S","S","R")));
        reject(mapping,"ENABLE",Arrays.asList(mixed.get(0),new BusinessObjectRef("X","BD_SUPPLIER_BANK","S","S",null)));
        reject(mapping,"ENABLE",Arrays.asList(mixed.get(0),new BusinessObjectRef("X","BD_SUPPLIER_BANK","S","S","OTHER")));
        mapping.put("adapter_class","nc.itf.uapbd.qd.esign.OtherAdapter");reject(mapping,"ENABLE",mixed);
        Map<String,Object> supplier=map("10140SBA","Disable","BD_SUPPLIER_BANK","QD-ES-24C25407F1C36AF6");
        supplier.put("operation_types","DISABLE");
        MappingRules.requireDomain(supplier,"DISABLE",Arrays.asList(new BusinessObjectRef("S","BD_SUPPLIER_BANK","SREL","S",null),
            new BusinessObjectRef("C","BD_CUSTOMER_BANK","CREL","C","S")));
        Map<String,Object> save=map("10140CBA","Add","BD_CUSTOMER_BANK","QD-ES-E153DD629D00947F");
        save.put("operation_types","INSERT");
        save.put("adapter_class",BankAccountRoute.SAVE_ADAPTER);
        MappingRules.requireDomain(save,"INSERT",mixed);
        save.put("adapter_class",BankAccountRoute.DELETE_ADAPTER);
        reject(save,"INSERT",mixed);
        if(!"custbankacc".equals(BankAccountRoute.permissionResource("10140CBA"))
            || !"supbankacc".equals(BankAccountRoute.permissionResource("10140SBA")))
            throw new AssertionError("Native bank metadata permission resource is incorrect");
        System.out.println("PASS BankAccountStateRouteTests (pinned mixed-domain routes; no NC or Oracle)");
    }
    private static Map<String,Object> map(String node,String button,String type,String scope) {
        return EsignValues.row("node_code",node,"button_code",button,"object_type",type,"group_scope_id",scope,
            "adapter_class",BankAccountRoute.ADAPTER,"operation_types","ENABLE","validated","Y");
    }
    private static void reject(Map<String,Object> mapping,String operation,List<BusinessObjectRef> refs) throws Exception {
        try { MappingRules.requireDomain(mapping,operation,refs);throw new AssertionError("Invalid bank route accepted"); }
        catch(BusinessException expected) { }
    }
}
