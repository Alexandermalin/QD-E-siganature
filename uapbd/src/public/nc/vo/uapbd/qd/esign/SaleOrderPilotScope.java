package nc.vo.uapbd.qd.esign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 销售订单签名试点范围：首轮组织、节点、按钮与动作域的固定白名单，客户端与服务端共用同一判定口径。 */
public final class SaleOrderPilotScope {
    /** 首轮集团主键。 */
    public static final String GROUP_ID = "0001A9100000000005MH";
    /** 首轮 990 销售组织主键。 */
    public static final String ORG_ID = "0001A9100000003UDS6R";
    /** 既有手工新增保存试点的范围 ID（正式清单第 208 行）。 */
    public static final String SCOPE_ID = "QD-ES-EAF88AC5CAF75442";
    /** 销售订单维护功能节点编码。 */
    public static final String NODE_CODE = "40060301";
    /** 既有保存试点的真实按钮编码。 */
    public static final String SAVE_BUTTON = "Save";

    /** 已核实按钮到正式清单范围 ID 的固定映射；顺序稳定，供策略维护与预检逐条输出。 */
    private static final Map<String, String> BUTTON_SCOPES;

    /** 已接入受控执行（服务端固定分支已完成并通过离线用例）的按钮；未接入的不得开启策略。 */
    private static final Map<String, Boolean> IMPLEMENTED;

    static {
        Map<String, String> scopes = new LinkedHashMap<String, String>();
        scopes.put(SAVE_BUTTON, SCOPE_ID);
        scopes.put("Delete", "QD-ES-FEFBC5B292C330AD");
        scopes.put("Commit", "QD-ES-4671CC2E70A34B97");
        scopes.put("UnCommit", "QD-ES-4F23D38FB22BA45F");
        scopes.put("Approve", "QD-ES-9C0B035FFEB2110D");
        scopes.put("UnApprove", "QD-ES-51BB76212FFCD156");
        scopes.put("Freeze", "QD-ES-C7E51B3ED69F8B05");
        scopes.put("UnFreeze", "QD-ES-BF1F44B944C037B3");
        scopes.put("BillClose", "QD-ES-2A439AB1FE3628B0");
        BUTTON_SCOPES = Collections.unmodifiableMap(scopes);

        Map<String, Boolean> implemented = new LinkedHashMap<String, Boolean>();
        implemented.put(SAVE_BUTTON, Boolean.TRUE);
        implemented.put("Commit", Boolean.TRUE);
        implemented.put("UnCommit", Boolean.TRUE);
        implemented.put("Approve", Boolean.TRUE);
        implemented.put("UnApprove", Boolean.TRUE);
        implemented.put("Delete", Boolean.TRUE);
        implemented.put("Freeze", Boolean.TRUE);
        implemented.put("UnFreeze", Boolean.TRUE);
        implemented.put("BillClose", Boolean.TRUE);
        IMPLEMENTED = Collections.unmodifiableMap(implemented);
    }

    private SaleOrderPilotScope() { }

    /** 首轮受控范围只接受已核实的集团与 990 销售组织组合。 */
    public static boolean supports(String group, String org) {
        return GROUP_ID.equals(group) && ORG_ID.equals(org);
    }

    /** 节点必须是销售订单维护；其他节点不属于本试点。 */
    public static boolean isPilotNode(String node) {
        return NODE_CODE.equals(node);
    }

    /** 按钮必须出现在已核实白名单中；未列入的按钮一律不受控。 */
    public static boolean isPilotButton(String node, String button) {
        return isPilotNode(node) && button != null && BUTTON_SCOPES.containsKey(button);
    }

    /** 返回按钮对应的正式清单范围 ID，未核实按钮返回 null。 */
    public static String scopeIdFor(String node, String button) {
        return isPilotNode(node) ? BUTTON_SCOPES.get(button) : null;
    }

    /** 返回已核实按钮到范围 ID 的只读映射，供策略维护与只读预检按固定顺序逐条核对。 */
    public static Map<String, String> buttonScopes() {
        return BUTTON_SCOPES;
    }

    /** 该按钮是否已完成受控接入：未接入的按钮即使登记在范围内也不得开启签名/审计策略。 */
    public static boolean isImplemented(String node, String button) {
        return isPilotButton(node, button) && Boolean.TRUE.equals(IMPLEMENTED.get(button));
    }

    /** 返回已接入受控执行的按钮集合（只读），供策略包装器与预检核对。 */
    public static Map<String, Boolean> implementedButtons() {
        return IMPLEMENTED;
    }

    /** 由正式清单范围 ID 反查按钮编码；未登记返回 null。 */
    public static String buttonForScope(String scopeId) {
        if (scopeId == null) { return null; }
        for (Map.Entry<String, String> entry : BUTTON_SCOPES.entrySet()) {
            if (scopeId.equals(entry.getValue())) { return entry.getKey(); }
        }
        return null;
    }

    /** 按钮在映射中对应的动作域：删除归 DELETE，保存归 INSERT，其余状态变更归 UPDATE。 */
    public static String operationTypeFor(String node, String button) {
        if (!isPilotButton(node, button)) { return null; }
        if ("Delete".equals(button)) { return "DELETE"; }
        if (SAVE_BUTTON.equals(button)) { return "INSERT"; }
        return "UPDATE";
    }

    /** 动作域必须与按钮的固定归类一致，禁止借同一按钮提交其他动作。 */
    public static boolean supportsOperation(String node, String button, String operationType) {
        String expected = operationTypeFor(node, button);
        return expected != null && expected.equals(operationType);
    }

    /** 受控动作要求组织、节点、按钮与动作域同时命中白名单。 */
    public static boolean supportsAction(String group, String org, String node, String button,
            String operationType) {
        return supports(group, org) && isPilotButton(node, button)
                && supportsOperation(node, button, operationType);
    }

    /** 返回按钮的中文名称，用于审计与日志中区分真实业务动作；未知按钮返回按钮编码本身。 */
    public static String buttonName(String button) {
        if (SAVE_BUTTON.equals(button)) { return "保存"; }
        if ("Delete".equals(button)) { return "删除"; }
        if ("Commit".equals(button)) { return "提交"; }
        if ("UnCommit".equals(button)) { return "收回"; }
        if ("Approve".equals(button)) { return "审批"; }
        if ("UnApprove".equals(button)) { return "取消审批"; }
        if ("Freeze".equals(button)) { return "冻结"; }
        if ("UnFreeze".equals(button)) { return "解冻"; }
        if ("BillClose".equals(button)) { return "整单关闭"; }
        return button;
    }
}
