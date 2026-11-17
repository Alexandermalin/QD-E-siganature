package nc.vo.uapbd.qd.esign;
import java.io.Serializable;
/** 一次签名确认的结果，分别保存认证状态与业务状态；认证成功并不表示业务已经执行成功。 */
public final class SignatureResult implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID=1L;
    /**
     * requestId：贯通准备、签名、执行及日志关联的请求标识。
     * status：本次认证的明确结果码，与业务状态分别保存。
     */
    private final String requestId,status;
    /** locked：是否因认证失败触发或发现账号锁定。 */
    private final boolean locked;
    /** businessState：与签名结果分别维护的业务执行状态。 */
    private final String businessState;
    /** remainingAttempts：本次签名请求剩余密码尝试次数，未知或不适用时为空。 */
    private final Integer remainingAttempts;
    /** 保存认证状态、锁定标记及可选业务状态；兼容的三参数形式不提供业务状态。 */
    public SignatureResult(String requestId,String status,boolean locked){this(requestId,status,locked,null);}
    /** 保存认证状态、锁定标记及可选业务状态；兼容的三参数形式不提供业务状态。 */
    public SignatureResult(String requestId,String status,boolean locked,String businessState){this(requestId,status,locked,businessState,null);}
    /** 保存服务端计算的剩余次数；原有构造方法继续返回未知次数。 */
    public SignatureResult(String requestId,String status,boolean locked,String businessState,Integer remainingAttempts){
        this.requestId=requestId;this.status=status;this.locked=locked;this.businessState=businessState;this.remainingAttempts=remainingAttempts;
    }
    /** 返回贯通准备、签名、执行及日志关联的请求标识。 */
    public String getRequestId(){return requestId;}
    /** 返回本次认证的明确结果码，与业务状态分别保存。 */
    public String getStatus(){return status;}
    /** 返回是否因认证失败触发或发现账号锁定。 */
    public boolean isLocked(){return locked;}
    /** 仅在状态明确为 SUCCESS 时返回真，未初始化或未知状态不能视为认证成功。 */
    public boolean isSuccess(){return "SUCCESS".equals(status);}
    /** 不需签名的事由确认也可准备执行业务，但不能被描述为签名成功。 */
    public boolean isReadyForExecution(){return ("SUCCESS".equals(status)||"NOT_REQUIRED".equals(status))&&"READY".equals(businessState);}
    /** 返回与签名结果分别维护的业务执行状态。 */
    public String getBusinessState(){return businessState;}
    /** 返回本次签名请求剩余的密码尝试次数。 */
    public Integer getRemainingAttempts(){return remainingAttempts;}
}
