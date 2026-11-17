package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.ui.ic.pub.model.ICBizEditorModel;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.AppUiState;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.ic.pub.pf.ICPFParameter;
import nc.vo.ic.qd.esign.InventoryPfSaveCommand;
import nc.vo.ic.qd.esign.InventoryPfSaveResult;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pattern.exception.ExceptionUtils;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SignaturePreparation;

/** Shares the authorization and PF handoff while each node retains its native action subclass. */
final class InventorySaveCoordinator implements OperationContextProvider, AuthorizedRequestConsumer {
    interface Host {
        AbstractScriptExcAction action();
        BillManageModel model();
        ICBizEditorModel editorModel();
        void performNative(ActionEvent event);
        void fillNativeContext(PFlowContext flow);
        boolean isComposite();
        boolean resumeNative(IResumeException error);
        void setNativeExecuted(boolean executed);
    }

    private final Host host;
    private final String node, nodeName, objectType;
    private final Class<? extends ICBillVO> billClass;
    private final InventorySaveHandoff handoff = new InventorySaveHandoff();
    private ActionEvent activeEvent;
    private SignatureLifecycleInterceptor lifecycle;
    private OperationContext draft;

    InventorySaveCoordinator(Host host, String node, String nodeName, String objectType,
            Class<? extends ICBillVO> billClass) {
        this.host = host;
        this.node = node;
        this.nodeName = nodeName;
        this.objectType = objectType;
        this.billClass = billClass;
    }

    void perform(ActionEvent event) {
        if (lifecycle != null) {
            if (isClosingSave(event)) throw saveNotFinished();
            return;
        }
        if (!isInventoryEdit()) { host.performNative(event); return; }
        AbstractScriptExcAction action = host.action();
        ActionInterceptor original = action.getInterceptor();
        ElectronicSignatureActionInterceptor signing = new ElectronicSignatureActionInterceptor();
        signing.setContextProvider(this);
        signing.setRequestConsumer(this);
        CompositeActionInterceptor chain = new CompositeActionInterceptor();
        chain.addInterceptor(signing);
        if (original != null) chain.addInterceptor(original);
        activeEvent = event;
        Throwable failure = null;
        try {
            action.setInterceptor(chain);
            lifecycle = SignatureLifecycleInstaller.install(action, signing);
            host.performNative(event);
        } catch (RuntimeException error) { failure = error; throw error; }
          catch (Error error) { failure = error; throw error; }
        finally {
            try { signing.finishLifecycle(signing.lifecycleGeneration(), action, event, failure); }
            finally {
                if (action.getInterceptor() == lifecycle || action.getInterceptor() == chain) action.setInterceptor(original);
                lifecycle = null;
                activeEvent = null;
                draft = null;
                handoff.reset();
            }
        }
        if (isClosingSave(event) && isInventoryEdit()) throw saveNotFinished();
    }

    void guardDoAction(ActionEvent event) throws BusinessException {
        if ((isInventoryEdit() || lifecycle != null) && (lifecycle == null || event != activeEvent
                || host.action().getInterceptor() != lifecycle || !isInventoryEdit() || draft == null)) {
            throw new BusinessException(nodeName + "保存状态已变化，请重新发起保存。");
        }
    }

    boolean takesOverFlow() throws BusinessException {
        if (handoff.context() != null) return true;
        if ((isInventoryEdit() && lifecycle == null) || (lifecycle != null && draft == null)) {
            throw new BusinessException(nodeName + "保存缺少策略核对结果。");
        }
        return false;
    }

    void processFlow(PFlowContext flow) throws Exception {
        OperationContext authorized = handoff.context();
        AbstractScriptExcAction action = host.action();
        if (authorized == null || flow != action.getFlowContext() || !isInventoryEdit() || lifecycle == null
                || action.getInterceptor() != lifecycle
                || (host.isComposite() && (!empty(action.getPreActionNames()) || !empty(action.getAfterActionNames())))
                || !"WRITE".equals(flow.getActionName()) || text(flow.getUserId()) != null) {
            throw new BusinessException(nodeName + "保存流程与当前授权不匹配。");
        }
        InventoryRetryAuthorization retryAuthorization = null;
        Throwable outcome = null;
        try {
            while (true) {
                try {
                    host.fillNativeContext(flow);
                    inventoryBill(flow);
                    InventoryPfPreparation.validate(action);
                    ICBillVO bill = inventoryBill(flow);
                    if (!(flow.getUserObj() instanceof ICPFParameter)
                            || !authorized.getGroupId().equals(text(bill.getHead().getPk_group()))
                            || !authorized.getOrgId().equals(text(bill.getHead().getPk_org()))) {
                        throw new BusinessException(nodeName + "组织或原生流程上下文与签名授权不一致。");
                    }
                    String billType = flow.getTrantype() == null ? flow.getBillType() : flow.getTrantype();
                    InventoryPfPreparation prepared = InventoryPfPreparation.prepare(flow.getParent(), "WRITE", billType,
                            bill, flow.getEParam());
                    if (!prepared.isAccepted()) throw new BusinessException("NC " + nodeName + "流程已取消，单据未保存。");
                    InventoryPfSaveCommand command = new InventoryPfSaveCommand(billType, bill,
                            (ICPFParameter) flow.getUserObj(), prepared.getNote(), prepared.getParameters());
                    String group = text(bill.getHead().getPk_group()), org = text(bill.getHead().getPk_org());
                    String pk = text(bill.getHead().getCgeneralhid());
                    String dispatched = retryAuthorization == null ? handoff.dispatch(group, org, pk)
                            : retryAuthorization.dispatch(group, org, pk);
                    IOperationService gateway = NCLocator.getInstance().lookup(IOperationService.class);
                    OperationResponse response = gateway.execute(dispatched, command);
                    if (response == null || !dispatched.equals(response.getRequestId()) || response.isReplayed()
                            || !(response.getBusinessResult() instanceof InventoryPfSaveResult)) throw unknownResult();
                    InventoryPfSaveResult saved = (InventoryPfSaveResult) response.getBusinessResult();
                    if (saved.getNativeResult() == null || text(saved.getPrimaryKey()) == null) throw unknownResult();
                    prepared.completed();
                    Object nativeResult = saved.getNativeResult();
                    Object[] returned;
                    if (nativeResult.getClass().isArray()) returned = (Object[]) nativeResult;
                    else { returned = (Object[]) Array.newInstance(nativeResult.getClass(), 1); returned[0] = nativeResult; }
                    action.processReturnObjs(returned);
                    return;
                } catch (Exception error) {
                    boolean dispatched = retryAuthorization == null
                            ? handoff.wasDispatched(authorized.getRequestId()) : retryAuthorization.wasDispatched();
                    // Post-dispatch native errors are exposed only after verified rollback and failure persistence.
                    Throwable cause = dispatched ? error : ExceptionUtils.unmarsh(error);
                    if (cause instanceof IResumeException) {
                        boolean retry = host.resumeNative((IResumeException) cause);
                        host.setNativeExecuted(retry);
                        if (retry && !dispatched) continue;
                        if (!retry) { outcome = error; return; }
                        if (retryAuthorization != null) {
                            try { retryAuthorization.close(error); }
                            catch (Exception cleanup) { error.addSuppressed(cleanup); throw error; }
                            retryAuthorization = null;
                        }
                        retryAuthorization = retryAuthorization();
                        if (!retryAuthorization.prepare(draft)) {
                            retryAuthorization = null;
                            host.setNativeExecuted(false);
                            return;
                        }
                        continue;
                    }
                    throw error;
                }
            }
        } catch (Exception failure) { outcome = failure; throw failure; }
          catch (Error failure) { outcome = failure; throw failure; }
        finally {
            if (retryAuthorization != null) {
                try { retryAuthorization.close(outcome); }
                catch (Exception cleanup) {
                    if (outcome != null) outcome.addSuppressed(cleanup);
                    else throw new BusinessException(nodeName + "已处理，但重试请求清理失败，请按请求号核对。", cleanup);
                }
            }
        }
    }

    @Override public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        if (action != host.action() || event != activeEvent || !isInventoryEdit() || host.editorModel() == null) {
            throw new BusinessException("当前动作不属于" + nodeName + "保存。");
        }
        String group = text(host.model().getContext().getPk_group());
        String org = headText("pk_org");
        if (group == null || org == null) throw new BusinessException("无法确定" + nodeName + "集团或业务单元。");
        boolean insert = host.model().getAppUiState() == AppUiState.ADD;
        String pk = null;
        if (!insert) {
            Object selected = host.model().getSelectedData();
            if (!billClass.isInstance(selected) || ((ICBillVO) selected).getHead() == null) {
                throw new BusinessException("无法确定待修改的" + nodeName + "单据。");
            }
            pk = text(((ICBillVO) selected).getHead().getCgeneralhid());
            if (pk == null) throw new BusinessException("待修改的" + nodeName + "单据缺少主键。");
        }
        BusinessObjectRef ref = new BusinessObjectRef(UUID.randomUUID().toString(), objectType, pk,
                headText("vbillcode"), null);
        draft = new OperationContext(null, null, group, org, node, nodeName, "Save", "保存",
                insert ? "INSERT" : "UPDATE", null, Collections.singletonList(ref));
        return draft;
    }

    @Override public boolean isAvailable(OperationContext context) {
        if (!handoff.supports(context) || !node.equals(context.getNodeCode())
                || !objectType.equals(context.getObjects().get(0).getObjectType())) return false;
        try { InventoryPfPreparation.verifyNativeContract(); return true; }
        catch (BusinessException unsupported) { return false; }
    }
    @Override public boolean bind(OperationContext context) { return isAvailable(context) && handoff.bind(context); }
    @Override public boolean wasDispatched(String requestId) { return handoff.wasDispatched(requestId); }
    @Override public void release(String requestId) { handoff.release(requestId); }
    @Override public Container getParentContainer() { return host.model().getContext().getEntranceUI(); }
    @Override public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }

    private InventoryRetryAuthorization retryAuthorization() {
        final ISignatureService signatures = NCLocator.getInstance().lookup(ISignatureService.class);
        return new InventoryRetryAuthorization(signatures, new InventoryRetryAuthorization.Prompt() {
            public SignatureDialogOutcome show(SignaturePreparation preparation, RequestCancellation cancellation) {
                ElectronicSignatureDialog dialog = new ElectronicSignatureDialog(getParentContainer(), getCurrentUserDisplayName(),
                        preparation, signatures, cancellation);
                try { dialog.showModal(); return dialog.getOutcome(); }
                finally { dialog.dispose(); }
            }
        });
    }

    private ICBillVO inventoryBill(PFlowContext flow) throws BusinessException {
        AggregatedValueObject[] bills = flow.getBillVos();
        if (flow.isBatch() || bills == null || bills.length != 1 || !billClass.isInstance(bills[0])
                || ((ICBillVO) bills[0]).getHead() == null) {
            throw new BusinessException(nodeName + "保存未能确定单据，请重新操作。");
        }
        return (ICBillVO) bills[0];
    }
    private boolean isInventoryEdit() {
        BillManageModel model = host.model();
        return model != null && model.getContext() != null && node.equals(model.getContext().getNodeCode())
                && (model.getAppUiState() == AppUiState.ADD || model.getAppUiState() == AppUiState.EDIT);
    }
    private String headText(String field) {
        Object value = host.editorModel().getCurSelHeadValue(field);
        return value instanceof String ? text((String) value) : null;
    }
    private static boolean empty(List<?> values) { return values == null || values.isEmpty(); }
    private static String text(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private static boolean isClosingSave(ActionEvent event) {
        return event != null && event.getID() == 0 && event.getActionCommand() == null;
    }
    private BusinessExceptionAdapter saveNotFinished() {
        return new BusinessExceptionAdapter(new BusinessException(nodeName + "单据尚未保存，窗口保持打开。"));
    }
    private BusinessException unknownResult() {
        return new BusinessException("无法确认" + nodeName + "保存结果，请先重新查询单据再处理。");
    }
}
