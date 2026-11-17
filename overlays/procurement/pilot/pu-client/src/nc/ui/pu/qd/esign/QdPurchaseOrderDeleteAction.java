package nc.ui.pu.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.ui.pu.m21.action.OrderDeleteAction;
import nc.ui.pu.m21.service.OrderDeleteService;
import nc.ui.pubapp.pub.task.ISingleBillService;
import nc.ui.pubapp.pub.task.MultiBillTaskRunner;
import nc.ui.uapbd.qd.esign.AuthorizedRequestConsumer;
import nc.ui.uapbd.qd.esign.ElectronicSignatureActionInterceptor;
import nc.ui.uapbd.qd.esign.OperationContextProvider;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInstaller;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInterceptor;
import nc.ui.uif2.NCAction;
import nc.vo.pu.m21.context.OrderContext;
import nc.vo.pu.m21.entity.OrderHeaderVO;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pu.qd.esign.PurchaseOrderOperationCommand;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uif2.LoginContext;

public final class QdPurchaseOrderDeleteAction extends OrderDeleteAction {
    private static final long serialVersionUID = 1L;
    private static final String NODE = "40040400";
    private static final String BUTTON = "Delete";
    private static final String DELETE = PurchaseOrderOperationCommand.DELETE;
    private static final String ORDER = PurchaseOrderOperationCommand.OBJECT_TYPE;

    private final OrderDeleteService<OrderVO> nativeDelete = new OrderDeleteService<OrderVO>();
    private final ISingleBillService<Object> singleDelete = new ISingleBillService<Object>() {
        @Override public Object operateBill(Object bill) throws Exception {
            DeleteClick click = pending;
            if (click == null && !isPilotNode()) {
                return nativeDelete.operateBill((OrderVO) bill);
            }
            if (click == null || click.authorization == null) {
                throw new BusinessException("采购订单删除缺少单笔确认。");
            }
            return click.authorization.dispatch((OrderVO) bill, nativeDelete);
        }
    };
    private final ISingleBillService<Object> batchDelete = new ISingleBillService<Object>() {
        @Override public Object operateBill(Object bill) throws Exception {
            OrderVO order = (OrderVO) bill;
            requireBatchBypass(draftFor(order), signatureService());
            return nativeDelete.operateBill(order);
        }
    };

    private boolean confirming;
    private Object[] ordersUnderConfirmation;
    private OperationContext[] confirmedSelection;
    private volatile DeleteClick pending;

    public QdPurchaseOrderDeleteAction() {
        setSingleBillService(singleDelete);
    }

    @Override public boolean beforeStartDoAction(ActionEvent event) throws Exception {
        if (confirming || pending != null) {
            return false;
        }
        if (!isPilotNode()) {
            return super.beforeStartDoAction(event);
        }
        if (getSingleBillService() != singleDelete || getMultiBillService() != null) {
            throw new BusinessException("采购订单删除服务配置与签章试点不符。");
        }
        confirming = true;
        try {
            if (!super.beforeStartDoAction(event)) {
                return false;
            }
            if (confirmedSelection.length == 1) {
                DeleteAuthorization authorization = new DeleteAuthorization(
                        confirmedSelection[0], new ElectronicSignatureActionInterceptor());
                authorization.parent = getModel().getContext().getEntranceUI();
                if (!authorization.begin(event)) {
                    return false;
                }
                pending = new DeleteClick(event, authorization, ordersUnderConfirmation, confirmedSelection);
            } else {
                ISignatureService service = signatureService();
                for (OperationContext order : confirmedSelection) {
                    requireBatchBypass(order, service);
                }
                pending = new DeleteClick(event, null, ordersUnderConfirmation, confirmedSelection);
            }
            return true;
        } finally {
            confirmedSelection = null;
            ordersUnderConfirmation = null;
            confirming = false;
        }
    }

    @Override protected void checkDataPermission(Object selection) throws Exception {
        super.checkDataPermission(selection);
        if (!confirming) {
            return;
        }
        if (!(selection instanceof Object[]) || ((Object[]) selection).length == 0) {
            throw new BusinessException("请选择要删除的采购订单。");
        }
        // This is the exact selection saved by the native action for confirmation and dispatch.
        Object[] orders = (Object[]) selection;
        OperationContext[] drafts = new OperationContext[orders.length];
        String group = getModel().getContext().getPk_group();
        for (int i = 0; i < orders.length; i++) {
            if (!(orders[i] instanceof OrderVO)) {
                throw new BusinessException("采购订单删除对象无效。");
            }
            drafts[i] = draftFor((OrderVO) orders[i]);
            if (!drafts[i].getGroupId().equals(group)) {
                throw new BusinessException("采购订单与当前登录集团不一致。");
            }
        }
        ordersUnderConfirmation = orders;
        confirmedSelection = drafts;
    }

    @Override public void doAction(ActionEvent event) throws Exception {
        DeleteClick click = pending;
        if ((click != null || isPilotNode()) && (click == null || click.event != event)) {
            throw new BusinessException("采购订单删除缺少本次原生确认。");
        }
        if (click != null) {
            click.requireConfirmedOrders();
        }
        super.doAction(event);
    }

    @Override public MultiBillTaskRunner<Object> getMultiBillTaskRunner() {
        MultiBillTaskRunner<Object> runner = super.getMultiBillTaskRunner();
        if (pending != null || isPilotNode()) {
            // The second SwingWorker can outlive the outer NCAsynAction callbacks.
            runner.setSingleBillService(batchDelete);
        }
        return runner;
    }

    @Override public void doAfterSuccess(ActionEvent event) {
        try {
            super.doAfterSuccess(event);
        } finally {
            finish(event, null);
        }
    }

    @Override public boolean doAfterFailure(ActionEvent event, Throwable failure) {
        try {
            return super.doAfterFailure(event, failure);
        } finally {
            finish(event, failure);
        }
    }

    private void finish(ActionEvent event, Throwable failure) {
        DeleteClick click = pending;
        if (click == null || click.event != event) {
            return;
        }
        try {
            if (click.authorization != null) {
                click.authorization.finish(event, failure);
            }
        } finally {
            pending = null;
        }
    }

    private boolean isPilotNode() {
        LoginContext login = getModel().getContext();
        // An uninitialized node must never route the pilot to native deletion.
        return login == null || blank(login.getNodeCode()) || NODE.equals(login.getNodeCode());
    }

    private static ISignatureService signatureService() {
        return NCLocator.getInstance().lookup(ISignatureService.class);
    }

    static OperationContext draftFor(OrderVO order) throws BusinessException {
        OrderHeaderVO head = order == null ? null : order.getHVO();
        if (head == null || blank(head.getPk_group()) || blank(head.getPk_org()) || blank(head.getPk_order())) {
            throw new BusinessException("采购订单删除缺少集团、业务单元或单据主键。");
        }
        BusinessObjectRef ref = new BusinessObjectRef(UUID.randomUUID().toString(), ORDER,
                head.getPk_order(), head.getVbillcode(), null);
        return new OperationContext(null, null, head.getPk_group(), head.getPk_org(), NODE,
                "采购订单维护", BUTTON, "删除", DELETE, null, Collections.singletonList(ref));
    }

    static void requireBatchBypass(OperationContext draft, ISignatureService service) throws BusinessException {
        SignaturePreparation preparation = service.prepare(draft);
        if (preparation != null && preparation.getPolicy() != null && preparation.getContext() != null
                && !preparation.getPolicy().isControlled() && !preparation.requiresExecutionGuard()
                && "BYPASS".equals(preparation.getState())
                && blank(preparation.getContext().getRequestId())
                && matches(draft, preparation.getContext())) {
            return;
        }
        BusinessException failure = new BusinessException("启用签章或审计时，采购订单删除试点仅支持单笔。");
        if (preparation != null && preparation.getContext() != null
                && !blank(preparation.getContext().getRequestId())) {
            try {
                service.cancel(preparation.getContext().getRequestId(), preparation.getContext().getReason());
            } catch (Exception cancellation) {
                failure.addSuppressed(cancellation);
            }
        }
        throw failure;
    }

    private static boolean matches(OperationContext draft, OperationContext context) {
        return context != null && draft.getGroupId().equals(context.getGroupId())
                && draft.getOrgId().equals(context.getOrgId())
                && NODE.equals(context.getNodeCode()) && BUTTON.equals(context.getButtonCode())
                && DELETE.equals(context.getOperationType())
                && matchesObject(draft.getObjects().get(0).getObjectPk(), context.getObjects());
    }

    private static boolean matchesBill(OperationContext draft, Object bill) {
        OrderHeaderVO head = bill instanceof OrderVO ? ((OrderVO) bill).getHVO() : null;
        return head != null && draft.getGroupId().equals(head.getPk_group())
                && draft.getOrgId().equals(head.getPk_org())
                && draft.getObjects().get(0).getObjectPk().equals(head.getPk_order());
    }

    private static boolean matchesObject(String pk, List<BusinessObjectRef> refs) {
        return refs != null && refs.size() == 1 && refs.get(0) != null
                && ORDER.equals(refs.get(0).getObjectType()) && pk.equals(refs.get(0).getObjectPk());
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class DeleteClick {
        final ActionEvent event;
        final DeleteAuthorization authorization;
        private final Object[] orders;
        private final OperationContext[] drafts;

        DeleteClick(ActionEvent event, DeleteAuthorization authorization, Object[] orders, OperationContext[] drafts) {
            this.event = event;
            this.authorization = authorization;
            this.orders = orders;
            this.drafts = drafts;
        }

        void requireConfirmedOrders() throws BusinessException {
            for (int i = 0; i < orders.length; i++) {
                if (!matchesBill(drafts[i], orders[i])) {
                    throw new BusinessException("采购订单与已确认的删除范围不一致。");
                }
            }
        }
    }

    static final class DeleteAuthorization extends NCAction
            implements OperationContextProvider, AuthorizedRequestConsumer {
        private static final long serialVersionUID = 1L;
        private final OperationContext draft;
        private final SignatureLifecycleInterceptor lifecycle;
        private OperationContext request;
        private boolean accepted;
        private boolean dispatched;
        private Container parent;

        DeleteAuthorization(OperationContext draft, ElectronicSignatureActionInterceptor signing) {
            this.draft = draft;
            signing.setContextProvider(this);
            signing.setRequestConsumer(this);
            setInterceptor(signing);
            lifecycle = SignatureLifecycleInstaller.install(this, signing);
        }

        boolean begin(ActionEvent event) {
            accepted = lifecycle.beforeDoAction(this, event);
            return accepted;
        }

        OrderVO dispatch(OrderVO order, OrderDeleteService<OrderVO> nativeDelete) throws Exception {
            if (!accepted || dispatched || !matchesBill(draft, order)) {
                throw new BusinessException("采购订单与已确认的删除范围不一致，或已发出删除请求。");
            }
            if (request == null) {
                dispatched = true;
                return nativeDelete.operateBill(order);
            }
            IOperationService service = NCLocator.getInstance().lookup(IOperationService.class);
            PurchaseOrderOperationCommand command = PurchaseOrderOperationCommand.forDelete(
                    new OrderVO[] {order}, new OrderContext[] {new OrderContext()});
            String requestId = request.getRequestId();
            dispatched = true;
            OperationResponse response;
            try {
                response = service.execute(requestId, command);
            } catch (RuntimeException uncertainty) {
                BusinessException failure = unknownResult(requestId);
                failure.initCause(uncertainty);
                throw failure;
            }
            if (response == null || !requestId.equals(response.getRequestId())
                    || response.getBusinessResult() != null
                    || !matchesObject(draft.getObjects().get(0).getObjectPk(), response.getObjects())) {
                throw unknownResult(requestId);
            }
            return order;
        }

        void finish(ActionEvent event, Throwable failure) {
            if (!accepted) {
                return;
            }
            accepted = false;
            if (failure == null) {
                lifecycle.afterDoActionSuccessed(this, event);
            } else {
                lifecycle.afterDoActionFailed(this, event, failure);
            }
        }

        private static BusinessException unknownResult(String requestId) {
            return new BusinessException("采购订单删除结果待核对，请求号 " + requestId + "。");
        }

        @Override public void doAction(ActionEvent event) {
            throw new UnsupportedOperationException("The native delete service dispatches the order");
        }

        @Override public OperationContext createContext(Action action, ActionEvent event) {
            return draft;
        }

        @Override public Container getParentContainer() {
            return parent;
        }

        @Override public String getCurrentUserDisplayName() {
            return null;
        }

        @Override public boolean isAvailable(OperationContext context) {
            return matches(draft, context);
        }

        @Override public boolean bind(OperationContext context) {
            if (request != null || !isAvailable(context) || blank(context.getRequestId())) {
                return false;
            }
            request = context;
            return true;
        }

        @Override public boolean wasDispatched(String requestId) {
            return dispatched && request != null && request.getRequestId().equals(requestId);
        }

        @Override public void release(String requestId) {
            if (request != null && request.getRequestId().equals(requestId)) {
                request = null;
            }
        }
    }
}
