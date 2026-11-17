package nc.impl.uapbd.qd.esign;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.TransactionBoundaryProbe;
import nc.vo.pub.BusinessException;

/** Stop dispatching new units when the previous unit has no confirmed durable result. */
final class AssignmentDispatch {
    interface UnitAction {
        void apply(String objectId) throws BusinessException;
        void verify(String objectId) throws BusinessException;
        void unknown(String objectId) throws BusinessException;
    }
    private AssignmentDispatch() { }
    static void run(String requestId,List<String> objectIds,UnitAction action,TransactionBoundaryProbe boundary,Logger log) throws BusinessException {
        EsignValues.require(requestId!=null && objectIds!=null && !objectIds.isEmpty() && action!=null && boundary!=null && log!=null,
            "分配认领范围不完整。");
        Set<String> unique=new HashSet<String>();
        for(String id:objectIds)EsignValues.require(id!=null && !id.trim().isEmpty() && unique.add(id),"分配认领单元无效或重复。");
        boundary.assertNoTransaction();
        for(String id:objectIds) {
            try { action.apply(id);boundary.assertNoTransaction();action.verify(id);boundary.assertNoTransaction(); }
            catch(BusinessException | RuntimeException failure) {
                log.log(Level.WARNING,"Assignment unit requires reconciliation. Request "+requestId+", object "+id,failure);
                try { boundary.assertNoTransaction();action.unknown(id);boundary.assertNoTransaction(); }
                catch(BusinessException | RuntimeException recording) {
                    if(recording!=failure)failure.addSuppressed(recording);
                    if(failure instanceof BusinessException)throw (BusinessException)failure;
                    throw (RuntimeException)failure;
                }
                break;
            }
        }
    }
}
