package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.ui.pub.bill.BillItem;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.AppUiState;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.so.m30.billui.action.SaleOrderSaveAction;
import nc.ui.so.m30.billui.view.SaleOrderBillForm;
import nc.ui.uif2.UIState;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.qd.esign.SaleOrderPfSaveCommand;
import nc.vo.so.qd.esign.SaleOrderPfSaveResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/** Single manual new-order save, retaining the native sales action and PF processing. */
public final class QdSaleOrderSaveAction extends SaleOrderSaveAction
        implements OperationContextProvider, AuthorizedRequestConsumer {
    private static final long serialVersionUID = 1L;
    private ActionEvent activeEvent;
    private SignatureLifecycleInterceptor lifecycle;
    private OperationContext draft;
    private final SaleOrderSaveHandoff handoff = new SaleOrderSaveHandoff();

    @Override public void actionPerformed(ActionEvent event) {
        if (lifecycle != null) {
            if (isClosingSave(event)) { throw saveNotFinished(); }
            return;
        }
        if (!isPilotAdd()) { super.actionPerformed(event); return; }
        ActionInterceptor original = getInterceptor();
        ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);
        signing.setRequestConsumer(this);
        CompositeActionInterceptor chain = new CompositeActionInterceptor();
        chain.addInterceptor(signing);
        if (original != null) { chain.addInterceptor(original); }
        activeEvent = event;
        Throwable failure = null;
        try {
            setInterceptor(chain);
            lifecycle = SignatureLifecycleInstaller.install(this, signing);
            super.actionPerformed(event);
        } catch (RuntimeException error) { failure = error; throw error; }
          catch (Error error) { failure = error; throw error; }
        finally {
            try { signing.finishLifecycle(signing.lifecycleGeneration(), this, event, failure); }
            finally {
                if (getInterceptor() == lifecycle || getInterceptor() == chain) { setInterceptor(original); }
                lifecycle = null; activeEvent = null; draft = null; handoff.reset();
            }
        }
        if (isClosingSave(event) && isPilotAdd()) { throw saveNotFinished(); }
    }

    @Override public void doAction(ActionEvent event) throws Exception {
        if ((isPilotAdd() || lifecycle != null) && (lifecycle == null || event != activeEvent
                || getInterceptor() != lifecycle || !isPilotAdd() || draft == null)) {
            throw new BusinessException("销售订单保存接入状态已变化，请重新发起保存。");
        }
        super.doAction(event);
    }

    @Override public void procFlow(PFlowContext flow) throws Exception {
        OperationContext authorized = handoff.context();
        if (authorized == null) {
            if ((isPilotAdd() && lifecycle == null) || (lifecycle != null && draft == null)) {
                throw new BusinessException("销售订单保存缺少策略核对结果。");
            }
            super.procFlow(flow);
            return;
        }
        if (flow != getFlowContext() || !isPilotAdd() || lifecycle == null || getInterceptor() != lifecycle
                || !empty(getPreActionNames()) || !empty(getAfterActionNames()) || getValidationService() != null
                || isFilledUpInFlow() || !"WRITE".equals(flow.getActionName())
                || (flow.getUserId() != null && flow.getUserId().length() != 0)) {
            throw new BusinessException("销售订单流程不属于已核对的单笔新增保存试点。");
        }
        fillUpContext(flow);
        AggregatedValueObject[] bills = flow.getBillVos();
        if (flow.isBatch() || bills == null || bills.length != 1 || !(bills[0] instanceof SaleOrderVO)) {
            throw new BusinessException("销售订单试点只支持一张手工新增订单。");
        }
        SaleOrderVO bill = (SaleOrderVO) bills[0];
        if (bill.getParentVO() == null || bill.getPrimaryKey() == null
                || !authorized.getGroupId().equals(bill.getParentVO().getPk_group())
                || !authorized.getOrgId().equals(bill.getParentVO().getPk_org())) {
            throw new BusinessException("保存时的销售组织与签名授权不一致。");
        }
        beforeCheck(getFullBill(bill));
        String billType = flow.getTrantype() == null ? flow.getBillType() : flow.getTrantype();
        SaleOrderPfPreparation prep = SaleOrderPfPreparation.prepare(flow.getParent(), "WRITE", billType,
                bill, flow.getEParam());
        if (!prep.isAccepted()) { throw new BusinessException("NC 销售订单流程已取消，订单未保存。"); }
        SaleOrderPfSaveCommand command = new SaleOrderPfSaveCommand(billType, bill, flow.getUserObj(),
                prep.getNote(), prep.getParameters());
        IOperationService gateway = NCLocator.getInstance().lookup(IOperationService.class);
        String dispatched = handoff.dispatch(bill.getParentVO().getPk_group(), bill.getParentVO().getPk_org(),
                bill.getPrimaryKey());
        OperationResponse response = gateway.execute(dispatched, command);
        if (response == null || !dispatched.equals(response.getRequestId()) || response.isReplayed()
                || !(response.getBusinessResult() instanceof SaleOrderPfSaveResult)) {
            throw unknownResult();
        }
        SaleOrderPfSaveResult saved = (SaleOrderPfSaveResult) response.getBusinessResult();
        if (!bill.getPrimaryKey().equals(saved.getPrimaryKey()) || saved.getNativeResult() == null) {
            throw unknownResult();
        }
        prep.completed();
        Object result = saved.getNativeResult();
        Object[] returned;
        if (result.getClass().isArray()) { returned = (Object[]) result; }
        else { returned = (Object[]) Array.newInstance(result.getClass(), 1); returned[0] = result; }
        processReturnObjs(returned);
    }

    @Override public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        if (action != this || event != activeEvent || !isManualAdd()) {
            throw new BusinessException("当前动作不属于销售订单新增保存。");
        }
        String group = getModel().getContext().getPk_group();
        String org = headText("pk_org");
        if (group == null || org == null) { throw new BusinessException("无法确定销售订单集团或销售组织。"); }
        if (!SaleOrderPilotScope.supports(group, org)) {
            throw new BusinessException("销售订单签名试点仅支持 990 销售组织。");
        }
        BusinessObjectRef object = new BusinessObjectRef(UUID.randomUUID().toString(), "SO_SALE_ORDER", null,
                headText("vbillcode"), null);
        draft = new OperationContext(null, null, group, org, "40060301", "销售订单维护", "Save", "保存",
                "INSERT", null, Collections.singletonList(object));
        return draft;
    }

    @Override public boolean isAvailable(OperationContext context) {
        if (!handoff.supports(context)) { return false; }
        try { SaleOrderPfPreparation.verifyNativeContract(); return true; }
        catch (BusinessException unsupported) { return false; }
    }
    @Override public boolean bind(OperationContext context) {
        return isAvailable(context) && handoff.bind(context);
    }
    @Override public boolean wasDispatched(String requestId) { return handoff.wasDispatched(requestId); }
    @Override public void release(String requestId) { handoff.release(requestId); }
    @Override public Container getParentContainer() { return getModel().getContext().getEntranceUI(); }
    @Override public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }

    private boolean isManualAdd() {
        return getModel() != null && getModel().getContext() != null
                && "40060301".equals(getModel().getContext().getNodeCode())
                && getModel().getUiState() == UIState.ADD && getModel().getAppUiState() == AppUiState.ADD;
    }
    private boolean isPilotAdd() {
        if (!isManualAdd()) { return false; }
        String org = headText("pk_org");
        // An unknown organization must fail closed rather than bypass a possible 990 order.
        return org == null || SaleOrderPilotScope.ORG_ID.equals(org);
    }
    private String headText(String name) {
        if (!(editor instanceof SaleOrderBillForm) || ((SaleOrderBillForm) editor).getBillCardPanel() == null) { return null; }
        BillItem item = ((SaleOrderBillForm) editor).getBillCardPanel().getHeadItem(name);
        Object value = item == null ? null : item.getValueObject();
        return value instanceof String && !((String) value).trim().isEmpty() ? (String) value : null;
    }
    private static boolean empty(java.util.List<?> values) { return values == null || values.isEmpty(); }
    private static boolean isClosingSave(ActionEvent event) {
        return event != null && event.getID() == 0 && event.getActionCommand() == null;
    }
    private BusinessExceptionAdapter saveNotFinished() {
        return new BusinessExceptionAdapter(new BusinessException("销售订单尚未保存，窗口保持打开。"));
    }
    private BusinessException unknownResult() {
        return new BusinessException("无法确认销售订单保存结果，请先重新查询订单再处理。");
    }
}
