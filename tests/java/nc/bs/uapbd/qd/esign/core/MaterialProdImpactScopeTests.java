package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialProdImpact;

/** Impact-scope logic with an explicit reader double; the NC query and CMT are not invoked. */
public final class MaterialProdImpactScopeTests {
    private MaterialProdImpactScopeTests() { }
    public static void run() throws Exception {
        final BusinessObjectRef root=new BusinessObjectRef("root","BD_MATERIAL_PROD","P0","M0",null);
        final MaterialProdImpact on=new MaterialProdImpact("ORG","COST",true);
        final MaterialProdImpact off=new MaterialProdImpact("ORG",null,false);
        Fake reader=new Fake();reader.rows=Arrays.asList(row("P2","G","ORG","COST","M2","CODE2"),
            row("P1","G","ORG","COST","M1","CODE1"));
        List<BusinessObjectRef> single=MaterialProdImpactScope.expand("G",root,off,reader);
        require(single.size()==1 && reader.calls==0,"Unselected class-feature mode must not query related rows");
        List<BusinessObjectRef> expanded=MaterialProdImpactScope.expand("G",root,on,reader);
        require(expanded.size()==3 && "P1".equals(expanded.get(1).getObjectPk()) && "P2".equals(expanded.get(2).getObjectPk())
            && "root".equals(expanded.get(1).getParentClientObjectId()),"Affected rows must be deterministically ordered under the selected row");
        List<BusinessObjectRef> repeated=MaterialProdImpactScope.expand("G",root,on,reader);
        require(expanded.get(1).getClientObjectId().equals(repeated.get(1).getClientObjectId()),"Impact identities must remain stable during recheck");
        MaterialProdImpactScope.verify("G",expanded,on,reader);
        reader.rows=Collections.singletonList(row("P1","G","ORG","COST","M1","CODE1"));
        rejectVerify(expanded,on,reader);
        reader.rows=Arrays.asList(row("P1","G","ORG","COST","M1","CODE1"),row("P1","G","ORG","COST","M1","CODE1"));
        rejectExpand(root,on,reader);
        reader.rows=Collections.singletonList(row("P1","OTHER","ORG","COST","M1","CODE1"));rejectExpand(root,on,reader);
        reader.rows=Collections.singletonList(row("P1","G","OTHER","COST","M1","CODE1"));rejectExpand(root,on,reader);
        reader.rows=Collections.singletonList(row("P1","G","ORG","OTHER","M1","CODE1"));rejectExpand(root,on,reader);
        reader.rows=Collections.singletonList(row("P0","G","ORG","COST","M0","CODE0"));rejectExpand(root,on,reader);
        rejectExpand(root,new MaterialProdImpact("ORG",null,true),reader);
        rejectExpand(root,new MaterialProdImpact("ORG' OR '1'='1","COST",true),reader);
        reader.rows=Collections.<MaterialProdImpactScope.Row>emptyList();
        MaterialProdImpactScope.verify("G",single,off,reader);
        System.out.println("PASS MaterialProdImpactScopeTests (candidate cascade membership; no NC query or transaction)");
    }
    private static void rejectExpand(BusinessObjectRef root,MaterialProdImpact impact,Fake reader) throws Exception {
        try { MaterialProdImpactScope.expand("G",root,impact,reader);throw new AssertionError("Invalid cascade scope was accepted"); }
        catch(BusinessException expected) { }
    }
    private static void rejectVerify(List<BusinessObjectRef> rows,MaterialProdImpact impact,Fake reader) throws Exception {
        try { MaterialProdImpactScope.verify("G",rows,impact,reader);throw new AssertionError("Changed cascade scope was accepted"); }
        catch(BusinessException expected) { }
    }
    private static MaterialProdImpactScope.Row row(String pk,String group,String org,String cost,String material,String code) {
        return new MaterialProdImpactScope.Row(pk,group,org,cost,material,code);
    }
    private static final class Fake implements MaterialProdImpactScope.Reader {
        int calls;List<MaterialProdImpactScope.Row> rows=new ArrayList<MaterialProdImpactScope.Row>();
        public List<MaterialProdImpactScope.Row> affected(String group,String org,String cost,String pk) { calls++;return rows; }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
