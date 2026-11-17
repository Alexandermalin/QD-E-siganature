package nc.itf.uapbd.qd.esign;
import java.io.Serializable;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationResponse;

/** 仅服务端本地使用的 CMT 事务边界，分别提交一次性执行认领、业务执行和失败结果；禁止注册为远程接口。 */
public interface IOperationTransactionService {
    /** 锁定并认领可执行且未过期的请求，保存一次性执行令牌并转为 RUNNING；已成功请求返回空令牌供结果重入。 */
    String claim_RequiresNew(String requestId) throws BusinessException;
    /** 校验已提交的执行认领，在当前 CMT 内重新授权、委托适配器并写入审计与成功结果；失败向外传播以回滚该次业务事务。 */
    OperationResponse executeClaimed_RequiresNew(String requestId,String executionToken,Serializable command) throws BusinessException;
    /** Re-read durable request, objects and required audit after the business CMT has returned. */
    void verifyCommitted_RequiresNew(String requestId,String executionToken,OperationResponse attempted) throws BusinessException;
    /** 核对内部执行令牌，单独登记仍在 RUNNING 的失败或待核对结果。 */
    void failClaimed_RequiresNew(String requestId,String executionToken,String errorCode) throws BusinessException;
}
