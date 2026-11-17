package nc.itf.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.MaterialAssignmentResult;

public interface IMaterialAssignmentService {
    MaterialAssignmentResult execute(String requestId) throws BusinessException;
    MaterialAssignmentResult getResult(String requestId) throws BusinessException;
}
