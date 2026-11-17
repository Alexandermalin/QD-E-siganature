package nc.impl.uapbd.qd.esign;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.core.TransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.uapbd.qd.esign.IOperationTransactionService;
import nc.vo.ic.qd.esign.SapplyPfBatchCommand;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.InventoryNativeCommand;

/** 以 NONE 事务属性运行的调度器，一次性认领先提交，再调用业务 CMT；失败结果另行记录，认领不可重置为可重用状态。 */
public final class OperationServiceImpl implements IOperationService {
    /** suppliedTransactions：显式注入的本地事务服务，未提供时通过 NCLocator 获取。 */
    private final IOperationTransactionService suppliedTransactions;
    /** boundary：用于拒绝外层既有事务的环境探针。 */
    private final TransactionBoundaryProbe boundary;
    /** 生产构造使用原生事务边界探针并延迟定位服务；测试构造要求显式提供非空事务服务和探针。 */
    public OperationServiceImpl(){suppliedTransactions=null;boundary=new NativeTransactionBoundaryProbe();}
    /** 生产构造使用原生事务边界探针并延迟定位服务；测试构造要求显式提供非空事务服务和探针。 */
    public OperationServiceImpl(IOperationTransactionService transactions,TransactionBoundaryProbe boundary){if(transactions==null||boundary==null)throw new IllegalArgumentException("transactions/boundary");suppliedTransactions=transactions;this.boundary=boundary;}
    /** 先拒绝外层既有事务，经本地 CMT 提交一次性认领后执行业务；业务异常原样抛出，失败结果尝试另行保存。 */
    public OperationResponse execute(String requestId,Serializable command) throws BusinessException {
        // 外层不能存在活动事务，否则认领可能随业务一起回滚，破坏一次性执行边界。
        boundary.assertNoTransaction();
        if (command instanceof SapplyPfBatchCommand)
            return SapplyBatchDispatch.execute(requestId, (SapplyPfBatchCommand) command, boundary);
        IOperationTransactionService transactions=suppliedTransactions==null?NCLocator.getInstance().lookup(IOperationTransactionService.class):suppliedTransactions;
        // claim CMT 先独立完成提交，再开始业务 CMT；离线测试只检查调用顺序。
        String token=transactions.claim_RequiresNew(requestId);
        boundary.assertNoTransaction();
        // The local transaction manager can log and swallow commit errors. Read durable evidence in a new CMT.
        try{
            OperationResponse attempted=transactions.executeClaimed_RequiresNew(requestId,token,command);
            boundary.assertNoTransaction();
            transactions.verifyCommitted_RequiresNew(requestId,token,attempted);
            boundary.assertNoTransaction();
            return attempted;
        }
        catch(BusinessException failure){
            recordFailure(transactions,requestId,token,failure);
            throw nativeBusinessFailure(command,failure);
        }
        catch(RuntimeException failure){recordFailure(transactions,requestId,token,failure);throw failure;}
    }
    /** A rollback marker alone cannot justify offering a native retry. */
    private static BusinessException nativeBusinessFailure(Serializable command,BusinessException failure) {
        if(command instanceof InventoryNativeCommand) {
            if(failure.getSuppressed().length>0) {
                return new BusinessException("库存业务失败结果未能完整记录，请按请求号核对后处理。",failure);
            }
        }
        return failure;
    }
    /** 业务结果无法确认时另行登记 UNKNOWN；记录失败保留原异常并阻断授权复用。 */
    private void recordFailure(IOperationTransactionService transactions,String id,String token,Throwable original){
        if(token==null)return;
        try{
            boundary.assertNoTransaction();
            transactions.failClaimed_RequiresNew(id,token,"BUSINESS_OUTCOME_UNKNOWN");
            boundary.assertNoTransaction();
        }
        catch(Exception persistenceFailure){
            if(original!=persistenceFailure)original.addSuppressed(persistenceFailure);
            // 保留已提交的 RUNNING 认领并阻断复用，不能重置为 READY；需要后续人工或运维核对。
            Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                "QD_ESIGN_FAILURE_RESULT_NOT_PERSISTED request="+id+"; reconciliation required",persistenceFailure);
        }
    }
}
