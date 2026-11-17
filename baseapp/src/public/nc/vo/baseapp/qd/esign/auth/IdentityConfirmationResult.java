package nc.vo.baseapp.qd.esign.auth;

import java.io.Serializable;

/** 身份再确认结果，仅明确 SUCCESS 才表示认证成功；不证明 NC 业务操作成功，不包含认证凭据。 */
public final class IdentityConfirmationResult implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;

    /** SUCCESS：认证成功状态码。 */
    public static final String SUCCESS = "SUCCESS";
    /** PASSWORD_INCORRECT：密码错误状态码。 */
    public static final String PASSWORD_INCORRECT = "PASSWORD_INCORRECT";
    /** ACCOUNT_LOCKED：账号已锁定状态码。 */
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    /** ACCOUNT_UNAVAILABLE：账号不可用状态码。 */
    public static final String ACCOUNT_UNAVAILABLE = "ACCOUNT_UNAVAILABLE";

    /** requestId：贯通准备、签名、执行及日志关联的请求标识。 */
    private String requestId;
    /** userId：NC 用户主键。 */
    private String userId;
    /** userCode：NC 用户编码。 */
    private String userCode;
    /** userName：操作时记录的 NC 用户姓名。 */
    private String userName;
    /** groupId：NC 集团主键，服务端从当前身份核定。 */
    private String groupId;
    /** status：原生身份确认的明确状态，未赋值不能视为成功。 */
    private String status;
    /** platformResult：原生认证 API 返回的结果码。 */
    private int platformResult;
    /** failureCount：本次失败后的原生计数；锁定清零前的数量会保留，未知为 -1。 */
    private int failureCount = -1;
    /** locked：是否因认证失败触发或发现账号锁定。 */
    private boolean locked;
    /** confirmedAt：成功确认的服务端 epoch 毫秒时间，未成功时保持零。 */
    private long confirmedAt;

    /** 返回贯通准备、签名、执行及日志关联的请求标识。 */
    public String getRequestId() { return requestId; }
    /** 设置贯通准备、签名、执行及日志关联的请求标识。 */
    public void setRequestId(String requestId) { this.requestId = requestId; }
    /** 返回NC 用户主键。 */
    public String getUserId() { return userId; }
    /** 设置NC 用户主键。 */
    public void setUserId(String userId) { this.userId = userId; }
    /** 返回NC 用户编码。 */
    public String getUserCode() { return userCode; }
    /** 设置NC 用户编码。 */
    public void setUserCode(String userCode) { this.userCode = userCode; }
    /** 返回操作时记录的 NC 用户姓名。 */
    public String getUserName() { return userName; }
    /** 设置操作时记录的 NC 用户姓名。 */
    public void setUserName(String userName) { this.userName = userName; }
    /** 返回NC 集团主键，服务端从当前身份核定。 */
    public String getGroupId() { return groupId; }
    /** 设置NC 集团主键，服务端从当前身份核定。 */
    public void setGroupId(String groupId) { this.groupId = groupId; }
    /** 返回原生身份确认的明确状态，未赋值不能视为成功。 */
    public String getStatus() { return status; }
    /** 设置原生身份确认的明确状态，未赋值不能视为成功。 */
    public void setStatus(String status) { this.status = status; }
    /** 返回原生认证 API 返回的结果码。 */
    public int getPlatformResult() { return platformResult; }
    /** 设置原生认证 API 返回的结果码。 */
    public void setPlatformResult(int platformResult) { this.platformResult = platformResult; }

    /** 返回本次失败后的原生计数；锁定清零前的数量会保留，未知为 -1。 */
    public int getFailureCount() { return failureCount; }
    /** 设置本次失败后的原生计数；锁定清零前的数量会保留，未知为 -1。 */
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
    /** 返回是否因认证失败触发或发现账号锁定。 */
    public boolean isLocked() { return locked; }
    /** 设置是否因认证失败触发或发现账号锁定。 */
    public void setLocked(boolean locked) { this.locked = locked; }

    /** 返回成功确认的服务端 epoch 毫秒时间，未成功时保持零。 */
    public long getConfirmedAt() { return confirmedAt; }
    /** 设置成功确认的服务端 epoch 毫秒时间，未成功时保持零。 */
    public void setConfirmedAt(long confirmedAt) { this.confirmedAt = confirmedAt; }
    /** 仅在状态明确为 SUCCESS 时返回真，未初始化或未知状态不能视为认证成功。 */
    public boolean isSuccess() { return SUCCESS.equals(status); }
}
