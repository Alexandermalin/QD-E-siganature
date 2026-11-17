package nc.ui.pu.qd.esign.relation;

import java.awt.Container;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IInvoiceRelationPendingService;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.uapbd.qd.esign.IPolicyService;
import nc.pu.m25.relevance.vo.RelevanceHVO;
import nc.ui.pu.m25.model.InvoiceBillManageModel;
import nc.ui.pu.qd.esign.ProcurementAuthorizationScope;
import nc.ui.uapbd.qd.esign.AuthorizedRequestConsumer;
import nc.ui.uapbd.qd.esign.ElectronicSignatureActionInterceptor;
import nc.ui.uapbd.qd.esign.OperationContextProvider;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInstaller;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInterceptor;
import nc.ui.uif2.NCAction;
import nc.vo.pu.m25.entity.InvoiceHeaderVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pu.qd.esign.InvoiceRelationCommand;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uif2.LoginContext;

public final class InvoiceRelationBridge {
    private InvoiceRelationBridge() { }

    public static boolean isControlled(InvoiceBillManageModel model, InvoiceVO invoice, String button)
            throws BusinessException {
        InvoiceRelationCommand.operation(button);
        LoginContext login = context(model);
        if (!InvoiceRelationCommand.NODE.equals(login.getNodeCode())) return false;
        InvoiceHeaderVO header = header(login, invoice);
        NCLocator locator = NCLocator.getInstance();
        locator.lookup(IInvoiceRelationPendingService.class).assertClear(header.getPk_invoice());
        PolicyDecision policy = locator.lookup(IPolicyService.class).resolve(header.getPk_org(), login.getNodeCode(), button);
        if (policy == null) throw new BusinessException("无法核对采购发票关联策略。");
        return policy.isControlled();
    }

    public static boolean execute(InvoiceBillManageModel model, ActionEvent event, String button, InvoiceVO invoice,
            RelevanceHVO[] selected) throws BusinessException {
        LoginContext login = context(model);
        if (!InvoiceRelationCommand.NODE.equals(login.getNodeCode())) {
            throw new BusinessException("当前页面不支持采购发票关联授权。");
        }
        InvoiceRelationCommand command = InvoiceRelationCommand.from(button, invoice, selected);
        OperationContext context = command.draft(login.getPk_group(), header(login, invoice));
        Grant grant = new Grant(context, login.getEntranceUI(), event);
        if (!grant.lifecycle.beforeDoAction(grant.action, grant.event)) return false;

        Throwable failure = null;
        try {
            if (grant.bound == null) throw new BusinessException("采购发票关联策略已变化，请刷新后重新确认。");
            IOperationService operations = NCLocator.getInstance().lookup(IOperationService.class);
            grant.dispatched = true;
            OperationResponse result = operations.execute(grant.bound.getRequestId(), command);
            if (result == null || result.isReplayed() || !grant.bound.getRequestId().equals(result.getRequestId())
                    || !command.getInvoicePk().equals(result.getBusinessResult())) {
                throw new BusinessException("无法确认采购发票关联执行结果，请按请求号核对。请求号 "
                        + grant.bound.getRequestId() + "。");
            }
            return true;
        } catch (BusinessException | RuntimeException ex) {
            if (grant.dispatched) {
                String requestId = grant.bound.getRequestId();
                String message = ex.getMessage();
                if (message == null || !message.contains(requestId)) {
                    BusinessException reported = new BusinessException("采购发票关联执行结果请按请求号核对。请求号 "
                            + requestId + "。" + (message == null ? "" : message));
                    reported.initCause(ex);
                    failure = reported;
                    throw reported;
                }
            }
            failure = ex;
            throw ex;
        } catch (Error ex) {
            failure = ex;
            throw ex;
        } finally {
            grant.finish(failure);
        }
    }

    private static LoginContext context(InvoiceBillManageModel model) throws BusinessException {
        if (model == null || model.getContext() == null || model.getContext().getNodeCode() == null) {
            throw new BusinessException("无法确定采购发票功能节点。");
        }
        return model.getContext();
    }

    private static InvoiceHeaderVO header(LoginContext login, InvoiceVO invoice) throws BusinessException {
        InvoiceHeaderVO header = invoice == null ? null : invoice.getParentVO();
        if (header == null || !InvoiceRelationCommand.safeKey(header.getPk_invoice(), 20)
                || login.getPk_group() == null || !login.getPk_group().equals(header.getPk_group())
                || header.getPk_org() == null || header.getPk_org().trim().isEmpty()) {
            throw new BusinessException("无法核对采购发票所属集团或业务单元。");
        }
        return header;
    }

    private static final class Grant implements OperationContextProvider, AuthorizedRequestConsumer {
        final OperationContext draft;
        final Container parent;
        final NCAction action;
        final ActionEvent event;
        final SignatureLifecycleInterceptor lifecycle;
        OperationContext bound;
        boolean dispatched;

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

        void finish(Throwable failure) {
            try {
                if (failure == null) lifecycle.afterDoActionSuccessed(action, event);
                else lifecycle.afterDoActionFailed(action, event, failure);
            } catch (RuntimeException ex) {
                if (failure == null) throw ex;
                failure.addSuppressed(ex);
            }
        }

        @Override public OperationContext createContext(Action source, ActionEvent click) { return draft; }
        @Override public Container getParentContainer() { return parent; }
        @Override public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }

        @Override public boolean isAvailable(OperationContext value) {
            return ProcurementAuthorizationScope.matches(draft, value);
        }

        @Override public boolean bind(OperationContext value) {
            if (!isAvailable(value) || bound != null || value.getRequestId() == null
                    || value.getRequestId().trim().isEmpty()) return false;
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
