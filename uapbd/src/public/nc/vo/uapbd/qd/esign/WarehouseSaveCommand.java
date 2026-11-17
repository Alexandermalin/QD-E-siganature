package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import nc.vo.bd.stordoc.StordocVO;

/** The native warehouse card including its agent-store rows. */
public final class WarehouseSaveCommand implements Serializable {
    private static final long serialVersionUID=1L;
    private final StordocVO value;
    private final String copySourceWarehouse,copySourceAddress;
    public WarehouseSaveCommand(StordocVO value) { this(value,null,null); }
    public WarehouseSaveCommand(StordocVO value,String copySourceWarehouse,String copySourceAddress) {
        this.value=value;this.copySourceWarehouse=copySourceWarehouse;this.copySourceAddress=copySourceAddress;
    }
    public StordocVO getValue() { return value; }
    public String getCopySourceWarehouse() { return copySourceWarehouse; }
    public String getCopySourceAddress() { return copySourceAddress; }
}
