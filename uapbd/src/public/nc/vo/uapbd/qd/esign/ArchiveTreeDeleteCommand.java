package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import nc.vo.pub.SuperVO;

/** The selected native tree row, checked against the persisted request on the server. */
public final class ArchiveTreeDeleteCommand implements Serializable {
    private static final long serialVersionUID=1L;
    private final SuperVO value;
    public ArchiveTreeDeleteCommand(SuperVO value) {
        if(value==null) throw new IllegalArgumentException("Tree archive is required");this.value=value;
    }
    public SuperVO getValue() { return value; }
}
