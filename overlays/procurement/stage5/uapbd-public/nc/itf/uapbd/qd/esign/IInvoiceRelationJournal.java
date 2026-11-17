package nc.itf.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;

public interface IInvoiceRelationJournal {
    String EXTERNAL_ACCEPTED = "EXTERNAL_ACCEPTED";
    String EXTERNAL_REJECTED = "EXTERNAL_REJECTED";
    String EXTERNAL_UNKNOWN = "EXTERNAL_UNKNOWN";

    void start_RequiresNew(OperationContext context, String invoicePk, String externalNumbers) throws BusinessException;

    void checkpoint_RequiresNew(String requestId, String phase) throws BusinessException;

    void finish(String requestId) throws BusinessException;

    void assertClear(String invoicePk) throws BusinessException;
}
