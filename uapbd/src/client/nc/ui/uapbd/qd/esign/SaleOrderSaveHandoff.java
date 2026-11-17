package nc.ui.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/** Per-action, one-time handoff from the signature request to the final PF invocation. */
final class SaleOrderSaveHandoff {
    private OperationContext authorized;
    private String dispatched;

    boolean supports(OperationContext context) {
        if (context == null || !"40060301".equals(context.getNodeCode()) || !"Save".equals(context.getButtonCode())
                || !"INSERT".equals(context.getOperationType()) || context.getGroupId() == null
                || !SaleOrderPilotScope.supports(context.getGroupId(), context.getOrgId())
                || context.getObjects().size() != 1) { return false; }
        BusinessObjectRef ref = context.getObjects().get(0);
        return "SO_SALE_ORDER".equals(ref.getObjectType()) && ref.getObjectPk() == null
                && ref.getParentClientObjectId() == null;
    }
    boolean bind(OperationContext context) {
        if (authorized != null || !supports(context) || context.getRequestId() == null
                || context.getRequestId().trim().isEmpty()) { return false; }
        authorized = context;
        return true;
    }
    OperationContext context() { return authorized; }
    String dispatch(String group, String org, String key) throws BusinessException {
        if (authorized == null || dispatched != null || key == null || key.trim().isEmpty()
                || !authorized.getGroupId().equals(group) || !authorized.getOrgId().equals(org)) {
            throw new BusinessException("销售订单保存缺少有效授权，或订单组织已变化。");
        }
        dispatched = authorized.getRequestId();
        return dispatched;
    }
    boolean wasDispatched(String requestId) { return requestId != null && requestId.equals(dispatched); }
    void release(String requestId) {
        if (authorized != null && authorized.getRequestId().equals(requestId)) { reset(); }
    }
    void reset() { authorized = null; dispatched = null; }
}
