package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import nc.vo.pub.SuperVO;

public final class PartnerOrgInfoCommand implements Serializable {
    private static final long serialVersionUID=1L;
    private final SuperVO value;
    public PartnerOrgInfoCommand(SuperVO value) { this.value=value; }
    public SuperVO getValue() { return value; }
}
