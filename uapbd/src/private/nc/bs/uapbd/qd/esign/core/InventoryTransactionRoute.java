package nc.bs.uapbd.qd.esign.core;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.InventoryBillScope;
import nc.vo.uapbd.qd.esign.SapplyLineScope;

/** Identifies inventory routes with explicit transaction outcome handling. */
public final class InventoryTransactionRoute {
    private InventoryTransactionRoute() { }

    public static boolean matches(String node, String button, String operation) {
        return InventoryBillScope.matches(node,button,operation);
    }

    public static boolean isUncertain(boolean ownerMode, String node, String button, String operation, String code) {
        return "BUSINESS_OUTCOME_UNKNOWN".equals(code) && (ownerMode || matches(node, button, operation));
    }

    /** Fail before creating or signing a controlled request until native batch semantics are implemented. */
    public static void requireSupportedPreparation(OperationContext context, boolean ownerMode, PolicyDecision policy)
            throws BusinessException {
        EsignValues.require(context != null && policy != null, "库存操作上下文或策略缺失。");
        if ((ownerMode || policy.isControlled()) && matches(context.getNodeCode(), context.getButtonCode(),
                context.getOperationType())) {
            if (SapplyLineScope.matches(context.getNodeCode(),context.getButtonCode(),context.getOperationType())) {
                SapplyLineScope.require(context);
                return;
            }
            EsignValues.require(context.getObjects().size() == 1,
                    "当前库存受控操作仅支持单张单据；原生批量接入尚未完成。");
        }
    }
}
