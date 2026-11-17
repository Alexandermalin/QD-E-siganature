package nc.vo.uapbd.qd.esign;

import java.io.Serializable;

/** 一个正式清单条目在所查业务单元的策略。 */
public final class ButtonPolicyRow implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String scopeId;
    private final String nodeCode;
    private final String nodeName;
    private final String buttonCode;
    private final String buttonName;
    private final String mappingState;
    private final boolean configurable;
    private final boolean signatureEnabled;
    private final boolean reasonRequired;
    private final boolean auditEnabled;
    private final long version;

    public ButtonPolicyRow(String scopeId, String nodeCode, String nodeName, String buttonCode,
                           String buttonName, String mappingState, boolean configurable,
                           boolean signatureEnabled, boolean reasonRequired, boolean auditEnabled, long version) {
        this.scopeId = scopeId;
        this.nodeCode = nodeCode;
        this.nodeName = nodeName;
        this.buttonCode = buttonCode;
        this.buttonName = buttonName;
        this.mappingState = mappingState;
        this.configurable = configurable;
        this.signatureEnabled = signatureEnabled;
        this.reasonRequired = reasonRequired;
        this.auditEnabled = auditEnabled;
        this.version = version;
    }

    public String getScopeId() { return scopeId; }
    public String getNodeCode() { return nodeCode; }
    public String getNodeName() { return nodeName; }
    public String getButtonCode() { return buttonCode; }
    public String getButtonName() { return buttonName; }
    public String getMappingState() { return mappingState; }
    public boolean isConfigurable() { return configurable; }
    public boolean isSignatureEnabled() { return signatureEnabled; }
    public boolean isReasonRequired() { return reasonRequired; }
    public boolean isAuditEnabled() { return auditEnabled; }
    public long getVersion() { return version; }
}
