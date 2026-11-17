package nc.ui.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.InventoryBillScope;

/** Binds one inventory save authorization to one native PF dispatch. */
final class InventorySaveHandoff implements InventoryAuthorizationHandoff {
    private OperationContext authorized;
    private String dispatched;

    boolean supports(OperationContext context) {
        if (context == null || !"Save".equals(context.getButtonCode())
                || !InventoryBillScope.matches(context.getNodeCode(), context.getButtonCode(), context.getOperationType())
                || blank(context.getGroupId())
                || blank(context.getOrgId()) || context.getObjects().size() != 1) return false;
        BusinessObjectRef ref = context.getObjects().get(0);
        String mapped=InventoryBillScope.objectType(context.getNodeCode());
        if (mapped==null || !mapped.equals(ref.getObjectType()) || ref.getParentClientObjectId() != null
                || ref.getDesiredOwnerOrg() != null || ref.getTargetOrg() != null) return false;
        if ("INSERT".equals(context.getOperationType())) return ref.getObjectPk() == null;
        return "UPDATE".equals(context.getOperationType()) && !blank(ref.getObjectPk());
    }

    public boolean bind(OperationContext context) {
        if (authorized != null || !supports(context) || blank(context.getRequestId())) return false;
        authorized = context;
        return true;
    }

    OperationContext context() { return authorized; }

    public String dispatch(String group, String org, String billPk) throws BusinessException {
        if (authorized == null || dispatched != null || !authorized.getGroupId().equals(group)
                || !authorized.getOrgId().equals(org)) {
            throw new BusinessException("库存保存缺少有效授权，或单据组织已变化。");
        }
        if ("UPDATE".equals(authorized.getOperationType())
                && !authorized.getObjects().get(0).getObjectPk().equals(billPk)) {
            throw new BusinessException("库存保存单据与签名授权不一致。");
        }
        dispatched = authorized.getRequestId();
        return dispatched;
    }

    public boolean wasDispatched(String requestId) { return requestId != null && requestId.equals(dispatched); }
    public void release(String requestId) {
        if (authorized != null && authorized.getRequestId().equals(requestId)) reset();
    }
    void reset() { authorized = null; dispatched = null; }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
