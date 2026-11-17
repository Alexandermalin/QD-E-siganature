package nc.ui.pu.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.ui.pu.m21.action.OrderSaveAction;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.uapbd.qd.esign.AuthorizedRequestConsumer;
import nc.ui.uapbd.qd.esign.ElectronicSignatureActionInterceptor;
import nc.ui.uapbd.qd.esign.OperationContextProvider;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInstaller;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInterceptor;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.UIState;
import nc.vo.pu.m21.entity.OrderHeaderVO;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pu.qd.esign.PurchaseOrderPfCommand;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.exception.ExceptionUtils;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;

public final class QdPurchaseOrderSaveAction extends OrderSaveAction {
    private static final long serialVersionUID = 1L;
    private static final String NODE = "40040400";
    private static final String BUTTON = "Save";
    private static final String OBJECT_TYPE = "PU_PURCHASE_ORDER";

    private ActionEvent activeEvent;
    private SaveAttempt attempt;
    private PurchaseOrderPfLocator.Scope scope;

    @Override public void actionPerformed(ActionEvent event) {
        if (attempt != null) {
            if (closingSave(event)) throw saveNotFinished();
            return;
        }
        if (combinedSave(event)) {
            super.actionPerformed(event);
            return;
        }
        String node = getModel() == null || getModel().getContext() == null
                ? null : getModel().getContext().getNodeCode();
        if (node == null || node.trim().isEmpty()) {
            throw new BusinessExceptionAdapter(new BusinessException("无法确定采购订单保存的功能节点。"));
        }
        if (!NODE.equals(node)) {
            super.actionPerformed(event);
            return;
        }
        activeEvent = event;
        attempt = new SaveAttempt();
        try {
            super.actionPerformed(event);
        } catch (RuntimeException failure) {
            if (!(ExceptionUtils.unmarsh(failure) instanceof SaveCancelled)) throw failure;
        } finally {
            attempt = null;
            activeEvent = null;
        }
        if (closingSave(event) && getModel().getUiState() != UIState.NOT_EDIT) throw saveNotFinished();
    }

    @Override public void doAction(ActionEvent event) throws Exception {
        if (attempt != null && activeEvent != event) {
            throw new BusinessException("采购订单保存正在处理中。");
        }
        if (getModel() != null && getModel().getContext() != null
                && NODE.equals(getModel().getContext().getNodeCode()) && !combinedSave(event)
                && (attempt == null || activeEvent != event)) {
            throw new BusinessException("采购订单保存缺少本次按钮授权流程。");
        }
        super.doAction(event);
    }

    @Override public void procFlow(PFlowContext flow) throws Exception {
        final SaveAttempt current = attempt;
        if (current == null) {
            super.procFlow(flow);
            return;
        }
        try (PurchaseOrderPfLocator.Scope installed = PurchaseOrderPfLocator.open(new PurchaseOrderPfLocator.Dispatch() {
            @Override public Object execute(Object[] args, IplatFormEntry nativePf) throws BusinessException {
                return dispatch(current, args, nativePf);
            }
        })) {
            scope = installed;
            super.procFlow(flow);
        } finally {
            scope = null;
        }
    }

    @Override protected void processExceptionHandler(Exception failure) {
        if (!(ExceptionUtils.unmarsh(failure) instanceof SaveCancelled)) super.processExceptionHandler(failure);
    }

    @Override protected void fillUpContext(PFlowContext flow) {
        super.fillUpContext(flow);
        if (scope == null || flow.getBillVos() == null) return;
        AggregatedValueObject[] bills = flow.getBillVos();
        if (flow.isBatch() || bills.length != 1 || !(bills[0] instanceof OrderVO)
                || !PurchaseOrderPfLocator.isPurchaseSave(flow.getActionName())) {
            throw new BusinessExceptionAdapter(new BusinessException("采购订单保存授权只能用于本次单据流程。"));
        }
        String type = flow.getTrantype();
        scope.expect(flow.getActionName(), type == null ? flow.getBillType() : type, (OrderVO) bills[0]);
    }

    private OperationContext draft() throws BusinessException {
        AbstractBill[] full = getFullOldVOs();
        if (full == null || full.length != 1 || !(full[0] instanceof OrderVO)) {
            throw new BusinessException("无法确定本次采购流程的完整订单。");
        }
        String group = getModel().getContext().getPk_group();
        OrderHeaderVO head = ((OrderVO) full[0]).getHVO();
        boolean insert = UIState.ADD == getModel().getUiState();
        String pk = insert || head == null ? null : head.getPk_order();
        if (head == null || blank(group) || !group.equals(head.getPk_group()) || blank(head.getPk_org())
                || (!insert && blank(pk))) {
            throw new BusinessException("无法确定采购订单的集团、业务单元或单据主键。");
        }
        return context(group, head.getPk_org(), insert ? "INSERT" : "UPDATE", pk, head.getVbillcode());
    }

    private Object dispatch(SaveAttempt current, Object[] args, IplatFormEntry nativePf) throws BusinessException {
        OrderVO order = (OrderVO) args[3];
        OperationContext fresh = draft();
        if (current.draft != null && (!current.resumable || !sameScope(current.draft, fresh, false))) {
            throw new BusinessException("采购订单请求已经派发，请先刷新核对结果。");
        }
        requireSameOrder(fresh, order);
        current.draft = fresh;
        current.resumable = false;
        SaveAuthorization grant = new SaveAuthorization(fresh, getModel().getContext().getEntranceUI());
        if (!grant.begin()) throw new SaveCancelled();

        Throwable failure = null;
        try {
            String action = (String) args[0];
            String billType = (String) args[1];
            WorkflownoteVO note = (WorkflownoteVO) args[2];
            HashMap<?, ?> parameters = (HashMap<?, ?>) args[5];
            if (grant.request == null) {
                if (current.guarded) throw new BusinessException("采购订单策略已变化，请重新点击保存。");
                return nativePf.processAction(action, billType, note, order, args[4], parameters);
            }
            current.guarded = true;
            PurchaseOrderPfCommand command = new PurchaseOrderPfCommand(action, billType, note, order, args[4], parameters);
            IOperationService service = NCLocator.getInstance().lookup(IOperationService.class);
            grant.dispatched = true;
            OperationResponse response = service.execute(grant.request.getRequestId(), command);
            if (response == null || !grant.request.getRequestId().equals(response.getRequestId())
                    || response.isReplayed() || response.getBusinessResult() == null) {
                throw new BusinessException("采购订单未返回本次保存结果，请刷新核对单据。");
            }
            return response.getBusinessResult();
        } catch (BusinessException | RuntimeException ex) {
            failure = ex;
            current.resumable = ExceptionUtils.unmarsh(ex) instanceof IResumeException;
            throw ex;
        } catch (Error ex) {
            failure = ex;
            throw ex;
        } finally {
            grant.finish(failure);
        }
    }

    private static void requireSameOrder(OperationContext context, OrderVO order) throws BusinessException {
        OrderHeaderVO head = order.getHVO();
        boolean insert = "INSERT".equals(context.getOperationType());
        String pk = context.getObjects().get(0).getObjectPk();
        if (head == null || (!insert && (pk == null || !pk.equals(head.getPk_order())))) {
            throw new BusinessException("采购订单的集团、业务单元或主键与授权不一致。");
        }
        // Native light updates omit unchanged dynamic fields.
        Set<String> fields = insert ? null : head.usedAttributeNames();
        if (!matchesOwner(head, fields, OrderHeaderVO.PK_GROUP, context.getGroupId())
                || !matchesOwner(head, fields, OrderHeaderVO.PK_ORG, context.getOrgId())) {
            throw new BusinessException("采购订单的集团、业务单元或主键与授权不一致。");
        }
    }

    private static boolean matchesOwner(OrderHeaderVO header, Set<String> fields, String name, String expected) {
        Object value = header.getAttributeValue(name);
        return expected.equals(value) || (fields != null && value == null && !fields.contains(name));
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean closingSave(ActionEvent event) {
        return event != null && event.getID() == 0 && event.getActionCommand() == null;
    }

    private static boolean combinedSave(ActionEvent event) {
        return event != null && "SaveAndCommitScriptAction".equals(event.getSource());
    }

    private static BusinessExceptionAdapter saveNotFinished() {
        return new BusinessExceptionAdapter(new BusinessException("采购订单尚未保存，窗口保持打开。"));
    }

    private static OperationContext context(String group, String org, String operation, String pk, String code) {
        BusinessObjectRef object = new BusinessObjectRef(UUID.randomUUID().toString(), OBJECT_TYPE, pk, code, null);
        return new OperationContext(null, null, group, org, NODE, "采购订单维护", BUTTON, "保存", operation,
                null, Collections.singletonList(object));
    }

    private static boolean sameScope(OperationContext draft, OperationContext value, boolean exactObject) {
        if (value == null || !draft.getNodeCode().equals(value.getNodeCode())
                || !draft.getButtonCode().equals(value.getButtonCode())
                || !draft.getGroupId().equals(value.getGroupId()) || !draft.getOrgId().equals(value.getOrgId())
                || !draft.getOperationType().equals(value.getOperationType()) || value.getObjects().size() != 1) return false;
        BusinessObjectRef expected = draft.getObjects().get(0);
        BusinessObjectRef actual = value.getObjects().get(0);
        return actual != null && expected.getObjectType().equals(actual.getObjectType())
                && Objects.equals(expected.getObjectPk(), actual.getObjectPk()) && actual.getParentClientObjectId() == null
                && (!exactObject || expected.getClientObjectId().equals(actual.getClientObjectId()));
    }

    private static final class SaveAttempt {
        private OperationContext draft;
        private boolean guarded;
        private boolean resumable;
    }

    private static final class SaveCancelled extends BusinessException {
        private static final long serialVersionUID = 1L;
        SaveCancelled() { super("已取消采购订单保存签章。"); }
    }

    private static final class SaveAuthorization extends NCAction
            implements OperationContextProvider, AuthorizedRequestConsumer {
        private static final long serialVersionUID = 1L;
        private final OperationContext draft;
        private final Container parent;
        private final ActionEvent event;
        private final SignatureLifecycleInterceptor lifecycle;
        private OperationContext request;
        private boolean dispatched;

        SaveAuthorization(OperationContext draft, Container parent) {
            this.draft = draft;
            this.parent = parent;
            event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, BUTTON);
            ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor();
            signing.setContextProvider(this);
            signing.setRequestConsumer(this);
            setInterceptor(signing);
            lifecycle = SignatureLifecycleInstaller.install(this, signing);
        }

        boolean begin() { return lifecycle.beforeDoAction(this, event); }

        void finish(Throwable failure) {
            if (failure == null) lifecycle.afterDoActionSuccessed(this, event);
            else lifecycle.afterDoActionFailed(this, event, failure);
        }

        @Override public void doAction(ActionEvent ignored) { }
        @Override public OperationContext createContext(Action action, ActionEvent click) { return draft; }
        @Override public Container getParentContainer() { return parent; }
        @Override public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }
        @Override public boolean isAvailable(OperationContext value) { return sameScope(draft, value, true); }

        @Override public boolean bind(OperationContext value) {
            if (!isAvailable(value) || request != null || blank(value.getRequestId())) return false;
            request = value;
            return true;
        }

        @Override public boolean wasDispatched(String requestId) {
            return request != null && request.getRequestId().equals(requestId) && dispatched;
        }

        @Override public void release(String requestId) {
            if (request != null && request.getRequestId().equals(requestId)) request = null;
        }
    }
}
