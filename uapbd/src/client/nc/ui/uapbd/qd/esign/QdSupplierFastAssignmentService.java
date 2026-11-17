package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.bd.pub.assign.AssignStatus;
import nc.itf.uapbd.qd.esign.ISupplierAssignmentService;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.ui.bd.supplier.baseinfo.assign.SupplierAssignService;
import nc.ui.uif2.model.AbstractAppModel;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.SupplierAssignmentResult;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uif2.LoginContext;

/** Source-only candidate for NC's explicit-selection supplier fast assignment dialogs. */
public final class QdSupplierFastAssignmentService extends SupplierAssignService {
    private LoginContext context;private AbstractAppModel model;
    @Override public void setContext(LoginContext value) { super.setContext(value);context=value; }
    public void setModel(AbstractAppModel value) { model=value; }
    @Override public ErrLogReturnValue assignByPks(String[] suppliers,String[] targets,boolean permission) throws BusinessException {
        return execute(suppliers,targets,"ASSIGN");
    }
    @Override public ErrLogReturnValue cancelAssignByPks(String[] suppliers,String[] targets,boolean permission) throws BusinessException {
        return execute(suppliers,targets,"CANCEL_ASSIGN");
    }
    @Override public ErrLogReturnValue assignByCondition(String where,AssignStatus status,String[] targets) throws BusinessException {
        QdAssignmentConditionSelection.Selection selected=condition(where,status,targets);
        return execute(selected.objects(),selected.targets(),"ASSIGN");
    }
    @Override public ErrLogReturnValue cancelAssignByCondition(String where,AssignStatus status,String[] targets) throws BusinessException {
        QdAssignmentConditionSelection.Selection selected=condition(where,status,targets);
        return execute(selected.objects(),selected.targets(),"CANCEL_ASSIGN");
    }
    private QdAssignmentConditionSelection.Selection condition(String where,AssignStatus status,String[] targets) throws BusinessException {
        if(context==null || model==null || model.getContext()!=context || context.getPk_group()==null
            || !("10140SUG".equals(context.getNodeCode()) || "10140SUO".equals(context.getNodeCode())))
            throw new BusinessException("供应商分配缺少原生上下文。");
        return QdAssignmentConditionSelection.resolve(where,status,targets,new QdAssignmentConditionSelection.Query() {
            public String[] find(String condition,AssignStatus assignStatus,String[] organizations) throws BusinessException {
                return QdSupplierFastAssignmentService.this.queryPksByCondition(condition,assignStatus,organizations);
            }
        });
    }
    private ErrLogReturnValue execute(String[] suppliers,String[] targets,final String operation) throws BusinessException {
        if(suppliers==null || suppliers.length==0 || targets==null || targets.length==0
                || (long)suppliers.length*targets.length>1000) throw new BusinessException("请选择供应商和目标组织，且一次不超过 1000 个组合。");
        final String[] selectedSuppliers=suppliers.clone(),selectedTargets=targets.clone();
        requireUnique(selectedSuppliers);requireUnique(selectedTargets);
        final OperationContext draft=QdPartnerBatchDispatcher.onEdt(new Callable<OperationContext>() {
            public OperationContext call() throws Exception { return draft(selectedSuppliers,selectedTargets,operation); }
        });
        ISignatureService signature=NCLocator.getInstance().lookup(ISignatureService.class);
        if(signature==null) throw new BusinessException("供应商分配签名服务不可用。");
        SignaturePreparation prepared=signature.prepare(draft);
        if(prepared==null || !prepared.requiresExecutionGuard() || prepared.getContext()==null
                || prepared.getContext().getRequestId()==null) throw new BusinessException("供应商分配入口尚未启用。");
        final String requestId=prepared.getContext().getRequestId();RequestCancellation cancellation=new RequestCancellation(signature,requestId);
        boolean dispatched=false;Throwable problem=null;
        try {
            validatePrepared(draft,prepared.getContext(),selectedSuppliers,selectedTargets);
            if(!confirm(prepared,signature,cancellation)) return null;
            if(Thread.currentThread().isInterrupted()) throw new BusinessException("供应商分配已中断，请重新发起。");
            ISupplierAssignmentService service=NCLocator.getInstance().lookup(ISupplierAssignmentService.class);
            if(service==null) throw new BusinessException("供应商分配服务不可用。");
            dispatched=true;SupplierAssignmentResult result;
            try { result=service.execute(requestId); }
            catch(BusinessException | RuntimeException failure) {
                try { result=service.getResult(requestId); }
                catch(BusinessException | RuntimeException readFailure) {
                    failure.addSuppressed(readFailure);throw new BusinessException("请按请求号 "+requestId+" 核对供应商分配结果。",failure);
                }
                if(result==null || "READY".equals(result.getState()) || "PENDING".equals(result.getState()))
                    throw new BusinessException("供应商分配未确认进入执行，请核对请求号："+requestId,failure);
            }
            validateReceipt(prepared.getContext(),result);showResult(result);return null;
        } catch(BusinessException failure) { problem=failure;throw failure; }
        catch(RuntimeException failure) { problem=failure;throw failure; }
        catch(Error failure) { problem=failure;throw failure; }
        finally {
            if(!dispatched) try { cancellation.cancel(null); }
            catch(Exception failure) {
                if(problem!=null) problem.addSuppressed(failure);
                else throw new BusinessException("无法取消供应商分配授权，请核对请求号："+requestId,failure);
            }
        }
    }
    private OperationContext draft(String[] suppliers,String[] targets,String operation) throws BusinessException {
        if(context==null || model==null || model.getContext()!=context) throw new BusinessException("无法确定供应商分配的原生页面。");
        String node=context.getNodeCode(),group=context.getPk_group(),button="ASSIGN".equals(operation)?"Assign":"CancelAssign";
        if(group==null || !SupplierAssignmentResult.isRoute(node,button,operation)) throw new BusinessException("供应商分配节点或集团无效。");
        String org=QdPartnerContextOrganizations.operationOrganization(model,"供应商分配");List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        for(String supplier:suppliers) for(String target:targets)
            refs.add(new BusinessObjectRef(UUID.randomUUID().toString(),"BD_SUPPLIER",supplier,null,null,null,null,target));
        return new OperationContext(null,null,group,org,node,"10140SUG".equals(node)?"供应商-集团":"供应商-业务单元",button,
            "ASSIGN".equals(operation)?"分配":"取消分配",operation,null,refs,OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private boolean confirm(final SignaturePreparation prepared,final ISignatureService signature,final RequestCancellation cancel)
            throws BusinessException {
        if(!prepared.getPolicy().isSignatureRequired() && !prepared.getPolicy().isReasonRequired()) return true;
        final String user=InvocationInfoProxy.getInstance().getUserCode();
        SignatureDialogOutcome outcome=QdPartnerBatchDispatcher.onEdt(new Callable<SignatureDialogOutcome>() {
            public SignatureDialogOutcome call() {
                ElectronicSignatureDialog dialog=new ElectronicSignatureDialog(dialogParent(),user,prepared,signature,cancel);
                try { dialog.showModal();return dialog.getOutcome(); } finally { dialog.dispose(); }
            }
        });
        if(outcome!=null && outcome.isConfirmed()) return true;
        if(outcome!=null && outcome.getFailure()!=null) throw new BusinessException("供应商分配确认失败。",outcome.getFailure());
        return false;
    }
    static void validatePrepared(OperationContext draft,OperationContext prepared,String[] suppliers,String[] selectedTargets) throws BusinessException {
        if(draft==null || prepared==null || !draft.getGroupId().equals(prepared.getGroupId()) || !draft.getOrgId().equals(prepared.getOrgId())
            || !draft.getNodeCode().equals(prepared.getNodeCode()) || !draft.getButtonCode().equals(prepared.getButtonCode())
            || !draft.getOperationType().equals(prepared.getOperationType()) || prepared.getObjects().isEmpty() || prepared.getObjects().size()>1000)
            throw new BusinessException("供应商分配授权与原生页面不一致。");
        Set<String> expectedSuppliers=new HashSet<String>(),selected=new HashSet<String>();
        for(String value:suppliers) expectedSuppliers.add(value);for(String value:selectedTargets) selected.add(value);
        Map<String,Set<String>> pairs=new HashMap<String,Set<String>>();
        for(BusinessObjectRef ref:prepared.getObjects()) {
            if(ref==null || !"BD_SUPPLIER".equals(ref.getObjectType()) || !expectedSuppliers.contains(ref.getObjectPk())
                || ref.getBillCode()==null || ref.getTargetOrg()==null || ref.getParentClientObjectId()!=null
                || ref.getClientObjectId()==null || !ref.getClientObjectId().startsWith("sa:") || !selected.contains(ref.getTargetOrg()))
                throw new BusinessException("供应商分配授权对象或目标不一致。");
            Set<String> targets=pairs.get(ref.getObjectPk());if(targets==null) { targets=new HashSet<String>();pairs.put(ref.getObjectPk(),targets); }
            if(!targets.add(ref.getTargetOrg())) throw new BusinessException("供应商分配授权目标重复。");
        }
        if(!pairs.keySet().equals(expectedSuppliers)) throw new BusinessException("供应商分配授权档案不完整。");
        Set<String> full=null;for(Set<String> targets:pairs.values()) {
            if(!targets.equals(selected) || (full!=null && !full.equals(targets))) throw new BusinessException("供应商分配目标范围不完整。");full=targets;
        }
    }
    static void validateReceipt(OperationContext prepared,SupplierAssignmentResult result) throws BusinessException {
        if(prepared==null || result==null || !prepared.getRequestId().equals(result.getRequestId()))
            throw new BusinessException("供应商分配回执与请求不一致。");
        Set<String> expected=new HashSet<String>();for(BusinessObjectRef ref:prepared.getObjects())
            if(!expected.add(ref.getObjectPk()+"\u0000"+ref.getTargetOrg())) throw new BusinessException("供应商分配授权组合重复。");
        boolean success=false,failed=false,unknown=false;
        for(SupplierAssignmentResult.Unit unit:result.getUnits()) {
            if(unit==null || !expected.remove(unit.getSupplier()+"\u0000"+unit.getTarget())) throw new BusinessException("供应商分配回执包含未知目标。");
            String state=unit.getState();
            if("SUCCESS".equals(state) || "ALREADY_ASSIGNED".equals(state) || "ALREADY_UNASSIGNED".equals(state)) success=true;
            else if("FAILED".equals(state)) failed=true;else if("PENDING".equals(state) || "UNKNOWN".equals(state)) unknown=true;
            else throw new BusinessException("供应商分配目标状态无效。");
        }
        if(!expected.isEmpty()) throw new BusinessException("供应商分配回执缺少目标。");
        String computed=unknown?"UNKNOWN":failed?(success?"PARTIAL":"FAILED"):"SUCCESS";
        if(!computed.equals(result.getState()) && !"RUNNING".equals(result.getState()))
            throw new BusinessException("供应商分配批次与逐目标结果不一致。");
    }
    private void showResult(final SupplierAssignmentResult result) {
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            StringBuilder details=new StringBuilder("请求号 ").append(result.getRequestId()).append("\n批次状态 ")
                .append(label(result.getState())).append("\n\n");
            for(SupplierAssignmentResult.Unit unit:result.getUnits()) details.append(unit.getCode()==null?unit.getSupplier():unit.getCode())
                .append(" → ").append(unit.getTarget()).append("  ").append(label(unit.getState())).append('\n');
            JTextArea text=new JTextArea(details.toString(),12,56);text.setEditable(false);text.setCaretPosition(0);
            JOptionPane.showMessageDialog(dialogParent(),new JScrollPane(text),"供应商分配结果",
                "SUCCESS".equals(result.getState())?JOptionPane.INFORMATION_MESSAGE:JOptionPane.WARNING_MESSAGE);
        }});
    }
    private Container dialogParent() {
        Container root=context.getEntranceUI();if(root==null) return null;Window owner=SwingUtilities.getWindowAncestor(root);
        Window active=KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        for(Window current=active;current!=null;current=current.getOwner()) if(current==owner && active.isShowing()) return active;
        return root;
    }
    private static String label(String state) {
        if("SUCCESS".equals(state)) return "成功";if("ALREADY_ASSIGNED".equals(state)) return "已分配";
        if("ALREADY_UNASSIGNED".equals(state)) return "未分配";if("FAILED".equals(state)) return "失败，未提交";
        if("PARTIAL".equals(state)) return "部分成功";return "待核对";
    }
    private static void requireUnique(String[] values) throws BusinessException {
        Set<String> unique=new HashSet<String>();for(String value:values)
            if(value==null || !value.matches("[A-Za-z0-9_-]{1,20}") || !unique.add(value)) throw new BusinessException("供应商或目标组织主键无效或重复。");
    }
}
