package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** A signed change is bound to its whole selection, group and operation organization. */
public final class NativeBatchRequestScopeTests {
    private NativeBatchRequestScopeTests() { }
    public static void run() throws Exception {
        NativeBatchChangeSet changes=change("one");
        List<BusinessObjectRef> selected=Arrays.asList(ref("client-a","BD_CUSTOMER","A"),ref("client-b","BD_CUSTOMER","B"));
        List<BusinessObjectRef> bound=NativeBatchRequestScope.bind("G","ORG","BD_CUSTOMER",selected,changes);
        NativeBatchRequestScope.verify("G","ORG","BD_CUSTOMER",bound,changes);
        require(bound.size()==2 && bound.get(0).getClientObjectId().length()<=100 && bound.get(1).getClientObjectId().length()<=100,
            "Batch request identities exceed storage bounds");
        require(bound.get(0).getClientObjectId().startsWith("bulk:") && bound.get(1).getClientObjectId().startsWith("bulk:"),
            "Existing main-tab request identity changed");
        List<BusinessObjectRef> reversed=new ArrayList<BusinessObjectRef>(bound);Collections.reverse(reversed);
        NativeBatchRequestScope.verify("G","ORG","BD_CUSTOMER",reversed,changes);
        reject("G","ORG","BD_CUSTOMER",bound,change("two"));
        reject("G","OTHER","BD_CUSTOMER",bound,changes);
        reject("OTHER","ORG","BD_CUSTOMER",bound,changes);
        reject("G","ORG","BD_CUSTOMER",Collections.singletonList(bound.get(0)),changes);
        List<BusinessObjectRef> altered=new ArrayList<BusinessObjectRef>(bound);
        BusinessObjectRef b=bound.get(1);altered.set(1,new BusinessObjectRef(b.getClientObjectId(),b.getObjectType(),"C",b.getBillCode(),null));
        reject("G","ORG","BD_CUSTOMER",altered,changes);
        altered.set(1,new BusinessObjectRef(b.getClientObjectId(),"BD_SUPPLIER",b.getObjectPk(),b.getBillCode(),null));
        reject("G","ORG","BD_CUSTOMER",altered,changes);
        altered.set(1,new BusinessObjectRef(b.getClientObjectId(),b.getObjectType(),b.getObjectPk(),b.getBillCode(),null,null,null,"OTHER"));
        reject("G","ORG","BD_CUSTOMER",altered,changes);
        altered.set(1,new BusinessObjectRef("forged",b.getObjectType(),b.getObjectPk(),b.getBillCode(),null));
        reject("G","ORG","BD_CUSTOMER",altered,changes);
        try { NativeBatchRequestScope.bind("G","ORG","BD_CUSTOMER",Arrays.asList(selected.get(0),selected.get(0)),changes);
            throw new AssertionError("Duplicate batch selection accepted"); }
        catch(BusinessException expected) { }
        List<BusinessObjectRef> large=new ArrayList<BusinessObjectRef>();
        for(int index=0;index<1001;index++) large.add(ref("selection-"+index,"BD_CUSTOMER","P"+index));
        List<BusinessObjectRef> authorized=NativeBatchRequestScope.bind("G","ORG","BD_CUSTOMER",large,changes);
        NativeBatchRequestScope.verify("G","ORG","BD_CUSTOMER",authorized,changes);
        reject("G","ORG","BD_CUSTOMER",authorized.subList(0,1000),changes);
        materialBatchValuesRemainBound();
        NativeOrgBatchSelectionTests.run();
        System.out.println("PASS NativeBatchRequestScopeTests (selection and typed changes bound to request; no NC or Oracle)");
    }
    private static void materialBatchValuesRemainBound() throws Exception {
        List<BusinessObjectRef> selected=Collections.singletonList(ref("client-a","BD_MATERIAL","A"));
        NativeBatchChangeSet mnemonic=materialChange("materialmnecode","001");
        List<BusinessObjectRef> signed=NativeBatchRequestScope.bind("G","ORG","BD_MATERIAL",selected,mnemonic);
        NativeBatchRequestScope.verify("G","ORG","BD_MATERIAL",signed,mnemonic);
        reject("G","ORG","BD_MATERIAL",signed,materialChange("materialmnecode","1"));
        reject("G","ORG","BD_MATERIAL",signed,materialChange("materialmnecode",Integer.valueOf(1)));
        reject("G","ORG","BD_MATERIAL",signed,materialChange("pk_marbasclass","001"));
        NativeBatchChangeSet classification=materialChange("pk_marbasclass","CLASS-A");
        List<BusinessObjectRef> signedClass=NativeBatchRequestScope.bind("G","ORG","BD_MATERIAL",selected,classification);
        reject("G","ORG","BD_MATERIAL",signedClass,materialChange("pk_marbasclass","CLASS-B"));
    }
    private static NativeBatchChangeSet materialChange(String field,Object value) throws BusinessException {
        return NativeBatchChangeSet.create(new String[]{field},Collections.<String,Object>singletonMap(field,value),
            Collections.singleton(field));
    }
    private static BusinessObjectRef ref(String id,String type,String pk) { return new BusinessObjectRef(id,type,pk,pk,null); }
    private static NativeBatchChangeSet change(String value) throws Exception {
        Map<String,Object> map=new HashMap<String,Object>();map.put("name",value);
        return NativeBatchChangeSet.create(new String[]{"name"},map,new HashSet<String>(Arrays.asList("name")));
    }
    private static void reject(String group,String org,String domain,List<BusinessObjectRef> refs,NativeBatchChangeSet changes) throws Exception {
        try { NativeBatchRequestScope.verify(group,org,domain,refs,changes);throw new AssertionError("Altered batch authorization accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
