package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.bd.material.fi.IMaterialFiService;
import nc.itf.bd.material.pu.IMaterialPuService;
import nc.itf.bd.material.cost.IMaterialCostService;
import nc.itf.bd.material.plan.IMaterialPlanService;
import nc.itf.bd.material.prod.IMaterialProdService;
import nc.itf.bd.material.sale.IMaterialSaleService;
import nc.itf.bd.material.stock.IMaterialStockService;
import nc.itf.uapbd.qd.esign.IMaterialOrgInfoOperationAdapter;
import nc.vo.bd.material.MaterialVO;
import nc.vo.bd.material.fi.MaterialFiVO;
import nc.vo.bd.material.pu.MaterialPuVO;
import nc.vo.bd.material.cost.MaterialCostVO;
import nc.vo.bd.material.plan.MaterialPlanVO;
import nc.vo.bd.material.prod.MaterialProdVO;
import nc.vo.bd.material.sale.MaterialSaleVO;
import nc.vo.bd.material.stock.MaterialStockVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialOrgInfoCommand;
import nc.vo.uapbd.qd.esign.MaterialOrgPage;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** 使用集团物料页签的原生修改服务，按实际行和目标组织授权并采集同事务快照。 */
public final class MaterialOrgInfoOperationAdapter implements IMaterialOrgInfoOperationAdapter {
    public void validate(OperationContext context, Serializable command) throws BusinessException {
        draft(context, page(context), command);
    }

    public Serializable execute(OperationContext context, Serializable command) throws BusinessException {
        MaterialOrgPage page = page(context);
        SuperVO value = draft(context, page, command);
        BusinessObjectRef object = context.getObjects().get(0);
        String materialPk = attribute(value, "pk_material");
        String targetOrg = attribute(value, "pk_org");
        ServerIdentity identity = ServerIdentity.current();
        EsignValues.require(context.getGroupId().equals(identity.groupId), "Material page group changed");
        EsignPermission.requirePageOrganization(identity, context.getNodeCode(), context.getButtonCode(), page.permission, targetOrg);

        PhysicalSnapshotReader reader = new PhysicalSnapshotReader();
        List<SnapshotRow> parent = reader.readLocked("bd_material", "pk_material", null, "pk_material", materialPk,
                "pk_group", context.getGroupId(), true);
        EsignValues.require(parent.size() == 1 && expectedOwner(context).equals(parent.get(0).getValues().get("pk_org")),
                "Material page parent is outside the authorized group");
        String code = parent.get(0).getValues().get("code");
        EsignValues.require(present(code) && (!present(object.getBillCode()) || object.getBillCode().equals(code)),
                "Material page parent code changed after signature preparation");

        List<SnapshotRow> before = reader.readLocked(page.table, page.key, "pk_material", page.key, object.getObjectPk(),
                "pk_group", context.getGroupId(), true);
        requireOwnedRow(before, context.getGroupId(), targetOrg, materialPk);
        List<SnapshotRow> children = childRows(reader, page, object.getObjectPk(), true);
        requireChildPayload(reader, page, value, object.getObjectPk(), children);
        AuditCaptureScope scope = AuditCaptureScope.current();
        boolean audit = scope != null && scope.isEnabled();
        if (audit) {
            before.addAll(children);
            scope.before(scope.objectId(page.domain, object.getObjectPk()), before);
        }

        if ("DELETE".equals(context.getOperationType())) {
            delete(page, value);
            EsignValues.require(reader.read(page.table, page.key, "pk_material", page.key, object.getObjectPk(),
                "pk_group", context.getGroupId(), true).isEmpty()
                && childRows(reader, page, object.getObjectPk(), false).isEmpty(),
                "原生物料页签删除未清除全部有效数据行，请核对事务。");
            return value;
        }
        SuperVO saved = update(page, value);
        requireResult(page, object, saved);
        EsignValues.require(context.getGroupId().equals(attribute(saved, "pk_group"))
                && targetOrg.equals(attribute(saved, "pk_org")) && materialPk.equals(attribute(saved, "pk_material")),
                "Native material page returned a different organization or material");
        List<SnapshotRow> after = reader.read(page.table, page.key, "pk_material", page.key, object.getObjectPk(),
                "pk_group", context.getGroupId(), true);
        requireOwnedRow(after, context.getGroupId(), targetOrg, materialPk);
        if (audit) {
            after.addAll(childRows(reader, page, object.getObjectPk(), false));
            scope.after(scope.objectId(page.domain, object.getObjectPk()), after);
        }
        return saved;
    }

    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result) throws BusinessException {
        MaterialOrgPage page = page(context);
        BusinessObjectRef object = context.getObjects().get(0);
        SuperVO saved = requireResult(page, object, result);
        MaterialVO parent = (MaterialVO)new BaseDAO().retrieveByPK(MaterialVO.class, attribute(saved, "pk_material"),
                new String[]{"pk_material", "pk_group", "pk_org", "code"});
        EsignValues.require(parent != null && context.getGroupId().equals(parent.getPk_group())
                && expectedOwner(context).equals(parent.getPk_org()) && present(parent.getCode()),
                "Saved material page has no authorized parent code");
        return Collections.singletonList(new BusinessObjectRef(object.getClientObjectId(), page.domain, saved.getPrimaryKey(),
                parent.getCode(), object.getParentClientObjectId()));
    }

    private static MaterialOrgPage page(OperationContext context) throws BusinessException {
        EsignValues.require(context != null && ("10140MAG".equals(context.getNodeCode()) || "10140MAO".equals(context.getNodeCode()))
                && ("UPDATE".equals(context.getOperationType()) || "DELETE".equals(context.getOperationType()))
                && present(context.getGroupId())
                && present(context.getOrgId()) && context.getObjects().size() == 1,
                "Material page requires one update object and an operation organization");
        MaterialOrgPage page = "DELETE".equals(context.getOperationType())
            ? MaterialOrgPage.forDeleteButton(context.getButtonCode()) : MaterialOrgPage.forButton(context.getButtonCode());
        if (page != null && ("DELETE".equals(context.getOperationType()) || page.supportsUpdate())) return page;
        throw new BusinessException("Unsupported material page save action");
    }

    private static SuperVO draft(OperationContext context, MaterialOrgPage page, Serializable command) throws BusinessException {
        EsignValues.require(command instanceof MaterialOrgInfoCommand, "Material organization page command is required");
        SuperVO value = ((MaterialOrgInfoCommand)command).getValue();
        BusinessObjectRef object = context.getObjects().get(0);
        EsignValues.require(value != null && value.getClass() == page.type && page.domain.equals(object.getObjectType())
                && present(object.getObjectPk()) && object.getObjectPk().equals(value.getPrimaryKey())
                && context.getGroupId().equals(attribute(value, "pk_group")) && present(attribute(value, "pk_org"))
                && present(attribute(value, "pk_material")), "Material page command differs from the authorized row");
        return value;
    }

    private static SuperVO requireResult(MaterialOrgPage page, BusinessObjectRef object, Serializable result) throws BusinessException {
        EsignValues.require(result != null && result.getClass() == page.type
                && object.getObjectPk().equals(((SuperVO)result).getPrimaryKey()),
                "Native material page returned a different row");
        return (SuperVO)result;
    }

    private static SuperVO update(MaterialOrgPage page, SuperVO value) throws BusinessException {
        NCLocator locator = NCLocator.getInstance();
        switch (page) {
            case FINANCE: return locator.lookup(IMaterialFiService.class).updateMaterialFiVO((MaterialFiVO)value);
            case PURCHASE: return locator.lookup(IMaterialPuService.class).updateMaterialPuVO((MaterialPuVO)value);
            case SALE: return locator.lookup(IMaterialSaleService.class).updateMaterialSaleVO((MaterialSaleVO)value);
            case STOCK: return locator.lookup(IMaterialStockService.class).updateMaterialStockVO((MaterialStockVO)value);
            case PLAN: return locator.lookup(IMaterialPlanService.class).updateMaterialPlanVO((MaterialPlanVO)value);
            case COST: return locator.lookup(IMaterialCostService.class).updateMaterialCostVO((MaterialCostVO)value);
            default: throw new BusinessException("Unsupported material page save action");
        }
    }

    private static void delete(MaterialOrgPage page, SuperVO value) throws BusinessException {
        NCLocator locator = NCLocator.getInstance();
        switch (page) {
            case FINANCE: locator.lookup(IMaterialFiService.class).deleteMaterialFiVO((MaterialFiVO)value); return;
            case PURCHASE: locator.lookup(IMaterialPuService.class).deleteMaterialPuVO((MaterialPuVO)value); return;
            case SALE: locator.lookup(IMaterialSaleService.class).deleteMaterialSaleVO((MaterialSaleVO)value); return;
            case STOCK: locator.lookup(IMaterialStockService.class).deleteMaterialStockVO((MaterialStockVO)value); return;
            case PLAN: locator.lookup(IMaterialPlanService.class).deleteMaterialPlanVO((MaterialPlanVO)value); return;
            case PROD: locator.lookup(IMaterialProdService.class).deleteMaterialProdVO((MaterialProdVO)value); return;
            case COST: locator.lookup(IMaterialCostService.class).deleteMaterialCostVO((MaterialCostVO)value); return;
            default: throw new BusinessException("Unsupported material page delete action");
        }
    }

    private static List<SnapshotRow> childRows(PhysicalSnapshotReader reader, MaterialOrgPage page,
            String parentPk, boolean lock) throws BusinessException {
        if (page.childTable == null) return new ArrayList<SnapshotRow>();
        return lock ? reader.readLocked(page.childTable, page.childKey, page.key, page.key, parentPk, true)
                : reader.read(page.childTable, page.childKey, page.key, page.key, parentPk, true);
    }

    /** Reject a child id from another parent, even when this object's audit switch is disabled. */
    private static void requireChildPayload(final PhysicalSnapshotReader reader, final MaterialOrgPage page, SuperVO value,
            String parentPk, List<SnapshotRow> existing) throws BusinessException {
        Set<String> owned = new HashSet<String>();
        for (SnapshotRow row : existing) owned.add(row.getRowId());
        validateChildren(page, page.children(value), parentPk, owned, new ChildLookup() {
            public boolean exists(String pk) throws BusinessException {
                return !reader.readLocked(page.childTable, page.childKey, page.key,
                    page.childKey, pk, false).isEmpty();
            }
        });
    }

    interface ChildLookup { boolean exists(String pk) throws BusinessException; }

    static void validateChildren(MaterialOrgPage page, SuperVO[] children, String parentPk,
            Set<String> owned, ChildLookup lookup) throws BusinessException {
        if (children == null) return;
        Set<String> seen = new HashSet<String>();
        for (SuperVO child : children) {
            EsignValues.require(child != null && child.getClass() == page.childType, "物料页签子行类型不正确。");
            validateChildRow(parentPk, attribute(child, page.key), child.getPrimaryKey(), child.getStatus(), owned, seen, lookup);
        }
    }

    static void validateChildRow(String parentPk, String parent, String pk, int status, Set<String> owned,
            Set<String> seen, ChildLookup lookup) throws BusinessException {
        EsignValues.require(status == VOStatus.NEW || status == VOStatus.UPDATED || status == VOStatus.DELETED
            || status == VOStatus.UNCHANGED, "物料页签子行状态不正确。");
        EsignValues.require(parent == null || parentPk.equals(parent), "物料页签子行属于其他数据行。");
        if (!present(pk)) {
            EsignValues.require(status == VOStatus.NEW, "已有物料页签子行缺少主键。");
            return;
        }
        EsignValues.require(seen.add(pk), "物料页签子行主键重复。");
        if (status == VOStatus.NEW) {
            EsignValues.require(!lookup.exists(pk), "新增物料页签子行主键已存在。");
        } else {
            EsignValues.require(owned.contains(pk), "物料页签子行不属于当前物料组织行。");
        }
    }

    private static void requireOwnedRow(List<SnapshotRow> rows, String group, String org, String material)
            throws BusinessException {
        EsignValues.require(rows.size() == 1, "Material page row is not active or no longer available");
        Map<String, String> data = rows.get(0).getValues();
        EsignValues.require(group.equals(data.get("pk_group")) && org.equals(data.get("pk_org"))
                && material.equals(data.get("pk_material")), "Material page row belongs to another organization or material");
    }

    private static String attribute(SuperVO value, String code) {
        Object attribute = value.getAttributeValue(code);
        return attribute instanceof String ? (String)attribute : null;
    }

    private static String expectedOwner(OperationContext context) throws BusinessException {
        String owner = OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            ? context.getObjects().get(0).getPolicyOwnerOrg() : context.getOrgId();
        EsignValues.require(present(owner), "Material page owner is unavailable");
        return owner;
    }

    private static boolean present(String value) { return value != null && !value.trim().isEmpty(); }
}
