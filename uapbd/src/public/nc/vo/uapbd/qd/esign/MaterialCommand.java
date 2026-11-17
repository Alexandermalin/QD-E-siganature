package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import nc.ui.bd.pub.extend.PrivateServiceContext;
import nc.vo.bd.material.MaterialVO;

/** 物料主档操作的类型化载荷，保留原生 MaterialVO；本对象只传业务数据，不代表已获得操作授权。 */
public final class MaterialCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final MaterialVO material;
    private final HashMap<String, Object> extensionValues;
    private final PrivateServiceContext extensionContext;

    public MaterialCommand(MaterialVO material) {
        this.material = material;
        this.extensionValues = null;
        this.extensionContext = null;
    }

    /** 保存页面实际收集的参数，扩展服务标识由服务端核对。 */
    public MaterialCommand(MaterialVO material, Map<String, Object> extensionValues,
            PrivateServiceContext extensionContext) {
        if (extensionValues == null || extensionContext == null) {
            throw new IllegalArgumentException("Material extension arguments are required");
        }
        this.material = material;
        this.extensionValues = new HashMap<String, Object>(extensionValues);
        this.extensionContext = extensionContext;
    }

    public MaterialVO getMaterial() { return material; }
    public Map<String, Object> getExtensionValues() { return extensionValues; }
    public PrivateServiceContext getExtensionContext() { return extensionContext; }
    public boolean hasExtensions() { return extensionValues != null || extensionContext != null; }

    public static boolean supportsSave(OperationContext context) {
        String node = context.getNodeCode();
        String operation = context.getOperationType();
        return "Save".equals(context.getButtonCode()) &&
                (("10140MAG".equals(node) || "10140MAO".equals(node))
                    && ("INSERT".equals(operation) || "UPDATE".equals(operation)));
    }

    public static boolean matchesDraft(OperationContext context, MaterialVO draft) {
        if (!supportsSave(context) || draft == null || context.getObjects().size() != 1
                || context.getGroupId() == null || context.getOrgId() == null
                || (OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
                    && context.getObjects().get(0).getDesiredOwnerOrg() == null)
                || !context.getGroupId().equals(draft.getPk_group())
                || !expectedOwner(context).equals(draft.getPk_org())
                || ("10140MAG".equals(context.getNodeCode()) && !context.getGroupId().equals(context.getOrgId()))) {
            return false;
        }
        String key = context.getObjects().get(0).getObjectPk();
        return "INSERT".equals(context.getOperationType()) ? key == null && draft.getPrimaryKey() == null
                : key != null && key.equals(draft.getPrimaryKey());
    }

    private static String expectedOwner(OperationContext context) {
        String desired = context.getObjects().get(0).getDesiredOwnerOrg();
        return OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && desired != null
            ? desired : context.getOrgId();
    }
}
