package nc.ui.uapbd.qd.esign;

import nc.ui.bd.stordoc.model.StordocAppModelDataManager;
import nc.ui.querytemplate.querytree.IQueryScheme;
import nc.vo.pub.BusinessException;

/** Records only an explicit, single organization from the native warehouse query. */
public final class QdWarehouseModelDataManager extends StordocAppModelDataManager implements PartnerOperationOrganization {
    private String operationOrg;

    @Override public void initModel() {
        operationOrg=null;
        super.initModel();
    }
    @Override public void initModelBySqlWhere(IQueryScheme scheme) {
        operationOrg=null;
        String selected=QdMaterialListModelDataManager.selectedOrganization(scheme,
            getModel()==null?null:getModel().getContext(),"pk_org",false);
        super.initModelBySqlWhere(scheme);
        operationOrg=selected;
    }
    @Override public void refresh() {
        operationOrg=null;
        super.refresh();
    }
    public String requireOperationOrganization() throws BusinessException {
        if(operationOrg==null) throw new BusinessException("请在仓库查询条件中选择唯一组织并重新查询后再修改。");
        return operationOrg;
    }
}
