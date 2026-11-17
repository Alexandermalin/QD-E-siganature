package nc.ui.uapbd.qd.esign;

import java.util.List;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;

/**
 * 销售管理其他三个节点的选择范围判定。
 *
 * <p>本批不写死某个试点组织：单据是否受控由服务端策略（组织 + 节点 + 按钮）决定，客户端只在
 * 组织归属明确且未混合时进入受控路径。混合组织或无法判定归属必须拒绝，禁止回落原生写入绕过签名；
 * 多选进入受控路径后由一次性交接与适配器按单笔约束拒绝，等待批量方案通过后再解锁。</p>
 */
final class SalesNodeSelection {
    /** 选择判定结果。 */
    enum Kind {
        /** 全部属于已核实集团且只有一张单据。 */
        CONTROLLED_SINGLE,
        /** 全部属于已核实集团但多于一张单据；未完成批量验收前受控路径应拒绝执行。 */
        CONTROLLED_MULTI,
        /** 单据组织混合；必须拒绝，避免原生写入绕过签名。 */
        REJECT_MIXED,
        /** 集团或组织缺失、为空；无法判定归属时必须拒绝。 */
        REJECT_UNKNOWN
    }

    private SalesNodeSelection() { }

    /** 按集团与单据业务组织判定选择范围；任一组织缺失视为无法判定。 */
    static Kind classify(String group, List<String> organizations) {
        if (group == null || group.trim().length() == 0 || organizations == null || organizations.isEmpty()) {
            return Kind.REJECT_UNKNOWN;
        }
        String first = null;
        for (String org : organizations) {
            if (org == null || org.trim().length() == 0 || !org.equals(org.trim())) { return Kind.REJECT_UNKNOWN; }
            if (!SalesOtherNodeScope.supports(group, org)) { return Kind.REJECT_UNKNOWN; }
            if (first == null) { first = org; } else if (!first.equals(org)) { return Kind.REJECT_MIXED; }
        }
        return organizations.size() == 1 ? Kind.CONTROLLED_SINGLE : Kind.CONTROLLED_MULTI;
    }

    /** 受控单笔或受控多选都需要进入签名拦截；是否允许执行由交接与适配器按单笔约束判定。 */
    static boolean isControlled(Kind kind) {
        return kind == Kind.CONTROLLED_SINGLE || kind == Kind.CONTROLLED_MULTI;
    }

    /** 拒绝场景的固定提示，说明原因且不提供降级到原生写入的入口。 */
    static String rejectionMessage(Kind kind) {
        if (kind == Kind.REJECT_MIXED) {
            return "所选单据包含不同业务组织，受控操作不允许混合组织，请分开处理。";
        }
        return "无法确认所选单据的集团或业务组织归属，受控操作已拒绝，请重新查询后再处理。";
    }
}
