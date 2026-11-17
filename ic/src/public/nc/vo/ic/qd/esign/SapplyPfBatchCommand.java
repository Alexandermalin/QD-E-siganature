package nc.vo.ic.qd.esign;

import java.util.HashMap;
import java.util.Map;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.uapbd.qd.esign.InventoryNativeCommand;

/** Native PF payload for a prepared batch of outbound-request bills. */
public final class SapplyPfBatchCommand implements InventoryNativeCommand {
    private static final long serialVersionUID = 1L;
    private final String action, billType;
    private final SapplyBillVO[] bills;
    private final PfUserObject[] userObjects;
    private final WorkflownoteVO workflowNote;
    private final HashMap<String,Object> parameters;
    private final String[] precheckSkippedPks;

    public SapplyPfBatchCommand(String action, String billType, SapplyBillVO[] bills,
            PfUserObject[] userObjects, WorkflownoteVO workflowNote, Map<String,Object> parameters) {
        this(action,billType,bills,userObjects,workflowNote,parameters,null);
    }

    public SapplyPfBatchCommand(String action, String billType, SapplyBillVO[] bills,
            PfUserObject[] userObjects, WorkflownoteVO workflowNote, Map<String,Object> parameters,
            String[] precheckSkippedPks) {
        this.action = action;
        this.billType = billType;
        this.bills = bills == null ? null : bills.clone();
        this.userObjects = userObjects == null ? null : userObjects.clone();
        this.workflowNote = workflowNote;
        this.parameters = parameters == null ? new HashMap<String,Object>() : new HashMap<String,Object>(parameters);
        this.precheckSkippedPks = precheckSkippedPks == null ? new String[0] : precheckSkippedPks.clone();
    }

    public String getAction() { return action; }
    public String getBillType() { return billType; }
    public SapplyBillVO[] getBills() { return bills == null ? null : bills.clone(); }
    public PfUserObject[] getUserObjects() { return userObjects == null ? null : userObjects.clone(); }
    public WorkflownoteVO getWorkflowNote() { return workflowNote; }
    /** Selected bills rejected before native submission; null supports older serialized commands. */
    public String[] getPrecheckSkippedPks() {
        return precheckSkippedPks == null ? new String[0] : precheckSkippedPks.clone();
    }
    public HashMap<String,Object> getParameters() {
        return parameters == null ? new HashMap<String,Object>() : new HashMap<String,Object>(parameters);
    }
}
