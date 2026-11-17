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
import nc.ui.so.m32.billui.action.SaleInvoiceCommitAction;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.pub.BusinessException;
import nc.vo.so.m32.entity.SaleInvoiceVO;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;

/**
 * 销售发票提交（PF SAVE，filledUpInFlow=true）的签名覆盖层。
 *
 * <p>保留原 {@link SaleInvoiceCommitAction} 的流程准备、组合单结果回填与
 * {@code powercommitvalidservice}；本类只在其最终 PF 写入前接入一次电子签名与受控网关。
 * 该交易类型若配置审批流定义，原生「送审」窗口仍在受控准备阶段弹出，取消时不产生业务请求。</p>
 */
public final class QdSaleInvoiceCommitAction extends SaleInvoiceCommitAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private static final String BUTTON = "Commit";
    private static final String NATIVE_ACTION = "SAVE";
    private ActionEvent activeEvent;
    private ElectronicSignatureActionInterceptor signing;
    private SignatureLifecycleInterceptor lifecycle;
    private OperationContext draft;
    private final SalesNodeActionHandoff handoff = new SalesNodeActionHandoff(SalesOtherNodeScope.INVOICE_NODE);

    @Override public void actionPerformed(ActionEvent event) {
        if (!isControlled()) { super.actionPerformed(event); return; }
        ActionInterceptor original = getInterceptor();
        if (original instanceof SignatureLifecycleInterceptor) {
            throw new BusinessExceptionAdapter(new BusinessException("销售发票提交签名流程正在处理，请勿重复触发。"));
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
        SalesPfActionDispatch.dispatch(this, flow, authorized, handoff, SalesOtherNodeScope.INVOICE_NODE,
                BUTTON, NATIVE_ACTION, "32", "nc.vo.so.m32.entity.SaleInvoiceVO",
                new SalesPfActionDispatch.ContextFiller() {
                    public void fillUp(PFlowContext values) throws Exception {
                        QdSaleInvoiceCommitAction.this.fillUpContext(values);
                    }
                });
    }

    /** 只处理本节点、单张已持久化、组织归属明确的发票；其余保持原生提交。 */
    private boolean isControlled() {
        if (getModel() == null || getModel().getContext() == null
                || !SalesOtherNodeScope.INVOICE_NODE.equals(getModel().getContext().getNodeCode())) { return false; }
        List<SaleInvoiceVO> bills = selectedBills();
        if (bills.isEmpty()) { return false; }
        for (SaleInvoiceVO bill : bills) {
            if (bill.getPrimaryKey() == null) { return false; }
        }
        List<String> organizations = new ArrayList<String>();
        for (SaleInvoiceVO bill : bills) {
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

    private List<SaleInvoiceVO> selectedBills() {
        List<SaleInvoiceVO> bills = new ArrayList<SaleInvoiceVO>();
        Object[] datas = getModel().getSelectedOperaDatas();
        if (datas == null) { return bills; }
        for (Object data : datas) {
            if (!(data instanceof SaleInvoiceVO)) { return new ArrayList<SaleInvoiceVO>(); }
            bills.add((SaleInvoiceVO) data);
        }
        return bills;
    }

    @Override public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        if (action != this || event != activeEvent || !isControlled()) {
            throw new BusinessException("当前动作不属于销售发票提交。");
        }
        List<SaleInvoiceVO> bills = selectedBills();
        if (bills.size() != 1) {
            throw new BusinessException("销售发票受控动作只支持一张发票，多选尚未适配，请单选后重试。");
        }
        SaleInvoiceVO bill = bills.get(0);
        if (bill.getParentVO() == null || bill.getPrimaryKey() == null) {
            throw new BusinessException("提交发票缺少表头或主键，请重新查询后重试。");
        }
        draft = SalesPfActionDispatch.context(getModel().getContext().getPk_group(),
                bill.getParentVO().getPk_org(), SalesOtherNodeScope.INVOICE_NODE, BUTTON,
                SalesOtherNodeScope.operationTypeFor(SalesOtherNodeScope.INVOICE_NODE, BUTTON), bill);
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
