package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.bd.service.ValueObjWithErrLog;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeBatchStateOutcome;
import nc.bs.uapbd.qd.esign.core.PartnerStateSnapshots;
import nc.itf.bd.material.baseinfo.IMaterialBaseInfoService;
import nc.itf.uapbd.qd.esign.IMaterialStateAdapter;
import nc.vo.bd.config.BDModeSelectedVO;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerStateCommand;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDModeManager;
import nc.vo.util.BDPKLockUtil;

/** Candidate material enable/disable using the native one-call batch transaction. */
public final class MaterialStateAdapter implements IMaterialStateAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { rows(context,command); }

    static void requireManageMode(String node) throws BusinessException {
        if (!"10140MAO".equals(node)) return;
        BDModeSelectedVO mode=BDModeManager.getInstance().getBDModeSelectedVOByMDClassID("c7dc0ccd-8872-4eee-8882-160e8f49dfad");
        EsignValues.require(mode!=null && mode.getManagemode()!=null,"无法确定物料的原生管理模式。");
        int value=mode.getManagemode().intValue();
        EsignValues.require(value!=1 && value!=2 && value!=5,"当前物料管理模式不允许在业务单元启用或停用。");
    }

    static void prelock(OperationContext context,Serializable command) throws BusinessException { BDPKLockUtil.lockSuperVO(rows(context,command)); }

    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        MaterialVO[] values=rows(context,command);boolean enable="ENABLE".equals(context.getOperationType());
        AuditCaptureScope scope=AuditCaptureScope.current();EsignValues.require(scope!=null,"物料启停缺少受控事务上下文。");
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();Map<String,List<SnapshotRow>> prior=new LinkedHashMap<String,List<SnapshotRow>>();
        Set<String> already=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) {
            List<SnapshotRow> before=snapshot(reader,context,ref,true);prior.put(ref.getClientObjectId(),before);
            if(PartnerStateSnapshots.alreadyTarget(before,"bd_material",ref.getObjectPk(),enable)) already.add(ref.getObjectPk());
            if(scope.isEnabledFor(ref.getClientObjectId())) scope.before(ref.getClientObjectId(),before);
        }
        IMaterialBaseInfoService service=NCLocator.getInstance().lookup(IMaterialBaseInfoService.class);
        ValueObjWithErrLog nativeResult=enable?service.enableMaterial(values):service.disableMaterial(values);
        List<NativeBatchStateOutcome.Unit> classified=NativeBatchStateOutcome.reconcile(context.getObjects(),already,nativeResult);
        List<ArchiveBatchResult.Unit> units=new ArrayList<ArchiveBatchResult.Unit>();
        for(NativeBatchStateOutcome.Unit unit:classified) {
            BusinessObjectRef ref=find(context,unit.getClientId());List<SnapshotRow> after=snapshot(reader,context,ref,false);
            List<SnapshotRow> before=prior.get(ref.getClientObjectId());
            if("SUCCESS".equals(unit.getState())) {
                PartnerStateSnapshots.verifySuccess(before,after,"bd_material",ref.getObjectPk(),null,enable);
                if(scope.isEnabledFor(ref.getClientObjectId())) scope.after(ref.getClientObjectId(),after);
            } else EsignValues.require(PartnerStateSnapshots.sameRows(before,after),"原生未成功物料或其异步日志发生变化，业务必须回滚。");
            units.add(new ArchiveBatchResult.Unit(unit.getClientId(),unit.getPk(),unit.getState()));
        }
        return new ArchiveBatchResult(nativeResult,units);
    }

    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        EsignValues.require(result instanceof ArchiveBatchResult,"物料启停结果不完整。");return context.getObjects();
    }

    private static List<SnapshotRow> snapshot(PhysicalSnapshotReader reader,OperationContext context,BusinessObjectRef ref,boolean lock)
            throws BusinessException {
        String pk=ref.getObjectPk();
        List<SnapshotRow> root=lock?reader.readLocked("bd_material","pk_material",null,"pk_material",pk,"pk_group",context.getGroupId(),true)
            :reader.read("bd_material","pk_material",null,"pk_material",pk,"pk_group",context.getGroupId(),true);
        EsignValues.require(root.size()==1 && ref.getPolicyOwnerOrg().equals(root.get(0).getValues().get("pk_org"))
            && ref.getBillCode()!=null && ref.getBillCode().equals(root.get(0).getValues().get("code")),"物料所属组织或编码已变化。");
        List<SnapshotRow> result=new ArrayList<SnapshotRow>(root);
        result.addAll(children(reader,"bd_materialconvert","pk_materialconvert",pk,true,lock));
        result.addAll(children(reader,"bd_materialtaxtype","pk_materialtaxtype",pk,true,lock));
        result.addAll(lock?reader.readLocked("bd_ansylog","pk_anydelte","pk_basdoc","pk_basdoc",pk,false)
            :reader.read("bd_ansylog","pk_anydelte","pk_basdoc","pk_basdoc",pk,false));
        return result;
    }
    private static List<SnapshotRow> children(PhysicalSnapshotReader reader,String table,String key,String pk,boolean active,boolean lock)
            throws BusinessException {
        return lock?reader.readLocked(table,key,"pk_material","pk_material",pk,active)
            :reader.read(table,key,"pk_material","pk_material",pk,active);
    }
    private static BusinessObjectRef find(OperationContext context,String id) throws BusinessException {
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getClientObjectId().equals(id)) return ref;
        throw new BusinessException("物料启停结果包含未授权对象。");
    }
    static MaterialVO[] rows(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && ArchiveBatchResult.isMaterialState(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null
            && context.getOrgId()!=null && !context.getObjects().isEmpty() && command instanceof PartnerStateCommand,"物料启停载荷或节点不正确。");
        Map<String,SuperVO> rows=((PartnerStateCommand)command).getRows();
        EsignValues.require(rows.size()==context.getObjects().size(),"物料启停数据与授权范围不一致。");
        List<MaterialVO> values=new ArrayList<MaterialVO>();Set<String> pks=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) {
            SuperVO raw=rows.get(ref.getClientObjectId());MaterialVO value=raw!=null && raw.getClass()==MaterialVO.class?(MaterialVO)raw:null;
            EsignValues.require("BD_MATERIAL".equals(ref.getObjectType()) && value!=null && ref.getObjectPk()!=null
                && ref.getObjectPk().equals(value.getPk_material()) && pks.add(ref.getObjectPk()) && context.getGroupId().equals(value.getPk_group())
                && ref.getPolicyOwnerOrg()!=null && ref.getPolicyOwnerOrg().equals(value.getPk_org()) && ref.getDesiredOwnerOrg()==null
                && ref.getParentClientObjectId()==null && context.getOrgId().equals(ref.getTargetOrg()),"物料启停对象、所属组织或操作组织不匹配。");
            values.add(value);
        }
        return values.toArray(new MaterialVO[values.size()]);
    }
}
