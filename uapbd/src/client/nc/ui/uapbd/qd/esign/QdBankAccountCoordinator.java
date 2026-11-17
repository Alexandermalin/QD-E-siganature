package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.io.Serializable;
import java.util.Collections;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.funcnode.ui.FuncletInitData;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.bd.bankacc.cust.model.CustBankaccAppModel;
import nc.ui.bd.cust.baseinfo.model.CustBaseInfoModel;
import nc.ui.bd.supplier.baseinfo.model.SupplierBaseInfoModel;
import nc.ui.uif2.UIState;
import nc.ui.uif2.model.AbstractAppModel;
import nc.vo.bd.bankaccount.BankAccbasVO;
import nc.vo.bd.bankaccount.cust.CustBankaccUnionVO;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.cust.CustbankVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BankAccountDeleteCommand;
import nc.vo.uapbd.qd.esign.BankAccountSaveCommand;
import nc.vo.uapbd.qd.esign.BankAccountStateCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;

/** Preserves the native popup and authorizes the operation at the final model service call. */
final class QdBankAccountCoordinator implements OperationContextProvider,AuthorizedRequestConsumer {
    private final CustBankaccAppModel model;private final AbstractAppModel parent;
    private final QdExplicitSignatureAction signingAction=new QdExplicitSignatureAction();
    private final ActionEvent event;private final boolean customer,insert;
    private final String group,org,node,archive,relation,account,version,operation,button,label,defaultCurrency;
    private final CustBankaccUnionVO value;
    private OperationContext authorized;private String dispatched;

    QdBankAccountCoordinator(CustBankaccAppModel model,String operation,ActionEvent event,
            CustBankaccUnionVO selected) throws BusinessException {
        this(model,operation,event,selected,null);
    }
    QdBankAccountCoordinator(CustBankaccAppModel model,String operation,ActionEvent event,
            CustBankaccUnionVO selected,String defaultCurrency) throws BusinessException {
        if(model==null || model.getContext()==null || selected==null)
            throw new BusinessException("银行账户缺少原生操作上下文。");
        this.model=model;this.value=selected;this.operation=operation;this.defaultCurrency=defaultCurrency;
        this.event=event==null?new ActionEvent(signingAction,ActionEvent.ACTION_PERFORMED,operation):event;
        insert="INSERT".equals(operation);
        if(insert) { button="Add";label="保存"; }
        else if("UPDATE".equals(operation)) { button="Edit";label=defaultCurrency==null?"保存":"设置默认账户"; }
        else if("DELETE".equals(operation)) { button="Delete";label="删除"; }
        else if("ENABLE".equals(operation)) { button="Enable";label="启用"; }
        else if("DISABLE".equals(operation)) { button="Disable";label="停用"; }
        else throw new BusinessException("银行账户操作不正确。");
        Object init=model.getContext().getInitData();Object source=init instanceof FuncletInitData?((FuncletInitData)init).getInitData():null;
        if(!(source instanceof CustBaseInfoModel) && !(source instanceof SupplierBaseInfoModel))
            throw new BusinessException("银行账户父档案入口尚未接入。");
        parent=(AbstractAppModel)source;node=model.getContext().getNodeCode();
        customer="10140CBA".equals(node);
        if(!customer && !"10140SBA".equals(node) || customer && !(parent instanceof CustBaseInfoModel)
            || !customer && !(parent instanceof SupplierBaseInfoModel)) throw new BusinessException("银行账户节点与父档案类型不一致。");
        String parentNode=parent.getContext()==null?null:parent.getContext().getNodeCode();
        if(customer && !("10140CUG".equals(parentNode) || "10140CUO".equals(parentNode))
            || !customer && !("10140SUG".equals(parentNode) || "10140SUO".equals(parentNode)))
            throw new BusinessException("银行账户父档案节点尚未接入。");
        group=parent.getContext().getPk_group();org=QdPartnerContextOrganizations.operationOrganization(parent);
        Object parentSelected=parent.getSelectedData();
        if(customer && parentSelected instanceof CustomerVO) archive=((CustomerVO)parentSelected).getPk_customer();
        else if(!customer && parentSelected instanceof SupplierVO) archive=((SupplierVO)parentSelected).getPk_supplier();
        else throw new BusinessException("银行账户父档案选择不正确。");
        CustbankVO link=selected.getCustbankVO();BankAccbasVO master=selected.getBankaccbasVO();
        if(link==null || master==null || group==null || org==null || archive==null || !archive.equals(link.getPk_cust())
            || !(customer?Integer.valueOf(1):Integer.valueOf(3)).equals(link.getAccclass())
            || !group.equals(master.getPk_group())
            || (insert?link.getPk_custbank()!=null || link.getPk_bankaccbas()!=null || master.getPk_bankaccbas()!=null
                :link.getPk_custbank()==null || link.getPk_bankaccbas()==null
                    || !link.getPk_bankaccbas().equals(master.getPk_bankaccbas()) || master.getTs()==null))
            throw new BusinessException("银行账户、关联档案或集团不一致，请刷新后重试。");
        relation=link.getPk_custbank();account=master.getPk_bankaccbas();version=insert?null:master.getTs().toString();
        if(!sameSelection()) throw new BusinessException("银行账户或父档案选择已变化。");
    }

    CustBankaccUnionVO execute() throws BusinessException { return (CustBankaccUnionVO)invoke(); }
    CustBankaccUnionVO[] executeDefault() throws BusinessException { return (CustBankaccUnionVO[])invoke(); }
    private Serializable invoke() throws BusinessException {
        ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);signing.setRequestConsumer(this);
        SignatureLifecycleInterceptor outer=null;boolean accepted=false;Throwable failure=null;
        try {
            outer=signingAction.install(signing);
            if(!outer.beforeDoAction(signingAction,event)) throw new BusinessException("银行账户"+label+"已取消或授权未通过。");
            accepted=true;OperationContext request=authorized;
            if(request==null || !isAvailable(request) || !sameSelection() || dispatched!=null)
                throw new BusinessException("银行账户选择或操作组织已变化，请重新操作。");
            dispatched=request.getRequestId();
            OperationResponse response=NCLocator.getInstance().lookup(IOperationService.class).execute(dispatched,
                command());
            if(response==null || response.isReplayed() || !dispatched.equals(response.getRequestId()))
                throw new BusinessException("无法确认银行账户"+label+"结果，请核对请求号："+dispatched);
            Serializable result=response.getBusinessResult();
            if(!matchesResult(result))
                throw new BusinessException("银行账户"+label+"结果与选择不一致，请核对请求号："+dispatched);
            outer.afterDoActionSuccessed(signingAction,event);return result;
        } catch(BusinessException error) {
            BusinessException reported=dispatched==null?error:new BusinessException("银行账户"+label+"未确认，请重新查询并核对请求号："+dispatched,error);
            failure=reported;if(accepted && outer!=null) outer.afterDoActionFailed(signingAction,event,reported);throw reported;
        } catch(RuntimeException error) {
            BusinessException reported=new BusinessException(dispatched==null?"银行账户"+label+"未完成。":"银行账户"+label+"未确认，请核对请求号："+dispatched,error);
            failure=reported;if(accepted && outer!=null) outer.afterDoActionFailed(signingAction,event,reported);throw reported;
        } catch(Error error) { failure=error;throw error; }
        finally { signing.finishLifecycle(signing.lifecycleGeneration(),signingAction,event,failure);authorized=null;dispatched=null; }
    }
    private boolean matchesResult(Serializable raw) {
        if(defaultCurrency!=null) {
            if(!(raw instanceof CustBankaccUnionVO[])) return false;
            boolean selected=false;
            for(CustBankaccUnionVO row:(CustBankaccUnionVO[])raw) {
                if(!matchesArchive(row)) return false;
                if(account.equals(row.getBankaccbasVO().getPk_bankaccbas())) selected=true;
            }
            return selected;
        }
        if(!(raw instanceof CustBankaccUnionVO)) return false;
        CustBankaccUnionVO result=(CustBankaccUnionVO)raw;
        return matchesArchive(result) && (insert || account.equals(result.getBankaccbasVO().getPk_bankaccbas()))
            && (insert || "UPDATE".equals(operation) || relation.equals(result.getCustbankVO().getPk_custbank()));
    }
    private boolean matchesArchive(CustBankaccUnionVO row) {
        return row!=null && row.getCustbankVO()!=null && row.getBankaccbasVO()!=null
            && archive.equals(row.getCustbankVO().getPk_cust()) && row.getCustbankVO().getPk_custbank()!=null
            && (customer?Integer.valueOf(1):Integer.valueOf(3)).equals(row.getCustbankVO().getAccclass())
            && row.getBankaccbasVO().getPk_bankaccbas()!=null;
    }
    private Serializable command() {
        if(insert || "UPDATE".equals(operation)) return new BankAccountSaveCommand(value,version,defaultCurrency);
        if("DELETE".equals(operation)) return new BankAccountDeleteCommand(value,version);
        return new BankAccountStateCommand(relation,account,version);
    }
    public OperationContext createContext(Action source,ActionEvent value) throws BusinessException {
        if(source!=signingAction || value!=event || !sameSelection()) throw new BusinessException("银行账户选择或操作组织已变化。");
        String id=defaultCurrency==null?UUID.randomUUID().toString():"bank-default:"+defaultCurrency;
        BusinessObjectRef selected=new BusinessObjectRef(id,customer?"BD_CUSTOMER_BANK":"BD_SUPPLIER_BANK",
            insert?archive:relation,null,null);
        return new OperationContext(null,null,group,org,node,customer?"客户银行账户":"供应商银行账户",button,
            label,operation,null,Collections.singletonList(selected),OperationContext.OWNER_POLICY_PROTOCOL);
    }
    public boolean isAvailable(OperationContext context) {
        if(context==null || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            || !node.equals(context.getNodeCode()) || !group.equals(context.getGroupId()) || !org.equals(context.getOrgId())
            || !button.equals(context.getButtonCode()) || !operation.equals(context.getOperationType())) return false;
        BusinessObjectRef root=null;
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getParentClientObjectId()==null) {
            if(root!=null) return false;root=ref;
        }
        return root!=null && (insert?archive:relation).equals(root.getObjectPk())
            && (customer?"BD_CUSTOMER_BANK":"BD_SUPPLIER_BANK").equals(root.getObjectType())
            && (defaultCurrency==null || ("bank-default:"+defaultCurrency).equals(root.getClientObjectId())
                || root.getClientObjectId().startsWith("bank-default:"+defaultCurrency+":"));
    }
    public boolean bind(OperationContext context) { if(authorized!=null || !isAvailable(context) || context.getRequestId()==null || !sameSelection()) return false;authorized=context;return true; }
    public boolean wasDispatched(String id) { return id!=null && id.equals(dispatched); }
    public void release(String id) { if(authorized!=null && authorized.getRequestId().equals(id)) { authorized=null;dispatched=null; } }
    public Container getParentContainer() { return model.getContext().getEntranceUI(); }
    public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }
    private boolean sameSelection() {
        try {
            if(!node.equals(model.getContext().getNodeCode()) || !group.equals(parent.getContext().getPk_group())
                || !org.equals(QdPartnerContextOrganizations.operationOrganization(parent)) || !model.canMaintainCustAcc()) return false;
            Object parentSelected=parent.getSelectedData();
            String current=customer && parentSelected instanceof CustomerVO?((CustomerVO)parentSelected).getPk_customer()
                :!customer && parentSelected instanceof SupplierVO?((SupplierVO)parentSelected).getPk_supplier():null;
            if(!archive.equals(current)) return false;
            if(insert) return model.getUiState()==UIState.ADD && value.getCustbankVO().getPk_custbank()==null
                && value.getBankaccbasVO().getPk_bankaccbas()==null && archive.equals(value.getCustbankVO().getPk_cust());
            Object raw=model.getSelectedData();if(!(raw instanceof CustBankaccUnionVO)) return false;
            CustBankaccUnionVO selected=(CustBankaccUnionVO)raw;CustbankVO link=selected.getCustbankVO();BankAccbasVO master=selected.getBankaccbasVO();
            return link!=null && master!=null && relation.equals(link.getPk_custbank()) && archive.equals(link.getPk_cust())
                && account.equals(link.getPk_bankaccbas()) && account.equals(master.getPk_bankaccbas()) && group.equals(master.getPk_group())
                && master.getTs()!=null && version.equals(master.getTs().toString());
        } catch(BusinessException error) { return false; }
    }
}
