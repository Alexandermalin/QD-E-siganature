package nc.ui.pp.supplierprice.qd.esign;

import java.util.List;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.ui.pubapp.uif2app.model.BatchBillTableModel;
import nc.ui.uif2.UIState;
import nc.vo.pub.BusinessException;
import nc.vo.purp.qd.esign.SupplierPriceEditDeleteCommand;

public final class QdSupplierPriceModel extends BatchBillTableModel {
    private static final long serialVersionUID = 1L;

    @Override protected void processDelLineInEdit(Integer[] indexes, List<Object> removed) {
        try {
            SupplierPriceActionScope scope = SupplierPriceActionScope.forService(getContext());
            if (scope == null || !"Delete".equals(scope.button)) {
                super.processDelLineInEdit(indexes, removed);
                return;
            }
            if (!scope.editorOnly) throw new BusinessException("价目删除的编辑状态已变化，请重新操作。");
            stageAuthorizedDelete(scope, indexes, removed);
        } catch (BusinessException ex) {
            throw new BusinessExceptionAdapter(ex);
        } catch (Exception ex) {
            throw new BusinessExceptionAdapter(new BusinessException("无法完成价目表暂存删除。", ex));
        }
    }

    private void stageAuthorizedDelete(SupplierPriceActionScope scope, Integer[] indexes, List<Object> removed)
            throws Exception {
        Object[] rows = new Object[indexes.length];
        for (int i = 0; i < indexes.length; i++) rows[i] = getRow(indexes[i]);
        PriceBatchRequest request = PriceBatchRequest.forEditDelete(rows);
        if (scope.nativeLargeBatch(request.context)) {
            super.processDelLineInEdit(indexes, removed);
            return;
        }
        SupplierPriceActionScope.Grant grant = scope.authorize(request.context);
        Throwable failure = null;
        try {
            if (getUiState() == UIState.NOT_EDIT) throw new BusinessException("价目表已退出编辑，请重新操作。");
            for (int i = 0; i < indexes.length; i++) {
                if (indexes[i] >= getRowCount() || getRow(indexes[i]) != rows[i]) {
                    throw new BusinessException("价目表选中行已变化，请重新删除。");
                }
            }
            if (grant.isControlled()) {
                // Consume the authorization before the native model mutates its pending rows.
                grant.execute(new SupplierPriceEditDeleteCommand(request.context.getObjects()));
            }
            super.processDelLineInEdit(indexes, removed);
        } catch (Exception ex) {
            failure = ex;
            throw ex;
        } catch (Error ex) {
            failure = ex;
            throw ex;
        } finally {
            grant.finish(failure);
        }
    }
}
