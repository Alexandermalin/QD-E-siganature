package nc.ui.uapbd.qd.esign;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.actions.ActionInterceptor;

/** 签名生命周期包装器的安装工具，必须在原生及插件拦截链组装完成后最后安装；未知复合链需要单独验证的适配方式。 */
public final class SignatureLifecycleInstaller {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private SignatureLifecycleInstaller() { }

    /** 在原链组装完成后最后安装外层保护，检查重复安装及不兼容的复合链，避免签名清理被短路。 */
    public static SignatureLifecycleInterceptor install(NCAction action,
            ElectronicSignatureActionInterceptor signing) {
        if (action == null || signing == null) { throw new IllegalArgumentException("Action and signing interceptor required"); }
        ActionInterceptor original = action.getInterceptor();
        if (original instanceof SignatureLifecycleInterceptor) {
            SignatureLifecycleInterceptor installed = (SignatureLifecycleInterceptor) original;
            if (installed.getSigning() != signing) { throw new IllegalStateException("A different signing lifecycle is already installed"); }
            return installed;
        }
        Set<ActionInterceptor> visited = Collections.newSetFromMap(new IdentityHashMap<ActionInterceptor, Boolean>());
        if (!contains(original, signing, visited)) {
            throw new IllegalStateException("The assembled interceptor chain does not expose this signing interceptor");
        }
        signing.requireInstallableOn(action);
        SignatureLifecycleInterceptor wrapper = new SignatureLifecycleInterceptor(action, original, signing);
        action.setInterceptor(wrapper);
        signing.attachLifecycle(action, wrapper);
        return wrapper;
    }

    /** 以对象身份及已访问集合遍历已知拦截链，识别目标并避免循环；未知复合结构不推断为兼容。 */
    private static boolean contains(ActionInterceptor current, ActionInterceptor expected, Set<ActionInterceptor> visited) {
        if (current == expected) { return true; }
        if (current == null || !visited.add(current)) { return false; }
        if (current instanceof nc.ui.uif2.actions.CompositeActionInterceptor) {
            for (ActionInterceptor child : ((nc.ui.uif2.actions.CompositeActionInterceptor) current).getInterceptors()) {
                if (contains(child, expected, visited)) {
                    throw new IllegalStateException("Signing cannot be added inside the UIF2 first-true failure composite; use a verified pubapp chain or explicit node adapter");
                }
            }
        } else if (current instanceof nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor) {
            for (ActionInterceptor child : ((nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor) current).getInterceptors()) {
                if (contains(child, expected, visited)) { return true; }
            }
        }
        // 未知自定义复合链必须提供单独验证的显式适配，不能猜测其短路语义。
        return false;
    }
}
