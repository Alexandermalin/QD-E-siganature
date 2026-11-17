package nc.vo.pu.qd.esign;

import java.io.Serializable;
import java.util.HashMap;

import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pub.workflownote.WorkflownoteVO;

/** The native PF arguments captured at the purchase-order Save boundary. */
public final class PurchaseOrderPfCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String actionName;
    private final String billType;
    private final WorkflownoteVO note;
    private final OrderVO order;
    private final Object userObject;
    private final HashMap<?, ?> parameters;

    public PurchaseOrderPfCommand(String actionName, String billType, WorkflownoteVO note, OrderVO order,
            Object userObject, HashMap<?, ?> parameters) {
        this.actionName = actionName;
        this.billType = billType;
        this.note = note;
        this.order = order;
        this.userObject = userObject;
        this.parameters = parameters == null ? null : new HashMap<Object, Object>(parameters);
    }

    public String getActionName() {
        return actionName;
    }

    public String getBillType() {
        return billType;
    }

    public WorkflownoteVO getNote() {
        return note;
    }

    public OrderVO getOrder() {
        return order;
    }

    public Object getUserObject() {
        return userObject;
    }

    public HashMap<?, ?> getParameters() {
        return parameters == null ? null : new HashMap<Object, Object>(parameters);
    }
}
