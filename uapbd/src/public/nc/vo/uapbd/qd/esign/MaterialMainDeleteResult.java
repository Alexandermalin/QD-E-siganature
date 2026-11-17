package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import nc.vo.bd.material.MaterialVO;

/** 物料主档删除的业务返回值；图片目录未在业务事务内删除，imageCleanupPending 表示已登记提交后清理任务。 */
public final class MaterialMainDeleteResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 已删除的物料主档数据，主键为授权对象主键。 */
    private final MaterialVO material;
    /** 业务与审计提交后是否存在待清理的图片目录；无图片时为 false。 */
    private final boolean imageCleanupPending;

    public MaterialMainDeleteResult(MaterialVO material,boolean imageCleanupPending) {
        this.material = material;
        this.imageCleanupPending = imageCleanupPending;
    }

    public MaterialVO getMaterial() { return material; }
    public boolean isImageCleanupPending() { return imageCleanupPending; }
}
