package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.so.m30.self.ISaleOrderMaintain;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.ml.NCLangRes;
import nc.ui.pub.beans.MessageDialog;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.so.m30.billui.action.assist.SaleOrderCloseAction;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pattern.model.transfer.bill.ClientBillCombinServer;
import nc.vo.so.m30.entity.SaleOrderBVO;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.qd.esign.SaleOrderStateCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/** 销售订单整单关闭的签名覆盖层：保留原生关闭原因输入、整单行补齐与界面刷新，签名事由与关闭原因分别传递。 */
public final class QdSaleOrderCloseAction extends SaleOrderCloseAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private ActionEvent activeEvent;
    private ElectronicSignatureActionInterceptor signing;
    private SignatureLifecycleInterceptor lifecycle;
    private SaleOrderVO selected;
    private String closeReason;
    private OperationContext draft;
    private final SaleOrderActionHandoff handoff = new SaleOrderActionHandoff();

    @Override public void actionPerformed(ActionEvent event) {
        if (!needsControlledHandling()) { super.actionPerformed(event); return; }
        // 原生输入与取消路径先行：取消关闭原因时不进入签名，也不产生任何写入。
        String reason;
        try {
            reason = (String) MessageDialog.showInputDlg(getParentContainer(),
                    NCLangRes.getInstance().getStrByID("4006011_0", "04006011-0233"),
                    NCLangRes.getInstance().getStrByID("4006011_0", "04006011-0234"), null, 120);
        } catch (RuntimeException error) {
            throw new BusinessExceptionAdapter(new BusinessException("整单关闭原因输入失败，未执行关闭。", error));
        }
        if (reason == null) { return; }
        try {
            SaleOrderVO bill = singleSelectedBill();
            fillMissingLines(bill);
            setCloseReason(bill, reason);
            selected = bill;
            closeReason = reason;
        } catch (BusinessException error) {
            throw new BusinessExceptionAdapter(error);
        }
        ActionInterceptor original = getInterceptor();
        if (original instanceof SignatureLifecycleInterceptor) {
            throw new BusinessExceptionAdapter(new BusinessException("销售订单整单关闭签名流程正在处理，请勿重复触发。"));
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
                // 策略关闭：保留原生关闭行为，关闭原因已在上方按原生方式写入。
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
            throw new BusinessExceptionAdapter(new BusinessException(
                    "整单关闭未完成，请先重新查询订单和工作项状态。", error));
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
                closeReason = null;
                draft = null;
                handoff.reset();
            }
        }
    }

    /** 判断当前选择是否需要受控：同组织单张 990 订单受控，全非试点走原生，混合组织拒绝。 */
    private boolean needsControlledHandling() {
        if (getModel() == null || getModel().getContext() == null
                || !"40060301".equals(getModel().getContext().getNodeCode())) { return false; }
        Object[] datas = getModel().getSelectedOperaDatas();
        if (datas == null || datas.length == 0) { return false; }
        String group = getModel().getContext().getPk_group();
        Set<Boolean> pilot = new LinkedHashSet<Boolean>();
        for (Object data : datas) {
            if (!(data instanceof SaleOrderVO) || ((SaleOrderVO) data).getParentVO() == null) { return false; }
            pilot.add(Boolean.valueOf(SaleOrderPilotScope.supports(group,
                    ((SaleOrderVO) data).getParentVO().getPk_org())));
        }
        if (pilot.contains(Boolean.FALSE) && pilot.contains(Boolean.TRUE)) {
            throw new BusinessExceptionAdapter(new BusinessException("整单关闭不支持混合销售组织选择，请分开操作。"));
        }
        return pilot.size() == 1 && pilot.contains(Boolean.TRUE);
    }

    private SaleOrderVO singleSelectedBill() throws BusinessException {
        Object[] datas = getModel().getSelectedOperaDatas();
        if (datas == null || datas.length != 1 || !(datas[0] instanceof SaleOrderVO)) {
            throw new BusinessException("受控整单关闭仅支持单张订单，多选尚未适配，请单选后再操作。");
        }
        SaleOrderVO bill = (SaleOrderVO) datas[0];
        if (bill.getParentVO() == null || bill.getPrimaryKey() == null) {
            throw new BusinessException("关闭订单缺少表头或主键，请重新查询后重试。");
        }
        return bill;
    }

    /** 原生客户端会用 ISaleOrderMaintain 回查补齐整单行；签名覆盖层保留同一行为。 */
    private void fillMissingLines(SaleOrderVO bill) throws BusinessException {
        if (bill.getChildrenVO() != null) { return; }
        ISaleOrderMaintain maintain = NCLocator.getInstance().lookup(ISaleOrderMaintain.class);
        SaleOrderVO[] queried = maintain.querySaleorder(new String[] { bill.getPrimaryKey() });
        if (queried == null || queried.length != 1 || queried[0] == null
                || queried[0].getChildrenVO() == null) {
            throw new BusinessException("无法补齐整单行，请重新查询订单后重试。");
        }
        bill.setChildrenVO(queried[0].getChildrenVO());
    }

    /** 关闭原因与签名事由分开传递：此处只写原生 vclosereason，不写签名事由。 */
    private static void setCloseReason(SaleOrderVO bill, String reason) throws BusinessException {
        SaleOrderBVO[] lines = bill.getChildrenVO();
        if (lines == null || lines.length == 0) { throw new BusinessException("整单关闭缺少整单行，请重新查询后重试。"); }
        for (SaleOrderBVO line : lines) {
            if (line == null) { throw new BusinessException("整单行数据不完整，请重新查询后重试。"); }
            line.setVclosereason(reason);
        }
    }

    /** 受控写入：只把一个类型化命令交给网关，成功后再按原方式合并返回结果并刷新界面。 */
    private void dispatch(OperationContext authorized) throws Exception {
        SaleOrderVO bill = selected;
        String reason = closeReason;
        if (bill == null || reason == null || bill.getPrimaryKey() == null) {
            throw new BusinessException("关闭订单已变化，请重新选择后再操作。");
        }
        String group = getModel().getContext().getPk_group();
        String org = bill.getParentVO().getPk_org();
        if (!authorized.getGroupId().equals(group) || !authorized.getOrgId().equals(org)
                || !authorized.getObjects().get(0).getObjectPk().equals(bill.getPrimaryKey())) {
            throw new BusinessException("关闭授权与当前订单不一致，请重新操作。");
        }
        IOperationService gateway = NCLocator.getInstance().lookup(IOperationService.class);
        String requestId = handoff.dispatch("BillClose", group, org, bill.getPrimaryKey());
        OperationResponse response = gateway.execute(requestId,
                new SaleOrderStateCommand(new SaleOrderVO[] { bill }, reason, null));
        requireKnownResponse(requestId, response, bill.getPrimaryKey());
        SaleOrderVO[] returned = (SaleOrderVO[]) response.getBusinessResult();
        new ClientBillCombinServer().combine(new SaleOrderVO[] { bill }, returned);
        processReturnObjs(new Object[] { bill });
    }

    /** 只接受与本次请求对应、非重放、返回真实订单且带整单行的结果。 */
    private static void requireKnownResponse(String requestId, OperationResponse response, String key)
            throws BusinessException {
        if (response == null || response.isReplayed() || !requestId.equals(response.getRequestId())
                || !(response.getBusinessResult() instanceof SaleOrderVO[])) {
            throw new BusinessException("无法确认整单关闭结果，请先重新查询订单再处理。");
        }
        SaleOrderVO[] returned = (SaleOrderVO[]) response.getBusinessResult();
        if (returned.length != 1 || returned[0] == null || !key.equals(returned[0].getPrimaryKey())
                || returned[0].getChildrenVO() == null) {
            throw new BusinessException("无法确认整单关闭结果，请先重新查询订单再处理。");
        }
    }

    @Override public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        if (action != this || event != activeEvent || selected == null || closeReason == null) {
            throw new BusinessException("当前动作不属于销售订单整单关闭。");
        }
        String group = getModel().getContext().getPk_group();
        String org = selected.getParentVO().getPk_org();
        if (group == null || org == null) { throw new BusinessException("无法确定订单集团或销售组织。"); }
        BusinessObjectRef object = new BusinessObjectRef(UUID.randomUUID().toString(), "SO_SALE_ORDER",
                selected.getPrimaryKey(), selected.getParentVO().getVbillcode(), null);
        draft = new OperationContext(null, null, group, org, "40060301", "销售订单维护", "BillClose", "整单关闭",
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
