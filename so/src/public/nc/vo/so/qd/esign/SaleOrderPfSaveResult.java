package nc.vo.so.qd.esign;

import java.io.Serializable;

/** Native PF result plus the persisted identity used by the audit gateway. */
public final class SaleOrderPfSaveResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Serializable nativeResult;
    private final String primaryKey;
    private final String billCode;

    public SaleOrderPfSaveResult(Serializable nativeResult, String primaryKey, String billCode) {
        this.nativeResult = nativeResult;
        this.primaryKey = primaryKey;
        this.billCode = billCode;
    }
    public Serializable getNativeResult() { return nativeResult; }
    public String getPrimaryKey() { return primaryKey; }
    public String getBillCode() { return billCode; }
}
