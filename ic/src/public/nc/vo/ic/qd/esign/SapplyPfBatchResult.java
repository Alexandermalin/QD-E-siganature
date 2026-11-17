package nc.vo.ic.qd.esign;

import java.io.Serializable;
import nc.vo.uap.pf.PfProcessBatchRetObject;
import nc.vo.uapbd.qd.esign.SapplyBatchOutcome;

/** Native display data and independently confirmed per-bill receipts. */
public final class SapplyPfBatchResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final SapplyBatchOutcome outcome;
    private final PfProcessBatchRetObject nativeResult;
    private final boolean nativeBatchInterrupted;

    public SapplyPfBatchResult(SapplyBatchOutcome outcome, PfProcessBatchRetObject nativeResult,
            boolean nativeBatchInterrupted) {
        this.outcome = outcome;
        this.nativeResult = nativeResult;
        this.nativeBatchInterrupted = nativeBatchInterrupted;
    }

    public SapplyBatchOutcome getOutcome() { return outcome; }
    public PfProcessBatchRetObject getNativeResult() { return nativeResult; }
    public boolean isNativeBatchInterrupted() { return nativeBatchInterrupted; }
}
