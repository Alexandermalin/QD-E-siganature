package nc.vo.pu.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;

import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pattern.model.entity.bill.AbstractBill;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** The arguments from NC's single-document PF entry point. */
public final class ProcurementWorkflowCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String actionName;
    private final String billType;
    private final WorkflownoteVO note;
    private final AbstractBill bill;
    private final Object userObject;
    private final HashMap<?, ?> parameters;

    public ProcurementWorkflowCommand(String actionName, String billType, WorkflownoteVO note, AbstractBill bill,
            Object userObject, HashMap<?, ?> parameters) {
        this.actionName = actionName;
        this.billType = billType;
        this.note = note;
        this.bill = bill;
        this.userObject = userObject;
        this.parameters = parameters == null ? null : new HashMap<Object, Object>(parameters);
    }

    public String getActionName() { return actionName; }
    public String getBillType() { return billType; }
    public WorkflownoteVO getNote() { return note; }
    public AbstractBill getBill() { return bill; }
    public Object getUserObject() { return userObject; }
    public HashMap<?, ?> getParameters() {
        return parameters == null ? null : new HashMap<Object, Object>(parameters);
    }

    public static OperationContext draft(String node, String button, String loginGroup, AggregatedValueObject fullBill)
            throws BusinessException {
        ProcurementWorkflowRoute route = ProcurementWorkflowRoute.forNode(node);
        ProcurementWorkflowRoute.Action action = ProcurementWorkflowRoute.Action.forButton(button);
        SuperVO header = route.header(fullBill);
        String group = ProcurementWorkflowRoute.field(header, "pk_group");
        String org = ProcurementWorkflowRoute.field(header, "pk_org");
        if (!ProcurementWorkflowRoute.validKey(loginGroup) || !loginGroup.equals(group)
                || !ProcurementWorkflowRoute.validKey(org)) {
            throw new BusinessException("采购审批单据缺少当前集团或业务单元。");
        }
        String pk = header.getPrimaryKey();
        BusinessObjectRef ref = new BusinessObjectRef(pk, route.objectType(), pk,
                ProcurementWorkflowRoute.field(header, "vbillcode"), null);
        return new OperationContext(null, null, group, org, node, route.nodeName(), button, action.label(),
                action.name(), null, Collections.singletonList(ref));
    }
}
