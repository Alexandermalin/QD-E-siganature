package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.ArchiveTreeSaveImpact;
import nc.bs.uapbd.qd.esign.core.ArchiveTreeScope;
import nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.bd.cust.custclass.ICustClassService;
import nc.itf.bd.material.marbasclass.IMaterialBasClassExtendService;
import nc.itf.bd.rack.IRackService;
import nc.itf.bd.supplier.supplierclass.ISupplierclassService;
import nc.itf.uapbd.qd.esign.IArchiveTreeDeleteAdapter;
import nc.vo.bd.cust.custclass.CustClassVO;
import nc.vo.bd.material.marbasclass.MarBasClassVO;
import nc.vo.bd.rack.RackVO;
import nc.vo.bd.supplier.supplierclass.SupplierclassVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveTreeDeleteCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Candidate single-leaf delete, preserving the original native delete service and transaction. */
public final class ArchiveTreeDeleteAdapter implements IArchiveTreeDeleteAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { value(context,command); }
    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        SuperVO value=value(context,command);BusinessObjectRef ref=context.getObjects().get(0);
        ArchiveTreeSaveAdapter.Tree tree=ArchiveTreeSaveAdapter.tree(ref.getObjectType());ServerIdentity identity=ServerIdentity.current();
        EsignValues.require(context.getGroupId().equals(identity.groupId),"分类删除的集团已变化。");
        EsignPermission.requireArchiveData(identity,ArchiveTreeScope.permissionResource(tree.domain),"delete",context.getObjects());
        ArchiveTreeMutationLock.acquire(tree.domain,tree==ArchiveTreeSaveAdapter.Tree.MATERIAL);
        SuperVO current=(SuperVO)new BaseDAO().retrieveByPK(tree.voClass,ref.getObjectPk());
        requireCurrent(context,value,current);
        ArchiveTreeScope.lockBranch(context.getGroupId(),tree.domain,ref.getObjectPk());
        requireLeaf(context,ref);
    }
    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        SuperVO requested=value(context,command);BusinessObjectRef ref=context.getObjects().get(0);
        ArchiveTreeSaveAdapter.Tree tree=ArchiveTreeSaveAdapter.tree(ref.getObjectType());BaseArchiveOwnerType type=BaseArchiveOwnerType.forDomain(tree.domain);
        SuperVO value=(SuperVO)new BaseDAO().retrieveByPK(tree.voClass,ref.getObjectPk());
        requireCurrent(context,requested,value);
        requireLeaf(context,ref);PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        List<SnapshotRow> before=reader.readLocked(type.rootTable,type.rootKey,null,type.rootKey,ref.getObjectPk(),"pk_group",context.getGroupId(),true);
        EsignValues.require(before.size()==1 && ref.getPolicyOwnerOrg().equals(before.get(0).getValues().get("pk_org")),"分类不存在或所属组织已变化。");
        SnapshotRow root=before.get(0);String parentId=root.getValues().get(ArchiveTreeSaveImpact.parentField(tree.domain));
        List<SnapshotRow> parent=null;
        if(parentId!=null && !parentId.isEmpty() && !"~".equals(parentId)) {
            parent=reader.readLocked(type.rootTable,type.rootKey,null,type.rootKey,parentId,"pk_group",context.getGroupId(),true);
            EsignValues.require(parent.size()==1,"无法核实待删除分类的上级。");
        }
        AuditCaptureScope scope=AuditCaptureScope.current();EsignValues.require(scope!=null,"分类删除缺少受控事务上下文。");
        List<SnapshotRow> extension=tree==ArchiveTreeSaveAdapter.Tree.MATERIAL
            ?MaterialClassExtension.rows(reader,ref.getObjectPk(),context.getGroupId(),ref.getPolicyOwnerOrg(),true)
            :Collections.<SnapshotRow>emptyList();
        if(scope.isEnabledFor(ref.getClientObjectId())) {
            List<SnapshotRow> snapshot=new java.util.ArrayList<SnapshotRow>();
            snapshot.add(new SnapshotRow(root.getEntity(),root.getRowId(),parentId,root.getValues(),root.getTypes(),root.getLabels(),true));
            snapshot.addAll(extension);
            scope.before(ref.getClientObjectId(),snapshot);
        }
        delete(context,tree,value,extension);
        EsignValues.require(reader.read(type.rootTable,type.rootKey,null,type.rootKey,ref.getObjectPk(),"pk_group",context.getGroupId(),true).isEmpty(),
            "无法核实树形档案删除结果，业务必须回滚。");
        EsignValues.require(reader.read(type.rootTable,type.rootKey,null,ArchiveTreeSaveImpact.parentField(tree.domain),
            ref.getObjectPk(),true).isEmpty(),"树形档案删除后仍存在活动子级，业务必须回滚。");
        if(tree==ArchiveTreeSaveAdapter.Tree.MATERIAL)
            EsignValues.require(MaterialClassExtension.rows(reader,ref.getObjectPk(),context.getGroupId(),ref.getPolicyOwnerOrg(),false).isEmpty(),
                "物料分类供应商扩展仍有活动数据，业务必须回滚。");
        if(parent!=null) {
            List<SnapshotRow> after=reader.read(type.rootTable,type.rootKey,null,type.rootKey,parentId,"pk_group",context.getGroupId(),true);
            EsignValues.require(after.size()==1 && parent.get(0).getValues().equals(after.get(0).getValues()),
                "原生删除还修改了上级档案，业务必须回滚并核对影响范围。");
        }
        return value;
    }
    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        BusinessObjectRef ref=context.getObjects().get(0);
        EsignValues.require(result instanceof SuperVO && ref.getObjectPk().equals(ArchiveTreeSaveAdapter.pk((SuperVO)result)),"分类删除结果不一致。");
        return Collections.singletonList(ref);
    }
    static void requireLeaf(OperationContext context,BusinessObjectRef ref) throws BusinessException {
        List<BusinessObjectRef> branch=ArchiveTreeScope.expand(context.getGroupId(),Collections.singletonList(ref),false);
        requireLeaf(branch,ref);
    }
    /** Use the persisted row as the native delete payload; never trust a client-supplied parent or timestamp. */
    static void requireCurrent(OperationContext context,SuperVO requested,SuperVO current) throws BusinessException {
        EsignValues.require(context!=null && requested!=null && current!=null && context.getObjects().size()==1
            && requested.getClass()==current.getClass(),"分类删除的档案已变化。");
        BusinessObjectRef ref=context.getObjects().get(0);ArchiveTreeSaveAdapter.Tree tree=ArchiveTreeSaveAdapter.tree(ref.getObjectType());
        String parent=ArchiveTreeSaveImpact.parentField(tree.domain);
        Object ts=timestamp(current);
        EsignValues.require(ref.getObjectPk().equals(ArchiveTreeSaveAdapter.pk(current))
            && ref.getPolicyOwnerOrg().equals(ArchiveTreeSaveAdapter.text(current,"pk_org"))
            && context.getGroupId().equals(ArchiveTreeSaveAdapter.text(current,"pk_group"))
            && java.util.Objects.equals(ArchiveTreeSaveAdapter.text(current,"code"),ref.getBillCode())
            && java.util.Objects.equals(ArchiveTreeSaveAdapter.text(current,"innercode"),ArchiveTreeSaveAdapter.text(requested,"innercode"))
            && java.util.Objects.equals(ArchiveTreeSaveAdapter.text(current,parent),ArchiveTreeSaveAdapter.text(requested,parent))
            && ts!=null && ts.equals(timestamp(requested))
            && (tree!=ArchiveTreeSaveAdapter.Tree.RACK || java.util.Objects.equals(ArchiveTreeSaveAdapter.text(current,"pk_stordoc"),
                ArchiveTreeSaveAdapter.text(requested,"pk_stordoc"))),"分类删除的档案、版本、上级或仓库已变化。");
    }
    private static Object timestamp(SuperVO value) {
        if(value instanceof CustClassVO) return ((CustClassVO)value).getTs();
        if(value instanceof SupplierclassVO) return ((SupplierclassVO)value).getTs();
        if(value instanceof MarBasClassVO) return ((MarBasClassVO)value).getTs();
        if(value instanceof RackVO) return ((RackVO)value).getTs();
        throw new IllegalArgumentException("Unsupported tree archive timestamp");
    }
    static void requireLeaf(List<BusinessObjectRef> branch,BusinessObjectRef ref) throws BusinessException {
        EsignValues.require(branch!=null && ref!=null && ref.getObjectPk()!=null && branch.size()==1 && branch.get(0)!=null
            && ref.getObjectPk().equals(branch.get(0).getObjectPk()) && ref.getObjectType().equals(branch.get(0).getObjectType())
            && ref.getClientObjectId().equals(branch.get(0).getClientObjectId()) && branch.get(0).getParentClientObjectId()==null,
            "所选档案存在下级，请先处理下级后再删除。");
    }
    private static void delete(OperationContext context,ArchiveTreeSaveAdapter.Tree tree,SuperVO value,List<SnapshotRow> extension)
            throws BusinessException {
        NCLocator locator=NCLocator.getInstance();
        if(tree==ArchiveTreeSaveAdapter.Tree.CUSTOMER) locator.lookup(ICustClassService.class).deleteCustClass((CustClassVO)value);
        else if(tree==ArchiveTreeSaveAdapter.Tree.SUPPLIER) locator.lookup(ISupplierclassService.class).deleteSupplierclassVO((SupplierclassVO)value);
        else if(tree==ArchiveTreeSaveAdapter.Tree.RACK) locator.lookup(IRackService.class).deleteRack((RackVO)value);
        else {
            MarBasClassVO materialClass=(MarBasClassVO)value;
            locator.lookup(IMaterialBasClassExtendService.class).deleteMarBasClAndExtendVO(materialClass,
                MaterialClassExtension.deleteContext(context.getNodeCode(),materialClass,extension));
        }
    }
    private static SuperVO value(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && "Delete".equals(context.getButtonCode()) && "DELETE".equals(context.getOperationType())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null && context.getOrgId()!=null
            && context.getObjects().size()==1 && command instanceof ArchiveTreeDeleteCommand,"分类删除载荷或动作不正确。");
        BusinessObjectRef ref=context.getObjects().get(0);ArchiveTreeSaveAdapter.Tree tree=ArchiveTreeSaveAdapter.tree(ref.getObjectType());
        SuperVO value=((ArchiveTreeDeleteCommand)command).getValue();
        EsignValues.require(tree.matchesNode(context.getNodeCode()) && value!=null && value.getClass()==tree.voClass
            && ref.getObjectPk()!=null && ref.getObjectPk().equals(ArchiveTreeSaveAdapter.pk(value))
            && context.getGroupId().equals(ArchiveTreeSaveAdapter.text(value,"pk_group")) && ref.getPolicyOwnerOrg()!=null
            && ref.getPolicyOwnerOrg().equals(ArchiveTreeSaveAdapter.text(value,"pk_org"))
            && context.getOrgId().equals(ref.getTargetOrg()) && ref.getDesiredOwnerOrg()==null && ref.getParentClientObjectId()==null,
            "分类删除的档案或所属组织不一致。");
        return value;
    }
}
