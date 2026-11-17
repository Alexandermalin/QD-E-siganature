package nc.bs.uapbd.qd.esign.core;

import java.util.Map;
import java.util.Arrays;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** The candidate route cannot borrow a page-delete, save or different-domain policy. */
public final class PartnerMainDeleteRouteTests {
    private PartnerMainDeleteRouteTests() { }
    public static void run() throws Exception {
        Map<String,Object> customer=mapping("10140CUG","BD_CUSTOMER",PartnerMainDeleteRoute.scopeFor("BD_CUSTOMER"));
        PartnerMainDeleteRoute.requireMapping(customer,"DELETE");
        BusinessObjectRef root=new BusinessObjectRef("root","BD_CUSTOMER","C",null,null);
        BusinessObjectRef parent=new BusinessObjectRef("parent","BD_CUSTOMER","P",null,"root");
        BusinessObjectRef counterpart=new BusinessObjectRef("paired","BD_SUPPLIER","C",null,"root");
        MappingRules.requireDomain(customer,"DELETE",Arrays.asList(root,parent,counterpart));
        try { MappingRules.requireDomain(customer,"DELETE",Arrays.asList(root,
                new BusinessObjectRef("x","BD_SUPPLIER","S",null,"missing")));
            throw new AssertionError("Delete scope accepted an unrelated parent"); } catch(BusinessException expected) { }
        Map<String,Object> supplier=mapping("10140SUO","BD_SUPPLIER",PartnerMainDeleteRoute.scopeFor("BD_SUPPLIER"));
        PartnerMainDeleteRoute.requireMapping(supplier,"DELETE");
        reject(customer,"UPDATE");
        Map<String,Object> wrong=mapping("10140CUO","BD_SUPPLIER",PartnerMainDeleteRoute.scopeFor("BD_SUPPLIER"));reject(wrong,"DELETE");
        wrong=mapping("10140SUG","BD_SUPPLIER",PartnerMainDeleteRoute.scopeFor("BD_CUSTOMER"));reject(wrong,"DELETE");
        wrong=mapping("10140MAG","BD_CUSTOMER",PartnerMainDeleteRoute.scopeFor("BD_CUSTOMER"));reject(wrong,"DELETE");
        wrong=mapping("10140CUG","BD_CUSTOMER",PartnerMainDeleteRoute.scopeFor("BD_CUSTOMER"));
        wrong.put("button_code","Save");reject(wrong,"DELETE");
        wrong=mapping("10140CUG","BD_CUSTOMER",PartnerMainDeleteRoute.scopeFor("BD_CUSTOMER"));
        wrong.put("validated","N");reject(wrong,"DELETE");
        System.out.println("PASS PartnerMainDeleteRouteTests (formal action and domain binding; no NC or Oracle)");
    }
    private static Map<String,Object> mapping(String node,String domain,String scope) {
        return EsignValues.row("node_code",node,"button_code","Delete","adapter_class",
            "nc.itf.uapbd.qd.esign.IMaterialPageDeleteService","validated","Y","object_type",domain,"group_scope_id",scope,
            "operation_types","DELETE");
    }
    private static void reject(Map<String,Object> value,String operation) throws Exception {
        try { PartnerMainDeleteRoute.requireMapping(value,operation);throw new AssertionError("Invalid partner-delete route accepted"); }
        catch(BusinessException expected) { }
    }
}
