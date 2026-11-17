package nc.test.qd.esign;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.impl.so.qd.esign.DeliveryActionRoute;
import nc.impl.so.qd.esign.DeliveryOperationAdapter;
import nc.itf.so.m4331.IDeliveryAssitFunc;
import nc.itf.uap.pf.IplatFormEntry;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.so.m4331.entity.DeliveryBVO;
import nc.vo.so.m4331.entity.DeliveryHVO;
import nc.vo.so.m4331.entity.DeliveryUserObject;
import nc.vo.so.m4331.entity.DeliveryVO;
import nc.vo.so.pub.SOParameterVO;
import nc.vo.so.qd.esign.DeliveryCommand;
import nc.vo.so.qd.esign.DeliveryStateResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;
import nc.vo.uapbd.qd.esign.SalesPfActionCommand;
import nc.vo.uapbd.qd.esign.SalesPfActionResult;

/**
 * 发货单维护（节点 40060402，正式清单第 229–238 行）服务端固定路由与适配器的离线契约检查。
 *
 * <p>使用原生 PF 入口与 {@code IDeliveryAssitFunc} 的接口替身核对动作名、交易类型、原生参数、
 * 行下标一致性、启用边界与结果核对；不连接 NC、数据库或事务，不打开界面，不执行任何真实写入。</p>
 */
public final class DeliveryAdapterTests {
    private interface Checked { void run() throws Exception; }
    private static int assertions;
    private static final String NODE = SalesOtherNodeScope.DELIVERY_NODE;
    private static final String G = SalesOtherNodeScope.GROUP_ID;
    private static final String O = "1001A9100000000001AB";
    private static final String PK = "1001A9100000000001DL";
    private static final String ROW1 = "1001A9100000000001D1";
    private static final String ROW2 = "1001A9100000000001D2";
    private static final String CODE = "FH-0001";
    private static final String TYPE = "4331-01";

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        buttonMapping();
        enablementBoundary();
        pfActionsSuccess();
        deleteStayClosed();
        pfGuardRejections();
        pfResultFacts();
        billCloseSuccess();
        billCloseGuards();
        rowCloseSuccess();
        rowCloseGuards();
        describeResults();
        nativeOutcomes();
        System.out.println("PASS DeliveryAdapterTests assertions=" + assertions
                + " (delivery 40060402 fixed routes, native parameters, row-index checks; no NC or database invoked)");
    }

    /** 10 个按钮到原生落点、动作域、范围 ID 与粒度的固定映射。 */
    private static void buttonMapping() throws Exception {
        String[][] expected = new String[][] {
            { "Save", "INSERT", DeliveryActionRoute.Kind.PF_WRITE.name(), "BILL", "PF" },
            { "Delete", "DELETE", DeliveryActionRoute.Kind.PF_DELETE.name(), "BILL", "PF" },
            { "Commit", "UPDATE", DeliveryActionRoute.Kind.PF_SAVE.name(), "BILL", "PF" },
            { "UnCommit", "UPDATE", DeliveryActionRoute.Kind.PF_UNSAVE.name(), "BILL", "PF" },
            { "Approve", "UPDATE", DeliveryActionRoute.Kind.PF_APPROVE.name(), "BILL", "PF" },
            { "UnApprove", "UPDATE", DeliveryActionRoute.Kind.PF_UNAPPROVE.name(), "BILL", "PF" },
            { "BillClose", "UPDATE", DeliveryActionRoute.Kind.STATE_BILL_CLOSE.name(), "BILL", "DIRECT" },
            { "BillOpen", "UPDATE", DeliveryActionRoute.Kind.STATE_BILL_OPEN.name(), "BILL", "DIRECT" },
            { "OutClose", "UPDATE", DeliveryActionRoute.Kind.STATE_ROW_CLOSE.name(), "ROW", "DIRECT" },
            { "OutOpen", "UPDATE", DeliveryActionRoute.Kind.STATE_ROW_OPEN.name(), "ROW", "DIRECT" }
        };
        check(DeliveryActionRoute.supportedActions().size() == 10, "发货单固定映射必须恰好登记 10 个按钮");
        check(DeliveryActionRoute.buttonScopes().size() == 10, "按钮范围 ID 必须恰好登记 10 行");
        check(SalesOtherNodeScope.scopeIdFor(NODE, "Save").equals("QD-ES-04ECAF336F8932CA"),
                "保存范围 ID 与正式清单一致");
        check(SalesOtherNodeScope.scopeIdFor(NODE, "OutOpen").equals("QD-ES-5275CF042A861E3D"),
                "出库打开范围 ID 与正式清单一致");
        for (String[] row : expected) {
            String button = row[0];
            DeliveryActionRoute route = route(button);
            check(route != null && route.getButton().equals(button), "按钮必须解析到本节点固定动作：" + button);
            check(row[1].equals(route.getOperationType()), "动作域固定：" + button);
            check(row[2].equals(route.getKind().name()), "原生落点固定：" + button);
            check(row[3].equals(route.getKind().getGranularity().name()), "作用粒度固定：" + button);
            check("PF".equals(row[4]) == route.getKind().isPf(), "PF/直接服务分类固定：" + button);
            check(route.getKind().getNativeTarget() != null && route.getKind().getNativeTarget().length() > 0, "原生落点描述非空：" + button);
            check(SalesOtherNodeScope.scopeIdFor(NODE, button).equals(route.getScopeId()), "范围 ID 固定：" + button);
            check(!route.isRowAction() || button.startsWith("Out"), "只有出库开/关是行动作：" + button);
        }
        check(route("OutClose").getKind().getPfAction() == null, "直接服务动作没有 PF 脚本名");
        check("WRITE".equals(route("Save").getKind().getPfAction()), "保存使用原生 PF WRITE");
        check("DELETE".equals(route("Delete").getKind().getPfAction()), "删除使用原生 PF DELETE");
        check("SAVE".equals(route("Commit").getKind().getPfAction()), "提交使用原生 PF SAVE");
        check("UNSAVE".equals(route("UnCommit").getKind().getPfAction()), "收回使用原生 PF UNSAVE");
        check("APPROVE".equals(route("Approve").getKind().getPfAction()), "审批使用原生 PF APPROVE");
        check("UNAPPROVE".equals(route("UnApprove").getKind().getPfAction()), "取消审批使用原生 PF UNAPPROVE");
        // 非本节点必须返回 null，由其他节点适配器自行处理，不能串用发货单授权。
        check(DeliveryActionRoute.forContext(context("40060301", "Save", "INSERT", PK)) == null,
                "非发货单节点不得解析出发货单路由");
        check(DeliveryActionRoute.forContext(null) == null, "空上下文不得解析出发货单路由");
    }

    /** 启用边界：删除与两个「打开」动作保持关闭，执行前拒绝。 */
    private static void enablementBoundary() throws Exception {
        for (String button : new String[] { "Save", "Commit", "UnCommit", "Approve", "UnApprove",
                "BillClose", "OutClose" }) {
            check(route(button).isEnabled(), "已完成受控接入的按钮必须允许执行：" + button);
        }
        for (String button : new String[] { "Delete", "BillOpen", "OutOpen" }) {
            DeliveryActionRoute route = route(button);
            check(!route.isEnabled(), "未核实恢复协议的按钮必须保持关闭：" + button);
            check(!SalesOtherNodeScope.isEnabled(NODE, button), "范围白名单同步关闭：" + button);
        }
        check(SalesOtherNodeScope.isEnabled(NODE, "Save"), "保存按钮在范围白名单中启用");
        check(SalesOtherNodeScope.enabledButtons(NODE).size() == 10, "范围白名单登记 10 个按钮");
        final DeliveryActionRoute closed = route("BillOpen");
        expectFailure(new Checked() { public void run() throws Exception { closed.requireEnabled(); } });
        final DeliveryActionRoute closedRow = route("OutOpen");
        expectFailure(new Checked() { public void run() throws Exception { closedRow.requireEnabled(); } });
    }

    /** PF 五动作（保存/提交/收回/审批/取消审批）各调用原生 PF 恰好一次并核对入参。 */
    private static void pfActionsSuccess() throws Exception {
        String[][] actions = new String[][] {
            { "Save", "WRITE" }, { "Commit", "SAVE" }, { "UnCommit", "UNSAVE" },
            { "Approve", "APPROVE" }, { "UnApprove", "UNAPPROVE" }
        };
        for (String[] action : actions) {
            Stub stub = new Stub();
            stub.returned = new DeliveryVO[] { delivery(PK, G, O, CODE, TYPE, ROW1) };
            DeliveryOperationAdapter adapter = stub.adapter();
            OperationContext context = context(action[0], operationType(action[0]), PK);
            SalesPfActionCommand command = command(PK, TYPE, user(), null, params());
            adapter.validate(context, command);
            Serializable result = adapter.execute(context, command);
            check(stub.calls == 1, "PF 动作必须只调用一次原生入口：" + action[0]);
            check(action[1].equals(stub.action), "原生 PF 动作名固定：" + action[0]);
            check(TYPE.equals(stub.billType), "交易类型原样传递：" + action[0]);
            check(stub.lastBill != null && PK.equals(stub.lastBill.getPrimaryKey()), "授权单据原样传递：" + action[0]);
            check(stub.lastUser instanceof PfUserObject
                    && ((PfUserObject) stub.lastUser).getUserObject() instanceof DeliveryUserObject,
                    "原生发货单用户对象原样传递：" + action[0]);
            check(stub.lastParameters != null && "notechecked".equals(stub.lastParameters.get("notechecked")),
                    "流程参数白名单原样传递：" + action[0]);
            check(result instanceof SalesPfActionResult && !((SalesPfActionResult) result).isDeleted(),
                    "PF 结果类型正确且非删除：" + action[0]);
            check(PK.equals(((SalesPfActionResult) result).getPrimaryKey()), "PF 结果保留授权主键：" + action[0]);
            check(CODE.equals(((SalesPfActionResult) result).getBillCode()), "PF 结果回填持久化单号：" + action[0]);
        }
        // 未带交易类型时按该节点固定功能型 4331 回退。
        Stub fallback = new Stub();
        fallback.returned = new DeliveryVO[] { delivery(PK, G, O, CODE, TYPE, ROW1) };
        fallback.adapter().execute(context("Commit", "UPDATE", PK), command(PK, null, user(), null, params()));
        check(fallback.calls == 1 && "4331".equals(fallback.billType),
                "缺省交易类型回退为固定功能型 4331");
        // 已核实的 4331-* 细分类型与表头一致时放行。
        Stub typed = new Stub();
        typed.returned = new DeliveryVO[] { delivery(PK, G, O, CODE, TYPE, ROW1) };
        typed.adapter().execute(context("Commit", "UPDATE", PK), command(PK, TYPE, user(), null, params()));
        check(typed.calls == 1 && TYPE.equals(typed.billType), "4331-* 细分类型与表头一致时放行");
    }

    /** 删除与两个「打开」动作保持关闭：原生调用次数必须为 0。 */
    private static void deleteStayClosed() throws Exception {
        final Stub delete = new Stub();
        delete.deletedHeader = header(PK, G, O, CODE, TYPE, 1, 3);
        final DeliveryOperationAdapter deleteAdapter = delete.adapter();
        final OperationContext deleteContext = context("Delete", "DELETE", PK);
        final SalesPfActionCommand deleteCommand = command(PK, TYPE, user(), null, params());
        expectFailure(new Checked() { public void run() throws Exception {
            deleteAdapter.execute(deleteContext, deleteCommand);
        }});
        check(delete.calls == 0, "关闭的删除按钮不得调用原生 PF");
        final Stub open = new Stub();
        final DeliveryOperationAdapter openAdapter = open.adapter();
        final OperationContext openContext = context("BillOpen", "UPDATE", PK);
        expectFailure(new Checked() { public void run() throws Exception {
            openAdapter.execute(openContext, stateCommand(PK, billParam(PK, null, emptyChecks())));
        }});
        check(open.closeCalls == 0 && open.calls == 0, "关闭的整单打开不得调用原生服务");
        final Stub rowOpen = new Stub();
        final DeliveryOperationAdapter rowOpenAdapter = rowOpen.adapter();
        final OperationContext rowOpenContext = rowContext("OutOpen", PK, ROW1);
        expectFailure(new Checked() { public void run() throws Exception {
            rowOpenAdapter.execute(rowOpenContext, rowCommand(PK, new int[] { 0 }, ROW1));
        }});
        check(rowOpen.closeCalls == 0 && rowOpen.calls == 0, "关闭的出库打开不得调用原生服务");
    }

    /** 命令、节点、按钮、动作域、对象与流程参数的类型化拒绝；所有拒绝不得触发原生调用。 */
    private static void pfGuardRejections() throws Exception {
        final Stub stub = new Stub();
        final DeliveryOperationAdapter adapter = stub.adapter();
        final OperationContext commit = context("Commit", "UPDATE", PK);
        final SalesPfActionCommand good = command(PK, TYPE, user(), null, params());
        expectFailure(new Checked() { public void run() throws Exception { adapter.validate(commit, null); } });
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(commit, new DeliveryCommand(null, null, null, null, null));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("40060303", "Commit", "UPDATE", PK), good);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "INSERT", PK), good);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", "OTHER-PK"), good);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(otherOrgContext("Commit", "UPDATE", PK), good);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Unknown", "UPDATE", PK), good);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(twoRoots("Commit"), good);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(childContext("Commit", PK, ROW1), good);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(wrongTypeContext("Commit"), good);
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", PK), command("OTHER", TYPE, user(), null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", PK), command(PK, "4331-02", user(), null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", PK), command(PK, "30", user(), null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", PK), command(PK, TYPE, null, null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            PfUserObject plain = new PfUserObject();
            plain.setUserObject("not-a-delivery-user-object");
            adapter.validate(context("Commit", "UPDATE", PK), command(PK, TYPE, plain, null, params()));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            PfUserObject skip = user();
            skip.setBusinessCheckMap(Collections.singletonMap("ATPCheck", Boolean.FALSE));
            adapter.validate(context("Commit", "UPDATE", PK), command(PK, TYPE, skip, null, params()));
        }});
        final Map<String, Object> unsafe = new HashMap<String, Object>();
        unsafe.put("compiler", "任意脚本");
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", PK), command(PK, TYPE, user(), null, unsafe));
        }});
        final Map<String, Object> wrong = new HashMap<String, Object>();
        wrong.put("silently", Integer.valueOf(1));
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context("Commit", "UPDATE", PK), command(PK, TYPE, user(), null, wrong));
        }});
        check(stub.calls == 0, "所有拒绝路径都不得调用原生 PF");
        // 行动作缺少子对象、整单动作出现子对象、根对象类型不符均拒绝。
        final DeliveryOperationAdapter rowAdapter = new Stub().adapter();
        expectFailure(new Checked() { public void run() throws Exception {
            rowAdapter.validate(context("OutClose", "UPDATE", PK), rowCommand(PK, new int[] { 0 }, null));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            rowAdapter.validate(childContext("BillClose", PK, ROW1),
                    stateCommand(PK, billParam(PK, null, emptyChecks())));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            rowAdapter.validate(context("OutClose", "UPDATE", "OTHER"), rowCommand(PK, new int[] { 0 }, ROW1));
        }});
    }

    /** PF 结果事实：持久化状态缺失、返回身份不符与状态字段不可核对一律拒绝。 */
    private static void pfResultFacts() throws Exception {
        final Stub commit = new Stub();
        commit.returned = new DeliveryVO[] { delivery("OTHER-PK", G, O, "OTHER-CODE", TYPE, ROW1) };
        final DeliveryOperationAdapter commitAdapter = commit.adapter();
        final OperationContext commitContext = context("Commit", "UPDATE", PK);
        final SalesPfActionCommand commitCommand = command(PK, TYPE, user(), null, params());
        expectFailure(new Checked() { public void run() throws Exception {
            commitAdapter.execute(commitContext, commitCommand);
        }});
        check(commit.calls == 1, "返回身份不符时原生 PF 仍只调用一次");
        Stub missing = new Stub();
        missing.headerMissing = true;
        final DeliveryOperationAdapter missingAdapter = missing.adapter();
        expectFailure(new Checked() { public void run() throws Exception {
            missingAdapter.execute(commitContext, commitCommand);
        }});
        check(missing.calls == 1, "持久化状态缺失时原生 PF 仍只调用一次");
        Stub noStatus = new Stub();
        noStatus.header = headerWithoutStatus(PK, G, O, CODE, TYPE);
        final DeliveryOperationAdapter noStatusAdapter = noStatus.adapter();
        expectFailure(new Checked() { public void run() throws Exception {
            noStatusAdapter.execute(commitContext, commitCommand);
        }});
        check(noStatus.calls == 1, "状态字段不可核对时原生 PF 仍只调用一次");
    }

    /** 整单关闭：只调用一次原生 closeDelivery(SOParameterVO)，参数白名单与返回身份核对。 */
    private static void billCloseSuccess() throws Exception {
        Stub stub = new Stub();
        stub.closeReturned = new DeliveryVO[] { delivery(PK, G, O, CODE, TYPE, ROW1) };
        DeliveryOperationAdapter adapter = stub.adapter();
        OperationContext context = context("BillClose", "UPDATE", PK);
        SOParameterVO parameter = billParam(PK, null, emptyChecks());
        DeliveryCommand command = stateCommand(PK, parameter);
        adapter.validate(context, command);
        Serializable result = adapter.execute(context, command);
        check(stub.closeCalls == 1, "整单关闭必须只调用一次 closeDelivery");
        check(stub.lastParameter == parameter, "整单关闭原样传递 SOParameterVO");
        check(stub.lastParameter.getVos() != null && stub.lastParameter.getVos().length == 1,
                "整单关闭参数只带一张单据");
        check(result instanceof DeliveryStateResult, "整单关闭返回类型化结果");
        check(PK.equals(((DeliveryStateResult) result).getPrimaryKey()), "整单关闭结果保留授权主键");
        check(CODE.equals(((DeliveryStateResult) result).getBillCode()), "整单关闭结果回填真实单号");
        check(stub.lastRows == null, "整单关闭不得调用行关闭");
    }

    /** 整单关闭参数白名单：vo/view/views/userObject、非空检查标记与非法 vos 一律拒绝且原生 0 调用。 */
    private static void billCloseGuards() throws Exception {
        final Stub stub = new Stub();
        final DeliveryOperationAdapter adapter = stub.adapter();
        final OperationContext context = context("BillClose", "UPDATE", PK);
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context, stateCommand(PK, null));
        }});
        final SOParameterVO withVo = billParam(PK, null, emptyChecks());
        withVo.setVo(delivery(PK, G, O, CODE, TYPE, ROW1));
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context, stateCommand(PK, withVo));
        }});
        final SOParameterVO withUser = billParam(PK, null, emptyChecks());
        withUser.setUserObject(new Object());
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context, stateCommand(PK, withUser));
        }});
        final SOParameterVO withChecks = billParam(PK, null,
                Collections.singletonMap("ATPCheck", Boolean.FALSE));
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context, stateCommand(PK, withChecks));
        }});
        final SOParameterVO twoVos = billParam(PK, null, emptyChecks());
        twoVos.setVos(new DeliveryVO[] { delivery(PK, G, O, CODE, TYPE, ROW1),
            delivery("OTHER-PK", G, O, "OTHER-CODE", TYPE, ROW2) });
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context, stateCommand(PK, twoVos));
        }});
        final SOParameterVO wrongPk = new SOParameterVO();
        wrongPk.setVos(new DeliveryVO[] { delivery("OTHER-PK", G, O, "OTHER-CODE", TYPE, ROW1) });
        wrongPk.setBusinessCheckMap(new HashMap<String, Boolean>());
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context, stateCommand(PK, wrongPk));
        }});
        final DeliveryCommand withRows = new DeliveryCommand(
                new DeliveryVO[] { delivery(PK, G, O, CODE, TYPE, ROW1) }, new int[] { 0 },
                new SOParameterVO[] { billParam(PK, null, emptyChecks()) }, null, null);
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context, withRows);
        }});
        final DeliveryCommand withMarker = new DeliveryCommand(
                new DeliveryVO[] { delivery(PK, G, O, CODE, TYPE, ROW1) }, null,
                new SOParameterVO[] { billParam(PK, null, emptyChecks()) }, "ATPCheck", Boolean.FALSE);
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context, withMarker);
        }});
        final DeliveryCommand wrongBill = new DeliveryCommand(
                new DeliveryVO[] { delivery("OTHER-PK", G, O, "OTHER-CODE", TYPE, ROW1) }, null,
                new SOParameterVO[] { billParam(PK, null, emptyChecks()) }, null, null);
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context, wrongBill);
        }});
        final DeliveryCommand wrongOrg = new DeliveryCommand(
                new DeliveryVO[] { delivery(PK, G, "OTHER-ORG", CODE, TYPE, ROW1) }, null,
                new SOParameterVO[] { billParam(PK, null, emptyChecks()) }, null, null);
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(context, wrongOrg);
        }});
        check(stub.closeCalls == 0, "所有整单关闭拒绝路径都不得调用原生服务");
    }

    /** 出库关闭：行下标一致时只调用一次 closeDeliveryRows(DeliveryVO,int[])。 */
    private static void rowCloseSuccess() throws Exception {
        Stub stub = new Stub();
        stub.loadedBill = billWithRows(PK, G, O, CODE, TYPE, ROW1, ROW2);
        stub.closeReturned = new DeliveryVO[] { delivery(PK, G, O, CODE, TYPE, ROW1) };
        DeliveryOperationAdapter adapter = stub.adapter();
        OperationContext context = rowContext("OutClose", PK, ROW1);
        DeliveryCommand command = rowCommand(PK, new int[] { 0 }, ROW1);
        adapter.validate(context, command);
        Serializable result = adapter.execute(context, command);
        check(stub.closeCalls == 1, "出库关闭必须只调用一次 closeDeliveryRows");
        check(stub.lastRows != null && stub.lastRows.length == 1 && stub.lastRows[0] == 0,
                "出库关闭原样传递卡片行下标");
        check(stub.lastRowBill != null && PK.equals(stub.lastRowBill.getPrimaryKey()),
                "出库关闭使用持久化发货单作为原生参数");
        check(stub.lastRowBill.getChildrenVO() != null && stub.lastRowBill.getChildrenVO().length == 2,
                "出库关闭原生参数包含持久化表体行");
        check(result instanceof DeliveryStateResult, "出库关闭返回类型化结果");
        check(PK.equals(((DeliveryStateResult) result).getPrimaryKey()), "出库关闭结果保留授权主键");
        // 下标 1 合法且授权行是第二行时同样放行。
        Stub second = new Stub();
        second.loadedBill = billWithRows(PK, G, O, CODE, TYPE, ROW1, ROW2);
        second.closeReturned = new DeliveryVO[] { delivery(PK, G, O, CODE, TYPE, ROW1) };
        second.adapter().execute(rowContext("OutClose", PK, ROW2), rowCommand(PK, new int[] { 1 }, ROW2));
        check(second.closeCalls == 1 && second.lastRows[0] == 1, "第二行下标与授权行主键一致时放行");
    }

    /** 行下标越界、与授权行主键不符、行集合变化与参数越界一律拒绝且原生 0 调用。 */
    private static void rowCloseGuards() throws Exception {
        final Stub stub = new Stub();
        stub.loadedBill = billWithRows(PK, G, O, CODE, TYPE, ROW1, ROW2);
        final DeliveryOperationAdapter adapter = stub.adapter();
        final OperationContext firstRow = rowContext("OutClose", PK, ROW1);
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(firstRow, rowCommand(PK, new int[] { 2 }, ROW1));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(firstRow, rowCommand(PK, new int[] { 1 }, ROW1));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(firstRow, rowCommand(PK, new int[] { -1 }, ROW1));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(firstRow, rowCommand(PK, new int[] { 0, 1 }, ROW1));
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(firstRow, rowCommand(PK, null, ROW1));
        }});
        final SOParameterVO parameter = billParam(PK, null, emptyChecks());
        final DeliveryCommand withParameters = new DeliveryCommand(
                new DeliveryVO[] { delivery(PK, G, O, CODE, TYPE, ROW1) }, new int[] { 0 },
                new SOParameterVO[] { parameter }, null, null);
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(firstRow, withParameters);
        }});
        final Stub missing = new Stub();
        final DeliveryOperationAdapter missingAdapter = missing.adapter();
        expectFailure(new Checked() { public void run() throws Exception {
            missingAdapter.validate(firstRow, rowCommand(PK, new int[] { 0 }, ROW1));
        }});
        final Stub fewerRows = new Stub();
        fewerRows.loadedBill = billWithSingleRow(PK, G, O, CODE, TYPE, ROW1);
        final DeliveryOperationAdapter fewerAdapter = fewerRows.adapter();
        expectFailure(new Checked() { public void run() throws Exception {
            fewerAdapter.validate(firstRow, rowCommand(PK, new int[] { 1 }, ROW1));
        }});
        // 授权行不属于持久化行集合时拒绝。
        final Stub foreign = new Stub();
        foreign.loadedBill = billWithRows(PK, G, O, CODE, TYPE, "OTHER-ROW", ROW2);
        final DeliveryOperationAdapter foreignAdapter = foreign.adapter();
        expectFailure(new Checked() { public void run() throws Exception {
            foreignAdapter.validate(firstRow, rowCommand(PK, new int[] { 0 }, ROW1));
        }});
        // 整单动作不得携带行动作上下文。
        expectFailure(new Checked() { public void run() throws Exception {
            adapter.validate(rowContext("BillClose", PK, ROW1),
                    stateCommand(PK, billParam(PK, null, emptyChecks())));
        }});
        check(stub.closeCalls == 0, "所有出库关闭拒绝路径都不得调用原生服务");
    }

    /** 结果引用：PF 1 个根引用；直接服务根引用加行动作行引用，客户端标识保持稳定。 */
    private static void describeResults() throws Exception {
        Stub pf = new Stub();
        DeliveryOperationAdapter pfAdapter = pf.adapter();
        final OperationContext commit = context("Commit", "UPDATE", PK);
        SalesPfActionResult pfResult = new SalesPfActionResult(null, PK, CODE, false);
        List<BusinessObjectRef> refs = pfAdapter.describeResult(commit, pfResult);
        check(refs.size() == 1, "PF 结果只描述一个根对象");
        check("root".equals(refs.get(0).getClientObjectId()), "PF 结果保留客户端对象标识");
        check(SalesOtherNodeScope.DELIVERY.equals(refs.get(0).getObjectType()), "PF 结果对象类型为发货单");
        check(PK.equals(refs.get(0).getObjectPk()), "PF 结果回填真实主键");
        check(CODE.equals(refs.get(0).getBillCode()), "PF 结果回填真实单号");
        check(refs.get(0).getParentClientObjectId() == null, "PF 结果为根引用");
        DeliveryOperationAdapter direct = new Stub().adapter();
        OperationContext close = context("BillClose", "UPDATE", PK);
        List<BusinessObjectRef> billRefs = direct.describeResult(close,
                new DeliveryStateResult(null, PK, CODE));
        check(billRefs.size() == 1 && PK.equals(billRefs.get(0).getObjectPk()),
                "整单关闭结果只描述已授权根对象");
        OperationContext row = rowContext("OutClose", PK, ROW1);
        List<BusinessObjectRef> rowRefs = direct.describeResult(row, new DeliveryStateResult(null, PK, CODE));
        check(rowRefs.size() == 2, "出库关闭结果描述根对象与授权行");
        check(PK.equals(rowRefs.get(0).getObjectPk()), "出库关闭结果根主键正确");
        check(rowRefs.get(0).getParentClientObjectId() == null, "出库关闭结果第一个是根引用");
        check(ROW1.equals(rowRefs.get(1).getObjectPk()), "出库关闭结果行主键沿用授权行主键");
        check("root".equals(rowRefs.get(1).getParentClientObjectId()), "出库关闭结果行引用指向授权根对象");
        check(SalesOtherNodeScope.DELIVERY_LINE.equals(rowRefs.get(1).getObjectType()), "行引用对象类型为发货单行");
        expectFailure(new Checked() { public void run() throws Exception {
            new Stub().adapter().describeResult(commit, "unexpected");
        }});
        expectFailure(new Checked() { public void run() throws Exception {
            new Stub().adapter().describeResult(commit, new SalesPfActionResult(null, null, CODE, false));
        }});
    }

    /** 原生异常原样上抛且不重试；直接服务异常同样只调用一次。 */
    private static void nativeOutcomes() throws Exception {
        final Stub rejected = new Stub();
        rejected.failure = new BusinessException("native pf rejected");
        final DeliveryOperationAdapter pfAdapter = rejected.adapter();
        final OperationContext commit = context("Commit", "UPDATE", PK);
        final SalesPfActionCommand command = command(PK, TYPE, user(), null, params());
        expectFailureMessage(new Checked() { public void run() throws Exception {
            pfAdapter.execute(commit, command);
        }}, "native pf rejected");
        check(rejected.calls == 1, "原生 PF 异常不得自动重试");
        final Stub closeRejected = new Stub();
        closeRejected.closeFailure = new BusinessException("native close rejected");
        final DeliveryOperationAdapter closeAdapter = closeRejected.adapter();
        final OperationContext closeContext = context("BillClose", "UPDATE", PK);
        final DeliveryCommand closeCommand = stateCommand(PK, billParam(PK, null, emptyChecks()));
        expectFailureMessage(new Checked() { public void run() throws Exception {
            closeAdapter.execute(closeContext, closeCommand);
        }}, "native close rejected");
        check(closeRejected.closeCalls == 1, "原生直接服务异常不得自动重试");
        final Stub badReturn = new Stub();
        badReturn.closeReturned = new DeliveryVO[] { delivery("OTHER-PK", G, O, "OTHER-CODE", TYPE, ROW1) };
        final DeliveryOperationAdapter badReturnAdapter = badReturn.adapter();
        expectFailure(new Checked() { public void run() throws Exception {
            badReturnAdapter.execute(closeContext, closeCommand);
        }});
        check(badReturn.closeCalls == 1, "直接服务返回身份不符时原生仍只调用一次");
    }

    // ------------------------------------------------------------------ 替身与夹具

    /** 原生 PF 入口与 IDeliveryAssitFunc 的接口替身，记录动作、入参、调用次数并可注入异常与持久化事实。 */
    private static final class Stub {
        Object returned;
        DeliveryHVO header;
        DeliveryHVO deletedHeader;
        boolean headerMissing;
        BusinessException failure;
        int calls;
        String action;
        String billType;
        DeliveryVO lastBill;
        Object lastUser;
        Map<String, Object> lastParameters;
        DeliveryVO loadedBill;
        DeliveryVO[] closeReturned;
        BusinessException closeFailure;
        int closeCalls;
        int loadBillCalls;
        SOParameterVO lastParameter;
        DeliveryVO lastRowBill;
        int[] lastRows;

        DeliveryOperationAdapter adapter() {
            return new DeliveryOperationAdapter() {
                @Override protected IplatFormEntry platformEntry() {
                    return (IplatFormEntry) Proxy.newProxyInstance(IplatFormEntry.class.getClassLoader(),
                            new Class<?>[] { IplatFormEntry.class }, new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if (!"processAction".equals(method.getName())) {
                                throw new AssertionError("unexpected PF entry call: " + method.getName());
                            }
                            calls++;
                            action = (String) args[0];
                            billType = (String) args[1];
                            lastBill = (DeliveryVO) args[3];
                            lastUser = args[4];
                            lastParameters = (Map<String, Object>) args[5];
                            if (failure != null) { throw failure; }
                            return returned;
                        }
                    });
                }

                @Override protected DeliveryHVO loadHeader(String key) {
                    if ("DELETE".equals(action)) { return deletedHeader; }
                    if (header != null) { return header; }
                    if (headerMissing) { return null; }
                    return header(key, G, O, CODE, TYPE, 0, 3);
                }

                @Override protected DeliveryVO loadBill(String key) {
                    loadBillCalls++;
                    return loadedBill;
                }

                @Override protected IDeliveryAssitFunc assitFunc() {
                    return (IDeliveryAssitFunc) Proxy.newProxyInstance(IDeliveryAssitFunc.class.getClassLoader(),
                            new Class<?>[] { IDeliveryAssitFunc.class }, new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String name = method.getName();
                            if ("closeDelivery".equals(name) && args != null && args.length == 1
                                    && args[0] instanceof SOParameterVO) {
                                closeCalls++;
                                lastParameter = (SOParameterVO) args[0];
                                if (closeFailure != null) { throw closeFailure; }
                                return closeReturned;
                            }
                            if ("closeDeliveryRows".equals(name)) {
                                closeCalls++;
                                lastRowBill = (DeliveryVO) args[0];
                                lastRows = (int[]) args[1];
                                if (closeFailure != null) { throw closeFailure; }
                                return closeReturned;
                            }
                            throw new AssertionError("unexpected assit func call: " + name);
                        }
                    });
                }
            };
        }
    }

    private static Map<String, Object> params() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("notechecked", "notechecked");
        return map;
    }

    private static Map<String, Boolean> emptyChecks() { return new HashMap<String, Boolean>(); }

    private static PfUserObject user() {
        PfUserObject pf = new PfUserObject();
        DeliveryUserObject delivery = new DeliveryUserObject();
        delivery.setIsclientsave(true);
        pf.setUserObject(delivery);
        return pf;
    }

    private static SalesPfActionCommand command(String pk, String billType, PfUserObject user,
            Object note, Map<String, Object> parameters) {
        return new SalesPfActionCommand(billType,
                new DeliveryVO[] { delivery(pk, G, O, CODE, TYPE, ROW1) }, user, null, parameters);
    }

    private static DeliveryCommand stateCommand(String pk, SOParameterVO parameter) {
        return new DeliveryCommand(new DeliveryVO[] { delivery(pk, G, O, CODE, TYPE, ROW1) }, null,
                parameter == null ? null : new SOParameterVO[] { parameter }, null, null);
    }

    private static DeliveryCommand rowCommand(String pk, int[] indexes, String authorizedRow) {
        DeliveryVO bill = delivery(pk, G, O, CODE, TYPE, authorizedRow);
        return new DeliveryCommand(new DeliveryVO[] { bill }, indexes, null, null, null);
    }

    private static SOParameterVO billParam(String pk, String ignored, Map<String, Boolean> checks) {
        SOParameterVO parameter = new SOParameterVO();
        parameter.setVos(new DeliveryVO[] { delivery(pk, G, O, CODE, TYPE, ROW1) });
        parameter.setBusinessCheckMap(checks);
        return parameter;
    }

    private static DeliveryActionRoute route(String button) throws BusinessException {
        if ("OutClose".equals(button) || "OutOpen".equals(button)) {
            return DeliveryActionRoute.forContext(rowContext(button, PK, ROW1));
        }
        return DeliveryActionRoute.forContext(context(button,
                SalesOtherNodeScope.operationTypeFor(NODE, button), PK));
    }

    private static String operationType(String button) {
        return SalesOtherNodeScope.operationTypeFor(NODE, button);
    }

    private static OperationContext context(String node, String button, String operation, String pk) {
        return new OperationContext(null, null, G, O, node, SalesOtherNodeScope.nodeName(node), button,
                SalesOtherNodeScope.buttonName(button), operation, null,
                Collections.singletonList(new BusinessObjectRef("root", SalesOtherNodeScope.DELIVERY, pk, CODE, null)));
    }

    private static OperationContext context(String button, String operation, String pk) {
        return context(NODE, button, operation, pk);
    }

    private static OperationContext otherOrgContext(String button, String operation, String pk) {
        return new OperationContext(null, null, G, O + "X", NODE, SalesOtherNodeScope.nodeName(NODE), button,
                SalesOtherNodeScope.buttonName(button), operation, null,
                Collections.singletonList(new BusinessObjectRef("root", SalesOtherNodeScope.DELIVERY, pk, CODE, null)));
    }

    private static OperationContext rowContext(String button, String pk, String rowPk) {
        List<BusinessObjectRef> objects = new ArrayList<BusinessObjectRef>();
        objects.add(new BusinessObjectRef("root", SalesOtherNodeScope.DELIVERY, pk, CODE, null));
        objects.add(new BusinessObjectRef("line", SalesOtherNodeScope.DELIVERY_LINE, rowPk, null, "root"));
        return new OperationContext(null, null, G, O, NODE, SalesOtherNodeScope.nodeName(NODE), button,
                SalesOtherNodeScope.buttonName(button), SalesOtherNodeScope.operationTypeFor(NODE, button),
                null, objects);
    }

    private static OperationContext childContext(String button, String pk, String rowPk) {
        return rowContext(button, pk, rowPk);
    }

    private static OperationContext twoRoots(String button) {
        List<BusinessObjectRef> objects = new ArrayList<BusinessObjectRef>();
        objects.add(new BusinessObjectRef("a", SalesOtherNodeScope.DELIVERY, PK, CODE, null));
        objects.add(new BusinessObjectRef("b", SalesOtherNodeScope.DELIVERY, "OTHER-PK", "OTHER-CODE", null));
        return new OperationContext(null, null, G, O, NODE, SalesOtherNodeScope.nodeName(NODE), button,
                SalesOtherNodeScope.buttonName(button), SalesOtherNodeScope.operationTypeFor(NODE, button),
                null, objects);
    }

    private static OperationContext wrongTypeContext(String button) {
        return new OperationContext(null, null, G, O, NODE, SalesOtherNodeScope.nodeName(NODE), button,
                SalesOtherNodeScope.buttonName(button), SalesOtherNodeScope.operationTypeFor(NODE, button), null,
                Collections.singletonList(new BusinessObjectRef("root", SalesOtherNodeScope.SALE_ORDER, PK, CODE, null)));
    }

    private static DeliveryVO delivery(final String pk, final String group, final String org,
            final String code, final String type, final String rowPk) {
        return new DeliveryVO() {
            public IBillMeta getMetaData() { return FIXTURE_META; }
            public DeliveryHVO getParentVO() { return header(pk, group, org, code, type, 0, 3); }
            public String getPrimaryKey() { return pk; }
            public DeliveryBVO[] getChildrenVO() {
                return rowPk == null ? new DeliveryBVO[0] : new DeliveryBVO[] { row(rowPk) };
            }
        };
    }

    private static DeliveryVO billWithRows(final String pk, final String group, final String org,
            final String code, final String type, final String firstRow, final String secondRow) {
        return new DeliveryVO() {
            public IBillMeta getMetaData() { return FIXTURE_META; }
            public DeliveryHVO getParentVO() { return header(pk, group, org, code, type, 0, 3); }
            public String getPrimaryKey() { return pk; }
            public DeliveryBVO[] getChildrenVO() {
                return new DeliveryBVO[] { row(firstRow), row(secondRow) };
            }
        };
    }

    private static DeliveryVO billWithSingleRow(final String pk, final String group, final String org,
            final String code, final String type, final String rowPk) {
        return new DeliveryVO() {
            public IBillMeta getMetaData() { return FIXTURE_META; }
            public DeliveryHVO getParentVO() { return header(pk, group, org, code, type, 0, 3); }
            public String getPrimaryKey() { return pk; }
            public DeliveryBVO[] getChildrenVO() { return new DeliveryBVO[] { row(rowPk) }; }
        };
    }
    private static DeliveryHVO header(final String pk, final String group, final String org,
            final String code, final String type, final int dr, final int status) {
        return new DeliveryHVO() {
            public Object getAttributeValue(String name) {
                return "dr".equals(name) ? Integer.valueOf(dr) : super.getAttributeValue(name);
            }
            public String getPk_group() { return group; }
            public String getPk_org() { return org; }
            public String getVbillcode() { return code; }
            public String getVtrantypecode() { return type; }
            public String getPrimaryKey() { return pk; }
            public Integer getFstatusflag() { return Integer.valueOf(status); }
        };
    }

    private static DeliveryHVO headerWithoutStatus(final String pk, final String group, final String org,
            final String code, final String type) {
        return new DeliveryHVO() {
            public Object getAttributeValue(String name) {
                return "dr".equals(name) ? Integer.valueOf(0) : super.getAttributeValue(name);
            }
            public String getPk_group() { return group; }
            public String getPk_org() { return org; }
            public String getVbillcode() { return code; }
            public String getVtrantypecode() { return type; }
            public String getPrimaryKey() { return pk; }
            public Integer getFstatusflag() { return null; }
        };
    }

    /** 离线单据元数据替身：避免构造 DeliveryVO 时访问 NC 元数据服务。 */
    private static final IBillMeta FIXTURE_META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "OFFLINE_DELIVERY_PF_FIXTURE"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    private static DeliveryBVO row(final String pk) {        return new DeliveryBVO() {
            public String getPrimaryKey() { return pk; }
            public String getCdeliverybid() { return pk; }
        };
    }

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
        throw new AssertionError("invalid delivery request was accepted");
    }

    private static void check(boolean ok, String message) {
        if (!ok) { throw new AssertionError(message); }
        assertions++;
    }
}
