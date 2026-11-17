package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.so.m30.billui.action.SaleOrderSendApproveAction;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.pub.BusinessException;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/** 销售订单提交（PF SAVE）的签名覆盖层：保留原生流程准备与结果刷新，仅在最终 PF 写入前接入签名与受控网关。 */
public final class QdSaleOrderSendApproveAction extends SaleOrderSendApproveAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private static final String BUTTON = "Commit";
    private static final String NATIVE_ACTION = "SAVE";
    private ActionEvent activeEvent;
    private ElectronicSignatureActionInterceptor signing;
    private SignatureLifecycleInterceptor lifecycle;
    private OperationContext draft;
    private final SaleOrderActionHandoff handoff = new SaleOrderActionHandoff();

    @Override public void actionPerformed(ActionEvent event) {
        if (!isControlled()) { super.actionPerformed(event); return; }
        ActionInterceptor original = getInterceptor();
        if (original instanceof SignatureLifecycleInterceptor) {
            throw new BusinessExceptionAdapter(new BusinessException("销售订单提交签名流程正在处理，请勿重复触发。"));
        }
        ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);
        signing.setRequestConsumer(this);
        CompositeActionInterceptor chain = new CompositeActionInterceptor();
        chain.addInterceptor(signing);
        if (original != null) { chain.addInterceptor(original); }
        activeEvent = event;
        this.signing = signing;
        Throwable failure = null;
        try {
            setInterceptor(chain);
            lifecycle = SignatureLifecycleInstaller.install(this, signing);
            super.actionPerformed(event);
        } catch (RuntimeException error) {
            failure = error;
            throw error;
        } catch (Error error) {
            failure = error;
            throw error;
        } finally {
            try {
                signing.finishLifecycle(signing.lifecycleGeneration(), this, event, failure);
            } finally {
                if (getInterceptor() == lifecycle || getInterceptor() == chain) { setInterceptor(original); }
                lifecycle = null;
                this.signing = null;
                activeEvent = null;
                draft = null;
                handoff.reset();
            }
        }
    }

    /** 原生流程已进入 procFlow 后：有签名授权走受控网关，否则保持原生 PF。 */
    @Override public void procFlow(PFlowContext flow) throws Exception {
        OperationContext authorized = handoff.context();
        if (authorized == null) { super.procFlow(flow); return; }
        SaleOrderPfActionDispatch.dispatch(this, this, flow, authorized, handoff, BUTTON, NATIVE_ACTION,
                new SaleOrderPfActionDispatch.ContextFiller() {
                    public void fillUp(PFlowContext values) throws Exception {
                        QdSaleOrderSendApproveAction.this.fillUpContext(values);
                    }
                });
    }

    /** 只处理首轮 990 组织的销售订单；其他组织继续走原生提交。 */
    private boolean isControlled() {
        if (getModel() == null || getModel().getContext() == null
                || !"40060301".equals(getModel().getContext().getNodeCode())) { return false; }
        List<SaleOrderVO> bills = selectedBills();
        if (bills.isEmpty()) { return false; }
        List<String> organizations = new ArrayList<String>();
        for (SaleOrderVO bill : bills) {
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
        if (action != this || event != activeEvent || !isControlled()) {
            throw new BusinessException("当前动作不属于销售订单提交。");
        }
        List<SaleOrderVO> bills = selectedBills();
        if (bills.isEmpty()) { throw new BusinessException("请选择待提交的销售订单。"); }
        String group = getModel().getContext().getPk_group();
        String org = bills.get(0).getParentVO().getPk_org();
        draft = SaleOrderPfActionDispatch.context(group, org, "40060301", BUTTON, "提交", "UPDATE", bills);
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
}
