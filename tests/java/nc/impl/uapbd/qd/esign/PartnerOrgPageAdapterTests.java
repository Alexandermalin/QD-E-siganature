package nc.impl.uapbd.qd.esign;

import java.util.HashSet;
import java.util.Set;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.PartnerOrgPage;
import nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType;
import nc.vo.bd.cust.CustbankVO;

/** Static identities and native VO metadata; native transactions require integration tests. */
public final class PartnerOrgPageAdapterTests {
    private PartnerOrgPageAdapterTests() { }
    public static void run() throws Exception {
        Set<String> domains=new HashSet<String>();
        for(PartnerOrgPage page:PartnerOrgPage.values()) {
            SuperVO value=page.type.newInstance();
            require(domains.add(page.domain) && page.table.equals(value.getTableName()) && page.key.equals(value.getPKFieldName()),
                "Partner page physical identity disagrees with NC: "+page.domain);
            require(PartnerOrgPage.forAction(page.groupNode,page.saveButton,"UPDATE")==page
                && PartnerOrgPage.forAction(page.unitNode,page.saveButton,"UPDATE")==page
                && PartnerOrgPage.forAction(page.groupNode,page.deleteButton,"DELETE")==page,
                "Partner page action must include node and operation identity: "+page.domain);
            require(PartnerOrgPage.forAction(page.groupNode,page.deleteButton,"UPDATE")==null,
                "A delete button must not resolve as a save");
        }
        require(PartnerOrgPage.forAction("10140MAG","FiSave","UPDATE")==null,"Material and partner nodes cannot share page definitions");
        require(PartnerOrgPage.forAction("10140SUG","FiDelete","DELETE")==null,"Supplier finance delete is FiDel, not FiDelete");
        CustbankVO bank=new CustbankVO();
        require("bd_custbank".equals(bank.getTableName()) && "pk_custbank".equals(bank.getPKFieldName())
            && "pk_cust".equals(bank.getParentPKFieldName()),"Bank owner relation must match native VO metadata");
        require(Integer.valueOf(1).equals(BaseArchiveOwnerType.CUSTOMER_BANK.bankAccountClass)
            && Integer.valueOf(3).equals(BaseArchiveOwnerType.SUPPLIER_BANK.bankAccountClass)
            && "pk_cust".equals(BaseArchiveOwnerType.CUSTOMER_BANK.childRootKey)
            && BaseArchiveOwnerType.CUSTOMER_BANK.rootType==BaseArchiveOwnerType.CUSTOMER
            && BaseArchiveOwnerType.SUPPLIER_BANK.rootType==BaseArchiveOwnerType.SUPPLIER,
            "Customer and supplier bank relations must resolve against their distinct root types");
        System.out.println("PASS PartnerOrgPageAdapterTests (native VO metadata and node/button identities; no NC transaction)");
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
