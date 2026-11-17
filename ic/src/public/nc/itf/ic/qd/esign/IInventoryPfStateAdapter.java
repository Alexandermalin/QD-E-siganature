package nc.itf.ic.qd.esign;

import nc.itf.uapbd.qd.esign.spi.BusinessOperationAdapter;
import nc.vo.ic.qd.esign.InventoryPfStateCommand;
import nc.vo.ic.qd.esign.InventoryPfStateResult;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Local adapter for an inventory state action. */
public interface IInventoryPfStateAdapter extends BusinessOperationAdapter {
    InventoryPfStateResult executeSapplyUnit(OperationContext context, InventoryPfStateCommand command,
            String nativeAction, NativeCall nativeCall) throws Throwable;

    /** A local, synchronous call to the current NC PF invocation. */
    interface NativeCall {
        Object proceed() throws Throwable;
    }
}
