package nc.vo.uapbd.qd.esign;

import java.io.Serializable;

/**
 * 发货单与销售发票 PF 流程动作结果：保留原生返回对象、真实持久化标识及删除事实。
 *
 * <p>原生返回的 VO 数组本身不证明业务成功；适配器必须按动作对应的持久化状态核对后构造本结果，
 * 由网关检查完整性并回填审计对象。子类或节点适配器可携带额外的行级证据，但不得省略真实主键。</p>
 */
public final class SalesPfActionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Serializable nativeResult;
    private final String primaryKey;
    private final String billCode;
    /** 删除动作是否已确认原单据失效（逻辑删除或物理不存在）。 */
    private final boolean deleted;

    public SalesPfActionResult(Serializable nativeResult, String primaryKey, String billCode, boolean deleted) {
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
