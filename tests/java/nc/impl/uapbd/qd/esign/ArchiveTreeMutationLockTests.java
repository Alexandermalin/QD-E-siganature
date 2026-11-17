package nc.impl.uapbd.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.vo.pub.BusinessException;

/** Fixed table scope and transaction checks with doubles; no Oracle lock is acquired. */
public final class ArchiveTreeMutationLockTests {
    private ArchiveTreeMutationLockTests() { }
    private static final class Fixture implements InvocationHandler,ArchiveTreeMutationLock.Executor {
        Object key=new Object();int status=Status.STATUS_ACTIVE;boolean rollback;int failAt;boolean switchTransaction;
        final List<String> locked=new ArrayList<String>();
        final TransactionSynchronizationRegistry registry=(TransactionSynchronizationRegistry)Proxy.newProxyInstance(
            TransactionSynchronizationRegistry.class.getClassLoader(),new Class<?>[]{TransactionSynchronizationRegistry.class},this);
        public Object invoke(Object proxy,Method method,Object[] args) {
            if("getTransactionKey".equals(method.getName())) return key;
            if("getTransactionStatus".equals(method.getName())) return Integer.valueOf(status);
            if("getRollbackOnly".equals(method.getName())) return Boolean.valueOf(rollback);
            if("setRollbackOnly".equals(method.getName())) { rollback=true;return null; }
            throw new UnsupportedOperationException(method.getName());
        }
        public void lock(String table) throws BusinessException {
            locked.add(table);
            if(switchTransaction) key=new Object();
            if(failAt==locked.size()) throw new BusinessException("simulated lock failure");
        }
        void acquire(String domain,boolean extensions) throws BusinessException {
            ArchiveTreeMutationLock.acquire(domain,extensions,registry,this);
        }
    }
    public static void run() throws Exception {
        Fixture material=new Fixture();material.acquire("BD_MARBASCLASS",true);
        require(material.locked.equals(Arrays.asList("bd_marbasclass","srmsm_marbasclsext","srmsm_marbasclsext_b")),
            "Material class and extension lock order changed");
        Fixture state=new Fixture();state.acquire("BD_MARBASCLASS",false);
        require(state.locked.equals(Arrays.asList("bd_marbasclass")),"State change locked unrelated extension tables");
        Fixture customer=new Fixture();customer.acquire("BD_CUSTCLASS",false);
        Fixture supplier=new Fixture();supplier.acquire("BD_SUPPLIERCLASS",false);
        Fixture rack=new Fixture();rack.acquire("BD_RACK",false);
        require(customer.locked.equals(Arrays.asList("bd_custclass")) && supplier.locked.equals(Arrays.asList("bd_supplierclass"))
            && rack.locked.equals(Arrays.asList("bd_rack")),"A tree domain used an incorrect physical table");
        Fixture invalid=new Fixture();reject(invalid,"BD_CUSTOMER",false);
        require(invalid.locked.isEmpty() && invalid.rollback,"Unknown domain did not fail before locking");
        invalid=new Fixture();reject(invalid,"BD_RACK",true);
        require(invalid.locked.isEmpty() && invalid.rollback,"Unsupported extension scope was accepted");
        Fixture failed=new Fixture();failed.failAt=2;reject(failed,"BD_MARBASCLASS",true);
        require(failed.locked.size()==2 && failed.rollback,"Partial table-lock failure did not mark rollback");
        Fixture changed=new Fixture();changed.switchTransaction=true;reject(changed,"BD_CUSTCLASS",false);
        require(!changed.rollback,"A different transaction was marked for rollback");
        Fixture ended=new Fixture();ended.status=Status.STATUS_COMMITTED;reject(ended,"BD_RACK",false);
        require(ended.locked.isEmpty(),"A finished transaction acquired a table lock");
        Fixture marked=new Fixture();marked.rollback=true;reject(marked,"BD_CUSTCLASS",false);
        require(marked.locked.isEmpty(),"A rollback-only transaction acquired a table lock");
        Fixture absent=new Fixture();absent.key=null;reject(absent,"BD_CUSTCLASS",false);
        require(absent.locked.isEmpty() && !absent.rollback,"A missing transaction was treated as an acquired lock");
        System.out.println("PASS ArchiveTreeMutationLockTests (fixed lock scope and transaction identity; no Oracle)");
    }
    private static void reject(Fixture fixture,String domain,boolean extensions) throws Exception {
        try { fixture.acquire(domain,extensions);throw new AssertionError("Unsafe tree lock accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
