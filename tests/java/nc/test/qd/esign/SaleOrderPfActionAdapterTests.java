package nc.test.qd.esign;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nc.bs.uapbd.qd.esign.audit.OrderPhysicalSnapshots;
import nc.impl.so.qd.esign.SaleOrderOperationAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.so.m30.entity.SaleOrderHVO;
import nc.vo.so.m30.entity.SaleOrderUserObject;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.qd.esign.SaleOrderPfActionCommand;
import nc.vo.so.qd.esign.SaleOrderPfActionResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/**
 * 销售订单 S3 PF 基础动作（删除/提交/收回）的离线契约检查。
 * 使用原生 PF 入口替身验证动作名、交易类型、流程载体与结果核对；不调用 NC、数据库或事务。
 */
public final class SaleOrderPfActionAdapterTests {
    private interface Checked { void run() throws Exception; }
    private static int assertions;
    private static final String G = SaleOrderPilotScope.GROUP_ID;
    private static final String O = SaleOrderPilotScope.ORG_ID;
    private static final String N = SaleOrderPilotScope.NODE_CODE;

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        enablement();
        commitSuccess();
        unCommitSuccess();
        approveSuccess();
        deleteConfirmed();
        returnIdentity();
        payloadGuards();
        nativeOutcomes();
        System.out.println("PASS SaleOrderPfActionAdapterTests assertions=" + assertions
                + " (PF branches, native parameters and result facts; no NC or database invoked)");
    }

    /** 启用与实现边界：删除/提交/收回服务端已就绪；删除的策略开启仍留待客户端确认顺序设计。 */
    private static void enablement() throws Exception {
        check(SaleOrderPilotScope.isImplemented(N, "Commit"), "commit is implemented");
        check(SaleOrderPilotScope.isImplemented(N, "UnCommit"), "uncommit is implemented");
        check(SaleOrderPilotScope.isImplemented(N, "Delete"), "delete is implemented after confirm-then-sign");
        check(SaleOrderPilotScope.isImplemented(N, "UnFreeze"), "unfreeze is implemented with ATP confirmation");
        check(SaleOrderPilotScope.isImplemented(N, "Approve"), "approve is implemented");
        check(SaleOrderPilotScope.isImplemented(N, "UnApprove"), "unapprove is implemented");
        check(SaleOrderPilotScope.implementedButtons().size() == 9, "nine buttons are implemented");
        for (String button : new String[] { "Delete", "Commit", "UnCommit", "Approve", "UnApprove", "Freeze", "UnFreeze", "BillClose" }) {
            check(route(button).isEnabled(), "server route is ready: " + button);
        }

        check(OrderPhysicalSnapshots.forAdapter("nc.itf.so.qd.esign.ISaleOrderOperationAdapter", "DELETE")
                == OrderPhysicalSnapshots.SALE, "delete audit uses the sale aggregate");
        check(OrderPhysicalSnapshots.forAdapter("nc.itf.so.qd.esign.ISaleOrderOperationAdapter", "UPDATE")
                == OrderPhysicalSnapshots.SALE, "commit/uncommit audit uses the sale aggregate");
    }

    /** 提交：只调用一次原生 PF SAVE，交易类型、流程说明与用户对象原样传递，结果回填真实单号。 */
    private static void commitSuccess() throws Exception {
        Stub stub = new Stub("30");
        stub.returned = new SaleOrderVO[] { order("SO-1", G, O, "SO-CODE") };
        SaleOrderOperationAdapter adapter = stub.adapter();
        OperationContext context = context("Commit", "UPDATE", "SO-1");
        SaleOrderPfActionCommand command = command("SO-1", "30", user(), null, params());
        adapter.validate(context, command);
        Serializable result = adapter.execute(context, command);
        check(stub.calls == 1 && "SAVE".equals(stub.action) && "30".equals(stub.billType),
                "commit invokes native PF SAVE exactly once");
        check(stub.lastBill != null && "SO-1".equals(stub.lastBill.getPrimaryKey()), "commit passes the authorized bill");
        check(stub.lastUser instanceof PfUserObject
                && ((PfUserObject) stub.lastUser).getUserObject() instanceof SaleOrderUserObject,
                "commit passes the native sales user object");
        check(stub.lastParameters != null && "notechecked".equals(stub.lastParameters.get("notechecked")),
                "commit passes the whitelisted PF parameters");
        check(result instanceof SaleOrderPfActionResult && !((SaleOrderPfActionResult) result).isDeleted(),
                "commit result is a known non-delete outcome");
        check("SO-CODE".equals(((SaleOrderPfActionResult) result).getBillCode()), "commit uses the persisted bill code");
        check("SO-1".equals(adapter.describeResult(context, result).get(0).getObjectPk()),
                "commit result maps the persisted order");
    }

    /** 收回：只调用一次原生 PF UNSAVE。 */
    private static void unCommitSuccess() throws Exception {
        Stub stub = new Stub("30");
        stub.returned = new SaleOrderVO[] { order("SO-1", G, O, "SO-CODE") };
        SaleOrderOperationAdapter adapter = stub.adapter();
        Serializable result = adapter.execute(context("UnCommit", "UPDATE", "SO-1"),
                command("SO-1", "30", user(), null, params()));
        check(stub.calls == 1 && "UNSAVE".equals(stub.action), "uncommit invokes native PF UNSAVE exactly once");
        check(result instanceof SaleOrderPfActionResult, "uncommit returns a typed outcome");
    }

    /** 删除：以持久化事实确认删除，不以成功回调代替；逻辑删除也视为已失效。 */
    private static void deleteConfirmed() throws Exception {
        Stub stub = new Stub(null);
        stub.deletedHeader = header("SO-1", G, O, "SO-CODE", 1);
        SaleOrderOperationAdapter adapter = stub.adapter();
        Serializable result = adapter.execute(context("Delete", "DELETE", "SO-1"),
                command("SO-1", "30", user(), null, params()));
        check(stub.calls == 1 && "DELETE".equals(stub.action), "delete invokes native PF DELETE exactly once");
        check(result instanceof SaleOrderPfActionResult && ((SaleOrderPfActionResult) result).isDeleted(),
                "logically deleted order is reported as deleted");
        check("SO-1".equals(adapter.describeResult(context("Delete", "DELETE", "SO-1"), result).get(0).getObjectPk()),
                "delete result maps the authorized order");
        Stub physical = new Stub(null);
        Serializable second = physical.adapter().execute(context("Delete", "DELETE", "SO-1"),
                command("SO-1", "30", user(), null, params()));
        check(second instanceof SaleOrderPfActionResult && ((SaleOrderPfActionResult) second).isDeleted(),
                "missing row is reported as deleted");
        final Stub active = new Stub(null);
        active.deletedHeader = header("SO-1", G, O, "SO-CODE", 0);
        expectFailure(new Checked() { public void run() throws Exception {
            active.adapter().execute(context("Delete", "DELETE", "SO-1"),
                    command("SO-1", "30", user(), null, params()));
        }});
        check(active.calls == 1, "active row after delete is still invoked once and then rejected");
    }

    /** 审批/取消审批：只调用一次原生 PF APPROVE/UNAPPROVE，流程说明与用户对象原样传递。 */
    private static void approveSuccess() throws Exception {
        Stub approve = new Stub("30");
        approve.returned = new SaleOrderVO[] { order("SO-1", G, O, "SO-CODE") };
        Serializable approved = approve.adapter().execute(context("Approve", "UPDATE", "SO-1"),
                command("SO-1", "30", user(), null, params()));
        check(approve.calls == 1 && "APPROVE".equals(approve.action), "approve invokes native PF APPROVE exactly once");
        check(approved instanceof SaleOrderPfActionResult, "approve returns a typed outcome");
        Stub unapprove = new Stub("30");
        unapprove.returned = new SaleOrderVO[] { order("SO-1", G, O, "SO-CODE") };
        Serializable unapproved = unapprove.adapter().execute(context("UnApprove", "UPDATE", "SO-1"),
                command("SO-1", "30", user(), null, params()));
        check(unapprove.calls == 1 && "UNAPPROVE".equals(unapprove.action), "unapprove invokes native PF UNAPPROVE exactly once");
        check(unapproved instanceof SaleOrderPfActionResult, "unapprove returns a typed outcome");
        check(OrderPhysicalSnapshots.forAdapter("nc.itf.so.qd.esign.ISaleOrderOperationAdapter", "UPDATE")
                == OrderPhysicalSnapshots.SALE, "approval audit uses the sale aggregate");
    }

    /** 原生返回的单据身份不一致时不得当成成功。 */
    private static void returnIdentity() throws Exception {
        final Stub stub = new Stub("30");
        stub.returned = new SaleOrderVO[] { order("SO-OTHER", G, O, "SO-OTHER") };
        expectFailure(new Checked() { public void run() throws Exception {
            stub.adapter().execute(context("Commit", "UPDATE", "SO-1"),
                    command("SO-1", "30", user(), null, params()));
        }});
        check(stub.calls == 1, "mismatched native return still invoked PF once");
    }

    /** 类型化命令与原生参数白名单：不接受任意流程载荷。 */
    private static void payloadGuards() throws Exception {
        final SaleOrderOperationAdapter adapter = new Stub("30").adapter();
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "SO-1"), null);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "SO-1"),
                    command("SO-1", "30-01", user(), null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "SO-1"),
                    command("SO-2", "30", user(), null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "SO-1"),
                    command("SO-1", "30", null, null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            PfUserObject pf = user();
            pf.setBusinessCheckMap(Collections.singletonMap("skip", Boolean.TRUE));
            adapter.validate(context("Commit", "UPDATE", "SO-1"), command("SO-1", "30", pf, null, params()));
        }});
        final Map<String, Object> unsafe = new HashMap<String, Object>();
        unsafe.put("compiler", "任意脚本");
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "SO-1"), command("SO-1", "30", user(), null, unsafe));
        }});
        final Map<String, Object> wrongType = new HashMap<String, Object>();
        wrongType.put("silently", Integer.valueOf(1));
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "SO-1"), command("SO-1", "30", user(), null, wrongType));
        }});

    }

    /** 原生拒绝原样传播且不重试。 */
    private static void nativeOutcomes() throws Exception {
        final Stub rejected = new Stub("30");
        rejected.failure = new BusinessException("native pf rejected");
        expectFailureMessage(new Checked() { public void run() throws Exception {
            rejected.adapter().execute(context("Commit", "UPDATE", "SO-1"),
                    command("SO-1", "30", user(), null, params()));
        }}, "native pf rejected");
        check(rejected.calls == 1, "native PF rejection is not retried");
        final Stub missing = new Stub("30");
        missing.persisted = null;
        missing.noHeader = true;
        expectFailure(new Checked() { public void run() throws Exception {
            missing.adapter().execute(context("Commit", "UPDATE", "SO-1"),
                    command("SO-1", "30", user(), null, params()));
        }});
    }

    /** PF 入口替身：记录动作、交易类型、流程载体与参数，可注入原生异常与持久化事实。 */
    private static final class Stub {
        Object returned;
        SaleOrderHVO persisted;
        SaleOrderHVO deletedHeader;
        boolean noHeader;
        BusinessException failure;
        int calls;
        String action, billType;
        SaleOrderVO lastBill;
        Object lastUser;
        Map<String, Object> lastParameters;
        private final String type;
        Stub(String type) { this.type = type; }
        SaleOrderOperationAdapter adapter() {
            return new SaleOrderOperationAdapter() {
                @Override protected IplatFormEntry platformEntry() {
                    return (IplatFormEntry) Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                            new Class<?>[] { IplatFormEntry.class }, new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if (!"processAction".equals(method.getName())) {
                                throw new AssertionError(method.getName());
                            }
                            calls++;
                            action = (String) args[0];
                            billType = (String) args[1];
                            lastBill = (SaleOrderVO) args[3];
                            lastUser = args[4];
                            lastParameters = (Map<String, Object>) args[5];
                            if (failure != null) { throw failure; }
                            return returned;
                        }
                    });
                }
                @Override protected SaleOrderHVO loadHeader(String key) {
                    if ("DELETE".equals(action)) { return deletedHeader; }
                    if (persisted != null) { return persisted; }
                    if (noHeader) { return null; }
                    return header(key, G, O, "SO-CODE", 0);
                }
            };
        }
    }

    private static Map<String, Object> params() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("notechecked", "notechecked");
        return map;
    }

    private static PfUserObject user() {
        PfUserObject pf = new PfUserObject();
        SaleOrderUserObject sales = new SaleOrderUserObject();
        sales.setIsclientsave(true);
        pf.setUserObject(sales);
        return pf;
    }

    private static SaleOrderPfActionCommand command(String pk, String billType, PfUserObject user,
            Object note, Map<String, Object> params) {
        return new SaleOrderPfActionCommand(billType,
                new SaleOrderVO[] { order(pk, G, O, "SO-CODE") }, user, null, params);
    }

    private static nc.impl.so.qd.esign.SaleOrderActionRoute route(String button) throws Exception {
        return nc.impl.so.qd.esign.SaleOrderActionRoute.forContext(context(button,
                SaleOrderPilotScope.operationTypeFor(N, button), "SO-X"));
    }

    private static OperationContext context(String button, String operation, String pk) {
        return new OperationContext("R", "B", G, O, N, "销售订单维护", button,
                SaleOrderPilotScope.buttonName(button), operation, null,
                Collections.singletonList(new BusinessObjectRef("draft", "SO_SALE_ORDER", pk, "SO-CODE", null)));
    }

    private static SaleOrderVO order(final String pk, final String group, final String org, final String code) {
        return new SaleOrderVO() {
            public IBillMeta getMetaData() { return FIXTURE_META; }
            public SaleOrderHVO getParentVO() { return header(pk, group, org, code, 0); }
            public String getPrimaryKey() { return pk; }
        };
    }

    private static SaleOrderHVO header(final String pk, final String group, final String org,
            final String code, final int dr) {
        SaleOrderHVO head = new SaleOrderHVO() {
            public String getPk_group() { return group; }
            public String getPk_org() { return org; }
            public String getVbillcode() { return code; }
            public String getVtrantypecode() { return "30"; }
            public String getPrimaryKey() { return pk; }
            public Integer getDr() { return Integer.valueOf(dr); }
            public Integer getFstatusflag() { return Integer.valueOf(3); }
        };
        head.setStatus(2);
        return head;
    }

    private static final IBillMeta FIXTURE_META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "OFFLINE_SALE_PF_FIXTURE"; }
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
        throw new AssertionError("invalid sales PF request was accepted");
    }

    private static void check(boolean ok, String message) {
        if (!ok) { throw new AssertionError(message); }
        assertions++;
    }
}
