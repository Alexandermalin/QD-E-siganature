package nc.test.qd.esign;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nc.impl.so.qd.esign.SaleInvoiceActionRoute;
import nc.impl.so.qd.esign.SaleInvoiceOperationAdapter;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.so.m32.entity.SaleInvoiceHVO;
import nc.vo.so.m32.entity.SaleInvoiceVO;
import nc.vo.so.m32.entity.SaleinvoiceUserObject;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;
import nc.vo.uapbd.qd.esign.SalesPfActionCommand;
import nc.vo.uapbd.qd.esign.SalesPfActionResult;

/**
 * 销售发票维护（节点 40060501，第 239–244 行）服务端固定路由与 PF 适配器的离线契约检查。
 *
 * <p>用原生 PF 入口替身核对动作名、交易类型、用户对象、流程说明、参数白名单与结果核对；
 * 不连接 NC、不访问数据库、不启动事务，也不证明真实会话下的审批窗口或跨模块回写。</p>
 */
public final class SaleInvoiceAdapterTests {
    private interface Checked { void run() throws Exception; }
    private static int assertions;
    private static final String G = SalesOtherNodeScope.GROUP_ID;
    private static final String O = "0001A9100000000009NY";
    private static final String N = SalesOtherNodeScope.INVOICE_NODE;

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        buttonMapping();
        enablementBoundary();
        contextGuards();
        saveSuccess();
        commitSuccess();
        unCommitSuccess();
        approveSuccess();
        unApproveSuccess();
        deleteStaysClosed();
        deleteFacts();
        payloadGuards();
        nativeOutcomes();
        returnIdentity();
        System.out.println("PASS SaleInvoiceAdapterTests assertions=" + assertions
                + " (fixed invoice routes, native PF parameters and persisted facts; no NC or database invoked)");
    }

    /** 6 个按钮到原生落点、动作域、原生 PF 动作与范围 ID 的固定映射。 */
    private static void buttonMapping() throws Exception {
        String[][] expected = new String[][] {
            { "Save", "INSERT", "WRITE", "PF WRITE" },
            { "Delete", "DELETE", "DELETE", "PF DELETE" },
            { "Commit", "UPDATE", "SAVE", "PF SAVE" },
            { "UnCommit", "UPDATE", "UNSAVE", "PF UNSAVE" },
            { "Approve", "UPDATE", "APPROVE", "PF APPROVE" },
            { "UnApprove", "UPDATE", "UNAPPROVE", "PF UNAPPROVE" } };
        for (String[] row : expected) {
            SaleInvoiceActionRoute route = route(row[0]);
            check(route != null && row[0].equals(route.getButton()), "route resolves: " + row[0]);
            check(row[1].equals(route.getOperationType()), "operation type: " + row[0]);
            check(row[2].equals(route.getKind().getNativeAction()), "native PF action: " + row[0]);
            check(row[3].equals(route.getKind().getNativeTarget()), "native target: " + row[0]);
            check(SalesOtherNodeScope.scopeIdFor(N, row[0]).equals(route.getScopeId()), "scope id: " + row[0]);
            check(route.getObjectPk().equals("INV-1"), "authorized invoice key: " + row[0]);
            check(route.getButtonName() != null && route.getButtonName().length() > 0, "button name: " + row[0]);
        }
        check(SaleInvoiceActionRoute.supportedActions().size() == 6, "six invoice buttons are mapped");
        check(SalesOtherNodeScope.SALE_INVOICE.equals(SalesOtherNodeScope.objectType(N)),
                "invoice object type is the invoice aggregate");
        check("nc.itf.so.qd.esign.ISaleInvoiceOperationAdapter".equals(adapterName())
                || SalesOtherNodeScope.isAdapter("nc.itf.so.qd.esign.ISaleInvoiceOperationAdapter"),
                "invoice adapter is registered in the shared scope");
    }

    /** 启用边界：删除本批关闭，其余 5 项已完成受控接入；关闭项执行前拒绝。 */
    private static void enablementBoundary() throws Exception {
        check(!SalesOtherNodeScope.isEnabled(N, "Delete"), "delete stays closed in the shared scope");
        for (String button : new String[] { "Save", "Commit", "UnCommit", "Approve", "UnApprove" }) {
            check(SalesOtherNodeScope.isEnabled(N, button), "button is enabled: " + button);
            check(route(button).isEnabled(), "route is enabled: " + button);
        }
        check(!route("Delete").isEnabled(), "delete route reports disabled");
        expectFailure(new Checked() { public void run() throws Exception { route("Delete").requireEnabled(); } });
        check(SalesOtherNodeScope.buttonScopes(N).size() == 6, "six buttons remain in the scope map");
        check(SalesOtherNodeScope.enabledButtons(N).size() == 6, "six enable flags remain documented");
    }

    /** 节点、按钮、动作域与对象范围的拒绝边界。 */
    private static void contextGuards() throws Exception {
        check(SaleInvoiceActionRoute.forContext(context("40060402", "Save", "INSERT", "INV-1")) == null,
                "delivery node is not an invoice route");
        check(SaleInvoiceActionRoute.forContext(context("40060301", "Commit", "UPDATE", "INV-1")) == null,
                "sales order node is not an invoice route");
        final SaleInvoiceOperationAdapter adapter = new Stub("32").adapter();
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Delete", "DELETE", "INV-1"), command("INV-1", "32", user(), null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            SaleInvoiceActionRoute.forContext(context(N, "Unknown", "UPDATE", "INV-1"));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            SaleInvoiceActionRoute.forContext(context(N, "Commit", "INSERT", "INV-1"));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            SaleInvoiceActionRoute.forContext(new OperationContext("R", "B", G, O, N, "销售发票维护", "Commit", "提交",
                    "UPDATE", null, Collections.singletonList(new BusinessObjectRef("draft",
                            SalesOtherNodeScope.SALE_INVOICE_LINE, "INV-1", "INV-CODE", null))));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            SaleInvoiceActionRoute.forContext(new OperationContext("R", "B", G, O, N, "销售发票维护", "Commit", "提交",
                    "UPDATE", null, Collections.singletonList(new BusinessObjectRef("draft",
                            SalesOtherNodeScope.SALE_INVOICE, null, "INV-CODE", null))));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            SaleInvoiceActionRoute.forContext(new OperationContext("R", "B", G, O, N, "销售发票维护", "Commit", "提交",
                    "UPDATE", null, java.util.Arrays.asList(
                            new BusinessObjectRef("a", SalesOtherNodeScope.SALE_INVOICE, "INV-1", null, null),
                            new BusinessObjectRef("b", SalesOtherNodeScope.SALE_INVOICE, "INV-2", null, null))));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            SaleInvoiceActionRoute.forContext(new OperationContext("R", "B", G, " ", N, "销售发票维护",
                    "Commit", "提交", "UPDATE", null, Collections.singletonList(new BusinessObjectRef("draft",
                            SalesOtherNodeScope.SALE_INVOICE, "INV-1", "INV-CODE", null))));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            SaleInvoiceActionRoute.forContext(new OperationContext("R", "B", "OTHER-GROUP", O, N, "销售发票维护",
                    "Commit", "提交", "UPDATE", null, Collections.singletonList(new BusinessObjectRef("draft",
                            SalesOtherNodeScope.SALE_INVOICE, "INV-1", "INV-CODE", null))));
        }});
        check(context("40060402", "Save", "INSERT", "INV-1").getObjects().size() == 1, "guard fixtures stay isolated");
    }

    /** 保存：一次 PF WRITE，参数与用户对象原样传递，结果回填持久化单号。 */
    private static void saveSuccess() throws Exception {
        Stub stub = new Stub("32");
        stub.returned = new SaleInvoiceVO[] { invoice("INV-1", G, O, "INV-CODE", "32", 0) };
        SaleInvoiceOperationAdapter adapter = stub.adapter();
        OperationContext context = context("Save", "INSERT", "INV-1");
        SalesPfActionCommand command = command("INV-1", "32", user(), null, params());
        adapter.validate(context, command);
        Serializable result = adapter.execute(context, command);
        check(stub.calls == 1 && "WRITE".equals(stub.action) && "32".equals(stub.billType),
                "save invokes native PF WRITE exactly once");
        check(stub.lastBill != null && "INV-1".equals(stub.lastBill.getPrimaryKey()),
                "save passes the authorized invoice");
        check(stub.lastUser instanceof PfUserObject
                && ((PfUserObject) stub.lastUser).getUserObject() instanceof SaleinvoiceUserObject,
                "save passes the native SaleinvoiceUserObject");
        check(stub.lastParameters != null && "notechecked".equals(stub.lastParameters.get("notechecked")),
                "save passes the whitelisted PF parameters");
        check(result instanceof SalesPfActionResult && !((SalesPfActionResult) result).isDeleted(),
                "save result is a known non-delete outcome");
        check("INV-CODE".equals(((SalesPfActionResult) result).getBillCode()),
                "save uses the persisted invoice code");
        check("INV-1".equals(adapter.describeResult(context, result).get(0).getObjectPk()),
                "save result maps the authorized invoice");
        check(stub.lastNote == null, "save may run without a native work note");
    }

    /** 提交：一次 PF SAVE。 */
    private static void commitSuccess() throws Exception {
        Stub stub = new Stub("32");
        stub.returned = new SaleInvoiceVO[] { invoice("INV-1", G, O, "INV-CODE", "32", 0) };
        Serializable result = stub.adapter().execute(context("Commit", "UPDATE", "INV-1"),
                command("INV-1", "32", user(), null, params()));
        check(stub.calls == 1 && "SAVE".equals(stub.action), "commit invokes native PF SAVE exactly once");
        check(result instanceof SalesPfActionResult, "commit returns a typed outcome");
        check("INV-CODE".equals(((SalesPfActionResult) result).getBillCode()), "commit uses the persisted code");
    }

    /** 收回：一次 PF UNSAVE。 */
    private static void unCommitSuccess() throws Exception {
        Stub stub = new Stub("32");
        stub.returned = new SaleInvoiceVO[] { invoice("INV-1", G, O, "INV-CODE", "32", 0) };
        Serializable result = stub.adapter().execute(context("UnCommit", "UPDATE", "INV-1"),
                command("INV-1", "32", user(), null, params()));
        check(stub.calls == 1 && "UNSAVE".equals(stub.action), "uncommit invokes native PF UNSAVE exactly once");
        check(result instanceof SalesPfActionResult && !((SalesPfActionResult) result).isDeleted(),
                "uncommit returns a typed outcome");
    }

    /** 审批：一次 PF APPROVE，审批窗口产生的 WorkflownoteVO 原样传递。 */
    private static void approveSuccess() throws Exception {
        Stub stub = new Stub("32");
        stub.returned = new SaleInvoiceVO[] { invoice("INV-1", G, O, "INV-CODE", "32", 0) };
        WorkflownoteVO note = new WorkflownoteVO();
        Serializable result = stub.adapter().execute(context("Approve", "UPDATE", "INV-1"),
                command("INV-1", "32", user(), note, params()));
        check(stub.calls == 1 && "APPROVE".equals(stub.action), "approve invokes native PF APPROVE exactly once");
        check(stub.lastNote == note, "approve passes the native workflow note unchanged");
        check(result instanceof SalesPfActionResult, "approve returns a typed outcome");
    }

    /** 取消审批：一次 PF UNAPPROVE。 */
    private static void unApproveSuccess() throws Exception {
        Stub stub = new Stub("32");
        stub.returned = new SaleInvoiceVO[] { invoice("INV-1", G, O, "INV-CODE", "32", 0) };
        Serializable result = stub.adapter().execute(context("UnApprove", "UPDATE", "INV-1"),
                command("INV-1", "32", user(), new WorkflownoteVO(), params()));
        check(stub.calls == 1 && "UNAPPROVE".equals(stub.action), "unapprove invokes native PF UNAPPROVE exactly once");
        check(result instanceof SalesPfActionResult, "unapprove returns a typed outcome");
    }

    /** 删除本批关闭：validate 与 execute 都在原生调用之前拒绝（原生 0 次调用）。 */
    private static void deleteStaysClosed() throws Exception {
        final Stub stub = new Stub(null);
        stub.deletedHeader = header("INV-1", G, O, "INV-CODE", 1, "32", 3);
        expectFailureMessage(new Checked() { public void run() throws Exception {
            stub.adapter().validate(context("Delete", "DELETE", "INV-1"),
                    command("INV-1", "32", user(), null, params()));
        }}, "本批保持关闭");
        expectFailureMessage(new Checked() { public void run() throws Exception {
            stub.adapter().execute(context("Delete", "DELETE", "INV-1"),
                    command("INV-1", "32", user(), null, params()));
        }}, "本批保持关闭");
        check(stub.calls == 0, "closed delete never reaches the native PF entry");
    }

    /** 删除事实核对：关闭状态下原生 0 次调用；已删除结果仍映射已授权发票。 */
    private static void deleteFacts() throws Exception {
        SaleInvoiceActionRoute route = route("Delete");
        check(route != null && "DELETE".equals(route.getKind().getNativeAction()),
                "delete route maps the native DELETE action");
        check(route != null && "DELETE".equals(route.getOperationType()),
                "delete route keeps the DELETE operation type");
        check(!route.isEnabled(), "delete route stays disabled for this batch");
        final Stub active = new Stub(null);
        active.deletedHeader = header("INV-1", G, O, "INV-CODE", 0, "32", 3);
        expectFailure(new Checked() { public void run() throws Exception {
            active.adapter().execute(context("Delete", "DELETE", "INV-1"),
                    command("INV-1", "32", user(), null, params()));
        }});
        check(active.calls == 0, "closed delete keeps zero native calls even with a live row");
        SaleInvoiceOperationAdapter adapter = new Stub(null).adapter();
        java.util.List<BusinessObjectRef> refs = adapter.describeResult(context("Delete", "DELETE", "INV-1"),
                new SalesPfActionResult(null, "INV-1", "INV-CODE", true));
        check(refs.size() == 1 && "INV-1".equals(refs.get(0).getObjectPk()),
                "deleted outcome still maps the authorized invoice");
        check(SalesOtherNodeScope.SALE_INVOICE.equals(refs.get(0).getObjectType()),
                "deleted outcome uses the invoice object type");
        check(new SalesPfActionResult(null, "INV-1", "INV-CODE", true).isDeleted(),
                "delete outcome carries the deleted fact");
    }

    /** 命令与原生参数白名单：不接受任意流程载荷、错对象、错组织与错交易类型。 */
    private static void payloadGuards() throws Exception {
        final SaleInvoiceOperationAdapter adapter = new Stub("32").adapter();
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "INV-1"), null);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "INV-1"),
                    new SalesPfActionCommand("32", new SaleInvoiceVO[] {
                        invoice("INV-1", G, O, "INV-CODE", "32", 0),
                        invoice("INV-2", G, O, "INV-CODE2", "32", 0) }, user(), null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "INV-1"),
                    command("INV-2", "32", user(), null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "INV-1"),
                    new SalesPfActionCommand("32", new SaleInvoiceVO[] {
                        invoice("INV-1", G, "OTHER-ORG", "INV-CODE", "32", 0) }, user(), null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "INV-1"),
                    new SalesPfActionCommand("32", new SaleInvoiceVO[] {
                        invoice("INV-1", "OTHER-GROUP", O, "INV-CODE", "32", 0) }, user(), null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "INV-1"),
                    command("INV-1", "32", null, null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            PfUserObject pf = new PfUserObject();
            pf.setUserObject("not-a-native-user-object");
            adapter.validate(context("Commit", "UPDATE", "INV-1"), command("INV-1", "32", pf, null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            PfUserObject pf = user();
            pf.setBusinessCheckMap(Collections.singletonMap("skip", Boolean.TRUE));
            adapter.validate(context("Commit", "UPDATE", "INV-1"), command("INV-1", "32", pf, null, params()));
        }});
        final Map<String, Object> unsafe = new HashMap<String, Object>();
        unsafe.put("compiler", "任意脚本");
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "INV-1"), command("INV-1", "32", user(), null, unsafe));
        }});
        final Map<String, Object> wrongType = new HashMap<String, Object>();
        wrongType.put("silently", Integer.valueOf(1));
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "INV-1"), command("INV-1", "32", user(), null, wrongType));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "INV-1"), command("INV-1", "30", user(), null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "INV-1"), command("INV-1", "32-01", user(), null, params()));
        }});
        final Map<String, Object> beside = new HashMap<String, Object>();
        beside.put("besideapprove", Boolean.TRUE);
        beside.put("nosendmessage", Boolean.TRUE);
        adapter.validate(context("UnApprove", "UPDATE", "INV-1"), command("INV-1", "32", user(), null, beside));
        check(true, "whitelisted workflow parameters are accepted");
    }

    /** 原生拒绝原样传播且不重试；执行后找不到持久化表头时不得当成成功。 */
    private static void nativeOutcomes() throws Exception {
        final Stub rejected = new Stub("32");
        rejected.failure = new BusinessException("native pf rejected");
        expectFailureMessage(new Checked() { public void run() throws Exception {
            rejected.adapter().execute(context("Commit", "UPDATE", "INV-1"),
                    command("INV-1", "32", user(), null, params()));
        }}, "native pf rejected");
        check(rejected.calls == 1, "native PF rejection is not retried");
        final Stub missing = new Stub("32");
        missing.noHeader = true;
        expectFailure(new Checked() { public void run() throws Exception {
            missing.adapter().execute(context("Commit", "UPDATE", "INV-1"),
                    command("INV-1", "32", user(), null, params()));
        }});
        check(missing.calls == 1, "missing persisted invoice is rejected after one native call");
        final Stub noStatus = new Stub("32");
        noStatus.persisted = headerWithoutStatus("INV-1", G, O, "INV-CODE");
        expectFailure(new Checked() { public void run() throws Exception {
            noStatus.adapter().execute(context("Commit", "UPDATE", "INV-1"),
                    command("INV-1", "32", user(), null, params()));
        }});
    }

    /** 原生返回的单据身份不一致时不得当成成功。 */
    private static void returnIdentity() throws Exception {
        final Stub stub = new Stub("32");
        stub.returned = new SaleInvoiceVO[] { invoice("INV-OTHER", G, O, "INV-OTHER", "32", 0) };
        expectFailure(new Checked() { public void run() throws Exception {
            stub.adapter().execute(context("Commit", "UPDATE", "INV-1"),
                    command("INV-1", "32", user(), null, params()));
        }});
        check(stub.calls == 1, "mismatched native return still invoked PF once");
        final SaleInvoiceOperationAdapter adapter = new Stub("32").adapter();
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.describeResult(context("Commit", "UPDATE", "INV-1"), "not-a-pf-result");
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.describeResult(context("Commit", "UPDATE", "INV-1"),
                    new SalesPfActionResult(null, "INV-2", "INV-CODE", false));
        }});
    }

    /** PF 入口替身：记录动作、交易类型、流程载体与参数，可注入原生异常与持久化事实。 */
    private static final class Stub {
        Object returned;
        SaleInvoiceHVO persisted;
        SaleInvoiceHVO deletedHeader;
        boolean noHeader;
        BusinessException failure;
        int calls;
        String action, billType;
        SaleInvoiceVO lastBill;
        Object lastUser;
        Object lastNote;
        Map<String, Object> lastParameters;
        private final String type;
        Stub(String type) { this.type = type; }
        SaleInvoiceOperationAdapter adapter() {
            return new SaleInvoiceOperationAdapter() {
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
                            lastNote = args[2];
                            lastBill = (SaleInvoiceVO) args[3];
                            lastUser = args[4];
                            lastParameters = (Map<String, Object>) args[5];
                            if (failure != null) { throw failure; }
                            return returned;
                        }
                    });
                }
                @Override protected SaleInvoiceHVO loadHeader(String key) {
                    if ("DELETE".equals(action)) { return deletedHeader; }
                    if (persisted != null) { return persisted; }
                    if (noHeader) { return null; }
                    return header(key, G, O, "INV-CODE", 0, type == null ? "32" : type, 3);
                }
            };
        }
    }

    private static String adapterName() { return SaleInvoiceOperationAdapter.class.getName(); }

    private static Map<String, Object> params() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("notechecked", "notechecked");
        return map;
    }

    private static PfUserObject user() {
        PfUserObject pf = new PfUserObject();
        SaleinvoiceUserObject invoice = new SaleinvoiceUserObject();
        invoice.setIsclientsave(true);
        pf.setUserObject(invoice);
        return pf;
    }

    private static SalesPfActionCommand command(String pk, String billType, PfUserObject user,
            WorkflownoteVO note, Map<String, Object> params) {
        return new SalesPfActionCommand(billType,
                new SaleInvoiceVO[] { invoice(pk, G, O, "INV-CODE", "32", 0) }, user, note, params);
    }

    private static SaleInvoiceActionRoute route(String button) throws Exception {
        return SaleInvoiceActionRoute.forContext(new OperationContext("R", "B", G, O, N, "销售发票维护",
                button, SalesOtherNodeScope.buttonName(button),
                SalesOtherNodeScope.operationTypeFor(N, button), null,
                Collections.singletonList(new BusinessObjectRef("draft", SalesOtherNodeScope.SALE_INVOICE,
                        "INV-1", "INV-CODE", null))));
    }

    private static OperationContext context(String button, String operation, String pk) {
        return context(N, button, operation, pk);
    }

    private static OperationContext context(String node, String button, String operation, String pk) {
        return new OperationContext("R", "B", G, O, node, "销售发票维护", button,
                SalesOtherNodeScope.buttonName(button), operation, null,
                Collections.singletonList(new BusinessObjectRef("draft", SalesOtherNodeScope.SALE_INVOICE,
                        pk, "INV-CODE", null)));
    }

    private static SaleInvoiceVO invoice(final String pk, final String group, final String org,
            final String code, final String type, final int dr) {
        return new SaleInvoiceVO() {
            public IBillMeta getMetaData() { return FIXTURE_META; }
            public SaleInvoiceHVO getParentVO() { return header(pk, group, org, code, dr, type, 3); }
            public String getPrimaryKey() { return pk; }
        };
    }

    private static SaleInvoiceHVO header(final String pk, final String group, final String org,
            final String code, final int dr, final String type, final int status) {
        return new SaleInvoiceHVO() {
            public String getPk_group() { return group; }
            public String getPk_org() { return org; }
            public String getVbillcode() { return code; }
            public String getVtrantypecode() { return type; }
            public String getPrimaryKey() { return pk; }
            public Integer getDr() { return Integer.valueOf(dr); }
            public Integer getFstatusflag() { return Integer.valueOf(status); }
        };
    }

    private static SaleInvoiceHVO headerWithoutStatus(final String pk, final String group,
            final String org, final String code) {
        return new SaleInvoiceHVO() {
            public String getPk_group() { return group; }
            public String getPk_org() { return org; }
            public String getVbillcode() { return code; }
            public String getVtrantypecode() { return "32"; }
            public String getPrimaryKey() { return pk; }
            public Integer getDr() { return Integer.valueOf(0); }
            public Integer getFstatusflag() { return null; }
        };
    }

    private static final IBillMeta FIXTURE_META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "OFFLINE_INVOICE_PF_FIXTURE"; }
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
        throw new AssertionError("invalid invoice PF request was accepted");
    }

    private static void check(boolean ok, String message) {
        if (!ok) { throw new AssertionError(message); }
        assertions++;
    }
}
