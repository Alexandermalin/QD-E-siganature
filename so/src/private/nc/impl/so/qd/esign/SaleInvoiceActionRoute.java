package nc.impl.so.qd.esign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;

/**
 * 销售发票维护（节点 40060501，第 239–244 行）的服务端固定动作白名单。
 *
 * <p>只接受已核实的节点、按钮、对象类型与动作域组合；服务端据此决定原生 PF 动作，客户端不能传入
 * 类名、SQL 或任意脚本名。未列入白名单的按钮在受控集团内一律拒绝，不回落到低层脚本写入。</p>
 *
 * <p>策略启用状态以 {@link SalesOtherNodeScope} 为唯一口径：删除按钮（第 240 行，
 * <code>Delete / 1001Z810000000008DPR</code>）本批保持关闭，因为已核实其二次确认位于
 * {@code DeleteScriptAction.doAction} 基类（返回值 ≠ 4 即取消），而确认框与签名窗口的先后顺序
 * 尚未在真实 NC 会话核实；{@code fs_delete}（0001Z810000000017PB3）属附件面板按钮组，不是本节点
 * 删除落点，不登记。</p>
 */
public final class SaleInvoiceActionRoute {
    /** 已核实的原生落点类型；operationType 与 {@link SalesOtherNodeScope} 的按钮归类一致。 */
    public enum Kind {
        /** 保存：完整 PF WRITE 路径，保留 compositevalidateService 组合校验。 */
        PF_WRITE("INSERT", "PF WRITE", "WRITE"),
        /** 删除：完整 PF DELETE 路径；本批关闭（确认框语义待真实会话核实）。 */
        PF_DELETE("DELETE", "PF DELETE", "DELETE"),
        /** 提交：完整 PF SAVE 路径（filledUpInFlow=true），同时覆盖「保存并提交」的提交段。 */
        PF_SAVE("UPDATE", "PF SAVE", "SAVE"),
        /** 收回：完整 PF UNSAVE 路径（服务端仅 BillUpdate.update）。 */
        PF_UNSAVE("UPDATE", "PF UNSAVE", "UNSAVE"),
        /** 审批：点击审批窗口「确定」后保留完整 PF APPROVE 路径。 */
        PF_APPROVE("UPDATE", "PF APPROVE", "APPROVE"),
        /** 取消审批：保留完整 PF UNAPPROVE 路径与原生下游/核销/凭证限制规则。 */
        PF_UNAPPROVE("UPDATE", "PF UNAPPROVE", "UNAPPROVE");

        private final String operationType;
        private final String nativeTarget;
        private final String nativeAction;

        Kind(String operationType, String nativeTarget, String nativeAction) {
            this.operationType = operationType;
            this.nativeTarget = nativeTarget;
            this.nativeAction = nativeAction;
        }

        /** 该动作在映射中登记的动作域。 */
        public String getOperationType() { return operationType; }

        /** 原生落点的可读描述，用于日志与离线测试断言。 */
        public String getNativeTarget() { return nativeTarget; }

        /** 原生 PF 动作名；服务端必须原样交给 IplatFormEntry.processAction。 */
        public String getNativeAction() { return nativeAction; }
    }

    /** 按钮到原生落点类型的固定映射：6 个已核实 bean 一一对应，顺序稳定供预检输出。 */
    private static final Map<String, Kind> KINDS;

    static {
        Map<String, Kind> kinds = new LinkedHashMap<String, Kind>();
        kinds.put("Save", Kind.PF_WRITE);
        kinds.put("Delete", Kind.PF_DELETE);
        kinds.put("Commit", Kind.PF_SAVE);
        kinds.put("UnCommit", Kind.PF_UNSAVE);
        kinds.put("Approve", Kind.PF_APPROVE);
        kinds.put("UnApprove", Kind.PF_UNAPPROVE);
        KINDS = Collections.unmodifiableMap(kinds);
    }

    private final String button;
    private final String operationType;
    private final Kind kind;
    private final String scopeId;
    private final String objectPk;

    private SaleInvoiceActionRoute(String button, Kind kind, String scopeId, String objectPk) {
        this.button = button;
        this.operationType = SalesOtherNodeScope.operationTypeFor(SalesOtherNodeScope.INVOICE_NODE, button);
        this.kind = kind;
        this.scopeId = scopeId;
        this.objectPk = objectPk;
    }

    /**
     * 为当前上下文解析固定动作；不属于本节点返回 null，属于本节点但不满足白名单时抛业务异常，
     * 禁止降级到其他写入路径。
     */
    public static SaleInvoiceActionRoute forContext(OperationContext context) throws BusinessException {
        if (context == null || !SalesOtherNodeScope.INVOICE_NODE.equals(context.getNodeCode())) { return null; }
        String button = context.getButtonCode();
        if (!SalesOtherNodeScope.isPilotButton(SalesOtherNodeScope.INVOICE_NODE, button)) {
            throw new BusinessException("销售发票受控入口未登记该按钮：" + button);
        }
        if (!SalesOtherNodeScope.supportsAction(context.getGroupId(), context.getOrgId(),
                SalesOtherNodeScope.INVOICE_NODE, button, context.getOperationType())) {
            throw new BusinessException("销售发票动作未通过固定白名单核对：" + button + "/"
                    + context.getOperationType());
        }
        List<BusinessObjectRef> objects = context.getObjects();
        if (objects == null || objects.size() != 1) {
            throw new BusinessException("销售发票受控动作只支持一张已核实的发票。");
        }
        BusinessObjectRef ref = objects.get(0);
        if (ref == null || !SalesOtherNodeScope.SALE_INVOICE.equals(ref.getObjectType())) {
            throw new BusinessException("销售发票受控动作的对象类型不匹配。");
        }
        if (ref.getObjectPk() == null || ref.getObjectPk().trim().length() == 0) {
            throw new BusinessException("销售发票受控动作缺少已授权的发票主键。");
        }
        if (ref.getParentClientObjectId() != null) {
            throw new BusinessException("销售发票受控动作不接受子对象作为独立目标。");
        }
        Kind kind = KINDS.get(button);
        if (kind == null) { throw new BusinessException("销售发票受控入口未登记该按钮：" + button); }
        return new SaleInvoiceActionRoute(button, kind,
                SalesOtherNodeScope.scopeIdFor(SalesOtherNodeScope.INVOICE_NODE, button), ref.getObjectPk());
    }

    /** 返回按钮到原生落点类型的只读映射，供预检脚本与离线测试逐条核对。 */
    public static Map<String, Kind> supportedActions() { return KINDS; }

    /** 真实按钮编码，日志与审计据此区分业务动作。 */
    public String getButton() { return button; }

    /** 中文按钮名，便于日志区分保存、提交与审批等动作。 */
    public String getButtonName() { return SalesOtherNodeScope.buttonName(button); }

    /** 映射登记的动作域。 */
    public String getOperationType() { return operationType; }

    /** 原生落点类型。 */
    public Kind getKind() { return kind; }

    /** 正式清单范围 ID。 */
    public String getScopeId() { return scopeId; }

    /** 已授权的发票主键。 */
    public String getObjectPk() { return objectPk; }

    /** 该按钮是否已完成受控接入并允许执行；以共享白名单为唯一口径。 */
    public boolean isEnabled() {
        return SalesOtherNodeScope.isEnabled(SalesOtherNodeScope.INVOICE_NODE, button);
    }

    /** 未完成受控接入的按钮必须在执行前拒绝，不得由通用分支兜底；删除本批关闭。 */
    public void requireEnabled() throws BusinessException {
        if (!isEnabled()) {
            throw new BusinessException("销售发票「" + getButtonName() + "」尚未接入受控执行，本批保持关闭。");
        }
    }
}
