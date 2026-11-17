package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Parent/structure evidence and paired-master scope survive authorization as stable request objects. */
public final class PartnerMainRequestScopeTests {
    private PartnerMainRequestScopeTests() { }
    public static void run() throws Exception {
        PartnerMainSaveImpactTests.Data data=new PartnerMainSaveImpactTests.Data();
        BusinessObjectRef draft=new BusinessObjectRef("client","BD_CUSTOMER","C","forged",null,"B");
        PartnerMainRequestScope.Expanded expanded=PartnerMainRequestScope.expand("G",draft,"P2",true,data,false);
        List<BusinessObjectRef> refs=expanded.getObjects();BusinessObjectRef root=refs.get(0);
        require(refs.size()==5 && root.getClientObjectId().length()<=100 && "C_CODE".equals(root.getBillCode())
            && "B".equals(root.getDesiredOwnerOrg()),"Main save request was not normalized and bound to its parent");
        for(int i=1;i<refs.size();i++) {
            BusinessObjectRef ref=refs.get(i);require(root.getClientObjectId().equals(ref.getParentClientObjectId()),"Affected archive lost its root");
            if("BD_SUPPLIER".equals(ref.getObjectType())) require("B".equals(ref.getDesiredOwnerOrg()),"Owner transfer omitted the paired master");
            else require(ref.getDesiredOwnerOrg()==null,"Owner transfer leaked to a structure-only archive");
        }
        PartnerMainRequestScope.verify("G",refs,data,false);
        List<BusinessObjectRef> omitted=PartnerMainRequestScope.expand("G",draft,"P2",false,data,false).getObjects();
        require(omitted.size()==5 && omitted.get(0).getClientObjectId().startsWith("main:1:"),
            "A client flag hid an existing paired archive from the signed request");
        data.structure("BD_CUSTOMER","D","C","111122229999","0");reject(refs,data);
        data=new PartnerMainSaveImpactTests.Data();data.archive("BD_SUPPLIER","C","G","A",null);reject(refs,data);
        data=new PartnerMainSaveImpactTests.Data();List<BusinessObjectRef> forged=new ArrayList<BusinessObjectRef>(refs);
        BusinessObjectRef child=forged.get(1);forged.set(1,new BusinessObjectRef(child.getClientObjectId(),child.getObjectType(),
            child.getObjectPk(),child.getBillCode(),child.getParentClientObjectId(),"A"));reject(forged,data);
        BusinessObjectRef unchanged=new BusinessObjectRef("draft","BD_CUSTOMER","C",null,null,"A");
        List<BusinessObjectRef> noTransfer=PartnerMainRequestScope.expand("G",unchanged,"P1",true,data,false).getObjects();
        require(noTransfer.size()==2 && noTransfer.get(1).getDesiredOwnerOrg()==null,"Unchanged owner incorrectly transferred the paired archive");
        try { PartnerMainRequestScope.expand("G",draft,"contains:colon",true,data,false);throw new AssertionError("Unsafe parent identifier accepted"); }
        catch(BusinessException expected) { }
        BusinessObjectRef newDraft=new BusinessObjectRef("draft","BD_CUSTOMER",null,"NEW",null,"G");
        require(PartnerMainRequestScope.verifyInsert("G",PartnerMainRequestScope.expandInsert("G",newDraft,"~",data,false),data,false)==null,
            "Group insert must preserve NC's root-parent sentinel");
        data.archive("BD_CUSTOMER","G_PARENT","G","G",null);
        List<BusinessObjectRef> insertion=PartnerMainRequestScope.expandInsert("G",newDraft,"G_PARENT",data,false);
        require("G_PARENT".equals(PartnerMainRequestScope.verifyInsert("G",insertion,data,false)) && insertion.size()==2,
            "The existing upper archive must be part of the group-insert authorization");
        data.structure("BD_CUSTOMER","G_PARENT",null,"1234","0");
        try { PartnerMainRequestScope.verifyInsert("G",insertion,data,false);throw new AssertionError("Stale new-record parent accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS PartnerMainRequestScopeTests (persisted parent and structure fingerprint; no NC or Oracle)");
    }
    private static void reject(List<BusinessObjectRef> refs,PartnerMainSaveImpactTests.Data data) throws Exception {
        try { PartnerMainRequestScope.verify("G",refs,data,false);throw new AssertionError("Stale or altered main-save authorization accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
