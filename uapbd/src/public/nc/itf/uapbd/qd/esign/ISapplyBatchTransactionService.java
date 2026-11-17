package nc.itf.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.SapplyBatchOutcome;

/** Local CMT boundaries for one authorized outbound-request batch. */
public interface ISapplyBatchTransactionService {
    String claim_RequiresNew(String requestId, String nativeAction, String[] billPks, String[] signedApprovalIds)
            throws BusinessException;

    void finish_RequiresNew(String requestId, String executionToken, String[] nativeRejectedPks,
            String[] precheckSkippedPks) throws BusinessException;

    SapplyBatchOutcome verifyFinished_RequiresNew(String requestId) throws BusinessException;
}
