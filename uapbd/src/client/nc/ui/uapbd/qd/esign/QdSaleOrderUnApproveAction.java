package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.so.m30.billui.action.SaleOrderUnApproveAction;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.qd.esign.SaleOrderPfActionCommand;
import nc.vo.so.qd.esign.SaleOrderPfActionResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/**
 * 销售订单取消审批（PF UNAPPROVE）的签名覆盖层。
 *
 * <p>先由原生 PF 客户端完成流程准备；无额外审批窗口时直接进入签名。
 * 签名后把原生流程说明交给受控网关，
 * 由服务端调用原生 PF UNAPPROVE，保留弃审结果与工作项语义。</p>
 */
public final class QdSaleOrderUnApproveAction extends SaleOrderUnApproveAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private static final String BUTTON = "UnApprove";
    private static final String NATIVE_ACTION = "UNAPPROVE";
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

    /** 原生审批流程进入 procFlow 后：先完成原生审批窗口准备，再签名并受控写入。 */
    @Override public void procFlow(PFlowContext flow) throws Exception {
        // 原生 validationService 是已配置的正常校验器，不作为回落原生的理由。
        if (!isControlledSelection() || flow != getFlowContext()
                || !NATIVE_ACTION.equals(flow.getActionName())
                || !empty(getPreActionNames()) || !empty(getAfterActionNames())) {
            super.procFlow(flow);
            return;
        }
        fillUpContext(flow);
        AggregatedValueObject[] bills = flow.getBillVos();
        if (flow.isBatch() || bills == null || bills.length != 1 || !(bills[0] instanceof SaleOrderVO)) {
            throw new BusinessException("取消审批受控动作只支持一张销售订单，多选尚未适配，请单选后重试。");
        }
        SaleOrderVO bill = (SaleOrderVO) bills[0];
        if (bill.getParentVO() == null || bill.getPrimaryKey() == null) {
            throw new BusinessException("取消审批订单缺少表头或主键，请重新查询后重试。");
        }
        String billType = flow.getTrantype() != null ? flow.getTrantype()
                : (flow.getBillType() != null ? flow.getBillType() : "30");
        SaleOrderPfPreparation prep = SaleOrderPfPreparation.prepare(flow.getParent(), NATIVE_ACTION,
                billType, bill, flow.getEParam());
        if (!prep.isAccepted()) { return; }

        ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);
        signing.setRequestConsumer(this);
        ActionInterceptor original = getInterceptor();
        CompositeActionInterceptor chain = new CompositeActionInterceptor();
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
            dispatch(authorized, flow, prep, bill, billType);
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
            SaleOrderVO bill, String billType) throws Exception {
        // 流程上下文是差异 VO（无组织集团），完整单据核对见 SaleOrderPfActionDispatch.fullBillOf。
        SaleOrderVO full = SaleOrderPfActionDispatch.fullBillOf(this, bill);
        if (!authorized.getGroupId().equals(full.getParentVO().getPk_group())
                || !authorized.getOrgId().equals(full.getParentVO().getPk_org())
                || !authorized.getObjects().get(0).getObjectPk().equals(bill.getPrimaryKey())) {
            throw new BusinessException("取消审批授权与当前订单不一致，请重新操作。");
        }
        SaleOrderPfActionCommand command = new SaleOrderPfActionCommand(billType, new SaleOrderVO[] { bill },
                flow.getUserObj(), prep.getNote(), prep.getParameters());
        IOperationService gateway = NCLocator.getInstance().lookup(IOperationService.class);
        String dispatched = handoff.dispatch(BUTTON, authorized.getGroupId(),
                authorized.getOrgId(), bill.getPrimaryKey());
        OperationResponse response = gateway.execute(dispatched, command);
        if (response == null || response.isReplayed() || !dispatched.equals(response.getRequestId())
                || !(response.getBusinessResult() instanceof SaleOrderPfActionResult)) {
            throw new BusinessException("无法确认取消审批结果，请先重新查询订单和审批任务。");
        }
        SaleOrderPfActionResult outcome = (SaleOrderPfActionResult) response.getBusinessResult();
        if (!bill.getPrimaryKey().equals(outcome.getPrimaryKey())) {
            throw new BusinessException("无法确认取消审批结果，请先重新查询订单和审批任务。");
        }
        prep.completed();
        Object nativeResult = outcome.getNativeResult();
        if (nativeResult != null) {
            Object[] returned = nativeResult.getClass().isArray() ? (Object[]) nativeResult
                    : new Object[] { nativeResult };
            processReturnObjs(returned);
        }
    }

    /** 受控取消审批只处理单张 990 组织订单；其他组织继续走原生弃审。 */
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
        // 混合组织或无法判定归属必须拒绝，禁止回落原生写入绕过签名。
        SaleOrderSelection.Kind kind = SaleOrderSelection.classify(
                getModel().getContext().getPk_group(), organizations);
        if (kind == SaleOrderSelection.Kind.REJECT_MIXED || kind == SaleOrderSelection.Kind.REJECT_UNKNOWN) {
            throw new BusinessExceptionAdapter(new BusinessException(SaleOrderSelection.rejectionMessage(kind)));
        }
        return SaleOrderSelection.isControlled(kind);
    }

    private List<SaleOrderVO> selectedBills() {
        List<SaleOrderVO> bills = new ArrayList<SaleOrderVO>();
        Object[] datas = getModel().getSelectedOperaDatas();
        if (datas == null) { return bills; }
        for (Object data : datas) {
            if (!(data instanceof SaleOrderVO)) { return new ArrayList<SaleOrderVO>(); }
            bills.add((SaleOrderVO) data);
        }
        return bills;
    }

    @Override public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        if (action != this || event != activeEvent || !isControlledSelection()) {
            throw new BusinessException("当前动作不属于销售订单取消审批。");
        }
        List<SaleOrderVO> bills = selectedBills();
        if (bills.size() != 1) { throw new BusinessException("取消审批受控动作只支持一张销售订单，多选尚未适配。"); }
        SaleOrderVO bill = bills.get(0);
        String group = getModel().getContext().getPk_group();
        if (bill.getParentVO() == null || bill.getPrimaryKey() == null) {
            throw new BusinessException("请选择待取消审批的销售订单。");
        }
        BusinessObjectRef object = new BusinessObjectRef(UUID.randomUUID().toString(), "SO_SALE_ORDER",
                bill.getPrimaryKey(), bill.getParentVO().getVbillcode(), null);
        draft = new OperationContext(null, null, group, bill.getParentVO().getPk_org(), "40060301",
                "销售订单维护", BUTTON, "取消审批", "UPDATE", null, Collections.singletonList(object));
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
