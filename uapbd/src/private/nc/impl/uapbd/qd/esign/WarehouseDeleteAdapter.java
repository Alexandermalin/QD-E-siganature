package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.bd.stordoc.IStordocService;
import nc.itf.uapbd.qd.esign.IWarehouseDeleteAdapter;
import nc.vo.bd.stordoc.StordocVO;
import nc.vo.bd.stordoc.AgentstoreVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.pub.VOStatus;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.WarehouseDeleteCommand;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;

/** Candidate for one native warehouse delete commit; batch dispatch is not wired yet. */
public final class WarehouseDeleteAdapter implements IWarehouseDeleteAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { draft(context,command); }
    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        BDPKLockUtil.lockSuperVO(new SuperVO[]{draft(context,command)});
    }
    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        StordocVO value=draft(context,command);BusinessObjectRef ref=context.getObjects().get(0);
        ServerIdentity identity=ServerIdentity.current();
        EsignValues.require(context.getGroupId().equals(identity.groupId),"仓库集团已变化。");
        EsignPermission.requireArchiveData(identity,"stordoc","delete",context.getObjects());
        AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"仓库删除缺少受控事务上下文。");
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();String pk=ref.getObjectPk();
        List<SnapshotRow> before=reader.readLocked("bd_stordoc","pk_stordoc",null,"pk_stordoc",pk,
            "pk_group",context.getGroupId(),true);
        EsignValues.require(before.size()==1 && ref.getPolicyOwnerOrg().equals(before.get(0).getValues().get("pk_org")),
            "仓库不存在或所属组织已变化。");
        List<SnapshotRow> children=reader.readLocked("bd_agentstore","pk_agentstore","pk_stordoc","pk_stordoc",pk,true);
        Set<String> owned=new HashSet<String>();for(SnapshotRow child:children) owned.add(child.getRowId());
        validateChildren(value.getAgentstores(),pk,owned);
        if(scope.isEnabledFor(ref.getClientObjectId())) { before.addAll(children);scope.before(ref.getClientObjectId(),before); }
        NCLocator.getInstance().lookup(IStordocService.class).deleteStordoc(value);
        EsignValues.require(reader.read("bd_stordoc","pk_stordoc",null,"pk_stordoc",pk,"pk_group",context.getGroupId(),true).isEmpty()
            && reader.read("bd_agentstore","pk_agentstore","pk_stordoc","pk_stordoc",pk,true).isEmpty(),
            "原生仓库删除未清除全部有效数据行，业务必须回滚。");
        return value;
    }
    static void validateChildren(AgentstoreVO[] rows,String parent,Set<String> owned) throws BusinessException {
        if(rows==null) return;
        Set<String> seen=new HashSet<String>();
        for(AgentstoreVO child:rows) EsignValues.require(child!=null && child.getClass()==AgentstoreVO.class
            && child.getPk_agentstore()!=null && owned.contains(child.getPk_agentstore()) && seen.add(child.getPk_agentstore())
            && (child.getPk_stordoc()==null || parent.equals(child.getPk_stordoc()))
            && (child.getStatus()==VOStatus.UNCHANGED || child.getStatus()==VOStatus.DELETED),"仓库删除包含其他仓库或无效的委托子行。");
    }
    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        BusinessObjectRef ref=context.getObjects().get(0);
        EsignValues.require(result instanceof StordocVO && ref.getObjectPk().equals(((StordocVO)result).getPk_stordoc()),
            "原生仓库删除结果与授权不一致。");
        return Collections.singletonList(ref);
    }
    private static StordocVO draft(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && "10140WARH".equals(context.getNodeCode()) && "Delete".equals(context.getButtonCode())
            && "DELETE".equals(context.getOperationType()) && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            && context.getGroupId()!=null && context.getOrgId()!=null && context.getObjects().size()==1
            && command instanceof WarehouseDeleteCommand,"仓库删除载荷或节点不正确。");
        BusinessObjectRef ref=context.getObjects().get(0);StordocVO value=((WarehouseDeleteCommand)command).getValue();
        EsignValues.require(value!=null && value.getClass()==StordocVO.class && "BD_STORDOC".equals(ref.getObjectType())
            && ref.getObjectPk()!=null && ref.getObjectPk().equals(value.getPk_stordoc())
            && context.getGroupId().equals(value.getPk_group()) && ref.getPolicyOwnerOrg()!=null
            && ref.getPolicyOwnerOrg().equals(value.getPk_org()) && ref.getDesiredOwnerOrg()==null
            && ref.getParentClientObjectId()==null,"仓库删除数据与授权档案或所属组织不一致。");
        return value;
    }
}
