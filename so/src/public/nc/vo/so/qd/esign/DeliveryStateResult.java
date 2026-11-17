package nc.vo.so.qd.esign;

import java.io.Serializable;

/**
 * 发货单直接服务动作（整单关闭/整单打开/出库行关闭/出库行打开）的结果。
 *
 * <p>保留原生 {@code DeliveryVO[]} 返回对象、真实持久化主键与真实单号，供网关核对业务对象身份。
 * 原生返回的对象数组本身不证明业务成功；适配器必须按主键核对原生命中结果覆盖了授权对象后才构造本结果。</p>
 */
public final class DeliveryStateResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Serializable nativeResult;
    private final String primaryKey;
    private final String billCode;

    public DeliveryStateResult(Serializable nativeResult, String primaryKey, String billCode) {
        this.nativeResult = nativeResult;
        this.primaryKey = primaryKey;
        this.billCode = billCode;
    }

    /** 返回原生命中服务结果，供客户端沿用原结果处理；可能为空。 */
    public Serializable getNativeResult() { return nativeResult; }

    /** 返回已授权且由原生命中结果覆盖的发货单真实主键。 */
    public String getPrimaryKey() { return primaryKey; }

    /** 返回原生命中结果或持久化表头中的真实单号。 */
    public String getBillCode() { return billCode; }
}
