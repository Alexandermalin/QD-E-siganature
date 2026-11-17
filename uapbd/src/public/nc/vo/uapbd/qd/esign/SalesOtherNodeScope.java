package nc.vo.uapbd.qd.esign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 销售管理其余三个节点（销售订单关闭管理、发货单维护、销售发票维护）的固定范围白名单。
 *
 * <p>本类只登记已核实的节点编码、按钮编码、正式清单范围 ID、对象类型与动作域，并为每个按钮标注
 * 服务端分支是否已通过离线用例。它不替代网关的策略判定、组织权限或原生业务规则：
 * 客户端、网关和适配器共用同一份口径，未列入白名单的按钮一律拒绝，不回落原生写入。</p>
 *
 * <p>三个节点即使按钮同名也不共享授权：对象类型、原生落点与范围 ID 都各自登记在独立分支中，
 * 服务端必须按节点分别核对。</p>
 */
public final class SalesOtherNodeScope {
    /** 已核实的集团主键（与既有销售订单试点一致）。 */
    public static final String GROUP_ID = "0001A9100000000005MH";
    /** 销售订单关闭管理功能节点。 */
    public static final String CLOSE_NODE = "40060303";
    /** 发货单维护功能节点。 */
    public static final String DELIVERY_NODE = "40060402";
    /** 销售发票维护功能节点。 */
    public static final String INVOICE_NODE = "40060501";
    /** 销售订单关闭管理的对象类型（整单动作）。 */
    public static final String SALE_ORDER = "SO_SALE_ORDER";
    /** 销售订单关闭管理的行对象类型（行动作）。 */
    public static final String SALE_ORDER_LINE = "SO_SALE_ORDER_B";
    /** 发货单对象类型（整单动作）。 */
    public static final String DELIVERY = "SO_DELIVERY";
    /** 发货单行对象类型（行动作）。 */
    public static final String DELIVERY_LINE = "SO_DELIVERY_B";
    /** 销售发票对象类型。 */
    public static final String SALE_INVOICE = "SO_SALE_INVOICE";
    /** 销售发票行对象类型。 */
    public static final String SALE_INVOICE_LINE = "SO_SALE_INVOICE_B";
    /** 保存按钮编码（新增与修改共用）。 */
    public static final String SAVE_BUTTON = "Save";

    /** 节点到（按钮 → 正式清单范围 ID）的固定映射；顺序稳定，供预检与策略维护逐条输出。 */
    private static final Map<String, Map<String, String>> SCOPES;
    /** 节点到（按钮 → 动作域）的固定映射：保存归 INSERT、删除归 DELETE、其余状态变更归 UPDATE。 */
    private static final Map<String, Map<String, String>> OPERATIONS;
    /** 节点到（按钮 → 服务端分支状态）：TRUE 表示已完成受控接入并允许执行，FALSE 保持关闭。 */
    private static final Map<String, Map<String, Boolean>> ENABLED;
    /** 节点到注册组织类型编码：仅用于只读预检脚本按组织类型列出候选业务单元。 */
    private static final Map<String, String> ORG_TYPES;

    static {
        Map<String, Map<String, String>> scopes = new LinkedHashMap<String, Map<String, String>>();
        scopes.put(CLOSE_NODE, scopes(
                "BillClose", "QD-ES-6DA27F33C45CD3B0",
                "BillOpen", "QD-ES-4A1493B793E22F6D",
                "LineClose", "QD-ES-F598A0B4A16EFE84",
                "LineOpen", "QD-ES-FF713F12789B45A1",
                "SendClose", "QD-ES-887831FDC8C23F76",
                "SendOpen", "QD-ES-8A7BC4FA7E1631FB",
                "OutClose", "QD-ES-32542641C9DC8398",
                "OutOpen", "QD-ES-64B839C61D768D19",
                "InvoiceClose", "QD-ES-9985A340EB7EFFC4",
                "InvoiceOpen", "QD-ES-C6E139BBEF4B8BE6",
                "SettleClose", "QD-ES-BA71E137623CEC43",
                "SettleOpen", "QD-ES-42EE6E523F56E9D0"));
        scopes.put(DELIVERY_NODE, scopes(
                "Save", "QD-ES-04ECAF336F8932CA",
                "Delete", "QD-ES-FF81305763F3CF1F",
                "Commit", "QD-ES-D494A8A893DDBC0B",
                "UnCommit", "QD-ES-14232F4638F48C8C",
                "Approve", "QD-ES-257B45A6A4F80EF6",
                "UnApprove", "QD-ES-7DA00D0A9032E168",
                "BillClose", "QD-ES-52A72C09C2BE1F47",
                "BillOpen", "QD-ES-61C46C0C9ED0BC7D",
                "OutClose", "QD-ES-AF1994D66608BEF1",
                "OutOpen", "QD-ES-5275CF042A861E3D"));
        scopes.put(INVOICE_NODE, scopes(
                "Save", "QD-ES-176DC431DD4F5803",
                "Delete", "QD-ES-8F8846F2D6E1ECD0",
                "Commit", "QD-ES-56499B75D3E91C33",
                "UnCommit", "QD-ES-29895E169F92EB91",
                "Approve", "QD-ES-9AE255EF674BD8E2",
                "UnApprove", "QD-ES-5EBFFEF0FFE24CAA"));
        SCOPES = Collections.unmodifiableMap(scopes);

        Map<String, Map<String, String>> operations = new LinkedHashMap<String, Map<String, String>>();
        operations.put(CLOSE_NODE, operations(CLOSE_NODE, SAVE_BUTTON, "INSERT", "Delete", "DELETE"));
        operations.put(DELIVERY_NODE, operations(DELIVERY_NODE, SAVE_BUTTON, "INSERT", "Delete", "DELETE"));
        operations.put(INVOICE_NODE, operations(INVOICE_NODE, SAVE_BUTTON, "INSERT", "Delete", "DELETE"));
        OPERATIONS = Collections.unmodifiableMap(operations);

        // 首轮只开放已有明确静态落点且无需额外恢复协议的按钮；需要原生继续确认的「打开」分支
        // 与入口歧义未核实的删除分支保持关闭，等待真实 NC 会话核实后再逐项开启。
        Map<String, Map<String, Boolean>> enabled = new LinkedHashMap<String, Map<String, Boolean>>();
        enabled.put(CLOSE_NODE, enabled(
                "BillClose", true, "BillOpen", false,
                "LineClose", true, "LineOpen", true,
                "SendClose", true, "SendOpen", true,
                "OutClose", true, "OutOpen", false,
                "InvoiceClose", true, "InvoiceOpen", true,
                "SettleClose", true, "SettleOpen", true));
        enabled.put(DELIVERY_NODE, enabled(
                "Save", true, "Delete", false,
                "Commit", true, "UnCommit", true,
                "Approve", true, "UnApprove", true,
                "BillClose", true, "BillOpen", false,
                "OutClose", true, "OutOpen", false));
        enabled.put(INVOICE_NODE, enabled(
                "Save", true, "Delete", false,
                "Commit", true, "UnCommit", true,
                "Approve", true, "UnApprove", true));
        ENABLED = Collections.unmodifiableMap(enabled);

        Map<String, String> orgTypes = new LinkedHashMap<String, String>();
        orgTypes.put(CLOSE_NODE, "SALEORGTYPE000000000");
        orgTypes.put(DELIVERY_NODE, "TRAFFICORGTYPE000000");
        orgTypes.put(INVOICE_NODE, "FINANCEORGTYPE000000");
        ORG_TYPES = Collections.unmodifiableMap(orgTypes);
    }

    private SalesOtherNodeScope() { }

    /** 构造按钮 → 范围 ID 的固定有序映射，参数按「按钮, 范围 ID」成对给出。 */
    private static Map<String, String> scopes(String... pairs) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (int index = 0; index < pairs.length; index += 2) { values.put(pairs[index], pairs[index + 1]); }
        return values;
    }

    /** 构造按钮 → 动作域映射；显式给出的按钮使用指定动作域，其余按钮统一归 UPDATE。 */
    private static Map<String, String> operations(String node, String... overrides) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        Map<String, String> overridesMap = new LinkedHashMap<String, String>();
        for (int index = 0; index < overrides.length; index += 2) { overridesMap.put(overrides[index], overrides[index + 1]); }
        for (String button : SCOPES.get(node).keySet()) {
            String operation = overridesMap.get(button);
            values.put(button, operation == null ? "UPDATE" : operation);
        }
        return values;
    }

    /** 构造按钮 → 可执行状态映射，参数按「按钮, 布尔值」成对给出。 */
    private static Map<String, Boolean> enabled(Object... pairs) {
        Map<String, Boolean> values = new LinkedHashMap<String, Boolean>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], Boolean.valueOf(((Boolean) pairs[index + 1]).booleanValue()));
        }
        return values;
    }

    /** 该节点是否属于本批三个节点之一。 */
    public static boolean isNode(String node) {
        return node != null && SCOPES.containsKey(node);
    }

    /** 节点到对象类型的固定映射：整单动作使用根对象，行动作由调用方结合按钮补充行对象。 */
    public static String objectType(String node) {
        if (CLOSE_NODE.equals(node)) { return SALE_ORDER; }
        if (DELIVERY_NODE.equals(node)) { return DELIVERY; }
        if (INVOICE_NODE.equals(node)) { return SALE_INVOICE; }
        return null;
    }

    /** 服务端适配接口是否属于本批三节点，用于映射校验与审计快照识别。 */
    public static boolean isAdapter(String adapter) {
        return "nc.itf.so.qd.esign.ISaleCloseOperationAdapter".equals(adapter)
                || "nc.itf.so.qd.esign.IDeliveryOperationAdapter".equals(adapter)
                || "nc.itf.so.qd.esign.ISaleInvoiceOperationAdapter".equals(adapter);
    }

    /** 适配接口对应的根对象类型；非本批适配器返回 null。 */
    public static String objectTypeForAdapter(String adapter) {
        if ("nc.itf.so.qd.esign.ISaleCloseOperationAdapter".equals(adapter)) { return SALE_ORDER; }
        if ("nc.itf.so.qd.esign.IDeliveryOperationAdapter".equals(adapter)) { return DELIVERY; }
        if ("nc.itf.so.qd.esign.ISaleInvoiceOperationAdapter".equals(adapter)) { return SALE_INVOICE; }
        return null;
    }

    /** 适配接口对应的行对象类型；无行对象的适配器返回 null。 */
    public static String lineObjectTypeForAdapter(String adapter) {
        if ("nc.itf.so.qd.esign.ISaleCloseOperationAdapter".equals(adapter)) { return SALE_ORDER_LINE; }
        if ("nc.itf.so.qd.esign.IDeliveryOperationAdapter".equals(adapter)) { return DELIVERY_LINE; }
        if ("nc.itf.so.qd.esign.ISaleInvoiceOperationAdapter".equals(adapter)) { return SALE_INVOICE_LINE; }
        return null;
    }

    /** 行对象类型；无行动作的节点返回 null。 */
    public static String lineObjectType(String node) {
        if (CLOSE_NODE.equals(node)) { return SALE_ORDER_LINE; }
        if (DELIVERY_NODE.equals(node)) { return DELIVERY_LINE; }
        if (INVOICE_NODE.equals(node)) { return SALE_INVOICE_LINE; }
        return null;
    }

    /** 功能节点中文名称，用于日志与审计区分真实节点。 */
    public static String nodeName(String node) {
        if (CLOSE_NODE.equals(node)) { return "销售订单关闭管理"; }
        if (DELIVERY_NODE.equals(node)) { return "发货单维护"; }
        if (INVOICE_NODE.equals(node)) { return "销售发票维护"; }
        return node;
    }

    /** 注册组织类型编码，仅供只读预检按组织类型列候选业务单元。 */
    public static String orgTypeCode(String node) { return ORG_TYPES.get(node); }

    /** 按钮是否登记在该节点的固定白名单中。 */
    public static boolean isPilotButton(String node, String button) {
        Map<String, String> values = SCOPES.get(node);
        return values != null && button != null && values.containsKey(button);
    }

    /** 返回按钮对应的正式清单范围 ID，未登记返回 null。 */
    public static String scopeIdFor(String node, String button) {
        Map<String, String> values = SCOPES.get(node);
        return values == null ? null : values.get(button);
    }

    /** 由正式清单范围 ID 反查按钮编码；未登记返回 null。 */
    public static String buttonForScope(String scopeId) {
        if (scopeId == null) { return null; }
        for (Map<String, String> values : SCOPES.values()) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (scopeId.equals(entry.getValue())) { return entry.getKey(); }
            }
        }
        return null;
    }

    /** 返回节点下按钮到范围 ID 的只读映射，供预检与策略维护按固定顺序核对。 */
    public static Map<String, String> buttonScopes(String node) {
        Map<String, String> values = SCOPES.get(node);
        return values == null ? Collections.<String, String>emptyMap() : values;
    }

    /** 返回全部节点编码的只读集合，供逐个节点输出预检结果。 */
    public static Map<String, Map<String, String>> allButtonScopes() { return SCOPES; }

    /** 按钮在映射中登记的动作域；未登记返回 null。 */
    public static String operationTypeFor(String node, String button) {
        Map<String, String> values = OPERATIONS.get(node);
        return values == null ? null : values.get(button);
    }

    /** 动作域必须与按钮的固定归类一致，禁止借同一按钮提交其他动作。 */
    public static boolean supportsOperation(String node, String button, String operationType) {
        String expected = operationTypeFor(node, button);
        return expected != null && expected.equals(operationType);
    }

    /** 该按钮的服务端分支是否已完成受控接入并允许执行；未接入的动作必须执行前拒绝。 */
    public static boolean isEnabled(String node, String button) {
        Map<String, Boolean> values = ENABLED.get(node);
        return values != null && Boolean.TRUE.equals(values.get(button));
    }

    /** 返回节点下按钮到可执行状态的只读映射，供预检与文档逐项输出。 */
    public static Map<String, Boolean> enabledButtons(String node) {
        Map<String, Boolean> values = ENABLED.get(node);
        return values == null ? Collections.<String, Boolean>emptyMap() : values;
    }

    /** 受控动作只在已核实的集团内生效；业务组织必须由服务端会话或持久化对象确定，不能为空。 */
    public static boolean supports(String group, String org) {
        return GROUP_ID.equals(group) && org != null && org.trim().length() > 0 && org.equals(org.trim());
    }

    /** 受控动作要求组织、节点、按钮与动作域同时命中白名单。 */
    public static boolean supportsAction(String group, String org, String node, String button,
            String operationType) {
        return supports(group, org) && isPilotButton(node, button)
                && supportsOperation(node, button, operationType);
    }

    /** 返回按钮的中文名称；同名按钮在不同节点按该节点所属业务对象解释。 */
    public static String buttonName(String button) {
        if (SAVE_BUTTON.equals(button)) { return "保存"; }
        if ("Delete".equals(button)) { return "删除"; }
        if ("Commit".equals(button)) { return "提交"; }
        if ("UnCommit".equals(button)) { return "收回"; }
        if ("Approve".equals(button)) { return "审批"; }
        if ("UnApprove".equals(button)) { return "取消审批"; }
        if ("BillClose".equals(button)) { return "整单关闭"; }
        if ("BillOpen".equals(button)) { return "整单打开"; }
        if ("LineClose".equals(button) || "RowClose".equals(button)) { return "行关闭"; }
        if ("LineOpen".equals(button) || "RowOpen".equals(button)) { return "行打开"; }
        if ("SendClose".equals(button)) { return "发货关闭"; }
        if ("SendOpen".equals(button)) { return "发货打开"; }
        if ("OutClose".equals(button)) { return "出库关闭"; }
        if ("OutOpen".equals(button)) { return "出库打开"; }
        if ("InvoiceClose".equals(button)) { return "开票关闭"; }
        if ("InvoiceOpen".equals(button)) { return "开票打开"; }
        if ("SettleClose".equals(button)) { return "结算关闭"; }
        if ("SettleOpen".equals(button)) { return "结算打开"; }
        return button;
    }
}
