package nc.impl.uapbd.qd.esign;

import nc.bs.dao.DAOException;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.IInvoiceRelationJournal;
import nc.itf.uapbd.qd.esign.IInvoiceRelationPendingService;
import nc.vo.pub.BusinessException;

public final class InvoiceRelationPendingServiceImpl implements IInvoiceRelationPendingService {
    private static final String[] INVOICE_BUTTONS = {
        "RelevanceInvoice", "UnRelevanceInvoice", "SendToAP", "CancelToAP"
    };

    @Override
    public void assertClear(String invoicePk) throws BusinessException {
        try {
            ServerIdentity actor = ServerIdentity.current();
            String org = InvoiceRelationJournalImpl.invoiceOrganization(actor, invoicePk);
            if (!canAct(actor, org)) throw new BusinessException("无法核对采购发票权限。");
            NCLocator.getInstance().lookup(IInvoiceRelationJournal.class).assertClear(invoicePk);
        } catch (DAOException e) {
            throw new BusinessException("无法查询数电发票核对状态，请联系管理员。");
        }
    }

    private static boolean canAct(ServerIdentity actor, String org) throws BusinessException {
        for (String button : INVOICE_BUTTONS) {
            if (EsignPermission.authorizedOrgs(actor, InvoiceRelationJournalImpl.INVOICE_NODE, button, false)
                    .contains(org)) return true;
        }
        return false;
    }
}
