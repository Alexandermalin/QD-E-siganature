package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import nc.bs.bd.assignservice.AssignOrgFilterUtil;
import nc.bs.bd.assignservice.AssignToOrgServiceImpl;
import nc.bs.bd.assignservice.IAssignToOrgService;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.uapbd.qd.esign.audit.AuditRecorder;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.impl.bd.material.assign.MaterialAssignServiceImpl;
import nc.md.common.AssociationKind;
import nc.md.model.IAssociation;
import nc.md.model.IBean;
import nc.md.util.MDUtil;
import nc.vo.bd.errorlog.ErrLogResult;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.bd.errorlog.ErrorLogUtil;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Executes the native organization relation and applicable native material tabs inside one unit CMT. */
final class MaterialAssignmentNative extends MaterialAssignServiceImpl {
    private static final String LOG_KEY = "assignerrorlogutilkey";
    private static final Set<String> STANDARD_TAB_SERVICES = new HashSet<String>(Arrays.asList(
        "nc.impl.bd.material.fi.MaterialFiAssignToOrgService",
        "nc.impl.bd.material.pfc.MaterialPfcAssignToOrgService",
        "nc.impl.bd.material.pu.MaterialPuAssignToOrgService",
        "nc.impl.bd.material.stock.MaterialStockAssignToOrgService",
        "nc.impl.bd.material.sale.MaterialSaleAssignToOrgService",
        "nc.impl.bd.material.plan.MaterialPlanAssignToOrgService",
        "nc.impl.bd.material.prod.MaterialProdAssignToOrgService"));

    /** Returns false only when every applicable relation already exists. */
    boolean apply(Map<String,Object> request, Map<String,Object> object) throws BusinessException {
        String material = EsignValues.text(object, "object_pk");
        String target = EsignValues.text(object, "target_org");
        String group = EsignValues.text(request, "pk_group");
        String[] materials = {material}, targets = {target};
        PhysicalSnapshotReader reader = new PhysicalSnapshotReader();
        List<SnapshotRow> before = new ArrayList<SnapshotRow>(), after = new ArrayList<SnapshotRow>();
        boolean audit = EsignValues.yes(object, "owner_audit_enabled");
        if (audit) { before.addAll(reader.readByPk("bd_material", "pk_material", material)); }
        List<SnapshotRow> main = relation(reader, "bd_marorg", "pk_marorg", material, target, group);
        before.addAll(main);

        // false keeps the inner native tab service in the unit CMT (the standard fast UI passes true).
        Map<IAssignToOrgService,List<String>> routes = groupTargetPks(targets, false);
        List<AssignToOrgServiceImpl> tabs = new ArrayList<AssignToOrgServiceImpl>();
        Set<String> tables = new HashSet<String>();
        for (Iterator<Map.Entry<IAssignToOrgService,List<String>>> it = routes.entrySet().iterator(); it.hasNext();) {
            Map.Entry<IAssignToOrgService,List<String>> route = it.next();
            IAssignToOrgService service = route.getKey();
            EsignValues.require(STANDARD_TAB_SERVICES.contains(service.getClass().getName())
                && service instanceof AssignToOrgServiceImpl, "目标组织包含待核实的物料分配页签。");
            AssignToOrgServiceImpl tab = (AssignToOrgServiceImpl)service;
            IBean bean = tab.getOrgBean();
            List<String> enabled = AssignOrgFilterUtil.filterEnableOrgIDs(
                AssignOrgFilterUtil.getAssignedOrgType(bean, tab.getOrgFieldName()), route.getValue());
            if (enabled == null || enabled.isEmpty()) {
                it.remove();
                continue;
            }
            EsignValues.require(route.getValue().size() == 1 && target.equals(route.getValue().get(0))
                && "pk_material".equalsIgnoreCase(tab.getRelationPkFieldName())
                && "pk_org".equalsIgnoreCase(tab.getOrgFieldName())
                && tables.add(tab.getAssignTableName().toLowerCase(Locale.ENGLISH)),
                "物料分配页签的组织或关联字段与已核实范围不一致。");
            requireFlatTab(bean);
            tabs.add(tab);
            List<SnapshotRow> existing = tabRows(reader, tab, material, target, group);
            before.addAll(existing);
            if (!existing.isEmpty()) { it.remove(); }
        }
        if (!main.isEmpty() && routes.isEmpty()) { return false; }

        if (main.isEmpty()) {
            InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
            String previous = invocation.getProperty(LOG_KEY);
            invocation.setProperty(LOG_KEY, generateNewPk());
            try { requireNoErrors(getMarOrgService().assignMaterialByPks(materials, targets, null)); }
            finally { invocation.setProperty(LOG_KEY, previous); }
        }
        if (!routes.isEmpty()) {
            ErrorLogUtil errors = new ErrorLogUtil(getBaseBean().getID(), EsignValues.text(request, "user_id"),
                "物料分配", false);
            assignByPks(materials, routes, errors);
            requireNoErrors(errors.getErrLogReturnValue(null, routes.size()));
        }

        if (audit) { after.addAll(reader.readByPk("bd_material", "pk_material", material)); }
        List<SnapshotRow> assigned = relation(reader, "bd_marorg", "pk_marorg", material, target, group);
        EsignValues.require(assigned.size() == 1, "原生服务未生成目标物料分配关系。");
        after.addAll(assigned);
        for (AssignToOrgServiceImpl tab : tabs) {
            List<SnapshotRow> rows = tabRows(reader, tab, material, target, group);
            EsignValues.require(rows.size() == 1, "原生服务未生成预期的物料组织信息。");
            after.addAll(rows);
        }
        if (audit) { new AuditRecorder().record(request, object, before, after); }
        return true;
    }

    private static List<SnapshotRow> tabRows(PhysicalSnapshotReader reader, AssignToOrgServiceImpl tab,
            String material, String target, String group) throws BusinessException {
        return relation(reader, tab.getAssignTableName(), tab.getOrgBean().getTable().getPrimaryKeyName(),
            material, target, group);
    }

    private static List<SnapshotRow> relation(PhysicalSnapshotReader reader, String table, String key,
            String material, String target, String group) throws BusinessException {
        List<SnapshotRow> rows = reader.readLocked(table, key, "pk_material", "pk_material", material,
            "pk_org", target, true);
        EsignValues.require(rows.size() <= 1, "存在多条物料组织记录，无法安全分配。");
        for (SnapshotRow row : rows) {
            EsignValues.require(group.equals(row.getValues().get("pk_group")), "物料组织记录所属集团不一致。");
        }
        return rows;
    }

    private static void requireFlatTab(IBean bean) throws BusinessException {
        List<IAssociation> associations = bean.getAssociations();
        if (associations == null) { return; }
        for (IAssociation association : associations) {
            boolean writesChildren = association.getType() == AssociationKind.Composite
                || (association.getType() == AssociationKind.Relation
                    && !MDUtil.isRefType(association.getStartAttribute().getDataType())
                    && !association.getStartAttribute().isCalculation());
            EsignValues.require(!writesChildren, "物料分配页签包含待核实的级联写入。");
        }
    }

    private static void requireNoErrors(ErrLogReturnValue result) throws BusinessException {
        ErrLogResult errors = result == null ? null : result.getErrLogResult();
        EsignValues.require(result != null && (errors == null || errors.getErrorMessagegNum() == 0),
            "原生物料服务拒绝了本次目标组织分配。");
    }
}
