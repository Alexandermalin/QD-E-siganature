package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nc.vo.bd.errorlog.ErrLogReturnValue;

/** Organization-page result; null nativeResult means no page rows were sent to NC. */
public final class ArchiveOrgBatchUpdateResult implements Serializable {
    private static final long serialVersionUID=1L;
    private final ErrLogReturnValue nativeResult;private final List<ArchiveBatchResult.Unit> units;private final int pageCount;
    public ArchiveOrgBatchUpdateResult(ErrLogReturnValue nativeResult,List<ArchiveBatchResult.Unit> units,int pageCount) {
        if(units==null || units.isEmpty() || units.contains(null) || pageCount<0
                || pageCount>PartnerBatchDraft.MAX_SINGLE_TRANSACTION_PAGE_ROWS
                || (pageCount==0?nativeResult!=null:nativeResult==null || nativeResult.getErrLogResult()==null
                    || nativeResult.getTotalNum()!=pageCount))
            throw new IllegalArgumentException("Organization batch outcomes or native page count are invalid");
        this.nativeResult=nativeResult;this.units=Collections.unmodifiableList(new ArrayList<ArchiveBatchResult.Unit>(units));
        this.pageCount=pageCount;
    }
    public ErrLogReturnValue getNativeResult() { return nativeResult; }
    public List<ArchiveBatchResult.Unit> getUnits() { return units; }
    public int getPageCount() { return pageCount; }
}
