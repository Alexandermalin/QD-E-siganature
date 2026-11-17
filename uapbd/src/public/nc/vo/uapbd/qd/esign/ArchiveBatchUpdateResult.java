package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nc.vo.bd.errorlog.ErrLogReturnValue;

/** Native quick-batch result and independently reconciled per-object transaction outcomes. */
public final class ArchiveBatchUpdateResult implements Serializable {
    private static final long serialVersionUID=1L;
    private final ErrLogReturnValue nativeResult;private final List<ArchiveBatchResult.Unit> units;
    public ArchiveBatchUpdateResult(ErrLogReturnValue nativeResult,List<ArchiveBatchResult.Unit> units) {
        if(nativeResult==null || units==null) throw new IllegalArgumentException("Batch result and units are required");
        this.nativeResult=nativeResult;this.units=Collections.unmodifiableList(new ArrayList<ArchiveBatchResult.Unit>(units));
    }
    public ErrLogReturnValue getNativeResult() { return nativeResult; }
    public List<ArchiveBatchResult.Unit> getUnits() { return units; }
}
