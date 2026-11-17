package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import nc.vo.pub.SuperVO;

/** Carries the selected native root; the server determines its actual descendants. */
public final class ArchiveTreeDisableCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final SuperVO root;
    public ArchiveTreeDisableCommand(SuperVO root) { this.root = root; }
    public SuperVO getRoot() { return root; }
}
