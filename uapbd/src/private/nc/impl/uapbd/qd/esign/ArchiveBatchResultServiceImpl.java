package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.ArchiveBatchReceipt;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPage;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchReceipt;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchTargetScope;
import nc.bs.uapbd.qd.esign.core.NativeOrgBatchPreparedScope;
import nc.bs.uapbd.qd.esign.core.PartnerBatchRoute;
import nc.bs.uapbd.qd.esign.persistence.NativeOrgBatchReceiptReader;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.IArchiveBatchResultService;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveBatchResult;
import nc.vo.uapbd.qd.esign.ArchiveBatchStatus;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Read the caller's batch result without claiming or re-executing any business request. */
public final class ArchiveBatchResultServiceImpl implements IArchiveBatchResultService {
    public ArchiveBatchStatus getResult(String id) throws BusinessException {
        QdEsignStore store=new QdEsignStore();ServerIdentity identity=ServerIdentity.current();
        Map<String,Object> request=SignatureServiceImpl.owned(store,id,identity,false);
        String node=EsignValues.text(request,"node_code"),button=EsignValues.text(request,"button_code"),
            operation=EsignValues.text(request,"operation_type");
        EsignValues.require(ArchiveBatchResult.isArchiveBatch(node,button,operation)
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(request.get("policy_mode")),"请求不是可查询的档案批量操作。");
        EsignPermission.requireBusiness(identity,node,button,EsignValues.text(request,"pk_org"));
        List<Map<String,Object>> objects=store.loadArchiveBatchResultObjects(request);
        return authorizedStatus(identity,request,objects);
    }
    /** The caller has already checked request ownership and read the authoritative object set. */
    static ArchiveBatchStatus authorizedStatus(ServerIdentity identity,Map<String,Object> request,List<Map<String,Object>> objects)
            throws BusinessException {
        EsignValues.require(identity!=null && request!=null,"档案批量结果身份或请求缺失。");
        String id=EsignValues.text(request,"request_id"),node=EsignValues.text(request,"node_code"),
            button=EsignValues.text(request,"button_code"),operation=EsignValues.text(request,"operation_type"),
            state=EsignValues.text(request,"business_state");
        EsignValues.require(id!=null && ArchiveBatchResult.isArchiveBatch(node,button,operation)
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(request.get("policy_mode")),"请求不是可查询的档案批量操作。");
        EsignPermission.requireBusiness(identity,node,button,EsignValues.text(request,"pk_org"));
        if(ArchiveBatchResult.isPartnerBatchUpdate(node,button,operation) && hasOrganizationMarker(objects)) {
            NativeOrgBatchReceiptReader.Result details=new NativeOrgBatchReceiptReader().readAvailable(request,objects);
            final ServerIdentity subject=identity;final String function=node,action=button,org=EsignValues.text(request,"pk_org");
            return buildOrganizationStatus(id,state,request,objects,details.getTarget(),details.getSelection(),details.getEvidence(),new OrganizationAccess() {
                public void require(List<BusinessObjectRef> refs,NativeOrgBatchPage page,List<String> targets) throws BusinessException {
                    EsignPermission.requireArchiveData(subject,PartnerBatchRoute.permissionResource(function),"batchupdate",refs);
                    EsignPermission.requirePageOrganizations(subject,function,action,page.tabCode,org,targets);
                }
            });
        }
        if(ArchiveBatchResult.isPartnerBatchUpdate(node,button,operation))
            EsignPermission.requireArchiveData(identity,PartnerBatchRoute.permissionResource(node),"batchupdate",ObjectRelations.restore(objects));
        if(ArchiveBatchResult.isSupplierFreeze(node,button,operation)) {
            java.util.Set<String> targets=EsignPermission.authorizedButtonTargets(identity,node,button,EsignValues.text(request,"pk_org"));
            for(Map<String,Object> row:objects) EsignValues.require(targets.contains(EsignValues.text(row,"target_org")),
                "没有目标组织的供应商冻结结果查询权限。");
        }
        return buildStatus(id,state,request,objects);
    }
    static ArchiveBatchStatus buildStatus(String id,String state,Map<String,Object> request,List<Map<String,Object>> objects)
            throws BusinessException {
        return buildStatus(id,state,request,objects,null,null,null);
    }
    interface OrganizationAccess {
        void require(List<BusinessObjectRef> refs,NativeOrgBatchPage page,List<String> targets) throws BusinessException;
    }
    static boolean hasOrganizationMarker(List<Map<String,Object>> objects) {
        if(objects!=null)for(Map<String,Object> row:objects) {
            String client=row==null?null:EsignValues.text(row,"client_object_id");
            if(client!=null && client.startsWith("orgbulk"))return true;
        }
        return false;
    }
    static ArchiveBatchStatus buildOrganizationStatus(String id,String state,Map<String,Object> request,List<Map<String,Object>> objects,
            NativeOrgBatchTargetScope.Evidence target,NativeOrgBatchReceipt.Evidence evidence,OrganizationAccess access)
            throws BusinessException {
        return buildOrganizationStatus(id,state,request,objects,target,null,evidence,access);
    }
    static ArchiveBatchStatus buildOrganizationStatus(String id,String state,Map<String,Object> request,List<Map<String,Object>> objects,
            NativeOrgBatchTargetScope.Evidence target,NativeOrgBatchPreparedScope.Evidence selection,
            NativeOrgBatchReceipt.Evidence evidence,OrganizationAccess access) throws BusinessException {
        EsignValues.require(request!=null && id!=null && id.equals(EsignValues.text(request,"request_id"))
            && state!=null && state.equals(EsignValues.text(request,"business_state")) && target!=null && access!=null
            && hasOrganizationMarker(objects),"组织批改结果请求或权限检查不完整。");
        String group=EsignValues.text(request,"pk_group"),org=EsignValues.text(request,"pk_org"),node=EsignValues.text(request,"node_code");
        EsignValues.require(ArchiveBatchResult.isPartnerBatchUpdate(node,EsignValues.text(request,"button_code"),
            EsignValues.text(request,"operation_type")) && target.getPage().domain.equals(PartnerBatchRoute.domain(node)),
            "组织批改结果节点与页签不一致。");
        List<BusinessObjectRef> refs=ObjectRelations.restore(objects);
        NativeOrgBatchTargetScope.Evidence verified=NativeOrgBatchTargetScope.restore(id,group,org,target.getPage(),refs,
            target.getTargets(),target.getDigest());
        NativeOrgBatchPreparedScope.Evidence checked=selection==null?null:
            NativeOrgBatchPreparedScope.restore(id,group,org,verified,refs,selection.getPages(),selection.getDigest());
        if(checked!=null && evidence!=null)NativeOrgBatchPreparedScope.requireOutcome(id,group,org,refs,checked,evidence);
        ArchiveBatchStatus status=buildStatus(id,state,request,objects,verified,checked,evidence);
        access.require(refs,verified.getPage(),verified.getTargets());
        return status;
    }
    private static ArchiveBatchStatus buildStatus(String id,String state,Map<String,Object> request,List<Map<String,Object>> objects,
            NativeOrgBatchTargetScope.Evidence targetScope,NativeOrgBatchPreparedScope.Evidence selected,
            NativeOrgBatchReceipt.Evidence pageEvidence) throws BusinessException {
        EsignValues.require(id!=null && state!=null && request!=null && objects!=null && !objects.isEmpty()
            && objects.size()==EsignValues.number(request,"owner_object_count"),"档案批量结果对象不完整。");
        List<ArchiveBatchStatus.Unit> units=new ArrayList<ArchiveBatchStatus.Unit>();
        List<ArchiveBatchResult.Unit> verify=new ArrayList<ArchiveBatchResult.Unit>();
        java.util.Set<String> clientIds=new java.util.HashSet<String>(),objectIds=new java.util.HashSet<String>(),objectPks=new java.util.HashSet<String>();
        String node=EsignValues.text(request,"node_code"),button=EsignValues.text(request,"button_code"),
            operation=EsignValues.text(request,"operation_type"),group=EsignValues.text(request,"pk_group"),
            org=EsignValues.text(request,"pk_org");
        EsignValues.require(group!=null && org!=null && ArchiveBatchResult.isArchiveBatch(node,button,operation),
            "档案批量结果缺少有效节点、动作或组织。");
        boolean quickBatch=ArchiveBatchResult.isPartnerBatchUpdate(node,button,operation);
        boolean supplierTargets=ArchiveBatchResult.isSupplierFreeze(node,button,operation);
        boolean supplier=supplierTargets || (("10140SUG".equals(node) || "10140SUO".equals(node))
            && (ArchiveBatchResult.isPartnerState(node,button,operation) || quickBatch));
        boolean material=ArchiveBatchResult.isMaterialState(node,button,operation)
            || (quickBatch && ("10140MAG".equals(node) || "10140MAO".equals(node)));
        boolean warehouse=ArchiveBatchResult.isWarehouseState(node,button,operation);
        String supplierPk=null;
        boolean allPending=true;
        for(Map<String,Object> object:objects) {
            String client=EsignValues.text(object,"client_object_id"),pk=EsignValues.text(object,"object_pk"),unitState=EsignValues.text(object,"unit_state"),
                objectId=EsignValues.text(object,"object_id"),target=EsignValues.text(object,"target_org");
            if(supplierTargets && supplierPk==null) supplierPk=pk;
            EsignValues.require(client!=null && clientIds.add(client) && pk!=null && target!=null
                && objectPks.add(supplierTargets?pk+"\u0000"+target:pk) && (!supplierTargets || pk.equals(supplierPk)) && objectId!=null && objectIds.add(objectId)
                && id.equals(EsignValues.text(object,"request_id")) && (warehouse?"BD_STORDOC":material?"BD_MATERIAL":supplier?"BD_SUPPLIER":"BD_CUSTOMER").equals(EsignValues.text(object,"object_type"))
                && group.equals(EsignValues.text(object,"pk_group")) && org.equals(EsignValues.text(object,"pk_org"))
                && (!quickBatch || org.equals(target))
                && EsignValues.text(object,"policy_owner_org")!=null && EsignValues.text(object,"target_org")!=null
                && ("PENDING".equals(unitState) || "SUCCESS".equals(unitState)
                || "FAILED".equals(unitState) || "UNCHANGED".equals(unitState) || "UNKNOWN".equals(unitState)
                || (targetScope!=null && pageEvidence!=null && "PARTIAL".equals(unitState))),"档案批量结果状态不完整。");
            units.add(new ArchiveBatchStatus.Unit(client,pk,EsignValues.text(object,"bill_code"),EsignValues.text(object,"policy_owner_org"),
                EsignValues.text(object,"target_org"),unitState,EsignValues.text(object,"unit_error_code")));
            verify.add(new ArchiveBatchResult.Unit(client,pk,unitState));
            if(!"PENDING".equals(unitState)) allPending=false;
        }
        if(targetScope!=null && pageEvidence!=null) {
            ArchiveBatchReceipt receipt=ArchiveBatchReceipt.verifyOrganization(id,group,org,targetScope.getPage(),
                ObjectRelations.restore(objects),pageEvidence);
            EsignValues.require(state.equals(receipt.getState()) && targetScope.getTargets().equals(pageEvidence.getTargets()),
                "组织批改批次、目标与页签回执不一致。");
            for(ArchiveBatchResult.Unit unit:verify) EsignValues.require(receipt.get(unit.getClientId())!=null
                && unit.getState().equals(receipt.get(unit.getClientId()).getState()),"组织批改主档状态与页签回执不一致。");
        } else if(targetScope!=null) {
            boolean allUnknownOrPending=true,allRolledBack=true;
            for(Map<String,Object> object:objects) {
                String value=EsignValues.text(object,"unit_state");
                allUnknownOrPending&="UNKNOWN".equals(value) || "PENDING".equals(value);
                allRolledBack&="FAILED".equals(value) && "BATCH_ROLLED_BACK".equals(EsignValues.text(object,"unit_error_code"));
            }
            boolean valid=(("PENDING".equals(state) || "READY".equals(state) || "RUNNING".equals(state)
                    || "CANCELLED".equals(state) || "EXPIRED".equals(state)) && allPending)
                || ("UNKNOWN".equals(state) && allUnknownOrPending)
                || ("FAILED".equals(state) && (allPending || (allRolledBack
                    && "BUSINESS_EXECUTION_FAILED".equals(EsignValues.text(request,"error_code")))));
            EsignValues.require(valid,"组织批改缺少对应页签回执，不能推断业务结果。");
        } else if("SUCCESS".equals(state) || "PARTIAL".equals(state) || ("FAILED".equals(state) && !allPending)) {
            ArchiveBatchReceipt receipt=ArchiveBatchReceipt.verify(ObjectRelations.restore(objects),new ArchiveBatchResult(null,verify));
            EsignValues.require(state.equals(receipt.getState()),"档案批次与逐对象结果不一致。");
        }
        if(targetScope==null)return new ArchiveBatchStatus(id,state,units);
        List<ArchiveBatchStatus.PageHeader> headers=new ArrayList<ArchiveBatchStatus.PageHeader>();
        if(pageEvidence!=null)for(NativeOrgBatchReceipt.Header header:pageEvidence.getHeaders())
            headers.add(new ArchiveBatchStatus.PageHeader(header.getClientId(),header.getPageCount()));
        List<ArchiveBatchStatus.Page> pages=new ArrayList<ArchiveBatchStatus.Page>();
        if(pageEvidence!=null)for(NativeOrgBatchReceipt.Page page:pageEvidence.getPages())
            pages.add(new ArchiveBatchStatus.Page(page.getClientId(),page.getPk(),page.getTarget(),page.getState(),page.getFailureStage()));
        return new ArchiveBatchStatus(id,state,units,new ArchiveBatchStatus.Organization(targetScope.getPage().tabCode,
            targetScope.getTargets(),pageEvidence!=null,headers,pages,selected==null?null:selected.getPages()));
    }
}
