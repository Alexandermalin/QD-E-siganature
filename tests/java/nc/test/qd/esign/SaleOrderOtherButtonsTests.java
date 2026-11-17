package nc.test.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.impl.so.qd.esign.SaleOrderActionRoute;
import nc.impl.so.qd.esign.SaleOrderOperationAdapter;
import nc.impl.so.qd.esign.SaleOrderPfSaveAdapter;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.qd.esign.SaleOrderCommand;
import nc.vo.so.qd.esign.SaleOrderPfActionCommand;
import nc.vo.so.qd.esign.SaleOrderStateCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/**
 * 销售订单其他按钮 S1 公共链路的离线契约检查：范围白名单、服务端固定动作路由、类型化命令与保存回归。
 * 不连接 NC、不执行原生 PF/RPC、不写数据库。
 */
public final class SaleOrderOtherButtonsTests {
    private interface Checked { void run() throws Exception; }
    private static int assertions;
    private static final String G = SaleOrderPilotScope.GROUP_ID;
    private static final String O = SaleOrderPilotScope.ORG_ID;
    private static final String N = SaleOrderPilotScope.NODE_CODE;
    private static final Map<String, SaleOrderActionRoute.Kind> EXPECTED;

    static {
        Map<String, SaleOrderActionRoute.Kind> expected = new LinkedHashMap<String, SaleOrderActionRoute.Kind>();
        expected.put("Delete", SaleOrderActionRoute.Kind.PF_DELETE);
        expected.put("Commit", SaleOrderActionRoute.Kind.PF_SAVE);
        expected.put("UnCommit", SaleOrderActionRoute.Kind.PF_UNSAVE);
        expected.put("Approve", SaleOrderActionRoute.Kind.PF_APPROVE);
        expected.put("UnApprove", SaleOrderActionRoute.Kind.PF_UNAPPROVE);
        expected.put("Freeze", SaleOrderActionRoute.Kind.STATE_FREEZE);
        expected.put("UnFreeze", SaleOrderActionRoute.Kind.STATE_UNFREEZE);
        expected.put("BillClose", SaleOrderActionRoute.Kind.STATE_CLOSE);
        EXPECTED = Collections.unmodifiableMap(expected);
    }

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        scopeWhitelist();
        routeWhitelist();
        routeRejections();
        adapterGate();
        typedCommands();
        System.out.println("PASS SaleOrderOtherButtonsTests assertions=" + assertions
                + " (whitelist, route, typed commands and save delegation; no NC or database invoked)");
    }

    /** 范围白名单：9 个范围 ID、动作域归类与中文名固定。 */
    private static void scopeWhitelist() {
        Map<String, String> scopes = SaleOrderPilotScope.buttonScopes();
        check(scopes.size() == 9, "nine verified button scopes are registered");
        check(SaleOrderPilotScope.SCOPE_ID.equals(scopes.get("Save")), "save keeps its existing scope id");
        check("QD-ES-FEFBC5B292C330AD".equals(scopes.get("Delete")), "delete scope id");
        check("QD-ES-4671CC2E70A34B97".equals(scopes.get("Commit")), "commit scope id");
        check("QD-ES-4F23D38FB22BA45F".equals(scopes.get("UnCommit")), "uncommit scope id");
        check("QD-ES-9C0B035FFEB2110D".equals(scopes.get("Approve")), "approve scope id");
        check("QD-ES-51BB76212FFCD156".equals(scopes.get("UnApprove")), "unapprove scope id");
        check("QD-ES-C7E51B3ED69F8B05".equals(scopes.get("Freeze")), "freeze scope id");
        check("QD-ES-BF1F44B944C037B3".equals(scopes.get("UnFreeze")), "unfreeze scope id");
        check("QD-ES-2A439AB1FE3628B0".equals(scopes.get("BillClose")), "bill-close scope id");
        check("DELETE".equals(SaleOrderPilotScope.operationTypeFor(N, "Delete")), "delete maps to DELETE");
        check("INSERT".equals(SaleOrderPilotScope.operationTypeFor(N, "Save")), "save maps to INSERT");
        for (String button : EXPECTED.keySet()) {
            String expected = "Delete".equals(button) ? "DELETE" : "UPDATE";
            check(expected.equals(SaleOrderPilotScope.operationTypeFor(N, button)),
                    "state or flow action maps to its fixed domain: " + button);
            check(SaleOrderPilotScope.supportsOperation(N, button,
                    SaleOrderPilotScope.operationTypeFor(N, button)), "expected operation domain accepted: " + button);
            check(!SaleOrderPilotScope.supportsOperation(N, button, "DELETE" == expected ? "UPDATE" : "DELETE"),
                    "unexpected domain rejected: " + button);
            check(SaleOrderPilotScope.buttonName(button).length() > 0, "button name available: " + button);
        }
        check(SaleOrderPilotScope.supports(G, O), "first-round group and organization accepted");
        check(!SaleOrderPilotScope.supports(G, "OTHER"), "other organizations rejected");
        check(!SaleOrderPilotScope.supports("OTHER", O), "other groups rejected");
        check(!SaleOrderPilotScope.isPilotNode("40060303"), "closing-management node is a separate batch");
        check(!SaleOrderPilotScope.isPilotButton(N, "Cancel"), "unverified button is not whitelisted");
        check(SaleOrderPilotScope.scopeIdFor(N, "Cancel") == null, "unverified button has no scope id");
    }

    /** 固定动作路由：8 个按钮各自映射到已核实的原生落点，S1 全部保持关闭。 */
    private static void routeWhitelist() throws Exception {
        check(SaleOrderActionRoute.supportedActions().size() == 8, "eight native routes registered");
        for (Map.Entry<String, SaleOrderActionRoute.Kind> entry : EXPECTED.entrySet()) {
            final String button = entry.getKey();
            final String operation = SaleOrderPilotScope.operationTypeFor(N, button);
            final OperationContext context = context(N, button, operation, G, O, "SO-1");
            SaleOrderActionRoute route = SaleOrderActionRoute.forContext(context);
            check(route != null, "verified button must resolve: " + button);
            check(entry.getValue() == route.getKind(), "native route kind: " + button);
            check(button.equals(route.getButton()), "real button code retained: " + button);
            check(route.getOperationType().equals(SaleOrderPilotScope.operationTypeFor(N, button)),
                    "mapping operation domain retained: " + button);
            check(route.getScopeId().equals(SaleOrderPilotScope.scopeIdFor(N, button)),
                    "scope id retained: " + button);
            check("SO-1".equals(route.getObjectPk()), "authorized order key retained: " + button);
            boolean implemented = true; // 八项按钮服务端均已就绪（删除/解冻已补齐受控分支）
            check(route.isEnabled() == implemented, "only implemented native routes are enabled: " + button);
            if (!route.isEnabled()) {
                expectBusiness(new Checked() {
                    public void run() throws Exception {
                        SaleOrderActionRoute.forContext(context(N, button, operation, G, O, "SO-1")).requireEnabled();
                    }
                });
            }
        }
        check(SaleOrderActionRoute.forContext(context(
                SaleOrderPilotScope.NODE_CODE, "Save", "INSERT", G, O, "SO-1")) == null,
                "save remains on its existing PF save pilot");
        check(SaleOrderActionRoute.forContext(context(
                "40060303", "BillClose", "UPDATE", G, O, "SO-1")) == null,
                "closing-management node must not use this route");
    }

    /** 错组织、错对象、错动作与未知按钮必须在执行前拒绝，不能回落其他写入路径。 */
    private static void routeRejections() throws Exception {
        expectBusiness(new Checked() { public void run() throws Exception {
            SaleOrderActionRoute.forContext(context(N, "Commit", "INSERT", G, O, "SO-1"));
        }});
        expectBusiness(new Checked() { public void run() throws Exception {
            SaleOrderActionRoute.forContext(context(N, "Delete", "UPDATE", G, O, "SO-1"));
        }});
        expectBusiness(new Checked() { public void run() throws Exception {
            SaleOrderActionRoute.forContext(context(N, "Freeze", "UPDATE", G, "OTHER", "SO-1"));
        }});
        expectBusiness(new Checked() { public void run() throws Exception {
            SaleOrderActionRoute.forContext(context(N, "Freeze", "UPDATE", "OTHER", O, "SO-1"));
        }});
        expectBusiness(new Checked() { public void run() throws Exception {
            SaleOrderActionRoute.forContext(context(N, "Freeze", "UPDATE", G, O, null));
        }});
        expectBusiness(new Checked() { public void run() throws Exception {
            SaleOrderActionRoute.forContext(context(N, "Cancel", "UPDATE", G, O, "SO-1"));
        }});
        expectBusiness(new Checked() { public void run() throws Exception {
            SaleOrderActionRoute.forContext(collection(N, "Commit", "UPDATE", 2));
        }});
        expectBusiness(new Checked() { public void run() throws Exception {
            SaleOrderActionRoute.forContext(objectType(N, "Commit", "UPDATE", "BD_MATERIAL"));
        }});
        expectBusiness(new Checked() { public void run() throws Exception {
            List<BusinessObjectRef> objects = new ArrayList<BusinessObjectRef>();
            objects.add(new BusinessObjectRef("root", "SO_SALE_ORDER", "SO-1", "SO-CODE", null));
            objects.add(new BusinessObjectRef("line", "SO_SALE_ORDER", "SO-1-B1", null, "root"));
            SaleOrderActionRoute.forContext(new OperationContext(null, null, G, O, N, "销售订单维护",
                    "Commit", "提交", "UPDATE", null, objects));
        }});
    }

    /** 适配器在 S1 只登记白名单并拒绝执行；保存分支持续委托既有 PF 保存适配器一次。 */
    private static void adapterGate() throws Exception {
        final CountingAdapter counting = new CountingAdapter();
        final SaleOrderCommand command = new SaleOrderCommand(
                new SaleOrderVO[] { order("SO-1") }, null, null);
        for (String button : new String[] { "Delete", "Commit", "UnCommit", "Approve", "UnApprove",
                "Freeze", "UnFreeze", "BillClose" }) {
            final String current = button;
            final String operation = SaleOrderPilotScope.operationTypeFor(N, button);
            final boolean stateKind = "Freeze".equals(button) || "UnFreeze".equals(button)
                    || "BillClose".equals(button);
            final String fragment = stateKind ? "SaleOrderStateCommand required" : "SaleOrderPfActionCommand required";
            expectBusinessMessage(new Checked() { public void run() throws Exception {
                counting.validate(context(N, current, operation, G, O, "SO-1"), command);
            }}, fragment);
            expectBusinessMessage(new Checked() { public void run() throws Exception {
                counting.execute(context(N, current, operation, G, O, "SO-1"), command);
            }}, fragment);
            check(counting.saveCalls == 0, "no native PF call before enablement: " + button);
        }
        expectBusiness(new Checked() { public void run() throws Exception {
            counting.validate(context("OTHER", "Freeze", "UPDATE", G, O, "SO-1"), command);
        }});
        check(counting.saveCalls == 0, "legacy selector is not reached for unsupported payloads");
        expectBusinessMessage(new Checked() { public void run() throws Exception {
            counting.validate(context(N, "Save", "INSERT", G, O, "SO-1"), command);
        }}, "FAKE_SAVE_VALIDATED");
        check(counting.saveCalls == 1, "save still delegates to the pinned PF save adapter exactly once");
        expectBusinessMessage(new Checked() { public void run() throws Exception {
            counting.execute(context(N, "Save", "INSERT", G, O, "SO-1"), command);
        }}, "FAKE_SAVE_EXECUTED");
        check(counting.saveCalls == 2, "save execution still delegates once per call");
        expectBusiness(new Checked() { public void run() throws Exception {
            new SaleOrderOperationAdapter().validate(
                    new OperationContext(null, null, G, O, "OTHER", "Other", "Save", "Save", "UPDATE", null,
                            Collections.singletonList(new BusinessObjectRef("d", "SO_SALE_ORDER", "SO-1", null, null))),
                    null);
        }});
    }

    /** 类型化命令的副本隔离与空值处理。 */
    private static void typedCommands() {
        SaleOrderVO[] bills = new SaleOrderVO[] { order("SO-1") };
        Map<String, Boolean> checkMap = new HashMap<String, Boolean>();
        checkMap.put("ATPCheck", Boolean.FALSE);
        SaleOrderStateCommand state = new SaleOrderStateCommand(bills, "客户取消", checkMap);
        bills[0] = null;
        checkMap.put("OTHER", Boolean.TRUE);
        check(state.getBills() != null && state.getBills()[0] != null, "state bills are copied");
        check("客户取消".equals(state.getCloseReason()), "close reason retained separately");
        check(state.getBusinessCheckMap().size() == 1 && !state.getBusinessCheckMap().containsKey("OTHER"),
                "business check map is copied");
        state.getBusinessCheckMap().put("MUTATED", Boolean.TRUE);
        check(!state.getBusinessCheckMap().containsKey("MUTATED"), "business check map getter returns a copy");
        check(state instanceof Serializable, "state command is serializable");
        SaleOrderStateCommand empty = new SaleOrderStateCommand(null, null, null);
        check(empty.getBills() == null, "missing state bills are tolerated");
        check(empty.getBusinessCheckMap().isEmpty(), "missing business check map is empty");

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("notechecked", "notechecked");
        SaleOrderPfActionCommand pf = new SaleOrderPfActionCommand(
                new SaleOrderVO[] { order("SO-1") }, null, null, parameters);
        parameters.put("extra", "value");
        check(pf.getBills().length == 1 && pf.getBills()[0] != null, "PF action bills are copied");
        check(pf.getParameters().size() == 1 && !pf.getParameters().containsKey("extra"),
                "PF parameters are copied");
        check(pf.getUserObject() == null && pf.getWorkflownote() == null, "optional PF carriers tolerate nulls");
        check(pf instanceof Serializable, "PF action command is serializable");
        check(SaleOrderPfActionCommand.class.getName().length() > 0, "PF action command resolved");
    }

    private static final class CountingAdapter extends SaleOrderOperationAdapter {
        int saveCalls;
        private final SaleOrderPfSaveAdapter fake = new SaleOrderPfSaveAdapter() {
            public void validate(OperationContext context, Serializable payload) throws BusinessException {
                saveCalls++;
                throw new BusinessException("FAKE_SAVE_VALIDATED");
            }
            public Serializable execute(OperationContext context, Serializable payload) throws BusinessException {
                saveCalls++;
                throw new BusinessException("FAKE_SAVE_EXECUTED");
            }
        };
        @Override protected SaleOrderPfSaveAdapter pfAdapter() { return fake; }
    }

    private static OperationContext context(String node, String button, String operation,
            String group, String org, String pk) {
        return new OperationContext("R", "B", group, org, node, "销售订单维护", button,
                SaleOrderPilotScope.buttonName(button), operation, null,
                Collections.singletonList(new BusinessObjectRef("draft", "SO_SALE_ORDER", pk, "SO-CODE", null)));
    }

    private static OperationContext collection(String node, String button, String operation, int count) {
        List<BusinessObjectRef> objects = new ArrayList<BusinessObjectRef>();
        for (int index = 0; index < count; index++) {
            objects.add(new BusinessObjectRef("draft-" + index, "SO_SALE_ORDER", "SO-" + index, null, null));
        }
        return new OperationContext("R", "B", G, O, node, "销售订单维护", button, "按钮",
                operation, null, objects);
    }

    private static OperationContext objectType(String node, String button, String operation, String type) {
        return new OperationContext("R", "B", G, O, node, "销售订单维护", button, "按钮", operation, null,
                Collections.singletonList(new BusinessObjectRef("draft", type, "SO-1", null, null)));
    }

    private static SaleOrderVO order(final String pk) {
        return new SaleOrderVO() {
            public IBillMeta getMetaData() { return FIXTURE_META; }
            public String getPrimaryKey() { return pk; }
        };
    }

    /** 离线元数据替身：避免测试实例化真实单据时经 NCLocator 加载平台元数据。 */
    private static final IBillMeta FIXTURE_META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "OFFLINE_SALES_ACTION_FIXTURE"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    private static void expectBusiness(Checked action) throws Exception {
        expectBusinessMessage(action, null);
    }

    private static void expectBusinessMessage(Checked action, String fragment) throws Exception {
        try {
            action.run();
        } catch (BusinessException error) {
            if (fragment != null && (error.getMessage() == null || error.getMessage().indexOf(fragment) < 0)) {
                throw new AssertionError("unexpected rejection message: " + error.getMessage());
            }
            assertions++;
            return;
        }
        throw new AssertionError("invalid sales-order request was accepted");
    }

    private static void check(boolean ok, String message) {
        if (!ok) { throw new AssertionError(message); }
        assertions++;
    }
}
