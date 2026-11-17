package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.so.m30.billui.action.assist.FreezeAction;

import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pattern.model.entity.bill.IBill;
import nc.vo.pubapp.pattern.model.transfer.bill.ClientBillCombinServer;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.qd.esign.SaleOrderStateCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/** 销售订单冻结的签名覆盖层：保留原生选择、返回 VO 合并与界面刷新，仅在真实写入前接入签名与受控网关。 */
public final class QdSaleOrderFreezeAction extends FreezeAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private ActionEvent activeEvent;
    private ElectronicSignatureActionInterceptor signing;
    private SignatureLifecycleInterceptor lifecycle;
    private SaleOrderVO selected;
    private OperationContext draft;
    private final SaleOrderActionHandoff handoff = new SaleOrderActionHandoff();

    @Override public void actionPerformed(ActionEvent event) {
        if (!isControlledSelection()) { super.actionPerformed(event); return; }
        ActionInterceptor original = getInterceptor();
        if (original instanceof SignatureLifecycleInterceptor) {
            throw new BusinessExceptionAdapter(new BusinessException("销售订单冻结签名流程正在处理，请勿重复触发。"));
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
            if (!lifecycle.beforeDoAction(this, event)) { return; }
            if (handoff.context() == null) {
                // 策略关闭或无需签名：保持原生冻结行为，不做受控写入。
                super.doAction(event);
            } else {
                dispatch(handoff.context());
            }
            lifecycle.afterDoActionSuccessed(this, event);
        } catch (BusinessException error) {
            failure = error;
            lifecycle.afterDoActionFailed(this, event, error);
            throw new BusinessExceptionAdapter(error);
        } catch (Exception error) {
            failure = error;
            lifecycle.afterDoActionFailed(this, event, error);
            throw new BusinessExceptionAdapter(new BusinessException("销售订单冻结未完成，请先查询订单状态。", error));
        } catch (Error error) {
            failure = error;
            lifecycle.afterDoActionFailed(this, event, error);
            throw error;
        } finally {
            try {
                signing.finishLifecycle(signing.lifecycleGeneration(), this, event, failure);
            } finally {
                if (getInterceptor() == lifecycle || getInterceptor() == chain) { setInterceptor(original); }
                lifecycle = null;
                this.signing = null;
                activeEvent = null;
                selected = null;
                draft = null;
                handoff.reset();
            }
        }
    }

    /** 受控冻结只处理单张 990 组织订单；其他组织继续走原生冻结。 */
    private boolean isControlledSelection() {
        if (getModel() == null || getModel().getContext() == null
                || !"40060301".equals(getModel().getContext().getNodeCode())) { return false; }
        SaleOrderVO bill = selectedData();
        if (bill == null || bill.getParentVO() == null || bill.getPrimaryKey() == null) { return false; }
        return SaleOrderPilotScope.supports(getModel().getContext().getPk_group(), bill.getParentVO().getPk_org());
    }

    private SaleOrderVO selectedData() {
        Object data = getModel().getSelectedData();
        return data instanceof SaleOrderVO ? (SaleOrderVO) data : null;
    }

    /** 受控写入：只把一个类型化命令交给网关，成功后再按原 VO 合并返回结果并刷新界面。 */
    private void dispatch(OperationContext authorized) throws Exception {
        SaleOrderVO bill = selected;
        if (bill == null || bill.getParentVO() == null || bill.getPrimaryKey() == null) {
            throw new BusinessException("冻结订单已变化，请重新选择后再操作。");
        }
        String group = getModel().getContext().getPk_group();
        String org = bill.getParentVO().getPk_org();
        if (!authorized.getGroupId().equals(group) || !authorized.getOrgId().equals(org)
                || !authorized.getObjects().get(0).getObjectPk().equals(bill.getPrimaryKey())) {
            throw new BusinessException("冻结授权与当前订单不一致，请重新操作。");
        }
        IOperationService gateway = NCLocator.getInstance().lookup(IOperationService.class);
        String requestId = handoff.dispatch("Freeze", group, org, bill.getPrimaryKey());
        OperationResponse response = gateway.execute(requestId,
                new SaleOrderStateCommand(new SaleOrderVO[] { bill }, null, null));
        requireKnownResponse(requestId, response, bill.getPrimaryKey());
        SaleOrderVO[] returned = (SaleOrderVO[]) response.getBusinessResult();
        new ClientBillCombinServer().combine(new SaleOrderVO[] { bill }, returned);
        getModel().directlyUpdate(new SaleOrderVO[] { bill });
    }

    /** 只接受与本次请求对应、非重放、且返回真实订单的结果；否则按未知结果处理。 */
    private static void requireKnownResponse(String requestId, OperationResponse response, String key)
            throws BusinessException {
        if (response == null || response.isReplayed() || !requestId.equals(response.getRequestId())
                || !(response.getBusinessResult() instanceof SaleOrderVO[])) {
            throw new BusinessException("无法确认冻结结果，请先重新查询订单再处理。");
        }
        SaleOrderVO[] returned = (SaleOrderVO[]) response.getBusinessResult();
        if (returned.length != 1 || returned[0] == null || !key.equals(returned[0].getPrimaryKey())) {
            throw new BusinessException("无法确认冻结结果，请先重新查询订单再处理。");
        }
    }

    @Override public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        if (action != this || event != activeEvent || !isControlledSelection()) {
            throw new BusinessException("当前动作不属于销售订单冻结。");
        }
        SaleOrderVO bill = selectedData();
        if (bill == null || bill.getParentVO() == null || bill.getPrimaryKey() == null) {
            throw new BusinessException("请选择待冻结的销售订单。");
        }
        selected = bill;
        String group = getModel().getContext().getPk_group();
        String org = bill.getParentVO().getPk_org();
        if (group == null || org == null) { throw new BusinessException("无法确定订单集团或销售组织。"); }
        BusinessObjectRef object = new BusinessObjectRef(UUID.randomUUID().toString(), "SO_SALE_ORDER",
                bill.getPrimaryKey(), bill.getParentVO().getVbillcode(), null);
        draft = new OperationContext(null, null, group, org, "40060301", "销售订单维护", "Freeze", "冻结",
                "UPDATE", null, Collections.singletonList(object));
        return draft;
    }

    @Override public boolean isAvailable(OperationContext context) {
        return handoff.supports(context) && selected != null && context.getObjects().size() == 1
                && context.getObjects().get(0).getObjectPk().equals(selected.getPrimaryKey());
    }
    @Override public boolean bind(OperationContext context) { return isAvailable(context) && handoff.bind(context); }
    @Override public boolean wasDispatched(String requestId) { return handoff.wasDispatched(requestId); }
    @Override public void release(String requestId) { handoff.release(requestId); }
    @Override public Container getParentContainer() {
        return getModel() == null || getModel().getContext() == null ? null : getModel().getContext().getEntranceUI();
    }
    @Override public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }
}
