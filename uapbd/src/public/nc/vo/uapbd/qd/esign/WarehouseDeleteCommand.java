package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import nc.vo.bd.stordoc.StordocVO;

/** One native warehouse deletion unit with its original optimistic version. */
public final class WarehouseDeleteCommand implements Serializable {
    private static final long serialVersionUID=1L;
    private final StordocVO value;
    public WarehouseDeleteCommand(StordocVO value) { this.value=value; }
    public StordocVO getValue() { return value; }
}
