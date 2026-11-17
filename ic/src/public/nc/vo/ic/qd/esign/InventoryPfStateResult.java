package nc.vo.ic.qd.esign;

import java.io.Serializable;

/** The native return value and the verified identity of the affected inventory bill. */
public final class InventoryPfStateResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Serializable nativeResult;
    private final String primaryKey, billCode;
    private final boolean deleted;

    public InventoryPfStateResult(Serializable nativeResult, String primaryKey, String billCode, boolean deleted) {
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
