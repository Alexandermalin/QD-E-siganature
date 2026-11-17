package nc.ui.pp.supplierprice.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.pp.supplierprice.action.BatchSupplierPriceSaveAction;
import nc.vo.pub.BusinessException;
import nc.vo.uif2.LoginContext;

public final class QdSupplierPriceSaveAction extends BatchSupplierPriceSaveAction {
    private static final long serialVersionUID = 1L;

    @Override public void doAction(ActionEvent event) throws Exception {
        LoginContext login = getModel().getContext();
        if (event == null || !SupplierPriceActionScope.isTarget(login)) {
            super.doAction(event);
            return;
        }
        if (!(getModel().getService() instanceof QdSupplierPriceModelService)) {
            throw new BusinessException("供应商价目表保存未加载签章执行服务。");
        }
        try (SupplierPriceActionScope scope = SupplierPriceActionScope.open(login, "Save", false)) {
            super.doAction(event);
        }
    }
}
