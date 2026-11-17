package nc.bs.uapbd.qd.esign.core;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.Transaction;
import nc.bs.framework.common.RuntimeEnv;
import nc.vo.pub.BusinessException;
import uap.mw.trans.util.TransUtil;
import uap.mw.trans.UAPTransaction;

/** 使用 NC65 TXAdviceHolder 的 JNDI 路径检查外层调用和实际业务事务。 */
public final class NativeTransactionBoundaryProbe implements TransactionBoundaryProbe {
    /** 要求当前线程不存在外层活动事务，防止一次性认领与后续业务意外合并为同一事务。 */
    public void assertNoTransaction() throws BusinessException {
        requireNoTransaction(transactionRegistry());
    }

    public static TransactionSynchronizationRegistry requireActiveTransaction() throws BusinessException {
        TransactionSynchronizationRegistry registry = transactionRegistry();
        EsignValues.require(registry.getTransactionKey() != null && registry.getTransactionStatus() == Status.STATUS_ACTIVE,
            "受控业务没有活动的 NC 事务。");
        return registry;
    }

    /** NC's local manager commits each data source separately; only one enlisted connection is currently supported. */
    public static void requireSingleDataSource(TransactionSynchronizationRegistry registry,Object key) throws BusinessException {
        try {
            Transaction value=TransUtil.getCurTransaction();
            EsignValues.require(value instanceof UAPTransaction && registry!=null && key!=null
                && key.equals(registry.getTransactionKey()) && key.equals(((UAPTransaction)value).getKey())
                && registry.getTransactionStatus()==Status.STATUS_ACTIVE && !registry.getRollbackOnly()
                && value.getStatus()==Status.STATUS_ACTIVE && !((UAPTransaction)value).getRollbackOnly(),
                "无法核实受控业务的原生事务，请按请求号核对。");
            requireSingleConnectionMap(((UAPTransaction)value).getConnectionMap());
        } catch(SystemException error) { throw new BusinessException("无法核实业务事务的数据源范围，请按请求号核对。",error); }
        catch(LinkageError error) { throw new BusinessException("当前 NC 事务实现不支持数据源核对，请按请求号核对。",error); }
    }

    static void requireSingleConnectionMap(java.util.Map<?,?> connections) throws BusinessException {
        EsignValues.require(connections!=null && connections.size()==1 && !connections.containsKey(null)
            && !connections.containsValue(null),"受控业务涉及多个或未知数据源，不能确认原子提交。");
        Object name=connections.keySet().iterator().next();
        EsignValues.require(name instanceof String && !((String)name).trim().isEmpty(),"受控业务的数据源标识不可用。");
    }

    private static TransactionSynchronizationRegistry transactionRegistry() throws BusinessException {
        try {
            InitialContext context = new InitialContext();
            try {
                String name = (RuntimeEnv.isRunningInWebSphere() || RuntimeEnv.isRunningInWeblogic())
                    ? "java:comp/TransactionSynchronizationRegistry" : "java:comp/env/TransactionSynchronizationRegistry";
                Object registry = context.lookup(name);
                EsignValues.require(registry instanceof TransactionSynchronizationRegistry, "NC transaction registry is unavailable");
                return (TransactionSynchronizationRegistry)registry;
            } finally {
                context.close();
            }
        } catch (NamingException error) {
            throw new BusinessException("Unable to verify the NC transaction boundary", error);
        }
    }

    static void requireNoTransaction(TransactionSynchronizationRegistry registry) throws BusinessException {
        boolean noTransaction;
        if ("uap.mw.trans.syn.UAPTransSynchronizationRegistry".equals(registry.getClass().getName())) {
            // NC65 的该实现无事务时也返回 STATUS_ACTIVE，且会吞掉 SystemException。
            try {
                noTransaction = TransUtil.getCurTransaction() == null;
            } catch (SystemException error) {
                throw new BusinessException("Unable to verify the NC transaction boundary; operation was not started", error);
            }
        } else {
            noTransaction = registry.getTransactionStatus() == Status.STATUS_NO_TRANSACTION;
        }
        EsignValues.require(noTransaction, "A controlled operation must enter through a new nontransactional client RPC");
    }
}
