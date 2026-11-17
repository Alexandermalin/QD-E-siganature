package nc.ui.pu.m25.action;

import nc.bs.framework.common.NCLocator;
import nc.itf.pubapp.pub.smart.IBillQueryService;
import nc.pu.m25.logger.ItfLogger;
import nc.ui.pu.m25.model.InvoiceBillManageModel;
import nc.ui.pub.beans.MessageDialog;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pu.m25.pub.InvoicePlanPriceSetter;
import nc.vo.pub.BusinessException;

final class InvoiceRelationRefresh {
    private InvoiceRelationRefresh() { }

    static void afterCommit(InvoiceBillManageModel model, InvoiceVO invoice, String operation) {
        try {
            IBillQueryService query = NCLocator.getInstance().lookup(IBillQueryService.class);
            InvoiceVO persisted = query.querySingleBillByPk(invoice.getClass(), invoice.getPrimaryKey());
            if (persisted == null) {
                throw new BusinessException("未查询到采购发票 " + invoice.getPrimaryKey());
            }
            new InvoicePlanPriceSetter().setPlanPrice(new InvoiceVO[] { persisted });
            model.directlyUpdate(persisted);
        } catch (Exception failure) {
            String message = "采购发票" + operation + "已完成，刷新界面失败，请手动刷新。";
            ItfLogger.error(message, failure);
            MessageDialog.showHintDlg(model.getContext().getEntranceUI(), "提示", message);
        }
    }
}
