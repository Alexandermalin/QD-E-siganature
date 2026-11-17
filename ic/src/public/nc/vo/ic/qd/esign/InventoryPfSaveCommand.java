package nc.vo.ic.qd.esign;

import java.util.HashMap;
import java.util.Map;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.ic.pub.pf.ICPFParameter;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.uapbd.qd.esign.InventoryNativeCommand;

/** One inventory WRITE invocation with the native PF context prepared by the client. */
public final class InventoryPfSaveCommand implements InventoryNativeCommand {
    private static final long serialVersionUID = 1L;
    private final String billType;
    private final ICBillVO bill;
    private final ICPFParameter userObject;
    private final WorkflownoteVO workflowNote;
    private final HashMap<String, Object> parameters;

    public InventoryPfSaveCommand(String billType, ICBillVO bill, ICPFParameter userObject,
            WorkflownoteVO workflowNote, Map<String, Object> parameters) {
        this.billType = billType;
        this.bill = bill;
        this.userObject = userObject;
        this.workflowNote = workflowNote;
        this.parameters = parameters == null ? new HashMap<String, Object>() : new HashMap<String, Object>(parameters);
    }

    public String getBillType() { return billType; }
    public ICBillVO getBill() { return bill; }
    public ICPFParameter getUserObject() { return userObject; }
    public WorkflownoteVO getWorkflowNote() { return workflowNote; }
    public HashMap<String, Object> getParameters() { return new HashMap<String, Object>(parameters); }
}
