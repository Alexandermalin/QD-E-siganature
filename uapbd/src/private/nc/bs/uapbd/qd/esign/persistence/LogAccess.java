package nc.bs.uapbd.qd.esign.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.log.LogKind;

/** 服务端日志访问范围，按日志类别和按钮求取集团及组织授权；LogQuery 中不接受客户端组织白名单。 */
public final class LogAccess {
    /** groupId：NC 集团主键，服务端从当前身份核定。 */
    public final String groupId;
    /** organizations：服务端核定的可访问业务单元集合。 */
    public final List<String> organizations;
    /** includeGroupSecurity：是否另外授予无业务单元的集团安全事件访问权。 */
    public final boolean includeGroupSecurity;
    /** 保存服务端计算的集团、组织集合与集团安全事件访问标志，供仓储构造查询边界。 */
    private LogAccess(String group,List<String> orgs,boolean groupSecurity) {
        groupId=group; organizations=orgs; includeGroupSecurity=groupSecurity;
    }
    /**
     * 按日志类别定位固定权限节点，要求对应 QUERY 或 EXPORT 按钮明确授权，并分别计算组织范围及 GROUP_SECURITY 集团安全访问权。
     */
    public static LogAccess resolve(LogKind kind,String button) throws BusinessException {
        String node;
        if(kind==LogKind.SIGNATURE) { node="QD_ES_SIGNLOG"; }
        else if(kind==LogKind.AUDIT) { node="QD_ES_AUDITLOG"; }
        else if(kind==LogKind.SECURITY) { node="QD_ES_SECURITY"; }
        else { throw new BusinessException("Log kind is required"); }
        ServerIdentity identity=ServerIdentity.current();
        if (!"QUERY".equals(button) && !"EXPORT".equals(button)) {
            throw new BusinessException("Invalid log access operation");
        }
        boolean organizationAccess=EsignPermission.hasExplicitPermission(identity,node,button);
        boolean groupBusiness=kind!=LogKind.SECURITY
            && EsignPermission.hasExplicitPermission(identity,node,"GROUP_"+button);
        if(!organizationAccess&&!groupBusiness) {
            throw new BusinessException("NC log function/button permission denied: " + node + "/" + button);
        }
        boolean groupSecurity=kind==LogKind.SECURITY
            && EsignPermission.hasExplicitPermission(identity,node,"GROUP_SECURITY");
        List<String> orgs=new ArrayList<String>();
        if (organizationAccess) {
            orgs.addAll(EsignPermission.authorizedOrgs(identity,node,button,true));
        }
        if (kind!=LogKind.SECURITY) {
            orgs=businessScopes(orgs,identity.groupId,groupBusiness);
        }
        if(orgs.isEmpty()&&!groupSecurity) { throw new BusinessException("No explicitly authorized log organizations"); }
        return new LogAccess(identity.groupId,Collections.unmodifiableList(orgs),groupSecurity);
    }

    static List<String> businessScopes(List<String> organizations,String group,boolean groupGranted) {
        List<String> scopes=new ArrayList<String>(organizations);
        // Pure group operations use the native group owner key. A BU grant never includes that key.
        scopes.remove(group);
        if (groupGranted) { scopes.add(group); }
        return scopes;
    }
}
