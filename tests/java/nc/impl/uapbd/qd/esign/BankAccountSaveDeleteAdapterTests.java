package nc.impl.uapbd.qd.esign;

import java.util.Collections;
import nc.vo.bd.bankaccount.BankAccbasVO;
import nc.vo.bd.bankaccount.cust.CustBankaccUnionVO;
import nc.vo.bd.cust.CustbankVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BankAccountDeleteCommand;
import nc.vo.uapbd.qd.esign.BankAccountSaveCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Tests bank account save/delete route guards and payload draft checks; no NC or Oracle. */
public final class BankAccountSaveDeleteAdapterTests {
    private BankAccountSaveDeleteAdapterTests() { }

    public static void run() throws Exception {
        require(BankAccountSaveAdapter.isRoute("10140CBA","Add","INSERT"),"Customer add route rejected");
        require(BankAccountSaveAdapter.isRoute("10140SBA","Edit","UPDATE"),"Supplier edit route rejected");
        require(!BankAccountSaveAdapter.isRoute("10140CBA","Edit","INSERT"),"Insert must use the Add button");
        require(!BankAccountSaveAdapter.isRoute("10140CBA","Add","UPDATE"),"Update must use the Edit button");
        require(!BankAccountSaveAdapter.isRoute("10140CBA","Save","INSERT"),"Unregistered Save button must be rejected");
        require(!BankAccountSaveAdapter.isRoute("10140CBA","Delete","DELETE"),"Delete button must not enter save adapter");
        require(!BankAccountSaveAdapter.isRoute("10140MAG","Add","INSERT"),"Material node must not enter bank save");
        require(BankAccountDeleteAdapter.isRoute("10140CBA","Delete","DELETE"),"Customer delete route rejected");
        require(BankAccountDeleteAdapter.isRoute("10140SBA","Delete","DELETE"),"Supplier delete route rejected");
        require(!BankAccountDeleteAdapter.isRoute("10140CBA","Save","DELETE"),"Save button must not enter delete adapter");

        BankAccountSaveAdapter save=new BankAccountSaveAdapter();
        BankAccountDeleteAdapter delete=new BankAccountDeleteAdapter();

        // 新增：客户弹窗、无主键关系与账户、集团一致。
        OperationContext insert=context("10140CBA","Add","INSERT","BD_CUSTOMER_BANK","CUST1");
        save.validate(insert,saveCommand("CUST1",1,null,null,"G1"));
        // 修改：供应商弹窗、选中关系与版本存在。
        OperationContext update=context("10140SBA","Edit","UPDATE","BD_SUPPLIER_BANK","REL1");
        save.validate(update,saveCommand("SUP1",3,"REL1","ACC1","G1"));
        // 删除：客户弹窗、关系与账户主键一致。
        OperationContext removal=context("10140CBA","Delete","DELETE","BD_CUSTOMER_BANK","REL1");
        delete.validate(removal,deleteCommand("CUST1",1,"REL1","ACC1"));

        // 节点与档案类型交叉必须拒绝；错误载荷必须拒绝。
        rejectSave(context("10140CBA","Add","INSERT","BD_SUPPLIER_BANK",null),saveCommand("CUST1",1,null,null,"G1"),"Node/domain mismatch must be rejected");
        rejectSave(context("10140CBA","Add","INSERT","BD_CUSTOMER_BANK",null),saveCommand("CUST1",3,null,null,"G1"),"Customer relation must use account class 1");
        rejectSave(context("10140CBA","Add","INSERT","BD_CUSTOMER_BANK",null),saveCommand("CUST1",1,"REL1",null,"G1"),"Insert with existing relation must be rejected");
        rejectSave(insert,saveCommand("OTHER",1,null,null,"G1"),"Insert must not switch the authorized parent archive");
        rejectSave(context("10140SBA","Edit","UPDATE","BD_SUPPLIER_BANK","REL1"),saveCommand("SUP1",3,"OTHER","ACC1","G1"),"Relation mismatch must be rejected");
        rejectDelete(context("10140CBA","Delete","DELETE","BD_CUSTOMER_BANK","REL1"),deleteCommand("CUST1",1,"REL1",null),"Account mismatch must be rejected");
        rejectDelete(context("10140SBA","Delete","DELETE","BD_SUPPLIER_BANK","REL1"),deleteCommand("SUP1",1,"REL1","ACC1"),"Supplier relation must use account class 3");

        System.out.println("PASS BankAccountSaveDeleteAdapterTests (popup routes and payload guards; no NC or Oracle)");
    }

    private static void rejectSave(OperationContext context,BankAccountSaveCommand command,String message) throws Exception {
        try { new BankAccountSaveAdapter().validate(context,command); throw new AssertionError(message); }
        catch(BusinessException expected) { }
    }

    private static void rejectDelete(OperationContext context,BankAccountDeleteCommand command,String message) throws Exception {
        try { new BankAccountDeleteAdapter().validate(context,command); throw new AssertionError(message); }
        catch(BusinessException expected) { }
    }

    private static BankAccountSaveCommand saveCommand(String archive,int accclass,String relationPk,
            String accountPk,String group) throws Exception {
        return new BankAccountSaveCommand(union(archive,accclass,relationPk,accountPk,group),"V1");
    }

    private static BankAccountDeleteCommand deleteCommand(String archive,int accclass,String relationPk,String accountPk) throws Exception {
        return new BankAccountDeleteCommand(union(archive,accclass,relationPk,accountPk,"G1"),"V1");
    }

    private static CustBankaccUnionVO union(String archive,int accclass,String relationPk,String accountPk,String group) throws Exception {
        CustbankVO relation=new CustbankVO();
        relation.setPk_custbank(relationPk);
        relation.setPk_cust(archive);
        relation.setPk_bankaccbas(accountPk);
        relation.setAccclass(Integer.valueOf(accclass));
        BankAccbasVO account=new BankAccbasVO();
        account.setPk_bankaccbas(accountPk);
        account.setPk_group(group);
        // NC's constructor contacts its metadata server. These tests exercise only the adapter's
        // route/payload checks, so create an explicit test fixture without that online constructor.
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field singleton=unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object allocator=singleton.get(null);
        CustBankaccUnionVO union=(CustBankaccUnionVO)unsafeType.getMethod("allocateInstance",Class.class)
            .invoke(allocator,CustBankaccUnionVO.class);
        union.setCustbankVO(relation);
        union.setBankaccbasVO(account);
        return union;
    }

    private static OperationContext context(String node,String button,String operation,String type,String rootPk) {
        BusinessObjectRef root=new BusinessObjectRef("C1",type,rootPk,"BK-01",null,null,"ORG1");
        return new OperationContext(requestId(),"B1","G1","ORG1",node,"银行账户",button,"按钮",operation,null,
            Collections.singletonList(root),OperationContext.OWNER_POLICY_PROTOCOL);
    }

    private static String requestId() {
        StringBuilder id=new StringBuilder("R");
        for(int index=0;index<31;index++) id.append('0');
        return id.toString();
    }

    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
