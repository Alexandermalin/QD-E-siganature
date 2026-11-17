package nc.ui.uapbd.qd.esign;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import nc.ui.uif2.model.AbstractAppModel;
import nc.vo.pub.BusinessException;

/** The bank popup receives its parent model, not the business-unit query manager. */
final class QdPartnerContextOrganizations {
    private static final WeakHashMap<AbstractAppModel,WeakReference<PartnerOperationOrganization>> SCOPES=
        new WeakHashMap<AbstractAppModel,WeakReference<PartnerOperationOrganization>>();
    private QdPartnerContextOrganizations() { }
    static synchronized void register(AbstractAppModel model,PartnerOperationOrganization manager) {
        if(model!=null && manager!=null) SCOPES.put(model,new WeakReference<PartnerOperationOrganization>(manager));
    }
    static synchronized String operationOrganization(AbstractAppModel model) throws BusinessException {
        return operationOrganization(model,"银行账户");
    }
    static synchronized String operationOrganization(AbstractAppModel model,String action) throws BusinessException {
        if(model==null || model.getContext()==null || model.getContext().getNodeCode()==null || model.getContext().getPk_group()==null)
            throw new BusinessException("无法确定"+action+"所属的原生档案页面。");
        String node=model.getContext().getNodeCode();
        if("10140CUG".equals(node) || "10140SUG".equals(node)) return model.getContext().getPk_group();
        if(!"10140CUO".equals(node) && !"10140SUO".equals(node)) throw new BusinessException(action+"的父档案入口尚未接入。");
        WeakReference<PartnerOperationOrganization> reference=SCOPES.get(model);
        PartnerOperationOrganization manager=reference==null?null:reference.get();
        if(manager==null) throw new BusinessException("无法确定"+action+"操作业务单元，请重新查询父档案。");
        return manager.requireOperationOrganization();
    }
}
