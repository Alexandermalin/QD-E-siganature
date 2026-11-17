package nc.vo.so.qd.esign;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.so.m30.entity.SaleOrderVO;

/** One native sales-order WRITE invocation, after the client-side PF preparation. */
public final class SaleOrderPfSaveCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String billType;
    private final SaleOrderVO bill;
    private final PfUserObject userObject;
    private final WorkflownoteVO workflowNote;
    private final HashMap<String, Object> parameters;

    public SaleOrderPfSaveCommand(String billType, SaleOrderVO bill, PfUserObject userObject,
            WorkflownoteVO workflowNote, Map<String, Object> parameters) {
        this.billType = billType;
        this.bill = bill;
        this.userObject = userObject;
        this.workflowNote = workflowNote;
        this.parameters = parameters == null ? new HashMap<String, Object>() : new HashMap<String, Object>(parameters);
    }

    public String getBillType() { return billType; }
    public SaleOrderVO getBill() { return bill; }
    public PfUserObject getUserObject() { return userObject; }
    public WorkflownoteVO getWorkflowNote() { return workflowNote; }
    public HashMap<String, Object> getParameters() { return new HashMap<String, Object>(parameters); }
}
