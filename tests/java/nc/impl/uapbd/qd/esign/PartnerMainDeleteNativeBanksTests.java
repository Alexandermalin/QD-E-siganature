package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.audit.PartnerMainDeleteBanks;
import nc.vo.bd.cust.CustbankVO;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** NC deletes every bank master named in the passed array, so shared accounts must be excluded. */
public final class PartnerMainDeleteNativeBanksTests {
    private PartnerMainDeleteNativeBanksTests() { }
    public static void run() throws Exception {
        Rows rows=new Rows();PartnerMainDeleteBanks.Selection scope=PartnerMainDeleteBanks.capture("G","BD_CUSTOMER","C",rows,false);
        CustomerVO customer=new CustomerVO();customer.setPk_customer("C");customer.setPk_group("G");
        customer.setCustbanks(new CustbankVO[]{bank("UNTRUSTED","A2","C",1)});
        Loader loader=new Loader();PartnerMainDeleteNativeBanks.prepare(customer,scope,loader);
        require(customer.getCustbanks().length==1 && "R1".equals(customer.getCustbanks()[0].getPrimaryKey())
            && "A1".equals(customer.getCustbanks()[0].getPk_bankaccbas()),"Native array included a shared or client-provided account");
        SupplierVO supplier=new SupplierVO();supplier.setPk_supplier("S");supplier.setPk_group("G");
        supplier.setSupbankacc(new CustbankVO[]{bank("UNTRUSTED","A2","S",3)});
        PartnerMainDeleteBanks.Selection supplierScope=PartnerMainDeleteBanks.capture("G","BD_SUPPLIER","S",rows,false);
        PartnerMainDeleteNativeBanks.prepare(supplier,supplierScope,loader);
        require(supplier.getSupbankacc().length==0,"Shared supplier account was passed to the native physical-delete service");
        loader.value=bank("R1","OTHER","C",1);reject(customer,scope,loader);
        loader.value=bank("R1","A1","OTHER",1);reject(customer,scope,loader);
        loader.value=bank("R1","A1","C",3);reject(customer,scope,loader);
        loader.value=bank("OTHER","A1","C",1);reject(customer,scope,loader);
        loader.value=null;reject(customer,scope,loader);
        customer.setPk_group("OTHER");loader.value=bank("R1","A1","C",1);reject(customer,scope,loader);
        System.out.println("PASS PartnerMainDeleteNativeBanksTests (server-owned native bank arrays; no NC or Oracle)");
    }
    private static void reject(CustomerVO value,PartnerMainDeleteBanks.Selection scope,Loader loader) throws Exception {
        try { PartnerMainDeleteNativeBanks.prepare(value,scope,loader);throw new AssertionError("Unsafe native bank array accepted"); }
        catch(BusinessException expected) { }
    }
    private static CustbankVO bank(String id,String account,String archive,int type) {
        CustbankVO row=new CustbankVO();row.setPk_custbank(id);row.setPk_bankaccbas(account);row.setPk_cust(archive);
        row.setAccclass(Integer.valueOf(type));return row;
    }
    private static SnapshotRow row(String table,String id,String... fields) {
        Map<String,String> values=new HashMap<String,String>();
        for(int i=0;i<fields.length;i+=2) values.put(fields[i],fields[i+1]);
        return new SnapshotRow(table,id,null,values,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),true);
    }
    private static SnapshotRow link(String id,String account,String archive,String type) {
        return row("bd_custbank",id,"pk_custbank",id,"pk_bankaccbas",account,"pk_cust",archive,"accclass",type);
    }
    private static final class Loader implements PartnerMainDeleteNativeBanks.Loader {
        CustbankVO value=bank("R1","A1","C",1);public CustbankVO load(String id) { return value; }
    }
    private static final class Rows implements PartnerMainDeleteBanks.Reader {
        final List<SnapshotRow> rows=Arrays.asList(row("bd_bankaccbas","A1","pk_bankaccbas","A1","pk_group","G"),
            row("bd_bankaccbas","A2","pk_bankaccbas","A2","pk_group","G"),
            link("R1","A1","C","1"),link("R2","A2","C","1"),link("R3","A2","S","3"));
        public List<SnapshotRow> read(String table,String key,String match,String value,boolean lock) {
            List<SnapshotRow> result=new ArrayList<SnapshotRow>();
            for(SnapshotRow row:rows) if(table.equals(row.getEntity()) && value.equals(row.getValues().get(match))) result.add(row);
            return result;
        }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
