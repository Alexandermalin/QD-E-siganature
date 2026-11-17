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
import nc.ui.uif2.NCAction;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.material.MaterialVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.bd.stordoc.StordocVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PartnerStateCommand;

/** Carries the native filtered partner selection into a single owner-policy batch. */
final class QdPartnerStateCoordinator implements OperationContextProvider,AuthorizedRequestConsumer {
    private final NCAction action;private final BillManageModel model;private final PartnerOperationOrganization scope;
    private final QdExplicitSignatureAction signingAction=new QdExplicitSignatureAction();
    private final boolean customer,material,warehouse,enable,batch;private final ActionEvent event;
    private final Map<String,SuperVO> rows=new LinkedHashMap<String,SuperVO>();
    private final List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();private final Set<String> selection;
    private OperationContext authorized;private String dispatched,lastRequestId;

    QdPartnerStateCoordinator(NCAction action,BillManageModel model,PartnerOperationOrganization scope,
            boolean customer,boolean enable,boolean batch,ActionEvent event,SuperVO[] eligible) throws BusinessException {
        this(action,model,scope,customer,false,enable,batch,event,eligible);
    }
    QdPartnerStateCoordinator(NCAction action,BillManageModel model,PartnerOperationOrganization scope,
            boolean customer,boolean material,boolean enable,boolean batch,ActionEvent event,SuperVO[] eligible) throws BusinessException {
        this(action,model,scope,customer,material,false,enable,batch,event,eligible);
    }
    QdPartnerStateCoordinator(NCAction action,BillManageModel model,PartnerOperationOrganization scope,
            boolean customer,boolean material,boolean warehouse,boolean enable,boolean batch,ActionEvent event,SuperVO[] eligible) throws BusinessException {
        if(action==null || model==null || model.getContext()==null || event==null || eligible==null || eligible.length==0)
            throw new BusinessException("档案启停缺少原生选择。");
        this.action=action;this.model=model;this.scope=scope;this.customer=customer;this.material=material;this.warehouse=warehouse;this.enable=enable;this.batch=batch;this.event=event;
        String group=model.getContext().getPk_group();selection=selectionIds(model,batch,customer,material,warehouse,group);Set<String> seen=new HashSet<String>();
        for(SuperVO value:eligible) {
            String pk=pk(value,customer,material,warehouse),rowGroup=group(value,customer,material,warehouse);
            if(pk==null || group==null || !group.equals(rowGroup) || !seen.add(pk) || !selection.contains(pk))
                throw new BusinessException("档案启停选择已变化或包含其他集团的数据。");
            String id=UUID.randomUUID().toString();rows.put(id,value);
            refs.add(new BusinessObjectRef(id,warehouse?"BD_STORDOC":material?"BD_MATERIAL":customer?"BD_CUSTOMER":"BD_SUPPLIER",pk,code(value,customer,material,warehouse),null));
        }
    }

    ArchiveBatchResult execute() throws BusinessException {
        ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);signing.setRequestConsumer(this);
        SignatureLifecycleInterceptor outer=null;boolean accepted=false;Throwable failure=null;
        try {
            outer=signingAction.install(signing);
            if(!outer.beforeDoAction(signingAction,event)) throw new BusinessException("档案启停已取消或授权未通过。");
            accepted=true;OperationContext request=authorized;
            if(request==null || !sameSelection(request) || dispatched!=null) throw new BusinessException("档案启停范围已变化，请重新操作。");
            String id=request.getRequestId();dispatched=id;lastRequestId=id;
            OperationResponse response=NCLocator.getInstance().lookup(IOperationService.class).execute(id,new PartnerStateCommand(rows));
            if(response==null || response.isReplayed() || !id.equals(response.getRequestId()) || !(response.getBusinessResult() instanceof ArchiveBatchResult))
                throw new BusinessException("无法确认档案启停结果，请核对请求号："+id);
            ArchiveBatchResult result=(ArchiveBatchResult)response.getBusinessResult();validateReceipt(request,result,customer,material,warehouse);
            outer.afterDoActionSuccessed(signingAction,event);return result;
        } catch(BusinessException error) {
            BusinessException reported=dispatched==null?error:new BusinessException("档案启停未确认，请重新查询并核对请求号："+dispatched,error);
            failure=reported;if(accepted && outer!=null) outer.afterDoActionFailed(signingAction,event,reported);throw reported;
        } catch(RuntimeException error) {
            BusinessException reported=new BusinessException(dispatched==null?"档案启停未完成。":"档案启停未确认，请重新查询并核对请求号："+dispatched,error);
            failure=reported;if(accepted && outer!=null) outer.afterDoActionFailed(signingAction,event,reported);throw reported;
        } catch(Error error) { failure=error;throw error; }
        finally { signing.finishLifecycle(signing.lifecycleGeneration(),signingAction,event,failure);authorized=null;dispatched=null; }
    }

    String getRequestId() { return lastRequestId; }
    public OperationContext createContext(Action source,ActionEvent value) throws BusinessException {
        if(source!=signingAction || value!=event || !selectionIds(model,batch,customer,material,warehouse,model.getContext().getPk_group()).equals(selection))
            throw new BusinessException("档案启停选择已变化。");
        String node=model.getContext().getNodeCode(),group=model.getContext().getPk_group();
        boolean groupNode=!warehouse && (material?"10140MAG".equals(node):customer?"10140CUG".equals(node):"10140SUG".equals(node));
        String org=groupNode?group:scope==null?null:scope.requireOperationOrganization();
        if(org==null || !(warehouse?ArchiveBatchResult.isWarehouseState(node,enable?"Enable":"Disable",enable?"ENABLE":"DISABLE")
            :material?ArchiveBatchResult.isMaterialState(node,enable?"Enable":"Disable",enable?"ENABLE":"DISABLE")
            :ArchiveBatchResult.isPartnerState(node,enable?"Enable":"Disable",enable?"ENABLE":"DISABLE")))
            throw new BusinessException("无法确定档案启停的操作节点或组织。");
        return new OperationContext(null,null,group,org,node,warehouse?"仓库":material?"物料":customer?"客户":"供应商",enable?"Enable":"Disable",enable?"启用":"停用",
            enable?"ENABLE":"DISABLE",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    public boolean isAvailable(OperationContext context) {
        if(context==null || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            || !(warehouse?ArchiveBatchResult.isWarehouseState(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
                :material?ArchiveBatchResult.isMaterialState(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
                :ArchiveBatchResult.isPartnerState(context.getNodeCode(),context.getButtonCode(),context.getOperationType()))
            || context.getGroupId()==null || context.getOrgId()==null || context.getObjects().size()!=refs.size()) return false;
        for(BusinessObjectRef ref:context.getObjects()) if(!(warehouse?"BD_STORDOC":material?"BD_MATERIAL":customer?"BD_CUSTOMER":"BD_SUPPLIER").equals(ref.getObjectType()) || ref.getObjectPk()==null) return false;
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
    private boolean sameSelection(OperationContext request) {
        try {
            String node=model.getContext().getNodeCode(),group=model.getContext().getPk_group();
            boolean groupNode=!warehouse && (material?"10140MAG".equals(node):customer?"10140CUG".equals(node):"10140SUG".equals(node));
            String org=groupNode?group:scope==null?null:scope.requireOperationOrganization();
            if(!request.getNodeCode().equals(node) || !request.getGroupId().equals(group) || !request.getOrgId().equals(org)
                || !selectionIds(model,batch,customer,material,warehouse,group).equals(selection)) return false;
            for(BusinessObjectRef ref:request.getObjects()) {
                SuperVO value=rows.get(ref.getClientObjectId());
                if(value==null || !ref.getObjectPk().equals(pk(value,customer,material,warehouse)) || !group.equals(group(value,customer,material,warehouse))) return false;
            }
            return true;
        } catch(BusinessException error) { return false; }
    }
    private static Set<String> selectionIds(BillManageModel model,boolean batch,boolean customer,boolean material,boolean warehouse,String group) throws BusinessException {
        Object[] selected=batch?model.getSelectedOperaDatas():new Object[]{model.getSelectedData()};Set<String> pks=new HashSet<String>();
        if(selected==null || group==null) throw new BusinessException("无法确定档案选择。");
        for(Object raw:selected) {
            if(!(raw instanceof SuperVO) || !group.equals(group((SuperVO)raw,customer,material,warehouse))) throw new BusinessException("档案选择包含其他集团的数据。");
            String pk=pk((SuperVO)raw,customer,material,warehouse);if(pk==null || !pks.add(pk)) throw new BusinessException("档案选择包含重复对象。");
        }
        return pks;
    }
    private static String pk(SuperVO value,boolean customer,boolean material,boolean warehouse) { return warehouse&&value instanceof StordocVO?((StordocVO)value).getPk_stordoc():material&&value instanceof MaterialVO?((MaterialVO)value).getPk_material():customer&&value instanceof CustomerVO?((CustomerVO)value).getPk_customer():
        !warehouse&&!material&&!customer&&value instanceof SupplierVO?((SupplierVO)value).getPk_supplier():null; }
    private static String group(SuperVO value,boolean customer,boolean material,boolean warehouse) { return warehouse&&value instanceof StordocVO?((StordocVO)value).getPk_group():material&&value instanceof MaterialVO?((MaterialVO)value).getPk_group():customer&&value instanceof CustomerVO?((CustomerVO)value).getPk_group():
        !warehouse&&!material&&!customer&&value instanceof SupplierVO?((SupplierVO)value).getPk_group():null; }
    private static String code(SuperVO value,boolean customer,boolean material,boolean warehouse) { return warehouse&&value instanceof StordocVO?((StordocVO)value).getCode():material&&value instanceof MaterialVO?((MaterialVO)value).getCode():customer&&value instanceof CustomerVO?((CustomerVO)value).getCode():
        !warehouse&&!material&&!customer&&value instanceof SupplierVO?((SupplierVO)value).getCode():null; }

    static void validateReceipt(OperationContext request,ArchiveBatchResult result,boolean customer) throws BusinessException {
        validateReceipt(request,result,customer,false);
    }
    static void validateReceipt(OperationContext request,ArchiveBatchResult result,boolean customer,boolean material) throws BusinessException {
        validateReceipt(request,result,customer,material,false);
    }
    static void validateReceipt(OperationContext request,ArchiveBatchResult result,boolean customer,boolean material,boolean warehouse) throws BusinessException {
        if(request==null || result==null || result.getNativeResult()==null || result.getUnits().size()!=request.getObjects().size())
            throw new BusinessException("档案启停逐对象结果不完整。");
        Map<String,String> expected=new HashMap<String,String>(),states=new HashMap<String,String>();
        for(BusinessObjectRef ref:request.getObjects()) expected.put(ref.getClientObjectId(),ref.getObjectPk());
        for(ArchiveBatchResult.Unit unit:result.getUnits()) {
            if(unit==null || !expected.containsKey(unit.getClientId()) || !expected.get(unit.getClientId()).equals(unit.getObjectPk())
                || states.put(unit.getObjectPk(),unit.getState())!=null || !("SUCCESS".equals(unit.getState()) || "FAILED".equals(unit.getState())
                || "UNCHANGED".equals(unit.getState()))) throw new BusinessException("档案启停结果与授权对象不一致。");
        }
        Set<String> seen=new HashSet<String>();ValueObjWithErrLog nativeResult=result.getNativeResult();
        SuperVO[] saved=nativeResult.getVos();
        if(saved!=null) for(SuperVO row:saved) {
            String pk=pk(row,customer,material,warehouse);if(pk==null || !seen.add(pk) || !"SUCCESS".equals(states.get(pk)))
                throw new BusinessException("档案启停成功明细与原生结果不一致。");
        }
        List<ErrLogElement> errors=nativeResult.getErrLogList();
        if(errors!=null) for(ErrLogElement error:errors) {
            String pk=error==null?null:error.getErrPK();if(pk==null || !seen.add(pk) || !"FAILED".equals(states.get(pk)))
                throw new BusinessException("档案启停失败明细与原生结果不一致。");
        }
        for(Map.Entry<String,String> state:states.entrySet()) if(!"UNCHANGED".equals(state.getValue()) && !seen.contains(state.getKey()))
            throw new BusinessException("档案启停原生结果缺少所选对象。");
    }

    static Object cardResult(ArchiveBatchResult result) throws BusinessException {
        ValueObjWithErrLog nativeResult=result.getNativeResult();List<ErrLogElement> errors=nativeResult.getErrLogList();
        if(errors!=null && !errors.isEmpty()) throw new BusinessException(errors.get(0).getErrReason());
        SuperVO[] saved=nativeResult.getVos();
        if(saved==null || saved.length!=1) throw new BusinessException("档案状态未发生变化，请刷新后重试。");
        return saved[0];
    }
}
