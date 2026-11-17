package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import nc.vo.pub.SuperVO;

/** The selected classification; policy ownership is resolved again by the server. */
public final class ArchiveTreeEnableCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final SuperVO root;
    public ArchiveTreeEnableCommand(SuperVO root) { this.root=root; }
    public SuperVO getRoot() { return root; }
}
