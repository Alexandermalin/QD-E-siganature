package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.NativeBatchChangeSet;
import nc.bs.uapbd.qd.esign.core.NativeBatchRequestScope;
import nc.bs.uapbd.qd.esign.core.NativeBatchUpdateOutcome;
import nc.bs.uapbd.qd.esign.core.PartnerBatchPayload;
import nc.bs.uapbd.qd.esign.core.PartnerBatchRoute;
import nc.bs.uapbd.qd.esign.core.PartnerStateSnapshots;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.IPartnerBatchAdapter;
import nc.md.persist.framework.MDPersistenceService;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.material.MaterialVO;
import nc.vo.bd.errorlog.ErrLogReturnValue;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.ArchiveBatchUpdateResult;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;

/** Candidate for one actual NC quick-batch main-tab commit. Not registered as an accepted site action. */
public final class PartnerBatchAdapter implements IPartnerBatchAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { checked(context,command); }

    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        PartnerBatchDraft draft=checked(context,command);String domain=PartnerBatchRoute.domain(context.getNodeCode());
        ServerIdentity identity=ServerIdentity.current();
        EsignPermission.requireArchiveData(identity,PartnerBatchRoute.permissionResource(context.getNodeCode()),"batchupdate",context.getObjects());
        PhysicalSnapshotReader physical=new PhysicalSnapshotReader();
        if(draft.getTabId()!=null) {
            List<SnapshotRow> tab=physical.readLocked("bd_batchupdatetab","pk_batchupdatetab",null,"pk_batchupdatetab",draft.getTabId(),false);
            EsignValues.require(tab.size()==1,"原生快速批改页签已变化。");
            physical.readLocked("bd_batchupdateattr","pk_batchupdateattr","pk_batchupdatetab","pk_batchupdatetab",draft.getTabId(),false);
        }
        NativeBatchChangeSet changes=PartnerBatchPayload.resolve(domain,draft);
        NativeBatchRequestScope.verify(context.getGroupId(),context.getOrgId(),domain,context.getObjects(),changes);
        List<BusinessObjectRef> ordered=ordered(context.getObjects());String[] pks=new String[ordered.size()];
        for(int index=0;index<ordered.size();index++) pks[index]=ordered.get(index).getObjectPk();
        BDPKLockUtil.lockString(pks);
        List<MaterialVO> materialVersions=new ArrayList<MaterialVO>();
        for(BusinessObjectRef ref:ordered) {
            SuperVO current=load(context,ref,domain);
            snapshot(physical,context,ref,current,true);
            if(current instanceof MaterialVO) materialVersions.add((MaterialVO)current);
            counterpart(physical,context,ref,true);
        }
        if(!materialVersions.isEmpty()) MaterialBatchSnapshots.requireDistinctLatestSources(materialVersions);
        NativeBatchRequestScope.verify(context.getGroupId(),context.getOrgId(),domain,context.getObjects(),
            PartnerBatchPayload.resolve(domain,draft));
    }

    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        PartnerBatchDraft draft=checked(context,command);String domain=PartnerBatchRoute.domain(context.getNodeCode());
        NativeBatchChangeSet changes=PartnerBatchPayload.resolve(domain,draft);
        NativeBatchRequestScope.verify(context.getGroupId(),context.getOrgId(),domain,context.getObjects(),changes);
        AuditCaptureScope scope=AuditCaptureScope.current();EsignValues.require(scope!=null,"快速批改缺少受控事务上下文。");
        PhysicalSnapshotReader physical=new PhysicalSnapshotReader();
        Map<String,List<SnapshotRow>> before=new LinkedHashMap<String,List<SnapshotRow>>(),after=new LinkedHashMap<String,List<SnapshotRow>>();
        Map<String,List<SnapshotRow>> oppositeBefore=new HashMap<String,List<SnapshotRow>>();
        List<SuperVO> oldRows=new ArrayList<SuperVO>();String[] pks=new String[context.getObjects().size()];int index=0;
        for(BusinessObjectRef ref:context.getObjects()) {
            pks[index++]=ref.getObjectPk();SuperVO current=load(context,ref,domain);oldRows.add(current);
            List<SnapshotRow> rows=snapshot(physical,context,ref,current,true);before.put(ref.getClientObjectId(),rows);
            oppositeBefore.put(ref.getClientObjectId(),counterpart(physical,context,ref,true));
            if(scope.isEnabledFor(ref.getClientObjectId())) scope.before(ref.getClientObjectId(),rows);
        }
        Set<String> unchanged=changes.unchanged(oldRows);final ServerIdentity identity=ServerIdentity.current();
        List<String> allowed=EsignPermission.authorizedOrgs(identity,context.getNodeCode(),context.getButtonCode(),false);
        EsignValues.require(allowed!=null && !allowed.isEmpty(),"无法确定原生快速批改组织权限。");
        String[] permissionOrgs=allowed.toArray(new String[allowed.size()]);ErrLogReturnValue nativeResult;Map<String,String> excluded;
        if("BD_CUSTOMER".equals(domain)) {
            TrackedCustomerBatchUpdateService nativeService=new TrackedCustomerBatchUpdateService();
            nativeResult=nativeService.batchUpdateByPksForNewTrans(changes.getSelectedFields(),changes.copyValues(),permissionOrgs,new String[0],pks,true);
            excluded=nativeService.excluded();
        } else if("BD_SUPPLIER".equals(domain)) {
            TrackedSupplierBatchUpdateService nativeService=new TrackedSupplierBatchUpdateService();
            nativeResult=nativeService.batchUpdateByPksForNewTrans(changes.getSelectedFields(),changes.copyValues(),permissionOrgs,new String[0],pks,true);
            excluded=nativeService.excluded();
        } else {
            final String operationOrg=context.getOrgId();
            TrackedMaterialBatchUpdateService nativeService=new TrackedMaterialBatchUpdateService();
            nativeResult=nativeService.batchUpdateWithClassificationCheck(changes.getSelectedFields(),changes.copyValues(),
                permissionOrgs,pks,new TrackedMaterialBatchUpdateService.ClassificationCheck() {
                    public void require(Object value) throws BusinessException {
                        EsignPermission.requireMaterialBatchClassification(identity,operationOrg,value);
                    }
                });
            excluded=nativeService.excluded();
        }
        List<SuperVO> currentRows=new ArrayList<SuperVO>();
        for(BusinessObjectRef ref:context.getObjects()) {
            SuperVO current=load(context,ref,domain);currentRows.add(current);
            after.put(ref.getClientObjectId(),snapshot(physical,context,ref,current,false));
            EsignValues.require(PartnerStateSnapshots.sameRows(oppositeBefore.get(ref.getClientObjectId()),counterpart(physical,context,ref,false)),
                "快速批改影响了尚未授权的关联档案，业务必须回滚。");
        }
        NativeBatchRequestScope.verify(context.getGroupId(),context.getOrgId(),domain,context.getObjects(),
            PartnerBatchPayload.resolve(domain,draft));
        List<NativeBatchUpdateOutcome.Unit> classified=NativeBatchUpdateOutcome.reconcile(context.getObjects(),unchanged,excluded,
            "BD_CUSTOMER".equals(domain)?CustomerVO.class:"BD_SUPPLIER".equals(domain)?SupplierVO.class:MaterialVO.class,nativeResult,before,after);
        Set<String> persistedValues=changes.unchanged(currentRows);List<ArchiveBatchResult.Unit> units=new ArrayList<ArchiveBatchResult.Unit>();
        Map<String,SuperVO> priorByPk=new HashMap<String,SuperVO>();
        for(SuperVO row:oldRows) EsignValues.require(priorByPk.put(row.getPrimaryKey(),row)==null,"快速批改执行前档案重复。");
        String table="BD_CUSTOMER".equals(domain)?"bd_customer":"BD_SUPPLIER".equals(domain)?"bd_supplier":"bd_material";
        for(NativeBatchUpdateOutcome.Unit unit:classified) {
            if("SUCCESS".equals(unit.getState())) {
                EsignValues.require(persistedValues.contains(unit.getPk()),"快速批改写入值与已授权值不一致，业务必须回滚。");
                changes.requirePhysicalChanges(priorByPk.get(unit.getPk()),table,unit.getPk(),before.get(unit.getClientId()),
                    after.get(unit.getClientId()));
                if(scope.isEnabledFor(unit.getClientId())) scope.after(unit.getClientId(),after.get(unit.getClientId()));
            }
            units.add(new ArchiveBatchResult.Unit(unit.getClientId(),unit.getPk(),unit.getState()));
        }
        return new ArchiveBatchUpdateResult(nativeResult,units);
    }

    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        EsignValues.require(result instanceof ArchiveBatchUpdateResult,"快速批改结果不完整。");return context.getObjects();
    }
    private static PartnerBatchDraft checked(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && PartnerBatchRoute.matches(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null
            && context.getOrgId()!=null && command!=null && command.getClass()==PartnerBatchDraft.class && !context.getObjects().isEmpty(),
            "基础档案快速批改载荷不正确。");
        String domain=PartnerBatchRoute.domain(context.getNodeCode());Set<String> pks=new HashSet<String>();
        for(BusinessObjectRef ref:context.getObjects()) EsignValues.require(ref!=null && domain.equals(ref.getObjectType())
            && ref.getObjectPk()!=null && pks.add(ref.getObjectPk()) && ref.getPolicyOwnerOrg()!=null && ref.getBillCode()!=null
            && ref.getDesiredOwnerOrg()==null && ref.getParentClientObjectId()==null && context.getOrgId().equals(ref.getTargetOrg()),
            "快速批改所选档案或所属组织不正确。");
        return (PartnerBatchDraft)command;
    }
    private static List<BusinessObjectRef> ordered(List<BusinessObjectRef> rows) {
        List<BusinessObjectRef> result=new ArrayList<BusinessObjectRef>(rows);
        Collections.sort(result,new Comparator<BusinessObjectRef>() { public int compare(BusinessObjectRef a,BusinessObjectRef b) {
            return a.getObjectPk().compareTo(b.getObjectPk());
        }});return result;
    }
    private static SuperVO load(OperationContext context,BusinessObjectRef ref,String domain) throws BusinessException {
        Class<?> type="BD_CUSTOMER".equals(domain)?CustomerVO.class:"BD_SUPPLIER".equals(domain)?SupplierVO.class:MaterialVO.class;
        Object value=MDPersistenceService.lookupPersistenceQueryService().queryBillOfVOByPK(type,ref.getObjectPk(),false);
        EsignValues.require(value!=null && value.getClass()==type,"快速批改档案不存在或类型已变化。");SuperVO row=(SuperVO)value;
        EsignValues.require(ref.getObjectPk().equals(row.getPrimaryKey()) && context.getGroupId().equals(row.getAttributeValue("pk_group"))
            && ref.getPolicyOwnerOrg().equals(row.getAttributeValue("pk_org")) && ref.getBillCode().equals(row.getAttributeValue("code")),
            "快速批改档案所属组织或编码已变化。");return row;
    }
    private static List<SnapshotRow> snapshot(PhysicalSnapshotReader reader,OperationContext context,BusinessObjectRef ref,
            SuperVO value,boolean lock) throws BusinessException {
        if("BD_MATERIAL".equals(ref.getObjectType()))
            return MaterialBatchSnapshots.read(reader,context.getGroupId(),ref,(MaterialVO)value,lock);
        return PartnerStateAdapter.snapshot(reader,context,ref,lock);
    }
    private static List<SnapshotRow> counterpart(PhysicalSnapshotReader reader,OperationContext context,BusinessObjectRef ref,boolean lock)
            throws BusinessException {
        if("BD_MATERIAL".equals(ref.getObjectType())) return Collections.emptyList();
        boolean customer="BD_CUSTOMER".equals(ref.getObjectType());String table=customer?"bd_supplier":"bd_customer",key=customer?"pk_supplier":"pk_customer";
        List<SnapshotRow> rows=lock?reader.readLocked(table,key,null,key,ref.getObjectPk(),false):reader.read(table,key,null,key,ref.getObjectPk(),false);
        EsignValues.require(rows.size()<=1,"关联客户或供应商主键不唯一。");
        if(!rows.isEmpty()) EsignValues.require(context.getGroupId().equals(rows.get(0).getValues().get("pk_group")),"关联档案跨集团。");
        return rows;
    }
}
