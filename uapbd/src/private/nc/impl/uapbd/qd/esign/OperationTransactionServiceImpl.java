package nc.impl.uapbd.qd.esign;
import java.io.Serializable;
import java.util.*;
import javax.transaction.TransactionSynchronizationRegistry;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.InventoryPhysicalSnapshots;
import nc.bs.uapbd.qd.esign.audit.SapplyBpAudit;
import nc.bs.uapbd.qd.esign.audit.SapplyLineSnapshots;
import nc.bs.uapbd.qd.esign.audit.AuditRecorder;
import nc.bs.uapbd.qd.esign.audit.OrderPhysicalSnapshots;
import nc.bs.uapbd.qd.esign.audit.SalesOtherNodeSnapshots;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.RequestRules;
import nc.bs.uapbd.qd.esign.core.MappingRules;
import nc.bs.uapbd.qd.esign.core.InventoryTransactionRoute;
import nc.bs.uapbd.qd.esign.core.NativeTransactionBoundaryProbe;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.bs.uapbd.qd.esign.core.TransactionRollback;
import nc.bs.uapbd.qd.esign.core.ArchiveTreeScope;
import nc.bs.uapbd.qd.esign.core.ArchiveBatchReceipt;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.EsignPermission;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.uapbd.qd.esign.IOperationTransactionService;
import nc.itf.uapbd.qd.esign.spi.BusinessOperationAdapter;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.*;

/** 服务端本地 CMT 业务网关，执行时重新核对签名、策略、权限与适配器白名单；异常向外传播，由容器回滚业务和审计写入。 */
public final class OperationTransactionServiceImpl implements IOperationTransactionService {
    /** ADAPTERS：允许由业务网关定位调用的固定本地服务接口白名单。 */
    private static final Set<String> ADAPTERS=Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        "nc.itf.so.qd.esign.ISaleOrderOperationAdapter","nc.itf.so.qd.esign.ISaleOrderPfSaveAdapter",
        "nc.itf.uapbd.qd.esign.IMaterialOperationAdapter",
        "nc.itf.uapbd.qd.esign.IMaterialOrgInfoOperationAdapter",
        "nc.itf.uapbd.qd.esign.IMaterialProdSaveAdapter",
        "nc.itf.uapbd.qd.esign.IMaterialMainDeleteAdapter",
        "nc.itf.uapbd.qd.esign.ICustomerFreezeAdapter",
        "nc.itf.uapbd.qd.esign.ISupplierFreezeAdapter",
        "nc.itf.uapbd.qd.esign.IPartnerStateAdapter",
        "nc.itf.uapbd.qd.esign.IPartnerMainSaveAdapter",
        "nc.itf.uapbd.qd.esign.IPartnerBatchAdapter",
        "nc.itf.uapbd.qd.esign.IMaterialStateAdapter",
        "nc.itf.uapbd.qd.esign.IWarehouseStateAdapter",
        "nc.itf.uapbd.qd.esign.IBankAccountStateAdapter",
        "nc.itf.uapbd.qd.esign.IBankAccountSaveAdapter",
        "nc.itf.uapbd.qd.esign.IBankAccountDeleteAdapter",
        "nc.itf.uapbd.qd.esign.IArchiveTreeDisableAdapter",
        "nc.itf.uapbd.qd.esign.IArchiveTreeEnableAdapter",
        "nc.itf.uapbd.qd.esign.IArchiveTreeSaveAdapter",
        "nc.itf.uapbd.qd.esign.IArchiveTreeDeleteAdapter",
        "nc.itf.uapbd.qd.esign.IPartnerOrgInfoOperationAdapter",
        "nc.itf.uapbd.qd.esign.IWarehouseSaveAdapter",
        "nc.itf.pu.qd.esign.IPurchaseOrderOperationAdapter","nc.itf.ic.qd.esign.IInventoryOperationAdapter",
        "nc.itf.ic.qd.esign.IInventoryPfSaveAdapter","nc.itf.ic.qd.esign.IInventoryPfStateAdapter",
        "nc.itf.ic.qd.esign.ISapplyLineOperationAdapter",
        "nc.itf.purp.qd.esign.ISupplierPriceOperationAdapter",
        "nc.itf.so.qd.esign.ISaleCloseOperationAdapter",
        "nc.itf.so.qd.esign.IDeliveryOperationAdapter",
        "nc.itf.so.qd.esign.ISaleInvoiceOperationAdapter")));
    /** 锁定并认领可执行且未过期的请求，保存一次性执行令牌并转为 RUNNING；已成功请求返回空令牌供结果重入。 */
    public String claim_RequiresNew(String id) throws BusinessException {
        QdEsignStore store=new QdEsignStore();Map<String,Object> r=SignatureServiceImpl.ownedForExecution(store,id,ServerIdentity.current());
        requireOrdinaryRoute(r);
        EsignValues.require(!MaterialPageDeleteResult.isRoute(EsignValues.text(r,"node_code"),EsignValues.text(r,"button_code"),
            EsignValues.text(r,"operation_type")), "档案页签删除须使用逐行执行服务。");
        if("SUCCESS".equals(r.get("business_state")))return null;
        EsignValues.require(!QdEsignStore.isArchivedResult(r),"Historical request cannot be claimed");
        EsignValues.require(RequestRules.mayExecute(EsignValues.text(r,"signature_state"),EsignValues.text(r,"business_state")),"Request is already consumed or is not ready");
        EsignValues.require(System.currentTimeMillis()<=EsignValues.parseUtc(EsignValues.text(r,"expires_at")),"Request expired; sign again");
        if(ArchiveBatchResult.isPartnerBatchUpdate(EsignValues.text(r,"node_code"),EsignValues.text(r,"button_code"),
                EsignValues.text(r,"operation_type")))
            EsignValues.require(!store.hasOrganizationBatchMarker(id),"组织页签批改的原生执行与回执链尚未开放。");
        String token=EsignValues.id();store.updateRequest(id,EsignValues.row("business_state","RUNNING","execution_token",token));return token;
    }
    static void requireOrdinaryRoute(Map<String,Object> request) throws BusinessException {
        EsignValues.require(!SapplyWorkflowBatchUnit.POLICY_MODE.equals(request.get("policy_mode")),
            "出库申请批量请求须使用原生批量执行服务。");
        String node=EsignValues.text(request,"node_code"),button=EsignValues.text(request,"button_code");
        String operation=EsignValues.text(request,"operation_type");
        EsignValues.require(!MaterialAssignmentResult.isRoute(node,button,operation)
            && !MaterialCancelAssignmentResult.isRoute(node,button,operation)
            && !CustomerAssignmentResult.isRoute(node,button,operation)
            && !SupplierAssignmentResult.isRoute(node,button,operation),"分配请求须使用逐目标执行服务。");
    }
    /** Runs only after the business service returned, in a new transaction and using committed evidence. */
    public void verifyCommitted_RequiresNew(String id,String token,OperationResponse attempted) throws BusinessException {
        TransactionSynchronizationRegistry transaction=NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object key=transaction.getTransactionKey();QdEsignStore store=new QdEsignStore();ServerIdentity identity=ServerIdentity.current();
        Map<String,Object> request=SignatureServiceImpl.owned(store,id,identity,false);
        String state=EsignValues.text(request,"business_state"),node=EsignValues.text(request,"node_code"),
            button=EsignValues.text(request,"button_code"),operation=EsignValues.text(request,"operation_type");
        boolean batch=ArchiveBatchResult.isArchiveBatch(node,button,operation);
        EsignValues.require(request.get("finished_at")!=null && ("SUCCESS".equals(state)
            || (batch && ("PARTIAL".equals(state) || "FAILED".equals(state)))),"业务提交结果尚未确认，请按请求号核对。");
        EsignPermission.requireBusiness(identity,node,button,EsignValues.text(request,"pk_org"));
        List<Map<String,Object>> objects=batch?store.loadArchiveBatchResultObjects(request):store.loadResultObjects(request);
        ArchiveBatchStatus batchStatus=null;
        if(batch) batchStatus=ArchiveBatchResultServiceImpl.authorizedStatus(identity,request,objects);
        OperationCommitVerification.verify(id,token,attempted,request,objects,batchStatus);
        EsignValues.require(key.equals(transaction.getTransactionKey()) && !transaction.getRollbackOnly(),"业务结果核对事务不可用。");
    }
    /** 核对内部令牌，单独登记仍处于 RUNNING 的失败或待核对结果；回滚标记本身不证明回滚完成。 */
    public void failClaimed_RequiresNew(String id,String token,String code) throws BusinessException {
        QdEsignStore store=new QdEsignStore();Map<String,Object> r=SignatureServiceImpl.owned(store,id,ServerIdentity.current(),true);
        EsignValues.require(token!=null&&token.equals(r.get("execution_token")),"Invalid internal execution token");
        if("RUNNING".equals(r.get("business_state"))){
            boolean uncertain="BUSINESS_OUTCOME_UNKNOWN".equals(code);
            if(ArchiveBatchResult.isArchiveBatch(EsignValues.text(r,"node_code"),EsignValues.text(r,"button_code"),EsignValues.text(r,"operation_type"))) {
                for(Map<String,Object> object:store.loadObjects(id)) if("PENDING".equals(object.get("unit_state")))
                    store.finishAssignmentUnit(id,EsignValues.text(object,"object_id"),uncertain?"UNKNOWN":"FAILED",
                        uncertain?"BATCH_OUTCOME_UNKNOWN":"BATCH_ROLLED_BACK");
            }
            Map<String,Object> result=EsignValues.row("business_state",uncertain?"UNKNOWN":"FAILED","error_code",code);
            if(!uncertain)result.put("finished_at",EsignValues.utc());
            store.updateRequest(id,result);
        }
    }
    /** 已成功请求重入时核验权限并返回持久化对象；新执行需有效令牌、权限和版本匹配，快照不完整使整个业务事务回滚。 */
    public OperationResponse executeClaimed_RequiresNew(final String id,final String token,final Serializable command) throws BusinessException {
        final QdEsignStore store=new QdEsignStore();final ServerIdentity i=ServerIdentity.current();final Map<String,Object> r=SignatureServiceImpl.ownedForExecution(store,id,i);
        EsignValues.require(!SapplyWorkflowBatchUnit.POLICY_MODE.equals(r.get("policy_mode")),
            "出库申请批量请求须使用原生批量执行服务。");
        // 成功请求重入只恢复已提交对象并重验权限，不重建原业务返回值，不重复调用原生服务。
        if("SUCCESS".equals(r.get("business_state"))){
            boolean batch=ArchiveBatchResult.isArchiveBatch(EsignValues.text(r,"node_code"),EsignValues.text(r,"button_code"),EsignValues.text(r,"operation_type"));
            List<Map<String,Object>> rows=batch?store.loadArchiveBatchResultObjects(r):store.loadResultObjects(r);
            EsignPermission.requireBusiness(i,EsignValues.text(r,"node_code"),EsignValues.text(r,"button_code"),EsignValues.text(r,"pk_org"));
            if(batch) {
                ArchiveBatchStatus checked=ArchiveBatchResultServiceImpl.authorizedStatus(i,r,rows);
                EsignValues.require(checked!=null && "SUCCESS".equals(checked.getState())
                    && (!ArchiveBatchResult.isPartnerBatchUpdate(EsignValues.text(r,"node_code"),EsignValues.text(r,"button_code"),
                        EsignValues.text(r,"operation_type"))
                        || ArchiveBatchResultServiceImpl.hasOrganizationMarker(rows)==(checked.getOrganization()!=null)),
                    "档案批量恢复结果或权限不完整。");
            }
            List<BusinessObjectRef> committed=ObjectRelations.restore(rows);
            return new OperationResponse(id,true,null,committed);
        }
        final boolean ownerMode=OperationContext.OWNER_POLICY_PROTOCOL.equals(EsignValues.text(r,"policy_mode"));
        boolean inventory=InventoryTransactionRoute.matches(EsignValues.text(r,"node_code"),
            EsignValues.text(r,"button_code"),EsignValues.text(r,"operation_type"));
        if(!ownerMode && !inventory)return executeClaimed(store,i,r,id,token,command,false);
        TransactionSynchronizationRegistry transaction=NativeTransactionBoundaryProbe.requireActiveTransaction();
        Object transactionKey=transaction.getTransactionKey();
        try{
            return executeGuarded(transaction,transactionKey,ownerMode,inventory,new GuardedOperation() {
                public OperationResponse execute() throws BusinessException {
                    return executeClaimed(store,i,r,id,token,command,ownerMode);
                }
            });
        }
        finally{if(ownerMode)SupplierFreezeAdapter.clearPrelock();}
    }

    interface GuardedOperation { OperationResponse execute() throws BusinessException; }

    /** The local NC manager can commit enlisted sources separately, including in inventory requests. */
    static OperationResponse executeGuarded(TransactionSynchronizationRegistry transaction,Object transactionKey,
            boolean ownerMode,boolean inventory,GuardedOperation operation) throws BusinessException {
        try {
            OperationResponse result=operation.execute();
            EsignValues.require(transactionKey.equals(transaction.getTransactionKey())&&!transaction.getRollbackOnly(),
                "受控业务事务已变化或已标记回滚。");
            if(ownerMode || inventory)NativeTransactionBoundaryProbe.requireSingleDataSource(transaction,transactionKey);
            return result;
        } catch(BusinessException failure){throw TransactionRollback.abort(transaction,transactionKey,failure);}
        catch(RuntimeException failure){throw TransactionRollback.abort(transaction,transactionKey,failure);}
    }

    private OperationResponse executeClaimed(QdEsignStore store,ServerIdentity i,Map<String,Object> r,
            String id,String token,Serializable command,boolean ownerMode) throws BusinessException {
        EsignValues.require(!QdEsignStore.isArchivedResult(r),"Historical request cannot execute business");
        // 内部令牌必须与已提交认领匹配，客户端不能仅凭请求号跳过认领。
        EsignValues.require("RUNNING".equals(r.get("business_state"))&&token!=null&&token.equals(r.get("execution_token")),"Missing or consumed internal execution claim");
        EsignValues.require(System.currentTimeMillis()<=EsignValues.parseUtc(EsignValues.text(r,"expires_at")),"Request expired; sign again");
        String org=EsignValues.text(r,"pk_org"),node=EsignValues.text(r,"node_code"),button=EsignValues.text(r,"button_code");
        Map<String,Object> mapping=store.loadNodeByAction(node,button);
        List<Map<String,Object>> objects=store.loadObjects(id);List<BusinessObjectRef> refs=ObjectRelations.restore(objects);
        nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope.Prepared organizationBatch=null;
        String service=mapping==null?null:EsignValues.text(mapping,"adapter_class");
        if(ownerMode && ("nc.itf.uapbd.qd.esign.IArchiveTreeDisableAdapter".equals(service)
                || "nc.itf.uapbd.qd.esign.IArchiveTreeEnableAdapter".equals(service))) {
            EsignValues.require(EsignValues.yes(mapping,"validated") && ADAPTERS.contains(service),"分类操作入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            BusinessObjectRef root=null;
            for(BusinessObjectRef ref:refs) if(ref.getParentClientObjectId()==null) {
                EsignValues.require(root==null,"树形档案操作只能选择一个根对象。");root=ref;
            }
            EsignValues.require(root!=null && root.getObjectPk()!=null && ArchiveTreeScope.supports(root.getObjectType()),"无法确定树形档案加锁范围。");
            String treeAction="nc.itf.uapbd.qd.esign.IArchiveTreeDisableAdapter".equals(service)?"disable":"enable";
            EsignValues.require(treeAction.equalsIgnoreCase(EsignValues.text(r,"operation_type")),"树形档案启停动作不正确。");
            EsignPermission.requireArchiveData(i,ArchiveTreeScope.permissionResource(root.getObjectType()),treeAction,refs);
            // Protect physical inserts as well as taking NC's cooperative branch/warehouse business lock.
            ArchiveTreeMutationLock.acquire(root.getObjectType(),false);
            ArchiveTreeScope.lockBranch(i.groupId,root.getObjectType(),root.getObjectPk());
        }
        if(ownerMode && "nc.itf.uapbd.qd.esign.IWarehouseSaveAdapter".equals(service)) {
            EsignValues.require(EsignValues.yes(mapping,"validated") && ADAPTERS.contains(service),"仓库业务入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            if("UPDATE".equals(EsignValues.text(r,"operation_type"))) EsignPermission.requireArchiveData(i,"stordoc","edit",refs);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            WarehouseSaveAdapter.prelock(prelockContext,command);
        }
        if(ownerMode && "nc.itf.uapbd.qd.esign.IMaterialMainDeleteAdapter".equals(service)) {
            EsignValues.require(EsignValues.yes(mapping,"validated") && ADAPTERS.contains(service),"物料删除入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            EsignPermission.requireArchiveData(i,"material","delete",refs);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            MaterialMainDeleteAdapter.prelock(prelockContext,command);
        }
        if(ownerMode && "nc.itf.uapbd.qd.esign.IMaterialProdSaveAdapter".equals(service)) {
            EsignValues.require(EsignValues.yes(mapping,"validated") && ADAPTERS.contains(service),"生产信息保存入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            MaterialProdSaveAdapter.prelock(prelockContext,command);
        }
        if(ownerMode && "nc.itf.uapbd.qd.esign.ICustomerFreezeAdapter".equals(service)) {
            EsignValues.require(EsignValues.yes(mapping,"validated") && ADAPTERS.contains(service),"客户冻结入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            CustomerFreezeAdapter.prelock(prelockContext,command);
        }
        if(ownerMode && "nc.itf.uapbd.qd.esign.ISupplierFreezeAdapter".equals(service)) {
            EsignValues.require(EsignValues.yes(mapping,"validated") && ADAPTERS.contains(service),"供应商冻结入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            SupplierFreezeAdapter.prelock(prelockContext,command);
        }
        if(ownerMode && "nc.itf.uapbd.qd.esign.IPartnerStateAdapter".equals(service)) {
            EsignValues.require(EsignValues.yes(mapping,"validated") && ADAPTERS.contains(service)
                && ArchiveBatchResult.isPartnerState(node,button,EsignValues.text(r,"operation_type")),"档案启停入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            boolean customer="10140CUG".equals(node) || "10140CUO".equals(node);
            EsignPermission.requireArchiveData(i,customer?"customer":"supplier",EsignValues.text(r,"operation_type").toLowerCase(java.util.Locale.ENGLISH),refs);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            PartnerStateAdapter.prelock(prelockContext,command);
        }
        if(ownerMode && "nc.itf.uapbd.qd.esign.IMaterialStateAdapter".equals(service)) {
            EsignValues.require(EsignValues.yes(mapping,"validated") && ADAPTERS.contains(service)
                && ArchiveBatchResult.isMaterialState(node,button,EsignValues.text(r,"operation_type")),"物料启停入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            EsignPermission.requireArchiveData(i,"material",EsignValues.text(r,"operation_type").toLowerCase(java.util.Locale.ENGLISH),refs);
            MaterialStateAdapter.requireManageMode(node);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            MaterialStateAdapter.prelock(prelockContext,command);
        }
        if(ownerMode && "nc.itf.uapbd.qd.esign.IWarehouseStateAdapter".equals(service)) {
            EsignValues.require(EsignValues.yes(mapping,"validated") && ADAPTERS.contains(service)
                && ArchiveBatchResult.isWarehouseState(node,button,EsignValues.text(r,"operation_type")),"仓库启停入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            EsignPermission.requireArchiveData(i,"stordoc",EsignValues.text(r,"operation_type").toLowerCase(java.util.Locale.ENGLISH),refs);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            WarehouseStateAdapter.prelock(prelockContext,command);
        }
        if(ownerMode && nc.bs.uapbd.qd.esign.core.BankAccountRoute.isAdapter(mapping)) {
            EsignValues.require(ADAPTERS.contains(service),"银行账户入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            if(nc.bs.uapbd.qd.esign.core.BankAccountRoute.SAVE_ADAPTER.equals(service)) BankAccountSaveAdapter.prelock(prelockContext,command);
            else if(nc.bs.uapbd.qd.esign.core.BankAccountRoute.DELETE_ADAPTER.equals(service)) BankAccountDeleteAdapter.prelock(prelockContext,command);
            else BankAccountStateAdapter.prelock(prelockContext,command);
        }
        if(ownerMode && nc.bs.uapbd.qd.esign.core.PartnerBatchRoute.isAdapter(mapping)) {
            nc.bs.uapbd.qd.esign.core.PartnerBatchRoute.requireMapping(mapping,EsignValues.text(r,"operation_type"));
            EsignValues.require(ADAPTERS.contains(service),"快速批改入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            if(nc.bs.uapbd.qd.esign.core.PartnerOrgBatchRouteGuard.isOrganization(refs,command))
                organizationBatch=nc.bs.uapbd.qd.esign.core.PartnerOrgBatchScope.verify(i,prelockContext,(PartnerBatchDraft)command,true);
            else PartnerBatchAdapter.prelock(prelockContext,command);
        }
        if(ownerMode && nc.bs.uapbd.qd.esign.core.PartnerMainSaveRoute.isAdapter(mapping)) {
            EsignValues.require(ADAPTERS.contains(service),"客户或供应商保存入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            PartnerMainSaveAdapter.prelock(prelockContext,command);
        }
        if(ownerMode && "nc.itf.uapbd.qd.esign.IArchiveTreeSaveAdapter".equals(service)) {
            EsignValues.require(EsignValues.yes(mapping,"validated") && ADAPTERS.contains(service),"树形档案保存入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            ArchiveTreeSaveAdapter.prelock(prelockContext,command);
        }
        if(ownerMode && "nc.itf.uapbd.qd.esign.IArchiveTreeDeleteAdapter".equals(service)) {
            EsignValues.require(EsignValues.yes(mapping,"validated") && ADAPTERS.contains(service),"树形档案删除入口尚未启用。");
            EsignPermission.requireBusiness(i,node,button,org);
            MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
            OperationContext prelockContext=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,
                EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),
                EsignValues.text(r,"reason"),refs,OperationContext.OWNER_POLICY_PROTOCOL);
            ArchiveTreeDeleteAdapter.prelock(prelockContext,command);
        }
        PolicyDecision p;
        if(ownerMode){p=PolicyServiceImpl.validateOwnerRequest(store,i,r,mapping,objects,true);}
        else{EsignPermission.requireBusiness(i,node,button,org);p=PolicyServiceImpl.resolveFor(i,org,node,button);
            EsignValues.require(p.isControlled()&&p.getVersion()==EsignValues.number(r,"policy_version")&&p.getControlVersion()==EsignValues.number(r,"control_version"),"Policy changed; start a new operation");}
        EsignValues.require(p.isValidated(),"Policy changed; start a new operation");
        EsignValues.require(!p.isReasonRequired()||(EsignValues.text(r,"reason")!=null&&EsignValues.text(r,"reason").trim().length()>0),"Operation reason is required");
        EsignValues.require(mapping!=null&&EsignValues.yes(mapping,"validated")&&ADAPTERS.contains(service),"No verified server adapter");
        MappingRules.requireDomain(mapping,EsignValues.text(r,"operation_type"),refs);
        OperationContext c=new OperationContext(id,EsignValues.text(r,"batch_id"),i.groupId,org,node,EsignValues.text(r,"node_name"),button,EsignValues.text(r,"button_name"),EsignValues.text(r,"operation_type"),EsignValues.text(r,"reason"),refs,ownerMode?OperationContext.OWNER_POLICY_PROTOCOL:null);
        Object located=NCLocator.getInstance().lookup(service);EsignValues.require(located instanceof BusinessOperationAdapter,"Invalid adapter service contract");
        // 仅定位固定白名单中的本地适配服务，执行前检查类型化载荷，不允许任意类名调用。
        BusinessOperationAdapter adapter=(BusinessOperationAdapter)located;adapter.validate(c,command);
        // 作用域仅包住本次同线程同步执行；原生事件监听器必须在该范围内采集权威快照。
        Set<String> auditIds=new HashSet<String>();
        Set<String> bankRepresentatives=ownerMode && nc.bs.uapbd.qd.esign.core.BankAccountRoute.isAdapter(mapping)
            ?nc.bs.uapbd.qd.esign.core.BankAccountAuditObjects.representatives(objects):null;
        if(ownerMode)for(Map<String,Object> object:objects)if(EsignValues.yes(object,"owner_audit_enabled")
            && (bankRepresentatives==null || bankRepresentatives.contains(EsignValues.text(object,"client_object_id"))))
            auditIds.add(EsignValues.text(object,"client_object_id"));
        AuditCaptureScope scope=ownerMode?new AuditCaptureScope(c,auditIds):new AuditCaptureScope(c,p.isAuditEnabled());
        SapplyBpAudit.Capture sapplyBp=null;
        try{
            OrderPhysicalSnapshots orders = p.isAuditEnabled()
                    ? OrderPhysicalSnapshots.forAdapter(service, c.getOperationType()) : null;
            SalesOtherNodeSnapshots salesOther = p.isAuditEnabled()
                    ? SalesOtherNodeSnapshots.forAdapter(service, c.getOperationType()) : null;
            InventoryPhysicalSnapshots inventory = scope.isEnabled() && InventoryPhysicalSnapshots.applies(service, c)
                    ? new InventoryPhysicalSnapshots() : null;
            boolean sapply = "40080801".equals(c.getNodeCode()) && InventoryPhysicalSnapshots.applies(service,c);
            if (sapply) {
                sapplyBp=SapplyBpAudit.open(c,scope,NativeTransactionBoundaryProbe.requireActiveTransaction());
            }
            if (orders != null) orders.before(scope, c);
            if (salesOther != null) salesOther.before(scope, c);
            if (inventory != null && !sapply) inventory.before(scope, c);
            NativeOrgBatchExecution.Outcome organizationOutcome=null;Serializable result;
            if(organizationBatch!=null) {
                organizationOutcome=NativeOrgBatchExecution.execute(c,organizationBatch,token,scope);
                result=new ArchiveOrgBatchUpdateResult(organizationOutcome.nativeResult,organizationOutcome.units,
                    organizationOutcome.evidence.getPages().size());
            } else result=adapter.execute(c,command);
            ArchiveBatchReceipt receipt=null;
            if("nc.itf.uapbd.qd.esign.ICustomerFreezeAdapter".equals(service)) {
                EsignValues.require(ownerMode && ArchiveBatchResult.isCustomerFreeze(node,button,c.getOperationType())
                    && result instanceof ArchiveBatchResult && ((ArchiveBatchResult)result).getNativeResult()!=null,"客户冻结结果类型不正确。");
                receipt=ArchiveBatchReceipt.verify(refs,(ArchiveBatchResult)result);
            } else if("nc.itf.uapbd.qd.esign.ISupplierFreezeAdapter".equals(service)) {
                EsignValues.require(ownerMode && ArchiveBatchResult.isSupplierFreeze(node,button,c.getOperationType())
                    && result instanceof ArchiveBatchResult && ((ArchiveBatchResult)result).getNativeResult()==null,"供应商冻结结果类型不正确。");
                receipt=ArchiveBatchReceipt.verify(refs,(ArchiveBatchResult)result);
            } else if("nc.itf.uapbd.qd.esign.IPartnerStateAdapter".equals(service)) {
                EsignValues.require(ownerMode && ArchiveBatchResult.isPartnerState(node,button,c.getOperationType())
                    && result instanceof ArchiveBatchResult && ((ArchiveBatchResult)result).getNativeResult()!=null,"档案启停结果类型不正确。");
                receipt=ArchiveBatchReceipt.verify(refs,(ArchiveBatchResult)result);
            } else if("nc.itf.uapbd.qd.esign.IMaterialStateAdapter".equals(service)) {
                EsignValues.require(ownerMode && ArchiveBatchResult.isMaterialState(node,button,c.getOperationType())
                    && result instanceof ArchiveBatchResult && ((ArchiveBatchResult)result).getNativeResult()!=null,"物料启停结果类型不正确。");
                receipt=ArchiveBatchReceipt.verify(refs,(ArchiveBatchResult)result);
            } else if("nc.itf.uapbd.qd.esign.IWarehouseStateAdapter".equals(service)) {
                EsignValues.require(ownerMode && ArchiveBatchResult.isWarehouseState(node,button,c.getOperationType())
                    && result instanceof ArchiveBatchResult && ((ArchiveBatchResult)result).getNativeResult()!=null,"仓库启停结果类型不正确。");
                receipt=ArchiveBatchReceipt.verify(refs,(ArchiveBatchResult)result);
            } else if("nc.itf.uapbd.qd.esign.IPartnerBatchAdapter".equals(service)) {
                EsignValues.require(ownerMode && ArchiveBatchResult.isPartnerBatchUpdate(node,button,c.getOperationType()),"快速批改结果类型不正确。");
                if(organizationOutcome!=null) {
                    EsignValues.require(result instanceof ArchiveOrgBatchUpdateResult,"组织页签批改结果类型不正确。");
                    receipt=organizationOutcome.receipt;
                } else {
                    EsignValues.require(result instanceof ArchiveBatchUpdateResult,"快速批改结果类型不正确。");
                    receipt=ArchiveBatchReceipt.verifyUpdate(refs,(ArchiveBatchUpdateResult)result);
                }
            } else EsignValues.require(!(result instanceof ArchiveBatchResult) && !(result instanceof ArchiveBatchUpdateResult)
                    && !(result instanceof ArchiveOrgBatchUpdateResult),
                "当前业务不支持批量档案结果。");
            List<BusinessObjectRef> saved=organizationOutcome==null?adapter.describeResult(c,result):c.getObjects();
            EsignValues.require(saved.size()==refs.size(),"Business result does not identify every selected object");
            if (sapplyBp!=null) sapplyBp.verify(saved);
            if(ownerMode)PolicyServiceImpl.validateSavedOwners(store,i.groupId,c.getOperationType(),objects,saved);
            // 核对结果客户端标识唯一后回填真实主键及编码，与业务、审计证据共同提交。
            Set<String> returned=new HashSet<String>();
            for(BusinessObjectRef o:saved){EsignValues.require(returned.add(o.getClientObjectId()),"Duplicate result object");store.backfillObject(id,o.getClientObjectId(),o.getObjectPk(),o.getBillCode());}
            if(receipt!=null)for(Map<String,Object> object:objects) {
                ArchiveBatchResult.Unit unit=receipt.get(EsignValues.text(object,"client_object_id"));
                EsignValues.require(unit!=null && "PENDING".equals(object.get("unit_state")),"档案批量结果状态不完整。");
                store.finishAssignmentUnit(id,EsignValues.text(object,"object_id"),unit.getState(),
                    "FAILED".equals(unit.getState())?"NATIVE_REJECTED":"PARTIAL".equals(unit.getState())?"PARTIAL_NATIVE_REJECTED":null);
            }
            if (orders != null) orders.after(scope, c, saved);
            if (salesOther != null) salesOther.after(scope, c, saved);
            if (inventory != null) {
                if (SapplyLineScope.matches(c.getNodeCode(),c.getButtonCode(),c.getOperationType()))
                    SapplyLineSnapshots.after(scope,c,saved);
                else inventory.after(scope, c, saved);
            }
            // 新增无需 BEFORE，删除无需 AFTER；其他必需快照缺失时抛错，使整个业务事务回滚。
            if(p.isAuditEnabled()){
                for(Map<String,Object> o:store.loadObjects(id)){
                    String key=EsignValues.text(o,"client_object_id"),op=c.getOperationType();
                    if(!scope.isEnabledFor(key))continue;
                    if(receipt!=null && !requiresUnitAudit(c,receipt.get(key)))continue;
                    EsignValues.require("INSERT".equals(op)||scope.hasBefore(key),"Locked BEFORE snapshot was not captured; business must roll back");
                    EsignValues.require("DELETE".equals(op)||scope.hasAfter(key),"Persisted AFTER snapshot was not captured; business must roll back");
                    new AuditRecorder().record(r,o,scope.getBefore(key),scope.getAfter(key),scope.getBeforeReferences(key));
                }
            }
            store.updateRequest(id,EsignValues.row("business_state",receipt==null?"SUCCESS":receipt.getState(),"finished_at",EsignValues.utc()));return new OperationResponse(id,false,result,saved);
        }finally{try { if(sapplyBp!=null)sapplyBp.close(); } finally { scope.close(); }}
    }
    /** A verified organization receipt may contain successful pages inside a partially successful root. */
    static boolean requiresUnitAudit(OperationContext context,ArchiveBatchResult.Unit unit) {
        if(unit==null)return false;
        if("SUCCESS".equals(unit.getState()))return true;
        String client=unit.getClientId(),pk=unit.getObjectPk();
        return "PARTIAL".equals(unit.getState()) && context!=null
            && ArchiveBatchResult.isPartnerBatchUpdate(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && pk!=null && pk.matches("[A-Za-z0-9_-]{1,20}") && client!=null
            && client.matches("orgbulk:[a-f0-9]{64}:[A-Za-z0-9_-]{1,20}") && client.endsWith(":"+pk);
    }
}
