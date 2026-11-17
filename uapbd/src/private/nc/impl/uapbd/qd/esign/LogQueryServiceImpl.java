package nc.impl.uapbd.qd.esign;

import nc.bs.uapbd.qd.esign.persistence.LogAccess;
import nc.bs.uapbd.qd.esign.persistence.QdLogRepository;
import nc.itf.uapbd.qd.esign.ILogQueryService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.log.LogKind;
import nc.vo.uapbd.qd.esign.log.LogPage;
import nc.vo.uapbd.qd.esign.log.LogQuery;
import nc.vo.uapbd.qd.esign.log.AuditDetails;
import nc.vo.uapbd.qd.esign.audit.SnapshotContent;
import nc.bs.uapbd.qd.esign.persistence.AuditDetailsRepository;
import java.util.List;
import java.util.Map;

/** 日志查询服务实现，在每次列表或详情读取前解析服务端权限，再委托数据库仓储查询。 */
public final class LogQueryServiceImpl implements ILogQueryService {
    /** 新客户端使用明确版本的方法，避免旧服务端忽略组织筛选。 */
    public LogPage queryAuditV2(LogQuery filter) throws BusinessException {
        return query(LogKind.AUDIT, filter);
    }
    /** 列表中的组织名称只解析已授权结果行，日志本身仍保存原始业务单元主键。 */
    public LogPage querySignatureV2(LogQuery filter) throws BusinessException {
        LogAccess access=LogAccess.resolve(LogKind.SIGNATURE,"QUERY");
        QdLogRepository repository=new QdLogRepository();
        List<Map<String,Object>> rows=repository.query(LogKind.SIGNATURE,filter,access.groupId,access.organizations,access.includeGroupSecurity);
        return new LogPage(repository.withOrganizationNames(rows,access.groupId),
            repository.count(LogKind.SIGNATURE,filter,access.groupId,access.organizations,access.includeGroupSecurity));
    }
    /** 先校验审计主记录的访问范围，再按实际请求关联加载分页差异与快照；未知、越权及不合法分页请求明确失败。 */
    public AuditDetails details(String auditId,String archiveMonth,int offset,int pageSize) throws BusinessException {
        LogAccess access=LogAccess.resolve(LogKind.AUDIT,"QUERY");
        return new AuditDetailsRepository().details(auditId,archiveMonth,offset,pageSize,access.groupId,access.organizations);
    }
    public SnapshotContent snapshotContent(String auditId,String archiveMonth,String snapshotId,String fieldCode,long offset)
            throws BusinessException {
        LogAccess access=LogAccess.resolve(LogKind.AUDIT,"QUERY");
        return new AuditDetailsRepository().snapshotContent(auditId,archiveMonth,snapshotId,fieldCode,offset,
            access.groupId,access.organizations);
    }
    /** 校验日志类别并在服务端解析 QUERY 权限，分别加载当前页和总数；两次读取不宣称同一数据库快照。 */
    public LogPage query(LogKind kind,LogQuery filter) throws BusinessException {
        LogAccess access=LogAccess.resolve(kind,"QUERY"); QdLogRepository repository=new QdLogRepository();
        List<Map<String,Object>> rows=repository.query(kind,filter,access.groupId,access.organizations,access.includeGroupSecurity);
        if(kind!=LogKind.SECURITY)rows=repository.withOrganizationNames(rows,access.groupId);
        return new LogPage(rows,
            repository.count(kind,filter,access.groupId,access.organizations,access.includeGroupSecurity));
    }
}
