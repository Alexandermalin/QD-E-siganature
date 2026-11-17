package nc.impl.uapbd.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationResponse;
import uap.mw.trans.UAPTransaction;
import uap.mw.trans.UAPTransactionManagerProxy;
import uap.mw.trans.itf.IUAPTransactionManager;
import uap.mw.trans.syn.UAPTransSynchronizationRegistry;

/** Verify the inventory gateway refuses an unprovable multi-source commit. */
public final class InventoryAtomicCommitTests {
    private InventoryAtomicCommitTests() { }

    public static void run() throws Exception {
        ThreadLocal<IUAPTransactionManager> managers=UAPTransactionManagerProxy.getTransactionManagerContainer();
        IUAPTransactionManager previous=managers.get();
        try {
            TransactionSynchronizationRegistry registry=UAPTransSynchronizationRegistry.getInstance();
            UAPTransaction single=transaction(managers,1);
            final int[] called={0};
            OperationTransactionServiceImpl.executeGuarded(registry,single.getKey(),false,true,
                new OperationTransactionServiceImpl.GuardedOperation() {
                    public OperationResponse execute() { called[0]++; return null; }
                });
            require(called[0]==1 && !single.getRollbackOnly(),"Single-source inventory transaction was rejected");
            for (int sources : new int[]{0,2}) {
                UAPTransaction invalid=transaction(managers,sources);
                try {
                    OperationTransactionServiceImpl.executeGuarded(registry,invalid.getKey(),false,true,
                        new OperationTransactionServiceImpl.GuardedOperation() {
                            public OperationResponse execute() { return null; }
                        });
                    throw new AssertionError("Inventory transaction with " + sources + " sources was accepted");
                } catch (BusinessException expected) {
                    require(TransactionRollback.isMarked(expected) && invalid.getRollbackOnly(),
                        "An unprovable inventory commit must mark the original transaction for rollback");
                }
            }
            UAPTransaction auditFailure=transaction(managers,1);
            final int[] businessCalls={0};
            try {
                OperationTransactionServiceImpl.executeGuarded(registry,auditFailure.getKey(),false,true,
                    new OperationTransactionServiceImpl.GuardedOperation() {
                        public OperationResponse execute() throws BusinessException {
                            businessCalls[0]++;throw new BusinessException("Injected WMS audit failure after native work");
                        }
                    });
                throw new AssertionError("WMS audit failure was accepted");
            } catch (BusinessException expected) {
                require(businessCalls[0]==1 && TransactionRollback.isMarked(expected) && auditFailure.getRollbackOnly(),
                    "Audit failure after native work must mark the original inventory transaction for rollback");
            }
            UAPTransaction owner=transaction(managers,2);
            try {
                OperationTransactionServiceImpl.executeGuarded(registry,owner.getKey(),true,false,
                    new OperationTransactionServiceImpl.GuardedOperation() {
                        public OperationResponse execute() { return null; }
                    });
                throw new AssertionError("Multi-source owner transaction was accepted");
            } catch (BusinessException expected) {
                require(TransactionRollback.isMarked(expected) && owner.getRollbackOnly(),"Owner guard regressed");
            }
            physicalRollbackCases(managers);
        } finally {
            if(previous==null)managers.remove();else managers.set(previous);
        }
        System.out.println("PASS InventoryAtomicCommitTests (local NC transactions, JDBC doubles; no NC/Oracle session)");
    }

    /** Exercise the actual local NC rollback manager against a controlled JDBC connection. */
    private static void physicalRollbackCases(ThreadLocal<IUAPTransactionManager> managers) throws Exception {
        TransactionSynchronizationRegistry registry=UAPTransSynchronizationRegistry.getInstance();
        final JdbcRollback success=new JdbcRollback(false);
        UAPTransaction tx=transaction(managers,0);
        uap.mw.ds.QdRollbackTestConnection connection=new uap.mw.ds.QdRollbackTestConnection(success.connection());
        tx.getConnectionMap().put("qd-test",connection);
        try {
            OperationTransactionServiceImpl.executeGuarded(registry,tx.getKey(),false,true,
                new OperationTransactionServiceImpl.GuardedOperation() {
                    public OperationResponse execute() throws BusinessException {
                        success.pendingWrites=true;
                        throw new BusinessException("Injected audit failure after native work");
                    }
                });
            throw new AssertionError("The injected audit failure was accepted");
        } catch(BusinessException expected) {
            require(tx.getRollbackOnly() && success.pendingWrites,"The real transaction must first be marked rollback-only");
        }
        tx.rollback();
        require(success.rollbackCalls==1 && !success.pendingWrites && connection.returnedCount()==1
                && tx.getStatus()==javax.transaction.Status.STATUS_ROLLEDBACK,
                "Successful NC rollback must call the physical JDBC rollback and return its connection");

        JdbcRollback failure=new JdbcRollback(true);failure.pendingWrites=true;
        UAPTransaction failed=transaction(managers,0);
        uap.mw.ds.QdRollbackTestConnection failedConnection=new uap.mw.ds.QdRollbackTestConnection(failure.connection());
        failed.getConnectionMap().put("qd-test",failedConnection);
        try {failed.rollback();throw new AssertionError("A physical JDBC rollback failure was hidden");}
        catch(javax.transaction.SystemException expected) {
            require(failure.rollbackCalls==1 && failure.pendingWrites && failedConnection.returnedCount()==1
                    && failed.getStatus()==javax.transaction.Status.STATUS_ROLLEDBACK,
                    "A rolled-back status is not proof that physical rollback succeeded");
        }

        Class<?> type=Class.forName("uap.mw.trans.UAPTransactionManager");
        java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
        java.lang.reflect.Field stack=type.getDeclaredField("tranStack");stack.setAccessible(true);
        for(boolean unchecked:new boolean[]{false,true}) {
            JdbcRollback swallowed=new JdbcRollback(true);swallowed.pendingWrites=true;swallowed.unchecked=unchecked;
            UAPTransaction failedInManager=new UAPTransaction();
            failedInManager.getConnectionMap().put("qd-test",new uap.mw.ds.QdRollbackTestConnection(swallowed.connection()));
            IUAPTransactionManager manager=(IUAPTransactionManager)constructor.newInstance();
            @SuppressWarnings("unchecked")
            java.util.LinkedList<uap.mw.trans.UAPTransactionContext> contexts=
                    (java.util.LinkedList<uap.mw.trans.UAPTransactionContext>)stack.get(manager);
            contexts.push(new uap.mw.trans.UAPTransactionContext(failedInManager));managers.set(manager);
            manager.rollbackAll();
            int status=unchecked?javax.transaction.Status.STATUS_ROLLING_BACK:javax.transaction.Status.STATUS_ROLLEDBACK;
            require(swallowed.rollbackCalls==1 && swallowed.pendingWrites && failedInManager.getStatus()==status,
                    "The local manager can return after logging a failed physical rollback");
        }
    }

    private static final class JdbcRollback implements InvocationHandler {
        private final boolean fail;
        boolean pendingWrites,unchecked;int rollbackCalls;
        JdbcRollback(boolean fail){this.fail=fail;}
        java.sql.Connection connection() {
            return (java.sql.Connection)Proxy.newProxyInstance(java.sql.Connection.class.getClassLoader(),
                    new Class<?>[]{java.sql.Connection.class},this);
        }
        public Object invoke(Object proxy,Method method,Object[] args) throws Throwable {
            if("rollback".equals(method.getName()) && (args==null || args.length==0)) {
                rollbackCalls++;
                if(fail){if(unchecked)throw new IllegalStateException("Injected rollback runtime failure");
                    throw new java.sql.SQLException("Injected physical rollback failure");}
                pendingWrites=false;return null;
            }
            if("toString".equals(method.getName()))return "QD rollback test connection";
            throw new UnsupportedOperationException(method.getName());
        }
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static UAPTransaction transaction(ThreadLocal<IUAPTransactionManager> managers,int sources) {
        final UAPTransaction current=new UAPTransaction();
        java.util.Map connections=current.getConnectionMap();
        for(int index=0;index<sources;index++)connections.put("source"+index,new Object());
        managers.set((IUAPTransactionManager)Proxy.newProxyInstance(IUAPTransactionManager.class.getClassLoader(),
            new Class<?>[]{IUAPTransactionManager.class},new InvocationHandler() {
                public Object invoke(Object proxy,Method method,Object[] args) {
                    if("getTransaction".equals(method.getName()))return current;
                    throw new UnsupportedOperationException(method.getName());
                }
            }));
        return current;
    }

    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
