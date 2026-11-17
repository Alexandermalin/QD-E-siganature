package nc.ui.uapbd.qd.esign;

import java.util.List;
import nc.vo.uapbd.qd.esign.SaleOrderPilotScope;

/**
 * 销售订单选择范围判定：用于区分"受控单笔、受控多选、原生、必须拒绝"四种状态。
 *
 * <p>混合组织与无法判定归属的选择必须拒绝，禁止通过原生写入绕开签名；只有全部落在首轮 990 组织的
 * 选择才进入受控路径（多选在批量方案通过前由交接与适配器单对象约束拒绝）。</p>
 */
final class SaleOrderSelection {
    /** 选择判定结果。 */
    enum Kind {
        /** 全部为首轮组织且只有一张单据。 */
        CONTROLLED_SINGLE,
        /** 全部为首轮组织但多于一张单据；未完成批量验收前受控路径应拒绝。 */
        CONTROLLED_MULTI,
        /** 全部不在首轮组织；保持原生行为。 */
        NATIVE,
        /** 首轮组织与非首轮组织混合；必须拒绝，避免原生写入绕过签名。 */
        REJECT_MIXED,
        /** 组织缺失或为空；无法判定归属时必须拒绝。 */
        REJECT_UNKNOWN
    }

    private SaleOrderSelection() { }

    /** 按集团与单据销售组织判定选择范围；组织缺失视为无法判定。 */
    static Kind classify(String group, List<String> organizations) {
        if (group == null || group.trim().length() == 0 || organizations == null || organizations.isEmpty()) {
            return Kind.REJECT_UNKNOWN;
        }
        int pilot = 0;
        int outside = 0;
        for (String org : organizations) {
            if (org == null || org.trim().length() == 0) { return Kind.REJECT_UNKNOWN; }
            if (SaleOrderPilotScope.supports(group, org)) { pilot++; } else { outside++; }
        }
        if (pilot > 0 && outside > 0) { return Kind.REJECT_MIXED; }
        if (outside > 0) { return Kind.NATIVE; }
        return organizations.size() == 1 ? Kind.CONTROLLED_SINGLE : Kind.CONTROLLED_MULTI;
    }

    /** 受控单笔或受控多选都需要进入签名拦截；是否允许执行由交接与适配器按单笔约束判定。 */
    static boolean isControlled(Kind kind) {
        return kind == Kind.CONTROLLED_SINGLE || kind == Kind.CONTROLLED_MULTI;
    }

    /** 拒绝场景的固定提示，说明原因且不提供降级到原生写入的入口。 */
    static String rejectionMessage(Kind kind) {
        if (kind == Kind.REJECT_MIXED) {
            return "所选销售订单包含首轮试点组织之外的订单，受控操作不允许混合组织，请分开处理。";
        }
        return "无法确认所选销售订单的组织归属，受控操作已拒绝，请重新查询后再处理。";
    }
}
