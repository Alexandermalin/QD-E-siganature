package nc.ui.uapbd.qd.esign;

import java.util.HashSet;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.InventoryBillScope;
import nc.vo.uapbd.qd.esign.SapplyBatchScope;
import nc.vo.uapbd.qd.esign.SapplyLineScope;

/** Binds a single authorized inventory state action to one PF dispatch. */
final class InventoryStateHandoff implements InventoryAuthorizationHandoff {
    private OperationContext authorized;
    private String dispatched;

    boolean supports(OperationContext context) {
        if (context == null || blank(context.getGroupId()) || blank(context.getOrgId())
                || SapplyLineScope.matches(context.getNodeCode(),
                        context.getButtonCode(),context.getOperationType())) return false;
        if (context.getObjects().size() != 1) return SapplyBatchScope.supports(context);
        BusinessObjectRef ref = context.getObjects().get(0);
        String mapped=InventoryBillScope.objectType(context.getNodeCode());
        boolean action = !"Save".equals(context.getButtonCode())
                && InventoryBillScope.matches(context.getNodeCode(), context.getButtonCode(), context.getOperationType());
        return mapped!=null && mapped.equals(ref.getObjectType()) && action && !blank(ref.getObjectPk())
                && ref.getObjectPk().equals(ref.getObjectPk().trim()) && ref.getParentClientObjectId() == null
                && ref.getDesiredOwnerOrg() == null && ref.getTargetOrg() == null;
    }

    public boolean bind(OperationContext context) {
        if (authorized != null || !supports(context) || blank(context.getRequestId())) return false;
        if (SapplyBatchScope.supports(context) && !SapplyBatchScope.POLICY_MODE.equals(context.getPolicyProtocol())) return false;
        authorized = context;
        return true;
    }

    OperationContext context() { return authorized; }
    public String dispatch(String group, String org, String pk) throws BusinessException {
        if (authorized == null || authorized.getObjects().size() != 1 || dispatched != null || !authorized.getGroupId().equals(group)
                || !authorized.getOrgId().equals(org) || !authorized.getObjects().get(0).getObjectPk().equals(pk)) {
            throw new BusinessException("库存操作缺少有效授权，或单据及组织已变化。");
        }
        dispatched = authorized.getRequestId();
        return dispatched;
    }
    String dispatchBatch(Set<String> selected) throws BusinessException {
        if (authorized == null || !SapplyBatchScope.supports(authorized) || dispatched != null || selected == null) {
            throw new BusinessException("出库申请批量操作缺少有效授权。");
        }
        Set<String> expected = new HashSet<String>();
        for (BusinessObjectRef ref : authorized.getObjects()) expected.add(ref.getObjectPk());
        if (!expected.equals(selected)) throw new BusinessException("出库申请批量所选单据已变化。");
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
