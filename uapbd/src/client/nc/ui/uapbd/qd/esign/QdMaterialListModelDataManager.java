package nc.ui.uapbd.qd.esign;

import java.util.List;
import nc.ui.bd.material.baseinfo.model.MaterialListModelDataManager;
import nc.ui.querytemplate.filter.IFilter;
import nc.ui.querytemplate.operator.EqOperator;
import nc.ui.querytemplate.operator.Eq2Operator;
import nc.ui.querytemplate.querytree.IQueryScheme;
import nc.ui.querytemplate.value.IFieldValueElement;
import nc.ui.querytemplate.value.RefValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.uif2.LoginContext;

/** 跟随物料原生查询记录明确的业务单元，避免把登录默认组织误当成查询所选组织。 */
public final class QdMaterialListModelDataManager extends MaterialListModelDataManager implements PartnerOperationOrganization {
    private String operationOrg;

    @Override public void initModel() {
        operationOrg = null;
        super.initModel();
    }

    @Override public void initModelBySqlWhere(IQueryScheme scheme) {
        operationOrg = null;
        String selected = selectedOrganization(scheme, getListModel().getContext());
        super.initModelBySqlWhere(scheme);
        operationOrg = selected;
    }

    public String requireOperationOrganization() throws BusinessException {
        if (operationOrg == null) {
            throw new BusinessException("无法确定本次操作的业务单元，请在物料查询条件中选择唯一业务单元后重新查询。");
        }
        return operationOrg;
    }

    /** 只接受明确的单个等值组织；多选、包含下级或未设组织过滤时不猜测。 */
    static String selectedOrganization(IQueryScheme scheme, LoginContext context) {
        return selectedOrganization(scheme, context, "pk_org_assign", true);
    }

    /** 仓库按 pk_org 查询；它的空过滤路径不等同于登录默认组织。 */
    static String selectedOrganization(IQueryScheme scheme, LoginContext context, String field, boolean useDefault) {
        if (scheme == null) { return null; }
        Object raw = scheme.get("filters");
        // 与原生 MaterialListModelDataManager 的空 filters 路径一致。
        if (raw == null) { return !useDefault || context == null ? null : clean(context.getPk_org()); }
        if (!(raw instanceof IFilter[])) { return null; }
        IFilter found = null;
        for (IFilter filter : (IFilter[])raw) {
            if (filter != null && filter.getFilterMeta() != null
                    && field.equals(filter.getFilterMeta().getFieldCode())) {
                if (found != null) { return null; }
                found = filter;
            }
        }
        if (found == null || found.getOperator() == null
                || (found.getOperator().getClass() != EqOperator.class && found.getOperator().getClass() != Eq2Operator.class)
                || found.getFieldValue() == null) { return null; }
        List<?> values = found.getFieldValue().getFieldValues();
        if (values == null || values.size() != 1 || !(values.get(0) instanceof IFieldValueElement)) { return null; }
        Object value = ((IFieldValueElement)values.get(0)).getValueObject();
        if (value instanceof RefValueObject) {
            RefValueObject ref = (RefValueObject)value;
            if (ref.isSubIncluded() || (ref.getMultiSelectionSql() != null
                    && !ref.getMultiSelectionSql().trim().isEmpty())) { return null; }
            return clean(ref.getPk());
        }
        return value instanceof String ? clean((String)value) : null;
    }

    private static String clean(String value) {
        if (value == null) { return null; }
        String trimmed = value.trim();
        return trimmed.matches("[A-Za-z0-9_-]{1,20}") ? trimmed : null;
    }
}
