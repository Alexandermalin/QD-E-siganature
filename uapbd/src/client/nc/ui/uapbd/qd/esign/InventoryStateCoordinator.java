package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.swing.Action;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.BusinessExceptionAdapter;
import nc.itf.pubapp.pub.exception.IResumeException;
import nc.itf.uapbd.qd.esign.IOperationService;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.model.BillManageModel;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.components.CommonConfirmDialogUtils;
import nc.vo.ic.general.define.ICBillHeadVO;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.InventoryPfStateResult;
import nc.vo.ic.qd.esign.SapplyPfBatchCommand;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pubapp.pattern.exception.ExceptionUtils;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.OperationResponse;
import nc.vo.uapbd.qd.esign.SapplyBatchScope;
import nc.vo.uapbd.qd.esign.SignaturePreparation;

/** Shared handoff for inventory state actions; each host retains its original NC action subclass. */
final class InventoryStateCoordinator implements OperationContextProvider, AuthorizedRequestConsumer {
    interface ConfirmedDelete {
        boolean hasDataPermission();
        void showPermissionDenied();
        void run() throws Exception;
    }
    interface DeleteConfirmation { boolean confirm(Container parent); }
    interface SigningFactory { ElectronicSignatureActionInterceptor create(); }
    interface Host {
        AbstractScriptExcAction action();
        BillManageModel model();
        void performNative(ActionEvent event);
        void fillNativeContext(PFlowContext flow);
        boolean isComposite();
        boolean resumeNative(IResumeException error);
    }

    interface SelectionHost extends Host {
        Object[] selectedBills() throws BusinessException;
    }

    private final Host host;
    private final String node, nodeName, objectType, button, buttonName, operation, nativeAction;
    private final Class<? extends ICBillVO> billClass;
    private final DeleteConfirmation deleteConfirmation;
    private final SigningFactory signingFactory;
    private final InventoryStateHandoff handoff = new InventoryStateHandoff();
    private ActionEvent activeEvent;
    private SignatureLifecycleInterceptor lifecycle;
    private OperationContext draft;
    private boolean deleteInProgress;
    private boolean deleteDoActionActive;
    private boolean deleteDoActionConsumed;
    private boolean deleteActionReady;
    private boolean awaitingDeletePermission;
    private boolean deletePermissionObserved;
    private boolean deletePermissionAllowed;

    InventoryStateCoordinator(Host host, String node, String nodeName, String objectType,
            Class<? extends ICBillVO> billClass, String button, String buttonName, String operation, String nativeAction) {
        this(host, node, nodeName, objectType, billClass, button, buttonName, operation, nativeAction,
                new DeleteConfirmation() {
                    public boolean confirm(Container parent) {
                        return CommonConfirmDialogUtils.showConfirmDeleteDialog(parent) == 4;
                    }
                }, new SigningFactory() {
                    public ElectronicSignatureActionInterceptor create() { return new ElectronicSignatureActionInterceptor(); }
                });
    }

    InventoryStateCoordinator(Host host, String node, String nodeName, String objectType,
            Class<? extends ICBillVO> billClass, String button, String buttonName, String operation, String nativeAction,
            DeleteConfirmation deleteConfirmation, SigningFactory signingFactory) {
        if (host == null || deleteConfirmation == null || signingFactory == null) {
            throw new IllegalArgumentException("Inventory action host, confirmation and signing factory are required");
        }
        this.host = host; this.node = node; this.nodeName = nodeName; this.objectType = objectType;
        this.billClass = billClass; this.button = button; this.buttonName = buttonName;
        this.operation = operation; this.nativeAction = nativeAction;
        this.deleteConfirmation = deleteConfirmation; this.signingFactory = signingFactory;
    }

    void perform(ActionEvent event) {
        if (lifecycle != null || deleteInProgress) {
            throw new BusinessExceptionAdapter(new BusinessException(nodeName + buttonName + "正在处理，请勿重复触发。"));
        }
        if (!matchesNode()) { host.performNative(event); return; }
        if ("DELETE".equals(operation)) {
            Set<ActionInterceptor> visited = Collections.newSetFromMap(new IdentityHashMap<ActionInterceptor, Boolean>());
            if (containsSigning(host.action().getInterceptor(), visited)) {
                throw new BusinessExceptionAdapter(new BusinessException(nodeName + "删除拦截链已包含签名，请检查节点配置。"));
            }
            AbstractScriptExcAction action = host.action();
            ActionInterceptor original = action.getInterceptor();
            ActionInterceptor outer = new NativeDeleteChain(original);
            deleteInProgress = true; deleteDoActionConsumed = false; deleteActionReady = false; activeEvent = event;
            try { action.setInterceptor(outer); host.performNative(event); }
            finally {
                if (action.getInterceptor() == outer) action.setInterceptor(original);
                deleteActionReady = false; deleteDoActionActive = false; deleteDoActionConsumed = false;
                awaitingDeletePermission = false; deletePermissionObserved = false; deletePermissionAllowed = false;
                deleteInProgress = false; activeEvent = null; draft = null; handoff.reset();
            }
            return;
        }
        AbstractScriptExcAction action = host.action();
        ActionInterceptor original = action.getInterceptor();
        ElectronicSignatureActionInterceptor signing = signingFactory.create();
        signing.setContextProvider(this); signing.setRequestConsumer(this);
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
                lifecycle = null; activeEvent = null; draft = null; handoff.reset();
            }
        }
    }

    /** True for the node's delete action, including direct calls that must be guarded. */
    boolean requiresConfirmedDelete() { return matchesNode() || deleteInProgress; }

    /** Preserve native confirmation and outer interceptor order, then authorize before native deletion. */
    void deleteAfterConfirmation(ActionEvent event, ConfirmedDelete nativeDelete) throws Exception {
        if (!"DELETE".equals(operation) || !deleteInProgress || !deleteActionReady || deleteDoActionActive || deleteDoActionConsumed
                || event != activeEvent || !matchesNode()
                || lifecycle != null || nativeDelete == null) {
            throw new BusinessException(nodeName + "删除状态已变化，请重新操作。");
        }
        deleteDoActionActive = true; deleteDoActionConsumed = true;
        try { runConfirmedDelete(event, nativeDelete); }
        finally { deleteDoActionActive = false; }
    }

    private void runConfirmedDelete(ActionEvent event, ConfirmedDelete nativeDelete) throws Exception {
        InventoryPfPreparation.verifyNativeDeleteContract(host.action().getClass().getSuperclass());
        rejectExistingSigning();
        if (!deleteConfirmation.confirm(getParentContainer())) return;
        rejectExistingSigning();
        if (!nativeDelete.hasDataPermission()) { nativeDelete.showPermissionDenied(); return; }
        if (!matchesNode() || event != activeEvent || !deleteActionReady) {
            throw new BusinessException(nodeName + "删除确认后的状态已变化，请重新操作。");
        }
        rejectExistingSigning();
        AbstractScriptExcAction action = host.action();
        ActionInterceptor original = action.getInterceptor();
        ElectronicSignatureActionInterceptor signing = signingFactory.create();
        if (signing == null) throw new BusinessException("库存删除签名适配器不可用。");
        signing.setContextProvider(this); signing.setRequestConsumer(this);
        Throwable failure = null;
        try {
            action.setInterceptor(signing);
            lifecycle = SignatureLifecycleInstaller.install(action, signing);
            if (!lifecycle.beforeDoAction(action, event)) return;
            if (!matchesNode() || event != activeEvent || action.getInterceptor() != lifecycle) {
                throw new BusinessException(nodeName + "删除授权后的状态已变化，请重新操作。");
            }
            awaitingDeletePermission = true; deletePermissionObserved = false; deletePermissionAllowed = false;
            try {
                nativeDelete.run();
                if (!deletePermissionObserved) {
                    throw new BusinessException(nodeName + "删除未观察到原生数据权限复核，请检查节点适配。");
                }
                if (!deletePermissionAllowed) {
                    OperationContext authorized = handoff.context();
                    if (authorized != null && handoff.wasDispatched(authorized.getRequestId())) {
                        throw new BusinessException(nodeName + "删除权限复核与派发状态冲突，请按请求号核对。");
                    }
                }
            }
            catch (Exception error) {
                try { lifecycle.afterDoActionFailed(action, event, error); }
                catch (RuntimeException cleanup) { if (cleanup != error) error.addSuppressed(cleanup); }
                throw error;
            }
            finally { awaitingDeletePermission = false; }
            if (!deletePermissionAllowed) {
                // The native action already displayed its denial; finish any authorization as a business failure.
                lifecycle.afterDoActionFailed(action, event, new BusinessException("原生数据权限复核未通过。"));
                return;
            }
            lifecycle.afterDoActionSuccessed(action, event);
        } catch (Exception error) { failure = error; throw error; }
          catch (Error error) { failure = error; throw error; }
        finally {
            try { signing.finishLifecycle(signing.lifecycleGeneration(), action, event, failure); }
            finally {
                if (action.getInterceptor() == lifecycle || action.getInterceptor() == signing) action.setInterceptor(original);
                lifecycle = null; draft = null; handoff.reset();
            }
        }
    }

    /** Observe the original ScriptPFlowAction recheck, without changing permission decisions elsewhere. */
    void observeDeletePermission(boolean allowed) {
        if (!awaitingDeletePermission) return;
        if (deletePermissionObserved) {
            throw new BusinessExceptionAdapter(new BusinessException(nodeName + "删除权限复核重复，请检查节点适配。"));
        }
        deletePermissionObserved = true;
        deletePermissionAllowed = allowed;
    }

    void guardDoAction(ActionEvent event) throws BusinessException {
        if ((matchesNode() || lifecycle != null) && (lifecycle == null || event != activeEvent
                || host.action().getInterceptor() != lifecycle || !matchesNode() || draft == null)) {
            throw new BusinessException(nodeName + buttonName + "状态已变化，请重新操作。");
        }
    }
    boolean takesOverFlow() throws BusinessException {
        if (handoff.context() != null) return true;
        if ((matchesNode() && lifecycle == null) || (lifecycle != null && draft == null)) {
            throw new BusinessException(nodeName + buttonName + "缺少策略核对结果。");
        }
        return false;
    }

    void processFlow(PFlowContext flow) throws Exception {
        requireDeletePermission();
        OperationContext authorized = handoff.context();
        AbstractScriptExcAction action = host.action();
        if (authorized == null || flow != action.getFlowContext() || !matchesNode() || lifecycle == null
                || action.getInterceptor() != lifecycle
                || ("40080801".equals(node) && "Commit".equals(button) && host.isComposite())
                || (host.isComposite() && (!empty(action.getPreActionNames()) || !empty(action.getAfterActionNames())))
                || !nativeAction.equals(flow.getActionName()) || text(flow.getUserId()) != null) {
            throw new BusinessException(nodeName + buttonName + "流程与当前授权不匹配。");
        }
        if (SapplyBatchScope.supports(authorized)) {
            processSapplyBatch(flow, action, authorized);
            return;
        }
        InventoryRetryAuthorization retry = null;
        Throwable outcome = null;
        try {
            while (true) {
                try {
                    host.fillNativeContext(flow);
                    inventoryBill(flow, authorized);
                    InventoryPfPreparation.validate(action);
                    ICBillVO bill = inventoryBill(flow, authorized);
                    String billType = flow.getTrantype() == null ? flow.getBillType() : flow.getTrantype();
                    InventoryPfPreparation prepared = InventoryPfPreparation.prepare(flow.getParent(), nativeAction,
                            billType, bill, flow.getEParam());
                    if (!prepared.isAccepted()) throw new BusinessException("NC " + nodeName + buttonName + "流程已取消。");
                    InventoryPfStateCommand command = new InventoryPfStateCommand(billType, bill, flow.getUserObj(),
                            prepared.getNote(), prepared.getParameters());
                    ICBillHeadVO head = bill.getHead();
                    String dispatched = retry == null ? dispatchCurrent(text(head.getPk_group()), text(head.getPk_org()),
                            text(head.getCgeneralhid())) : retry.dispatch(text(head.getPk_group()), text(head.getPk_org()),
                            text(head.getCgeneralhid()));
                    IOperationService gateway = NCLocator.getInstance().lookup(IOperationService.class);
                    OperationResponse response = gateway.execute(dispatched, command);
                    if (response == null || response.isReplayed() || !dispatched.equals(response.getRequestId())
                            || !(response.getBusinessResult() instanceof InventoryPfStateResult)) throw unknownResult();
                    InventoryPfStateResult result = (InventoryPfStateResult) response.getBusinessResult();
                    if (!authorized.getObjects().get(0).getObjectPk().equals(result.getPrimaryKey())
                            || result.isDeleted() != "DELETE".equals(operation)) throw unknownResult();
                    prepared.completed();
                    Object raw = result.getNativeResult();
                    Object[] returned = null;
                    if (raw != null) {
                        if (raw.getClass().isArray()) returned = (Object[]) raw;
                        else { returned = (Object[]) Array.newInstance(raw.getClass(), 1); returned[0] = raw; }
                    }
                    action.processReturnObjs(returned);
                    return;
                } catch (Exception error) {
                    boolean dispatched = retry == null ? handoff.wasDispatched(authorized.getRequestId()) : retry.wasDispatched();
                    Throwable cause = dispatched ? error : ExceptionUtils.unmarsh(error);
                    if (cause instanceof IResumeException) {
                        boolean again = host.resumeNative((IResumeException) cause);
                        InventoryPfPreparation.setExecuted(action, again);
                        if (again && !dispatched) continue;
                        if (!again) { outcome = error; return; }
                        if (retry != null) {
                            try { retry.close(error); }
                            catch (Exception cleanup) { error.addSuppressed(cleanup); throw error; }
                            retry = null;
                        }
                        retry = retryAuthorization();
                        if (!retry.prepare(draft)) { retry = null; InventoryPfPreparation.setExecuted(action, false); return; }
                        continue;
                    }
                    throw error;
                }
            }
        } catch (Exception failure) { outcome = failure; throw failure; }
          catch (Error failure) { outcome = failure; throw failure; }
        finally {
            if (retry != null) {
                try { retry.close(outcome); }
                catch (Exception cleanup) {
                    if (outcome != null) outcome.addSuppressed(cleanup);
                    else throw new BusinessException(nodeName + buttonName + "已处理，但请求清理失败，请按请求号核对。", cleanup);
                }
            }
        }
    }

    private void processSapplyBatch(PFlowContext flow, AbstractScriptExcAction action, OperationContext authorized)
            throws Exception {
        host.fillNativeContext(flow);
        InventoryPfPreparation.validate(action);
        AggregatedValueObject[] actual = flow.getBillVos();
        if (!flow.isBatch() || actual == null || actual.length != authorized.getObjects().size()) {
            throw new BusinessException("出库申请批量原生单据范围已变化。");
        }
        SapplyBillVO[] bills = new SapplyBillVO[actual.length];
        for (int index = 0; index < actual.length; index++) {
            if (!(actual[index] instanceof SapplyBillVO)) throw new BusinessException("出库申请批量单据类型不正确。");
            bills[index] = (SapplyBillVO) actual[index];
        }
        SapplyBatchClientPreparation.beforeCheck(action, flow, bills);
        Set<String> selected = new HashSet<String>();
        for (SapplyBillVO bill : bills) {
            ICBillHeadVO head = bill.getHead();
            if (head == null || !authorized.getGroupId().equals(text(head.getPk_group()))
                    || !authorized.getOrgId().equals(text(head.getPk_org()))
                    || text(head.getCgeneralhid()) == null || !selected.add(text(head.getCgeneralhid()))) {
                throw new BusinessException("出库申请批量单据或组织已变化。");
            }
        }
        String billType = flow.getTrantype() == null ? flow.getBillType() : flow.getTrantype();
        SapplyPfBatchCommand command = SapplyBatchClientPreparation.prepare(flow, nativeAction, billType, bills);
        if (command == null) {
            InventoryPfPreparation.setExecuted(action, false);
            return;
        }
        requireDeletePermission();
        String id = handoff.dispatchBatch(selected);
        OperationResponse response = NCLocator.getInstance().lookup(IOperationService.class).execute(id, command);
        SapplyBatchClientResult.apply(action, host.model(), getParentContainer(), authorized, response, bills);
    }

    public OperationContext createContext(Action action, ActionEvent event) throws BusinessException {
        if (action != host.action() || event != activeEvent || !matchesNode()) {
            throw new BusinessException("当前动作不属于" + nodeName + buttonName + "。");
        }
        String group = text(host.model().getContext().getPk_group()), org = null;
        Object[] selected = host instanceof SelectionHost ? ((SelectionHost) host).selectedBills()
                : host.model().getSelectedOperaDatas();
        if (group == null || selected == null || selected.length == 0) throw new BusinessException("请选择待操作的" + nodeName + "单据。");
        List<BusinessObjectRef> refs = new ArrayList<BusinessObjectRef>();
        for (Object value : selected) {
            if (!billClass.isInstance(value) || ((ICBillVO) value).getHead() == null) {
                throw new BusinessException("无法确定待操作的" + nodeName + "单据。");
            }
            ICBillHeadVO head = ((ICBillVO) value).getHead();
            String pk = text(head.getCgeneralhid()), billOrg = text(head.getPk_org());
            if (pk == null || billOrg == null || !group.equals(text(head.getPk_group()))
                    || (org != null && !org.equals(billOrg))) {
                throw new BusinessException("所选库存单据的主键或组织不一致，请重新选择。");
            }
            org = billOrg;
            refs.add(new BusinessObjectRef(UUID.randomUUID().toString(), objectType, pk, text(head.getVbillcode()), null));
        }
        draft = new OperationContext(null, null, group, org, node, nodeName, button, buttonName, operation, null, refs);
        return draft;
    }
    public boolean isAvailable(OperationContext context) {
        if (!handoff.supports(context) || !node.equals(context.getNodeCode()) || !button.equals(context.getButtonCode())
                || !objectType.equals(context.getObjects().get(0).getObjectType())) return false;
        try { InventoryPfPreparation.verifyNativeContract(); return true; }
        catch (BusinessException unsupported) { return false; }
    }
    public boolean bind(OperationContext context) { return isAvailable(context) && handoff.bind(context); }
    String dispatchCurrent(String group, String org, String pk) throws BusinessException {
        requireDeletePermission();
        return handoff.dispatch(group, org, pk);
    }
    private void requireDeletePermission() throws BusinessException {
        if ("DELETE".equals(operation) && (!awaitingDeletePermission || !deletePermissionObserved || !deletePermissionAllowed)) {
            throw new BusinessException(nodeName + "删除缺少通过的原生数据权限复核。");
        }
    }
    public boolean wasDispatched(String requestId) { return handoff.wasDispatched(requestId); }
    public void release(String requestId) { handoff.release(requestId); }
    public Container getParentContainer() { return host.model().getContext().getEntranceUI(); }
    public String getCurrentUserDisplayName() { return InvocationInfoProxy.getInstance().getUserCode(); }

    private InventoryRetryAuthorization retryAuthorization() {
        final ISignatureService signatures = NCLocator.getInstance().lookup(ISignatureService.class);
        return new InventoryRetryAuthorization(signatures, new InventoryRetryAuthorization.Prompt() {
            public SignatureDialogOutcome show(SignaturePreparation preparation, RequestCancellation cancellation) {
                ElectronicSignatureDialog dialog = new ElectronicSignatureDialog(getParentContainer(), getCurrentUserDisplayName(),
                        preparation, signatures, cancellation);
                try { dialog.showModal(); return dialog.getOutcome(); } finally { dialog.dispose(); }
            }
        }, new InventoryStateHandoff());
    }
    private ICBillVO inventoryBill(PFlowContext flow, OperationContext authorized) throws BusinessException {
        AggregatedValueObject[] bills = flow.getBillVos();
        if (flow.isBatch() || bills == null || bills.length != 1 || !billClass.isInstance(bills[0])
                || ((ICBillVO) bills[0]).getHead() == null) throw new BusinessException("当前库存受控操作只支持单张单据。");
        ICBillVO bill = (ICBillVO) bills[0]; ICBillHeadVO head = bill.getHead();
        if (!authorized.getGroupId().equals(text(head.getPk_group())) || !authorized.getOrgId().equals(text(head.getPk_org()))
                || !authorized.getObjects().get(0).getObjectPk().equals(text(head.getCgeneralhid()))) {
            throw new BusinessException("库存单据或组织与签名授权不一致。");
        }
        return bill;
    }
    private boolean matchesNode() {
        return host.model() != null && host.model().getContext() != null && node.equals(host.model().getContext().getNodeCode());
    }
    /** Keep NC's original interceptor callbacks while disallowing direct entry during its before/after callbacks. */
    private final class NativeDeleteChain implements ActionInterceptor {
        private final ActionInterceptor delegate;
        NativeDeleteChain(ActionInterceptor delegate) { this.delegate = delegate; }
        public boolean beforeDoAction(Action action, ActionEvent event) {
            if (action != host.action() || event != activeEvent || !deleteInProgress) {
                throw new IllegalStateException("Unexpected inventory delete event");
            }
            boolean accepted = delegate == null || delegate.beforeDoAction(action, event);
            deleteActionReady = accepted;
            return accepted;
        }
        public void afterDoActionSuccessed(Action action, ActionEvent event) {
            deleteActionReady = false;
            if (delegate != null) delegate.afterDoActionSuccessed(action, event);
        }
        public boolean afterDoActionFailed(Action action, ActionEvent event, Throwable error) {
            deleteActionReady = false;
            return delegate == null || delegate.afterDoActionFailed(action, event, error);
        }
    }
    private static boolean containsSigning(ActionInterceptor current, Set<ActionInterceptor> visited) {
        if (current == null || !visited.add(current)) return false;
        if (current instanceof ElectronicSignatureActionInterceptor || current instanceof SignatureLifecycleInterceptor) return true;
        if (current instanceof InventoryStateCoordinator.NativeDeleteChain) {
            return containsSigning(((InventoryStateCoordinator.NativeDeleteChain) current).delegate, visited);
        }
        if (current instanceof nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor) {
            for (ActionInterceptor child : ((nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor) current).getInterceptors())
                if (containsSigning(child, visited)) return true;
        } else if (current instanceof nc.ui.uif2.actions.CompositeActionInterceptor) {
            for (ActionInterceptor child : ((nc.ui.uif2.actions.CompositeActionInterceptor) current).getInterceptors())
                if (containsSigning(child, visited)) return true;
        }
        return false;
    }
    private void rejectExistingSigning() throws BusinessException {
        Set<ActionInterceptor> visited = Collections.newSetFromMap(new IdentityHashMap<ActionInterceptor, Boolean>());
        if (containsSigning(host.action().getInterceptor(), visited)) {
            throw new BusinessException(nodeName + "删除拦截链已包含签名，请检查节点配置。");
        }
    }
    private static boolean empty(List<?> values) { return values == null || values.isEmpty(); }
    private static String text(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private BusinessException unknownResult() {
        return new BusinessException("无法确认" + nodeName + buttonName + "结果，请先重新查询单据。");
    }
}
