package nc.impl.uapbd.qd.esign;

import java.util.Map;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;

/** Read policy for an unscoped native BP without requiring an interactive user profile. */
public final class NativeBpPolicyResolver {
    private static final String POLICY_SQL="SELECT c.master_enabled,p.enabled,p.sign_enabled,p.audit_enabled"
            +" FROM dual LEFT JOIN qd_es_org_control c ON c.pk_group=? AND c.pk_org=?"
            +" LEFT JOIN qd_es_policy p ON p.pk_group=? AND p.pk_org=? AND p.node_code=? AND p.button_code=?";
    private NativeBpPolicyResolver() { }

    public static boolean isControlled(String group,String org,String node,String button) throws BusinessException {
        InvocationInfoProxy invocation=InvocationInfoProxy.getInstance();
        requireContext(group,invocation==null?null:invocation.getGroupId(),
                invocation==null?null:invocation.getUserDataSource());
        return isControlled(new QdEsignStore(),group,org,node,button);
    }

    static boolean isControlled(QdEsignStore store,String group,String org,String node,String button)
            throws BusinessException {
        Map<String,Object> mapping=store.loadNodeByAction(node,button);
        String groupScope=mapping==null?null:EsignValues.text(mapping,"group_scope_id");
        EsignValues.require(groupScope==null || groupScope.trim().isEmpty(),"出库申请策略映射类型不正确。");
        SQLParameter parameters=new SQLParameter();
        parameters.addParam(group);parameters.addParam(org);parameters.addParam(group);parameters.addParam(org);
        parameters.addParam(node);parameters.addParam(button);
        Map<String,Object> policy=store.one(POLICY_SQL,parameters);
        EsignValues.require(policy!=null,"无法读取出库申请策略开关。");
        return EsignValues.yes(policy,"master_enabled") && EsignValues.yes(policy,"enabled")
                && (EsignValues.yes(policy,"sign_enabled") || EsignValues.yes(policy,"audit_enabled"));
    }

    static void requireContext(String group,String contextGroup,String dataSource) throws BusinessException {
        EsignValues.require(group!=null && !group.trim().isEmpty() && group.equals(group.trim())
                && group.equals(contextGroup) && dataSource!=null && !dataSource.trim().isEmpty(),
                "库存 BP 缺少可信的集团或数据源上下文。");
    }

}
