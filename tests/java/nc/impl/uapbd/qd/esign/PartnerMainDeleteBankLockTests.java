package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nc.vo.pub.BusinessException;

/** All account graph tables must be locked consistently, and any lock failure aborts immediately. */
public final class PartnerMainDeleteBankLockTests {
    private PartnerMainDeleteBankLockTests() { }
    public static void run() throws Exception {
        final List<String> held=new ArrayList<String>();
        PartnerMainDeleteBankLock.acquire(new PartnerMainDeleteBankLock.Executor() {
            public void lock(String table) { held.add(table); }
        });
        require(held.equals(Arrays.asList("bd_bankaccbas","bd_bankaccsub","bd_bankaccuse","bd_custbank")),
            "Bank graph locks missing or acquired in a different order");
        held.clear();
        try { PartnerMainDeleteBankLock.acquire(new PartnerMainDeleteBankLock.Executor() {
                public void lock(String table) throws BusinessException {
                    held.add(table);if("bd_bankaccsub".equals(table)) throw new BusinessException("busy");
                }
            });throw new AssertionError("Lock failure was ignored"); }
        catch(BusinessException expected) {
            require(held.equals(Arrays.asList("bd_bankaccbas","bd_bankaccsub")),"Lock acquisition continued after contention");
        }
        System.out.println("PASS PartnerMainDeleteBankLockTests (fixed lock order and contention handling; no Oracle lock acquired)");
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
