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
import nc.ui.bd.pub.action.DeleteWithProgressBarAction;
import nc.ui.bd.pub.actions.ManageModeActionInterceptor;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.model.AbstractAppModel;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.bd.stordoc.StordocVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteCommand;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Candidate warehouse delete preserving native data checks, confirmation and per-row commits. */
public final class QdWarehouseDeleteAction extends DeleteWithProgressBarAction
        implements OperationContextProvider,AuthorizedRequestConsumer {
    private static final long serialVersionUID=1L;
    private final QdWarehouseModelService service;
    private final ManageModeActionInterceptor manageMode=new ManageModeActionInterceptor();
    private QdWarehouseModelDataManager operationScope;
    private volatile ActionEvent activeEvent;
    private volatile OperationContext authorized;
    private volatile String dispatched;
    private volatile String attemptRequestId;
    private volatile Map<String,SuperVO> selectedRows;
    private volatile MaterialPageDeleteResult result;
    private volatile boolean workerStarted;

    public QdWarehouseDeleteAction(QdWarehouseModelService service) {
        if(service==null) throw new IllegalArgumentException("Warehouse service is required");
        this.service=service;setInterceptor(manageMode);
    }
    public void setOperationScope(QdWarehouseModelDataManager value) { operationScope=value; }
    @Override public void setModel(AbstractAppModel value) { super.setModel(value);manageMode.setModel(value); }

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
            monitorStarted=true;
            signing.setContextProvider(this);signing.setRequestConsumer(this);
            CompositeActionInterceptor chain=new CompositeActionInterceptor();chain.addInterceptor(signing);
            setInterceptor(chain);outer=SignatureLifecycleInstaller.install(this,signing);
            if(!outer.beforeDoAction(this,event)) {
                super.doAfterFailure(event,new BusinessException("仓库删除已取消。"));monitorStarted=false;return;
            }
            accepted=true;
            if(authorized==null || !sameSelection(authorized)) throw new BusinessException("仓库选择或操作组织已变化，请重新删除。");
            final SignatureLifecycleInterceptor lifecycle=outer;
            workerStarted=true;
            try {
                new SwingWorker<Void,Void>() {
                    @Override protected Void doInBackground() throws Exception { doAction(event);return null; }
                    @Override protected void done() { finishWorker(event,original,signing,lifecycle,this); }
                }.execute();
            } catch(RuntimeException error) { workerStarted=false;throw error; }
        } catch(Exception error) {
            if(accepted && outer!=null) outer.afterDoActionFailed(this,event,error);
            if(monitorStarted) super.doAfterFailure(event,error);
            report(error);
        } catch(Error error) {
            if(accepted && outer!=null) outer.afterDoActionFailed(this,event,error);
            if(monitorStarted) super.doAfterFailure(event,error);
            throw error;
        } finally {
            if(!workerStarted) {
                signing.finishLifecycle(signing.lifecycleGeneration(),this,event,null);
                setInterceptor(original);clearAttempt();
            }
        }
    }
    @Override public void doAction(ActionEvent event) throws Exception {
        OperationContext request=authorized;Map<String,SuperVO> rows=selectedRows;
        if(event!=activeEvent || !workerStarted || request==null || rows==null || dispatched!=null)
            throw new BusinessException("仓库删除缺少一次性授权。");
        dispatched=request.getRequestId();
        MaterialPageDeleteResult receipt=NCLocator.getInstance().lookup(IMaterialPageDeleteService.class)
            .execute(request.getRequestId(),new MaterialPageDeleteCommand(rows));
        QdMaterialOrgInfoDeleteAction.validateReceipt(request,receipt);result=receipt;
    }
    private void finishWorker(ActionEvent event,ActionInterceptor original,ElectronicSignatureActionInterceptor signing,
            SignatureLifecycleInterceptor lifecycle,SwingWorker<Void,Void> worker) {
        Throwable failure=null;
        try {
            worker.get();
            lifecycle.afterDoActionSuccessed(this,event);
            if(original!=null) original.afterDoActionSuccessed(this,event);
            showResult(event);
        } catch(InterruptedException error) {
            Thread.currentThread().interrupt();failure=error;finishFailed(event,original,lifecycle,error);
        } catch(ExecutionException error) {
            failure=error.getCause()==null?error:error.getCause();finishFailed(event,original,lifecycle,failure);
        } catch(RuntimeException error) { failure=error;finishFailed(event,original,lifecycle,error); }
        catch(Error error) { failure=error;finishFailed(event,original,lifecycle,error);throw error; }
        finally {
            signing.finishLifecycle(signing.lifecycleGeneration(),this,event,failure);
            setInterceptor(original);clearAttempt();
        }
    }
    private void finishFailed(ActionEvent event,ActionInterceptor original,SignatureLifecycleInterceptor lifecycle,Throwable failure) {
        try {
            boolean handled=super.doAfterFailure(event,failure);
            if(handled) handled=lifecycle.afterDoActionFailed(this,event,failure);
            if(handled && original!=null) handled=original.afterDoActionFailed(this,event,failure);
            if(handled) report(failure);
        } finally {
            // Even if a native failure callback throws, the worker's outer finally releases the attempt.
        }
    }
    private void showResult(ActionEvent event) {
        MaterialPageDeleteResult receipt=result;Map<String,SuperVO> rows=selectedRows;
        try {
            if(receipt==null || rows==null) throw new BusinessException("无法确认仓库删除结果，请重新查询。");
            List<SuperVO> removed=new ArrayList<SuperVO>();int success=0,failed=0,skipped=0,unknown=0;
            for(MaterialPageDeleteResult.Unit unit:receipt.getUnits()) {
                if("SUCCESS".equals(unit.getState())) {
                    success++;SuperVO row=rows.get(unit.getClientObjectId());
                    if(row!=null && model().findBusinessData(row)>=0) removed.add(row);
                } else if("FAILED".equals(unit.getState())) failed++;
                else if("SKIPPED".equals(unit.getState())) skipped++;
                else unknown++;
            }
            if(!removed.isEmpty()) model().directlyDelete(removed.toArray(new Object[removed.size()]));
            if("SUCCESS".equals(receipt.getState())) super.doAfterSuccess(event);
            else {
                super.doAfterFailure(event,new BusinessException("仓库删除未全部执行成功。"));
                JOptionPane.showMessageDialog(getParentContainer(),"删除成功 "+success+" 条，失败 "+failed+" 条，未执行 "+skipped
                    +" 条，待核对 "+unknown+" 条。请求号："+receipt.getRequestId(),"仓库删除结果",JOptionPane.WARNING_MESSAGE);
            }
        } catch(Exception error) {
            super.doAfterFailure(event,error);
            JOptionPane.showMessageDialog(getParentContainer(),"删除结果已返回，但界面更新失败，请重新查询核对。请求号："
                +(receipt==null?"未知":receipt.getRequestId()),"仓库删除结果",JOptionPane.WARNING_MESSAGE);
        }
    }
    public OperationContext createContext(Action action,ActionEvent event) throws BusinessException {
        if(action!=this || event!=activeEvent || model().getService()!=service) throw new BusinessException("仓库删除上下文无效。");
        String node=model().getContext().getNodeCode(),group=model().getContext().getPk_group();
        String org=operationScope==null?null:operationScope.requireOperationOrganization();
        if(!"10140WARH".equals(node) || group==null || org==null) throw new BusinessException("无法确定仓库节点或操作组织。");
        Map<String,SuperVO> rows=new LinkedHashMap<String,SuperVO>();List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        Set<String> keys=new HashSet<String>();
        for(Object item:selection()) {
            if(!(item instanceof StordocVO)) throw new BusinessException("无法确定待删除的仓库。");
            StordocVO value=(StordocVO)item;String pk=value.getPk_stordoc();
            if(pk==null || !group.equals(value.getPk_group()) || !keys.add(pk)) throw new BusinessException("选择包含其他集团或重复的仓库。");
            String id=UUID.randomUUID().toString();rows.put(id,value);
            refs.add(new BusinessObjectRef(id,"BD_STORDOC",pk,value.getCode(),null));
        }
        if(refs.isEmpty()) throw new BusinessException("请选择待删除的仓库。");
        selectedRows=rows;
        return new OperationContext(null,null,group,org,node,"仓库","Delete","删除","DELETE",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    public boolean isAvailable(OperationContext context) {
        if(context==null || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
                || !MaterialPageDeleteResult.isWarehouse(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
                || context.getGroupId()==null || context.getOrgId()==null || context.getObjects().isEmpty()) return false;
        for(BusinessObjectRef ref:context.getObjects()) if(!"BD_STORDOC".equals(ref.getObjectType()) || ref.getObjectPk()==null) return false;
        return true;
    }
    public boolean bind(OperationContext context) {
        if(authorized!=null || !isAvailable(context) || context.getRequestId()==null || !sameSelection(context)) return false;
        authorized=context;attemptRequestId=context.getRequestId();return true;
    }
    public boolean wasDispatched(String id) { return id!=null && id.equals(dispatched); }
    public void release(String id) { if(authorized!=null && authorized.getRequestId().equals(id)) { authorized=null;dispatched=null; } }
    public Container getParentContainer() { return model().getContext().getEntranceUI(); }
    public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }
    private boolean sameSelection(OperationContext context) {
        if(!isAvailable(context) || selectedRows==null || model().getContext()==null
                || !context.getGroupId().equals(model().getContext().getPk_group())
                || !context.getNodeCode().equals(model().getContext().getNodeCode())) return false;
        try { if(operationScope==null || !context.getOrgId().equals(operationScope.requireOperationOrganization())) return false; }
        catch(BusinessException error) { return false; }
        return sameRows(context,selectedRows,selection());
    }
    static boolean sameRows(OperationContext context,Map<String,SuperVO> rows,Object[] selected) {
        if(context==null || context.getGroupId()==null || rows==null || selected==null || rows.size()!=context.getObjects().size()
                || selected.length!=rows.size()) return false;
        Set<String> ids=new HashSet<String>(),pks=new HashSet<String>();
        for(int i=0;i<selected.length;i++) {
            BusinessObjectRef ref=context.getObjects().get(i);
            if(ref==null || ref.getClientObjectId()==null || ref.getObjectPk()==null) return false;
            SuperVO bound=rows.get(ref.getClientObjectId());
            if(!ids.add(ref.getClientObjectId()) || !pks.add(ref.getObjectPk()) || !(bound instanceof StordocVO)
                    || !(selected[i] instanceof StordocVO)) return false;
            StordocVO before=(StordocVO)bound,after=(StordocVO)selected[i];
            if(!ref.getObjectPk().equals(before.getPk_stordoc()) || !ref.getObjectPk().equals(after.getPk_stordoc())
                    || !context.getGroupId().equals(before.getPk_group()) || !context.getGroupId().equals(after.getPk_group())) return false;
        }
        return true;
    }
    private Object[] selection() {
        Integer[] indexes=model().getSelectedOperaRows();
        Object[] rows=indexes!=null && indexes.length>0?model().getSelectedOperaDatas():new Object[]{model().getSelectedData()};
        return rows==null?new Object[0]:rows;
    }
    private BillManageModel model() { return (BillManageModel)getModel(); }
    private void report(Throwable error) {
        String message=error==null?"仓库删除结果未知，请重新查询核对。":error.getMessage();
        if(message==null || message.trim().isEmpty()) message="仓库删除结果未知，请重新查询核对。";
        if(attemptRequestId!=null) message=message+" 请重新查询并核对请求号："+attemptRequestId;
        if(getExceptionHandler()!=null) processExceptionHandler(new BusinessException(message));
        else JOptionPane.showMessageDialog(getParentContainer(),message,"仓库删除",JOptionPane.WARNING_MESSAGE);
    }
    private void clearAttempt() { activeEvent=null;authorized=null;dispatched=null;attemptRequestId=null;selectedRows=null;result=null;workerStarted=false; }
}
