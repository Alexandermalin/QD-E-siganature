package nc.vo.ic.qd.esign;

import java.util.HashMap;
import java.util.Map;
import nc.vo.ic.general.define.ICBillVO;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.InventoryNativeCommand;

/** Carries the native PF context for one already-authorized inventory bill. */
public final class InventoryPfStateCommand implements InventoryNativeCommand {
    private static final long serialVersionUID = 1L;
    private final String billType;
    private final ICBillVO bill;
    private final PfUserObject userObject;
    private final WorkflownoteVO workflowNote;
    private final HashMap<String, Object> parameters;

    public InventoryPfStateCommand(String billType, ICBillVO bill, PfUserObject userObject,
            WorkflownoteVO workflowNote, Map<String, Object> parameters) {
        this.billType = billType;
        this.bill = bill;
        this.userObject = userObject;
        this.workflowNote = workflowNote;
        this.parameters = parameters == null ? new HashMap<String, Object>() : new HashMap<String, Object>(parameters);
    }
    public String getBillType() { return billType; }
    public ICBillVO getBill() { return bill; }
    public PfUserObject getUserObject() { return userObject; }
    public WorkflownoteVO getWorkflowNote() { return workflowNote; }
    public HashMap<String, Object> getParameters() { return new HashMap<String, Object>(parameters); }
}
