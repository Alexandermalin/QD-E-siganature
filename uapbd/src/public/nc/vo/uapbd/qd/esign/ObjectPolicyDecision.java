package nc.vo.uapbd.qd.esign;

import java.io.Serializable;

/** 由服务端核实的单个基础档案策略；操作组织仍保留在 OperationContext 中。 */
public final class ObjectPolicyDecision implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String clientObjectId, ownerOrg, ownerType, scopeId, ownerRoot, targetOrg;
    private final PolicyDecision policy;

    public ObjectPolicyDecision(String clientObjectId, String ownerOrg, String ownerType, String scopeId,
                                String ownerRoot, String targetOrg, PolicyDecision policy) {
        if (clientObjectId == null || ownerOrg == null || ownerType == null || scopeId == null || policy == null) {
            throw new IllegalArgumentException("Object policy identity is required");
        }
        this.clientObjectId = clientObjectId; this.ownerOrg = ownerOrg; this.ownerType = ownerType;
        this.scopeId = scopeId; this.ownerRoot = ownerRoot; this.targetOrg = targetOrg; this.policy = policy;
    }
    public String getClientObjectId() { return clientObjectId; }
    public String getOwnerOrg() { return ownerOrg; }
    public String getOwnerType() { return ownerType; }
    public String getScopeId() { return scopeId; }
    public String getOwnerRoot() { return ownerRoot; }
    public String getTargetOrg() { return targetOrg; }
    public PolicyDecision getPolicy() { return policy; }
}
