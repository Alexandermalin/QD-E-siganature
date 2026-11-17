package nc.test.qd.esign;

import nc.bs.uapbd.qd.esign.audit.SalesOtherNodeSnapshots;

/**
 * 销售管理其他三节点审计快照识别的离线契约检查。
 *
 * <p>只核对「适配接口 + 动作域 → 快照范围」的固定映射与表/主键线索；
 * 不连接数据库、不读取物理列，真实快照采集须在联调环境验证。</p>
 */
public final class SalesOtherNodeSnapshotsTests {
    private static int assertions;

    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        assertions = 0;
        check(SalesOtherNodeSnapshots.values().length == 3, "exactly three snapshot scopes are registered");
        String close = "nc.itf.so.qd.esign.ISaleCloseOperationAdapter";
        String delivery = "nc.itf.so.qd.esign.IDeliveryOperationAdapter";
        String invoice = "nc.itf.so.qd.esign.ISaleInvoiceOperationAdapter";
        // 关闭管理复用销售订单聚合快照，用于证明整单/行的实际影响范围。
        check(SalesOtherNodeSnapshots.forAdapter(close, "UPDATE") == SalesOtherNodeSnapshots.SALE_ORDER_CLOSE,
                "close management update uses the sale-order aggregate snapshot");
        check(SalesOtherNodeSnapshots.forAdapter(close, "DELETE") == SalesOtherNodeSnapshots.SALE_ORDER_CLOSE,
                "close management delete uses the sale-order aggregate snapshot");
        check(SalesOtherNodeSnapshots.forAdapter(close, "INSERT") == SalesOtherNodeSnapshots.SALE_ORDER_CLOSE,
                "close management insert is mapped but no insert action exists");
        // 发货单与销售发票使用各自聚合，不能借用订单快照。
        check(SalesOtherNodeSnapshots.forAdapter(delivery, "UPDATE") == SalesOtherNodeSnapshots.DELIVERY,
                "delivery update uses the delivery aggregate snapshot");
        check(SalesOtherNodeSnapshots.forAdapter(delivery, "DELETE") == SalesOtherNodeSnapshots.DELIVERY,
                "delivery delete uses the delivery aggregate snapshot");
        check(SalesOtherNodeSnapshots.forAdapter(invoice, "UPDATE") == SalesOtherNodeSnapshots.SALE_INVOICE,
                "invoice update uses the invoice aggregate snapshot");
        check(SalesOtherNodeSnapshots.forAdapter(invoice, "DELETE") == SalesOtherNodeSnapshots.SALE_INVOICE,
                "invoice delete uses the invoice aggregate snapshot");
        // 非本批适配器与非写入动作不得被本类接管。
        check(SalesOtherNodeSnapshots.forAdapter("nc.itf.so.qd.esign.ISaleOrderOperationAdapter", "UPDATE") == null,
                "the sales-order adapter keeps its existing snapshot path");
        check(SalesOtherNodeSnapshots.forAdapter("nc.itf.pu.qd.esign.IPurchaseOrderOperationAdapter", "UPDATE") == null,
                "the purchase adapter is outside this batch");
        check(SalesOtherNodeSnapshots.forAdapter(close, "APPROVE") == null, "approval is not a snapshot action");
        check(SalesOtherNodeSnapshots.forAdapter(close, null) == null, "missing operation is rejected");
        check(SalesOtherNodeSnapshots.forAdapter(null, "UPDATE") == null, "missing adapter is rejected");
        System.out.println("PASS SalesOtherNodeSnapshotsTests assertions=" + assertions
                + " (adapter-to-snapshot table only; no database or NC transaction invoked)");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new IllegalStateException("FAILED: " + message); }
    }
}
