package nc.ui.pp.supplierprice.qd.esign;

import nc.ui.pp.supplierprice.model.SupplierPriceModelService;
import nc.vo.bd.meta.BatchOperateVO;
import nc.vo.pub.BusinessException;
import nc.vo.purp.qd.esign.SupplierPriceBatchCommand;

public final class QdSupplierPriceModelService extends SupplierPriceModelService {
    private static final long serialVersionUID = 1L;

    @Override public BatchOperateVO batchSave(BatchOperateVO batch) throws Exception {
        SupplierPriceActionScope scope = SupplierPriceActionScope.forService(getContext());
        if (scope == null || !PriceBatchRequest.hasChanges(batch)) return super.batchSave(batch);
        if (scope.editorOnly) throw new BusinessException("编辑态删除只能暂存，价目批次未提交。");
        PriceBatchRequest request = PriceBatchRequest.forBatch(scope.button, batch);
        if (scope.nativeLargeBatch(request.context)) return super.batchSave(batch);
        SupplierPriceActionScope.Grant grant = scope.authorize(request.context);
        Throwable failure = null;
        try {
            if (!grant.isControlled()) return super.batchSave(batch);
            Object result = grant.execute(new SupplierPriceBatchCommand(batch, request.addClientIds)).getBusinessResult();
            if (!(result instanceof BatchOperateVO)) {
                throw new BusinessException("价目批次未返回原生回显结果，请先刷新核对。");
            }
            return (BatchOperateVO) result;
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
