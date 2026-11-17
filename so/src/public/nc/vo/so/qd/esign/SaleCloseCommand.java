package nc.vo.so.qd.esign;

import java.io.Serializable;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.m30.entity.SaleOrderViewVO;

/**
 * 销售订单关闭管理（节点 40060303，正式清单第 217–228 行）的类型化命令。
 *
 * <p>整单动作（整单关闭 / 整单打开）只携带一张订单的完整聚合：表头加该单全部表体行；
 * 行动作（行关闭 / 行打开 / 发货、出库、开票、结算的关闭与打开）只携带一行视图。
 * 二者互斥：不适用的字段必须为 null，适配器据此拒绝跨粒度的载荷。</p>
 *
 * <p>原生 12 项进入动作后首先采集「关闭/打开原因」，逐行写入
 * {@code SaleOrderBVO.setVclosereason(String)} 后随单据提交；因此命令必须携带原因，
 * 且适配器会核对命令中的每一行原因与之一致。签名事由由 {@code OperationContext} 单独传递，
 * 二者不混用。</p>
 *
 * <p>本命令不携带方法名、服务名、SQL 或流程参数等可控字符串：原生落点只由服务端固定路由决定。</p>
 */
public final class SaleCloseCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 整单动作使用的订单聚合（表头加该单全部行）；行动作必须为 null。 */
    private final SaleOrderVO[] bills;
    /** 行动作使用的单行视图；整单动作必须为 null。 */
    private final SaleOrderViewVO[] rows;
    /** 由原生输入框取得的关闭/打开原因，必须已逐行写入受影响的 {@code vclosereason}。 */
    private final String closeReason;

    public SaleCloseCommand(SaleOrderVO[] bills, SaleOrderViewVO[] rows, String closeReason) {
        this.bills = bills == null ? null : bills.clone();
        this.rows = rows == null ? null : rows.clone();
        this.closeReason = closeReason;
    }

    /** 返回待处理订单聚合副本，保留原生命中所需的表头与全部表体行。 */
    public SaleOrderVO[] getBills() { return bills == null ? null : bills.clone(); }

    /** 返回待处理单行视图副本，保留原生命中所需的行主键与行原因。 */
    public SaleOrderViewVO[] getRows() { return rows == null ? null : rows.clone(); }

    /** 返回已逐行写入单据的关闭/打开原因。 */
    public String getCloseReason() { return closeReason; }
}
