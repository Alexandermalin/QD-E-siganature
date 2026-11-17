package nc.test.qd.esign;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;

/**
 * 销售管理其他三节点范围白名单的离线契约检查。
 *
 * <p>只核对固定表：28 个正式清单范围 ID、节点/按钮/动作域/对象类型/可执行开关与站点点位一致；
 * 不调用 NC、数据库或事务，不代表真实按钮已注册或已联调。</p>
 */
public final class SalesOtherNodeScopeTests {
    private interface Checked { void run() throws Exception; }
    private static int assertions;

    private static final String GROUP = "0001A9100000000005MH";
    private static final String ORG = "0001A9100000003UDS6R";

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        nodesAndCounts();
        scopeIdentifiers();
        operationDomains();
        enablement();
        organizationAndAdapter();
        names();
        System.out.println("PASS SalesOtherNodeScopeTests assertions=" + assertions
                + " (fixed 28-row scope table; no NC or database invoked)");
    }

    /** 三个节点都存在，按钮数量为正式清单的 12 / 10 / 6。 */
    private static void nodesAndCounts() throws Exception {
        check(SalesOtherNodeScope.isNode("40060303"), "close node is registered");
        check(SalesOtherNodeScope.isNode("40060402"), "delivery node is registered");
        check(SalesOtherNodeScope.isNode("40060501"), "invoice node is registered");
        check(!SalesOtherNodeScope.isNode("40060301"), "sales-order node is not part of this batch");
        check(!SalesOtherNodeScope.isNode(null), "null node is rejected");
        check(SalesOtherNodeScope.buttonScopes(SalesOtherNodeScope.CLOSE_NODE).size() == 12,
                "close management has twelve rows");
        check(SalesOtherNodeScope.buttonScopes(SalesOtherNodeScope.DELIVERY_NODE).size() == 10,
                "delivery has ten rows");
        check(SalesOtherNodeScope.buttonScopes(SalesOtherNodeScope.INVOICE_NODE).size() == 6,
                "sale invoice has six rows");
        check(SalesOtherNodeScope.allButtonScopes().size() == 3, "exactly three nodes are registered");
        check("SO_SALE_ORDER".equals(SalesOtherNodeScope.objectType(SalesOtherNodeScope.CLOSE_NODE)),
                "close management targets the sale order aggregate");
        check("SO_DELIVERY".equals(SalesOtherNodeScope.objectType(SalesOtherNodeScope.DELIVERY_NODE)),
                "delivery targets the delivery aggregate");
        check("SO_SALE_INVOICE".equals(SalesOtherNodeScope.objectType(SalesOtherNodeScope.INVOICE_NODE)),
                "invoice targets the sale invoice aggregate");
        check("SALEORGTYPE000000000".equals(SalesOtherNodeScope.orgTypeCode(SalesOtherNodeScope.CLOSE_NODE)),
                "close management registers as a sales organization node");
        check("TRAFFICORGTYPE000000".equals(SalesOtherNodeScope.orgTypeCode(SalesOtherNodeScope.DELIVERY_NODE)),
                "delivery registers as a traffic organization node");
        check("FINANCEORGTYPE000000".equals(SalesOtherNodeScope.orgTypeCode(SalesOtherNodeScope.INVOICE_NODE)),
                "invoice registers as a finance organization node");
    }

    /** 28 个正式清单范围 ID 与运营清单逐字一致且互不重复。 */
    private static void scopeIdentifiers() throws Exception {
        String[][] rows = {
            { "40060303", "BillClose", "QD-ES-6DA27F33C45CD3B0" },
            { "40060303", "BillOpen", "QD-ES-4A1493B793E22F6D" },
            { "40060303", "LineClose", "QD-ES-F598A0B4A16EFE84" },
            { "40060303", "LineOpen", "QD-ES-FF713F12789B45A1" },
            { "40060303", "SendClose", "QD-ES-887831FDC8C23F76" },
            { "40060303", "SendOpen", "QD-ES-8A7BC4FA7E1631FB" },
            { "40060303", "OutClose", "QD-ES-32542641C9DC8398" },
            { "40060303", "OutOpen", "QD-ES-64B839C61D768D19" },
            { "40060303", "InvoiceClose", "QD-ES-9985A340EB7EFFC4" },
            { "40060303", "InvoiceOpen", "QD-ES-C6E139BBEF4B8BE6" },
            { "40060303", "SettleClose", "QD-ES-BA71E137623CEC43" },
            { "40060303", "SettleOpen", "QD-ES-42EE6E523F56E9D0" },
            { "40060402", "Save", "QD-ES-04ECAF336F8932CA" },
            { "40060402", "Delete", "QD-ES-FF81305763F3CF1F" },
            { "40060402", "Commit", "QD-ES-D494A8A893DDBC0B" },
            { "40060402", "UnCommit", "QD-ES-14232F4638F48C8C" },
            { "40060402", "Approve", "QD-ES-257B45A6A4F80EF6" },
            { "40060402", "UnApprove", "QD-ES-7DA00D0A9032E168" },
            { "40060402", "BillClose", "QD-ES-52A72C09C2BE1F47" },
            { "40060402", "BillOpen", "QD-ES-61C46C0C9ED0BC7D" },
            { "40060402", "OutClose", "QD-ES-AF1994D66608BEF1" },
            { "40060402", "OutOpen", "QD-ES-5275CF042A861E3D" },
            { "40060501", "Save", "QD-ES-176DC431DD4F5803" },
            { "40060501", "Delete", "QD-ES-8F8846F2D6E1ECD0" },
            { "40060501", "Commit", "QD-ES-56499B75D3E91C33" },
            { "40060501", "UnCommit", "QD-ES-29895E169F92EB91" },
            { "40060501", "Approve", "QD-ES-9AE255EF674BD8E2" },
            { "40060501", "UnApprove", "QD-ES-5EBFFEF0FFE24CAA" }
        };
        check(rows.length == 28, "the acceptance table has twenty-eight rows");
        Set<String> unique = new HashSet<String>();
        for (String[] row : rows) {
            check(SalesOtherNodeScope.isPilotButton(row[0], row[1]), "button is registered: " + row[0] + "/" + row[1]);
            check(row[2].equals(SalesOtherNodeScope.scopeIdFor(row[0], row[1])),
                    "scope id matches the formal list: " + row[2]);
            check(row[1].equals(SalesOtherNodeScope.buttonForScope(row[2])),
                    "scope id maps back to its button: " + row[2]);
            check(unique.add(row[2]), "scope id is unique: " + row[2]);
        }
        check(unique.size() == 28, "all twenty-eight scope ids are unique");
        // 同名按钮在不同节点必须各自登记，不能共享一个授权。
        check(SalesOtherNodeScope.scopeIdFor("40060303", "Delete") == null,
                "close management has no delete button scope");
        check(SalesOtherNodeScope.scopeIdFor("40060402", "Delete") != null,
                "delivery delete has its own scope");
        check(!SalesOtherNodeScope.scopeIdFor("40060402", "Delete").equals(
                SalesOtherNodeScope.scopeIdFor("40060501", "Delete")),
                "delivery and invoice delete never share one scope");
        check(SalesOtherNodeScope.isPilotButton("40060303", "LineClose"), "line close is close-management only");
        check(!SalesOtherNodeScope.isPilotButton("40060501", "LineClose"), "invoice has no line close button");
    }

    /** 动作域固定：保存归 INSERT、删除归 DELETE、其余状态变更归 UPDATE。 */
    private static void operationDomains() throws Exception {
        check("INSERT".equals(SalesOtherNodeScope.operationTypeFor("40060402", "Save")), "delivery save is INSERT");
        check("INSERT".equals(SalesOtherNodeScope.operationTypeFor("40060501", "Save")), "invoice save is INSERT");
        check("DELETE".equals(SalesOtherNodeScope.operationTypeFor("40060402", "Delete")), "delivery delete is DELETE");
        check("DELETE".equals(SalesOtherNodeScope.operationTypeFor("40060501", "Delete")), "invoice delete is DELETE");
        check("UPDATE".equals(SalesOtherNodeScope.operationTypeFor("40060303", "BillClose")), "bill close is UPDATE");
        check("UPDATE".equals(SalesOtherNodeScope.operationTypeFor("40060402", "OutClose")), "delivery out close is UPDATE");
        check("UPDATE".equals(SalesOtherNodeScope.operationTypeFor("40060501", "Approve")), "invoice approve is UPDATE");
        check(SalesOtherNodeScope.operationTypeFor("40060303", "Unknown") == null, "unknown button has no domain");
        check(SalesOtherNodeScope.supportsOperation("40060303", "BillClose", "UPDATE"), "operation domain matches");
        check(!SalesOtherNodeScope.supportsOperation("40060303", "BillClose", "DELETE"), "wrong domain is rejected");
        check(!SalesOtherNodeScope.supportsOperation("40060402", "Commit", "SAVE"), "PF action name is not a domain");
    }

    /** 可执行开关：需要原生恢复协议的打开动作、入口歧义的删除动作保持关闭。 */
    private static void enablement() throws Exception {
        check(SalesOtherNodeScope.isEnabled("40060303", "BillClose"), "bill close is enabled");
        check(SalesOtherNodeScope.isEnabled("40060303", "LineClose"), "line close is enabled");
        check(SalesOtherNodeScope.isEnabled("40060303", "SendClose"), "send close is enabled");
        check(SalesOtherNodeScope.isEnabled("40060303", "OutClose"), "out close is enabled");
        check(SalesOtherNodeScope.isEnabled("40060303", "InvoiceClose"), "invoice close is enabled");
        check(SalesOtherNodeScope.isEnabled("40060303", "SettleClose"), "settle close is enabled");
        check(SalesOtherNodeScope.isEnabled("40060303", "LineOpen"), "line open is enabled");
        check(SalesOtherNodeScope.isEnabled("40060303", "SendOpen"), "send open is enabled");
        check(SalesOtherNodeScope.isEnabled("40060303", "InvoiceOpen"), "invoice open is enabled");
        check(SalesOtherNodeScope.isEnabled("40060303", "SettleOpen"), "settle open is enabled");
        check(!SalesOtherNodeScope.isEnabled("40060303", "BillOpen"), "bill open waits for the ATP resume protocol");
        check(!SalesOtherNodeScope.isEnabled("40060303", "OutOpen"), "out open waits for the ATP resume protocol");
        check(SalesOtherNodeScope.isEnabled("40060402", "Save"), "delivery save is enabled");
        check(!SalesOtherNodeScope.isEnabled("40060402", "Delete"), "delivery delete waits for the confirmation order");
        check(SalesOtherNodeScope.isEnabled("40060402", "Commit"), "delivery commit is enabled");
        check(SalesOtherNodeScope.isEnabled("40060402", "UnCommit"), "delivery uncommit is enabled");
        check(SalesOtherNodeScope.isEnabled("40060402", "Approve"), "delivery approve is enabled");
        check(SalesOtherNodeScope.isEnabled("40060402", "UnApprove"), "delivery unapprove is enabled");
        check(SalesOtherNodeScope.isEnabled("40060402", "BillClose"), "delivery bill close is enabled");
        check(!SalesOtherNodeScope.isEnabled("40060402", "BillOpen"), "delivery bill open waits for resume");
        check(SalesOtherNodeScope.isEnabled("40060402", "OutClose"), "delivery row close is enabled");
        check(!SalesOtherNodeScope.isEnabled("40060402", "OutOpen"), "delivery row open waits for resume");
        check(SalesOtherNodeScope.isEnabled("40060501", "Save"), "invoice save is enabled");
        check(!SalesOtherNodeScope.isEnabled("40060501", "Delete"), "invoice delete waits for the entry decision");
        check(SalesOtherNodeScope.isEnabled("40060501", "Commit"), "invoice commit is enabled");
        check(SalesOtherNodeScope.isEnabled("40060501", "UnCommit"), "invoice uncommit is enabled");
        check(SalesOtherNodeScope.isEnabled("40060501", "Approve"), "invoice approve is enabled");
        check(SalesOtherNodeScope.isEnabled("40060501", "UnApprove"), "invoice unapprove is enabled");
        check(SalesOtherNodeScope.enabledButtons("40060303").size() == 12, "close management lists twelve buttons");
        check(SalesOtherNodeScope.enabledButtons("40060402").size() == 10, "delivery lists ten buttons");
        check(SalesOtherNodeScope.enabledButtons("40060501").size() == 6, "invoice lists six buttons");
        check(!SalesOtherNodeScope.isEnabled("40060303", "Unknown"), "unknown button is never enabled");
    }

    /** 组织口径：只接受已核实集团且业务组织非空、无首尾空白。 */
    private static void organizationAndAdapter() throws Exception {
        check(SalesOtherNodeScope.supports(GROUP, ORG), "verified group and organization are accepted");
        check(!SalesOtherNodeScope.supports(GROUP, " "), "blank organization is rejected");
        check(!SalesOtherNodeScope.supports(GROUP, " " + ORG), "padded organization is rejected");
        check(!SalesOtherNodeScope.supports(GROUP, null), "null organization is rejected");
        check(!SalesOtherNodeScope.supports("OTHER", ORG), "unknown group is rejected");
        check(!SalesOtherNodeScope.supports(null, ORG), "missing group is rejected");
        check(SalesOtherNodeScope.supportsAction(GROUP, ORG, "40060303", "BillClose", "UPDATE"),
                "complete action tuple is accepted");
        check(!SalesOtherNodeScope.supportsAction(GROUP, ORG, "40060303", "BillClose", "DELETE"),
                "action tuple with wrong domain is rejected");
        check(!SalesOtherNodeScope.supportsAction(GROUP, ORG, "40060303", "Delete", "DELETE"),
                "close management has no delete button");
        check(SalesOtherNodeScope.isAdapter("nc.itf.so.qd.esign.ISaleCloseOperationAdapter"), "close adapter is known");
        check(SalesOtherNodeScope.isAdapter("nc.itf.so.qd.esign.IDeliveryOperationAdapter"), "delivery adapter is known");
        check(SalesOtherNodeScope.isAdapter("nc.itf.so.qd.esign.ISaleInvoiceOperationAdapter"), "invoice adapter is known");
        check(!SalesOtherNodeScope.isAdapter("nc.itf.so.qd.esign.ISaleOrderOperationAdapter"),
                "the sales-order adapter is not part of this batch");
        check("SO_SALE_ORDER".equals(SalesOtherNodeScope.objectTypeForAdapter(
                "nc.itf.so.qd.esign.ISaleCloseOperationAdapter")), "close adapter object type is fixed");
        check("SO_DELIVERY_B".equals(SalesOtherNodeScope.lineObjectTypeForAdapter(
                "nc.itf.so.qd.esign.IDeliveryOperationAdapter")), "delivery line object type is fixed");
        check(SalesOtherNodeScope.objectTypeForAdapter("unknown") == null, "unknown adapter has no object type");
    }

    /** 中文名称用于日志与审计区分真实节点/动作。 */
    private static void names() throws Exception {
        check("销售订单关闭管理".equals(SalesOtherNodeScope.nodeName("40060303")), "close node name");
        check("发货单维护".equals(SalesOtherNodeScope.nodeName("40060402")), "delivery node name");
        check("销售发票维护".equals(SalesOtherNodeScope.nodeName("40060501")), "invoice node name");
        check("整单关闭".equals(SalesOtherNodeScope.buttonName("BillClose")), "bill close name");
        check("出库打开".equals(SalesOtherNodeScope.buttonName("OutOpen")), "out open name");
        check("审批".equals(SalesOtherNodeScope.buttonName("Approve")), "approve name");
        check("Unknown".equals(SalesOtherNodeScope.buttonName("Unknown")), "unknown button keeps its code");
        Map<String, String> scopes = SalesOtherNodeScope.allButtonScopes().get("40060501");
        check(scopes != null && scopes.size() == 6, "invoice scope map is exposed read-only");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new IllegalStateException("FAILED: " + message); }
    }
}
