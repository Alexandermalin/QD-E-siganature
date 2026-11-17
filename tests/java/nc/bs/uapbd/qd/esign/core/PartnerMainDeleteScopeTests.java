package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Exercises native structure effects and membership drift without an NC server. */
public final class PartnerMainDeleteScopeTests {
    private PartnerMainDeleteScopeTests() { }

    public static void run() throws Exception {
        Data data=new Data();
        data.base.archives.remove("BD_CUSTOMER/D");
        data.base.structures.remove("BD_CUSTOMER/D");
        BusinessObjectRef draft=new BusinessObjectRef("draft","BD_CUSTOMER","C","client-code",null);
        List<BusinessObjectRef> signed=PartnerMainDeleteScope.expand("G","A","BD_CUSTOMER",
            Collections.singletonList(draft),data,false);
        require(signed.size()==3 && contains(signed,"BD_CUSTOMER","P1") && contains(signed,"BD_SUPPLIER","C")
            && "C_CODE".equals(signed.get(0).getBillCode()),"Customer delete omitted the old parent or paired archive");
        PartnerMainDeleteScope.verify("G","A","BD_CUSTOMER",signed,data,false);

        data.facts.add("bank-link:R2:OTHER");
        reject(signed,data,"A new shared-account relation must invalidate the signed scope");
        data.facts.remove(data.facts.size()-1);
        data.base.archive("BD_CUSTOMER","S","G","A","P1");
        data.base.structure("BD_CUSTOMER","S","P1","11119999","0");
        reject(signed,data,"A new sibling changes whether NC removes the old parent structure");

        data=new Data();
        data.base.archives.remove("BD_CUSTOMER/D");
        data.base.structures.remove("BD_CUSTOMER/D");
        data.base.archives.remove("BD_SUPPLIER/C");
        data.base.archive("BD_CUSTOMER","C","G","A","~");
        data.base.structure("BD_CUSTOMER","C","~","1111","0");
        data.base.structure("BD_CUSTOMER","~",null,"1","0");
        signed=PartnerMainDeleteScope.expand("G","A","BD_CUSTOMER",Collections.singletonList(draft),data,false);
        require(signed.size()==1,"NC's virtual root structure must not become a fictitious customer");
        PartnerMainDeleteScope.verify("G","A","BD_CUSTOMER",signed,data,false);
        data.base.structure("BD_CUSTOMER","~",null,"2","0");
        reject(signed,data,"A changed virtual-root structure must invalidate the scope");

        data=new Data();
        data.base.archive("BD_SUPPLIER","S","G","A","SP");
        data.base.archive("BD_SUPPLIER","SP","G","A",null);
        data.base.structure("BD_SUPPLIER","S","SP","11112222","0");
        data.base.structure("BD_SUPPLIER","SP",null,"1111","0");
        BusinessObjectRef supplier=new BusinessObjectRef("draft-s","BD_SUPPLIER","S",null,null);
        signed=PartnerMainDeleteScope.expand("G","A","BD_SUPPLIER",Collections.singletonList(supplier),data,false);
        require(signed.size()==1,"Native supplier deletion does not delete the parent structure");
        PartnerMainDeleteScope.verify("G","A","BD_SUPPLIER",signed,data,false);

        List<BusinessObjectRef> duplicate=Arrays.asList(supplier,supplier);
        try {
            PartnerMainDeleteScope.expand("G","A","BD_SUPPLIER",duplicate,data,false);
            throw new AssertionError("Duplicate selected archive accepted");
        } catch(BusinessException expected) { }
        System.out.println("PASS PartnerMainDeleteScopeTests (structure and related-row drift; no NC or Oracle)");
    }

    private static void reject(List<BusinessObjectRef> refs,Data data,String message) throws Exception {
        try {
            PartnerMainDeleteScope.verify("G","A","BD_CUSTOMER",refs,data,false);
            throw new AssertionError(message);
        } catch(BusinessException expected) { }
    }

    private static boolean contains(List<BusinessObjectRef> refs,String domain,String id) {
        for(BusinessObjectRef ref:refs) if(domain.equals(ref.getObjectType()) && id.equals(ref.getObjectPk())) return true;
        return false;
    }

    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }

    private static final class Data implements PartnerMainDeleteScope.Reader {
        final PartnerMainSaveImpactTests.Data base=new PartnerMainSaveImpactTests.Data();
        final List<String> facts=new ArrayList<String>(Collections.singletonList("bank-link:R1:C"));
        public PartnerMainSaveImpact.Archive archive(String group,String domain,String id,boolean lock) {
            return base.archive(group,domain,id,lock);
        }
        public PartnerMainSaveImpact.Structure structure(String domain,String id,boolean lock) {
            return base.structure(domain,id,lock);
        }
        public List<PartnerMainSaveImpact.Structure> children(String domain,String id,boolean lock) {
            return base.children(domain,id,lock);
        }
        public List<PartnerMainSaveImpact.Structure> prefix(String domain,String id,boolean lock) {
            return base.prefix(domain,id,lock);
        }
        public List<String> dependencies(String group,String domain,String id,boolean lock) {
            return new ArrayList<String>(facts);
        }
    }
}
