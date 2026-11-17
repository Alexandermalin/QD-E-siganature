package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.bd.service.ErrLogElement;
import nc.bs.bd.service.ValueObjWithErrLog;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.CustomerFreezeCommand;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;

/** Hands the native filtered customer selection to one owner-policy batch request. */
final class QdCustomerFreezeCoordinator implements OperationContextProvider,AuthorizedRequestConsumer {
    private final NCAction action;
    private final BillManageModel model;
    private final PartnerOperationOrganization operationScope;
    private final boolean freeze,batch;
    private final ActionEvent event;
    private final Map<String,CustomerVO> rows=new LinkedHashMap<String,CustomerVO>();
    private final List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
    private final Set<String> initialSelection;
    private OperationContext authorized;
    private String dispatched,state,lastRequestId;

    QdCustomerFreezeCoordinator(NCAction action,BillManageModel model,PartnerOperationOrganization scope,
            boolean freeze,boolean batch,ActionEvent event,SuperVO[] eligible) throws BusinessException {
        if(action==null || model==null || model.getContext()==null || event==null || eligible==null || eligible.length==0)
            throw new BusinessException("客户冻结缺少原生选择。");
        this.action=action;this.model=model;this.operationScope=scope;this.freeze=freeze;this.batch=batch;this.event=event;
        String group=model.getContext().getPk_group();initialSelection=selectionIds(model,batch,group);
        Set<String> seen=new HashSet<String>();
        for(SuperVO item:eligible) {
            if(!(item instanceof CustomerVO)) throw new BusinessException("客户冻结数据类型不正确。");
            CustomerVO value=(CustomerVO)item;String pk=value.getPk_customer();
            if(pk==null || group==null || !group.equals(value.getPk_group()) || !seen.add(pk) || !initialSelection.contains(pk))
                throw new BusinessException("客户冻结范围已变化或包含其他集团客户。");
            String id=UUID.randomUUID().toString();rows.put(id,value);
            refs.add(new BusinessObjectRef(id,"BD_CUSTOMER",pk,value.getCode(),null));
        }
    }
    ArchiveBatchResult execute() throws BusinessException {
        ActionInterceptor original=action.getInterceptor();ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);signing.setRequestConsumer(this);
        CompositeActionInterceptor chain=new CompositeActionInterceptor();chain.addInterceptor(signing);
        SignatureLifecycleInterceptor outer=null;boolean accepted=false;Throwable failure=null;
        try {
            action.setInterceptor(chain);outer=SignatureLifecycleInstaller.install(action,signing);
            if(!outer.beforeDoAction(action,event)) throw new BusinessException("客户冻结已取消或授权未通过。");
            accepted=true;OperationContext request=authorized;
            if(request==null || !sameSelection(request) || dispatched!=null) throw new BusinessException("客户冻结范围已变化，请重新操作。");
            String id=request.getRequestId();dispatched=id;lastRequestId=id;
            OperationResponse response=NCLocator.getInstance().lookup(IOperationService.class).execute(id,new CustomerFreezeCommand(rows));
            if(response==null || response.isReplayed() || !id.equals(response.getRequestId()) || !(response.getBusinessResult() instanceof ArchiveBatchResult))
                throw new BusinessException("无法确认客户冻结结果，请重新查询。请求号："+id);
            ArchiveBatchResult result=(ArchiveBatchResult)response.getBusinessResult();state=validateReceipt(request,result);
            outer.afterDoActionSuccessed(action,event);return result;
        } catch(BusinessException error) {
            BusinessException reported=dispatched==null?error:new BusinessException("客户操作未确认，请重新查询并核对请求号："+dispatched,error);
            failure=reported;if(accepted && outer!=null) outer.afterDoActionFailed(action,event,reported);throw reported;
        } catch(RuntimeException error) {
            BusinessException reported=new BusinessException(dispatched==null?"客户操作未完成。":"客户操作未确认，请重新查询并核对请求号："+dispatched,error);
            failure=reported;if(accepted && outer!=null) outer.afterDoActionFailed(action,event,reported);throw reported;
        } catch(Error error) { failure=error;throw error; }
        finally {
            signing.finishLifecycle(signing.lifecycleGeneration(),action,event,failure);action.setInterceptor(original);
            authorized=null;dispatched=null;
        }
    }
    String getState() { return state; }
    String getRequestId() { return lastRequestId; }
    public OperationContext createContext(Action source,ActionEvent event) throws BusinessException {
        if(source!=action || event!=this.event || !selectionIds(model,batch,model.getContext().getPk_group()).equals(initialSelection))
            throw new BusinessException("客户冻结选择已变化。");
        String node=model.getContext().getNodeCode(),group=model.getContext().getPk_group();
        String org="10140CUG".equals(node)?group:operationScope==null?null:operationScope.requireOperationOrganization();
        if(org==null || !("10140CUG".equals(node) || "10140CUO".equals(node))) throw new BusinessException("无法确定客户冻结的操作组织。");
        return new OperationContext(null,null,group,org,node,"客户",freeze?"Freeze":"Unfreeze",freeze?"冻结":"解冻",
            freeze?"FREEZE":"UNFREEZE",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    public boolean isAvailable(OperationContext context) {
        if(context==null || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            || !ArchiveBatchResult.isCustomerFreeze(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            || context.getGroupId()==null || context.getOrgId()==null || context.getObjects().size()!=refs.size()) return false;
        for(BusinessObjectRef ref:context.getObjects()) if(!"BD_CUSTOMER".equals(ref.getObjectType()) || ref.getObjectPk()==null) return false;
        return true;
    }
    public boolean bind(OperationContext context) {
        if(authorized!=null || !isAvailable(context) || context.getRequestId()==null || !sameSelection(context)) return false;
        authorized=context;return true;
    }
    public boolean wasDispatched(String id) { return id!=null && id.equals(dispatched); }
    public void release(String id) { if(authorized!=null && authorized.getRequestId().equals(id)) { authorized=null;dispatched=null; } }
    public Container getParentContainer() { return model.getContext().getEntranceUI(); }
    public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }
    private boolean sameSelection(OperationContext context) {
        try {
            String group=model.getContext().getPk_group(),node=model.getContext().getNodeCode();
            String org="10140CUG".equals(node)?group:operationScope==null?null:operationScope.requireOperationOrganization();
            if(!context.getNodeCode().equals(node) || !context.getGroupId().equals(group) || !context.getOrgId().equals(org)
                    || !selectionIds(model,batch,group).equals(initialSelection)) return false;
            for(BusinessObjectRef ref:context.getObjects()) {
                CustomerVO value=rows.get(ref.getClientObjectId());
                if(value==null || !ref.getObjectPk().equals(value.getPk_customer()) || !group.equals(value.getPk_group())) return false;
            }
            return true;
        } catch(BusinessException error) { return false; }
    }
    private static Set<String> selectionIds(BillManageModel model,boolean batch,String group) throws BusinessException {
        Object[] selected=batch?model.getSelectedOperaDatas():new Object[]{model.getSelectedData()};
        Set<String> pks=new HashSet<String>();
        if(selected==null || group==null) throw new BusinessException("无法确定客户选择。");
        for(Object raw:selected) {
            if(!(raw instanceof CustomerVO) || !group.equals(((CustomerVO)raw).getPk_group())
                    || ((CustomerVO)raw).getPk_customer()==null || !pks.add(((CustomerVO)raw).getPk_customer()))
                throw new BusinessException("客户选择包含重复或其他集团的档案。");
        }
        return pks;
    }
    static String validateReceipt(OperationContext request,ArchiveBatchResult result) throws BusinessException {
        if(request==null || result==null || result.getNativeResult()==null || result.getUnits().size()!=request.getObjects().size())
            throw new BusinessException("客户冻结逐档案结果不完整，请按请求号核对。");
        Map<String,String> expected=new HashMap<String,String>(),states=new HashMap<String,String>();
        for(BusinessObjectRef ref:request.getObjects()) expected.put(ref.getClientObjectId(),ref.getObjectPk());
        boolean success=false,failed=false;
        for(ArchiveBatchResult.Unit unit:result.getUnits()) {
            if(unit==null || !expected.containsKey(unit.getClientId()) || !expected.get(unit.getClientId()).equals(unit.getObjectPk())
                    || states.put(unit.getObjectPk(),unit.getState())!=null) throw new BusinessException("客户冻结结果与授权对象不一致。");
            if("SUCCESS".equals(unit.getState())) success=true;
            else if("FAILED".equals(unit.getState())) failed=true;
            else throw new BusinessException("客户冻结结果状态不正确。");
        }
        ValueObjWithErrLog nativeResult=result.getNativeResult();Set<String> seen=new HashSet<String>();
        SuperVO[] saved=nativeResult.getVos();
        if(saved!=null) for(SuperVO row:saved) {
            String pk=row instanceof CustomerVO?((CustomerVO)row).getPk_customer():null;
            if(pk==null || !seen.add(pk) || !"SUCCESS".equals(states.get(pk))) throw new BusinessException("客户冻结成功明细与原生结果不一致。");
        }
        List<ErrLogElement> errors=nativeResult.getErrLogList();
        if(errors!=null) for(ErrLogElement error:errors) {
            String pk=error==null?null:error.getErrPK();
            if(pk==null || !seen.add(pk) || !"FAILED".equals(states.get(pk))) throw new BusinessException("客户冻结失败明细与原生结果不一致。");
        }
        if(seen.size()!=states.size()) throw new BusinessException("客户冻结原生结果缺少所选客户。");
        return failed?(success?"PARTIAL":"FAILED"):"SUCCESS";
    }
}
