package nc.ui.uapbd.qd.esign;

import nc.vo.uapbd.qd.esign.SignatureResult;

/** 仅在客户端使用的确认窗口结果，保存签名响应、事由及异常，不包含凭据，也不能作为业务成功证据。 */
public final class SignatureDialogOutcome {
    /** result：本次签名窗口返回的认证结果。 */
    private final SignatureResult result;
    /** reason：操作事由文本。 */
    private final String reason;
    /** failure：最近一次失败原因，保留原始异常。 */
    private final Throwable failure;

    /** 封装窗口结果、最终事由和失败原因，不保存密码或业务执行证明。 */
    public SignatureDialogOutcome(SignatureResult result, String reason, Throwable failure) {
        this.result = result;
        this.reason = reason;
        this.failure = failure;
    }

    /** 返回本次认证 RPC 的结果。 */
    public SignatureResult getSignatureResult() { return result; }
    /** 返回操作事由文本。 */
    public String getReason() { return reason; }
    /** 返回最近一次失败原因，保留原始异常。 */
    public Throwable getFailure() { return failure; }
    /** 只有认证结果明确成功、业务状态为 READY 且没有窗口失败时才返回真，仍不代表业务已经执行。 */
    public boolean isConfirmed() {
        return result != null && result.isReadyForExecution() && failure == null;
    }
}
