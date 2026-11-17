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
import nc.itf.uapbd.qd.esign.IPolicyService;
import nc.ui.pub.beans.MessageDialog;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.so.m30.billui.action.assist.UnFreezeAction;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pattern.model.transfer.bill.ClientBillCombinServer;
import nc.vo.scmpub.res.BusinessCheck;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.qd.esign.SaleOrderStateCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PolicyDecision;

/**
 * 销售订单解冻（直接服务）的签名覆盖层，保留原生 ATP 不足时的"继续/取消"决策。
 *
 * <p>受控路径先在网关执行一次解冻；若原生 ATP 检查返回不足，客户端保留原生式确认：
 * 取消则无业务变更；继续则在用户已确认的前提下重新授权并携带 ATPCheck=false 执行一次成功写入。
 * 受控网关无法回传可恢复异常，服务端以固定前缀标记该结果。</p>
 */
public final class QdSaleOrderUnFreezeAction extends UnFreezeAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private static final String BUTTON = "UnFreeze";
    private static final String ATP_MARKER = "QD_ESIGN_ATP_CONFIRM:";
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
            handoff.reset();
        }
    }

    @Override public void doAction(ActionEvent event) throws Exception {
        SaleOrderVO bill = selectedBill();
        if (!isControlledSelection() || bill == null || bill.getParentVO() == null
                || bill.getPrimaryKey() == null) {
            super.doAction(event);
            return;
        }
        String group = bill.getParentVO().getPk_group();
        String org = bill.getParentVO().getPk_org();
        if (!policyControlled(org)) { super.doAction(event); return; }
        Authorization first = authorize(event);
        if (first == null) { return; }
        try {
            dispatch(first.context, bill, group, org, null);
            first.success();
        } catch (BusinessException error) {
            first.failure(error);
            if (!isAtpConfirm(error)) { throw error; }
            if (!confirmContinueAfterAtp()) { return; }
            Authorization second = authorize(event);
            if (second == null) { return; }
            try {
                dispatch(second.context, bill, group, org, atpContinue());
                second.success();
            } catch (BusinessException secondFailure) {
                second.failure(secondFailure);
                throw secondFailure;
            }
        }
    }

    /** 一次授权：签名通过后返回绑定的上下文；取消签名返回空。 */
    private Authorization authorize(ActionEvent event) throws BusinessException {
        ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);
        signing.setRequestConsumer(this);
        ActionInterceptor original = getInterceptor();
        CompositeActionInterceptor chain = new CompositeActionInterceptor();
        chain.addInterceptor(signing);
        if (original != null) { chain.addInterceptor(original); }
        SignatureLifecycleInterceptor lifecycle;
        try {
            setInterceptor(chain);
            lifecycle = SignatureLifecycleInstaller.install(this, signing);
        } catch (RuntimeException failure) {
            setInterceptor(original);
            throw failure;
        }
        Authorization authorization = new Authorization(signing, lifecycle, original, event);
        boolean accepted;
        signing.enterOuter(lifecycle);
        try {
            accepted = signing.beforeDoAction(this, event);
        } catch (RuntimeException failure) {
            authorization.failure(failure);
            throw failure;
        } finally {
            signing.leaveOuter(lifecycle);
        }
        if (!accepted) { authorization.failure(null); return null; }
        OperationContext context = handoff.context();
        if (context == null) { authorization.failure(null); return null; }
        authorization.context = context;
        return authorization;
    }

    /** 受控执行一次冻结状态服务：只提交一个类型化命令，成功后按原生方式合并并刷新。 */
    private void dispatch(OperationContext authorized, SaleOrderVO bill, String group, String org,
            java.util.Map<String, Boolean> checks) throws Exception {
        if (!authorized.getGroupId().equals(group) || !authorized.getOrgId().equals(org)
                || !authorized.getObjects().get(0).getObjectPk().equals(bill.getPrimaryKey())) {
            throw new BusinessException("解冻授权与当前订单不一致，请重新操作。");
        }
        SaleOrderStateCommand command = new SaleOrderStateCommand(new SaleOrderVO[] { bill }, null, checks);
        IOperationService gateway = NCLocator.getInstance().lookup(IOperationService.class);
        String requestId = handoff.dispatch(BUTTON, group, org, bill.getPrimaryKey());
        OperationResponse response = gateway.execute(requestId, command);
        if (response == null || response.isReplayed() || !requestId.equals(response.getRequestId())
                || !(response.getBusinessResult() instanceof SaleOrderVO[])) {
            throw new BusinessException("无法确认解冻结果，请先重新查询订单再处理。");
        }
        SaleOrderVO[] returned = (SaleOrderVO[]) response.getBusinessResult();
        if (returned.length != 1 || returned[0] == null
                || !bill.getPrimaryKey().equals(returned[0].getPrimaryKey())) {
            throw new BusinessException("无法确认解冻结果，请先重新查询订单再处理。");
        }
        new ClientBillCombinServer().combine(new SaleOrderVO[] { bill }, returned);
        getModel().directlyUpdate(new SaleOrderVO[] { bill });
    }

    /** 原生式 ATP 继续确认：默认选择"否"，避免误提交。 */
    private boolean confirmContinueAfterAtp() {
        int choice = MessageDialog.showYesNoDlg(getParentContainer(), "ATP 检查",
                "该订单存在 ATP 可用量不足提示。是否继续解冻？\n继续将跳过 ATP 检查并重新进行电子签名确认。", 7);
        return choice == 4;
    }

    private static boolean isAtpConfirm(BusinessException error) {
        return error != null && error.getMessage() != null && error.getMessage().startsWith(ATP_MARKER);
    }

    private static java.util.Map<String, Boolean> atpContinue() {
        java.util.Map<String, Boolean> map = new java.util.HashMap<String, Boolean>();
        map.put(BusinessCheck.ATPCheck.getCheckCode(), Boolean.FALSE);
        return map;
    }

    /** 策略查询：未受控时保持原生解冻流程。 */
    private boolean policyControlled(String org) throws BusinessException {
        IPolicyService service = NCLocator.getInstance().lookup(IPolicyService.class);
        PolicyDecision decision = service.resolve(org, "40060301", BUTTON);
        return decision != null && decision.isControlled();
    }

    /** 受控解冻只处理单张 990 组织订单；混合组织或归属不明必须拒绝。 */
    private boolean isControlledSelection() {
        if (getModel() == null || getModel().getContext() == null
                || !"40060301".equals(getModel().getContext().getNodeCode())) { return false; }
        Object data = getModel().getSelectedData();
        if (!(data instanceof SaleOrderVO) || ((SaleOrderVO) data).getParentVO() == null) { return false; }
        SaleOrderVO bill = (SaleOrderVO) data;
        SaleOrderSelection.Kind kind = SaleOrderSelection.classify(
                getModel().getContext().getPk_group(), Collections.singletonList(bill.getParentVO().getPk_org()));
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
            throw new BusinessException("当前动作不属于销售订单解冻。");
        }
        SaleOrderVO bill = selectedBill();
        if (bill == null || bill.getParentVO() == null || bill.getPrimaryKey() == null) {
            throw new BusinessException("请选择待解冻的销售订单。");
        }
        String group = getModel().getContext().getPk_group();
        BusinessObjectRef object = new BusinessObjectRef(UUID.randomUUID().toString(), "SO_SALE_ORDER",
                bill.getPrimaryKey(), bill.getParentVO().getVbillcode(), null);
        draft = new OperationContext(null, null, group, bill.getParentVO().getPk_org(), "40060301",
                "销售订单维护", BUTTON, "解冻", "UPDATE", null, Collections.singletonList(object));
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

    /** 一次签名的生命周期：成功/失败回调后释放并恢复原拦截链。 */
    private final class Authorization {
        private final ElectronicSignatureActionInterceptor signing;
        private final SignatureLifecycleInterceptor lifecycle;
        private final ActionInterceptor original;
        private final ActionEvent event;
        private OperationContext context;
        private Authorization(ElectronicSignatureActionInterceptor signing, SignatureLifecycleInterceptor lifecycle,
                ActionInterceptor original, ActionEvent event) {
            this.signing = signing; this.lifecycle = lifecycle; this.original = original; this.event = event;
        }
        private void success() {
            signing.afterDoActionSuccessed(QdSaleOrderUnFreezeAction.this, event);
            release(null);
        }
        private void failure(Throwable failure) {
            if (failure != null) {
                try { signing.afterDoActionFailed(QdSaleOrderUnFreezeAction.this, event, failure); }
                catch (RuntimeException ignored) { }
            }
            release(failure);
        }
        private void release(Throwable failure) {
            try {
                signing.finishLifecycle(signing.lifecycleGeneration(), QdSaleOrderUnFreezeAction.this, event, failure);
            } finally {
                setInterceptor(original);
            }
        }
    }
}
