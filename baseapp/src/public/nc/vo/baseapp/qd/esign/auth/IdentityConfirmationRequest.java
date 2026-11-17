package nc.vo.baseapp.qd.esign.auth;

import java.io.Serializable;
import java.util.Arrays;

/** 单次再确认所需凭据的传输对象，调用者身份取自 NC 会话；密码通过副本管理并由各持有方及时清零。 */
public final class IdentityConfirmationRequest implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;

    /** requestId：贯通准备、签名、执行及日志关联的请求标识。 */
    private String requestId;
    /** password：单次认证密码数组，使用后清零。 */
    private char[] password;

    /** 返回贯通准备、签名、执行及日志关联的请求标识。 */
    public String getRequestId() {
        return requestId;
    }

    /** 设置贯通准备、签名、执行及日志关联的请求标识。 */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /** 返回内部密码的防御性副本；调用方拥有该副本，使用后必须自行清零。 */
    public char[] getPassword() {
        return password == null ? null : password.clone();
    }

    /** 先清理旧密码再复制新数组，传入数组仍由调用方拥有并负责清零。 */
    public void setPassword(char[] password) {
        clearPassword();
        this.password = password == null ? null : password.clone();
    }

    /** 清零内部保存的密码数组并清除引用，避免认证凭据被对象长期保留。 */
    public void clearPassword() {
        if (password != null) {
            Arrays.fill(password, '\0');
            password = null;
        }
    }

    /** 输出不含密码、编码后凭据或其他认证秘密的诊断文本。 */
    @Override
    public String toString() {
        return "IdentityConfirmationRequest";
    }
}
