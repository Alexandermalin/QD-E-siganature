package nc.vo.purp.qd.esign;

import java.io.Serializable;
import nc.vo.bd.meta.BatchOperateVO;

/** 供应商价格的原生批处理输入，保留原生行下标关系；当前适配器不启用混合操作或多对象批量。 */
public final class SupplierPriceOperationCommand implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;
    /** OBJECT_TYPE：当前类型化命令允许的固定业务对象类型。 */
    public static final String OBJECT_TYPE = "PURP_SUPPLIER_PRICE";
    /**
     * INSERT：新增业务动作标识。
     * UPDATE：修改业务动作标识。
     * DELETE：删除业务动作标识。
     */
    public static final String INSERT = "INSERT", UPDATE = "UPDATE", DELETE = "DELETE";
    /** batch：原生 BatchOperateVO 载荷，保留动作分组及行下标。 */
    private final BatchOperateVO batch;
    /** 保存原生供应商价格批处理对象，不重排其动作分组和行下标。 */
    public SupplierPriceOperationCommand(BatchOperateVO batch) { this.batch = batch; }
    /** 返回原生 BatchOperateVO 载荷，保留动作分组及行下标。 */
    public BatchOperateVO getBatch() { return batch; }
}
