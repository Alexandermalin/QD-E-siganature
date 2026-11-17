package nc.ui.uapbd.qd.esign;

import java.util.Map;
import nc.bs.framework.common.NCLocator;
import nc.itf.bd.material.baseinfo.IMaterialBaseInfoExtendService;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.bd.material.baseinfo.model.MaterialBaseInfoModelService;
import nc.ui.bd.pub.extend.PrivateServiceContext;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.MaterialCommand;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;

/** 保留原生模型的扩展数据组装，只替换已授权 Save 动作的最终远程调用。 */
final class QdMaterialModelService extends MaterialBaseInfoModelService implements AuthorizedRequestConsumer {
    private final ThreadLocal<OperationContext> saving = new ThreadLocal<OperationContext>();
    private final IMaterialBaseInfoExtendService controlledService = new ControlledSave();
    private OperationContext authorized;
    private String dispatched;
    private OperationContext bypass;

    @Override
    public Object insert(Object draft) throws Exception {
        if (draft instanceof MaterialVO && getModel().getCopyPk() != null
                && getModel().getCreateVersionPk() == null) {
            // 卡片可能带回旧物料主键。原生复制通过独立的 copyPk 参数识别来源。
            ((MaterialVO) draft).setPk_material(null);
        }
        return super.insert(draft);
    }

    @Override
    public boolean isAvailable(OperationContext context) {
        return MaterialCommand.supportsSave(context) && getModel().getService() == this;
    }

    @Override
    public boolean bind(OperationContext context) {
        if (authorized != null || context.getRequestId() == null || !isAvailable(context)) {
            return false;
        }
        authorized = context;
        return true;
    }

    @Override
    public boolean wasDispatched(String requestId) {
        return requestId != null && requestId.equals(dispatched);
    }

    @Override
    public void release(String requestId) {
        if (authorized != null && authorized.getRequestId().equals(requestId)) {
            authorized = null;
            dispatched = null;
        }
    }

    void acceptPreparation(OperationContext draft) {
        // 公共拦截器只在服务端返回不受控策略，或成功完成授权绑定时放行。
        bypass = authorized == null ? draft : null;
    }

    void beginSave(Object draft, String operation) throws BusinessException {
        if (saving.get() != null) {
            throw new BusinessException("物料保存请求正在处理。");
        }
        OperationContext preparation = authorized == null ? bypass : authorized;
        if (preparation == null) {
            throw new BusinessException("物料保存缺少签名服务的放行结果，请重新保存。");
        }
        if (!(draft instanceof MaterialVO) || !operation.equals(preparation.getOperationType())
                || !MaterialCommand.matchesDraft(preparation, (MaterialVO) draft)) {
            throw new BusinessException("保存时的物料组织或对象与签名策略核对结果不一致，请重新保存。");
        }
        if (authorized != null) {
            saving.set(authorized);
        }
    }

    void endSave() {
        saving.remove();
    }

    void finishAttempt() {
        endSave();
        authorized = null;
        dispatched = null;
        bypass = null;
    }

    @Override
    public IMaterialBaseInfoExtendService getBaseInfoExtendService() {
        return saving.get() == null ? super.getBaseInfoExtendService() : controlledService;
    }

    private final class ControlledSave implements IMaterialBaseInfoExtendService {
        @Override
        public MaterialVO insertMaterialWithExtendVO(MaterialVO draft, Map<String, Object> values,
                PrivateServiceContext context) throws BusinessException {
            return executeSave("INSERT", draft, values, context)[0];
        }

        private MaterialVO[] executeSave(String operation, MaterialVO draft, Map<String, Object> values,
                PrivateServiceContext context) throws BusinessException {
            OperationContext request = saving.get();
            if (request == null || request != authorized || !operation.equals(request.getOperationType())
                    || wasDispatched(request.getRequestId())) {
                throw new BusinessException("物料保存缺少有效的一次性授权，请重新签名。");
            }
            if (!MaterialCommand.matchesDraft(request, draft)) {
                throw new BusinessException("保存时的物料组织或对象与授权范围不一致。");
            }
            MaterialCommand command = new MaterialCommand(draft, values, context);
            IOperationService gateway = NCLocator.getInstance().lookup(IOperationService.class);
            dispatched = request.getRequestId();
            OperationResponse result = gateway.execute(dispatched, command);
            if (result == null || !dispatched.equals(result.getRequestId()) || result.isReplayed()
                    || !(result.getBusinessResult() instanceof MaterialVO[])) {
                throw unknownResult();
            }
            MaterialVO[] saved = (MaterialVO[]) result.getBusinessResult();
            if (saved.length != 1 || saved[0] == null || saved[0].getPrimaryKey() == null) {
                throw unknownResult();
            }
            return saved;
        }

        @Override
        public ErrLogReturnValue createMaterialVersionWithExtendVO(MaterialVO draft, String key,
                Map<String, Object> values, PrivateServiceContext context) throws BusinessException {
            throw unsupportedOperation();
        }

        @Override
        public ErrLogReturnValue copyInsertMaterialWithExtendVO(MaterialVO draft, String key,
                Map<String, Object> values, PrivateServiceContext context) throws BusinessException {
            throw unsupportedOperation();
        }

        @Override
        public MaterialVO[] updateMaterialWithExtendVO(MaterialVO draft, Map<String, Object> values,
                PrivateServiceContext context) throws BusinessException {
            return executeSave("UPDATE", draft, values, context);
        }

        @Override
        public void deleteMaterialWithExtendVO(MaterialVO draft, Map<String, Object> values,
                PrivateServiceContext context) throws BusinessException {
            throw unsupportedOperation();
        }

        private BusinessException unsupportedOperation() {
            return new BusinessException("本次授权只用于物料普通保存。");
        }

        private BusinessException unknownResult() {
            return new BusinessException("无法在页面确认物料保存结果，请重新查询物料后再处理。");
        }
    }
}
