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
import nc.itf.bd.stordoc.IStordocService;
import nc.itf.uapbd.qd.esign.IWarehouseStateAdapter;
import nc.vo.bd.stordoc.StordocVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerStateCommand;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;

/** Candidate warehouse enable/disable preserving NC's batch service and transaction. */
public final class WarehouseStateAdapter implements IWarehouseStateAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { rows(context,command); }
    static void prelock(OperationContext context,Serializable command) throws BusinessException { BDPKLockUtil.lockSuperVO(rows(context,command)); }
    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        StordocVO[] values=rows(context,command);boolean enable="ENABLE".equals(context.getOperationType());
        AuditCaptureScope scope=AuditCaptureScope.current();EsignValues.require(scope!=null,"仓库启停缺少受控事务上下文。");
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();Map<String,List<SnapshotRow>> prior=new LinkedHashMap<String,List<SnapshotRow>>();
        Set<String> already=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) {
            List<SnapshotRow> before=snapshot(reader,context,ref,true);prior.put(ref.getClientObjectId(),before);
            if(PartnerStateSnapshots.alreadyTarget(before,"bd_stordoc",ref.getObjectPk(),enable)) already.add(ref.getObjectPk());
            if(scope.isEnabledFor(ref.getClientObjectId())) scope.before(ref.getClientObjectId(),before);
        }
        IStordocService service=NCLocator.getInstance().lookup(IStordocService.class);
        ValueObjWithErrLog nativeResult=enable?service.enableStordoc(values):service.disableStordoc(values);
        List<NativeBatchStateOutcome.Unit> classified=NativeBatchStateOutcome.reconcile(context.getObjects(),already,nativeResult);
        List<ArchiveBatchResult.Unit> units=new ArrayList<ArchiveBatchResult.Unit>();
        for(NativeBatchStateOutcome.Unit unit:classified) {
            BusinessObjectRef ref=find(context,unit.getClientId());List<SnapshotRow> after=snapshot(reader,context,ref,false);
            List<SnapshotRow> before=prior.get(ref.getClientObjectId());
            if("SUCCESS".equals(unit.getState())) {
                PartnerStateSnapshots.verifySuccess(before,after,"bd_stordoc",ref.getObjectPk(),null,enable);
                if(scope.isEnabledFor(ref.getClientObjectId())) scope.after(ref.getClientObjectId(),after);
            } else EsignValues.require(PartnerStateSnapshots.sameRows(before,after),"原生未成功仓库或其子行发生变化，业务必须回滚。");
            units.add(new ArchiveBatchResult.Unit(unit.getClientId(),unit.getPk(),unit.getState()));
        }
        return new ArchiveBatchResult(nativeResult,units);
    }
    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        EsignValues.require(result instanceof ArchiveBatchResult,"仓库启停结果不完整。");return context.getObjects();
    }
    private static List<SnapshotRow> snapshot(PhysicalSnapshotReader reader,OperationContext context,BusinessObjectRef ref,boolean lock)
            throws BusinessException {
        String pk=ref.getObjectPk();
        List<SnapshotRow> root=lock?reader.readLocked("bd_stordoc","pk_stordoc",null,"pk_stordoc",pk,"pk_group",context.getGroupId(),true)
            :reader.read("bd_stordoc","pk_stordoc",null,"pk_stordoc",pk,"pk_group",context.getGroupId(),true);
        EsignValues.require(root.size()==1 && ref.getPolicyOwnerOrg().equals(root.get(0).getValues().get("pk_org"))
            && ref.getBillCode()!=null && ref.getBillCode().equals(root.get(0).getValues().get("code")),"仓库所属组织或编码已变化。");
        List<SnapshotRow> result=new ArrayList<SnapshotRow>(root);
        result.addAll(lock?reader.readLocked("bd_agentstore","pk_agentstore","pk_stordoc","pk_stordoc",pk,true)
            :reader.read("bd_agentstore","pk_agentstore","pk_stordoc","pk_stordoc",pk,true));
        return result;
    }
    private static BusinessObjectRef find(OperationContext context,String id) throws BusinessException {
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getClientObjectId().equals(id)) return ref;
        throw new BusinessException("仓库启停结果包含未授权对象。");
    }
    static StordocVO[] rows(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && ArchiveBatchResult.isWarehouseState(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null
            && context.getOrgId()!=null && !context.getObjects().isEmpty() && command instanceof PartnerStateCommand,"仓库启停载荷或节点不正确。");
        Map<String,SuperVO> rows=((PartnerStateCommand)command).getRows();
        EsignValues.require(rows.size()==context.getObjects().size(),"仓库启停数据与授权范围不一致。");
        List<StordocVO> values=new ArrayList<StordocVO>();Set<String> pks=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) {
            SuperVO raw=rows.get(ref.getClientObjectId());StordocVO value=raw!=null && raw.getClass()==StordocVO.class?(StordocVO)raw:null;
            EsignValues.require("BD_STORDOC".equals(ref.getObjectType()) && value!=null && ref.getObjectPk()!=null
                && ref.getObjectPk().equals(value.getPk_stordoc()) && pks.add(ref.getObjectPk()) && context.getGroupId().equals(value.getPk_group())
                && ref.getPolicyOwnerOrg()!=null && ref.getPolicyOwnerOrg().equals(value.getPk_org()) && ref.getDesiredOwnerOrg()==null
                && ref.getParentClientObjectId()==null && context.getOrgId().equals(ref.getTargetOrg()),"仓库启停对象、所属组织或操作组织不匹配。");
            values.add(value);
        }
        return values.toArray(new StordocVO[values.size()]);
    }
}
