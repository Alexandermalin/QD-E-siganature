package nc.itf.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.MaterialCancelAssignmentResult;

/** Candidate dispatcher: accepts only the persisted server-created request, never business data from a client. */
public interface IMaterialCancelAssignmentService {
    MaterialCancelAssignmentResult execute(String requestId) throws BusinessException;
    MaterialCancelAssignmentResult getResult(String requestId) throws BusinessException;
}

