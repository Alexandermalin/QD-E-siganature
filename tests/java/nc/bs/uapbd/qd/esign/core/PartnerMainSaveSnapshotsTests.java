package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.audit.PartnerMainSaveSnapshots;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.FieldChange;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Covers paired roots, shared main record and inserted/deleted structure rows. */
public final class PartnerMainSaveSnapshotsTests {
    private PartnerMainSaveSnapshotsTests() { }
    public static void run() throws Exception {
        PartnerMainSaveImpact.Selection selection=PartnerMainSaveImpact.select("G","BD_CUSTOMER","C","P2",true,
            new PartnerMainSaveImpactTests.Data(),false);
        Rows data=new Rows();Map<String,List<SnapshotRow>> before=PartnerMainSaveSnapshots.capture("G",selection,
            Collections.<String,String>emptyMap(),false,data,true);
        require(before.size()==5 && before.get("BD_CUSTOMER\u0000C").size()==3 && before.get("BD_SUPPLIER\u0000C").size()==2
            && before.get("BD_CUSTOMER\u0000P2").size()==1,"Main, paired, shared or absent new-parent structure was miscaptured");
        data.put(root("bd_customer","pk_customer","C","B","C_NEW","P2"));
        data.put(root("bd_supplier","pk_supplier","C","B","C_CODE",null));
        data.put(structure("bd_custstructure","pk_customer","C","P2","88882222"));
        data.put(structure("bd_custstructure","pk_customer","D","C","888822223333"));
        data.rows.remove("bd_custstructure/P1");data.put(structure("bd_custstructure","pk_customer","P2",null,"8888"));
        data.put(shared("C","B"));Map<String,String> owners=new HashMap<String,String>();
        owners.put("BD_CUSTOMER\u0000C","B");owners.put("BD_SUPPLIER\u0000C","B");
        Map<String,List<SnapshotRow>> after=PartnerMainSaveSnapshots.capture("G",selection,owners,true,data,false);
        require(changed(before,after,"BD_CUSTOMER\u0000C","bd_customer","pk_org")
            && changed(before,after,"BD_CUSTOMER\u0000C","bd_custstructure","pk_parent")
            && changed(before,after,"BD_SUPPLIER\u0000C","bd_supplier","pk_org")
            && changed(before,after,"BD_CUSTOMER\u0000P1","bd_custstructure","pk_customer")
            && changed(before,after,"BD_CUSTOMER\u0000P2","bd_custstructure","pk_customer"),"Physical changes did not align by table and row");
        data.put(root("bd_customer","pk_customer","C","OTHER","C_NEW","P2"));reject(selection,owners,data);
        data=new Rows();owners=new HashMap<String,String>();owners.put("BD_CUSTOMER\u0000D","B");reject(selection,owners,data);
        System.out.println("PASS PartnerMainSaveSnapshotsTests (shared main and changed structure rows; no NC or Oracle)");
    }
    private static boolean changed(Map<String,List<SnapshotRow>> before,Map<String,List<SnapshotRow>> after,String id,String table,String field) {
        for(FieldChange change:SnapshotSupport.diff(before.get(id),after.get(id)))
            if(table.equals(change.getRow().getEntity()) && field.equals(change.getFieldCode())) return true;return false;
    }
    private static void reject(PartnerMainSaveImpact.Selection selection,Map<String,String> owners,Rows data) throws Exception {
        try { PartnerMainSaveSnapshots.capture("G",selection,owners,true,data,false);throw new AssertionError("Invalid partner snapshot accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static SnapshotRow root(String table,String key,String pk,String owner,String code,String parent) {
        return row(table,pk,key,pk,"pk_group","G","pk_org",owner,"code",code,
            "bd_customer".equals(table)?"pk_customer_main":"pk_supplier_main",parent);
    }
    private static SnapshotRow structure(String table,String key,String pk,String parent,String code) {
        return row(table,pk,key,pk,"pk_parent",parent,"innercode",code,"dr","0");
    }
    private static SnapshotRow shared(String pk,String owner) { return row("bd_cust_supplier",pk,"pk_cust_sup",pk,"pk_group","G","pk_org",owner); }
    private static SnapshotRow row(String table,String id,String... pairs) {
        Map<String,String> values=new HashMap<String,String>();for(int i=0;i<pairs.length;i+=2) values.put(pairs[i],pairs[i+1]);
        return new SnapshotRow(table,id,null,values,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static final class Rows implements PartnerMainSaveSnapshots.Reader {
        final Map<String,SnapshotRow> rows=new HashMap<String,SnapshotRow>();
        Rows() {
            put(root("bd_customer","pk_customer","C","A","C_CODE","P1"));put(root("bd_supplier","pk_supplier","C","G","C_CODE",null));
            put(root("bd_customer","pk_customer","D","B","D_CODE","C"));put(root("bd_customer","pk_customer","P1","A","P1_CODE",null));
            put(root("bd_customer","pk_customer","P2","B","P2_CODE",null));
            put(structure("bd_custstructure","pk_customer","C","P1","11112222"));put(structure("bd_custstructure","pk_customer","D","C","111122223333"));
            put(structure("bd_custstructure","pk_customer","P1",null,"1111"));put(shared("C","A"));
        }
        void put(SnapshotRow row) { rows.put(row.getEntity()+"/"+row.getRowId(),row); }
        public List<SnapshotRow> read(String table,String key,String value,String scope,String scopeValue,boolean active,boolean lock) {
            SnapshotRow row=rows.get(table+"/"+value);return row==null?new ArrayList<SnapshotRow>():Collections.singletonList(row);
        }
    }
}
