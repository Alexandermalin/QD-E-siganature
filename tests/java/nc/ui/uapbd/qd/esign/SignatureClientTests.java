package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Action;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uapbd.qd.esign.SignatureResult;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.actions.ActionInterceptor;

/** 签名客户端控制器的离线测试，使用显式测试替身覆盖交接、重复点击、取消与原拦截链清理；不调用 NC 窗口、认证或数据库。 */
public final class SignatureClientTests {
    /** assertions：已执行的离线断言计数。 */
    private static int assertions;
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private SignatureClientTests() { }

    /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
    public static void run() throws Exception {
        testSignatureHandoffAndDuplicate();
        testCancellationAndFailedPassword();
        testRetryableSignatureResult();
        testUnsupportedAndBypass();
        testNativeExceptionPreserved();
        testMissingBusinessDispatch();
        testNativeBeforeShortCircuit();
        testNativeFailureShortCircuit();
        testNativeSuccessExceptionAndCleanupFailures();
        testMissingOuterGuard();
        testExplicitStateBridge();
        System.out.println("SignatureClientTests: " + assertions
                + " assertions passed (offline controller/test-double checks).");
    }

    private static void testRetryableSignatureResult() {
        check(ElectronicSignatureDialog.canRetry(new SignatureResult("req", "PASSWORD_INCORRECT", false, "PENDING", Integer.valueOf(4))),
                "wrong password keeps the current dialog open");
        check(!ElectronicSignatureDialog.canRetry(new SignatureResult("req", "PASSWORD_INCORRECT", false, "FAILED", Integer.valueOf(4))),
                "finished requests cannot be retried");
        check(!ElectronicSignatureDialog.canRetry(new SignatureResult("req", "PASSWORD_INCORRECT", false, "PENDING")),
                "unknown remaining count cannot be retried");
        check(!ElectronicSignatureDialog.canRetry(new SignatureResult("req", "ACCOUNT_LOCKED", true, "FAILED", Integer.valueOf(0))),
                "locked accounts cannot be retried");
    }

    /** Native BD state actions append interceptors; an isolated anchor must leave that chain untouched. */
    private static void testExplicitStateBridge() {
        final NCAction nativeAction=new NCAction() {
            private static final long serialVersionUID=1L;
            @Override public void setInterceptor(ActionInterceptor value) {
                if(getInterceptor()!=null) throw new AssertionError("Native additive interceptor was changed");
                super.setInterceptor(value);
            }
            public void doAction(ActionEvent event) { }
        };
        Original original=new Original();nativeAction.setInterceptor(original);
        final QdExplicitSignatureAction bridge=new QdExplicitSignatureAction();
        final ActionEvent event=new ActionEvent(nativeAction,ActionEvent.ACTION_PERFORMED,"Enable");
        FakeService service=new FakeService(true,true);Consumer consumer=new Consumer();Interceptor signing=new Interceptor();
        signing.setSignatureService(service);signing.setRequestConsumer(consumer);
        signing.setContextProvider(new OperationContextProvider() {
            public OperationContext createContext(Action action,ActionEvent current) {
                check(action==bridge && current==event,"explicit authorization remains bound to its native event");
                return draft();
            }
            public Container getParentContainer() { return null; }
            public String getCurrentUserDisplayName() { return "test"; }
        });
        SignatureLifecycleInterceptor lifecycle=bridge.install(signing);
        check(nativeAction.getInterceptor()==original,"explicit lifecycle does not replace or append to native chain");
        check(lifecycle.beforeDoAction(bridge,event),"explicit state bridge accepts a prepared signature");
        consumer.dispatched=true;lifecycle.afterDoActionSuccessed(bridge,event);
        check(consumer.bound==null && consumer.releases==1,"explicit state bridge releases the authorization");
        check(nativeAction.getInterceptor()==original,"native interceptor chain is unchanged after cleanup");
        try { bridge.install(signing);throw new AssertionError("Explicit bridge accepted a second installation"); }
        catch(IllegalStateException expected) { check(true,"explicit bridge rejects duplicate installation"); }
    }

    /** 命令行入口，执行本文件的离线检查或原有工程示例。 */
    public static void main(String[] args) throws Exception { run(); }

    /** 验证签名成功后授权交给最终 RPC，重复点击被拒绝且不会清理先前仍在执行的请求。 */
    private static void testSignatureHandoffAndDuplicate() {
        Harness h = new Harness(true, true);
        check(h.before(), "signature success permits adapted original action");
        check(h.consumer.bound != null && "entered reason".equals(h.consumer.bound.getReason()),
                "entered reason accompanies request handoff");
        check(h.service.prepares == 1 && h.interceptor.prompts == 1, "one preparation and prompt");
        check(!h.before(), "duplicate action blocked while original action owns binding");
        check(h.service.prepares == 1, "duplicate does not prepare another request");
        h.consumer.dispatched = true;
        h.success();
        check(h.consumer.releases == 1 && h.consumer.bound == null, "successful action clears binding");
        check(h.service.failureReports == 0, "UI success callback writes no business result");
        check(h.before(), "interceptor reusable after cleanup");
        h.consumer.dispatched = true;
        h.success();
    }

    /** 验证用户取消和密码错误均阻止原业务继续，并检查取消与绑定清理次数。 */
    private static void testCancellationAndFailedPassword() {
        Harness cancel = new Harness(true, true);
        cancel.interceptor.mode = "CANCEL";
        check(!cancel.before(), "cancel terminates original action");
        check(cancel.service.cancels == 1, "dialog and interceptor share one cancellation attempt");
        check(cancel.consumer.binds == 0, "cancel never hands off business request");
        Harness denied = new Harness(true, true);
        denied.interceptor.mode = "PASSWORD_INCORRECT";
        check(!denied.before(), "failed password terminates original action");
        check(denied.interceptor.prompts == 1 && denied.consumer.binds == 0,
                "failed confirmation cannot execute business handoff");
        check(denied.interceptor.getLastError() != null, "authentication failure retained for diagnostics");
        Harness cancelFailure = new Harness(true, true);
        cancelFailure.interceptor.mode = "CANCEL";
        cancelFailure.service.failCancel = true;
        check(!cancelFailure.before(), "cancellation network failure cannot allow original action");
        check(cancelFailure.service.cancels == 1 && cancelFailure.interceptor.getLastError() != null,
                "failed cancellation is retained without duplicate RPC");
    }

    /** 验证未实现交接的受控动作被拒绝，以及 BYPASS 路径仍保留原 NC 行为。 */
    private static void testUnsupportedAndBypass() {
        Harness unsupported = new Harness(true, true);
        unsupported.consumer.available = false;
        check(!unsupported.before(), "unadapted RPC is blocked");
        check(unsupported.interceptor.prompts == 0 && unsupported.consumer.binds == 0,
                "unsupported action cannot consume a password or receive authorization");
        check(unsupported.service.cancels == 1, "prepared unsupported request is cancelled");
        Harness bypass = new Harness(false, false);
        bypass.consumer.available = false;
        check(bypass.before(), "both switches off keeps original NC action");
        check(bypass.interceptor.prompts == 0 && bypass.consumer.binds == 0 && bypass.service.cancels == 0,
                "bypass makes no signature or business handoff");
        Harness guarded = new Harness(false, false);
        guarded.service.executionGuard = true;
        check(guarded.before(), "owner-controlled action can proceed with both switches off");
        check(guarded.interceptor.prompts == 0 && guarded.consumer.binds == 1,
                "owner verification still reaches the server gateway without a signature prompt");
        guarded.consumer.dispatched = true;
        guarded.success();
        Harness reasonOnly = new Harness(false, false);
        reasonOnly.service.independentReason = true;
        check(reasonOnly.before() && reasonOnly.interceptor.prompts == 1 && reasonOnly.consumer.binds == 1,
                "reason-only owner policy requests confirmation without bypassing the gateway");
        reasonOnly.consumer.dispatched = true;
        reasonOnly.success();
    }

    /** 验证原业务异常对象被保留，清理或失败上报不能替换原异常。 */
    private static void testNativeExceptionPreserved() {
        Harness h = new Harness(false, true);
        check(h.before() && h.interceptor.prompts == 0, "audit-only request requires no password prompt");
        h.service.failFailureReport = true;
        BusinessException original = new BusinessException("original NC exception");
        check(h.lifecycle.afterDoActionFailed(h.action, h.event, original),
                "original NC failure handler remains enabled");
        check(h.interceptor.getLastError() == original, "original exception identity retained");
        check(original.getSuppressed().length == 1, "failure-report error is observable");
        check(h.consumer.releases == 1 && h.consumer.bound == null, "failed action clears binding");
        check("BUSINESS_EXCEPTION".equals(h.service.lastErrorCode), "only server-approved error code crosses failure RPC");
    }

    /** 验证只绑定但未实际派发受控业务 RPC 时，上报缺少派发而非宣称业务成功。 */
    private static void testMissingBusinessDispatch() {
        Harness h = new Harness(true, true);
        check(h.before(), "verified consumer may bind");
        h.success();
        check(h.interceptor.getLastError() != null && h.service.failureReports == 1,
                "unconsumed request is explicit error, never a fabricated business success");
        check("DISPATCH_NOT_OBSERVED".equals(h.service.lastErrorCode), "server-approved missing-dispatch outcome reported");
        check(h.consumer.bound == null, "missing-dispatch binding cleared");
    }

    /** 构造具有稳定对象标识的签名草稿，供客户端控制器测试使用。 */
    private static OperationContext draft() {
        return new OperationContext(null, null, "group", "org", "node", "Node", "SAVE", "Save", "SAVE", null,
                Arrays.asList(new BusinessObjectRef("new-1", "TEST", null, null, null)));
    }

    /** 验证原生前置拦截器短路时依然完成外层签名清理。 */
    private static void testNativeBeforeShortCircuit() {
        Original original = new Original(); original.before = false;
        Harness h = new Harness(false, true, original);
        check(!h.before(), "original before=false decision preserved");
        check(h.consumer.binds == 1 && h.consumer.releases == 1 && h.service.cancels == 1,
                "outer cleans signing binding even when original before short-circuits the real NC composite");
        check(h.consumer.bound == null, "before short-circuit leaves no bound context");
        original.before = true;
        check(h.before(), "short-circuited interceptor can start a fresh operation");
        h.consumer.dispatched = true; h.success();
        Original throwing = new Original(); throwing.beforeError = new RuntimeException("native before error");
        Harness failure = new Harness(false, true, throwing);
        try { failure.before(); throw new AssertionError("Native before error was lost"); }
        catch (RuntimeException error) { check(error == throwing.beforeError, "original before exception identity preserved"); }
        check(failure.consumer.bound == null && failure.service.cancels == 1,
                "throwing before also cleans authorization");
    }

    /** 验证原生失败回调的处理决定原样返回，不能因短路遗漏清理。 */
    private static void testNativeFailureShortCircuit() {
        Original original = new Original(); original.afterFailure = false;
        Harness h = new Harness(false, true, original); check(h.before(), "failure-short-circuit request prepared");
        BusinessException error = new BusinessException("native handled failure");
        check(!h.lifecycle.afterDoActionFailed(h.action, h.event, error), "original handled=false result preserved exactly");
        check(h.service.failureReports == 0 && h.service.cancels == 1 && h.consumer.releases == 1,
                "outer cleanup runs when native composite skips signing failure callback");
        check(h.interceptor.getLastError() == error && h.consumer.bound == null, "native error retained after skipped callback");
    }

    /** 验证原生成功回调及清理失败的组合路径，原异常和附加异常均被保留。 */
    private static void testNativeSuccessExceptionAndCleanupFailures() {
        Original original = new Original(); original.successError = new RuntimeException("native success callback error");
        Harness h = new Harness(false, true, original); check(h.before(), "success-callback failure request prepared");
        long generation = h.interceptor.lifecycleGeneration();
        h.service.failCancel = true; h.consumer.failRelease = true;
        try { h.success(); throw new AssertionError("Native success callback error was lost"); }
        catch (RuntimeException error) { check(error == original.successError, "native success-callback exception preserved"); }
        check(original.successError.getSuppressed().length == 1
                && original.successError.getSuppressed()[0].getSuppressed().length == 1,
                "cancellation and release errors are attached to original exception");
        check(h.consumer.bound == null && h.service.cancels == 1 && h.consumer.releases == 1,
                "cleanup state cleared despite cancellation and release errors");
        h.interceptor.finishLifecycle(generation, h.action, h.event, original.successError);
        check(h.service.cancels == 1 && h.consumer.releases == 1, "repeated lifecycle completion is idempotent");
    }

    /** 验证缺少最终外层生命周期保护的受控动作无法执行签名交接。 */
    private static void testMissingOuterGuard() {
        Harness controlled = new Harness(true, true);
        controlled.action.setInterceptor(controlled.interceptor);
        check(!controlled.action.getInterceptor().beforeDoAction(controlled.action, controlled.event),
                "controlled actions refuse missing/replaced outer lifecycle");
        check(controlled.interceptor.prompts == 0 && controlled.consumer.binds == 0 && controlled.service.cancels == 1,
                "missing lifecycle cannot obtain credentials or hand off business work");
        Harness bypass = new Harness(false, false); bypass.action.setInterceptor(bypass.interceptor);
        check(bypass.action.getInterceptor().beforeDoAction(bypass.action, bypass.event), "BYPASS keeps original logic without lifecycle installation");
        SignatureDialogOutcome consumed = new SignatureDialogOutcome(new SignatureResult("req", "SUCCESS", false, "CANCELLED"), null, null);
        check(!consumed.isConfirmed(), "identity success on a consumed request cannot authorize a new operation");
        SignatureDialogOutcome reasonReady = new SignatureDialogOutcome(new SignatureResult("req", "NOT_REQUIRED", false, "READY"), "reason", null);
        check(reasonReady.isConfirmed() && !reasonReady.getSignatureResult().isSuccess(),
                "reason-only confirmation does not claim that a signature occurred");
        check(!new SignatureDialogOutcome(new SignatureResult("req", "NOT_REQUIRED", false, "PENDING"), null, null).isConfirmed(),
                "unconfirmed reason-only request cannot authorize business execution");
        nc.ui.uif2.actions.CompositeActionInterceptor incompatible = new nc.ui.uif2.actions.CompositeActionInterceptor();
        incompatible.add(controlled.interceptor); incompatible.add(new Original());
        controlled.action.setInterceptor(incompatible);
        try { SignatureLifecycleInstaller.install(controlled.action, controlled.interceptor); throw new AssertionError("Incompatible failure-composite semantics accepted"); }
        catch (IllegalStateException expected) { check(controlled.action.getInterceptor() == incompatible,
                "unsupported first-true failure composite is rejected without replacing the native chain"); }
    }

    /** 签名生命周期测试装配器，连接原动作、控制器、上下文提供器和交接替身，用于观察一次点击的完整执行顺序。 */
    private static final class Harness {
        /** service：测试装配使用的签名服务替身。 */
        final FakeService service;
        /** consumer：测试装配使用的交接替身。 */
        final Consumer consumer = new Consumer();
        /** interceptor：测试装配使用的签名控制器。 */
        final Interceptor interceptor = new Interceptor();
        /** action：动作实例。 */
        final NCAction action = new NCAction() {
            /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
            private static final long serialVersionUID = 1L;
            /** 空的测试动作占位，避免离线控制器检查触发真实 NC 业务。 */
            public void doAction(ActionEvent event) { }
        };
        /** lifecycle：测试装配使用的外层生命周期保护器。 */
        final SignatureLifecycleInterceptor lifecycle;
        /** event：测试动作的 SAVE 事件。 */
        final ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "SAVE");
        /** 装配本场景的签名服务、上下文和交接替身，按需组合原生拦截链，最后安装真实生命周期包装器。 */
        Harness(boolean sign, boolean audit) {
            this(sign, audit, null);
        }
        /** 装配本场景的签名服务、上下文和交接替身，按需组合原生拦截链，最后安装真实生命周期包装器。 */
        Harness(boolean sign, boolean audit, ActionInterceptor original) {
            service = new FakeService(sign, audit);
            interceptor.setSignatureService(service);
            interceptor.setRequestConsumer(consumer);
            interceptor.setContextProvider(new OperationContextProvider() {
                /** 返回固定测试草稿，只供本地控制器断言，不读取真实节点选择。 */
                public OperationContext createContext(Action action, ActionEvent event) { return draft(); }
                /** 测试中不打开窗口，因此父容器固定为空。 */
                public Container getParentContainer() { return null; }
                /** 返回固定的测试展示名，不作为认证身份。 */
                public String getCurrentUserDisplayName() { return "test display only"; }
            });
            if (original == null) { action.setInterceptor(interceptor); }
            else {
                nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor chain =
                        new nc.ui.pubapp.uif2app.actions.interceptor.CompositeActionInterceptor();
                chain.addInterceptor(original); chain.addInterceptor(interceptor); action.setInterceptor(chain);
            }
            lifecycle = SignatureLifecycleInstaller.install(action, interceptor);
        }
        /** 模拟经最终生命周期包装器进入一次动作前置调用。 */
        boolean before() { return lifecycle.beforeDoAction(action, event); }
        /** 模拟一次动作成功回调，检查外层生命周期清理及交接观察结果。 */
        void success() { lifecycle.afterDoActionSuccessed(action, event); }
    }

    /** 可控的签名窗口替身，按测试模式产生成功、失败或取消，并收集异常提示以便断言。 */
    private static final class Interceptor extends ElectronicSignatureActionInterceptor {
        /** mode：测试要求的签名结果模式。 */
        String mode = "SUCCESS";
        /** prompts：测试中打开确认窗口的次数。 */
        int prompts;
        /** notices：测试中收集的异常提示。 */
        final List<Throwable> notices = new ArrayList<Throwable>();
        /** 按测试模式直接构造签名窗口结果并记录次数，取消模式使用共用取消协调器，不打开真实窗口或认证密码。 */
        @Override
        protected SignatureDialogOutcome showSignatureDialog(SignaturePreparation prepared,
                RequestCancellation cancellation) {
            prompts++;
            if ("CANCEL".equals(mode)) {
                try { cancellation.cancel("cancel reason"); }
                catch (Exception error) { return new SignatureDialogOutcome(null, "cancel reason", error); }
                return new SignatureDialogOutcome(null, "cancel reason", null);
            }
            SignatureResult result = new SignatureResult(prepared.getContext().getRequestId(), mode, false,
                    "SUCCESS".equals(mode) ? "READY" : "FAILED");
            return new SignatureDialogOutcome(result, "entered reason", result.isSuccess() ? null
                    : new BusinessException("test credential rejected"));
        }
        /** 把异常加入测试观察列表，不调用 NC 消息窗口。 */
        @Override
        protected void reportFailure(Throwable error) { notices.add(error); }
    }

    /** 授权交接测试替身，记录绑定、实际派发和释放次数，可显式模拟适配器不可用及释放失败。 */
    private static final class Consumer implements AuthorizedRequestConsumer {
        /**
         * available：测试中适配交接是否可用。
         * dispatched：测试中最终业务 RPC 是否已实际派发。
         * failRelease：是否模拟释放绑定失败。
         */
        boolean available = true, dispatched, failRelease;
        /**
         * binds：测试中授权绑定的调用次数。
         * releases：测试中绑定释放的调用次数。
         */
        int binds, releases;
        /** bound：测试中保留的已授权业务上下文。 */
        OperationContext bound;
        /** 返回本场景设置的适配可用标记，不检测真实节点或 RPC。 */
        public boolean isAvailable(OperationContext context) { return available; }
        /** 累加绑定次数并保存测试上下文，返回预设可用标记，不发送任何业务调用。 */
        public boolean bind(OperationContext context) { binds++; bound = context; return available; }
        /** 返回测试设置的派发观察值，不检测真实远程调用。 */
        public boolean wasDispatched(String requestId) { return dispatched; }
        /** 累加释放次数并清除测试绑定；按开关抛出指定异常，供生命周期失败路径断言。 */
        public void release(String requestId) { releases++; bound = null; if (failRelease) { throw new IllegalStateException("test release failure"); } }
    }

    /** 原生拦截链的测试替身，可控制前置放行、失败处理结果和回调异常，以验证原行为被保留。 */
    private static final class Original implements ActionInterceptor {
        /**
         * before：原生前置拦截器测试替身的放行开关。
         * afterFailure：原生失败回调是否已处理异常的测试返回值。
         */
        boolean before = true, afterFailure = true;
        /**
         * beforeError：测试中前置回调抛出的异常。
         * successError：测试中成功回调抛出的异常。
         */
        RuntimeException beforeError, successError;
        /** 按测试设置抛出前置异常或返回放行开关，用于验证原链短路和异常保留。 */
        public boolean beforeDoAction(Action action, ActionEvent event) { if (beforeError != null) { throw beforeError; } return before; }
        /** 按测试设置抛出成功回调异常，供外层最终清理路径断言。 */
        public void afterDoActionSuccessed(Action action, ActionEvent event) { if (successError != null) { throw successError; } }
        /** 返回测试设定的异常处理决定，验证包装器不会改写原生布尔结果。 */
        public boolean afterDoActionFailed(Action action, ActionEvent event, Throwable error) { return afterFailure; }
    }

    /** 签名 RPC 测试替身，只记录调用及构造结果，不执行真实认证或持久化。 */
    private static final class FakeService implements ISignatureService {
        /**
         * sign：测试中是否要求签名。
         * audit：测试中是否启用审计。
         */
        final boolean sign, audit;
        /**
         * prepares：测试中请求准备的调用次数。
         * cancels：测试中取消 RPC 的调用次数。
         * failureReports：测试中业务失败上报的调用次数。
         */
        int prepares, cancels, failureReports;
        /**
         * failCancel：是否模拟取消 RPC 失败。
         * failFailureReport：是否模拟失败上报 RPC 失败。
         */
        boolean failCancel, failFailureReport;
        boolean executionGuard, independentReason;
        /** lastErrorCode：测试替身记录的最近一次失败上报码。 */
        String lastErrorCode;
        /** 保存本场景的签名及审计开关，用于后续构造准备响应，不读取真实策略。 */
        FakeService(boolean sign, boolean audit) { this.sign = sign; this.audit = audit; }
        /** 记录准备次数并按预设开关构造策略及请求编号，返回 PENDING 或 BYPASS，不访问真实策略或数据库。 */
        public SignaturePreparation prepare(OperationContext context) {
            prepares++;
            PolicyDecision policy = new PolicyDecision(true, sign, true, audit, true, 1L, 0L, independentReason);
            boolean needsRequest = policy.isControlled() || executionGuard;
            return new SignaturePreparation(needsRequest ? context.withRequest("req-" + prepares, "batch", "group") : context,
                    policy, needsRequest ? "PENDING" : "BYPASS", executionGuard);
        }
        /** 禁止离线控制器测试进入真实密码确认路径，调用即以 AssertionError 失败。 */
        public SignatureResult confirm(String id, char[] password, String reason) {
            throw new AssertionError("No real UI/password confirmation should run in these offline tests");
        }
        /** 记录取消次数，按开关模拟传输失败，不更新真实请求。 */
        public void cancel(String id, String reason) throws BusinessException {
            cancels++;
            if (failCancel) { throw new BusinessException("test cancellation transport failure"); }
        }
        /** 核对真实服务允许的失败码，记录次数和最近错误码，按开关模拟上报失败。 */
        public void reportBusinessFailure(String id, String code) throws BusinessException {
            if (!Arrays.asList("BUSINESS_EXCEPTION", "CLIENT_ADAPTER_FAILED", "DISPATCH_NOT_OBSERVED").contains(code)) {
                throw new AssertionError("Failure code outside real server whitelist: " + code);
            }
            failureReports++; lastErrorCode = code;
            if (failFailureReport) { throw new BusinessException("test failure-report transport failure"); }
        }
        /** 返回固定成功认证结果，供控制器测试使用，不查询真实身份或数据库。 */
        public SignatureResult getResult(String id) { return new SignatureResult(id, "SUCCESS", false); }
    }

    /** 累计断言并在条件不满足时抛出 AssertionError，使测试失败可被构建发现。 */
    private static void check(boolean value, String description) {
        assertions++;
        if (!value) { throw new AssertionError(description); }
    }
}
