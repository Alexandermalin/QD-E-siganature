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
import nc.ui.so.m4331.billui.action.DeliveryApproveAction;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.pub.BusinessException;
import nc.vo.so.m4331.entity.DeliveryVO;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;

/**
 * 发货单审批（PF APPROVE）的签名覆盖层。
 *
 * <p>保留原生 {@code DeliveryApproveAction} 的审批任务校验、审批窗口准备与审批结果回填：本类只在动作入口安装签名拦截链，
 * 并在原生流程已进入 {@code procFlow} 后，用一次性授权把类型化命令交给受控网关替代原生最终 PF 写入。
 * 新单据（尚无持久化主键）、其他节点与无法判定归属的选择继续走原生路径；打开或取消审批窗口不产生业务写入，「确定」后由受控网关执行一次 PF 审批。</p>
 */
public final class QdDeliveryApproveAction extends DeliveryApproveAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private static final String NODE = SalesOtherNodeScope.DELIVERY_NODE;
    private static final String BUTTON = "Approve";
    private static final String NATIVE_ACTION = "APPROVE";
    private static final String DEFAULT_BILL_TYPE = "4331";
    private static final String BILL_CLASS = "nc.vo.so.m4331.entity.DeliveryVO";
    private ActionEvent activeEvent;
    private ElectronicSignatureActionInterceptor signing;
    private SignatureLifecycleInterceptor lifecycle;
    private OperationContext draft;
    private final SalesNodeActionHandoff handoff = new SalesNodeActionHandoff(NODE);

    @Override public void actionPerformed(ActionEvent event) {
        if (!isControlled()) { super.actionPerformed(event); return; }
        ActionInterceptor original = getInterceptor();
        if (original instanceof SignatureLifecycleInterceptor) {
            throw new BusinessExceptionAdapter(new BusinessException("审批签名流程正在处理，请勿重复触发。"));
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

    /** 原生流程已进入 procFlow 后：有签名授权走受控网关，否则保持原生 PF 写入。 */
    @Override public void procFlow(PFlowContext flow) throws Exception {
        OperationContext authorized = handoff.context();
        if (authorized == null) { super.procFlow(flow); return; }
        SalesPfActionDispatch.dispatch(this, flow, authorized, handoff, NODE, BUTTON, NATIVE_ACTION,
                DEFAULT_BILL_TYPE, BILL_CLASS, new SalesPfActionDispatch.ContextFiller() {
                    public void fillUp(PFlowContext values) throws Exception {
                        QdDeliveryApproveAction.this.fillUpContext(values);
                    }
                });
    }

    /** 只处理发货单维护节点的已存单张单据；其他节点、新增或列表未选中继续走原生审批。 */
    private boolean isControlled() {
        if (getModel() == null || getModel().getContext() == null
                || !NODE.equals(getModel().getContext().getNodeCode())) { return false; }
        List<DeliveryVO> bills = selectedBills();
        if (bills.isEmpty()) { return false; }
        List<String> organizations = new ArrayList<String>();
        for (DeliveryVO bill : bills) {
            organizations.add(bill.getParentVO() == null ? null : bill.getParentVO().getPk_org());
        }
        // 混合组织或无法判定归属必须拒绝，禁止回落原生写入绕过签名。
        SalesNodeSelection.Kind kind = SalesNodeSelection.classify(
                getModel().getContext().getPk_group(), organizations);
        if (kind == SalesNodeSelection.Kind.REJECT_MIXED || kind == SalesNodeSelection.Kind.REJECT_UNKNOWN) {
            throw new BusinessExceptionAdapter(new BusinessException(SalesNodeSelection.rejectionMessage(kind)));
        }
        return SalesNodeSelection.isControlled(kind);
    }

    private List<DeliveryVO> selectedBills() {
        List<DeliveryVO> bills = new ArrayList<DeliveryVO>();
        Object[] datas = getModel().getSelectedOperaDatas();
        if (datas == null) { return bills; }
        for (Object data : datas) {
            if (!(data instanceof DeliveryVO)) { return new ArrayList<DeliveryVO>(); }
            bills.add((DeliveryVO) data);
        }
        return bills;
    }

    @Override public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        if (action != this || event != activeEvent || !isControlled()) {
            throw new BusinessException("当前动作不属于审批。");
        }
        List<DeliveryVO> bills = selectedBills();
        if (bills.size() != 1) { throw new BusinessException("发货单受控动作只支持一张单据，多选尚未适配。"); }
        DeliveryVO bill = bills.get(0);
        if (bill.getParentVO() == null || bill.getPrimaryKey() == null) {
            throw new BusinessException("请选择已保存的发货单后再审批。");
        }
        String group = getModel().getContext().getPk_group();
        String org = bill.getParentVO().getPk_org();
        draft = SalesPfActionDispatch.context(group, org, NODE, BUTTON,
                SalesOtherNodeScope.operationTypeFor(NODE, BUTTON), bill);
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
