package nc.ui.pp.supplierprice.qd.esign;

import java.awt.event.ActionEvent;
import nc.ui.pp.supplierprice.action.BatchDelAction;
import nc.ui.uif2.UIState;
import nc.vo.pub.BusinessException;
import nc.vo.uif2.LoginContext;

public final class QdSupplierPriceDeleteAction extends BatchDelAction {
    private static final long serialVersionUID = 1L;

    @Override public void doAction(ActionEvent event) throws Exception {
        LoginContext login = getModel().getContext();
        if (!SupplierPriceActionScope.isTarget(login)) {
            super.doAction(event);
            return;
        }
        if (!(getModel() instanceof QdSupplierPriceModel)
                || !(getModel().getService() instanceof QdSupplierPriceModelService)) {
            throw new BusinessException("供应商价目表删除未加载签章执行服务。");
        }
        boolean editing = getModel().getUiState() != UIState.NOT_EDIT;
        try (SupplierPriceActionScope scope = SupplierPriceActionScope.open(login, "Delete", editing)) {
            super.doAction(event);
        }
    }
}
