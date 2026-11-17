package nc.vo.pu.qd.esign;

import java.io.Serializable;
import nc.vo.pu.m21.context.OrderContext;
import nc.vo.pu.m21.entity.OrderVO;

/** 采购订单原生输入载荷，区分保存与删除上下文并保留数组副本；禁止存放认证信息。 */
public final class PurchaseOrderOperationCommand implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;
    /** OBJECT_TYPE：当前类型化命令允许的固定业务对象类型。 */
    public static final String OBJECT_TYPE = "PU_PURCHASE_ORDER";
    /**
     * INSERT：新增业务动作标识。
     * UPDATE：修改业务动作标识。
     * DELETE：删除业务动作标识。
     */
    public static final String INSERT = "INSERT", UPDATE = "UPDATE", DELETE = "DELETE";
    /** bills：传给原生业务服务的待处理单据数组。 */
    private final OrderVO[] bills;
    /** saveContext：原生采购订单保存上下文。 */
    private final OrderContext saveContext;
    /** deleteContexts：与删除单据对应的原生删除上下文数组。 */
    private final OrderContext[] deleteContexts;

    /** 复制采购单据及删除上下文数组，保存原生保存上下文；由工厂方法区分保存或删除载荷。 */
    private PurchaseOrderOperationCommand(OrderVO[] bills, OrderContext saveContext,
            OrderContext[] deleteContexts) {
        this.bills = bills == null ? null : bills.clone();
        this.saveContext = saveContext;
        this.deleteContexts = deleteContexts == null ? null : deleteContexts.clone();
    }
    /** 构造采购订单保存载荷，复制单据数组并保留原生保存上下文。 */
    public static PurchaseOrderOperationCommand forSave(OrderVO[] bills, OrderContext context) {
        return new PurchaseOrderOperationCommand(bills, context, null);
    }
    /** 构造采购订单删除载荷，保留与单据数组相对应的删除上下文。 */
    public static PurchaseOrderOperationCommand forDelete(OrderVO[] bills, OrderContext[] contexts) {
        return new PurchaseOrderOperationCommand(bills, null, contexts);
    }
    /** 返回传给原生业务服务的待处理单据数组。 */
    public OrderVO[] getBills() { return bills == null ? null : bills.clone(); }
    /** 返回原生采购订单保存上下文。 */
    public OrderContext getSaveContext() { return saveContext; }
    /** 返回与删除单据对应的原生删除上下文数组。 */
    public OrderContext[] getDeleteContexts() {
        return deleteContexts == null ? null : deleteContexts.clone();
    }
}
