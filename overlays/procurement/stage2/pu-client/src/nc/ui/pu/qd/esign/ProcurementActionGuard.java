package nc.ui.pu.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.itf.uap.pf.IplatFormEntry;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.ScriptPFlowAction;
import nc.ui.uapbd.qd.esign.AuthorizedRequestConsumer;
import nc.ui.uapbd.qd.esign.ElectronicSignatureActionInterceptor;
import nc.ui.uapbd.qd.esign.OperationContextProvider;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInstaller;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInterceptor;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.UIState;
import nc.vo.pu.m20.entity.PraybillVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pu.m422x.entity.StoreReqAppVO;
import nc.vo.pu.qd.esign.ProcurementBillPfCommand;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.exception.ExceptionUtils;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uif2.LoginContext;

/** Keeps native validation, confirmation, retry and result processing around the guarded PF call. */
final class ProcurementActionGuard {
    private final ScriptPFlowAction owner;
    private final Kind kind;
    private final boolean deleting;
    private boolean running;
    private ActionEvent event;
    private volatile FlowGuard active;

    ProcurementActionGuard(ScriptPFlowAction owner, Kind kind, boolean deleting) {
        this.owner = owner;
        this.kind = kind;
        this.deleting = deleting;
    }

    interface NativeCall {
        void run() throws Exception;
    }

    void runAction(ActionEvent event, NativeCall nativeAction) throws Exception {
        if (running) throw new BusinessException("采购保存或删除正在处理中。");
        this.event = event;
        running = true;
        try {
            nativeAction.run();
        } finally {
            running = false;
            this.event = null;
        }
    }

    void runFlow(NativeCall nativeFlow) throws Exception {
        if ((!deleting && combinedSave(event)) || !isMappedNode()) {
            nativeFlow.run();
            return;
        }
        if (!running || active != null || populated(owner.getPreActionNames()) || populated(owner.getAfterActionNames())) {
            throw new BusinessException("采购保存或删除需要独立执行。");
        }
        FlowGuard current = new FlowGuard();
        active = current;
        try (ProcurementPfLocator.Scope scope = ProcurementPfLocator.open(kind, current)) {
            current.scope = scope;
            nativeFlow.run();
        } catch (Exception failure) {
            current.finish(failure);
            throw failure;
        } catch (Error failure) {
            current.finish(failure);
            throw failure;
        } finally {
            try {
                current.finish(new BusinessException("采购流程未派发。"));
            } finally {
                active = null;
            }
        }
    }

    void filled(PFlowContext flow) {
        if (active == null) return;
        try {
            active.prepare(flow);
        } catch (BusinessException failure) {
            throw new BusinessExceptionAdapter(failure);
        }
    }

    static boolean isSaveCancelled(Exception failure) {
        return ExceptionUtils.unmarsh(failure) instanceof SaveCancelled;
    }

    private static final class SaveCancelled extends BusinessException {
        private static final long serialVersionUID = 1L;

        SaveCancelled() { super("采购单据保存未获签章授权。"); }
    }

    void checkClosingSave(ActionEvent event) {
        if (event != null && event.getID() == 0 && event.getActionCommand() == null && isMappedNode()
                && (owner.getModel() == null || owner.getModel().getUiState() != UIState.NOT_EDIT)) {
            throw new BusinessExceptionAdapter(new BusinessException("采购单据尚未保存，窗口保持打开。"));
        }
    }

    void beforeBatchBill(Object bill) {
        if (!isMappedNode()) return;
        FlowGuard current = active;
        if (current != null && current.thread == Thread.currentThread() && !owner.getFlowContext().isBatch()) {
            return;
        }
        try {
            LoginContext login = owner.getModel().getContext();
            if (login == null || !kind.node.equals(login.getNodeCode()) || !kind.billClass.isInstance(bill)) {
                throw new BusinessException("采购批量删除的功能节点或单据不一致。");
            }
            OperationContext draft = kind.draft(login.getPk_group(), "DELETE", (AbstractBill) bill);
            requireBatchBypass(draft, NCLocator.getInstance().lookup(ISignatureService.class));
        } catch (BusinessException failure) {
            throw new BusinessExceptionAdapter(failure);
        }
    }

    private boolean isMappedNode() {
        LoginContext login = owner.getModel() == null ? null : owner.getModel().getContext();
        return login == null || blank(login.getNodeCode()) || kind.node.equals(login.getNodeCode());
    }

    private static boolean combinedSave(ActionEvent event) {
        return event != null && "SaveAndCommitScriptAction".equals(event.getSource());
    }

    private static boolean populated(List<?> values) {
        return values != null && !values.isEmpty();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private final class FlowGuard implements ProcurementPfLocator.Dispatch {
        private final Thread thread = Thread.currentThread();
        private ProcurementPfLocator.Scope scope;
        private OperationContext draft;
        private Gate gate;
        private boolean retryAllowed;
        private boolean previousGuarded;
        private String actionName;
        private String billType;

        void prepare(PFlowContext flow) throws BusinessException {
            AggregatedValueObject[] bills = flow.getBillVos();
            if (bills == null) return;
            LoginContext login = owner.getModel().getContext();
            String action = flow.getActionName();
            String type = flow.getTrantype() == null ? flow.getBillType() : flow.getTrantype();
            if (login == null || !kind.node.equals(login.getNodeCode()) || blank(login.getPk_group())
                    || !kind.baseType.equals(flow.getBillType()) || blank(type)
                    || !kind.allows(action, deleting) || bills.length == 0) {
                throw new BusinessException("采购功能节点或流程配置与签章范围不一致。");
            }
            if (flow.isBatch() || bills.length != 1) {
                if (draft != null || !deleting || bills.length < 2) {
                    throw new BusinessException("当前采购流程只支持单笔操作。");
                }
                AbstractBill[] full = fullBills(bills.length);
                ISignatureService signatures = NCLocator.getInstance().lookup(ISignatureService.class);
                for (int i = 0; i < bills.length; i++) {
                    OperationContext batchDraft = kind.draft(login.getPk_group(), "DELETE", full[i]);
                    kind.requireBill(batchDraft, bills[i]);
                    requireBatchBypass(batchDraft, signatures);
                }
                scope.allowBatch(action, type, bills);
                return;
            }
            if (deleting && !begin(action, type, bills[0])) {
                flow.setBillVos(null);
                return;
            }
            scope.expect(action, type, (AbstractBill) bills[0]);
        }

        private AbstractBill[] fullBills(int count) throws BusinessException {
            AbstractBill[] full = owner.getFullOldVOs();
            if (full == null || full.length != count) {
                throw new BusinessException("无法确定本次采购流程的完整单据。");
            }
            return full;
        }

        private boolean begin(String action, String type, Object bill) throws BusinessException {
            LoginContext login = owner.getModel().getContext();
            if (login == null || !kind.node.equals(login.getNodeCode())) {
                throw new BusinessException("采购功能节点与签章范围不一致。");
            }
            String operation = deleting ? "DELETE" : owner.getModel().getUiState() == UIState.ADD ? "INSERT" : "UPDATE";
            OperationContext fresh = kind.draft(login.getPk_group(), operation, fullBills(1)[0]);
            kind.requireBill(fresh, bill);
            if (draft != null && (!retryAllowed || !actionName.equals(action) || !billType.equals(type)
                    || !sameScope(draft, fresh, false))) {
                throw new BusinessException("采购流程与已确认的范围不一致，或请求已经派发。");
            }
            retryAllowed = false;
            draft = fresh;
            actionName = action;
            billType = type;
            gate = new Gate(draft, login.getEntranceUI());
            if (!gate.begin()) return false;
            if (previousGuarded && gate.request == null) {
                throw new BusinessException("采购签章策略已变化，请重新确认本次操作。");
            }
            return true;
        }

        @Override public Object execute(Object[] args, IplatFormEntry nativePf) throws BusinessException {
            AbstractBill bill = (AbstractBill) args[3];
            // The native flow has completed validation and beforeCheck at this PF boundary.
            if (!deleting && !begin((String) args[0], (String) args[1], bill)) throw new SaveCancelled();
            if (gate == null || !gate.accepted) throw new BusinessException("采购流程缺少本次确认。");
            kind.requireBill(draft, bill);
            Object result;
            try {
                if (gate.request == null) {
                    result = nativePf.processAction((String) args[0], (String) args[1], (WorkflownoteVO) args[2],
                            bill, args[4], (HashMap<?, ?>) args[5]);
                } else {
                    ProcurementBillPfCommand command = new ProcurementBillPfCommand((String) args[0], (String) args[1],
                            (WorkflownoteVO) args[2], bill, args[4], (HashMap<?, ?>) args[5]);
                    IOperationService service = NCLocator.getInstance().lookup(IOperationService.class);
                    gate.dispatched = true;
                    previousGuarded = true;
                    result = kind.result(gate.request, service.execute(gate.request.getRequestId(), command));
                }
            } catch (BusinessException failure) {
                retryAllowed = ExceptionUtils.unmarsh(failure) instanceof IResumeException;
                finish(failure);
                throw failure;
            } catch (RuntimeException failure) {
                retryAllowed = ExceptionUtils.unmarsh(failure) instanceof IResumeException;
                finish(failure);
                throw failure;
            }
            finish(null);
            return result;
        }

        void finish(Throwable failure) {
            Gate current = gate;
            gate = null;
            if (current != null) current.finish(failure);
        }
    }

    static void requireBatchBypass(OperationContext draft, ISignatureService service) throws BusinessException {
        SignaturePreparation preparation = service.prepare(draft);
        OperationContext checked = preparation == null ? null : preparation.getContext();
        if (checked != null && preparation.getPolicy() != null && !preparation.getPolicy().isControlled()
                && !preparation.requiresExecutionGuard() && "BYPASS".equals(preparation.getState())
                && blank(checked.getRequestId()) && sameScope(draft, checked, true)) {
            return;
        }
        BusinessException failure = new BusinessException("启用签章或审计时，请逐笔删除采购单据。");
        if (checked != null && !blank(checked.getRequestId())) {
            try {
                service.cancel(checked.getRequestId(), checked.getReason());
            } catch (Exception cancellation) {
                failure.addSuppressed(cancellation);
            }
        }
        throw failure;
    }

    private static boolean sameScope(OperationContext first, OperationContext second, boolean checkId) {
        if (second == null || !first.getGroupId().equals(second.getGroupId())
                || !first.getOrgId().equals(second.getOrgId()) || !first.getNodeCode().equals(second.getNodeCode())
                || !first.getButtonCode().equals(second.getButtonCode())
                || !first.getOperationType().equals(second.getOperationType())
                || second.getObjects() == null || second.getObjects().size() != 1) return false;
        BusinessObjectRef expected = first.getObjects().get(0);
        BusinessObjectRef actual = second.getObjects().get(0);
        return actual != null && expected.getObjectType().equals(actual.getObjectType())
                && Objects.equals(expected.getObjectPk(), actual.getObjectPk())
                && actual.getParentClientObjectId() == null
                && (!checkId || expected.getClientObjectId().equals(actual.getClientObjectId()));
    }

    enum Kind {
        STORE_REQ("40040000", "物资需求申请单", "422X", "PU_STORE_REQ", StoreReqAppVO.class),
        PRAYBILL("40040200", "请购单维护", "20", "PU_PRAYBILL", PraybillVO.class),
        INVOICE("40041000", "采购发票维护", "25", "PU_PURCHASE_INVOICE", InvoiceVO.class);

        final String node;
        final String nodeName;
        final String baseType;
        final String objectType;
        final Class<? extends AbstractBill> billClass;

        Kind(String node, String nodeName, String baseType, String objectType, Class<? extends AbstractBill> billClass) {
            this.node = node;
            this.nodeName = nodeName;
            this.baseType = baseType;
            this.objectType = objectType;
            this.billClass = billClass;
        }

        boolean allows(String action, boolean deleting) {
            if (deleting) return "DISCARD".equals(action);
            return "SAVEBASE".equals(action) || (this == PRAYBILL && "SAVEREVISE".equals(action));
        }

        OperationContext draft(String group, String operation, AbstractBill bill) throws BusinessException {
            ISuperVO head = head(bill);
            boolean insert = "INSERT".equals(operation);
            String pk = head == null ? null : head.getPrimaryKey();
            String org = text(head, "pk_org");
            if (blank(group) || !group.equals(text(head, "pk_group")) || org == null || (!insert && blank(pk))) {
                throw new BusinessException("采购单据缺少集团、业务单元或主键，或与当前登录集团不一致。");
            }
            BusinessObjectRef ref = new BusinessObjectRef(UUID.randomUUID().toString(), objectType,
                    insert ? null : pk, text(head, "vbillcode"), null);
            boolean delete = "DELETE".equals(operation);
            return new OperationContext(null, null, group, org, node, nodeName, delete ? "Delete" : "Save",
                    delete ? "删除" : "保存", operation, null, Collections.singletonList(ref));
        }

        void requireBill(OperationContext draft, Object bill) throws BusinessException {
            ISuperVO head = head(bill);
            boolean insert = "INSERT".equals(draft.getOperationType());
            String pk = draft.getObjects().get(0).getObjectPk();
            if (head == null || (!insert && !pk.equals(head.getPrimaryKey()))
                    || !ownerMatches(head, "pk_group", draft.getGroupId(), insert)
                    || !ownerMatches(head, "pk_org", draft.getOrgId(), insert)) {
                throw new BusinessException("采购单据的集团、业务单元或主键与授权不一致。");
            }
        }

        private boolean ownerMatches(ISuperVO head, String field, String expected, boolean insert) {
            Object actual = head.getAttributeValue(field);
            if (expected.equals(actual)) return true;
            if (insert || actual != null) return false;
            Set<String> attributes = head.usedAttributeNames();
            return attributes != null && !attributes.contains(field);
        }

        Object result(OperationContext request, OperationResponse response) throws BusinessException {
            BusinessObjectRef expected = request.getObjects().get(0);
            if (response == null || !request.getRequestId().equals(response.getRequestId()) || response.isReplayed()
                    || response.getObjects() == null || response.getObjects().size() != 1) {
                throw unknown(request);
            }
            BusinessObjectRef actual = response.getObjects().get(0);
            if (actual == null || !objectType.equals(actual.getObjectType())
                    || !expected.getClientObjectId().equals(actual.getClientObjectId())
                    || actual.getParentClientObjectId() != null || blank(actual.getObjectPk())
                    || (expected.getObjectPk() != null && !expected.getObjectPk().equals(actual.getObjectPk()))) {
                throw unknown(request);
            }
            Object value = response.getBusinessResult();
            if ("DELETE".equals(request.getOperationType())) {
                if (value != null) throw unknown(request);
                return null;
            }
            if (!(value instanceof Object[]) || !billClass.isAssignableFrom(value.getClass().getComponentType())
                    || ((Object[]) value).length != 1) {
                throw unknown(request);
            }
            ISuperVO head = head(((Object[]) value)[0]);
            if (head == null || !actual.getObjectPk().equals(head.getPrimaryKey())
                    || !ownerMatches(head, "pk_group", request.getGroupId(), false)
                    || !ownerMatches(head, "pk_org", request.getOrgId(), false)) {
                throw unknown(request);
            }
            return value;
        }

        private ISuperVO head(Object bill) {
            return billClass.isInstance(bill) ? ((AbstractBill) bill).getParent() : null;
        }

        private static String text(ISuperVO head, String field) {
            Object value = head == null ? null : head.getAttributeValue(field);
            return value instanceof String && !blank((String) value) ? (String) value : null;
        }

        private static BusinessException unknown(OperationContext request) {
            return new BusinessException("采购单据操作结果待核对，请求号 " + request.getRequestId() + "。");
        }
    }

    private static final class Gate extends NCAction implements OperationContextProvider, AuthorizedRequestConsumer {
        private static final long serialVersionUID = 1L;
        private final OperationContext draft;
        private final Container parent;
        private final ActionEvent event;
        private final SignatureLifecycleInterceptor lifecycle;
        private OperationContext request;
        private boolean accepted;
        private boolean dispatched;

        Gate(OperationContext draft, Container parent) {
            this.draft = draft;
            this.parent = parent;
            event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, draft.getButtonCode());
            ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor();
            signing.setContextProvider(this);
            signing.setRequestConsumer(this);
            setInterceptor(signing);
            lifecycle = SignatureLifecycleInstaller.install(this, signing);
        }

        boolean begin() {
            accepted = lifecycle.beforeDoAction(this, event);
            return accepted;
        }

        void finish(Throwable failure) {
            if (!accepted) return;
            accepted = false;
            if (failure == null) lifecycle.afterDoActionSuccessed(this, event);
            else lifecycle.afterDoActionFailed(this, event, failure);
        }

        @Override public void doAction(ActionEvent event) {
            throw new UnsupportedOperationException("Native PF dispatches the procurement operation");
        }

        @Override public OperationContext createContext(Action action, ActionEvent event) {
            return draft;
        }

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
