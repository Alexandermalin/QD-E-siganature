package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.MaterialProdImpactScope;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.bd.material.prod.IMaterialProdService;
import nc.itf.uapbd.qd.esign.IMaterialProdSaveAdapter;
import nc.vo.bd.material.prod.MaterialProdVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialOrgInfoCommand;
import nc.vo.uapbd.qd.esign.MaterialProdImpact;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Production save candidate: one native call, with separate owner policies for its whole cascade. */
public final class MaterialProdSaveAdapter implements IMaterialProdSaveAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { draft(context,command); }

    /** Revalidate scope and take the native material locks before the policy framework locks rows. */
    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        MaterialProdVO value=draft(context,command);MaterialProdImpact impact=impact(value);
        ServerIdentity identity=ServerIdentity.current();
        EsignValues.require(context.getGroupId().equals(identity.groupId),"生产信息所属集团已变化。");
        EsignPermission.requirePageOrganization(identity,context.getNodeCode(),context.getButtonCode(),"prod_info",impact.getTargetOrg());
        MaterialProdImpactScope.verify(context.getGroupId(),context.getObjects(),impact);
        MaterialProdImpactScope.lockMaterials(context.getGroupId(),context.getObjects(),impact.getTargetOrg());
    }

    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        MaterialProdVO value=draft(context,command);MaterialProdImpact impact=impact(value);BusinessObjectRef selected=root(context);
        ServerIdentity identity=ServerIdentity.current();
        EsignValues.require(context.getGroupId().equals(identity.groupId),"生产信息所属集团已变化。");
        EsignPermission.requirePageOrganization(identity,context.getNodeCode(),context.getButtonCode(),"prod_info",impact.getTargetOrg());
        MaterialProdImpactScope.verify(context.getGroupId(),context.getObjects(),impact);
        AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"生产信息保存缺少受控事务上下文。");
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();QdEsignStore store=new QdEsignStore();
        for(BusinessObjectRef ref:context.getObjects()) {
            List<SnapshotRow> before=row(reader,store,context,ref,impact,true);
            if(ref.getClientObjectId().equals(selected.getClientObjectId()))
                EsignValues.require(value.getPk_material().equals(before.get(0).getValues().get("pk_material")),"生产信息与所选物料不一致。");
            if(scope.isEnabledFor(ref.getClientObjectId())) scope.before(ref.getClientObjectId(),before);
        }
        MaterialProdVO saved=NCLocator.getInstance().lookup(IMaterialProdService.class).updateMaterialProdVO(value);
        EsignValues.require(saved!=null && selected.getObjectPk().equals(saved.getPk_materialprod())
            && context.getGroupId().equals(saved.getPk_group()) && impact.getTargetOrg().equals(saved.getPk_org())
            && Objects.equals(impact.getCostClass(),saved.getPk_marcostclass()) && saved.getClassfeature()!=null
            && impact.isClassFeature()==saved.getClassfeature().booleanValue(),"原生生产信息保存结果与授权不一致。");
        // A newly matching row must not be silently changed without its own policy and snapshot.
        MaterialProdImpactScope.verify(context.getGroupId(),context.getObjects(),impact);
        for(BusinessObjectRef ref:context.getObjects()) {
            List<SnapshotRow> after=row(reader,store,context,ref,impact,false);
            if(scope.isEnabledFor(ref.getClientObjectId())) scope.after(ref.getClientObjectId(),after);
        }
        return saved;
    }

    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        BusinessObjectRef selected=root(context);
        EsignValues.require(result instanceof MaterialProdVO && selected.getObjectPk().equals(((MaterialProdVO)result).getPk_materialprod()),
            "生产信息保存返回了其他档案。");
        return context.getObjects();
    }
    private static List<SnapshotRow> row(PhysicalSnapshotReader reader,QdEsignStore store,OperationContext context,
            BusinessObjectRef ref,MaterialProdImpact impact,boolean lock) throws BusinessException {
        Map<String,Object> owner=store.loadMaterialOwner(context.getGroupId(),"BD_MATERIAL_PROD",ref.getObjectPk(),false);
        String material=owner==null?null:EsignValues.text(owner,"pk_material");
        EsignValues.require(owner!=null && ref.getPolicyOwnerOrg()!=null && ref.getPolicyOwnerOrg().equals(EsignValues.text(owner,"pk_org"))
            && impact.getTargetOrg().equals(EsignValues.text(owner,"target_org"))
            && material!=null && ref.getBillCode()!=null && ref.getBillCode().equals(EsignValues.text(owner,"code")),"关联物料的所属组织或编码已变化。");
        List<SnapshotRow> rows=lock?reader.readLocked("bd_materialprod","pk_materialprod","pk_material","pk_materialprod",
            ref.getObjectPk(),"pk_group",context.getGroupId(),true)
            :reader.read("bd_materialprod","pk_materialprod","pk_material","pk_materialprod",
                ref.getObjectPk(),"pk_group",context.getGroupId(),true);
        EsignValues.require(rows.size()==1 && material.equals(rows.get(0).getValues().get("pk_material"))
            && impact.getTargetOrg().equals(rows.get(0).getValues().get("pk_org")),"生产信息行或目标组织已变化。");
        return rows;
    }
    private static MaterialProdVO draft(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && ("10140MAG".equals(context.getNodeCode()) || "10140MAO".equals(context.getNodeCode()))
            && "PrSave".equals(context.getButtonCode()) && "UPDATE".equals(context.getOperationType())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null
            && context.getOrgId()!=null && command instanceof MaterialOrgInfoCommand,"生产信息保存载荷或节点不正确。");
        BusinessObjectRef selected=root(context);Object raw=((MaterialOrgInfoCommand)command).getValue();
        EsignValues.require(raw!=null && raw.getClass()==MaterialProdVO.class,"生产信息保存的数据类型不正确。");
        MaterialProdVO value=(MaterialProdVO)raw;
        EsignValues.require(selected.getObjectPk().equals(value.getPk_materialprod()) && context.getGroupId().equals(value.getPk_group())
            && value.getPk_material()!=null && value.getPk_org()!=null && value.getClassfeature()!=null,
            "生产信息与授权对象、集团或目标组织不一致。");
        for(BusinessObjectRef ref:context.getObjects()) EsignValues.require(ref.getPolicyOwnerOrg()!=null
            && value.getPk_org().equals(ref.getTargetOrg()) && ref.getDesiredOwnerOrg()==null,"生产信息逐档案授权不完整。");
        return value;
    }
    private static BusinessObjectRef root(OperationContext context) throws BusinessException {
        EsignValues.require(context!=null && !context.getObjects().isEmpty(),"生产保存授权对象为空。");
        BusinessObjectRef root=null;
        for(BusinessObjectRef ref:context.getObjects()) {
            EsignValues.require(ref!=null && "BD_MATERIAL_PROD".equals(ref.getObjectType()) && ref.getObjectPk()!=null,
                "生产保存包含其他类型的档案。");
            if(ref.getParentClientObjectId()==null) { EsignValues.require(root==null,"生产保存只能选择一个主对象。");root=ref; }
        }
        EsignValues.require(root!=null,"生产保存主对象缺失。");
        for(BusinessObjectRef ref:context.getObjects()) if(ref!=root) EsignValues.require(root.getClientObjectId().equals(ref.getParentClientObjectId()),
            "生产保存级联关系不正确。");
        return root;
    }
    private static MaterialProdImpact impact(MaterialProdVO value) {
        return new MaterialProdImpact(value.getPk_org(),value.getPk_marcostclass(),value.getClassfeature().booleanValue());
    }
}
