package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import nc.vo.pub.SuperVO;

/** Typed payload for a single native tree save. */
public final class ArchiveTreeSaveCommand implements Serializable {
    private static final long serialVersionUID=1L;
    private final SuperVO value;
    public ArchiveTreeSaveCommand(SuperVO value) {
        if(value==null) throw new IllegalArgumentException("Tree archive is required");
        this.value=value;
    }
    public SuperVO getValue() { return value; }
}
