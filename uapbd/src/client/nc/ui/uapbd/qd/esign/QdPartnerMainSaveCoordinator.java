package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.uif2.UIState;
import nc.ui.uif2.model.AbstractAppModel;
import nc.vo.bd.cust.CustSupBusiException;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PartnerMainSaveCommand;
import nc.vo.uapbd.qd.esign.PartnerMainSaveDraft;

/** Authorizes the validated native editor value at the final main-card save call. */
final class QdPartnerMainSaveCoordinator implements OperationContextProvider,AuthorizedRequestConsumer {
    private final QdExplicitSignatureAction action=new QdExplicitSignatureAction();
    private final ActionEvent event=new ActionEvent(action,ActionEvent.ACTION_PERFORMED,"Save");
    private final AbstractAppModel model;
    private final SuperVO value;
    private final String node,group,org,domain,operation,pk,owner,parent;
    private OperationContext authorized;
    private String dispatched;

    static SuperVO save(final AbstractAppModel model,final SuperVO value,final Map<String,Object> extensions,final boolean skipTaxMatch)
            throws BusinessException {
        return QdPartnerBatchDispatcher.onEdt(new Callable<SuperVO>() {
            public SuperVO call() throws Exception {
                return new QdPartnerMainSaveCoordinator(model,value).execute(new PartnerMainSaveCommand(value,extensions,skipTaxMatch));
            }
        });
    }

    private QdPartnerMainSaveCoordinator(AbstractAppModel model,SuperVO value) throws BusinessException {
        if(model==null || model.getContext()==null || !(value instanceof CustomerVO || value instanceof SupplierVO))
            throw new BusinessException("无法确定客户或供应商主档的原生保存入口。");
        this.model=model;
        this.value=value;
        boolean customer=value instanceof CustomerVO;
        node=model.getContext().getNodeCode();
        group=model.getContext().getPk_group();
        org=QdPartnerContextOrganizations.operationOrganization(model,"主档保存");
        domain=customer?"BD_CUSTOMER":"BD_SUPPLIER";
        pk=value.getPrimaryKey();
        owner=text(value,"pk_org");
        parent=parent(value);
        operation=pk==null?"INSERT":"UPDATE";
        if(customer?!("10140CUG".equals(node) || "10140CUO".equals(node)):!("10140SUG".equals(node) || "10140SUO".equals(node)))
            throw new BusinessException("客户或供应商主档与功能节点不一致。");
        if(group==null || owner==null) throw new BusinessException("无法确定客户或供应商的集团和所属组织。");
        if(pk==null && !group.equals(org))
            throw new BusinessException("业务单元新增会自动分配组织及页签，该保存入口仍需核实原生提交范围。");
        if(!sameSelection()) throw new BusinessException("档案、所属组织或操作组织已变化。");
    }

    private SuperVO execute(PartnerMainSaveCommand command) throws BusinessException {
        ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);
        signing.setRequestConsumer(this);
        SignatureLifecycleInterceptor lifecycle=action.install(signing);
        boolean accepted=false;
        Throwable failure=null;
        try {
            if(!lifecycle.beforeDoAction(action,event)) throw new BusinessException("客户或供应商保存已取消或授权未通过。");
            accepted=true;
            if(authorized==null || !isAvailable(authorized) || !sameSelection())
                throw new BusinessException("客户或供应商主档保存范围已变化。");
            dispatched=authorized.getRequestId();
            OperationResponse response=NCLocator.getInstance().lookup(IOperationService.class).execute(dispatched,command);
            if(response==null || response.isReplayed() || !dispatched.equals(response.getRequestId())
                || response.getBusinessResult()==null || response.getBusinessResult().getClass()!=value.getClass())
                throw new BusinessException("无法确认主档保存结果。");
            SuperVO saved=(SuperVO)response.getBusinessResult();
            if(saved.getPrimaryKey()==null || pk!=null && !pk.equals(saved.getPrimaryKey())
                || !group.equals(text(saved,"pk_group")) || !owner.equals(text(saved,"pk_org")))
                throw new BusinessException("主档保存结果与授权不一致。");
            lifecycle.afterDoActionSuccessed(action,event);
            return saved;
        } catch(BusinessException error) {
            // The native editor handles this exception and offers its existing tax-match choices.
            BusinessException reported=dispatched==null || error instanceof CustSupBusiException?error:
                new BusinessException("主档保存未确认，请核对请求号："+dispatched,error);
            failure=reported;
            if(accepted) lifecycle.afterDoActionFailed(action,event,reported);
            throw reported;
        } catch(RuntimeException error) {
            failure=error;
            if(accepted) lifecycle.afterDoActionFailed(action,event,error);
            throw error;
        } catch(Error error) {
            failure=error;
            throw error;
        } finally {
            signing.finishLifecycle(signing.lifecycleGeneration(),action,event,failure);
        }
    }

    public OperationContext createContext(Action source,ActionEvent actual) throws BusinessException {
        if(source!=action || actual!=event || !sameSelection()) throw new BusinessException("客户或供应商保存选择已变化。");
        BusinessObjectRef root=new BusinessObjectRef(UUID.randomUUID().toString(),domain,pk,text(value,"code"),null,owner);
        return new OperationContext(null,null,group,org,node,"BD_CUSTOMER".equals(domain)?"客户":"供应商","Save","保存",
            operation,null,Collections.singletonList(root),OperationContext.OWNER_POLICY_PROTOCOL,null,null,new PartnerMainSaveDraft(parent,false));
    }

    public boolean isAvailable(OperationContext context) {
        if(context==null || !group.equals(context.getGroupId()) || !org.equals(context.getOrgId()) || !node.equals(context.getNodeCode())
            || !"Save".equals(context.getButtonCode()) || !operation.equals(context.getOperationType())
            || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) || context.getPartnerMainSaveDraft()==null
            || !Objects.equals(parent,context.getPartnerMainSaveDraft().getParent())) return false;
        BusinessObjectRef root=null;
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getParentClientObjectId()==null) {
            if(root!=null) return false;
            root=ref;
        }
        return root!=null && domain.equals(root.getObjectType()) && Objects.equals(pk,root.getObjectPk())
            && owner.equals(root.getDesiredOwnerOrg());
    }

    public boolean bind(OperationContext context) {
        if(authorized!=null || !isAvailable(context) || context.getRequestId()==null || !sameSelection()) return false;
        authorized=context;
        return true;
    }
    public boolean wasDispatched(String id) { return id!=null && id.equals(dispatched); }
    public void release(String id) { if(authorized!=null && authorized.getRequestId().equals(id)) { authorized=null;dispatched=null; } }
    public Container getParentContainer() { return model.getContext().getEntranceUI(); }
    public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }

    private boolean sameSelection() {
        try {
            if(group==null || !group.equals(model.getContext().getPk_group()) || !group.equals(text(value,"pk_group"))
                || !node.equals(model.getContext().getNodeCode()) || !org.equals(QdPartnerContextOrganizations.operationOrganization(model,"主档保存"))
                || !owner.equals(text(value,"pk_org")) || !Objects.equals(parent,parent(value)) || !Objects.equals(pk,value.getPrimaryKey()))
                return false;
            if(pk==null) return model.getUiState()==UIState.ADD;
            Object selected=model.getSelectedData();
            return selected instanceof SuperVO && selected.getClass()==value.getClass() && pk.equals(((SuperVO)selected).getPrimaryKey());
        } catch(BusinessException error) { return false; }
    }

    private static String parent(SuperVO value) {
        return PartnerMainSaveDraft.normalizeParent(text(value,value instanceof CustomerVO?"pk_customer_main":"pk_supplier_main"));
    }
    private static String text(SuperVO value,String field) {
        Object raw=value.getAttributeValue(field);
        return raw instanceof String?(String)raw:null;
    }
}
