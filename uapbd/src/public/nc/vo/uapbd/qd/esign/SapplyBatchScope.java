package nc.vo.uapbd.qd.esign;

/** Shared route and selection contract for outbound-request batches. */
public final class SapplyBatchScope {
    public static final String POLICY_MODE = "SAPPLY_BATCH";
    public static final String NODE = "40080801";

    private SapplyBatchScope() { }

    public static boolean supports(OperationContext context) {
        if (context == null || !NODE.equals(context.getNodeCode()) || context.getObjects().size() < 2) return false;
        String button = context.getButtonCode(), operation = context.getOperationType();
        boolean supported = "Delete".equals(button) && "DELETE".equals(operation)
                || ("Commit".equals(button) || "UnCommit".equals(button)
                    || "Approve".equals(button) || "UnApprove".equals(button)) && "UPDATE".equals(operation);
        if (!supported) return false;
        for (BusinessObjectRef ref : context.getObjects()) {
            String pk = ref.getObjectPk();
            if (!InventoryBillScope.SAPPLY.equals(ref.getObjectType()) || pk == null
                    || !pk.matches("[A-Za-z0-9_-]{1,20}") || ref.getParentClientObjectId() != null
                    || ref.getTargetOrg() != null || ref.getDesiredOwnerOrg() != null
                    || ref.getPolicyOwnerOrg() != null) return false;
        }
        return true;
    }
}
