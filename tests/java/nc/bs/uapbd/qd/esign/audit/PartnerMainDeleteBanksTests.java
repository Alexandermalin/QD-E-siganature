package nc.bs.uapbd.qd.esign.audit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Shared accounts survive the exact physical relationship deletion performed by the native listener. */
public final class PartnerMainDeleteBanksTests {
    private PartnerMainDeleteBanksTests() { }

    public static void run() throws Exception {
        Rows data=new Rows();
        PartnerMainDeleteBanks.Selection before=PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",data,true);
        require(before.getExclusiveAccounts().equals(Collections.singleton("A1")),"Shared account selected for physical deletion");
        require("R1".equals(before.getExclusiveRelations().get("A1")),"Exclusive account did not select an authoritative relation");
        require(before.getRows().size()==11 && data.locked>0,"Account graph or locked reads missing");
        List<String> evidence=before.evidence();
        data.add(link("R6","A1","OTHER","1","1"));
        PartnerMainDeleteBanks.Selection changed=PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",data,false);
        require(changed.getExclusiveAccounts().isEmpty() && !evidence.equals(changed.evidence()),
            "A deleted-status external relationship must protect the account and change authorization evidence");

        data=new Rows();before=PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",data,false);
        data.remove("bd_custbank","R1","R2","R3");
        data.remove("bd_bankaccbas","A1");data.remove("bd_bankaccsub","S1");data.remove("bd_bankaccuse","U1");
        List<SnapshotRow> after=PartnerMainDeleteBanks.verifyAfter(before,data,true);
        require(after.size()==5 && find(after,"bd_custbank","R4")!=null && find(after,"bd_custbank","R5")!=null,
            "Shared account and its other relationships were not preserved");

        Rows modified=new Rows();PartnerMainDeleteBanks.Selection signed=PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",modified,false);
        modified.remove("bd_custbank","R1","R2","R3","R4");
        rejectAfter(signed,modified,"Deletion of another archive's relationship was accepted");
        modified=new Rows();signed=PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",modified,false);
        modified.remove("bd_custbank","R1","R2","R3");modified.remove("bd_bankaccbas","A2");
        rejectAfter(signed,modified,"Deletion of a shared account master was accepted");
        modified=new Rows();signed=PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",modified,false);
        modified.remove("bd_custbank","R1","R2","R3");modified.add(link("NEW","A1","OTHER","1","0"));
        rejectAfter(signed,modified,"A newly shared account was accepted after deletion");
        modified=new Rows();signed=PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",modified,false);
        modified.remove("bd_custbank","R1","R2","R3");modified.remove("bd_bankaccbas","A1");
        rejectAfter(signed,modified,"An orphaned currency or use row was accepted after deletion");
        modified=new Rows();modified.remove("bd_bankaccuse","U1");modified.add(use("U1",null,"S1"));
        signed=PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",modified,false);
        modified.remove("bd_custbank","R1","R2","R3");modified.remove("bd_bankaccbas","A1");modified.remove("bd_bankaccsub","S1");
        rejectAfter(signed,modified,"An orphaned use row with no account column was accepted");
        modified=new Rows();signed=PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",modified,false);
        modified.remove("bd_custbank","R1","R2","R3");modified.add(link("R1","OTHER","OTHER","1","0"));
        modified.remove("bd_bankaccbas","A1");modified.remove("bd_bankaccsub","S1");modified.remove("bd_bankaccuse","U1");
        rejectAfter(signed,modified,"An own relation transferred to a different account and archive was accepted");
        modified=new Rows();signed=PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",modified,false);
        modified.remove("bd_custbank","R1","R2","R3");modified.remove("bd_bankaccbas","A1");
        modified.remove("bd_bankaccsub","S1");modified.add(sub("S1","OTHER"));modified.remove("bd_bankaccuse","U1");
        rejectAfter(signed,modified,"An exclusive currency transferred to a different account was accepted");
        modified=new Rows();signed=PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",modified,false);
        modified.remove("bd_custbank","R1","R2","R3");modified.add(link("NEW","A2","C","1","0"));
        rejectAfter(signed,modified,"A new own relationship was accepted after deletion");
        modified=new Rows();modified.remove("bd_bankaccbas","A1");rejectCapture(modified,"Missing physical account accepted");
        modified=new Rows();modified.remove("bd_bankaccbas","A1");modified.add(master("A1","OTHER"));
        rejectCapture(modified,"Cross-group account accepted");
        modified=new Rows();modified.add(link("R1","A1","C","1","0"));rejectCapture(modified,"Duplicate relation accepted");
        System.out.println("PASS PartnerMainDeleteBanksTests (physical account graph and shared account preservation; no NC or Oracle)");
    }

    private static void rejectCapture(Rows data,String message) throws Exception {
        try { PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",data,false);throw new AssertionError(message); }
        catch(BusinessException expected) { }
    }
    private static void rejectAfter(PartnerMainDeleteBanks.Selection before,Rows data,String message) throws Exception {
        try { PartnerMainDeleteBanks.verifyAfter(before,data,false);throw new AssertionError(message); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static SnapshotRow find(List<SnapshotRow> rows,String table,String pk) {
        for(SnapshotRow row:rows) if(table.equals(row.getEntity()) && pk.equals(row.getRowId())) return row;return null;
    }
    private static SnapshotRow row(String table,String id,String... fields) {
        Map<String,String> values=new HashMap<String,String>();
        for(int index=0;index<fields.length;index+=2) values.put(fields[index],fields[index+1]);
        return new SnapshotRow(table,id,null,values,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static SnapshotRow master(String id,String group) { return row("bd_bankaccbas",id,"pk_bankaccbas",id,"pk_group",group); }
    private static SnapshotRow sub(String id,String account) { return row("bd_bankaccsub",id,"pk_bankaccsub",id,"pk_bankaccbas",account); }
    private static SnapshotRow use(String id,String account,String sub) {
        return row("bd_bankaccuse",id,"pk_bankaccuse",id,"pk_bankaccbas",account,"pk_bankaccsub",sub);
    }
    private static SnapshotRow link(String id,String account,String archive,String type,String dr) {
        return row("bd_custbank",id,"pk_custbank",id,"pk_bankaccbas",account,"pk_cust",archive,"accclass",type,"dr",dr);
    }
    private static final class Rows implements PartnerMainDeleteBanks.Reader {
        final List<SnapshotRow> rows=new ArrayList<SnapshotRow>();int locked;
        Rows() {
            rows.addAll(Arrays.asList(master("A1","G"),sub("S1","A1"),use("U1","A1","S1"),
                master("A2","G"),sub("S2","A2"),use("U2","A2","S2"),
                link("R1","A1","C","1","0"),link("R2","A1","C","1","0"),
                link("R3","A2","C","1","0"),link("R4","A2","SUP","3","1"),link("R5","A2","C","3","0")));
        }
        void add(SnapshotRow row) { rows.add(row); }
        void remove(String table,String... ids) {
            List<String> selected=Arrays.asList(ids);
            for(Iterator<SnapshotRow> it=rows.iterator();it.hasNext();) {
                SnapshotRow row=it.next();if(table.equals(row.getEntity()) && selected.contains(row.getRowId())) it.remove();
            }
        }
        public List<SnapshotRow> read(String table,String key,String match,String value,boolean lock) {
            if(lock) locked++;
            List<SnapshotRow> result=new ArrayList<SnapshotRow>();
            for(SnapshotRow row:rows) if(table.equals(row.getEntity()) && value.equals(row.getValues().get(match))) result.add(row);
            return result;
        }
    }
}
