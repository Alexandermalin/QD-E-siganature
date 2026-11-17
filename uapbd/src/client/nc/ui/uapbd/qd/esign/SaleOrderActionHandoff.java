package nc.ui.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/**
 * 销售订单其他按钮的一次性授权交接。
 *
 * <p>签名窗口确认后绑定一次；原生调用前按真实按钮、组织与对象主键派发一次；动作结束即释放。
 * 窗口关闭后重新核对按钮与对象，防止签名后换单、切组织或换动作。</p>
 */
final class SaleOrderActionHandoff {
    /** 已签名的授权上下文；非空表示本动作占用了一次性授权。 */
    private OperationContext authorized;
    /** 已派发的请求标识；重复派发被拒绝。 */
    private String dispatched;

    /** 只接受已核实节点/按钮/动作域、首轮组织、单张已存订单的上下文。 */
    boolean supports(OperationContext context) {
        if (context == null) { return false; }
        if (!SaleOrderPilotScope.isPilotButton(context.getNodeCode(), context.getButtonCode())) { return false; }
        // 保存试点有独立交接，本类只接管其他按钮。
        if (SaleOrderPilotScope.SAVE_BUTTON.equals(context.getButtonCode())) { return false; }
        if (!SaleOrderPilotScope.supportsAction(context.getGroupId(), context.getOrgId(), context.getNodeCode(),
                context.getButtonCode(), context.getOperationType())) { return false; }
        if (context.getObjects() == null || context.getObjects().size() != 1) { return false; }
        BusinessObjectRef ref = context.getObjects().get(0);
        return ref != null && "SO_SALE_ORDER".equals(ref.getObjectType())
                && ref.getObjectPk() != null && ref.getObjectPk().trim().length() > 0
                && ref.getParentClientObjectId() == null;
    }

    /** 绑定一次授权；已有授权或上下文不合格时返回 false，由调用方终止受控动作。 */
    boolean bind(OperationContext context) {
        if (authorized != null || !supports(context) || context.getRequestId() == null
                || context.getRequestId().trim().isEmpty()) { return false; }
        authorized = context;
        return true;
    }

    /** 返回已绑定授权，供动作在原生调用前再次核对；未绑定时为空。 */
    OperationContext context() { return authorized; }

    /** 在原生调用前派发一次授权；按钮、组织或对象变化均拒绝，防止签名后换单。 */
    String dispatch(String button, String group, String org, String objectPk) throws BusinessException {
        if (authorized == null || dispatched != null || objectPk == null || objectPk.trim().isEmpty()
                || button == null || !button.equals(authorized.getButtonCode())
                || !authorized.getGroupId().equals(group) || !authorized.getOrgId().equals(org)
                || !objectPk.equals(authorized.getObjects().get(0).getObjectPk())) {
            throw new BusinessException("销售订单动作缺少有效授权，或按钮、组织、对象已变化。");
        }
        dispatched = authorized.getRequestId();
        return dispatched;
    }

    /** 是否已由本授权派发过该请求；客户端成功回调据此判断原生入口是否被接管。 */
    boolean wasDispatched(String requestId) {
        return requestId != null && requestId.equals(dispatched);
    }

    /** 按请求号释放绑定；其他请求不得清理当前授权。 */
    void release(String requestId) {
        if (authorized != null && authorized.getRequestId().equals(requestId)) { reset(); }
    }

    /** 清空授权与派发状态，供动作结束或失败后的清理路径使用。 */
    void reset() { authorized = null; dispatched = null; }
}
