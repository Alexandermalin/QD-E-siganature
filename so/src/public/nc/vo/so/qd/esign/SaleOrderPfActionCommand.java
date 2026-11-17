package nc.vo.so.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pflow.PfUserObject;
import nc.vo.so.m30.entity.SaleOrderVO;

/** 销售订单 PF 流程动作（删除/提交/收回/审批/取消审批）的类型化命令，保留原生流程参数载体。 */
public final class SaleOrderPfActionCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 原生 PF 的交易类型（含交易类型编码时优先使用，否则回退功能型 30）。 */
    private final String billType;
    /** 原生流程动作实际处理的单据集合。 */
    private final SaleOrderVO[] bills;
    /** 原生流程所需的销售订单用户对象。 */
    private final PfUserObject userObject;
    /** 审批窗口产生的流程说明；打开或取消审批窗口时不应构造受控命令。 */
    private final WorkflownoteVO workflownote;
    /** 原生 PF 扩展参数副本，只允许按动作定义的白名单键。 */
    private final Map<String, Object> parameters;

    public SaleOrderPfActionCommand(SaleOrderVO[] bills, PfUserObject userObject,
            WorkflownoteVO workflownote, Map<String, Object> parameters) {
        this(null, bills, userObject, workflownote, parameters);
    }

    public SaleOrderPfActionCommand(String billType, SaleOrderVO[] bills, PfUserObject userObject,
            WorkflownoteVO workflownote, Map<String, Object> parameters) {
        this.billType = billType;
        this.bills = bills == null ? null : bills.clone();
        this.userObject = userObject;
        this.workflownote = workflownote;
        this.parameters = parameters == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new HashMap<String, Object>(parameters));
    }

    /** 返回原生 PF 交易类型；为空时由服务端按原生规则回退。 */
    public String getBillType() { return billType; }

    /** 返回待处理单据副本。 */
    public SaleOrderVO[] getBills() { return bills == null ? null : bills.clone(); }

    /** 返回原生流程用户对象。 */
    public PfUserObject getUserObject() { return userObject; }

    /** 返回审批窗口产生的流程说明，未构造时为空。 */
    public WorkflownoteVO getWorkflownote() { return workflownote; }

    /** 返回 PF 扩展参数副本。 */
    public Map<String, Object> getParameters() {
        return parameters.isEmpty() ? Collections.<String, Object>emptyMap()
                : new HashMap<String, Object>(parameters);
    }
}
