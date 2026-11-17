package nc.vo.purp.qd.esign;

import java.io.Serializable;
import nc.vo.bd.meta.BatchOperateVO;

public final class SupplierPriceBatchCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String OBJECT_TYPE = SupplierPriceOperationCommand.OBJECT_TYPE;
    public static final String BATCH_SAVE = "BATCH_SAVE";
    public static final String DELETE = SupplierPriceOperationCommand.DELETE;

    private final BatchOperateVO batch;
    private final String[] addClientObjectIds;

    /** The client object IDs correspond to the original add rows in native batch order. */
    public SupplierPriceBatchCommand(BatchOperateVO batch, String[] addClientObjectIds) {
        this.batch = batch;
        this.addClientObjectIds = addClientObjectIds == null ? null : addClientObjectIds.clone();
    }

    public BatchOperateVO getBatch() {
        return batch;
    }

    public String[] getAddClientObjectIds() {
        return addClientObjectIds == null ? new String[0] : addClientObjectIds.clone();
    }
}
