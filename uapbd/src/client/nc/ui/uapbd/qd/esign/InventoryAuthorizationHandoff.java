package nc.ui.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.OperationContext;

/** One-time client handoff shared by inventory save and state actions. */
interface InventoryAuthorizationHandoff {
    boolean bind(OperationContext context);
    String dispatch(String group, String org, String pk) throws BusinessException;
    boolean wasDispatched(String requestId);
    void release(String requestId);
}
