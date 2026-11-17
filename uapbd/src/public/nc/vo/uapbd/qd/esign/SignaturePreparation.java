package nc.vo.uapbd.qd.esign;
import java.io.Serializable;
/** 签名前置准备结果，包含服务端确认的操作上下文、策略和当前请求状态，供客户端决定放行、确认或继续业务调用。 */
public final class SignaturePreparation implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID=1L;
    /** context：当前操作的业务上下文。 */
    private final OperationContext context;
    /** policy：服务端解析后的生效策略。 */
    private final PolicyDecision policy;
    /** state：请求准备阶段状态。 */
    private final String state;
    private final boolean executionGuard;
    /** 封装权威上下文、生效策略及准备状态，供客户端继续确认或交接。 */
    public SignaturePreparation(OperationContext context,PolicyDecision policy,String state){this(context,policy,state,false);}
    public SignaturePreparation(OperationContext context,PolicyDecision policy,String state,boolean executionGuard){this.context=context;this.policy=policy;this.state=state;this.executionGuard=executionGuard;}
    /** 返回当前操作的业务上下文。 */
    public OperationContext getContext(){return context;}
    /** 返回服务端解析后的生效策略。 */
    public PolicyDecision getPolicy(){return policy;}
    /** 返回请求准备阶段状态。 */
    public String getState(){return state;}
    public boolean requiresExecutionGuard(){return executionGuard;}
}
