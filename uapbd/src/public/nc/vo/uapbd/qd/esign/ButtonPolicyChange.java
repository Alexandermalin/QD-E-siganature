package nc.vo.uapbd.qd.esign;

import java.io.Serializable;

/** 保存按钮策略时提交的清单标识、勾选值及查询版本。 */
public final class ButtonPolicyChange implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String scopeId;
    private final boolean signatureEnabled;
    private final boolean reasonRequired;
    private final boolean auditEnabled;
    private final long expectedVersion;

    public ButtonPolicyChange(String scopeId, boolean signatureEnabled, boolean reasonRequired,
                              boolean auditEnabled, long expectedVersion) {
        this.scopeId = scopeId;
        this.signatureEnabled = signatureEnabled;
        this.reasonRequired = reasonRequired;
        this.auditEnabled = auditEnabled;
        this.expectedVersion = expectedVersion;
    }

    public String getScopeId() { return scopeId; }
    public boolean isSignatureEnabled() { return signatureEnabled; }
    public boolean isReasonRequired() { return reasonRequired; }
    public boolean isAuditEnabled() { return auditEnabled; }
    public long getExpectedVersion() { return expectedVersion; }
}
