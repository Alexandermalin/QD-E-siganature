package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import nc.itf.uapbd.qd.esign.IArchiveTreeEnableAdapter;
import nc.vo.bd.cust.custclass.CustClassVO;
import nc.vo.bd.material.marbasclass.MarBasClassVO;
import nc.vo.bd.rack.RackVO;
import nc.vo.bd.supplier.supplierclass.SupplierclassVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveTreeEnableCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Native classification enable changes the selected row, unlike the cascading disable. */
public final class ArchiveTreeEnableAdapter implements IArchiveTreeEnableAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { selected(context,command); }

    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        BusinessObjectRef ref=selected(context,command);
        SuperVO value=((ArchiveTreeEnableCommand)command).getRoot();
        BaseArchiveOwnerType type=BaseArchiveOwnerType.forDomain(ref.getObjectType());
        EsignPermission.requireArchiveData(ServerIdentity.current(),ArchiveTreeScope.permissionResource(ref.getObjectType()),
            "enable",context.getObjects());
        ArchiveTreeScope.lockBranch(context.getGroupId(),ref.getObjectType(),ref.getObjectPk());
        AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"分类启用缺少受控事务上下文。");
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        List<SnapshotRow> before=reader.readLocked(type.rootTable,type.rootKey,null,type.rootKey,ref.getObjectPk(),
            "pk_group",context.getGroupId(),true);
        requireRow(before,ref);
        if(type==BaseArchiveOwnerType.RACK) EsignValues.require(((RackVO)value).getPk_stordoc()!=null
            && ((RackVO)value).getPk_stordoc().equals(before.get(0).getValues().get("pk_stordoc")),"货位所属仓库已变化。");
        if(type==BaseArchiveOwnerType.SUPPLIER_CLASS) requireSupplierAncestors(reader,context.getGroupId(),before.get(0));
        Object state=value.getAttributeValue("enablestate");
        String actual=before.get(0).getValues().get("enablestate");
        EsignValues.require(state!=null && actual!=null && actual.equals(state.toString()) && !"2".equals(actual),
            "分类状态已变化或已经启用，请刷新后重试。");
        if(scope.isEnabledFor(ref.getClientObjectId())) scope.before(ref.getClientObjectId(),Collections.singletonList(withParent(before.get(0),type)));
        SuperVO result=enable(type,value);
        EsignValues.require(result!=null && result.getClass()==value.getClass() && ref.getObjectPk().equals(result.getPrimaryKey()),
            "原生分类启用未返回所选档案。");
        List<SnapshotRow> after=reader.read(type.rootTable,type.rootKey,null,type.rootKey,ref.getObjectPk(),
            "pk_group",context.getGroupId(),true);
        requireRow(after,ref);
        EsignValues.require("2".equals(after.get(0).getValues().get("enablestate")),"无法核实分类启用结果，业务必须回滚。");
        if(scope.isEnabledFor(ref.getClientObjectId())) scope.after(ref.getClientObjectId(),Collections.singletonList(withParent(after.get(0),type)));
        return result;
    }

    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        EsignValues.require(context!=null && context.getObjects().size()==1 && result instanceof SuperVO
            && context.getObjects().get(0).getObjectPk().equals(((SuperVO)result).getPrimaryKey()),"原生分类启用结果不一致。");
        return context.getObjects();
    }

    private static BusinessObjectRef selected(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && "ENABLE".equals(context.getOperationType()) && "Enable".equals(context.getButtonCode())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null
            && context.getObjects().size()==1 && command instanceof ArchiveTreeEnableCommand,"分类启用载荷或动作不正确。");
        BusinessObjectRef ref=context.getObjects().get(0);
        BaseArchiveOwnerType type=BaseArchiveOwnerType.forDomain(ref.getObjectType());
        SuperVO value=((ArchiveTreeEnableCommand)command).getRoot();
        boolean route=type==BaseArchiveOwnerType.CUSTOMER_CLASS && ("10140CCLG".equals(context.getNodeCode()) || "10140CCLO".equals(context.getNodeCode()))
            || type==BaseArchiveOwnerType.SUPPLIER_CLASS && ("10140SCLG".equals(context.getNodeCode()) || "10140SCLO".equals(context.getNodeCode()))
            || type==BaseArchiveOwnerType.MATERIAL_CLASS && ("10140MCLG".equals(context.getNodeCode()) || "10140MCLO".equals(context.getNodeCode()))
            || type==BaseArchiveOwnerType.RACK && "10140RACK".equals(context.getNodeCode());
        boolean exact=type==BaseArchiveOwnerType.CUSTOMER_CLASS && value!=null && value.getClass()==CustClassVO.class
            || type==BaseArchiveOwnerType.SUPPLIER_CLASS && value!=null && value.getClass()==SupplierclassVO.class
            || type==BaseArchiveOwnerType.MATERIAL_CLASS && value!=null && value.getClass()==MarBasClassVO.class
            || type==BaseArchiveOwnerType.RACK && value!=null && value.getClass()==RackVO.class;
        EsignValues.require(route && exact && ref.getParentClientObjectId()==null && ref.getDesiredOwnerOrg()==null
            && context.getOrgId()!=null && context.getOrgId().equals(ref.getTargetOrg())
            && ref.getObjectPk()!=null && ref.getObjectPk().equals(value.getPrimaryKey())
            && context.getGroupId().equals(value.getAttributeValue("pk_group")) && ref.getPolicyOwnerOrg()!=null
            && ref.getPolicyOwnerOrg().equals(value.getAttributeValue("pk_org")),"分类启用的节点、档案或所属组织不一致。");
        return ref;
    }
    private static SuperVO enable(BaseArchiveOwnerType type,SuperVO value) throws BusinessException {
        NCLocator locator=NCLocator.getInstance();
        if(type==BaseArchiveOwnerType.CUSTOMER_CLASS) return locator.lookup(ICustClassService.class).enableCustcl((CustClassVO)value);
        if(type==BaseArchiveOwnerType.MATERIAL_CLASS) return locator.lookup(IMaterialBasClassService.class).enableMaterialBasClass((MarBasClassVO)value);
        if(type==BaseArchiveOwnerType.RACK) return locator.lookup(IRackService.class).enableRack((RackVO)value);
        Object result=locator.lookup(ISupplierclassService.class).enableSupplierclassVO((SupplierclassVO)value);
        EsignValues.require(result instanceof SupplierclassVO,"原生供应商分类启用结果不完整。");
        return (SupplierclassVO)result;
    }
    private static void requireRow(List<SnapshotRow> rows,BusinessObjectRef ref) throws BusinessException {
        EsignValues.require(rows.size()==1 && ref.getObjectPk().equals(rows.get(0).getRowId())
            && ref.getPolicyOwnerOrg()!=null && ref.getPolicyOwnerOrg().equals(rows.get(0).getValues().get("pk_org")),
            "分类档案不存在或所属组织已变化。");
    }
    private static SnapshotRow withParent(SnapshotRow row,BaseArchiveOwnerType type) {
        String column=type==BaseArchiveOwnerType.MATERIAL_CLASS || type==BaseArchiveOwnerType.RACK?"pk_parent":"parent_id";
        return new SnapshotRow(row.getEntity(),row.getRowId(),row.getValues().get(column),row.getValues(),row.getTypes(),row.getLabels(),true);
    }
    /** NC's supplier client forbids enabling a node below a disabled ancestor. */
    private static void requireSupplierAncestors(PhysicalSnapshotReader reader,String group,SnapshotRow row) throws BusinessException {
        Set<String> visited=new HashSet<String>();visited.add(row.getRowId());
        String parent=row.getValues().get("parent_id");
        while(parent!=null && !parent.isEmpty() && !"~".equals(parent)) {
            EsignValues.require(visited.size()<1000 && visited.add(parent),"供应商分类上级关系无效。");
            List<SnapshotRow> rows=reader.read("bd_supplierclass","pk_supplierclass",null,"pk_supplierclass",parent,"pk_group",group,true);
            EsignValues.require(rows.size()==1 && parent.equals(rows.get(0).getRowId()),"无法核实供应商分类的上级。");
            EsignValues.require(!"3".equals(rows.get(0).getValues().get("enablestate")),"上级供应商分类已停用，不能启用当前分类。");
            parent=rows.get(0).getValues().get("parent_id");
        }
    }
}
