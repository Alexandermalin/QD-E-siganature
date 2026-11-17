package nc.impl.so.qd.esign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/**
 * 销售订单其他按钮的服务端固定动作白名单。
 *
 * <p>只接受已核实的节点、按钮、对象类型与动作域组合；服务端据此决定原生方法，客户端不能传入类名、
 * SQL 或任意脚本名。未列入白名单的按钮在受控组织内一律拒绝，不回落到低层脚本写入。</p>
 */
public final class SaleOrderActionRoute {
    /** 已核实的原生落点类型。 */
    public enum Kind {
        /** 删除：保留完整 PF DELETE 路径。 */
        PF_DELETE("DELETE", "PF DELETE", false),
        /** 提交：保留完整 PF SAVE 路径，同时覆盖「保存并提交」的提交段。 */
        PF_SAVE("SAVE", "PF SAVE", false),
        /** 收回：保留完整 PF UNSAVE 路径。 */
        PF_UNSAVE("UNSAVE", "PF UNSAVE", false),
        /** 审批：点击审批窗口「确定」后保留完整 PF APPROVE 路径。 */
        PF_APPROVE("APPROVE", "PF APPROVE", false),
        /** 取消审批：保留完整 PF UNAPPROVE 路径。 */
        PF_UNAPPROVE("UNAPPROVE", "PF UNAPPROVE", false),
        /** 冻结：ISaleOrderCloseManageMaintain.freezeSaleOrder。 */
        STATE_FREEZE("UPDATE", "ISaleOrderCloseManageMaintain.freezeSaleOrder", true),
        /** 解冻：ISaleOrderCloseManageMaintain.unFreezeSaleOrder，保留 ATP 继续/取消分支。 */
        STATE_UNFREEZE("UPDATE", "ISaleOrderCloseManageMaintain.unFreezeSaleOrder", true),
        /** 整单关闭：closeSaleOrder，关闭原因与签名事由分别传递。 */
        STATE_CLOSE("UPDATE", "ISaleOrderCloseManageMaintain.closeSaleOrder", true);

        private final String operationType;
        private final String nativeTarget;
        private final boolean state;

        Kind(String operationType, String nativeTarget, boolean state) {
            this.operationType = operationType;
            this.nativeTarget = nativeTarget;
            this.state = state;
        }

        /** 该动作在映射中登记的动作域。 */
        public String getOperationType() { return operationType; }

        /** 原生落点的可读描述，用于日志与离线测试断言。 */
        public String getNativeTarget() { return nativeTarget; }

        /** 是否走 ISaleOrderCloseManageMaintain 直接服务（非 PF）。 */
        public boolean isState() { return state; }
    }

    /** 按钮到原生落点类型的固定映射。 */
    private static final Map<String, Kind> KINDS;

    /** 已通过离线用例、允许执行的按钮集合；解冻按 ATP 确认协议受控执行。 */
    private static final Map<String, Boolean> ENABLED;

    static {
        Map<String, Kind> kinds = new LinkedHashMap<String, Kind>();
        kinds.put("Delete", Kind.PF_DELETE);
        kinds.put("Commit", Kind.PF_SAVE);
        kinds.put("UnCommit", Kind.PF_UNSAVE);
        kinds.put("Approve", Kind.PF_APPROVE);
        kinds.put("UnApprove", Kind.PF_UNAPPROVE);
        kinds.put("Freeze", Kind.STATE_FREEZE);
        kinds.put("UnFreeze", Kind.STATE_UNFREEZE);
        kinds.put("BillClose", Kind.STATE_CLOSE);
        KINDS = Collections.unmodifiableMap(kinds);

        Map<String, Boolean> enabled = new LinkedHashMap<String, Boolean>();
        enabled.put("Delete", Boolean.TRUE);
        enabled.put("Commit", Boolean.TRUE);
        enabled.put("UnCommit", Boolean.TRUE);
        enabled.put("Approve", Boolean.TRUE);
        enabled.put("UnApprove", Boolean.TRUE);
        enabled.put("Freeze", Boolean.TRUE);
        enabled.put("UnFreeze", Boolean.TRUE);
        enabled.put("BillClose", Boolean.TRUE);
        ENABLED = Collections.unmodifiableMap(enabled);
    }

    private final String button;
    private final String operationType;
    private final Kind kind;
    private final String scopeId;
    private final String objectPk;

    private SaleOrderActionRoute(String button, Kind kind, String scopeId, String objectPk) {
        this.button = button;
        this.operationType = SaleOrderPilotScope.operationTypeFor(SaleOrderPilotScope.NODE_CODE, button);
        this.kind = kind;
        this.scopeId = scopeId;
        this.objectPk = objectPk;
    }

    /**
     * 为当前上下文解析固定动作；不属于本批按钮返回 null（保存由 PF 保存分支处理），
     * 属于受控节点但不满足白名单时抛业务异常，禁止降级到其他写入路径。
     */
    public static SaleOrderActionRoute forContext(OperationContext context) throws BusinessException {
        if (context == null || !SaleOrderPilotScope.isPilotNode(context.getNodeCode())) { return null; }
        String button = context.getButtonCode();
        // 保存试点已有独立的白名单与适配器，这里不重复接管。
        if (SaleOrderPilotScope.SAVE_BUTTON.equals(button)) { return null; }
        if (!SaleOrderPilotScope.isPilotButton(context.getNodeCode(), button)) {
            throw new BusinessException("销售订单受控入口未登记该按钮：" + button);
        }
        if (!SaleOrderPilotScope.supports(context.getGroupId(), context.getOrgId())) {
            throw new BusinessException("销售订单受控动作仅支持已核实集团与 990 销售组织。");
        }
        if (!SaleOrderPilotScope.supportsOperation(context.getNodeCode(), button, context.getOperationType())) {
            throw new BusinessException("销售订单动作域与按钮不匹配：" + button + "/" + context.getOperationType());
        }
        List<BusinessObjectRef> objects = context.getObjects();
        if (objects == null || objects.size() != 1) {
            throw new BusinessException("销售订单受控动作只支持一张已核实的订单。");
        }
        BusinessObjectRef ref = objects.get(0);
        if (ref == null || !"SO_SALE_ORDER".equals(ref.getObjectType())) {
            throw new BusinessException("销售订单受控动作的对象类型不匹配。");
        }
        if (ref.getObjectPk() == null || ref.getObjectPk().trim().length() == 0) {
            throw new BusinessException("销售订单受控动作缺少已授权的订单主键。");
        }
        if (ref.getParentClientObjectId() != null) {
            throw new BusinessException("销售订单受控动作不接受子对象作为独立目标。");
        }
        return new SaleOrderActionRoute(button, KINDS.get(button),
                SaleOrderPilotScope.scopeIdFor(context.getNodeCode(), button), ref.getObjectPk());
    }

    /** 返回按钮到原生落点类型的只读映射，供预检脚本与离线测试逐条核对。 */
    public static Map<String, Kind> supportedActions() { return KINDS; }

    /** 真实按钮编码，日志与审计据此区分业务动作。 */
    public String getButton() { return button; }

    /** 中文按钮名，便于日志区分保存与提交等动作。 */
    public String getButtonName() { return SaleOrderPilotScope.buttonName(button); }

    /** 映射登记的动作域。 */
    public String getOperationType() { return operationType; }

    /** 原生落点类型。 */
    public Kind getKind() { return kind; }

    /** 正式清单范围 ID。 */
    public String getScopeId() { return scopeId; }

    /** 已授权的订单主键。 */
    public String getObjectPk() { return objectPk; }

    /** 该按钮是否已完成受控接入并允许执行。 */
    public boolean isEnabled() { return Boolean.TRUE.equals(ENABLED.get(button)); }

    /** 未完成受控接入的按钮必须在执行前拒绝，不得由通用分支兜底。 */
    public void requireEnabled() throws BusinessException {
        if (!isEnabled()) {
            throw new BusinessException("销售订单「" + getButtonName() + "」尚未接入受控执行，本批保持关闭。");
        }
    }
}
