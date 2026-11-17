package nc.ui.pu.qd.esign.status;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import nc.ui.uapbd.qd.esign.AuthorizedRequestConsumer;
import nc.ui.uapbd.qd.esign.ElectronicSignatureActionInterceptor;
import nc.ui.uapbd.qd.esign.OperationContextProvider;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInstaller;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInterceptor;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.model.AbstractAppModel;
import nc.ui.uif2.model.IMultiRowSelectModel;
import nc.vo.pu.qd.esign.ProcurementStatusCommand;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uif2.LoginContext;

public final class StatusActionBridge {
    private static final int MAX_SIGNED_OBJECTS = 1000;

    public interface NativeAction { void run() throws Exception; }
    public interface NativeCall { Object run() throws Exception; }

    private StatusActionBridge() { }

    public static void perform(final AbstractAppModel model, final String button, final ActionEvent event,
            NativeAction original) throws Exception {
        String node = node(model);
        if (!target(node)) {
            original.run();
            return;
        }
        final String service = ProcurementStatusCommand.serviceFor(node, button);
        final String method = ProcurementStatusCommand.methodFor(node, button);
        try (StatusServiceLocator.Scope scope = StatusServiceLocator.open(service, method, new StatusServiceLocator.Dispatch() {
            private boolean dispatched;

            @Override public Object execute(Method nativeMethod, Object[] args, final StatusServiceLocator.Call nativeCall)
                    throws Exception {
                if (dispatched) throw new BusinessException("同一采购状态动作已派发。");
                dispatched = true;
                Object value = dispatch(model, button, event, service, method, args, new NativeCall() {
                    @Override public Object run() throws Exception { return nativeCall.run(); }
                });
                if (value != null && !nativeMethod.getReturnType().isInstance(value)) {
                    throw new BusinessException("采购状态操作返回类型与原生调用不符，请刷新核对。");
                }
                return value;
            }
        })) {
            original.run();
        }
    }

    public static Object dispatch(AbstractAppModel model, String button, ActionEvent event, String service,
            String method, Object[] args, NativeCall original) throws Exception {
        String node = node(model);
        if (!target(node)) return original.run();
        LoginContext login = model.getContext();
        ProcurementStatusCommand command = new ProcurementStatusCommand(service, method, args);
        List<OperationContext> drafts = command.drafts(node, button, login.getPk_group(), selection(model));
        if (drafts.size() > 1) {
            requireUncontrolledOrganizations(drafts, NCLocator.getInstance().lookup(IPolicyService.class));
            return original.run();
        }
        OperationContext draft = drafts.get(0);
        if (draft.getObjects().size() > MAX_SIGNED_OBJECTS) {
            requireUncontrolled(draft.getOrgId(), node, button, "受控采购状态操作每批最多 1000 张单据，请分批选择。");
            return original.run();
        }
        Grant grant = authorize(draft, login.getEntranceUI(), event);
        Throwable failure = null;
        try {
            if (grant.bound == null) return original.run();
            grant.dispatched = true;
            OperationResponse response = NCLocator.getInstance().lookup(IOperationService.class)
                    .execute(grant.bound.getRequestId(), command);
            if (response == null || response.isReplayed() || !grant.bound.getRequestId().equals(response.getRequestId())) {
                throw new BusinessException("无法确定采购状态操作结果，请刷新单据后核对。");
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

    /** Native order freeze/unfreeze uses independent per-bill workers for a multi-selection. */
    public static void requireNativeBatch(AbstractAppModel model, String button) throws BusinessException {
        if (!"40040400".equals(node(model))) return;
        Object[] bills = model instanceof IMultiRowSelectModel
                ? ((IMultiRowSelectModel) model).getSelectedOperaDatas() : null;
        if (bills == null || bills.length <= 1) return;
        Set<String> verified = new LinkedHashSet<String>();
        for (Object value : bills) {
            if (!(value instanceof AggregatedValueObject)) throw new BusinessException("采购订单选择范围无效。");
            String org = owner(model.getContext(), head((AggregatedValueObject) value));
            if (verified.add(org)) checkNativeOrderBatch(org, button);
        }
    }

    public static void requireNativeBatchBill(AbstractAppModel model, String button, AggregatedValueObject bill)
            throws BusinessException {
        if (!"40040400".equals(node(model))) return;
        SuperVO header = head(bill);
        if (header == null || blank(header.getPrimaryKey())) throw new BusinessException("批量采购订单缺少单据身份。");
        if (header.getAttributeValue("pk_group") == null || header.getAttributeValue("pk_org") == null) {
            for (AggregatedValueObject original : selection(model)) {
                SuperVO candidate = head(original);
                if (candidate != null && header.getPrimaryKey().equals(candidate.getPrimaryKey())) {
                    header = candidate;
                    break;
                }
            }
        }
        checkNativeOrderBatch(owner(model.getContext(), header), button);
    }

    private static void checkNativeOrderBatch(String org, String button) throws BusinessException {
        requireUncontrolled(org, "40040400", button, "启用签章或审计时，采购订单冻结和解冻试点仅支持单笔。");
    }

    private static void requireUncontrolled(String org, String node, String button, String message)
            throws BusinessException {
        requireUncontrolled(NCLocator.getInstance().lookup(IPolicyService.class), org, node, button, message);
    }

    static void requireUncontrolledOrganizations(List<OperationContext> drafts, IPolicyService policies)
            throws BusinessException {
        for (OperationContext draft : drafts) {
            requireUncontrolled(policies, draft.getOrgId(), draft.getNodeCode(), draft.getButtonCode(),
                    "启用签章或审计时，请按同一业务单元选择采购单据。");
        }
    }

    private static void requireUncontrolled(IPolicyService policies, String org, String node, String button,
            String message) throws BusinessException {
        PolicyDecision policy = policies.resolve(org, node, button);
        if (policy == null || policy.isControlled()) throw new BusinessException(message);
    }

    private static AggregatedValueObject[] selection(AbstractAppModel model) {
        Map<String, AggregatedValueObject> bills = new LinkedHashMap<String, AggregatedValueObject>();
        if (model instanceof IMultiRowSelectModel) {
            Object[] selected = ((IMultiRowSelectModel) model).getSelectedOperaDatas();
            if (selected != null) for (Object bill : selected) putSelected(bills, bill);
        }
        putSelected(bills, model.getSelectedData());
        return bills.values().toArray(new AggregatedValueObject[bills.size()]);
    }

    private static void putSelected(Map<String, AggregatedValueObject> bills, Object value) {
        if (!(value instanceof AggregatedValueObject)) return;
        AggregatedValueObject bill = (AggregatedValueObject) value;
        SuperVO head = head(bill);
        if (head != null && !blank(head.getPrimaryKey())) bills.put(head.getPrimaryKey(), bill);
    }

    private static SuperVO head(AggregatedValueObject bill) {
        return bill != null && bill.getParentVO() instanceof SuperVO ? (SuperVO) bill.getParentVO() : null;
    }

    private static String owner(LoginContext login, SuperVO head) throws BusinessException {
        Object org = head == null ? null : head.getAttributeValue("pk_org");
        Object group = head == null ? null : head.getAttributeValue("pk_group");
        if (!(org instanceof String) || blank((String) org) || blank(login.getPk_group())
                || !Objects.equals(login.getPk_group(), group)) {
            throw new BusinessException("无法核实采购订单所属集团和业务单元。");
        }
        return (String) org;
    }

    private static String node(AbstractAppModel model) throws BusinessException {
        String code = model == null || model.getContext() == null ? null : model.getContext().getNodeCode();
        if (blank(code)) throw new BusinessException("无法确定采购状态操作的功能节点。");
        return code;
    }

    private static boolean target(String node) {
        return "40040000".equals(node) || "40040200".equals(node) || "40040400".equals(node) || "40041000".equals(node);
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private static Grant authorize(final OperationContext context, final Container parent, final ActionEvent click)
            throws Exception {
        return onEdt(new Callable<Grant>() {
            @Override public Grant call() throws BusinessException {
                Grant grant = new Grant(context, parent, click);
                if (!grant.lifecycle.beforeDoAction(grant.action, grant.event)) {
                    throw new BusinessException("已取消采购状态操作签章。");
                }
                return grant;
            }
        });
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
                    && draft.getGroupId().equals(value.getGroupId()) && draft.getOrgId().equals(value.getOrgId());
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
