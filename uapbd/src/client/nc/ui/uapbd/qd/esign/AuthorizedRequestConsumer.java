package nc.ui.uapbd.qd.esign;

import nc.vo.uapbd.qd.esign.OperationContext;

/** 逐节点实现的授权交接接口，将请求绑定到最终受控业务 RPC。禁止通过静态映射交接；绑定方法本身不执行业务 RPC。 */
public interface AuthorizedRequestConsumer {
    /** 在请求准备前判断当前节点是否具备已实现的最终 RPC 交接路径。 */
    boolean isAvailable(OperationContext context);
    /** 将授权上下文绑定到最终受控业务调用，只有真实 RPC 会消费本请求时才返回真；本方法不执行 RPC。 */
    boolean bind(OperationContext authorizedContext) throws Exception;

    /** 核对当前请求是否确实交给最终受控业务 RPC，不能把仅完成绑定视为已经执行。 */
    boolean wasDispatched(String requestId);

    /** 尝试清除当前动作实例的请求绑定并保留异常提示，避免遗留授权被后续动作复用。 */
    void release(String requestId);
}
