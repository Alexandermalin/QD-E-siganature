package nc.itf.uapbd.qd.esign;

import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ButtonPolicyChange;
import nc.vo.uapbd.qd.esign.ButtonPolicyRow;
import nc.vo.uapbd.qd.esign.OrganizationPolicyView;
import nc.vo.uapbd.qd.esign.ObjectPolicyDecision;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan;

/** 管理基础档案集团策略和其他业务单元策略。 */
public interface IPolicyService {
    /** 按真实节点、按钮和操作组织读取生效配置。 */
    PolicyDecision resolve(String orgId,String nodeCode,String buttonCode) throws BusinessException;
    List<ObjectPolicyDecision> resolveForObjects(OperationContext context) throws BusinessException;
    /** Read-only planning of exact organization-page slices; it does not authorize or execute them. */
    PartnerOrgBatchPlan planOrganizationBatch(OperationContext context) throws BusinessException;

    /** 返回当前集团的基础档案策略；视图中的组织标识为集团主键。 */
    OrganizationPolicyView queryGroupPolicy() throws BusinessException;
    List<ButtonPolicyRow> saveGroupPolicies(List<ButtonPolicyChange> changes) throws BusinessException;
    long setGroupMaster(boolean enabled, long expectedControlVersion) throws BusinessException;

    List<String> listPolicyOrganizations() throws BusinessException;
    OrganizationPolicyView queryOrganizationPolicy(String orgId) throws BusinessException;
    List<ButtonPolicyRow> saveOrganizationPolicies(String orgId, List<ButtonPolicyChange> changes) throws BusinessException;
    long setOrganizationMaster(String orgId, boolean enabled, long expectedControlVersion) throws BusinessException;

    /** 兼容旧客户端的单行查询和保存。 */
    PolicyDecision getPolicyForManagement(String orgId,String nodeCode,String buttonCode) throws BusinessException;
    void save(String orgId,String nodeCode,String buttonCode,boolean sign,boolean reason,boolean audit,long expectedVersion) throws BusinessException;

    /** 保留旧 RPC 方法以返回客户端升级提示。 */
    @Deprecated
    void setMasterEnabled(boolean enabled) throws BusinessException;
}
