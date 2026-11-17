package nc.bs.uapbd.qd.esign.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.sec.esapi.NCESAPI;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.itf.uap.bbd.func.IFuncRegisterQueryService;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.ColumnListProcessor;
import nc.jdbc.framework.processor.ColumnProcessor;
import nc.pubitf.rbac.IFunctionPermissionPubService;
import nc.pubitf.rbac.IDataPermissionPubService;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.pub.BusinessException;
import nc.vo.sm.funcreg.PageVO;
import nc.vo.uap.rbac.FuncPermissionState;
import nc.vo.uap.rbac.FuncSubInfo;
import nc.vo.uap.rbac.profile.FunctionPermProfileImpl;
import nc.vo.uap.rbac.profile.IFunctionPermProfile;
import nc.vo.uap.rbac.profile.SubjectPermInfo;
import nc.vo.util.VisibleUtil;

/** 按原生 NC 授权主体分别求取节点、按钮与组织权限，未知权限结构采取拒绝策略，避免跨主体合并导致组织范围扩大。 */
public final class EsignPermission {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private EsignPermission() { }
    /** 校验当前身份对真实节点、按钮及业务单元的操作授权，无授权则拒绝进入签名或业务执行。 */
    public static void requireBusiness(ServerIdentity identity, String node, String button, String org)
            throws BusinessException {
        EsignValues.require(org != null && org.trim().length() > 0, "Business unit is required");
        EsignValues.require(authorizedOrgs(identity, node, button, false).contains(org),
                "NC business function/button/organization permission denied");
    }
    /** 复用 NC 页签的组织类型过滤，按钮和目标组织必须来自同一授权主体。 */
    public static void requirePageOrganization(ServerIdentity identity, String node, String button, String page, String org)
            throws BusinessException {
        EsignValues.require(pageOrganization(loadSubjects(identity, node), node, button, page, org),
                "NC material page organization permission denied");
    }
    /** Batch operation organization and target page must be granted by the same native subject. */
    public static void requirePageOrganization(ServerIdentity identity,String node,String button,String page,
            String operationOrg,String targetOrg) throws BusinessException {
        requirePageOrganizations(identity,node,button,page,operationOrg,Collections.singletonList(targetOrg));
    }
    /** Load the native subjects once even when a batch has many target organizations. */
    public static void requirePageOrganizations(ServerIdentity identity,String node,String button,String page,
            String operationOrg,List<String> targetOrgs) throws BusinessException {
        EsignValues.require(operationOrg!=null && !operationOrg.trim().isEmpty() && targetOrgs!=null && !targetOrgs.isEmpty(),
            "NC page operation and target organizations are required");
        Set<String> allowed=pageOrganizations(loadSubjects(identity,node),node,button,page,operationOrg),seen=new LinkedHashSet<String>();
        for(String target:targetOrgs) EsignValues.require(target!=null && !target.trim().isEmpty() && seen.add(target) && allowed.contains(target),
            "NC page/button/operation organization and target organization permission denied");
    }

    /** Recheck the native metadata-operation data permission for every affected persisted archive. */
    public static void requireArchiveData(final ServerIdentity identity, final String resource, final String operation,
            List<BusinessObjectRef> objects) throws BusinessException {
        EsignValues.require(identity != null && identity.userId != null && identity.groupId != null
            && resource != null && operation != null && objects != null && !objects.isEmpty(),
            "档案数据权限检查缺少必要参数。");
        final IDataPermissionPubService permissions = NCLocator.getInstance().lookup(IDataPermissionPubService.class);
        EsignValues.require(permissions != null, "无法获取 NC 数据权限服务。");
        requireArchiveData(objects, new ArchiveDataCheck() {
            public boolean allows(String pk) throws BusinessException {
                return permissions.isUserhasPermissionByMetaDataOperation(resource, pk, operation,
                    identity.groupId, identity.userId);
            }
        });
    }

    interface ArchiveDataCheck { boolean allows(String pk) throws BusinessException; }

    static void requireArchiveData(List<BusinessObjectRef> objects, ArchiveDataCheck check) throws BusinessException {
        EsignValues.require(objects != null && !objects.isEmpty() && check != null, "档案数据权限范围为空。");
        for (BusinessObjectRef object : objects) {
            EsignValues.require(object != null && object.getObjectPk() != null
                && check.allows(object.getObjectPk()), "没有档案数据的操作权限。");
        }
    }

    /** Match the native material classification reference for the current signer and operation organization. */
    public static void requireMaterialBatchClassification(final ServerIdentity identity, final String operationOrg, Object value)
            throws BusinessException {
        EsignValues.require(identity!=null && identity.userId!=null && identity.groupId!=null && operationOrg!=null,
            "物料分类参照权限检查缺少用户或组织。");
        requireMaterialBatchClassification(value,new ArchiveDataCheck() {
            public boolean allows(String classificationPk) throws BusinessException {
                return materialClassificationSelectable(identity,operationOrg,classificationPk);
            }
        });
    }

    private static boolean materialClassificationSelectable(ServerIdentity identity,String operationOrg,String classificationPk)
            throws BusinessException {
        IDataPermissionPubService permissions=NCLocator.getInstance().lookup(IDataPermissionPubService.class);
        EsignValues.require(permissions!=null,"无法获取 NC 物料分类参照权限服务。");
        BaseDAO dao=new BaseDAO();
        String classificationSql=withRefCondition("SELECT pk_marbasclass FROM bd_marbasclass WHERE pk_marbasclass=?"
            +" AND NVL(dr,0)=0 AND enablestate=2 AND pk_group IN (?, 'GLOBLE00000000000000')",
            permissions.getDataRefSQLWherePart(identity.userId,identity.groupId,"materialbasclass","default","bd_marbasclass"));
        SQLParameter classificationParameters=new SQLParameter();
        classificationParameters.addParam(classificationPk);
        classificationParameters.addParam(identity.groupId);
        if(classificationVisible(dao,identity.groupId,operationOrg,classificationSql,classificationParameters,classificationPk)) return true;
        if(!classificationPk.equals(dao.executeQuery(classificationSql,classificationParameters,new ColumnProcessor()))) return false;

        // The stock classification reference also allows switching to a selectable business unit.
        String orgSql=withRefCondition("SELECT pk_org FROM org_orgs WHERE pk_group=?"
            +" AND isbusinessunit='Y' AND enablestate=2 AND NVL(dr,0)=0",
            permissions.getDataRefSQLWherePart(identity.userId,identity.groupId,"org","default","org_orgs"));
        SQLParameter orgParameters=new SQLParameter();
        orgParameters.addParam(identity.groupId);
        Object result=dao.executeQuery(orgSql,orgParameters,new ColumnListProcessor());
        EsignValues.require(result instanceof List<?>,"无法获取 NC 业务单元参照范围。");
        for(Object org:(List<?>)result) {
            if(org instanceof String && !operationOrg.equals(org)
                && classificationVisible(dao,identity.groupId,(String)org,classificationSql,classificationParameters,classificationPk)) return true;
        }
        return false;
    }

    private static boolean classificationVisible(BaseDAO dao,String group,String org,String sql,
            SQLParameter parameters,String classificationPk) throws BusinessException {
        String visibility=VisibleUtil.getRefVisibleCondition(NCESAPI.clientSqlEncode(group),
            NCESAPI.clientSqlEncode(org),"c099f7d7-52a9-4b98-bee7-2e3a6c3ea263","bd_marbasclass");
        return classificationPk.equals(dao.executeQuery(withRefCondition(sql,visibility),parameters,new ColumnProcessor()));
    }

    private static String withRefCondition(String sql,String condition) {
        // NC returns null when there is no additional reference condition.
        return condition==null || condition.trim().isEmpty()?sql:sql+" AND ("+condition+")";
    }

    static void requireMaterialBatchClassification(Object value, ArchiveDataCheck check) throws BusinessException {
        EsignValues.require(value instanceof String && ((String)value).matches("[A-Za-z0-9_-]{1,20}"),
            "批改物料分类需要有效的分类主键。");
        EsignValues.require(check!=null && check.allows((String)value),"目标物料分类不在当前可用的分类参照范围内。");
    }

    /** The fast assignment selector combines native organization and tab permissions. Keep them within the Assign grant. */
    public static Set<String> authorizedAssignmentTargets(ServerIdentity identity, String node, String operationOrg)
            throws BusinessException {
        return authorizedButtonTargets(identity,node,"Assign",operationOrg,true);
    }

    /** Keep the target and the real button on the same native authorization subject. */
    public static Set<String> authorizedButtonTargets(ServerIdentity identity,String node,String button,String operationOrg)
            throws BusinessException {
        return authorizedButtonTargets(identity,node,button,operationOrg,false);
    }

    private static Set<String> authorizedButtonTargets(ServerIdentity identity,String node,String button,String operationOrg,boolean strict)
            throws BusinessException {
        PageVO[] pages = NCLocator.getInstance().lookup(IFuncRegisterQueryService.class).getPages4Function(node);
        Set<String> allowed = new LinkedHashSet<String>();
        for (SubjectPermInfo subject : loadSubjects(identity, node)) {
            FuncSubInfo info = isolated(subject, node);
            if (info == null || !buttonAllowed(info, button, strict)) { continue; }
            String[] source = info.getFuncPermissionPkorgs();
            if (source == null || !Arrays.asList(source).contains(operationOrg)) { continue; }
            if (subject.getPkorgs() != null) { allowed.addAll(Arrays.asList(subject.getPkorgs())); }
            if (pages == null) { continue; }
            for (PageVO page : pages) {
                String code = page.getPagecode();
                if (info.getPagePermissionState(code) != FuncPermissionState.REGISTERD_HASPERMISSION) { continue; }
                String[] targets = info.getPagePermissionOrgs(code);
                if (targets != null) { allowed.addAll(Arrays.asList(targets)); }
            }
        }
        return allowed;
    }
    /** 管理操作按严格按钮权限校验；指定组织时还需该组织在授权范围内，集团级操作也必须明确注册并授予按钮。 */
    public static void require(ServerIdentity identity, String node, String button, String org)
            throws BusinessException {
        // 集团级管理没有业务单元可供限制，仍要求真实注册并明确授权的按钮。
        if (org == null) {
            EsignValues.require(hasExplicitPermission(identity, node, button),
                    "NC explicitly registered management button permission denied: " + node + "/" + button);
        } else {
            EsignValues.require(authorizedOrgs(identity, node, button, true).contains(org),
                    "NC management function/button/organization permission denied: " + node + "/" + button);
        }
    }
    /** 读取原生授权主体后求取该节点按钮可操作的组织集合，不接受客户端自报范围。 */
    public static List<String> authorizedOrgs(ServerIdentity identity, String node, String button, boolean strict)
            throws BusinessException {
        return organizations(loadSubjects(identity, node), node, button, strict);
    }

    /** 要求注册节点按钮具有明确授权，用于集团级安全事件等无业务单元操作。 */
    public static boolean hasExplicitPermission(ServerIdentity identity, String node, String button)
            throws BusinessException {
        return explicit(loadSubjects(identity, node), node, button);
    }
    /** 通过原生权限服务加载当前用户的授权主体及节点信息，未知结构或缺少配置不扩大权限。 */
    private static SubjectPermInfo[] loadSubjects(ServerIdentity identity, String node) throws BusinessException {
        EsignValues.require(identity != null && identity.userId != null && identity.groupId != null,
                "NC authenticated identity is required");
        EsignValues.require(node != null && node.trim().length() > 0, "NC function code is required");
        IFunctionPermProfile profile = NCLocator.getInstance().lookup(IFunctionPermissionPubService.class)
                .getFunctionPermProfileWithGroup(identity.userId, identity.groupId);
        // 此处使用 AbstractFunctionPermProfoleGenerator 实际生成的公共实现类型。
        // 未知权限配置结构直接拒绝，不能因此放宽组织范围。
        EsignValues.require(profile instanceof FunctionPermProfileImpl, "Unsupported NC permission profile implementation");
        FunctionPermProfileImpl nativeProfile = (FunctionPermProfileImpl) profile;
        EsignValues.require(identity.userId.equals(nativeProfile.getOwnerUserID())
                && identity.groupId.equals(nativeProfile.getPk_group()), "NC permission profile identity/group mismatch");
        // 触发原生延迟初始化，以取得该授权主体真实的节点及按钮明细。
        nativeProfile.getFuncSubInfo(node);
        SubjectPermInfo[] subjects = nativeProfile.getSubjectPermInfos();
        return subjects == null ? new SubjectPermInfo[0] : subjects;
    }
    /** 逐授权主体核对按钮与组织范围，保留原生组织类型过滤，不跨主体合并不相干授权。 */
    static List<String> organizations(SubjectPermInfo[] subjects, String node, String button, boolean strict)
            throws BusinessException {
        requireNames(node, button);
        Set<String> allowed = new LinkedHashSet<String>();
        if (subjects != null) {
            for (SubjectPermInfo subject : subjects) {
                // 把每个授权主体的按钮与组织范围一起判断，不能拿主体甲的按钮权限组合主体乙的组织。
                FuncSubInfo isolated = isolated(subject, node);
                if (isolated == null || !buttonAllowed(isolated, button, strict)) continue;
                // 当前 NC65 Home 的 getButtonPermissionOrgs 实现仅返回 null，不能依赖它计算组织授权。
                // 逐主体调用原生方法，以保留 NC 的功能组织类型过滤。
                String[] scoped = isolated.getFuncPermissionPkorgs();
                if (scoped == null) continue;
                for (String org : scoped) if (org != null && org.trim().length() > 0) allowed.add(org);
            }
        }
        return Collections.unmodifiableList(new ArrayList<String>(allowed));
    }
    static boolean pageOrganization(SubjectPermInfo[] subjects, String node, String button, String page, String org)
            throws BusinessException {
        return pageOrganization(subjects,node,button,page,null,org);
    }
    static boolean pageOrganization(SubjectPermInfo[] subjects,String node,String button,String page,String operationOrg,String org)
            throws BusinessException {
        Set<String> allowed=pageOrganizations(subjects,node,button,page,operationOrg);
        return org!=null && allowed.contains(org);
    }
    static Set<String> pageOrganizations(SubjectPermInfo[] subjects,String node,String button,String page,String operationOrg)
            throws BusinessException {
        requireNames(node, button);
        Set<String> allowed=new LinkedHashSet<String>();
        if (subjects == null || page == null || page.trim().isEmpty() || (operationOrg!=null && operationOrg.trim().isEmpty()))
            return Collections.unmodifiableSet(allowed);
        for (SubjectPermInfo subject : subjects) {
            FuncSubInfo isolated = isolated(subject, node);
            if (isolated == null || !buttonAllowed(isolated, button, false)
                    || isolated.getPagePermissionState(page) != FuncPermissionState.REGISTERD_HASPERMISSION) { continue; }
            if(operationOrg!=null) {
                String[] source=isolated.getFuncPermissionPkorgs();
                if(source==null || !Arrays.asList(source).contains(operationOrg)) continue;
            }
            String[] organizations = isolated.getPagePermissionOrgs(page);
            if (organizations == null) { continue; }
            for (String permitted : organizations) if(permitted!=null && !permitted.trim().isEmpty()) allowed.add(permitted);
        }
        return Collections.unmodifiableSet(allowed);
    }
    /** 检查原生权限主体是否对指定按钮给出明确授权，不把未注册当作默认允许。 */
    static boolean explicit(SubjectPermInfo[] subjects, String node, String button) throws BusinessException {
        requireNames(node, button);
        if (subjects != null) {
            for (SubjectPermInfo subject : subjects) {
                FuncSubInfo isolated = isolated(subject, node);
                if (isolated != null && buttonAllowed(isolated, button, true)) return true;
            }
        }
        return false;
    }
    /** 取得单个授权主体对应的节点权限，避免读取其他主体的组织或按钮信息。 */
    private static FuncSubInfo isolated(SubjectPermInfo subject, String node) {
        if (subject == null || subject.getFuncNodeInfo(node) == null) return null;
        FuncSubInfo info = new FuncSubInfo();
        info.setFuncode(node);
        info.setSubjectPermInfos(new SubjectPermInfo[] { subject });
        return info;
    }
    /** 严格模式要求按钮明确注册且授权；业务兼容模式沿用原生 isValidButton 判断，不替换平台既有语义。 */
    private static boolean buttonAllowed(FuncSubInfo info, String button, boolean strict) {
        return strict ? info.getButtonPermissionState(button) == FuncPermissionState.REGISTERD_HASPERMISSION
                : info.isValidButton(button);
    }
    /** 要求真实节点编码与按钮编码完整，避免缺失编码时放宽为通配访问。 */
    private static void requireNames(String node, String button) throws BusinessException {
        EsignValues.require(node != null && node.trim().length() > 0 && button != null && button.trim().length() > 0,
                "NC function and button codes are required");
    }
}
