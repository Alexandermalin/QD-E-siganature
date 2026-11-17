package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Shared account scope checks without an Oracle connection. */
public final class BankAccountImpactScopeTests {
    private BankAccountImpactScopeTests() { }
    public static void run() throws Exception {
        FakeReader reader=reader();BusinessObjectRef selected=new BusinessObjectRef("client","BD_CUSTOMER_BANK","R1",null,null);
        BankAccountImpactScope.Selection original=BankAccountImpactScope.select("G","BANK",selected,reader,false);
        require(original.getArchives().size()==2,"Shared customer and supplier were not both discovered");
        require(original.getArchives().get(0).getRelations().equals(Arrays.asList("R1","R2")),"Multiple currencies were not grouped under one archive");
        BankAccountImpactScope.verifyUnchanged(original,BankAccountImpactScope.select("G","BANK",selected,reader,false));
        reader.owners.put("R3",owner("G","OTHER","S","SUP"));
        BankAccountImpactScope.Selection moved=BankAccountImpactScope.select("G","BANK",selected,reader,false);
        try { BankAccountImpactScope.verifyUnchanged(original,moved);throw new AssertionError("Changed owner accepted"); }
        catch(BusinessException expected) { }
        reader=reader();reader.rows.add(relation("R4","BANK","X",8));reject(reader,selected);
        reader=reader();reader.rows.add(relation("R1","BANK","C",1));reject(reader,selected);
        reader=reader();reader.owners.put("R3",owner("OTHER_GROUP","OS","S","SUP"));reject(reader,selected);
        reader=reader();reader.owners.put("R2",owner("G","OTHER","C","CUST"));reject(reader,selected);
        reader=reader();reject(reader,new BusinessObjectRef("client","BD_SUPPLIER_BANK","R1",null,null));
        reader=reader();reader.rows.remove(2);
        BankAccountImpactScope.Selection fewer=BankAccountImpactScope.select("G","BANK",selected,reader,false);
        try { BankAccountImpactScope.verifyUnchanged(original,fewer);throw new AssertionError("Removed account relation accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS BankAccountImpactScopeTests (shared owner and relation changes; no NC or Oracle)");
    }
    private static FakeReader reader() {
        FakeReader reader=new FakeReader();reader.rows.add(relation("R1","BANK","C",1));reader.rows.add(relation("R2","BANK","C",1));reader.rows.add(relation("R3","BANK","S",3));
        reader.owners.put("R1",owner("G","OC","C","CUST"));reader.owners.put("R2",owner("G","OC","C","CUST"));reader.owners.put("R3",owner("G","OS","S","SUP"));
        return reader;
    }
    private static BankAccountImpactScope.Relation relation(String id,String account,String archive,int type) { return new BankAccountImpactScope.Relation(id,account,archive,Integer.valueOf(type)); }
    private static BankAccountImpactScope.Owner owner(String group,String org,String archive,String code) { return new BankAccountImpactScope.Owner(group,org,archive,code); }
    private static void reject(FakeReader reader,BusinessObjectRef selected) throws Exception {
        try { BankAccountImpactScope.select("G","BANK",selected,reader,false);throw new AssertionError("Invalid shared account scope accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    private static final class FakeReader implements BankAccountImpactScope.Reader {
        final List<BankAccountImpactScope.Relation> rows=new ArrayList<BankAccountImpactScope.Relation>();
        final Map<String,BankAccountImpactScope.Owner> owners=new HashMap<String,BankAccountImpactScope.Owner>();
        public List<BankAccountImpactScope.Relation> relations(String account,boolean lock) { return rows; }
        public BankAccountImpactScope.Owner owner(String group,String domain,String relation,boolean lock) { return owners.get(relation); }
    }
}
