package nc.impl.so.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;

/**
 * 发货单维护（节点 40060402，正式清单第 229–238 行）的服务端固定动作白名单。
 *
 * <p>只接受已核实的节点、按钮、对象类型与动作域组合；服务端据此决定原生方法，客户端不能传入类名、
 * SQL 或任意脚本名。发货单与销售订单即使按钮同名也不共享授权：对象类型、原生落点与范围 ID 都在
 * 本节点独立登记。未列入白名单的按钮一律拒绝，不回落到低层脚本写入。</p>
 *
 * <p>PF 六键（保存/删除/提交/收回/审批/取消审批）保留原生流程平台；辅助四键（整单关闭/打开、
 * 出库行关闭/打开）固定委托 {@code nc.itf.so.m4331.IDeliveryAssitFunc}。本批保持删除与两个「打开」
 * 动作关闭：删除的确认顺序与打开动作的 ATP/信用/容差继续协议尚未在真实 NC 会话核实。</p>
 */
public final class DeliveryActionRoute {
    /** 动作作用粒度：整单动作只接受根对象，行动作必须同时给出已授权的子对象行。 */
    public enum Granularity {
        /** 整单动作：1 个根对象，不接受子对象。 */
        BILL,
        /** 行动作：1 个根对象加 1 个已授权的表体行子对象。 */
        ROW
    }

    /** 已核实的原生落点类型。 */
    public enum Kind {
        /** 保存：保留完整 PF WRITE 路径。 */
        PF_WRITE("INSERT", "IplatFormEntry.processAction(WRITE)", Granularity.BILL, "WRITE"),
        /** 删除：保留完整 PF DELETE 路径；本批保持关闭。 */
        PF_DELETE("DELETE", "IplatFormEntry.processAction(DELETE)", Granularity.BILL, "DELETE"),
        /** 提交：保留完整 PF SAVE 路径。 */
        PF_SAVE("UPDATE", "IplatFormEntry.processAction(SAVE)", Granularity.BILL, "SAVE"),
        /** 收回：保留完整 PF UNSAVE 路径。 */
        PF_UNSAVE("UPDATE", "IplatFormEntry.processAction(UNSAVE)", Granularity.BILL, "UNSAVE"),
        /** 审批：点击审批窗口「确定」后保留完整 PF APPROVE 路径。 */
        PF_APPROVE("UPDATE", "IplatFormEntry.processAction(APPROVE)", Granularity.BILL, "APPROVE"),
        /** 取消审批：保留完整 PF UNAPPROVE 路径。 */
        PF_UNAPPROVE("UPDATE", "IplatFormEntry.processAction(UNAPPROVE)", Granularity.BILL, "UNAPPROVE"),
        /** 整单关闭：IDeliveryAssitFunc.closeDelivery(SOParameterVO)。 */
        STATE_BILL_CLOSE("UPDATE", "IDeliveryAssitFunc.closeDelivery(SOParameterVO)", Granularity.BILL, null),
        /** 整单打开：IDeliveryAssitFunc.openDelivery(SOParameterVO)；本批保持关闭。 */
        STATE_BILL_OPEN("UPDATE", "IDeliveryAssitFunc.openDelivery(SOParameterVO)", Granularity.BILL, null),
        /** 出库关闭：IDeliveryAssitFunc.closeDeliveryRows(DeliveryVO,int[])，下标为表体行下标。 */
        STATE_ROW_CLOSE("UPDATE", "IDeliveryAssitFunc.closeDeliveryRows(DeliveryVO,int[])", Granularity.ROW, null),
        /** 出库打开：IDeliveryAssitFunc.openDeiveryRows(SOParameterVO,int[])（原生拼写）；本批保持关闭。 */
        STATE_ROW_OPEN("UPDATE", "IDeliveryAssitFunc.openDeiveryRows(SOParameterVO,int[])", Granularity.ROW, null);

        private final String operationType;
        private final String nativeTarget;
        private final Granularity granularity;
        private final String pfAction;

        Kind(String operationType, String nativeTarget, Granularity granularity, String pfAction) {
            this.operationType = operationType;
            this.nativeTarget = nativeTarget;
            this.granularity = granularity;
            this.pfAction = pfAction;
        }

        /** 该动作在映射中登记的动作域。 */
        public String getOperationType() { return operationType; }

        /** 原生落点的可读描述，用于日志与离线测试断言。 */
        public String getNativeTarget() { return nativeTarget; }

        /** 作用粒度：整单或行。 */
        public Granularity getGranularity() { return granularity; }

        /** 是否走原生 PF 流程平台（保存/删除/提交/收回/审批/取消审批）。 */
        public boolean isPf() { return pfAction != null; }

        /** 是否直接委托 IDeliveryAssitFunc（整单/行关闭与打开）。 */
        public boolean isDirectService() { return pfAction == null; }

        /** 原生 PF 动作脚本名；直接服务动作返回 null。 */
        public String getPfAction() { return pfAction; }
    }

    /** 按钮到原生落点类型的固定映射，与正式清单第 229–238 行一一对应。 */
    private static final Map<String, Kind> KINDS;
    /** 发货单维护功能节点下的按钮到正式清单范围 ID 的固定映射。 */
    private static final Map<String, String> SCOPES;

    static {
        Map<String, Kind> kinds = new LinkedHashMap<String, Kind>();
        kinds.put("Save", Kind.PF_WRITE);
        kinds.put("Delete", Kind.PF_DELETE);
        kinds.put("Commit", Kind.PF_SAVE);
        kinds.put("UnCommit", Kind.PF_UNSAVE);
        kinds.put("Approve", Kind.PF_APPROVE);
        kinds.put("UnApprove", Kind.PF_UNAPPROVE);
        kinds.put("BillClose", Kind.STATE_BILL_CLOSE);
        kinds.put("BillOpen", Kind.STATE_BILL_OPEN);
        kinds.put("OutClose", Kind.STATE_ROW_CLOSE);
        kinds.put("OutOpen", Kind.STATE_ROW_OPEN);
        KINDS = Collections.unmodifiableMap(kinds);
        SCOPES = SalesOtherNodeScope.buttonScopes(SalesOtherNodeScope.DELIVERY_NODE);
    }

    private final String button;
    private final Kind kind;
    private final String operationType;
    private final String scopeId;
    private final String objectPk;
    /** 已完成授权的表体行主键；整单动作为 null。 */
    private final String rowPk;

    private DeliveryActionRoute(String button, Kind kind, String scopeId, String objectPk, String rowPk) {
        this.button = button;
        this.kind = kind;
        this.operationType = kind.getOperationType();
        this.scopeId = scopeId;
        this.objectPk = objectPk;
        this.rowPk = rowPk;
    }

    /**
     * 为当前上下文解析固定动作；不属于发货单节点返回 null（由其他节点适配器自行处理），
     * 属于本节点但不满足白名单时抛业务异常，禁止降级到其他写入路径。
     *
     * <p>整单动作只接受一个已持久化的 {@code SO_DELIVERY} 根对象；行动作允许一个已持久化的
     * {@code SO_DELIVERY_B} 子对象，且必须指向本次唯一根对象。本批只做单笔：行动作恰好 1 行。</p>
     */
    public static DeliveryActionRoute forContext(OperationContext context) throws BusinessException {
        if (context == null || !SalesOtherNodeScope.DELIVERY_NODE.equals(context.getNodeCode())) { return null; }
        String button = context.getButtonCode();
        if (!SalesOtherNodeScope.isPilotButton(SalesOtherNodeScope.DELIVERY_NODE, button)) {
            throw new BusinessException("发货单受控入口未登记该按钮：" + button);
        }
        Kind kind = KINDS.get(button);
        if (kind == null) { throw new BusinessException("发货单受控入口未登记该按钮：" + button); }
        if (!SalesOtherNodeScope.supports(context.getGroupId(), context.getOrgId())) {
            throw new BusinessException("发货单受控动作仅支持已核实集团与明确业务组织。");
        }
        if (!SalesOtherNodeScope.supportsOperation(SalesOtherNodeScope.DELIVERY_NODE, button,
                context.getOperationType())) {
            throw new BusinessException("发货单动作域与按钮不匹配：" + button + "/" + context.getOperationType());
        }
        List<BusinessObjectRef> objects = context.getObjects();
        if (objects == null || objects.isEmpty()) {
            throw new BusinessException("发货单受控动作缺少已授权的业务对象。");
        }
        String rootClientId = null;
        String rootPk = null;
        List<BusinessObjectRef> lines = new ArrayList<BusinessObjectRef>();
        for (BusinessObjectRef ref : objects) {
            if (ref == null || ref.getObjectPk() == null || ref.getObjectPk().trim().length() == 0) {
                throw new BusinessException("发货单受控动作缺少已持久化的对象主键。");
            }
            if (ref.getObjectPk().length() > 20 || !ref.getObjectPk().equals(ref.getObjectPk().trim())) {
                throw new BusinessException("发货单受控动作的对象主键不受支持。");
            }
            if (ref.getParentClientObjectId() == null) {
                if (rootClientId != null || !SalesOtherNodeScope.DELIVERY.equals(ref.getObjectType())) {
                    throw new BusinessException("发货单受控动作只接受一个已核实的发货单根对象。");
                }
                rootClientId = ref.getClientObjectId();
                rootPk = ref.getObjectPk();
            } else {
                if (kind.getGranularity() != Granularity.ROW
                        || !SalesOtherNodeScope.DELIVERY_LINE.equals(ref.getObjectType())) {
                    throw new BusinessException("发货单整单动作不接受子对象作为独立目标。");
                }
                lines.add(ref);
            }
        }
        if (rootPk == null) { throw new BusinessException("发货单受控动作缺少已授权的发货单根对象。"); }
        String rowPk = null;
        if (kind.getGranularity() == Granularity.ROW) {
            if (lines.size() != 1) { throw new BusinessException("发货单行动作只支持一个已授权的表体行。"); }
            BusinessObjectRef line = lines.get(0);
            if (line.getObjectPk() == null || line.getObjectPk().trim().length() == 0) {
                throw new BusinessException("发货单行动作缺少已授权的表体行主键。");
            }
            if (!rootClientId.equals(line.getParentClientObjectId())) {
                throw new BusinessException("发货单行动作的表体行不属于本次授权发货单。");
            }
            rowPk = line.getObjectPk();
        } else if (!lines.isEmpty()) {
            throw new BusinessException("发货单整单动作不接受子对象作为独立目标。");
        }
        return new DeliveryActionRoute(button, kind,
                SalesOtherNodeScope.scopeIdFor(SalesOtherNodeScope.DELIVERY_NODE, button), rootPk, rowPk);
    }

    /** 返回按钮到原生落点类型的只读映射，供预检脚本与离线测试逐条核对。 */
    public static Map<String, Kind> supportedActions() { return KINDS; }

    /** 返回按钮到正式清单范围 ID 的只读映射，顺序与正式清单一致。 */
    public static Map<String, String> buttonScopes() { return SCOPES; }

    /** 真实按钮编码，日志与审计据此区分业务动作。 */
    public String getButton() { return button; }

    /** 中文按钮名，便于日志区分保存与提交等动作。 */
    public String getButtonName() { return SalesOtherNodeScope.buttonName(button); }

    /** 映射登记的动作域。 */
    public String getOperationType() { return operationType; }

    /** 原生落点类型。 */
    public Kind getKind() { return kind; }

    /** 正式清单范围 ID。 */
    public String getScopeId() { return scopeId; }

    /** 已授权的发货单主键。 */
    public String getObjectPk() { return objectPk; }

    /** 已授权的表体行主键；整单动作为 null。 */
    public String getRowPk() { return rowPk; }

    /** 是否为行动作（出库关闭/打开）。 */
    public boolean isRowAction() { return kind.getGranularity() == Granularity.ROW; }

    /** 该按钮是否已完成受控接入并允许执行；与 SalesOtherNodeScope 的启用开关保持一致。 */
    public boolean isEnabled() {
        return SalesOtherNodeScope.isEnabled(SalesOtherNodeScope.DELIVERY_NODE, button);
    }

    /** 未完成受控接入的按钮必须在执行前拒绝，不得由通用分支兜底。 */
    public void requireEnabled() throws BusinessException {
        if (!isEnabled()) {
            throw new BusinessException("发货单「" + getButtonName() + "」尚未接入受控执行，本批保持关闭。");
        }
    }
}
