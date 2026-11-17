package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import nc.bs.bd.cache.CacheProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.bd.baseservice.BDUniqueFieldTrimUtil;
import nc.bs.dao.BaseDAO;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.audit.WmsArchiveSnapshots;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.bd.stordoc.IStordocService;
import nc.itf.uapbd.qd.esign.IWarehouseSaveAdapter;
import nc.md.persist.framework.MDPersistenceService;
import nc.vo.bd.address.AddressVO;
import nc.vo.bd.stordoc.AgentstoreVO;
import nc.vo.bd.stordoc.StordocVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.VOStatus;
import nc.vo.pub.SuperVO;
import nc.vo.pub.CircularlyAccessibleValueObject;
import nc.vo.util.BDPKLockUtil;
import nc.vo.util.BDAuditInfoUtil;
import nc.vo.util.bizlock.BizlockDataUtil;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.WarehouseSaveCommand;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Candidate for a native warehouse save, including every agent-store row. */
public final class WarehouseSaveAdapter implements IWarehouseSaveAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { draft(context,command); }
    /** Take the native lock order before owner rows are locked by the policy framework. */
    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        StordocVO value=draft(context,command);
        new BDUniqueFieldTrimUtil("5489bda0-d876-4db2-9568-02954f240cc8").trimUniqueFields(new SuperVO[]{value});
        if("UPDATE".equals(context.getOperationType())) BDPKLockUtil.lockSuperVO(new SuperVO[]{value});
        BizlockDataUtil.lockDataByBizlock(new CircularlyAccessibleValueObject[]{value});
    }
    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        StordocVO value=draft(context,command);BusinessObjectRef ref=context.getObjects().get(0);
        boolean insert="INSERT".equals(context.getOperationType());String pk=value.getPrimaryKey();
        ServerIdentity identity=ServerIdentity.current();
        EsignValues.require(context.getGroupId().equals(identity.groupId),"仓库集团已变化。");
        if(!insert) EsignPermission.requireArchiveData(identity,"stordoc","edit",context.getObjects());
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"仓库保存缺少受控事务上下文。");
        List<SnapshotRow> oldChildren=new ArrayList<SnapshotRow>();
        if(!insert) {
            List<SnapshotRow> before=reader.readLocked("bd_stordoc","pk_stordoc",null,"pk_stordoc",pk,"pk_group",context.getGroupId(),true);
            EsignValues.require(before.size()==1 && ref.getPolicyOwnerOrg().equals(before.get(0).getValues().get("pk_org")),"仓库不存在或所属组织已变化。");
            oldChildren=children(reader,pk,true);
            if(scope.isEnabledFor(ref.getClientObjectId())) {
                before.addAll(oldChildren);
                before.addAll(WmsArchiveSnapshots.read(reader,ref.getObjectType(),pk,true));
                scope.before(ref.getClientObjectId(),before);
            }
        }
        validateChildren(reader,value.getAgentstores(),pk,oldChildren);
        String copiedAddress=copyAddress(context,(WarehouseSaveCommand)command,value,identity,reader);
        IStordocService service=NCLocator.getInstance().lookup(IStordocService.class);
        StordocVO saved=insert?service.insertStordoc(value):service.updateStordoc(value);
        String desired=ref.getDesiredOwnerOrg()==null?ref.getPolicyOwnerOrg():ref.getDesiredOwnerOrg();
        EsignValues.require(saved!=null && saved.getPrimaryKey()!=null && (insert || pk.equals(saved.getPrimaryKey()))
            && context.getGroupId().equals(saved.getPk_group()) && desired!=null && desired.equals(saved.getPk_org())
            && saved.getCode()!=null && !saved.getCode().trim().isEmpty(),"原生仓库保存返回了其他档案或组织。");
        List<SnapshotRow> after=reader.read("bd_stordoc","pk_stordoc",null,"pk_stordoc",saved.getPrimaryKey(),"pk_group",context.getGroupId(),true);
        EsignValues.require(after.size()==1 && desired.equals(after.get(0).getValues().get("pk_org"))
            && saved.getCode().equals(after.get(0).getValues().get("code")),"仓库落库结果与授权不一致，业务必须回滚。");
        if(copiedAddress!=null) {
            EsignValues.require(copiedAddress.equals(saved.getStoraddr()) && copiedAddress.equals(after.get(0).getValues().get("storaddr")),
                "复制的地址未被新仓库引用，业务必须回滚。");
            List<SnapshotRow> addresses=reader.readByPk("bd_address","pk_address",copiedAddress);
            EsignValues.require(addresses.size()==1,"无法核实复制的仓库地址。");
            SnapshotRow address=addresses.get(0);
            after.add(new SnapshotRow(address.getEntity(),address.getRowId(),saved.getPrimaryKey(),
                address.getValues(),address.getTypes(),address.getLabels(),true));
        }
        if(scope.isEnabledFor(ref.getClientObjectId())) {
            after.addAll(children(reader,saved.getPrimaryKey(),false));
            after.addAll(WmsArchiveSnapshots.read(reader,ref.getObjectType(),saved.getPrimaryKey(),false));
            scope.after(ref.getClientObjectId(),after);
        }
        return saved;
    }

    private static String copyAddress(OperationContext context,WarehouseSaveCommand command,StordocVO value,
            ServerIdentity identity,PhysicalSnapshotReader reader) throws BusinessException {
        String sourcePk=command.getCopySourceWarehouse(),sourceAddress=command.getCopySourceAddress();
        if(sourcePk==null) return null;
        EsignPermission.requireBusiness(identity,context.getNodeCode(),"Copy",context.getOrgId());
        BusinessObjectRef sourceRef=new BusinessObjectRef("warehouse-copy-source","BD_STORDOC",sourcePk,null,null);
        EsignPermission.requireArchiveData(identity,"stordoc","query",Collections.singletonList(sourceRef));
        List<SnapshotRow> source=reader.readLocked("bd_stordoc","pk_stordoc",null,"pk_stordoc",sourcePk,
            "pk_group",context.getGroupId(),true);
        EsignValues.require(source.size()==1 && context.getOrgId().equals(source.get(0).getValues().get("pk_org"))
            && Objects.equals(sourceAddress,source.get(0).getValues().get("storaddr")),
            "复制来源的仓库或地址已变化，请刷新后重新复制。");
        if(sourceAddress==null || sourceAddress.trim().isEmpty()) return null;
        // The native copy preserves free-text addresses and clones an existing physical address.
        List<SnapshotRow> address=reader.readLocked("bd_address","pk_address",null,"pk_address",sourceAddress,true);
        if(address.isEmpty()) return null;
        AddressVO old=(AddressVO)new BaseDAO().retrieveByPK(AddressVO.class,sourceAddress);
        EsignValues.require(old!=null && sourceAddress.equals(old.getPk_address()),"无法加载复制来源的仓库地址。");
        AddressVO copy=(AddressVO)old.clone();
        copy.setPk_address(null);
        copy.setStatus(VOStatus.NEW);
        BDAuditInfoUtil.addData(new SuperVO[]{copy});
        // Use the native persistence and cache contract within the enclosing warehouse CMT.
        String pk=MDPersistenceService.lookupPersistenceService().saveBill(copy);
        EsignValues.require(pk!=null && !sourceAddress.equals(pk),"仓库复制未生成新的地址。");
        CacheProxy.fireDataInserted(copy.getTableName());
        value.setStoraddr(pk);
        if(sourceAddress.equals(value.getPk_address())) value.setPk_address(pk);
        return pk;
    }
    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        EsignValues.require(result instanceof StordocVO && context.getObjects().size()==1,"仓库保存结果不完整。");
        StordocVO saved=(StordocVO)result;BusinessObjectRef ref=context.getObjects().get(0);
        EsignValues.require(saved.getPrimaryKey()!=null && ("INSERT".equals(context.getOperationType()) || saved.getPrimaryKey().equals(ref.getObjectPk())),"仓库保存返回了其他档案。");
        return Collections.singletonList(new BusinessObjectRef(ref.getClientObjectId(),ref.getObjectType(),saved.getPrimaryKey(),saved.getCode(),ref.getParentClientObjectId()));
    }
    private static StordocVO draft(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && "10140WARH".equals(context.getNodeCode()) && "Save".equals(context.getButtonCode())
            && ("INSERT".equals(context.getOperationType()) || "UPDATE".equals(context.getOperationType()))
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null
            && context.getOrgId()!=null && context.getObjects().size()==1 && command instanceof WarehouseSaveCommand,"仓库保存载荷或节点不正确。");
        BusinessObjectRef ref=context.getObjects().get(0);StordocVO value=((WarehouseSaveCommand)command).getValue();
        EsignValues.require(value!=null && value.getClass()==StordocVO.class && "BD_STORDOC".equals(ref.getObjectType())
            && context.getGroupId().equals(value.getPk_group()) && value.getPk_org()!=null && ref.getParentClientObjectId()==null
            && (ref.getDesiredOwnerOrg()!=null?ref.getDesiredOwnerOrg().equals(value.getPk_org()):value.getPk_org().equals(ref.getPolicyOwnerOrg())),
            "仓库数据与授权集团或所属组织不一致。");
        if("INSERT".equals(context.getOperationType())) EsignValues.require(ref.getObjectPk()==null && value.getPrimaryKey()==null
            && context.getOrgId().equals(value.getPk_org()),"新增仓库所属组织与操作组织不一致。");
        else EsignValues.require(ref.getPolicyOwnerOrg()!=null && ref.getObjectPk()!=null && ref.getObjectPk().equals(value.getPrimaryKey()),
            "仓库保存与授权档案不一致。");
        WarehouseSaveCommand input=(WarehouseSaveCommand)command;
        String source=input.getCopySourceWarehouse(),address=input.getCopySourceAddress();
        EsignValues.require(source==null?address==null:"INSERT".equals(context.getOperationType())
            && !source.trim().isEmpty() && source.length()<=20 && Objects.equals(address,value.getStoraddr()),
            "仓库复制来源与保存内容不一致。");
        return value;
    }
    private static List<SnapshotRow> children(PhysicalSnapshotReader reader,String pk,boolean lock) throws BusinessException {
        return lock?reader.readLocked("bd_agentstore","pk_agentstore","pk_stordoc","pk_stordoc",pk,true)
            :reader.read("bd_agentstore","pk_agentstore","pk_stordoc","pk_stordoc",pk,true);
    }
    private static void validateChildren(final PhysicalSnapshotReader reader,AgentstoreVO[] rows,String parent,List<SnapshotRow> previous)
            throws BusinessException {
        if(rows==null) return;
        Set<String> owned=new HashSet<String>();
        for(SnapshotRow row:previous) owned.add(row.getRowId());
        MaterialOrgInfoOperationAdapter.ChildLookup lookup=new MaterialOrgInfoOperationAdapter.ChildLookup() {
            public boolean exists(String pk) throws BusinessException {
                return !reader.readLocked("bd_agentstore","pk_agentstore","pk_stordoc","pk_agentstore",pk,false).isEmpty();
            }
        };
        validateChildPayload(rows,parent,owned,lookup);
    }
    static void validateChildPayload(AgentstoreVO[] rows,String parent,Set<String> owned,
            MaterialOrgInfoOperationAdapter.ChildLookup lookup) throws BusinessException {
        if(rows==null) return;
        Set<String> seen=new HashSet<String>();
        for(AgentstoreVO row:rows) {
            EsignValues.require(row!=null && row.getClass()==AgentstoreVO.class,"仓库委托子行类型不正确。");
            String childId=row.getPk_agentstore();
            if(parent==null) {
                EsignValues.require(row.getStatus()==VOStatus.NEW && row.getPk_stordoc()==null,"新增仓库委托子行不正确。");
                if(childId!=null) EsignValues.require(seen.add(childId) && !lookup.exists(childId),"新增仓库委托子行主键重复。");
            } else MaterialOrgInfoOperationAdapter.validateChildRow(parent,row.getPk_stordoc(),childId,row.getStatus(),owned,seen,lookup);
        }
    }
}
