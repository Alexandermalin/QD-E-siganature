package nc.ui.pp.supplierprice.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.io.Serializable;
import java.util.Objects;
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
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uif2.LoginContext;

final class SupplierPriceActionScope implements AutoCloseable {
    private static final ThreadLocal<SupplierPriceActionScope> CURRENT = new ThreadLocal<SupplierPriceActionScope>();
    private static final int MAX_SIGNED_OBJECTS = 1000;
    final LoginContext login;
    final String button;
    final boolean editorOnly;

    private SupplierPriceActionScope(LoginContext login, String button, boolean editorOnly) {
        this.login = login;
        this.button = button;
        this.editorOnly = editorOnly;
    }

    static SupplierPriceActionScope open(LoginContext login, String button, boolean editorOnly) throws BusinessException {
        if (CURRENT.get() != null) throw new BusinessException("供应商价目表操作正在处理。");
        SupplierPriceActionScope scope = new SupplierPriceActionScope(login, button, editorOnly);
        CURRENT.set(scope);
        return scope;
    }

    static SupplierPriceActionScope forService(LoginContext login) throws BusinessException {
        SupplierPriceActionScope scope = CURRENT.get();
        if (scope != null && scope.login != login) {
            throw new BusinessException("供应商价目批次与当前按钮上下文不一致。");
        }
        return scope;
    }

    static boolean isTarget(LoginContext login) throws BusinessException {
        if (login == null || login.getNodeCode() == null || login.getNodeCode().trim().isEmpty()) {
            throw new BusinessException("无法确定供应商价目表的功能节点。");
        }
        return PriceBatchRequest.NODE.equals(login.getNodeCode());
    }

    @Override public void close() { CURRENT.remove(); }

    Grant authorize(final OperationContext draft) throws Exception {
        requireLoginGroup(draft);
        return onEdt(new Callable<Grant>() {
            @Override public Grant call() throws BusinessException {
                Grant grant = new Grant(draft, login.getEntranceUI());
                if (!grant.lifecycle.beforeDoAction(grant.action, grant.event)) {
                    throw new BusinessException("已取消供应商价目表签章。");
                }
                return grant;
            }
        });
    }

    boolean nativeLargeBatch(OperationContext draft) throws BusinessException {
        if (draft.getObjects().size() <= MAX_SIGNED_OBJECTS) return false;
        requireLoginGroup(draft);
        return nativeLargeBatch(draft, NCLocator.getInstance().lookup(IPolicyService.class));
    }

    static boolean nativeLargeBatch(OperationContext draft, IPolicyService policy) throws BusinessException {
        if (draft.getObjects().size() <= MAX_SIGNED_OBJECTS) return false;
        PolicyDecision decision = policy.resolve(draft.getOrgId(), draft.getNodeCode(), draft.getButtonCode());
        if (decision == null || decision.isControlled()) {
            throw new BusinessException("受控供应商价目批次最多包含 1000 行，请分批操作。");
        }
        return true;
    }

    private void requireLoginGroup(OperationContext draft) throws BusinessException {
        if (!Objects.equals(login.getPk_group(), draft.getGroupId())) {
            throw new BusinessException("供应商价目批次所属集团与当前登录集团不一致。");
        }
    }

    private static <T> T onEdt(Callable<T> callback) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return callback.call();
        FutureTask<T> work = new FutureTask<T>(callback);
        SwingUtilities.invokeAndWait(work);
        try {
            return work.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw (Error) cause;
        }
    }

    static final class Grant implements AuthorizedRequestConsumer, OperationContextProvider {
        private final OperationContext draft;
        private final Container parent;
        private final NCAction action;
        private final ActionEvent event;
        private final SignatureLifecycleInterceptor lifecycle;
        private OperationContext bound;
        private boolean dispatched;

        Grant(OperationContext draft, Container parent) {
            this.draft = draft;
            this.parent = parent;
            this.action = new NCAction() {
                @Override public void doAction(ActionEvent ignored) { }
            };
            ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor();
            signing.setContextProvider(this);
            signing.setRequestConsumer(this);
            action.setInterceptor(signing);
            lifecycle = SignatureLifecycleInstaller.install(action, signing);
            event = new ActionEvent(action, ActionEvent.ACTION_PERFORMED, draft.getButtonCode());
        }

        boolean isControlled() { return bound != null; }

        OperationResponse execute(Serializable command) throws BusinessException {
            if (bound == null || dispatched) throw new BusinessException("价目批次缺少可用授权或已派发。");
            dispatched = true;
            OperationResponse result = NCLocator.getInstance().lookup(IOperationService.class)
                    .execute(bound.getRequestId(), command);
            if (result == null || result.isReplayed() || !bound.getRequestId().equals(result.getRequestId())) {
                throw new BusinessException("无法确定价目批次结果，请刷新后核对。");
            }
            return result;
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

        @Override public boolean isAvailable(OperationContext context) {
            return context != null && PriceBatchRequest.NODE.equals(context.getNodeCode());
        }

        @Override public boolean bind(OperationContext context) {
            if (!isAvailable(context) || bound != null || context.getRequestId() == null) return false;
            bound = context;
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
