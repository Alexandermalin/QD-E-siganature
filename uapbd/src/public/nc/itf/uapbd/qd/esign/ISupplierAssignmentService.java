package nc.itf.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.SupplierAssignmentResult;

/** Candidate dispatcher: accepts only the persisted server-created request, never business data from a client. */
public interface ISupplierAssignmentService {
    SupplierAssignmentResult execute(String requestId) throws BusinessException;
    SupplierAssignmentResult getResult(String requestId) throws BusinessException;
}
