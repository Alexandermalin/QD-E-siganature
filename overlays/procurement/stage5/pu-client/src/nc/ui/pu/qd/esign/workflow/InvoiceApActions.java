package nc.ui.pu.qd.esign.workflow;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.uapbd.qd.esign.IPolicyService;
import nc.ui.pu.m25.action.InvoiceCancelSendApAction;
import nc.ui.pu.m25.action.InvoiceSendApAction;
import nc.ui.pu.qd.esign.ProcurementAuthorizationScope;
import nc.ui.pubapp.uif2app.actions.pflow.MultiBillScriptRunner;
import nc.ui.pubapp.uif2app.actions.pflow.ScriptPFlowAction;
import nc.ui.uapbd.qd.esign.AuthorizedRequestConsumer;
import nc.ui.uapbd.qd.esign.ElectronicSignatureActionInterceptor;
import nc.ui.uapbd.qd.esign.OperationContextProvider;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInstaller;
import nc.ui.uapbd.qd.esign.SignatureLifecycleInterceptor;
import nc.ui.uif2.NCAction;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pu.qd.esign.InvoiceApCommand;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uif2.LoginContext;

public final class InvoiceApActions {
    private static final String BATCH_MESSAGE = "启用签章或审计时，采购发票应付动作请按单笔操作。";

    private InvoiceApActions() { }

    public static final class Send extends InvoiceSendApAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(ActionEvent event) throws Exception {
            try (WorkflowPfLocator.Scope scope = open(this, InvoiceApCommand.SEND_BUTTON, event, false)) {
                super.doAction(event);
            }
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) multibillScriptRunner = new Runner(this, InvoiceApCommand.SEND_BUTTON);
            return multibillScriptRunner;
        }
    }

    public static final class Cancel extends InvoiceCancelSendApAction {
        private static final long serialVersionUID = 1L;

        @Override public void doAction(ActionEvent event) throws Exception {
            try (WorkflowPfLocator.Scope scope = open(this, InvoiceApCommand.CANCEL_BUTTON, event, false)) {
                super.doAction(event);
            }
        }

        @Override public MultiBillScriptRunner getMultibillScriptRunner() {
            if (multibillScriptRunner == null) multibillScriptRunner = new Runner(this, InvoiceApCommand.CANCEL_BUTTON);
            return multibillScriptRunner;
        }
    }

    private static WorkflowPfLocator.Scope open(ScriptPFlowAction owner, String button, ActionEvent click,
            boolean batchWorker) throws BusinessException {
        LoginContext login = owner.getModel() == null ? null : owner.getModel().getContext();
        if (login == null || !InvoiceApCommand.validKey(login.getNodeCode())) {
            throw new BusinessException("无法确定采购发票应付动作的功能节点。");
        }
        return InvoiceApCommand.NODE.equals(login.getNodeCode())
                ? WorkflowPfLocator.open(new Dispatch(owner, button, click, login, batchWorker)) : null;
    }

    private static final class Runner extends MultiBillScriptRunner {
        private final ScriptPFlowAction owner;
        private final String button;

        Runner(ScriptPFlowAction owner, String button) {
            super(owner.getFlowContext());
            this.owner = owner;
            this.button = button;
            setScriptAction(owner);
            setProcessedBillNumOnce(owner.getProcessedBillNumOnce());
        }

        @Override protected List<Object> taskRun(List<String> detail, List<Object> oldSuccessful) {
            try (WorkflowPfLocator.Scope scope = open(owner, button, null, true)) {
                return super.taskRun(detail, oldSuccessful);
            } catch (BusinessException failure) {
                detail.add(failure.getMessage());
                return Collections.emptyList();
            }
        }
    }

    private static final class Dispatch implements WorkflowPfLocator.Dispatch {
        private final ScriptPFlowAction owner;
        private final String button;
        private final ActionEvent click;
        private final LoginContext login;
        private final AggregatedValueObject[] batch;
        private boolean dispatched;

        Dispatch(ScriptPFlowAction owner, String button, ActionEvent click, LoginContext login, boolean worker)
                throws BusinessException {
            this.owner = owner;
            this.button = button;
            this.click = click;
            this.login = login;
            AggregatedValueObject[] selected = worker ? owner.getFlowContext().getBillVos() : null;
            if (worker && (selected == null || selected.length == 0)) {
                throw new BusinessException("无法核对采购发票应付批次。");
            }
            batch = selected == null ? null : selected.clone();
        }

        @Override public Object execute(Method method, Object[] args, WorkflowPfLocator.Call nativeCall)
                throws BusinessException {
            String action = InvoiceApCommand.actionFor(button);
            if (args.length != 6 || !action.equals(args[0]) || !(args[1] instanceof String)) {
                throw new BusinessException("采购发票应付动作与原生 PF 调用不匹配。");
            }
            boolean nativeBatch = "processBatch".equals(method.getName());
            AggregatedValueObject[] selected = batch == null ? owner.getFlowContext().getBillVos() : batch;
            if (batch != null || nativeBatch) {
                Set<String> orgs = new LinkedHashSet<String>();
                if (selected != null) for (AggregatedValueObject bill : selected) orgs.add(draft(bill, selected).getOrgId());
                if (nativeBatch && args[3] instanceof AggregatedValueObject[]) {
                    for (AggregatedValueObject bill : (AggregatedValueObject[]) args[3]) orgs.add(draft(bill, selected).getOrgId());
                } else if (!nativeBatch && args[3] instanceof AggregatedValueObject) {
                    orgs.add(draft((AggregatedValueObject) args[3], selected).getOrgId());
                } else {
                    throw new BusinessException("无法核对采购发票应付批次参数。");
                }
                WorkflowActionBridge.requireUncontrolled(NCLocator.getInstance().lookup(IPolicyService.class),
                        orgs, InvoiceApCommand.NODE, button, BATCH_MESSAGE);
                return nativeCall.run();
            }
            if (!(args[3] instanceof InvoiceVO) || selected == null || selected.length != 1 || dispatched) {
                throw new BusinessException("无法核对采购发票应付单笔操作，请刷新后重试。");
            }
            InvoiceVO invoice = (InvoiceVO) args[3];
            OperationContext draft = draft(invoice, selected);
            Container parent = owner.getFlowContext().getParent();
            Grant grant = new Grant(draft, parent == null ? login.getEntranceUI() : parent, click);
            if (!grant.lifecycle.beforeDoAction(grant.action, grant.event)) {
                throw new BusinessException("已取消采购发票应付签章。");
            }
            Throwable failure = null;
            try {
                if (grant.bound == null) return nativeCall.run();
                InvoiceApCommand command = command(action, (String) args[1], invoice, args[2], args[5]);
                IOperationService service = NCLocator.getInstance().lookup(IOperationService.class);
                String request = grant.bound.getRequestId();
                dispatched = true;
                grant.dispatched = true;
                OperationResponse response = service.execute(request, command);
                if (response == null || response.isReplayed() || !request.equals(response.getRequestId())) {
                    throw new BusinessException("无法确认采购发票应付执行结果，请刷新单据并核对请求 " + request + "。");
                }
                return response.getBusinessResult();
            } catch (BusinessException ex) {
                failure = ex;
                throw ex;
            } catch (RuntimeException ex) {
                failure = ex;
                throw ex;
            } catch (Error ex) {
                failure = ex;
                throw ex;
            } finally {
                grant.finish(failure);
            }
        }

        private OperationContext draft(AggregatedValueObject raw, AggregatedValueObject[] selected)
                throws BusinessException {
            InvoiceVO bill = raw instanceof InvoiceVO ? (InvoiceVO) raw : null;
            String pk = InvoiceApCommand.header(bill).getPk_invoice();
            InvoiceVO matching = find(selected, pk);
            if (matching == null) throw new BusinessException("应付操作不属于本次选中的采购发票。");
            InvoiceVO full = find(owner.getFullOldVOs(), pk);
            return InvoiceApCommand.draft(button, login.getPk_group(), full == null ? matching : full);
        }

        private static InvoiceVO find(AggregatedValueObject[] bills, String pk) throws BusinessException {
            if (bills != null) for (AggregatedValueObject bill : bills) {
                if (bill instanceof InvoiceVO && pk.equals(InvoiceApCommand.header((InvoiceVO) bill).getPk_invoice())) {
                    return (InvoiceVO) bill;
                }
            }
            return null;
        }
    }

    static InvoiceApCommand command(String action, String type, InvoiceVO bill, Object note, Object raw)
            throws BusinessException {
        if (note != null || raw != null && !(raw instanceof Map)) throw unsupportedParameters();
        boolean reload = false;
        if (raw != null) for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if ("notechecked".equals(key) && key.equals(value) || "worknote".equals(key) && value == null) continue;
            if ("reload_vo".equals(key) && value instanceof InvoiceVO
                    && InvoiceApCommand.header(bill).getPk_invoice()
                            .equals(InvoiceApCommand.header((InvoiceVO) value).getPk_invoice())) {
                reload = true;
                continue;
            }
            throw unsupportedParameters();
        }
        return new InvoiceApCommand(action, type, bill, reload);
    }

    private static BusinessException unsupportedParameters() {
        return new BusinessException("采购发票应付流程平台参数超出授权范围。");
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
            if (!isAvailable(value) || bound != null || !InvoiceApCommand.validKey(value.getRequestId())) return false;
            bound = value;
            return true;
        }

        @Override public boolean wasDispatched(String id) {
            return bound != null && bound.getRequestId().equals(id) && dispatched;
        }

        @Override public void release(String id) {
            if (bound != null && bound.getRequestId().equals(id)) bound = null;
        }
    }
}
