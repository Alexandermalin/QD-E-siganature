package nc.ui.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;

/**
 * 销售管理其他三个节点的一次性授权交接（关闭管理 / 发货单 / 发票）。
 *
 * <p>与销售订单按钮的交接语义一致：签名窗口确认后绑定一次，原生最终调用前按真实按钮、组织与
 * 根对象主键派发一次，动作结束即释放；签名后换单、切组织、换动作或重复派发一律拒绝。
 * 行级动作的选中行以子对象形式随上下文授权，服务端适配器必须再次核对行主键与命令中的行集合。</p>
 */
final class SalesNodeActionHandoff {
    /** 只接管一个固定节点，防止把销售订单或其他节点的授权挪用到本节点。 */
    private final String node;
    /** 已签名的授权上下文；非空表示本动作占用了一次性授权。 */
    private OperationContext authorized;
    /** 已派发的请求标识；重复派发被拒绝。 */
    private String dispatched;

    SalesNodeActionHandoff(String node) {
        if (!SalesOtherNodeScope.isNode(node)) { throw new IllegalArgumentException("Unsupported sales node"); }
        this.node = node;
    }

    /** 只接受已核实节点/按钮/动作域、集团匹配、单张根单据（可带已授权行）的上下文。 */
    boolean supports(OperationContext context) {
        if (context == null || !node.equals(context.getNodeCode())) { return false; }
        String button = context.getButtonCode();
        if (!SalesOtherNodeScope.isPilotButton(node, button)) { return false; }
        if (!SalesOtherNodeScope.supportsAction(context.getGroupId(), context.getOrgId(), node, button,
                context.getOperationType())) { return false; }
        if (context.getObjects() == null || context.getObjects().isEmpty()) { return false; }
        String rootType = SalesOtherNodeScope.objectType(node);
        String lineType = SalesOtherNodeScope.lineObjectType(node);
        String rootId = null;
        for (BusinessObjectRef ref : context.getObjects()) {
            if (ref == null || ref.getObjectPk() == null || ref.getObjectPk().trim().length() == 0) { return false; }
            if (ref.getParentClientObjectId() == null) {
                if (rootId != null || !rootType.equals(ref.getObjectType())) { return false; }
                rootId = ref.getClientObjectId();
            } else if (lineType == null || !lineType.equals(ref.getObjectType())) {
                return false;
            }
        }
        if (rootId == null) { return false; }
        // 子对象必须指向本次唯一根对象，避免把其他单据的行混入授权范围。
        for (BusinessObjectRef ref : context.getObjects()) {
            if (ref.getParentClientObjectId() != null && !rootId.equals(ref.getParentClientObjectId())) {
                return false;
            }
        }
        return true;
    }

    /** 绑定一次授权；已有授权或上下文不合格时返回 false，由调用方终止受控动作。 */
    boolean bind(OperationContext context) {
        if (authorized != null || dispatched != null || !supports(context) || context.getRequestId() == null
                || context.getRequestId().trim().length() == 0) { return false; }
        authorized = context;
        return true;
    }

    /** 返回已绑定授权，供动作在原生调用前再次核对；未绑定时为空。 */
    OperationContext context() { return authorized; }

    /** 在原生最终调用前派发一次授权；按钮、组织或根对象变化均拒绝，防止签名后换单。 */
    String dispatch(String button, String group, String org, String rootPk) throws BusinessException {
        if (authorized == null || dispatched != null || button == null || !button.equals(authorized.getButtonCode())
                || !authorized.getGroupId().equals(group) || !authorized.getOrgId().equals(org)
                || !rootPk.equals(rootObjectPk())) {
            throw new BusinessException("业务动作缺少有效授权，或按钮、组织、对象已变化，请重新签名。");
        }
        dispatched = authorized.getRequestId();
        return dispatched;
    }

    /** 授权根对象的持久化主键。 */
    String rootObjectPk() { return authorized == null ? null : authorized.getObjects().get(0).getObjectPk(); }

    /** 是否已由本授权派发过该请求；客户端成功回调据此判断原生入口是否被接管。 */
    boolean wasDispatched(String requestId) {
        return requestId != null && requestId.equals(dispatched);
    }

    /** 按请求号释放绑定；其他请求不得清理当前授权。 */
    void release(String requestId) {
        if (authorized != null && authorized.getRequestId().equals(requestId)) { reset(); }
    }

    /** 清空授权与派发状态，供动作结束或失败后的清理路径使用。 */
    void reset() {
        authorized = null;
        dispatched = null;
    }
}
