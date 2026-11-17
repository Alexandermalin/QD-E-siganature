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
 * 销售订单关闭管理（节点 40060303）的服务端固定动作白名单。
 *
 * <p>只接受已核实的 12 个按钮、固定动作域、单个根对象（销售订单）以及行动作下的已授权行对象；
 * 服务端据此决定原生方法，客户端不能传入类名、方法名、SQL 或任意流程名。未列入白名单的按钮、
 * 串节点、串动作域、多单、整单带行对象一律拒绝，不回落到未受控的原生写入。</p>
 *
 * <p>整单动作（BillClose / BillOpen）作用于一张订单及其全部表体行；其余 10 项为行动作，
 * 只作用于选中行。行级按钮在原生界面多选时直接放行，因此受控路径必须只接受单笔单行授权。</p>
 */
public final class SaleCloseActionRoute {
    /** 动作粒度：整单（含该单全部行）或单行。 */
    public enum Granularity {
        BILL, ROW
    }

    /** 原生命中返回类型：整单数组、行视图数组或参数容器数组。 */
    public enum ResultKind {
        ORDER_ARRAY, VIEW_ARRAY, PARAMETER_ARRAY
    }

    /**
     * 已核实的原生落点映射：按钮 → {动作域, 原生方法, 粒度, 返回类型}。
     *
     * <p>全部 12 项属于 {@code nc.itf.so.m30.closemanage.ISaleOrderCloseManageMaintain}，
     * 动作域统一为 UPDATE。整单打开与出库打开返回 {@code SOParameterVO[]}，其中没有任何逐单
     * 成功标志，不能当作成功回执；这两项因原生 ATP「继续确认」恢复协议未核实而保持关闭。</p>
     */
    public enum Kind {
        /** 整单关闭：closeSaleOrder(SaleOrderVO[])。 */
        BILL_CLOSE("UPDATE", "closeSaleOrder", Granularity.BILL, ResultKind.ORDER_ARRAY),
        /** 整单打开：openSaleOrder(SaleOrderVO[], boolean)，客户端恒传 false；本批关闭。 */
        BILL_OPEN("UPDATE", "openSaleOrder", Granularity.BILL, ResultKind.PARAMETER_ARRAY),
        /** 行关闭：closeSaleOrderRow(SaleOrderViewVO[])。 */
        LINE_CLOSE("UPDATE", "closeSaleOrderRow", Granularity.ROW, ResultKind.VIEW_ARRAY),
        /** 行打开：openSaleOrderRow(SaleOrderViewVO[])。 */
        LINE_OPEN("UPDATE", "openSaleOrderRow", Granularity.ROW, ResultKind.VIEW_ARRAY),
        /** 发货关闭：closeSaleOrderSend(SaleOrderVO[])，服务内按行转整单。 */
        SEND_CLOSE("UPDATE", "closeSaleOrderSend", Granularity.ROW, ResultKind.VIEW_ARRAY),
        /** 发货打开：openSaleOrderSend(SaleOrderVO[])。 */
        SEND_OPEN("UPDATE", "openSaleOrderSend", Granularity.ROW, ResultKind.VIEW_ARRAY),
        /** 出库关闭：closeSaleOrderOut(SaleOrderVO[])。 */
        OUT_CLOSE("UPDATE", "closeSaleOrderOut", Granularity.ROW, ResultKind.VIEW_ARRAY),
        /** 出库打开：openSaleOrderOut(SaleOrderVO[], boolean)，布尔在原生实现中未被引用；本批关闭。 */
        OUT_OPEN("UPDATE", "openSaleOrderOut", Granularity.ROW, ResultKind.PARAMETER_ARRAY),
        /** 开票关闭：closeSaleOrderInvoice(SaleOrderVO[])。 */
        INVOICE_CLOSE("UPDATE", "closeSaleOrderInvoice", Granularity.ROW, ResultKind.VIEW_ARRAY),
        /** 开票打开：openSaleOrderInvoice(SaleOrderVO[])。 */
        INVOICE_OPEN("UPDATE", "openSaleOrderInvoice", Granularity.ROW, ResultKind.VIEW_ARRAY),
        /** 结算关闭：closeSaleOrderSettle(SaleOrderVO[])。 */
        SETTLE_CLOSE("UPDATE", "closeSaleOrderSettle", Granularity.ROW, ResultKind.VIEW_ARRAY),
        /** 结算打开：openSaleOrderSettle(SaleOrderVO[])。 */
        SETTLE_OPEN("UPDATE", "openSaleOrderSettle", Granularity.ROW, ResultKind.VIEW_ARRAY);

        private final String operationType;
        private final String nativeTarget;
        private final Granularity granularity;
        private final ResultKind resultKind;

        Kind(String operationType, String method, Granularity granularity, ResultKind resultKind) {
            this.operationType = operationType;
            this.nativeTarget = "ISaleOrderCloseManageMaintain." + method;
            this.granularity = granularity;
            this.resultKind = resultKind;
        }

        /** 该动作在映射中登记的动作域。 */
        public String getOperationType() { return operationType; }

        /** 原生落点的可读描述，用于日志与离线测试断言。 */
        public String getNativeTarget() { return nativeTarget; }

        /** 整单动作或行动作。 */
        public Granularity getGranularity() { return granularity; }

        /** 原生命中返回类型。 */
        public ResultKind getResultKind() { return resultKind; }

        /** 是否整单动作（一张订单含全部行）。 */
        public boolean isBill() { return granularity == Granularity.BILL; }
    }

    /** 按钮 → 原生落点类型的固定映射；顺序稳定，供预检与文档逐条输出。 */
    private static final Map<String, Kind> KINDS;

    static {
        Map<String, Kind> kinds = new LinkedHashMap<String, Kind>();
        kinds.put("BillClose", Kind.BILL_CLOSE);
        kinds.put("BillOpen", Kind.BILL_OPEN);
        kinds.put("LineClose", Kind.LINE_CLOSE);
        kinds.put("LineOpen", Kind.LINE_OPEN);
        kinds.put("SendClose", Kind.SEND_CLOSE);
        kinds.put("SendOpen", Kind.SEND_OPEN);
        kinds.put("OutClose", Kind.OUT_CLOSE);
        kinds.put("OutOpen", Kind.OUT_OPEN);
        kinds.put("InvoiceClose", Kind.INVOICE_CLOSE);
        kinds.put("InvoiceOpen", Kind.INVOICE_OPEN);
        kinds.put("SettleClose", Kind.SETTLE_CLOSE);
        kinds.put("SettleOpen", Kind.SETTLE_OPEN);
        KINDS = Collections.unmodifiableMap(kinds);
    }

    private final String button;
    private final String operationType;
    private final Kind kind;
    private final String scopeId;
    private final String objectPk;
    private final List<String> linePks;

    private SaleCloseActionRoute(String button, Kind kind, String scopeId, String objectPk, List<String> linePks) {
        this.button = button;
        this.operationType = SalesOtherNodeScope.operationTypeFor(SalesOtherNodeScope.CLOSE_NODE, button);
        this.kind = kind;
        this.scopeId = scopeId;
        this.objectPk = objectPk;
        this.linePks = linePks;
    }

    /**
     * 为当前上下文解析固定动作。
     *
     * <p>不属于关闭管理节点返回 null（交给其他适配器处理）；属于本节点但不满足固定的
     * 节点/按钮/动作域/组织/对象组合时抛业务异常，禁止降级到其他写入路径。</p>
     */
    public static SaleCloseActionRoute forContext(OperationContext context) throws BusinessException {
        if (context == null || !SalesOtherNodeScope.CLOSE_NODE.equals(context.getNodeCode())) {
            return null;
        }
        String button = context.getButtonCode();
        if (!SalesOtherNodeScope.isPilotButton(SalesOtherNodeScope.CLOSE_NODE, button)) {
            throw new BusinessException("销售订单关闭管理未登记该按钮：" + button);
        }
        Kind kind = KINDS.get(button);
        if (kind == null) {
            throw new BusinessException("销售订单关闭管理动作未映射原生落点：" + button);
        }
        if (!SalesOtherNodeScope.supports(context.getGroupId(), context.getOrgId())) {
            throw new BusinessException("销售订单关闭管理受控动作仅支持已核实集团与销售组织。");
        }
        if (!SalesOtherNodeScope.supportsAction(context.getGroupId(), context.getOrgId(),
                SalesOtherNodeScope.CLOSE_NODE, button, context.getOperationType())) {
            throw new BusinessException("销售订单关闭管理动作域与按钮不匹配：" + button + "/"
                    + context.getOperationType());
        }
        List<BusinessObjectRef> objects = context.getObjects();
        if (objects == null || objects.isEmpty()) {
            throw new BusinessException("销售订单关闭管理缺少已授权的业务对象。");
        }
        BusinessObjectRef root = null;
        for (BusinessObjectRef ref : objects) {
            if (ref == null) {
                throw new BusinessException("销售订单关闭管理包含空对象引用。");
            }
            if (ref.getParentClientObjectId() == null) {
                if (root != null) {
                    throw new BusinessException("销售订单关闭管理只支持一张已授权的销售订单。");
                }
                if (!SalesOtherNodeScope.SALE_ORDER.equals(ref.getObjectType())) {
                    throw new BusinessException("销售订单关闭管理的对象类型不匹配。");
                }
                if (blank(ref.getObjectPk())) {
                    throw new BusinessException("销售订单关闭管理缺少已授权的订单主键。");
                }
                root = ref;
            }
        }
        if (root == null) {
            throw new BusinessException("销售订单关闭管理缺少已授权的订单根对象。");
        }
        List<String> linePks = new ArrayList<String>();
        if (kind.isBill()) {
            // 整单动作只接受一个根对象；出现行对象说明授权范围被篡改或选择发生变化。
            if (objects.size() != 1) {
                throw new BusinessException("整单动作不接受未授权的子对象。");
            }
        } else {
            for (BusinessObjectRef ref : objects) {
                if (ref.getParentClientObjectId() == null) {
                    continue;
                }
                if (!SalesOtherNodeScope.SALE_ORDER_LINE.equals(ref.getObjectType())) {
                    throw new BusinessException("行级动作的子对象类型不匹配。");
                }
                if (blank(ref.getObjectPk())) {
                    throw new BusinessException("行级动作缺少已授权的行主键。");
                }
                if (!root.getClientObjectId().equals(ref.getParentClientObjectId())) {
                    throw new BusinessException("行级动作的行不属于已授权订单。");
                }
                linePks.add(ref.getObjectPk());
            }
        }
        List<String> authorizedLines = linePks.isEmpty() ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(linePks));
        return new SaleCloseActionRoute(button, kind,
                SalesOtherNodeScope.scopeIdFor(SalesOtherNodeScope.CLOSE_NODE, button),
                root.getObjectPk(), authorizedLines);
    }

    /** 返回按钮到原生落点类型的只读映射，供预检脚本与离线测试逐条核对。 */
    public static Map<String, Kind> supportedActions() { return KINDS; }

    /** 真实按钮编码，日志与审计据此区分业务动作。 */
    public String getButton() { return button; }

    /** 中文按钮名，便于日志区分关闭与打开等动作。 */
    public String getButtonName() { return SalesOtherNodeScope.buttonName(button); }

    /** 映射登记的动作域。 */
    public String getOperationType() { return operationType; }

    /** 原生落点类型与粒度、返回类型。 */
    public Kind getKind() { return kind; }

    /** 正式清单范围 ID。 */
    public String getScopeId() { return scopeId; }

    /** 已授权的订单主键。 */
    public String getObjectPk() { return objectPk; }

    /** 行动作已授权的行主键集；整单动作与未携带行对象的行动作为空。 */
    public List<String> getLinePks() { return linePks; }

    /** 该按钮的服务端分支是否已完成受控接入并允许执行。 */
    public boolean isEnabled() {
        return SalesOtherNodeScope.isEnabled(SalesOtherNodeScope.CLOSE_NODE, button);
    }

    /** 未完成受控接入的按钮必须在执行前拒绝，不得由通用分支兜底。 */
    public void requireEnabled() throws BusinessException {
        if (!isEnabled()) {
            throw new BusinessException("销售订单关闭管理「" + getButtonName() + "」尚未接入受控执行，本批保持关闭。");
        }
    }

    /** 判定主键是否缺失或全为空白。 */
    private static boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
