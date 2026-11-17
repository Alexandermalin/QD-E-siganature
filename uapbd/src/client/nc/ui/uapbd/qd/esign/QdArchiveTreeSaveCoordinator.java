package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.UIState;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.model.HierachicalDataAppModel;
import nc.vo.bd.cust.custclass.CustClassVO;
import nc.vo.bd.material.marbasclass.MarBasClassVO;
import nc.vo.bd.rack.RackVO;
import nc.vo.bd.supplier.supplierclass.SupplierclassVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveTreeSaveCommand;
import nc.vo.uapbd.qd.esign.ArchiveTreeSaveDraft;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;

/** Signs the validated native editor value and dispatches its exact tree-save scope. */
final class QdArchiveTreeSaveCoordinator implements OperationContextProvider,AuthorizedRequestConsumer {
    private final NCAction action;private final HierachicalDataAppModel model;private final ActionEvent event;private final SuperVO value;
    private final String pk,group,owner,code,parent,warehouse,node,operationOrg,domain,initialSelectedPk;
    private final Object initialSelection;private final boolean insert;
    private OperationContext authorized;private String dispatched;

    QdArchiveTreeSaveCoordinator(NCAction action,HierachicalDataAppModel model,ActionEvent event,Object input) throws BusinessException {
        if(action==null || model==null || model.getContext()==null || event==null || !(input instanceof SuperVO))
            throw new BusinessException("树形档案保存缺少业务上下文。");
        this.action=action;this.model=model;this.event=event;this.value=(SuperVO)input;
        node=model.getContext().getNodeCode();domain=domain(node);group=model.getContext().getPk_group();
        operationOrg=groupNode(node)?group:model.getContext().getPk_org();
        insert=model.getUiState()==UIState.ADD;initialSelection=model.getSelectedData();
        initialSelectedPk=initialSelection instanceof SuperVO?pk((SuperVO)initialSelection):null;
        pk=pk(value);owner=field(value,"pk_org");code=field(value,"code");parent=field(value,"parent");warehouse=field(value,"warehouse");
        if(domain==null || (!insert && pk==null) || (insert && pk!=null) || group==null || owner==null || operationOrg==null
                || insert && !owner.equals(operationOrg) || !group.equals(field(value,"pk_group"))
                || !matches(domain,value) || !selected()) throw new BusinessException("无法确定树形档案、集团或操作组织。");
    }
    SuperVO execute() throws BusinessException {
        ActionInterceptor original=action.getInterceptor();ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);signing.setRequestConsumer(this);
        CompositeActionInterceptor chain=new CompositeActionInterceptor();chain.addInterceptor(signing);
        SignatureLifecycleInterceptor outer=null;boolean accepted=false;Throwable failure=null;
        try {
            action.setInterceptor(chain);outer=SignatureLifecycleInstaller.install(action,signing);
            if(!outer.beforeDoAction(action,event)) throw new BusinessException("树形档案保存已取消或授权未通过。");
            accepted=true;OperationContext request=authorized;
            if(request==null || !sameSelection(request) || dispatched!=null) throw new BusinessException("树形档案保存内容已变化，请重新操作。");
            String id=request.getRequestId();dispatched=id;
            OperationResponse response=NCLocator.getInstance().lookup(IOperationService.class).execute(id,new ArchiveTreeSaveCommand(value));
            if(response==null || response.isReplayed() || !id.equals(response.getRequestId()) || response.getBusinessResult()==null
                    || response.getBusinessResult().getClass()!=value.getClass()) throw unknown(id);
            SuperVO saved=(SuperVO)response.getBusinessResult();
            if(pk(saved)==null || !insert && !pk.equals(pk(saved)) || !group.equals(field(saved,"pk_group")) || !owner.equals(field(saved,"pk_org"))) throw unknown(id);
            outer.afterDoActionSuccessed(action,event);return saved;
        } catch(BusinessException error) {
            BusinessException reported=dispatched==null?error:new BusinessException("树形档案保存未确认，请重新查询并核对请求号："+dispatched,error);
            failure=reported;if(accepted && outer!=null) outer.afterDoActionFailed(action,event,reported);throw reported;
        } catch(RuntimeException error) {
            BusinessException reported=new BusinessException(dispatched==null?"树形档案保存未完成。":"树形档案保存未确认，请核对请求号："+dispatched,error);
            failure=reported;if(accepted && outer!=null) outer.afterDoActionFailed(action,event,reported);throw reported;
        } catch(Error error) { failure=error;throw error; }
        finally { signing.finishLifecycle(signing.lifecycleGeneration(),action,event,failure);action.setInterceptor(original);authorized=null;dispatched=null; }
    }
    public OperationContext createContext(Action source,ActionEvent current) throws BusinessException {
        if(source!=action || current!=event || !selected() || !sameValue()) throw new BusinessException("树形档案保存内容已变化。");
        BusinessObjectRef ref=new BusinessObjectRef(UUID.randomUUID().toString(),domain,pk,code,null,owner);
        return new OperationContext(null,null,group,operationOrg,node,"10140RACK".equals(node)?"货位":"基础档案分类",
            "Save","保存",insert?"INSERT":"UPDATE",null,Collections.singletonList(ref),OperationContext.OWNER_POLICY_PROTOCOL,null,
            new ArchiveTreeSaveDraft(code,parent,warehouse));
    }
    public boolean isAvailable(OperationContext context) {
        return context!=null && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            && (insert?"INSERT":"UPDATE").equals(context.getOperationType()) && "Save".equals(context.getButtonCode())
            && domain(context.getNodeCode())!=null && context.getArchiveTreeSaveDraft()!=null && !context.getObjects().isEmpty();
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
        Object selected=model.getSelectedData();
        if(!insert) return selected instanceof SuperVO && pk!=null && pk.equals(pk((SuperVO)selected));
        if(initialSelection==null) return selected==null;
        if(initialSelectedPk==null) return selected==initialSelection;
        return selected instanceof SuperVO && selected.getClass()==initialSelection.getClass()
            && initialSelectedPk.equals(pk((SuperVO)selected));
    }
    private boolean sameValue() {
        return Objects.equals(pk,pk(value)) && group.equals(field(value,"pk_group")) && owner.equals(field(value,"pk_org"))
            && Objects.equals(code,field(value,"code")) && Objects.equals(parent,field(value,"parent")) && Objects.equals(warehouse,field(value,"warehouse"));
    }
    private boolean sameSelection(OperationContext context) {
        if(!selected() || !sameValue() || !node.equals(model.getContext().getNodeCode()) || !group.equals(model.getContext().getPk_group())
                || !operationOrg.equals(groupNode(node)?group:model.getContext().getPk_org()) || !node.equals(context.getNodeCode())
                || !group.equals(context.getGroupId()) || !operationOrg.equals(context.getOrgId())) return false;
        ArchiveTreeSaveDraft draft=context.getArchiveTreeSaveDraft();
        if(draft==null || !Objects.equals(code,draft.getCode()) || !Objects.equals(parent,draft.getParent())
                || !Objects.equals(warehouse,draft.getWarehouse())) return false;
        BusinessObjectRef root=null;
        for(BusinessObjectRef ref:context.getObjects()) {
            if(!domain.equals(ref.getObjectType())) return false;
            if(ref.getParentClientObjectId()==null) { if(root!=null) return false;root=ref; }
        }
        return root!=null && Objects.equals(pk,root.getObjectPk()) && owner.equals(root.getDesiredOwnerOrg());
    }
    private static BusinessException unknown(String id) { return new BusinessException("无法确认树形档案保存结果，请按请求号核对："+id); }
    static boolean groupNode(String node) {
        return "10140CCLG".equals(node) || "10140SCLG".equals(node) || "10140MCLG".equals(node);
    }
    static String domain(String node) {
        if("10140CCLG".equals(node)||"10140CCLO".equals(node)) return "BD_CUSTCLASS";
        if("10140SCLG".equals(node)||"10140SCLO".equals(node)) return "BD_SUPPLIERCLASS";
        if("10140MCLG".equals(node)||"10140MCLO".equals(node)) return "BD_MARBASCLASS";
        if("10140RACK".equals(node)) return "BD_RACK";return null;
    }
    static boolean matches(String domain,SuperVO row) {
        return "BD_CUSTCLASS".equals(domain) && row.getClass()==CustClassVO.class
            || "BD_SUPPLIERCLASS".equals(domain) && row.getClass()==SupplierclassVO.class
            || "BD_MARBASCLASS".equals(domain) && row.getClass()==MarBasClassVO.class
            || "BD_RACK".equals(domain) && row.getClass()==RackVO.class;
    }
    static String pk(SuperVO row) {
        if(row instanceof CustClassVO) return ((CustClassVO)row).getPk_custclass();
        if(row instanceof SupplierclassVO) return ((SupplierclassVO)row).getPk_supplierclass();
        if(row instanceof MarBasClassVO) return ((MarBasClassVO)row).getPk_marbasclass();
        if(row instanceof RackVO) return ((RackVO)row).getPk_rack();return null;
    }
    static String field(SuperVO row,String name) {
        if(row instanceof CustClassVO) { CustClassVO v=(CustClassVO)row;
            if("pk_group".equals(name)) return v.getPk_group();if("pk_org".equals(name)) return v.getPk_org();
            if("code".equals(name)) return v.getCode();if("parent".equals(name)) return v.getParent_id();if("warehouse".equals(name)) return null;
        } else if(row instanceof SupplierclassVO) { SupplierclassVO v=(SupplierclassVO)row;
            if("pk_group".equals(name)) return v.getPk_group();if("pk_org".equals(name)) return v.getPk_org();
            if("code".equals(name)) return v.getCode();if("parent".equals(name)) return v.getParent_id();if("warehouse".equals(name)) return null;
        } else if(row instanceof MarBasClassVO) { MarBasClassVO v=(MarBasClassVO)row;
            if("pk_group".equals(name)) return v.getPk_group();if("pk_org".equals(name)) return v.getPk_org();
            if("code".equals(name)) return v.getCode();if("parent".equals(name)) return v.getPk_parent();if("warehouse".equals(name)) return null;
        } else if(row instanceof RackVO) { RackVO v=(RackVO)row;
            if("pk_group".equals(name)) return v.getPk_group();if("pk_org".equals(name)) return v.getPk_org();
            if("code".equals(name)) return v.getCode();if("parent".equals(name)) return v.getPk_parent();if("warehouse".equals(name)) return v.getPk_stordoc();
        }
        return null;
    }
}
