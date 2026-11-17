package nc.vo.so.qd.esign;

import java.io.Serializable;
import nc.vo.so.m4331.entity.DeliveryVO;
import nc.vo.so.pub.SOParameterVO;

/**
 * 发货单维护（节点 40060402）直接服务动作的类型化命令。
 *
 * <p>只承载原生客户端在用户点击后已经产出的数据：当前单张发货单（表头+全部表体）、卡片表格的
 * 选中行下标、以及整单动作直接服务的原生参数载体 {@link SOParameterVO}。所有数组与标记以副本隔离，
 * 服务端按固定节点白名单重新核对主键、组织、行下标与行主键后才委托
 * {@code nc.itf.so.m4331.IDeliveryAssitFunc}，不接受任意服务名、SQL 或流程载荷。</p>
 *
 * <p>PF 流程动作（保存/删除/提交/收回/审批/取消审批）使用共享的
 * {@code nc.vo.uapbd.qd.esign.SalesPfActionCommand}，本类不重复定义。</p>
 */
public final class DeliveryCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 整单/行动作应用的当前单张发货单：表头加全部表体行。 */
    private final DeliveryVO[] bills;
    /** 行动作（出库关闭/打开）使用的卡片表格行下标；整单动作必须为空。 */
    private final int[] rowIndexes;
    /** 直接服务动作的原生参数载体；整单关闭只使用 {@code vos} 与空的业务检查标记。 */
    private final SOParameterVO[] parameters;
    /** 单条业务检查继续标记的键；值为空表示本次没有继续决定。 */
    private final String businessCheckCode;
    /** 单条业务检查继续标记的值；false 表示用户已确认跳过该检查。 */
    private final Boolean businessCheckValue;

    /**
     * 构造类型化命令；数组参数复制后只读保存，调用方后续修改不影响已构造的命令。
     */
    public DeliveryCommand(DeliveryVO[] bills, int[] rowIndexes, SOParameterVO[] parameters,
            String businessCheckCode, Boolean businessCheckValue) {
        this.bills = bills == null ? null : bills.clone();
        this.rowIndexes = rowIndexes == null ? null : rowIndexes.clone();
        this.parameters = parameters == null ? null : parameters.clone();
        this.businessCheckCode = businessCheckCode;
        this.businessCheckValue = businessCheckValue;
    }

    /** 返回待处理发货单副本，保留原生 VO 的表头与全部表体。 */
    public DeliveryVO[] getBills() { return bills == null ? null : bills.clone(); }

    /** 返回卡片表格行下标副本；整单动作为空。 */
    public int[] getRowIndexes() { return rowIndexes == null ? null : rowIndexes.clone(); }

    /** 返回原生参数载体副本；行动作必须为空。 */
    public SOParameterVO[] getParameters() { return parameters == null ? null : parameters.clone(); }

    /** 返回业务检查继续标记的键；本批关闭的打开动作与未确认场景必须为空。 */
    public String getBusinessCheckCode() { return businessCheckCode; }

    /** 返回业务检查继续标记的值；false 表示用户已确认跳过对应检查。 */
    public Boolean getBusinessCheckValue() { return businessCheckValue; }
}
