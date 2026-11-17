package nc.vo.so.qd.esign;

import java.io.Serializable;

/** 原生 PF 流程动作结果：保留原生返回对象、真实持久化标识及删除事实，供网关核验并回填审计。 */
public final class SaleOrderPfActionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Serializable nativeResult;
    private final String primaryKey;
    private final String billCode;
    /** 删除动作是否已确认原单据失效（逻辑删除或物理不存在）。 */
    private final boolean deleted;

    public SaleOrderPfActionResult(Serializable nativeResult, String primaryKey, String billCode, boolean deleted) {
        this.nativeResult = nativeResult;
        this.primaryKey = primaryKey;
        this.billCode = billCode;
        this.deleted = deleted;
    }

    public Serializable getNativeResult() { return nativeResult; }
    public String getPrimaryKey() { return primaryKey; }
    public String getBillCode() { return billCode; }
    public boolean isDeleted() { return deleted; }
}
