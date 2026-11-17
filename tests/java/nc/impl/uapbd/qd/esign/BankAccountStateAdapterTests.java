package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFDateTime;
import nc.vo.bd.bankaccount.BankAccbasVO;
import nc.vo.uapbd.qd.esign.BankAccountStateCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Only identity/version metadata is accepted; mutable bank-account VO data is never supplied by the client. */
public final class BankAccountStateAdapterTests {
    private BankAccountStateAdapterTests() { }
    public static void run() throws Exception {
        BankAccountStateAdapter adapter=new BankAccountStateAdapter();
        BusinessObjectRef selected=ref("C","BD_CUSTOMER_BANK","R1",null,"A","B");
        BusinessObjectRef affected=ref("S","BD_SUPPLIER_BANK","R2","C","G","B");
        OperationContext context=context("10140CBA","Enable","ENABLE",Arrays.asList(selected,affected));
        adapter.validate(context,new BankAccountStateCommand("R1","BANK","2026-01-01 00:00:00"));
        reject(adapter,context,new BankAccountStateCommand("R2","BANK","timestamp"));
        reject(adapter,context,new BankAccountStateCommand("R1","BANK",null));
        reject(adapter,context,new BankAccountStateCommand("R1"," ","timestamp"));
        reject(adapter,context,"arbitrary mutable command");
        reject(adapter,context("10140CBA","Disable","ENABLE",Arrays.asList(selected,affected)),new BankAccountStateCommand("R1","BANK","t"));
        reject(adapter,context("10140SBA","Enable","ENABLE",Arrays.asList(selected,affected)),new BankAccountStateCommand("R1","BANK","t"));
        reject(adapter,context("10140CBA","Enable","ENABLE",Arrays.asList(selected,ref("X","BD_SUPPLIER","R3","C","G","B"))),
            new BankAccountStateCommand("R1","BANK","t"));
        reject(adapter,context("10140CBA","Enable","ENABLE",Arrays.asList(selected,ref("X","BD_SUPPLIER_BANK","R3","C","G","OTHER"))),
            new BankAccountStateCommand("R1","BANK","t"));
        reject(adapter,context("10140CBA","Enable","ENABLE",Collections.singletonList(ref("C","BD_CUSTOMER_BANK","R1",null,null,"B"))),
            new BankAccountStateCommand("R1","BANK","t"));
        BankAccbasVO bank=new BankAccbasVO();bank.setTs(new UFDateTime("2026-11-11 12:34:56"));bank.setCreator("CREATOR");
        bank.setModifier("MODIFIER");bank.setEnablestate(Integer.valueOf(3));String ts=bank.getTs().toString();
        BankAccountStateAdapter.verifyNativeState(bank,ts,"OPERATOR",true);
        rejectState(bank,ts,"CREATOR",true);rejectState(bank,ts,"MODIFIER",true);rejectState(bank,"stale","OPERATOR",true);
        rejectState(bank,ts,"OPERATOR",false);bank.setEnablestate(Integer.valueOf(2));
        BankAccountStateAdapter.verifyNativeState(bank,ts,"CREATOR",false);rejectState(bank,ts,"OPERATOR",true);
        System.out.println("PASS BankAccountStateAdapterTests (immutable selected identity and version; no NC or Oracle)");
    }
    private static void reject(BankAccountStateAdapter adapter,OperationContext context,java.io.Serializable command) throws Exception {
        try { adapter.validate(context,command);throw new AssertionError("Invalid bank state command accepted"); }
        catch(BusinessException expected) { }
    }
    private static void rejectState(BankAccbasVO value,String ts,String user,boolean enable) throws Exception {
        try { BankAccountStateAdapter.verifyNativeState(value,ts,user,enable);throw new AssertionError("Invalid native bank state accepted"); }
        catch(BusinessException expected) { }
    }
    private static BusinessObjectRef ref(String client,String domain,String pk,String parent,String owner,String target) {
        return new BusinessObjectRef(client,domain,pk,"CODE",parent,null,owner,target);
    }
    private static OperationContext context(String node,String button,String operation,List<BusinessObjectRef> refs) {
        return new OperationContext(null,null,"G","B",node,"银行账户",button,"动作",operation,null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
}
