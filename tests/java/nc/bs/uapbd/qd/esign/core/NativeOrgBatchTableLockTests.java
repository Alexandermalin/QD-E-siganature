package nc.bs.uapbd.qd.esign.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.uapbd.qd.esign.audit.NativeDeleteMetadataGraph;
import nc.vo.pub.BusinessException;

/** In-memory transaction and metadata doubles; this does not exercise Oracle locking. */
public final class NativeOrgBatchTableLockTests {
    private NativeOrgBatchTableLockTests() { }
    static final class Fixture implements InvocationHandler {
        Object key=new Object();int status=Status.STATUS_ACTIVE;boolean rollback;
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
        NativeOrgBatchTableLock.Ticket ticket() throws BusinessException {
            return NativeOrgBatchTableLock.acquire(NativeOrgBatchPage.CUSTOMER_FINANCE,registry,new NativeOrgBatchTableLock.Metadata() {
                public NativeDeleteMetadataGraph.Capture read() throws BusinessException { return NativeOrgBatchSnapshotTests.metadataForTest(true); }
            },new NativeOrgBatchTableLock.Executor() {
                public void lock(String table) { locked.add(table); }
            });
        }
    }
    public static void run() throws Exception {
        final Fixture f=new Fixture();final NativeOrgBatchTableLock.Ticket ticket=f.ticket();
        require(f.locked.equals(Arrays.asList("bd_custfinance","bd_custfinance_ext")),
            "The page and empty extension tables were not locked in stable order");
        ticket.require(NativeOrgBatchPage.CUSTOMER_FINANCE);
        reject(ticket,NativeOrgBatchPage.CUSTOMER_SALE,"A ticket for another page was accepted");
        Object original=f.key;f.key=new Object();reject(ticket,NativeOrgBatchPage.CUSTOMER_FINANCE,"A new transaction reused old locks");
        f.key=original;f.status=Status.STATUS_COMMITTED;reject(ticket,NativeOrgBatchPage.CUSTOMER_FINANCE,"A committed transaction reused old locks");
        f.status=Status.STATUS_ACTIVE;f.rollback=true;reject(ticket,NativeOrgBatchPage.CUSTOMER_FINANCE,"A rollback-only transaction was accepted");
        f.rollback=false;final boolean[] denied={false};
        Thread other=new Thread(new Runnable() { public void run() {
            try { ticket.require(NativeOrgBatchPage.CUSTOMER_FINANCE); } catch(BusinessException expected) { denied[0]=true; }
        }});
        other.start();other.join();require(denied[0],"Another thread reused the lock ticket");

        final Fixture changed=new Fixture();
        try { NativeOrgBatchTableLock.acquire(NativeOrgBatchPage.CUSTOMER_FINANCE,changed.registry,new NativeOrgBatchTableLock.Metadata() {
                int reads;
                public NativeDeleteMetadataGraph.Capture read() throws BusinessException {
                    return NativeOrgBatchSnapshotTests.metadataForTest(reads++==0);
                }
            },new NativeOrgBatchTableLock.Executor() { public void lock(String table) { changed.locked.add(table); } });
            throw new AssertionError("Changed metadata was accepted after locking");
        } catch(BusinessException expected) { require(changed.rollback,"Changed metadata did not mark the transaction for rollback"); }

        final Fixture failure=new Fixture();
        try { NativeOrgBatchTableLock.acquire(NativeOrgBatchPage.CUSTOMER_FINANCE,failure.registry,new NativeOrgBatchTableLock.Metadata() {
                public NativeDeleteMetadataGraph.Capture read() throws BusinessException { return NativeOrgBatchSnapshotTests.metadataForTest(true); }
            },new NativeOrgBatchTableLock.Executor() { public void lock(String table) throws BusinessException {
                failure.locked.add(table);if(failure.locked.size()==2) throw new BusinessException("lock failed");
            }});throw new AssertionError("A failed table lock was accepted");
        } catch(BusinessException expected) { require(failure.rollback,"A partial lock failure did not mark rollback"); }

        final Fixture switched=new Fixture();
        try { NativeOrgBatchTableLock.acquire(NativeOrgBatchPage.CUSTOMER_FINANCE,switched.registry,new NativeOrgBatchTableLock.Metadata() {
                public NativeDeleteMetadataGraph.Capture read() throws BusinessException { return NativeOrgBatchSnapshotTests.metadataForTest(true); }
            },new NativeOrgBatchTableLock.Executor() { public void lock(String table) { switched.key=new Object(); } });
            throw new AssertionError("A changed transaction during locking was accepted");
        } catch(BusinessException expected) { require(!switched.rollback,"A different transaction was marked for rollback"); }
        System.out.println("PASS NativeOrgBatchTableLockTests (ordered metadata scope and transaction identity; no Oracle)");
    }
    private static void reject(NativeOrgBatchTableLock.Ticket ticket,NativeOrgBatchPage page,String message) throws Exception {
        try { ticket.require(page);throw new AssertionError(message); } catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
