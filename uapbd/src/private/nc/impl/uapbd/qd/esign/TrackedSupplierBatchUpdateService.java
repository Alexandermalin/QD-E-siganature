package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.core.NativeBatchFailureTracker;
import nc.impl.bd.supplier.baseinfo.SupBaseInfoBatchUpdateForNewTransServiceImpl;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;

/** Candidate for the actual NC quick-batch supplier commit service, retaining its native validators. */
final class TrackedSupplierBatchUpdateService extends SupBaseInfoBatchUpdateForNewTransServiceImpl {
    private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();

    @Override protected List<SupplierVO> filterPermissionVOs(List<SupplierVO> rows,String[] orgs) throws BusinessException {
        Set<String> before=NativeBatchFailureTracker.snapshot(rows);
        List<SupplierVO> result=super.filterPermissionVOs(rows,orgs);
        tracker.filtered("PERMISSION",before,result);return result;
    }
    @Override protected List<SupplierVO> validateVOs(List<SupplierVO> rows) throws BusinessException {
        Set<String> before=NativeBatchFailureTracker.snapshot(rows);
        List<SupplierVO> result=super.validateVOs(rows);
        tracker.filtered("VALIDATION",before,result);return result;
    }
    @Override protected SupplierVO[] checkBeforeUpdate(List<SupplierVO> rows) throws BusinessException {
        Set<String> before=NativeBatchFailureTracker.snapshot(rows);
        SupplierVO[] result=super.checkBeforeUpdate(rows);
        tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<SupplierVO>emptyList():Arrays.asList(result));return result;
    }
    Map<String,String> excluded() { return tracker.excluded(); }
}
