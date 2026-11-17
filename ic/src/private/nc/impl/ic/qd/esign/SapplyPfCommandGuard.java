package nc.impl.ic.qd.esign;

import java.util.Map;
import nc.vo.ic.pub.pf.ICPFParameter;
import nc.vo.ic.pub.define.BillOperator;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.SapplyApprovalIntent;
import nc.vo.pub.BusinessException;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.bs.uapbd.qd.esign.audit.SapplyWorkflowBatchAspect;

/** Rejects unverified workflow overrides and binds supplied approval notes to the authorized bill. */
final class SapplyPfCommandGuard {
    private SapplyPfCommandGuard() { }
    static void validate(OperationContext context,InventoryPfStateCommand command,String currentUserId) throws BusinessException {
        validate(context, command, currentUserId, false);
    }

    static void validate(OperationContext context, InventoryPfStateCommand command, String currentUserId, boolean batch)
            throws BusinessException {
        if(context==null || command==null || context.getObjects().size()!=1)
            throw new BusinessException("出库申请流程授权不完整。");
        boolean approval="Approve".equals(context.getButtonCode()) || "UnApprove".equals(context.getButtonCode());
        if(approval && batch) {
            SapplyWorkflowBatchAspect.requireApprovalIntent(context,command);
        } else if(approval) {
            String signedId=context.getObjects().get(0).getClientObjectId();
            if(!SapplyApprovalIntent.clientObjectId(context.getButtonCode(),command).equals(signedId))
                throw new BusinessException("出库申请审批内容与签名授权不一致，请重新操作。");
        }
        PfUserObject user=command.getUserObject();
        if(user!=null) {
            if(user.getClass()!=PfUserObject.class && user.getClass()!=ICPFParameter.class)
                throw new BusinessException("出库申请流程用户参数类型尚未核对。");
            if(user.getUserObject()!=null || user.getBusinessCheckMap()!=null && !user.getBusinessCheckMap().isEmpty())
                throw new BusinessException("出库申请流程不允许附加跳过检查的用户参数。");
            if(user instanceof ICPFParameter) {
                ICPFParameter ic=(ICPFParameter)user;
                if(text(ic.getAccreditUserID())!=null || ic.isBHasBalancedNum() || ic.isBSafetyStockCheckFlag()
                        || ic.getReserveProcessType()!=0 || ic.isbToleranceCheckFlag() || ic.isbBudgetControlCheckFlag()
                        || ic.isbBudgetCheckkFlag() || ic.isBfromETFlag())
                    throw new BusinessException("出库申请流程包含尚未授权的原生检查覆盖参数。");
                if(("Approve".equals(context.getButtonCode()) || "UnApprove".equals(context.getButtonCode()))
                        && ic.getBillaction()!=null && ic.getBillaction()!=BillOperator.Audit)
                    throw new BusinessException("出库申请审批携带了不匹配的原生单据动作。");
            }
        }
        for(Map.Entry<String,Object> item:command.getParameters().entrySet()) {
            String key=item.getKey();Object value=item.getValue();
            if("notechecked".equals(key) && "notechecked".equals(value))continue;
            if (batch && ("batch".equals(key) && "batch".equals(value) || "worknote".equals(key) && value == null)) continue;
            if(!approval && "besideapprove".equals(key)
                    && (value instanceof Boolean || "true".equals(value) || "false".equals(value)))continue;
            throw new BusinessException("出库申请流程携带未核实的扩展或静默参数。");
        }
        WorkflownoteVO note=command.getWorkflowNote();
        if("Approve".equals(context.getButtonCode()) && note==null)
            throw new BusinessException("出库申请审批缺少原生审批确认结果。");
        if(note==null)return;
        if(approval && !batch && SapplyApprovalIntent.isWorkflowAdjustment(note))
            throw new BusinessException("加签或指定审批人不是出库申请审批动作，不能派发审批。");
        checkIfPresent(note.getBillid(),context.getObjects().get(0).getObjectPk(),"审批单据");
        checkIfPresent(note.getPk_group(),context.getGroupId(),"审批集团");
        checkIfPresent(note.getPk_org(),context.getOrgId(),"审批组织");
        nc.vo.pub.pf.WfTaskInfo taskInfo=SapplyApprovalIntent.workflowTaskInfo(note);
        if(taskInfo!=null && taskInfo.getTask()!=null)
            checkIfPresent(taskInfo.getTask().getBillID(),context.getObjects().get(0).getObjectPk(),"审批任务单据");
        String approver=text(note.getCheckman());
        if(approver!=null && (text(currentUserId)==null || !approver.equals(text(currentUserId))))
            throw new BusinessException("出库申请审批人与当前登录用户不一致。");
    }
    private static void checkIfPresent(String supplied,String expected,String field) throws BusinessException {
        String actual=text(supplied);
        if(actual!=null && !actual.equals(text(expected)))throw new BusinessException(field+"与签名授权不一致。");
    }
    private static String text(String value){if(value==null)return null;String s=value.trim();return s.isEmpty()||"~".equals(s)?null:s;}
}
