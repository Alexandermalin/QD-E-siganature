package nc.ui.uapbd.qd.esign;

import nc.ui.bd.supplier.baseinfo.model.SupplierBaseInfoDataManager;
import nc.ui.querytemplate.querytree.IQueryScheme;
import nc.vo.pub.BusinessException;

/** Retains the supplier query while recording a single explicitly selected business unit. */
public final class QdSupplierListModelDataManager extends SupplierBaseInfoDataManager implements PartnerOperationOrganization {
    private String operationOrg;
    @Override public void initModel() { operationOrg=null;super.initModel();QdPartnerContextOrganizations.register(getManageModel(),this); }
    @Override public void initModelBySqlWhere(String where) { operationOrg=null;super.initModelBySqlWhere(where);QdPartnerContextOrganizations.register(getManageModel(),this); }
    @Override public void initModelBySqlWhere(IQueryScheme scheme) {
        operationOrg=null;
        String selected=QdMaterialListModelDataManager.selectedOrganization(scheme,getManageModel().getContext());
        super.initModelBySqlWhere(scheme);operationOrg=selected;QdPartnerContextOrganizations.register(getManageModel(),this);
    }
    public String requireOperationOrganization() throws BusinessException {
        if(operationOrg==null) throw new BusinessException("无法确定供应商操作业务单元，请在查询中选择唯一业务单元后重新查询。");
        return operationOrg;
    }
}
