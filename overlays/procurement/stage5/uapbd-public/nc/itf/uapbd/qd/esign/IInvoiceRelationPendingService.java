package nc.itf.uapbd.qd.esign;

import nc.vo.pub.BusinessException;

public interface IInvoiceRelationPendingService {
    void assertClear(String invoicePk) throws BusinessException;
}
