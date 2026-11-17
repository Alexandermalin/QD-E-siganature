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
import nc.impl.so.qd.esign.SaleCloseActionRoute;
import nc.impl.so.qd.esign.SaleCloseOperationAdapter;
import nc.itf.so.m30.closemanage.ISaleOrderCloseManageMaintain;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.ISuperVO;
import nc.vo.pub.IVOMeta;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.so.m30.entity.SaleOrderBVO;
import nc.vo.so.m30.entity.SaleOrderHVO;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.so.m30.entity.SaleOrderViewVO;
import nc.vo.so.pub.SOParameterVO;
import nc.vo.so.qd.esign.SaleCloseCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;

/**
 * 销售订单关闭管理（节点 40060303，正式清单第 217–228 行）的离线契约检查。
 *
 * <p>只验证服务端固定路由、12 项按钮映射与粒度/返回类型、启用边界、原因一致性、单笔单行约束、
 * 原生调用次数（恰一次、不重试）以及返回身份核对；不调用 NC 服务、数据库或事务。
 * 原生服务以 {@link java.lang.reflect.Proxy} 伪造，适配器通过受保护的 {@code closeManage()}
 * 接缝注入，因此离线可断言真实调用次数与入参。</p>
 */
public final class SaleCloseAdapterTests {
    private interface Checked {
        void run() throws Exception;
    }

    private static final String NODE = SalesOtherNodeScope.CLOSE_NODE;
    private static final String GROUP = SalesOtherNodeScope.GROUP_ID;
    private static final String ORG = "SALE-ORG-CLOSE";
    private static final String OTHER_ORG = "SALE-ORG-OTHER";
    private static final String ORDER = "ORDER-1";
    private static final String ORDER_2 = "ORDER-2";
    private static final String LINE = "LINE-1";
    private static final String LINE_2 = "LINE-2";
    private static final String REASON = "客户取消订单";

    /** 按钮、原生方法、粒度、返回类型（与关闭管理证据表和固定路由逐一对应）。 */
    private static final String[][] MAPPING = {
            { "BillClose", "closeSaleOrder", "BILL", "ORDER_ARRAY" },
            { "BillOpen", "openSaleOrder", "BILL", "PARAMETER_ARRAY" },
            { "LineClose", "closeSaleOrderRow", "ROW", "VIEW_ARRAY" },
            { "LineOpen", "openSaleOrderRow", "ROW", "VIEW_ARRAY" },
            { "SendClose", "closeSaleOrderSend", "ROW", "VIEW_ARRAY" },
            { "SendOpen", "openSaleOrderSend", "ROW", "VIEW_ARRAY" },
            { "OutClose", "closeSaleOrderOut", "ROW", "VIEW_ARRAY" },
            { "OutOpen", "openSaleOrderOut", "ROW", "PARAMETER_ARRAY" },
            { "InvoiceClose", "closeSaleOrderInvoice", "ROW", "VIEW_ARRAY" },
            { "InvoiceOpen", "openSaleOrderInvoice", "ROW", "VIEW_ARRAY" },
            { "SettleClose", "closeSaleOrderSettle", "ROW", "VIEW_ARRAY" },
            { "SettleOpen", "openSaleOrderSettle", "ROW", "VIEW_ARRAY" } };

    private static int assertions;

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        mappingAndScopes();
        enablement();
        successfulActions();
        reasonChecks();
        selectionRejections();
        nativeOutcomes();
        describeResults();
        System.out.println("PASS SaleCloseAdapterTests assertions=" + assertions
                + " (12 fixed close-manage routes, enablement gate, reason/selection checks and native identity"
                + " verification; no NC service, database or transaction invoked)");
    }

    /** 12 个按钮的固定映射、范围 ID 反查、动作域、粒度、返回类型与对象解析。 */
    private static void mappingAndScopes() throws Exception {
        check(SaleCloseActionRoute.supportedActions().size() == MAPPING.length,
                "关闭管理固定路由覆盖 12 项");
        for (int index = 0; index < MAPPING.length; index++) {
            String button = MAPPING[index][0];
            String method = MAPPING[index][1];
            String granularity = MAPPING[index][2];
            String resultKind = MAPPING[index][3];
            String scopeId = SalesOtherNodeScope.scopeIdFor(NODE, button);
            check(scopeId != null && scopeId.trim().length() > 0, button + " 已登记正式清单范围 ID");
            check(button.equals(SalesOtherNodeScope.buttonForScope(scopeId)), button + " 范围 ID 可反查按钮");
            check("UPDATE".equals(SalesOtherNodeScope.operationTypeFor(NODE, button)),
                    button + " 动作域固定为 UPDATE");
            check(SalesOtherNodeScope.supportsAction(GROUP, ORG, NODE, button, "UPDATE"),
                    button + " 节点/按钮/组织/动作域命中白名单");
            SaleCloseActionRoute route = routeFor(button);
            check(route != null, button + " 解析固定动作");
            check(button.equals(route.getButton()), button + " 路由按钮编码一致");
            check(SalesOtherNodeScope.buttonName(button).equals(route.getButtonName()),
                    button + " 路由按钮名称一致");
            check(("ISaleOrderCloseManageMaintain." + method).equals(route.getKind().getNativeTarget()),
                    button + " 原生落点固定为 " + method);
            check("UPDATE".equals(route.getKind().getOperationType()), button + " 路由动作域为 UPDATE");
            check(granularity.equals(route.getKind().getGranularity().name()),
                    button + " 动作粒度为 " + granularity);
            check(resultKind.equals(route.getKind().getResultKind().name()),
                    button + " 返回类型为 " + resultKind);
            check(scopeId.equals(route.getScopeId()), button + " 路由范围 ID 与白名单一致");
            check(ORDER.equals(route.getObjectPk()), button + " 路由绑定授权订单主键");
            if ("ROW".equals(granularity)) {
                check(route.getLinePks().size() == 1 && LINE.equals(route.getLinePks().get(0)),
                        button + " 行动作绑定已授权行主键");
            } else {
                check(route.getLinePks().isEmpty(), button + " 整单动作不绑定行主键");
            }
        }
    }

    /** 启用边界：10 项可执行；BillOpen/OutOpen 在任何原生调用前被拒绝且调用次数为 0。 */
    private static void enablement() throws Exception {
        int enabled = 0;
        for (int index = 0; index < MAPPING.length; index++) {
            String button = MAPPING[index][0];
            boolean expected = !"BillOpen".equals(button) && !"OutOpen".equals(button);
            SaleCloseActionRoute route = routeFor(button);
            check(route.isEnabled() == expected, button + " 路由可执行状态与范围白名单一致");
            check(SalesOtherNodeScope.isEnabled(NODE, button) == expected, button + " 服务端白名单可执行状态一致");
            if (expected) {
                enabled++;
                route.requireEnabled();
                check(true, button + " 已接入受控执行，允许执行");
            }
        }
        check(enabled == 10, "关闭管理 10 个动作已接入受控执行");
        // 原生 ATP「继续确认」协议未核实：整单打开/出库打开必须在执行前拒绝，且不产生任何原生调用。
        final Stub billOpenStub = new Stub();
        final SaleCloseOperationAdapter billOpenAdapter = billOpenStub.adapter();
        final OperationContext billOpenContext = context("BillOpen", "UPDATE", rootObjects());
        final SaleCloseCommand billOpenCommand = billCommand(ORDER, ORG, REASON, REASON);
        expectFailureMessage(new Checked() {
            public void run() throws Exception { billOpenAdapter.validate(billOpenContext, billOpenCommand); }
        }, "整单打开");
        expectFailureMessage(new Checked() {
            public void run() throws Exception { billOpenAdapter.execute(billOpenContext, billOpenCommand); }
        }, "整单打开");
        check(billOpenStub.totalCalls() == 0, "未启用的整单打开不产生原生调用");
        final Stub outOpenStub = new Stub();
        final SaleCloseOperationAdapter outOpenAdapter = outOpenStub.adapter();
        final OperationContext outOpenContext = context("OutOpen", "UPDATE", rootAndLineObjects());
        final SaleCloseCommand outOpenCommand = rowCommand(ORDER, ORG, LINE, REASON, REASON);
        expectFailureMessage(new Checked() {
            public void run() throws Exception { outOpenAdapter.validate(outOpenContext, outOpenCommand); }
        }, "出库打开");
        expectFailureMessage(new Checked() {
            public void run() throws Exception { outOpenAdapter.execute(outOpenContext, outOpenCommand); }
        }, "出库打开");
        check(outOpenStub.totalCalls() == 0, "未启用的出库打开不产生原生调用");
    }

    /** 10 个已启用动作：校验阶段零原生调用，执行阶段固定方法恰好一次，入参主键/原因与授权一致。 */
    private static void successfulActions() throws Exception {
        int verified = 0;
        for (int index = 0; index < MAPPING.length; index++) {
            String button = MAPPING[index][0];
            if (!SalesOtherNodeScope.isEnabled(NODE, button)) {
                continue;
            }
            verified++;
            String method = MAPPING[index][1];
            String granularity = MAPPING[index][2];
            String resultKind = MAPPING[index][3];
            Stub stub = new Stub();
            if ("ORDER_ARRAY".equals(resultKind)) {
                stub.result(method, new SaleOrderVO[] { orderVO(ORDER, GROUP, ORG, "SO-CODE-RETURNED",
                        new SaleOrderBVO[] { lineVO(LINE, ORDER, REASON) }) });
            } else {
                stub.result(method, new SaleOrderViewVO[] { viewVO(ORDER, ORG, LINE, REASON, "SO-CODE-RETURNED") });
            }
            SaleCloseOperationAdapter adapter = stub.adapter();
            OperationContext ctx = context(button, "UPDATE",
                    "BILL".equals(granularity) ? rootObjects() : rootAndLineObjects());
            SaleCloseCommand command = "BILL".equals(granularity)
                    ? billCommand(ORDER, ORG, REASON, REASON)
                    : rowCommand(ORDER, ORG, LINE, REASON, REASON);
            adapter.validate(ctx, command);
            check(stub.totalCalls() == 0, button + " 校验阶段不调用原生服务");
            Serializable result = adapter.execute(ctx, command);
            check(result != null, button + " 返回原生结果");
            check(stub.calls(method) == 1, button + " 原生固定方法恰好调用一次");
            check(stub.totalCalls() == 1, button + " 未调用其他原生方法");
            Object[] nativeArguments = stub.arguments(method);
            check(nativeArguments != null && nativeArguments.length >= 1, button + " 记录原生入参");
            if ("BILL".equals(granularity)) {
                check(nativeArguments[0] instanceof SaleOrderVO[], button + " 整单动作传入订单聚合");
                SaleOrderVO[] passed = (SaleOrderVO[]) nativeArguments[0];
                check(passed.length == 1 && ORDER.equals(passed[0].getPrimaryKey()), button + " 传入授权订单");
                check(passed[0].getChildrenVO() != null && passed[0].getChildrenVO().length == 1
                        && REASON.equals(passed[0].getChildrenVO()[0].getVclosereason()),
                        button + " 传入整单全部行且行原因与命令一致");
            } else if (method.endsWith("Row")) {
                check(nativeArguments[0] instanceof SaleOrderViewVO[], button + " 行开关传入行视图");
                SaleOrderViewVO[] passed = (SaleOrderViewVO[]) nativeArguments[0];
                check(passed.length == 1 && LINE.equals(passed[0].getBody().getPrimaryKey()),
                        button + " 传入授权行视图");
                check(passed[0].getHead() != null && ORDER.equals(passed[0].getHead().getPrimaryKey()),
                        button + " 行视图所属订单与授权一致");
            } else {
                check(nativeArguments[0] instanceof SaleOrderVO[], button + " 行级动作传入转换后的整单聚合");
                SaleOrderVO[] passed = (SaleOrderVO[]) nativeArguments[0];
                check(passed.length == 1 && ORDER.equals(passed[0].getPrimaryKey()),
                        button + " 转换后主键与授权订单一致");
                check(passed[0].getChildrenVO() != null && passed[0].getChildrenVO().length == 1
                        && LINE.equals(passed[0].getChildrenVO()[0].getPrimaryKey())
                        && REASON.equals(passed[0].getChildrenVO()[0].getVclosereason()),
                        button + " 转换保留授权行与行原因");
            }
            check(adapter.describeResult(ctx, result).size() == ctx.getObjects().size(),
                    button + " 结果引用覆盖全部授权对象");
        }
        check(verified == 10, "逐一验证 10 个已启用动作的原生调用");
    }

    /** 关闭/打开原因缺失、空白、首尾空白或与受影响行原因不一致时一律拒绝。 */
    private static void reasonChecks() throws Exception {
        SaleCloseOperationAdapter adapter = new Stub().adapter();
        final SaleCloseOperationAdapter target = adapter;
        expectFailure(new Checked() {
            public void run() throws Exception {
                target.validate(context("BillClose", "UPDATE", rootObjects()),
                        billCommand(ORDER, ORG, null, REASON));
            }
        });
        expectFailure(new Checked() {
            public void run() throws Exception {
                target.validate(context("BillClose", "UPDATE", rootObjects()),
                        billCommand(ORDER, ORG, "", REASON));
            }
        });
        expectFailure(new Checked() {
            public void run() throws Exception {
                target.validate(context("BillClose", "UPDATE", rootObjects()),
                        billCommand(ORDER, ORG, "   ", REASON));
            }
        });
        expectFailure(new Checked() {
            public void run() throws Exception {
                target.validate(context("BillClose", "UPDATE", rootObjects()),
                        billCommand(ORDER, ORG, " " + REASON, REASON));
            }
        });
        expectFailure(new Checked() {
            public void run() throws Exception {
                target.validate(context("BillClose", "UPDATE", rootObjects()),
                        billCommand(ORDER, ORG, REASON + " ", REASON));
            }
        });
        expectFailure(new Checked() {
            public void run() throws Exception {
                target.validate(context("BillClose", "UPDATE", rootObjects()),
                        billCommand(ORDER, ORG, REASON, "其它原因"));
            }
        });
        expectFailure(new Checked() {
            public void run() throws Exception {
                target.validate(context("LineClose", "UPDATE", rootAndLineObjects()),
                        rowCommand(ORDER, ORG, LINE, "其它原因", REASON));
            }
        });
        final SaleOrderVO bill = orderVO(ORDER, GROUP, ORG, "SO-CODE",
                new SaleOrderBVO[] { lineVO(LINE, ORDER, REASON) });
        expectFailure(new Checked() {
            public void run() throws Exception {
                target.validate(context("BillClose", "UPDATE", rootObjects()),
                        new SaleCloseCommand(new SaleOrderVO[] { bill },
                                new SaleOrderViewVO[] { viewVO(ORDER, ORG, LINE, REASON, "SO-CODE") }, REASON));
            }
        });
        expectFailure(new Checked() {
            public void run() throws Exception {
                target.validate(context("LineClose", "UPDATE", rootAndLineObjects()),
                        new SaleCloseCommand(new SaleOrderVO[] { bill }, null, REASON));
            }
        });
    }

    /** 串节点、未登记按钮、错动作域、多对象、多行、行归属错误、缺授权主键与载荷类型错误全部拒绝。 */
    private static void selectionRejections() throws Exception {
        SaleCloseOperationAdapter adapter = new Stub().adapter();
        final SaleCloseOperationAdapter target = adapter;
        // 串节点：销售订单维护 40060301 不属于本适配器。
        expectValidateFailure("40060301", "BillClose", "UPDATE", GROUP, ORG, rootObjects(),
                billCommand(ORDER, ORG, REASON, REASON));
        // 未登记按钮。
        expectValidateFailure(NODE, "Refresh", "UPDATE", GROUP, ORG, rootObjects(), null);
        // 错动作域：关闭不能以 INSERT 提交。
        expectValidateFailure(NODE, "BillClose", "INSERT", GROUP, ORG, rootObjects(),
                billCommand(ORDER, ORG, REASON, REASON));
        // 伪造组织/集团。
        expectValidateFailure(NODE, "BillClose", "UPDATE", GROUP, "   ", rootObjects(),
                billCommand(ORDER, ORG, REASON, REASON));
        expectValidateFailure(NODE, "BillClose", "UPDATE", "GROUP-OTHER", ORG, rootObjects(),
                billCommand(ORDER, ORG, REASON, REASON));
        // 多对象：两次根对象。
        List<BusinessObjectRef> twoOrders = new ArrayList<BusinessObjectRef>();
        twoOrders.add(root("c-root-1", ORDER, "SO-CODE"));
        twoOrders.add(root("c-root-2", ORDER_2, "SO-CODE-2"));
        expectValidateFailure(NODE, "BillClose", "UPDATE", GROUP, ORG, twoOrders,
                billCommand(ORDER, ORG, REASON, REASON));
        // 整单动作出现子对象。
        expectValidateFailure(NODE, "BillClose", "UPDATE", GROUP, ORG, rootAndLineObjects(),
                billCommand(ORDER, ORG, REASON, REASON));
        // 行动作授权多行。
        List<BusinessObjectRef> twoLines = new ArrayList<BusinessObjectRef>();
        twoLines.add(root("c-root", ORDER, "SO-CODE"));
        twoLines.add(line("c-line-1", LINE, "c-root"));
        twoLines.add(line("c-line-2", LINE_2, "c-root"));
        expectValidateFailure(NODE, "LineClose", "UPDATE", GROUP, ORG, twoLines,
                rowCommand(ORDER, ORG, LINE, REASON, REASON));
        // 行视图所属订单与授权订单不一致。
        expectValidateFailure(NODE, "LineClose", "UPDATE", GROUP, ORG, rootAndLineObjects(),
                rowCommand(ORDER_2, ORG, LINE, REASON, REASON));
        // 命令行不在授权行范围内。
        expectValidateFailure(NODE, "LineClose", "UPDATE", GROUP, ORG, rootAndLineObjects(),
                rowCommand(ORDER, ORG, LINE_2, REASON, REASON));
        // 子对象类型不是销售订单行。
        List<BusinessObjectRef> wrongChildType = new ArrayList<BusinessObjectRef>();
        wrongChildType.add(root("c-root", ORDER, "SO-CODE"));
        wrongChildType.add(new BusinessObjectRef("c-other", SalesOtherNodeScope.DELIVERY_LINE,
                LINE, null, "c-root"));
        expectValidateFailure(NODE, "LineClose", "UPDATE", GROUP, ORG, wrongChildType,
                rowCommand(ORDER, ORG, LINE, REASON, REASON));
        // 缺少已授权订单主键。
        List<BusinessObjectRef> missingKey = new ArrayList<BusinessObjectRef>();
        missingKey.add(root("c-root", null, "SO-CODE"));
        expectValidateFailure(NODE, "BillClose", "UPDATE", GROUP, ORG, missingKey,
                billCommand(ORDER, ORG, REASON, REASON));
        List<BusinessObjectRef> blankKey = new ArrayList<BusinessObjectRef>();
        blankKey.add(root("c-root", "   ", "SO-CODE"));
        expectValidateFailure(NODE, "BillClose", "UPDATE", GROUP, ORG, blankKey,
                billCommand(ORDER, ORG, REASON, REASON));
        // 多单命令、多行命令。
        final SaleOrderVO[] twoBills = new SaleOrderVO[] {
                orderVO(ORDER, GROUP, ORG, "SO-CODE", new SaleOrderBVO[] { lineVO(LINE, ORDER, REASON) }),
                orderVO(ORDER_2, GROUP, ORG, "SO-CODE-2",
                        new SaleOrderBVO[] { lineVO(LINE_2, ORDER_2, REASON) }) };
        expectValidateFailure(NODE, "BillClose", "UPDATE", GROUP, ORG, rootObjects(),
                new SaleCloseCommand(twoBills, null, REASON));
        final SaleOrderViewVO[] twoRows = new SaleOrderViewVO[] {
                viewVO(ORDER, ORG, LINE, REASON, "SO-CODE"),
                viewVO(ORDER, ORG, LINE_2, REASON, "SO-CODE") };
        expectValidateFailure(NODE, "LineClose", "UPDATE", GROUP, ORG, rootAndLineObjects(),
                new SaleCloseCommand(null, twoRows, REASON));
        // 命令类型错误。
        expectFailure(new Checked() {
            public void run() throws Exception {
                target.validate(context("BillClose", "UPDATE", rootObjects()), null);
            }
        });
        expectFailure(new Checked() {
            public void run() throws Exception {
                target.validate(context("BillClose", "UPDATE", rootObjects()), "not-a-command");
            }
        });
        // 行动作未授权行对象时仍按命令行的订单归属与母单约束执行（受控单笔路径允许不带子对象）。
        Stub stub = new Stub();
        stub.result("closeSaleOrderRow", new SaleOrderViewVO[] { viewVO(ORDER, ORG, LINE, REASON, "SO-CODE") });
        SaleCloseOperationAdapter rowAdapter = stub.adapter();
        rowAdapter.validate(context("LineClose", "UPDATE", rootObjects()),
                rowCommand(ORDER, ORG, LINE, REASON, REASON));
        rowAdapter.execute(context("LineClose", "UPDATE", rootObjects()),
                rowCommand(ORDER, ORG, LINE, REASON, REASON));
        check(stub.calls("closeSaleOrderRow") == 1, "未携带行对象的单笔行开关仍只调用原生一次");
    }

    /** 原生异常原样上抛且不重试；空/多余/重复/身份不符的结果一律拒绝。 */
    private static void nativeOutcomes() throws Exception {
        final Stub rejected = new Stub();
        rejected.failure("closeSaleOrder", new BusinessException("native rejected"));
        final SaleCloseOperationAdapter rejectedAdapter = rejected.adapter();
        expectFailureMessage(new Checked() {
            public void run() throws Exception {
                rejectedAdapter.execute(context("BillClose", "UPDATE", rootObjects()),
                        billCommand(ORDER, ORG, REASON, REASON));
            }
        }, "native rejected");
        check(rejected.calls("closeSaleOrder") == 1, "原生拒绝只调用一次、不自动重试");

        final Stub empty = new Stub();
        empty.result("closeSaleOrderRow", new SaleOrderViewVO[0]);
        final SaleCloseOperationAdapter emptyAdapter = empty.adapter();
        expectFailure(new Checked() {
            public void run() throws Exception {
                emptyAdapter.execute(context("LineClose", "UPDATE", rootAndLineObjects()),
                        rowCommand(ORDER, ORG, LINE, REASON, REASON));
            }
        });
        check(empty.calls("closeSaleOrderRow") == 1, "空结果仍只调用原生一次");

        final Stub mismatched = new Stub();
        mismatched.result("closeSaleOrderRow",
                new SaleOrderViewVO[] { viewVO(ORDER_2, ORG, LINE, REASON, "SO-CODE-2") });
        final SaleCloseOperationAdapter mismatchedAdapter = mismatched.adapter();
        expectFailure(new Checked() {
            public void run() throws Exception {
                mismatchedAdapter.execute(context("LineClose", "UPDATE", rootAndLineObjects()),
                        rowCommand(ORDER, ORG, LINE, REASON, REASON));
            }
        });
        check(mismatched.calls("closeSaleOrderRow") == 1, "身份不符结果仍只调用原生一次");

        final Stub duplicated = new Stub();
        final SaleOrderViewVO duplicateView = viewVO(ORDER, ORG, LINE, REASON, "SO-CODE");
        duplicated.result("closeSaleOrderRow", new SaleOrderViewVO[] { duplicateView, duplicateView });
        final SaleCloseOperationAdapter duplicatedAdapter = duplicated.adapter();
        expectFailure(new Checked() {
            public void run() throws Exception {
                duplicatedAdapter.execute(context("LineClose", "UPDATE", rootAndLineObjects()),
                        rowCommand(ORDER, ORG, LINE, REASON, REASON));
            }
        });
        check(duplicated.calls("closeSaleOrderRow") == 1, "重复结果仍只调用原生一次");

        final Stub nullBill = new Stub();
        nullBill.result("closeSaleOrder", null);
        final SaleCloseOperationAdapter nullBillAdapter = nullBill.adapter();
        expectFailure(new Checked() {
            public void run() throws Exception {
                nullBillAdapter.execute(context("BillClose", "UPDATE", rootObjects()),
                        billCommand(ORDER, ORG, REASON, REASON));
            }
        });
        check(nullBill.calls("closeSaleOrder") == 1, "空整单结果仍只调用原生一次");

        final Stub noLines = new Stub();
        noLines.result("closeSaleOrder", new SaleOrderVO[] { orderVO(ORDER, GROUP, ORG, "SO-CODE", null) });
        final SaleCloseOperationAdapter noLinesAdapter = noLines.adapter();
        expectFailure(new Checked() {
            public void run() throws Exception {
                noLinesAdapter.execute(context("BillClose", "UPDATE", rootObjects()),
                        billCommand(ORDER, ORG, REASON, REASON));
            }
        });
        check(noLines.calls("closeSaleOrder") == 1, "缺少整单行的结果仍只调用原生一次");
    }

    /** describeResult 按授权对象逐个返回引用：clientObjectId 对齐、根对象回填真实单号、行对象编码为空。 */
    private static void describeResults() throws Exception {
        Stub stub = new Stub();
        stub.result("closeSaleOrder", new SaleOrderVO[] { orderVO(ORDER, GROUP, ORG, "RETURNED-CODE",
                new SaleOrderBVO[] { lineVO(LINE, ORDER, REASON) }) });
        SaleCloseOperationAdapter adapter = stub.adapter();
        final OperationContext billContext = context("BillClose", "UPDATE", rootObjects());
        SaleCloseCommand billCommand = billCommand(ORDER, ORG, REASON, REASON);
        List<BusinessObjectRef> refs = adapter.describeResult(billContext,
                adapter.execute(billContext, billCommand));
        check(refs.size() == 1, "整单结果引用覆盖授权根对象");
        BusinessObjectRef rootRef = refs.get(0);
        check("c-root".equals(rootRef.getClientObjectId()), "根对象客户端标识保持不变");
        check(SalesOtherNodeScope.SALE_ORDER.equals(rootRef.getObjectType()), "根对象类型为销售订单");
        check(ORDER.equals(rootRef.getObjectPk()), "根对象回填授权订单主键");
        check("RETURNED-CODE".equals(rootRef.getBillCode()), "根对象单号取原生返回表头");
        check(rootRef.getParentClientObjectId() == null, "根对象无父引用");

        Stub rowStub = new Stub();
        rowStub.result("closeSaleOrderRow",
                new SaleOrderViewVO[] { viewVO(ORDER, ORG, LINE, REASON, "VIEW-CODE") });
        SaleCloseOperationAdapter rowAdapter = rowStub.adapter();
        final OperationContext rowContext = context("LineClose", "UPDATE", rootAndLineObjects());
        SaleCloseCommand rowCommand = rowCommand(ORDER, ORG, LINE, REASON, REASON);
        List<BusinessObjectRef> rowRefs = rowAdapter.describeResult(rowContext,
                rowAdapter.execute(rowContext, rowCommand));
        check(rowRefs.size() == 2, "行级结果引用覆盖根对象与行对象");
        check("c-root".equals(rowRefs.get(0).getClientObjectId())
                && SalesOtherNodeScope.SALE_ORDER.equals(rowRefs.get(0).getObjectType())
                && ORDER.equals(rowRefs.get(0).getObjectPk()), "行级结果根对象客户端标识与主键对齐");
        check("VIEW-CODE".equals(rowRefs.get(0).getBillCode()), "行级结果根对象单号取返回行视图表头");
        check("c-line".equals(rowRefs.get(1).getClientObjectId())
                && SalesOtherNodeScope.SALE_ORDER_LINE.equals(rowRefs.get(1).getObjectType())
                && LINE.equals(rowRefs.get(1).getObjectPk()), "行级结果行对象客户端标识与主键对齐");
        check(rowRefs.get(1).getBillCode() == null, "行级结果行对象编码为空");
        check("c-root".equals(rowRefs.get(1).getParentClientObjectId()), "行级结果行对象父引用对齐");
        final SaleCloseOperationAdapter finalAdapter = adapter;
        final OperationContext finalContext = billContext;
        expectFailure(new Checked() {
            public void run() throws Exception { finalAdapter.describeResult(finalContext, null); }
        });
    }

    // ------------------------------------------------------------------
    // 离线假原生服务与 VO 夹具（不访问 NC 元数据、数据库或事务）
    // ------------------------------------------------------------------

    /** 假原生关闭管理服务：记录每个方法的调用次数与最后一次入参，可注入异常与返回。 */
    private static final class Stub {
        private final Map<String, Integer> calls = new HashMap<String, Integer>();
        private final Map<String, Object[]> arguments = new HashMap<String, Object[]>();
        private final Map<String, Object> results = new HashMap<String, Object>();
        private final Map<String, Throwable> failures = new HashMap<String, Throwable>();
        private final ISaleOrderCloseManageMaintain service = (ISaleOrderCloseManageMaintain) Proxy.newProxyInstance(
                ISaleOrderCloseManageMaintain.class.getClassLoader(),
                new Class<?>[] { ISaleOrderCloseManageMaintain.class }, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if ("hashCode".equals(name)) { return Integer.valueOf(System.identityHashCode(proxy)); }
                        if ("equals".equals(name)) { return Boolean.valueOf(proxy == args[0]); }
                        if ("toString".equals(name)) { return "OFFLINE_CLOSE_MANAGE_FIXTURE"; }
                        Integer count = calls.get(name);
                        calls.put(name, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
                        arguments.put(name, args);
                        Throwable failure = failures.get(name);
                        if (failure != null) { throw failure; }
                        return results.get(name);
                    }
                });

        void result(String method, Object value) { results.put(method, value); }

        void failure(String method, Throwable error) { failures.put(method, error); }

        int calls(String method) {
            Integer count = calls.get(method);
            return count == null ? 0 : count.intValue();
        }

        int totalCalls() {
            int total = 0;
            for (Integer count : calls.values()) { total += count.intValue(); }
            return total;
        }

        Object[] arguments(String method) { return arguments.get(method); }

        SaleCloseOperationAdapter adapter() {
            return new SaleCloseOperationAdapter() {
                @Override protected ISaleOrderCloseManageMaintain closeManage() { return service; }
            };
        }
    }

    private static String[] mappingOf(String button) {
        for (int index = 0; index < MAPPING.length; index++) {
            if (MAPPING[index][0].equals(button)) { return MAPPING[index]; }
        }
        throw new IllegalArgumentException("unregistered button " + button);
    }

    private static SaleCloseActionRoute routeFor(String button) throws Exception {
        String granularity = mappingOf(button)[2];
        return SaleCloseActionRoute.forContext(context(button, "UPDATE",
                "BILL".equals(granularity) ? rootObjects() : rootAndLineObjects()));
    }

    private static OperationContext context(String button, String operation, List<BusinessObjectRef> objects) {
        return context(NODE, button, operation, GROUP, ORG, objects);
    }

    private static OperationContext context(String node, String button, String operation, String group,
            String org, List<BusinessObjectRef> objects) {
        return new OperationContext("REQ-CLOSE-1", "BATCH-CLOSE-1", group, org, node, "销售订单关闭管理",
                button, SalesOtherNodeScope.buttonName(button), operation, null, objects);
    }

    private static List<BusinessObjectRef> rootObjects() {
        return Collections.singletonList(root("c-root", ORDER, "SO-CODE"));
    }

    private static List<BusinessObjectRef> rootAndLineObjects() {
        List<BusinessObjectRef> objects = new ArrayList<BusinessObjectRef>();
        objects.add(root("c-root", ORDER, "SO-CODE"));
        objects.add(line("c-line", LINE, "c-root"));
        return objects;
    }

    private static BusinessObjectRef root(String clientId, String objectPk, String billCode) {
        return new BusinessObjectRef(clientId, SalesOtherNodeScope.SALE_ORDER, objectPk, billCode, null);
    }

    private static BusinessObjectRef line(String clientId, String objectPk, String parentClientId) {
        return new BusinessObjectRef(clientId, SalesOtherNodeScope.SALE_ORDER_LINE, objectPk, null,
                parentClientId);
    }

    private static SaleCloseCommand billCommand(String orderPk, String org, String reason, String lineReason) {
        return new SaleCloseCommand(new SaleOrderVO[] { orderVO(orderPk, GROUP, org, "SO-CODE",
                new SaleOrderBVO[] { lineVO(LINE, orderPk, lineReason) }) }, null, reason);
    }

    private static SaleCloseCommand rowCommand(String orderPk, String org, String linePk, String lineReason,
            String reason) {
        return new SaleCloseCommand(null,
                new SaleOrderViewVO[] { viewVO(orderPk, org, linePk, lineReason, "SO-CODE") }, reason);
    }

    private static SaleOrderBVO lineVO(final String linePk, final String orderPk, final String reason) {
        return new SaleOrderBVO() {
            public String getPrimaryKey() { return linePk; }
            public String getCsaleorderbid() { return linePk; }
            public String getCsaleorderid() { return orderPk; }
            public String getVclosereason() { return reason; }
        };
    }

    private static SaleOrderHVO headerVO(final String orderPk, final String group, final String org,
            final String code) {
        return new SaleOrderHVO() {
            public String getPrimaryKey() { return orderPk; }
            public String getCsaleorderid() { return orderPk; }
            public String getPk_group() { return group; }
            public String getPk_org() { return org; }
            public String getVbillcode() { return code; }
        };
    }

    private static SaleOrderVO orderVO(final String orderPk, final String group, final String org,
            final String code, final SaleOrderBVO[] lines) {
        return new SaleOrderVO() {
            public IBillMeta getMetaData() { return FIXTURE_META; }
            public String getPrimaryKey() { return orderPk; }
            public SaleOrderHVO getParentVO() { return headerVO(orderPk, group, org, code); }
            public SaleOrderBVO[] getChildrenVO() { return lines; }
        };
    }

    private static SaleOrderViewVO viewVO(final String orderPk, final String org, final String linePk,
            final String reason, final String code) {
        final SaleOrderHVO head = headerVO(orderPk, GROUP, org, code);
        final SaleOrderBVO body = lineVO(linePk, orderPk, reason);
        final SaleOrderVO converted = orderVO(orderPk, GROUP, org, code, new SaleOrderBVO[] { body });
        return new SaleOrderViewVO() {
            public SaleOrderHVO getHead() { return head; }
            public SaleOrderBVO getBody() { return body; }
            public SaleOrderVO changeToSaleOrderVO() { return converted; }
        };
    }

    /** 关闭管理夹具只需满足 IBillMeta 的最小契约，避免离线环境加载 NC 元数据/定位器。 */
    private static final IBillMeta FIXTURE_META = new IBillMeta() {
        public Map<String, Object> getBusinessAttribute() { return Collections.emptyMap(); }
        public IAttributeMeta[] getChildForeignKeys() { return new IAttributeMeta[0]; }
        public IVOMeta[] getChildren() { return new IVOMeta[0]; }
        public String getComponentName() { return "OFFLINE_CLOSE_MANAGE_FIXTURE"; }
        public IVOMeta getParent() { throw new UnsupportedOperationException(); }
        public Class<? extends ISuperVO> getVOClass(IVOMeta meta) { throw new UnsupportedOperationException(); }
        public IVOMeta getVOMeta(Class<? extends ISuperVO> type) { throw new UnsupportedOperationException(); }
    };

    private static void expectValidateFailure(String node, final String button, final String operation,
            final String group, final String org, final List<BusinessObjectRef> objects,
            final SaleCloseCommand command) throws Exception {
        final SaleCloseOperationAdapter adapter = new Stub().adapter();
        final OperationContext context = context(node, button, operation, group, org, objects);
        expectFailure(new Checked() {
            public void run() throws Exception { adapter.validate(context, command); }
        });
    }

    private static void expectFailure(final Checked action) throws Exception {
        expectFailureMessage(action, null);
    }

    private static void expectFailureMessage(final Checked action, String fragment) throws Exception {
        try {
            action.run();
        } catch (BusinessException error) {
            if (fragment != null && (error.getMessage() == null || error.getMessage().indexOf(fragment) < 0)) {
                throw new AssertionError("unexpected rejection message: " + error.getMessage());
            }
            assertions++;
            return;
        }
        throw new AssertionError("invalid close-manage request was accepted");
    }

    private static void check(boolean ok, String message) {
        if (!ok) { throw new AssertionError(message); }
        assertions++;
    }
}
