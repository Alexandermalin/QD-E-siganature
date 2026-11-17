package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.bd.pub.orginfo.model.OrgInfoMediator;
import nc.ui.bd.supplier.baseinfo.model.SupplierBaseInfoModel;
import nc.ui.bd.supplier.baseinfo.view.SupEntireFreezeOrgDlg;
import nc.ui.pub.beans.MessageDialog;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.AppEvent;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.ShowStatusBarMsgUtil;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.bd.pub.NODE_TYPE;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.ml.NCLangRes4VoTransl;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SupplierFreezeCommand;

/** Preserves the native confirmation and target selector, then dispatches one signed transaction. */
final class QdSupplierFreezeCoordinator implements OperationContextProvider,AuthorizedRequestConsumer {
    private final NCAction action;private final SupplierBaseInfoModel model;private final PartnerOperationOrganization scope;
    private final boolean freeze;private final ActionEvent event;private final SupplierVO supplier;
    private final String selectedPk,selectedOwner,selectedGroup;private final List<String> targets;
    private final List<BusinessObjectRef> refs;private OperationContext authorized;private String dispatched;

    static void run(NCAction action,SupplierBaseInfoModel model,PartnerOperationOrganization scope,boolean freeze,ActionEvent event)
            throws BusinessException {
        if(model==null || model.getContext()==null || event==null) throw new BusinessException("供应商冻结缺少业务上下文。");
        String message=NCLangRes4VoTransl.getNCLangRes().getStrByID("10140sub",freeze?"010140sub0012":"010140sub0014");
        if(1!=MessageDialog.showOkCancelDlg(model.getContext().getEntranceUI(),null,message)) return;
        Object selected=model.getSelectedData();
        if(!(selected instanceof SupplierVO)) throw new BusinessException("请选择供应商。");
        SupplierVO supplier=(SupplierVO)selected;String[] organizations;
        if(model.getContext().getNodeType()==NODE_TYPE.ORG_NODE) organizations=new String[]{supplier.getPk_org()};
        else {
            SupEntireFreezeOrgDlg dialog=new SupEntireFreezeOrgDlg(model.getContext().getEntranceUI(),model.getContext());
            dialog.setOrgRefStr(NCLangRes4VoTransl.getNCLangRes().getStrByID("10140sub",freeze?"010140sub0013":"010140sub0015"));
            dialog.initialize();if(dialog.showModal()!=1) return;organizations=dialog.getPk_orgs();
        }
        QdSupplierFreezeCoordinator operation=new QdSupplierFreezeCoordinator(action,model,scope,freeze,event,supplier,organizations);
        int[] counts=operation.execute();
        model.fireEvent(new AppEvent(OrgInfoMediator.PAGE_REFRESH,action,null));
        ShowStatusBarMsgUtil.showStatusBarMsg((freeze?"供应商冻结":"供应商解冻")+"完成；变更组织 "+counts[0]
            +"，未变化组织 "+counts[1]+"。请求号："+operation.lastRequestId,model.getContext());
    }
    private String lastRequestId;
    QdSupplierFreezeCoordinator(NCAction action,SupplierBaseInfoModel model,PartnerOperationOrganization scope,boolean freeze,
            ActionEvent event,SupplierVO supplier,String[] targets) throws BusinessException {
        if(action==null || model==null || model.getContext()==null || event==null || supplier==null || targets==null || targets.length==0)
            throw new BusinessException("供应商冻结缺少供应商或目标组织。");
        this.action=action;this.model=model;this.scope=scope;this.freeze=freeze;this.event=event;this.supplier=supplier;
        selectedPk=supplier.getPk_supplier();selectedOwner=supplier.getPk_org();selectedGroup=supplier.getPk_group();
        if(selectedPk==null || selectedOwner==null || selectedGroup==null || !selectedGroup.equals(model.getContext().getPk_group()))
            throw new BusinessException("供应商或所属集团不明确。");
        List<String> selected=new ArrayList<String>();List<BusinessObjectRef> objects=new ArrayList<BusinessObjectRef>();Set<String> seen=new HashSet<String>();
        for(String target:targets) {
            if(target==null || target.trim().isEmpty() || !target.equals(target.trim()) || !seen.add(target))
                throw new BusinessException("供应商冻结目标组织无效或重复。");
            selected.add(target);objects.add(new BusinessObjectRef(UUID.randomUUID().toString(),"BD_SUPPLIER",selectedPk,
                supplier.getCode(),null,null,null,target));
        }
        this.targets=Collections.unmodifiableList(selected);this.refs=Collections.unmodifiableList(objects);
    }
    private int[] execute() throws BusinessException {
        ActionInterceptor original=action.getInterceptor();ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);signing.setRequestConsumer(this);
        CompositeActionInterceptor chain=new CompositeActionInterceptor();chain.addInterceptor(signing);
        SignatureLifecycleInterceptor outer=null;boolean accepted=false;Throwable failure=null;
        try {
            action.setInterceptor(chain);outer=SignatureLifecycleInstaller.install(action,signing);
            if(!outer.beforeDoAction(action,event)) throw new BusinessException("供应商冻结已取消或授权未通过。");
            accepted=true;OperationContext request=authorized;
            if(request==null || !sameSelection(request) || dispatched!=null) throw new BusinessException("供应商冻结范围已变化，请重新操作。");
            String id=request.getRequestId();dispatched=id;lastRequestId=id;
            OperationResponse response=NCLocator.getInstance().lookup(IOperationService.class).execute(id,new SupplierFreezeCommand(supplier,targets));
            if(response==null || response.isReplayed() || !id.equals(response.getRequestId()) || !(response.getBusinessResult() instanceof ArchiveBatchResult))
                throw new BusinessException("无法确认供应商冻结结果，请按请求号核对。");
            int[] counts=validateReceipt(request,(ArchiveBatchResult)response.getBusinessResult());
            outer.afterDoActionSuccessed(action,event);return counts;
        } catch(BusinessException error) {
            BusinessException reported=dispatched==null?error:new BusinessException("供应商操作未确认，请重新查询并核对请求号："+dispatched,error);
            failure=reported;if(accepted && outer!=null) outer.afterDoActionFailed(action,event,reported);throw reported;
        } catch(RuntimeException error) {
            BusinessException reported=new BusinessException(dispatched==null?"供应商操作未完成。":"供应商操作未确认，请重新查询并核对请求号："+dispatched,error);
            failure=reported;if(accepted && outer!=null) outer.afterDoActionFailed(action,event,reported);throw reported;
        } catch(Error error) { failure=error;throw error; }
        finally { signing.finishLifecycle(signing.lifecycleGeneration(),action,event,failure);action.setInterceptor(original);authorized=null;dispatched=null; }
    }
    public OperationContext createContext(Action source,ActionEvent current) throws BusinessException {
        if(source!=action || current!=event || !selected()) throw new BusinessException("供应商冻结选择已变化。");
        String node=model.getContext().getNodeCode(),group=model.getContext().getPk_group();
        String org="10140SUG".equals(node)?group:scope==null?null:scope.requireOperationOrganization();
        if(org==null || !("10140SUG".equals(node) || "10140SUO".equals(node))) throw new BusinessException("无法确定供应商冻结的操作组织。");
        return new OperationContext(null,null,group,org,node,"供应商",freeze?"Freeze":"Unfreeze",freeze?"冻结":"解冻",
            freeze?"FREEZE":"UNFREEZE",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    public boolean isAvailable(OperationContext context) {
        return context!=null && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            && ArchiveBatchResult.isSupplierFreeze(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && context.getGroupId()!=null && context.getOrgId()!=null && context.getObjects().size()==refs.size();
    }
    public boolean bind(OperationContext context) {
        if(authorized!=null || !isAvailable(context) || context.getRequestId()==null || !sameSelection(context)) return false;
        authorized=context;return true;
    }
    public boolean wasDispatched(String id) { return id!=null && id.equals(dispatched); }
    public void release(String id) { if(authorized!=null && authorized.getRequestId().equals(id)) { authorized=null;dispatched=null; } }
    public Container getParentContainer() { return model.getContext().getEntranceUI(); }
    public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }
    private boolean selected() {
        Object current=model.getSelectedData();
        if(!(current instanceof SupplierVO)) return false;
        SupplierVO row=(SupplierVO)current;
        return selectedPk.equals(row.getPk_supplier()) && selectedOwner.equals(row.getPk_org()) && selectedGroup.equals(row.getPk_group())
            && selectedPk.equals(supplier.getPk_supplier()) && selectedOwner.equals(supplier.getPk_org()) && selectedGroup.equals(supplier.getPk_group())
            && selectedGroup.equals(model.getContext().getPk_group());
    }
    private boolean sameSelection(OperationContext context) {
        try {
            String node=model.getContext().getNodeCode(),group=model.getContext().getPk_group();
            String org="10140SUG".equals(node)?group:scope==null?null:scope.requireOperationOrganization();
            if(!selected() || !context.getNodeCode().equals(node) || !context.getGroupId().equals(group) || !context.getOrgId().equals(org)) return false;
            Map<String,String> expected=new HashMap<String,String>();for(BusinessObjectRef ref:refs) expected.put(ref.getClientObjectId(),ref.getTargetOrg());
            for(BusinessObjectRef ref:context.getObjects()) if(!selectedPk.equals(ref.getObjectPk()) || !"BD_SUPPLIER".equals(ref.getObjectType())
                    || !ref.getTargetOrg().equals(expected.remove(ref.getClientObjectId()))) return false;
            return expected.isEmpty();
        } catch(BusinessException error) { return false; }
    }
    static int[] validateReceipt(OperationContext request,ArchiveBatchResult result) throws BusinessException {
        if(request==null || result==null || result.getNativeResult()!=null || result.getUnits().size()!=request.getObjects().size())
            throw new BusinessException("供应商冻结逐组织结果不完整，请按请求号核对。");
        Map<String,String> expected=new HashMap<String,String>();
        for(BusinessObjectRef ref:request.getObjects()) if(expected.put(ref.getClientObjectId(),ref.getObjectPk())!=null)
            throw new BusinessException("供应商冻结授权对象重复。");
        int[] counts=new int[2];
        for(ArchiveBatchResult.Unit unit:result.getUnits()) {
            if(unit==null || unit.getObjectPk()==null || !unit.getObjectPk().equals(expected.remove(unit.getClientId()))) throw new BusinessException("供应商冻结结果与授权对象不一致。");
            if("SUCCESS".equals(unit.getState())) counts[0]++;else if("UNCHANGED".equals(unit.getState())) counts[1]++;
            else throw new BusinessException("供应商冻结结果状态不正确。");
        }
        if(!expected.isEmpty() || counts[0]==0) throw new BusinessException("供应商冻结结果未确认任何变化。");
        return counts;
    }
}
