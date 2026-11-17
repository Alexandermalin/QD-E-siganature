package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import nc.bs.framework.common.NCLocator;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.ui.pub.beans.MessageDialog;
import nc.ui.uif2.actions.ActionInterceptor;
import nc.ui.uif2.NCAction;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SignaturePreparation;

/** 客户端签名入口，只负责准备、确认和向最终业务 RPC 交接授权请求；权威业务执行及审计由服务端适配器完成。 */
public class ElectronicSignatureActionInterceptor implements ActionInterceptor {
    /** contextProvider：逐节点提供真实业务上下文的适配接口。 */
    private OperationContextProvider contextProvider;
    /** requestConsumer：将授权绑定到最终业务 RPC 的逐节点交接接口。 */
    private AuthorizedRequestConsumer requestConsumer;
    /** signatureService：签名远程服务，可在离线测试中显式替换。 */
    private ISignatureService signatureService;
    /** inProgress：是否已有签名流程在处理，阻止重复点击重入。 */
    private boolean inProgress;
    /** activeAction：当前正在签名的动作实例。 */
    private Action activeAction;
    /** activeContext：当前已准备或已确认的业务请求上下文。 */
    private OperationContext activeContext;
    /** lastError：最近一次交接或清理失败，供调用方和测试查看。 */
    private Throwable lastError;
    /** lifecycleOwner：安装外层生命周期包装器的 NC 动作。 */
    private NCAction lifecycleOwner;
    /** lifecycleWrapper：最终安装的外层生命周期保护器。 */
    private SignatureLifecycleInterceptor lifecycleWrapper;
    /** lifecycleAction：当前生命周期所属动作。 */
    private Action lifecycleAction;
    /** lifecycleEvent：当前生命周期所属事件。 */
    private ActionEvent lifecycleEvent;
    /** generation：生命周期递增代次，防止旧回调清理新请求。 */
    private long generation;
    /** outerDepth：外层生命周期包装器的进入深度。 */
    private int outerDepth;

    /** 注入逐节点上下文提供器，实际节点接入时必须使用经验证的实现。 */
    public void setContextProvider(OperationContextProvider provider) { contextProvider = provider; }
    /** 注入最终业务 RPC 交接接口，未就绪时受控动作应拒绝继续。 */
    public void setRequestConsumer(AuthorizedRequestConsumer consumer) { requestConsumer = consumer; }
    /** 显式指定签名服务，供接线或离线测试使用；未指定时使用原生服务定位。 */
    public void setSignatureService(ISignatureService service) { signatureService = service; }
    /** 返回最近一次交接或清理失败，供调用方和测试查看。 */
    public Throwable getLastError() { return lastError; }

    /** 返回显式注入或经原生定位取得的签名服务，不使用静态共享业务状态。 */
    protected ISignatureService service() {
        if (signatureService == null) { signatureService = NCLocator.getInstance().lookup(ISignatureService.class); }
        return signatureService;
    }

    /** 处理动作前置阶段，准备或确认授权并保护原有拦截链语义；返回值决定本次动作是否继续。 */
    @Override
    public boolean beforeDoAction(Action action, ActionEvent event) {
        // 拒绝重复点击时不修改原请求代次；先前仍在执行的操作继续由自己的生命周期负责。
        if (inProgress) {
            reportFailure(new BusinessException("电子签名操作正在处理，请勿重复触发。"));
            return false;
        }
        inProgress = true;
        generation++;
        lifecycleAction = action;
        lifecycleEvent = event;
        lastError = null;
        boolean accepted = false;
        OperationContext context = null;
        RequestCancellation cancellation = null;
        try {
            if (contextProvider == null) { throw new BusinessException("当前节点尚未配置电子签名上下文适配器。"); }
            OperationContext draft = contextProvider.createContext(action, event);
            if (draft == null) { throw new BusinessException("当前操作缺少业务上下文。"); }
            // 在准备请求前检查交接适配是否就绪；BYPASS 路径仍可沿用原 NC 行为。
            boolean available = requestConsumer != null && requestConsumer.isAvailable(draft);
            SignaturePreparation prepared = service().prepare(draft);
            if (prepared == null || prepared.getContext() == null || prepared.getPolicy() == null) {
                throw new BusinessException("签名准备结果不完整，已终止本次操作。");
            }
            // 不受控时沿用原 NC 动作；受控流程才要求有效请求、外层保护和最终业务 RPC 交接。
            if (!prepared.getPolicy().isControlled() && !prepared.requiresExecutionGuard()) { return true; }
            context = prepared.getContext();
            if (context.getRequestId() == null || context.getRequestId().length() == 0) {
                throw new BusinessException("签名准备结果没有请求编号。");
            }
            cancellation = new RequestCancellation(service(), context.getRequestId());
            // 运行时再次核对最终安装关系，防止之后的插件替换拦截链而失去外层清理。
            if (outerDepth == 0 || action != lifecycleOwner || lifecycleOwner.getInterceptor() != lifecycleWrapper) {
                throw new BusinessException("当前节点尚未在完整原拦截器链外安装签名生命周期保护，已终止本次操作。");
            }
            if (!available) { throw new BusinessException("当前操作的业务 RPC 尚未接入受控服务，已终止本次操作。"); }
            if (prepared.getPolicy().isSignatureRequired() || prepared.getPolicy().isReasonRequired()) {
                SignatureDialogOutcome outcome = showSignatureDialog(prepared, cancellation);
                if (outcome == null || !outcome.isConfirmed()) {
                    if (outcome != null && outcome.getFailure() != null) { throw asException(outcome.getFailure()); }
                    return false;
                }
                context = withReason(context, outcome.getReason());
            }
            // 只有最终业务 RPC 确实消费授权时才放行动作，绑定本身不表示已经派发或业务成功。
            if (!requestConsumer.bind(context)) {
                throw new BusinessException("业务适配器未接收授权请求，已终止本次操作。");
            }
            activeContext = context;
            activeAction = action;
            accepted = true;
            return true;
        } catch (Exception error) {
            lastError = error;
            reportFailure(error);
            return false;
        } finally {
            // 所有未放行出口都共用一次取消尝试并释放实例绑定，避免准备后遗留可用请求。
            if (!accepted) {
                if (cancellation != null) {
                    try { cancellation.cancel(context == null ? null : context.getReason()); }
                    catch (Exception error) { retainAndReport(error); }
                }
                release(context);
            }
        }
    }

    /** 打开单次认证窗口并返回局部结果，窗口结果不表示业务操作成功。 */
    protected SignatureDialogOutcome showSignatureDialog(SignaturePreparation prepared,
            RequestCancellation cancellation) {
        ElectronicSignatureDialog dialog = null;
        try {
            dialog = new ElectronicSignatureDialog(parent(), contextProvider.getCurrentUserDisplayName(),
                    prepared, service(), cancellation);
            dialog.showModal();
            return dialog.getOutcome();
        } finally {
            if (dialog != null) { dialog.dispose(); }
        }
    }

    /** 处理动作成功回调，核对最终受控 RPC 是否实际派发，并由生命周期保护路径释放本次绑定。 */
    @Override
    public void afterDoActionSuccessed(Action action, ActionEvent event) {
        if (activeContext == null || activeAction != action) { return; }
        OperationContext completed = activeContext;
        try {
            // 原动作成功回调仍需核对派发事实；未交给受控 RPC 时不生成业务成功审计。
            if (!requestConsumer.wasDispatched(completed.getRequestId())) {
                BusinessException error = new BusinessException("原动作未调用已接管的业务 RPC，未记录业务成功审计。请检查节点适配。");
                lastError = error;
                reportFailure(error);
                reportBusinessFailure(completed, "DISPATCH_NOT_OBSERVED");
            }
        } catch (Exception error) {
            retainAndReport(error);
        } finally {
            release(completed);
        }
    }

    /** 保留原生失败处理语义与原异常，不把客户端失败回调当作业务已回滚或已提交的唯一依据。 */
    @Override
    public boolean afterDoActionFailed(Action action, ActionEvent event, Throwable error) {
        if (activeContext == null || activeAction != action) { return true; }
        OperationContext failed = activeContext;
        lastError = error;
        try {
            reportBusinessFailure(failed, "BUSINESS_EXCEPTION");
        } catch (Exception auditError) {
            retainAndReport(auditError);
        } finally {
            release(failed);
        }
        // 让 NC 继续执行原有错误处理路径，保持原异常不变。
        return true;
    }

    /** 上报允许的客户端失败码，仅在请求仍为 READY 时登记失败及结束时间。 */
    private void reportBusinessFailure(OperationContext context, String code) throws BusinessException {
        service().reportBusinessFailure(context.getRequestId(), code);
    }

    /** 尝试清除当前动作实例的请求绑定并保留异常提示，避免遗留授权被后续动作复用。 */
    private void release(OperationContext context) {
        try {
            releaseStrict(context);
        } catch (RuntimeException error) {
            retainAndReport(error);
        }
    }

    /** 调用交接接口释放请求绑定，失败向生命周期清理路径传播。 */
    private void releaseStrict(OperationContext context) {
        try {
            if (context != null && requestConsumer != null) { requestConsumer.release(context.getRequestId()); }
        } finally {
            activeContext = null;
            activeAction = null;
            lifecycleAction = null;
            lifecycleEvent = null;
            inProgress = false;
        }
    }

    /** 检查控制器能否安装到当前 NC 动作，防止同一实例被不同动作或不兼容链复用。 */
    void requireInstallableOn(NCAction action) {
        if (lifecycleOwner != null && lifecycleOwner != action) {
            throw new IllegalStateException("One signing interceptor instance cannot serve multiple NC actions");
        }
    }
    /** 记录已经安装的外层保护器及所属动作，供运行时检查安装关系。 */
    void attachLifecycle(NCAction owner, SignatureLifecycleInterceptor wrapper) {
        requireInstallableOn(owner); lifecycleOwner = owner; lifecycleWrapper = wrapper;
    }
    /** 登记外层包装器进入，受控前置流程据此确认生命周期保护存在。 */
    void enterOuter(SignatureLifecycleInterceptor wrapper) {
        if (wrapper != lifecycleWrapper) { throw new IllegalStateException("Unexpected signing lifecycle owner"); }
        outerDepth++;
    }
    /** 退出外层保护深度，按实例身份核对进入与退出关系。 */
    void leaveOuter(SignatureLifecycleInterceptor wrapper) {
        if (wrapper != lifecycleWrapper || outerDepth <= 0) { throw new IllegalStateException("Unbalanced signing lifecycle"); }
        outerDepth--;
    }
    /** 返回当前请求生命周期代次，供外层包装器保留事件归属。 */
    long lifecycleGeneration() { return generation; }
    /** 检查代次、动作和事件是否仍属于本次流程，阻止陈旧回调误清理其他请求。 */
    boolean ownsLifecycle(long expected, Action action, ActionEvent event) {
        return inProgress && generation == expected && lifecycleAction == action && lifecycleEvent == event;
    }

    /** 按代次幂等完成清理，核对派发事实并结束仍可关闭的请求；原业务异常保持原对象，清理失败作为附加信息保留。 */
    void finishLifecycle(long expected, Action action, ActionEvent event, Throwable original) {
        if (!ownsLifecycle(expected, action, event)) { return; }
        if (original != null) { lastError = original; }
        OperationContext unfinished = activeContext;
        Exception cleanup = null;
        try { cancelIfPending(unfinished); }
        catch (Exception failure) { cleanup = failure; }
        try { releaseStrict(unfinished); }
        catch (RuntimeException failure) {
            if (cleanup == null) { cleanup = failure; } else if (cleanup != failure) { cleanup.addSuppressed(failure); }
        }
        if (cleanup != null) {
            if (original != null && original != cleanup) { original.addSuppressed(cleanup); lastError = original; }
            else { lastError = cleanup; }
            reportFailure(cleanup);
        }
    }

    /** 请求服务端判断是否可结束尚未执行的授权，不在客户端推断 RUNNING 或已提交结果。 */
    private void cancelIfPending(OperationContext context) throws BusinessException {
        // 是否结束 READY/PENDING 由服务端决定，已提交结果或 RUNNING 不由客户端改写。
        if (context != null) { service().cancel(context.getRequestId(), context.getReason()); }
    }

    /** 复制已准备上下文并补入本次确认事由，不改变对象范围或服务端请求标识。 */
    private OperationContext withReason(OperationContext context, String reason) {
        return new OperationContext(context.getRequestId(), context.getBatchId(), context.getGroupId(), context.getOrgId(),
                context.getNodeCode(), context.getNodeName(), context.getButtonCode(), context.getButtonName(),
                context.getOperationType(), reason, context.getObjects(), context.getPolicyProtocol(), context.getMaterialProdImpact(),
                context.getArchiveTreeSaveDraft(),context.getPartnerMainSaveDraft(),context.getPartnerBatchDraft());
    }

    /** 将 Throwable 适配为异常回调所需类型，并保留原始原因。 */
    private Exception asException(Throwable error) {
        return error instanceof Exception ? (Exception) error : new RuntimeException(error);
    }

    /** 保留最近异常并尝试显示提示，提示失败也不能替换原业务异常路径。 */
    private void retainAndReport(Throwable error) {
        if (lastError == error) { return; }
        if (lastError != null && lastError != error) { lastError.addSuppressed(error); }
        else { lastError = error; }
        reportFailure(error);
    }

    /** 通过 NC 消息窗口报告失败，可在测试替身中覆盖以观察提示而不打开窗口。 */
    protected void reportFailure(Throwable error) {
        try {
            MessageDialog.showErrorDlg(parent(), "电子签名", error.getMessage() == null ? "操作未完成，请查看系统异常记录。" : error.getMessage());
        } catch (RuntimeException notificationError) {
            if (notificationError != error) { error.addSuppressed(notificationError); }
            // 即使界面无法显示提示，也继续保留原 NC 失败处理路径。
            java.util.logging.Logger.getLogger(getClass().getName()).log(java.util.logging.Level.SEVERE,
                    "QD_ESIGN_CLIENT_NOTIFICATION_FAILED", notificationError);
        }
    }

    /** 取得当前节点提供的父容器，缺少提供器时使用空父容器。 */
    private Container parent() { return contextProvider == null ? null : contextProvider.getParentContainer(); }
}
