package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import nc.ui.pub.pf.PfUtilClient;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.actions.pflow.MultiBillScriptRunner;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.qd.esign.SapplyPfBatchCommand;
import nc.vo.pf.change.PfUtilBaseTools;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pflow.PfUserObject;

/** Prepares one complete, multi-bill PF invocation without executing it. */
final class SapplyBatchClientPreparation {
    private static final String RUNNER_HASH = "926460E907FC0150F444DAE07064ACC7482484EDF35FDD2C1122B40D18F277E9";

    private SapplyBatchClientPreparation() { }

    /** Execute the additional native runner check after AbstractScriptExcAction validation. */
    static void beforeCheck(AbstractScriptExcAction action, PFlowContext flow, SapplyBillVO[] bills)
            throws Exception {
        if (action == null || flow == null || action.getFlowContext() != flow || bills == null || bills.length < 2)
            throw new BusinessException("出库申请批量原生校验上下文无效。");
        InventoryPfPreparation.verifyNativeContract();
        InventoryPfPreparation.verifyClass(MultiBillScriptRunner.class, RUNNER_HASH);
        MultiBillScriptRunner runner = new MultiBillScriptRunner(flow);
        runner.setScriptAction(action);
        try {
            java.lang.reflect.Method check = MultiBillScriptRunner.class.getDeclaredMethod("beforeCheck",
                    AggregatedValueObject[].class);
            check.setAccessible(true);
            check.invoke(runner, (Object) bills);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new BusinessException("出库申请原生批量校验失败。", cause);
        }
    }

    /** Returns null only when the user declines a native confirmation. */
    static SapplyPfBatchCommand prepare(PFlowContext flow, String action, String billType,
            SapplyBillVO[] bills) throws BusinessException {
        return prepare(flow,action,billType,bills,null);
    }

    static SapplyPfBatchCommand prepare(PFlowContext flow, String action, String billType,
            SapplyBillVO[] bills,String verifiedUser) throws BusinessException {
        requireInput(flow, action, billType, bills,verifiedUser);
        String[] skipped = precheckSkippedPks(flow, bills);
        Set<String> skippedKeys = new HashSet<String>(java.util.Arrays.asList(skipped));
        List<SapplyBillVO> valid = new ArrayList<SapplyBillVO>();
        for (SapplyBillVO bill : bills)
            if (!skippedKeys.contains(bill.getHead().getCgeneralhid().trim())) valid.add(bill);
        if (valid.isEmpty()) throw new BusinessException(allRejectedMessage(flow,bills));
        SapplyBillVO[] submitted = valid.toArray(new SapplyBillVO[valid.size()]);
        HashMap<String,Object> parameters = nativeParameters(flow);
        try {
            SaleOrderPfPreparation.verifyNativeContract();
            InventoryPfPreparation.verifyClass(MultiBillScriptRunner.class, RUNNER_HASH);
            SaleOrderPfPreparation.field("m_isSuccess").setBoolean(null, true);
            SaleOrderPfPreparation.field("m_checkFlag").setBoolean(null, true);
            boolean accepted = ((Boolean) SaleOrderPfPreparation.method("beforeProcessBatchAction",
                    Container.class, String.class, String.class).invoke(null, flow.getParent(), action, billType))
                    .booleanValue();
            if (!accepted) {
                SaleOrderPfPreparation.field("m_isSuccess").setBoolean(null, false);
                return null;
            }

            // MultiBillScriptRunner supplies a null previous worknote on its first invocation;
            // runBatchNew adds this marker before preparing a multi-bill workflow.
            parameters.put("worknote", null);
            parameters.put("batch", "batch");
            boolean save = PfUtilBaseTools.isSaveAction(action, billType);
            boolean approve = PfUtilBaseTools.isApproveAction(action, billType);
            String approvalButton=nc.vo.ic.qd.esign.SapplyNativeAction.approvalButton(action);
            boolean approval = approvalButton!=null;
            WorkflownoteVO note = null;
            if (save || approve) {
                note = (WorkflownoteVO) SaleOrderPfPreparation.method("actionAboutApproveflow", Container.class,
                        String.class, String.class, Object.class, HashMap.class, Integer.TYPE)
                        .invoke(null, flow.getParent(), action, billType, submitted, parameters, Integer.valueOf(submitted.length));
            } else if (PfUtilBaseTools.isStartAction(action, billType)
                    || PfUtilBaseTools.isSignalAction(action, billType)) {
                note = (WorkflownoteVO) SaleOrderPfPreparation.method("actionAboutWorkflow", Container.class,
                        String.class, String.class, AggregatedValueObject.class, HashMap.class, Integer.TYPE)
                        .invoke(null, flow.getParent(), action, billType, submitted[0], parameters, Integer.valueOf(submitted.length));
            }
            if (!PfUtilClient.isSuccess()) {
                if (PfUtilClient.isCanceled()) return null;
                throw new BusinessException("出库申请原生批量流程准备未完成。");
            }
            // Approval retains its confirmed note; ordinary batch actions cannot carry unverified workflow selections.
            if ((!approval && ((approve && !save) || (note != null && !save)))
                    || note != null && note.getClass() != WorkflownoteVO.class
                    || "Approve".equals(approvalButton) && note == null) {
                throw new BusinessException("出库申请批量流程包含尚未接入的工作流确认，请停止操作。");
            }
            requireSupportedParameters(parameters);
            return new SapplyPfBatchCommand(action, billType, bills, flow.getUserObjs(), note, parameters, skipped);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof BusinessException) throw (BusinessException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new BusinessException("出库申请原生批量流程准备失败。", cause);
        } catch (BusinessException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new BusinessException("无法执行出库申请原生批量流程准备。", failure);
        }
    }

    private static void requireInput(PFlowContext flow, String action, String billType, SapplyBillVO[] bills)
            throws BusinessException { requireInput(flow,action,billType,bills,null); }

    private static void requireInput(PFlowContext flow, String action, String billType, SapplyBillVO[] bills,String verifiedUser)
            throws BusinessException {
        String approvalButton=nc.vo.ic.qd.esign.SapplyNativeAction.approvalButton(action);
        boolean approval=approvalButton!=null;
        String base="Approve".equals(approvalButton)?"APPROVE":"UnApprove".equals(approvalButton)?"UNAPPROVE":action;
        if (!SwingUtilities.isEventDispatchThread() || flow == null || !flow.isBatch()
                || !("SAVE".equals(action) || "UNSAVE".equals(action) || "DELETE".equals(action)
                    || approval)
                || !base.equals(flow.getActionName())
                || approval && (!nc.vo.ic.qd.esign.SapplyNativeAction.matches(approvalButton,action,verifiedUser)
                    || !verifiedUser.equals(flow.getUserId()))
                || !approval && flow.getUserId()!=null && !flow.getUserId().trim().isEmpty()
                || billType == null || !billType.matches("4455(?:-[A-Za-z0-9_-]{1,95})?")
                || !billType.equals(flow.getTrantype() == null ? flow.getBillType() : flow.getTrantype())
                || bills == null || bills.length < 2 || bills.length > 1000) {
            throw new BusinessException("出库申请原生批量流程上下文无效。");
        }
        AggregatedValueObject[] selected = flow.getBillVos();
        PfUserObject[] users = flow.getUserObjs();
        if (selected == null || selected.length != bills.length || users != null && users.length != bills.length)
            throw new BusinessException("出库申请批量准备仅支持完整选择的一次执行。");
        Set<String> seen = new HashSet<String>();
        for (int index = 0; index < bills.length; index++) {
            if (bills[index] == null || bills[index] != selected[index] || bills[index].getHead() == null)
                throw new BusinessException("出库申请批量准备与原生选择不一致。");
            String pk = bills[index].getHead().getCgeneralhid();
            pk = pk == null ? null : pk.trim();
            if (pk == null || !pk.matches("[A-Za-z0-9_-]{1,20}") || !seen.add(pk))
                throw new BusinessException("出库申请批量包含缺少主键或重复的单据。");
        }
        precheckSkippedPks(flow,bills);
    }

    /** Translate NC's zero-based exception positions without trusting malformed indexes. */
    static String[] precheckSkippedPks(PFlowContext flow,SapplyBillVO[] bills) throws BusinessException {
        if(flow==null || bills==null)throw new BusinessException("出库申请批量预校验上下文无效。");
        Map<?,?> errors=flow.getExceptionResults();
        Set<Integer> positions=new HashSet<Integer>();
        if(errors!=null)for(Map.Entry<?,?> entry:errors.entrySet()) {
            if(!(entry.getKey() instanceof Integer) || entry.getValue()!=null && !(entry.getValue() instanceof String))
                throw new BusinessException("出库申请原生预校验结果格式无效。");
            int index=((Integer)entry.getKey()).intValue();
            if(index<0 || index>=bills.length)throw new BusinessException("出库申请原生预校验位置超出选择范围。");
            if(entry.getValue()!=null)positions.add(Integer.valueOf(index));
        }
        List<String> skipped=new ArrayList<String>();
        for(int index=0;index<bills.length;index++)if(positions.contains(Integer.valueOf(index))) {
            SapplyBillVO bill=bills[index];
            String pk=bill==null || bill.getHead()==null?null:bill.getHead().getCgeneralhid();
            if(pk==null || !pk.trim().matches("[A-Za-z0-9_-]{1,20}"))
                throw new BusinessException("出库申请预校验单据主键无效。");
            skipped.add(pk.trim());
        }
        return skipped.toArray(new String[skipped.size()]);
    }

    private static String allRejectedMessage(PFlowContext flow,SapplyBillVO[] bills) {
        Map<String,String> reasons=SapplyBatchClientResult.precheckMessages(flow,bills);
        StringBuilder message=new StringBuilder("所选出库申请均未通过原生校验，没有可提交的单据。");
        int shown=Math.min(bills.length,5);
        for(int index=0;index<shown;index++) {
            SapplyBillVO bill=bills[index];String pk=bill.getHead().getCgeneralhid().trim();
            String code=bill.getHead().getVbillcode();
            code=code==null?"":code.replace('\r',' ').replace('\n',' ').trim();
            if(code.isEmpty())code=pk;
            if(code.length()>80)code=code.substring(0,80)+"…";
            String reason=reasons.get(pk);
            message.append('\n').append(code).append("：")
                    .append(reason==null?"未通过原生校验":reason);
        }
        if(bills.length>shown)message.append("\n另有 ").append(bills.length-shown).append(" 张未通过原生校验。");
        return message.toString();
    }

    private static HashMap<String,Object> nativeParameters(PFlowContext flow) throws BusinessException {
        if (flow.getEParam() == null) throw new BusinessException("出库申请批量缺少原生流程参数。");
        HashMap<String,Object> parameters = new HashMap<String,Object>();
        for (Map.Entry<Object,Object> entry : flow.getEParam().entrySet()) {
            if (!(entry.getKey() instanceof String)) throw new BusinessException("出库申请批量流程参数无效。");
            if ("besideapprove".equals(entry.getKey()) && entry.getValue()==null) continue;
            parameters.put((String) entry.getKey(), entry.getValue());
        }
        requireSupportedParameters(parameters);
        return parameters;
    }

    private static void requireSupportedParameters(Map<String,Object> parameters) throws BusinessException {
        for (Map.Entry<String,Object> entry : parameters.entrySet()) {
            if (!("batch".equals(entry.getKey()) && "batch".equals(entry.getValue()))
                    && !("worknote".equals(entry.getKey()) && entry.getValue() == null)) {
                throw new BusinessException("出库申请批量包含尚未接入的原生流程参数。");
            }
        }
    }
}
