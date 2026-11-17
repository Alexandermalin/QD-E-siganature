package nc.vo.uapbd.qd.esign;

import java.io.Serializable;

/** 所选业务对象的稳定引用。尚未保存的对象也以客户端标识关联；持久化主键可在业务成功后回填，父对象使用客户端标识关联。 */
public final class BusinessObjectRef implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;
    /**
     * clientObjectId：客户端生成的稳定对象标识，新对象未保存时仍可关联。
     * objectType：受控业务对象类型。
     * objectPk：已持久化业务对象主键，新增前可为空。
     * billCode：单据或档案编码。
     * parentClientObjectId：父对象的客户端稳定标识，根对象为空。
     */
    private final String clientObjectId, objectType, objectPk, billCode, parentClientObjectId, desiredOwnerOrg, policyOwnerOrg, targetOrg;
    /** 建立稳定对象引用，要求客户端标识和对象类型存在；尚未保存的主键、编码及根对象父标识可为空。 */
    public BusinessObjectRef(String clientObjectId, String objectType, String objectPk,
            String billCode, String parentClientObjectId) {
        this(clientObjectId, objectType, objectPk, billCode, parentClientObjectId, null);
    }

    /** 新增或变更所属组织的候选值仅用于服务端核验，不作为已授权的归属。 */
    public BusinessObjectRef(String clientObjectId, String objectType, String objectPk,
            String billCode, String parentClientObjectId, String desiredOwnerOrg) {
        this(clientObjectId, objectType, objectPk, billCode, parentClientObjectId, desiredOwnerOrg, null);
    }
    public BusinessObjectRef(String clientObjectId, String objectType, String objectPk,
            String billCode, String parentClientObjectId, String desiredOwnerOrg, String policyOwnerOrg) {
        this(clientObjectId, objectType, objectPk, billCode, parentClientObjectId, desiredOwnerOrg, policyOwnerOrg, null);
    }

    /** 分配候选目标仅表示请求范围；服务端仍须核验组织、权限及实际提交结果。 */
    public BusinessObjectRef(String clientObjectId, String objectType, String objectPk,
            String billCode, String parentClientObjectId, String desiredOwnerOrg, String policyOwnerOrg, String targetOrg) {
        if (clientObjectId == null || clientObjectId.length() == 0 || objectType == null) {
            throw new IllegalArgumentException("Object identity and type are required");
        }
        this.clientObjectId = clientObjectId; this.objectType = objectType;
        this.objectPk = objectPk; this.billCode = billCode; this.parentClientObjectId = parentClientObjectId;
        this.desiredOwnerOrg = desiredOwnerOrg;
        this.policyOwnerOrg = policyOwnerOrg;
        this.targetOrg = targetOrg;
    }
    /** 返回客户端生成的稳定对象标识，新对象未保存时仍可关联。 */
    public String getClientObjectId() { return clientObjectId; }
    /** 返回受控业务对象类型。 */
    public String getObjectType() { return objectType; }
    /** 返回已持久化业务对象主键，新增前可为空。 */
    public String getObjectPk() { return objectPk; }
    /** 返回单据或档案编码。 */
    public String getBillCode() { return billCode; }
    /** 返回父对象的客户端稳定标识，根对象为空。 */
    public String getParentClientObjectId() { return parentClientObjectId; }
    public String getDesiredOwnerOrg() { return desiredOwnerOrg; }
    /** 执行阶段由服务端保存的授权对象还原；客户端提交的值不参与归属决策。 */
    public String getPolicyOwnerOrg() { return policyOwnerOrg; }
    public String getTargetOrg() { return targetOrg; }
}
