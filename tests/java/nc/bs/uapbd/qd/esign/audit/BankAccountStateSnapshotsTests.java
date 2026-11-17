package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.BankAccountImpactScope;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.audit.FieldChange;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Shared bank-account state snapshots must preserve each affected archive's physical rows. */
public final class BankAccountStateSnapshotsTests {
    private BankAccountStateSnapshotsTests() { }
    public static void run() throws Exception {
        BankAccountImpactScope.Selection selection=BankAccountImpactScope.select("G","BANK",
            new BusinessObjectRef("selected","BD_CUSTOMER_BANK","R1",null,null),new Impact(),false);
        Rows data=new Rows();Map<String,List<SnapshotRow>> before=BankAccountStateSnapshots.capture("G",selection,data,true);
        List<SnapshotRow> customer=before.get("BD_CUSTOMER_BANK\u0000C");
        List<SnapshotRow> supplier=before.get("BD_SUPPLIER_BANK\u0000S");
        require(customer!=null && customer.size()==8 && supplier!=null && supplier.size()==7,
            "Affected archives did not retain the master, all currencies, and their own relations");
        require("C".equals(find(customer,"bd_bankaccbas","BANK").getParentRowId())
            && "S".equals(find(supplier,"bd_bankaccbas","BANK").getParentRowId()),"Master parent was not aligned to each archive");
        require(find(customer,"bd_custbank","R3")==null && find(supplier,"bd_custbank","R1")==null,
            "A different archive's relationship leaked into the snapshot");
        require(data.lockedReads==7,"Locked physical reads were not propagated");
        data.master=master("2","G");Map<String,List<SnapshotRow>> after=BankAccountStateSnapshots.capture("G",selection,data,false);
        for(String id:before.keySet()) {
            List<FieldChange> changes=SnapshotSupport.diff(before.get(id),after.get(id));
            require(changes.size()==1 && "bd_bankaccbas".equals(changes.get(0).getRow().getEntity())
                && "enablestate".equals(changes.get(0).getFieldCode()) && "3".equals(changes.get(0).getBeforeValue())
                && "2".equals(changes.get(0).getAfterValue()),"Shared state difference did not align by table and stable row");
        }
        data=new Rows();data.master=master("3","OTHER");reject(selection,data);
        data=new Rows();data.roots.put("C",root("bd_customer","pk_customer","C","G","OTHER","C_CODE"));reject(selection,data);
        data=new Rows();data.roots.put("C",root("bd_customer","pk_customer","C","OTHER","OC","C_CODE"));reject(selection,data);
        data=new Rows();data.roots.put("C",root("bd_customer","pk_customer","C","G","OC","DIFFERENT"));reject(selection,data);
        data=new Rows();data.children.add(currency("SUB3","OTHER"));reject(selection,data);
        data=new Rows();data.children.add(currency("SUB1","BANK"));reject(selection,data);
        data=new Rows();data.uses.get("SUB1").add(use("USE3","SUB1","OTHER"));reject(selection,data);
        data=new Rows();data.uses.get("SUB2").add(use("USE1","SUB2","BANK"));reject(selection,data);
        data=new Rows();data.links.remove(2);reject(selection,data);
        data=new Rows();data.links.add(link("R4","BANK","C","1"));reject(selection,data);
        data=new Rows();data.links.set(0,link("R1","BANK","C","3"));reject(selection,data);
        data=new Rows();data.links.set(0,link("R1","BANK","OTHER","1"));reject(selection,data);
        data=new Rows();data.links.add(link("R1","BANK","C","1"));reject(selection,data);
        System.out.println("PASS BankAccountStateSnapshotsTests (shared account physical rows and scope changes; no NC or Oracle)");
    }
    private static void reject(BankAccountImpactScope.Selection selection,Rows rows) throws Exception {
        try { BankAccountStateSnapshots.capture("G",selection,rows,false);throw new AssertionError("Invalid bank snapshot scope accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static SnapshotRow find(List<SnapshotRow> rows,String table,String pk) {
        for(SnapshotRow row:rows) if(table.equals(row.getEntity()) && pk.equals(row.getRowId())) return row;
        return null;
    }
    private static SnapshotRow master(String state,String group) {
        return row("bd_bankaccbas","BANK",null,"pk_bankaccbas","BANK","pk_group",group,"enablestate",state);
    }
    private static SnapshotRow currency(String id,String account) {
        return row("bd_bankaccsub",id,account,"pk_bankaccsub",id,"pk_bankaccbas",account,"description",null);
    }
    private static SnapshotRow link(String id,String account,String archive,String type) {
        return row("bd_custbank",id,archive,"pk_custbank",id,"pk_bankaccbas",account,"pk_cust",archive,"accclass",type);
    }
    private static SnapshotRow use(String id,String child,String account) {
        return row("bd_bankaccuse",id,child,"pk_bankaccuse",id,"pk_bankaccsub",child,"pk_bankaccbas",account,"pk_org","ORG");
    }
    private static SnapshotRow root(String table,String key,String id,String group,String org,String code) {
        return row(table,id,null,key,id,"pk_group",group,"pk_org",org,"code",code);
    }
    private static SnapshotRow row(String table,String id,String parent,String... fields) {
        Map<String,String> values=new HashMap<String,String>();
        for(int i=0;i<fields.length;i+=2) values.put(fields[i],fields[i+1]);
        return new SnapshotRow(table,id,parent,values,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static final class Rows implements BankAccountStateSnapshots.Reader {
        SnapshotRow master=master("3","G");
        final List<SnapshotRow> children=new ArrayList<SnapshotRow>(Arrays.asList(currency("SUB1","BANK"),currency("SUB2","BANK")));
        final List<SnapshotRow> links=new ArrayList<SnapshotRow>(Arrays.asList(link("R1","BANK","C","1"),link("R2","BANK","C","1"),link("R3","BANK","S","3")));
        final Map<String,List<SnapshotRow>> uses=new HashMap<String,List<SnapshotRow>>();
        final Map<String,SnapshotRow> roots=new HashMap<String,SnapshotRow>();int lockedReads;
        Rows() { uses.put("SUB1",new ArrayList<SnapshotRow>(Arrays.asList(use("USE1","SUB1","BANK"))));
            uses.put("SUB2",new ArrayList<SnapshotRow>(Arrays.asList(use("USE2","SUB2","BANK"))));
            roots.put("C",root("bd_customer","pk_customer","C","G","OC","C_CODE"));
            roots.put("S",root("bd_supplier","pk_supplier","S","G","OS","S_CODE")); }
        public List<SnapshotRow> read(String table,String key,String parent,String match,String value,
                String scope,String scopeValue,boolean lock) {
            if(lock) lockedReads++;
            if("bd_bankaccbas".equals(table)) return Collections.singletonList(master);
            if("bd_bankaccsub".equals(table)) return children;
            if("bd_custbank".equals(table)) return links;
            if("bd_bankaccuse".equals(table)) return uses.get(value);
            return Collections.singletonList(roots.get(value));
        }
    }
    private static final class Impact implements BankAccountImpactScope.Reader {
        public List<BankAccountImpactScope.Relation> relations(String account,boolean lock) {
            return Arrays.asList(new BankAccountImpactScope.Relation("R1",account,"C",Integer.valueOf(1)),
                new BankAccountImpactScope.Relation("R2",account,"C",Integer.valueOf(1)),
                new BankAccountImpactScope.Relation("R3",account,"S",Integer.valueOf(3)));
        }
        public BankAccountImpactScope.Owner owner(String group,String domain,String relation,boolean lock) {
            return "R3".equals(relation)?new BankAccountImpactScope.Owner(group,"OS","S","S_CODE")
                :new BankAccountImpactScope.Owner(group,"OC","C","C_CODE");
        }
    }
}
