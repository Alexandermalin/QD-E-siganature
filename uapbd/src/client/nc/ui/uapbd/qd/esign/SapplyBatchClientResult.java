package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.ui.pub.beans.MessageDialog;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.actions.pflow.MultiBillScriptRunner;
import nc.ui.pubapp.uif2app.actions.pflow.ScriptPFlowAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.qd.esign.SapplyPfBatchResult;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pflow.PFReturnObject;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SapplyBatchOutcome;

/** Refreshes only committed outbound requests and shows the durable result by bill number. */
final class SapplyBatchClientResult {
    private SapplyBatchClientResult() { }

    static void apply(AbstractScriptExcAction action, BillManageModel model, Container parent,
            OperationContext authorized, OperationResponse response, SapplyBillVO[] submitted) throws Exception {
        if (response == null || response.isReplayed() || !authorized.getRequestId().equals(response.getRequestId())
                || !(response.getBusinessResult() instanceof SapplyPfBatchResult)) throw unconfirmed(authorized);
        SapplyPfBatchResult result = (SapplyPfBatchResult) response.getBusinessResult();
        Map<String,String> precheckMessages = precheckMessages(action,submitted);
        Set<String> committed = committedKeys(authorized, result.getOutcome());
        boolean delete = "Delete".equals(authorized.getButtonCode());
        List<SapplyBillVO> display = delete ? matchingBills(action, submitted, committed)
                : returnedBills(result, committed);
        boolean refreshRequired = display.size() != committed.size();
        try {
            if (!display.isEmpty()) {
                if (delete) model.directlyDelete(display.toArray(new SapplyBillVO[display.size()]));
                else refreshRequired |= !refreshReturned(action, display, "SUCCESS".equals(result.getOutcome().getState()));
            }
        } catch (Exception refreshFailure) {
            throw new BusinessException("出库申请批量结果已确认，界面刷新失败，请重新查询。\n"
                    + message(result, true, precheckMessages), refreshFailure);
        }
        String message = message(result, refreshRequired, precheckMessages);
        MessageDialog.showHintsDlg(parent, "出库申请批量" + authorized.getButtonName(), message);
    }

    private static boolean refreshReturned(AbstractScriptExcAction action, List<SapplyBillVO> display, boolean allSucceeded)
            throws BusinessException {
        if (!(action instanceof ScriptPFlowAction)) return false;
        ScriptPFlowAction script = (ScriptPFlowAction) action;
        AbstractBill[] originals = script.getFullOldVOs();
        if (originals == null) return false;
        AbstractBill[] ordered = originalsInResultOrder(originals, display);
        if (ordered == null) return false;
        MultiBillScriptRunner runner = action.getMultibillScriptRunner();
        boolean wasSuccessful = runner.isTaskSuccessful();
        try {
            script.setFullOldVOs(ordered);
            runner.setTaskSuccessful(allSucceeded);
            InventoryPfPreparation.completeNativeResult(null);
            action.processReturnObjs(display.toArray(new SapplyBillVO[display.size()]));
            return true;
        } finally {
            runner.setTaskSuccessful(wasSuccessful);
            script.setFullOldVOs(originals);
        }
    }

    static AbstractBill[] originalsInResultOrder(AbstractBill[] originals, List<SapplyBillVO> returned) {
        Map<String,SapplyBillVO> byKey = new HashMap<String,SapplyBillVO>();
        for (AbstractBill original : originals) {
            String pk = billKey(original);
            if (pk == null || byKey.put(pk, (SapplyBillVO) original) != null) return null;
        }
        SapplyBillVO[] ordered = new SapplyBillVO[returned.size()];
        for (int index = 0; index < ordered.length; index++) {
            ordered[index] = byKey.remove(billKey(returned.get(index)));
            if (ordered[index] == null) return null;
        }
        return ordered;
    }

    static Set<String> committedKeys(OperationContext authorized, SapplyBatchOutcome outcome) throws BusinessException {
        if (outcome == null || !authorized.getRequestId().equals(outcome.getRequestId())
                || outcome.getUnits().size() != authorized.getObjects().size()) throw unconfirmed(authorized);
        Map<String,String> expected = new HashMap<String,String>();
        for (BusinessObjectRef ref : authorized.getObjects()) expected.put(ref.getObjectPk(), ref.getClientObjectId());
        Set<String> committed = new HashSet<String>();
        for (SapplyBatchOutcome.Unit unit : outcome.getUnits()) {
            if (unit == null || unit.getBill() == null) throw unconfirmed(authorized);
            BusinessObjectRef bill = unit.getBill();
            String client = expected.remove(bill.getObjectPk());
            if (client == null || !client.equals(bill.getClientObjectId())
                    || !"IC_SAPPLY".equals(bill.getObjectType())) throw unconfirmed(authorized);
            if ("SUCCESS".equals(unit.getState())) committed.add(bill.getObjectPk());
            else if (!"FAILED".equals(unit.getState()) && !"UNKNOWN".equals(unit.getState())) throw unconfirmed(authorized);
        }
        if (!expected.isEmpty()) throw unconfirmed(authorized);
        return committed;
    }

    private static List<SapplyBillVO> matchingBills(AbstractScriptExcAction action, SapplyBillVO[] submitted, Set<String> keys) {
        Object[] originals = action instanceof ScriptPFlowAction ? ((ScriptPFlowAction) action).getFullOldVOs() : null;
        List<SapplyBillVO> result = new ArrayList<SapplyBillVO>();
        Set<String> remaining = new HashSet<String>(keys);
        if (originals != null) addBills(originals, remaining, result);
        addBills(submitted, remaining, result);
        return result;
    }

    private static List<SapplyBillVO> returnedBills(SapplyPfBatchResult result, Set<String> keys) {
        List<SapplyBillVO> bills = new ArrayList<SapplyBillVO>();
        Object[] nativeBills = result.getNativeResult() == null ? null : result.getNativeResult().getRetObj();
        if (nativeBills != null) {
            Set<String> remaining = new HashSet<String>(keys);
            for (Object value : nativeBills) {
                if (value instanceof PFReturnObject) addBills(((PFReturnObject) value).getBills(), remaining, bills);
                else if (value instanceof Object[]) addBills((Object[]) value, remaining, bills);
                else addBill(value, remaining, bills);
            }
        }
        return bills;
    }

    private static void addBills(Object[] source, Set<String> remaining, List<SapplyBillVO> bills) {
        if (source != null) for (Object value : source) addBill(value, remaining, bills);
    }

    private static void addBill(Object value, Set<String> remaining, List<SapplyBillVO> bills) {
        String pk = billKey(value);
        if (pk != null && remaining.remove(pk)) bills.add((SapplyBillVO) value);
    }

    private static String billKey(Object value) {
        if (!(value instanceof SapplyBillVO) || ((SapplyBillVO) value).getHead() == null) return null;
        String pk = ((SapplyBillVO) value).getHead().getCgeneralhid();
        return pk == null || pk.trim().isEmpty() ? null : pk.trim();
    }

    static Map<String,String> precheckMessages(AbstractScriptExcAction action,SapplyBillVO[] selected) {
        try { return precheckMessages(action==null?null:action.getFlowContext(),selected); }
        catch(RuntimeException ignored) { return new HashMap<String,String>(); }
    }

    static Map<String,String> precheckMessages(nc.ui.pubapp.pub.common.context.PFlowContext flow,SapplyBillVO[] selected) {
        Map<String,String> result=new HashMap<String,String>();
        try {
            if(flow==null || selected==null || flow.getExceptionResults()==null)return result;
            Map<?,?> errors=flow.getExceptionResults();
            for(int index=0;index<selected.length;index++) {
                String pk=billKey(selected[index]);Object raw=errors.get(Integer.valueOf(index));
                if(pk==null || !(raw instanceof String))continue;
                String reason=((String)raw).replace('\r',' ').replace('\n',' ').trim();
                if(reason.length()>240)reason=reason.substring(0,240)+"…";
                if(!reason.isEmpty())result.put(pk,reason);
            }
        } catch(RuntimeException ignored) { result.clear(); }
        return result;
    }

    static String message(SapplyPfBatchResult result, boolean refreshRequired,Map<String,String> precheckMessages) {
        SapplyBatchOutcome outcome = result.getOutcome();
        StringBuilder message = new StringBuilder("请求号 ").append(outcome.getRequestId());
        if (result.isNativeBatchInterrupted()) message.append("\nNC 批次返回异常，以下结果来自已提交回执。");
        if (result.getNativeResult() != null && result.getNativeResult().getExceptionInfo() != null
                && result.getNativeResult().getExceptionInfo().getErrorMessageMap() != null
                && !result.getNativeResult().getExceptionInfo().getErrorMessageMap().isEmpty())
            message.append("\nNC 原生批次报告异常，单据状态以回执及现场核对为准。");
        for (SapplyBatchOutcome.Unit unit : outcome.getUnits()) {
            BusinessObjectRef bill = unit.getBill();
            message.append('\n').append(bill.getBillCode() == null ? bill.getObjectPk() : bill.getBillCode()).append("  ");
            if ("SUCCESS".equals(unit.getState())) message.append("成功");
            else if ("FAILED".equals(unit.getState())) {
                if ("CLIENT_PRECHECK_SKIPPED".equals(unit.getErrorCode())) {
                    message.append("未提交：原生预校验未通过");
                    String reason=precheckMessages.get(bill.getObjectPk());
                    if(reason!=null)message.append("（").append(reason).append("）");
                }
                else message.append("失败");
            }
            else message.append("结果待核对");
        }
        if (refreshRequired) message.append("\n请重新查询单据以刷新已提交结果。");
        return message.toString();
    }

    private static BusinessException unconfirmed(OperationContext context) {
        return new BusinessException("无法确认出库申请批量结果，请按请求号核对 " + context.getRequestId());
    }
}
