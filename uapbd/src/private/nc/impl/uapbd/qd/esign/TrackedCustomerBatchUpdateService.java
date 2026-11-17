package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.core.NativeBatchFailureTracker;
import nc.impl.bd.cust.baseinfo.CustBaseInfoBatchUpdateServiceImpl;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.pub.BusinessException;

/** Candidate native customer batch service that preserves per-object filtering evidence. */
final class TrackedCustomerBatchUpdateService extends CustBaseInfoBatchUpdateServiceImpl {
    private final NativeBatchFailureTracker tracker=new NativeBatchFailureTracker();

    @Override protected List<CustomerVO> filterPermissionVOs(List<CustomerVO> rows,String[] orgs) throws BusinessException {
        Set<String> before=NativeBatchFailureTracker.snapshot(rows);
        List<CustomerVO> result=super.filterPermissionVOs(rows,orgs);
        tracker.filtered("PERMISSION",before,result);return result;
    }
    @Override protected List<CustomerVO> validateVOs(List<CustomerVO> rows) throws BusinessException {
        Set<String> before=NativeBatchFailureTracker.snapshot(rows);
        List<CustomerVO> result=super.validateVOs(rows);
        tracker.filtered("VALIDATION",before,result);return result;
    }
    @Override protected CustomerVO[] checkBeforeUpdate(List<CustomerVO> rows) throws BusinessException {
        Set<String> before=NativeBatchFailureTracker.snapshot(rows);
        CustomerVO[] result=super.checkBeforeUpdate(rows);
        tracker.filtered("BEFORE_EVENT",before,result==null?Collections.<CustomerVO>emptyList():Arrays.asList(result));return result;
    }
    Map<String,String> excluded() { return tracker.excluded(); }
}
