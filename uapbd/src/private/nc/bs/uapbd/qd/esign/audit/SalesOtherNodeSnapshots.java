package nc.bs.uapbd.qd.esign.audit;

import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.SalesOtherNodeScope;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/**
 * 销售管理其他三节点（关闭管理 / 发货单 / 销售发票）的物理快照采集。
 *
 * <p>只覆盖已核实的真实头/体表与主键列：关闭管理复用销售订单聚合
 * （{@code so_saleorder} + {@code so_saleorder_b}），发货单为
 * {@code so_delivery} + {@code so_delivery_b}（BMF {@code modules/so/METADATA/so_delivery/delivery.bmf} 静态核实），
 * 销售发票为 {@code so_saleinvoice} + {@code so_saleinvoice_b}（命名规则推断，现场须只读核对）。
 * 根对象采集头+全部行；行对象单独采集对应行，用于证明行动作的实际影响范围。
 * 本类不自行提交事务、不修改数据，缺失或越权快照一律抛错使业务回滚。</p>
 */
public enum SalesOtherNodeSnapshots {
    /** 销售订单关闭管理：根=销售订单，行=销售订单行。 */
    SALE_ORDER_CLOSE("so_saleorder", "csaleorderid", SalesOtherNodeScope.SALE_ORDER,
            "so_saleorder_b", "csaleorderbid", SalesOtherNodeScope.SALE_ORDER_LINE),
    /** 发货单维护。 */
    DELIVERY("so_delivery", "cdeliveryid", SalesOtherNodeScope.DELIVERY,
            "so_delivery_b", "cdeliverybid", SalesOtherNodeScope.DELIVERY_LINE),
    /** 销售发票维护。 */
    SALE_INVOICE("so_saleinvoice", "csaleinvoiceid", SalesOtherNodeScope.SALE_INVOICE,
            "so_saleinvoice_b", "csaleinvoicebid", SalesOtherNodeScope.SALE_INVOICE_LINE);

    private final String rootTable;
    private final String rootKey;
    private final String rootType;
    private final String lineTable;
    private final String lineKey;
    private final String lineType;

    SalesOtherNodeSnapshots(String rootTable, String rootKey, String rootType, String lineTable, String lineKey,
            String lineType) {
        this.rootTable = rootTable;
        this.rootKey = rootKey;
        this.rootType = rootType;
        this.lineTable = lineTable;
        this.lineKey = lineKey;
        this.lineType = lineType;
    }

    /** 按适配接口识别快照范围；非本批适配器或非写入动作返回 null。 */
    public static SalesOtherNodeSnapshots forAdapter(String adapter, String operation) {
        if (!"INSERT".equals(operation) && !"UPDATE".equals(operation) && !"DELETE".equals(operation)) {
            return null;
        }
        if (!SalesOtherNodeScope.isAdapter(adapter)) { return null; }
        String objectType = SalesOtherNodeScope.objectTypeForAdapter(adapter);
        if (SalesOtherNodeScope.SALE_ORDER.equals(objectType)) { return SALE_ORDER_CLOSE; }
        if (SalesOtherNodeScope.DELIVERY.equals(objectType)) { return DELIVERY; }
        if (SalesOtherNodeScope.SALE_INVOICE.equals(objectType)) { return SALE_INVOICE; }
        return null;
    }

    /** 执行前权威快照：根对象加锁读头+全部行，行对象加锁读对应行；新增动作不采集。 */
    public void before(AuditCaptureScope scope, OperationContext context) throws BusinessException {
        if ("INSERT".equals(context.getOperationType())) { return; }
        capture(scope, context, true);
    }

    /** 执行后快照：删除动作核对对象确实失效，其他动作按主键重读并核对集团/组织。 */
    public void after(AuditCaptureScope scope, OperationContext context, List<BusinessObjectRef> result)
            throws BusinessException {
        capture(scope, context, false);
    }

    private void capture(AuditCaptureScope scope, OperationContext context, boolean lock)
            throws BusinessException {
        PhysicalSnapshotReader reader = new PhysicalSnapshotReader();
        String rootPk = null;
        for (BusinessObjectRef ref : context.getObjects()) {
            if (ref == null || ref.getObjectPk() == null || ref.getObjectPk().trim().length() == 0) {
                throw new BusinessException("快照对象缺少已持久化主键。");
            }
            if (ref.getParentClientObjectId() == null) {
                if (rootPk != null || !rootType.equals(ref.getObjectType())) {
                    throw new BusinessException("快照只接受一个已核实的根对象。");
                }
                rootPk = ref.getObjectPk();
            }
        }
        if (rootPk == null) { throw new BusinessException("快照缺少已核实的根对象。"); }
        for (BusinessObjectRef ref : context.getObjects()) {
            if (ref.getParentClientObjectId() == null) {
                captureRoot(reader, scope, context, ref, lock);
            } else {
                if (!lineType.equals(ref.getObjectType())) {
                    throw new BusinessException("快照行对象类型不受支持。");
                }
                captureLine(reader, scope, context, ref, rootPk, lock);
            }
        }
    }

    private void captureRoot(PhysicalSnapshotReader reader, AuditCaptureScope scope, OperationContext context,
            BusinessObjectRef ref, boolean lock) throws BusinessException {
        String pk = ref.getObjectPk();
        if (!lock && "DELETE".equals(context.getOperationType())) {
            if (!reader.readByPk(rootTable, rootKey, pk).isEmpty()) {
                throw new BusinessException("原生删除后授权单据仍然有效。");
            }
            return;
        }
        List<SnapshotRow> rows = lock ? reader.readLocked(rootTable, rootKey, null, rootKey, pk, true)
                : reader.readByPk(rootTable, rootKey, pk);
        if (rows.size() != 1) { throw new BusinessException("授权单据不存在或主键不唯一。"); }
        if (!context.getGroupId().equals(rows.get(0).getValues().get("pk_group"))
                || !context.getOrgId().equals(rows.get(0).getValues().get("pk_org"))) {
            throw new BusinessException("持久化单据超出授权集团或业务组织。");
        }
        rows.addAll(details(reader, lineTable, lineKey, rootKey, pk, lock));
        if (lock) { scope.before(ref.getClientObjectId(), rows); } else { scope.after(ref.getClientObjectId(), rows); }
    }

    private void captureLine(PhysicalSnapshotReader reader, AuditCaptureScope scope, OperationContext context,
            BusinessObjectRef ref, String rootPk, boolean lock) throws BusinessException {
        String pk = ref.getObjectPk();
        if (!lock && "DELETE".equals(context.getOperationType())) {
            if (!reader.readByPk(lineTable, lineKey, pk).isEmpty()) {
                throw new BusinessException("原生删除后授权单据行仍然有效。");
            }
            return;
        }
        List<SnapshotRow> rows = lock ? reader.readLocked(lineTable, lineKey, null, lineKey, pk, true)
                : reader.readByPk(lineTable, lineKey, pk);
        if (rows.size() != 1) { throw new BusinessException("授权单据行不存在或主键不唯一。"); }
        Object parent = rows.get(0).getValues().get(rootKey);
        if (!rootPk.equals(parent)) { throw new BusinessException("授权单据行不属于已授权根对象。"); }
        List<SnapshotRow> header = reader.readByPk(rootTable, rootKey, rootPk);
        if (header.size() != 1) { throw new BusinessException("授权单据表头不存在。"); }
        if (!context.getGroupId().equals(header.get(0).getValues().get("pk_group"))
                || !context.getOrgId().equals(header.get(0).getValues().get("pk_org"))) {
            throw new BusinessException("持久化单据超出授权集团或业务组织。");
        }
        if (lock) { scope.before(ref.getClientObjectId(), rows); } else { scope.after(ref.getClientObjectId(), rows); }
    }

    private List<SnapshotRow> details(PhysicalSnapshotReader reader, String childTable, String childKey,
            String parentKey, String pk, boolean lock) throws BusinessException {
        return lock ? reader.readLocked(childTable, childKey, parentKey, parentKey, pk, true)
                : reader.read(childTable, childKey, parentKey, parentKey, pk, true);
    }
}
