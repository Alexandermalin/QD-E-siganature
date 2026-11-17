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
import nc.ui.uif2.model.AbstractAppModel;
import nc.ui.uif2.model.BillManageModel;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteCommand;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;
import nc.vo.uapbd.qd.esign.OperationContext;

/**
 * 候选物料主档删除：一次确认整个选择，服务端逐物料校验策略并以独立事务提交。
 * 成功、失败和结果未知的档案分别展示；图片任务随成功物料提交并单独核对。
 */
public final class QdMaterialDeleteAction extends BDAnsyDelAction
        implements OperationContextProvider,AuthorizedRequestConsumer {
    private static final long serialVersionUID=1L;
    private QdMaterialListModelDataManager operationScope;
    private volatile ActionEvent activeEvent;
    private volatile OperationContext authorized;
    private volatile String dispatched;
    private volatile String attemptRequestId;
    private volatile Map<String,SuperVO> selectedRows;
    private volatile MaterialPageDeleteResult result;
    private volatile boolean workerStarted;

    public void setOperationScope(QdMaterialListModelDataManager value) { operationScope=value; }
    @Override public void setModel(AbstractAppModel value) { super.setModel(value); }

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
                super.doAfterFailure(event,new BusinessException("物料删除已取消。"));monitorStarted=false;return;
            }
            accepted=true;
            if(authorized==null) {
                // 路由未接入或策略不受控：签名拦截器放行且未绑定授权，沿用原生异步删除。
                super.actionPerformed(event);
                return;
            }
            if(!sameSelection(authorized)) throw new BusinessException("物料选择或操作组织已变化，请重新删除。");
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
            throw new BusinessException("物料删除缺少一次性授权。");
        dispatched=request.getRequestId();
        MaterialPageDeleteResult receipt=NCLocator.getInstance().lookup(IMaterialPageDeleteService.class)
            .execute(dispatched,new MaterialPageDeleteCommand(rows));
        QdMaterialOrgInfoDeleteAction.validateReceipt(request,receipt);
        result=receipt;
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
            // 原生失败回调抛出时，worker 外层 finally 仍会释放本次授权。
        }
    }

    /** Only remove confirmed successes from the UI; unknown units require request reconciliation. */
    private void showResult(ActionEvent event) {
        MaterialPageDeleteResult receipt=result;Map<String,SuperVO> rows=selectedRows;
        try {
            if(receipt==null || rows==null) throw new BusinessException("无法确认物料删除结果，请重新查询。");
            List<SuperVO> removed=new ArrayList<SuperVO>();int succeeded=0,failed=0,unknown=0;
            for(MaterialPageDeleteResult.Unit unit:receipt.getUnits()) {
                if("SUCCESS".equals(unit.getState())) {
                    succeeded++;SuperVO row=rows.get(unit.getClientObjectId());
                    if(row!=null && model().findBusinessData(row)>=0) removed.add(row);
                } else if("FAILED".equals(unit.getState())) failed++;else unknown++;
            }
            if(!removed.isEmpty()) model().directlyDelete(removed.toArray(new Object[removed.size()]));
            if("SUCCESS".equals(receipt.getState())) super.doAfterSuccess(event);
            else super.doAfterFailure(event,new BusinessException("物料删除未全部成功。"));
            JOptionPane.showMessageDialog(getParentContainer(),"删除成功 "+succeeded+" 条，失败 "+failed
                +" 条，待核对 "+unknown+" 条。成功物料如有图片，清理任务将单独核对。请求号："+attemptRequestId,
                "物料删除结果",unknown>0 || failed>0?JOptionPane.WARNING_MESSAGE:JOptionPane.INFORMATION_MESSAGE);
        } catch(Exception error) {
            super.doAfterFailure(event,error);
            JOptionPane.showMessageDialog(getParentContainer(),"删除结果已返回，但界面更新失败，请重新查询核对。请求号："
                +(attemptRequestId==null?"未知":attemptRequestId),"物料删除",JOptionPane.WARNING_MESSAGE);
        }
    }

    public OperationContext createContext(Action action,ActionEvent event) throws BusinessException {
        if(action!=this || event!=activeEvent) throw new BusinessException("物料删除上下文无效。");
        BillManageModel model=model();
        String node=model.getContext().getNodeCode(),group=model.getContext().getPk_group();
        if(group==null) throw new BusinessException("无法确定当前集团。");
        boolean groupNode="10140MAG".equals(node);
        String org=groupNode?group:operationScope==null?null:operationScope.requireOperationOrganization();
        if(!("10140MAG".equals(node) || "10140MAO".equals(node)) || org==null)
            throw new BusinessException("无法确定物料删除的节点或操作组织。");
        Map<String,SuperVO> rows=new LinkedHashMap<String,SuperVO>();List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        Set<String> keys=new HashSet<String>();
        for(Object raw:selection()) {
            if(raw==null || raw.getClass()!=MaterialVO.class) throw new BusinessException("选择的数据不是物料主档。");
            MaterialVO value=(MaterialVO)raw;String pk=value.getPrimaryKey();
            if(pk==null || pk.trim().isEmpty() || !group.equals(value.getPk_group()) || !keys.add(pk))
                throw new BusinessException("待删除物料未保存、不属于当前集团或重复选择。");
            String id=UUID.randomUUID().toString();rows.put(id,value);
            refs.add(new BusinessObjectRef(id,"BD_MATERIAL",pk,value.getCode(),null));
        }
        if(refs.isEmpty() || refs.size()>1000) throw new BusinessException("请选择 1 至 1000 条待删除的物料。");
        selectedRows=rows;
        return new OperationContext(null,null,group,org,node,groupNode?"物料-集团":"物料-业务单元",
            "Delete","删除","DELETE",null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }

    private Object[] selection() throws BusinessException {
        Integer[] indexes=model().getSelectedOperaRows();
        Object[] rows=indexes!=null && indexes.length>0?model().getSelectedOperaDatas():new Object[]{model().getSelectedData()};
        if(rows==null || rows.length==0) throw new BusinessException("请选择待删除的物料。");
        return rows;
    }

    public boolean isAvailable(OperationContext context) { return accepts(context); }

    /** 受控删除入口的纯判断：集团/业务单元已保存物料的 Delete 动作。 */
    static boolean accepts(OperationContext context) {
        if(context==null || !OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            || context.getGroupId()==null || context.getOrgId()==null || context.getObjects().isEmpty()
            || context.getObjects().size()>1000 || !MaterialPageDeleteResult.isMaterialMain(context.getNodeCode(),context.getButtonCode(),context.getOperationType())) return false;
        Set<String> keys=new HashSet<String>(),ids=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) if(ref==null || !"BD_MATERIAL".equals(ref.getObjectType())
            || ref.getObjectPk()==null || !keys.add(ref.getObjectPk()) || !ids.add(ref.getClientObjectId())) return false;
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
        try {
            boolean groupNode="10140MAG".equals(context.getNodeCode());
            String org=groupNode?context.getGroupId():operationScope==null?null:operationScope.requireOperationOrganization();
            if(!context.getOrgId().equals(org)) return false;
        } catch(BusinessException error) { return false; }
        Object[] current;
        try { current=selection(); } catch(BusinessException error) { return false; }
        return sameRows(context,selectedRows,current);
    }

    static boolean sameRows(OperationContext context,Map<String,SuperVO> bound,Object[] current) {
        if(!accepts(context) || bound==null || current==null || bound.size()!=context.getObjects().size()
            || current.length!=bound.size())return false;
        Set<String> expected=new HashSet<String>(),selected=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) {
            SuperVO raw=bound.get(ref.getClientObjectId());
            if(raw==null || raw.getClass()!=MaterialVO.class || !ref.getObjectPk().equals(raw.getPrimaryKey())
                || !context.getGroupId().equals(((MaterialVO)raw).getPk_group()) || !expected.add(ref.getObjectPk()))return false;
        }
        for(Object raw:current) {
            if(raw==null || raw.getClass()!=MaterialVO.class)return false;
            MaterialVO material=(MaterialVO)raw;
            if(material.getPrimaryKey()==null || !context.getGroupId().equals(material.getPk_group())
                || !selected.add(material.getPrimaryKey()))return false;
        }
        return expected.equals(selected);
    }

    private BillManageModel model() { return (BillManageModel)getModel(); }
    private void report(Throwable error) {
        String message=error==null?"物料删除结果未知，请重新查询核对。":error.getMessage();
        if(message==null || message.trim().isEmpty()) message="物料删除结果未知，请重新查询核对。";
        if(attemptRequestId!=null) message=message+" 请重新查询并核对请求号："+attemptRequestId;
        if(getExceptionHandler()!=null) processExceptionHandler(new BusinessException(message));
        else JOptionPane.showMessageDialog(getParentContainer(),message,"物料删除",JOptionPane.WARNING_MESSAGE);
    }
    private void clearAttempt() { activeEvent=null;authorized=null;dispatched=null;attemptRequestId=null;selectedRows=null;result=null;workerStarted=false; }
}
