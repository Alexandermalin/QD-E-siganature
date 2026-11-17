package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.ArchiveTreeScope;
import nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.bd.cust.custclass.ICustClassService;
import nc.itf.bd.material.marbasclass.IMaterialBasClassService;
import nc.itf.bd.rack.IRackService;
import nc.itf.bd.supplier.supplierclass.ISupplierclassService;
import nc.itf.uapbd.qd.esign.IArchiveTreeDisableAdapter;
import nc.vo.bd.cust.custclass.CustClassVO;
import nc.vo.bd.material.marbasclass.MarBasClassVO;
import nc.vo.bd.rack.RackVO;
import nc.vo.bd.supplier.supplierclass.SupplierclassVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveTreeDisableCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** One native disable call; every affected archive has its own owner policy and snapshots. */
public final class ArchiveTreeDisableAdapter implements IArchiveTreeDisableAdapter {
    public void validate(OperationContext context, Serializable command) throws BusinessException {
        selected(context, command);
    }

    public Serializable execute(OperationContext context, Serializable command) throws BusinessException {
        BusinessObjectRef rootRef = selected(context, command);
        SuperVO value = ((ArchiveTreeDisableCommand)command).getRoot();
        ArchiveTreeScope.verify(context.getGroupId(), context.getObjects(), true);
        EsignPermission.requireArchiveData(ServerIdentity.current(), ArchiveTreeScope.permissionResource(rootRef.getObjectType()),
            "disable", context.getObjects());
        BaseArchiveOwnerType type = BaseArchiveOwnerType.forDomain(rootRef.getObjectType());
        PhysicalSnapshotReader reader = new PhysicalSnapshotReader();
        AuditCaptureScope scope = AuditCaptureScope.current();
        EsignValues.require(scope != null, "分类停用缺少受控事务上下文。");
        for (BusinessObjectRef ref : context.getObjects()) {
            List<SnapshotRow> before = reader.readLocked(type.rootTable, type.rootKey, null,
                type.rootKey, ref.getObjectPk(), "pk_group", context.getGroupId(), true);
            requireRow(before, ref);
            if (rootRef.getClientObjectId().equals(ref.getClientObjectId())) {
                Object state = value.getAttributeValue("enablestate");
                String actualState = before.get(0).getValues().get("enablestate");
                EsignValues.require(state != null && actualState != null && actualState.equals(state.toString())
                    && !"3".equals(actualState), "分类状态已变化或已经停用，请刷新后重试。");
                if(type==BaseArchiveOwnerType.RACK) EsignValues.require(((RackVO)value).getPk_stordoc()!=null
                    && ((RackVO)value).getPk_stordoc().equals(before.get(0).getValues().get("pk_stordoc")),"货位所属仓库已变化。");
            }
            if (scope.isEnabledFor(ref.getClientObjectId())) {
                scope.before(ref.getClientObjectId(), Collections.singletonList(withParent(before.get(0), type)));
            }
        }
        SuperVO result = disable(type, value);
        EsignValues.require(result != null && result.getClass() == value.getClass()
            && rootRef.getObjectPk().equals(result.getPrimaryKey()), "原生分类停用未返回所选档案。");
        for (BusinessObjectRef ref : context.getObjects()) {
            List<SnapshotRow> after = reader.read(type.rootTable, type.rootKey, null,
                type.rootKey, ref.getObjectPk(), "pk_group", context.getGroupId(), true);
            requireRow(after, ref);
            EsignValues.require("3".equals(after.get(0).getValues().get("enablestate")),
                "无法核实分类级联停用结果，业务必须回滚。");
            if (scope.isEnabledFor(ref.getClientObjectId())) {
                scope.after(ref.getClientObjectId(), Collections.singletonList(withParent(after.get(0), type)));
            }
        }
        return result;
    }

    public List<BusinessObjectRef> describeResult(OperationContext context, Serializable result) throws BusinessException {
        BusinessObjectRef root = root(context);
        EsignValues.require(result instanceof SuperVO && root.getObjectPk().equals(((SuperVO)result).getPrimaryKey()),
            "原生分类停用返回的档案不一致。");
        return context.getObjects();
    }

    private static BusinessObjectRef selected(OperationContext context, Serializable command) throws BusinessException {
        EsignValues.require(context != null && "DISABLE".equals(context.getOperationType())
            && "Disable".equals(context.getButtonCode()) && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            && command instanceof ArchiveTreeDisableCommand, "分类停用载荷或动作不正确。");
        BusinessObjectRef selected = root(context);
        BaseArchiveOwnerType type = BaseArchiveOwnerType.forDomain(selected.getObjectType());
        SuperVO value = ((ArchiveTreeDisableCommand)command).getRoot();
        boolean route = type == BaseArchiveOwnerType.CUSTOMER_CLASS
                && ("10140CCLG".equals(context.getNodeCode()) || "10140CCLO".equals(context.getNodeCode()))
            || type == BaseArchiveOwnerType.SUPPLIER_CLASS
                && ("10140SCLG".equals(context.getNodeCode()) || "10140SCLO".equals(context.getNodeCode()))
            || type == BaseArchiveOwnerType.MATERIAL_CLASS
                && ("10140MCLG".equals(context.getNodeCode()) || "10140MCLO".equals(context.getNodeCode()))
            || type == BaseArchiveOwnerType.RACK && "10140RACK".equals(context.getNodeCode());
        boolean exactType = type == BaseArchiveOwnerType.CUSTOMER_CLASS && value != null && value.getClass() == CustClassVO.class
            || type == BaseArchiveOwnerType.SUPPLIER_CLASS && value != null && value.getClass() == SupplierclassVO.class
            || type == BaseArchiveOwnerType.MATERIAL_CLASS && value != null && value.getClass() == MarBasClassVO.class
            || type == BaseArchiveOwnerType.RACK && value != null && value.getClass() == RackVO.class;
        EsignValues.require(route && exactType && selected.getObjectPk() != null
            && selected.getObjectPk().equals(value.getPrimaryKey())
            && context.getGroupId().equals(value.getAttributeValue("pk_group"))
            && selected.getPolicyOwnerOrg() != null && selected.getPolicyOwnerOrg().equals(value.getAttributeValue("pk_org")),
            "分类停用数据与授权的节点、档案或所属组织不一致。");
        for (BusinessObjectRef ref : context.getObjects()) {
            EsignValues.require(selected.getObjectType().equals(ref.getObjectType())
                && ref.getObjectPk() != null && ref.getDesiredOwnerOrg() == null, "分类停用范围不正确。");
        }
        return selected;
    }

    private static BusinessObjectRef root(OperationContext context) throws BusinessException {
        BusinessObjectRef root = null;
        for (BusinessObjectRef ref : context.getObjects()) if (ref.getParentClientObjectId() == null) {
            EsignValues.require(root == null, "一次分类停用仅支持一个所选根分类。");
            root = ref;
        }
        EsignValues.require(root != null && ArchiveTreeScope.supports(root.getObjectType()), "无法确定待停用的根分类。");
        return root;
    }

    private static SuperVO disable(BaseArchiveOwnerType type, SuperVO value) throws BusinessException {
        NCLocator locator = NCLocator.getInstance();
        if (type == BaseArchiveOwnerType.CUSTOMER_CLASS)
            return locator.lookup(ICustClassService.class).disableCustcl((CustClassVO)value);
        if (type == BaseArchiveOwnerType.MATERIAL_CLASS)
            return locator.lookup(IMaterialBasClassService.class).disableMaterialBasClass((MarBasClassVO)value);
        if (type == BaseArchiveOwnerType.RACK) return locator.lookup(IRackService.class).disableRack((RackVO)value);
        Object result = locator.lookup(ISupplierclassService.class).disableSupplierclassVO((SupplierclassVO)value);
        EsignValues.require(result instanceof SupplierclassVO, "原生供应商分类停用结果不完整。");
        return (SupplierclassVO)result;
    }

    private static void requireRow(List<SnapshotRow> rows, BusinessObjectRef ref) throws BusinessException {
        EsignValues.require(rows.size() == 1 && ref.getObjectPk().equals(rows.get(0).getRowId())
            && ref.getPolicyOwnerOrg() != null && ref.getPolicyOwnerOrg().equals(rows.get(0).getValues().get("pk_org")),
            "分类档案不存在或所属组织已变化。");
    }

    private static SnapshotRow withParent(SnapshotRow row, BaseArchiveOwnerType type) {
        String column = type == BaseArchiveOwnerType.MATERIAL_CLASS || type == BaseArchiveOwnerType.RACK ? "pk_parent" : "parent_id";
        return new SnapshotRow(row.getEntity(), row.getRowId(), row.getValues().get(column),
            row.getValues(), row.getTypes(), row.getLabels(), true);
    }
}
