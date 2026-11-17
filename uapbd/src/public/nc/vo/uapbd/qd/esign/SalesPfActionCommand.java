package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pubapp.pflow.PfUserObject;

/**
 * 发货单与销售发票 PF 流程动作的通用类型化命令。
 *
 * <p>只承载原生流程已经产出的数据：交易类型、单据集合、原生流程用户对象、审批窗口说明与流程参数。
 * 服务端按节点固定白名单核对动作域、对象类型、组织与参数键，客户端不能携带任意服务名或脚本名。</p>
 */
public final class SalesPfActionCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 原生 PF 的交易类型编码；为空时由服务端按节点固定功能型回退。 */
    private final String billType;
    /** 原生流程动作实际处理的单据集合。 */
    private final AggregatedValueObject[] bills;
    /** 原生流程所需的用户对象；节点适配器按节点核定其真实类型。 */
    private final PfUserObject userObject;
    /** 审批窗口产生的流程说明；打开或取消审批窗口时不应构造受控命令。 */
    private final WorkflownoteVO workflownote;
    /** 原生 PF 扩展参数副本，只允许按动作定义的白名单键。 */
    private final Map<String, Object> parameters;

    public SalesPfActionCommand(String billType, AggregatedValueObject[] bills, PfUserObject userObject,
            WorkflownoteVO workflownote, Map<String, Object> parameters) {
        this.billType = billType;
        this.bills = bills == null ? null : bills.clone();
        this.userObject = userObject;
        this.workflownote = workflownote;
        this.parameters = parameters == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new HashMap<String, Object>(parameters));
    }

    /** 返回原生 PF 交易类型；为空时由节点适配器按原生规则回退。 */
    public String getBillType() { return billType; }

    /** 返回待处理单据副本。 */
    public AggregatedValueObject[] getBills() { return bills == null ? null : bills.clone(); }

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
