package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import nc.vo.pub.SuperVO;

/** 物料组织页签的原生修改载荷，授权范围由服务端重新核对。 */
public final class MaterialOrgInfoCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final SuperVO value;

    public MaterialOrgInfoCommand(SuperVO value) { this.value = value; }
    public SuperVO getValue() { return value; }
}
