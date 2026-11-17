package nc.ui.pu.qd.esign.workflow;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.uapbd.qd.esign.IPolicyService;
import nc.ui.pubapp.uif2app.actions.pflow.ScriptPFlowAction;
import nc.ui.uapbd.qd.esign.AuthorizedRequestConsumer;
import nc.ui.uapbd.qd.esign.ElectronicSignatureActionInterceptor;
import nc.ui.uapbd.qd.esign.OperationContextProvider;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInstaller;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInterceptor;
import nc.ui.uif2.NCAction;
import nc.vo.pu.qd.esign.ProcurementWorkflowCommand;
import nc.vo.pu.qd.esign.ProcurementWorkflowRoute;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uif2.LoginContext;

/** Replaces only the native write after approvals, selections, and workflow parameters are confirmed. */
public final class WorkflowActionBridge {
    private static final String BATCH_MESSAGE = "启用签章或审计时，采购工作流请按单笔操作。";
    private static final String COMPOSITE_MESSAGE = "启用保存或提交签章审计时，请先保存，再提交。";

    public interface NativeAction { void run() throws Exception; }

    private WorkflowActionBridge() { }

    public static void perform(ScriptPFlowAction owner, String node, String button, ActionEvent event,
            NativeAction original) throws Exception {
        try (Scope scope = install(owner, node, button, event, false)) {
            original.run();
        }
    }

    /** Opens a separate scope on NC's batch worker thread. */
    public static Scope open(ScriptPFlowAction owner, String node, String button, ActionEvent event)
            throws BusinessException {
        return install(owner, node, button, event, true);
    }

    private static Scope install(ScriptPFlowAction owner, String node, String button, ActionEvent event, boolean worker)
            throws BusinessException {
        LoginContext login = owner.getModel() == null ? null : owner.getModel().getContext();
        if (login == null || blank(login.getNodeCode())) throw new BusinessException("无法确定采购工作流节点。");
        if (!node.equals(login.getNodeCode())) return new Scope(null);
        return new Scope(WorkflowPfLocator.open(new Dispatch(owner, node, button, event, login, worker)));
    }

    public static final class Scope implements AutoCloseable {
        private final WorkflowPfLocator.Scope locator;

        private Scope(WorkflowPfLocator.Scope locator) {
            this.locator = locator;
        }

        @Override public void close() {
            if (locator != null) locator.close();
        }
    }

    private static final class Dispatch implements WorkflowPfLocator.Dispatch {
        private final ScriptPFlowAction owner;
        private final String node;
        private final String button;
        private final String pfAction;
        private final ActionEvent event;
        private final LoginContext login;
        private final AggregatedValueObject[] workerSelection;
        private boolean dispatching;

        Dispatch(ScriptPFlowAction owner, String node, String button, ActionEvent event, LoginContext login,
                boolean worker) throws BusinessException {
            this.owner = owner;
            this.node = node;
            this.button = button;
            this.event = event;
            this.login = login;
            pfAction = ProcurementWorkflowRoute.Action.forButton(button).pfAction();
            AggregatedValueObject[] bills = worker ? owner.getFlowContext().getBillVos() : null;
            if (worker && (bills == null || bills.length == 0)) {
                throw new BusinessException("无法核对采购工作流后台批次。");
            }
            workerSelection = bills == null ? null : bills.clone();
        }

        @Override public Object execute(Method method, Object[] args, WorkflowPfLocator.Call original)
                throws BusinessException {
            if (dispatching) throw new BusinessException("采购工作流正在派发。");
            if (args.length != 6 || !(args[0] instanceof String) || !(args[1] instanceof String)) {
                throw new BusinessException("采购工作流调用参数不受支持。");
            }
            String action = (String) args[0];
            if ("Commit".equals(button) && "SAVEBASE".equals(action) && args[3] instanceof AbstractBill) {
                Set<String> orgs = new LinkedHashSet<String>();
                orgs.add(organization(fullBill((AbstractBill) args[3]), login.getPk_group()));
                IPolicyService policies = policies();
                requireUncontrolled(policies, orgs, node, "Save", COMPOSITE_MESSAGE);
                requireUncontrolled(policies, orgs, node, button, COMPOSITE_MESSAGE);
                return original.run();
            }
            if (!matchesPfAction(pfAction, action, login.getPk_loginUser())) {
                throw new BusinessException("采购按钮与原生工作流动作不匹配。");
            }
            boolean nativeBatch = "processBatch".equals(method.getName());
            if (workerSelection != null || nativeBatch) {
                Set<String> orgs = new LinkedHashSet<String>();
                if (workerSelection != null) addOrganizations(orgs, workerSelection);
                if (args[3] instanceof AggregatedValueObject[]) {
                    addOrganizations(orgs, (AggregatedValueObject[]) args[3]);
                } else if (args[3] instanceof AggregatedValueObject && !nativeBatch) {
                    orgs.add(organization(fullBill((AggregatedValueObject) args[3]), login.getPk_group()));
                } else {
                    throw new BusinessException("无法核对采购工作流批次参数。");
                }
                requireUncontrolled(policies(), orgs, node, button, BATCH_MESSAGE);
                return original.run();
            }
            if (!(args[3] instanceof AbstractBill) || args[2] != null && !(args[2] instanceof WorkflownoteVO)
                    || args[5] != null && !(args[5] instanceof HashMap)) {
                throw new BusinessException("采购工作流缺少原生单据或审批参数。");
            }
            AbstractBill bill = (AbstractBill) args[3];
            OperationContext draft = ProcurementWorkflowCommand.draft(node, button, login.getPk_group(), fullBill(bill));
            ProcurementWorkflowCommand command = new ProcurementWorkflowCommand(action, (String) args[1],
                    (WorkflownoteVO) args[2], bill, args[4], (HashMap<?, ?>) args[5]);
            Container parent = owner.getFlowContext().getParent();
            dispatching = true;
            try {
                return dispatch(draft, parent == null ? login.getEntranceUI() : parent, event, command, original);
            } catch (BusinessException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw WorkflowPfLocator.failure(ex);
            } finally {
                dispatching = false;
            }
        }

        private void addOrganizations(Set<String> orgs, AggregatedValueObject[] bills) throws BusinessException {
            for (AggregatedValueObject bill : bills) orgs.add(organization(fullBill(bill), login.getPk_group()));
        }

        private AggregatedValueObject fullBill(AggregatedValueObject bill) throws BusinessException {
            ISuperVO head = header(bill);
            AggregatedValueObject[] selected = workerSelection == null ? owner.getFlowContext().getBillVos() : workerSelection;
            String pk = head.getPrimaryKey();
            AggregatedValueObject matching = findBill(selected, bill, pk);
            if (matching == null) {
                throw new BusinessException("采购工作流单据不属于本次动作范围。");
            }
            AggregatedValueObject original = findBill(owner.getFullOldVOs(), bill, pk);
            if (original == null) original = matching;
            ISuperVO originalHead = header(original);
            for (String ownerField : new String[] {"pk_group", "pk_org"}) {
                Object actual = head.getAttributeValue(ownerField);
                if (actual != null && !actual.equals(originalHead.getAttributeValue(ownerField))) {
                    throw new BusinessException("采购工作流单据所属集团或业务单元已变化。");
                }
            }
            return original;
        }
    }

    private static AggregatedValueObject findBill(AggregatedValueObject[] selection, AggregatedValueObject bill,
            String pk) throws BusinessException {
        if (selection != null) for (AggregatedValueObject candidate : selection) {
            if (candidate == bill || candidate != null && candidate.getClass() == bill.getClass()
                    && !blank(pk) && pk.equals(header(candidate).getPrimaryKey())) return candidate;
        }
        return null;
    }

    static boolean matchesPfAction(String expected, String action, String userId) {
        return expected.equals(action) || ("APPROVE".equals(expected) || "UNAPPROVE".equals(expected))
                && userId != null && userId.length() == 20 && (expected + userId).equals(action);
    }

    private static ISuperVO header(AggregatedValueObject bill) throws BusinessException {
        ISuperVO head = bill instanceof AbstractBill ? ((AbstractBill) bill).getParent() : null;
        if (head == null) throw new BusinessException("采购工作流缺少表头。");
        return head;
    }

    private static String organization(AggregatedValueObject bill, String loginGroup) throws BusinessException {
        ISuperVO head = header(bill);
        Object org = head.getAttributeValue("pk_org");
        if (!(org instanceof String) || blank((String) org) || blank(loginGroup)
                || !loginGroup.equals(head.getAttributeValue("pk_group"))) {
            throw new BusinessException("无法核对采购工作流业务单元。");
        }
        return (String) org;
    }

    private static IPolicyService policies() {
        return NCLocator.getInstance().lookup(IPolicyService.class);
    }

    static void requireUncontrolled(IPolicyService policies, Set<String> orgs, String node, String button,
            String message) throws BusinessException {
        if (orgs.isEmpty()) throw new BusinessException("采购工作流没有可核对的业务单元。");
        for (String org : orgs) {
            PolicyDecision decision = policies.resolve(org, node, button);
            if (decision == null || decision.isControlled()) throw new BusinessException(message);
        }
    }

    private static Object dispatch(final OperationContext draft, final Container parent, final ActionEvent click,
            ProcurementWorkflowCommand command, WorkflowPfLocator.Call original) throws Exception {
        final Grant grant = onEdt(new Callable<Grant>() {
            @Override public Grant call() throws BusinessException {
                Grant value = new Grant(draft, parent, click);
                if (!value.lifecycle.beforeDoAction(value.action, value.event)) {
                    throw new BusinessException("已取消采购工作流签章。");
                }
                return value;
            }
        });
        Throwable failure = null;
        try {
            if (grant.bound == null) return original.run();
            IOperationService service = NCLocator.getInstance().lookup(IOperationService.class);
            grant.dispatched = true;
            OperationResponse response = service.execute(grant.bound.getRequestId(), command);
            if (response == null || response.isReplayed() || !grant.bound.getRequestId().equals(response.getRequestId())) {
                throw new BusinessException("无法确定采购工作流执行结果，请刷新单据核对。");
            }
            return response.getBusinessResult();
        } catch (Exception ex) {
            failure = ex;
            throw ex;
        } catch (Error ex) {
            failure = ex;
            throw ex;
        } finally {
            grant.finish(failure);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static <T> T onEdt(Callable<T> callback) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return callback.call();
        FutureTask<T> task = new FutureTask<T>(callback);
        SwingUtilities.invokeAndWait(task);
        try {
            return task.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw (Error) cause;
        }
    }

    private static final class Grant implements OperationContextProvider, AuthorizedRequestConsumer {
        private final OperationContext draft;
        private final Container parent;
        private final NCAction action;
        private final ActionEvent event;
        private final SignatureLifecycleInterceptor lifecycle;
        private OperationContext bound;
        private boolean dispatched;

        Grant(OperationContext draft, Container parent, ActionEvent click) {
            this.draft = draft;
            this.parent = parent;
            action = new NCAction() {
                @Override public void doAction(ActionEvent ignored) { }
            };
            event = new ActionEvent(action, ActionEvent.ACTION_PERFORMED, draft.getButtonCode(),
                    click == null ? 0L : click.getWhen(), click == null ? 0 : click.getModifiers());
            ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor();
            signing.setContextProvider(this);
            signing.setRequestConsumer(this);
            action.setInterceptor(signing);
            lifecycle = SignatureLifecycleInstaller.install(action, signing);
        }

        void finish(final Throwable failure) throws Exception {
            try {
                onEdt(new Callable<Void>() {
                    @Override public Void call() {
                        if (failure == null) lifecycle.afterDoActionSuccessed(action, event);
                        else lifecycle.afterDoActionFailed(action, event, failure);
                        return null;
                    }
                });
            } catch (Exception ex) {
                if (failure == null) throw ex;
                failure.addSuppressed(ex);
            }
        }

        @Override public OperationContext createContext(Action source, ActionEvent click) { return draft; }
        @Override public Container getParentContainer() { return parent; }
        @Override public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }

        @Override public boolean isAvailable(OperationContext value) {
            return value != null && draft.getNodeCode().equals(value.getNodeCode())
                    && draft.getButtonCode().equals(value.getButtonCode())
                    && draft.getOperationType().equals(value.getOperationType())
                    && draft.getGroupId().equals(value.getGroupId()) && draft.getOrgId().equals(value.getOrgId())
                    && Objects.equals(draft.getObjects(), value.getObjects());
        }

        @Override public boolean bind(OperationContext value) {
            if (!isAvailable(value) || bound != null || blank(value.getRequestId())) return false;
            bound = value;
            return true;
        }

        @Override public boolean wasDispatched(String requestId) {
            return bound != null && bound.getRequestId().equals(requestId) && dispatched;
        }

        @Override public void release(String requestId) {
            if (bound != null && bound.getRequestId().equals(requestId)) bound = null;
        }
    }
}
