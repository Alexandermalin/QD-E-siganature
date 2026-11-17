package nc.ui.uapbd.qd.esign;

import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.uif2.model.IAppModelService;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.MaterialOrgInfoCommand;
import nc.vo.uapbd.qd.esign.MaterialOrgPage;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uif2.LoginContext;

/** Retains the native page model and delegates only an authorized update to the operation gateway. */
public final class QdMaterialOrgInfoModelService implements IAppModelService, AuthorizedRequestConsumer {
    private final IAppModelService nativeService;
    private final String button;
    private final String objectType;
    private final Class<? extends SuperVO> rowClass;
    private final ThreadLocal<OperationContext> saving = new ThreadLocal<OperationContext>();
    private OperationContext authorized;
    private String dispatched;
    private OperationContext prepared;

    public QdMaterialOrgInfoModelService(IAppModelService nativeService, String button) {
        this.nativeService = nativeService;
        this.button = button;
        MaterialOrgPage page = MaterialOrgPage.forButton(button);
        if (page == null) {
            throw new IllegalArgumentException("Unexpected material page save action");
        }
        objectType = page.domain;
        rowClass = page.type;
    }

    String getButton() { return button; }
    String getObjectType() { return objectType; }

    SuperVO requireRow(Object value) throws BusinessException {
        if (!rowClass.isInstance(value) || ((SuperVO) value).getPrimaryKey() == null) {
            throw new BusinessException("无法确定待保存的物料页签数据行。");
        }
        return rowClass.cast(value);
    }

    @Override
    public boolean isAvailable(OperationContext context) {
        if(context == null || !("10140MAG".equals(context.getNodeCode()) || "10140MAO".equals(context.getNodeCode())) || !button.equals(context.getButtonCode())
            || !"UPDATE".equals(context.getOperationType()) || context.getGroupId() == null || context.getOrgId() == null
            || context.getObjects().isEmpty()) return false;
        if(!"PrSave".equals(button) && context.getObjects().size()!=1) return false;
        nc.vo.uapbd.qd.esign.BusinessObjectRef root=context.getObjects().get(0);
        if(!objectType.equals(root.getObjectType()) || root.getObjectPk()==null || root.getParentClientObjectId()!=null) return false;
        for(int index=1;index<context.getObjects().size();index++) {
            nc.vo.uapbd.qd.esign.BusinessObjectRef ref=context.getObjects().get(index);
            if(!"PrSave".equals(button) || !objectType.equals(ref.getObjectType()) || ref.getObjectPk()==null
                    || !root.getClientObjectId().equals(ref.getParentClientObjectId())) return false;
        }
        return true;
    }

    @Override
    public boolean bind(OperationContext context) {
        if (authorized != null || !isAvailable(context) || context.getRequestId() == null) {
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

    void acceptPreparation(OperationContext context) {
        prepared = authorized == null ? context : authorized;
    }

    void beginSave(Object draft) throws BusinessException {
        if (!isAvailable(prepared) || saving.get() != null || !sameRow(prepared, requireRow(draft))) {
            throw new BusinessException("物料页签保存数据行与授权不一致，请重新发起保存。");
        }
        saving.set(prepared);
    }

    void endSave() { saving.remove(); }

    void finishAttempt() {
        endSave();
        prepared = null;
        authorized = null;
        dispatched = null;
    }

    @Override
    public Object update(Object draft) throws Exception {
        OperationContext context = saving.get();
        if (context == null) {
            return nativeService.update(draft);
        }
        SuperVO row = requireRow(draft);
        if (context != prepared || !sameRow(context, row)) {
            throw new BusinessException("物料页签保存数据行已变化，请重新发起保存。");
        }
        String requestId = context.getRequestId();
        if (requestId == null) {
            return nativeService.update(draft);
        }
        if (context != authorized || wasDispatched(requestId)) {
            throw new BusinessException("物料页签保存缺少有效的一次性授权。");
        }
        IOperationService gateway = NCLocator.getInstance().lookup(IOperationService.class);
        dispatched = requestId;
        OperationResponse result = gateway.execute(requestId, new MaterialOrgInfoCommand(row));
        if (result == null || !requestId.equals(result.getRequestId()) || result.isReplayed()
                || !rowClass.isInstance(result.getBusinessResult())
                || !sameRow(context, rowClass.cast(result.getBusinessResult()))) {
            throw new BusinessException("无法确认物料页签保存结果，请重新查询后核对。");
        }
        return result.getBusinessResult();
    }

    private static boolean sameRow(OperationContext context, SuperVO row) {
        return context.getObjects().get(0).getObjectPk().equals(row.getPrimaryKey())
            && context.getGroupId().equals(row.getAttributeValue("pk_group"));
    }

    @Override
    public Object insert(Object value) throws Exception { return nativeService.insert(value); }

    @Override
    public void delete(Object value) throws Exception { nativeService.delete(value); }

    @Override
    public Object[] queryByDataVisibilitySetting(LoginContext context) throws Exception {
        return nativeService.queryByDataVisibilitySetting(context);
    }
}
