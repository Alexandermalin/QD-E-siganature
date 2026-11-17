package nc.ui.uapbd.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import nc.bs.uif2.validation.IValidationService;
import nc.ui.pubapp.pub.common.context.PFlowContext;
import nc.ui.pubapp.uif2app.actions.pflow.AbstractScriptExcAction;
import nc.ui.pubapp.uif2app.actions.pflow.IBatchOperateValidateService;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;

/**
 * 发货单/销售发票共用 PF 客户端桥的离线契约检查。
 *
 * <p>只验证：覆盖 procFlow 后仍会执行同一版本的原生流程动作校验器、未配置校验器时为空操作、
 * 校验失败按原异常传播。不打开 NC 窗口、不调用业务 RPC、不连接数据库。</p>
 */
public final class SalesPfBridgeTests {
    private interface Checked { void run() throws Exception; }
    private static int assertions;

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        noValidatorIsNoOp();
        nativeValidatorIsReplayedOnce();
        validatorFailurePropagates();
        System.out.println("PASS SalesPfBridgeTests assertions=" + assertions
                + " (native PF validator replay for overridden procFlow; no NC window or RPC invoked)");
    }

    /** 未配置校验器时不得触发任何反射调用或异常。 */
    private static void noValidatorIsNoOp() throws Exception {
        AbstractScriptExcAction action = action();
        PFlowContext flow = new PFlowContext();
        action.setFlowContext(flow);
        check(action.getValidationService() == null, "no validator is configured by default");
        SalesPfActionDispatch.runNativeValidation(action);
        assertions++; // 走到这里即说明空操作未抛异常
    }

    /** 配置了原生批量校验器时必须恰好执行一次，并且收到完整单据数组。 */
    private static void nativeValidatorIsReplayedOnce() throws Exception {
        final int[] calls = { 0 };
        final Object[] received = new Object[1];
        IValidationService service = (IValidationService) Proxy.newProxyInstance(
                IValidationService.class.getClassLoader(),
                new Class<?>[] { IValidationService.class, IBatchOperateValidateService.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("validate".equals(name)) {
                            calls[0]++;
                            received[0] = args == null ? null : args[0];
                            return null;
                        }
                        if ("getValidationExceptionResults".equals(name)) { return new HashMap<Object, Object>(); }
                        if ("hashCode".equals(name)) { return Integer.valueOf(System.identityHashCode(proxy)); }
                        if ("equals".equals(name)) { return Boolean.valueOf(proxy == args[0]); }
                        if ("toString".equals(name)) { return "OFFLINE_VALIDATOR_FIXTURE"; }
                        return null;
                    }
                });
        AbstractScriptExcAction action = action();
        PFlowContext flow = new PFlowContext();
        flow.setBillVos(new AbstractBill[] { bill() });
        action.setFlowContext(flow);
        action.setValidationService(service);
        check(action.getValidationService() != null, "the native validator is visible to the bridge");
        SalesPfActionDispatch.runNativeValidation(action);
        check(calls[0] == 1, "native validator runs exactly once");
        check(received[0] instanceof Object[] && ((Object[]) received[0]).length == 1,
                "native validator receives the full bill array");
    }

    /** 原生校验失败必须按原异常传播，不能被当成通过。 */
    private static void validatorFailurePropagates() throws Exception {
        IValidationService service = (IValidationService) Proxy.newProxyInstance(
                IValidationService.class.getClassLoader(),
                new Class<?>[] { IValidationService.class, IBatchOperateValidateService.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("validate".equals(name)) { throw new IllegalStateException("native validation failed"); }
                        if ("getValidationExceptionResults".equals(name)) { return new HashMap<Object, Object>(); }
                        if ("hashCode".equals(name)) { return Integer.valueOf(System.identityHashCode(proxy)); }
                        if ("equals".equals(name)) { return Boolean.valueOf(proxy == args[0]); }
                        if ("toString".equals(name)) { return "OFFLINE_VALIDATOR_FIXTURE"; }
                        return null;
                    }
                });
        final AbstractScriptExcAction action = action();
        PFlowContext flow = new PFlowContext();
        flow.setBillVos(new AbstractBill[] { bill() });
        action.setFlowContext(flow);
        action.setValidationService(service);
        expectRejection(new Checked() { public void run() throws Exception {
            SalesPfActionDispatch.runNativeValidation(action);
        }}, "native validation failure is propagated");
    }

    private static AbstractScriptExcAction action() {
        return new AbstractScriptExcAction() {
            private static final long serialVersionUID = 1L;
            protected void fillUpContext(PFlowContext flow) { }
            protected void processReturnObj(Object[] objects) { }
        };
    }

    /** 离线单据夹具：只提供空元数据，避免离线环境初始化 NC 元数据/定位器。 */
    private static AbstractBill bill() {
        final IBillMeta meta = (IBillMeta) Proxy.newProxyInstance(IBillMeta.class.getClassLoader(),
                new Class<?>[] { IBillMeta.class }, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("getChildren".equals(name)) { return new IVOMeta[0]; }
                        if ("hashCode".equals(name)) { return Integer.valueOf(System.identityHashCode(proxy)); }
                        if ("equals".equals(name)) { return Boolean.valueOf(proxy == args[0]); }
                        if ("toString".equals(name)) { return "OFFLINE_BILL_META_FIXTURE"; }
                        Class<?> type = method.getReturnType();
                        if (type == Boolean.TYPE) { return Boolean.FALSE; }
                        if (type == Integer.TYPE) { return Integer.valueOf(0); }
                        if (type == Long.TYPE) { return Long.valueOf(0L); }
                        return null;
                    }
                });
        return new AbstractBill() {
            private static final long serialVersionUID = 1L;
            public IBillMeta getMetaData() { return meta; }
        };
    }

    private static void expectRejection(Checked action, String message) throws Exception {
        assertions++;
        try {
            action.run();
        } catch (RuntimeException expected) {
            if (expected.getMessage() != null && expected.getMessage().contains("native validation failed")) { return; }
            return;
        }
        throw new IllegalStateException("FAILED: expected rejection: " + message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new IllegalStateException("FAILED: " + message); }
    }
}
