package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.uapbd.qd.esign.IPolicyService;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.so.m30.billui.action.SaleOrderDeleteAction;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.qd.esign.SaleOrderPfActionCommand;
import nc.vo.so.qd.esign.SaleOrderPfActionResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PolicyDecision;

/**
 * 销售订单删除（PF DELETE）的签名覆盖层。
 *
 * <p>原生余额校验、删除确认都在 PF 写入之前完成；本覆盖层在确认之后、最终 PF 写入之前
 * 才请求签名，并把类型化命令交给网关执行一次原生 PF DELETE。确认取消或签名取消都不写库，
 * 也不产生业务请求。</p>
 */
public final class QdSaleOrderDeleteAction extends SaleOrderDeleteAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private static final String BUTTON = "Delete";
    private static final String NATIVE_ACTION = "DELETE";
    private ActionEvent activeEvent;
    private OperationContext draft;
    private final SaleOrderActionHandoff handoff = new SaleOrderActionHandoff();

    @Override public void actionPerformed(ActionEvent event) {
        if (!isControlledSelection()) { super.actionPerformed(event); return; }
        activeEvent = event;
        try {
            super.actionPerformed(event);
        } finally {
            activeEvent = null;
            draft = null;
        }
    }

    /** 原生余额校验与删除确认完成后：策略受控时签名并走网关，否则保持原生 PF DELETE。 */
    @Override public void procFlow(PFlowContext flow) throws Exception {
        if (!isControlledSelection() || flow != getFlowContext()
                || !NATIVE_ACTION.equals(flow.getActionName())
                || !empty(getPreActionNames()) || !empty(getAfterActionNames())) {
            super.procFlow(flow);
            return;
        }
        fillUpContext(flow);
        AggregatedValueObject[] bills = flow.getBillVos();
        if (flow.isBatch() || bills == null || bills.length != 1 || !(bills[0] instanceof SaleOrderVO)) {
            throw new BusinessException("删除受控动作只支持一张销售订单，多选尚未适配，请单选后重试。");
        }
        SaleOrderVO bill = (SaleOrderVO) bills[0];
        if (bill.getParentVO() == null || bill.getPrimaryKey() == null) {
            throw new BusinessException("删除订单缺少表头或主键，请重新查询后重试。");
        }
        // 差异 VO 不带组织集团；组织策略必须按完整单据判断，空组织会被误判为不受控而原生删除绕过签名。
        SaleOrderVO full = SaleOrderPfActionDispatch.fullBillOf(this, bill);
        String group = full.getParentVO().getPk_group();
        String org = full.getParentVO().getPk_org();
        if (!policyControlled(org)) { super.procFlow(flow); return; }
        String billType = flow.getTrantype() != null ? flow.getTrantype()
                : (flow.getBillType() != null ? flow.getBillType() : "30");
        SaleOrderPfPreparation prep = SaleOrderPfPreparation.prepare(flow.getParent(), NATIVE_ACTION,
                billType, bill, flow.getEParam());
        if (!prep.isAccepted()) { return; }

        ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);
        signing.setRequestConsumer(this);
        nc.ui.uif2.actions.ActionInterceptor original = getInterceptor();
        nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor chain =
                new nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor();
        chain.addInterceptor(signing);
        if (original != null) { chain.addInterceptor(original); }
        SignatureLifecycleInterceptor lifecycle = null;
        Throwable failure = null;
        try {
            setInterceptor(chain);
            lifecycle = SignatureLifecycleInstaller.install(this, signing);
            boolean accepted;
            signing.enterOuter(lifecycle);
            try {
                accepted = signing.beforeDoAction(this, activeEvent);
            } finally {
                signing.leaveOuter(lifecycle);
            }
            if (!accepted) { return; }
            OperationContext authorized = handoff.context();
            if (authorized == null) { super.procFlow(flow); return; }
            dispatch(authorized, flow, prep, bill, billType, group, org);
            signing.afterDoActionSuccessed(this, activeEvent);
        } catch (RuntimeException error) {
            failure = error;
            try { signing.afterDoActionFailed(this, activeEvent, error); } catch (RuntimeException ignored) { }
            throw error;
        } catch (Exception error) {
            failure = error;
            try { signing.afterDoActionFailed(this, activeEvent, error); } catch (RuntimeException ignored) { }
            throw error;
        } finally {
            try {
                signing.finishLifecycle(signing.lifecycleGeneration(), this, activeEvent, failure);
            } finally {
                setInterceptor(original);
            }
        }
    }

    private void dispatch(OperationContext authorized, PFlowContext flow, SaleOrderPfPreparation prep,
            SaleOrderVO bill, String billType, String group, String org) throws Exception {
        if (!authorized.getGroupId().equals(group) || !authorized.getOrgId().equals(org)
                || !authorized.getObjects().get(0).getObjectPk().equals(bill.getPrimaryKey())) {
            throw new BusinessException("删除授权与当前订单不一致，请重新操作。");
        }
        SaleOrderPfActionCommand command = new SaleOrderPfActionCommand(billType, new SaleOrderVO[] { bill },
                flow.getUserObj(), prep.getNote(), prep.getParameters());
        IOperationService gateway = NCLocator.getInstance().lookup(IOperationService.class);
        String dispatched = handoff.dispatch(BUTTON, group, org, bill.getPrimaryKey());
        OperationResponse response = gateway.execute(dispatched, command);
        if (response == null || response.isReplayed() || !dispatched.equals(response.getRequestId())
                || !(response.getBusinessResult() instanceof SaleOrderPfActionResult)) {
            throw new BusinessException("无法确认删除结果，请先重新查询订单再处理。");
        }
        SaleOrderPfActionResult outcome = (SaleOrderPfActionResult) response.getBusinessResult();
        if (!outcome.isDeleted() || !bill.getPrimaryKey().equals(outcome.getPrimaryKey())) {
            throw new BusinessException("无法确认删除结果，请先重新查询订单再处理。");
        }
        prep.completed();
        // 与原生删除一致：成功后在列表中移除该单据。
        getModel().directlyDelete(new Object[] { bill });
    }

    /** 策略查询：未受控时保持原生单次流程，避免重复准备或重复确认。 */
    private boolean policyControlled(String org) throws BusinessException {
        IPolicyService service = NCLocator.getInstance().lookup(IPolicyService.class);
        PolicyDecision decision = service.resolve(org, "40060301", BUTTON);
        return decision != null && decision.isControlled();
    }

    /** 受控删除只处理 990 组织；混合组织或归属不明必须拒绝。 */
    private boolean isControlledSelection() {
        if (getModel() == null || getModel().getContext() == null
                || !"40060301".equals(getModel().getContext().getNodeCode())) { return false; }
        Object[] datas = getModel().getSelectedOperaDatas();
        if (datas == null || datas.length == 0) { return false; }
        List<String> organizations = new ArrayList<String>();
        for (Object data : datas) {
            if (!(data instanceof SaleOrderVO)) { organizations.add(null); continue; }
            SaleOrderVO bill = (SaleOrderVO) data;
            organizations.add(bill.getParentVO() == null ? null : bill.getParentVO().getPk_org());
        }
        SaleOrderSelection.Kind kind = SaleOrderSelection.classify(
                getModel().getContext().getPk_group(), organizations);
        if (kind == SaleOrderSelection.Kind.REJECT_MIXED || kind == SaleOrderSelection.Kind.REJECT_UNKNOWN) {
            throw new BusinessExceptionAdapter(new BusinessException(SaleOrderSelection.rejectionMessage(kind)));
        }
        return SaleOrderSelection.isControlled(kind);
    }

    private SaleOrderVO selectedBill() {
        Object data = getModel().getSelectedData();
        return data instanceof SaleOrderVO ? (SaleOrderVO) data : null;
    }

    @Override public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        if (action != this || event != activeEvent || !isControlledSelection()) {
            throw new BusinessException("当前动作不属于销售订单删除。");
        }
        List<Object> bills = new ArrayList<Object>();
        Object[] datas = getModel().getSelectedOperaDatas();
        if (datas != null) { Collections.addAll(bills, datas); }
        if (bills.size() != 1 || !(bills.get(0) instanceof SaleOrderVO)) {
            throw new BusinessException("删除受控动作只支持一张销售订单，多选尚未适配。");
        }
        SaleOrderVO bill = (SaleOrderVO) bills.get(0);
        String group = getModel().getContext().getPk_group();
        BusinessObjectRef object = new BusinessObjectRef(UUID.randomUUID().toString(), "SO_SALE_ORDER",
                bill.getPrimaryKey(), bill.getParentVO() == null ? null : bill.getParentVO().getVbillcode(), null);
        draft = new OperationContext(null, null, group,
                bill.getParentVO() == null ? null : bill.getParentVO().getPk_org(), "40060301",
                "销售订单维护", BUTTON, "删除", "DELETE", null, Collections.singletonList(object));
        return draft;
    }

    @Override public boolean isAvailable(OperationContext context) { return handoff.supports(context); }
    @Override public boolean bind(OperationContext context) { return isAvailable(context) && handoff.bind(context); }
    @Override public boolean wasDispatched(String requestId) { return handoff.wasDispatched(requestId); }
    @Override public void release(String requestId) { handoff.release(requestId); }
    @Override public Container getParentContainer() {
        return getModel() == null || getModel().getContext() == null ? null : getModel().getContext().getEntranceUI();
    }
    @Override public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }

    private static boolean empty(List<?> values) { return values == null || values.isEmpty(); }
}
