package nc.test.qd.esign;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.audit.OrderPhysicalSnapshots;
import nc.impl.so.qd.esign.SaleOrderActionRoute;
import nc.impl.so.qd.esign.SaleOrderOperationAdapter;
import nc.itf.so.m30.closemanage.ISaleOrderCloseManageMaintain;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.scmpub.res.BusinessCheck;
import nc.vo.so.m30.entity.SaleOrderBVO;
import nc.vo.so.m30.entity.SaleOrderHVO;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.qd.esign.SaleOrderStateCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/**
 * 销售订单 S2 直接服务（冻结/解冻/整单关闭）的离线契约检查。
 * 只验证服务端固定分支、参数白名单、结果身份核对与审计识别；不调用 NC、数据库或事务。
 */
public final class SaleOrderStateAdapterTests {
    private interface Checked { void run() throws Exception; }
    private static int assertions;
    private static final String G = SaleOrderPilotScope.GROUP_ID;
    private static final String O = SaleOrderPilotScope.ORG_ID;
    private static final String N = SaleOrderPilotScope.NODE_CODE;

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        auditRecognition();
        enablement();
        freezeSuccess();
        closeSuccess();
        unfreezeSuccess();
        parameterWhitelist();
        identityAndOrganization();
        nativeOutcomes();
        System.out.println("PASS SaleOrderStateAdapterTests assertions=" + assertions
                + " (direct-service branches, parameter whitelist and identity checks; no NC or database invoked)");
    }

    /** 审计识别必须覆盖销售适配器的 UPDATE 快照，否则直接服务动作不会被差异审计覆盖。 */
    private static void auditRecognition() {
        check(OrderPhysicalSnapshots.forAdapter("nc.itf.so.qd.esign.ISaleOrderOperationAdapter", "UPDATE")
                == OrderPhysicalSnapshots.SALE, "sales direct-service update uses the sale snapshot");
        check(OrderPhysicalSnapshots.forAdapter("nc.itf.so.qd.esign.ISaleOrderOperationAdapter", "DELETE")
                == OrderPhysicalSnapshots.SALE, "sales delete keeps the sale snapshot");
        check(OrderPhysicalSnapshots.forAdapter("nc.itf.so.qd.esign.ISaleOrderOperationAdapter", "INSERT")
                == OrderPhysicalSnapshots.SALE, "sales insert keeps the sale snapshot");
        // 现场已部署的审计识别以 ISaleOrderOperationAdapter 为准；PF 保存适配器的识别属主线增强，待部署后再断言。
        check(OrderPhysicalSnapshots.forAdapter("nc.itf.so.qd.esign.ISaleOrderOperationAdapter", "APPROVE") == null,
                "unknown operation is not audited as a sale aggregate");
    }

    /** 已接入与未接入按钮的启用边界；解冻因 ATP 恢复协议待联调确认保持关闭。 */
    private static void enablement() throws Exception {
        check(SaleOrderPilotScope.isImplemented(N, "Save"), "save pilot is implemented");
        check(SaleOrderPilotScope.isImplemented(N, "Freeze"), "freeze is implemented");
        check(SaleOrderPilotScope.isImplemented(N, "BillClose"), "bill close is implemented");
        check(SaleOrderPilotScope.isImplemented(N, "Commit"), "commit is implemented");
        check(SaleOrderPilotScope.isImplemented(N, "UnCommit"), "uncommit is implemented");
        check(SaleOrderPilotScope.isImplemented(N, "UnFreeze"), "unfreeze is implemented with ATP confirmation");
        check(SaleOrderPilotScope.isImplemented(N, "Delete"), "delete is implemented after confirm-then-sign");
        check(SaleOrderPilotScope.isImplemented(N, "Approve"), "approve is implemented");
        check(SaleOrderPilotScope.isImplemented(N, "UnApprove"), "unapprove is implemented");
        check(SaleOrderPilotScope.implementedButtons().size() == 9, "nine buttons are implemented");
        check("Freeze".equals(SaleOrderPilotScope.buttonForScope("QD-ES-C7E51B3ED69F8B05")),
                "freeze scope maps back to its button");
        check(SaleOrderPilotScope.buttonForScope("QD-ES-51BB76212FFCD156").equals("UnApprove"),
                "unapprove scope maps back to its button");
        check(route("Freeze").isEnabled() && route("BillClose").isEnabled(), "freeze and close routes are enabled");
        check(route("Delete").isEnabled() && route("Commit").isEnabled() && route("UnCommit").isEnabled(),
                "delete/commit/uncommit server routes are ready");
        check(route("Approve").isEnabled() && route("UnApprove").isEnabled(), "approval routes are ready");
        check(route("UnFreeze").isEnabled(), "unfreeze route is ready");
    }

    /** 冻结成功：原生直接服务只调用一次，返回身份与授权对象一致并可用于结果回填。 */
    private static void freezeSuccess() throws Exception {
        Stub stub = new Stub();
        stub.returned = new SaleOrderVO[] { order("SO-1", G, O, "SO-CODE", lines("取消订单")) };
        SaleOrderOperationAdapter adapter = stub.adapter();
        OperationContext context = context("Freeze", "UPDATE", O, "SO-1");
        SaleOrderStateCommand command = state("SO-1", O, null, null, false);
        adapter.validate(context, command);
        Serializable result = adapter.execute(context, command);
        check(stub.freezeCalls == 1 && stub.closeCalls == 0 && stub.unFreezeCalls == 0,
                "freeze invokes only freezeSaleOrder once");
        check(stub.lastBills != null && stub.lastBills.length == 1
                && "SO-1".equals(stub.lastBills[0].getPrimaryKey()), "freeze passes the authorized order");
        List<BusinessObjectRef> refs = adapter.describeResult(context, result);
        check(refs.size() == 1 && "SO-1".equals(refs.get(0).getObjectPk())
                && "SO-CODE".equals(refs.get(0).getBillCode()), "freeze result maps the persisted order");
    }

    /** 整单关闭成功：原因逐行一致、返回整单行存在、身份核对通过。 */
    private static void closeSuccess() throws Exception {
        Stub stub = new Stub();
        stub.returned = new SaleOrderVO[] { order("SO-1", G, O, "SO-CODE", lines("客户取消")) };
        SaleOrderOperationAdapter adapter = stub.adapter();
        OperationContext context = context("BillClose", "UPDATE", O, "SO-1");
        SaleOrderStateCommand command = state("SO-1", O, "客户取消", null, true);
        adapter.validate(context, command);
        Serializable result = adapter.execute(context, command);
        check(stub.closeCalls == 1 && stub.freezeCalls == 0 && stub.unFreezeCalls == 0,
                "bill close invokes only closeSaleOrder once");
        check("客户取消".equals(stub.lastBills[0].getChildrenVO()[0].getVclosereason()),
                "close reason is carried on the native lines, not merged with the signature reason");
        List<BusinessObjectRef> refs = adapter.describeResult(context, result);
        check(refs.size() == 1 && "SO-1".equals(refs.get(0).getObjectPk()), "close result maps the persisted order");
    }

    /** 解冻成功：原生直接服务只调用一次，空检查参数不跳过 ATP。 */
    private static void unfreezeSuccess() throws Exception {
        Stub stub = new Stub();
        stub.returned = new SaleOrderVO[] { order("SO-1", G, O, "SO-CODE", lines("原因A")) };
        SaleOrderOperationAdapter adapter = stub.adapter();
        OperationContext context = context("UnFreeze", "UPDATE", O, "SO-1");
        adapter.validate(context, state("SO-1", O, null, null, false));
        Serializable result = adapter.execute(context, state("SO-1", O, null, null, false));
        check(stub.unFreezeCalls == 1 && stub.freezeCalls == 0 && stub.closeCalls == 0,
                "unfreeze invokes only unFreezeSaleOrder once");
        check(stub.lastChecks != null && stub.lastChecks.isEmpty(),
                "unfreeze must not skip the native ATP check by default");
        check(result instanceof SaleOrderVO[] && "SO-1".equals(((SaleOrderVO[]) result)[0].getPrimaryKey()),
                "unfreeze returns the native order identity");
        Stub continueStub = new Stub();
        continueStub.returned = new SaleOrderVO[] { order("SO-1", G, O, "SO-CODE", lines("原因A")) };
        continueStub.adapter().execute(context, state("SO-1", O, null, atpContinue(), false));
        check(continueStub.lastChecks != null && Boolean.FALSE.equals(
                continueStub.lastChecks.get(BusinessCheck.ATPCheck.getCheckCode())),
                "confirmed ATP continuation is passed explicitly to the native service");
        final Stub atp = new Stub();
        atp.failure = new AtpResumeException();
        expectFailureMessage(new Checked() { public void run() throws Exception {
            atp.adapter().execute(context("UnFreeze", "UPDATE", O, "SO-1"), state("SO-1", O, null, null, false));
        }}, "QD_ESIGN_ATP_CONFIRM");
        check(atp.unFreezeCalls == 1, "ATP warning is not retried by the server");
    }

    /** 原生 ATP 可恢复异常替身：受控网关以固定前缀标记，客户端据此询问继续/取消。 */
    private static final class AtpResumeException extends nc.vo.pub.BusinessException
            implements nc.itf.pubapp.pub.exception.IResumeException {
        private static final long serialVersionUID = 1L;
        AtpResumeException() { super("ATP 可用量不足"); }
        public String getBusiExceptionType() {
            return BusinessCheck.ATPCheck.getCheckCode();
        }
    }
    /** 动作参数白名单：关闭原因、整单行、ATP 继续标记与跳过检查都不能被随意替换。 */
    private static void parameterWhitelist() throws Exception {
        final SaleOrderOperationAdapter adapter = new Stub().adapter();
        // 关闭必须带原因，且原因必须已逐行写入。
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("BillClose", "UPDATE", O, "SO-1"), state("SO-1", O, null, null, true));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("BillClose", "UPDATE", O, "SO-1"), state("SO-1", O, "  ", null, true));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("BillClose", "UPDATE", O, "SO-1"),
                    new SaleOrderStateCommand(new SaleOrderVO[] { order("SO-1", G, O, "SO-CODE", lines("原因B")) },
                            "原因A", null));
        }});
        // 冻结不接受关闭原因或业务检查参数。
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Freeze", "UPDATE", O, "SO-1"), state("SO-1", O, "原因A", null, false));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Freeze", "UPDATE", O, "SO-1"), state("SO-1", O, null, atpContinue(), false));
        }});
        // 解冻只接受 ATP 继续标记（false），不接受任意键或 true。
        final Map<String, Boolean> arbitrary = new HashMap<String, Boolean>();
        arbitrary.put("skipAll", Boolean.FALSE);
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("UnFreeze", "UPDATE", O, "SO-1"), state("SO-1", O, null, arbitrary, false));
        }});
        final Map<String, Boolean> wrongValue = new HashMap<String, Boolean>();
        wrongValue.put(BusinessCheck.ATPCheck.getCheckCode(), Boolean.TRUE);
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("UnFreeze", "UPDATE", O, "SO-1"), state("SO-1", O, null, wrongValue, false));
        }});
        // 非状态动作不能把状态命令当作脚本命令提交。
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Delete", "DELETE", O, "SO-1"), state("SO-1", O, null, null, false));
        }});
    }

    /** 订单主键、组织与动作域必须与授权上下文一致。 */
    private static void identityAndOrganization() throws Exception {
        final SaleOrderOperationAdapter adapter = new Stub().adapter();
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Freeze", "UPDATE", O, "SO-1"), state("SO-2", O, null, null, false));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Freeze", "UPDATE", O, "SO-1"), state("SO-1", "OTHER", null, null, false));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Freeze", "INSERT", O, "SO-1"), state("SO-1", O, null, null, false));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Freeze", "UPDATE", O, "SO-1"), null);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Freeze", "UPDATE", O, "SO-1"),
                    new SaleOrderStateCommand(new SaleOrderVO[] { order("SO-1", G, O, "SO-CODE", lines("原因A")),
                            order("SO-2", G, O, "SO-CODE2", lines("原因A")) }, null, null));
        }});
    }

    /** 原生拒绝与返回不一致时不得当成成功；原生调用次数保持一次，不自动重试。 */
    private static void nativeOutcomes() throws Exception {
        final Stub rejected = new Stub();
        rejected.failure = new BusinessException("native rejected");
        expectFailureMessage(new Checked() { public void run() throws Exception {
            rejected.adapter().execute(context("Freeze", "UPDATE", O, "SO-1"), state("SO-1", O, null, null, false));
        }}, "native rejected");
        check(rejected.freezeCalls == 1, "native rejection is not retried");
        final Stub mismatched = new Stub();
        mismatched.returned = new SaleOrderVO[] { order("SO-2", G, O, "OTHER", lines("原因A")) };
        expectFailure(new Checked() { public void run() throws Exception {
            mismatched.adapter().execute(context("Freeze", "UPDATE", O, "SO-1"), state("SO-1", O, null, null, false));
        }});
        check(mismatched.freezeCalls == 1, "mismatched native result still invoked the service once");
        final Stub noLines = new Stub();
        noLines.returned = new SaleOrderVO[] { order("SO-1", G, O, "SO-CODE", null) };
        expectFailure(new Checked() { public void run() throws Exception {
            noLines.adapter().execute(context("BillClose", "UPDATE", O, "SO-1"), state("SO-1", O, "原因A", null, true));
        }});
        final Stub empty = new Stub();
        empty.returned = new SaleOrderVO[0];
        expectFailure(new Checked() { public void run() throws Exception {
            empty.adapter().execute(context("Freeze", "UPDATE", O, "SO-1"), state("SO-1", O, null, null, false));
        }});
    }

    /** 直接服务桩：记录调用次数、参数与返回，可注入原生异常。 */
    private static final class Stub {
        SaleOrderVO[] returned;
        BusinessException failure;
        int freezeCalls, unFreezeCalls, closeCalls;
        SaleOrderVO[] lastBills;
        Map<String, Boolean> lastChecks;
        private final ISaleOrderCloseManageMaintain service = (ISaleOrderCloseManageMaintain) Proxy.newProxyInstance(
                ISaleOrderCloseManageMaintain.class.getClassLoader(),
                new Class<?>[] { ISaleOrderCloseManageMaintain.class }, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ("freezeSaleOrder".equals(name)) {
                    freezeCalls++; lastBills = (SaleOrderVO[]) args[0];
                    if (failure != null) { throw failure; }
                    return returned;
                }
                if ("unFreezeSaleOrder".equals(name)) {
                    unFreezeCalls++; lastBills = (SaleOrderVO[]) args[0];
                    lastChecks = (Map<String, Boolean>) args[1];
                    if (failure != null) { throw failure; }
                    return returned;
                }
                if ("closeSaleOrder".equals(name)) {
                    closeCalls++; lastBills = (SaleOrderVO[]) args[0];
                    if (failure != null) { throw failure; }
                    return returned;
                }
                if ("hashCode".equals(name)) { return Integer.valueOf(System.identityHashCode(proxy)); }
                if ("equals".equals(name)) { return Boolean.valueOf(proxy == args[0]); }
                if ("toString".equals(name)) { return "OFFLINE_CLOSE_MANAGE_FIXTURE"; }
                return null;
            }
        });
        SaleOrderOperationAdapter adapter() {
            return new SaleOrderOperationAdapter() {
                @Override protected ISaleOrderCloseManageMaintain closeManage() { return service; }
            };
        }
    }

    private static SaleOrderActionRoute route(String button) throws Exception {
        return SaleOrderActionRoute.forContext(context(button,
                SaleOrderPilotScope.operationTypeFor(N, button), O, "SO-" + button));
    }

    private static OperationContext context(String button, String operation, String org, String pk) {
        return new OperationContext("R", "B", G, org, N, "销售订单维护", button,
                SaleOrderPilotScope.buttonName(button), operation, null,
                Collections.singletonList(new BusinessObjectRef("draft", "SO_SALE_ORDER", pk, "SO-CODE", null)));
    }

    private static SaleOrderStateCommand state(String pk, String org, String reason,
            Map<String, Boolean> checks, boolean withLines) {
        return new SaleOrderStateCommand(new SaleOrderVO[] {
                order(pk, G, org, "SO-CODE", withLines ? lines(reason == null ? "原因A" : reason) : null) },
                reason, checks);
    }

    private static Map<String, Boolean> atpContinue() {
        Map<String, Boolean> map = new HashMap<String, Boolean>();
        map.put(BusinessCheck.ATPCheck.getCheckCode(), Boolean.FALSE);
        return map;
    }

    private static SaleOrderBVO[] lines(final String reason) {
        SaleOrderBVO line = new SaleOrderBVO() {
            public String getVclosereason() { return reason; }
        };
        return new SaleOrderBVO[] { line };
    }

    private static SaleOrderVO order(final String pk, final String group, final String org,
            final String code, final SaleOrderBVO[] children) {
        return new SaleOrderVO() {
            public IBillMeta getMetaData() { return FIXTURE_META; }
            public SaleOrderHVO getParentVO() { return header(pk, group, org, code); }
            public String getPrimaryKey() { return pk; }
            public SaleOrderBVO[] getChildrenVO() { return children; }
        };
    }

    private static SaleOrderHVO header(final String pk, final String group, final String org, final String code) {
        return new SaleOrderHVO() {
            public String getPk_group() { return group; }
            public String getPk_org() { return org; }
            public String getVbillcode() { return code; }
            public String getPrimaryKey() { return pk; }
        };
    }

    private static final IBillMeta FIXTURE_META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "OFFLINE_SALE_STATE_FIXTURE"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    private static void expectFailure(Checked action) throws Exception {
        expectFailureMessage(action, null);
    }

    private static void expectFailureMessage(Checked action, String fragment) throws Exception {
        try {
            action.run();
        } catch (BusinessException error) {
            if (fragment != null && (error.getMessage() == null || error.getMessage().indexOf(fragment) < 0)) {
                throw new AssertionError("unexpected rejection message: " + error.getMessage());
            }
            assertions++;
            return;
        }
        throw new AssertionError("invalid sales direct-service request was accepted");
    }

    private static void check(boolean ok, String message) {
        if (!ok) { throw new AssertionError(message); }
        assertions++;
    }
}
