package nc.vo.pu.qd.esign;

import java.io.Serializable;
import java.util.HashMap;

import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;

/** Native PF arguments for one controlled procurement bill operation. */
public final class ProcurementBillPfCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String actionName;
    private final String billType;
    private final WorkflownoteVO note;
    private final AbstractBill bill;
    private final Object userObject;
    private final HashMap<?, ?> parameters;

    public ProcurementBillPfCommand(String actionName, String billType, WorkflownoteVO note, AbstractBill bill,
            Object userObject, HashMap<?, ?> parameters) {
        this.actionName = actionName;
        this.billType = billType;
        this.note = note;
        this.bill = bill;
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

    public AbstractBill getBill() {
        return bill;
    }

    public Object getUserObject() {
        return userObject;
    }

    public HashMap<?, ?> getParameters() {
        return parameters == null ? null : new HashMap<Object, Object>(parameters);
    }
}
