package nc.ui.uapbd.qd.esign;

import java.util.Map;
import nc.bs.bd.batchupdate.IBatchUpdateService;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.uif2.components.pagination.IPaginationQueryService;
import nc.ui.uif2.model.IAppModelService;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PartnerOrgInfoCommand;
import nc.vo.uapbd.qd.esign.PartnerOrgPage;
import nc.vo.uif2.LoginContext;

/** Delegates native operations and routes only the signed page update through the gateway. */
public class QdPartnerOrgInfoModelService implements IAppModelService,IBatchUpdateService,AuthorizedRequestConsumer {
    private final IAppModelService nativeService;
    private final PartnerOrgPage page;
    private final ThreadLocal<OperationContext> saving=new ThreadLocal<OperationContext>();
    private OperationContext authorized,prepared;
    private String dispatched;
    public QdPartnerOrgInfoModelService(IAppModelService nativeService,String domain) {
        this.nativeService=nativeService;page=PartnerOrgPage.forDomain(domain);
        if(nativeService==null || page==null) throw new IllegalArgumentException("Unknown partner page service");
    }
    PartnerOrgPage page() { return page; }
    SuperVO requireRow(Object value) throws BusinessException {
        if(!page.type.isInstance(value) || ((SuperVO)value).getPrimaryKey()==null) throw new BusinessException("无法确定往来档案页签数据行。");
        return page.type.cast(value);
    }
    public boolean isAvailable(OperationContext context) {
        return context!=null && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            && PartnerOrgPage.forAction(context.getNodeCode(),context.getButtonCode(),context.getOperationType())==page
            && "UPDATE".equals(context.getOperationType()) && context.getGroupId()!=null && context.getOrgId()!=null
            && context.getObjects().size()==1 && page.domain.equals(context.getObjects().get(0).getObjectType())
            && context.getObjects().get(0).getObjectPk()!=null;
    }
    public boolean bind(OperationContext context) {
        if(authorized!=null || !isAvailable(context) || context.getRequestId()==null) return false;
        authorized=context;return true;
    }
    public boolean wasDispatched(String id) { return id!=null && id.equals(dispatched); }
    public void release(String id) { if(authorized!=null && authorized.getRequestId().equals(id)) { authorized=null;dispatched=null; } }
    void acceptPreparation(OperationContext context) { prepared=authorized==null?context:authorized; }
    void beginSave(Object value) throws BusinessException {
        if(!isAvailable(prepared) || saving.get()!=null || !sameRow(prepared,requireRow(value))) throw new BusinessException("往来档案页签与授权不一致，请重新保存。");
        saving.set(prepared);
    }
    void endSave() { saving.remove(); }
    void finishAttempt() { endSave();prepared=null;authorized=null;dispatched=null; }
    public Object update(Object value) throws Exception {
        OperationContext context=saving.get();
        if(context==null) return nativeService.update(value);
        SuperVO row=requireRow(value);
        if(context!=prepared || !sameRow(context,row) || context.getRequestId()==null || context!=authorized || wasDispatched(context.getRequestId()))
            throw new BusinessException("往来档案页签保存缺少有效的一次性授权。");
        String id=context.getRequestId();dispatched=id;
        OperationResponse response=NCLocator.getInstance().lookup(IOperationService.class).execute(id,new PartnerOrgInfoCommand(row));
        if(response==null || !id.equals(response.getRequestId()) || response.isReplayed()
                || !page.type.isInstance(response.getBusinessResult()) || !sameRow(context,page.type.cast(response.getBusinessResult())))
            throw new BusinessException("无法确认往来档案页签保存结果，请重新查询核对。请求号："+id);
        return response.getBusinessResult();
    }
    private static boolean sameRow(OperationContext context,SuperVO row) {
        return context.getObjects().get(0).getObjectPk().equals(row.getPrimaryKey())
            && context.getGroupId().equals(row.getAttributeValue("pk_group"));
    }
    public Object insert(Object value) throws Exception { return nativeService.insert(value); }
    public void delete(Object value) throws Exception { nativeService.delete(value); }
    public Object[] queryByDataVisibilitySetting(LoginContext context) throws Exception { return nativeService.queryByDataVisibilitySetting(context); }
    protected Object[] queryNativeObjectByPks(String[] pks) throws BusinessException {
        if(!(nativeService instanceof IPaginationQueryService)) throw new BusinessException("当前原生页签不支持分页查询。");
        return ((IPaginationQueryService)nativeService).queryObjectByPks(pks);
    }
    public ErrLogReturnValue batchUpdateByPks(String[] pks,Map<String,Object> values,String[] a,String[] b,String[] c,boolean flag) throws BusinessException {
        if(!(nativeService instanceof IBatchUpdateService)) throw new BusinessException("当前原生页签不支持批改。");
        return ((IBatchUpdateService)nativeService).batchUpdateByPks(pks,values,a,b,c,flag);
    }
    public ErrLogReturnValue batchUpdateByCondition(String[] pks,Map<String,Object> values,String[] a,String[] b,String condition) throws BusinessException {
        if(!(nativeService instanceof IBatchUpdateService)) throw new BusinessException("当前原生页签不支持批改。");
        return ((IBatchUpdateService)nativeService).batchUpdateByCondition(pks,values,a,b,condition);
    }
}
