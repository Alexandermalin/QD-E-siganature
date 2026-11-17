package nc.ui.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.actions.pflow.ScriptPFlowAction;
import nc.ui.uif2.NCAction;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.qd.esign.SaleOrderPfActionCommand;
import nc.vo.so.qd.esign.SaleOrderPfActionResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;

/**
 * 销售订单 PF 动作的受控执行辅助：在原生动作已完成输入/确认并进入 procFlow 后，
 * 用一次性授权把类型化命令交给网关，替代原生最终 PF 调用，同时保留流程准备与结果处理语义。
 */
final class SaleOrderPfActionDispatch {
    /** 供各动作类回调原生 protected fillUpContext，组装真实流程参数。 */
    interface ContextFiller { void fillUp(PFlowContext flow) throws Exception; }

    private SaleOrderPfActionDispatch() { }

    /** 受控执行固定 PF 动作；不满足授权与流程核对时抛业务异常，不回落原生写入。 */
    static void dispatch(NCAction owner, AbstractScriptExcAction action, PFlowContext flow,
            OperationContext authorized, SaleOrderActionHandoff handoff, String button,
            String expectedAction, ContextFiller filler) throws Exception {
        if (authorized == null) { throw new BusinessException("销售订单流程动作缺少签名授权。"); }
        // 原生 validationService/userId 属正常流程上下文，不作为回落原生的理由；
        // 只核对流程对象、动作名与前置/后置动作，防止把别的动作当成本次签名动作执行。
        if (flow != action.getFlowContext() || !expectedAction.equals(flow.getActionName())
                || !empty(action.getPreActionNames()) || !empty(action.getAfterActionNames())) {
            throw new BusinessException("销售订单流程动作与签名授权不一致，请重新操作。");
        }
        filler.fillUp(flow);
        AggregatedValueObject[] bills = flow.getBillVos();
        if (flow.isBatch() || bills == null || bills.length != 1 || !(bills[0] instanceof SaleOrderVO)) {
            throw new BusinessException("销售订单受控流程动作只支持一张订单。");
        }
        SaleOrderVO bill = (SaleOrderVO) bills[0];
        if (bill.getParentVO() == null || bill.getPrimaryKey() == null) {
            throw new BusinessException("流程动作的订单缺少表头或主键，请重新操作。");
        }
        // 原生 getFlowData 对已存订单统一产出差异 VO（仅主键与流程字段，无组织集团），
        // 组织与集团必须用原生动作保存的完整单据核对，防止差异 VO 空值造成误判或漏判。
        SaleOrderVO full = fullBillOf(action, bill);
        if (!authorized.getGroupId().equals(full.getParentVO().getPk_group())
                || !authorized.getOrgId().equals(full.getParentVO().getPk_org())
                || !authorized.getObjects().get(0).getObjectPk().equals(bill.getPrimaryKey())) {
            throw new BusinessException("流程动作的订单、组织或主键与签名授权不一致。");
        }
        String billType = flow.getTrantype() != null ? flow.getTrantype()
                : (flow.getBillType() != null ? flow.getBillType() : "30");
        SaleOrderPfPreparation prep = SaleOrderPfPreparation.prepare(flow.getParent(), expectedAction,
                billType, bill, flow.getEParam());
        if (!prep.isAccepted()) { throw new BusinessException("NC 销售订单流程已取消，订单未处理。"); }
        SaleOrderPfActionCommand command = new SaleOrderPfActionCommand(billType,
                new SaleOrderVO[] { bill }, flow.getUserObj(), prep.getNote(), prep.getParameters());
        IOperationService gateway = NCLocator.getInstance().lookup(IOperationService.class);
        String dispatched = handoff.dispatch(button, authorized.getGroupId(),
                authorized.getOrgId(), bill.getPrimaryKey());
        OperationResponse response = gateway.execute(dispatched, command);
        requireKnownResult(dispatched, response, bill.getPrimaryKey());
        SaleOrderPfActionResult outcome = (SaleOrderPfActionResult) response.getBusinessResult();
        prep.completed();
        applyNativeResult(action, outcome);
    }

    /** 只接受与本次请求对应、非重放、返回真实订单结果的成功；否则按未知结果处理。 */
    private static void requireKnownResult(String requestId, OperationResponse response, String key)
            throws BusinessException {
        if (response == null || response.isReplayed() || !requestId.equals(response.getRequestId())
                || !(response.getBusinessResult() instanceof SaleOrderPfActionResult)) {
            throw new BusinessException("无法确认销售订单流程结果，请先重新查询订单再处理。");
        }
        SaleOrderPfActionResult outcome = (SaleOrderPfActionResult) response.getBusinessResult();
        if (!key.equals(outcome.getPrimaryKey())) {
            throw new BusinessException("无法确认销售订单流程结果，请先重新查询订单再处理。");
        }
    }

    /** 交由原生动作自己的结果处理刷新界面；返回值缺失时不做无依据刷新。 */
    private static void applyNativeResult(AbstractScriptExcAction action, SaleOrderPfActionResult outcome)
            throws Exception {
        Object result = outcome.getNativeResult();
        if (result == null) { return; }
        Object[] returned;
        if (result.getClass().isArray()) { returned = (Object[]) result; }
        else {
            returned = (Object[]) java.lang.reflect.Array.newInstance(result.getClass(), 1);
            returned[0] = result;
        }
        action.processReturnObjs(returned);
    }

    /** 构造一次性请求上下文：单张已存订单、真实按钮与固定动作域。 */
    static OperationContext context(String group, String org, String node, String button,
            String buttonName, String operationType, List<SaleOrderVO> bills) throws BusinessException {
        if (bills == null || bills.isEmpty()) { throw new BusinessException("请选择销售订单。"); }
        List<BusinessObjectRef> objects = new ArrayList<BusinessObjectRef>();
        for (SaleOrderVO bill : bills) {
            if (bill.getParentVO() == null || bill.getPrimaryKey() == null || org == null
                    || !org.equals(bill.getParentVO().getPk_org())) {
                throw new BusinessException("销售订单选择已变化或包含不同组织，请重新选择。");
            }
            objects.add(new BusinessObjectRef(UUID.randomUUID().toString(), "SO_SALE_ORDER",
                    bill.getPrimaryKey(), bill.getParentVO().getVbillcode(), null));
        }
        return new OperationContext(null, null, group, org, node, "销售订单维护", button, buttonName,
                operationType, null, objects);
    }

    private static boolean empty(java.util.List<?> values) { return values == null || values.isEmpty(); }

    /**
     * 原生 ScriptPFlowAction.getFlowData 对已存单据产出差异 VO；从动作保存的完整单据里
     * 按主键取同单完整 VO，供组织集团核对。取不到或主键对不上时拒绝受控流程。
     */
    static SaleOrderVO fullBillOf(AbstractScriptExcAction action, SaleOrderVO bill) throws BusinessException {
        if (!(action instanceof ScriptPFlowAction)) {
            throw new BusinessException("无法取得订单完整单据，已终止受控流程。");
        }
        AbstractBill[] fulls = ((ScriptPFlowAction) action).getFullOldVOs();
        if (fulls == null || fulls.length != 1 || !(fulls[0] instanceof SaleOrderVO)) {
            throw new BusinessException("无法取得订单完整单据，请重新操作。");
        }
        SaleOrderVO full = (SaleOrderVO) fulls[0];
        if (full.getParentVO() == null || full.getPrimaryKey() == null
                || !full.getPrimaryKey().equals(bill.getPrimaryKey())) {
            throw new BusinessException("流程动作的订单与完整单据不一致，请重新操作。");
        }
        return full;
    }
}
