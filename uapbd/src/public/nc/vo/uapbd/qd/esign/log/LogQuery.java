package nc.vo.uapbd.qd.esign.log;

import java.io.Serializable;

/** 客户端日志筛选与分页条件。组织授权范围由服务端单独计算，客户端不能通过此对象扩大查询权限。 */
public final class LogQuery implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID = 1L;
    /** fromUtc：查询起始 UTC 时间，具体边界由仓储条件定义。 */
    private String fromUtc;
    /** toUtc：查询结束 UTC 时间，具体边界由仓储条件定义。 */
    private String toUtc;
    /** userId：NC 用户主键。 */
    private String userId;
    /** userCode：NC 用户编码。 */
    private String userCode;
    /** organizationId：用户选择的业务单元主键，仍须与服务端授权范围取交集。 */
    private String organizationId;
    /** nodeCode：NC 功能节点编码。 */
    private String nodeCode;
    /** billCode：单据或档案编码。 */
    private String billCode;
    /** requestId：贯通准备、签名、执行及日志关联的请求标识。 */
    private String requestId;
    /** batchId：同批业务对象共用的批次标识。 */
    private String batchId;
    /** resultCode：日志结果码筛选条件。 */
    private String resultCode;
    /** reasonContains：按字面包含匹配的事由筛选条件。 */
    private String reasonContains;
    /** offset：从零开始的分页偏移。 */
    private int offset;
    /** pageSize：每页记录数，服务端还会校验范围。 */
    private int pageSize = 100;
    /** 返回包含该时刻的 UTC 查询起点，和排除终点一起形成左闭右开区间。 */
    public String getFromUtc() { return fromUtc; }
    /** 设置左闭右开区间的 UTC 起点，服务端会再次严格校验日期时间。 */
    public void setFromUtc(String value) { fromUtc = value; }
    /** 返回不包含该时刻的 UTC 查询终点，必须严格晚于起点。 */
    public String getToUtc() { return toUtc; }
    /** 设置左闭右开区间的 UTC 终点，服务端会再次校验其晚于起点。 */
    public void setToUtc(String value) { toUtc = value; }
    /** 返回NC 用户主键。 */
    public String getUserId() { return userId; }
    /** 设置NC 用户主键。 */
    public void setUserId(String value) { userId = value; }
    /** 返回NC 用户编码。 */
    public String getUserCode() { return userCode; }
    /** 设置NC 用户编码。 */
    public void setUserCode(String value) { userCode = value; }
    /** 返回可选业务单元筛选。 */
    public String getOrganizationId() { return organizationId; }
    /** 设置业务单元筛选；服务端独立验证权限。 */
    public void setOrganizationId(String value) { organizationId = value; }
    /** 返回NC 功能节点编码。 */
    public String getNodeCode() { return nodeCode; }
    /** 设置NC 功能节点编码。 */
    public void setNodeCode(String value) { nodeCode = value; }
    /** 返回单据或档案编码。 */
    public String getBillCode() { return billCode; }
    /** 设置单据或档案编码。 */
    public void setBillCode(String value) { billCode = value; }
    /** 返回贯通准备、签名、执行及日志关联的请求标识。 */
    public String getRequestId() { return requestId; }
    /** 设置贯通准备、签名、执行及日志关联的请求标识。 */
    public void setRequestId(String value) { requestId = value; }
    /** 返回同批业务对象共用的批次标识。 */
    public String getBatchId() { return batchId; }
    /** 设置同批业务对象共用的批次标识。 */
    public void setBatchId(String value) { batchId = value; }
    /** 返回日志结果码筛选条件。 */
    public String getResultCode() { return resultCode; }
    /** 设置日志结果码筛选条件。 */
    public void setResultCode(String value) { resultCode = value; }
    /** 返回按字面包含匹配的事由筛选条件。 */
    public String getReasonContains() { return reasonContains; }
    /** 设置按字面包含匹配的事由筛选条件。 */
    public void setReasonContains(String value) { reasonContains = value; }
    /** 返回从零开始的分页偏移。 */
    public int getOffset() { return offset; }
    /** 设置从零开始的分页偏移。 */
    public void setOffset(int value) { offset = value; }
    /** 返回每页记录数，服务端还会校验范围。 */
    public int getPageSize() { return pageSize; }
    /** 设置每页记录数，服务端还会校验范围。 */
    public void setPageSize(int value) { pageSize = value; }
}
