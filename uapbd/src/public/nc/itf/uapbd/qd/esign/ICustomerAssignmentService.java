package nc.itf.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.CustomerAssignmentResult;

/** Candidate dispatcher: accepts only the persisted server-created request, never business data from a client. */
public interface ICustomerAssignmentService {
    CustomerAssignmentResult execute(String requestId) throws BusinessException;
    CustomerAssignmentResult getResult(String requestId) throws BusinessException;
}
