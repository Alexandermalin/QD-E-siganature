package nc.itf.uapbd.qd.esign;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.log.LogKind;
import nc.vo.uapbd.qd.esign.log.LogPage;
import nc.vo.uapbd.qd.esign.log.LogQuery;
import nc.vo.uapbd.qd.esign.log.AuditDetails;
import nc.vo.uapbd.qd.esign.audit.SnapshotContent;

/** 签名、审计和安全日志的远程只读查询契约；列表与审计详情均由服务端裁剪授权范围。 */
public interface ILogQueryService {
    /** 按日志类别、时间及业务筛选分页查询，授权组织由服务端决定，不接受客户端扩大查询范围。 */
    LogPage query(LogKind kind, LogQuery filter) throws BusinessException;
    /** 审计筛选版本 2：服务端必须执行组织条件，旧服务端缺少此方法时不能静默查询。 */
    LogPage queryAuditV2(LogQuery filter) throws BusinessException;
    /** 签名日志版本 2：按组织筛选并为列表解析业务单元名称。 */
    LogPage querySignatureV2(LogQuery filter) throws BusinessException;
    /** 先校验审计主记录的访问范围，再按实际请求关联加载分页差异与快照；未知、越权及不合法分页请求明确失败。 */
    AuditDetails details(String auditId,String archiveMonth,int offset,int pageSize) throws BusinessException;
    /** Returns snapshot field previews when fieldCode is null or one value segment otherwise. */
    SnapshotContent snapshotContent(String auditId,String archiveMonth,String snapshotId,String fieldCode,long offset)
        throws BusinessException;
}
