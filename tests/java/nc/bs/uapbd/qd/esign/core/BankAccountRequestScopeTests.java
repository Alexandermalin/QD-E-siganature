package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Request identities bind the account and all physical relationships, not client-supplied ownership. */
public final class BankAccountRequestScopeTests {
    private BankAccountRequestScopeTests() { }
    public static void run() throws Exception {
        Reader data=new Reader();BusinessObjectRef draft=new BusinessObjectRef("untrusted","BD_CUSTOMER_BANK","R1","bad-code",null);
        BankAccountRequestScope.Expanded original=BankAccountRequestScope.expand("G",draft,data,false);
        List<BusinessObjectRef> refs=original.getObjects();
        require(refs.size()==3 && refs.get(0).getParentClientObjectId()==null && "C_CODE".equals(refs.get(0).getBillCode()),
            "Selected relation was not normalized from the server");
        for(int i=1;i<refs.size();i++) require(refs.get(0).getClientObjectId().equals(refs.get(i).getParentClientObjectId()),
            "Related archive did not retain the selected relationship as its parent");
        BankAccountRequestScope.verify("G",refs,data,false);
        data.account="OTHER";reject(refs,data);
        data=new Reader();data.rows.remove(2);reject(refs,data);
        data=new Reader();data.rows.add(new BankAccountImpactScope.Relation("R4","BANK","C",Integer.valueOf(1)));reject(refs,data);
        data=new Reader();data.supplierCode="CHANGED";reject(refs,data);
        data=new Reader();List<BusinessObjectRef> altered=new ArrayList<BusinessObjectRef>(refs);
        BusinessObjectRef child=altered.get(1);altered.set(1,new BusinessObjectRef(child.getClientObjectId(),child.getObjectType(),
            child.getObjectPk(),child.getBillCode(),null));reject(altered,data);
        data=new Reader();altered=new ArrayList<BusinessObjectRef>(refs);altered.add(refs.get(1));reject(altered,data);
        BusinessObjectRef defaultDraft=new BusinessObjectRef("bank-default:USD_ROW","BD_CUSTOMER_BANK","R1",null,null);
        List<BusinessObjectRef> defaults=BankAccountRequestScope.expand("G",defaultDraft,data,false).getObjects();
        require("USD_ROW".equals(BankAccountRequestScope.defaultCurrency(defaults.get(0)))
            && !refs.get(0).getClientObjectId().equals(defaults.get(0).getClientObjectId()),
            "The default currency row must be bound to the account authorization");
        BankAccountRequestScope.verify("G",defaults,data,false);
        altered=new ArrayList<BusinessObjectRef>(defaults);
        BusinessObjectRef root=altered.get(0);
        altered.set(0,new BusinessObjectRef(root.getClientObjectId().replace("USD_ROW","CNY_ROW"),root.getObjectType(),
            root.getObjectPk(),root.getBillCode(),null));
        reject(altered,data);
        System.out.println("PASS BankAccountRequestScopeTests (persisted account and relationship membership; no NC or Oracle)");
    }
    private static void reject(List<BusinessObjectRef> refs,Reader reader) throws Exception {
        try { BankAccountRequestScope.verify("G",refs,reader,false);throw new AssertionError("Changed bank request scope accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static final class Reader implements BankAccountRequestScope.Reader {
        String account="BANK",supplierCode="S_CODE";
        final List<BankAccountImpactScope.Relation> rows=new ArrayList<BankAccountImpactScope.Relation>(Arrays.asList(
            new BankAccountImpactScope.Relation("R1","BANK","C",Integer.valueOf(1)),
            new BankAccountImpactScope.Relation("R2","BANK","C",Integer.valueOf(1)),
            new BankAccountImpactScope.Relation("R3","BANK","S",Integer.valueOf(3))));
        public String accountForRelation(String relation,boolean lock) { return account; }
        public List<BankAccountImpactScope.Relation> relations(String account,boolean lock) {
            List<BankAccountImpactScope.Relation> result=new ArrayList<BankAccountImpactScope.Relation>();
            for(BankAccountImpactScope.Relation row:rows) result.add(new BankAccountImpactScope.Relation(row.id,account,row.archive,row.accountClass));
            return result;
        }
        public BankAccountImpactScope.Owner owner(String group,String domain,String relation,boolean lock) {
            return "R3".equals(relation)?new BankAccountImpactScope.Owner(group,"OS","S",supplierCode)
                :new BankAccountImpactScope.Owner(group,"OC","C","C_CODE");
        }
    }
}
