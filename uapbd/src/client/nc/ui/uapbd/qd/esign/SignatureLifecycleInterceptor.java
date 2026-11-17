package nc.ui.uapbd.qd.esign;

import java.awt.event.ActionEvent;
import javax.swing.Action;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.actions.ActionInterceptor;

/** 包裹完整原生拦截链，在外层保证签名资源最终清理，并原样保留原链失败处理结果；被拒绝的重复事件不能清理仍在执行的先前操作。 */
public final class SignatureLifecycleInterceptor implements ActionInterceptor {
    /** owner：所属 NC 动作实例。 */
    private final NCAction owner;
    /** delegate：被包装的完整原生拦截链。 */
    private final ActionInterceptor delegate;
    /** signing：负责签名准备及授权交接的控制器。 */
    private final ElectronicSignatureActionInterceptor signing;
    /** acceptedGeneration：本次已接受事件对应的生命周期代次。 */
    private long acceptedGeneration;
    /** acceptedEvent：本次已接受的动作事件，用于识别重复点击。 */
    private ActionEvent acceptedEvent;

    /** 保存所属动作、完整原链和签名控制器，后续以事件和代次识别清理归属。 */
    SignatureLifecycleInterceptor(NCAction owner, ActionInterceptor delegate,
            ElectronicSignatureActionInterceptor signing) {
        this.owner = owner; this.delegate = delegate; this.signing = signing;
    }
    /** 返回负责签名准备及授权交接的控制器。 */
    ElectronicSignatureActionInterceptor getSigning() { return signing; }

    /** 处理动作前置阶段，准备或确认授权并保护原有拦截链语义；返回值决定本次动作是否继续。 */
    public boolean beforeDoAction(Action action, ActionEvent event) {
        if (action != owner) { throw new IllegalArgumentException("Lifecycle wrapper belongs to another NC action"); }
        long before = signing.lifecycleGeneration();
        boolean accepted = false;
        Throwable original = null;
        signing.enterOuter(this);
        try {
            accepted = delegate.beforeDoAction(action, event);
            if (accepted && signing.ownsLifecycle(signing.lifecycleGeneration(), action, event)) {
                acceptedGeneration = signing.lifecycleGeneration(); acceptedEvent = event;
            }
            return accepted;
        } catch (RuntimeException failure) {
            original = failure; throw failure;
        } catch (Error failure) {
            original = failure; throw failure;
        } finally {
            signing.leaveOuter(this);
            // 被拒绝的重复事件不能清理先前仍在执行的操作。
            if (!accepted && signing.lifecycleGeneration() != before) {
                signing.finishLifecycle(signing.lifecycleGeneration(), action, event, original);
            }
        }
    }

    /** 处理动作成功回调，核对最终受控 RPC 是否实际派发，并由生命周期保护路径释放本次绑定。 */
    public void afterDoActionSuccessed(Action action, ActionEvent event) {
        long generation = acceptedGeneration;
        Throwable original = null;
        try { delegate.afterDoActionSuccessed(action, event); }
        catch (RuntimeException failure) { original = failure; throw failure; }
        catch (Error failure) { original = failure; throw failure; }
        finally { finish(generation, action, event, original); }
    }

    /** 保留原生失败处理语义与原异常，不把客户端失败回调当作业务已回滚或已提交的唯一依据。 */
    public boolean afterDoActionFailed(Action action, ActionEvent event, Throwable error) {
        long generation = acceptedGeneration;
        Throwable original = error;
        try {
            // 原样保留原链对异常已处理或未处理的判断结果。
            return delegate.afterDoActionFailed(action, event, error);
        } catch (RuntimeException failure) { original = failure; throw failure; }
        catch (Error failure) { original = failure; throw failure; }
        finally { finish(generation, action, event, original); }
    }

    /** 仅在事件和代次仍匹配时触发签名控制器清理，避免后续或重复事件清理此前动作。 */
    private void finish(long generation, Action action, ActionEvent event, Throwable original) {
        if (action != owner || event != acceptedEvent) { return; }
        try { signing.finishLifecycle(generation, action, event, original); }
        finally {
            if (acceptedGeneration == generation && acceptedEvent == event) { acceptedEvent = null; }
        }
    }
}
