package nc.vo.ic.qd.esign;

import java.io.Serializable;

/** Native PF return and the identity verified after the save. */
public final class InventoryPfSaveResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Serializable nativeResult;
    private final String primaryKey;
    private final String billCode;

    public InventoryPfSaveResult(Serializable nativeResult, String primaryKey, String billCode) {
        this.nativeResult = nativeResult;
        this.primaryKey = primaryKey;
        this.billCode = billCode;
    }

    public Serializable getNativeResult() { return nativeResult; }
    public String getPrimaryKey() { return primaryKey; }
    public String getBillCode() { return billCode; }
}
