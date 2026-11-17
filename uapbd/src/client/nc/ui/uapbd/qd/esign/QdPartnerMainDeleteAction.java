package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IMaterialPageDeleteService;
import nc.ui.bd.pub.actions.BDAnsyDelAction;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteCommand;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Candidate batch master delete, with one authorization and one independent CMT per selected archive. */
public final class QdPartnerMainDeleteAction extends BDAnsyDelAction implements OperationContextProvider,AuthorizedRequestConsumer {
    private static final long serialVersionUID=1L;
    private volatile ActionEvent activeEvent;private volatile OperationContext authorized;
    private volatile String dispatched,attemptRequestId;private volatile Map<String,SuperVO> selectedByPk,boundRows;
    private volatile MaterialPageDeleteResult result;private volatile boolean workerStarted;

    @Override public void actionPerformed(final ActionEvent event) {
        if(activeEvent!=null) return;
        activeEvent=event;workerStarted=false;
        final ActionInterceptor original=getInterceptor();
        final ElectronicSignatureActionInterceptor signing=new ElectronicSignatureActionInterceptor();
        SignatureLifecycleInterceptor outer=null;boolean monitorStarted=false,accepted=false;
        try {
            beforeDoAction();
            if(original!=null && !original.beforeDoAction(this,event)) return;
            if(!super.beforeStartDoAction(event)) return;
            monitorStarted=true;signing.setContextProvider(this);signing.setRequestConsumer(this);
            CompositeActionInterceptor chain=new CompositeActionInterceptor();chain.addInterceptor(signing);
            setInterceptor(chain);outer=SignatureLifecycleInstaller.install(this,signing);
            if(!outer.beforeDoAction(this,event)) {
                super.doAfterFailure(event,new BusinessException("主档删除已取消。"));monitorStarted=false;return;
            }
            accepted=true;
            if(authorized==null) throw new BusinessException("主档删除受控入口尚未启用。");
            if(!sameSelection(authorized)) throw new BusinessException("主档选择或操作组织已变化，请重新删除。");
            final SignatureLifecycleInterceptor lifecycle=outer;workerStarted=true;
            try {
                new SwingWorker<Void,Void>() {
                    @Override protected Void doInBackground() throws Exception { doAction(event);return null; }
                    @Override protected void done() { finishWorker(event,original,signing,lifecycle,this); }
                }.execute();
            } catch(RuntimeException error) { workerStarted=false;throw error; }
        } catch(Exception error) {
            if(accepted && outer!=null) outer.afterDoActionFailed(this,event,error);
            if(monitorStarted) super.doAfterFailure(event,error);report(error);
        } catch(Error error) {
            if(accepted && outer!=null) outer.afterDoActionFailed(this,event,error);
            if(monitorStarted) super.doAfterFailure(event,error);throw error;
        } finally {
            if(!workerStarted) { signing.finishLifecycle(signing.lifecycleGeneration(),this,event,null);setInterceptor(original);clearAttempt(); }
        }
    }

    @Override public void doAction(ActionEvent event) throws Exception {
        OperationContext request=authorized;Map<String,SuperVO> rows=boundRows;
        if(event!=activeEvent || !workerStarted || request==null || rows==null || dispatched!=null)
            throw new BusinessException("主档删除缺少一次性授权。");
        dispatched=request.getRequestId();
        MaterialPageDeleteResult receipt=NCLocator.getInstance().lookup(IMaterialPageDeleteService.class)
            .execute(dispatched,new MaterialPageDeleteCommand(rows));
        QdMaterialOrgInfoDeleteAction.validateReceipt(request,receipt);result=receipt;
    }

    private void finishWorker(ActionEvent event,ActionInterceptor original,ElectronicSignatureActionInterceptor signing,
            SignatureLifecycleInterceptor lifecycle,SwingWorker<Void,Void> worker) {
        Throwable failure=null;
        try {
            worker.get();lifecycle.afterDoActionSuccessed(this,event);
            if(original!=null) original.afterDoActionSuccessed(this,event);showResult(event);
        } catch(InterruptedException error) {
            Thread.currentThread().interrupt();failure=error;finishFailed(event,original,lifecycle,error);
        } catch(ExecutionException error) {
            failure=error.getCause()==null?error:error.getCause();finishFailed(event,original,lifecycle,failure);
        } catch(RuntimeException error) { failure=error;finishFailed(event,original,lifecycle,error); }
        catch(Error error) { failure=error;finishFailed(event,original,lifecycle,error);throw error; }
        finally { signing.finishLifecycle(signing.lifecycleGeneration(),this,event,failure);setInterceptor(original);clearAttempt(); }
    }
    private void finishFailed(ActionEvent event,ActionInterceptor original,SignatureLifecycleInterceptor lifecycle,Throwable failure) {
        boolean handled=super.doAfterFailure(event,failure);
        if(handled) handled=lifecycle.afterDoActionFailed(this,event,failure);
        if(handled && original!=null) handled=original.afterDoActionFailed(this,event,failure);
        if(handled) report(failure);
    }
    private void showResult(ActionEvent event) {
        MaterialPageDeleteResult receipt=result;Map<String,SuperVO> rows=boundRows;
        try {
            if(receipt==null || rows==null) throw new BusinessException("无法确认主档删除结果，请重新查询。");
            List<SuperVO> removed=new ArrayList<SuperVO>();int succeeded=0,failed=0,unknown=0;
            for(MaterialPageDeleteResult.Unit unit:receipt.getUnits()) {
                if("SUCCESS".equals(unit.getState())) {
                    succeeded++;SuperVO row=rows.get(unit.getClientObjectId());
                    if(row!=null && model().findBusinessData(row)>=0) removed.add(row);
                } else if("FAILED".equals(unit.getState())) failed++;else unknown++;
            }
            if(!removed.isEmpty()) model().directlyDelete(removed.toArray(new Object[removed.size()]));
            if("SUCCESS".equals(receipt.getState())) super.doAfterSuccess(event);
            else super.doAfterFailure(event,new BusinessException("主档删除未全部成功。"));
            JOptionPane.showMessageDialog(getParentContainer(),"删除成功 "+succeeded+" 条，失败 "+failed
                +" 条，待核对 "+unknown+" 条。请求号："+attemptRequestId,"主档删除结果",
                unknown>0 || failed>0?JOptionPane.WARNING_MESSAGE:JOptionPane.INFORMATION_MESSAGE);
        } catch(Exception error) {
            super.doAfterFailure(event,error);
            JOptionPane.showMessageDialog(getParentContainer(),"结果已返回，但界面更新失败，请重新查询核对。请求号："
                +(attemptRequestId==null?"未知":attemptRequestId),"主档删除",JOptionPane.WARNING_MESSAGE);
        }
    }

    public OperationContext createContext(Action action,ActionEvent event) throws BusinessException {
        if(action!=this || event!=activeEvent) throw new BusinessException("主档删除上下文无效。");
        BillManageModel model=model();String node=model.getContext().getNodeCode(),group=model.getContext().getPk_group();
        String domain=domain(node),org=QdPartnerContextOrganizations.operationOrganization(model,"主档删除");
        if(domain==null || group==null || org==null) throw new BusinessException("无法确定主档删除节点、集团或操作组织。");
        Map<String,SuperVO> selected=new LinkedHashMap<String,SuperVO>();List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        for(Object raw:selection()) {
            if(!type(domain,raw)) throw new BusinessException("选择的数据不是客户或供应商主档。");
            SuperVO row=(SuperVO)raw;String pk=row.getPrimaryKey();
            if(pk==null || !group.equals(row.getAttributeValue("pk_group")) || row.getAttributeValue("pk_org")==null
                || selected.put(pk,row)!=null) throw new BusinessException("待删除档案未保存、不属于当前集团或重复选择。");
            Object code=row.getAttributeValue("code");
            refs.add(new BusinessObjectRef(UUID.randomUUID().toString(),domain,pk,code==null?null:code.toString(),null));
        }
        if(refs.isEmpty() || refs.size()>1000) throw new BusinessException("请选择 1 至 1000 条待删除的档案。");
        selectedByPk=selected;
        return new OperationContext(null,null,group,org,node,"BD_CUSTOMER".equals(domain)?"客户":"供应商",
            "Delete","删除","DELETE",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private Object[] selection() throws BusinessException {
        Integer[] indexes=model().getSelectedOperaRows();
        Object[] rows=indexes!=null && indexes.length>0?model().getSelectedOperaDatas():new Object[]{model().getSelectedData()};
        if(rows==null || rows.length==0) throw new BusinessException("请选择待删除的档案。");return rows;
    }
    public boolean isAvailable(OperationContext context) { return accepts(context); }
    static boolean accepts(OperationContext context) {
        if(context==null || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            || context.getGroupId()==null || context.getOrgId()==null || context.getObjects().isEmpty()
            || context.getObjects().size()>1000 || !MaterialPageDeleteResult.isPartnerMain(context.getNodeCode(),context.getButtonCode(),context.getOperationType())) return false;
        String selectedDomain=domain(context.getNodeCode());Set<String> roots=new HashSet<String>(),pks=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) {
            if(ref==null || ref.getObjectPk()==null || ref.getDesiredOwnerOrg()!=null
                || !("BD_CUSTOMER".equals(ref.getObjectType()) || "BD_SUPPLIER".equals(ref.getObjectType()))) return false;
            if(ref.getParentClientObjectId()==null && (!selectedDomain.equals(ref.getObjectType())
                    || !roots.add(ref.getClientObjectId()) || !pks.add(ref.getObjectPk()))) return false;
        }
        if(roots.isEmpty()) return false;
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getParentClientObjectId()!=null && !roots.contains(ref.getParentClientObjectId())) return false;
        return true;
    }
    public boolean bind(OperationContext context) {
        if(authorized!=null || !accepts(context) || context.getRequestId()==null || !sameSelection(context)) return false;
        Map<String,SuperVO> rows=new LinkedHashMap<String,SuperVO>();
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getParentClientObjectId()==null) {
            SuperVO row=selectedByPk.get(ref.getObjectPk());if(row==null || rows.put(ref.getClientObjectId(),row)!=null) return false;
        }
        if(rows.size()!=selectedByPk.size()) return false;
        boundRows=rows;authorized=context;attemptRequestId=context.getRequestId();return true;
    }
    public boolean wasDispatched(String id) { return id!=null && id.equals(dispatched); }
    public void release(String id) { if(authorized!=null && authorized.getRequestId().equals(id)) { authorized=null;dispatched=null; } }
    public Container getParentContainer() { return model().getContext().getEntranceUI(); }
    public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }

    private boolean sameSelection(OperationContext context) {
        if(!accepts(context) || selectedByPk==null || model().getContext()==null
            || !context.getGroupId().equals(model().getContext().getPk_group())
            || !context.getNodeCode().equals(model().getContext().getNodeCode())) return false;
        try {
            if(!context.getOrgId().equals(QdPartnerContextOrganizations.operationOrganization(model(),"主档删除"))) return false;
            return sameRows(context,selectedByPk,selection());
        } catch(BusinessException error) { return false; }
    }
    static boolean sameRows(OperationContext context,Map<String,SuperVO> initial,Object[] current) {
        if(!accepts(context) || initial==null || current==null || initial.size()!=current.length) return false;
        String domain=domain(context.getNodeCode());Set<String> expected=new HashSet<String>(),selected=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getParentClientObjectId()==null) {
            SuperVO row=initial.get(ref.getObjectPk());
            if(!type(domain,row) || !ref.getObjectPk().equals(row.getPrimaryKey())
                || !context.getGroupId().equals(row.getAttributeValue("pk_group")) || !expected.add(ref.getObjectPk())) return false;
        }
        for(Object raw:current) {
            if(!type(domain,raw)) return false;SuperVO row=(SuperVO)raw;
            if(row.getPrimaryKey()==null || !context.getGroupId().equals(row.getAttributeValue("pk_group"))
                || !selected.add(row.getPrimaryKey())) return false;
        }
        return expected.size()==initial.size() && expected.equals(selected);
    }
    private static String domain(String node) {
        if("10140CUG".equals(node) || "10140CUO".equals(node)) return "BD_CUSTOMER";
        if("10140SUG".equals(node) || "10140SUO".equals(node)) return "BD_SUPPLIER";return null;
    }
    private static boolean type(String domain,Object value) {
        return value!=null && ("BD_CUSTOMER".equals(domain)?value.getClass()==CustomerVO.class:
            "BD_SUPPLIER".equals(domain) && value.getClass()==SupplierVO.class);
    }
    private BillManageModel model() { return (BillManageModel)getModel(); }
    private void report(Throwable error) {
        String message=error==null?null:error.getMessage();
        if(message==null || message.trim().isEmpty()) message="主档删除结果未知，请重新查询核对。";
        if(attemptRequestId!=null) message=message+" 请求号："+attemptRequestId;
        if(getExceptionHandler()!=null) processExceptionHandler(new BusinessException(message));
        else JOptionPane.showMessageDialog(getParentContainer(),message,"主档删除",JOptionPane.WARNING_MESSAGE);
    }
    private void clearAttempt() {
        activeEvent=null;authorized=null;dispatched=null;attemptRequestId=null;selectedByPk=null;boundRows=null;result=null;workerStarted=false;
    }
}
