package nc.ui.uapbd.qd.esign;

import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.uif2.model.IAppModelService;
import nc.vo.bd.stordoc.StordocVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.WarehouseSaveCommand;
import nc.vo.uif2.LoginContext;

/** Keeps the native model service for other actions; guarded saves consume one request. */
public final class QdWarehouseModelService implements IAppModelService, AuthorizedRequestConsumer {
    private final IAppModelService nativeService;
    private final ThreadLocal<OperationContext> saving=new ThreadLocal<OperationContext>();
    private OperationContext authorized,prepared;
    private String dispatched;
    private String copySource,sourceAddress;

    public QdWarehouseModelService(IAppModelService nativeService) {
        if(nativeService==null) throw new IllegalArgumentException("Native warehouse service is required");
        this.nativeService=nativeService;
    }
    public boolean isAvailable(OperationContext context) {
        return context!=null && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            && "10140WARH".equals(context.getNodeCode()) && "Save".equals(context.getButtonCode())
            && ("INSERT".equals(context.getOperationType()) || "UPDATE".equals(context.getOperationType()))
            && context.getGroupId()!=null && context.getOrgId()!=null && context.getObjects().size()==1
            && "BD_STORDOC".equals(context.getObjects().get(0).getObjectType());
    }
    public boolean bind(OperationContext context) {
        if(authorized!=null || !isAvailable(context) || context.getRequestId()==null) return false;
        authorized=context;return true;
    }
    public boolean wasDispatched(String requestId) { return requestId!=null && requestId.equals(dispatched); }
    public void release(String requestId) {
        if(authorized!=null && authorized.getRequestId().equals(requestId)) { authorized=null;dispatched=null; }
    }
    void acceptPreparation(OperationContext context) { prepared=authorized==null?context:authorized; }
    void beginSave(Object value,String operation) throws BusinessException {
        if(saving.get()!=null || prepared==null || prepared!=authorized || prepared.getRequestId()==null
                || !isAvailable(prepared) || !operation.equals(prepared.getOperationType()) || !sameDraft(prepared,value))
            throw new BusinessException("仓库保存数据与授权不一致，请重新发起保存。");
        saving.set(prepared);
    }
    void endSave() { saving.remove(); }
    void finishAttempt() { endSave();prepared=null;authorized=null;dispatched=null; }
    void setCopySource(String pk,String address) { copySource=pk;sourceAddress=address; }
    void clearCopySource() { copySource=null;sourceAddress=null; }

    public Object insert(Object value) throws Exception { return save(value,"INSERT"); }
    public Object update(Object value) throws Exception { return save(value,"UPDATE"); }
    private Object save(Object value,String operation) throws Exception {
        OperationContext context=saving.get();
        if(context==null) return "INSERT".equals(operation)?nativeService.insert(value):nativeService.update(value);
        if(context!=prepared || context!=authorized || !operation.equals(context.getOperationType())
                || !sameDraft(context,value) || wasDispatched(context.getRequestId()))
            throw new BusinessException("仓库保存缺少有效的一次性授权。");
        String id=context.getRequestId();dispatched=id;
        StordocVO draft=(StordocVO)value;
        WarehouseSaveCommand command="INSERT".equals(operation) && copySource!=null
            && java.util.Objects.equals(sourceAddress,draft.getStoraddr())
            ?new WarehouseSaveCommand(draft,copySource,sourceAddress):new WarehouseSaveCommand(draft);
        OperationResponse response=NCLocator.getInstance().lookup(IOperationService.class).execute(id,command);
        if(response==null || !id.equals(response.getRequestId()) || response.isReplayed()
                || !(response.getBusinessResult() instanceof StordocVO)) throw unknown(id);
        StordocVO saved=(StordocVO)response.getBusinessResult();
        BusinessObjectRef ref=context.getObjects().get(0);
        if(saved.getPk_stordoc()==null || !context.getGroupId().equals(saved.getPk_group())
                || !ref.getDesiredOwnerOrg().equals(saved.getPk_org())
                || ("UPDATE".equals(operation) && !ref.getObjectPk().equals(saved.getPk_stordoc()))) throw unknown(id);
        clearCopySource();
        return saved;
    }
    static boolean sameDraft(OperationContext context,Object raw) {
        if(!(raw instanceof StordocVO) || context==null || context.getObjects().size()!=1) return false;
        StordocVO value=(StordocVO)raw;BusinessObjectRef ref=context.getObjects().get(0);
        if(!context.getGroupId().equals(value.getPk_group()) || ref.getDesiredOwnerOrg()==null
                || !ref.getDesiredOwnerOrg().equals(value.getPk_org())) return false;
        return "INSERT".equals(context.getOperationType())?ref.getObjectPk()==null && value.getPk_stordoc()==null
            :"UPDATE".equals(context.getOperationType()) && ref.getObjectPk()!=null && ref.getObjectPk().equals(value.getPk_stordoc());
    }
    private static BusinessException unknown(String id) { return new BusinessException("无法确认仓库保存结果，请重新查询核对。请求号："+id); }
    public void delete(Object value) throws Exception { nativeService.delete(value); }
    public Object[] queryByDataVisibilitySetting(LoginContext context) throws Exception { return nativeService.queryByDataVisibilitySetting(context); }
}
