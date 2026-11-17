package nc.vo.uapbd.qd.esign;
import java.io.Serializable;

/** 按组织解析的策略，包含配置值、生效值及两个独立版本。 */
public final class PolicyDecision implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID=1L;
    /**
     * masterEnabled：业务单元总控开关的当前值。
     * signatureRequired：结合总控后是否需要签名。
     * reasonRequired：结合总控和签名配置后是否必须填写事由。
     * auditEnabled：结合总控后是否启用业务审计。
     * validated：节点适配是否已标记为验证通过。
     */
    private final boolean masterEnabled, signatureRequired, reasonRequired, auditEnabled, validated;
    /** version：节点策略版本，用于乐观并发或授权有效性检查。 */
    private final long version;
    /**
     * configuredSignature：管理员配置的原始签名开关。
     * configuredReason：管理员配置的原始事由必填开关。
     * configuredAudit：管理员配置的原始审计开关。
     */
    private final boolean configuredSignature,configuredReason,configuredAudit;
    /** controlVersion：业务单元总控版本，用于使开关变动前的旧授权失效。 */
    private final long controlVersion;
    /** 保存原始开关，按组织总控计算生效值。兼容构造方式的总控版本默认为零。 */
    public PolicyDecision(boolean master,boolean sign,boolean reason,boolean audit,boolean validated,long version){
        this(master,sign,reason,audit,validated,version,0);
    }
    /** 按组织总控计算生效值，并记录按钮策略和总控版本。业务单据沿用事由从属于签名的规则。 */
    public PolicyDecision(boolean master,boolean sign,boolean reason,boolean audit,boolean validated,long version,long controlVersion){
        this(master,sign,reason,audit,validated,version,controlVersion,false);
    }
    /** 基础档案可以独立要求事由，不要求操作人同时进行电子签名。 */
    public PolicyDecision(boolean master,boolean sign,boolean reason,boolean audit,boolean validated,long version,long controlVersion,boolean independentReason){
        this.masterEnabled=master;this.signatureRequired=master&&sign;this.reasonRequired=master&&reason&&(independentReason||sign);
        this.auditEnabled=master&&audit;this.validated=validated;this.version=version;
        this.configuredSignature=sign;this.configuredReason=reason;this.configuredAudit=audit;
        this.controlVersion=controlVersion;
    }
    /** 返回业务单元总控开关的当前值。 */
    public boolean isMasterEnabled(){return masterEnabled;}
    /** 返回结合总控后是否需要签名。 */
    public boolean isSignatureRequired(){return signatureRequired;}
    /** 返回结合总控和签名配置后是否必须填写事由。 */
    public boolean isReasonRequired(){return reasonRequired;}
    /** 返回结合总控后是否启用业务审计。 */
    public boolean isAuditEnabled(){return auditEnabled;}
    /** 返回节点适配是否已标记为验证通过。 */
    public boolean isValidated(){return validated;}
    /** 返回节点策略版本，用于乐观并发或授权有效性检查。 */
    public long getVersion(){return version;}
    /** 任一有效的签名、事由或审计要求均构成业务控制。 */
    public boolean isControlled(){return signatureRequired||reasonRequired||auditEnabled;}
    /** 返回管理员配置的原始签名开关。 */
    public boolean getConfiguredSignature(){return configuredSignature;}
    /** 返回管理员配置的原始事由必填开关。 */
    public boolean getConfiguredReason(){return configuredReason;}
    /** 返回管理员配置的原始审计开关。 */
    public boolean getConfiguredAudit(){return configuredAudit;}
    /** 返回业务单元总控版本，用于使开关变动前的旧授权失效。 */
    public long getControlVersion(){return controlVersion;}
}
